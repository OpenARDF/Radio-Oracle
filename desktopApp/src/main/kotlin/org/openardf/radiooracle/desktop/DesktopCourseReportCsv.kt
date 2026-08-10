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

import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventRace
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.event.toDisplayLabel
import java.math.BigDecimal
import kotlin.math.roundToInt

data class DesktopCourseReportRow(
    val courseName: String,
    val lengthMeters: Int?,
    val climbMeters: Int?,
    val siControlCodes: List<Int>
)

object DesktopCourseReportCsv {
    fun rows(
        projectFile: EventProjectFile,
        protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo> = emptyMap()
    ): List<DesktopCourseReportRow> {
        val raceData = projectFile.raceData
        val candidates = raceData.categories.mapNotNull { categoryData ->
            val categoryId = categoryData.category.id
            val protectedCourseInfo = protectedCourseInfoByCategoryId[categoryId]
            val siControlCodes = categoryData.assignedSiControlCodes(projectFile)
                .distinct()
                .sorted()
            if (siControlCodes.isEmpty()) {
                return@mapNotNull null
            }
            val calculatedMetrics = protectedCourseInfo
                ?.route
                ?.takeIf { it.size >= 2 }
                ?.map { point ->
                    CourseGeoPoint(
                        latitude = point.latitude,
                        longitude = point.longitude,
                        elevationMeters = point.elevationMeters
                    )
                }
                ?.let(DesktopCourseRouteMetricsCalculator::metrics)
            DesktopCourseReportCandidate(
                categoryData = categoryData,
                lengthMeters = protectedCourseInfo?.lengthMeters
                    ?: calculatedMetrics?.horizontalLengthMeters?.roundToInt()
                    ?: categoryData.category.lengthMeters.takeIf { it > 0 },
                climbMeters = protectedCourseInfo?.climbMeters
                    ?: calculatedMetrics?.climbMeters?.roundToInt()
                    ?: categoryData.category.climbMeters.takeIf {
                        categoryData.category.lengthMeters > 0 || it > 0
                    },
                siControlCodes = siControlCodes
            )
        }
        val uniqueRoutes = candidates
            .groupBy(DesktopCourseReportCandidate::siControlCodes)
            .map { (_, matches) ->
                matches.maxWithOrNull(
                    compareBy<DesktopCourseReportCandidate> { it.metricCompleteness }
                        .thenBy { it.lengthMeters ?: Int.MIN_VALUE }
                        .thenBy { -it.categoryData.category.order }
                ) ?: error("Course report route group cannot be empty.")
            }
            .sortedWith(
                compareByDescending<DesktopCourseReportCandidate> { it.lengthMeters ?: Int.MIN_VALUE }
                    .thenBy { it.categoryData.category.order }
                    .thenBy { it.categoryData.category.name }
                    .thenBy { it.siControlCodes.joinToString(",") }
            )
        val prefix = raceData.race.courseReportPrefix()
        return uniqueRoutes.mapIndexed { index, route ->
            DesktopCourseReportRow(
                courseName = "${prefix}_${index + 1}",
                lengthMeters = route.lengthMeters,
                climbMeters = route.climbMeters,
                siControlCodes = route.siControlCodes
            )
        }
    }

    fun generate(
        projectFile: EventProjectFile,
        protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo> = emptyMap()
    ): String {
        val rows = rows(
            projectFile = projectFile,
            protectedCourseInfoByCategoryId = protectedCourseInfoByCategoryId
        )
        val controlColumnCount = rows.maxOfOrNull { it.siControlCodes.size } ?: 0
        val header = buildList {
            add("Course")
            add("km")
            add("m")
            repeat(controlColumnCount) { index -> add("C${index + 1}") }
        }
        return buildList {
            add(header.toCsvLine())
            rows.forEach { row ->
                add(
                    buildList {
                        add(row.courseName)
                        add(row.lengthMeters?.let(::kilometersText).orEmpty())
                        add(row.climbMeters?.toString().orEmpty())
                        addAll(row.siControlCodes.map(Int::toString))
                        repeat(controlColumnCount - row.siControlCodes.size) { add("") }
                    }.toCsvLine()
                )
            }
        }.joinToString(separator = "\r\n", postfix = "\r\n")
    }

    private fun EventCategoryData.assignedSiControlCodes(projectFile: EventProjectFile): List<Int> {
        val controlsById = projectFile.raceData.controls.associateBy { it.id }
        return controlPoints
            .sortedBy { it.order }
            .mapNotNull { controlPoint -> controlsById[controlPoint.controlId]?.siCode }
    }

    private fun EventRace.courseReportPrefix(): String {
        val typeLabel = raceType.toDisplayLabel().replace(' ', '-')
        return when (raceType) {
            RaceType.CLASSIC -> when (raceBand) {
                RaceBand.M80 -> "80m-$typeLabel"
                RaceBand.M2 -> "2m-$typeLabel"
                else -> typeLabel
            }
            else -> typeLabel
        }
    }

    private fun kilometersText(lengthMeters: Int): String =
        BigDecimal.valueOf(lengthMeters.toLong(), 3).stripTrailingZeros().toPlainString()

    private fun List<String>.toCsvLine(): String = joinToString(",") { value ->
        if (value.any { it == ',' || it == '"' || it == '\r' || it == '\n' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
}

private data class DesktopCourseReportCandidate(
    val categoryData: EventCategoryData,
    val lengthMeters: Int?,
    val climbMeters: Int?,
    val siControlCodes: List<Int>
) {
    val metricCompleteness: Int
        get() = listOf(lengthMeters, climbMeters).count { it != null }
}
