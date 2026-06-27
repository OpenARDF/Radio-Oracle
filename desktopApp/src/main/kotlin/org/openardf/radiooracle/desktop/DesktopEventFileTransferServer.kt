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

import com.google.zxing.BarcodeFormat
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.qrcode.QRCodeWriter
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.openardf.radiooracle.shared.event.EVENT_FILE_TRANSFER_CONTENT_TYPE
import java.awt.image.BufferedImage
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Base64
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private const val EventFileTransferPath = "/radio-oracle/event-file"

data class DesktopEventFileTransferAddress(
    val label: String,
    val host: String,
    val interfaceName: String = ""
)

data class DesktopEventFileTransferSession(
    val address: DesktopEventFileTransferAddress,
    val port: Int,
    val token: String,
    val fileName: String
) {
    val url: String
        get() = "http://${address.host}:$port$EventFileTransferPath?token=$token"
}

class DesktopEventFileTransferServer(
    private val filePath: Path,
    private val contentType: String = EVENT_FILE_TRANSFER_CONTENT_TYPE,
    private val addressesProvider: () -> List<DesktopEventFileTransferAddress> = ::discoverDesktopEventFileTransferAddresses,
    private val tokenFactory: () -> String = ::generateDesktopEventFileTransferToken,
    private val timeoutMillis: Long = TimeUnit.MINUTES.toMillis(10),
    private val onStopped: (DesktopEventFileTransferStopReason) -> Unit = {}
) {
    private var server: HttpServer? = null
    private var executor: ScheduledExecutorService? = null
    private var timeoutTask: ScheduledFuture<*>? = null
    private var eventFileBytes: ByteArray = ByteArray(0)
    private var token: String = ""
    private val stopped = AtomicBoolean(false)

    val addresses: List<DesktopEventFileTransferAddress>
        get() = addressesProvider().ifEmpty {
            listOf(DesktopEventFileTransferAddress("This computer", "127.0.0.1"))
        }

    val port: Int
        get() = server?.address?.port ?: error("Event File transfer server is not running.")

    @Synchronized
    fun start(address: DesktopEventFileTransferAddress = addresses.first()): DesktopEventFileTransferSession {
        server?.let {
            return DesktopEventFileTransferSession(
                address = address,
                port = it.address.port,
                token = token,
                fileName = filePath.fileName.toString()
            )
        }

        eventFileBytes = Files.readAllBytes(filePath)
        token = tokenFactory()
        stopped.set(false)

        val nextServer = HttpServer.create(InetSocketAddress(InetAddress.getByName("0.0.0.0"), 0), 0)
        val nextExecutor = Executors.newScheduledThreadPool(2) { runnable ->
            Thread(runnable, "radio-oracle-event-file-transfer").apply { isDaemon = true }
        }
        nextServer.executor = nextExecutor
        nextServer.createContext(EventFileTransferPath) { exchange ->
            handleExchange(exchange)
        }
        nextServer.start()

        server = nextServer
        executor = nextExecutor
        timeoutTask = nextExecutor.schedule(
            { stop(DesktopEventFileTransferStopReason.Timeout) },
            timeoutMillis,
            TimeUnit.MILLISECONDS
        )

        return DesktopEventFileTransferSession(
            address = address,
            port = nextServer.address.port,
            token = token,
            fileName = filePath.fileName.toString()
        )
    }

    @Synchronized
    fun sessionFor(address: DesktopEventFileTransferAddress): DesktopEventFileTransferSession =
        DesktopEventFileTransferSession(
            address = address,
            port = port,
            token = token,
            fileName = filePath.fileName.toString()
        )

    fun stop(reason: DesktopEventFileTransferStopReason = DesktopEventFileTransferStopReason.Cancelled) {
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
        if (exchange.requestURI.path != EventFileTransferPath) {
            exchange.sendStatus(404)
            return
        }
        if (exchange.requestMethod != "GET") {
            exchange.sendStatus(404)
            return
        }
        if (queryParameters(exchange.requestURI)["token"] != token) {
            exchange.sendStatus(403)
            return
        }

        exchange.responseHeaders.add("Content-Type", contentType)
        exchange.responseHeaders.add(
            "Content-Disposition",
            "attachment; filename=\"${contentDispositionFileName(filePath.fileName.toString())}\""
        )
        exchange.sendResponseHeaders(200, eventFileBytes.size.toLong())
        exchange.responseBody.use { output ->
            output.write(eventFileBytes)
        }

        Thread(
            { stop(DesktopEventFileTransferStopReason.Downloaded) },
            "radio-oracle-event-file-transfer-stop"
        ).apply { isDaemon = true }.start()
    }
}

