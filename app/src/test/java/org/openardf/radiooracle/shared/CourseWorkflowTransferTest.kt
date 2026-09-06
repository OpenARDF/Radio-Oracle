package org.openardf.radiooracle.shared

import org.junit.Assert.*
import org.junit.Test
import org.openardf.radiooracle.backend.room.withFreshImportIds
import org.openardf.radiooracle.backend.shared.toEventRaceData
import org.openardf.radiooracle.backend.shared.toRoomRaceData
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.event.*
import org.openardf.radiooracle.shared.publicresults.ProtectedCourseCipher
import java.io.File

/** Verifies the adapter boundary before final Apply can write authoritative bindings. */
class CourseWorkflowTransferTest {
    @Test fun preservesCatalogIdentityAcrossRoomAndProtectionModes() {
        val project = courseTransferFixture()
        val info = project.raceData.categories.single().category.courseInfo!!
        for (encrypted in listOf(false, true)) {
            val input = if (encrypted) EventProjectEditor.updateCategoryEncryptedCourseInfo(project, "m21",
                ProtectedCourseCipher.encryptCourseInfo(info, "fixture-password"))
            else EventProjectEditor.updateCategoryCourseInfo(project, "m21", info)
            val transferred = input.raceData.toRoomRaceData().withFreshImportIds().toEventRaceData()
            val category = transferred.categories.single().category
            val decoded = if (encrypted) ProtectedCourseCipher.decryptCourseInfo(category.encryptedCourseInfo!!, "fixture-password")
                else requireNotNull(category.courseInfo)
            assertEquals(info, decoded)
            assertEquals(131, transferred.controls.single().siCode)
            assertEquals("Fox1", transferred.controls.single().publicLabel ?: transferred.controls.single().label)
            assertEquals("Room must preserve explicit control references",
                CourseResolutionStatus.RESOLVED, CourseControlResolver.resolve(transferred.controls.single(), listOf(decoded)).status)
        }
        val report = File("build/reports/course-workflow/android-transfer.json")
        requireNotNull(report.parentFile).mkdirs()
        report.writeText("""{"scenario":"android-transfer","steps":[{"step":"protected-payload-round-trip","status":"passed"},{"step":"catalog-binding-round-trip","status":"passed"}]}""")
    }
    @Test fun nativeEditsDoNotSilentlyReinterpretBindingsOrStaleDrafts() {
        val source = courseTransferFixture()
        val draft = EventCourseDrafts.edit(source) { p ->
            EventProjectEditor.updateCategoryCourseInfo(p, "m21", p.raceData.categories.single().category.courseInfo!!.copy(lengthMeters = 123))
        }
        val native = draft.raceData.toRoomRaceData().withFreshImportIds()
        val roundTrip = EventProjectFile(raceData = native.toEventRaceData())
        EventCourseDrafts.requireCurrent(roundTrip)
        assertEquals(123, EventCourseDrafts.candidate(roundTrip).raceData.categories.single().category.courseInfo!!.lengthMeters)
        assertEquals(source.raceData.controls.single().id, roundTrip.raceData.controls.single().id)
        val changed = native.copy(aliases = native.aliases.map { it.copy(name = "Fox5") })
        val edited = EventProjectFile(raceData = changed.toEventRaceData())
        assertEquals("Fox5", edited.raceData.controls.single().publicLabel)
        assertEquals(CourseResolutionStatus.INVALID, CourseControlResolver.resolve(edited.raceData.controls.single(),
            listOf(edited.raceData.categories.single().category.courseInfo!!)).status)
        assertThrows(IllegalArgumentException::class.java) { EventCourseDrafts.requireCurrent(edited) }
    }

}

internal fun courseTransferFixture(): EventProjectFile {
    val control = EventControl("placement-independent-control-id", "race", "Fox1", 131, ControlPointType.CONTROL)
    var project = EventProjectFactory.createEmptyProject("race", "Transfer fixture", "2026-09-06T09:00")
    project = project.copy(raceData = project.raceData.copy(controls = listOf(control)))
    project = EventProjectEditor.addCategory(project, "m21", "M21")
    project = EventProjectEditor.replaceCategoryAssignedControls(project, "m21", listOf(control.id)) { "assignment-$it" }
    val info = CourseDesignBindings.prepare(ProtectedCourseInfo(controlPoints = listOf(
        ProtectedCourseControlPoint("location-1", "Fox1", 40.0, -75.0))), listOf(control),
        mapOf("location-1" to control.id), listOf("location-1"), "revision-1")
    return EventProjectEditor.updateCategoryCourseInfo(project, "m21", info)
}
