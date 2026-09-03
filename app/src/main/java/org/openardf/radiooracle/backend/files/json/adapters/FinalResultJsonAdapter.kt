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

package org.openardf.radiooracle.backend.files.json.adapters;

import com.squareup.moshi.ToJson;
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.files.json.temps.AliasJson
import org.openardf.radiooracle.backend.files.json.temps.FinalResultsJson
import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorData
import org.openardf.radiooracle.backend.room.entity.embeddeds.RaceData
import org.openardf.radiooracle.shared.event.EventCategorySort

/** Moshi adapter for exporting final results from a complete race aggregate. */
class FinalResultJsonAdapter(val dataProcessor: DataProcessor) {
    /** Serializes categories, aliases, and competitor results for final-result JSON export. */
    @ToJson
    fun toJson(raceData: RaceData): FinalResultsJson {
        val categoryAdapter = CategoryJsonAdapter(raceData.race.id)
        val competitorAdapter = CompetitorJsonAdapter(raceData.race, dataProcessor)

        val categoryIdsWithResults = raceData.competitorData.mapNotNull { competitorData ->
            if (competitorData.readoutData == null) {
                null
            } else {
                competitorData.competitorCategory.category?.id
                    ?: competitorData.competitorCategory.competitor.categoryId
            }
        }.toSet()
        val sortedCategories = raceData.categories
            .filter { it.category.id in categoryIdsWithResults }
            .sortedWith { left, right ->
                EventCategorySort.compareNames(left.category.name, right.category.name)
            }
        val categoryNamesById = raceData.categories.associate { it.category.id to it.category.name }
        val sortedCompetitors = raceData.competitorData.sortedWith(
            EventCategorySort.byName { competitorData: CompetitorData ->
                competitorData.competitorCategory.category?.name
                    ?: categoryNamesById[competitorData.competitorCategory.competitor.categoryId].orEmpty()
            }.thenBy {
                it.readoutData?.result?.place?.takeIf { place -> place > 0 } ?: Int.MAX_VALUE
            }.thenBy {
                it.competitorCategory.competitor.lastName
            }.thenBy {
                it.competitorCategory.competitor.firstName
            }
        )

        return FinalResultsJson(
            categories = sortedCategories.map { cat -> categoryAdapter.toJson(cat) },
            aliases = raceData.aliases.map { al -> AliasJson(al.siCode, al.name) },
            competitors = sortedCompetitors.map { cd -> competitorAdapter.toJson(cd) },
        )
    }
}
