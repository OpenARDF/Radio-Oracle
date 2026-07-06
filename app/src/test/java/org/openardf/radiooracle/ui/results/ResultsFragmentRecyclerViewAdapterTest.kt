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

package org.openardf.radiooracle.ui.results

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.backend.room.entity.Category
import org.openardf.radiooracle.backend.room.entity.Competitor
import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorCategory
import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorData
import org.openardf.radiooracle.backend.wrappers.ResultWrapper
import java.util.UUID

class ResultsFragmentRecyclerViewAdapterTest {
    @Test
    fun expandAllResultParentRowsExpandsCollapsedCategoryRows() {
        val results = mutableListOf(
            resultWrapper("M21", competitor("One"), competitor("Two")),
            resultWrapper("W21", competitor("Three"))
        )

        results.expandAllResultParentRows()

        assertEquals(listOf(0, 1, 1, 0, 1), results.map { it.isChild })
        assertTrue(results[0].isExpanded)
        assertTrue(results[3].isExpanded)
        assertEquals("One", results[1].competitorData.single().competitorCategory.competitor.lastName)
        assertEquals("Two", results[2].competitorData.single().competitorCategory.competitor.lastName)
        assertEquals("Three", results[4].competitorData.single().competitorCategory.competitor.lastName)
    }

    @Test
    fun expandAllResultParentRowsDoesNotDuplicateExistingChildRows() {
        val results = mutableListOf(
            resultWrapper("M21", competitor("One"), competitor("Two"))
        )

        results.expandAllResultParentRows()
        results.expandAllResultParentRows()

        assertEquals(listOf(0, 1, 1), results.map { it.isChild })
        assertEquals(listOf("One", "Two"), results.drop(1).map { it.competitorData.single().competitorCategory.competitor.lastName })
    }

    private fun resultWrapper(categoryName: String, vararg competitors: CompetitorData): ResultWrapper =
        ResultWrapper(
            category = Category(categoryName),
            competitorData = competitors.toMutableList(),
            finished = competitors.size
        )

    private fun competitor(lastName: String): CompetitorData {
        val raceId = UUID.randomUUID()
        val competitor = Competitor(
            id = UUID.randomUUID(),
            raceId = raceId,
            firstName = "Runner",
            lastName = lastName,
            club = "",
            index = "",
            startNumber = 0
        )
        return CompetitorData(CompetitorCategory(competitor, null), readoutData = null)
    }
}
