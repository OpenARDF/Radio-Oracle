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

package org.openardf.radiooracle.shared.files

import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.PunchStatus
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.shared.event.EventAliasPunch
import org.openardf.radiooracle.shared.event.EventCategory
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventCompetitor
import org.openardf.radiooracle.shared.event.EventCompetitorCategory
import org.openardf.radiooracle.shared.event.EventCompetitorData
import org.openardf.radiooracle.shared.event.EventControl
import org.openardf.radiooracle.shared.event.EventPunch
import org.openardf.radiooracle.shared.event.EventRace
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.EventReadoutData
import org.openardf.radiooracle.shared.event.EventResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SplitResultExportsTest {
    @Test
    fun ranksIdenticalDirectedLegsForEveryCompetitionFormat() {
        RaceType.entries.forEach { raceType ->
            val source = raceData()
            val report = SplitResultExports.model(source.copy(race = source.race.copy(raceType = raceType)))
            val results = report.categories.single().results.associateBy { it.name }

            assertEquals(
                2,
                results.getValue("ALPHA Alice").splits[1].legPlace,
                "$raceType must compare Fox 1 to Fox 2 even when competitors visit foxes in different orders"
            )
            assertEquals(1, results.getValue("CHARLIE Cara").splits[1].legPlace, raceType.name)
            assertFalse(
                results.getValue("BRAVO Bob").splits[1].key ==
                    results.getValue("CHARLIE Cara").splits[1].key,
                "$raceType must not compare different directed legs at the same split index"
            )
        }
    }

    @Test
    fun ranksOnlyIdenticalDirectedLegsAcrossDifferentVisitOrders() {
        val report = SplitResultExports.model(raceData())
        val results = report.categories.single().results.associateBy { it.name }

        val alice = results.getValue("ALPHA Alice")
        val bob = results.getValue("BRAVO Bob")
        val cara = results.getValue("CHARLIE Cara")

        assertEquals(listOf("Start", "Fox 1", "Fox 2"), alice.splits.map { it.from.label })
        assertEquals(listOf("Fox 1", "Fox 2", "Finish"), alice.splits.map { it.control.label })
        assertEquals(listOf(3, 2, 3), alice.splits.map { it.legPlace })

        assertEquals(listOf("Fox 1", "Fox 3", "Fox 2", "Finish"), bob.splits.map { it.control.label })
        assertEquals(listOf(1, 1, 1, 1), bob.splits.map { it.legPlace })

        assertEquals(listOf("Fox 1", "Fox 2", "Finish"), cara.splits.map { it.control.label })
        assertEquals(listOf(1, 1, 1), cara.splits.map { it.legPlace })

        assertEquals(alice.splits[1].key, cara.splits[1].key)
        assertFalse(alice.splits[1].key == bob.splits[1].key)
        assertFalse(bob.splits[2].key == cara.splits[1].key)
    }

    @Test
    fun doesNotRankInvalidOrDuplicatePunches() {
        val source = raceData()
        val competitor = source.competitorData.first()
        val punches = competitor.readoutData!!.punches.mapIndexed { index, aliasPunch ->
            if (index == 1) {
                aliasPunch.copy(punch = aliasPunch.punch.copy(punchStatus = PunchStatus.INVALID))
            } else {
                aliasPunch
            }
        }
        val changed = source.copy(
            competitorData = listOf(
                competitor.copy(readoutData = competitor.readoutData.copy(punches = punches))
            )
        )

        val split = SplitResultExports.model(changed).categories.single().results.single().splits[0]

        assertEquals("MP", split.punchStatusText)
        assertNull(split.legPlace)
    }

    @Test
    fun exportsLongCsvWithCalculatedAndPresentationFields() {
        val csv = SplitResultExports.csv(raceData())

        assertTrue(csv.startsWith("Race;Start;Category;Place;Bib;Competitor;Club;Person ID;SI;Status;Points;Total Time;Transmitters;Split #;From;Control;SI Code;Punch Status;Leg Time;Leg Seconds;Cumulative Time;Cumulative Seconds;Leg Place\n"))
        assertTrue(csv.contains("Split Test;2026-08-30T10:00;M21;1;12;BRAVO Bob;BOK;B;100002;OK;3;00:11:50;Fox 1, Fox 3, Fox 2;1;Start;Fox 1;31;OK;00:01:30;90;00:01:30;90;1"))
        assertTrue(csv.contains(";3;Fox 3;Fox 2;32;OK;00:03:40;220;00:07:10;430;1"))
        assertTrue(csv.contains(";4;Fox 2;Finish;;OK;00:04:40;280;00:11:50;710;1"))
    }

    @Test
    fun createsLandscapePdfContainingSplitDetailsAndBibNumbers() {
        val text = SplitResultPdfExports.pdf(raceData()).decodeToString()

        assertTrue(text.startsWith("%PDF-1.4"))
        assertTrue(text.contains("/MediaBox [0 0 792 612]"))
        assertTrue(text.contains("Split Test"))
        assertTrue(text.contains("BRAVO Bob"))
        assertTrue(text.contains("Fox 3"))
        assertTrue(text.contains("00:03:40"))
        assertTrue(text.contains("(Bib)"))
        assertTrue(text.contains("(12)"))
    }

    private fun raceData(): EventRaceData {
        val race = EventRace(
            id = "race",
            name = "Split Test",
            apiKey = "",
            startDateTimeIso = "2026-08-30T10:00",
            raceType = RaceType.CLASSIC,
            raceLevel = RaceLevel.PRACTICE,
            raceBand = RaceBand.M80,
            timeLimitSeconds = 7_200
        )
        val category = EventCategory(
            id = "m21",
            raceId = race.id,
            name = "M21",
            isMan = true,
            maxAge = null,
            lengthMeters = 5_000,
            climbMeters = 100,
            order = 1,
            differentProperties = false,
            raceType = null,
            raceBand = null,
            timeLimitSeconds = null,
            controlPointsString = "31 32 33"
        )
        val competitors = listOf(
            competitor(race.id, category.id, "alice", "Alice", "Alpha", "A", "11", 100001),
            competitor(race.id, category.id, "bob", "Bob", "Bravo", "B", "12", 100002),
            competitor(race.id, category.id, "cara", "Cara", "Charlie", "C", "13", 100003)
        )
        val paths = mapOf(
            "alice" to listOf(31 to 100L, 32 to 200L, 0 to 300L),
            "bob" to listOf(31 to 90L, 33 to 120L, 32 to 220L, 0 to 280L),
            "cara" to listOf(31 to 90L, 32 to 180L, 0 to 280L)
        )
        return EventRaceData(
            race = race,
            categories = listOf(EventCategoryData(category, emptyList(), competitors)),
            aliases = emptyList(),
            competitorData = competitors.mapIndexed { index, competitor ->
                EventCompetitorData(
                    competitorCategory = EventCompetitorCategory(competitor, category),
                    readoutData = readout(
                        raceId = race.id,
                        competitor = competitor,
                        path = paths.getValue(competitor.id),
                        runtime = paths.getValue(competitor.id).sumOf { it.second },
                        points = paths.getValue(competitor.id).count { it.first != 0 },
                        index = index
                    )
                )
            },
            unmatchedReadoutData = emptyList(),
            controls = listOf(
                EventControl("control-31", race.id, "F1", 31, ControlPointType.CONTROL, publicLabel = "Fox 1"),
                EventControl("control-32", race.id, "F2", 32, ControlPointType.CONTROL, publicLabel = "Fox 2"),
                EventControl("control-33", race.id, "F3", 33, ControlPointType.CONTROL, publicLabel = "Fox 3")
            )
        )
    }

    private fun competitor(
        raceId: String,
        categoryId: String,
        id: String,
        firstName: String,
        lastName: String,
        personId: String,
        bib: String,
        siNumber: Int
    ): EventCompetitor = EventCompetitor(
        id = id,
        raceId = raceId,
        categoryId = categoryId,
        firstName = firstName,
        lastName = lastName,
        club = "BOK",
        index = personId,
        isMan = true,
        birthYear = null,
        siNumber = siNumber,
        siRent = false,
        drawnStartTimeSeconds = null,
        bibNumber = bib
    )

    private fun readout(
        raceId: String,
        competitor: EventCompetitor,
        path: List<Pair<Int, Long>>,
        runtime: Long,
        points: Int,
        index: Int
    ): EventReadoutData {
        val resultId = "result-${competitor.id}"
        var elapsed = 36_000L
        val punches = buildList {
            add(punch(raceId, resultId, competitor.siNumber, 0, elapsed, 0, SIRecordType.START, 0))
            path.forEachIndexed { punchIndex, (code, legSeconds) ->
                elapsed += legSeconds
                add(
                    punch(
                        raceId = raceId,
                        resultId = resultId,
                        siNumber = competitor.siNumber,
                        code = code,
                        siTime = elapsed,
                        splitSeconds = legSeconds,
                        type = if (code == 0) SIRecordType.FINISH else SIRecordType.CONTROL,
                        order = punchIndex + 1
                    )
                )
            }
        }
        return EventReadoutData(
            result = EventResult(
                id = resultId,
                raceId = raceId,
                competitorId = competitor.id,
                siNumber = competitor.siNumber,
                cardType = 0,
                checkTimeSeconds = null,
                startTimeSeconds = 36_000,
                finishTimeSeconds = 36_000 + runtime,
                readoutDateTimeIso = "2026-08-30T10:20",
                automaticStatus = true,
                resultStatus = ResultStatus.OK,
                points = points,
                runTimeSeconds = runtime,
                modified = false,
                sent = false,
                place = index + 1
            ),
            punches = punches
        )
    }

    private fun punch(
        raceId: String,
        resultId: String,
        siNumber: Int?,
        code: Int,
        siTime: Long,
        splitSeconds: Long,
        type: SIRecordType,
        order: Int
    ): EventAliasPunch = EventAliasPunch(
        punch = EventPunch(
            id = "$resultId-$order",
            raceId = raceId,
            resultId = resultId,
            cardNumber = siNumber,
            siCode = code,
            siTimeSeconds = siTime,
            originalSiTimeSeconds = siTime,
            punchType = type,
            order = order,
            punchStatus = PunchStatus.VALID,
            splitSeconds = splitSeconds
        ),
        alias = null
    )
}
