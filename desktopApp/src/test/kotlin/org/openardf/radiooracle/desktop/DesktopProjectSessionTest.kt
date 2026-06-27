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
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.EventCategory
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventRace
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.EventValidationIssue
import org.openardf.radiooracle.shared.event.EventValidationRules
import java.nio.file.Path

class DesktopProjectSessionTest {
    @Test
    fun startsWithoutAnOpenProject() {
        val session = DesktopProjectSession(InMemoryProjectFileStore())

        assertNull(session.currentProject)
        assertNull(session.currentPath)
        assertEquals(false, session.hasUnsavedChanges)
    }

    @Test
    fun opensAProjectAndRemembersItsPath() {
        val path = Path.of("event.rom.json")
        val projectFile = projectFile("Opened Race")
        val store = InMemoryProjectFileStore(mapOf(path to projectFile))
        val session = DesktopProjectSession(store)

        session.open(path)

        assertEquals(projectFile, session.currentProject)
        assertEquals(path, session.currentPath)
        assertEquals(false, session.hasUnsavedChanges)
    }

    @Test
    fun startsNewProjectWithoutAPathAndMarksItDirty() {
        val projectFile = projectFile("New Race")
        val session = DesktopProjectSession(InMemoryProjectFileStore())

        session.newProject(projectFile)

        assertEquals(projectFile, session.currentProject)
        assertNull(session.currentPath)
        assertEquals(true, session.hasUnsavedChanges)
    }

    @Test
    fun updatesCurrentProjectAndMarksItDirty() {
        val path = Path.of("event.rom.json")
        val projectFile = projectFile("Original Race")
        val store = InMemoryProjectFileStore(mapOf(path to projectFile))
        val session = DesktopProjectSession(store)

        session.open(path)
        val updatedProject = session.updateCurrentProject { current ->
            current.copy(
                raceData = current.raceData.copy(
                    race = current.raceData.race.copy(name = "Updated Race")
                )
            )
        }

        assertEquals("Updated Race", updatedProject.raceData.race.name)
        assertEquals(updatedProject, session.currentProject)
        assertEquals(true, session.hasUnsavedChanges)
    }

    @Test
    fun savesCurrentProjectToRememberedPath() {
        val path = Path.of("event.rom.json")
        val projectFile = projectFile("Original Race")
        val store = InMemoryProjectFileStore(mapOf(path to projectFile))
        val session = DesktopProjectSession(store)

        session.open(path)
        session.updateCurrentProject { current ->
            current.copy(
                raceData = current.raceData.copy(
                    race = current.raceData.race.copy(name = "Updated Race")
                )
            )
        }
        session.save()

        assertEquals("Updated Race", store.writtenProjects[path]?.raceData?.race?.name)
        assertEquals(false, session.hasUnsavedChanges)
    }

    @Test
    fun noOpUpdateDoesNotClearExistingUnsavedChanges() {
        val path = Path.of("event.rom.json")
        val projectFile = projectFile("Original Race")
        val store = InMemoryProjectFileStore(mapOf(path to projectFile))
        val session = DesktopProjectSession(store)

        session.open(path)
        session.updateCurrentProject { current ->
            current.copy(
                raceData = current.raceData.copy(
                    race = current.raceData.race.copy(name = "Updated Race")
                )
            )
        }
        session.updateCurrentProject { it }

        assertEquals(true, session.hasUnsavedChanges)
    }

    @Test
    fun savesCurrentProjectToAChosenPath() {
        val source = Path.of("source.rom.json")
        val target = Path.of("target.rom.json")
        val projectFile = projectFile("Saved Race")
        val store = InMemoryProjectFileStore(mapOf(source to projectFile))
        val session = DesktopProjectSession(store)

        session.open(source)
        session.saveAs(target)

        assertEquals(projectFile, store.writtenProjects[target])
        assertEquals(target, session.currentPath)
        assertEquals(false, session.hasUnsavedChanges)
    }

    @Test
    fun saveAdoptsStorageNormalizedProjectInMemory() {
        val path = Path.of("event.rom.json")
        val projectFile = projectFile("Legacy Category Race").withLegacyCategoryRaceSettings()
        val store = InMemoryProjectFileStore(mapOf(path to projectFile))
        val session = DesktopProjectSession(store)

        session.open(path)
        assertEquals(
            true,
            EventValidationRules.validateRaceData(session.currentProject!!.raceData)
                .contains(EventValidationIssue.LegacyCategoryRaceSettings("W21"))
        )
        session.save()

        assertEquals(
            false,
            EventValidationRules.validateRaceData(session.currentProject!!.raceData)
                .contains(EventValidationIssue.LegacyCategoryRaceSettings("W21"))
        )
        assertEquals(false, session.currentProject!!.raceData.categories.single().category.differentProperties)
        assertNull(session.currentProject!!.raceData.categories.single().category.raceType)
        assertNull(session.currentProject!!.raceData.categories.single().category.raceBand)
        assertNull(session.currentProject!!.raceData.categories.single().category.timeLimitSeconds)
        assertEquals(session.currentProject, store.writtenProjects[path])
    }

