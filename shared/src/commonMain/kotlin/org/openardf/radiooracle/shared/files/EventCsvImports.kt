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

package org.openardf.radiooracle.shared.files

import org.openardf.radiooracle.shared.sportident.SportIdentCodes
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.CompetitorCorridorRules
import org.openardf.radiooracle.shared.event.EventControlDetails
import org.openardf.radiooracle.shared.event.StandardCategoryRules
import org.openardf.radiooracle.shared.event.defaultScored
import org.openardf.radiooracle.shared.event.toDisplayLabel

data class CsvImportError(
    val lineIndex: Int,
    val message: String
)

data class CsvImportResult<T>(
    val rows: List<T>,
    val invalidLines: List<CsvImportError>
)

data class CompetitorCsvImportRow(
    val siNumber: Int?,
    val startNumber: Int?,
    val firstName: String,
    val lastName: String,
    val categoryName: String,
    val isMan: Boolean,
    val birthYear: Int?,
    val club: String,
    val personId: String,
    val startTimeText: String?,
    val siRent: Boolean,
    val preferredStartGroup: Int? = null,
    val bibNumber: String = "",
    val callSign: String = "",
    val courseName: String = "",
    val email: String = "",
    val cellPhone: String = "",
    val usaChampEligible: Boolean? = null,
    val region2ChampEligible: Boolean? = null
) {
    @Deprecated("Use personId; this is the IOF Person/Id-compatible identity field.")
    val index: String
        get() = personId
}

enum class CompetitorCsvImportProfile {
    CANONICAL,
    ARDF_EVENT_REGISTRATION
}

data class CompetitorStartCsvImportRow(
    val startNumber: Int,
    val startTimeText: String,
    val siNumber: Int?,
    val personId: String = "",
    val bibNumber: String = "",
    val callSign: String = "",
    val corridor: String? = null
)

data class CategoryCsvImportRow(
    val name: String,
    val isMan: Boolean,
    val maxAge: Int,
    val lengthMeters: Int,
    val climbMeters: Int,
    val followsRacePresets: Boolean,
    val raceType: RaceType?,
    val timeLimitMinutes: Long?,
    val raceBand: RaceBand?,
    val controlPointsText: String,
    val encryptedIdealOrder: String? = null
)

data class ControlCsvImportRow(
    val siCode: Int,
    val type: ControlPointType,
    val scored: Boolean,
    val publicLabel: String,
    val notes: String
)

/** Shared parsers for CSV import formats currently accepted by Android and desktop. */
object EventCsvImports {
    fun parseAndroidCategoryRows(csvText: String): CsvImportResult<CategoryCsvImportRow> =
        parseRows(csvText, EventCsvFormat.Category::isHeader, ::parseAndroidCategoryRow)

    fun parseAndroidCompetitorRows(csvText: String): CsvImportResult<CompetitorCsvImportRow> =
        if (detectCompetitorProfile(csvText) == CompetitorCsvImportProfile.ARDF_EVENT_REGISTRATION)
            parseArdfEventRegistrationCompetitorRows(csvText)
        else parseRows(csvText, EventCsvFormat.Competitor::isHeader, ::parseAndroidCompetitorRow)

    fun detectCompetitorProfile(csvText: String): CompetitorCsvImportProfile =
        if (listOf(',', ';').any { delimiter ->
            CsvCodec.records(csvText, delimiter).firstOrNull()?.let {
                it.error == null && EventCsvFormat.ArdfEventRegistration.isHeader(it.fields)
            } == true
        }) CompetitorCsvImportProfile.ARDF_EVENT_REGISTRATION else CompetitorCsvImportProfile.CANONICAL

    fun parseArdfEventRegistrationCompetitorRows(csvText: String): CsvImportResult<CompetitorCsvImportRow> =
        parseRows(csvText, EventCsvFormat.ArdfEventRegistration::isHeader, ::parseArdfEventRegistrationCompetitorRow)

    fun parseAndroidCompetitorStartRows(csvText: String): CsvImportResult<CompetitorStartCsvImportRow> =
        parseRows(csvText, EventCsvFormat.CompetitorStart::isHeader, ::parseAndroidCompetitorStartRow)

    fun parseControlRows(csvText: String): CsvImportResult<ControlCsvImportRow> {
        val legacy = listOf(',', ';').any { delimiter ->
            CsvCodec.records(csvText, delimiter).firstOrNull()?.let {
                it.error == null && EventCsvFormat.Control.isLegacyHeader(it.fields)
            } == true
        }
        return parseRows(csvText, EventCsvFormat.Control::isHeader) { fields, lineIndex ->
            parseControlRow(fields, lineIndex, legacy)
        }
    }

