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

import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.CompetitorCsvImportDuplicatePolicy
import org.openardf.radiooracle.shared.event.EventProjectEditor
import org.openardf.radiooracle.shared.event.EventProjectFactory
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.StandardCategoryRules
import org.openardf.radiooracle.shared.files.CompetitorCsvImportRow
import org.openardf.radiooracle.shared.files.EventCsvExports
import java.io.StringReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import java.util.prefs.Preferences
import javax.swing.text.MutableAttributeSet
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.parser.ParserDelegator

data class DesktopEventRegGeneratedFile(
    val competitionName: String,
    val path: Path,
    val competitorCount: Int
)

data class DesktopEventRegImportResult(
    val sourceUrl: String,
    val outputDirectory: Path,
    val generatedFiles: List<DesktopEventRegGeneratedFile>
)

data class DesktopEventRegGeneratedCompetitorFile(
    val competitionName: String,
    val path: Path,
    val competitorCount: Int
)

data class DesktopEventRegCompetitorCsvImportResult(
    val sourceUrl: String,
    val outputDirectory: Path,
    val generatedFiles: List<DesktopEventRegGeneratedCompetitorFile>
)

data class DesktopEventRegRegistration(
    val eventName: String,
    val competitions: List<DesktopEventRegCompetition>
)

data class DesktopEventRegCompetition(
    val name: String,
    val competitors: List<DesktopEventRegCompetitor>
)

data class DesktopEventRegCompetitor(
    val firstName: String,
    val lastName: String,
    val club: String,
    val categoryName: String,
    val startTimeText: String?
)

object DesktopEventRegImportPreferences {
    private const val LAST_EVENT_REG_URL_KEY = "lastEventRegUrl"
    private val preferences: Preferences =
        Preferences.userNodeForPackage(DesktopEventRegImportPreferences::class.java)

    fun lastRegistrationUrl(): String =
        preferences.get(LAST_EVENT_REG_URL_KEY, "")

    fun rememberRegistrationUrl(url: String) {
        preferences.put(LAST_EVENT_REG_URL_KEY, url.trim())
    }
}

object DesktopEventRegImporter {
    fun importCompetitorCsvsFromWebsite(
        url: String,
        outputDirectory: Path,
        startDateTimeIso: String,
        fetchHtml: (String) -> String = ::fetchHtml,
        idFactory: () -> String = { UUID.randomUUID().toString() }
    ): DesktopEventRegCompetitorCsvImportResult {
        val normalizedUrl = normalizedUrl(url)
        val registration = DesktopEventRegRegistrationParser.parse(fetchHtml(normalizedUrl))
        val projects = DesktopEventRegProjectBuilder.buildProjects(
            registration = registration,
            startDateTimeIso = startDateTimeIso,
            idFactory = idFactory
        )

        require(projects.isNotEmpty()) {
            "No competition columns with registered competitors were found."
        }

        Files.createDirectories(outputDirectory)
        val generatedFiles = projects.map { generatedProject ->
            val path = uniqueCsvPath(
                outputDirectory.resolve(
                    DesktopProjectFilePaths.defaultCsvFileName(
                        generatedProject.projectFile.raceData.race.name,
                        "competitors"
                    )
                )
            )
            Files.writeString(path, EventCsvExports.competitors(generatedProject.projectFile.raceData), StandardCharsets.UTF_8)
            DesktopEventRegGeneratedCompetitorFile(
                competitionName = generatedProject.competitionName,
                path = path,
                competitorCount = generatedProject.competitorCount
            )
        }

        return DesktopEventRegCompetitorCsvImportResult(
            sourceUrl = normalizedUrl,
            outputDirectory = outputDirectory,
            generatedFiles = generatedFiles
        )
    }

