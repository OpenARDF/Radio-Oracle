/*
 * MIT License
 *
 * Copyright (c) 2025 Pavel Kolský
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.openardf.radiooracle.backend.publicresults

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.openardf.radiooracle.shared.publicresults.PublicResultsSiteCatalog
import java.io.File
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Managed Android mirror of the deployed results site.
 *
 * Retain mode first reads the public Cloudflare site into a staging directory. This
 * keeps history published by desktop or another Android device instead of assuming
 * this device's local mirror is authoritative.
 */
object AndroidPublicResultsSiteMirror {
    private const val ROOT_FOLDER = "public-results-sites"

    fun directory(
        context: Context,
        settings: AndroidCloudflarePagesPublishSettings
    ): File {
        val value = settings.normalized()
        val readablePrefix = "${value.projectName}-${value.branch}"
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(64)
            .ifBlank { "cloudflare-pages" }
        val identity = listOf(value.accountId, value.projectName, value.branch).joinToString("\n")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(StandardCharsets.UTF_8))
            .take(8)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return File(File(context.filesDir, ROOT_FOLDER), "$readablePrefix-$digest")
    }

    fun prepare(
        context: Context,
        settings: AndroidCloudflarePagesPublishSettings,
        synchronizer: AndroidPublicResultsRemoteSynchronizer =
            AndroidPublicResultsRemoteSynchronizer()
    ): File {
        val directory = directory(context, settings)
        if (settings.retentionMode == AndroidPublicResultsRetentionMode.REPLACE_PREVIOUS) {
            resetManagedDirectory(context, directory)
        } else {
            synchronizer.synchronize(settings.publicSiteBaseUrl(), directory, context.cacheDir)
        }
        directory.mkdirs()
        return directory
    }

    fun publishedEntryCount(
        context: Context,
        settings: AndroidCloudflarePagesPublishSettings
    ): Int {
        val catalog = directory(context, settings).resolve("data/races.json")
        return if (catalog.isFile) {
            PublicResultsSiteCatalog.parse(catalog.readText()).size
        } else {
            0
        }
    }

    fun deleteGeneratedSiteDirectory(root: File, relativePath: String) {
        val normalizedRoot = root.canonicalFile
        val target = normalizedRoot.resolve(relativePath).canonicalFile
        require(target.parentFile == normalizedRoot && target != normalizedRoot) {
            "Unsafe generated public-results path: $relativePath"
        }
        if (!target.exists()) return
        require(isGeneratedSiteDirectory(target)) {
            "Refusing to remove a directory not created by the public-results exporter: $target"
        }
        require(target.deleteRecursively()) {
            "Could not remove the previous public-results directory: $target"
        }
    }

    private fun resetManagedDirectory(context: Context, directory: File) {
        val managedRoot = File(context.filesDir, ROOT_FOLDER).canonicalFile
        val target = directory.canonicalFile
        require(target.parentFile == managedRoot && target != managedRoot) {
            "Refusing to reset an unmanaged public-results directory: $target"
        }
        if (target.exists()) {
            require(target.deleteRecursively()) {
                "Could not clear the prior public-results mirror."
            }
        }
        target.mkdirs()
    }

    private fun isGeneratedSiteDirectory(directory: File): Boolean =
        directory.resolve("index.html").isFile &&
            listOf(
                "event-summary.json",
                "public-results.json",
                "series-results.json"
            ).any { directory.resolve("data/$it").isFile }
}

