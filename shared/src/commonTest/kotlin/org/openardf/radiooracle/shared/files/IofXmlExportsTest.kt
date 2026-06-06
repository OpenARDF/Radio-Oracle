package org.openardf.radiooracle.shared.files

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
import org.openardf.radiooracle.shared.event.EventPunch
import org.openardf.radiooracle.shared.event.EventRace
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.EventReadoutData
import org.openardf.radiooracle.shared.event.EventResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IofXmlExportsTest {
    @Test
    fun exportsIofStartListUsingAndroidStructure() {
        val xml = IofXmlExports.startList(raceData(), creator = "Radio-Oracle 1.0")

        assertTrue(xml.startsWith("""<?xml version="1.0" encoding="UTF-8"?>"""))
        assertTrue(xml.contains("""<StartList xmlns="http://www.orienteering.org/datastandard/3.0" iofVersion="3.0" creator="Radio-Oracle 1.0">"""))
        assertTrue(xml.contains("<Event>"))
        assertTrue(xml.contains("<Name>IOF &amp; Start Race</Name>"))
        assertTrue(xml.contains("<Date>2026-06-01</Date>"))
        assertTrue(xml.contains("<Time>10:00:00</Time>"))
        assertTrue(xml.contains("<ClassStart>"))
        assertTrue(xml.contains("<Class>"))
        assertTrue(xml.contains("<Name>M21</Name>"))
        assertTrue(xml.contains("<Length>5200</Length>"))
        assertTrue(xml.contains("<Climb>120</Climb>"))
        assertTrue(xml.contains("<PersonStart>"))
        assertTrue(xml.contains("""<Id type="CZE">OK001</Id>"""))
        assertTrue(xml.contains("<Family>Runner</Family>"))
        assertTrue(xml.contains("<Given>Alice</Given>"))
        assertTrue(xml.contains("<Organisation>"))
        assertTrue(xml.contains("<Name>OK &amp; Test</Name>"))
        assertTrue(xml.contains("<BibNumber>OK001</BibNumber>"))
        assertTrue(xml.contains("<StartTime>2026-06-01T10:10:00</StartTime>"))
        assertTrue(xml.contains("<ControlCard>123456</ControlCard>"))
    }

    @Test
    fun groupsStartsByCategoryAndUsesRaceStartWhenDrawnTimeIsMissing() {
        val xml = IofXmlExports.startList(raceData(includeSecondCategory = true))

        assertEquals(2, Regex("<ClassStart>").findAll(xml).count())
        assertTrue(xml.indexOf("<Name>M21</Name>") < xml.indexOf("<Name>W21</Name>"))
        assertTrue(xml.contains("<Family>NoTime</Family>"))
        assertTrue(xml.contains("<StartTime>2026-06-01T10:00:00</StartTime>"))
        assertFalse(xml.contains("<ControlCard></ControlCard>"))
    }

    @Test
    fun exportsIofResultListUsingAndroidStructure() {
        val xml = IofXmlExports.resultList(raceData(includeSecondCategory = true, includeReadout = true), creator = "Radio-Oracle 1.0")

        assertTrue(xml.startsWith("""<?xml version="1.0" encoding="UTF-8"?>"""))
        assertTrue(xml.contains("""<ResultList xmlns="http://www.orienteering.org/datastandard/3.0" iofVersion="3.0" creator="Radio-Oracle 1.0" status="Complete">"""))
        assertTrue(xml.contains("<ClassResult>"))
        assertTrue(xml.contains("""<Class sex="M">"""))
        assertTrue(xml.contains("<Family>Runner</Family>"))
        assertTrue(xml.contains("<StartTime>2026-06-01T10:00:00</StartTime>"))
        assertTrue(xml.contains("<FinishTime>2026-06-01T10:45:00</FinishTime>"))
        assertTrue(xml.contains("<Time>2700</Time>"))
        assertTrue(xml.contains("<Position>1</Position>"))
        assertTrue(xml.contains("<Status>OK</Status>"))
        assertTrue(xml.contains("<ControlCode>31</ControlCode>"))
        assertTrue(xml.contains("<Time>600</Time>"))
        assertTrue(xml.contains("<ControlCode>32</ControlCode>"))
        assertTrue(xml.contains("<Time>1500</Time>"))
        assertTrue(xml.contains("<Family>NoTime</Family>"))
        assertTrue(xml.contains("<Status>Active</Status>"))
    }

    @Test
    fun resultListOnlyWritesPositionForOkResults() {
        val xml = IofXmlExports.resultList(raceData(includeReadout = true, resultStatus = ResultStatus.MISPUNCHED))

        assertTrue(xml.contains("<Status>MissingPunch</Status>"))
        assertFalse(xml.contains("<Position>"))
    }

    private fun raceData(
        includeSecondCategory: Boolean = false,
        includeReadout: Boolean = false,
        resultStatus: ResultStatus = ResultStatus.OK
    ): EventRaceData {
        val race = EventRace(
            id = "race",
            name = "IOF & Start Race",
            apiKey = "",
            startDateTimeIso = "2026-06-01T10:00:00",
            raceType = RaceType.CLASSIC,
            raceLevel = RaceLevel.PRACTICE,
            raceBand = RaceBand.M80,
            timeLimitSeconds = 7_200
        )
        val m21 = category(race.id, "m21", "M21", order = 1)
        val w21 = category(race.id, "w21", "W21", order = 2)
        val competitors = buildList {
            add(
                competitor(
                    raceId = race.id,
                    category = m21,
                    id = "alice",
                    firstName = "Alice",
                    lastName = "Runner",
                    club = "OK & Test",
                    index = "OK001",
                    siNumber = 123456,
                    startNumber = 7,
                    drawnStartTimeSeconds = 600
                )
            )
            if (includeSecondCategory) {
                add(
                    competitor(
                        raceId = race.id,
                        category = w21,
                        id = "notime",
                        firstName = "Bob",
                        lastName = "NoTime",
                        club = "",
                        index = "",
                        siNumber = null,
                        startNumber = 8,
                        drawnStartTimeSeconds = null
                    )
                )
            }
        }

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
                    readoutData = if (includeReadout && competitor.id == "alice") readout(resultStatus) else null
                )
            },
            unmatchedReadoutData = emptyList()
        )
    }

    private fun category(raceId: String, id: String, name: String, order: Int): EventCategory =
        EventCategory(
            id = id,
            raceId = raceId,
            name = name,
            isMan = name.startsWith("M"),
            maxAge = null,
            lengthMeters = 5_200,
            climbMeters = 120,
            order = order,
            differentProperties = false,
            raceType = null,
            raceBand = null,
            timeLimitSeconds = null,
            controlPointsString = ""
        )

    private fun competitor(
        raceId: String,
        category: EventCategory,
        id: String,
        firstName: String,
        lastName: String,
        club: String,
        index: String,
        siNumber: Int?,
        startNumber: Int,
        drawnStartTimeSeconds: Long?
    ): EventCompetitor =
        EventCompetitor(
            id = id,
            raceId = raceId,
            categoryId = category.id,
            firstName = firstName,
            lastName = lastName,
            club = club,
            index = index,
            isMan = category.isMan,
            birthYear = null,
            siNumber = siNumber,
            siRent = false,
            startNumber = startNumber,
            drawnStartTimeSeconds = drawnStartTimeSeconds
        )

    private fun readout(resultStatus: ResultStatus): EventReadoutData =
        EventReadoutData(
            result = EventResult(
                id = "result",
                raceId = "race",
                competitorId = "alice",
                siNumber = 123456,
                cardType = 5,
                checkTimeSeconds = null,
                startTimeSeconds = 36_000,
                finishTimeSeconds = 38_700,
                readoutDateTimeIso = "2026-06-01T10:46:00",
                automaticStatus = true,
                resultStatus = resultStatus,
                points = 2,
                runTimeSeconds = 2_700,
                modified = false,
                sent = false,
                place = 0
            ),
            punches = listOf(
                punch(code = 13, type = SIRecordType.START, splitSeconds = 0),
                punch(code = 31, type = SIRecordType.CONTROL, splitSeconds = 600),
                punch(code = 32, type = SIRecordType.CONTROL, splitSeconds = 900),
                punch(code = 34, type = SIRecordType.FINISH, splitSeconds = 1_200)
            )
        )

    private fun punch(code: Int, type: SIRecordType, splitSeconds: Long): EventAliasPunch =
        EventAliasPunch(
            punch = EventPunch(
                id = "punch-$code",
                raceId = "race",
                resultId = "result",
                cardNumber = 123456,
                siCode = code,
                siTimeSeconds = 36_000 + splitSeconds,
                originalSiTimeSeconds = 36_000 + splitSeconds,
                punchType = type,
                order = code,
                punchStatus = PunchStatus.VALID,
                splitSeconds = splitSeconds
            ),
            alias = null
        )
}
