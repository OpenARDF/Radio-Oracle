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

        assertEquals(2, projectFile.schemaVersion)
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

        assertTrue(encoded.contains("\"schemaVersion\": 2"))
        assertTrue(encoded.contains("\"appName\": \"Radio-Oracle\""))
        assertEquals(original, decoded)
    }

    @Test
    fun refusesProjectFilesWithUnsupportedSchemaVersions() {
        val encoded = EventProjectFileJson.encode(EventProjectFile(raceData = raceData()))
            .replace("\"schemaVersion\": 2", "\"schemaVersion\": 3")

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
