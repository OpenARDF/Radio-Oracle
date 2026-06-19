package org.openardf.radiooracle.desktop

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private const val AndroidFileReceivePath = "/radio-oracle/file-receive"
private const val AndroidFileReceiveFileNameHeader = "X-Radio-Oracle-Filename-B64"
private const val DefaultMaxAndroidUploadBytes = 50L * 1024L * 1024L

data class DesktopAndroidFileReceiveSession(
    val address: DesktopEventFileTransferAddress,
    val port: Int,
    val token: String
) {
    val url: String
        get() = "http://${address.host}:$port$AndroidFileReceivePath?token=$token"
}

data class DesktopAndroidFileReceiveResult(
    val path: Path,
    val fileName: String,
    val contentType: String?,
    val byteCount: Long
)

class DesktopAndroidFileReceiveServer(
    private val receiveDirectory: Path,
    private val addressesProvider: () -> List<DesktopEventFileTransferAddress> = ::discoverDesktopEventFileTransferAddresses,
    private val tokenFactory: () -> String = ::generateDesktopEventFileTransferToken,
    private val timeoutMillis: Long = TimeUnit.MINUTES.toMillis(10),
    private val maxUploadBytes: Long = DefaultMaxAndroidUploadBytes,
    private val onReceived: (DesktopAndroidFileReceiveResult) -> Unit = {},
    private val onStopped: (DesktopAndroidFileReceiveStopReason) -> Unit = {}
) {
    private var server: HttpServer? = null
    private var executor: ScheduledExecutorService? = null
    private var timeoutTask: ScheduledFuture<*>? = null
    private var token: String = ""
    private val stopped = AtomicBoolean(false)

    val addresses: List<DesktopEventFileTransferAddress>
        get() = addressesProvider().ifEmpty {
            listOf(DesktopEventFileTransferAddress("This computer", "127.0.0.1"))
        }

    val port: Int
        get() = server?.address?.port ?: error("Android file receive server is not running.")

    @Synchronized
    fun start(address: DesktopEventFileTransferAddress = addresses.first()): DesktopAndroidFileReceiveSession {
        server?.let {
            return DesktopAndroidFileReceiveSession(address = address, port = it.address.port, token = token)
        }

        Files.createDirectories(receiveDirectory)
        token = tokenFactory()
        stopped.set(false)

        val nextServer = HttpServer.create(InetSocketAddress(InetAddress.getByName("0.0.0.0"), 0), 0)
        val nextExecutor = Executors.newScheduledThreadPool(2) { runnable ->
            Thread(runnable, "radio-oracle-android-file-receive").apply { isDaemon = true }
        }
        nextServer.executor = nextExecutor
        nextServer.createContext(AndroidFileReceivePath) { exchange ->
            handleExchange(exchange)
        }
        nextServer.start()

        server = nextServer
        executor = nextExecutor
        timeoutTask = nextExecutor.schedule(
            { stop(DesktopAndroidFileReceiveStopReason.Timeout) },
            timeoutMillis,
            TimeUnit.MILLISECONDS
        )

        return DesktopAndroidFileReceiveSession(address = address, port = nextServer.address.port, token = token)
    }

    @Synchronized
    fun sessionFor(address: DesktopEventFileTransferAddress): DesktopAndroidFileReceiveSession =
        DesktopAndroidFileReceiveSession(address = address, port = port, token = token)

    fun stop(reason: DesktopAndroidFileReceiveStopReason = DesktopAndroidFileReceiveStopReason.Cancelled) {
        if (!stopped.compareAndSet(false, true)) {
            return
        }
        val nextServer: HttpServer?
        val nextExecutor: ScheduledExecutorService?
        synchronized(this) {
            nextServer = server
            nextExecutor = executor
            timeoutTask?.cancel(false)
            timeoutTask = null
            server = null
            executor = null
        }
        nextServer?.stop(0)
        nextExecutor?.shutdownNow()
        onStopped(reason)
    }

    private fun handleExchange(exchange: HttpExchange) {
        if (exchange.requestURI.path != AndroidFileReceivePath || exchange.requestMethod != "POST") {
            exchange.sendStatus(404)
            return
        }
        if (queryParameters(exchange.requestURI)["token"] != token) {
            exchange.sendStatus(403)
            return
        }

        val fileName = decodedFileName(exchange.requestHeaders.getFirst(AndroidFileReceiveFileNameHeader))
        if (fileName.isBlank()) {
            exchange.sendStatus(400)
            return
        }

        val bytes = try {
            exchange.requestBody.use { input -> input.readLimitedBytes(maxUploadBytes) }
        } catch (_: AndroidUploadTooLargeException) {
            exchange.sendStatus(413)
            return
        }
        val targetPath = uniqueReceivePath(receiveDirectory, fileName)
        Files.write(targetPath, bytes)
        val result = DesktopAndroidFileReceiveResult(
            path = targetPath,
            fileName = targetPath.fileName.toString(),
            contentType = exchange.requestHeaders.getFirst("Content-Type"),
            byteCount = bytes.size.toLong()
        )
        exchange.sendStatus(201)
        onReceived(result)
        Thread(
            { stop(DesktopAndroidFileReceiveStopReason.Received) },
            "radio-oracle-android-file-receive-stop"
        ).apply { isDaemon = true }.start()
    }
}

enum class DesktopAndroidFileReceiveStopReason {
    Received,
    Cancelled,
    Timeout
}

object DesktopAndroidFileReceiveLocations {
    fun receiveDirectory(userHome: Path = Path.of(System.getProperty("user.home"))): Path =
        DesktopEventFileLocations.defaultEventFileDirectory(userHome).resolve("received-from-android")
}

private fun decodedFileName(encoded: String?): String =
    encoded
        ?.takeIf { it.isNotBlank() }
        ?.let {
            runCatching {
                String(Base64.getUrlDecoder().decode(it), StandardCharsets.UTF_8)
            }.getOrDefault("")
        }
        ?.let(::safeReceivedFileName)
        ?: ""

private fun safeReceivedFileName(fileName: String): String =
    fileName
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .replace(Regex("[\\p{Cntrl}:*?\"<>|]"), "_")
        .trim()
        .ifBlank { "android-upload.bin" }

private fun uniqueReceivePath(directory: Path, fileName: String): Path {
    val safeName = safeReceivedFileName(fileName)
    val dotIndex = safeName.lastIndexOf('.').takeIf { it > 0 }
    val stem = dotIndex?.let { safeName.substring(0, it) } ?: safeName
    val extension = dotIndex?.let { safeName.substring(it) } ?: ""
    var candidate = directory.resolve(safeName)
    var index = 2
    while (Files.exists(candidate)) {
        candidate = directory.resolve("$stem $index$extension")
        index += 1
    }
    return candidate
}

private fun java.io.InputStream.readLimitedBytes(maxBytes: Long): ByteArray {
    val buffer = ByteArray(16 * 1024)
    val output = ByteArrayOutputStream()
    var total = 0L
    while (true) {
        val read = read(buffer)
        if (read < 0) {
            return output.toByteArray()
        }
        total += read
        if (total > maxBytes) {
            throw AndroidUploadTooLargeException()
        }
        output.write(buffer, 0, read)
    }
}

private class AndroidUploadTooLargeException : RuntimeException()

private fun HttpExchange.sendStatus(status: Int) {
    sendResponseHeaders(status, -1)
    close()
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
