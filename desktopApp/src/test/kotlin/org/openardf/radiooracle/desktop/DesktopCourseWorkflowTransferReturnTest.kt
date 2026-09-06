package org.openardf.radiooracle.desktop

import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.openardf.radiooracle.shared.event.*
import org.openardf.radiooracle.shared.publicresults.ProtectedCourseCipher
import java.nio.file.Files
import java.nio.file.Path

class DesktopCourseWorkflowTransferReturnTest {
    @Test fun returnedAndroidArchivesKeepAppliedDesignAndOriginalPunches() {
        val configured = System.getProperty("radiooracle.courseTransferDirectory")
        assumeNotNull(configured)
        val directory = Path.of(configured!!)
        val input = EventSeriesArchiveZipCodec.decode(Files.readAllBytes(directory.resolve("transfer-input.roseries")))
        for (mode in listOf("plain", "encrypted")) {
            val returned = EventSeriesArchiveZipCodec.decode(Files.readAllBytes(directory.resolve("transfer-return-$mode.roseries")))
            assertEquals(input.seriesFile, returned.seriesFile)
            input.membersBySeriesEventId.forEach { (id, original) ->
                val source = returned.member(id)
                val plain = if (mode == "encrypted") ProtectedCourseCipher.removeProjectCourseProtection(source, "fixture-password") else source
                assertEquals("passed", CourseWorkflowAudit.audit(plain.raceData).status)
                val expectedCourses = original.raceData.categories.associate { it.category.name to it.category.courseInfo }
                assertEquals(expectedCourses, plain.raceData.categories.associate { it.category.name to it.category.courseInfo })
                fun punches(project: EventProjectFile) = project.raceData.competitorData.map { competitor ->
                    competitor.readoutData!!.punches.map { it.punch.let { listOf(it.siCode, it.punchType, it.siTimeSeconds, it.originalSiTimeSeconds) }.toString() }.joinToString("|")
                }.sorted()
                assertEquals(punches(original), punches(plain))
                fun results(project: EventProjectFile) = project.raceData.competitorData.associate {
                    val result = it.readoutData!!.result
                    result.siNumber to result.copy(id = "", raceId = "", competitorId = null, categoryId = null)
                }
                assertEquals("Transfer changed a recorded result in $id", results(original), results(plain))
                val destination = directory.resolve("returned-$mode-$id.kml")
                DesktopControlsRouteKmlKmzExporter.exportPlainFile(DesktopControlsRouteKmlKmzExportTarget(destination,
                    DesktopControlsRouteKmlKmzExportFormat.Kml), plain)
                val exported = DesktopCourseFileReader.read(destination).controls
                expectedCourses.values.filterNotNull().flatMap { it.controlPoints }.forEach { point ->
                    assertTrue(exported.any { it.name == point.label && kotlin.math.abs(it.point.latitude - point.latitude) < 0.000001 })
                }
            }
        }
        Files.writeString(directory.resolve("round-trip.json"), """{"scenario":"desktop-archive-android-room-desktop","steps":[{"step":"plaintext","status":"passed"},{"step":"encrypted","status":"passed"},{"step":"applied-design-and-raw-punches","status":"passed"}]}""")
    }
}