enum class DesktopEventFileTransferStopReason {
    Downloaded,
    Cancelled,
    Timeout
}

fun discoverDesktopEventFileTransferAddresses(): List<DesktopEventFileTransferAddress> {
    val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
        .filter { it.isUp && !it.isLoopback }

    val addresses = interfaces.flatMap { networkInterface ->
        Collections.list(networkInterface.inetAddresses)
            .filterIsInstance<Inet4Address>()
            .filter { !it.isLoopbackAddress }
            .map {
                DesktopEventFileTransferAddress(
                    label = "${networkInterface.displayName} (${it.hostAddress})",
                    host = it.hostAddress,
                    interfaceName = networkInterface.name
                )
            }
    }

    return sortDesktopEventFileTransferAddresses(addresses)
}

fun sortDesktopEventFileTransferAddresses(
    addresses: List<DesktopEventFileTransferAddress>
): List<DesktopEventFileTransferAddress> {
    return addresses.sortedWith(
        compareByDescending<DesktopEventFileTransferAddress> { eventFileTransferAddressScore(it) }
            .thenBy { it.label }
            .thenBy { it.host }
    )
}

fun generateDesktopEventFileTransferToken(): String {
    val bytes = ByteArray(24)
    SecureRandom().nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

fun desktopEventFileTransferQrCode(url: String, size: Int = 320): BufferedImage {
    val matrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, size, size)
    return MatrixToImageWriter.toBufferedImage(matrix)
}

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

private fun contentDispositionFileName(fileName: String): String =
    fileName.replace("\\", "_").replace("\"", "_").replace("\r", "_").replace("\n", "_")

private fun isPrivateIpv4(host: String): Boolean {
    val parts = host.split('.').mapNotNull { it.toIntOrNull() }
    if (parts.size != 4) {
        return false
    }
    return parts[0] == 10 ||
        parts[0] == 192 && parts[1] == 168 ||
        parts[0] == 172 && parts[1] in 16..31 ||
        parts[0] == 169 && parts[1] == 254
}

private fun eventFileTransferAddressScore(address: DesktopEventFileTransferAddress): Int {
    val physicalLanScore = if (isLikelyPhysicalLanInterface(address)) 200 else 0
    val privateAddressScore = if (isPrivateIpv4(address.host)) 100 else 0
    val primaryMacWifiScore = if (address.interfaceName == "en0") 25 else 0
    return physicalLanScore + privateAddressScore + primaryMacWifiScore
}

private fun isLikelyPhysicalLanInterface(address: DesktopEventFileTransferAddress): Boolean {
    val interfaceName = address.interfaceName.lowercase()
    val label = address.label.lowercase()
    val blockedPrefixes = listOf("bridge", "utun", "awdl", "llw", "ap", "vmenet", "vmnet")
    val blockedLabelFragments = listOf("bridge", "vmware", "virtual", "awdl", "llw")
    if (blockedPrefixes.any { interfaceName.startsWith(it) } ||
        blockedLabelFragments.any { label.contains(it) }
    ) {
        return false
    }
    return interfaceName.startsWith("en") ||
        interfaceName.startsWith("eth") ||
        interfaceName.startsWith("wlan") ||
        label.contains("wi-fi") ||
        label.contains("wifi") ||
        label.contains("ethernet")
}
