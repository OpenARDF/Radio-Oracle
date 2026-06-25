package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.RaceType
import java.util.Locale

object DesktopCourseFormatDetector {
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
            "$expectedText Course Generator expected a $expectedText course points file, " +
                "but $sourceName appears to be $detectedText."
        )
    }

    fun RaceType.displayName(): String =
        name.lowercase().replaceFirstChar { it.titlecase(Locale.US) }

    private fun String.containsFoxoringToken(): Boolean =
        contains("foxoring") ||
            contains("fox-o") ||
            contains("fox o") ||
            Regex("""\bfoxo\b""").containsMatchIn(this)
}
