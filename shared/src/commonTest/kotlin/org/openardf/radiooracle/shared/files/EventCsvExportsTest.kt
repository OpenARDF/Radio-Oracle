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
import org.openardf.radiooracle.shared.event.EventControlPoint
import org.openardf.radiooracle.shared.event.EventPunch
import org.openardf.radiooracle.shared.event.EventRace
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.EventReadoutData
import org.openardf.radiooracle.shared.event.EventResult
import kotlin.test.Test
import kotlin.test.assertEquals

class EventCsvExportsTest {
    @Test
    fun exportsPortableCategoryRows() {
        assertEquals(
            "M21;1;99;5000;100;1;;;;2;31,32\n",
            EventCsvExports.categories(raceData())
        )
    }

    @Test
    fun exportedCategoryRowsParseWithSharedImportContract() {
        val result = EventCsvImports.parseAndroidCategoryRows(EventCsvExports.categories(raceData()))

        assertEquals(emptyList(), result.invalidLines)
        val row = result.rows.single()
        assertEquals("M21", row.name)
        assertEquals("31 32", row.controlPointsText)
    }

    @Test
    fun exportsPortableCompetitorRows() {
        assertEquals(
            "123456;Test;Runner;M21;1;1985;;OK Test;;7;OK001\n",
            EventCsvExports.competitors(raceData())
        )
    }

    @Test
    fun exportsPortableCompetitorStartRows() {
        assertEquals(
            "7;Runner;Test;M21;;10:00;OK001;;OK Test;123456\n",
            EventCsvExports.competitorStarts(raceData())
        )
    }

    @Test
    fun exportsCompetitorStartRowsByCategory() {
        assertEquals(
            """
            3;Gamma;Carol;M21;;12:00;OK003;;OK Test;333333
            4;NoTime;Dave;M21;;;OK004;;OK Test;444444
            2;Beta;Bob;W21;;11:00;OK002;;OK Test;222222
            1;Alpha;Alice;W21;;13:00;OK001;;OK Test;111111
            """.trimIndent() + "\n",
            EventCsvExports.competitorStartsByCategory(startVariantRaceData())
        )
    }

    @Test
    fun exportsCompetitorStartRowsByMinute() {
        assertEquals(
            """
            2;Beta;Bob;W21;;11:00;OK002;;OK Test;222222
            3;Gamma;Carol;M21;;12:00;OK003;;OK Test;333333
            1;Alpha;Alice;W21;;13:00;OK001;;OK Test;111111
            4;NoTime;Dave;M21;;;OK004;;OK Test;444444
            """.trimIndent() + "\n",
            EventCsvExports.competitorStartsByMinute(startVariantRaceData())
        )
    }

    @Test
    fun exportsPortableReadoutRows() {
        assertEquals(
            "123456;00:01:40;00:10:00;00:20:00;2;31;00:12:00;32;00:15:00\n",
            EventCsvExports.readouts(raceData())
        )
    }

    @Test
    fun exportsPortableResultRows() {
        assertEquals(
            "1;RUNNER Test;OK;2;00:10:00\n",
            EventCsvExports.results(raceData())
        )
    }

