package org.openardf.radiooracle.desktop

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventResultDetails
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DesktopLocalResultServer(
    private val projectSupplier: () -> EventProjectFile?
) {
    private var server: HttpServer? = null
    private var executor: ExecutorService? = null
    var url: String? = null
        private set

    @Synchronized
    fun start(): String {
        url?.let { return it }

        val nextServer = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        val nextExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "radio-oracle-local-results").apply { isDaemon = true }
        }
        nextServer.executor = nextExecutor
        nextServer.createContext("/") { exchange ->
            exchange.sendText(indexHtml(), "text/html; charset=utf-8")
        }
        nextServer.createContext("/results.json") { exchange ->
            exchange.sendText(resultsJson(), "application/json; charset=utf-8")
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

    fun resultsJson(): String {
        val projectFile = projectSupplier()
            ?: return """{"project_open":false,"race_name":"","results":[]}"""
        val results = EventResultDetails.from(projectFile.raceData)

        return buildString {
            append("""{"project_open":true""")
            append(""","race_name":""")
            appendJsonString(projectFile.raceData.race.name)
            append(""","result_count":${results.size}""")
            append(""","results":[""")
            results.forEachIndexed { index, result ->
                if (index > 0) append(',')
                append('{')
                append(""""place":""")
                appendJsonString(result.placeText)
                append(""","competitor":""")
                appendJsonString(result.competitorName)
                append(""","status":""")
                appendJsonString(result.statusLabel)
                append(""","points":""")
                appendJsonString(result.pointsText)
                append(""","runtime":""")
                appendJsonString(result.runTimeText)
                append('}')
            }
            append("]}")
        }
    }

    private fun indexHtml(): String {
        val projectFile = projectSupplier()
        val raceName = projectFile?.raceData?.race?.name ?: "No project open"
        val results = projectFile?.let { EventResultDetails.from(it.raceData) } ?: emptyList()

        return buildString {
            append("<!doctype html><html><head><meta charset=\"utf-8\">")
            append("<title>Radio-Oracle Results</title>")
            append("<style>body{font-family:sans-serif;margin:24px}table{border-collapse:collapse}")
            append("td,th{border-bottom:1px solid #ddd;padding:6px 10px;text-align:left}</style>")
            append("</head><body><h1>")
            appendHtml(raceName)
            append("</h1><table><thead><tr><th>Place</th><th>Competitor</th><th>Status</th><th>Points</th><th>Runtime</th></tr></thead><tbody>")
            results.forEach { result ->
                append("<tr><td>")
                appendHtml(result.placeText)
                append("</td><td>")
                appendHtml(result.competitorName)
                append("</td><td>")
                appendHtml(result.statusLabel)
                append("</td><td>")
                appendHtml(result.pointsText)
                append("</td><td>")
                appendHtml(result.runTimeText)
                append("</td></tr>")
            }
            append("</tbody></table></body></html>")
        }
    }
}

private fun HttpExchange.sendText(text: String, contentType: String) {
    val bytes = text.toByteArray(StandardCharsets.UTF_8)
    responseHeaders.set("Content-Type", contentType)
    sendResponseHeaders(200, bytes.size.toLong())
    responseBody.use { output ->
        output.write(bytes)
    }
}

private fun StringBuilder.appendJsonString(value: String) {
    append('"')
    value.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(char)
        }
    }
    append('"')
}

private fun StringBuilder.appendHtml(value: String) {
    value.forEach { char ->
        when (char) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(char)
        }
    }
}
