package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventResultDetails
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.files.FinalResultJsonExports
import org.openardf.radiooracle.shared.files.HtmlResultExports
import org.openardf.radiooracle.shared.files.IofXmlExports
import org.openardf.radiooracle.shared.files.LiveResultJsonExports
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

data class DesktopPublicResultSiteExportPaths(
    val directory: Path,
    val indexHtml: Path,
    val publicResultsJson: Path,
    val finalResultsJson: Path,
    val liveResultsJson: Path,
    val iofResultListXml: Path,
    val printableResultsHtml: Path
)

/** Writes the static public-results site that can be uploaded to Cloudflare Pages. */
object DesktopPublicResultSiteExports {
    fun export(
        directory: Path,
        projectFile: EventProjectFile,
        appVersion: String = "Desktop",
        protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>? = null,
        generatedAt: Instant = Instant.now()
    ): DesktopPublicResultSiteExportPaths {
        val assetsDirectory = directory.resolve("assets")
        val dataDirectory = directory.resolve("data")
        val downloadsDirectory = directory.resolve("downloads")
        listOf(directory, assetsDirectory, dataDirectory, downloadsDirectory).forEach(Files::createDirectories)

        val finalResultsJson = FinalResultJsonExports.results(projectFile.raceData, protectedCourseInfoByCategoryId)
        val liveResultsJson = LiveResultJsonExports.results(projectFile.raceData)
        val iofResultListXml = IofXmlExports.resultList(projectFile.raceData)
        val printableResultsHtml = HtmlResultExports.results(
            raceData = projectFile.raceData,
            appVersion = appVersion,
            protectedCourseInfoByCategoryId = protectedCourseInfoByCategoryId
        )

        val indexPath = directory.resolve("index.html")
        val publicResultsPath = dataDirectory.resolve("public-results.json")
        val finalResultsPath = downloadsDirectory.resolve("final-results.json")
        val liveResultsPath = downloadsDirectory.resolve("live-results.json")
        val iofResultsPath = downloadsDirectory.resolve("iof-result-list.xml")
        val printableResultsPath = downloadsDirectory.resolve("printable-results.html")

        writeText(indexPath, indexHtml(projectFile.raceData.race.name))
        writeText(assetsDirectory.resolve("site.css"), siteCss())
        writeText(assetsDirectory.resolve("site.js"), siteJs())
        writeText(directory.resolve("_headers"), headersText())
        writeText(publicResultsPath, publicResultsJson(projectFile, generatedAt))
        writeText(dataDirectory.resolve("final-results.json"), finalResultsJson)
        writeText(dataDirectory.resolve("live-results.json"), liveResultsJson)
        writeText(finalResultsPath, finalResultsJson)
        writeText(liveResultsPath, liveResultsJson)
        writeText(iofResultsPath, iofResultListXml)
        writeText(printableResultsPath, printableResultsHtml)

        return DesktopPublicResultSiteExportPaths(
            directory = directory,
            indexHtml = indexPath,
            publicResultsJson = publicResultsPath,
            finalResultsJson = finalResultsPath,
            liveResultsJson = liveResultsPath,
            iofResultListXml = iofResultsPath,
            printableResultsHtml = printableResultsPath
        )
    }

