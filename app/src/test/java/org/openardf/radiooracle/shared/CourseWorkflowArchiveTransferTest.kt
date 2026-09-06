package org.openardf.radiooracle.shared

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.room.ARDFRepository
import org.openardf.radiooracle.backend.room.withFreshImportIds
import org.openardf.radiooracle.backend.shared.toEventRaceData
import org.openardf.radiooracle.backend.shared.toRoomRaceData
import org.openardf.radiooracle.shared.event.*
import org.openardf.radiooracle.shared.publicresults.ProtectedCourseCipher
import java.io.File

/** The input is produced by the real desktop lifecycle, including its synthetic SPORTident readouts. */
@RunWith(RobolectricTestRunner::class)
class CourseWorkflowArchiveTransferTest {
    @Test fun desktopArchivePersistsThroughAndroidRoomAndExportsBack() = runBlocking {
        val configured = System.getProperty("radiooracle.courseTransferDirectory")
        assumeNotNull(configured)
        val directory = File(configured!!)
        val input = directory.resolve("transfer-input.roseries")
        val bytes = input.readBytes()
        val archive = EventSeriesArchiveZipCodec.decode(bytes)
        val context = RuntimeEnvironment.getApplication()
        DataProcessor.resetForTests(); ARDFRepository.resetForTests()
        context.deleteDatabase("event-database")
        ARDFRepository.initialize(context); DataProcessor.initialize(context)
        val processor = DataProcessor.get()
        for (encrypted in listOf(false, true)) {
            val returned = archive.membersBySeriesEventId.mapValues { (_, original) ->
                val source = if (encrypted) ProtectedCourseCipher.protectProjectCourseData(original, "fixture-password") else original
                val native = source.raceData.toRoomRaceData().withFreshImportIds()
                processor.saveRaceData(native)
                val stored = processor.getRaceData(native.race.id).toEventRaceData()
                val restored = source.copy(raceData = stored)
                val plain = if (encrypted) ProtectedCourseCipher.removeProjectCourseProtection(restored, "fixture-password") else restored
                assertEquals(original.raceData.competitorData.map { it.readoutData!!.result.points }.sortedDescending(),
                    plain.raceData.competitorData.map { it.readoutData!!.result.points }.sortedDescending())
                assertEquals("passed", CourseWorkflowAudit.audit(plain.raceData).status)
                restored
            }
            directory.resolve("transfer-return-${if (encrypted) "encrypted" else "plain"}.roseries")
                .writeBytes(EventSeriesArchiveZipCodec.encode(archive.copy(membersBySeriesEventId = returned)))
        }
        assertArrayEquals(bytes, input.readBytes())
    }
}
