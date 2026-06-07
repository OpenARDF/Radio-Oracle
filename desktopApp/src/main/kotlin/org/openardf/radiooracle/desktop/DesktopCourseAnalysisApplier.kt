package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.event.EventControl
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.event.ProtectedCourseRoutePoint
import org.openardf.radiooracle.shared.event.ProtectedIdealOrderRules

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

    fun applyFoxRenumberingOnly(
        projectFile: EventProjectFile,
        renumbering: DesktopCourseWaitRenumbering,
        password: String
    ): DesktopCourseFoxRenumberingApplyResult {
        val trimmedPassword = password.trim()
        require(trimmedPassword.isNotEmpty()) {
            "Protected course password is required."
        }
        require(renumbering.improvesWait) {
            "No improved fox renumbering was calculated."
        }
        val labelByControlId = renumbering.assignments
            .filter { it.suggestedSlotLabel.isNotBlank() && it.suggestedSlotLabel != it.currentSlotLabel }
            .associate { it.controlId to it.suggestedSlotLabel }
        require(labelByControlId.isNotEmpty()) {
            "No fox numbering changes were calculated."
        }

        val updatedControls = projectFile.raceData.controls.map { control ->
            val calculatedLabel = labelByControlId[control.id]
            when {
                control.type == ControlPointType.CONTROL && calculatedLabel != null ->
                    control.copy(publicLabel = calculatedLabel, latitude = null, longitude = null)
                control.latitude != null || control.longitude != null ->
                    control.copy(latitude = null, longitude = null)
                else -> control
            }
        }
        val updatedControlsById = updatedControls.associateBy { it.id }

        val updatedInfoByCategoryId = mutableMapOf<String, ProtectedCourseInfo>()
        val encryptedInfoByCategoryId = mutableMapOf<String, String>()
        val encryptedIdealOrderByCategoryId = mutableMapOf<String, String?>()
        projectFile.raceData.categories.forEach { categoryData ->
            categoryData.category.encryptedIdealOrder?.takeIf { it.isNotBlank() }?.let { encryptedIdealOrder ->
                val idealOrderText = DesktopProtectedCourseOrder.decrypt(encryptedIdealOrder, trimmedPassword)
                val resolvedControlIds = runCatching {
                    ProtectedIdealOrderRules.resolveControlIds(idealOrderText, projectFile.raceData.controls)
                }.getOrElse { error ->
                    throw IllegalArgumentException(
                        "Protected ideal order could not be updated for ${categoryData.category.name}: ${error.message ?: error::class.simpleName}"
                    )
                }
                val updatedIdealOrderText = resolvedControlIds
                    .map { controlId ->
                        updatedControlsById[controlId]
                            ?: throw IllegalArgumentException("Protected ideal order control could not be preserved: $controlId")
                    }
                    .joinToString(" ") { it.idealOrderToken(updatedControls) }
                encryptedIdealOrderByCategoryId[categoryData.category.id] =
                    DesktopProtectedCourseOrder.encrypt(updatedIdealOrderText, trimmedPassword)
            }

            categoryData.category.encryptedCourseInfo?.takeIf { it.isNotBlank() }?.let { encryptedCourseInfo ->
                val courseInfo = DesktopProtectedCourseOrder.decryptCourseInfo(encryptedCourseInfo, trimmedPassword)
                val referencesChangedControl =
                    courseInfo.controlPoints.any { it.controlId in labelByControlId.keys } ||
                        courseInfo.courseObjects.any { it.id in labelByControlId.keys }
                if (referencesChangedControl) {
                    val updatedInfo = courseInfo.copy(
                        controlPoints = courseInfo.controlPoints.map { controlPoint ->
                            labelByControlId[controlPoint.controlId]?.let { controlPoint.copy(label = it) } ?: controlPoint
                        },
                        courseObjects = courseInfo.courseObjects.map { courseObject ->
                            labelByControlId[courseObject.id]?.let { courseObject.copy(label = it) } ?: courseObject
                        }
                    )
                    updatedInfoByCategoryId[categoryData.category.id] = updatedInfo
                    encryptedInfoByCategoryId[categoryData.category.id] =
                        DesktopProtectedCourseOrder.encryptCourseInfo(updatedInfo, trimmedPassword)
                }
            }
        }

        val updatedCategories = projectFile.raceData.categories.map { categoryData ->
            val categoryId = categoryData.category.id
            val hasUpdatedIdealOrder = encryptedIdealOrderByCategoryId.containsKey(categoryId)
            val encryptedCourseInfo = encryptedInfoByCategoryId[categoryId]
            if (hasUpdatedIdealOrder || encryptedCourseInfo != null) {
                categoryData.copy(
                    category = categoryData.category.copy(
                        encryptedIdealOrder = if (hasUpdatedIdealOrder) {
                            encryptedIdealOrderByCategoryId[categoryId]
                        } else {
                            categoryData.category.encryptedIdealOrder
                        },
                        encryptedCourseInfo = encryptedCourseInfo ?: categoryData.category.encryptedCourseInfo
                    )
                )
            } else {
                categoryData
            }
        }

        return DesktopCourseFoxRenumberingApplyResult(
            projectFile = projectFile.copy(
                raceData = projectFile.raceData.copy(
                    controls = updatedControls,
                    categories = updatedCategories
                )
            ),
            courseInfoByCategoryId = updatedInfoByCategoryId,
            changedControlCount = labelByControlId.size,
            affectedCategoryCount = (updatedInfoByCategoryId.keys + encryptedIdealOrderByCategoryId.keys).size
        )
    }
}

private fun EventControl.idealOrderToken(controls: List<EventControl>): String {
    val candidates = listOfNotNull(
        publicLabel?.trim()?.takeIf { it.isNotEmpty() },
        label.trim().takeIf { it.isNotEmpty() },
        siCode.toString()
    ).distinct()
    return candidates
        .map { token -> quoteIdealOrderToken(token) }
        .firstOrNull { token ->
            runCatching { ProtectedIdealOrderRules.resolveControlIds(token, controls) == listOf(id) }
                .getOrDefault(false)
        }
        ?: label.trim()
}

private fun quoteIdealOrderToken(token: String): String {
    val needsQuoting = token.any { it.isWhitespace() || it == ',' || it == ';' }
    return when {
        !needsQuoting -> token
        '\'' !in token -> "'$token'"
        '"' !in token -> "\"$token\""
        else -> token
    }
}

data class DesktopCourseFoxRenumberingApplyResult(
    val projectFile: EventProjectFile,
    val courseInfoByCategoryId: Map<String, ProtectedCourseInfo>,
    val changedControlCount: Int,
    val affectedCategoryCount: Int
)
