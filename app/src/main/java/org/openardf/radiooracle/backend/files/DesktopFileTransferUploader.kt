package org.openardf.radiooracle.backend.files

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.openardf.radiooracle.backend.logging.DebugLog
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64

data class DesktopFileTransferUpload(
    val fileName: String,
    val contentType: String,
    val bytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DesktopFileTransferUpload

        if (fileName != other.fileName) return false
        if (contentType != other.contentType) return false
        if (!bytes.contentEquals(other.bytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = fileName.hashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

class DesktopFileTransferUploader(
    private val client: OkHttpClient = OkHttpClient()
) {
    @Throws(EventFileTransferException::class)
    fun upload(rawUrl: String, upload: DesktopFileTransferUpload) {
        val url = DesktopFileReceiveUrlValidator.validate(rawUrl)
        DebugLog.info(
            "EventFileTransfer",
            "Uploading ${upload.fileName} bytes=${upload.bytes.size} to ${safeUrlDescription(url)}"
        )
        val request = Request.Builder()
            .url(url)
            .header(
                "X-Radio-Oracle-Filename-B64",
                Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(upload.fileName.toByteArray(StandardCharsets.UTF_8))
            )
            .post(upload.bytes.toRequestBody(upload.contentType.toMediaTypeOrNull()))
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (error: SocketTimeoutException) {
            DebugLog.warn("EventFileTransfer", "Timed out uploading to ${safeUrlDescription(url)}")
            throw EventFileTransferException("The desktop did not respond. Check that both devices are on the same trusted Wi-Fi or hotspot.")
        } catch (error: IOException) {
            DebugLog.warn("EventFileTransfer", "Could not reach ${safeUrlDescription(url)}: ${error.message ?: error::class.simpleName}")
            throw EventFileTransferException("Could not reach the desktop. Check the Wi-Fi connection and try again.")
        }

        response.use {
            if (it.code == 403) {
                DebugLog.warn("EventFileTransfer", "Desktop rejected upload token at ${safeUrlDescription(url)}")
                throw EventFileTransferException("This receive link is expired, already used, or has the wrong token.")
            }
            if (it.code == 413) {
                DebugLog.warn("EventFileTransfer", "Desktop rejected upload as too large at ${safeUrlDescription(url)}")
                throw EventFileTransferException("The desktop rejected this file because it is too large.")
            }
            if (!it.isSuccessful) {
                DebugLog.warn("EventFileTransfer", "Desktop returned HTTP ${it.code} from ${safeUrlDescription(url)}")
                throw EventFileTransferException("The desktop returned HTTP ${it.code}. Start a new receive link and try again.")
            }
            DebugLog.info("EventFileTransfer", "Uploaded ${upload.fileName} to ${safeUrlDescription(url)}")
        }
    }
}

object DesktopFileReceiveUrlValidator {
    @Throws(EventFileTransferException::class)
    fun validate(rawUrl: String): String {
        val trimmedUrl = rawUrl.trim()
        val uri = try {
            URI(trimmedUrl)
        } catch (error: IllegalArgumentException) {
            throw EventFileTransferException("Enter the local receive URL shown on the desktop.")
        }

        if (uri.scheme != "http") {
            throw EventFileTransferException("Desktop receive URLs must start with http://.")
        }
        if (uri.path != "/radio-oracle/file-receive") {
            throw EventFileTransferException("Scan the Receive from Android QR code shown on the desktop.")
        }
        val host = uri.host ?: throw EventFileTransferException("Desktop receive URL is missing a host.")
        if (!isLocalHost(host)) {
            throw EventFileTransferException("Use a private Wi-Fi, hotspot, or .local desktop address, not a public internet URL.")
        }
        val token = queryParameters(uri)["token"]
        if (token.isNullOrBlank()) {
            throw EventFileTransferException("Desktop receive URL is missing its token.")
        }
        return uri.toASCIIString()
    }

    private fun isLocalHost(host: String): Boolean {
        val normalizedHost = host.lowercase()
        if (normalizedHost == "localhost" || normalizedHost.endsWith(".local")) {
            return true
        }

        val parts = normalizedHost.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 4) {
            return false
        }
        return parts[0] == 10 ||
            parts[0] == 127 ||
            parts[0] == 192 && parts[1] == 168 ||
            parts[0] == 172 && parts[1] in 16..31 ||
            parts[0] == 169 && parts[1] == 254
    }

    private fun queryParameters(uri: URI): Map<String, String> {
        val query = uri.rawQuery ?: return emptyMap()
        return query.split('&')
            .mapNotNull { part ->
                val pieces = part.split('=', limit = 2)
                if (pieces.isEmpty() || pieces[0].isEmpty()) {
                    null
                } else {
                    val key = URLDecoder.decode(pieces[0], StandardCharsets.UTF_8)
                    val value = if (pieces.size == 2) pieces[1] else ""
                    key to URLDecoder.decode(value, StandardCharsets.UTF_8)
                }
            }
            .toMap()
    }
}
