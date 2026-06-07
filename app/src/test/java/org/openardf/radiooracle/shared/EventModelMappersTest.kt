package org.openardf.radiooracle.shared

import junit.framework.TestCase.assertEquals
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
import org.openardf.radiooracle.backend.room.entity.embeddeds.ResultData
import org.openardf.radiooracle.backend.room.enums.ControlPointType
import org.openardf.radiooracle.backend.room.enums.PunchStatus
import org.openardf.radiooracle.backend.room.enums.RaceBand
import org.openardf.radiooracle.backend.room.enums.RaceLevel
import org.openardf.radiooracle.backend.room.enums.RaceType
import org.openardf.radiooracle.backend.room.enums.ResultStatus
import org.openardf.radiooracle.backend.room.enums.SIRecordType
import org.openardf.radiooracle.backend.shared.toEventRaceData
import org.openardf.radiooracle.backend.shared.toRoomRaceData
import org.openardf.radiooracle.backend.sportident.SITime
import org.openardf.radiooracle.shared.event.EventCategory
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventControl
import org.openardf.radiooracle.shared.event.EventControlPoint
import org.openardf.radiooracle.shared.event.EventRace
import org.openardf.radiooracle.shared.event.EventRaceData
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

class EventModelMappersTest {
    @Test
    fun mapsRoomRaceDataToSharedEventRaceData() {
        val raceId = uuid("00000000-0000-0000-0000-000000000001")
        val categoryId = uuid("00000000-0000-0000-0000-000000000002")
        val competitorId = uuid("00000000-0000-0000-0000-000000000003")
        val resultId = uuid("00000000-0000-0000-0000-000000000004")
        val punchId = uuid("00000000-0000-0000-0000-000000000005")

        val race = Race(
            id = raceId,
            name = "Test Race",
            apiKey = "api-key",
            startDateTime = LocalDateTime.of(2026, 5, 30, 10, 0),
            raceType = RaceType.CLASSIC,
            raceLevel = RaceLevel.PRACTICE,
            raceBand = RaceBand.M80,
            timeLimit = Duration.ofHours(2)
        )
        val category = Category(
            id = categoryId,
            raceId = raceId,
            name = "M21",
            isMan = true,
            maxAge = null,
            length = 5_000,
            climb = 100,
            order = 1,
            differentProperties = true,
            raceType = RaceType.SPRINT,
            categoryBand = RaceBand.M2,
            timeLimit = Duration.ofMinutes(45),
            controlPointsString = "31 32"
        )
        val controlPoint = ControlPoint(
            id = uuid("00000000-0000-0000-0000-000000000006"),
            categoryId = categoryId,
            siCode = 31,
            type = ControlPointType.CONTROL,
            order = 1
        )
        val competitor = Competitor(
            id = competitorId,
            raceId = raceId,
            categoryId = categoryId,
            firstName = "Pavel",
            lastName = "Kolsky",
            club = "OK",
            index = "OK001",
            isMan = true,
            birthYear = 1980,
            siNumber = 123456,
            siRent = false,
            startNumber = 42,
            drawnRelativeStartTime = Duration.ofMinutes(10)
        )
        val alias = Alias(
            id = uuid("00000000-0000-0000-0000-000000000007"),
            raceId = raceId,
            siCode = 31,
            name = "F1"
        )
        val result = Result(
            id = resultId,
            raceId = raceId,
            competitorId = competitorId,
            siNumber = 123456,
            cardType = 5,
            checkTime = SITime(LocalTime.of(9, 30)),
            startTime = SITime(LocalTime.of(10, 0)),
            finishTime = SITime(LocalTime.of(10, 45)),
            readoutTime = LocalDateTime.of(2026, 5, 30, 10, 46),
            automaticStatus = true,
            resultStatus = ResultStatus.OK,
            points = 2,
            runTime = Duration.ofMinutes(45),
            modified = false,
            sent = true
        )
        result.place = 1
        val punch = Punch(
            id = punchId,
            raceId = raceId,
            resultId = resultId,
            cardNumber = 123456,
            siCode = 31,
            siTime = SITime(LocalTime.of(10, 15)),
            origSiTime = SITime(LocalTime.of(10, 15)),
            punchType = SIRecordType.CONTROL,
            order = 1,
            punchStatus = PunchStatus.VALID,
            split = Duration.ofMinutes(15)
        )

        val raceData = RaceData(
            race = race,
            categories = listOf(CategoryData(category, listOf(controlPoint), listOf(competitor))),
            aliases = listOf(alias),
            competitorData = listOf(
                CompetitorData(
                    CompetitorCategory(competitor, category),
                    ReadoutData(result, listOf(AliasPunch(punch, alias)))
                )
            ),
            unmatchedReadoutData = emptyList()
        )

        val shared = raceData.toEventRaceData()

        assertEquals(raceId.toString(), shared.race.id)
        assertEquals("2026-05-30T10:00", shared.race.startDateTimeIso)
        assertEquals(7_200L, shared.race.timeLimitSeconds)
        assertEquals(2_700L, shared.categories.single().category.timeLimitSeconds)
        assertEquals(600L, shared.competitorData.single().competitorCategory.competitor.drawnStartTimeSeconds)
        assertEquals(900L, shared.competitorData.single().readoutData!!.punches.single().punch.splitSeconds)
        assertEquals("F1", shared.competitorData.single().readoutData!!.punches.single().alias!!.name)
        assertEquals(1, shared.competitorData.single().readoutData!!.result.place)
        assertEquals("F1", shared.controls.single().label)
        assertEquals(shared.controls.single().id, shared.categories.single().controlPoints.single().controlId)
        assertEquals(listOf(shared.controls.single().id), shared.categories.single().publicControlIds)

        val room = shared.toRoomRaceData()
        assertEquals(raceId, room.race.id)
        assertEquals(Duration.ofHours(2), room.race.timeLimit)
        assertEquals(Duration.ofMinutes(45), room.categories.single().category.timeLimit)
        assertEquals("M21;1;99;5000;100;0;SPRINT;45;2m", room.categories.single().category.toCSVString())
        assertEquals("31", room.categories.single().controlPoints.single().toCsvString())
        assertEquals("123456;31;10:15:00,0,0", room.competitorData.single().readoutData!!.punches.single().punch.toCsvString())
        assertEquals(
            "123456;09:30:00;10:00:00;10:45:00;1;31;10:15:00",
            ResultData(
                result = room.competitorData.single().readoutData!!.result,
                punches = room.competitorData.single().readoutData!!.punches,
                competitorCategory = room.competitorData.single().competitorCategory
            ).toReadoutCSVString()
        )
        assertEquals(Duration.ofMinutes(10), room.competitorData.single().competitorCategory.competitor.drawnRelativeStartTime)
        assertEquals(Duration.ofMinutes(15), room.competitorData.single().readoutData!!.punches.single().punch.split)
        assertEquals(1, room.competitorData.single().readoutData!!.result.place)
        assertEquals(listOf("F1"), room.aliases.map { it.name })
    }

