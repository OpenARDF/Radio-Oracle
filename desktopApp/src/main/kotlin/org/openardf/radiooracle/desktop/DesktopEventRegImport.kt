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
import java.io.ByteArrayInputStream
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
import java.util.zip.ZipInputStream
import javax.swing.text.MutableAttributeSet
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.parser.ParserDelegator
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

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
    val startTimeText: String?,
    val siNumber: Int? = null,
    val startNumber: Int? = null,
    val bibNumber: String = "",
    val callSign: String = "",
    val birthYear: Int? = null,
    val personId: String = "",
    val isMan: Boolean? = null,
    val siRent: Boolean = false
)

object DesktopEventRegImportPreferences {
    private const val LAST_EVENT_REG_URL_KEY = "lastEventRegUrl"
    private const val LAST_GOOGLE_SHEET_URL_KEY = "lastGoogleSheetUrl"
    private val preferences: Preferences =
        Preferences.userNodeForPackage(DesktopEventRegImportPreferences::class.java)

    fun lastRegistrationUrl(): String =
        preferences.get(LAST_EVENT_REG_URL_KEY, "")

    fun rememberRegistrationUrl(url: String) {
        preferences.put(LAST_EVENT_REG_URL_KEY, url.trim())
    }

    fun lastGoogleSheetUrl(): String =
        preferences.get(LAST_GOOGLE_SHEET_URL_KEY, "")

