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

package org.openardf.radiooracle.shared.event

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventCategoryCompetitorSyncTest {
    @Test
    fun identifiesMissingAndEmptyCategories() {
        val m21 = category("m21", "M21")
        val w21 = category("w21", "W21").copy(lengthMeters = 4_000)
        val m40 = category("m40", "M40")
        val project = project(
            categories = listOf(categoryData(m21), categoryData(w21)),
            competitors = listOf(competitor("one", m21), competitor("two", m40))
        )

        val plan = EventCategoryCompetitorSync.plan(project.raceData)

        assertEquals(listOf("M40"), plan.missingCategories.map { it.categoryName })
        assertEquals(1, plan.missingCategories.single().competitorCount)
        assertEquals(listOf("W21"), plan.emptyCategories.map { it.categoryName })
        assertTrue(plan.emptyCategories.single().hasCourseData)
        assertTrue(plan.hasMismatch)
    }

    @Test
    fun addsNeededCategoriesAndRemovesSelectedEmptyCategories() {
        val m21 = category("m21", "M21")
        val w21 = category("w21", "W21")
        val m40 = category("m40", "M40")
        val project = project(
            categories = listOf(categoryData(m21), categoryData(w21)),
            competitors = listOf(competitor("one", m21), competitor("two", m40))
        )

        val outcome = EventCategoryCompetitorSync.apply(
            projectFile = project,
            missingCategoryIdsToSync = setOf("m40"),
            emptyCategoryIdsToRemove = setOf("w21")
        )

        assertEquals(1, outcome.addedCategoryCount)
        assertEquals(1, outcome.repairedAssignmentCount)
        assertEquals(1, outcome.removedCategoryCount)
        assertEquals(listOf("M21", "M40"), outcome.projectFile.raceData.categories.map { it.category.name })
        assertEquals(
            "m40",
            outcome.projectFile.raceData.competitorData
                .first { it.competitorCategory.competitor.id == "two" }
                .competitorCategory
                .competitor
                .categoryId
        )
        assertEquals(1, outcome.projectFile.raceData.categories.first { it.category.id == "m40" }.competitors.size)
        assertFalse(EventCategoryCompetitorSync.plan(outcome.projectFile.raceData).hasMismatch)
    }

    @Test
    fun repairsEquivalentCategoryIdentityWithoutAddingDuplicate() {
        val activeM21 = category("active-m21", "M21")
        val staleM21 = category("stale-m21", "M-21")
        val project = project(
            categories = listOf(categoryData(activeM21)),
            competitors = listOf(competitor("one", staleM21))
        )
        val plan = EventCategoryCompetitorSync.plan(project.raceData)

        assertEquals("active-m21", plan.missingCategories.single().existingEquivalentCategoryId)
        assertTrue(plan.emptyCategories.isEmpty())

        val outcome = EventCategoryCompetitorSync.apply(
            projectFile = project,
            missingCategoryIdsToSync = setOf("stale-m21"),
            emptyCategoryIdsToRemove = emptySet()
        )

        assertEquals(0, outcome.addedCategoryCount)
        assertEquals(1, outcome.repairedAssignmentCount)
        assertEquals("active-m21", outcome.projectFile.raceData.competitorData.single().competitorCategory.competitor.categoryId)
        assertFalse(EventCategoryCompetitorSync.plan(outcome.projectFile.raceData).hasMismatch)
    }

    private fun project(
        categories: List<EventCategoryData>,
        competitors: List<EventCompetitorData>
    ): EventProjectFile =
        EventProjectFactory.createEmptyProject(
            raceId = "race",
            raceName = "Category Sync",
            startDateTimeIso = "2026-07-23T09:00"
        ).let { project ->
            project.copy(
                raceData = project.raceData.copy(
                    categories = categories,
                    competitorData = competitors
                )
            )
        }

    private fun category(id: String, name: String): EventCategory =
        EventCategory(
            id = id,
            raceId = "race",
            name = name,
            isMan = name.startsWith("M"),
            maxAge = null,
            lengthMeters = 0,
            climbMeters = 0,
            order = 0,
            differentProperties = false,
            raceType = null,
            raceBand = null,
            timeLimitSeconds = null,
            controlPointsString = ""
        )

    private fun categoryData(category: EventCategory): EventCategoryData =
        EventCategoryData(
            category = category,
            controlPoints = emptyList(),
            competitors = emptyList()
        )

    private fun competitor(id: String, category: EventCategory): EventCompetitorData =
        EventCompetitorData(
            competitorCategory = EventCompetitorCategory(
                competitor = EventCompetitor(
                    id = id,
                    raceId = "race",
                    categoryId = category.id,
                    firstName = "First",
                    lastName = id,
                    club = "",
                    index = "",
                    isMan = category.isMan,
                    birthYear = null,
                    siNumber = null,
                    siRent = false,
                    drawnStartTimeSeconds = null
                ),
                category = category
            ),
            readoutData = null
        )
}
