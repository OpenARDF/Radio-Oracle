package org.openardf.radiooracle.files.json

import com.squareup.moshi.Moshi
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.files.json.adapters.LocalDateTimeAdapter
import org.openardf.radiooracle.backend.files.json.adapters.RaceDataJsonAdapter
import org.openardf.radiooracle.backend.files.processors.JsonProcessor
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
import org.openardf.radiooracle.backend.room.withFreshImportIds
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.PunchStatus
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.shared.event.EventCategory
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventControl
import org.openardf.radiooracle.shared.event.EventControlPoint
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventProjectFileJson
import org.openardf.radiooracle.shared.event.EventRace
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.backend.sportident.SIConstants
import org.openardf.radiooracle.backend.sportident.SITime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.InputStream
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

class RaceJsonTests {
    val dataProcessor: DataProcessor = mock()

//    @Test
//    fun testToJson() {
//        val race = Race()
//        val cat1 = Category("A")
//        val cat2 = Category("B")
//
//        val adapter = RaceDataJsonAdapter()
//        val raceData = RaceData(race, listOf(cat1, cat2),)
//        val raceJson = adapter.fromJson(raceData)
//
//    }

    @Before
    fun setup() {
        `when`(dataProcessor.resultStatusShortStringToEnum("MP")).thenReturn(ResultStatus.MISPUNCHED)
        `when`(
            dataProcessor
                .shortStringToPunchStatus(org.mockito.kotlin.any())
        )
            .thenReturn(PunchStatus.VALID)
        `when`(dataProcessor.resultStatusToShortString(org.mockito.kotlin.any()))
            .thenReturn("OK")
        `when`(dataProcessor.punchStatusToShortString(org.mockito.kotlin.any()))
            .thenReturn("OK")
    }

    @Test
    fun fullRaceExportMatchesGoldenProjectShape() {
        val moshi = Moshi.Builder()
            .add(RaceDataJsonAdapter(dataProcessor))
            .add(LocalDateTimeAdapter())
            .add(KotlinJsonAdapterFactory())
            .build()
        val out = moshi.adapter(RaceData::class.java).toJson(goldenRaceData())

        val stream = resourceStream("json/json_full_race_export_golden.ardfjs")
        val valid = stream.bufferedReader().use { it.readText() }

        assertEquals(canonicalJson(valid), canonicalJson(out))
    }

    @Test
    fun testValidFromJson() {

        val stream = resourceStream("json/json_valid_race_import.ardfjs")
        val raceData = JsonProcessor.importRaceData(stream, dataProcessor)

        assertEquals("EXAMPLE", raceData.race.name)
        assertEquals(LocalDateTime.of(2025, 11, 28, 13, 0, 0), raceData.race.startDateTime)
        assertEquals(RaceType.CLASSIC, raceData.race.raceType)
        assertEquals(RaceBand.M80, raceData.race.raceBand)
        assertEquals(RaceLevel.DISTRICT, raceData.race.raceLevel)

        val categories = raceData.categories.map { it.category.name }.sorted()
        assertEquals(listOf("D19", "D20", "M19", "M20", "Ostatní", "RT"), categories)

        val competitors = raceData.competitorData.map { it.competitorCategory.competitor }
        val startNumbers = competitors.map { it.startNumber }.sorted()
        assertEquals(listOf(40, 41, 42, 43, 44, 45, 46), startNumbers)

        val comp1 =
            raceData.competitorData.find { it.competitorCategory.competitor.siNumber == 10000 }
        assertEquals("KOLSKÝ Pavel", comp1?.competitorCategory?.competitor?.getFullName())
        assertEquals(ResultStatus.MISPUNCHED, comp1?.readoutData?.result?.resultStatus)

    }

    // Should throw exception, since the required start time is missing
    @Test
    fun testInvalidFromJson() {
        val stream = resourceStream("json/json_invalid_race_import.ardfjs")
        assertThrows(JsonDataException::class.java) {
            JsonProcessor.importRaceData(
                stream,
                dataProcessor
            )
        }
    }

