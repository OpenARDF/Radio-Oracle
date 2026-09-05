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

import org.openardf.radiooracle.shared.event.EventControl
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.event.ProtectedCourseRoutePoint
import org.openardf.radiooracle.shared.event.ProtectedIdealOrderRules

/** Applies a calculated Course Analyzer route back into the saved Race File model. */
object DesktopCourseAnalysisApplier {
    fun applyCalculatedRoute(
        projectFile: EventProjectFile,
        courseInfo: ProtectedCourseInfo,
        application: DesktopCourseCalculatedRouteApplication,
        password: String?
    ): DesktopCourseCalculatedRouteApplyResult {
        val storagePassword = projectFile.courseDataPassword(password)
        require(projectFile.raceData.categories.any { it.category.id == application.categoryId }) {
            "Category was not found: ${application.categoryId}"
        }

        val labelByControlId = application.foxAssignments.associate { assignment ->
            assignment.controlId to assignment.calculatedLabel
        }
        val updatedControls = projectFile.raceData.controls.withoutPublicCoordinates()
        val updatedControlsById = updatedControls.associateBy { it.id }
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
            }
        ).withUpdatedProtectedLabels(labelByControlId, markAnalyzerSavedNumbering = false)
        val sameCourseCategoryIds = DesktopCourseAnalyzer
            .sameCourseCategories(projectFile, application.categoryId)
            .mapTo(mutableSetOf()) { it.category.id }
        var updatedProject = projectFile.copy(raceData = projectFile.raceData.copy(controls = updatedControls))
        projectFile.raceData.categories.forEach { categoryData ->
            if (categoryData.category.id == application.categoryId) {
                updatedProject = updatedProject.withStoredIdealOrder(
                    categoryData.category.id,
                    application.idealOrderText,
                    storagePassword
                )
                updatedProject = updatedProject.withStoredCourseInfo(
                    categoryData.category.id,
                    updatedCourseInfo,
                    storagePassword
                )
            } else {
                val hasSameCourse = categoryData.category.id in sameCourseCategoryIds
                val updatedOtherIdealOrder = if (hasSameCourse) {
                    application.idealOrderText
                } else {
                    categoryData.category
                        .takeIf { it.encryptedIdealOrder?.isNotBlank() == true || it.idealOrder != null }
                        ?.storedIdealOrder(storagePassword)
                        ?.takeIf(String::isNotBlank)
                        ?.let { idealOrderText ->
                            val resolvedControlIds = runCatching {
                                ProtectedIdealOrderRules.resolveControlIds(idealOrderText, projectFile.raceData.controls)
                            }.getOrElse { error ->
                                throw IllegalArgumentException(
                                    "Stored ideal order could not be updated for ${categoryData.category.name}: ${error.message ?: error::class.simpleName}"
                                )
                            }
                            resolvedControlIds.joinToString(" ") { controlId ->
                                labelByControlId[controlId]
                                    ?.let(::quoteIdealOrderToken)
                                    ?: updatedControlsById[controlId]
                                        ?.idealOrderToken(updatedControls)
                                    ?: throw IllegalArgumentException("Stored ideal order control could not be preserved: $controlId")
                            }
                        }
                }
                val updatedOtherCourseInfo = categoryData.category
                    .takeIf { it.encryptedCourseInfo?.isNotBlank() == true || it.courseInfo != null }
                    ?.storedCourseInfo(storagePassword)
                    ?.let { storedCourseInfo ->
                        storedCourseInfo
                            .withUpdatedProtectedLabels(labelByControlId, markAnalyzerSavedNumbering = true)
                            .let { updatedInfo ->
                                if (hasSameCourse) {
                                    updatedInfo.copy(idealOrder = application.idealOrderText)
                                } else {
                                    updatedInfo
                                }
                            }
                }
                if (updatedOtherIdealOrder != null) {
                    updatedProject = updatedProject.withStoredIdealOrder(
                        categoryData.category.id,
                        updatedOtherIdealOrder,
                        storagePassword
                    )
                }
                if (updatedOtherCourseInfo != null) {
                    updatedProject = updatedProject.withStoredCourseInfo(
                        categoryData.category.id,
                        updatedOtherCourseInfo,
                        storagePassword
                    )
                }
            }
        }
        return DesktopCourseCalculatedRouteApplyResult(
            projectFile = updatedProject,
            courseInfo = updatedCourseInfo,
            affectedCategoryCount = projectFile.raceData.categories.count { it.category.id in sameCourseCategoryIds }
        )
    }

    fun applyFoxRenumberingOnly(
        projectFile: EventProjectFile,
        renumbering: DesktopCourseWaitRenumbering,
        password: String?
    ): DesktopCourseFoxRenumberingApplyResult {
        val storagePassword = projectFile.courseDataPassword(password)
        require(renumbering.improvesWait) {
            "No improved fox renumbering was calculated."
        }
        val labelByControlId = renumbering.assignments
            .filter { it.suggestedSlotLabel.isNotBlank() && it.suggestedSlotLabel != it.currentSlotLabel }
            .associate { it.controlId to it.suggestedSlotLabel }
        require(labelByControlId.isNotEmpty()) {
            "No fox numbering changes were calculated."
        }

        val updatedControls = projectFile.raceData.controls.withoutPublicCoordinates()
        val updatedControlsById = updatedControls.associateBy { it.id }

        val updatedInfoByCategoryId = mutableMapOf<String, ProtectedCourseInfo>()
        val updatedIdealOrderByCategoryId = mutableMapOf<String, String>()
        projectFile.raceData.categories.forEach { categoryData ->
            categoryData.category
                .takeIf { it.encryptedIdealOrder?.isNotBlank() == true || it.idealOrder != null }
                ?.storedIdealOrder(storagePassword)
                ?.takeIf(String::isNotBlank)
                ?.let { idealOrderText ->
                val resolvedControlIds = runCatching {
                    ProtectedIdealOrderRules.resolveControlIds(idealOrderText, projectFile.raceData.controls)
                }.getOrElse { error ->
                    throw IllegalArgumentException(
                        "Stored ideal order could not be updated for ${categoryData.category.name}: ${error.message ?: error::class.simpleName}"
                    )
                }
                val updatedIdealOrderText = resolvedControlIds
                    .map { controlId ->
                        labelByControlId[controlId]
                            ?.let(::quoteIdealOrderToken)
                            ?: updatedControlsById[controlId]
                                ?.idealOrderToken(updatedControls)
                            ?: throw IllegalArgumentException("Stored ideal order control could not be preserved: $controlId")
                    }
                    .joinToString(" ")
                updatedIdealOrderByCategoryId[categoryData.category.id] = updatedIdealOrderText
            }

            categoryData.category.storedCourseInfo(storagePassword)?.let { courseInfo ->
                val referencesChangedControl =
                    courseInfo.controlPoints.any { it.controlId in labelByControlId.keys } ||
                        courseInfo.courseObjects.any { it.id in labelByControlId.keys }
                if (referencesChangedControl) {
                    val updatedInfo = courseInfo.withUpdatedProtectedLabels(labelByControlId, markAnalyzerSavedNumbering = true)
                    updatedInfoByCategoryId[categoryData.category.id] = updatedInfo
                }
            }
        }

        var updatedProject = projectFile.copy(raceData = projectFile.raceData.copy(controls = updatedControls))
        projectFile.raceData.categories.forEach { categoryData ->
            val categoryId = categoryData.category.id
            updatedIdealOrderByCategoryId[categoryId]?.let { updatedIdealOrder ->
                updatedProject = updatedProject.withStoredIdealOrder(
                    categoryId,
                    updatedIdealOrder,
                    storagePassword
                )
            }
            updatedInfoByCategoryId[categoryId]?.let { updatedInfo ->
                updatedProject = updatedProject.withStoredCourseInfo(categoryId, updatedInfo, storagePassword)
            }
        }

        return DesktopCourseFoxRenumberingApplyResult(
            projectFile = updatedProject,
            courseInfoByCategoryId = updatedInfoByCategoryId,
            changedControlCount = labelByControlId.size,
            affectedCategoryCount = (updatedInfoByCategoryId.keys + updatedIdealOrderByCategoryId.keys).size
        )
    }
}