    /** Detect the dialect once per file using headers and valid rows, never separators inside a list. */
    private fun <T> parseRows(
        csvText: String,
        isHeader: (List<String>) -> Boolean,
        parseRow: (List<String>, Int) -> T
    ): CsvImportResult<T> {
        val candidates = listOf(',', ';').map { delimiter ->
            val rows = mutableListOf<T>()
            val errors = mutableListOf<CsvImportError>()
            val records = CsvCodec.records(csvText, delimiter)
            val hasHeader = records.firstOrNull()?.let { it.error == null && isHeader(it.fields) } == true
            records.drop(if (hasHeader) 1 else 0).forEach { record ->
                try {
                    require(record.error == null) { record.error.orEmpty() }
                    rows += parseRow(record.fields, record.lineIndex)
                } catch (error: IllegalArgumentException) {
                    errors += CsvImportError(record.lineIndex, error.message ?: "Invalid CSV record")
                }
            }
            Triple(CsvImportResult(rows, errors), hasHeader, records.firstOrNull()?.fields?.size ?: 0)
        }
        // A recognized header wins even if every data record is invalid. Otherwise prefer the dialect
        // that yields valid rows; field count breaks ties for useful errors in malformed headerless files.
        return candidates.maxWith(compareBy<Triple<CsvImportResult<T>, Boolean, Int>>(
            { it.second }, { it.first.rows.size }, { -it.first.invalidLines.size }, { it.third }
        )).first
    }

    private fun parseAndroidCategoryRow(fields: List<String>, lineIndex: Int): CategoryCsvImportRow {
        require(fields.size >= EventCsvFormat.Category.COLUMN_COUNT) {
            "Expected at least ${EventCsvFormat.Category.COLUMN_COUNT} columns at line: $lineIndex"
        }

        val name = fields[EventCsvFormat.Category.NAME].trim()
        val maxAge = fields[EventCsvFormat.Category.MAX_AGE].trim().toInt()
        val lengthMeters = fields[EventCsvFormat.Category.LENGTH_METERS].trim().takeIf { it.isNotEmpty() }?.toInt() ?: 0
        val climbMeters = fields[EventCsvFormat.Category.CLIMB_METERS].trim().takeIf { it.isNotEmpty() }?.toInt() ?: 0
        require(name.isNotEmpty() && maxAge > 0 && lengthMeters >= 0 && climbMeters >= 0) {
            "Invalid category data at line: $lineIndex"
        }

        val followsRacePresets = fields[EventCsvFormat.Category.FOLLOWS_RACE_PRESETS].trim() == "1"
        val raceType = if (followsRacePresets) null else parseRaceType(fields[EventCsvFormat.Category.RACE_TYPE].trim())
        val timeLimitMinutes = if (followsRacePresets) null else fields[EventCsvFormat.Category.TIME_LIMIT_MINUTES].trim().toLong()
        val raceBand = if (followsRacePresets) null else parseRaceBand(fields[EventCsvFormat.Category.RACE_BAND].trim())

        return CategoryCsvImportRow(
            name = name,
            isMan = StandardCategoryRules.inferIsManFromName(name)
                ?: (fields[EventCsvFormat.Category.IS_MAN].trim() == "1"),
            maxAge = maxAge,
            lengthMeters = lengthMeters,
            climbMeters = climbMeters,
            followsRacePresets = followsRacePresets,
            raceType = raceType,
            timeLimitMinutes = timeLimitMinutes,
            raceBand = raceBand,
            controlPointsText = EventCsvFormat.Category.controlPointsFrom(fields),
            encryptedIdealOrder = fields.getOrNull(EventCsvFormat.Category.ENCRYPTED_IDEAL_ORDER)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        )
    }

