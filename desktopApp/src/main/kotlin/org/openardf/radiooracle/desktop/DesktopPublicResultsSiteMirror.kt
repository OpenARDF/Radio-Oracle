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

package org.openardf.radiooracle.desktop

import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.openardf.radiooracle.shared.event.PublicResultsPublication
import org.openardf.radiooracle.shared.event.PublicResultsPublicationStatus

enum class DesktopPublicResultsRetentionMode(
    val displayLabel: String,
    val description: String
) {
    RETAIN_PREVIOUS(
        displayLabel = "Retain previous events",
        description = "Keep previously published events and overwrite only the current race or series."
    ),
    REPLACE_PREVIOUS(
        displayLabel = "Replace previous events",
        description = "Publish only the current race or series and remove all previous result pages."
    )
}

internal object DesktopPublicResultsPublicationSelection {
    private var pendingStatus: PublicResultsPublicationStatus? = null
    private var activeSelection = false
    var lastGeneratedStatus: PublicResultsPublicationStatus =
        PublicResultsPublicationStatus.PRELIMINARY
        private set

    fun begin(status: PublicResultsPublicationStatus?) {
        pendingStatus = status
    }

    fun cancel() {
        pendingStatus = null
    }

    fun consumeForGeneration(): PublicResultsPublicationStatus {
        val status = pendingStatus
        activeSelection = status != null
        pendingStatus = null
        return status ?: PublicResultsPublicationStatus.PRELIMINARY
    }

    fun completeGeneration(status: PublicResultsPublicationStatus) {
        if (activeSelection) {
            lastGeneratedStatus = status
        }
        activeSelection = false
    }

    fun publication(url: String, publishedAtIso: String): PublicResultsPublication =
        PublicResultsPublication(url, publishedAtIso, lastGeneratedStatus)
}

internal object DesktopPublicResultsSiteMirror {
    private const val ROOT_FOLDER = "public-results-sites"

