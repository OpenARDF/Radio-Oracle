package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.event.EventCourseDrafts
import org.openardf.radiooracle.shared.event.EventProjectFile

/** Centralizes desktop explanations while shared code owns the recorded-activity rule itself. */
internal object DesktopCourseImportAvailability {
    const val ReadoutRestriction = "Control and course imports are unavailable because this race contains saved SI-card readouts, including unmatched readouts. Replacing its course design could change recorded results. Create a new race copy without readouts, then import into that copy."
    const val ControlEditReadoutRestriction = "Controls cannot be added, changed, or deleted because this race contains saved SI-card readouts, including unmatched readouts. Changing its course design could change recorded results. Create a new race copy without readouts, then edit that copy."

    val designImportActions = setOf(
        DesktopNavAction.ImportIofCourseDataXml,
        DesktopNavAction.ImportCourseKmlKmz, DesktopNavAction.ImportCourseGpx,
        DesktopNavAction.ImportControlsKmlKmz, DesktopNavAction.ImportControlsGpx,
        DesktopNavAction.ImportControlsCsv
    )

    fun disabledReason(project: EventProjectFile?): String? = when {
        project == null -> "Open or create a Race File before importing controls or courses."
        EventCourseDrafts.hasRecordedActivity(project.raceData) -> ReadoutRestriction
        project.raceData.courseDraft?.version?.let { it != 1 } == true ->
            "This race contains a course draft from an unsupported format. Open it with a compatible Radio-Oracle version before importing controls or courses."
        else -> null
    }

    fun controlEditDisabledReason(project: EventProjectFile?): String? = when {
        project == null -> "Open or create a Race File before editing controls."
        EventCourseDrafts.hasRecordedActivity(project.raceData) -> ControlEditReadoutRestriction
        else -> null
    }

    fun requireControlEditsAvailable(project: EventProjectFile) {
        controlEditDisabledReason(project)?.let { throw IllegalArgumentException(it) }
    }

    fun requireAvailable(project: EventProjectFile) {
        val reason = disabledReason(project)
        require(reason == null) { reason.orEmpty() }
    }
}
