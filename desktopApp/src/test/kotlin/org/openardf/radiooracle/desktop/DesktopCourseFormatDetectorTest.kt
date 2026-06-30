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
import org.junit.Assert.assertNull
import org.junit.Test
import org.openardf.radiooracle.shared.domain.RaceType

class DesktopCourseFormatDetectorTest {
    @Test
    fun detectsSingleSupportedGeneratorTypeFromFileNameAndCourseElements() {
        assertEquals(
            RaceType.FOXORING,
            DesktopCourseFormatDetector.detectedGeneratorRaceType(
                "course-points.kml",
                courseData("Start", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "B", "Finish")
            )
        )
        assertEquals(
            RaceType.SPRINT,
            DesktopCourseFormatDetector.detectedGeneratorRaceType(
                "course-points.kml",
                courseData("Start", "1", "2", "3", "4", "5", "Spectator", "1F", "2F", "3F", "4F", "5F", "B", "Finish")
            )
        )
        assertEquals(
            RaceType.CLASSIC,
            DesktopCourseFormatDetector.detectedGeneratorRaceType(
                "course-points.kml",
                courseData("Start", "Fox 1", "Fox 2", "Fox 3", "Fox 4", "Fox 5", "B", "Finish")
            )
        )
        assertEquals(
            RaceType.SPRINT,
            DesktopCourseFormatDetector.detectedGeneratorRaceType(
                "course-points.kml",
                routeData(
                    controls = listOf("Start", "1", "2", "3", "4", "5", "1F", "2F", "3F", "4F", "5F", "B", "Finish"),
                    route = listOf("Start", "1", "2", "B", "1F", "2F", "B", "Finish")
                )
            )
        )
    }

    @Test
    fun returnsNullWhenGeneratorTypeIsAmbiguousOrUnknown() {
        assertNull(
            DesktopCourseFormatDetector.detectedGeneratorRaceType(
                "classic-sprint.kml",
                courseData("Start", "1", "2", "Spectator", "1F", "2F", "B", "Finish")
            )
        )
        assertNull(
            DesktopCourseFormatDetector.detectedGeneratorRaceType(
                "course-points.kml",
                courseData("Start", "1", "2", "3", "B", "Finish")
            )
        )
    }

    private fun courseData(vararg names: String): DesktopCourseKmlData =
        DesktopCourseKmlData(
            controls = names.mapIndexed { index, name ->
                CourseControlPoint(
                    name = name,
                    point = CourseGeoPoint(latitude = 45.0 + index * 0.001, longitude = -122.0)
                )
            },
            routes = emptyList()
        )

    private fun routeData(controls: List<String>, route: List<String>): DesktopCourseKmlData {
        val controlPoints = controls.mapIndexed { index, name ->
            CourseControlPoint(
                name = name,
                point = CourseGeoPoint(latitude = 45.0 + index * 0.001, longitude = -122.0)
            )
        }
        val pointsByName = controlPoints.associateBy { it.name }
        return DesktopCourseKmlData(
            controls = controlPoints,
            routes = listOf(
                CourseRoute(
                    name = "M21",
                    points = route.map { name -> requireNotNull(pointsByName[name]) { "Missing $name" }.point }
                )
            )
        )
    }
}
