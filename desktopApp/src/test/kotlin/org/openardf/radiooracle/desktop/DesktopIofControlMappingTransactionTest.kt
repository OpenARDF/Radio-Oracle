package org.openardf.radiooracle.desktop

import java.nio.file.Path
import org.junit.Assert.*
import org.junit.Test
import org.openardf.radiooracle.shared.event.*
import org.openardf.radiooracle.shared.files.*

class DesktopIofControlMappingTransactionTest {
    private fun xml() = DesktopIofCourseAnalysisTest().xml()
    private fun imported(original: EventProjectFile, input: String, name: String? = null): EventProjectFile {
        val rows = IofCourseControlMappings.prefill(IofXmlImports.courseControlSources(input), original).map {
            if (name != null && it.sourceId == "31") it.copy(publicName = name) else it
        }
        val parsed = IofXmlImports.courseDataWithControlMappings(input, original.raceData.race, rows).parsedData
        return EventProjectEditor.importIofCourseData(original, parsed).projectFile
    }

    @Test fun analysisAcceptanceAndSaveReopenKeepReviewedNamesAndIdentities() {
        val original = DesktopCourseImportAvailabilityTest.project()
        val transaction = DesktopCourseImportTransaction.prepare(original)
        val sources = IofXmlImports.validatedCourseControlSources(xml(), IofXmlSchemaResource.loadBundledSchema())
        val rows = IofCourseControlMappings.prefill(sources, original).map {
            if (it.sourceId == "31") it.copy(publicName = "Fox1") else it
        }
        val review = PendingIofControlMappingReview(Path.of("review.xml"), transaction, xml(), sources, rows)
        val courseReview = DesktopIofControlMappingReview.prepareCourseReview(review, rows, false, null)
        assertEquals(setOf("W21", "M21"), courseReview.newCourseMappingNames.toSet())
        val candidate = EventProjectEditor.importIofCourseData(original, courseReview.courseData).projectFile
        val ids = candidate.raceData.categories.map { it.category.id }.toSet()
        val prepared = DesktopIofCourseAnalysis.prepare(candidate, ids, null, elevationLookup = { 100.0 }).project
        val accepted = transaction.applyTo(original) { prepared }
        val reopened = EventProjectFileJson.decode(EventProjectFileJson.encode(accepted))
        val control = reopened.raceData.controls.single { it.siCode == 31 }
        assertEquals("Fox1", EventControlCatalog.displayLabel(control))
        assertTrue(reopened.raceData.categories.all { data ->
            val info = data.category.courseInfo!!
            ResolvedCourseProjection.courseInfo(reopened.raceData, data.category.id, info)
            info.appliedBindings!!.controls.any { it.controlId == control.id && it.label == "Fox1" }
        })
        assertTrue(original.raceData.controls.isEmpty())
    }

    @Test fun partialReimportRefreshesNamesInOtherCoursesWithoutChangingTheirGeometry() {
        val initial = imported(DesktopCourseImportAvailabilityTest.project(), xml())
        val original = DesktopIofCourseAnalysis.prepare(initial, initial.raceData.categories.map { it.category.id }.toSet(),
            null, elevationLookup = { 100.0 }).project
        val input = xml().replace("<ClassCourseAssignment><ClassName>W21</ClassName><CourseName>Shared course</CourseName></ClassCourseAssignment>", "")
        val candidate = imported(original, input, "Fox1")
        val before = original.raceData.categories.single { it.category.name == "W21" }.category.courseInfo!!
        val refreshed = DesktopIofControlMappingReview.refreshSharedNames(original, candidate, mapOf(31 to "Fox1"), null)
        val after = refreshed.raceData.categories.single { it.category.name == "W21" }.category.courseInfo!!
        assertEquals(before.route, after.route)
        assertEquals(before.suppliedLegLengths, after.suppliedLegLengths)
        assertEquals(before.lengthMeters, after.lengthMeters)
        assertTrue(after.appliedBindings!!.controls.any { it.siCode == 31 && it.label == "Fox1" })
        val prepared = DesktopIofCourseAnalysis.prepare(refreshed,
            refreshed.raceData.categories.filter { it.category.name == "M21" }.map { it.category.id }.toSet(), null,
            elevationLookup = { 100.0 }).project
        ResolvedCourseProjection.courseInfos(prepared.raceData,
            prepared.raceData.categories.associate { it.category.id to it.category.courseInfo!! })
    }

    @Test fun protectedSharedCourseRenameRequiresUnlockAndRetainsProtection() {
        val candidate = imported(DesktopCourseImportAvailabilityTest.project(), xml())
        val original = DesktopIofCourseAnalysis.prepare(candidate, candidate.raceData.categories.map { it.category.id }.toSet(),
            "fixture-password", elevationLookup = { 100.0 }).project
        val input = xml().replace("<ClassCourseAssignment><ClassName>W21</ClassName><CourseName>Shared course</CourseName></ClassCourseAssignment>", "")
        val renamed = imported(original, input, "Fox1")
        val before = EventProjectFileJson.encode(original)
        assertThrows(IllegalArgumentException::class.java) {
            DesktopIofControlMappingReview.refreshSharedNames(original, renamed, mapOf(31 to "Fox1"), null)
        }
        val refreshed = DesktopIofControlMappingReview.refreshSharedNames(original, renamed, mapOf(31 to "Fox1"), "fixture-password")
        val stored = refreshed.raceData.categories.single { it.category.name == "W21" }.category
        assertNull(stored.courseInfo)
        assertNotNull(stored.encryptedCourseInfo)
        assertTrue(stored.storedCourseInfo("fixture-password")!!.appliedBindings!!.controls.any { it.siCode == 31 && it.label == "Fox1" })
        assertEquals(before, EventProjectFileJson.encode(original))
    }
}
