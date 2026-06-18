package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.domain.StandardCategoryType

/** Parsed built-in category definition independent of Android resources. */
data class StandardCategoryDefinition(
    val name: String,
    val isMan: Boolean,
    val maxAge: Int
)

/** Shared parser and provider for built-in standard category presets. */
object StandardCategoryRules {
    private val standardCategoryNamePattern = Regex("^[A-Z][ -]?\\d{1,3}[A-Z]*$")

    private val internationalRows = listOf(
        "W19;0;19",
        "W21;0;34",
        "W35;0;44",
        "W45;0;54",
        "W55;0;64",
        "W65;0;200",
        "M19;1;19",
        "M21;1;39",
        "M40;1;49",
        "M50;1;59",
        "M60;1;69",
        "M70;1;200"
    )

    private val czechRows = listOf(
        "D7;0;7",
        "D9;0;9",
        "D12;0;12",
        "D14;0;14",
        "D16;0;16",
        "D19;0;19",
        "D20;0;34",
        "D35;0;44",
        "D45;0;54",
        "D55;0;64",
        "D65;0;200",
        "M7;1;7",
        "M9;1;9",
        "M12;1;12",
        "M14;1;14",
        "M16;1;16",
        "M19;1;19",
        "M20;1;39",
        "M40;1;49",
        "M50;1;59",
        "M60;1;69",
        "M70;1;200"
    )

    /** Returns built-in category definitions for the requested preset set. */
    fun definitionsFor(type: StandardCategoryType): List<StandardCategoryDefinition> {
        val rows = when (type) {
            StandardCategoryType.INTERNATIONAL -> internationalRows
            StandardCategoryType.CZECH -> czechRows
        }
        return rows.mapNotNull(::parseDefinition)
    }

    /** Infers category gender from standard ARDF category prefixes when the stored flag is stale or absent. */
    fun inferIsManFromName(name: String): Boolean? {
        val normalized = name.trim().uppercase()
        if (!standardCategoryNamePattern.matches(normalized)) {
            return null
        }
        return when (normalized.first()) {
            'M' -> true
            'W', 'D' -> false
            else -> null
        }
    }

    /** Parses a semicolon-delimited category preset row in the form name;isMan;maxAge. */
    fun parseDefinition(row: String): StandardCategoryDefinition? {
        val fields = row.split(";")
        if (fields.size != 3) {
            return null
        }

        val name = fields[0].trim()
        val isManToken = fields[1].trim()
        val maxAge = fields[2].trim().toIntOrNull() ?: return null

        if (name.isEmpty() || isManToken !in setOf("0", "1") || maxAge <= 0) {
            return null
        }

        return StandardCategoryDefinition(
            name = name,
            isMan = isManToken == "1",
            maxAge = maxAge
        )
    }
}
