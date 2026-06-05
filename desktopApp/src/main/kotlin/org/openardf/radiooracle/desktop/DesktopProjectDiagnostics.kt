package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventProjectSummary
import org.openardf.radiooracle.shared.event.EventValidationIssue
import org.openardf.radiooracle.shared.event.EventValidationRules
import org.openardf.radiooracle.shared.results.EventResultSending

/** Read-only Event File and desktop-beta diagnostics shown in the Settings section. */
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
    val liveResultPlanText: String,
    val betaLimitations: List<String>
) {
    companion object {
        fun from(projectFile: EventProjectFile?): DesktopProjectDiagnostics {
            val summary = projectFile?.let(EventProjectSummary::from)
            val validationIssues = projectFile
                ?.let { EventValidationRules.validateRaceData(it.raceData) }
                ?.map(::validationIssueText)
                ?: emptyList()
            val sendPlan = projectFile?.let { EventResultSending.plan(it.raceData) }
            return DesktopProjectDiagnostics(
                projectState = if (projectFile == null) "No Event File open" else "Event File open",
                schemaText = projectFile?.let { "${it.appName} schema ${it.schemaVersion}" } ?: "",
                raceId = projectFile?.raceData?.race?.id ?: "",
                raceName = summary?.raceName ?: "",
                startDateTimeIso = projectFile?.raceData?.race?.startDateTimeIso ?: "",
                categoryCount = summary?.categoryCount ?: 0,
                competitorCount = summary?.competitorCount ?: 0,
                readoutCount = summary?.readoutCount ?: 0,
                resultCount = summary?.resultCount ?: 0,
                validationState = when {
                    projectFile == null -> "No Event File open"
                    validationIssues.isEmpty() -> "No validation issues"
                    else -> "${validationIssues.size} validation issue${if (validationIssues.size == 1) "" else "s"}"
                },
                validationIssues = validationIssues,
                liveResultPlanText = sendPlan?.let {
                    "${it.candidateCount} unsent matched result${if (it.candidateCount == 1) "" else "s"}; " +
                        "${it.alreadySentCount} already sent; " +
                        "${it.missingReadoutCount} competitor${if (it.missingReadoutCount == 1) "" else "s"} without readouts; " +
                        "${it.unmatchedReadoutCount} unmatched readout${if (it.unmatchedReadoutCount == 1) "" else "s"}."
                } ?: "No Event File open",
                betaLimitations = listOf(
                    "SPORTident station maintenance and reprogramming remain post-beta.",
                    "Desktop Bluetooth printer transport remains post-beta.",
                    "Full Android result-service configuration remains post-beta.",
                    "Event File storage is file-backed .rom.json, not shared SQL."
                )
            )
        }

        private fun validationIssueText(issue: EventValidationIssue): String =
            when (issue) {
                EventValidationIssue.BlankRaceName -> "Event name is blank."
                is EventValidationIssue.DuplicateCategoryNames ->
                    "Duplicate category names: ${issue.names.joinToString()}."
                is EventValidationIssue.DuplicateAliasNames ->
                    "Duplicate alias names: ${issue.names.joinToString()}."
                is EventValidationIssue.DuplicateAliasCodes ->
                    "Duplicate alias SI codes: ${issue.codes.joinToString()}."
                is EventValidationIssue.DuplicateControlIds ->
                    "Duplicate control IDs: ${issue.ids.joinToString()}."
                is EventValidationIssue.DuplicateControlLabels ->
                    "Duplicate control labels: ${issue.labels.joinToString()}."
                is EventValidationIssue.DuplicateStartNumbers ->
                    "Duplicate start numbers: ${issue.startNumbers.joinToString()}."
                is EventValidationIssue.DuplicateSINumbers ->
                    "Duplicate SI numbers: ${issue.siNumbers.joinToString()}."
                is EventValidationIssue.MultipleStartPunches ->
                    "Readout has multiple start punches: ${issue.siNumber ?: "unknown SI"}."
                is EventValidationIssue.MultipleFinishPunches ->
                    "Readout has multiple finish punches: ${issue.siNumber ?: "unknown SI"}."
                is EventValidationIssue.LegacyIncompatibleCategoryControlCodes ->
                    "Category ${issue.categoryName} uses control codes above 255: ${issue.codes.joinToString()}."
                is EventValidationIssue.LegacyIncompatibleAliasCodes ->
                    "Aliases use control codes above 255: ${issue.codes.joinToString()}."
                is EventValidationIssue.LegacyIncompatibleControlCodes ->
                    "Controls use codes above 255: ${issue.codes.joinToString()}."
                is EventValidationIssue.MissingCategoryControlReferences ->
                    "Category ${issue.categoryName} references missing controls: ${issue.controlIds.joinToString()}."
                is EventValidationIssue.InvalidCategoryControlPoints ->
                    "Invalid control points for ${issue.categoryName}: ${
                        DesktopControlPointValidationText.messageFor(issue.error, issue.token, issue.siCode)
                    }"
            }
    }
}
