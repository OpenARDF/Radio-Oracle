package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.event.EventCourseDrafts
import org.openardf.radiooracle.shared.event.EventProjectFile

/** The same eligibility check is used by import buttons, file pickers and final acceptance. */
internal object DesktopCourseImportAvailability {
    const val ReadoutRestriction = "Control and course imports are unavailable because this race contains saved SI-card readouts, including unmatched readouts. Replacing its course design could change recorded results. Create a new race copy without readouts, then import into that copy."

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

    fun requireAvailable(project: EventProjectFile) {
        val reason = disabledReason(project)
        require(reason == null) { reason.orEmpty() }
    }
}
