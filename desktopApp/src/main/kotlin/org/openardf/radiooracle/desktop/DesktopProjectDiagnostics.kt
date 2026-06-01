package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventProjectSummary
import org.openardf.radiooracle.shared.event.EventValidationIssue
import org.openardf.radiooracle.shared.event.EventValidationRules

/** Read-only project and desktop-beta diagnostics shown in the Settings section. */
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
    val betaLimitations: List<String>
) {
    companion object {
        fun from(projectFile: EventProjectFile?): DesktopProjectDiagnostics {
            val summary = projectFile?.let(EventProjectSummary::from)
            val validationIssues = projectFile
                ?.let { EventValidationRules.validateRaceData(it.raceData) }
                ?.map(::validationIssueText)
                ?: emptyList()
            return DesktopProjectDiagnostics(
                projectState = if (projectFile == null) "No project open" else "Project open",
                schemaText = projectFile?.let { "${it.appName} schema ${it.schemaVersion}" } ?: "",
                raceId = projectFile?.raceData?.race?.id ?: "",
                raceName = summary?.raceName ?: "",
                startDateTimeIso = projectFile?.raceData?.race?.startDateTimeIso ?: "",
                categoryCount = summary?.categoryCount ?: 0,
                competitorCount = summary?.competitorCount ?: 0,
                readoutCount = summary?.readoutCount ?: 0,
                resultCount = summary?.resultCount ?: 0,
                validationState = when {
                    projectFile == null -> "No project open"
                    validationIssues.isEmpty() -> "No validation issues"
                    else -> "${validationIssues.size} validation issue${if (validationIssues.size == 1) "" else "s"}"
                },
                validationIssues = validationIssues,
                betaLimitations = listOf(
                    "Live SPORTident download remains post-beta.",
                    "Printing remains post-beta.",
                    "Live result sending remains post-beta.",
                    "Project storage is file-backed .rom.json, not shared SQL."
                )
            )
        }

        private fun validationIssueText(issue: EventValidationIssue): String =
            when (issue) {
                EventValidationIssue.BlankRaceName -> "Race name is blank."
                is EventValidationIssue.DuplicateCategoryNames ->
                    "Duplicate category names: ${issue.names.joinToString()}."
                is EventValidationIssue.DuplicateAliasNames ->
                    "Duplicate alias names: ${issue.names.joinToString()}."
                is EventValidationIssue.DuplicateAliasCodes ->
                    "Duplicate alias SI codes: ${issue.codes.joinToString()}."
                is EventValidationIssue.DuplicateStartNumbers ->
                    "Duplicate start numbers: ${issue.startNumbers.joinToString()}."
                is EventValidationIssue.DuplicateSINumbers ->
                    "Duplicate SI numbers: ${issue.siNumbers.joinToString()}."
                is EventValidationIssue.MultipleStartPunches ->
                    "Readout has multiple start punches: ${issue.siNumber ?: "unknown SI"}."
                is EventValidationIssue.MultipleFinishPunches ->
                    "Readout has multiple finish punches: ${issue.siNumber ?: "unknown SI"}."
            }
    }
}