    private fun raceData(): EventRaceData {
        val race = EventRace(
            id = "race",
            name = "CSV Race",
            apiKey = "",
            startDateTimeIso = "2026-06-01T10:00",
            raceType = RaceType.CLASSIC,
            raceLevel = RaceLevel.PRACTICE,
            raceBand = RaceBand.M80,
            timeLimitSeconds = 7_200
        )
        val category = EventCategory(
            id = "category",
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
            controlPointsString = "31 32"
        )
        val competitor = EventCompetitor(
            id = "competitor",
            raceId = race.id,
            categoryId = category.id,
            firstName = "Test",
            lastName = "Runner",
            club = "OK Test",
            index = "OK001",
            isMan = true,
            birthYear = 1985,
            siNumber = 123456,
            siRent = false,
            startNumber = 7,
            drawnStartTimeSeconds = 600
        )
        val readout = EventReadoutData(
            result = EventResult(
                id = "result",
                raceId = race.id,
                competitorId = competitor.id,
                siNumber = 123456,
                cardType = 0,
                checkTimeSeconds = 100,
                startTimeSeconds = 600,
                finishTimeSeconds = 1_200,
                readoutDateTimeIso = "2026-06-01T10:20",
                automaticStatus = true,
                resultStatus = ResultStatus.OK,
                points = 2,
                runTimeSeconds = 600,
                modified = false,
                sent = false,
                place = 1
            ),
            punches = listOf(
                punch("punch-1", race.id, "result", 31, 720, 1),
                punch("punch-2", race.id, "result", 32, 900, 2)
            )
        )

        return EventRaceData(
            race = race,
            categories = listOf(
                EventCategoryData(
                    category = category,
                    controlPoints = listOf(
                        EventControlPoint("control-1", category.id, 31, ControlPointType.CONTROL, 1),
                        EventControlPoint("control-2", category.id, 32, ControlPointType.CONTROL, 2)
                    ),
                    competitors = listOf(competitor)
                )
            ),
            aliases = emptyList(),
            competitorData = listOf(
                EventCompetitorData(
                    competitorCategory = EventCompetitorCategory(competitor, category),
                    readoutData = readout
                )
            ),
            unmatchedReadoutData = emptyList()
        )
    }

    private fun startVariantRaceData(): EventRaceData {
        val race = EventRace(
            id = "race",
            name = "CSV Race",
            apiKey = "",
            startDateTimeIso = "2026-06-01T10:00",
            raceType = RaceType.CLASSIC,
            raceLevel = RaceLevel.PRACTICE,
            raceBand = RaceBand.M80,
            timeLimitSeconds = 7_200
        )
        val m21 = EventCategory(
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
            controlPointsString = ""
        )
        val w21 = m21.copy(id = "w21", name = "W21", isMan = false)
        val competitors = listOf(
            variantCompetitor(race.id, w21.id, "Alice", "Alpha", "OK001", 111111, 1, 780),
            variantCompetitor(race.id, w21.id, "Bob", "Beta", "OK002", 222222, 2, 660),
            variantCompetitor(race.id, m21.id, "Carol", "Gamma", "OK003", 333333, 3, 720),
            variantCompetitor(race.id, m21.id, "Dave", "NoTime", "OK004", 444444, 4, null)
        )
        return EventRaceData(
            race = race,
            categories = listOf(
                EventCategoryData(m21, controlPoints = emptyList(), competitors = competitors.filter { it.categoryId == m21.id }),
                EventCategoryData(w21, controlPoints = emptyList(), competitors = competitors.filter { it.categoryId == w21.id })
            ),
            aliases = emptyList(),
            competitorData = competitors.map { competitor ->
                EventCompetitorData(
                    competitorCategory = EventCompetitorCategory(
                        competitor = competitor,
                        category = if (competitor.categoryId == m21.id) m21 else w21
                    ),
                    readoutData = null
                )
            },
            unmatchedReadoutData = emptyList()
        )
    }

    private fun variantCompetitor(
        raceId: String,
        categoryId: String,
        firstName: String,
        lastName: String,
        index: String,
        siNumber: Int,
        startNumber: Int,
        drawnStartTimeSeconds: Long?
    ): EventCompetitor =
        EventCompetitor(
            id = index,
            raceId = raceId,
            categoryId = categoryId,
            firstName = firstName,
            lastName = lastName,
            club = "OK Test",
            index = index,
            isMan = categoryId == "m21",
            birthYear = 1985,
            siNumber = siNumber,
            siRent = false,
            startNumber = startNumber,
            drawnStartTimeSeconds = drawnStartTimeSeconds
        )

    private fun punch(
        id: String,
        raceId: String,
        resultId: String,
        siCode: Int,
        siTimeSeconds: Long,
        order: Int
    ): EventAliasPunch =
        EventAliasPunch(
            punch = EventPunch(
                id = id,
                raceId = raceId,
                resultId = resultId,
                cardNumber = 123456,
                siCode = siCode,
                siTimeSeconds = siTimeSeconds,
                originalSiTimeSeconds = siTimeSeconds,
                punchType = SIRecordType.CONTROL,
                order = order,
                punchStatus = PunchStatus.UNKNOWN,
                splitSeconds = 0
            ),
            alias = null
        )
}