private fun ProtectedCourseInfo.withUpdatedProtectedLabels(
    labelByControlId: Map<String, String>,
    markAnalyzerSavedNumbering: Boolean
): ProtectedCourseInfo {
    val descriptionByControlNumber = (controlPoints.map { it.label to it.description } +
        courseObjects.map { it.label to it.description })
        .unambiguousCourseDescriptionsByIdentity()

    fun movedDescription(label: String, currentDescription: String?): String? =
        descriptionByControlNumber[label.courseDescriptionIdentityKey()] ?: currentDescription

    return copy(
        sourceName = if (markAnalyzerSavedNumbering && !sourceName.startsWith("Course Analyzer", ignoreCase = true)) {
            "Course Analyzer fox renumbering"
        } else {
            sourceName
        },
        controlPoints = controlPoints.map { controlPoint ->
            val updatedLabel = labelByControlId[controlPoint.controlId] ?: controlPoint.label
            controlPoint.copy(
                label = updatedLabel,
                description = movedDescription(updatedLabel, controlPoint.description)
            )
        },
        courseObjects = courseObjects.map { courseObject ->
            val updatedLabel = labelByControlId[courseObject.id] ?: courseObject.label
            courseObject.copy(
                label = updatedLabel,
                description = movedDescription(updatedLabel, courseObject.description)
            )
        }
    )
}

private fun List<EventControl>.withoutPublicCoordinates(): List<EventControl> =
    map { control ->
        if (control.latitude != null || control.longitude != null) {
            control.copy(latitude = null, longitude = null)
        } else {
            control
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

data class DesktopCourseCalculatedRouteApplyResult(
    val projectFile: EventProjectFile,
    val courseInfo: ProtectedCourseInfo,
    val affectedCategoryCount: Int
)
