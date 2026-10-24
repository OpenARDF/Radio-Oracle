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

    @Test fun completeReimportClearsPriorIofCategoryMissingFromTheNewFile() {
        val initial = imported(DesktopCourseImportAvailabilityTest.project(), xml())
        val original = DesktopIofCourseAnalysis.prepare(initial, initial.raceData.categories.map { it.category.id }.toSet(),
            null, elevationLookup = { 100.0 }).project
        val input = xml().replace("<ClassCourseAssignment><ClassName>W21</ClassName><CourseName>Shared course</CourseName></ClassCourseAssignment>", "")
        val sources = IofXmlImports.validatedCourseControlSources(input, IofXmlSchemaResource.loadBundledSchema())
        val rows = IofCourseControlMappings.prefill(sources, original).map {
            if (it.sourceId == "31") it.copy(publicName = "Fox1") else it
        }
        val review = DesktopIofControlMappingReview.prepareCourseReview(
            PendingIofControlMappingReview(Path.of("complete.xml"), DesktopCourseImportTransaction.prepare(original), input, sources, rows),
            rows,
            false,
            null
        )
        assertEquals(listOf("W21"), review.replacement.activeCategoryNamesToClear)
        val candidate = EventProjectEditor.importIofCourseData(
            original,
            review.courseData,
            review.replacement.categoryIds
        ).projectFile
        val refreshed = DesktopIofControlMappingReview.refreshSharedNames(
            original,
            candidate,
            review.courseData.reviewedControlNames,
            null
        )
        val missing = refreshed.raceData.categories.single { it.category.name == "W21" }
        assertTrue(missing.controlPoints.isEmpty())
        assertNull(missing.category.courseInfo)
        val retained = refreshed.raceData.categories.single { it.category.name == "M21" }.category.courseInfo!!
        assertTrue(retained.controlPoints.any { it.label == "Fox1" })
        val prepared = DesktopIofCourseAnalysis.prepare(refreshed,
            refreshed.raceData.categories.filter { it.category.name == "M21" }.map { it.category.id }.toSet(), null,
            elevationLookup = { 100.0 }).project
        ResolvedCourseProjection.courseInfos(prepared.raceData,
            prepared.raceData.categories.mapNotNull { data ->
                data.category.courseInfo?.let { data.category.id to it }
            }.toMap())
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

    @Test fun replacementPlanUsesUnlockedIofProvenanceForEncryptedCourses() {
        val candidate = imported(DesktopCourseImportAvailabilityTest.project(), xml())
        val protected = DesktopIofCourseAnalysis.prepare(candidate, candidate.raceData.categories.map { it.category.id }.toSet(),
            "fixture-password", elevationLookup = { 100.0 }).project
        val input = xml().replace("<ClassCourseAssignment><ClassName>W21</ClassName><CourseName>Shared course</CourseName></ClassCourseAssignment>", "")
        val parsed = IofXmlImports.courseData(input, protected.raceData.race).parsedData

        val replacement = DesktopIofControlMappingReview.courseSetReplacement(
            protected,
            parsed,
            "fixture-password"
        )

        assertEquals(protected.raceData.categories.map { it.category.id }.toSet(), replacement.categoryIds)
        assertEquals(listOf("W21"), replacement.activeCategoryNamesToClear)
        assertThrows(IllegalArgumentException::class.java) {
            DesktopIofControlMappingReview.courseSetReplacement(protected, parsed, null)
        }
    }
}