    fun directory(
        settings: DesktopCloudflarePagesPublishSettings,
        appDataDirectory: Path = DesktopAppDirectories.appDataDirectory()
    ): Path {
        val normalized = settings.normalized()
        val readablePrefix = listOf(normalized.projectName, normalized.branch)
            .joinToString("-")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(64)
            .ifBlank { "cloudflare-pages" }
        val identity = listOf(
            normalized.accountId,
            normalized.projectName,
            normalized.branch
        ).joinToString("\n")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(StandardCharsets.UTF_8))
            .take(8)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return appDataDirectory
            .resolve(ROOT_FOLDER)
            .resolve("$readablePrefix-$digest")
            .toAbsolutePath()
            .normalize()
    }

    fun prepare(
        settings: DesktopCloudflarePagesPublishSettings,
        appDataDirectory: Path = DesktopAppDirectories.appDataDirectory(),
        choosePublicationStatus: () -> PublicResultsPublicationStatus? = {
            PublicResultsPublicationStatus.PRELIMINARY
        },
        confirmReplacement: (Int) -> Boolean = { true }
    ): Path? {
        val publicationStatus = choosePublicationStatus() ?: return null
        DesktopPublicResultsPublicationSelection.begin(publicationStatus)
        val directory = directory(settings, appDataDirectory)
        try {
            if (settings.retentionMode == DesktopPublicResultsRetentionMode.REPLACE_PREVIOUS) {
                val retainedEntryCount = publishedEntryCount(directory)
                if (retainedEntryCount > 0 && !confirmReplacement(retainedEntryCount)) {
                    DesktopPublicResultsPublicationSelection.cancel()
                    return null
                }
                reset(directory, appDataDirectory)
            } else {
                Files.createDirectories(directory)
            }
            return directory
        } catch (error: Throwable) {
            DesktopPublicResultsPublicationSelection.cancel()
            throw error
        }
    }

    /**
     * Creates an isolated publish candidate. Retained events are copied into the candidate, while
     * replace mode starts empty. The managed mirror is changed only after [StagedPublish.promote].
     */
    fun stageForPublish(
        settings: DesktopCloudflarePagesPublishSettings,
        appDataDirectory: Path = DesktopAppDirectories.appDataDirectory()
    ): StagedPublish {
        val normalizedSettings = settings.normalized()
        DesktopPublicResultsPublicationSelection.begin(normalizedSettings.publicationStatus)
        val mirrorDirectory = directory(normalizedSettings, appDataDirectory)
        val managedRoot = mirrorDirectory.parent
        Files.createDirectories(managedRoot)
        val stagingDirectory = Files.createTempDirectory(
            managedRoot,
            "${mirrorDirectory.fileName}.staging-"
        )
        return try {
            if (
                normalizedSettings.retentionMode == DesktopPublicResultsRetentionMode.RETAIN_PREVIOUS &&
                Files.isDirectory(mirrorDirectory)
            ) {
                copyRecursively(mirrorDirectory, stagingDirectory)
            }
            StagedPublish(
                stagingDirectory = stagingDirectory,
                mirrorDirectory = mirrorDirectory,
                managedRoot = managedRoot
            )
        } catch (error: Throwable) {
            DesktopPublicResultsPublicationSelection.cancel()
            deleteManagedDirectory(stagingDirectory, managedRoot)
            throw error
        }
    }

    fun publishedEntryCount(directory: Path): Int {
        val racesJson = directory.resolve("data").resolve("races.json")
        if (!Files.isRegularFile(racesJson)) {
            return 0
        }
        return runCatching {
            Json.parseToJsonElement(Files.readString(racesJson, StandardCharsets.UTF_8))
                .jsonObject["races"]
                ?.jsonArray
                ?.size
                ?: 1
        }.getOrDefault(1)
    }

    fun deleteGeneratedSiteDirectory(root: Path, relativePath: String) {
        val normalizedRoot = root.toAbsolutePath().normalize()
        val target = normalizedRoot.resolve(relativePath).normalize()
        require(target.parent == normalizedRoot && target != normalizedRoot) {
            "Unsafe generated public-results path: $relativePath"
        }
        if (!Files.exists(target)) {
            return
        }
        require(isGeneratedSiteDirectory(target)) {
            "Refusing to remove a directory not created by the public-results exporter: $target"
        }
        deleteRecursively(target)
    }

    private fun reset(directory: Path, appDataDirectory: Path) {
        val managedRoot = appDataDirectory.resolve(ROOT_FOLDER).toAbsolutePath().normalize()
        val normalizedDirectory = directory.toAbsolutePath().normalize()
        require(normalizedDirectory.parent == managedRoot && normalizedDirectory != managedRoot) {
            "Refusing to reset an unmanaged public-results directory: $normalizedDirectory"
        }
        if (Files.exists(normalizedDirectory)) {
            deleteRecursively(normalizedDirectory)
        }
        Files.createDirectories(normalizedDirectory)
    }

    internal class StagedPublish internal constructor(
        val stagingDirectory: Path,
        val mirrorDirectory: Path,
        private val managedRoot: Path
    ) {
        private var promoted = false

        fun promote(): Path {
            check(!promoted) { "The staged public-results site has already been promoted." }
            requireManagedChild(stagingDirectory, managedRoot)
            requireManagedChild(mirrorDirectory, managedRoot)
            val backupDirectory = managedRoot.resolve(
                "${mirrorDirectory.fileName}.backup-${UUID.randomUUID()}"
            )
            var existingMirrorMoved = false
            try {
                if (Files.exists(mirrorDirectory)) {
                    moveDirectory(mirrorDirectory, backupDirectory)
                    existingMirrorMoved = true
                }
                moveDirectory(stagingDirectory, mirrorDirectory)
                promoted = true
            } catch (error: Throwable) {
                if (!Files.exists(mirrorDirectory) && existingMirrorMoved && Files.exists(backupDirectory)) {
                    runCatching { moveDirectory(backupDirectory, mirrorDirectory) }
                }
                throw error
            }
            if (existingMirrorMoved && Files.exists(backupDirectory)) {
                runCatching { deleteManagedDirectory(backupDirectory, managedRoot) }
            }
            return mirrorDirectory
        }

        fun discard() {
            DesktopPublicResultsPublicationSelection.cancel()
            if (!promoted && Files.exists(stagingDirectory)) {
                deleteManagedDirectory(stagingDirectory, managedRoot)
            }
        }
    }

    private fun isGeneratedSiteDirectory(directory: Path): Boolean {
        if (!Files.isRegularFile(directory.resolve("index.html"))) {
            return false
        }
        val dataDirectory = directory.resolve("data")
        return Files.isDirectory(dataDirectory) && listOf(
            "event-summary.json",
            "public-results.json",
            "series-results.json"
        ).any { Files.isRegularFile(dataDirectory.resolve(it)) }
    }

    private fun copyRecursively(source: Path, target: Path) {
        Files.walk(source).use { paths ->
            paths.forEach { sourcePath ->
                val relativePath = source.relativize(sourcePath)
                val targetPath = target.resolve(relativePath)
                if (Files.isDirectory(sourcePath)) {
                    Files.createDirectories(targetPath)
                } else {
                    targetPath.parent?.let(Files::createDirectories)
                    Files.copy(
                        sourcePath,
                        targetPath,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES
                    )
                }
            }
        }
    }

    private fun moveDirectory(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target)
        }
    }

    private fun deleteManagedDirectory(directory: Path, managedRoot: Path) {
        requireManagedChild(directory, managedRoot)
        if (Files.exists(directory)) {
            deleteRecursively(directory)
        }
    }

    private fun requireManagedChild(directory: Path, managedRoot: Path) {
        val normalizedRoot = managedRoot.toAbsolutePath().normalize()
        val normalizedDirectory = directory.toAbsolutePath().normalize()
        require(normalizedDirectory.parent == normalizedRoot && normalizedDirectory != normalizedRoot) {
            "Unsafe managed public-results directory: $normalizedDirectory"
        }
    }

    private fun deleteRecursively(directory: Path) {
        Files.walk(directory).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::delete)
        }
    }
}