    private fun publicResultsJson(projectFile: EventProjectFile, generatedAt: Instant): String {
        val raceData = projectFile.raceData
        val results = EventResultDetails.from(raceData)
        val groupedResults = results.groupBy { it.categoryId.orEmpty() to it.categoryName }
        return buildString {
            append("{\n")
            append("  \"event\": {\n")
            append("    \"name\": ")
            appendJsonString(raceData.race.name)
            append(",\n    \"start\": ")
            appendJsonString(raceData.race.startDateTimeIso)
            append(",\n    \"format\": ")
            appendJsonString(raceData.race.raceType.name)
            append(",\n    \"level\": ")
            appendJsonString(raceData.race.raceLevel.name)
            append("\n  },\n")
            append("  \"generatedAt\": ")
            appendJsonString(generatedAt.toString())
            append(",\n")
            append("  \"resultCount\": ${results.size},\n")
            append("  \"categories\": [\n")
            groupedResults.entries.forEachIndexed { categoryIndex, (category, categoryResults) ->
                if (categoryIndex > 0) append(",\n")
                append("    {\n")
                append("      \"id\": ")
                appendJsonString(category.first)
                append(",\n      \"name\": ")
                appendJsonString(category.second)
                append(",\n      \"results\": [\n")
                categoryResults.forEachIndexed { resultIndex, result ->
                    if (resultIndex > 0) append(",\n")
                    append("        {")
                    append("\"place\": ")
                    appendJsonString(result.placeText.ifBlank { result.statusLabel })
                    append(", \"competitor\": ")
                    appendJsonString(result.competitorName)
                    append(", \"status\": ")
                    appendJsonString(result.statusLabel)
                    append(", \"points\": ")
                    appendJsonString(result.pointsText)
                    append(", \"runtime\": ")
                    appendJsonString(result.runTimeText)
                    append(", \"punches\": ")
                    appendJsonString(result.punchCodesText)
                    append("}")
                }
                append("\n      ]\n")
                append("    }")
            }
            append("\n  ]\n")
            append("}\n")
        }
    }

    private fun indexHtml(eventName: String): String =
        """
        <!doctype html>
        <html lang="en">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>${htmlText(eventName)} Results</title>
          <link rel="stylesheet" href="assets/site.css">
        </head>
        <body>
          <header class="page-header">
            <div>
              <p class="eyebrow">Radio-Oracle public results</p>
              <h1 id="event-name">${htmlText(eventName)}</h1>
              <p id="event-meta" class="summary">Loading results...</p>
            </div>
            <nav class="download-links" aria-label="Downloads">
              <a href="downloads/printable-results.html">Printable HTML</a>
              <a href="downloads/final-results.json">Final JSON</a>
              <a href="downloads/iof-result-list.xml">IOF XML</a>
            </nav>
          </header>

          <main>
            <section class="overview" aria-label="Event summary">
              <div><span>Results</span><strong id="result-count">0</strong></div>
              <div><span>Categories</span><strong id="category-count">0</strong></div>
              <div><span>Published</span><strong id="published-at">Pending</strong></div>
            </section>

            <section class="panel">
              <div class="panel-heading">
                <div>
                  <h2>Results</h2>
                  <p>Category results, points, runtime, status, and punch order.</p>
                </div>
                <input id="result-filter" type="search" placeholder="Filter results" aria-label="Filter results">
              </div>
              <div id="results"></div>
            </section>

            <section class="panel">
              <h2>Route Downloads</h2>
              <p>Course route KML files and route graphics will appear here once they are generated from Course Analyzer exports.</p>
              <div id="route-downloads" class="empty-state">No route downloads in this export.</div>
            </section>
          </main>

          <footer>Generated by Radio-Oracle.</footer>
          <script src="assets/site.js"></script>
        </body>
        </html>
        """.trimIndent() + "\n"

    private fun siteCss(): String =
        """
        :root{--bg:#f4f6f8;--panel:#fff;--text:#111827;--muted:#5f6b7a;--line:#d8dee8;--accent:#1769aa;--accent-strong:#0f4f84}
        *{box-sizing:border-box}
        body{margin:0;background:var(--bg);color:var(--text);font-family:Inter,ui-sans-serif,system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif}
        .page-header{display:flex;align-items:flex-end;justify-content:space-between;gap:24px;padding:36px min(6vw,72px) 28px;background:#fff;border-bottom:1px solid var(--line)}
        .eyebrow{margin:0 0 6px;color:var(--accent-strong);font-size:13px;font-weight:700;text-transform:uppercase}
        h1,h2,h3,p{margin-top:0}h1{margin-bottom:8px;font-size:34px;line-height:1.1}h2{margin-bottom:8px;font-size:20px}h3{margin:20px 0 8px;font-size:17px}
        .summary,.panel p,footer,.empty-state{color:var(--muted)}
        .download-links{display:flex;flex-wrap:wrap;justify-content:flex-end;gap:10px}
        .download-links a{display:inline-flex;align-items:center;min-height:40px;padding:0 14px;border-radius:6px;background:var(--accent);color:#fff;font-weight:700;text-decoration:none;white-space:nowrap}
        main{display:grid;gap:18px;width:min(1180px,calc(100% - 32px));margin:24px auto 40px}
        .overview{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:1px;overflow:hidden;border:1px solid var(--line);border-radius:8px;background:var(--line)}
        .overview>div{min-height:86px;padding:18px;background:var(--panel)}.overview span{display:block;margin-bottom:6px;color:var(--muted);font-size:13px}.overview strong{font-size:20px}
        .panel{padding:20px;border:1px solid var(--line);border-radius:8px;background:var(--panel)}
        .panel-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:18px;margin-bottom:16px}
        input[type=search]{width:min(280px,100%);height:40px;padding:0 12px;border:1px solid var(--line);border-radius:6px;font:inherit}
        table{width:100%;border-collapse:collapse}th,td{padding:9px 8px;border-bottom:1px solid var(--line);text-align:left;vertical-align:top}th{color:var(--muted);font-size:13px;font-weight:700}.number{text-align:right}.punches{color:var(--muted);font-size:13px}
        footer{width:min(1180px,calc(100% - 32px));margin:0 auto 32px;font-size:13px}
        @media (max-width:760px){.page-header,.panel-heading{display:block}.download-links{justify-content:flex-start;margin-top:18px}.overview{grid-template-columns:1fr}input[type=search]{margin-top:12px}}
        """.trimIndent() + "\n"

