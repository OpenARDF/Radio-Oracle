package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.event.ProtectedCourseRoutePoint

/** Applies a calculated Course Analyzer route back into the saved Event File model. */
object DesktopCourseAnalysisApplier {
    fun applyCalculatedRoute(
        projectFile: EventProjectFile,
        courseInfo: ProtectedCourseInfo,
        application: DesktopCourseCalculatedRouteApplication,
        password: String
    ): Pair<EventProjectFile, ProtectedCourseInfo> {
        val trimmedPassword = password.trim()
        require(trimmedPassword.isNotEmpty()) {
            "Protected course password is required."
        }
        require(projectFile.raceData.categories.any { it.category.id == application.categoryId }) {
            "Category was not found: ${application.categoryId}"
        }

        val labelByControlId = application.foxAssignments.associate { assignment ->
            assignment.controlId to assignment.calculatedLabel
        }
        val updatedControls = projectFile.raceData.controls.map { control ->
            val calculatedLabel = labelByControlId[control.id]
            if (control.type == ControlPointType.CONTROL && calculatedLabel != null) {
                control.copy(publicLabel = calculatedLabel, latitude = null, longitude = null)
            } else if (control.latitude != null || control.longitude != null) {
                control.copy(latitude = null, longitude = null)
            } else {
                control
            }
        }
        val updatedCourseInfo = courseInfo.copy(
            idealOrder = application.idealOrderText,
            lengthMeters = application.routeLengthMeters,
            climbMeters = application.climbMeters,
            sourceName = "Course Analyzer calculated route",
            sourceSha256 = "",
            sampledPointCount = application.routePoints.size,
            route = application.routePoints.map { point ->
                ProtectedCourseRoutePoint(
                    latitude = point.latitude,
                    longitude = point.longitude,
                    elevationMeters = point.elevationMeters
                )
            },
            controlPoints = courseInfo.controlPoints.map { controlPoint ->
                labelByControlId[controlPoint.controlId]?.let { controlPoint.copy(label = it) } ?: controlPoint
            },
            courseObjects = courseInfo.courseObjects.map { courseObject ->
                labelByControlId[courseObject.id]?.let { courseObject.copy(label = it) } ?: courseObject
            }
        )
        val encryptedIdealOrder = DesktopProtectedCourseOrder.encrypt(application.idealOrderText, trimmedPassword)
        val encryptedCourseInfo = DesktopProtectedCourseOrder.encryptCourseInfo(updatedCourseInfo, trimmedPassword)
        val updatedCategories = projectFile.raceData.categories.map { categoryData ->
            if (categoryData.category.id == application.categoryId) {
                categoryData.copy(
                    category = categoryData.category.copy(
                        encryptedIdealOrder = encryptedIdealOrder,
                        encryptedCourseInfo = encryptedCourseInfo
                    )
                )
            } else {
                categoryData
            }
        }
        return projectFile.copy(
            raceData = projectFile.raceData.copy(
                controls = updatedControls,
                categories = updatedCategories
            )
        ) to updatedCourseInfo
    }
}
