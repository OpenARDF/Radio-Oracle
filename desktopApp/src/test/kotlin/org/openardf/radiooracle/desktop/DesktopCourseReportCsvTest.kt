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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.EventCategory
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventControl
import org.openardf.radiooracle.shared.event.EventControlPoint
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventRace
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.event.ProtectedCourseRoutePoint

class DesktopCourseReportCsvTest {
    @Test
    fun exportsUniqueRoutesLongestFirstWithExpandedControlColumns() {
        val projectFile = projectFile(
            categoryCourses = listOf(
                CategoryCourse("M21", listOf(170, 171, 187, 174, 173), order = 1),
                CategoryCourse("M40", listOf(170, 171, 187, 174, 173), order = 2),
                CategoryCourse("W70", listOf(181, 172, 175), order = 3)
            )
        )
        val protectedInfo = mapOf(
            "category-1" to ProtectedCourseInfo(lengthMeters = 4_200, climbMeters = 120),
            "category-2" to ProtectedCourseInfo(lengthMeters = 4_200, climbMeters = 120),
            "category-3" to ProtectedCourseInfo(lengthMeters = 2_800, climbMeters = 85)
        )

        val csv = DesktopCourseReportCsv.generate(
            projectFile = projectFile,
            protectedCourseInfoByCategoryId = protectedInfo
        )

        assertEquals(
            listOf(
                "Course,km,m,C1,C2,C3,C4,C5",
                "80m-Classic_1,4.2,120,170,171,173,174,187",
                "80m-Classic_2,2.8,85,172,175,181,,"
            ),
            csv.lineSequence().filter(String::isNotEmpty).toList()
        )
    }

    @Test
    fun combinesDifferentOrdersOfTheSameControlsAndSortsTheControlsAscending() {
        val projectFile = projectFile(
            raceType = RaceType.SPRINT,
            raceBand = RaceBand.NONE,
            categoryCourses = listOf(
                CategoryCourse("M21", listOf(170, 171, 187), order = 1, lengthMeters = 2_000),
                CategoryCourse("M40", listOf(170, 187, 171), order = 2, lengthMeters = 1_900)
            )
        )

        val rows = DesktopCourseReportCsv.rows(
            projectFile = projectFile
        )

        assertEquals(listOf("Sprint_1"), rows.map { it.courseName })
        assertEquals(listOf(170, 171, 187), rows.single().siControlCodes)
        assertEquals(2_000, rows.single().lengthMeters)
    }

    @Test
    fun calculatesMissingDistanceAndClimbFromStoredRouteGeometry() {
        val projectFile = projectFile(
            categoryCourses = listOf(CategoryCourse("M21", listOf(170), order = 1))
        )
        val routeInfo = ProtectedCourseInfo(
            route = listOf(
                ProtectedCourseRoutePoint(45.0, -122.0, 10.0),
                ProtectedCourseRoutePoint(45.001, -122.0, 15.0)
            )
        )

        val row = DesktopCourseReportCsv.rows(
            projectFile = projectFile,
            protectedCourseInfoByCategoryId = mapOf("category-1" to routeInfo)
        ).single()

        assertNotNull(row.lengthMeters)
        assertTrue(requireNotNull(row.lengthMeters) in 110..112)
        assertEquals(5, row.climbMeters)
    }

    private fun projectFile(
        categoryCourses: List<CategoryCourse>,
        raceType: RaceType = RaceType.CLASSIC,
        raceBand: RaceBand = RaceBand.M80
    ): EventProjectFile {
        val siCodes = categoryCourses.flatMap { it.siCodes }.distinct()
        val controls = siCodes.map { siCode ->
            EventControl(
                id = "control-$siCode",
                raceId = "race-1",
                label = siCode.toString(),
                siCode = siCode,
                type = ControlPointType.CONTROL,
                publicLabel = siCode.toString()
            )
        }
        val categories = categoryCourses.mapIndexed { index, course ->
            val categoryId = "category-${index + 1}"
            EventCategoryData(
                category = EventCategory(
                    id = categoryId,
                    raceId = "race-1",
                    name = course.name,
                    isMan = course.name.startsWith("M"),
                    maxAge = course.name.drop(1).toIntOrNull(),
                    lengthMeters = course.lengthMeters,
                    climbMeters = course.climbMeters,
                    order = course.order,
                    differentProperties = false,
                    raceType = null,
                    raceBand = null,
                    timeLimitSeconds = null,
                    controlPointsString = ""
                ),
                controlPoints = course.siCodes.mapIndexed { controlIndex, siCode ->
                    EventControlPoint(
                        id = "point-$categoryId-$controlIndex",
                        categoryId = categoryId,
                        siCode = siCode,
                        type = ControlPointType.CONTROL,
                        order = controlIndex + 1,
                        controlId = "control-$siCode"
                    )
                },
                competitors = emptyList()
            )
        }
        return EventProjectFile(
            raceData = EventRaceData(
                race = EventRace(
                    id = "race-1",
                    name = "Course Report Test",
                    apiKey = "",
                    startDateTimeIso = "2026-08-09T09:00:00",
                    raceType = raceType,
                    raceLevel = RaceLevel.PRACTICE,
                    raceBand = raceBand,
                    timeLimitSeconds = 7_200
                ),
                categories = categories,
                aliases = emptyList(),
                competitorData = emptyList(),
                unmatchedReadoutData = emptyList(),
                controls = controls
            )
        )
    }

    private data class CategoryCourse(
        val name: String,
        val siCodes: List<Int>,
        val order: Int,
        val lengthMeters: Int = 0,
        val climbMeters: Int = 0
    )
}
