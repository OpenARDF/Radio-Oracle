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
import org.openardf.radiooracle.shared.event.EventInForestDetails
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventResultDetails
import org.openardf.radiooracle.shared.event.EventStartListDetails
import org.openardf.radiooracle.shared.event.competitionCategories
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DesktopLocalResultServer(
    private val projectSupplier: () -> EventProjectFile?,
    private val raceElapsedSeconds: (String) -> Long = ::defaultRaceElapsedSeconds
) {
    private var server: HttpServer? = null
    private var executor: ExecutorService? = null
    var url: String? = null
        private set

    private val autoRefreshMeta = "<meta http-equiv=\"refresh\" content=\"5\">"

    @Synchronized
    fun start(): String {
        url?.let { return it }

        val nextServer = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        val nextExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "radio-oracle-local-results").apply { isDaemon = true }
        }
        nextServer.executor = nextExecutor
        nextServer.createContext("/") { exchange ->
            exchange.handleExactGet("/") {
                exchange.sendText(indexHtml(), "text/html; charset=utf-8")
            }
        }
        nextServer.createContext("/results.json") { exchange ->
            exchange.handleExactGet("/results.json") {
                exchange.sendText(resultsJson(), "application/json; charset=utf-8")
            }
        }
        nextServer.createContext("/categories") { exchange ->
            exchange.handleExactGet("/categories") {
                exchange.sendText(categoriesHtml(), "text/html; charset=utf-8")
            }
        }
        nextServer.createContext("/categories.json") { exchange ->
            exchange.handleExactGet("/categories.json") {
                exchange.sendText(categoriesJson(), "application/json; charset=utf-8")
            }
        }
        nextServer.createContext("/starts") { exchange ->
            exchange.handleExactGet("/starts") {
                exchange.sendText(startsHtml(), "text/html; charset=utf-8")
            }
        }
        nextServer.createContext("/starts.json") { exchange ->
            exchange.handleExactGet("/starts.json") {
                exchange.sendText(startsJson(), "application/json; charset=utf-8")
            }
        }
        nextServer.createContext("/in-forest") { exchange ->
            exchange.handleExactGet("/in-forest") {
                exchange.sendText(inForestHtml(), "text/html; charset=utf-8")
            }
        }
        nextServer.createContext("/in-forest.json") { exchange ->
            exchange.handleExactGet("/in-forest.json") {
                exchange.sendText(inForestJson(), "application/json; charset=utf-8")
            }
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
                append(""""category":""")
                appendJsonString(result.categoryName)
                append(""","place":""")
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

    fun categoriesJson(): String {
        val projectFile = projectSupplier()
            ?: return """{"project_open":false,"race_name":"","categories":[]}"""
        val raceData = projectFile.raceData
        val categories = raceData.competitionCategories(includeResultCategoryIds = false)

        return buildString {
            append("""{"project_open":true""")
            append(""","race_name":""")
            appendJsonString(raceData.race.name)
            append(""","category_count":${categories.size}""")
            append(""","categories":[""")
            categories
                .sortedWith(compareBy({ it.category.order }, { it.category.name }))
                .forEachIndexed { index, categoryData ->
                    if (index > 0) append(',')
                    val categoryId = categoryData.category.id
                    val competitors = raceData.competitorData.filter { data ->
                        data.competitorCategory.category?.id == categoryId ||
                            data.competitorCategory.competitor.categoryId == categoryId
                    }
                    append('{')
                    append(""""name":""")
                    appendJsonString(categoryData.category.name)
                    append(""","competitor_count":${competitors.size}""")
                    append(""","result_count":${competitors.count { it.readoutData != null }}""")
                    append('}')
                }
            append("]}")
        }
    }

    fun startsJson(): String {
        val projectFile = projectSupplier()
            ?: return """{"project_open":false,"race_name":"","starts":[]}"""
        val raceData = projectFile.raceData
        val details = EventStartListDetails.from(raceData)

        return buildString {
            append("""{"project_open":true""")
            append(""","race_name":""")
            appendJsonString(raceData.race.name)
            append(""","scheduled_count":${details.scheduledCount}""")
            append(""","unscheduled_count":${details.unscheduledCount}""")
            append(""","starts":[""")
            details.rows.forEachIndexed { index, row ->
                if (index > 0) append(',')
                append('{')
                append(""""start_sequence":""")
                appendJsonString(row.startSequenceText)
                append(""","start_time":""")
                appendJsonString(row.startTimeText)
                append(""","start_number":""")
                appendJsonString(row.startNumberText)
                append(""","competitor":""")
                appendJsonString(row.competitorName)
                append(""","category":""")
                appendJsonString(row.categoryName)
                append(""","si_number":""")
                appendJsonString(row.siNumberText)
                append('}')
            }
            append("]}")
        }
    }

    fun inForestJson(): String {
        val projectFile = projectSupplier()
            ?: return """{"project_open":false,"race_name":"","in_forest":[]}"""
        val raceData = projectFile.raceData
        val details = EventInForestDetails.from(raceData, raceElapsedSeconds(raceData.race.startDateTimeIso))

        return buildString {
            append("""{"project_open":true""")
            append(""","race_name":""")
            appendJsonString(raceData.race.name)
            append(""","in_forest_count":${details.inForestCount}""")
            append(""","finished_count":${details.finishedCount}""")
            append(""","not_started_count":${details.notStartedCount}""")
            append(""","unscheduled_count":${details.unscheduledCount}""")
            append(""","in_forest":[""")
            details.inForestRows.forEachIndexed { index, row ->
                if (index > 0) append(',')
                append('{')
                append(""""competitor":""")
                appendJsonString(row.competitorName)
                append(""","category":""")
                appendJsonString(row.categoryName)
                append(""","start_time":""")
                appendJsonString(row.startTimeText)
                append(""","elapsed":""")
                appendJsonString(row.elapsedText)
                append(""","limit":""")
                appendJsonString(row.limitText)
                append(""","over_limit":${row.overLimit}""")
                append('}')
            }
            append("]}")
        }
    }

    private fun indexHtml(): String {
        val projectFile = projectSupplier()
        val raceName = projectFile?.raceData?.race?.name ?: "No Race File open"
        val results = projectFile?.let { EventResultDetails.from(it.raceData) } ?: emptyList()
        val groupedResults = results.groupBy { it.categoryId to it.categoryName }

        return buildString {
            append("<!doctype html><html><head><meta charset=\"utf-8\">")
            append(autoRefreshMeta)
            append("<title>Radio-Oracle Results</title>")
            append("<style>body{font-family:sans-serif;margin:24px}table{border-collapse:collapse}")
            append("td,th{border-bottom:1px solid #ddd;padding:6px 10px;text-align:left}")
            append("tr.category th{background:#eee;font-size:1.05em}</style>")
            append("</head><body><h1>")
            appendHtml(raceName)
            append("</h1>")
            appendLocalNavigation()
            append("<table><thead><tr><th>Place</th><th>Competitor</th><th>Status</th><th>Points</th><th>Runtime</th></tr></thead><tbody>")
            groupedResults.forEach { (category, categoryResults) ->
                append("<tr class=\"category\"><th colspan=\"5\">")
                appendHtml(category.second)
                append(" (")
                append(categoryResults.size)
                append(")</th></tr>")
                categoryResults.forEach { result ->
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
            }
            append("</tbody></table></body></html>")
        }
    }

    private fun categoriesHtml(): String {
        val projectFile = projectSupplier()
        val raceData = projectFile?.raceData
        val raceName = raceData?.race?.name ?: "No Race File open"

        return buildString {
            append("<!doctype html><html><head><meta charset=\"utf-8\">")
            append(autoRefreshMeta)
            append("<title>Radio-Oracle Categories</title>")
            append("<style>body{font-family:sans-serif;margin:24px}table{border-collapse:collapse}")
            append("td,th{border-bottom:1px solid #ddd;padding:6px 10px;text-align:left}</style>")
            append("</head><body><h1>")
            appendHtml(raceName)
            append("</h1>")
            appendLocalNavigation()
            append("<table><thead><tr><th>Category</th><th>Competitors</th><th>Results</th></tr></thead><tbody>")
            raceData?.competitionCategories(includeResultCategoryIds = false)
                ?.sortedWith(compareBy({ it.category.order }, { it.category.name }))
                ?.forEach { categoryData ->
                    val categoryId = categoryData.category.id
                    val competitors = raceData.competitorData.filter { data ->
                        data.competitorCategory.category?.id == categoryId ||
                            data.competitorCategory.competitor.categoryId == categoryId
                    }
                    append("<tr><td>")
                    appendHtml(categoryData.category.name)
                    append("</td><td>")
                    appendHtml(competitors.size.toString())
                    append("</td><td>")
                    appendHtml(competitors.count { it.readoutData != null }.toString())
                    append("</td></tr>")
                }
            append("</tbody></table></body></html>")
        }
    }

    private fun startsHtml(): String {
        val projectFile = projectSupplier()
        val raceData = projectFile?.raceData
        val raceName = raceData?.race?.name ?: "No Race File open"
        val details = raceData?.let { EventStartListDetails.from(it) }

        return buildString {
            append("<!doctype html><html><head><meta charset=\"utf-8\">")
            append(autoRefreshMeta)
            append("<title>Radio-Oracle Starts</title>")
            append("<style>body{font-family:sans-serif;margin:24px}table{border-collapse:collapse}")
            append("td,th{border-bottom:1px solid #ddd;padding:6px 10px;text-align:left}</style>")
            append("</head><body><h1>")
            appendHtml(raceName)
            append("</h1>")
            appendLocalNavigation()
            append("<p>Scheduled: ")
            appendHtml((details?.scheduledCount ?: 0).toString())
            append(" | Unscheduled: ")
            appendHtml((details?.unscheduledCount ?: 0).toString())
            append("</p><table><thead><tr><th>Start</th><th>Time</th><th>Competitor</th><th>Category</th><th>SI</th></tr></thead><tbody>")
            details?.rows?.forEach { row ->
                append("<tr><td>")
                appendHtml(row.startSequenceText)
                append("</td><td>")
                appendHtml(row.startTimeText)
                append("</td><td>")
                appendHtml(row.competitorName)
                append("</td><td>")
                appendHtml(row.categoryName)
                append("</td><td>")
                appendHtml(row.siNumberText)
                append("</td></tr>")
            }
            append("</tbody></table></body></html>")
        }
    }

    private fun inForestHtml(): String {
        val projectFile = projectSupplier()
        val raceData = projectFile?.raceData
        val raceName = raceData?.race?.name ?: "No Race File open"
        val details = raceData?.let { EventInForestDetails.from(it, raceElapsedSeconds(it.race.startDateTimeIso)) }

        return buildString {
            append("<!doctype html><html><head><meta charset=\"utf-8\">")
            append(autoRefreshMeta)
            append("<title>Radio-Oracle In Forest</title>")
            append("<style>body{font-family:sans-serif;margin:24px}table{border-collapse:collapse}")
            append("td,th{border-bottom:1px solid #ddd;padding:6px 10px;text-align:left}")
            append(".over{color:#b00020;font-weight:bold}</style>")
            append("</head><body><h1>")
            appendHtml(raceName)
            append("</h1>")
            appendLocalNavigation()
            append("<p>In forest: ")
            appendHtml((details?.inForestCount ?: 0).toString())
            append(" | Finished: ")
            appendHtml((details?.finishedCount ?: 0).toString())
            append(" | Not started: ")
            appendHtml((details?.notStartedCount ?: 0).toString())
            append(" | Unscheduled: ")
            appendHtml((details?.unscheduledCount ?: 0).toString())
            append("</p><table><thead><tr><th>Competitor</th><th>Category</th><th>Start</th><th>Elapsed</th><th>Limit</th><th>Status</th></tr></thead><tbody>")
            details?.inForestRows?.forEach { row ->
                append("<tr><td>")
                appendHtml(row.competitorName)
                append("</td><td>")
                appendHtml(row.categoryName)
                append("</td><td>")
                appendHtml(row.startTimeText)
                append("</td><td>")
                appendHtml(row.elapsedText)
                append("</td><td>")
                appendHtml(row.limitText)
                append("</td><td")
                if (row.overLimit) append(" class=\"over\"")
                append(">")
                appendHtml(if (row.overLimit) "Over limit" else "In forest")
                append("</td></tr>")
            }
            append("</tbody></table></body></html>")
        }
    }

    private fun StringBuilder.appendLocalNavigation() {
        append("<nav><a href=\"/\">Results</a> | <a href=\"/categories\">Categories</a> | ")
        append("<a href=\"/starts\">Starts</a> | <a href=\"/in-forest\">In Forest</a></nav>")
        append("<p>JSON: <a href=\"/results.json\">results</a> | ")
        append("<a href=\"/categories.json\">categories</a> | ")
        append("<a href=\"/starts.json\">starts</a> | ")
        append("<a href=\"/in-forest.json\">in forest</a></p>")
    }
}

private fun defaultRaceElapsedSeconds(startDateTimeIso: String): Long =
    runCatching {
        Duration.between(LocalDateTime.parse(startDateTimeIso), LocalDateTime.now()).seconds
    }.getOrDefault(0L)

private fun HttpExchange.handleExactGet(path: String, block: () -> Unit) {
    when {
        requestMethod != "GET" && requestMethod != "HEAD" -> {
            responseHeaders.set("Allow", "GET, HEAD")
            sendText("Method not allowed", "text/plain; charset=utf-8", statusCode = 405)
        }
        requestURI.path != path -> sendText("Not found", "text/plain; charset=utf-8", statusCode = 404)
        else -> block()
    }
}

private fun HttpExchange.sendText(text: String, contentType: String) {
    sendText(text, contentType, statusCode = 200)
}

private fun HttpExchange.sendText(text: String, contentType: String, statusCode: Int) {
    val bytes = text.toByteArray(StandardCharsets.UTF_8)
    responseHeaders.set("Content-Type", contentType)
    responseHeaders.set("Cache-Control", "no-store")
    val responseLength = if (requestMethod == "HEAD") -1L else bytes.size.toLong()
    sendResponseHeaders(statusCode, responseLength)
    responseBody.use { output ->
        if (requestMethod != "HEAD") {
            output.write(bytes)
        }
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
