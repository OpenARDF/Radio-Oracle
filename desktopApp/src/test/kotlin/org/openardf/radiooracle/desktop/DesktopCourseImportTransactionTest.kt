package org.openardf.radiooracle.desktop

import org.junit.Assert.*
import org.junit.Test
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.event.*
import java.nio.file.Path

class DesktopCourseImportTransactionTest {
    private fun project(): EventProjectFile {
        var project = EventProjectFactory.createEmptyProject("race", "Import fixture", "2026-09-11T09:00")
        project = project.copy(raceData = project.raceData.copy(controls = listOf(
            EventControl("fox1", "race", "1", 131, ControlPointType.CONTROL),
            EventControl("fox2", "race", "2", 132, ControlPointType.CONTROL))))
        project = EventProjectEditor.addCategory(project, "short", "Short")
        return assignments(project, listOf("fox1"))
    }

    private fun assignments(project: EventProjectFile, ids: List<String>) =
        EventProjectEditor.replaceCategoryAssignedControls(project, "short", ids) { "assignment-$it" }

    private fun staleProject(): EventProjectFile {
        val draft = EventCourseDrafts.edit(project()) { it.copy(raceData = it.raceData.copy(
            race = it.raceData.race.copy(courseAnalyzerSpeedCompensationFactor = 1.25))) }
        // Setup can change the applied course while a previous imported draft is saved.
        return assignments(draft, listOf("fox2"))
    }

    @Test fun importAfterAssignedControlsChangedAppliesImmediatelyAndClearsOldDraft() {
        val stale = EventProjectFileJson.decode(EventProjectFileJson.encode(staleProject()))
        assertThrows(IllegalArgumentException::class.java) { EventCourseDrafts.requireCurrent(stale) }
        val preparation = DesktopCourseImportTransaction.prepare(stale)
        assertTrue(preparation.replacesOutdatedDraft)
        assertEquals(listOf("fox2"), preparation.baseProject.raceData.categories.single().publicControlIds)
        val path = Path.of(requireNotNull(javaClass.getResource("/condes/course.gpx")).toURI())
        val (preview, summary) = DesktopCourseKmlImporter.importProtectedCourseInfo(path, preparation.baseProject, null,
            elevationProvider = { 100.0 }, createMissingCategories = true, createMissingControls = true)
        assertTrue(summary.importedCategoryCount > 0)
        val assigned = DesktopCourseKmlImporter.applyCategoryAssignmentUpdates(preview, summary.categoryAssignmentUpdates)
        val prepared = DesktopAuthoritativeCourseImport.prepare(assigned, summary.matchedCategoryIds.toSet(), null)
        val imported = preparation.applyTo(stale) { prepared }
        EventCourseDrafts.requireCurrent(imported)
        assertNull(imported.raceData.courseDraft)
        assertEquals(EventCourseDrafts.capture(prepared.raceData), EventCourseDrafts.capture(imported.raceData))
        EventCourseDrafts.requireCurrent(EventProjectFileJson.decode(EventProjectFileJson.encode(imported)))
    }

    @Test fun importUsesAppliedCourseInsteadOfUnappliedDraftEdits() {
        val draft = EventCourseDrafts.edit(project()) { assignments(it, listOf("fox1", "fox2")) }
        val preparation = DesktopCourseImportTransaction.prepare(draft)
        assertFalse(preparation.replacesOutdatedDraft)
        val imported = preparation.applyTo(draft) { it }
        assertTrue(preparation.replacesDraft)
        assertNull(imported.raceData.courseDraft)
        assertEquals(listOf("fox1"), imported.raceData.categories.single().publicControlIds)
        EventCourseDrafts.requireCurrent(imported)
    }

    @Test fun cancelledOrFailedReplacementLeavesTheOldDraftUntouched() {
        val original = staleProject()
        val session = DesktopProjectSession(object : ProjectFileStore {
            override fun read(path: Path) = error("unused")
            override fun write(path: Path, projectFile: EventProjectFile) = error("unused")
        })
        session.newProject(original)
        val preparation = DesktopCourseImportTransaction.prepare(original)
        assertEquals(original, session.currentProject) // Preparing/reviewing alone never clears the old draft.
        assertThrows(IllegalStateException::class.java) {
            session.updateCurrentProject { preparation.applyTo(it) { error("Import failed") } }
        }
        assertEquals(original, session.currentProject)
    }

    @Test fun laterAppliedEditsOrDraftEditsRejectAnEarlierImportPreview() {
        val original = staleProject()
        val preparation = DesktopCourseImportTransaction.prepare(original)
        val changedApplied = assignments(original, listOf("fox1", "fox2"))
        assertThrows(IllegalArgumentException::class.java) { preparation.applyTo(changedApplied) { it } }
        val changedDraft = original.copy(raceData = original.raceData.copy(courseDraft = original.raceData.courseDraft!!.let {
            it.copy(design = it.design.copy(analyzerSpeedCompensationFactor = 1.5))
        }))
        assertThrows(IllegalArgumentException::class.java) { preparation.applyTo(changedDraft) { it } }
        assertThrows(IllegalArgumentException::class.java) { preparation.applyTo(EventCourseDrafts.cancel(original)) { it } }
    }

    @Test fun unsupportedDraftFormatCannotBeDiscardedByAnImport() {
        val original = staleProject()
        val future = original.copy(raceData = original.raceData.copy(courseDraft = original.raceData.courseDraft!!.copy(version = 99)))
        assertThrows(IllegalArgumentException::class.java) { DesktopCourseImportTransaction.prepare(future) }
    }
}
