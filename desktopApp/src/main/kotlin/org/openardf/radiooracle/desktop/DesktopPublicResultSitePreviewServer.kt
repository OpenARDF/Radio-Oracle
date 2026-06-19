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
    private val siteDirectory: Path
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

        val nextServer = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
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
        url = "http://127.0.0.1:${nextServer.address.port}/"
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
    val target = siteDirectory.resolve(requestedPath).normalize()
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
