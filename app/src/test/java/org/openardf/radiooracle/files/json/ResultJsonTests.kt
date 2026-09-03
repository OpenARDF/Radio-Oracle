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

package org.openardf.radiooracle.files.json

import ResultJsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import junit.framework.TestCase.assertEquals
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.files.json.adapters.FinalResultJsonAdapter
import org.openardf.radiooracle.backend.files.json.adapters.LocalDateTimeAdapter
import org.openardf.radiooracle.backend.files.json.adapters.RaceDataJsonAdapter
import org.openardf.radiooracle.backend.files.json.temps.FinalResultsJson
import org.openardf.radiooracle.backend.files.json.temps.ResultJson
import org.openardf.radiooracle.backend.results.ResultsProcessor
import org.openardf.radiooracle.backend.room.entity.Alias
import org.openardf.radiooracle.backend.room.entity.Category
import org.openardf.radiooracle.backend.room.entity.Competitor
import org.openardf.radiooracle.backend.room.entity.ControlPoint
import org.openardf.radiooracle.backend.room.entity.Punch
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.room.entity.Result
import org.openardf.radiooracle.backend.room.entity.embeddeds.AliasPunch
import org.openardf.radiooracle.backend.room.entity.embeddeds.CategoryData
import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorCategory
import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorData
import org.openardf.radiooracle.backend.room.entity.embeddeds.RaceData
import org.openardf.radiooracle.backend.room.entity.embeddeds.ReadoutData
import org.openardf.radiooracle.shared.domain.PunchStatus
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.backend.sportident.SITime
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.LocalTime
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

class ResultJsonTests {
    val dataProcessor = mock(DataProcessor::class.java)

    @Before
    fun setup() {
        `when`(dataProcessor.resultStatusToShortString(org.mockito.kotlin.any()))
            .thenReturn("OK")

        `when`(dataProcessor.punchStatusToShortString(org.mockito.kotlin.any()))
            .thenReturn("OK")
    }

    @Test
    fun testLiveResultsToJson() {
        val race = Race()
        race.startDateTime = LocalDateTime.of(2025, 11, 23, 13, 0, 0)

        val result = Result()
        result.checkTime = SITime(LocalTime.of(12, 49, 3))
        result.startTime = SITime(LocalTime.of(13, 0, 0))
        result.finishTime = SITime(LocalTime.of(14, 15, 0))
        result.resultStatus = ResultStatus.OK
        result.runTime = Duration.ofMinutes(75)
        result.readoutTime = LocalDateTime.of(2025, 11, 23, 14, 18, 24)

        val punches = arrayListOf(
            Punch(13, SITime(LocalTime.of(13, 0, 0)), SIRecordType.START, 1),
            Punch(31, SITime(LocalTime.of(13, 35, 0)), SIRecordType.CONTROL, 1),
            Punch(32, SITime(LocalTime.of(13, 43, 11)), SIRecordType.CONTROL, 1),
            Punch(33, SITime(LocalTime.of(14, 5, 50)), SIRecordType.CONTROL, 1),
            Punch(34, SITime(LocalTime.of(14, 10, 22)), SIRecordType.CONTROL, 1),
        )
        ResultsProcessor.calculateSplits(punches)

        val ap = punches.mapIndexed { index, punch ->
            AliasPunch(
                punch,
                Alias(punch.siCode, index.toString())
            )
        }
        val readoutData = ReadoutData(result, ap)

        val compData = CompetitorData(
            CompetitorCategory(Competitor(), Category("A")),
            readoutData
        )
        val json = ResultJsonAdapter(race, dataProcessor).toJson(compData)

        val moshi: Moshi = Moshi.Builder()
            .add(RaceDataJsonAdapter(dataProcessor))
            .add(LocalDateTimeAdapter())
            .add(KotlinJsonAdapterFactory())
            .build()
        val out = moshi.adapter(ResultJson::class.java).toJson(json)

        val stream =
            requireNotNull(this::class.java.classLoader)
                .getResourceAsStream("json/json_results_filtered_start.ardfjs")
        val valid = stream.bufferedReader().use { it.readText() }.filterNot { it.isWhitespace() }

        assertEquals(valid, out)
    }

