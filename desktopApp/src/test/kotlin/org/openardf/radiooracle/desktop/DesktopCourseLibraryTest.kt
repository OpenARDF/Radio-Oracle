package org.openardf.radiooracle.desktop

import org.junit.Assert.*
import org.junit.Test
import org.openardf.radiooracle.shared.event.*
import org.openardf.radiooracle.shared.files.IofXmlImports

class DesktopCourseLibraryTest {
    @Test fun assignedProtectedCoursesRequireThePasswordAndStayProtectedWhenReused() {
        val initial = unassigned()
        var project = DesktopCourseLibrary.apply(initial,
            DesktopCourseLibraryEdit.Assign(initial.raceData.courseMappings.single().category.id, emptySet(), "W21"), null) { "w21" }
        project = EventProjectEditor.addCategory(project, "w65", "W65")
        val source = project.raceData.categories.first().category
        val password = "fixture-password"
        project = project.withStoredCourseInfo("w21", source.courseInfo, password).withStoredIdealOrder("w21", source.idealOrder, password)
        val edit = DesktopCourseLibraryEdit.Assign("w21", setOf("w65"))
        assertThrows(IllegalArgumentException::class.java) { DesktopCourseLibrary.apply(project, edit, null) }
        val result = DesktopCourseLibrary.apply(project, edit, password)
        val copied = result.raceData.categories.single { it.category.id == "w65" }.category
        assertNull(copied.courseInfo)
        assertEquals(source.courseInfo!!.courseObjects, copied.storedCourseInfo(password)!!.courseObjects)
        assertEquals(source.idealOrder, copied.storedIdealOrder(password))
        assertEquals(project.raceData.categories.first(), result.raceData.categories.first())
    }

    @Test fun controlsOnlyAssignmentDoesNotRequireCourseGeometryOrAnalyzer() {
        var project = EventProjectFactory.createEmptyProject("race", "Controls only", "2026-09-16T09:00")
        project = EventProjectEditor.addCategory(project, "w21", "W21")
        project = EventProjectEditor.updateCategoryControlPoints(project, "w21", "31 32 33 50B") { "point-$it" }
        project = EventProjectEditor.addCategory(project, "w65", "W65")
        val result = DesktopCourseLibrary.apply(project, DesktopCourseLibraryEdit.Assign("w21", setOf("w65")), null)
        assertNull(result.raceData.categories.last().category.courseInfo)
        assertEquals(result.raceData.categories.first().publicControlIds, result.raceData.categories.last().publicControlIds)
        EventControlCatalog.requireCanonical(EventProjectFileJson.decode(EventProjectFileJson.encode(result)))
    }

    @Test fun categoriesReuseAnAssignedCourseWithoutAnalyzerOrChangingItsSource() {
        val initial = unassigned()
        val sourceId = initial.raceData.courseMappings.single().category.id
        var project = DesktopCourseLibrary.apply(initial, DesktopCourseLibraryEdit.Assign(sourceId, emptySet(), "W21"), null) { "w21" }
        project = EventProjectEditor.addCategory(project, "m70", "M70")
        project = EventProjectEditor.addCategory(project, "w65", "W65")
        val source = project.raceData.categories.first { it.category.id == "w21" }
        val before = EventProjectFileJson.encode(project)
        assertEquals(listOf("w21"), DesktopCourseLibrary.assignmentSources(project).map { it.category.id })
        val result = DesktopCourseLibrary.apply(project, DesktopCourseLibraryEdit.Assign("w21", setOf("m70", "w65")), null)
        assertEquals(source, result.raceData.categories.first { it.category.id == "w21" })
        assertEquals(project.raceData.controls, result.raceData.controls)
        result.raceData.categories.forEach { assertCoursePreserved(source, it) }
        assertEquals(3, DesktopCourseLibrary.assignmentSources(result).size)
        val infos = result.raceData.categories.associate { it.category.id to requireNotNull(it.category.courseInfo) }
        assertEquals(setOf("W21", "M70", "W65"), courseAnalysisRouteCategories(result, infos).map { it.category.name }.toSet())
        assertEquals(before, EventProjectFileJson.encode(project))
        EventControlCatalog.requireCanonical(EventProjectFileJson.decode(EventProjectFileJson.encode(result)))
        assertThrows(IllegalArgumentException::class.java) {
            DesktopCourseLibrary.apply(result, DesktopCourseLibraryEdit.Delete("w21"), null)
        }
    }

    @Test fun explicitXmlClassesCreateActiveCategoriesAndReimportPromotesLegacyMappings() {
        val base = DesktopCourseImportAvailabilityTest.project()
        val parsed = IofXmlImports.courseData(DesktopIofCourseAnalysisTest().xml(), base.raceData.race).parsedData
        assertEquals(2, parsed.assignedCategoryIds.size)
        val legacy = EventProjectEditor.importIofCourseData(base, parsed.copy(assignedCategoryIds = emptySet())).projectFile
        assertEquals(2, legacy.raceData.courseMappings.size)
        val imported = EventProjectEditor.importIofCourseData(legacy, parsed).projectFile
        assertEquals(setOf("W21", "M21"), imported.raceData.categories.map { it.category.name }.toSet())
        assertEquals(legacy.raceData.courseMappings.map { it.category.id }, imported.raceData.categories.map { it.category.id })
        assertTrue(imported.raceData.courseMappings.isEmpty())
        assertEquals(imported, EventProjectEditor.importIofCourseData(imported, parsed).projectFile)
    }

