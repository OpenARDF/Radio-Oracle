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

import org.openardf.radiooracle.shared.domain.RaceType

data class CourseRuleRequirement(
    val minControls: Int,
    val maxControls: Int,
    val minLengthMeters: Int,
    val maxLengthMeters: Int
) {
    fun controlRangeText(): String =
        if (minControls == maxControls) minControls.toString() else "$minControls-$maxControls"

    fun lengthRangeText(): String =
        "${lengthValueText(minLengthMeters)}-${lengthValueText(maxLengthMeters)} km"

    private fun lengthValueText(meters: Int): String =
        if (meters % 1000 == 0) {
            "${meters / 1000}"
        } else {
            "${meters / 1000}.${(meters % 1000).toString().padStart(3, '0').trimEnd('0')}"
    }
}

data class CourseSpacingRuleSet(
    val formatLabel: String,
    val startMinMeters: Int,
    val pairMinMeters: Int,
    val includeBeaconInStartCheck: Boolean,
    val includeSpectatorInPairCheck: Boolean,
    val includeBeaconInPairCheck: Boolean
)

object EventCourseRuleCatalog {
    const val CLIMB_LIMIT_PERCENT = 6.0

    val classicRequirements: LinkedHashMap<String, CourseRuleRequirement> = linkedMapOf(
        "W12" to CourseRuleRequirement(3, 3, 2_000, 3_000),
        "W14" to CourseRuleRequirement(4, 4, 2_500, 3_000),
        "W16" to CourseRuleRequirement(5, 5, 3_500, 4_000),
        "W19" to CourseRuleRequirement(4, 4, 6_000, 8_000),
        "W21" to CourseRuleRequirement(4, 4, 7_000, 9_000),
        "W35" to CourseRuleRequirement(4, 5, 6_000, 8_000),
        "W45" to CourseRuleRequirement(3, 4, 5_000, 7_000),
        "W55" to CourseRuleRequirement(3, 4, 4_000, 6_000),
        "W65" to CourseRuleRequirement(3, 4, 4_000, 6_000),
        "W75" to CourseRuleRequirement(2, 4, 3_000, 5_000),
        "M12" to CourseRuleRequirement(3, 3, 2_000, 3_000),
        "M14" to CourseRuleRequirement(4, 4, 2_500, 3_000),
        "M16" to CourseRuleRequirement(5, 5, 3_500, 4_000),
        "M19" to CourseRuleRequirement(4, 4, 8_000, 10_000),
        "M21" to CourseRuleRequirement(5, 5, 9_000, 12_000),
        "M40" to CourseRuleRequirement(4, 4, 8_000, 10_000),
        "M50" to CourseRuleRequirement(4, 5, 6_000, 8_000),
        "M60" to CourseRuleRequirement(3, 4, 5_000, 7_000),
        "M70" to CourseRuleRequirement(3, 4, 4_000, 6_000),
        "M80" to CourseRuleRequirement(2, 4, 3_000, 5_000)
    )

    val foxoringRequirements: LinkedHashMap<String, CourseRuleRequirement> = linkedMapOf(
        "W19" to CourseRuleRequirement(5, 8, 4_000, 6_000),
        "W21" to CourseRuleRequirement(6, 10, 5_000, 7_000),
        "W35" to CourseRuleRequirement(5, 8, 4_000, 6_000),
        "W45" to CourseRuleRequirement(4, 7, 4_000, 6_000),
        "W55" to CourseRuleRequirement(4, 7, 3_000, 5_000),
        "W65" to CourseRuleRequirement(4, 7, 3_000, 5_000),
        "W75" to CourseRuleRequirement(4, 7, 3_000, 4_000),
        "M19" to CourseRuleRequirement(6, 8, 6_000, 8_000),
        "M21" to CourseRuleRequirement(8, 10, 7_000, 9_000),
        "M40" to CourseRuleRequirement(6, 8, 6_000, 8_000),
        "M50" to CourseRuleRequirement(5, 8, 5_000, 7_000),
        "M60" to CourseRuleRequirement(5, 8, 4_000, 6_000),
        "M70" to CourseRuleRequirement(4, 7, 3_000, 5_000),
        "M80" to CourseRuleRequirement(4, 7, 3_000, 4_000)
    )

    val sprintRequirements: LinkedHashMap<String, CourseRuleRequirement> = linkedMapOf(
        *classicRequirements.map { (category, requirement) ->
            category to requirement.copy(
                minControls = requirement.minControls * 2,
                maxControls = requirement.maxControls * 2
            )
        }.toTypedArray()
    )

    fun categoryRuleKey(categoryName: String): String? {
        val rawKey = Regex("""\b[WMD][\s_-]*\d{2}\b""")
            .find(categoryName.uppercase())
            ?.value
            ?: return null
        val compactKey = rawKey.filter { it.isLetterOrDigit() }
        return if (compactKey.startsWith("D")) "W${compactKey.drop(1)}" else compactKey
    }

    fun categoryRequirement(categoryName: String, raceType: RaceType): CourseRuleRequirement? {
        val key = categoryRuleKey(categoryName) ?: return null
        return categoryRequirementByKey(key, raceType)
    }

    fun categoryRequirementByKey(categoryKey: String, raceType: RaceType): CourseRuleRequirement? =
        when (raceType) {
            RaceType.FOXORING -> foxoringRequirements[categoryKey]
            RaceType.SPRINT -> sprintRequirements[categoryKey]
            RaceType.CLASSIC, RaceType.SHORT -> classicRequirements[categoryKey]
            RaceType.ORIENTEERING -> null
        }

    fun routeLengthRequirement(categoryName: String, raceType: RaceType): CourseRuleRequirement? =
        when (raceType) {
            RaceType.CLASSIC, RaceType.SHORT, RaceType.FOXORING -> categoryRequirement(categoryName, raceType)
            RaceType.SPRINT, RaceType.ORIENTEERING -> null
        }

    fun hasClimbLimit(raceType: RaceType): Boolean =
        when (raceType) {
            RaceType.CLASSIC,
            RaceType.SHORT,
            RaceType.SPRINT,
            RaceType.FOXORING -> true
            RaceType.ORIENTEERING -> false
        }

    fun spacingRuleSet(raceType: RaceType, categoryName: String = ""): CourseSpacingRuleSet? {
        val key = categoryRuleKey(categoryName)
        return when (raceType) {
            RaceType.SPRINT -> CourseSpacingRuleSet(
                "Sprint",
                100,
                100,
                includeBeaconInStartCheck = false,
                includeSpectatorInPairCheck = true,
                includeBeaconInPairCheck = true
            )
            RaceType.FOXORING -> CourseSpacingRuleSet(
                "Foxoring",
                250,
                250,
                includeBeaconInStartCheck = true,
                includeSpectatorInPairCheck = false,
                includeBeaconInPairCheck = true
            )
            RaceType.CLASSIC, RaceType.SHORT -> CourseSpacingRuleSet(
                formatLabel = if (isYouthClassicCategoryKey(key)) "Youth Classic" else "Classic",
                startMinMeters = if (isYouthClassicCategoryKey(key)) 500 else 750,
                pairMinMeters = 400,
                includeBeaconInStartCheck = true,
                includeSpectatorInPairCheck = false,
                includeBeaconInPairCheck = true
            )
            RaceType.ORIENTEERING -> null
        }
    }

    fun isYouthClassicCategoryKey(categoryKey: String?): Boolean =
        categoryKey in setOf("W12", "W14", "W16", "M12", "M14", "M16")
}
