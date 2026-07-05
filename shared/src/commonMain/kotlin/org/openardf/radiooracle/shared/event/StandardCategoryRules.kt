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
    private val standardCategoryPartsPattern = Regex("^([A-Z])\\s*-?\\s*(\\d{1,3})([A-Z]*)$")

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
        val normalized = normalizedCategoryName(name).uppercase()
        if (!standardCategoryNamePattern.matches(normalized)) {
            return null
        }
        return when (normalized.first()) {
            'M' -> true
            'W', 'D' -> false
            else -> null
        }
    }

    /** Returns the gender flag corrected by standard category naming when the name implies one. */
    fun reconcileIsManWithName(name: String, isMan: Boolean): Boolean =
        inferIsManFromName(name) ?: isMan

    /** Returns the canonical spelling for standard ARDF category names while leaving custom names intact. */
    fun normalizedCategoryName(name: String): String {
        val trimmed = name.trim()
        val match = standardCategoryPartsPattern.matchEntire(trimmed.uppercase()) ?: return trimmed
        return "${match.groupValues[1]}${match.groupValues[2]}${match.groupValues[3]}"
    }

    /** Compares category names using canonical standard-category spelling. */
    fun categoryNamesEquivalent(first: String, second: String): Boolean =
        normalizedCategoryName(first).equals(normalizedCategoryName(second), ignoreCase = true)

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