    fun importFromWebsite(
        url: String,
        outputDirectory: Path,
        startDateTimeIso: String,
        fetchHtml: (String) -> String = ::fetchHtml,
        idFactory: () -> String = { UUID.randomUUID().toString() }
    ): DesktopEventRegImportResult {
        val normalizedUrl = normalizedUrl(url)
        val registration = DesktopEventRegRegistrationParser.parse(fetchHtml(normalizedUrl))
        val projects = DesktopEventRegProjectBuilder.buildProjects(
            registration = registration,
            startDateTimeIso = startDateTimeIso,
            idFactory = idFactory
        )

        require(projects.isNotEmpty()) {
            "No competition columns with registered competitors were found."
        }

        Files.createDirectories(outputDirectory)
        val generatedFiles = projects.map { generatedProject ->
            val path = uniqueProjectPath(
                outputDirectory.resolve(
                    DesktopProjectFilePaths.defaultProjectFileName(generatedProject.projectFile.raceData.race.name)
                )
            )
            DesktopProjectFiles.write(path, generatedProject.projectFile)
            DesktopEventRegGeneratedFile(
                competitionName = generatedProject.competitionName,
                path = path,
                competitorCount = generatedProject.competitorCount
            )
        }

        return DesktopEventRegImportResult(
            sourceUrl = normalizedUrl,
            outputDirectory = outputDirectory,
            generatedFiles = generatedFiles
        )
    }

    private fun normalizedUrl(url: String): String {
        val trimmed = url.trim()
        require(trimmed.isNotEmpty()) {
            "Website URL cannot be blank."
        }
        val uri = URI(trimmed)
        require(uri.scheme == "https" || uri.scheme == "http") {
            "Website URL must start with http:// or https://."
        }
        require(!uri.host.isNullOrBlank()) {
            "Website URL must include a host."
        }
        return uri.toString()
    }

    private fun fetchHtml(url: String): String {
        val request = HttpRequest.newBuilder(URI(url))
            .timeout(Duration.ofSeconds(30))
            .header("User-Agent", "Radio-Oracle/${DesktopBuildInfo.displayVersion}")
            .GET()
            .build()
        val response = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
            .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        require(response.statusCode() in 200..299) {
            "Website returned HTTP ${response.statusCode()}."
        }
        return response.body()
    }

    private fun uniqueProjectPath(initialPath: Path): Path {
        val basePath = DesktopProjectFilePaths.withProjectExtension(initialPath)
        val baseStem = basePath.fileName.toString().removeSuffix(DesktopProjectFilePaths.PROJECT_EXTENSION)
        return uniquePath(basePath, baseStem, DesktopProjectFilePaths.PROJECT_EXTENSION)
    }

    private fun uniqueCsvPath(initialPath: Path): Path {
        val basePath = DesktopProjectFilePaths.withCsvExtension(initialPath)
        val baseStem = basePath.fileName.toString().removeSuffix(DesktopProjectFilePaths.CSV_EXTENSION)
        return uniquePath(basePath, baseStem, DesktopProjectFilePaths.CSV_EXTENSION)
    }

    private fun uniquePath(basePath: Path, baseStem: String, extension: String): Path {
        var path = basePath
        var counter = 2
        while (Files.exists(path)) {
            path = basePath.resolveSibling("$baseStem $counter$extension")
            counter++
        }
        return path
    }
}

object DesktopEventRegRegistrationParser {
    fun parse(html: String): DesktopEventRegRegistration {
        val rows = RegListTableParser.parse(html)
        require(rows.size >= 2) {
            "Registration table was not found."
        }

        val headers = rows.first()
        val bodyRows = rows.drop(1)
        val nameIndex = headers.indexOfFirst { it.equals("Name", ignoreCase = true) }
        val clubIndex = headers.indexOfFirst { it.equals("Club", ignoreCase = true) }
        require(nameIndex >= 0) {
            "Registration table is missing a Name column."
        }

        val classColumns = headers
            .mapIndexedNotNull { index, header ->
                val competitionName = header.trim().removeSuffix(" Class")
                if (header.endsWith(" Class") && competitionName.isNotBlank()) {
                    CompetitionColumn(
                        index = index,
                        competitionName = competitionName,
                        startIndex = startColumnIndex(headers, index)
                    )
                } else {
                    null
                }
            }
        require(classColumns.isNotEmpty()) {
            "Registration table has no competition class columns."
        }

        val competitions = classColumns.mapNotNull { column ->
            val competitors = bodyRows.mapNotNull { row ->
                val classValue = row.getOrBlank(column.index)
                val categoryName = categoryName(column.competitionName, classValue)
                if (categoryName.isBlank()) {
                    return@mapNotNull null
                }
                val name = parseName(row.getOrBlank(nameIndex))
                DesktopEventRegCompetitor(
                    firstName = name.first,
                    lastName = name.last,
                    club = if (clubIndex >= 0) row.getOrBlank(clubIndex) else "",
                    categoryName = categoryName,
                    startTimeText = column.startIndex?.let { row.getOrBlank(it) }?.let(::normalizedStartTime)
                )
            }
            if (competitors.isEmpty()) {
                null
            } else {
                DesktopEventRegCompetition(column.competitionName, competitors)
            }
        }

        return DesktopEventRegRegistration(
            eventName = eventNameFromHtml(html),
            competitions = competitions
        )
    }

