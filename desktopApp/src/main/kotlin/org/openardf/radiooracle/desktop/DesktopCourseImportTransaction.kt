package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.event.EventCourseDrafts
import org.openardf.radiooracle.shared.event.EventProjectFile

/** A read-only import transaction based on the applied race, independent of Analyzer drafts. */
internal class DesktopCourseImportTransaction private constructor(
    private val sourceProject: EventProjectFile,
    val baseProject: EventProjectFile,
    val replacesOutdatedDraft: Boolean
) {
    val replacesDraft: Boolean get() = sourceProject.raceData.courseDraft != null

    fun disabledReason(current: EventProjectFile?): String? {
        DesktopCourseImportAvailability.disabledReason(current)?.let { return it }
        requireNotNull(current)
        return if (current.raceData.race.id != sourceProject.raceData.race.id ||
            EventCourseDrafts.snapshotHash(current) != EventCourseDrafts.snapshotHash(sourceProject) ||
            current.raceData.courseDraft != sourceProject.raceData.courseDraft) {
            "Course data changed while this import was being reviewed. Cancel and import the file again."
        } else null
    }

    fun applyTo(current: EventProjectFile, transform: (EventProjectFile) -> EventProjectFile): EventProjectFile {
        val reason = disabledReason(current)
        require(reason == null) { reason.orEmpty() }
        val startingProject = EventCourseDrafts.cancel(current)
        val prepared = transform(baseProject)
        org.openardf.radiooracle.shared.event.EventControlCatalog.requireCanonical(prepared)
        return EventCourseDrafts.commit(startingProject, prepared, EventCourseDrafts.snapshotHash(startingProject))
    }

    companion object {
        fun prepare(project: EventProjectFile): DesktopCourseImportTransaction {
            DesktopCourseImportAvailability.requireAvailable(project)
            // Validate the format before considering replacement; unknown future drafts are not discarded.
            EventCourseDrafts.candidate(project)
            val draft = project.raceData.courseDraft
            val outdated = draft != null && draft.baseSnapshotHash != EventCourseDrafts.snapshotHash(project)
            return DesktopCourseImportTransaction(project,
                EventCourseDrafts.cancel(project), outdated)
        }
    }
}
