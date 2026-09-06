package org.openardf.radiooracle.backend.commands

import android.content.Context
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.room.withFreshImportIds
import org.openardf.radiooracle.backend.shared.toEventRaceData
import org.openardf.radiooracle.backend.shared.toRoomRaceData
import org.openardf.radiooracle.shared.event.*
import org.openardf.radiooracle.shared.publicresults.ProtectedCourseCipher
import java.io.File

/** Called only through the existing debug-only receiver, using an isolated synthetic acceptance archive. */
internal object CourseWorkflowDeviceSmoke {
    suspend fun run(context: Context, processor: DataProcessor) {
        val folder = File(context.filesDir, "course-workflow-smoke").also { it.mkdirs() }
        val input = folder.resolve("transfer-input.roseries")
        val originalBytes = input.readBytes()
        val archive = EventSeriesArchiveZipCodec.decode(originalBytes)
        require(archive.seriesFile.seriesId == "workflow-series" && archive.seriesFile.name == "Workflow Series") {
            "Only the generated workflow fixture is accepted by this debug command."
        }
        val created = mutableListOf<java.util.UUID>()
        try {
            for (encrypted in listOf(false, true)) {
                val members = archive.membersBySeriesEventId.mapValues { (_, project) ->
                    val source = if (encrypted) ProtectedCourseCipher.protectProjectCourseData(project, "fixture-password") else project
                    val native = source.raceData.toRoomRaceData().withFreshImportIds()
                    require(processor.getRace(native.race.id) == null) { "Synthetic race identity already exists." }
                    created += native.race.id
                    processor.saveRaceData(native)
                    val restored = source.copy(raceData = processor.getRaceData(native.race.id).toEventRaceData())
                    val plain = if (encrypted) ProtectedCourseCipher.removeProjectCourseProtection(restored, "fixture-password") else restored
                    require(CourseWorkflowAudit.audit(plain.raceData).status == "passed") { "Stored course bindings failed validation." }
                    restored
                }
                folder.resolve("transfer-return-${if (encrypted) "encrypted" else "plain"}.roseries")
                    .writeBytes(EventSeriesArchiveZipCodec.encode(archive.copy(membersBySeriesEventId = members)))
            }
            require(originalBytes.contentEquals(input.readBytes())) { "The input fixture changed." }
        } finally {
            // Only UUIDs created by this invocation are removed. Never select or modify the operator's race.
            created.forEach { processor.deleteRace(it) }
        }
        folder.resolve("complete.json").writeText("""{"scenario":"android-device-course-storage","steps":[{"step":"plaintext-and-encrypted-room-round-trip","status":"passed"}]}""")
    }
}
