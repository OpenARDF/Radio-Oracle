package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.publicresults.ProtectedCourseCipher
import kotlin.test.*

class EventCourseDraftTest {
    private fun project(): EventProjectFile {
        var p = EventProjectFactory.createEmptyProject("race", "Draft fixture", "2026-09-06T09:00")
        p = p.copy(raceData = p.raceData.copy(controls = listOf(EventControl("fox", "race", "Fox1", 131, ControlPointType.CONTROL))))
        p = EventProjectEditor.addCategory(p, "m21", "M21")
        p = EventProjectEditor.replaceCategoryAssignedControls(p, "m21", listOf("fox")) { "assignment-$it" }
        return EventProjectEditor.updateCategoryCourseInfo(p, "m21", ProtectedCourseInfo(idealOrder = "Fox1",
            controlPoints = listOf(ProtectedCourseControlPoint("fox", "Fox1", 40.0, -75.0))))
    }

    private fun move(p: EventProjectFile, latitude: Double) = EventProjectEditor.updateCategoryCourseInfo(p, "m21",
        p.raceData.categories.single().category.courseInfo!!.let { info -> info.copy(controlPoints = info.controlPoints.map { it.copy(latitude = latitude) }) })

    @Test fun editsAndReopenPreserveAppliedCourseAndCancellationRestoresIt() {
        val original = project()
        val draft = EventCourseDrafts.edit(original) { move(it, 41.0) }
        val reopened = EventProjectFileJson.decode(EventProjectFileJson.encode(draft))
        assertEquals(40.0, reopened.raceData.categories.single().category.courseInfo!!.controlPoints.single().latitude)
        assertEquals(41.0, EventCourseDrafts.candidate(reopened).raceData.categories.single().category.courseInfo!!.controlPoints.single().latitude)
        assertEquals(original.raceData, EventCourseDrafts.cancel(reopened).raceData)
        val candidate = EventCourseDrafts.candidate(reopened)
        val committed = EventCourseDrafts.commit(reopened, candidate, EventCourseDrafts.snapshotHash(candidate))
        assertNull(committed.raceData.courseDraft)
        assertEquals(41.0, committed.raceData.categories.single().category.courseInfo!!.controlPoints.single().latitude)
    }

    @Test fun lateCalculationsAndConcurrentAppliedChangesCannotOverwriteTheCurrentDesign() {
        val draftA = EventCourseDrafts.edit(project()) { move(it, 41.0) }
        val calculatedA = EventCourseDrafts.candidate(draftA)
        val token = EventCourseDrafts.snapshotHash(calculatedA)
        val draftB = EventCourseDrafts.edit(draftA) { move(it, 42.0) }
        assertFailsWith<IllegalArgumentException> { EventCourseDrafts.commit(draftB, calculatedA, token) }
        assertFailsWith<IllegalArgumentException> { EventCourseDrafts.requireCurrent(move(draftA, 43.0)) }
        assertEquals(42.0, EventCourseDrafts.candidate(draftB).raceData.categories.single().category.courseInfo!!.controlPoints.single().latitude)
    }

    @Test fun storageNormalizationKeepsCurrentDraftsCurrentAndStaleDraftsStale() {
        val source = project().let { p -> p.copy(raceData = p.raceData.copy(categories = p.raceData.categories.map {
            it.copy(category = it.category.copy(isMan = false, differentProperties = true, timeLimitSeconds = 123))
        })) }
        val draft = EventCourseDrafts.edit(source) { move(it, 41.0) }
        val reopened = EventProjectFileJson.decode(EventProjectFileJson.encode(draft))
        EventCourseDrafts.requireCurrent(reopened)
        assertTrue(EventCourseDrafts.candidate(reopened).raceData.categories.single().category.isMan)
        val stale = move(draft, 42.0)
        assertFailsWith<IllegalArgumentException> { EventCourseDrafts.requireCurrent(EventProjectFileJson.decode(EventProjectFileJson.encode(stale))) }
    }

    @Test fun protectionCoversAppliedAndDraftCourseDataAndPreservesFreshness() {
        val draft = EventCourseDrafts.edit(project()) { move(it, 41.0) }
        val protected = ProtectedCourseCipher.protectProjectCourseData(draft, "fixture-password")
        EventCourseDrafts.requireCurrent(protected)
        assertTrue(EventCourseDrafts.protectedCategories(protected.raceData).all { it.category.courseInfo == null && it.category.encryptedCourseInfo != null })
        val changed = ProtectedCourseCipher.reencryptProjectCourseProtection(protected, "fixture-password", "next-password")
        EventCourseDrafts.requireCurrent(changed)
        assertFailsWith<IllegalArgumentException> { ProtectedCourseCipher.removeProjectCourseProtection(changed, "wrong") }
        val restored = ProtectedCourseCipher.removeProjectCourseProtection(changed, "next-password")
        assertEquals(draft.raceData, restored.raceData)
    }
}
