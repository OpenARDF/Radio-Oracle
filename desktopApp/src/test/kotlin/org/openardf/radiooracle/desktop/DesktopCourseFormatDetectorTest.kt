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
                "foxoring-course.kml",
                courseData("Start", "1", "2", "3", "4", "5", "B", "Finish")
            )
        )
        assertEquals(
            RaceType.SPRINT,
            DesktopCourseFormatDetector.detectedGeneratorRaceType(
                "course-points.kml",
                courseData("Start", "1", "2", "3", "4", "5", "Spectator", "1F", "2F", "3F", "4F", "5F", "B", "Finish")
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
}
