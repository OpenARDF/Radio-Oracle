package org.openardf.radiooracle.desktop

import org.junit.Assert.*
import org.junit.Test
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.event.*

class DesktopCourseImportAvailabilityTest {
    @Test fun allControlAndCourseImportButtonsExplainReadoutBlock() {
        fun flatten(items: List<DesktopNavItem>): List<DesktopNavItem> = items.flatMap { listOf(it) + flatten(it.children) }
        val items = flatten(DesktopNavigation.rootItems(DesktopWorkflow.Setup))
        val readiness = DesktopNavigationReadiness.from(withReadout(project()))
        val imports = items.filter { it.action in DesktopCourseImportAvailability.designImportActions }
        assertEquals(6, imports.size)
        imports.forEach { item ->
            assertFalse(DesktopNavigation.isItemEnabled(item, readiness))
            assertEquals(DesktopCourseImportAvailability.ReadoutRestriction,
                DesktopNavigation.disabledItemReasonWithMenuOverrideHint(item, readiness))
            assertFalse(DesktopNavigation.canLongClickOverrideDisabledMenu(item, readiness))
        }
        val csv = items.single { it.action == DesktopNavAction.ImportControlsCsv }
        assertFalse(DesktopNavigation.isItemEnabled(csv, readiness))
        val freshReadiness = DesktopNavigationReadiness.from(project())
        imports.forEach { assertTrue(it.label, DesktopNavigation.isItemEnabled(it, freshReadiness)) }
    }

    @Test fun readoutBlocksPreparationAndLateAcceptanceWithoutChangingRace() {
        val original = project()
        val transaction = DesktopCourseImportTransaction.prepare(original)
        val recorded = withReadout(original)
        assertTrue(recorded.raceData.competitorData.isEmpty()) // Unmatched readouts alone block design replacement.
        val before = EventProjectFileJson.encode(recorded)
        assertEquals(DesktopCourseImportAvailability.ReadoutRestriction,
            assertThrows(IllegalArgumentException::class.java) { DesktopCourseImportTransaction.prepare(recorded) }.message)
        var transformed = false
        assertEquals(DesktopCourseImportAvailability.ReadoutRestriction,
            assertThrows(IllegalArgumentException::class.java) { transaction.applyTo(recorded) { transformed = true; it } }.message)
        assertFalse(transformed)
        assertEquals(before, EventProjectFileJson.encode(recorded))
        assertNull(DesktopCourseImportAvailability.disabledReason(EventProjectFactory.copyForCourseRedesign(recorded,
            "new-race", "New race", "2026-09-15T09:00")))
    }

    @Test fun unavailableProjectAndUnsupportedDraftHaveActionableReasons() {
        assertTrue(DesktopCourseImportAvailability.disabledReason(null)!!.contains("Open or create"))
        val draft = EventCourseDrafts.start(project())
        val unsupported = draft.copy(raceData = draft.raceData.copy(courseDraft = draft.raceData.courseDraft!!.copy(version = 99)))
        assertTrue(DesktopCourseImportAvailability.disabledReason(unsupported)!!.contains("compatible Radio-Oracle version"))
        assertNull(DesktopCourseImportAvailability.disabledReason(project()))
    }

    companion object {
        fun project() = EventProjectFactory.createEmptyProject("race", "Import review", "2026-09-14T09:00")
        fun withReadout(project: EventProjectFile): EventProjectFile {
            val readout = EventReadoutData(EventResult("readout", project.raceData.race.id, null, 123456, 0,
                null, 0, 1200, "2026-09-14T10:00", true, ResultStatus.OK, 1, 1200, false, false), emptyList())
            return project.copy(raceData = project.raceData.copy(unmatchedReadoutData = listOf(readout)))
        }
    }
}
