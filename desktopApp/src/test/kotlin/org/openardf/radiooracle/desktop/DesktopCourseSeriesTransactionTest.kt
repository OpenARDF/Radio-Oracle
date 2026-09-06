package org.openardf.radiooracle.desktop

import org.junit.Assert.*
import org.junit.Test
import org.openardf.radiooracle.shared.event.*
import java.nio.file.Files
import java.nio.file.Path

class DesktopCourseSeriesTransactionTest {
    private fun fixture(): Pair<Path, Map<Path, EventProjectFile>> {
        val root = Files.createTempDirectory("course-series-transaction-")
        val projects = (1..2).associate { index -> root.resolve("race-$index.json") to
            EventProjectFactory.createEmptyProject("race-$index", "Race $index", "2026-09-06T09:00") }
        projects.forEach(DesktopProjectFiles::write)
        val manifest = root.resolve(EVENT_SERIES_FILE_NAME)
        DesktopEventSeriesFiles.write(manifest, EventSeriesFile(seriesId = "series", name = "Fixture", events = projects.entries.mapIndexed { index, entry ->
            EventSeriesEvent("member-$index", entry.key.fileName.toString(), index, entry.value.raceData.race.name)
        }))
        return manifest to projects
    }

    @Test fun failedSecondWriteRestoresExactOriginalBytesAndAllowsRetry() {
        val (manifest, original) = fixture()
        val bytes = original.mapValues { Files.readString(it.key) }
        val expected = original.keys.associateWith(DesktopEventSeriesFiles::eventFingerprint)
        val updated = original.mapValues { EventCourseDrafts.start(it.value) }
        assertThrows(java.io.IOException::class.java) {
            DesktopCourseSeriesTransaction.write(manifest, updated, expected) { if (it == 1) throw java.io.IOException("injected disk failure") }
        }
        bytes.forEach { (path, text) -> assertEquals(text, Files.readString(path)) }
        DesktopCourseSeriesTransaction.write(manifest, updated, expected)
        updated.forEach { (path, project) -> assertEquals(project.raceData, DesktopProjectFiles.read(path).raceData) }
    }

    @Test fun reopeningRecoversAnInterruptedMultiFileWrite() {
        val (manifest, original) = fixture()
        val bytes = original.mapValues { Files.readString(it.key) }
        val expected = original.keys.associateWith(DesktopEventSeriesFiles::eventFingerprint)
        assertThrows(AssertionError::class.java) {
            DesktopCourseSeriesTransaction.write(manifest, original.mapValues { EventCourseDrafts.start(it.value) }, expected) {
                if (it == 1) throw AssertionError("simulated interruption before second member")
            }
        }
        assertNotEquals(bytes.values.first(), Files.readString(bytes.keys.first()))
        DesktopEventSeriesFiles.read(manifest)
        bytes.forEach { (path, text) -> assertEquals(text, Files.readString(path)) }
    }

    @Test fun externalEditBlocksCommitAndIsNeverOverwrittenDuringRecovery() {
        val (manifest, original) = fixture()
        val expected = original.keys.associateWith(DesktopEventSeriesFiles::eventFingerprint)
        val last = original.keys.last()
        assertThrows(IllegalArgumentException::class.java) {
            DesktopCourseSeriesTransaction.write(manifest, original.mapValues { EventCourseDrafts.start(it.value) }, expected) {
                if (it == 1) Files.writeString(last, "external edit")
            }
        }
        assertEquals("external edit", Files.readString(last))
        assertThrows(IllegalArgumentException::class.java) { DesktopEventSeriesFiles.read(manifest) }
        assertEquals("external edit", Files.readString(last))
    }

    @Test fun archiveBatchPreservesDraftsAndRejectsChangedSnapshotsBeforeReplacing() {
        val (manifest, original) = fixture()
        val series = DesktopEventSeriesFiles.read(manifest)
        val archive = EventSeriesArchive(series, series.events.associate { event -> event.seriesEventId to original.getValue(manifest.parent.resolve(event.eventFilePath)) })
        val path = manifest.parent.resolve("fixture.roseries")
        val workspace = DesktopEventSeriesArchiveWorkspaces.create(path, archive)
        try {
            val targets = workspace.memberPaths.associateWith { EventCourseDrafts.start(workspace.readMember(it)) }
            val expected = targets.keys.associateWith(DesktopEventSeriesFiles::eventFingerprint)
            DesktopEventSeriesFiles.writeEvents(workspace.manifestPath, targets, expected)
            val reopened = EventSeriesArchiveZipCodec.decode(Files.readAllBytes(path))
            assertTrue(reopened.membersBySeriesEventId.values.all { it.raceData.courseDraft != null })
            val bytes = Files.readAllBytes(path).toList()
            assertThrows(IllegalArgumentException::class.java) { DesktopEventSeriesFiles.writeEvents(workspace.manifestPath, targets, expected) }
            assertEquals(bytes, Files.readAllBytes(path).toList())
        } finally { workspace.close() }
    }
}
