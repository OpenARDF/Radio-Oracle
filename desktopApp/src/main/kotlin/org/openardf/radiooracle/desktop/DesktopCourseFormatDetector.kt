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
import org.openardf.radiooracle.shared.event.ControlRoleLabelRules
import java.util.Locale
import kotlin.math.roundToLong

object DesktopCourseFormatDetector {
    val supportedGeneratorRaceTypes: List<RaceType> = listOf(RaceType.CLASSIC, RaceType.FOXORING, RaceType.SPRINT)

    fun inferredRaceTypes(
        sourceName: String,
        courseData: DesktopCourseKmlData
    ): List<RaceType> {
        val pointShape = CoursePointShape.from(courseData)
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
            controlTypes = controlTypes,
            hasClassicCourseShape = pointShape.hasClassicCourseShape,
            hasFoxoringCourseShape = pointShape.hasFoxoringCourseShape,
            hasSprintRouteShape = pointShape.hasSprintRouteShape
        )
    }

    fun inferredRaceTypes(
        sourceName: String,
        clues: List<String>,
        controlCount: Int?,
        controlTypes: List<ControlPointType>,
        hasClassicCourseShape: Boolean = false,
        hasFoxoringCourseShape: Boolean = false,
        hasSprintRouteShape: Boolean = false
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
                (haystack.contains("sprint") || hasSprintControlShape || hasSprintRouteShape)
            ) {
                add(RaceType.SPRINT)
            }
            if (haystack.containsFoxoringToken() || (hasFoxoringCourseShape && !hasSprintRouteShape)) {
                add(RaceType.FOXORING)
            }
            if (haystack.contains("classic") || hasClassicCourseShape) {
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

    private data class CoursePointShape(
        val startCount: Int,
        val finishCount: Int,
        val foxCount: Int,
        val beaconCount: Int,
        val spectatorCount: Int,
        val hasSprintRouteShape: Boolean
    ) {
        val hasClassicCourseShape: Boolean =
            startCount == 1 &&
                finishCount == 1 &&
                foxCount == 5 &&
                beaconCount == 1 &&
                spectatorCount == 0

        val hasFoxoringCourseShape: Boolean =
            foxCount > 5 && spectatorCount == 0

        companion object {
            fun from(courseData: DesktopCourseKmlData): CoursePointShape {
                var startCount = 0
                var finishCount = 0
                var foxCount = 0
                var beaconCount = 0
                var spectatorCount = 0
                val shapedPoints = courseData.controls.mapNotNull { control ->
                    val role = pointRole(control.name) ?: return@mapNotNull null
                    ShapedCoursePoint(role, control.point)
                }
                courseData.controls.forEach { control ->
                    when (pointRole(control.name)) {
                        CoursePointRole.START -> startCount += 1
                        CoursePointRole.FINISH -> finishCount += 1
                        CoursePointRole.CONTROL -> foxCount += 1
                        CoursePointRole.BEACON -> beaconCount += 1
                        CoursePointRole.SPECTATOR -> spectatorCount += 1
                        null -> Unit
                    }
                }
                return CoursePointShape(
                    startCount = startCount,
                    finishCount = finishCount,
                    foxCount = foxCount,
                    beaconCount = beaconCount,
                    spectatorCount = spectatorCount,
                    hasSprintRouteShape = courseData.routes.any { route ->
                        route.hasSprintRouteShape(shapedPoints)
                    }
                )
            }

            private fun pointRole(name: String): CoursePointRole? =
                when {
                    DesktopCoursePointLabelClassifier.isCourseStartName(name) -> CoursePointRole.START
                    DesktopCoursePointLabelClassifier.isCourseFinishName(name) -> CoursePointRole.FINISH
                    else -> when (ControlRoleLabelRules.inferredRole(name)) {
                        ControlPointType.CONTROL -> CoursePointRole.CONTROL
                        ControlPointType.BEACON -> CoursePointRole.BEACON
                        ControlPointType.SEPARATOR -> CoursePointRole.SPECTATOR
                        null -> null
                    }
                }

            private fun CourseRoute.hasSprintRouteShape(points: List<ShapedCoursePoint>): Boolean {
                val roles = orderedRouteRoles(points)
                if (roles.firstOrNull() != CoursePointRole.START || roles.lastOrNull() != CoursePointRole.FINISH) {
                    return false
                }
                for (transitionIndex in 1 until roles.lastIndex) {
                    val transition = roles[transitionIndex]
                    if (transition != CoursePointRole.SPECTATOR && transition != CoursePointRole.BEACON) {
                        continue
                    }
                    val beforeTransition = roles.subList(1, transitionIndex)
                    val afterTransition = roles.subList(transitionIndex + 1, roles.lastIndex)
                    val finalBeaconIndex = afterTransition.indexOfLast { it == CoursePointRole.BEACON }
                    if (finalBeaconIndex < 0) {
                        continue
                    }
                    val secondLoopFoxes = afterTransition.take(finalBeaconIndex)
                    if (
                        beforeTransition.any { it == CoursePointRole.CONTROL } &&
                        secondLoopFoxes.any { it == CoursePointRole.CONTROL }
                    ) {
                        return true
                    }
                }
                return false
            }

            private fun CourseRoute.orderedRouteRoles(points: List<ShapedCoursePoint>): List<CoursePointRole> =
                this.points
                    .mapNotNull { routePoint ->
                        points
                            .map { shapedPoint -> shapedPoint to routePoint.distanceMetersTo(shapedPoint.point) }
                            .filter { (_, distance) -> distance <= ROUTE_POINT_TOLERANCE_METERS }
                            .minByOrNull { (_, distance) -> distance }
                            ?.first
                    }
                    .fold(mutableListOf<ShapedCoursePoint>()) { ordered, point ->
                        if (ordered.lastOrNull()?.routeKey != point.routeKey) {
                            ordered += point
                        }
                        ordered
                    }
                    .map { it.role }
        }
    }

    private data class ShapedCoursePoint(
        val role: CoursePointRole,
        val point: CourseGeoPoint
    ) {
        val routeKey: String =
            "$role|${(point.latitude * 10_000_000).roundToLong()}|${(point.longitude * 10_000_000).roundToLong()}"
    }

    private enum class CoursePointRole {
        START,
        FINISH,
        CONTROL,
        BEACON,
        SPECTATOR
    }

    private const val ROUTE_POINT_TOLERANCE_METERS = 50.0
}
