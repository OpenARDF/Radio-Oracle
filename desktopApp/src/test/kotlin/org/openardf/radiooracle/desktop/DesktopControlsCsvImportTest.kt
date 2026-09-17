package org.openardf.radiooracle.desktop

import org.junit.Assert.*
import org.junit.Test
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.event.*
import org.openardf.radiooracle.shared.files.ControlCsvImportRow

class DesktopControlsCsvImportTest {
    @Test fun readoutsAddedAfterReviewBlockBothMergeAndSynchronizationBeforeAnyImport() {
        val fresh = DesktopCourseImportAvailabilityTest.project()
        val rows = listOf(row(31))
        assertNull(DesktopCourseImportAvailability.disabledReason(fresh)) // Review may start here.
        val recordedProjects = listOf(DesktopCourseImportAvailabilityTest.withReadout(fresh),
            DesktopClassicRouteAnalysisTest().fixture())
        for (recorded in recordedProjects) for (synchronize in listOf(false, true)) {
            val before = EventProjectFileJson.encode(recorded)
            var idRequests = 0
            val error = assertThrows(IllegalArgumentException::class.java) {
                DesktopControlsCsvImport.applyTo(recorded, rows, synchronize) { idRequests++; "new-control" }
            }
            assertEquals(DesktopCourseImportAvailability.ReadoutRestriction, error.message)
            assertEquals(0, idRequests)
            assertEquals(before, EventProjectFileJson.encode(recorded))
        }
    }

    @Test fun raceWithoutReadoutsStillMergesScoringFlagsAndKeepsMissingControls() {
        val original = projectWithControls()
        val result = DesktopControlsCsvImport.applyTo(original, listOf(row(31, false)), false) { error("No new control needed") }
        assertFalse(result.projectFile.raceData.controls.single { it.siCode == 31 }.scored)
        assertTrue(original.raceData.controls.single { it.siCode == 31 }.scored)
        assertEquals(original.raceData.controls.size, result.projectFile.raceData.controls.size)
        assertTrue(result.deletedControls.isEmpty())
    }

    @Test fun permittedSynchronizationRetainsItsExistingOverLimitDeletionBehavior() {
        val original = projectWithControls()
        val result = DesktopControlsCsvImport.applyTo(original, listOf(row(36)), true) { "new-control" }
        assertEquals(listOf(31), result.deletedControls.map { it.siCode })
        assertEquals(listOf(32, 33, 34, 35, 36), result.projectFile.raceData.controls.map { it.siCode }.sorted())
        assertEquals(5, original.raceData.controls.size)
    }

    private fun projectWithControls(): EventProjectFile = (31..35).fold(DesktopCourseImportAvailabilityTest.project()) { project, code ->
        EventProjectEditor.addControl(project, "control-$code", "", code.toString(), ControlPointType.CONTROL)
    }
    private fun row(code: Int, scored: Boolean = true) = ControlCsvImportRow(code, ControlPointType.CONTROL, scored, "Fox ${code - 30}", "")
}
