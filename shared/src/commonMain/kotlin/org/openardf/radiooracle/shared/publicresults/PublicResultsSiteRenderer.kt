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

package org.openardf.radiooracle.shared.publicresults

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.openardf.radiooracle.shared.event.EventAwardDisplayMode
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventResultDetails
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.event.supportsChampionshipAwards
import org.openardf.radiooracle.shared.files.FinalResultJsonExports
import org.openardf.radiooracle.shared.files.HtmlResultExports
import org.openardf.radiooracle.shared.files.IofXmlExports
import org.openardf.radiooracle.shared.files.LiveResultJsonExports
import java.nio.charset.StandardCharsets

data class PublicResultsRaceRenderRequest(
    val projectFile: EventProjectFile,
    val protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo> = emptyMap(),
    val awardDisplayMode: EventAwardDisplayMode = EventAwardDisplayMode.FIRST_TO_THIRD
)

data class RenderedPublicResultsRace(
    val path: String,
    val name: String,
    val start: String,
    val resultCount: Int,
    val unofficialResults: Boolean,
    val publicationId: String,
    val files: Map<String, ByteArray>,
    val courseGraphics: List<String>
) {
    fun catalogEntry(generatedAtIso: String): PublishedPublicResultsEntry =
        PublishedPublicResultsEntry(
            path = path,
            name = name,
            start = start,
            generatedAt = generatedAtIso,
            resultCount = resultCount,
            unofficialResults = unofficialResults,
            publicationId = publicationId
        )
}

data class RenderedPublicResultsSeries(
    val path: String,
    val name: String,
    val start: String,
    val resultCount: Int,
    val unofficialResults: Boolean,
    val publicationId: String,
    val files: Map<String, ByteArray>,
    val races: List<RenderedPublicResultsRace>
) {
    fun catalogEntry(generatedAtIso: String): PublishedPublicResultsEntry =
        PublishedPublicResultsEntry(
            path = path,
            name = name,
            start = start,
            generatedAt = generatedAtIso,
            resultCount = resultCount,
            unofficialResults = unofficialResults,
            publicationId = publicationId
        )
}

/** Shared, filesystem-neutral static-site renderer. */
object PublicResultsSiteRenderer {
    private val json = Json {
        encodeDefaults = true
        prettyPrint = true
    }

    fun renderRace(
        request: PublicResultsRaceRenderRequest,
        generatedAtIso: String,
        appVersion: String
    ): RenderedPublicResultsRace {
        val raceData = request.projectFile.raceData
        val path = PublicResultsSiteCatalog.eventPath(
            eventName = raceData.race.name,
            startDateTimeIso = raceData.race.startDateTimeIso,
            generatedDate = generatedAtIso
        )
        val results = EventResultDetails.from(raceData)
        val unofficial = raceData.race.supportsChampionshipAwards()
        val graphics = courseGraphics(request, results)
        val finalJson = FinalResultJsonExports.results(
            raceData = raceData,
            protectedCourseInfoByCategoryId =
                request.protectedCourseInfoByCategoryId.takeIf { it.isNotEmpty() },
            awardDisplayMode = request.awardDisplayMode
        )
        val printableHtml = HtmlResultExports.results(
            raceData = raceData,
            appVersion = appVersion,
            protectedCourseInfoByCategoryId =
                request.protectedCourseInfoByCategoryId.takeIf { it.isNotEmpty() },
            awardDisplayMode = request.awardDisplayMode
        )
        val pageHtml = eventPageHtml(
            printableHtml = printableHtml,
            eventName = raceData.race.name,
            start = raceData.race.startDateTimeIso,
            generatedAtIso = generatedAtIso,
            resultCount = results.size,
            unofficialResults = unofficial,
            courseGraphics = graphics
        )
        val summary = PublicResultsEventSummary(
            path = path,
            name = raceData.race.name,
            start = raceData.race.startDateTimeIso,
            generatedAt = generatedAtIso,
            resultCount = results.size,
            unofficialResults = unofficial,
            publicationId = "race:${raceData.race.id}",
            courseGraphics = graphics.map { "course-graphics/${it.fileName}" }
        )
        val files = buildMap<String, ByteArray> {
            putText("index.html", pageHtml)
            putText("data/event-summary.json", json.encodeToString(summary) + "\n")
            putText("data/public-results.json", finalJson)
            graphics.forEach { graphic ->
                putText("course-graphics/${graphic.fileName}", graphic.svg)
            }
            if (results.isNotEmpty()) {
                val liveJson = LiveResultJsonExports.results(raceData)
                val iofXml = IofXmlExports.resultList(raceData)
                putText("data/final-results.json", finalJson)
                putText("data/live-results.json", liveJson)
                putText("downloads/final-results.json", finalJson)
                putText("downloads/live-results.json", liveJson)
                putText("downloads/iof-result-list.xml", iofXml)
                putText("downloads/printable-results.html", printableHtml)
            }
        }
        return RenderedPublicResultsRace(
            path = path,
            name = raceData.race.name,
            start = raceData.race.startDateTimeIso,
            resultCount = results.size,
            unofficialResults = unofficial,
            publicationId = "race:${raceData.race.id}",
            files = files,
            courseGraphics = graphics.map { "course-graphics/${it.fileName}" }
        )
    }

