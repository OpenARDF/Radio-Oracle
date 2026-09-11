package org.openardf.radiooracle.desktop

import org.junit.Assert.*
import org.junit.Test
import org.openardf.radiooracle.shared.event.*
import java.nio.file.Path

class DesktopCourseSpeedFactorTest {
    private val path = Path.of("speed-factor.json")

    private fun session(project: EventProjectFile): DesktopProjectSession {
        val store = object : ProjectFileStore {
            private var saved = project
            override fun read(path: Path) = saved
            override fun write(path: Path, projectFile: EventProjectFile) {
                saved = EventProjectFileJson.decode(EventProjectFileJson.encode(projectFile))
            }
        }
        return DesktopProjectSession(store).apply { open(path) }
    }

    private fun applied() = EventProjectEditor.updateCourseAnalyzerSpeedCompensationFactor(
        DesktopAuthoritativeCourseImportTest.project(), 0.6)

    private fun pending() = EventCourseDrafts.edit(applied()) {
        EventProjectEditor.updateCourseAnalyzerSpeedCompensationFactor(
            EventProjectEditor.replaceCategoryAssignedControls(it, "short", listOf("fox1", "fox3")) { "draft-$it" }, 1.0)
    }

    @Test fun appliedUpdateDoesNotCreateOrSelectADraft() {
        val initial = applied()
        val session = session(initial)
        val ui = DesktopCourseDesignUi()
        val updated = ui.updateSpeedFactor(session, 0.7)
        assertEquals(EventProjectEditor.updateCourseAnalyzerSpeedCompensationFactor(initial, 0.7), updated)
        assertNull(updated.raceData.courseDraft)
        assertEquals(DesktopCourseRouteSource.Applied, ui.routeSource(updated))
        assertTrue(session.hasUnsavedChanges)
    }

    @Test fun staleDraftDoesNotBlockAppliedUpdateOrGetReplacedWhenSaved() {
        val initial = EventProjectEditor.updateCourseAnalyzerSpeedCompensationFactor(pending(), 0.5)
        assertThrows(IllegalArgumentException::class.java) { EventCourseDrafts.requireCurrent(initial) }
        val session = session(initial)
        val ui = DesktopCourseDesignUi()
        val updated = ui.updateSpeedFactor(session, 0.7)
        assertEquals(EventProjectEditor.updateCourseAnalyzerSpeedCompensationFactor(initial, 0.7), updated)
        assertEquals(initial.raceData.courseDraft, updated.raceData.courseDraft)
        assertEquals(0.7, ui.analysisProject(updated).raceData.race.courseAnalyzerSpeedCompensationFactor, 0.0)
        assertEquals(DesktopCourseRouteSource.Applied, ui.analysisSource)
        assertThrows(IllegalArgumentException::class.java) { EventCourseDrafts.requireCurrent(updated) }
        session.saveAs(path)
        session.open(path)
        val reopened = session.currentProject!!
        assertEquals(0.7, reopened.raceData.race.courseAnalyzerSpeedCompensationFactor, 0.0)
        assertEquals(initial.raceData.courseDraft, reopened.raceData.courseDraft)
        assertThrows(IllegalArgumentException::class.java) { EventCourseDrafts.requireCurrent(reopened) }
    }

    @Test fun appliedUpdatePreservesCurrentDraftDesignAndItsOwnSpeedFactor() {
        val initial = pending()
        val session = session(initial)
        val ui = DesktopCourseDesignUi()
        val updated = ui.updateSpeedFactor(session, 0.7)
        EventCourseDrafts.requireCurrent(updated)
        assertEquals(initial.raceData.courseDraft!!.design, updated.raceData.courseDraft!!.design)
        assertEquals(EventCourseDrafts.candidate(initial), EventCourseDrafts.candidate(updated))
        assertEquals(0.7, updated.raceData.race.courseAnalyzerSpeedCompensationFactor, 0.0)
        assertEquals(initial.raceData.categories, updated.raceData.categories)
        assertEquals(DesktopCourseRouteSource.Applied, ui.analysisSource)
        session.saveAs(path)
        session.open(path)
        EventCourseDrafts.requireCurrent(session.currentProject!!)
    }

    @Test fun draftUpdateLeavesAppliedCourseAndSpeedFactorUntouched() {
        val initial = pending()
        val session = session(initial)
        val ui = DesktopCourseDesignUi().apply { analysisSource = DesktopCourseRouteSource.Draft }
        val updated = ui.updateSpeedFactor(session, 0.8)
        assertEquals(EventCourseDrafts.cancel(initial), EventCourseDrafts.cancel(updated))
        assertEquals(0.8, ui.analysisProject(updated).raceData.race.courseAnalyzerSpeedCompensationFactor, 0.0)
        assertEquals(DesktopCourseRouteSource.Draft, ui.analysisSource)
        assertEquals(initial.raceData.courseDraft!!.design.categories, updated.raceData.courseDraft!!.design.categories)
        EventCourseDrafts.requireCurrent(updated)
    }

    @Test fun editingAStaleDraftStillRejectsWithoutChangingTheSession() {
        val initial = EventProjectEditor.updateCourseAnalyzerSpeedCompensationFactor(pending(), 0.5)
        val session = session(initial)
        val ui = DesktopCourseDesignUi().apply { analysisSource = DesktopCourseRouteSource.Draft }
        assertThrows(IllegalArgumentException::class.java) { ui.updateSpeedFactor(session, 0.7) }
        assertEquals(initial, session.currentProject)
        assertFalse(session.hasUnsavedChanges)
        assertEquals(DesktopCourseRouteSource.Draft, ui.analysisSource)
    }

    @Test fun invalidSpeedFactorsLeaveBothSourcesAndSessionUnchanged() {
        for (source in DesktopCourseRouteSource.values()) {
            val initial = pending()
            val session = session(initial)
            val ui = DesktopCourseDesignUi().apply { analysisSource = source }
            for (factor in listOf(Double.NaN, Double.POSITIVE_INFINITY, 0.24, 2.01)) {
                assertThrows(IllegalArgumentException::class.java) { ui.updateSpeedFactor(session, factor) }
                assertEquals(initial, session.currentProject)
                assertFalse(session.hasUnsavedChanges)
                assertEquals(source, ui.analysisSource)
            }
        }
    }
}
