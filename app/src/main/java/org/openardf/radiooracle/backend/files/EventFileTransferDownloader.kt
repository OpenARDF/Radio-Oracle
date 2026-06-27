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

package org.openardf.radiooracle.backend.files

import okhttp3.OkHttpClient
import okhttp3.Request
import org.openardf.radiooracle.backend.logging.DebugLog
import org.openardf.radiooracle.shared.event.EventFileTransferPayloads
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class EventFileTransferDownload(
    val fileName: String,
    val contentType: String?,
    val bytes: ByteArray
) {
    val isZip: Boolean
        get() = EventFileTransferPayloads.isSeriesPackage(fileName, contentType)

    fun text(): String = bytes.toString(Charsets.UTF_8)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EventFileTransferDownload

        if (fileName != other.fileName) return false
        if (contentType != other.contentType) return false
        if (!bytes.contentEquals(other.bytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = fileName.hashCode()
        result = 31 * result + (contentType?.hashCode() ?: 0)
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

class EventFileTransferDownloader(
    private val client: OkHttpClient = OkHttpClient()
) {
    @Throws(EventFileTransferException::class)
    fun download(rawUrl: String): EventFileTransferDownload {
        val url = EventFileTransferUrlValidator.validate(rawUrl)
        DebugLog.info("EventFileTransfer", "Downloading Event File from ${safeUrlDescription(url)}")
        val request = Request.Builder().url(url).get().build()

        val response = try {
            client.newCall(request).execute()
        } catch (error: SocketTimeoutException) {
            DebugLog.warn("EventFileTransfer", "Timed out downloading from ${safeUrlDescription(url)}")
            throw EventFileTransferException("The desktop did not respond. Check that both devices are on the same trusted Wi-Fi or hotspot.")
        } catch (error: IOException) {
            DebugLog.warn("EventFileTransfer", "Could not reach ${safeUrlDescription(url)}: ${error.message ?: error::class.simpleName}")
            throw EventFileTransferException("Could not reach the desktop. Check the Wi-Fi connection and try again.")
        }

        response.use {
            if (it.code == 403) {
                DebugLog.warn("EventFileTransfer", "Desktop rejected transfer token at ${safeUrlDescription(url)}")
                throw EventFileTransferException("This transfer link is expired, already used, or has the wrong token.")
            }
            if (!it.isSuccessful) {
                DebugLog.warn("EventFileTransfer", "Desktop returned HTTP ${it.code} from ${safeUrlDescription(url)}")
                throw EventFileTransferException("The desktop returned HTTP ${it.code}. Start a new transfer and try again.")
            }
            val bytes = it.body.bytes()
            val fileName = contentDispositionFileName(it.header("Content-Disposition"))
                ?: if (EventFileTransferPayloads.isSeriesPackage(fileName = null, contentType = it.header("Content-Type"))) {
                    "event-series.zip"
                } else {
                    "event.rom.json"
                }
            DebugLog.info("EventFileTransfer", "Downloaded Event File bytes=${bytes.size} from ${safeUrlDescription(url)}")
            return EventFileTransferDownload(
                fileName = fileName,
                contentType = it.header("Content-Type"),
                bytes = bytes
            )
        }
    }
}

private fun contentDispositionFileName(header: String?): String? {
    val value = header ?: return null
    val fileNameStar = Regex("""filename\*=UTF-8''([^;]+)""", RegexOption.IGNORE_CASE)
        .find(value)
        ?.groupValues
        ?.getOrNull(1)
        ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8) }
    if (!fileNameStar.isNullOrBlank()) {
        return safeDownloadFileName(fileNameStar)
    }
    return Regex("""filename="?([^";]+)"?""", RegexOption.IGNORE_CASE)
        .find(value)
        ?.groupValues
        ?.getOrNull(1)
        ?.let(::safeDownloadFileName)
}

private fun safeDownloadFileName(fileName: String): String =
    fileName
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .replace(Regex("[\\p{Cntrl}:*?\"<>|]"), "_")
        .trim()
        .ifBlank { "event.rom.json" }

object EventFileTransferUrlValidator {
    @Throws(EventFileTransferException::class)
    fun validate(rawUrl: String): String {
        val trimmedUrl = rawUrl.trim()
        val uri = try {
            URI(trimmedUrl)
        } catch (error: IllegalArgumentException) {
            throw EventFileTransferException("Enter the local transfer URL shown on the desktop.")
        }

        if (uri.scheme != "http") {
            throw EventFileTransferException("Desktop transfer URLs must start with http://.")
        }
        val host = uri.host ?: throw EventFileTransferException("Desktop transfer URL is missing a host.")
        if (!isLocalHost(host)) {
            throw EventFileTransferException("Use a private Wi-Fi, hotspot, or .local desktop address, not a public internet URL.")
        }
        val token = queryParameters(uri)["token"]
        if (token.isNullOrBlank()) {
            throw EventFileTransferException("Desktop transfer URL is missing its token.")
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

class EventFileTransferException(message: String) : Exception(message)

internal fun safeUrlDescription(url: String): String {
    return runCatching {
        val uri = URI(url)
        "${uri.scheme}://${uri.host}${if (uri.port >= 0) ":${uri.port}" else ""}${uri.path}"
    }.getOrDefault("desktop transfer URL")
}