    fun renderSeries(
        seriesId: String,
        seriesName: String,
        races: List<RenderedPublicResultsRace>,
        generatedAtIso: String
    ): RenderedPublicResultsSeries {
        require(races.isNotEmpty()) {
            "The Race Series does not contain any races to publish."
        }
        val path = PublicResultsSiteCatalog.seriesPath(
            seriesName = seriesName,
            firstStartDateTimeIso = races.first().start,
            generatedDate = generatedAtIso
        )
        val unofficial = races.any(RenderedPublicResultsRace::unofficialResults)
        val document = PublicResultsSeriesDocument(
            seriesName = seriesName,
            generatedAt = generatedAtIso,
            unofficialResults = unofficial,
            races = races.map { race ->
                PublicResultsSeriesRace(
                    name = race.name,
                    start = race.start,
                    resultCount = race.resultCount,
                    unofficialResults = race.unofficialResults,
                    dataUrl = "../${race.path}/data/public-results.json",
                    downloadsUrl = "../${race.path}/downloads/",
                    eventUrl = "../${race.path}/",
                    courseGraphics = race.courseGraphics.map { "../${race.path}/$it" }
                )
            }
        )
        val files = mapOf(
            "index.html" to seriesPageHtml(seriesName, generatedAtIso, unofficial, races)
                .toByteArray(StandardCharsets.UTF_8),
            "data/series-results.json" to (json.encodeToString(document) + "\n")
                .toByteArray(StandardCharsets.UTF_8)
        )
        return RenderedPublicResultsSeries(
            path = path,
            name = seriesName,
            start = races.minOf(RenderedPublicResultsRace::start),
            resultCount = races.sumOf(RenderedPublicResultsRace::resultCount),
            unofficialResults = unofficial,
            publicationId = "series:$seriesId",
            files = files,
            races = races
        )
    }

    fun headersText(): String =
        """
        /*
          X-Content-Type-Options: nosniff
          Referrer-Policy: strict-origin-when-cross-origin
          X-Frame-Options: SAMEORIGIN
          Cache-Control: no-cache
        """.trimIndent() + "\n"