    @Test
    fun mapsPortableControlModelToAndroidRoomData() {
        val projectRaceData = EventRaceData(
            race = EventRace(
                id = "desktop-smoke-race",
                name = "Desktop Smoke Race",
                apiKey = "",
                startDateTimeIso = "2026-05-31T10:00",
                raceType = RaceType.CLASSIC,
                raceLevel = RaceLevel.PRACTICE,
                raceBand = RaceBand.M80,
                timeLimitSeconds = 7_200
            ),
            categories = listOf(
                EventCategoryData(
                    category = EventCategory(
                        id = "category-m21",
                        raceId = "desktop-smoke-race",
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
                    ),
                    controlPoints = listOf(
                        EventControlPoint(
                            id = "category-m21-control-1",
                            categoryId = "category-m21",
                            siCode = 0,
                            type = ControlPointType.CONTROL,
                            order = 1,
                            controlId = "control-fox1-31-control"
                        )
                    ),
                    competitors = emptyList(),
                    publicControlIds = listOf("control-fox1-31-control")
                )
            ),
            aliases = emptyList(),
            competitorData = emptyList(),
            unmatchedReadoutData = emptyList(),
            controls = listOf(
                EventControl(
                    id = "control-fox1-31-control",
                    raceId = "desktop-smoke-race",
                    label = "FOX 1",
                    siCode = 31,
                    type = ControlPointType.CONTROL
                )
            )
        )

        val room = projectRaceData.toRoomRaceData()

        assertEquals("Desktop Smoke Race", room.race.name)
        assertEquals("M21", room.categories.single().category.name)
        assertEquals("31", room.categories.single().category.controlPointsString)
        assertEquals(31, room.categories.single().controlPoints.single().siCode)
        assertEquals(ControlPointType.CONTROL, room.categories.single().controlPoints.single().type)
        assertEquals(listOf("FOX 1"), room.aliases.map { it.name })
        assertEquals(listOf(31), room.aliases.map { it.siCode })
    }

    @Test
    fun roomCompetitorNameHelpersUseSharedFormatting() {
        val competitor = Competitor(
            id = uuid("00000000-0000-0000-0000-000000000011"),
            raceId = uuid("00000000-0000-0000-0000-000000000001"),
            categoryId = null,
            firstName = "Pavel",
            lastName = "Kolsky",
            club = "OK",
            index = "OK001",
            isMan = true,
            birthYear = null,
            siNumber = null,
            siRent = false,
            startNumber = 42,
            drawnRelativeStartTime = null
        )

        assertEquals("KOLSKY Pavel", competitor.getFullName())
        assertEquals("KOLSKY Pavel (42)", competitor.getNameWithStartNumber())
        assertEquals(";42;Pavel;Kolsky;M21;0;;OK;OK001;;0;;OK001;", competitor.toSimpleCsvString("M21"))
        assertEquals(
            "42;Kolsky;Pavel;M21;;;OK001;;OK;",
            competitor.toStartCsvString("M21", LocalDateTime.of(2026, 5, 30, 10, 0))
        )
    }

    private fun uuid(value: String): UUID = UUID.fromString(value)
}
