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

import org.openardf.radiooracle.shared.event.withResultControlLabels
import org.openardf.radiooracle.shared.event.EventControl
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.event.ProtectedIdealOrderRules

/** Applies a calculated Course Analyzer route back into the saved Race File model. */
object DesktopCourseAnalysisApplier {
    fun prepare(project: EventProjectFile, selections: List<DesktopCourseRouteSelection>, password: String?): DesktopPreparedCourseDesign =
        prepareCourseDesign(project, selections, password)

    fun prepareAll(project: EventProjectFile, accepted: DesktopCourseRouteSelection,
                   reviewedBindingsByCategoryId: Map<String, Map<String, String>>, password: String?,
                   elevationLookup: (CourseGeoPoint) -> Double? = { null }, checkCancelled: () -> Unit = {}): DesktopPreparedCourseDesign =
        prepareAllCourseDesigns(project, accepted, reviewedBindingsByCategoryId, password, elevationLookup, checkCancelled)

    fun commit(project: EventProjectFile, prepared: DesktopPreparedCourseDesign): EventProjectFile =
        org.openardf.radiooracle.shared.event.EventCourseDrafts.commit(project, prepared.candidate, prepared.expectedCandidateHash)

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
                            ?.let(ProtectedIdealOrderRules::quoteToken)
                            ?: updatedControlsById[controlId]
                                ?.let { ProtectedIdealOrderRules.formatControlIds(listOf(it.id), updatedControls) }
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
                    val updatedInfo = courseInfo.withUpdatedProtectedLabels(labelByControlId, markAnalyzerSavedNumbering = true, projectFile.raceData.controls)
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
    markAnalyzerSavedNumbering: Boolean,
    resultControls: List<EventControl> = emptyList()
): ProtectedCourseInfo {
    val descriptionByControlNumber = (controlPoints.map { it.label to it.description } +
        courseObjects.map { it.label to it.description })
        .unambiguousCourseDescriptionsByIdentity()

    fun movedDescription(label: String, currentDescription: String?): String? =
        descriptionByControlNumber[label.courseDescriptionIdentityKey()] ?: currentDescription

    return withResultControlLabels(resultControls).copy(
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

data class DesktopCourseFoxRenumberingApplyResult(
    val projectFile: EventProjectFile,
    val courseInfoByCategoryId: Map<String, ProtectedCourseInfo>,
    val changedControlCount: Int,
    val affectedCategoryCount: Int
)
