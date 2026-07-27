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

package org.openardf.radiooracle.publicresults

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.backend.publicresults.AndroidPublicResultsSiteExports
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventRace
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.publicresults.PublicResultsRaceRenderRequest
import org.openardf.radiooracle.shared.publicresults.PublicResultsSiteCatalog
import java.nio.file.Files

class AndroidPublicResultsSiteExportsTest {
    @Test
    fun overwritesCurrentRaceButRetainsOtherPublishedRace() {
        val root = Files.createTempDirectory("android-public-results").toFile()
        val first = AndroidPublicResultsSiteExports.exportRace(
            root,
            request("race-a", "Old Name"),
            GENERATED_AT,
            "test"
        )
        AndroidPublicResultsSiteExports.exportRace(
            root,
            request("race-b", "Other Race"),
            GENERATED_AT,
            "test"
        )
        val updated = AndroidPublicResultsSiteExports.exportRace(
            root,
            request("race-a", "New Name"),
            GENERATED_AT,
            "test"
        )
        val catalog = PublicResultsSiteCatalog.parse(root.resolve("data/races.json").readText())

        assertEquals(2, catalog.size)
        assertEquals(setOf("race:race-a", "race:race-b"), catalog.map { it.publicationId }.toSet())
        assertFalse(root.resolve(first.eventPath).exists())
        assertTrue(root.resolve("${updated.eventPath}/data/event-summary.json").isFile)
    }

    @Test
    fun seriesPublishesEveryMemberButOnlySeriesAtTopLevel() {
        val root = Files.createTempDirectory("android-public-series").toFile()
        val exported = AndroidPublicResultsSiteExports.exportSeries(
            directory = root,
            seriesId = "series-1",
            seriesName = "Two Race Series",
            races = listOf(
                request("race-a", "Day 1"),
                request("race-b", "Day 2")
            ),
            generatedAtIso = GENERATED_AT,
            appVersion = "test"
        )
        val catalog = PublicResultsSiteCatalog.parse(root.resolve("data/races.json").readText())
        val seriesJson = root.resolve("${exported.eventPath}/data/series-results.json").readText()

        assertEquals(listOf("series:series-1"), catalog.map { it.publicationId })
        assertTrue(root.resolve("2026-07-27-day-1/data/public-results.json").isFile)
        assertTrue(root.resolve("2026-07-27-day-2/data/public-results.json").isFile)
        assertTrue(seriesJson.contains("../2026-07-27-day-1/data/public-results.json"))
        assertTrue(seriesJson.contains("../2026-07-27-day-2/data/public-results.json"))
    }

    private fun request(id: String, name: String): PublicResultsRaceRenderRequest =
        PublicResultsRaceRenderRequest(
            projectFile = EventProjectFile(
                raceData = EventRaceData(
                    race = EventRace(
                        id = id,
                        name = name,
                        apiKey = "",
                        startDateTimeIso = "2026-07-27T10:00:00",
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
        )

    companion object {
        private const val GENERATED_AT = "2026-07-27T12:00:00Z"
    }
}
