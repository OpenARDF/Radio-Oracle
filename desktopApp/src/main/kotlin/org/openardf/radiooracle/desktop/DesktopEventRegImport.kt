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
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.CompetitorCsvImportDuplicatePolicy
import org.openardf.radiooracle.shared.event.CompetitorCsvImportOutcome
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventCompetitorData
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
import java.time.LocalDate
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

data class DesktopCompetitorImportDocumentationFile(
    val competitionName: String,
    val kind: String,
    val path: Path,
    val rowCount: Int
)

data class DesktopCompetitorImportDocumentation(
    val outputDirectory: Path,
    val files: List<DesktopCompetitorImportDocumentationFile>
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
    val courseName: String = "",
    val siNumber: Int? = null,
    val startNumber: Int? = null,
    val bibNumber: String = "",
    val callSign: String = "",
    val birthYear: Int? = null,
    val personId: String = "",
    val isMan: Boolean? = null,
    val siRent: Boolean = false,
    val email: String = "",
    val cellPhone: String = "",
    val usaChampEligible: Boolean? = null,
    val region2ChampEligible: Boolean? = null
)

data class DesktopSpreadsheetRegistrationImport(
    val sourceUrl: String,
    val registration: DesktopEventRegRegistration
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
    fun websiteRegistration(
        url: String,
        fetchHtml: (String) -> String = ::fetchHtml
    ): DesktopSpreadsheetRegistrationImport {
        val normalizedUrl = normalizedUrl(url)
        return DesktopSpreadsheetRegistrationImport(
            sourceUrl = normalizedUrl,
            registration = DesktopEventRegRegistrationParser.parse(fetchHtml(normalizedUrl))
        )
    }

    fun spreadsheetRegistration(
        url: String,
        fallbackEventName: String = "Google Sheets Registration",
        fetchSpreadsheet: (String) -> SpreadsheetDownload = ::fetchSpreadsheet
    ): DesktopSpreadsheetRegistrationImport {
        val normalizedUrl = normalizedUrl(url, label = "Spreadsheet URL")
        return DesktopSpreadsheetRegistrationImport(
            sourceUrl = normalizedUrl,
            registration = DesktopEventRegSpreadsheetParser.parse(
                download = fetchSpreadsheet(normalizedUrl),
                fallbackEventName = fallbackEventName
            )
        )
    }

    fun importCompetitorCsvsFromWebsite(
        url: String,
        outputDirectory: Path,
        startDateTimeIso: String,
        fetchHtml: (String) -> String = ::fetchHtml,
        idFactory: () -> String = { UUID.randomUUID().toString() }
    ): DesktopEventRegCompetitorCsvImportResult {
        val import = websiteRegistration(
            url = url,
            fetchHtml = fetchHtml
        )
        val projects = DesktopEventRegProjectBuilder.buildProjects(
            registration = import.registration,
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
            sourceUrl = import.sourceUrl,
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
        val import = spreadsheetRegistration(
            url = url,
            fetchSpreadsheet = fetchSpreadsheet
        )
        val projects = DesktopEventRegProjectBuilder.buildProjects(
            registration = import.registration,
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
            sourceUrl = import.sourceUrl,
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

    fun fetchSpreadsheet(url: String): SpreadsheetDownload {
        val candidateUrls = spreadsheetDownloadCandidateUrls(url)

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

    internal fun spreadsheetDownloadCandidateUrls(url: String): List<String> {
        val googleId = googleSpreadsheetId(url)
        return if (googleId != null) {
            val googleGid = googleSpreadsheetGid(url) ?: "0"
            listOf(
                "https://docs.google.com/spreadsheets/d/$googleId/export?format=xlsx",
                "https://drive.google.com/uc?export=download&id=$googleId",
                "https://docs.google.com/spreadsheets/d/$googleId/export?format=csv&gid=$googleGid"
            )
        } else {
            listOf(url)
        }
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

    private fun googleSpreadsheetGid(url: String): String? {
        val uri = URI(url)
        return sequenceOf(uri.rawQuery, uri.rawFragment)
            .filterNotNull()
            .flatMap { it.split("&").asSequence() }
            .firstOrNull { it.startsWith("gid=") }
            ?.substringAfter("=")
            ?.takeIf { gid -> gid.isNotBlank() && gid.all { it.isDigit() } }
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

data class DesktopSpreadsheetColumnRef(
    val heading: String,
    val occurrence: Int = 0
) {
    val isBlank: Boolean
        get() = heading.isBlank()
}

enum class DesktopSpreadsheetCompetitorField(
    val displayLabel: String,
    val required: Boolean,
    val headingAliases: List<String>
) {
    FIRST_NAME("First name", true, listOf("First", "First Name")),
    LAST_NAME("Last name", true, listOf("Last", "Last Name")),
    CLUB("Club", false, listOf("Club")),
    SI_NUMBER("SI number", false, listOf("E-Punch ID", "SI Card#", "SI", "SI Number")),
    START_NUMBER("Start number", false, listOf("Start Number", "Start #")),
    BIB_NUMBER("Bib number", false, listOf("Bib#", "Bib", "Bib Number")),
    CALL_SIGN("Call sign", false, listOf("Call--Call", "Call", "Call Sign")),
    BIRTH_YEAR("Birth year", false, listOf("YearBorn", "Birth Year", "Year Born")),
    PERSON_ID("Person / confirmation ID", false, listOf("ConfNum", "Confirmation Number", "Person ID")),
    SEX("Sex / gender", false, listOf("Sex", "Gender")),
    SI_RENT("SI rental", false, listOf("RentPunch", "Rent SI?", "SI Rent")),
    EMAIL("Email", false, listOf("email", "e-mail", "email address")),
    CELL_PHONE("Cell phone", false, listOf("cellphone", "cell phone", "mobile", "phone")),
    USA_CHAMP_ELIGIBLE(
        "USA championship eligibility",
        false,
        listOf(
            "National--Champ Eligibility",
            "National Champ Eligibility",
            "National Championship Eligibility",
            "Nat'l Champ Eligibility",
            "USA--Champ Eligibility",
            "USA Champ Eligibility",
            "USA Championship Eligibility",
            "US Champ Eligibility",
            "Elig US Ch?",
            "Elig US Ch",
            "US Championship Eligible"
        )
    ),
    REGION2_CHAMP_ELIGIBLE(
        "Region 2 championship eligibility",
        false,
        listOf(
            "Regional--Champ Eligibility",
            "Regional Champ Eligibility",
            "Regional Championship Eligibility",
            "Reg Champ Eligibility",
            "Region2--Champ Eligibility",
            "Region 2--Champ Eligibility",
            "Region2 Champ Eligibility",
            "Region 2 Champ Eligibility",
            "Region 2 Championship Eligibility",
            "Elig R2 Ch?",
            "Elig R2 Ch",
            "Region 2 Championship Eligible"
        )
    )
}

data class DesktopSpreadsheetCompetitionColumnMapping(
    val competitionName: String,
    val categoryColumn: DesktopSpreadsheetColumnRef? = null,
    val courseColumn: DesktopSpreadsheetColumnRef? = null,
    val startTimeColumn: DesktopSpreadsheetColumnRef? = null
)

data class DesktopSpreadsheetCompetitorColumnMapping(
    val competitorColumns: Map<DesktopSpreadsheetCompetitorField, DesktopSpreadsheetColumnRef> = emptyMap(),
    val competitions: List<DesktopSpreadsheetCompetitionColumnMapping> = emptyList()
) {
    fun column(field: DesktopSpreadsheetCompetitorField): DesktopSpreadsheetColumnRef? =
        competitorColumns[field]

    fun withColumn(
        field: DesktopSpreadsheetCompetitorField,
        column: DesktopSpreadsheetColumnRef?
    ): DesktopSpreadsheetCompetitorColumnMapping =
        copy(
            competitorColumns = if (column == null || column.isBlank) {
                competitorColumns - field
            } else {
                competitorColumns + (field to column)
            }
        )
}

object DesktopEventRegSpreadsheetParser {
    fun parse(download: SpreadsheetDownload, fallbackEventName: String): DesktopEventRegRegistration {
        val rowCandidates = if (download.isXlsx()) {
            XlsxWorkbookReader.readSheets(download.bytes)
        } else {
            String(download.bytes, StandardCharsets.UTF_8)
                .split('\u000c')
                .map(::parseCommaRows)
                .filter { it.isNotEmpty() }
        }
        return parseRows(
            rows = rowCandidates.bestRegistrationRows(),
            eventName = eventNameFromFileName(download.fileName) ?: fallbackEventName
        )
    }

    fun parseCsv(csvText: String, eventName: String): DesktopEventRegRegistration =
        parseRows(
            csvText
                .split('\u000c')
                .map(::parseCommaRows)
                .filter { it.isNotEmpty() }
                .bestRegistrationRows(),
            eventName
        )

    fun parseRows(rows: List<List<String>>, eventName: String): DesktopEventRegRegistration {
        val headerIndex = rows.indexOfFirst { row ->
            row.any { it.normalizedHeader() == "first" } &&
                row.any { it.normalizedHeader() == "last" }
        }
        require(headerIndex >= 0) {
            "Spreadsheet is missing First and Last columns."
        }
        val headers = rows[headerIndex].map { it.trim() }
        return parseMappedRows(
            rows = rows,
            headerRowIndex = headerIndex,
            eventName = eventName,
            mapping = suggestedColumnMapping(headers)
        )
    }

    fun parseMappedRows(
        rows: List<List<String>>,
        headerRowIndex: Int,
        eventName: String,
        mapping: DesktopSpreadsheetCompetitorColumnMapping
    ): DesktopEventRegRegistration {
        require(headerRowIndex in rows.indices) {
            "Select a valid spreadsheet header row."
        }
        val headers = rows[headerRowIndex].map { it.trim() }
        val bodyRows = rows.drop(headerRowIndex + 1).filter { row -> row.any { it.isNotBlank() } }
        val columns = SpreadsheetColumns(headers, mapping)

        val competitionColumns = resolveCompetitionColumns(headers, mapping.competitions)
        require(competitionColumns.isNotEmpty()) {
            "Map at least one competition category/class or course column."
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
                    courseName = spreadsheetCategoryName(
                        competitionName = column.competitionName,
                        classValue = "",
                        courseValue = row.getOrBlank(column.courseIndex)
                    ).ifBlank { categoryName },
                    startTimeText = row.getOrBlank(column.startIndex).let(::normalizedStartTime),
                    siNumber = row.getOrBlank(columns.siNumberIndex).numericText().toIntOrNull(),
                    startNumber = row.getOrBlank(columns.startNumberIndex).numericText().toIntOrNull(),
                    bibNumber = row.getOrBlank(columns.bibNumberIndex).trim(),
                    callSign = row.getOrBlank(columns.callSignIndex).trim(),
                    birthYear = row.getOrBlank(columns.birthYearIndex).birthYear(),
                    personId = row.getOrBlank(columns.personIdIndex).trim(),
                    isMan = row.getOrBlank(columns.sexIndex).sexIsMan(),
                    siRent = row.getOrBlank(columns.siRentIndex).yesLike(),
                    email = row.getOrBlank(columns.emailIndex).trim(),
                    cellPhone = row.getOrBlank(columns.cellPhoneIndex).trim(),
                    usaChampEligible = row.getOrBlank(columns.usaChampEligibilityIndex).yesNoLike(),
                    region2ChampEligible = row.getOrBlank(columns.region2ChampEligibilityIndex).yesNoLike()
                )
            }
            if (competitors.isEmpty()) {
                null
            } else {
                DesktopEventRegCompetition(column.competitionName, competitors)
            }
        }

        return DesktopEventRegRegistration(
            eventName = eventName.ifBlank { "Spreadsheet Registration" },
            competitions = competitions
        )
    }

    fun suggestedColumnMapping(headers: List<String>): DesktopSpreadsheetCompetitorColumnMapping {
        val competitorColumns = DesktopSpreadsheetCompetitorField.entries.mapNotNull { field ->
            headers.columnRefOfFirstHeader(*field.headingAliases.toTypedArray())?.let { field to it }
        }.toMap()
        return DesktopSpreadsheetCompetitorColumnMapping(
            competitorColumns = competitorColumns,
            competitions = suggestedCompetitionColumns(headers)
        )
    }

    private fun suggestedCompetitionColumns(
        headers: List<String>
    ): List<DesktopSpreadsheetCompetitionColumnMapping> {
        val classColumns = headers.mapIndexedNotNull { index, header ->
            val name = header.trim().removeSuffix(" Class")
            if (header.endsWith(" Class") && name.isNotBlank()) {
                DesktopSpreadsheetCompetitionColumnMapping(
                    competitionName = name,
                    categoryColumn = headers.columnRefAt(index),
                    courseColumn = headers.columnRefOfFirstHeader("$name Crs"),
                    startTimeColumn = headers.columnRefOfFirstHeader("$name Start")
                )
            } else {
                null
            }
        }
        val classNames = classColumns.mapTo(mutableSetOf()) { it.competitionName.lowercase() }
        val courseOnlyColumns = headers.mapIndexedNotNull { index, header ->
            val name = header.trim().removeSuffix(" Crs")
            if (header.endsWith(" Crs") && name.isNotBlank() && name.lowercase() !in classNames) {
                DesktopSpreadsheetCompetitionColumnMapping(
                    competitionName = name,
                    courseColumn = headers.columnRefAt(index),
                    startTimeColumn = headers.columnRefOfFirstHeader("$name Start")
                )
            } else {
                null
            }
        }
        val usedNames = (classColumns + courseOnlyColumns)
            .mapTo(mutableSetOf()) { it.competitionName.lowercase() }
        val modifierColumns = headers.mapIndexedNotNull { index, header ->
            val name = modifierCompetitionName(header)
            if (name != null && name.lowercase() !in usedNames) {
                usedNames += name.lowercase()
                DesktopSpreadsheetCompetitionColumnMapping(
                    competitionName = name,
                    courseColumn = headers.columnRefAt(index),
                    startTimeColumn = headers.columnRefOfFirstHeader("$name Start")
                )
            } else {
                null
            }
        }
        return classColumns + courseOnlyColumns + modifierColumns
    }

    private fun resolveCompetitionColumns(
        headers: List<String>,
        mappings: List<DesktopSpreadsheetCompetitionColumnMapping>
    ): List<SpreadsheetCompetitionColumn> =
        mappings.map { mapping ->
            require(mapping.competitionName.isNotBlank()) {
                "Every competition mapping needs a race/competition name."
            }
            val categoryIndex = headers.indexOfColumn(mapping.categoryColumn)
            val courseIndex = headers.indexOfColumn(mapping.courseColumn)
            require(categoryIndex != null || courseIndex != null) {
                "Competition ${mapping.competitionName} needs a mapped category/class or course column."
            }
            SpreadsheetCompetitionColumn(
                competitionName = mapping.competitionName.trim(),
                classIndex = categoryIndex,
                courseIndex = courseIndex,
                startIndex = headers.indexOfColumn(mapping.startTimeColumn)
            )
        }

    private fun modifierCompetitionName(header: String): String? =
        when (header.normalizedHeader()) {
            "sprint mod", "sprint modified", "spr mod", "spr modified" -> "SprMod-NC"
            "fox mod", "fox modified", "foxo mod", "foxo modified" -> "FoxMod-NC"
            "2m mod", "2m modified" -> "2mMod-NC"
            "80m mod", "80m modified" -> "80mMod-NC"
            else -> null
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

    private fun List<List<List<String>>>.bestRegistrationRows(): List<List<String>> {
        require(isNotEmpty()) {
            "Spreadsheet is empty."
        }
        return maxByOrNull { rows -> spreadsheetRowsScore(rows) } ?: first()
    }

    private fun spreadsheetRowsScore(rows: List<List<String>>): Int {
        val headerIndex = rows.indexOfFirst { row ->
            row.any { it.normalizedHeader() == "first" } &&
                row.any { it.normalizedHeader() == "last" }
        }
        if (headerIndex < 0) {
            return -1
        }
        val headers = rows[headerIndex].map { it.trim() }
        val mapping = suggestedColumnMapping(headers)
        val competitionColumns = resolveCompetitionColumns(headers, mapping.competitions)
        if (competitionColumns.isEmpty()) {
            return -1
        }
        val bodyRows = rows.drop(headerIndex + 1).filter { row -> row.any { it.isNotBlank() } }
        val columns = SpreadsheetColumns(headers, mapping)
        val competitorCells = competitionColumns.sumOf { column ->
            bodyRows.count { row ->
                val categoryName = spreadsheetCategoryName(
                    competitionName = column.competitionName,
                    classValue = row.getOrBlank(column.classIndex),
                    courseValue = row.getOrBlank(column.courseIndex)
                )
                categoryName.isNotBlank() &&
                    (row.getOrBlank(columns.firstNameIndex).isNotBlank() ||
                        row.getOrBlank(columns.lastNameIndex).isNotBlank())
            }
        }
        val identityColumnCount = listOf(
            columns.clubIndex,
            columns.siNumberIndex,
            columns.bibNumberIndex,
            columns.callSignIndex,
            columns.birthYearIndex,
            columns.sexIndex,
            columns.personIdIndex,
            columns.emailIndex,
            columns.cellPhoneIndex,
            columns.usaChampEligibilityIndex,
            columns.region2ChampEligibilityIndex
        ).count { it != null }
        val filledIdentityCells = bodyRows.sumOf { row ->
            listOf(
                columns.clubIndex,
                columns.siNumberIndex,
                columns.bibNumberIndex,
                columns.callSignIndex,
                columns.birthYearIndex,
                columns.sexIndex,
                columns.personIdIndex,
                columns.emailIndex,
                columns.cellPhoneIndex
            ).count { index -> row.getOrBlank(index).isNotBlank() }
        }
        return competitorCells * 20 +
            competitionColumns.size * 10 +
            identityColumnCount * 20 +
            filledIdentityCells * 2
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

    private fun List<String>.columnRefOfFirstHeader(vararg candidates: String): DesktopSpreadsheetColumnRef? =
        indexOfFirstHeader(*candidates)?.let { index -> columnRefAt(index) }

    private fun List<String>.columnRefAt(index: Int): DesktopSpreadsheetColumnRef {
        val heading = getOrNull(index).orEmpty().trim()
        val normalized = heading.normalizedHeader()
        val occurrence = take(index).count { it.normalizedHeader() == normalized }
        return DesktopSpreadsheetColumnRef(heading = heading, occurrence = occurrence)
    }

    private fun List<String>.indexOfColumn(column: DesktopSpreadsheetColumnRef?): Int? {
        if (column == null || column.isBlank) {
            return null
        }
        val normalized = column.heading.normalizedHeader()
        return indices
            .filter { index -> getOrNull(index).orEmpty().normalizedHeader() == normalized }
            .getOrNull(column.occurrence)
    }

    private fun List<String>.getOrBlank(index: Int?): String =
        index?.let { getOrNull(it) }?.trim().orEmpty()

    private fun String.normalizedHeader(): String =
        trim().removePrefix("\ufeff").lowercase().replace(Regex("\\s+"), " ")

    private fun String.numericText(): String =
        trim().removeSuffix(".0").filter { it.isDigit() }

    private fun String.birthYear(): Int? {
        val trimmed = trim()
        if (trimmed.isBlank()) {
            return null
        }
        val displayedYear = Regex("(?<!\\d)\\d{4}(?!\\d)")
            .findAll(trimmed)
            .mapNotNull { match -> match.value.toIntOrNull() }
            .firstOrNull { year -> year in MinimumBirthYear..MaximumBirthYear }
        if (displayedYear != null) {
            return displayedYear
        }
        val excelSerial = trimmed.toDoubleOrNull()
            ?.takeIf { serial -> serial in MinimumExcelBirthDateSerial..MaximumExcelBirthDateSerial }
            ?: return null
        return runCatching {
            ExcelDateEpoch.plusDays(excelSerial.toLong()).year
        }.getOrNull()?.takeIf { year -> year in MinimumBirthYear..MaximumBirthYear }
    }

    private val ExcelDateEpoch: LocalDate = LocalDate.of(1899, 12, 30)
    private const val MinimumBirthYear: Int = 1800
    private const val MaximumBirthYear: Int = 2200
    private const val MinimumExcelBirthDateSerial: Double = 1_000.0
    private const val MaximumExcelBirthDateSerial: Double = 120_000.0

    private fun String.sexIsMan(): Boolean? =
        when (trim().uppercase()) {
            "M", "MALE" -> true
            "F", "W", "FEMALE", "WOMAN" -> false
            else -> null
        }

    private fun String.yesLike(): Boolean =
        trim().uppercase() in setOf("Y", "YES", "TRUE", "1")

    private fun String.yesNoLike(): Boolean? =
        when (trim().uppercase()) {
            "" -> null
            "Y", "YES", "TRUE", "1", "ELIGIBLE" -> true
            "N", "NO", "FALSE", "0", "INELIGIBLE", "NOT ELIGIBLE" -> false
            else -> null
        }

    private data class SpreadsheetCompetitionColumn(
        val competitionName: String,
        val classIndex: Int?,
        val courseIndex: Int?,
        val startIndex: Int?
    )

    private class SpreadsheetColumns(
        private val headers: List<String>,
        private val mapping: DesktopSpreadsheetCompetitorColumnMapping
    ) {
        val firstNameIndex = requireNotNull(headers.indexOfColumn(mapping.column(DesktopSpreadsheetCompetitorField.FIRST_NAME))) {
            "Map a valid First name column."
        }
        val lastNameIndex = requireNotNull(headers.indexOfColumn(mapping.column(DesktopSpreadsheetCompetitorField.LAST_NAME))) {
            "Map a valid Last name column."
        }
        val clubIndex = index(DesktopSpreadsheetCompetitorField.CLUB)
        val siNumberIndex = index(DesktopSpreadsheetCompetitorField.SI_NUMBER)
        val bibNumberIndex = index(DesktopSpreadsheetCompetitorField.BIB_NUMBER)
        val callSignIndex = index(DesktopSpreadsheetCompetitorField.CALL_SIGN)
        val birthYearIndex = index(DesktopSpreadsheetCompetitorField.BIRTH_YEAR)
        val sexIndex = index(DesktopSpreadsheetCompetitorField.SEX)
        val personIdIndex = index(DesktopSpreadsheetCompetitorField.PERSON_ID)
        val siRentIndex = index(DesktopSpreadsheetCompetitorField.SI_RENT)
        val startNumberIndex = index(DesktopSpreadsheetCompetitorField.START_NUMBER)
        val emailIndex = index(DesktopSpreadsheetCompetitorField.EMAIL)
        val cellPhoneIndex = index(DesktopSpreadsheetCompetitorField.CELL_PHONE)
        val usaChampEligibilityIndex = index(DesktopSpreadsheetCompetitorField.USA_CHAMP_ELIGIBLE)
        val region2ChampEligibilityIndex = index(DesktopSpreadsheetCompetitorField.REGION2_CHAMP_ELIGIBLE)

        private fun index(field: DesktopSpreadsheetCompetitorField): Int? =
            headers.indexOfColumn(mapping.column(field))
    }
}

data class DesktopSpreadsheetCompetitorImportTarget(
    val targetId: String,
    val displayName: String,
    val path: Path?,
    val projectFile: EventProjectFile,
    val seriesEventId: String? = null,
    val seriesOrder: Int? = null,
    val seriesFormatLabel: String = "",
    val seriesStartDateTimeIso: String = ""
)

data class DesktopSpreadsheetCompetitorImportPlan(
    val sourceUrl: String,
    val eventName: String,
    val mappings: List<DesktopSpreadsheetCompetitorImportMapping>
) {
    val selectedMappings: List<DesktopSpreadsheetCompetitorImportMapping>
        get() = mappings.filter { it.selectedByDefault }

    val competitorCount: Int
        get() = selectedMappings.sumOf { it.competitorCount }
}

data class DesktopSpreadsheetCompetitorImportMapping(
    val competitionName: String,
    val target: DesktopSpreadsheetCompetitorImportTarget?,
    val confidence: Int,
    val reasons: List<String>,
    val warnings: List<String>,
    val rows: List<CompetitorCsvImportRow>,
    val preview: DesktopSpreadsheetCompetitorImportPreview?,
    val selectedByDefault: Boolean
) {
    val competitorCount: Int
        get() = rows.size

    val canOverrideRejection: Boolean
        get() = target != null && confidence >= DesktopSpreadsheetCompetitorImporter.MinimumOverrideConfidence
}

data class DesktopSpreadsheetCompetitorImportPreview(
    val importedCount: Int,
    val updatedCount: Int,
    val deletedCount: Int,
    val createdCategoryNames: List<String>,
    val removableEmptyCategoryNames: List<String>,
    val protectedEmptyCategoryNames: List<String>,
    val warnings: List<String>,
    val addedCompetitors: List<DesktopSpreadsheetCompetitorImportAction> = emptyList(),
    val updatedCompetitors: List<DesktopSpreadsheetCompetitorImportAction> = emptyList(),
    val removedCompetitors: List<DesktopSpreadsheetCompetitorImportAction> = emptyList()
)

data class DesktopSpreadsheetCompetitorImportAction(
    val actionId: String,
    val rowIndex: Int?,
    val competitorId: String?,
    val name: String,
    val categoryName: String,
    val club: String,
    val siNumber: Int?,
    val bibNumber: String,
    val callSign: String,
    val personId: String,
    val fieldChanges: List<String> = emptyList()
)

data class DesktopSpreadsheetCompetitorImportAppliedMapping(
    val competitionName: String,
    val targetDisplayName: String,
    val targetPath: Path?,
    val outcome: CompetitorCsvImportOutcome,
    val removedCategoryNames: List<String>,
    val updatedProjectFile: EventProjectFile
)

object DesktopSpreadsheetCompetitorImporter {
    const val MinimumAutoMapConfidence: Int = 55
    const val MinimumOverrideConfidence: Int = 30

    fun buildPlan(
        url: String,
        targets: List<DesktopSpreadsheetCompetitorImportTarget>,
        fetchSpreadsheet: (String) -> SpreadsheetDownload = DesktopEventRegImporter::fetchSpreadsheet,
        previewIdFactory: () -> String = newPreviewIdFactory()
    ): DesktopSpreadsheetCompetitorImportPlan {
        require(targets.isNotEmpty()) {
            "Open or create a Race File before importing spreadsheet competitors."
        }
        val spreadsheetImport = DesktopEventRegImporter.spreadsheetRegistration(
            url = url,
            fetchSpreadsheet = fetchSpreadsheet
        )
        return buildPlan(
            registrationImport = spreadsheetImport,
            targets = targets,
            previewIdFactory = previewIdFactory
        )
    }

    fun buildPlan(
        registrationImport: DesktopSpreadsheetRegistrationImport,
        targets: List<DesktopSpreadsheetCompetitorImportTarget>,
        previewIdFactory: () -> String = newPreviewIdFactory()
    ): DesktopSpreadsheetCompetitorImportPlan {
        require(targets.isNotEmpty()) {
            "Open or create a Race File before importing competitors."
        }
        val initialMappings = registrationImport.registration.competitions.map { competition ->
            val rows = competition.competitors.map { it.toImportRow() }
            val match = bestTargetMatch(competition, targets)
            val target = match.target?.takeIf { match.confidence >= MinimumOverrideConfidence }
            val reasons = if (targets.size == 1 && target != null && match.reasons.none { it.contains("only open", ignoreCase = true) }) {
                listOf("Only open Race File") + match.reasons
            } else {
                match.reasons
            }
            val selectedByDefault = target != null &&
                match.autoSelectable &&
                match.confidence >= MinimumAutoMapConfidence
            val warnings = buildList {
                if (target == null || !selectedByDefault) {
                    add("No confident Race File match was found.")
                }
                addAll(match.warnings)
            }
            DesktopSpreadsheetCompetitorImportMapping(
                competitionName = competition.name,
                target = target,
                confidence = match.confidence,
                reasons = reasons,
                warnings = warnings,
                rows = rows,
                preview = target?.let {
                    previewImport(
                        projectFile = it.projectFile,
                        rows = rows,
                        idFactory = previewIdFactory
                    )
                },
                selectedByDefault = selectedByDefault
            )
        }
        val mappings = suppressDuplicateDefaultSelections(initialMappings)
        require(mappings.isNotEmpty()) {
            "No competition columns with registered competitors were found."
        }
        return DesktopSpreadsheetCompetitorImportPlan(
            sourceUrl = registrationImport.sourceUrl,
            eventName = registrationImport.registration.eventName,
            mappings = mappings
        )
    }

    fun buildRowsPlan(
        sourceUrl: String,
        eventName: String,
        competitionName: String,
        rows: List<CompetitorCsvImportRow>,
        target: DesktopSpreadsheetCompetitorImportTarget,
        warnings: List<String> = emptyList(),
        previewIdFactory: () -> String = newPreviewIdFactory()
    ): DesktopSpreadsheetCompetitorImportPlan {
        require(rows.isNotEmpty()) {
            "No competitor rows were found."
        }
        val mapping = DesktopSpreadsheetCompetitorImportMapping(
            competitionName = competitionName,
            target = target,
            confidence = 100,
            reasons = listOf("Current Race File target"),
            warnings = warnings,
            rows = rows,
            preview = previewImport(
                projectFile = target.projectFile,
                rows = rows,
                idFactory = previewIdFactory
            ),
            selectedByDefault = true
        )
        return DesktopSpreadsheetCompetitorImportPlan(
            sourceUrl = sourceUrl,
            eventName = eventName,
            mappings = listOf(mapping)
        )
    }

    fun writeDocumentationCsvs(
        plan: DesktopSpreadsheetCompetitorImportPlan,
        outputDirectory: Path,
        idFactory: () -> String = { UUID.randomUUID().toString() }
    ): DesktopCompetitorImportDocumentation {
        Files.createDirectories(outputDirectory)
        val files = plan.mappings.flatMap { mapping ->
            val project = documentationProject(plan.eventName, mapping, idFactory)
            val categoryPath = uniqueDocumentationCsvPath(
                outputDirectory.resolve(
                    DesktopProjectFilePaths.defaultCsvFileName(project.raceData.race.name, "categories")
                )
            )
            Files.writeString(categoryPath, EventCsvExports.categories(project.raceData), StandardCharsets.UTF_8)
            val competitorPath = uniqueDocumentationCsvPath(
                outputDirectory.resolve(
                    DesktopProjectFilePaths.defaultCsvFileName(project.raceData.race.name, "competitors")
                )
            )
            Files.writeString(competitorPath, EventCsvExports.competitors(project.raceData), StandardCharsets.UTF_8)
            listOf(
                DesktopCompetitorImportDocumentationFile(
                    competitionName = mapping.competitionName,
                    kind = "categories",
                    path = categoryPath,
                    rowCount = project.raceData.categories.size
                ),
                DesktopCompetitorImportDocumentationFile(
                    competitionName = mapping.competitionName,
                    kind = "competitors",
                    path = competitorPath,
                    rowCount = mapping.competitorCount
                )
            )
        }
        return DesktopCompetitorImportDocumentation(outputDirectory, files)
    }

    fun applyMapping(
        mapping: DesktopSpreadsheetCompetitorImportMapping,
        competitorIdFactory: () -> String = { UUID.randomUUID().toString() },
        categoryIdFactory: () -> String = { UUID.randomUUID().toString() },
        removeEmptyCategories: Boolean = true,
        removeEmptyCourseCategories: Boolean = false,
        selectedRowIndexes: Set<Int>? = null,
        selectedRemovalCompetitorIds: Set<String>? = null,
        emptyCategoryNamesToRemove: Set<String>? = null,
        emptyCourseCategoryNamesToRemove: Set<String>? = null
    ): DesktopSpreadsheetCompetitorImportAppliedMapping {
        val target = requireNotNull(mapping.target) {
            "No Race File target was selected for ${mapping.competitionName}."
        }
        val selectedRows = selectedRowIndexes?.let { indexes ->
            mapping.rows.filterIndexed { index, _ -> index in indexes }
        } ?: mapping.rows
        val deleteMissingImportKeys = selectedRemovalCompetitorIds?.let { selectedIds ->
            target.projectFile.raceData.competitorData
                .mapNotNull { data ->
                    val competitor = data.competitorCategory.competitor
                    EventProjectEditor.competitorImportKey(competitor).takeIf { competitor.id in selectedIds }
                }
                .toSet()
        }
        val outcome = syncCompetitors(
            projectFile = target.projectFile,
            rows = selectedRows,
            competitorIdFactory = competitorIdFactory,
            categoryIdFactory = categoryIdFactory,
            deleteMissingImportKeys = deleteMissingImportKeys
        )
        val emptyCategoryNames = emptyCategoryNamesToRemove ?: if (removeEmptyCategories) {
            mapping.preview?.removableEmptyCategoryNames.orEmpty().toSet()
        } else {
            emptySet()
        }
        val emptyCourseCategoryNames = emptyCourseCategoryNamesToRemove ?: if (removeEmptyCourseCategories) {
            mapping.preview?.protectedEmptyCategoryNames.orEmpty().toSet()
        } else {
            emptySet()
        }
        var updatedProject = outcome.projectFile
        val removedCategoryNames = mutableListOf<String>()
        (emptyCategoryNames + emptyCourseCategoryNames).forEach { categoryName ->
            val category = updatedProject.raceData.categories
                .firstOrNull { categoryData ->
                    categoryData.category.name == categoryName &&
                        categoryData.competitors.isEmpty() &&
                        (!categoryData.hasCourseData() || categoryName in emptyCourseCategoryNames)
                }
            if (category != null) {
                updatedProject = EventProjectEditor.removeCategory(updatedProject, category.category.id, deleteCompetitors = false)
                removedCategoryNames += category.category.name
            }
        }
        return DesktopSpreadsheetCompetitorImportAppliedMapping(
            competitionName = mapping.competitionName,
            targetDisplayName = target.displayName,
            targetPath = target.path,
            outcome = outcome.copy(projectFile = updatedProject),
            removedCategoryNames = removedCategoryNames,
            updatedProjectFile = updatedProject
        )
    }

    private fun previewImport(
        projectFile: EventProjectFile,
        rows: List<CompetitorCsvImportRow>,
        idFactory: () -> String
    ): DesktopSpreadsheetCompetitorImportPreview {
        val originalCompetitors = projectFile.raceData.competitorData
        val originalById = originalCompetitors.associateBy { it.competitorCategory.competitor.id }
        val rowIndexByImportKey = rows
            .mapIndexed { index, row -> EventProjectEditor.competitorImportKey(row) to index }
            .toMap()
        val existingCategoryNames = projectFile.raceData.categories
            .mapTo(mutableSetOf()) { StandardCategoryRules.normalizedCategoryName(it.category.name) }
        val outcome = syncCompetitors(
            projectFile = projectFile,
            rows = rows,
            competitorIdFactory = idFactory,
            categoryIdFactory = idFactory
        )
        val updatedCompetitors = outcome.projectFile.raceData.competitorData
        val updatedById = updatedCompetitors.associateBy { it.competitorCategory.competitor.id }
        val addedActions = updatedCompetitors
            .filter { it.competitorCategory.competitor.id !in originalById }
            .map { data ->
                val importKey = EventProjectEditor.competitorImportKey(data.competitorCategory.competitor)
                data.toImportAction(
                    actionId = "row-${rowIndexByImportKey[importKey] ?: importKey}",
                    rowIndex = rowIndexByImportKey[importKey],
                    fieldChanges = emptyList()
                )
            }
            .sortedWith(compareBy<DesktopSpreadsheetCompetitorImportAction> { it.categoryName }.thenBy { it.name })
        val updatedActions = updatedCompetitors
            .mapNotNull { data ->
                val original = originalById[data.competitorCategory.competitor.id] ?: return@mapNotNull null
                val changes = original.diffForReview(data)
                if (changes.isEmpty()) {
                    null
                } else {
                    val importKey = EventProjectEditor.competitorImportKey(data.competitorCategory.competitor)
                    data.toImportAction(
                        actionId = "row-${rowIndexByImportKey[importKey] ?: importKey}",
                        rowIndex = rowIndexByImportKey[importKey],
                        fieldChanges = changes
                    )
                }
            }
            .sortedWith(compareBy<DesktopSpreadsheetCompetitorImportAction> { it.categoryName }.thenBy { it.name })
        val removedActions = originalCompetitors
            .filter { it.competitorCategory.competitor.id !in updatedById }
            .map { data ->
                data.toImportAction(
                    actionId = "remove-${data.competitorCategory.competitor.id}",
                    rowIndex = null,
                    fieldChanges = emptyList()
                )
            }
            .sortedWith(compareBy<DesktopSpreadsheetCompetitorImportAction> { it.categoryName }.thenBy { it.name })
        val createdCategoryNames = outcome.projectFile.raceData.categories
            .filter { StandardCategoryRules.normalizedCategoryName(it.category.name) !in existingCategoryNames }
            .map { it.category.name }
            .distinct()
            .sorted()
        val emptyCategories = outcome.projectFile.raceData.categories
            .filter { it.competitors.isEmpty() }
            .sortedBy { it.category.name }
        return DesktopSpreadsheetCompetitorImportPreview(
            importedCount = outcome.importedCount,
            updatedCount = outcome.updatedCount,
            deletedCount = outcome.deletedCount,
            createdCategoryNames = createdCategoryNames,
            removableEmptyCategoryNames = emptyCategories
                .filterNot { it.hasCourseData() }
                .map { it.category.name },
            protectedEmptyCategoryNames = emptyCategories
                .filter { it.hasCourseData() }
                .map { it.category.name },
            warnings = outcome.warnings,
            addedCompetitors = addedActions,
            updatedCompetitors = updatedActions,
            removedCompetitors = removedActions
        )
    }

    private fun documentationProject(
        eventName: String,
        mapping: DesktopSpreadsheetCompetitorImportMapping,
        idFactory: () -> String
    ): EventProjectFile {
        val raceId = idFactory()
        val project = EventProjectFactory.createEmptyProject(
            raceId = raceId,
            raceName = "$eventName - ${mapping.competitionName}",
            startDateTimeIso = "1970-01-01T00:00"
        ).withRaceFormat(mapping.competitionName)
        return syncCompetitors(
            projectFile = project,
            rows = mapping.rows,
            competitorIdFactory = idFactory,
            categoryIdFactory = idFactory
        ).projectFile
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

    private fun uniqueDocumentationCsvPath(initialPath: Path): Path {
        val basePath = DesktopProjectFilePaths.withCsvExtension(initialPath)
        val baseStem = basePath.fileName.toString().removeSuffix(DesktopProjectFilePaths.CSV_EXTENSION)
        var path = basePath
        var counter = 2
        while (Files.exists(path)) {
            path = basePath.resolveSibling("$baseStem $counter${DesktopProjectFilePaths.CSV_EXTENSION}")
            counter++
        }
        return path
    }

    private fun suppressDuplicateDefaultSelections(
        mappings: List<DesktopSpreadsheetCompetitorImportMapping>
    ): List<DesktopSpreadsheetCompetitorImportMapping> {
        val duplicatedTargetIds = mappings
            .filter { it.selectedByDefault }
            .mapNotNull { mapping -> mapping.target?.targetId }
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        if (duplicatedTargetIds.isEmpty()) {
            return mappings
        }
        return mappings.map { mapping ->
            if (mapping.target?.targetId in duplicatedTargetIds && mapping.selectedByDefault) {
                mapping.copy(
                    selectedByDefault = false,
                    warnings = mapping.warnings + "Another spreadsheet competition also maps to ${mapping.target?.displayName}; choose one Race File mapping before importing."
                )
            } else {
                mapping
            }
        }
    }

    private fun syncCompetitors(
        projectFile: EventProjectFile,
        rows: List<CompetitorCsvImportRow>,
        competitorIdFactory: () -> String,
        categoryIdFactory: () -> String,
        deleteMissingImportKeys: Set<String>? = null
    ): CompetitorCsvImportOutcome =
        EventProjectEditor.importCompetitorRowsWithOutcome(
            projectFile = projectFile,
            rows = rows,
            competitorIdFactory = competitorIdFactory,
            categoryIdFactory = categoryIdFactory,
            duplicatePolicy = CompetitorCsvImportDuplicatePolicy.UPDATE_EXISTING_BY_IMPORT_KEY,
            deleteMissingByImportKey = deleteMissingImportKeys == null,
            deleteMissingImportKeys = deleteMissingImportKeys,
            createMissingCategories = true
        )

    private fun EventCompetitorData.toImportAction(
        actionId: String,
        rowIndex: Int?,
        fieldChanges: List<String>
    ): DesktopSpreadsheetCompetitorImportAction {
        val competitor = competitorCategory.competitor
        return DesktopSpreadsheetCompetitorImportAction(
            actionId = actionId,
            rowIndex = rowIndex,
            competitorId = competitor.id,
            name = listOf(competitor.firstName, competitor.lastName)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { "(unnamed competitor)" },
            categoryName = competitorCategory.category?.name.orEmpty(),
            club = competitor.club,
            siNumber = competitor.siNumber,
            bibNumber = competitor.bibNumber,
            callSign = competitor.callSign,
            personId = competitor.index,
            fieldChanges = fieldChanges
        )
    }

    private fun EventCompetitorData.diffForReview(updated: EventCompetitorData): List<String> {
        val originalCompetitor = competitorCategory.competitor
        val updatedCompetitor = updated.competitorCategory.competitor
        return buildList {
            addChange("Name", "${originalCompetitor.firstName} ${originalCompetitor.lastName}".trim(), "${updatedCompetitor.firstName} ${updatedCompetitor.lastName}".trim())
            addChange("Category", competitorCategory.category?.name.orEmpty(), updated.competitorCategory.category?.name.orEmpty())
            addChange("Club", originalCompetitor.club, updatedCompetitor.club)
            addChange("Person ID", originalCompetitor.index, updatedCompetitor.index)
            addChange("Bib", originalCompetitor.bibNumber, updatedCompetitor.bibNumber)
            addChange("Call sign", originalCompetitor.callSign, updatedCompetitor.callSign)
            addChange("Email", originalCompetitor.email, updatedCompetitor.email)
            addChange("Cell", originalCompetitor.cellPhone, updatedCompetitor.cellPhone)
            addChange("National eligibility", originalCompetitor.usaChampEligible?.toString().orEmpty(), updatedCompetitor.usaChampEligible?.toString().orEmpty())
            addChange("Regional eligibility", originalCompetitor.region2ChampEligible?.toString().orEmpty(), updatedCompetitor.region2ChampEligible?.toString().orEmpty())
            addChange("SI", originalCompetitor.siNumber?.toString().orEmpty(), updatedCompetitor.siNumber?.toString().orEmpty())
            addChange("Gender", originalCompetitor.isMan.toString(), updatedCompetitor.isMan.toString())
            addChange("Birth year", originalCompetitor.birthYear?.toString().orEmpty(), updatedCompetitor.birthYear?.toString().orEmpty())
            addChange(
                "Start",
                originalCompetitor.drawnStartTimeSeconds?.toString().orEmpty(),
                updatedCompetitor.drawnStartTimeSeconds?.toString().orEmpty()
            )
            addChange(
                "Start group",
                originalCompetitor.preferredStartGroup?.toString().orEmpty(),
                updatedCompetitor.preferredStartGroup?.toString().orEmpty()
            )
        }
    }

    private fun MutableList<String>.addChange(label: String, before: String, after: String) {
        if (before != after) {
            add("$label: ${before.ifBlank { "(blank)" }} -> ${after.ifBlank { "(blank)" }}")
        }
    }

    private fun bestTargetMatch(
        competition: DesktopEventRegCompetition,
        targets: List<DesktopSpreadsheetCompetitorImportTarget>
    ): TargetMatch {
        val scored = targets
            .map { target -> scoreTarget(competition.name, target) }
            .sortedWith(compareByDescending<TargetMatch> { it.confidence }.thenBy { it.target?.seriesOrder ?: Int.MAX_VALUE })
        val best = scored.firstOrNull() ?: return TargetMatch(null, 0, emptyList(), listOf("No Race Files are available."))
        val second = scored.drop(1).firstOrNull()
        return if (second != null && best.confidence == second.confidence && best.confidence >= MinimumAutoMapConfidence) {
            best.copy(
                autoSelectable = false,
                warnings = best.warnings + "Two Race Files have the same match confidence: ${best.target?.displayName} and ${second.target?.displayName}."
            )
        } else {
            best
        }
    }

    private fun scoreTarget(competitionName: String, target: DesktopSpreadsheetCompetitorImportTarget): TargetMatch {
        val sourceFormat = competitionName.eventRegRaceFormat()
        val targetRace = target.projectFile.raceData.race
        var score = 0
        val reasons = mutableListOf<String>()
        if (sourceFormat.raceType == targetRace.raceType && sourceFormat.raceBand == targetRace.raceBand) {
            score += 70
            reasons += "Same race format (${targetRace.raceType.toDisplayText(targetRace.raceBand)})"
        } else if (sourceFormat.raceType == targetRace.raceType) {
            score += 35
            reasons += "Same race type (${targetRace.raceType.toDisplayText(null)})"
        }
        val sourceTokens = competitionName.matchTokens()
        val targetText = listOf(
            target.displayName,
            target.path?.fileName?.toString().orEmpty(),
            target.projectFile.raceData.race.name,
            target.seriesFormatLabel
        ).joinToString(" ").lowercase()
        val overlap = sourceTokens.count { token -> targetText.contains(token) }
        if (overlap > 0) {
            score += (overlap * 8).coerceAtMost(24)
            reasons += "Name text overlaps"
        }
        val raceLevel = targetRace.raceLevel.name.lowercase()
        if (raceLevel != RaceLevel.PRACTICE.name.lowercase() && competitionName.lowercase().contains(raceLevel)) {
            score += 8
            reasons += "Race level text overlaps"
        }
        if (target.seriesFormatLabel.isNotBlank() && target.seriesFormatLabel.lowercase().matchTokens().any { it in sourceTokens }) {
            score += 10
            reasons += "Series format label matches"
        }
        return TargetMatch(
            target = target,
            confidence = score.coerceAtMost(100),
            reasons = reasons.ifEmpty { listOf("Weak metadata match") },
            warnings = emptyList()
        )
    }

    private fun newPreviewIdFactory(): () -> String {
        var next = 0
        return {
            next += 1
            "preview-$next"
        }
    }

    private data class TargetMatch(
        val target: DesktopSpreadsheetCompetitorImportTarget?,
        val confidence: Int,
        val reasons: List<String>,
        val warnings: List<String>,
        val autoSelectable: Boolean = true
    )
}

private fun EventCategoryData.hasCourseData(): Boolean =
    controlPoints.isNotEmpty() ||
        publicControlIds.isNotEmpty() ||
        category.controlPointsString.isNotBlank() ||
        category.lengthMeters != 0 ||
        category.climbMeters != 0 ||
        category.encryptedIdealOrder?.isNotBlank() == true ||
        category.encryptedCourseInfo?.isNotBlank() == true

private fun String.matchTokens(): Set<String> =
    lowercase()
        .split(Regex("[^a-z0-9]+"))
        .map { token ->
            when (token) {
                "classic" -> ""
                "meter", "meters" -> "m"
                else -> token
            }
        }
        .filter { it.length >= 2 }
        .toSet()

private fun RaceType.toDisplayText(raceBand: RaceBand?): String =
    when (this) {
        RaceType.CLASSIC -> when (raceBand) {
            RaceBand.M80 -> "80m Classic"
            RaceBand.M2 -> "2m Classic"
            else -> "Classic"
        }
        RaceType.SPRINT -> "Sprint"
        RaceType.FOXORING -> "Foxoring"
        else -> name.lowercase().replaceFirstChar { it.titlecase() }
    }

data class DesktopXlsxWorksheet(
    val name: String,
    val rows: List<List<String>>
)

internal object XlsxWorkbookReader {
    fun readRows(bytes: ByteArray): List<List<String>> {
        return readWorksheets(bytes).firstOrNull()?.rows
            ?: error("XLSX workbook is missing worksheets.")
    }

    fun readSheets(bytes: ByteArray): List<List<List<String>>> =
        readWorksheets(bytes).map { it.rows }

    fun readWorksheets(bytes: ByteArray): List<DesktopXlsxWorksheet> {
        val entries = unzip(bytes)
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let(::readSharedStrings).orEmpty()
        val sheetEntries = worksheetEntries(entries).ifEmpty {
            entries.keys
                .filter { it.matches(Regex("xl/worksheets/sheet\\d+\\.xml")) }
                .sortedBy { path -> Regex("\\d+").find(path)?.value?.toIntOrNull() ?: Int.MAX_VALUE }
                .mapIndexed { index, path -> "Sheet ${index + 1}" to path }
        }
        val sheets = sheetEntries.mapNotNull { (sheetName, entryName) ->
            entries[entryName]
                ?.let { readSheetRows(it, sharedStrings) }
                ?.takeIf { it.isNotEmpty() }
                ?.let { rows -> DesktopXlsxWorksheet(sheetName, rows) }
        }
        if (sheets.isEmpty()) {
            error("XLSX workbook is missing worksheets.")
        }
        return sheets
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

    private fun worksheetEntries(entries: Map<String, ByteArray>): List<Pair<String, String>> {
        val workbookXml = entries["xl/workbook.xml"] ?: return emptyList()
        val relationshipXml = entries["xl/_rels/workbook.xml.rels"] ?: return emptyList()
        val targetsById = readWorkbookRelationships(relationshipXml)
        val document = parseXml(workbookXml)
        val sheets = document.documentElement.getElementsByTagNameNS("*", "sheet")
        return (0 until sheets.length).mapNotNull { index ->
            val sheet = sheets.item(index) as Element
            val relationshipId = sheet.getAttributeNS(
                "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
                "id"
            ).ifBlank { sheet.getAttribute("r:id") }
            targetsById[relationshipId]?.let { target ->
                sheet.getAttribute("name").ifBlank { "Sheet ${index + 1}" } to workbookTargetEntryName(target)
            }
        }
    }

    private fun readWorkbookRelationships(bytes: ByteArray): Map<String, String> {
        val document = parseXml(bytes)
        val relationships = document.documentElement.getElementsByTagNameNS("*", "Relationship")
        return (0 until relationships.length).mapNotNull { index ->
            val relationship = relationships.item(index) as Element
            val id = relationship.getAttribute("Id")
            val target = relationship.getAttribute("Target")
            if (id.isNotBlank() && target.contains("worksheets/")) {
                id to target
            } else {
                null
            }
        }.toMap()
    }

    private fun workbookTargetEntryName(target: String): String {
        val normalized = target.removePrefix("/").removePrefix("./")
        return if (normalized.startsWith("xl/")) {
            normalized
        } else {
            "xl/$normalized"
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
                    courseName = categoryName,
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
        courseName = courseName,
        isMan = isMan
            ?: StandardCategoryRules.inferIsManFromName(categoryName)
            ?: categoryName.trim().uppercase().startsWith("M"),
        birthYear = birthYear,
        club = club,
        personId = personId,
        startTimeText = startTimeText,
        siRent = siRent,
        bibNumber = bibNumber,
        callSign = callSign,
        email = email,
        cellPhone = cellPhone,
        usaChampEligible = usaChampEligible,
        region2ChampEligible = region2ChampEligible
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
