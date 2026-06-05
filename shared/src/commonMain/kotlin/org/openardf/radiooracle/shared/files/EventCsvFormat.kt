package org.openardf.radiooracle.shared.files

/** Shared CSV contract for formats supported by both Android and desktop. */
object EventCsvFormat {
    const val DELIMITER = ';'
    const val CONTROL_POINT_DELIMITER = ','

    object Category {
        const val COLUMN_COUNT = 11
        const val OPEN_MAX_AGE = 99
        const val NAME = 0
        const val IS_MAN = 1
        const val MAX_AGE = 2
        const val LENGTH_METERS = 3
        const val CLIMB_METERS = 4
        const val FOLLOWS_RACE_PRESETS = 5
        const val RACE_TYPE = 6
        const val TIME_LIMIT_MINUTES = 7
        const val RACE_BAND = 8
        const val ANDROID_IMPORT_CONTROL_POINTS = 9
        const val EXPORTED_CONTROL_COUNT = 9
        const val EXPORTED_CONTROL_POINTS = 10
        const val ENCRYPTED_IDEAL_ORDER = 11

        fun controlPointsFrom(fields: List<String>): String {
            val exportedControlPoints = fields[EXPORTED_CONTROL_POINTS].trim()
            val importedControlPoints = fields[ANDROID_IMPORT_CONTROL_POINTS].trim()
            return (exportedControlPoints.takeIf { it.isNotEmpty() } ?: importedControlPoints)
                .replace(CONTROL_POINT_DELIMITER, ' ')
                .trim()
        }
    }

    object Competitor {
        const val COLUMN_COUNT = 12
        const val REQUIRED_IMPORT_COLUMNS = 6
        const val SI_NUMBER = 0
        const val START_NUMBER = 1
        const val FIRST_NAME = 2
        const val LAST_NAME = 3
        const val CATEGORY_NAME = 4
        const val IS_MAN = 5
        const val BIRTH_YEAR = 6
        const val CLUB = 7
        const val INDEX = 8
        const val START_TIME = 9
        const val SI_RENT = 10
        const val PREFERRED_START_GROUP = 11

        val HEADER = listOf(
            "si_number",
            "start_number",
            "first_name",
            "last_name",
            "category",
            "gender",
            "birth_year",
            "club",
            "index",
            "start_time",
            "si_rent",
            "preferred_start_group"
        )
        val HEADER_ROW = HEADER.joinToString(DELIMITER.toString())

        fun isHeader(fields: List<String>): Boolean =
            fields.map { it.trim().lowercase() }.let { normalized ->
                normalized == HEADER || normalized == HEADER.dropLast(1)
            }
    }

    object CompetitorStart {
        const val COLUMN_COUNT = 3
        const val START_NUMBER = 0
        const val START_TIME = 1
        const val SI_NUMBER = 2
    }

    object ArdfEventRegistration {
        const val COLUMN_COUNT = 5
        const val FIRST_NAME = 0
        const val LAST_NAME = 1
        const val INDEX = 2
        const val SI_NUMBER = 3
        const val CATEGORY_NAME = 4

        private val CZECH_HEADER = listOf("jméno", "příjmení", "registrace", "si", "kategorie")
        private val ASCII_HEADER = listOf("jmeno", "prijmeni", "registrace", "si", "kategorie")

        fun isHeader(fields: List<String>): Boolean {
            val normalized = fields.map { it.trim().lowercase() }
            return normalized == CZECH_HEADER || normalized == ASCII_HEADER
        }
    }
}
