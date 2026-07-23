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

import java.nio.file.Files
import java.nio.file.Path
import java.util.prefs.Preferences
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class DesktopCompetitorSpreadsheetImportProfile(
    val sheetName: String,
    val headerRowIndex: Int,
    val mapping: DesktopSpreadsheetCompetitorColumnMapping
)

data class DesktopSpreadsheetColumnOption(
    val columnIndex: Int,
    val reference: DesktopSpreadsheetColumnRef
) {
    val displayLabel: String
        get() = "${reference.heading} (${spreadsheetColumnLetters(columnIndex)})"
}

data class DesktopCompetitorSpreadsheetImportDraft(
    val path: Path,
    val worksheets: List<DesktopXlsxWorksheet>,
    val selectedSheetName: String,
    val headerRowIndex: Int,
    val mapping: DesktopSpreadsheetCompetitorColumnMapping,
    val rememberedProfile: DesktopCompetitorSpreadsheetImportProfile? = null
) {
    val selectedWorksheet: DesktopXlsxWorksheet
        get() = worksheets.firstOrNull { it.name == selectedSheetName } ?: worksheets.first()

    val headers: List<String>
        get() = selectedWorksheet.rows.getOrNull(headerRowIndex).orEmpty().map(String::trim)

    val columnOptions: List<DesktopSpreadsheetColumnOption>
        get() {
            val occurrenceByHeading = mutableMapOf<String, Int>()
            return headers.mapIndexedNotNull { index, heading ->
                val trimmed = heading.trim()
                if (trimmed.isBlank()) {
                    return@mapIndexedNotNull null
                }
                val normalized = trimmed.normalizedSpreadsheetHeading()
                val occurrence = occurrenceByHeading.getOrDefault(normalized, 0)
                occurrenceByHeading[normalized] = occurrence + 1
                DesktopSpreadsheetColumnOption(
                    columnIndex = index,
                    reference = DesktopSpreadsheetColumnRef(trimmed, occurrence)
                )
            }
        }

    val validationErrors: List<String>
        get() = buildList {
            DesktopSpreadsheetCompetitorField.entries.forEach { field ->
                val reference = mapping.column(field)
                when {
                    field.required && reference == null ->
                        add("${field.displayLabel} must be mapped.")
                    reference != null && !headers.containsReference(reference) ->
                        add("${field.displayLabel} heading “${reference.heading}” is not present in the selected header row.")
                }
            }
            if (
                mapping.column(DesktopSpreadsheetCompetitorField.FIRST_NAME) != null &&
                mapping.column(DesktopSpreadsheetCompetitorField.FIRST_NAME) ==
                mapping.column(DesktopSpreadsheetCompetitorField.LAST_NAME)
            ) {
                add("First name and Last name must use different columns.")
            }
            if (mapping.competitions.isEmpty()) {
                add("Add at least one race/competition mapping.")
            }
            val duplicateNames = mapping.competitions
                .map { it.competitionName.trim().lowercase() }
                .filter { it.isNotBlank() }
                .groupingBy { it }
                .eachCount()
                .filterValues { it > 1 }
                .keys
            mapping.competitions.forEachIndexed { index, competition ->
                val label = competition.competitionName.trim().ifBlank { "Competition ${index + 1}" }
                if (competition.competitionName.isBlank()) {
                    add("Competition ${index + 1} needs a race/competition name.")
                }
                if (competition.categoryColumn == null && competition.courseColumn == null) {
                    add("$label needs a Category/class or Course column.")
                }
                listOf(
                    "Category/class" to competition.categoryColumn,
                    "Course" to competition.courseColumn,
                    "Start time" to competition.startTimeColumn
                ).forEach { (fieldLabel, reference) ->
                    if (reference != null && !headers.containsReference(reference)) {
                        add("$label $fieldLabel heading “${reference.heading}” is not present in the selected header row.")
                    }
                }
            }
            if (duplicateNames.isNotEmpty()) {
                add("Race/competition names must be unique.")
            }
            if (selectedWorksheet.rows.drop(headerRowIndex + 1).none { row -> row.any { it.isNotBlank() } }) {
                add("The selected header row has no data rows below it.")
            }
        }.distinct()

    val canImport: Boolean
        get() = validationErrors.isEmpty()

    fun withSheet(sheetName: String): DesktopCompetitorSpreadsheetImportDraft {
        val worksheet = worksheets.firstOrNull { it.name == sheetName } ?: return this
        val newHeaderRowIndex = preferredHeaderRowIndex(worksheet, rememberedProfile)
        val newHeaders = worksheet.rows.getOrNull(newHeaderRowIndex).orEmpty()
        return copy(
            selectedSheetName = worksheet.name,
            headerRowIndex = newHeaderRowIndex,
            mapping = preferredMapping(worksheet.name, newHeaders, rememberedProfile)
        )
    }

    fun withHeaderRow(index: Int): DesktopCompetitorSpreadsheetImportDraft {
        if (index !in selectedWorksheet.rows.indices) {
            return this
        }
        val newHeaders = selectedWorksheet.rows[index]
        return copy(
            headerRowIndex = index,
            mapping = preferredMapping(selectedSheetName, newHeaders, rememberedProfile)
        )
    }

    fun withCompetitorColumn(
        field: DesktopSpreadsheetCompetitorField,
        reference: DesktopSpreadsheetColumnRef?
    ): DesktopCompetitorSpreadsheetImportDraft =
        copy(mapping = mapping.withColumn(field, reference))

    fun withCompetition(
        index: Int,
        competition: DesktopSpreadsheetCompetitionColumnMapping
    ): DesktopCompetitorSpreadsheetImportDraft =
        copy(
            mapping = mapping.copy(
                competitions = mapping.competitions.mapIndexed { itemIndex, item ->
                    if (itemIndex == index) competition else item
                }
            )
        )

    fun addCompetition(): DesktopCompetitorSpreadsheetImportDraft =
        copy(
            mapping = mapping.copy(
                competitions = mapping.competitions + DesktopSpreadsheetCompetitionColumnMapping(
                    competitionName = "Competition ${mapping.competitions.size + 1}"
                )
            )
        )

    fun removeCompetition(index: Int): DesktopCompetitorSpreadsheetImportDraft =
        copy(
            mapping = mapping.copy(
                competitions = mapping.competitions.filterIndexed { itemIndex, _ -> itemIndex != index }
            )
        )

    fun toProfile(): DesktopCompetitorSpreadsheetImportProfile =
        DesktopCompetitorSpreadsheetImportProfile(
            sheetName = selectedSheetName,
            headerRowIndex = headerRowIndex,
            mapping = mapping
        )

    fun toRegistrationImport(): DesktopSpreadsheetRegistrationImport {
        require(canImport) {
            validationErrors.joinToString(" ")
        }
        val eventName = path.fileName.toString()
            .substringBeforeLast(".")
            .trim()
            .ifBlank { "Spreadsheet Registration" }
        val registration = DesktopEventRegSpreadsheetParser.parseMappedRows(
            rows = selectedWorksheet.rows,
            headerRowIndex = headerRowIndex,
            eventName = eventName,
            mapping = mapping
        )
        require(registration.competitions.isNotEmpty()) {
            "No competitors with a mapped category or course were found."
        }
        return DesktopSpreadsheetRegistrationImport(
            sourceUrl = path.toAbsolutePath().normalize().toString(),
            registration = registration
        )
    }

    companion object {
        fun load(
            path: Path,
            rememberedProfile: DesktopCompetitorSpreadsheetImportProfile? =
                DesktopCompetitorSpreadsheetImportPreferences.lastProfile()
        ): DesktopCompetitorSpreadsheetImportDraft {
            require(path.fileName.toString().endsWith(".xlsx", ignoreCase = true)) {
                "Select an Excel workbook with the .xlsx extension."
            }
            require(Files.isRegularFile(path)) {
                "Spreadsheet file does not exist: $path"
            }
            val worksheets = XlsxWorkbookReader.readWorksheets(Files.readAllBytes(path))
            val selectedWorksheet = rememberedProfile
                ?.sheetName
                ?.let { rememberedName ->
                    worksheets.firstOrNull { it.name.equals(rememberedName, ignoreCase = true) }
                }
                ?: worksheets.first()
            val selectedHeaderRowIndex = preferredHeaderRowIndex(selectedWorksheet, rememberedProfile)
            val headers = selectedWorksheet.rows.getOrNull(selectedHeaderRowIndex).orEmpty()
            return DesktopCompetitorSpreadsheetImportDraft(
                path = path.toAbsolutePath().normalize(),
                worksheets = worksheets,
                selectedSheetName = selectedWorksheet.name,
                headerRowIndex = selectedHeaderRowIndex,
                mapping = preferredMapping(selectedWorksheet.name, headers, rememberedProfile),
                rememberedProfile = rememberedProfile
            )
        }
    }
}

