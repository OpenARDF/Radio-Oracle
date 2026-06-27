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

package org.openardf.radiooracle.backend.files.json.adapters

import UnmatchedResultJsonAdapter
import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.files.json.temps.AliasJson
import org.openardf.radiooracle.backend.files.json.temps.RaceJson
import org.openardf.radiooracle.backend.helpers.TimeProcessor
import org.openardf.radiooracle.backend.room.entity.Alias
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorData
import org.openardf.radiooracle.backend.room.entity.embeddeds.RaceData
import org.openardf.radiooracle.backend.room.enums.RaceBand
import org.openardf.radiooracle.backend.room.enums.RaceLevel
import org.openardf.radiooracle.backend.room.enums.RaceType
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

/** Moshi adapter for importing and exporting a complete race aggregate as JSON. */
class RaceDataJsonAdapter(val dataProcessor: DataProcessor) {

    /** Serializes the race and all child aggregates into the race JSON schema. */
    @ToJson
    fun toJson(raceData: RaceData): RaceJson {
        val categoryAdapter = CategoryJsonAdapter(raceData.race.id)
        val competitorAdapter = CompetitorJsonAdapter(raceData.race, dataProcessor)
        val unmatchedAdapter = UnmatchedResultJsonAdapter(raceData.race, dataProcessor)

        val race = raceData.race
        return RaceJson(
            race_name = race.name,
            race_start = race.startDateTime,
            race_type = race.raceType,
            race_band = race.raceBand,
            race_level = race.raceLevel,
            race_time_limit = race.timeLimit.toMinutes().toString(),
            race_api_key = race.apiKey,
            categories = raceData.categories.map { cat -> categoryAdapter.toJson(cat) },
            aliases = raceData.aliases.map { al -> AliasJson(al.siCode, al.name) },
            competitors = raceData.competitorData.map { cd -> competitorAdapter.toJson(cd) },
            unmatched_results = raceData.unmatchedReadoutData.map { rd -> unmatchedAdapter.toJson(rd) }
        )
    }

    /** Deserializes a race payload with fresh local identifiers and rebuilt relations. */
    @FromJson
    fun fromJson(raceJson: RaceJson): RaceData {

        val race = Race(
            id = UUID.randomUUID(),
            name = raceJson.race_name,
            apiKey = raceJson.race_api_key ?: "",
            startDateTime = raceJson.race_start ?: LocalDateTime.now(),
            raceType = raceJson.race_type ?: RaceType.CLASSIC,
            raceBand = raceJson.race_band ?: RaceBand.M80,
            raceLevel = raceJson.race_level ?: RaceLevel.PRACTICE,
            timeLimit = Duration.ofMinutes(raceJson.race_time_limit?.toLong() ?: 120)
        )
        val categoryAdapter = CategoryJsonAdapter(race.id)
        val competitorAdapter = CompetitorJsonAdapter(race, dataProcessor)
        val unmatchedAdapter = UnmatchedResultJsonAdapter(race, dataProcessor)

        val categories = raceJson.categories.mapIndexed { index, catJson ->
            categoryAdapter.fromJson(catJson).also {
                it.category.raceId = race.id
                it.category.order = index
            }
        }

        val aliases = raceJson.aliases?.map { aliasJson ->
            Alias(
                UUID.randomUUID(),
                race.id,
                aliasJson.alias_si_code,
                aliasJson.alias_name
            ).also { it.raceId = race.id }
        }

        val competitorData = ArrayList<CompetitorData>()
        val startNumberByDrawnStartTime = raceJson.competitors
            .mapNotNull { json ->
                json.competitor_start_time
                    ?.takeIf { it.isNotBlank() }
                    ?.let { runCatching { TimeProcessor.minuteStringToDuration(it) }.getOrNull() }
            }
            .distinct()
            .sorted()
            .withIndex()
            .associate { (index, startTime) -> startTime to index + 1 }

        // Start numbers are departure slots. Recompute them from start times when
        // the backup contains starts so simultaneous starters share a number.
        for (compJson in raceJson.competitors) {
            val cd = competitorAdapter.fromJson(compJson)
                .also { it.competitorCategory.competitor.raceId = race.id }

            if (compJson.competitor_category.isNotBlank()) {
                cd.competitorCategory.competitor.categoryId =
                    categories.find { compJson.competitor_category == it.category.name }?.category?.id
            }

            cd.competitorCategory.competitor.drawnRelativeStartTime
                ?.let(startNumberByDrawnStartTime::get)
                ?.let { cd.competitorCategory.competitor.startNumber = it }
            competitorData.add(cd)
        }

        val unmatchedData =
            raceJson.unmatched_results?.map { json -> unmatchedAdapter.fromJson(json) }

        return RaceData(
            race = race,
            categories = categories,
            aliases = aliases ?: emptyList(),
            competitorData = competitorData,
            unmatchedReadoutData = unmatchedData?.toList() ?: emptyList()
        )
    }
}
