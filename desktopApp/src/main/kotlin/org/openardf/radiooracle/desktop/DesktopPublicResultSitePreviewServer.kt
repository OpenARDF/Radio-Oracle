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

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DesktopPublicResultSitePreviewServer(
    private val siteDirectory: Path,
    private val sharedAddress: DesktopEventFileTransferAddress? = null
) {
    private var server: HttpServer? = null
    private var executor: ExecutorService? = null
    var url: String? = null
        private set

    @Synchronized
    fun start(): String {
        url?.let { return it }
        require(Files.isDirectory(siteDirectory)) {
            "Public results site folder does not exist: $siteDirectory"
        }
        require(Files.isRegularFile(siteDirectory.resolve("index.html"))) {
            "Public results site folder does not contain index.html."
        }

        val bindAddress = if (sharedAddress == null) {
            InetAddress.getLoopbackAddress()
        } else {
            InetAddress.getByName("0.0.0.0")
        }
        val nextServer = HttpServer.create(InetSocketAddress(bindAddress, 0), 0)
        val nextExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "radio-oracle-public-site-preview").apply { isDaemon = true }
        }
        nextServer.executor = nextExecutor
        nextServer.createContext("/") { exchange ->
            exchange.handleStaticSiteRequest(siteDirectory)
        }
        nextServer.start()

        server = nextServer
        executor = nextExecutor
        url = "http://${sharedAddress?.host ?: "127.0.0.1"}:${nextServer.address.port}/"
        return requireNotNull(url)
    }

    @Synchronized
    fun stop() {
        server?.stop(0)
        executor?.shutdownNow()
        server = null
        executor = null
        url = null
    }
}

private fun HttpExchange.handleStaticSiteRequest(siteDirectory: Path) {
    if (requestMethod != "GET" && requestMethod != "HEAD") {
        responseHeaders.add("Allow", "GET, HEAD")
        sendResponseHeaders(405, -1)
        close()
        return
    }

    val requestedPath = requestURI.path.trimStart('/').ifBlank { "index.html" }
    val requestedTarget = siteDirectory.resolve(requestedPath).normalize()
    val target = if (Files.isDirectory(requestedTarget)) {
        requestedTarget.resolve("index.html").normalize()
    } else {
        requestedTarget
    }
    val siteRoot = siteDirectory.normalize()
    if (!target.startsWith(siteRoot) || !Files.isRegularFile(target)) {
        sendResponseHeaders(404, -1)
        close()
        return
    }

    responseHeaders.add("Cache-Control", "no-store")
    responseHeaders.add("Content-Type", contentType(target))
    if (requestMethod == "HEAD") {
        sendResponseHeaders(200, -1)
        close()
        return
    }

    val bytes = Files.readAllBytes(target)
    sendResponseHeaders(200, bytes.size.toLong())
    responseBody.use { it.write(bytes) }
}

private fun contentType(path: Path): String =
    when (path.fileName.toString().substringAfterLast('.', missingDelimiterValue = "").lowercase()) {
        "html" -> "text/html; charset=utf-8"
        "css" -> "text/css; charset=utf-8"
        "js" -> "application/javascript; charset=utf-8"
        "json" -> "application/json; charset=utf-8"
        "xml" -> "application/xml; charset=utf-8"
        "kml" -> "application/vnd.google-earth.kml+xml; charset=utf-8"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        else -> "application/octet-stream"
    }