object DesktopCompetitorSpreadsheetImportPreferences {
    private const val LAST_DIRECTORY_KEY = "lastCompetitorSpreadsheetDirectory"
    private const val LAST_PROFILE_KEY = "lastCompetitorSpreadsheetProfileV1"
    private val preferences: Preferences =
        Preferences.userNodeForPackage(DesktopCompetitorSpreadsheetImportPreferences::class.java)

    fun preferredDirectory(): Path {
        val remembered = preferences.get(LAST_DIRECTORY_KEY, "")
            .takeIf { it.isNotBlank() }
            ?.let(Path::of)
            ?.takeIf(Files::isDirectory)
        return remembered ?: DesktopEventFileLocations.preferredEventFileDirectory()
    }

    fun rememberFile(path: Path) {
        path.toAbsolutePath().normalize().parent?.let { directory ->
            preferences.put(LAST_DIRECTORY_KEY, directory.toString())
        }
    }

    fun lastProfile(): DesktopCompetitorSpreadsheetImportProfile? =
        preferences.get(LAST_PROFILE_KEY, "")
            .takeIf { it.isNotBlank() }
            ?.let(DesktopCompetitorSpreadsheetProfileCodec::decode)

    fun rememberProfile(profile: DesktopCompetitorSpreadsheetImportProfile) {
        preferences.put(LAST_PROFILE_KEY, DesktopCompetitorSpreadsheetProfileCodec.encode(profile))
    }
}

object DesktopCompetitorSpreadsheetProfileCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(profile: DesktopCompetitorSpreadsheetImportProfile): String =
        buildJsonObject {
            put("sheetName", JsonPrimitive(profile.sheetName))
            put("headerRowIndex", JsonPrimitive(profile.headerRowIndex))
            put("competitorColumns", buildJsonObject {
                profile.mapping.competitorColumns.forEach { (field, reference) ->
                    put(field.name, reference.toJson())
                }
            })
            put("competitions", buildJsonArray {
                profile.mapping.competitions.forEach { competition ->
                    add(
                        buildJsonObject {
                            put("name", JsonPrimitive(competition.competitionName))
                            competition.categoryColumn?.let { put("categoryColumn", it.toJson()) }
                            competition.courseColumn?.let { put("courseColumn", it.toJson()) }
                            competition.startTimeColumn?.let { put("startTimeColumn", it.toJson()) }
                        }
                    )
                }
            })
        }.toString()

    fun decode(value: String): DesktopCompetitorSpreadsheetImportProfile? =
        runCatching {
            val root = json.parseToJsonElement(value).jsonObject
            val competitorColumns = root["competitorColumns"]
                ?.jsonObject
                ?.mapNotNull { (fieldName, element) ->
                    val field = DesktopSpreadsheetCompetitorField.entries.firstOrNull { it.name == fieldName }
                    val reference = element.jsonObject.toColumnRef()
                    if (field == null || reference == null) null else field to reference
                }
                ?.toMap()
                .orEmpty()
            val competitions = root["competitions"]
                ?.jsonArray
                ?.mapNotNull { element ->
                    val item = element.jsonObject
                    item["name"]?.jsonPrimitive?.contentOrNull?.let { name ->
                        DesktopSpreadsheetCompetitionColumnMapping(
                            competitionName = name,
                            categoryColumn = item["categoryColumn"]?.jsonObject?.toColumnRef(),
                            courseColumn = item["courseColumn"]?.jsonObject?.toColumnRef(),
                            startTimeColumn = item["startTimeColumn"]?.jsonObject?.toColumnRef()
                        )
                    }
                }
                .orEmpty()
            DesktopCompetitorSpreadsheetImportProfile(
                sheetName = root["sheetName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                headerRowIndex = root["headerRowIndex"]?.jsonPrimitive?.intOrNull ?: 0,
                mapping = DesktopSpreadsheetCompetitorColumnMapping(
                    competitorColumns = competitorColumns,
                    competitions = competitions
                )
            )
        }.getOrNull()

    private fun DesktopSpreadsheetColumnRef.toJson(): JsonObject =
        buildJsonObject {
            put("heading", JsonPrimitive(heading))
            put("occurrence", JsonPrimitive(occurrence))
        }

    private fun JsonObject.toColumnRef(): DesktopSpreadsheetColumnRef? {
        val heading = get("heading")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        return DesktopSpreadsheetColumnRef(
            heading = heading,
            occurrence = get("occurrence")?.jsonPrimitive?.intOrNull?.coerceAtLeast(0) ?: 0
        )
    }
}