    fun rememberGoogleSheetUrl(url: String) {
        preferences.put(LAST_GOOGLE_SHEET_URL_KEY, url.trim())
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

    fun importFromGoogleSheet(
        url: String,
        outputDirectory: Path,
        startDateTimeIso: String,
        fetchSpreadsheet: (String) -> SpreadsheetDownload = ::fetchSpreadsheet,
        idFactory: () -> String = { UUID.randomUUID().toString() }
    ): DesktopEventRegImportResult {
        val normalizedUrl = normalizedUrl(url, label = "Spreadsheet URL")
        val registration = DesktopEventRegSpreadsheetParser.parse(
            download = fetchSpreadsheet(normalizedUrl),
            fallbackEventName = "Google Sheets Registration"
        )
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

    private fun normalizedUrl(url: String, label: String = "Website URL"): String {
        val trimmed = url.trim()
        require(trimmed.isNotEmpty()) {
            "$label cannot be blank."
        }
        val uri = URI(trimmed)
        require(uri.scheme == "https" || uri.scheme == "http") {
            "$label must start with http:// or https://."
        }
        require(!uri.host.isNullOrBlank()) {
            "$label must include a host."
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

    private fun fetchSpreadsheet(url: String): SpreadsheetDownload {
        val googleId = googleSpreadsheetId(url)
        val candidateUrls = if (googleId != null) {
            listOf(
                "https://docs.google.com/spreadsheets/d/$googleId/export?format=csv&gid=0",
                "https://docs.google.com/spreadsheets/d/$googleId/export?format=xlsx",
                "https://drive.google.com/uc?export=download&id=$googleId"
            )
        } else {
            listOf(url)
        }

        val errors = mutableListOf<String>()
        candidateUrls.forEach { candidateUrl ->
            runCatching { downloadSpreadsheet(candidateUrl) }
                .onSuccess { download ->
                    if (download.isUsableSpreadsheet()) {
                        return download
                    }
                    errors += "downloaded unsupported content from $candidateUrl"
                }
                .onFailure { error ->
                    errors += error.message ?: error::class.simpleName.orEmpty()
                }
        }

        val suffix = errors.filter { it.isNotBlank() }.distinct().joinToString("; ")
        error(
            if (suffix.isBlank()) {
                "Spreadsheet could not be downloaded as CSV or XLSX."
            } else {
                "Spreadsheet could not be downloaded as CSV or XLSX: $suffix"
            }
        )
    }

    private fun downloadSpreadsheet(url: String): SpreadsheetDownload {
        val request = HttpRequest.newBuilder(URI(url))
            .timeout(Duration.ofSeconds(45))
            .header("User-Agent", "Radio-Oracle/${DesktopBuildInfo.displayVersion}")
            .GET()
            .build()
        val response = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
            .send(request, HttpResponse.BodyHandlers.ofByteArray())
        require(response.statusCode() in 200..299) {
            "Spreadsheet returned HTTP ${response.statusCode()}."
        }
        return SpreadsheetDownload(
            bytes = response.body(),
            contentType = response.headers().firstValue("Content-Type").orElse(""),
            fileName = fileNameFromContentDisposition(
                response.headers().firstValue("Content-Disposition").orElse("")
            )
        )
    }

    private fun googleSpreadsheetId(url: String): String? {
        val uri = URI(url)
        if (!uri.host.orEmpty().contains("google.com", ignoreCase = true)) {
            return null
        }
        return Regex("/spreadsheets/d/([^/?#]+)").find(uri.rawPath)?.groupValues?.get(1)
            ?: uri.rawQuery
                ?.split("&")
                ?.firstOrNull { it.startsWith("id=") }
                ?.substringAfter("=")
    }

    private fun fileNameFromContentDisposition(value: String): String? {
        val utf8 = Regex("filename\\*=UTF-8''([^;]+)", RegexOption.IGNORE_CASE)
            .find(value)
            ?.groupValues
            ?.get(1)
            ?.let { java.net.URLDecoder.decode(it, StandardCharsets.UTF_8) }
        if (!utf8.isNullOrBlank()) {
            return utf8
        }
        return Regex("filename=\"?([^\";]+)\"?", RegexOption.IGNORE_CASE)
            .find(value)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
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

data class SpreadsheetDownload(
    val bytes: ByteArray,
    val contentType: String = "",
    val fileName: String? = null
) {
    fun isXlsx(): Boolean =
        bytes.size >= 4 &&
            bytes[0] == 'P'.code.toByte() &&
            bytes[1] == 'K'.code.toByte()

    fun isCsvLike(): Boolean {
        if (isXlsx()) {
            return false
        }
        val lowerType = contentType.lowercase()
        if (lowerType.contains("csv") || lowerType.startsWith("text/")) {
            return true
        }
        val prefix = bytes.decodeToString(endIndex = minOf(bytes.size, 512)).trimStart()
        return prefix.contains("First,") && prefix.contains("Last,")
    }

    fun isUsableSpreadsheet(): Boolean =
        isXlsx() || isCsvLike()
}

object DesktopEventRegSpreadsheetParser {
    fun parse(download: SpreadsheetDownload, fallbackEventName: String): DesktopEventRegRegistration {
        val rows = if (download.isXlsx()) {
            XlsxFirstSheetReader.readRows(download.bytes)
        } else {
            parseCommaRows(String(download.bytes, StandardCharsets.UTF_8).substringBefore('\u000c'))
        }
        return parseRows(
            rows = rows,
            eventName = eventNameFromFileName(download.fileName) ?: fallbackEventName
        )
    }

    fun parseCsv(csvText: String, eventName: String): DesktopEventRegRegistration =
        parseRows(parseCommaRows(csvText.substringBefore('\u000c')), eventName)

    fun parseRows(rows: List<List<String>>, eventName: String): DesktopEventRegRegistration {
        val headerIndex = rows.indexOfFirst { row ->
            row.any { it.normalizedHeader() == "first" } &&
                row.any { it.normalizedHeader() == "last" }
        }
        require(headerIndex >= 0) {
            "Spreadsheet is missing First and Last columns."
        }
        val headers = rows[headerIndex].map { it.trim() }
        val bodyRows = rows.drop(headerIndex + 1).filter { row -> row.any { it.isNotBlank() } }
        val columns = SpreadsheetColumns(headers)

        val competitionColumns = spreadsheetCompetitionColumns(headers)
        require(competitionColumns.isNotEmpty()) {
            "Spreadsheet has no competition class or course columns."
        }

        val competitions = competitionColumns.mapNotNull { column ->
            val competitors = bodyRows.mapNotNull { row ->
                val categoryName = spreadsheetCategoryName(
                    competitionName = column.competitionName,
                    classValue = row.getOrBlank(column.classIndex),
                    courseValue = row.getOrBlank(column.courseIndex)
                )
                if (categoryName.isBlank()) {
                    return@mapNotNull null
                }
                val firstName = row.getOrBlank(columns.firstNameIndex)
                val lastName = row.getOrBlank(columns.lastNameIndex)
                if (firstName.isBlank() && lastName.isBlank()) {
                    return@mapNotNull null
                }
                DesktopEventRegCompetitor(
                    firstName = firstName,
                    lastName = lastName,
                    club = row.getOrBlank(columns.clubIndex),
                    categoryName = categoryName,
                    startTimeText = row.getOrBlank(column.startIndex).let(::normalizedStartTime),
                    siNumber = row.getOrBlank(columns.siNumberIndex).numericText().toIntOrNull(),
                    startNumber = row.getOrBlank(columns.startNumberIndex).numericText().toIntOrNull(),
                    bibNumber = row.getOrBlank(columns.bibNumberIndex).trim(),
                    callSign = row.getOrBlank(columns.callSignIndex).trim(),
                    birthYear = row.getOrBlank(columns.birthYearIndex).birthYear(),
                    personId = row.getOrBlank(columns.personIdIndex).trim(),
                    isMan = row.getOrBlank(columns.sexIndex).sexIsMan(),
                    siRent = row.getOrBlank(columns.siRentIndex).yesLike()
                )
            }
            if (competitors.isEmpty()) {
                null
            } else {
                DesktopEventRegCompetition(column.competitionName, competitors)
            }
        }

        return DesktopEventRegRegistration(
            eventName = eventName.ifBlank { "Google Sheets Registration" },
            competitions = competitions
        )
    }

    private fun spreadsheetCompetitionColumns(headers: List<String>): List<SpreadsheetCompetitionColumn> {
        val classColumns = headers.mapIndexedNotNull { index, header ->
            val name = header.trim().removeSuffix(" Class")
            if (header.endsWith(" Class") && name.isNotBlank()) {
                SpreadsheetCompetitionColumn(
                    competitionName = name,
                    classIndex = index,
                    courseIndex = headers.indexOfFirstHeader("$name Crs"),
                    startIndex = headers.indexOfFirstHeader("$name Start")
                )
            } else {
                null
            }
        }
        val classNames = classColumns.mapTo(mutableSetOf()) { it.competitionName.lowercase() }
        val courseOnlyColumns = headers.mapIndexedNotNull { index, header ->
            val name = header.trim().removeSuffix(" Crs")
            if (header.endsWith(" Crs") && name.isNotBlank() && name.lowercase() !in classNames) {
                SpreadsheetCompetitionColumn(
                    competitionName = name,
                    classIndex = null,
                    courseIndex = index,
                    startIndex = headers.indexOfFirstHeader("$name Start")
                )
            } else {
                null
            }
        }
        return classColumns + courseOnlyColumns
    }

    private fun spreadsheetCategoryName(competitionName: String, classValue: String, courseValue: String): String {
        val value = classValue.trim().ifBlank { courseValue.trim() }
        return when (value.lowercase()) {
            "", "-", "n", "no", "nc" -> ""
            "y", "comp", "competing" -> competitionName
            else -> value
        }
    }

    private fun parseCommaRows(csvText: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < csvText.length) {
            val ch = csvText[index]
            when {
                ch == '"' && inQuotes && index + 1 < csvText.length && csvText[index + 1] == '"' -> {
                    cell.append('"')
                    index++
                }
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    row += cell.toString().trim()
                    cell.clear()
                }
                (ch == '\n' || ch == '\r') && !inQuotes -> {
                    if (ch == '\r' && index + 1 < csvText.length && csvText[index + 1] == '\n') {
                        index++
                    }
                    row += cell.toString().trim()
                    cell.clear()
                    if (row.any { it.isNotBlank() }) {
                        rows += row.toList()
                    }
                    row.clear()
                }
                else -> cell.append(ch)
            }
            index++
        }
        row += cell.toString().trim()
        if (row.any { it.isNotBlank() }) {
            rows += row.toList()
        }
        return rows
    }

    private fun normalizedStartTime(value: String): String? =
        value.trim().takeIf { it.matches(Regex("\\d{1,3}:\\d{2}")) }

    private fun eventNameFromFileName(fileName: String?): String? =
        fileName
            ?.substringBeforeLast(".")
            ?.replace(Regex("\\s+-\\s+Sheet\\d+$"), "")
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    private fun List<String>.indexOfFirstHeader(vararg candidates: String): Int? {
        val normalizedCandidates = candidates.map { it.normalizedHeader() }.toSet()
        return indexOfFirst { it.normalizedHeader() in normalizedCandidates }.takeIf { it >= 0 }
    }

    private fun List<String>.getOrBlank(index: Int?): String =
        index?.let { getOrNull(it) }?.trim().orEmpty()

    private fun String.normalizedHeader(): String =
        trim().removePrefix("\ufeff").lowercase().replace(Regex("\\s+"), " ")

    private fun String.numericText(): String =
        trim().removeSuffix(".0").filter { it.isDigit() }

    private fun String.birthYear(): Int? {
        val trimmed = trim()
        return Regex("^\\d{4}").find(trimmed)?.value?.toIntOrNull()
    }

    private fun String.sexIsMan(): Boolean? =
        when (trim().uppercase()) {
            "M", "MALE" -> true
            "F", "W", "FEMALE", "WOMAN" -> false
            else -> null
        }

    private fun String.yesLike(): Boolean =
        trim().uppercase() in setOf("Y", "YES", "TRUE", "1")

    private data class SpreadsheetCompetitionColumn(
        val competitionName: String,
        val classIndex: Int?,
        val courseIndex: Int?,
        val startIndex: Int?
    )

    private class SpreadsheetColumns(headers: List<String>) {
        val firstNameIndex = requireNotNull(headers.indexOfFirstHeader("First", "First Name")) {
            "Spreadsheet is missing a First column."
        }
        val lastNameIndex = requireNotNull(headers.indexOfFirstHeader("Last", "Last Name")) {
            "Spreadsheet is missing a Last column."
        }
        val clubIndex = headers.indexOfFirstHeader("Club")
        val siNumberIndex = headers.indexOfFirstHeader("E-Punch ID", "SI Card#", "SI", "SI Number")
        val bibNumberIndex = headers.indexOfFirstHeader("Bib#", "Bib", "Bib Number")
        val callSignIndex = headers.indexOfFirstHeader("Call--Call", "Call", "Call Sign")
        val birthYearIndex = headers.indexOfFirstHeader("YearBorn", "Birth Year", "Year Born")
        val sexIndex = headers.indexOfFirstHeader("Sex", "Gender")
        val personIdIndex = headers.indexOfFirstHeader("ConfNum", "Confirmation Number", "Person ID")
        val siRentIndex = headers.indexOfFirstHeader("RentPunch", "Rent SI?", "SI Rent")
        val startNumberIndex = headers.indexOfFirstHeader("Start Number", "Start #")
    }
}

private object XlsxFirstSheetReader {
    fun readRows(bytes: ByteArray): List<List<String>> {
        val entries = unzip(bytes)
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let(::readSharedStrings).orEmpty()
        val sheetXml = entries["xl/worksheets/sheet1.xml"]
            ?: error("XLSX workbook is missing the first worksheet.")
        return readSheetRows(sheetXml, sharedStrings)
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    entries[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
            }
        }
        return entries
    }

    private fun readSharedStrings(bytes: ByteArray): List<String> {
        val document = parseXml(bytes)
        val nodes = document.documentElement.getElementsByTagNameNS("*", "si")
        return (0 until nodes.length).map { index ->
            val element = nodes.item(index) as Element
            element.getElementsByTagNameNS("*", "t").asElements().joinToString("") { it.textContent }
        }
    }

    private fun readSheetRows(bytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val document = parseXml(bytes)
        val rows = document.documentElement.getElementsByTagNameNS("*", "row")
        return (0 until rows.length).mapNotNull { rowIndex ->
            val rowElement = rows.item(rowIndex) as Element
            val cells = mutableListOf<String>()
            val cellNodes = rowElement.getElementsByTagNameNS("*", "c")
            for (cellIndex in 0 until cellNodes.length) {
                val cell = cellNodes.item(cellIndex) as Element
                val columnIndex = cell.getAttribute("r").takeIf { it.isNotBlank() }?.let(::xlsxColumnIndex)
                    ?: cells.size
                while (cells.size < columnIndex) {
                    cells += ""
                }
                cells += cellValue(cell, sharedStrings)
            }
            cells.takeIf { row -> row.any { it.isNotBlank() } }
        }
    }

    private fun cellValue(cell: Element, sharedStrings: List<String>): String {
        val type = cell.getAttribute("t")
        return when (type) {
            "s" -> cell.firstChildText("v")?.toIntOrNull()?.let { sharedStrings.getOrNull(it) }.orEmpty()
            "inlineStr" -> cell.getElementsByTagNameNS("*", "t").asElements().joinToString("") { it.textContent }
            else -> cell.firstChildText("v").orEmpty()
        }.trim()
    }

    private fun xlsxColumnIndex(cellReference: String): Int {
        val letters = cellReference.takeWhile { it.isLetter() }.uppercase()
        var value = 0
        letters.forEach { char ->
            value = value * 26 + (char - 'A' + 1)
        }
        return (value - 1).coerceAtLeast(0)
    }

    private fun parseXml(bytes: ByteArray) =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }.newDocumentBuilder().parse(ByteArrayInputStream(bytes))

    private fun Element.firstChildText(localName: String): String? {
        val nodes = getElementsByTagNameNS("*", localName)
        return if (nodes.length == 0) null else nodes.item(0).textContent
    }

    private fun org.w3c.dom.NodeList.asElements(): List<Element> =
        (0 until length).mapNotNull { item(it) as? Element }
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
        siNumber = siNumber,
        startNumber = startNumber,
        firstName = firstName,
        lastName = lastName,
        categoryName = categoryName,
        isMan = isMan
            ?: StandardCategoryRules.inferIsManFromName(categoryName)
            ?: categoryName.trim().uppercase().startsWith("M"),
        birthYear = birthYear,
        club = club,
        personId = personId,
        startTimeText = startTimeText,
        siRent = siRent,
        bibNumber = bibNumber,
        callSign = callSign
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
