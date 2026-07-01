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

import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventProjectSummary
import org.openardf.radiooracle.shared.event.EventValidationRules
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.results.EventResultSending

/** Read-only Race File and desktop-beta diagnostics shown in the Settings section. */
data class DesktopProjectDiagnostics(
    val projectState: String,
    val schemaText: String,
    val raceId: String,
    val raceName: String,
    val startDateTimeIso: String,
    val categoryCount: Int,
    val competitorCount: Int,
    val readoutCount: Int,
    val resultCount: Int,
    val validationState: String,
    val validationIssues: List<String>,
    val readinessIssues: List<String>,
    val liveResultPlanText: String,
    val betaLimitations: List<String>
) {
    companion object {
        fun from(
            projectFile: EventProjectFile?,
            protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo> = emptyMap()
        ): DesktopProjectDiagnostics {
            val summary = projectFile?.let(EventProjectSummary::from)
            val validationIssues = projectFile
                ?.let { EventValidationRules.validateRaceData(it.raceData) }
                ?.map(DesktopEventValidationText::messageFor)
                ?: emptyList()
            val readinessIssues = projectFile
                ?.let { readinessIssueText(it, protectedCourseInfoByCategoryId) }
                ?: emptyList()
            val sendPlan = projectFile?.let { EventResultSending.plan(it.raceData) }
            return DesktopProjectDiagnostics(
                projectState = if (projectFile == null) "No Race File open" else "Race File open",
                schemaText = projectFile?.let { "${it.appName} schema ${it.schemaVersion}" } ?: "",
                raceId = projectFile?.raceData?.race?.id ?: "",
                raceName = summary?.raceName ?: "",
                startDateTimeIso = projectFile?.raceData?.race?.startDateTimeIso ?: "",
                categoryCount = summary?.categoryCount ?: 0,
                competitorCount = summary?.competitorCount ?: 0,
                readoutCount = summary?.readoutCount ?: 0,
                resultCount = summary?.resultCount ?: 0,
                validationState = when {
                    projectFile == null -> "No Race File open"
                    validationIssues.isEmpty() && readinessIssues.isEmpty() -> "No validation issues"
                    readinessIssues.isEmpty() ->
                        "${validationIssues.size} validation issue${if (validationIssues.size == 1) "" else "s"}"
                    validationIssues.isEmpty() ->
                        "${readinessIssues.size} readiness issue${if (readinessIssues.size == 1) "" else "s"}"
                    else -> "${validationIssues.size} validation issue${if (validationIssues.size == 1) "" else "s"}; " +
                        "${readinessIssues.size} readiness issue${if (readinessIssues.size == 1) "" else "s"}"
                },
                validationIssues = validationIssues,
                readinessIssues = readinessIssues,
                liveResultPlanText = sendPlan?.let {
                    "${it.candidateCount} unsent matched result${if (it.candidateCount == 1) "" else "s"}; " +
                        "${it.alreadySentCount} already sent; " +
                        "${it.missingReadoutCount} competitor${if (it.missingReadoutCount == 1) "" else "s"} without readouts; " +
                        "${it.unmatchedReadoutCount} unmatched readout${if (it.unmatchedReadoutCount == 1) "" else "s"}."
                } ?: "No Race File open",
                betaLimitations = listOf(
                    "SPORTident station maintenance and reprogramming remain post-beta.",
                    "Desktop Bluetooth printer transport remains post-beta.",
                    "Full Android result-service configuration remains post-beta.",
                    "Race File storage is file-backed .rom.json, not shared SQL."
                )
            )
        }

        private fun readinessIssueText(
            projectFile: EventProjectFile,
            protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>
        ): List<String> {
            val competitorCountsByCategoryId = projectFile.raceData.competitorData
                .mapNotNull { data ->
                    data.competitorCategory.competitor.categoryId ?: data.competitorCategory.category?.id
                }
                .groupingBy { it }
                .eachCount()
            val controlsById = projectFile.raceData.controls.associateBy { it.id }
            val issues = mutableListOf<String>()

            projectFile.raceData.categories.forEach { categoryData ->
                val competitorCount = competitorCountsByCategoryId[categoryData.category.id] ?: 0
                val hasPublicCourse = categoryData.controlPoints.isNotEmpty() ||
                    categoryData.category.controlPointsString.isNotBlank()
                val protectedCourseInfo = protectedCourseInfoByCategoryId[categoryData.category.id]
                val hasStoredCourse = protectedCourseInfo != null ||
                    categoryData.category.encryptedCourseInfo?.isNotBlank() == true
                when {
                    competitorCount > 0 && !hasPublicCourse && !hasStoredCourse ->
                        issues += "Category ${categoryData.category.name} has $competitorCount competitor${if (competitorCount == 1) "" else "s"} but no course data."
                    competitorCount == 0 && (hasPublicCourse || hasStoredCourse) ->
                        issues += "Category ${categoryData.category.name} has course data but no competitors; it will be ignored by Race Ops and results."
                }

                if (protectedCourseInfo != null) {
                    val assignedControlIds = categoryData.controlPoints.map { it.controlId }.toSet() +
                        categoryData.publicControlIds.toSet()
                    val storedControlIds = protectedCourseInfo.controlPoints.map { it.controlId }.toSet()
                    if (assignedControlIds.isNotEmpty() && storedControlIds.isNotEmpty() && assignedControlIds != storedControlIds) {
                        issues += "Category ${categoryData.category.name} assigned controls do not match the stored route controls."
                    }
                    val missingControlIds = storedControlIds.filterNot { it in controlsById.keys }
                    if (missingControlIds.isNotEmpty()) {
                        issues += "Category ${categoryData.category.name} stored course references missing controls: ${missingControlIds.joinToString()}."
                    }
                    val changedControlLabels = protectedCourseInfo.controlPoints.mapNotNull { storedControl ->
                        val control = controlsById[storedControl.controlId] ?: return@mapNotNull null
                        val currentLabel = control.publicLabel?.trim()?.takeIf { it.isNotEmpty() } ?: control.label
                        storedControl.label.takeIf {
                            storedControl.type != control.type || it != currentLabel
                        }
                    }
                    if (changedControlLabels.isNotEmpty()) {
                        issues += "Category ${categoryData.category.name} has stored route controls whose label or type changed after route import: ${changedControlLabels.distinct().joinToString()}."
                    }
                }
            }

            return issues
        }
    }
}