    @Test
    fun testFinalResultsToJson() {
        val race = Race()
        race.startDateTime = LocalDateTime.of(2025, 11, 23, 13, 0, 0)

        val result = Result()
        result.checkTime = SITime(LocalTime.of(12, 49, 3))
        result.startTime = SITime(LocalTime.of(13, 0, 0))
        result.finishTime = SITime(LocalTime.of(14, 15, 0))
        result.resultStatus = ResultStatus.OK
        result.runTime = Duration.ofMinutes(75)
        result.readoutTime = LocalDateTime.of(2025, 11, 23, 14, 18, 24)

        val punches = arrayListOf(
            Punch(13, SITime(LocalTime.of(13, 0, 0)), SIRecordType.START, 1),
            Punch(31, SITime(LocalTime.of(13, 35, 0)), SIRecordType.CONTROL, 1),
            Punch(32, SITime(LocalTime.of(13, 43, 11)), SIRecordType.CONTROL, 1),
            Punch(33, SITime(LocalTime.of(14, 5, 50)), SIRecordType.CONTROL, 1),
            Punch(34, SITime(LocalTime.of(14, 10, 22)), SIRecordType.CONTROL, 1),
        )
        ResultsProcessor.calculateSplits(punches)

        val ap = punches.mapIndexed { index, punch ->
            AliasPunch(
                punch,
                Alias(punch.siCode, index.toString())
            )
        }
        val category = Category("A")
        val competitor = Competitor()
        val readoutData = ReadoutData(result, ap)

        val compData = listOf(
            CompetitorData(
                CompetitorCategory(Competitor(), category),
                readoutData
            )
        )
        val controlPoints = listOf(
            ControlPoint(31),
            ControlPoint(32),
            ControlPoint(33),
            ControlPoint(34)
        )

        val aliases = listOf(
            Alias(31, "1"),
            Alias(32, "2"),
            Alias(33, "3"),
            Alias(34, "4")
        )
        val catData = listOf(CategoryData(category, controlPoints, listOf(competitor)))

        val rd = RaceData(race, catData, aliases, compData, emptyList())
        val json = FinalResultJsonAdapter(dataProcessor).toJson(rd)

        val moshi: Moshi = Moshi.Builder()
            .add(RaceDataJsonAdapter(dataProcessor))
            .add(LocalDateTimeAdapter())
            .add(KotlinJsonAdapterFactory())
            .build()
        val out = moshi.adapter(FinalResultsJson::class.java).toJson(json)

        val stream =
            requireNotNull(this::class.java.classLoader)
                .getResourceAsStream("json/json_final_results.ardfjs")
        val valid = stream.bufferedReader().use { it.readText() }.filterNot { it.isWhitespace() }

        assertEquals(valid, out)
    }

    @Test
    fun finalResultsSortCategoriesWomenFirstAndOmitCategoriesWithoutResults() {
        val race = Race()
        val categories = listOf("M21", "W16", "W21").mapIndexed { index, name ->
            Category(name).apply {
                raceId = race.id
                order = index
            }
        }
        val competitorData = categories.mapIndexed { index, category ->
            val competitor = Competitor().apply {
                id = UUID.nameUUIDFromBytes("competitor-$index".toByteArray())
                raceId = race.id
                categoryId = category.id
                firstName = category.name
                lastName = "Runner"
            }
            CompetitorData(
                competitorCategory = CompetitorCategory(competitor, category),
                readoutData = if (category.name == "W16") {
                    null
                } else {
                    ReadoutData(
                        Result().apply {
                            this.raceId = race.id
                            competitorId = competitor.id
                            place = 1
                        },
                        emptyList()
                    )
                }
            )
        }
        val raceData = RaceData(
            race = race,
            categories = categories.map { category -> CategoryData(category, emptyList(), emptyList()) },
            aliases = emptyList(),
            competitorData = competitorData,
            unmatchedReadoutData = emptyList()
        )

        val exported = FinalResultJsonAdapter(dataProcessor).toJson(raceData)

        assertEquals(listOf("W21", "M21"), exported.categories.map { it.category_name })
    }
}
