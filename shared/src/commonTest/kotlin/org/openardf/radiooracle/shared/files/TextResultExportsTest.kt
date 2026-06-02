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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextResultExportsTest {
    @Test
    fun exportsTextResultsWithSplitSection() {
        val text = TextResultExports.results(raceData(), appVersion = "1.0")

        assertTrue(text.contains("Results"))
        assertTrue(text.contains("Race: Text Result Race"))
        assertTrue(text.contains("Category M21\tLimit: 120\tLength: 5.0 km\tControls: 31 32"))
        assertTrue(text.contains("1.\tRUNNER Alice\tIDX\t00:45:00\t2\t31 32"))
        assertTrue(text.contains("Splits"))
        assertTrue(text.contains("1.\tRUNNER Alice\tIDX\t00:45:00\t2\t31 32\t00:10:00 00:25:00"))
        assertTrue(text.contains("Generated with Radio-Oracle 1.0"))
    }

    @Test
    fun usesShortStatusInsteadOfPlaceForNonOkResults() {
        val text = TextResultExports.results(raceData(resultStatus = ResultStatus.DID_NOT_FINISH))

        assertTrue(text.contains("DNF\tRUNNER Alice"))
        assertFalse(text.contains("1.\tRUNNER Alice"))
    }

    private fun raceData(resultStatus: ResultStatus = ResultStatus.OK): EventRaceData {
        val race = EventRace(
            id = "race",
            name = "Text Result Race",
            apiKey = "",
            startDateTimeIso = "2026-06-01T10:00:00",
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
            firstName = "Alice",
            lastName = "Runner",
            club = "",
            index = "IDX",
            isMan = true,
            birthYear = null,
            siNumber = 123456,
            siRent = false,
            startNumber = 1,
            drawnStartTimeSeconds = null
        )
        return EventRaceData(
            race = race,
            categories = listOf(EventCategoryData(category, controlPoints = emptyList(), competitors = listOf(competitor))),
            aliases = emptyList(),
            competitorData = listOf(
                EventCompetitorData(
                    competitorCategory = EventCompetitorCategory(competitor, category),
                    readoutData = readout(resultStatus)
                )
            ),
            unmatchedReadoutData = emptyList()
        )
    }

    private fun readout(resultStatus: ResultStatus): EventReadoutData =
        EventReadoutData(
            result = EventResult(
                id = "result",
                raceId = "race",
                competitorId = "competitor",
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
                punch(code = 31, splitSeconds = 600),
                punch(code = 32, splitSeconds = 1_500)
            )
        )

    private fun punch(code: Int, splitSeconds: Long): EventAliasPunch =
        EventAliasPunch(
            punch = EventPunch(
                id = "punch-$code",
                raceId = "race",
                resultId = "result",
                cardNumber = 123456,
                siCode = code,
                siTimeSeconds = 36_000 + splitSeconds,
                originalSiTimeSeconds = 36_000 + splitSeconds,
                punchType = SIRecordType.CONTROL,
                order = code,
                punchStatus = PunchStatus.VALID,
                splitSeconds = splitSeconds
            ),
            alias = null
        )
}
