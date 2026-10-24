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

import java.nio.file.Files
import java.time.LocalDateTime
import kotlin.io.path.createTempDirectory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.domain.RaceLevel

class DesktopNewCompetitionWizardTest {
    @After
    fun closeSeriesWorkspaces() {
        DesktopEventSeriesArchiveWorkspaces.closeAll()
    }

    @Test
    fun createsAndOpensOneConfiguredRaceFile() {
        val directory = createTempDirectory("radio-oracle-wizard-single-")
        val path = directory.resolve("Classic 80m.json")

        val result = DesktopNewCompetitionCreator.create(
            plan = DesktopNewCompetitionPlan(
                races = listOf(
                    race(
                        name = "Classic 80m",
                        format = DesktopRaceFormat.Classic80m,
                        level = RaceLevel.NATIONAL,
                        limit = "140"
                    )
                )
            ),
            destination = path,
            idFactory = { "race-1" }
        )

        val project = DesktopProjectFiles.read(result.openPath)
        assertTrue(Files.isRegularFile(path))
        assertEquals(path, result.rememberedPath)
        assertEquals("Classic 80m", project.raceData.race.name)
        assertEquals(RaceLevel.NATIONAL, project.raceData.race.raceLevel)
        assertEquals(8_400, project.raceData.race.timeLimitSeconds)
        assertEquals(DesktopRaceFormat.Classic80m.raceBand, project.raceData.race.raceBand)
    }

    @Test
    fun createsSeriesOnlyAfterAllRacesAndSeriesNameAreSpecified() {
        val directory = createTempDirectory("radio-oracle-wizard-series-")
        val path = directory.resolve("Championship.roseries")
        val ids = ArrayDeque(listOf("race-1", "race-2", "series-1"))

        val result = DesktopNewCompetitionCreator.create(
            plan = DesktopNewCompetitionPlan(
                races = listOf(
                    race("Classic", DesktopRaceFormat.Classic80m),
                    race("Classic", DesktopRaceFormat.Sprint)
                ),
                seriesName = "Autumn Championship"
            ),
            destination = path,
            idFactory = { ids.removeFirst() }
        )

        val workspace = requireNotNull(DesktopEventSeriesArchiveWorkspaces.workspaceForContainer(path))
        assertTrue(Files.isRegularFile(path))
        assertEquals(2, result.createdRaceCount)
        assertEquals("Autumn Championship", workspace.archive.seriesFile.name)
        assertEquals(listOf("Classic.json", "Classic 2.json"), workspace.archive.seriesFile.events.map { it.eventFilePath })
        assertEquals(listOf("race-1", "race-2"), workspace.archive.membersBySeriesEventId.keys.toList())
        assertTrue(workspace.archive.membersBySeriesEventId.values.all { it.seriesLink?.seriesId == "series-1" })
        assertEquals(workspace.memberPaths.first(), result.openPath)
    }

    private fun race(
        name: String,
        format: DesktopRaceFormat,
        level: RaceLevel = RaceLevel.PRACTICE,
        limit: String = "120"
    ): DesktopWizardRace =
        DesktopWizardRace(
            name = name,
            format = format,
            level = level,
            timeLimitMinutes = limit,
            startDateTime = LocalDateTime.of(2026, 10, 10, 9, 0)
        )
}
