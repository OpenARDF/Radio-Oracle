/*
 * MIT License
 *
 * Copyright (c) 2025 Pavel Kolský
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.openardf.radiooracle.desktop

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventRace
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.EventSeriesArchive
import org.openardf.radiooracle.shared.event.EventSeriesArchiveZipCodec
import org.openardf.radiooracle.shared.event.EventSeriesEvent
import org.openardf.radiooracle.shared.event.EventSeriesFile
import java.nio.file.Files

class DesktopEventSeriesArchiveWorkspaceTest {
    @After
    fun closeWorkspaces() {
        DesktopEventSeriesArchiveWorkspaces.closeAll()
    }

    @Test
    fun createsSingleArchiveAndRoutesMemberWritesBackIntoIt() {
        val root = Files.createTempDirectory("radio-oracle-roseries-test")
        val archivePath = root.resolve("Championship.roseries")
        val workspace = DesktopEventSeriesArchiveWorkspaces.create(archivePath, archive())

        val memberPath = workspace.memberPaths.single()
        val updatedProject = workspace.archive.member("day-1").let { project ->
            project.copy(
                raceData = project.raceData.copy(
                    race = project.raceData.race.copy(name = "Updated Day")
                )
            )
        }
        DesktopProjectFiles.write(memberPath, updatedProject)

        assertTrue(Files.isRegularFile(archivePath))
        assertEquals(listOf("Championship.roseries"), Files.list(root).use { stream ->
            stream.map { it.fileName.toString() }.sorted().toList()
        })
        val reopened = EventSeriesArchiveZipCodec.decode(Files.readAllBytes(archivePath))
        assertEquals("Updated Day", reopened.member("day-1").raceData.race.name)
        assertEquals("Updated Day", reopened.seriesFile.events.single().displayName)
    }

    @Test
    fun refusesToOverwriteArchiveChangedOutsideWorkspace() {
        val root = Files.createTempDirectory("radio-oracle-roseries-conflict")
        val archivePath = root.resolve("Championship.roseries")
        val workspace = DesktopEventSeriesArchiveWorkspaces.create(archivePath, archive())
        Files.write(archivePath, byteArrayOf(1, 2, 3))

        assertThrows(IllegalArgumentException::class.java) {
            workspace.writeMember(workspace.memberPaths.single(), projectFile("Changed"))
        }
    }

    @Test
    fun removingFinalMemberWritesStandaloneBeforeDeletingContainer() {
        val root = Files.createTempDirectory("radio-oracle-roseries-remove")
        val archivePath = root.resolve("Championship.roseries")
        val standalonePath = root.resolve("Day 1.json")
        val workspace = DesktopEventSeriesArchiveWorkspaces.create(archivePath, archive())

        val removal = workspace.removeMember("day-1", standalonePath)

        assertNull(removal.remainingArchive)
        assertTrue(Files.isRegularFile(standalonePath))
        assertNull(DesktopProjectFiles.read(standalonePath).seriesLink)
        assertFalse(Files.exists(archivePath))
    }

    @Test
    fun refusesToDetachMemberOverItsSeriesContainer() {
        val root = Files.createTempDirectory("radio-oracle-roseries-remove-target")
        val archivePath = root.resolve("Championship.roseries")
        val workspace = DesktopEventSeriesArchiveWorkspaces.create(archivePath, archive())

        assertThrows(IllegalArgumentException::class.java) {
            workspace.removeMember("day-1", archivePath)
        }

        assertTrue(Files.isRegularFile(archivePath))
        assertEquals(1, EventSeriesArchiveZipCodec.decode(Files.readAllBytes(archivePath)).memberCount)
    }

    private fun archive(): EventSeriesArchive {
        val event = EventSeriesEvent("day-1", "Day 1.json", 0, "Day 1")
        return EventSeriesArchive(
            seriesFile = EventSeriesFile(
                seriesId = "series-1",
                name = "Championship",
                events = listOf(event)
            ),
            membersBySeriesEventId = mapOf("day-1" to projectFile("Day 1"))
        )
    }

    private fun projectFile(name: String): EventProjectFile =
        EventProjectFile(
            raceData = EventRaceData(
                race = EventRace(
                    id = name.lowercase().replace(" ", "-"),
                    name = name,
                    apiKey = "",
                    startDateTimeIso = "2026-06-25T10:00:00",
                    raceType = RaceType.CLASSIC,
                    raceLevel = RaceLevel.PRACTICE,
                    raceBand = RaceBand.M2,
                    timeLimitSeconds = 3_600
                ),
                categories = emptyList(),
                aliases = emptyList(),
                competitorData = emptyList(),
                unmatchedReadoutData = emptyList()
            )
        )
}
