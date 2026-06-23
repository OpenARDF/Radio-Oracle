package org.openardf.radiooracle.shared.files

import org.openardf.radiooracle.shared.sportident.SportIdentCodes
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceType
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
    val index: String,
    val startTimeText: String?,
    val siRent: Boolean,
    val preferredStartGroup: Int? = null,
    val bibNumber: String = index,
    val callSign: String = ""
)

enum class CompetitorCsvImportProfile {
    CANONICAL,
    ARDF_EVENT_REGISTRATION
}

data class CompetitorStartCsvImportRow(
    val startNumber: Int,
    val startTimeText: String,
    val siNumber: Int?,
    val bibNumber: String = "",
    val callSign: String = ""
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
    fun parseAndroidCategoryRows(csvText: String): CsvImportResult<CategoryCsvImportRow> {
        val rows = mutableListOf<CategoryCsvImportRow>()
        val invalidLines = mutableListOf<CsvImportError>()

        csvText.lineSequence().forEachIndexed { lineIndex, line ->
            if (line.isBlank()) return@forEachIndexed

            try {
                rows += parseAndroidCategoryRow(parseSemicolonRow(line), lineIndex)
            } catch (error: IllegalArgumentException) {
                invalidLines += CsvImportError(lineIndex, error.message ?: "Invalid category row")
            }
        }

        return CsvImportResult(rows, invalidLines)
    }

    fun parseAndroidCompetitorRows(csvText: String): CsvImportResult<CompetitorCsvImportRow> {
        if (detectCompetitorProfile(csvText) == CompetitorCsvImportProfile.ARDF_EVENT_REGISTRATION) {
            return parseArdfEventRegistrationCompetitorRows(csvText)
        }

        val rows = mutableListOf<CompetitorCsvImportRow>()
        val invalidLines = mutableListOf<CsvImportError>()

        csvText.lineSequence().forEachIndexed { lineIndex, line ->
            if (line.isBlank()) return@forEachIndexed

            try {
                rows += parseAndroidCompetitorRow(parseSemicolonRow(line), lineIndex)
            } catch (_: HeaderRow) {
                // Optional exported header row.
            } catch (error: IllegalArgumentException) {
                invalidLines += CsvImportError(lineIndex, error.message ?: "Invalid competitor row")
            }
        }

        return CsvImportResult(rows, invalidLines)
    }

    fun detectCompetitorProfile(csvText: String): CompetitorCsvImportProfile {
        val firstFields = csvText.lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.let(::parseSemicolonRow)
            ?: return CompetitorCsvImportProfile.CANONICAL

        return if (EventCsvFormat.ArdfEventRegistration.isHeader(firstFields)) {
            CompetitorCsvImportProfile.ARDF_EVENT_REGISTRATION
        } else {
            CompetitorCsvImportProfile.CANONICAL
        }
    }

    fun parseArdfEventRegistrationCompetitorRows(csvText: String): CsvImportResult<CompetitorCsvImportRow> {
        val rows = mutableListOf<CompetitorCsvImportRow>()
        val invalidLines = mutableListOf<CsvImportError>()

        csvText.lineSequence().forEachIndexed { lineIndex, line ->
            if (line.isBlank()) return@forEachIndexed

            try {
                val fields = parseSemicolonRow(line)
                if (EventCsvFormat.ArdfEventRegistration.isHeader(fields)) {
                    return@forEachIndexed
                }
                rows += parseArdfEventRegistrationCompetitorRow(fields, lineIndex)
            } catch (error: IllegalArgumentException) {
                invalidLines += CsvImportError(lineIndex, error.message ?: "Invalid ARDFEvent competitor row")
            }
        }

        return CsvImportResult(rows, invalidLines)
    }

    fun parseAndroidCompetitorStartRows(csvText: String): CsvImportResult<CompetitorStartCsvImportRow> {
        val rows = mutableListOf<CompetitorStartCsvImportRow>()
        val invalidLines = mutableListOf<CsvImportError>()

        csvText.lineSequence().forEachIndexed { lineIndex, line ->
            if (line.isBlank()) return@forEachIndexed

            try {
                rows += parseAndroidCompetitorStartRow(parseSemicolonRow(line), lineIndex)
            } catch (error: IllegalArgumentException) {
                invalidLines += CsvImportError(lineIndex, error.message ?: "Invalid competitor-start row")
            }
        }

        return CsvImportResult(rows, invalidLines)
    }

    fun parseControlRows(csvText: String): CsvImportResult<ControlCsvImportRow> {
        val rows = mutableListOf<ControlCsvImportRow>()
        val invalidLines = mutableListOf<CsvImportError>()
        var usesLegacyMandatoryColumn = false

        csvText.lineSequence().forEachIndexed { lineIndex, line ->
            if (line.isBlank()) return@forEachIndexed

            try {
                val fields = parseSemicolonRow(line)
                if (lineIndex == 0 && EventCsvFormat.Control.isHeader(fields)) {
                    usesLegacyMandatoryColumn = EventCsvFormat.Control.isLegacyHeader(fields)
                    return@forEachIndexed
                }
                rows += parseControlRow(fields, lineIndex, usesLegacyMandatoryColumn)
            } catch (error: IllegalArgumentException) {
                invalidLines += CsvImportError(lineIndex, error.message ?: "Invalid control row")
            }
        }

        return CsvImportResult(rows, invalidLines)
    }

    private fun parseAndroidCategoryRow(fields: List<String>, lineIndex: Int): CategoryCsvImportRow {
        require(fields.size >= EventCsvFormat.Category.COLUMN_COUNT) {
            "Expected at least ${EventCsvFormat.Category.COLUMN_COUNT} columns at line: $lineIndex"
        }

        val name = fields[EventCsvFormat.Category.NAME].trim()
        val maxAge = fields[EventCsvFormat.Category.MAX_AGE].trim().toInt()
        val lengthMeters = fields[EventCsvFormat.Category.LENGTH_METERS].trim().takeIf { it.isNotEmpty() }?.toInt() ?: 0
        val climbMeters = fields[EventCsvFormat.Category.CLIMB_METERS].trim().takeIf { it.isNotEmpty() }?.toInt() ?: 0
        require(name.isNotEmpty() && maxAge > 0 && lengthMeters > 0 && climbMeters >= 0) {
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
        if (lineIndex == 0 && EventCsvFormat.Competitor.isHeader(fields)) {
            throw HeaderRow
        }

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
            index = fields.optionalTrimmed(EventCsvFormat.Competitor.INDEX),
            bibNumber = fields.optionalTrimmed(EventCsvFormat.Competitor.BIB_NUMBER)
                .ifBlank { fields.optionalTrimmed(EventCsvFormat.Competitor.INDEX) },
            callSign = fields.optionalTrimmed(EventCsvFormat.Competitor.CALL_SIGN),
            startTimeText = fields.optionalTrimmed(EventCsvFormat.Competitor.START_TIME).takeIf { it.isNotEmpty() },
            siRent = fields.optionalTrimmedInt(EventCsvFormat.Competitor.SI_RENT) == 1,
            preferredStartGroup = preferredStartGroup
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
            index = fields[EventCsvFormat.ArdfEventRegistration.INDEX].trim(),
            bibNumber = fields[EventCsvFormat.ArdfEventRegistration.INDEX].trim(),
            callSign = "",
            startTimeText = null,
            siRent = false
        )
    }

    private fun parseAndroidCompetitorStartRow(fields: List<String>, lineIndex: Int): CompetitorStartCsvImportRow {
        require(
            fields.size == EventCsvFormat.CompetitorStart.COLUMN_COUNT ||
                fields.size >= EventCsvFormat.CompetitorStart.EXPORTED_COLUMN_COUNT
        ) {
            "Expected ${EventCsvFormat.CompetitorStart.COLUMN_COUNT} or at least ${EventCsvFormat.CompetitorStart.EXPORTED_COLUMN_COUNT} columns at line: $lineIndex"
        }

        val exportedShape = fields.size >= EventCsvFormat.CompetitorStart.EXPORTED_COLUMN_COUNT
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
        val bibNumber = if (exportedShape) fields[EventCsvFormat.CompetitorStart.EXPORTED_INDEX].trim() else ""
        val startNumber = fields[EventCsvFormat.CompetitorStart.START_NUMBER].trim().toInt()
        val siNumber = fields[siNumberColumn].trim().takeIf { it.isNotEmpty() }?.toInt()
        require(siNumber == null || SportIdentCodes.isSINumberValid(siNumber)) {
            "Invalid SI number at line: $lineIndex"
        }

        return CompetitorStartCsvImportRow(
            startNumber = startNumber,
            startTimeText = fields[startTimeColumn].trim(),
            siNumber = siNumber,
            bibNumber = bibNumber
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

    private fun parseSemicolonRow(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0

        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && inQuotes && line.getOrNull(index + 1) == '"' -> {
                    current.append('"')
                    index++
                }
                char == '"' -> inQuotes = !inQuotes
                char == ';' && !inQuotes -> {
                    fields += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
            index++
        }

        require(!inQuotes) {
            "Unclosed quoted field"
        }

        fields += current.toString()
        return fields
    }

    private fun List<String>.optionalTrimmed(index: Int): String =
        getOrNull(index)?.trim() ?: ""

    private fun List<String>.optionalTrimmedInt(index: Int): Int? =
        optionalTrimmed(index).takeIf { it.isNotEmpty() }?.toInt()

    private object HeaderRow : IllegalArgumentException()
}
