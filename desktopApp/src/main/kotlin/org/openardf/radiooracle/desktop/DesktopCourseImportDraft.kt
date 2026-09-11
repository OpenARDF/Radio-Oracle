package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.event.EventCourseDrafts
import org.openardf.radiooracle.shared.event.EventProjectFile

/** An import reviews the current design, or starts afresh if that design's base has changed. */
internal class DesktopCourseImportDraft private constructor(
    private val sourceProject: EventProjectFile,
    val baseProject: EventProjectFile,
    val replacesOutdatedDraft: Boolean
) {
    fun applyTo(current: EventProjectFile, transform: (EventProjectFile) -> EventProjectFile): EventProjectFile {
        require(current.raceData.race.id == sourceProject.raceData.race.id &&
            EventCourseDrafts.snapshotHash(current) == EventCourseDrafts.snapshotHash(sourceProject) &&
            current.raceData.courseDraft == sourceProject.raceData.courseDraft) {
            "Course data changed while this import was being reviewed. Cancel and import the file again."
        }
        val startingProject = if (replacesOutdatedDraft) EventCourseDrafts.cancel(current) else current
        return EventCourseDrafts.edit(startingProject, transform)
    }

    companion object {
        fun prepare(project: EventProjectFile): DesktopCourseImportDraft {
            // Validate the format before considering replacement; unknown future drafts are not discarded.
            val candidate = EventCourseDrafts.candidate(project)
            val draft = project.raceData.courseDraft
            val outdated = draft != null && draft.baseSnapshotHash != EventCourseDrafts.snapshotHash(project)
            return DesktopCourseImportDraft(project,
                if (outdated) EventCourseDrafts.cancel(project) else candidate, outdated)
        }
    }
}