private fun preferredHeaderRowIndex(
    worksheet: DesktopXlsxWorksheet,
    profile: DesktopCompetitorSpreadsheetImportProfile?
): Int {
    val profiledRow = profile?.mapping?.let { mapping ->
        worksheet.rows.indices.firstOrNull { rowIndex ->
            val headers = worksheet.rows[rowIndex]
            val first = mapping.column(DesktopSpreadsheetCompetitorField.FIRST_NAME)
            val last = mapping.column(DesktopSpreadsheetCompetitorField.LAST_NAME)
            first != null && last != null &&
                headers.containsReference(first) &&
                headers.containsReference(last)
        }
    }
    if (profiledRow != null) {
        return profiledRow
    }
    return worksheet.rows.indices.firstOrNull { rowIndex ->
        val suggested = DesktopEventRegSpreadsheetParser.suggestedColumnMapping(worksheet.rows[rowIndex])
        suggested.column(DesktopSpreadsheetCompetitorField.FIRST_NAME) != null &&
            suggested.column(DesktopSpreadsheetCompetitorField.LAST_NAME) != null
    } ?: profile
        ?.headerRowIndex
        ?.takeIf { it in worksheet.rows.indices }
        ?: 0
}

private fun preferredMapping(
    sheetName: String,
    headers: List<String>,
    profile: DesktopCompetitorSpreadsheetImportProfile?
): DesktopSpreadsheetCompetitorColumnMapping {
    val suggested = DesktopEventRegSpreadsheetParser.suggestedColumnMapping(headers)
    if (profile == null) {
        return suggested
    }
    val profiledFirst = profile.mapping.column(DesktopSpreadsheetCompetitorField.FIRST_NAME)
    val profiledLast = profile.mapping.column(DesktopSpreadsheetCompetitorField.LAST_NAME)
    val profileMatches = profile.sheetName.equals(sheetName, ignoreCase = true) ||
        (
            profiledFirst != null &&
                profiledLast != null &&
                headers.containsReference(profiledFirst) &&
                headers.containsReference(profiledLast)
            )
    return if (profileMatches) {
        profile.mapping.copy(
            competitorColumns = suggested.competitorColumns + profile.mapping.competitorColumns,
            competitions = profile.mapping.competitions.ifEmpty { suggested.competitions }
        )
    } else {
        suggested
    }
}

private fun List<String>.containsReference(reference: DesktopSpreadsheetColumnRef): Boolean {
    val normalized = reference.heading.normalizedSpreadsheetHeading()
    return count { it.normalizedSpreadsheetHeading() == normalized } > reference.occurrence
}

private fun String.normalizedSpreadsheetHeading(): String =
    trim().removePrefix("\ufeff").lowercase().replace(Regex("\\s+"), " ")

private fun spreadsheetColumnLetters(columnIndex: Int): String {
    var number = columnIndex + 1
    val result = StringBuilder()
    while (number > 0) {
        val remainder = (number - 1) % 26
        result.append(('A'.code + remainder).toChar())
        number = (number - 1) / 26
    }
    return result.reverse().toString()
}