    private fun startColumnIndex(headers: List<String>, classIndex: Int): Int? {
        val nextClassIndex = headers
            .drop(classIndex + 1)
            .indexOfFirst { it.endsWith(" Class") }
            .let { if (it < 0) headers.size else classIndex + 1 + it }
        return (classIndex + 1 until nextClassIndex)
            .firstOrNull { headers[it].equals("Start", ignoreCase = true) }
    }

    private fun categoryName(competitionName: String, classValue: String): String =
        when (val trimmed = classValue.trim()) {
            "", "-" -> ""
            "Y" -> competitionName
            else -> trimmed
        }

    private fun normalizedStartTime(value: String): String? {
        val trimmed = value.trim()
        return trimmed.takeIf { it.matches(Regex("\\d{1,3}:\\d{2}")) }
    }

    private fun parseName(value: String): ParsedName {
        val trimmed = value.trim()
        require(trimmed.isNotBlank()) {
            "Competitor name cannot be blank."
        }
        val commaIndex = trimmed.indexOf(',')
        if (commaIndex >= 0) {
            return ParsedName(
                first = trimmed.substring(commaIndex + 1).trim(),
                last = trimmed.substring(0, commaIndex).trim()
            )
        }
        val parts = trimmed.split(Regex("\\s+"))
        return if (parts.size == 1) {
            ParsedName(first = "", last = parts.first())
        } else {
            ParsedName(first = parts.dropLast(1).joinToString(" "), last = parts.last())
        }
    }

    private fun eventNameFromHtml(html: String): String {
        val titleMatch = Regex("<title[^>]*>(.*?)</title>", RegexOption.IGNORE_CASE)
            .find(html)
            ?.groupValues
            ?.get(1)
            ?.let(::htmlText)
            ?.substringAfter("|", "")
            ?.removeSuffix("Registration List")
            ?.trim()
        return titleMatch?.takeIf { it.isNotBlank() } ?: "EventReg Registration"
    }

    private fun htmlText(value: String): String =
        value.replace(Regex("<[^>]+>"), " ")
            .replace("&amp;", "&")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun List<String>.getOrBlank(index: Int): String =
        getOrNull(index)?.trim() ?: ""

    private data class CompetitionColumn(
        val index: Int,
        val competitionName: String,
        val startIndex: Int?
    )

    private data class ParsedName(
        val first: String,
        val last: String
    )
}

private data class GeneratedEventRegProject(
    val competitionName: String,
    val competitorCount: Int,
    val projectFile: EventProjectFile
)

private object DesktopEventRegProjectBuilder {
    fun buildProjects(
        registration: DesktopEventRegRegistration,
        startDateTimeIso: String,
        idFactory: () -> String
    ): List<GeneratedEventRegProject> =
        registration.competitions.map { competition ->
            val raceId = idFactory()
            val project = EventProjectFactory.createEmptyProject(
                raceId = raceId,
                raceName = "${registration.eventName} - ${competition.name}",
                startDateTimeIso = startDateTimeIso
            ).withRaceFormat(competition.name)
            val outcome = EventProjectEditor.importCompetitorRowsWithOutcome(
                projectFile = project,
                rows = competition.competitors.map { it.toImportRow() },
                competitorIdFactory = idFactory,
                categoryIdFactory = idFactory,
                duplicatePolicy = CompetitorCsvImportDuplicatePolicy.REJECT_DUPLICATES
            )
            GeneratedEventRegProject(
                competitionName = competition.name,
                competitorCount = competition.competitors.size,
                projectFile = outcome.projectFile
            )
        }

