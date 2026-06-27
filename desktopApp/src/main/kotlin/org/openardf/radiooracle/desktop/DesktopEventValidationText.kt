package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.event.EventValidationIssue
import org.openardf.radiooracle.shared.event.EventValidationIssueSeverity
import org.openardf.radiooracle.shared.event.EventValidationRules

/** Desktop copy for shared Event File validation issues. */
object DesktopEventValidationText {
    fun severityFor(issue: EventValidationIssue): EventValidationIssueSeverity =
        EventValidationRules.severity(issue)

    fun areaFor(issue: EventValidationIssue): String =
        when (issue) {
            EventValidationIssue.BlankRaceName -> "Event File"
            EventValidationIssue.NoCategories,
            is EventValidationIssue.DuplicateCategoryNames,
            is EventValidationIssue.InvalidCategoryControlPoints,
            is EventValidationIssue.MissingCategoryAssignedControls,
            is EventValidationIssue.MissingCategoryControlReferences,
            is EventValidationIssue.CategoryCourseRequirementIssue,
            is EventValidationIssue.LegacyIncompatibleCategoryControlCodes -> "Categories"
            is EventValidationIssue.ControlInventoryIssue,
            is EventValidationIssue.DuplicateControlIds,
            is EventValidationIssue.DuplicateControlLabels,
            is EventValidationIssue.DuplicatePublicLabels,
            is EventValidationIssue.MissingPublicLabels,
            is EventValidationIssue.UnusedControls,
            is EventValidationIssue.LegacyIncompatibleControlCodes -> "Controls"
            is EventValidationIssue.DuplicateAliasCodes,
            is EventValidationIssue.DuplicateAliasNames,
            is EventValidationIssue.LegacyIncompatibleAliasCodes -> "Aliases"
            is EventValidationIssue.DuplicateBibNumbers,
            is EventValidationIssue.DuplicateCallSigns,
            is EventValidationIssue.DuplicateSINumbers,
            is EventValidationIssue.InvalidStartNumberAssignments,
            is EventValidationIssue.MissingCompetitorSiNumbers -> "Competitors"
            is EventValidationIssue.MultipleFinishPunches,
            is EventValidationIssue.MultipleStartPunches -> "Readouts"
        }

    fun messageFor(issue: EventValidationIssue): String =
        when (issue) {
            EventValidationIssue.BlankRaceName -> "Event name is blank."
            EventValidationIssue.NoCategories -> "At least one category must be defined."
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
            is EventValidationIssue.ControlInventoryIssue -> issue.message
            is EventValidationIssue.MissingPublicLabels ->
                "Controls without Public labels: ${issue.controls.joinToString()}."
            is EventValidationIssue.DuplicatePublicLabels ->
                "Duplicate Public labels: ${issue.labels.joinToString()}."
            is EventValidationIssue.UnusedControls ->
                "Controls not assigned to any category: ${issue.controls.joinToString()}."
            is EventValidationIssue.InvalidStartNumberAssignments ->
                "Start numbers do not match assigned start times for ${issue.competitorIds.size} competitor(s)."
            is EventValidationIssue.DuplicateSINumbers ->
                "Duplicate SI numbers: ${issue.siNumbers.joinToString()}."
            is EventValidationIssue.DuplicateBibNumbers ->
                "Duplicate bib numbers: ${issue.bibNumbers.joinToString()}."
            is EventValidationIssue.DuplicateCallSigns ->
                "Duplicate call signs: ${issue.callSigns.joinToString()}."
            is EventValidationIssue.MissingCompetitorSiNumbers ->
                "Competitors without SI numbers: ${issue.competitorNames.joinToString()}."
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
            is EventValidationIssue.MissingCategoryAssignedControls ->
                "Category ${issue.categoryName} has no assigned controls."
            is EventValidationIssue.CategoryCourseRequirementIssue ->
                "Category ${issue.categoryName}: ${issue.message}"
            is EventValidationIssue.InvalidCategoryControlPoints ->
                "Invalid control points for ${issue.categoryName}: ${
                    DesktopControlPointValidationText.messageFor(issue.error, issue.token, issue.siCode)
                }"
        }
}