    private fun parseAndroidCompetitorRow(fields: List<String>, lineIndex: Int): CompetitorCsvImportRow {
        require(fields.size >= EventCsvFormat.Competitor.REQUIRED_IMPORT_COLUMNS) {
            "Expected at least ${EventCsvFormat.Competitor.REQUIRED_IMPORT_COLUMNS} columns at line: $lineIndex"
        }

        val firstName = fields[EventCsvFormat.Competitor.FIRST_NAME].trim()
        val lastName = fields[EventCsvFormat.Competitor.LAST_NAME].trim()
        require(firstName.isNotEmpty() && lastName.isNotEmpty()) {
            "Missing first/last name at line: $lineIndex"
        }

        val siNumber = fields[EventCsvFormat.Competitor.SI_NUMBER].trim().takeIf { it.isNotEmpty() }?.toInt()
        require(siNumber == null || SportIdentCodes.isSINumberValid(siNumber)) {
            "Invalid SI number at line: $lineIndex"
        }

        val preferredStartGroup = fields.optionalTrimmedInt(EventCsvFormat.Competitor.PREFERRED_START_GROUP)
        require(preferredStartGroup == null || preferredStartGroup in 1..3) {
            "Preferred start group must be 1, 2, or 3 at line: $lineIndex"
        }

        return CompetitorCsvImportRow(
            siNumber = siNumber,
            startNumber = fields[EventCsvFormat.Competitor.START_NUMBER].trim().takeIf { it.isNotEmpty() }?.toInt(),
            firstName = firstName,
            lastName = lastName,
            categoryName = fields[EventCsvFormat.Competitor.CATEGORY_NAME].trim(),
            isMan = fields[EventCsvFormat.Competitor.IS_MAN].trim().toIntOrNull() == 0,
            birthYear = fields.optionalTrimmedInt(EventCsvFormat.Competitor.BIRTH_YEAR),
            club = fields.optionalTrimmed(EventCsvFormat.Competitor.CLUB),
            personId = fields.optionalTrimmed(EventCsvFormat.Competitor.PERSON_ID),
            bibNumber = fields.optionalTrimmed(EventCsvFormat.Competitor.BIB_NUMBER),
            callSign = fields.optionalTrimmed(EventCsvFormat.Competitor.CALL_SIGN),
            startTimeText = fields.optionalTrimmed(EventCsvFormat.Competitor.START_TIME).takeIf { it.isNotEmpty() },
            siRent = fields.optionalTrimmedInt(EventCsvFormat.Competitor.SI_RENT) == 1,
            preferredStartGroup = preferredStartGroup,
            email = fields.optionalTrimmed(EventCsvFormat.Competitor.EMAIL),
            cellPhone = fields.optionalTrimmed(EventCsvFormat.Competitor.CELL_PHONE),
            usaChampEligible = fields.optionalBoolean(EventCsvFormat.Competitor.USA_CHAMP_ELIGIBLE),
            region2ChampEligible = fields.optionalBoolean(EventCsvFormat.Competitor.REGION2_CHAMP_ELIGIBLE)
        )
    }

    private fun parseArdfEventRegistrationCompetitorRow(fields: List<String>, lineIndex: Int): CompetitorCsvImportRow {
        require(fields.size == EventCsvFormat.ArdfEventRegistration.COLUMN_COUNT) {
            "Expected ${EventCsvFormat.ArdfEventRegistration.COLUMN_COUNT} columns at line: $lineIndex"
        }

        val firstName = fields[EventCsvFormat.ArdfEventRegistration.FIRST_NAME].trim()
        val lastName = fields[EventCsvFormat.ArdfEventRegistration.LAST_NAME].trim()
        require(firstName.isNotEmpty() && lastName.isNotEmpty()) {
            "Missing first/last name at line: $lineIndex"
        }

        val siNumber = fields[EventCsvFormat.ArdfEventRegistration.SI_NUMBER].trim().takeIf { it.isNotEmpty() }?.toInt()
        require(siNumber == null || SportIdentCodes.isSINumberValid(siNumber)) {
            "Invalid SI number at line: $lineIndex"
        }

        val categoryName = fields[EventCsvFormat.ArdfEventRegistration.CATEGORY_NAME].trim()
        return CompetitorCsvImportRow(
            siNumber = siNumber,
            startNumber = null,
            firstName = firstName,
            lastName = lastName,
            categoryName = categoryName,
            isMan = StandardCategoryRules.inferIsManFromName(categoryName)
                ?: categoryName.trim().uppercase().startsWith("M"),
            birthYear = null,
            club = "",
            personId = fields[EventCsvFormat.ArdfEventRegistration.INDEX].trim(),
            bibNumber = "",
            callSign = "",
            startTimeText = null,
            siRent = false
        )
    }