    private fun courseGraphics(
        request: PublicResultsRaceRenderRequest,
        results: List<EventResultDetails>
    ): List<RenderedCourseGraphic> {
        val courseRequests = results.mapNotNull { result ->
            val categoryId = result.categoryId ?: return@mapNotNull null
            val courseInfo = request.protectedCourseInfoByCategoryId[categoryId]
                ?: return@mapNotNull null
            Triple(categoryId, result.categoryName, courseInfo)
        }.distinctBy { it.first }
        return courseRequests
            .groupBy { it.third }
            .mapNotNull { (courseInfo, categories) ->
                val first = categories.first()
                val title = "${categories.map { it.second }.distinct().joinToString(", ")} course"
                runCatching {
                    RenderedCourseGraphic(
                        fileName = "course-${
                            PublicResultsSiteCatalog.safePathSegment(first.first, "course")
                        }.svg",
                        title = title,
                        svg = CourseDiagramSvg.render(title, courseInfo)
                    )
                }.getOrNull()
            }
    }

    private fun eventPageHtml(
        printableHtml: String,
        eventName: String,
        start: String,
        generatedAtIso: String,
        resultCount: Int,
        unofficialResults: Boolean,
        courseGraphics: List<RenderedCourseGraphic>
    ): String {
        val resultsLabel = PublicResultsSiteCatalog.publicResultsLabel(unofficialResults)
        val parentLabel = if (unofficialResults) {
            "All published unofficial results"
        } else {
            "All published results"
        }
        if (resultCount == 0) {
            val comingSoon = PublicResultsSiteCatalog.comingSoonResultsLabel(unofficialResults)
            val description = if (unofficialResults) {
                "This page is ready for the event. Return after the race begins for unofficial results."
            } else {
                "This page is ready for the event. Return after the race begins for results."
            }
            return """
            <!doctype html>
            <html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>${html(eventName)} $comingSoon</title>${pageStyle()}</head>
            <body><main class="public-shell">
              <p class="eyebrow">${html(resultsLabel)}</p>
              <h1>${html(eventName)}</h1>
              <p class="meta">${html(start)}</p>
              <a href="../">$parentLabel</a>
              <section class="notice coming-soon">
                <h2>$comingSoon</h2>
                <p>$description</p>
              </section>
              <p class="generated">Page generated ${html(generatedAtIso)} by Radio-Oracle.</p>
            </main></body></html>
            """.trimIndent() + "\n"
        }

        val navigation = """
        <div class="public-nav">
          <p class="eyebrow">${html(resultsLabel)}</p>
          <a href="../">$parentLabel</a>
          <span>${html(resultCount.toString())} published results</span>
          <span>Updated ${html(generatedAtIso)}</span>
          <div class="downloads">
            <a href="downloads/printable-results.html">Printable HTML</a>
            <a href="downloads/final-results.json">Final JSON</a>
            <a href="downloads/live-results.json">Live JSON</a>
            <a href="downloads/iof-result-list.xml">IOF XML</a>
          </div>
        </div>
        """.trimIndent()
        val diagrams = if (courseGraphics.isEmpty()) {
            ""
        } else {
            """
            <section class="course-diagrams">
              <h2>2D Course Diagrams</h2>
              ${courseGraphics.joinToString("\n") { graphic ->
                    """<figure><img src="course-graphics/${html(graphic.fileName)}" alt="${html(graphic.title)}"><figcaption>${html(graphic.title)}</figcaption></figure>"""
                }}
            </section>
            """.trimIndent()
        }
        return printableHtml
            .replace("</style>", "${embeddedPageStyle()}</style>")
            .replace("<body>", "<body>$navigation")
            .replace("</body>", "$diagrams</body>")
    }

    private fun seriesPageHtml(
        seriesName: String,
        generatedAtIso: String,
        unofficialResults: Boolean,
        races: List<RenderedPublicResultsRace>
    ): String {
        val resultsLabel = PublicResultsSiteCatalog.publicResultsLabel(unofficialResults)
        val parentLabel = if (unofficialResults) {
            "All published unofficial results"
        } else {
            "All published results"
        }
        val raceCards = races.joinToString("\n") { race ->
            val status = if (race.resultCount == 0) {
                PublicResultsSiteCatalog.comingSoonResultsLabel(race.unofficialResults)
            } else {
                "${race.resultCount} ${PublicResultsSiteCatalog.publicResultsLabel(race.unofficialResults).lowercase()}"
            }
            val diagrams = race.courseGraphics.joinToString("\n") { graphic ->
                """<a class="diagram-link" href="../${html(race.path)}/$graphic"><img src="../${html(race.path)}/$graphic" alt="${html(race.name)} course diagram"></a>"""
            }
            """
            <article class="race-card">
              <h2><a href="../${html(race.path)}/">${html(race.name)}</a></h2>
              <p>${html(race.start)} · $status</p>
              $diagrams
            </article>
            """.trimIndent()
        }
        return """
        <!doctype html>
        <html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>${html(seriesName)} $resultsLabel</title>${pageStyle()}</head>
        <body><main class="public-shell">
          <p class="eyebrow">Radio-Oracle Race Series $resultsLabel</p>
          <h1>${html(seriesName)}</h1>
          <p class="meta">Updated ${html(generatedAtIso)}</p>
          <a href="../">$parentLabel</a>
          <section class="series-races">$raceCards</section>
          <p class="generated">${if (unofficialResults) "Unofficial results" else "Results"} generated by Radio-Oracle.</p>
        </main></body></html>
        """.trimIndent() + "\n"
    }

    private fun pageStyle(): String =
        """
        <style>
          *{box-sizing:border-box}body{margin:0;background:#f4f6f8;color:#111827;font-family:Arial,sans-serif}
          .public-shell{width:min(1000px,calc(100% - 32px));margin:28px auto;padding:24px;background:#fff;border:1px solid #d8dee8;border-radius:10px}
          h1{margin:.2rem 0}.eyebrow{font-weight:700;text-transform:uppercase;letter-spacing:.08em;color:#1769aa}.meta,.generated{color:#5f6b7a}
          a{color:#1769aa}.notice,.race-card{margin:24px 0;padding:18px;border:1px solid #d8dee8;border-radius:8px}.coming-soon{background:#fff8e6;border-color:#e4b64d}
          .series-races{display:grid;gap:16px;margin-top:24px}.race-card h2{margin-top:0}.diagram-link img{display:block;width:min(100%,560px);height:auto;margin-top:12px;border:1px solid #d8dee8}
        </style>
        """.trimIndent()

    private fun embeddedPageStyle(): String =
        """
        .public-nav{margin:0 0 24px;padding:14px 16px;border:1px solid #d8dee8;background:#f8fafc}
        .public-nav>*{margin-right:16px}.public-nav .eyebrow{font-weight:700;text-transform:uppercase;color:#1769aa}
        .downloads{display:flex;gap:12px;flex-wrap:wrap;margin-top:10px}
        .course-diagrams{margin-top:32px}.course-diagrams figure{margin:16px 0}.course-diagrams img{display:block;width:min(100%,1000px);height:auto;border:1px solid #d8dee8}.course-diagrams figcaption{margin-top:6px;font-weight:700}
        """

    private fun html(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

    private fun MutableMap<String, ByteArray>.putText(path: String, text: String) {
        put(path, text.toByteArray(StandardCharsets.UTF_8))
    }

    private data class RenderedCourseGraphic(
        val fileName: String,
        val title: String,
        val svg: String
    )
}

@Serializable
private data class PublicResultsEventSummary(
    val path: String,
    val name: String,
    val start: String,
    val generatedAt: String,
    val resultCount: Int,
    val unofficialResults: Boolean,
    val publicationId: String,
    val courseGraphics: List<String>
)

@Serializable
private data class PublicResultsSeriesDocument(
    val seriesName: String,
    val generatedAt: String,
    val unofficialResults: Boolean,
    val races: List<PublicResultsSeriesRace>
)

@Serializable
private data class PublicResultsSeriesRace(
    val name: String,
    val start: String,
    val resultCount: Int,
    val unofficialResults: Boolean,
    val dataUrl: String,
    val downloadsUrl: String,
    val eventUrl: String,
    val courseGraphics: List<String>
)