    @Test
    fun exportsCurrentProjectWithoutChangingSessionState() {
        val source = Path.of("source.rom.json")
        val exportPath = Path.of("export.rom.json")
        val projectFile = projectFile("Original Race")
        val store = InMemoryProjectFileStore(mapOf(source to projectFile))
        val session = DesktopProjectSession(store)

        session.open(source)
        val editedProject = session.updateCurrentProject { current ->
            current.copy(
                raceData = current.raceData.copy(
                    race = current.raceData.race.copy(name = "Edited Race")
                )
            )
        }
        session.exportCopy(exportPath)

        assertEquals(editedProject, store.writtenProjects[exportPath])
        assertEquals(source, session.currentPath)
        assertEquals(true, session.hasUnsavedChanges)
    }

    @Test
    fun closesProjectWhenThereAreNoUnsavedChanges() {
        val path = Path.of("event.rom.json")
        val projectFile = projectFile("Closable Race")
        val session = DesktopProjectSession(InMemoryProjectFileStore(mapOf(path to projectFile)))

        session.open(path)
        session.closeProject()

        assertNull(session.currentProject)
        assertNull(session.currentPath)
        assertEquals(false, session.hasUnsavedChanges)
    }

    @Test
    fun rejectsCloseWithUnsavedChanges() {
        val path = Path.of("event.rom.json")
        val projectFile = projectFile("Dirty Race")
        val session = DesktopProjectSession(InMemoryProjectFileStore(mapOf(path to projectFile)))

        session.open(path)
        session.updateCurrentProject { current ->
            current.copy(
                raceData = current.raceData.copy(
                    race = current.raceData.race.copy(name = "Edited Race")
                )
            )
        }

        assertThrows(IllegalStateException::class.java) {
            session.closeProject()
        }
        assertEquals(projectFile.raceData.race.id, session.currentProject?.raceData?.race?.id)
        assertEquals(path, session.currentPath)
        assertEquals(true, session.hasUnsavedChanges)
    }

    @Test
    fun closesAndDiscardsUnsavedChangesWhenRequested() {
        val path = Path.of("event.rom.json")
        val projectFile = projectFile("Dirty Race")
        val session = DesktopProjectSession(InMemoryProjectFileStore(mapOf(path to projectFile)))

        session.open(path)
        session.updateCurrentProject { current ->
            current.copy(
                raceData = current.raceData.copy(
                    race = current.raceData.race.copy(name = "Edited Race")
                )
            )
        }
        session.closeProject(discardUnsavedChanges = true)

        assertNull(session.currentProject)
        assertNull(session.currentPath)
        assertEquals(false, session.hasUnsavedChanges)
    }

    private fun projectFile(name: String): EventProjectFile =
        EventProjectFile(
            raceData = EventRaceData(
                race = EventRace(
                    id = name,
                    name = name,
                    apiKey = "",
                    startDateTimeIso = "2026-05-31T10:00",
                    raceType = RaceType.CLASSIC,
                    raceLevel = RaceLevel.PRACTICE,
                    raceBand = RaceBand.M80,
                    timeLimitSeconds = 7_200
                ),
                categories = emptyList(),
                aliases = emptyList(),
                competitorData = emptyList(),
                unmatchedReadoutData = emptyList()
            )
        )

    private fun EventProjectFile.withLegacyCategoryRaceSettings(): EventProjectFile {
        val category = EventCategory(
            id = "category",
            raceId = raceData.race.id,
            name = "W21",
            isMan = false,
            maxAge = null,
            lengthMeters = 5_000,
            climbMeters = 100,
            order = 1,
            differentProperties = true,
            raceType = RaceType.SPRINT,
            raceBand = RaceBand.M2,
            timeLimitSeconds = 3_600,
            controlPointsString = ""
        )
        return copy(
            raceData = raceData.copy(
                categories = listOf(EventCategoryData(category, emptyList(), emptyList()))
            )
        )
    }
}

private class InMemoryProjectFileStore(
    private val projects: Map<Path, EventProjectFile> = emptyMap()
) : ProjectFileStore {
    val writtenProjects = mutableMapOf<Path, EventProjectFile>()

    override fun read(path: Path): EventProjectFile =
        projects.getValue(path)

    override fun write(path: Path, projectFile: EventProjectFile) {
        writtenProjects[path] = projectFile
    }
}
