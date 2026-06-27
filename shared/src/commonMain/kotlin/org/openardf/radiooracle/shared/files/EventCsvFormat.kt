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
        const val COLUMN_COUNT = 14
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
        const val BIB_NUMBER = 12
        const val CALL_SIGN = 13

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
            "preferred_start_group",
            "bib_number",
            "call_sign"
        )
        val LEGACY_HEADER = HEADER.take(PREFERRED_START_GROUP + 1)
        val HEADER_ROW = HEADER.joinToString(DELIMITER.toString())

        fun isHeader(fields: List<String>): Boolean =
            fields.map { it.trim().lowercase() }.let { normalized ->
                normalized == HEADER ||
                    normalized == HEADER.dropLast(1) ||
                    normalized == LEGACY_HEADER ||
                    normalized == LEGACY_HEADER.dropLast(1)
            }
    }

    object CompetitorStart {
        const val COLUMN_COUNT = 3
        const val START_NUMBER = 0
        const val START_TIME = 1
        const val SI_NUMBER = 2
        const val EXPORTED_COLUMN_COUNT = 10
        const val EXPORTED_START_TIME = 5
        const val EXPORTED_INDEX = 6
        const val EXPORTED_SI_NUMBER = 9
    }

    object Control {
        const val COLUMN_COUNT = 5
        const val SI_CODE = 0
        const val ROLE = 1
        const val FOX = 2
        const val PUBLIC_LABEL = 3
        const val NOTES = 4

        val HEADER = listOf("si_code", "role", "fox", "public_label", "notes")
        val LEGACY_HEADER = listOf("si_code", "role", "mandatory", "public_label", "notes")
        val HEADER_ROW = HEADER.joinToString(DELIMITER.toString())

        fun isHeader(fields: List<String>): Boolean =
            fields.map { it.trim().lowercase() }.let { normalized ->
                normalized == HEADER || normalized == LEGACY_HEADER
            }

        fun isLegacyHeader(fields: List<String>): Boolean =
            fields.map { it.trim().lowercase() } == LEGACY_HEADER
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