    @Test fun coursesWithoutClassAssignmentsRemainVisibleCourseRecordsUntilAssigned() {
        val original = unassigned()
        assertTrue(original.raceData.categories.isEmpty())
        val source = original.raceData.courseMappings.single()
        val result = DesktopCourseLibrary.apply(original, DesktopCourseLibraryEdit.Assign(source.category.id, emptySet(), "W21"), null) { "new-w21" }
        assertTrue(result.raceData.courseMappings.isEmpty())
        assertEquals("W21", result.raceData.categories.single().category.name)
        assertCoursePreserved(source, result.raceData.categories.single())
        assertEquals(1, original.raceData.courseMappings.size)
        assertTrue(original.raceData.categories.isEmpty())
        EventControlCatalog.requireCanonical(EventProjectFileJson.decode(EventProjectFileJson.encode(result)))
    }

    @Test fun assigningOneCourseToSeveralExistingCategoriesPreservesCategoryIdentityAndProtection() {
        var original = unassigned()
        original = EventProjectEditor.addCategory(original, "w", "W21")
        original = EventProjectEditor.addCategory(original, "m", "M21")
        original = EventProjectEditor.addCompetitor(original, "runner", "Test", "Runner", "1", "123456")
        original = EventProjectEditor.assignCompetitorCategory(original, "runner", "w")
        assertEquals(1, original.raceData.competitorData.size)
        val source = original.raceData.courseMappings.single()
        val password = "test-course-password"
        original = original.withStoredCourseInfo(source.category.id, source.category.courseInfo, password)
            .withStoredIdealOrder(source.category.id, source.category.idealOrder, password)
        val result = DesktopCourseLibrary.apply(original, DesktopCourseLibraryEdit.Assign(source.category.id, setOf("w", "m")), password)
        assertEquals(listOf("w", "m"), result.raceData.categories.map { it.category.id })
        assertEquals(listOf("W21", "M21"), result.raceData.categories.map { it.category.name })
        assertTrue(result.raceData.courseMappings.isEmpty())
        assertEquals(original.raceData.competitorData, result.raceData.competitorData)
        result.raceData.categories.forEach { data ->
            assertNull(data.category.courseInfo)
            val info = data.category.storedCourseInfo(password)!!
            assertEquals(source.category.courseInfo!!.suppliedLegLengths, info.suppliedLegLengths)
            assertEquals(source.controlPoints.map { it.controlId }.toSet(), data.controlPoints.map { it.controlId }.toSet())
            assertNull(CourseDesignBindings.validationError(info))
        }
    }

    @Test fun deletingAnUnassignedCourseKeepsCategoriesAndSharedControlsAndReadoutsBlockEdits() {
        val original = EventProjectEditor.addCategory(unassigned(), "w", "W21")
        val source = original.raceData.courseMappings.single()
        val edit = DesktopCourseLibraryEdit.Delete(source.category.id)
        val result = DesktopCourseLibrary.apply(original, edit, null)
        assertTrue(result.raceData.courseMappings.isEmpty())
        assertEquals(original.raceData.categories, result.raceData.categories)
        assertEquals(original.raceData.controls, result.raceData.controls)
        assertThrows(IllegalArgumentException::class.java) {
            DesktopCourseLibrary.apply(DesktopCourseImportAvailabilityTest.withReadout(original), edit, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DesktopCourseLibrary.apply(EventCourseDrafts.start(original), edit, null)
        }
    }

    private fun assertCoursePreserved(source: EventCategoryData, target: EventCategoryData) {
        assertEquals(source.category.lengthMeters, target.category.lengthMeters)
        assertEquals(source.category.climbMeters, target.category.climbMeters)
        assertEquals(source.category.idealOrder, target.category.idealOrder)
        assertEquals(source.category.courseInfo!!.suppliedLegLengths, target.category.courseInfo!!.suppliedLegLengths)
        assertEquals(source.controlPoints.map { it.controlId }.toSet(), target.controlPoints.map { it.controlId }.toSet())
    }

    companion object {
        fun unassigned(): EventProjectFile {
            val base = DesktopCourseImportAvailabilityTest.project()
            val xml = DesktopIofCourseAnalysisTest().xml().replace(Regex("<ClassCourseAssignment>.*?</ClassCourseAssignment>"), "")
            val parsed = IofXmlImports.courseData(xml, base.raceData.race).parsedData
            assertTrue(parsed.assignedCategoryIds.isEmpty())
            val imported = EventProjectEditor.importIofCourseData(base, parsed).projectFile
            return DesktopIofCourseAnalysis.prepare(imported, imported.raceData.courseMappings.map { it.category.id }.toSet(), null,
                elevationLookup = { 100.0 }).project
        }
    }
}
