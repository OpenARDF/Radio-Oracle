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
        password: String
    ): Pair<EventProjectFile, ProtectedCourseInfo> {
        val trimmedPassword = password.trim()
        require(trimmedPassword.isNotEmpty()) {
            "Race Password is required."
        }
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
                val updatedOtherIdealOrder = categoryData.category.encryptedIdealOrder
                    ?.takeIf { it.isNotBlank() }
                    ?.let { encryptedValue ->
                        val idealOrderText = DesktopProtectedCourseOrder.decrypt(encryptedValue, trimmedPassword)
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
                val updatedOtherCourseInfo = categoryData.category.encryptedCourseInfo
                    ?.takeIf { it.isNotBlank() }
                    ?.let { encryptedValue ->
                        DesktopProtectedCourseOrder.decryptCourseInfo(encryptedValue, trimmedPassword)
                            .withUpdatedProtectedLabels(labelByControlId, markAnalyzerSavedNumbering = true)
                    }
                if (updatedOtherIdealOrder == null && updatedOtherCourseInfo == null) {
                    categoryData
                } else {
                    categoryData.copy(
                        category = categoryData.category.copy(
                            encryptedIdealOrder = updatedOtherIdealOrder
                                ?.let { DesktopProtectedCourseOrder.encrypt(it, trimmedPassword) }
                                ?: categoryData.category.encryptedIdealOrder,
                            encryptedCourseInfo = updatedOtherCourseInfo
                                ?.let { DesktopProtectedCourseOrder.encryptCourseInfo(it, trimmedPassword) }
                                ?: categoryData.category.encryptedCourseInfo
                        )
                    )
                }
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
            "Race Password is required."
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

        val updatedControls = projectFile.raceData.controls.withoutPublicCoordinates()
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
                encryptedIdealOrderByCategoryId[categoryData.category.id] =
                    DesktopProtectedCourseOrder.encrypt(updatedIdealOrderText, trimmedPassword)
            }

            categoryData.category.encryptedCourseInfo?.takeIf { it.isNotBlank() }?.let { encryptedCourseInfo ->
                val courseInfo = DesktopProtectedCourseOrder.decryptCourseInfo(encryptedCourseInfo, trimmedPassword)
                val referencesChangedControl =
                    courseInfo.controlPoints.any { it.controlId in labelByControlId.keys } ||
                        courseInfo.courseObjects.any { it.id in labelByControlId.keys }
                if (referencesChangedControl) {
                    val updatedInfo = courseInfo.withUpdatedProtectedLabels(labelByControlId, markAnalyzerSavedNumbering = true)
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

private fun ProtectedCourseInfo.withUpdatedProtectedLabels(
    labelByControlId: Map<String, String>,
    markAnalyzerSavedNumbering: Boolean
): ProtectedCourseInfo =
    copy(
        sourceName = if (markAnalyzerSavedNumbering && !sourceName.startsWith("Course Analyzer", ignoreCase = true)) {
            "Course Analyzer fox renumbering"
        } else {
            sourceName
        },
        controlPoints = controlPoints.map { controlPoint ->
            labelByControlId[controlPoint.controlId]?.let { controlPoint.copy(label = it) } ?: controlPoint
        },
        courseObjects = courseObjects.map { courseObject ->
            labelByControlId[courseObject.id]?.let { courseObject.copy(label = it) } ?: courseObject
        }
    )

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