    private fun siteJs(): String =
        """
        async function loadResults(){const response=await fetch("data/public-results.json",{cache:"no-store"});if(!response.ok){throw new Error(`Unable to load results: ${'$'}{response.status}`)}return response.json()}
        function text(value){return String(value ?? "")}
        function escapeHtml(value){return text(value).replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;").replaceAll('"',"&quot;").replaceAll("'","&#39;")}
        function renderSummary(data){document.getElementById("event-name").textContent=data.event.name;document.getElementById("event-meta").textContent=`${'$'}{data.event.format} | ${'$'}{data.event.level} | Start ${'$'}{data.event.start}`;document.getElementById("result-count").textContent=data.resultCount;document.getElementById("category-count").textContent=data.categories.length;document.getElementById("published-at").textContent=data.generatedAt}
        function renderResults(data,query=""){const normalized=query.trim().toLowerCase();const root=document.getElementById("results");root.innerHTML="";let visible=0;data.categories.forEach(category=>{const rows=category.results.filter(result=>`${'$'}{result.place} ${'$'}{result.competitor} ${'$'}{result.status} ${'$'}{result.points} ${'$'}{result.runtime} ${'$'}{result.punches}`.toLowerCase().includes(normalized));if(rows.length===0)return;visible+=rows.length;const section=document.createElement("section");section.className="category";section.innerHTML=`<h3>${'$'}{escapeHtml(category.name)}</h3><table><thead><tr><th class="number">Place</th><th>Competitor</th><th>Status</th><th class="number">Points</th><th>Runtime</th><th>Punches</th></tr></thead><tbody>${'$'}{rows.map(result=>`<tr><td class="number">${'$'}{escapeHtml(result.place)}</td><td>${'$'}{escapeHtml(result.competitor)}</td><td>${'$'}{escapeHtml(result.status)}</td><td class="number">${'$'}{escapeHtml(result.points)}</td><td>${'$'}{escapeHtml(result.runtime)}</td><td class="punches">${'$'}{escapeHtml(result.punches)}</td></tr>`).join("")}</tbody></table>`;root.appendChild(section)});if(visible===0){root.innerHTML='<div class="empty-state">No matching results.</div>'}}
        loadResults().then(data=>{renderSummary(data);renderResults(data);document.getElementById("result-filter").addEventListener("input",event=>renderResults(data,event.target.value))}).catch(error=>{document.getElementById("results").textContent=error.message})
        """.trimIndent() + "\n"

    private fun headersText(): String =
        """
        /*
          X-Content-Type-Options: nosniff

        /data/*
          Cache-Control: no-store

        /downloads/*
          Cache-Control: public, max-age=300
        """.trimIndent() + "\n"

    private fun writeText(path: Path, text: String) {
        path.parent?.let(Files::createDirectories)
        Files.writeString(path, text, StandardCharsets.UTF_8)
    }

    private fun StringBuilder.appendJsonString(value: String) {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character < ' ') {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
        append('"')
    }

    private fun htmlText(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
}
