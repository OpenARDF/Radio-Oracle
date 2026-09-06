package org.openardf.radiooracle.shared.publicresults

import kotlinx.serialization.Serializable
import java.net.URI
import java.security.MessageDigest

@Serializable
data class PublicResultsArtifact(val path: String, val size: Int, val sha256: String)

@Serializable
data class PublicResultsArtifactManifest(val version: Int = 1, val artifacts: List<PublicResultsArtifact>)

/** Public bytes only. This inventory never includes passwords, course provenance, or private race files. */
object PublicResultsArtifactVerification {
    fun manifest(site: CloudflarePagesSite): PublicResultsArtifactManifest {
        val paths = site.assets.map { it.relativePath }.toSet()
        require(paths.containsAll(listOf("index.html", "data/races.json"))) { "The public site is missing its index or catalog." }
        site.assets.forEach { asset ->
            require(safePath(asset.relativePath)) { "Unsafe public artifact path." }
            require(asset.hash == CloudflarePagesProtocol.assetHash(asset.relativePath, asset.content)) { "A public artifact changed after it was selected for upload." }
            if (asset.relativePath.endsWith(".html")) {
                val base = URI("https://public.invalid/").resolve(asset.relativePath)
                Regex("""(?:src|href)\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                    .findAll(asset.content.toString(Charsets.UTF_8)).forEach { match ->
                        val reference = match.groupValues[1].replace("&amp;", "&")
                        if (!reference.startsWith('#')) {
                            val target = runCatching { base.resolve(reference) }.getOrNull()
                            if (target?.host == base.host) {
                                val path = target.path.removePrefix("/").let { if (it.isEmpty() || it.endsWith('/')) "${it}index.html" else it }
                                require(path in paths) { "Public page ${asset.relativePath} references a missing artifact: $path" }
                            }
                        }
                    }
            }
        }
        return PublicResultsArtifactManifest(artifacts = site.assets.sortedBy { it.relativePath }.map {
            PublicResultsArtifact(it.relativePath, it.content.size, digest(it.content))
        })
    }

    /** Independent downloader seam: exact binary content, including PNG/PDF, must match the frozen inventory. */
    fun verify(manifest: PublicResultsArtifactManifest, fetch: (String) -> ByteArray): List<String> {
        require(manifest.version == 1 && manifest.artifacts.isNotEmpty() &&
            manifest.artifacts.map { it.path }.distinct().size == manifest.artifacts.size &&
            manifest.artifacts.all { safePath(it.path) && it.size >= 0 && it.sha256.matches(Regex("[0-9a-f]{64}")) }) { "Invalid public artifact inventory." }
        return manifest.artifacts.mapNotNull { expected ->
            val actual = runCatching { fetch(expected.path) }.getOrNull()
            expected.path.takeIf { actual == null || actual.size != expected.size || digest(actual) != expected.sha256 }
        }
    }

    private fun safePath(path: String): Boolean = path.isNotBlank() && !path.startsWith('/') &&
        path.split('/', '\\').none { it.isBlank() || it == "." || it == ".." } &&
        path.none { it == '?' || it == '#' || it == ':' || it == '%' }

    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