class AndroidPublicResultsRemoteSynchronizer(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(2, TimeUnit.MINUTES)
        .build()
) {
    fun synchronize(baseUrl: String, targetDirectory: File, cacheDirectory: File) {
        val rootUrl = baseUrl.trim().trimEnd('/')
        val catalogBytes = get("$rootUrl/data/races.json", optional = true)
        if (catalogBytes == null) {
            replaceTargetWithEmptyCatalog(targetDirectory, cacheDirectory)
            return
        }
        val catalogText = catalogBytes.toString(StandardCharsets.UTF_8)
        val entries = PublicResultsSiteCatalog.parseStrict(catalogText)
        requireNotNull(entries) {
            "The existing public results site has an invalid data/races.json catalog."
        }

        val staging = File(cacheDirectory, "public-results-sync-${UUID.randomUUID()}")
        require(staging.mkdirs()) {
            "Could not create a public-results synchronization directory."
        }
        try {
            write(staging, "data/races.json", catalogBytes)
            write(
                staging,
                "index.html",
                get("$rootUrl/index.html", optional = true)
                    ?: PublicResultsSiteCatalog.rootIndexHtml(entries)
                        .toByteArray(StandardCharsets.UTF_8)
            )
            val synchronizedPaths = mutableSetOf<String>()
            entries.forEach { entry ->
                synchronizePublishedDirectory(
                    rootUrl,
                    entry.path,
                    staging,
                    synchronizedPaths
                )
            }
            replaceTarget(staging, targetDirectory)
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun replaceTargetWithEmptyCatalog(targetDirectory: File, cacheDirectory: File) {
        val staging = File(cacheDirectory, "public-results-empty-${UUID.randomUUID()}")
        require(staging.mkdirs()) {
            "Could not create an empty public-results synchronization directory."
        }
        try {
            write(
                staging,
                "data/races.json",
                PublicResultsSiteCatalog.encode(emptyList()).toByteArray(StandardCharsets.UTF_8)
            )
            write(
                staging,
                "index.html",
                PublicResultsSiteCatalog.rootIndexHtml(emptyList()).toByteArray(StandardCharsets.UTF_8)
            )
            replaceTarget(staging, targetDirectory)
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun synchronizePublishedDirectory(
        rootUrl: String,
        publishedPath: String,
        staging: File,
        synchronizedPaths: MutableSet<String>
    ) {
        if (!synchronizedPaths.add(publishedPath)) return
        val fixedFiles = listOf(
            "index.html",
            "assets/site.css",
            "assets/site.js",
            "assets/series-site.js",
            "data/event-summary.json",
            "data/public-results.json",
            "data/final-results.json",
            "data/live-results.json",
            "data/series-results.json",
            "downloads/final-results.json",
            "downloads/live-results.json",
            "downloads/iof-result-list.xml",
            "downloads/printable-results.html"
        )
        val downloaded = mutableMapOf<String, ByteArray>()
        fixedFiles.forEach { suffix ->
            get("$rootUrl/$publishedPath/$suffix", optional = true)?.let { bytes ->
                val relativePath = "$publishedPath/$suffix"
                downloaded[relativePath] = bytes
                write(staging, relativePath, bytes)
            }
        }
        require("$publishedPath/index.html" in downloaded) {
            "The existing public results site is missing $publishedPath/index.html."
        }
        require(
            listOf(
                "$publishedPath/data/event-summary.json",
                "$publishedPath/data/public-results.json",
                "$publishedPath/data/series-results.json"
            ).any(downloaded::containsKey)
        ) {
            "The existing public results site has an incomplete event directory: $publishedPath."
        }

        downloaded
            .filterKeys { it.endsWith(".json") }
            .values
            .flatMap { referencedPublicPaths(rootUrl, publishedPath, it) }
            .distinct()
            .forEach { relativePath ->
                get("$rootUrl/$relativePath", optional = true)?.let { bytes ->
                    write(staging, relativePath, bytes)
                    if (relativePath.endsWith("/data/public-results.json")) {
                        val memberPath = relativePath.substringBefore("/data/public-results.json")
                        synchronizePublishedDirectory(
                            rootUrl,
                            memberPath,
                            staging,
                            synchronizedPaths
                        )
                    }
                }
            }
    }

    private fun referencedPublicPaths(
        rootUrl: String,
        publishedPath: String,
        bytes: ByteArray
    ): List<String> {
        val document = runCatching {
            Json.parseToJsonElement(bytes.toString(StandardCharsets.UTF_8))
        }.getOrNull() ?: return emptyList()
        val base = URI.create("$rootUrl/$publishedPath/")
        val root = URI.create("$rootUrl/")
        return document.stringValues()
            .mapNotNull { value ->
                if (
                    !value.endsWith(".json") &&
                    !value.endsWith(".html") &&
                    !value.endsWith(".xml") &&
                    !value.endsWith(".svg") &&
                    !value.endsWith(".png") &&
                    !value.endsWith(".webp")
                ) {
                    return@mapNotNull null
                }
                val resolved = base.resolve(value).normalize()
                if (
                    resolved.scheme != root.scheme ||
                    resolved.authority != root.authority ||
                    !resolved.path.startsWith(root.path)
                ) {
                    return@mapNotNull null
                }
                resolved.path.removePrefix(root.path).trimStart('/')
                    .takeIf { safeRelativePath(it) }
            }
    }

    private fun JsonElement.stringValues(): List<String> =
        when (this) {
            is JsonPrimitive -> if (isString) listOf(content) else emptyList()
            is JsonArray -> flatMap { it.stringValues() }
            is JsonObject -> values.flatMap { it.stringValues() }
        }

    private fun get(url: String, optional: Boolean): ByteArray? {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "*/*")
            .header("Cache-Control", "no-cache")
            .build()
        return client.newCall(request).execute().use { response ->
            if (optional && response.code == 404) {
                return@use null
            }
            if (!response.isSuccessful) {
                throw IllegalStateException(
                    "Could not synchronize the existing public results site: HTTP ${response.code} for $url"
                )
            }
            val bytes = response.body.bytes()
            require(bytes.size <= 25 * 1024 * 1024) {
                "Existing public results file is larger than 25 MiB: $url"
            }
            bytes
        }
    }

    private fun write(root: File, relativePath: String, bytes: ByteArray) {
        require(safeRelativePath(relativePath)) {
            "Unsafe public-results path: $relativePath"
        }
        val target = root.resolve(relativePath).canonicalFile
        require(target.path.startsWith(root.canonicalPath + File.separator)) {
            "Unsafe public-results path: $relativePath"
        }
        target.parentFile?.mkdirs()
        target.writeBytes(bytes)
    }

    private fun replaceTarget(staging: File, target: File) {
        if (target.exists()) {
            require(target.deleteRecursively()) {
                "Could not replace the local public-results mirror."
            }
        }
        target.mkdirs()
        staging.walkTopDown().filter(File::isFile).forEach { source ->
            val destination = target.resolve(source.relativeTo(staging).path)
            destination.parentFile?.mkdirs()
            source.copyTo(destination, overwrite = true)
        }
    }

    private fun safeRelativePath(path: String): Boolean =
        path.isNotBlank() &&
            !path.startsWith('/') &&
            path.split('/', '\\').none { it.isBlank() || it == "." || it == ".." }
}