    private fun parseAndroidCompetitorStartRow(fields: List<String>, lineIndex: Int): CompetitorStartCsvImportRow {
        require(
            fields.size == EventCsvFormat.CompetitorStart.COLUMN_COUNT ||
                fields.size == EventCsvFormat.CompetitorStart.COMPACT_COLUMN_COUNT_WITH_CORRIDOR ||
                fields.size >= EventCsvFormat.CompetitorStart.MIN_EXPORTED_COLUMN_COUNT
        ) {
            "Expected ${EventCsvFormat.CompetitorStart.COLUMN_COUNT} or ${EventCsvFormat.CompetitorStart.COMPACT_COLUMN_COUNT_WITH_CORRIDOR}, or at least ${EventCsvFormat.CompetitorStart.MIN_EXPORTED_COLUMN_COUNT} columns at line: $lineIndex"
        }

        val exportedShape = fields.size >= EventCsvFormat.CompetitorStart.MIN_EXPORTED_COLUMN_COUNT
        val startTimeColumn = if (exportedShape) {
            EventCsvFormat.CompetitorStart.EXPORTED_START_TIME
        } else {
            EventCsvFormat.CompetitorStart.START_TIME
        }
        val siNumberColumn = if (exportedShape) {
            EventCsvFormat.CompetitorStart.EXPORTED_SI_NUMBER
        } else {
            EventCsvFormat.CompetitorStart.SI_NUMBER
        }
        val bibNumber = if (exportedShape) fields[EventCsvFormat.CompetitorStart.EXPORTED_BIB_NUMBER].trim() else ""
        val personId = if (exportedShape) fields[EventCsvFormat.CompetitorStart.EXPORTED_PERSON_ID].trim() else ""
        val corridor = when {
            fields.size == EventCsvFormat.CompetitorStart.COMPACT_COLUMN_COUNT_WITH_CORRIDOR ->
                CompetitorCorridorRules.normalized(fields[EventCsvFormat.CompetitorStart.COMPACT_CORRIDOR])

            fields.size >= EventCsvFormat.CompetitorStart.EXPORTED_COLUMN_COUNT ->
                CompetitorCorridorRules.normalized(fields[EventCsvFormat.CompetitorStart.EXPORTED_CORRIDOR])

            else -> null
        }
        val startNumber = fields[EventCsvFormat.CompetitorStart.START_NUMBER].trim().toInt()
        val siNumber = fields[siNumberColumn].trim().takeIf { it.isNotEmpty() }?.toInt()
        require(siNumber == null || SportIdentCodes.isSINumberValid(siNumber)) {
            "Invalid SI number at line: $lineIndex"
        }

        return CompetitorStartCsvImportRow(
            startNumber = startNumber,
            startTimeText = fields[startTimeColumn].trim(),
            siNumber = siNumber,
            personId = personId,
            bibNumber = bibNumber,
            corridor = corridor
        )
    }

    private fun parseControlRow(
        fields: List<String>,
        lineIndex: Int,
        usesLegacyMandatoryColumn: Boolean
    ): ControlCsvImportRow {
        require(fields.size >= EventCsvFormat.Control.COLUMN_COUNT) {
            "Expected at least ${EventCsvFormat.Control.COLUMN_COUNT} columns at line: $lineIndex"
        }
        val siCode = fields[EventCsvFormat.Control.SI_CODE].trim().toIntOrNull()
            ?: throw IllegalArgumentException("Invalid control SI code at line: $lineIndex")
        require(SportIdentCodes.isSICodeValid(siCode)) {
            "Control SI code is outside the supported range at line: $lineIndex"
        }
        val type = parseControlType(fields[EventCsvFormat.Control.ROLE].trim())
        val flag = fields[EventCsvFormat.Control.FOX].trim()
        val scored = if (usesLegacyMandatoryColumn) {
            if (parseBooleanFlag(flag)) false else type.defaultScored()
        } else {
            flag.takeIf { it.isNotEmpty() }?.let(::parseBooleanFlag) ?: type.defaultScored()
        }
        return ControlCsvImportRow(
            siCode = siCode,
            type = type,
            scored = scored,
            publicLabel = fields[EventCsvFormat.Control.PUBLIC_LABEL].trim(),
            notes = fields[EventCsvFormat.Control.NOTES].trim()
        )
    }

    private fun parseBooleanFlag(value: String): Boolean =
        value == "1" || value.equals("true", ignoreCase = true) || value.equals("yes", ignoreCase = true)

    private fun parseRaceType(value: String): RaceType =
        RaceType.entries.firstOrNull { it.name == value || it.toDisplayLabel() == value }
            ?: throw IllegalArgumentException("Unknown race type: $value")

    private fun parseControlType(value: String): ControlPointType =
        ControlPointType.entries.firstOrNull {
            it.name.equals(value, ignoreCase = true)
        } ?: when (value.trim().lowercase()) {
            "fox", "control" -> ControlPointType.CONTROL
            "spectator", "separator" -> ControlPointType.SEPARATOR
            "beacon" -> ControlPointType.BEACON
            else -> throw IllegalArgumentException("Unknown control role: $value")
        }

    private fun parseRaceBand(value: String): RaceBand =
        RaceBand.entries.firstOrNull { it.name == value || it.toDisplayLabel() == value }
            ?: throw IllegalArgumentException("Unknown race band: $value")

    private fun List<String>.optionalTrimmed(index: Int): String =
        getOrNull(index)?.trim() ?: ""

    private fun List<String>.optionalTrimmedInt(index: Int): Int? =
        optionalTrimmed(index).takeIf { it.isNotEmpty() }?.toInt()

    private fun List<String>.optionalBoolean(index: Int): Boolean? =
        when (optionalTrimmed(index).lowercase()) {
            "" -> null
            "1", "y", "yes", "true", "eligible", "champ eligible" -> true
            "0", "n", "no", "false", "ineligible", "not eligible" -> false
            else -> null
        }

}