    private fun EventProjectFile.withRaceFormat(competitionName: String): EventProjectFile {
        val format = competitionName.eventRegRaceFormat()
        return copy(
            raceData = raceData.copy(
                race = raceData.race.copy(
                    raceType = format.raceType,
                    raceBand = format.raceBand
                )
            )
        )
    }
}

private data class EventRegRaceFormat(
    val raceType: RaceType,
    val raceBand: RaceBand
)

private fun String.eventRegRaceFormat(): EventRegRaceFormat {
    val lower = lowercase()
    val raceType = when {
        lower.contains("sprint") || lower.startsWith("spr") -> RaceType.SPRINT
        lower.contains("fox") -> RaceType.FOXORING
        else -> RaceType.CLASSIC
    }
    val raceBand = when {
        lower.contains("2m") -> RaceBand.M2
        lower.contains("80m") -> RaceBand.M80
        else -> RaceBand.NONE
    }
    return EventRegRaceFormat(raceType, raceBand)
}

private fun DesktopEventRegCompetitor.toImportRow(): CompetitorCsvImportRow =
    CompetitorCsvImportRow(
        siNumber = null,
        startNumber = null,
        firstName = firstName,
        lastName = lastName,
        categoryName = categoryName,
        isMan = StandardCategoryRules.inferIsManFromName(categoryName)
            ?: categoryName.trim().uppercase().startsWith("M"),
        birthYear = null,
        club = club,
        personId = "",
        startTimeText = startTimeText,
        siRent = false
    )

private class RegListTableParser : HTMLEditorKit.ParserCallback() {
    private val rows = mutableListOf<List<String>>()
    private var inTargetTable = false
    private var tableDepth = 0
    private var inRow = false
    private var inCell = false
    private var currentRow = mutableListOf<String>()
    private val currentCell = StringBuilder()

    override fun handleStartTag(tag: HTML.Tag, attributes: MutableAttributeSet, position: Int) {
        if (tag == HTML.Tag.TABLE) {
            val id = attributes.getAttribute(HTML.Attribute.ID)?.toString()
            if (inTargetTable) {
                tableDepth++
            } else if (id == "reglistTable") {
                inTargetTable = true
                tableDepth = 1
            }
        }
        if (!inTargetTable) {
            return
        }
        when (tag) {
            HTML.Tag.TR -> {
                inRow = true
                currentRow = mutableListOf()
            }
            HTML.Tag.TH,
            HTML.Tag.TD -> {
                inCell = true
                currentCell.clear()
            }
            else -> Unit
        }
    }

    override fun handleEndTag(tag: HTML.Tag, position: Int) {
        if (!inTargetTable) {
            return
        }
        when (tag) {
            HTML.Tag.TH,
            HTML.Tag.TD -> {
                currentRow += normalizeCell(currentCell.toString())
                currentCell.clear()
                inCell = false
            }
            HTML.Tag.TR -> {
                if (inRow && currentRow.any { it.isNotBlank() }) {
                    rows += currentRow
                }
                currentRow = mutableListOf()
                inRow = false
            }
            HTML.Tag.TABLE -> {
                tableDepth--
                if (tableDepth == 0) {
                    inTargetTable = false
                }
            }
            else -> Unit
        }
    }

    override fun handleSimpleTag(tag: HTML.Tag, attributes: MutableAttributeSet, position: Int) {
        if (inTargetTable && inCell && tag == HTML.Tag.BR) {
            currentCell.append(' ')
        }
    }

    override fun handleText(data: CharArray, position: Int) {
        if (inTargetTable && inCell) {
            currentCell.append(data)
        }
    }

    private fun normalizeCell(value: String): String =
        value.replace(Regex("\\s+"), " ").trim()

    companion object {
        fun parse(html: String): List<List<String>> {
            val parser = RegListTableParser()
            ParserDelegator().parse(StringReader(html), parser, true)
            return parser.rows
        }
    }
}
