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

import ResultJsonAdapter
import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.files.json.temps.CompetitorJson
import org.openardf.radiooracle.backend.helpers.TimeProcessor
import org.openardf.radiooracle.backend.room.entity.Competitor
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorCategory
import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorData
import org.openardf.radiooracle.backend.room.entity.embeddeds.ReadoutData
import org.openardf.radiooracle.backend.room.enums.ResultStatus
import java.util.UUID

/** Moshi adapter for converting competitors and their optional readouts to the race JSON schema. */
class CompetitorJsonAdapter(val race: Race, val dataProcessor: DataProcessor) {
    /** Serializes one competitor and omits invalid readouts that cannot be reconstructed safely. */
    @ToJson
    fun toJson(competitorData: CompetitorData): CompetitorJson {
        val competitor = competitorData.competitorCategory.competitor
        return CompetitorJson(

            first_name = competitor.firstName,
            last_name = competitor.lastName,
            competitor_club = competitor.club,
            competitor_category = (competitorData.competitorCategory.category?.name ?: ""),
            competitor_index = competitor.index,
            competitor_gender = competitor.isMan,
            birth_year = competitor.birthYear,
            si_number = competitor.siNumber,
            si_rent = competitor.siRent,
            start_number = competitor.startNumber,
            competitor_start_time = competitor.drawnRelativeStartTime?.let {
                TimeProcessor.durationToFormattedString(
                    it, true
                )
            } ?: "",
            result = if (competitorData.readoutData != null &&
                // Do not serialize when start or finish time is missing.
                competitorData.readoutData!!.result.resultStatus != ResultStatus.ERROR
            ) {
                ResultJsonAdapter(race, dataProcessor).toJson(competitorData)
            } else null
        )
    }

    /** Deserializes a competitor and attaches a readout when the payload includes one. */
    @FromJson
    fun fromJson(competitorJson: CompetitorJson): CompetitorData {
        val competitor = Competitor(
            id = UUID.randomUUID(),
            raceId = race.id,
            categoryId = null,
            firstName = competitorJson.first_name,
            lastName = competitorJson.last_name,
            club = competitorJson.competitor_club ?: "",
            index = competitorJson.competitor_index ?: "",
            isMan = competitorJson.competitor_gender,
            birthYear = competitorJson.birth_year,
            siNumber = competitorJson.si_number,
            siRent = competitorJson.si_rent ?: false,
            startNumber = competitorJson.start_number ?: 0,
            drawnRelativeStartTime = if (competitorJson.competitor_start_time?.isNotEmpty() == true) {
                TimeProcessor.minuteStringToDuration(competitorJson.competitor_start_time)
            } else null
        )
        if (competitorJson.result != null) {
            val resultData = ResultJsonAdapter(
                race,
                dataProcessor
            ).fromJson(competitorJson.result)
            resultData.result.competitorId = competitor.id
            resultData.result.siNumber = competitor.siNumber
            val readoutData = ReadoutData(resultData.result, resultData.punches)
            return CompetitorData(CompetitorCategory(competitor, null), readoutData)
        }
        return CompetitorData(CompetitorCategory(competitor, null), null)
    }
}