    @Test
    fun importsRadioOracleEventFileJson() {
        val eventFileJson = EventProjectFileJson.encode(
            EventProjectFile(
                raceData = EventRaceData(
                    race = EventRace(
                        id = "desktop-race",
                        name = "Desktop Event",
                        apiKey = "",
                        startDateTimeIso = "2026-06-05T09:00",
                        raceType = RaceType.CLASSIC,
                        raceLevel = RaceLevel.PRACTICE,
                        raceBand = RaceBand.M80,
                        timeLimitSeconds = 7_200
                    ),
                    categories = listOf(
                        EventCategoryData(
                            category = EventCategory(
                                id = "category-m21",
                                raceId = "desktop-race",
                                name = "M21",
                                isMan = true,
                                maxAge = null,
                                lengthMeters = 5_000,
                                climbMeters = 100,
                                order = 0,
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
                            raceId = "desktop-race",
                            label = "161",
                            siCode = 31,
                            type = ControlPointType.CONTROL,
                            publicLabel = "FOX 1"
                        )
                    )
                )
            )
        )

        val raceData = JsonProcessor.importRaceData(eventFileJson, dataProcessor)

        assertEquals("Desktop Event", raceData.race.name)
        assertEquals("M21", raceData.categories.single().category.name)
        assertEquals("31", raceData.categories.single().category.controlPointsString)
        assertEquals(31, raceData.categories.single().controlPoints.single().siCode)
        assertEquals(listOf("FOX 1"), raceData.aliases.map { it.name })
        assertEquals("event-file:desktop-race", raceData.race.importSourceId)
        assertEquals(64, raceData.race.importFingerprint?.length)
    }

    @Test
    fun freshImportIdsCloneRaceDataAndPreserveRelationships() {
        val original = goldenRaceData()
        original.race.importSourceId = "event-file:desktop-race"
        original.race.importFingerprint = "abc123"
        val imported = original.withFreshImportIds()

        val importedRaceId = imported.race.id
        val importedCategory = imported.categories.single().category
        val importedControlPoint = imported.categories.single().controlPoints.single()
        val importedAlias = imported.aliases.single()
        val importedCompetitor = imported.competitorData.single().competitorCategory.competitor
        val importedResult = imported.competitorData.single().readoutData!!.result
        val importedPunches = imported.competitorData.single().readoutData!!.punches.map { it.punch }

        assertEquals("Desktop Golden Race", imported.race.name)
        assertNotEquals(original.race.id, importedRaceId)
        assertEquals(original.race.importSourceId, imported.race.importSourceId)
        assertEquals(original.race.importFingerprint, imported.race.importFingerprint)
        assertEquals(importedRaceId, importedCategory.raceId)
        assertEquals(importedCategory.id, importedControlPoint.categoryId)
        assertEquals(importedRaceId, importedAlias.raceId)
        assertEquals(importedRaceId, importedCompetitor.raceId)
        assertEquals(importedCategory.id, importedCompetitor.categoryId)
        assertEquals(importedRaceId, importedResult.raceId)
        assertEquals(importedCompetitor.id, importedResult.competitorId)
        importedPunches.forEach { punch ->
            assertEquals(importedRaceId, punch.raceId)
            assertEquals(importedResult.id, punch.resultId)
        }
    }

    private fun goldenRaceData(): RaceData {
        val raceId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val categoryId = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val competitorId = UUID.fromString("00000000-0000-0000-0000-000000000003")
        val resultId = UUID.fromString("00000000-0000-0000-0000-000000000004")

        val race = Race(
            id = raceId,
            name = "Desktop Golden Race",
            apiKey = "debug-key",
            startDateTime = LocalDateTime.of(2026, 5, 31, 10, 0),
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
            maxAge = 99,
            length = 3_500,
            climb = 120,
            order = 0,
            differentProperties = false,
            raceType = null,
            categoryBand = null,
            timeLimit = null,
            controlPointsString = "31"
        )
        val controlPoint = ControlPoint(
            id = UUID.fromString("00000000-0000-0000-0000-000000000005"),
            categoryId = categoryId,
            siCode = 31,
            type = ControlPointType.CONTROL,
            order = 0
        )
        val alias = Alias(
            id = UUID.fromString("00000000-0000-0000-0000-000000000006"),
            raceId = raceId,
            siCode = 31,
            name = "Fox 1"
        )
        val competitor = Competitor(
            id = competitorId,
            raceId = raceId,
            categoryId = categoryId,
            firstName = "Test",
            lastName = "Runner",
            club = "OpenARDF",
            index = "TST001",
            isMan = true,
            birthYear = 1980,
            siNumber = 123456,
            siRent = false,
            startNumber = 7,
            drawnRelativeStartTime = Duration.ofMinutes(10)
        )
        val result = Result(
            id = resultId,
            raceId = raceId,
            competitorId = competitorId,
            siNumber = 123456,
            cardType = SIConstants.SI_CARD6,
            checkTime = SITime(LocalTime.of(9, 55)),
            startTime = SITime(LocalTime.of(10, 10)),
            finishTime = SITime(LocalTime.of(10, 40)),
            readoutTime = LocalDateTime.of(2026, 5, 31, 10, 42),
            automaticStatus = true,
            resultStatus = ResultStatus.OK,
            points = 1,
            runTime = Duration.ofMinutes(30),
            modified = false,
            sent = false
        ).also { it.place = 2 }

        val startPunch = Punch(
            id = UUID.fromString("00000000-0000-0000-0000-000000000007"),
            raceId = raceId,
            resultId = resultId,
            cardNumber = 123456,
            siCode = 0,
            siTime = SITime(LocalTime.of(10, 10)),
            origSiTime = SITime(LocalTime.of(10, 10)),
            punchType = SIRecordType.START,
            order = 0,
            punchStatus = PunchStatus.VALID,
            split = Duration.ZERO
        )
        val controlPunch = Punch(
            id = UUID.fromString("00000000-0000-0000-0000-000000000008"),
            raceId = raceId,
            resultId = resultId,
            cardNumber = 123456,
            siCode = 31,
            siTime = SITime(LocalTime.of(10, 20)),
            origSiTime = SITime(LocalTime.of(10, 20)),
            punchType = SIRecordType.CONTROL,
            order = 1,
            punchStatus = PunchStatus.VALID,
            split = Duration.ofMinutes(10)
        )
        val finishPunch = Punch(
            id = UUID.fromString("00000000-0000-0000-0000-000000000009"),
            raceId = raceId,
            resultId = resultId,
            cardNumber = 123456,
            siCode = 0,
            siTime = SITime(LocalTime.of(10, 40)),
            origSiTime = SITime(LocalTime.of(10, 40)),
            punchType = SIRecordType.FINISH,
            order = 2,
            punchStatus = PunchStatus.VALID,
            split = Duration.ofMinutes(20)
        )

        return RaceData(
            race = race,
            categories = listOf(CategoryData(category, listOf(controlPoint), listOf(competitor))),
            aliases = listOf(alias),
            competitorData = listOf(
                CompetitorData(
                    competitorCategory = CompetitorCategory(competitor, category),
                    readoutData = ReadoutData(
                        result,
                        listOf(
                            AliasPunch(startPunch, null),
                            AliasPunch(controlPunch, alias),
                            AliasPunch(finishPunch, null)
                        )
                    )
                )
            ),
            unmatchedReadoutData = emptyList()
        )
    }

    private fun resourceStream(path: String): InputStream {
        val classLoader = requireNotNull(this::class.java.classLoader)
        return requireNotNull(classLoader.getResourceAsStream(path)) {
            "Missing test resource $path"
        }
    }

    private fun canonicalJson(json: String): String {
        val adapter = Moshi.Builder().build().adapter(Any::class.java)
        return adapter.toJson(adapter.fromJson(json))
    }
}
