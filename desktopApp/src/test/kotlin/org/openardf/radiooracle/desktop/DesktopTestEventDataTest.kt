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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.event.EventProjectFactory
import org.openardf.radiooracle.shared.event.EventResultDetails

class DesktopTestEventDataTest {
    @Test
    fun insertsCompatibleStagedTestData() {
        val emptyProject = testProject()

        val controls = DesktopTestEventData.insertControls(emptyProject)
        val categories = DesktopTestEventData.insertCategories(controls.projectFile)
        val competitors = DesktopTestEventData.insertCompetitors(categories.projectFile)

        assertTrue(controls.projectFile.raceData.controls.any { it.siCode == 90 && it.type == ControlPointType.BEACON })
        assertEquals(3, competitors.projectFile.raceData.categories.count { it.category.id.startsWith("test-cat-") })
        assertEquals(12, competitors.projectFile.raceData.competitorData.count {
            it.competitorCategory.competitor.id.startsWith("test-competitor-")
        })
        assertEquals(
            emptyList<String>(),
            competitors.projectFile.raceData.competitorData
                .filter { it.competitorCategory.competitor.id.startsWith("test-competitor-") }
                .filter {
                    it.competitorCategory.competitor.categoryId == null ||
                        it.competitorCategory.competitor.siNumber == null ||
                        it.competitorCategory.competitor.drawnStartTimeSeconds == null
                }
                .map { it.competitorCategory.competitor.id }
        )
    }

    @Test
    fun testSportIdentDownloadsUseGeneratedCompetitorsAndCategories() {
        val projectWithFixtures = DesktopTestEventData.insertCompetitors(testProject()).projectFile

        val downloads = DesktopTestSportIdentDownloads.insert(projectWithFixtures, maxDownloads = 12)
        val results = EventResultDetails.from(downloads.projectFile.raceData)

        assertEquals(12, downloads.insertedCount)
        assertEquals(12, results.size)
        assertTrue(results.map { it.categoryName }.toSet().containsAll(setOf("Test M21", "Test M50", "Test W21")))
        assertTrue(results.any { it.pointsText.toInt() > 0 })
    }

    @Test
    fun stagedInsertionsAreIdempotent() {
        val first = DesktopTestEventData.insertCompetitors(testProject())
        val second = DesktopTestEventData.insertCompetitors(first.projectFile)

        assertEquals(0, second.insertedCount)
        assertEquals(first.projectFile.raceData.categories.size, second.projectFile.raceData.categories.size)
        assertEquals(first.projectFile.raceData.competitorData.size, second.projectFile.raceData.competitorData.size)
    }

    private fun testProject() =
        EventProjectFactory.createEmptyProject(
            raceId = "race",
            raceName = "Testing",
            startDateTimeIso = "2026-06-05T09:00"
        )
}
