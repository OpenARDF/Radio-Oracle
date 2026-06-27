package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.PunchStatus
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.SIRecordType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventProjectFileTest {
    @Test
    fun defaultsToCurrentSchemaAndAppName() {
        val projectFile = EventProjectFile(raceData = raceData())

        assertEquals(3, projectFile.schemaVersion)
        assertEquals("Radio-Oracle", projectFile.appName)
        assertTrue(projectFile.isSupportedSchema())
    }

    @Test
    fun rejectsUnsupportedSchemaVersions() {
        assertFalse(EventProjectFileFormat.isSupportedSchema(0))
        assertFalse(EventProjectFileFormat.isSupportedSchema(EventProjectFileFormat.CURRENT_SCHEMA_VERSION + 1))
    }

    @Test
    fun serializesAndDeserializesPortableProjectFiles() {
        val original = EventProjectFile(raceData = raceData())

        val encoded = EventProjectFileJson.encode(original)
        val decoded = EventProjectFileJson.decode(encoded)

        assertTrue(encoded.contains("\"schemaVersion\": 3"))
        assertTrue(encoded.contains("\"appName\": \"Radio-Oracle\""))
        assertTrue(encoded.contains("\"courseAnalyzerSpeedCompensationFactor\": 1.0"))
        assertEquals(original.schemaVersion, decoded.schemaVersion)
        assertEquals(original.appName, decoded.appName)
        assertEquals(original.seriesLink, decoded.seriesLink)
        assertEquals(original.raceData.race, decoded.raceData.race)
    }

    @Test
    fun omitsSeriesLinkForOrdinaryEventFiles() {
        val encoded = EventProjectFileJson.encode(EventProjectFile(raceData = raceData()))

        assertFalse(encoded.contains("seriesLink"))
    }

    @Test
    fun serializesAndDeserializesOptionalSeriesLink() {
        val original = EventProjectFile(
            raceData = raceData(),
            seriesLink = EventSeriesLink(seriesId = "series-1", seriesEventId = "day-1")
        )

        val encoded = EventProjectFileJson.encode(original)
        val decoded = EventProjectFileJson.decode(encoded)

        assertTrue(encoded.contains("\"seriesLink\""))
        assertEquals(original.seriesLink, decoded.seriesLink)
    }

    @Test
    fun seriesLinkDoesNotStoreManifestOwnedSeriesName() {
        val encoded = EventProjectFileJson.encode(
            EventProjectFile(
                raceData = raceData(),
                seriesLink = EventSeriesLink(seriesId = "series-1", seriesEventId = "day-1")
            )
        )

        assertFalse(encoded.contains("seriesName"))
        assertFalse(encoded.contains("Championship Series"))
    }

    @Test
    fun defaultsMissingCourseAnalyzerSpeedFactorForOlderEventFiles() {
        val encoded = EventProjectFileJson.encode(EventProjectFile(raceData = raceData()))
            .replace(Regex(""",\n\s+"courseAnalyzerSpeedCompensationFactor": 1\.0"""), "")

        val decoded = EventProjectFileJson.decode(encoded)

        assertEquals(1.0, decoded.raceData.race.courseAnalyzerSpeedCompensationFactor)
    }

    @Test
    fun refusesProjectFilesWithUnsupportedSchemaVersions() {
        val encoded = EventProjectFileJson.encode(EventProjectFile(raceData = raceData()))
            .replace("\"schemaVersion\": 3", "\"schemaVersion\": 4")

        assertFailsWith<IllegalArgumentException> {
            EventProjectFileJson.decode(encoded)
        }
    }

    @Test
    fun acceptsUnknownFieldsWithinSupportedSchemaVersions() {
        val encoded = EventProjectFileJson.encode(EventProjectFile(raceData = raceData()))
            .replace("\"appName\": \"Radio-Oracle\"", "\"appName\": \"Radio-Oracle\", \"futureField\": true")

        assertEquals(EventProjectFileFormat.APP_NAME, EventProjectFileJson.decode(encoded).appName)
    }

    @Test
    fun clearsPublicControlCoordinatesWhenEncodingAndDecoding() {
        val projectFile = EventProjectFile(
            raceData = raceData().copy(
                controls = listOf(
                    EventControl(
                        id = "control-31",
                        raceId = "race",
                        label = "FOX 1",
                        siCode = 31,
                        type = ControlPointType.CONTROL,
                        latitude = 39.123456,
                        longitude = -95.654321
                    )
                )
            )
        )

        val encoded = EventProjectFileJson.encode(projectFile)
        val decoded = EventProjectFileJson.decode(
            encoded
                .replace("\"latitude\": null", "\"latitude\": 39.123456")
                .replace("\"longitude\": null", "\"longitude\": -95.654321")
        )

        assertFalse(encoded.contains("39.123456"))
        assertFalse(encoded.contains("-95.654321"))
        assertEquals(null, decoded.raceData.controls.single().latitude)
        assertEquals(null, decoded.raceData.controls.single().longitude)
    }

    @Test
    fun backfillsControlsWhenOlderProjectFileOmitsControlCatalog() {
        val encoded = EventProjectFileJson.encode(EventProjectFile(raceData = raceData()))
            .replace(
                Regex(""",\n\s+"controls": \[\]"""),
                ""
            )

        val decoded = EventProjectFileJson.decode(encoded)

        assertEquals(listOf("FOX 1"), decoded.raceData.controls.map { it.label })
        assertEquals(listOf(31), decoded.raceData.controls.map { it.siCode })
        assertEquals(listOf(ControlPointType.CONTROL), decoded.raceData.controls.map { it.type })
    }

    @Test
    fun migratesLegacyMandatoryControlsToUnscoredControls() {
        val projectFile = EventProjectFile(
            raceData = raceData().copy(
                controls = listOf(
                    EventControl(
                        id = "control-31",
                        raceId = "race",
                        label = "FOX 1",
                        siCode = 31,
                        type = ControlPointType.CONTROL,
                        scored = true,
                        mandatory = true
                    )
                )
            )
        )
        val legacyEncoded = EventProjectFileJson.encode(projectFile)
            .replace("\"schemaVersion\": 3", "\"schemaVersion\": 2")
            .replace(Regex("""\s+"scored": true,\n"""), "")

        val decoded = EventProjectFileJson.decode(legacyEncoded)

        assertFalse(decoded.raceData.controls.single().scored)
        assertFalse(decoded.raceData.controls.single().mandatory)
    }

    @Test
    fun clearsLegacyCategoryRaceSettingsOnEncode() {
        val baseRaceData = raceData()
        val legacyCategoryData = baseRaceData.categories.single().let { categoryData ->
            categoryData.copy(
                category = categoryData.category.copy(
                    differentProperties = true,
                    raceType = RaceType.SPRINT,
                    raceBand = RaceBand.M2,
                    timeLimitSeconds = 3_600
                )
            )
        }
        val projectFile = EventProjectFile(
            raceData = baseRaceData.copy(categories = listOf(legacyCategoryData))
        )

        val decoded = EventProjectFileJson.decode(EventProjectFileJson.encode(projectFile))
            .raceData.categories.single().category

        assertFalse(decoded.differentProperties)
        assertEquals(null, decoded.raceType)
        assertEquals(null, decoded.raceBand)
        assertEquals(null, decoded.timeLimitSeconds)
    }

    private fun raceData(): EventRaceData =
        EventRaceData(
            race = EventRace(
                id = "race",
                name = "Race",
                apiKey = "",
                startDateTimeIso = "2026-05-30T10:00",
                raceType = RaceType.CLASSIC,
                raceLevel = RaceLevel.PRACTICE,
                raceBand = RaceBand.M80,
                timeLimitSeconds = 7_200
            ),
            categories = listOf(
                EventCategoryData(
                    category = EventCategory(
                        id = "category",
                        raceId = "race",
                        name = "M21",
                        isMan = true,
                        maxAge = null,
                        lengthMeters = 5_000,
                        climbMeters = 120,
                        order = 1,
                        differentProperties = false,
                        raceType = null,
                        raceBand = null,
                        timeLimitSeconds = null,
                        controlPointsString = "31 32 33"
                    ),
                    controlPoints = listOf(
                        EventControlPoint(
                            id = "control",
                            categoryId = "category",
                            siCode = 31,
                            type = ControlPointType.CONTROL,
                            order = 1
                        )
                    ),
                    competitors = listOf(competitor())
                )
            ),
            aliases = listOf(
                EventAlias(
                    id = "alias",
                    raceId = "race",
                    siCode = 31,
                    name = "FOX 1"
                )
            ),
            competitorData = listOf(
                EventCompetitorData(
                    competitorCategory = EventCompetitorCategory(
                        competitor = competitor(),
                        category = null
                    ),
                    readoutData = readoutData()
                )
            ),
            unmatchedReadoutData = listOf(readoutData())
        )

    private fun competitor(): EventCompetitor =
        EventCompetitor(
            id = "competitor",
            raceId = "race",
            categoryId = "category",
            firstName = "Pavel",
            lastName = "Kolsky",
            club = "OPEN",
            index = "OK001",
            isMan = true,
            birthYear = 1980,
            siNumber = 123456,
            siRent = false,
            startNumber = 101,
            drawnStartTimeSeconds = 600
        )

    private fun readoutData(): EventReadoutData {
        val result = EventResult(
            id = "result",
            raceId = "race",
            competitorId = "competitor",
            siNumber = 123456,
            cardType = 10,
            checkTimeSeconds = 300,
            startTimeSeconds = 600,
            finishTimeSeconds = 1_800,
            readoutDateTimeIso = "2026-05-30T12:00",
            automaticStatus = true,
            resultStatus = ResultStatus.OK,
            points = 3,
            runTimeSeconds = 1_200,
            modified = false,
            sent = false,
            place = 1
        )
        val punch = EventPunch(
            id = "punch",
            raceId = "race",
            resultId = "result",
            cardNumber = 123456,
            siCode = 31,
            siTimeSeconds = 900,
            originalSiTimeSeconds = 900,
            punchType = SIRecordType.CONTROL,
            order = 1,
            punchStatus = PunchStatus.VALID,
            splitSeconds = 300
        )
        return EventReadoutData(
            result = result,
            punches = listOf(EventAliasPunch(punch, null))
        )
    }
}
