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

import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.RaceType
import java.util.Locale

object DesktopCourseFormatDetector {
    val supportedGeneratorRaceTypes: List<RaceType> = listOf(RaceType.CLASSIC, RaceType.FOXORING, RaceType.SPRINT)

    fun inferredRaceTypes(
        sourceName: String,
        courseData: DesktopCourseKmlData
    ): List<RaceType> {
        val controlTypes = courseData.controls.mapNotNull { control ->
            when {
                DesktopCoursePointLabelClassifier.isCourseEndpointName(control.name) -> null
                DesktopCoursePointLabelClassifier.isBeaconLabel(control.name) -> ControlPointType.BEACON
                DesktopCoursePointLabelClassifier.isSpectatorLabel(control.name) -> ControlPointType.SEPARATOR
                DesktopCoursePointLabelClassifier.sprintSlowFoxNumber(control.name) != null -> ControlPointType.CONTROL
                DesktopCoursePointLabelClassifier.sprintFastFoxNumber(control.name) != null -> ControlPointType.CONTROL
                else -> null
            }
        }
        return inferredRaceTypes(
            sourceName = sourceName,
            clues = courseData.routes.map { it.name } + courseData.controls.map { it.name },
            controlCount = controlTypes.size,
            controlTypes = controlTypes
        )
    }

    fun inferredRaceTypes(
        sourceName: String,
        clues: List<String>,
        controlCount: Int?,
        controlTypes: List<ControlPointType>
    ): List<RaceType> {
        val sourceNameText = sourceName.lowercase()
        val haystack = (listOf(sourceName) + clues)
            .joinToString(" ")
            .lowercase()
        val sourceNameSuggestsFoxoring = sourceNameText.containsFoxoringToken()
        val foxCount = controlTypes.count { it == ControlPointType.CONTROL }
        val hasSpectator = controlTypes.any { it == ControlPointType.SEPARATOR }
        val exceedsSprintFoxLimit = foxCount > 10
        val hasSprintControlShape = controlCount != null &&
            controlCount > 6 &&
            foxCount in 1..10 &&
            hasSpectator
        return buildList {
            if (
                !sourceNameSuggestsFoxoring &&
                !exceedsSprintFoxLimit &&
                (haystack.contains("sprint") || hasSprintControlShape)
            ) {
                add(RaceType.SPRINT)
            }
            if (haystack.containsFoxoringToken()) {
                add(RaceType.FOXORING)
            }
            if (haystack.contains("classic")) {
                add(RaceType.CLASSIC)
            }
            if (haystack.contains("orienteering")) {
                add(RaceType.ORIENTEERING)
            }
        }
    }

    fun requireGeneratorFormat(
        expected: RaceType,
        sourceName: String,
        courseData: DesktopCourseKmlData
    ) {
        val inferredTypes = inferredRaceTypes(sourceName, courseData).distinct()
        if (inferredTypes.isEmpty() || expected in inferredTypes) {
            return
        }
        val expectedText = expected.displayName()
        val detectedText = inferredTypes.joinToString(" or ") { it.displayName() }
        throw IllegalArgumentException(
            "$expectedText Route Generator expected a $expectedText course points file, " +
                "but $sourceName appears to be $detectedText."
        )
    }

    fun detectedGeneratorRaceType(sourceName: String, courseData: DesktopCourseKmlData): RaceType? =
        inferredRaceTypes(sourceName, courseData)
            .distinct()
            .filter { it in supportedGeneratorRaceTypes }
            .singleOrNull()

    fun RaceType.displayName(): String =
        name.lowercase().replaceFirstChar { it.titlecase(Locale.US) }

    private fun String.containsFoxoringToken(): Boolean =
        contains("foxoring") ||
            contains("fox-o") ||
            contains("fox o") ||
            Regex("""\bfoxo\b""").containsMatchIn(this)
}
