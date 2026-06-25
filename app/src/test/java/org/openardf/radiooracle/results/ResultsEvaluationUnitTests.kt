package org.openardf.radiooracle.results

import kotlinx.coroutines.test.runTest
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.results.ResultsProcessor
import org.openardf.radiooracle.backend.room.entity.Alias
import org.openardf.radiooracle.backend.room.entity.Competitor
import org.openardf.radiooracle.backend.room.entity.ControlPoint
import org.openardf.radiooracle.backend.room.entity.Punch
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.room.entity.Result
import org.openardf.radiooracle.backend.room.entity.embeddeds.ControlPointAlias
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.PunchStatus
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.backend.sportident.SIConstants
import org.openardf.radiooracle.backend.sportident.SITime
import org.openardf.radiooracle.shared.sportident.SportIdentRunTimingStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Random
import java.util.UUID

/**
 * Tests the correct evaluation of the punches
 * TODO: Add more random data
 */
class ResultsEvaluationUnitTests {

    @Test
    fun rejectsReadoutTimingWithFinishBeforeStart() {
        val timing = ResultsProcessor.calculateReadoutRunTiming(
            SITime(LocalTime.of(14, 2, 23), 4, 0),
            SITime(LocalTime.of(0, 2, 11), 0, 0)
        )

        assertEquals(false, timing.isValid)
        assertEquals(SportIdentRunTimingStatus.FINISH_BEFORE_START, timing.status)
        assertEquals(0L, timing.runTimeSeconds)
    }

    @Test
    fun flagsNonSequentialControlsWithoutBlockingRunTime() {
        val timing = ResultsProcessor.calculateReadoutRunTiming(
            SITime(LocalTime.of(10, 0, 0), 4, 0),
            SITime(LocalTime.of(10, 30, 0), 4, 0),
            listOf(
                SITime(LocalTime.of(10, 12, 0), 4, 0),
                SITime(LocalTime.of(10, 11, 59), 4, 0)
            )
        )

        assertEquals(false, timing.isValid)
        assertEquals(false, timing.blocksResult)
        assertEquals(SportIdentRunTimingStatus.CONTROL_NOT_AFTER_PREVIOUS_CONTROL, timing.status)
        assertEquals(30L * 60L, timing.runTimeSeconds)
    }

    @Test
    fun recalculationPreservesEditedStartTimeOverStartListFallback() = runTest {
        val raceId = UUID.randomUUID()
        val resultId = UUID.randomUUID()
        val competitorId = UUID.randomUUID()
        val dataProcessor = mock<DataProcessor>()
        val race = Race(
            id = raceId,
            name = "Editable Readout",
            apiKey = "",
            startDateTime = LocalDateTime.of(2026, 6, 25, 0, 0),
            raceType = RaceType.CLASSIC,
            raceLevel = RaceLevel.PRACTICE,
            raceBand = RaceBand.M80,
            timeLimit = Duration.ofHours(2)
        )
        val competitor = Competitor(
            id = competitorId,
            raceId = raceId,
            categoryId = null,
            firstName = "Alice",
            lastName = "Runner",
            club = "",
            index = "",
            isMan = false,
            birthYear = null,
            siNumber = 123456,
            siRent = false,
            startNumber = 1,
            drawnRelativeStartTime = Duration.ofHours(12)
        )
        val result = Result(
            id = resultId,
            raceId = raceId,
            competitorId = competitorId,
            siNumber = 123456,
            cardType = SIConstants.SI_CARD8_9_SIAC,
            checkTime = null,
            startTime = SITime(LocalTime.of(10, 0)),
            finishTime = SITime(LocalTime.of(10, 30)),
            automaticStatus = false,
            resultStatus = ResultStatus.ERROR,
            points = 0,
            runTime = Duration.ZERO,
            modified = true,
            sent = false
        )
        val punches = arrayListOf(
            Punch(31, SITime(LocalTime.of(10, 10)), SIRecordType.CONTROL, 1)
        )
        whenever(dataProcessor.getCompetitor(competitorId)).thenReturn(competitor)

        ResultsProcessor.calculateResult(
            result = result,
            category = null,
            punches = punches,
            manualStatus = ResultStatus.OK,
            race = race,
            dataProcessor = dataProcessor
        )

        assertEquals(10 * 60L * 60L, result.startTime?.getSeconds())
        assertEquals(ResultStatus.OK, result.resultStatus)
        assertEquals(false, result.automaticStatus)
        assertEquals(Duration.ofMinutes(30), result.runTime)
    }

    @Test
    fun recalculationClearsStaleTimingInvalidPunchStatus() = runTest {
        val raceId = UUID.randomUUID()
        val dataProcessor = mock<DataProcessor>()
        val race = Race(
            id = raceId,
            name = "Editable Readout",
            apiKey = "",
            startDateTime = LocalDateTime.of(2026, 6, 25, 0, 0),
            raceType = RaceType.CLASSIC,
            raceLevel = RaceLevel.PRACTICE,
            raceBand = RaceBand.M80,
            timeLimit = Duration.ofHours(2)
        )
        val result = Result(
            id = UUID.randomUUID(),
            raceId = raceId,
            competitorId = null,
            siNumber = 123456,
            cardType = SIConstants.SI_CARD8_9_SIAC,
            checkTime = null,
            startTime = SITime(LocalTime.of(10, 0)),
            finishTime = SITime(LocalTime.of(10, 30)),
            automaticStatus = false,
            resultStatus = ResultStatus.ERROR,
            points = 0,
            runTime = Duration.ZERO,
            modified = true,
            sent = false
        )
        val punches = arrayListOf(
            Punch(
                UUID.randomUUID(),
                raceId,
                result.id,
                result.siNumber,
                31,
                SITime(LocalTime.of(10, 10)),
                SITime(LocalTime.of(10, 10)),
                SIRecordType.CONTROL,
                0,
                PunchStatus.INVALID,
                Duration.ZERO
            )
        )

        ResultsProcessor.calculateResult(
            result = result,
            category = null,
            punches = punches,
            manualStatus = ResultStatus.OK,
            race = race,
            dataProcessor = dataProcessor
        )

        assertEquals(ResultStatus.OK, result.resultStatus)
        assertEquals(Duration.ofMinutes(30), result.runTime)
        assertEquals(PunchStatus.UNKNOWN, punches.single { it.punchType == SIRecordType.CONTROL }.punchStatus)
    }

    @Test
    fun manualRecalculationRepairsStaleFinishDayWeekBeforeApplyingOkStatus() = runTest {
        val raceId = UUID.randomUUID()
        val dataProcessor = mock<DataProcessor>()
        val race = Race(
            id = raceId,
            name = "Sprint Practice",
            apiKey = "",
            startDateTime = LocalDateTime.of(2026, 6, 25, 0, 0),
            raceType = RaceType.SPRINT,
            raceLevel = RaceLevel.PRACTICE,
            raceBand = RaceBand.M80,
            timeLimit = Duration.ofHours(2)
        )
        val result = Result(
            id = UUID.randomUUID(),
            raceId = raceId,
            competitorId = null,
            siNumber = 2005010,
            cardType = SIConstants.SI_CARD8_9_SIAC,
            checkTime = null,
            startTime = SITime(LocalTime.of(14, 2, 23), 4, 0),
            finishTime = SITime(LocalTime.of(14, 10, 0), 0, 0),
            automaticStatus = true,
            resultStatus = ResultStatus.ERROR,
            points = 0,
            runTime = Duration.ZERO,
            modified = true,
            sent = false
        )
        val punches = arrayListOf(
            Punch(
                UUID.randomUUID(),
                raceId,
                result.id,
                result.siNumber,
                31,
                SITime(LocalTime.of(14, 5), 4, 0),
                SITime(LocalTime.of(14, 5), 4, 0),
                SIRecordType.CONTROL,
                0,
                PunchStatus.INVALID,
                Duration.ZERO
            )
        )

        ResultsProcessor.calculateResult(
            result = result,
            category = null,
            punches = punches,
            manualStatus = ResultStatus.OK,
            race = race,
            dataProcessor = dataProcessor
        )

        assertEquals(4 * 24 * 60 * 60L + 14 * 60 * 60L + 10 * 60L, result.finishTime?.getSeconds())
        assertEquals(ResultStatus.OK, result.resultStatus)
        assertEquals(false, result.automaticStatus)
        assertEquals(Duration.ofSeconds(7 * 60L + 37L), result.runTime)
    }

    @Test
    fun testClassicsCorrectData() {
        val result = Result()
        val punches = ArrayList<Punch>()
        val controlPoints = ArrayList<ControlPoint>()

        for (i in 1..6) {
            punches.add(
                Punch(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    null,
                    null,
                    30 + i,
                    SITime(),
                    SITime(),
                    SIRecordType.CONTROL, i, PunchStatus.UNKNOWN, Duration.ZERO
                )
            )
            controlPoints.add(
                ControlPoint(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    30 + i,
                    ControlPointType.CONTROL,
                    i
                )
            )
        }
        controlPoints.last().type = ControlPointType.BEACON
        ResultsProcessor.evaluateClassics(punches, controlPoints, result)
        assertEquals(ResultStatus.OK, result.resultStatus)
        //Check the punches
        for (punch in punches) {
            assertEquals(PunchStatus.VALID, punch.punchStatus)
        }
        assertEquals(5, result.points)
    }

    @Test
    fun testClassicsRandomData() {
        for (t in 0..50) {
            val result = Result(

            )
            val punches = ArrayList<Punch>()
            val controlPoints = ArrayList<ControlPoint>()

            val randLength = Random().nextInt(1000) + 1
            var randCode = 0

            for (i in 0..randLength) {

                randCode += Random().nextInt(10) + 1

                punches.add(
                    Punch(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        null,
                        null,
                        randCode,
                        SITime(),
                        SITime(),
                        SIRecordType.CONTROL, i, PunchStatus.UNKNOWN, Duration.ZERO
                    )
                )
                controlPoints.add(
                    ControlPoint(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        randCode,
                        ControlPointType.CONTROL,
                        i
                    )
                )
            }

            controlPoints.last().type = ControlPointType.BEACON
            ResultsProcessor.evaluateClassics(punches, controlPoints, result)
            assertEquals(ResultStatus.OK, result.resultStatus)
            assertEquals(randLength, result.points)
        }
    }

    @Test
    fun testClassicsDuplicateBeaconEvaluation() {
        val result = Result()
        val punches = ArrayList<Punch>()
        val controlPoints = ArrayList<ControlPoint>()

        for (i in 1..6) {
            punches.add(
                Punch(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    null,
                    null,
                    30 + i,
                    SITime(),
                    SITime(),
                    SIRecordType.CONTROL, i, PunchStatus.UNKNOWN, Duration.ZERO
                )
            )
            controlPoints.add(
                ControlPoint(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    30 + i,
                    ControlPointType.CONTROL,
                    i
                )
            )
        }
        controlPoints.last().type = ControlPointType.BEACON

        punches.add(
            Punch(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                36,
                SITime(),
                SITime(),
                SIRecordType.CONTROL, 19, PunchStatus.UNKNOWN, Duration.ZERO
            )
        )

        ResultsProcessor.evaluateClassics(punches, controlPoints, result)
        assertEquals(ResultStatus.OK, result.resultStatus)

        //Check the punches
        assertEquals(5, result.points)
        assertEquals(PunchStatus.INVALID, punches[punches.size - 2].punchStatus)
    }

    @Test
    fun testClassicsPunchesAfterBeaconEvaluation() {
        val result = Result()
        val punches = ArrayList<Punch>()
        val controlPoints = ArrayList<ControlPoint>()

        for (i in 1..6) {
            controlPoints.add(
                ControlPoint(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    30 + i,
                    ControlPointType.CONTROL,
                    i
                )
            )
        }

        controlPoints.last().type = ControlPointType.BEACON

        for (i in 3..6) {
            punches.add(
                Punch(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    null,
                    null,
                    30 + i,
                    SITime(),
                    SITime(),
                    SIRecordType.CONTROL, i, PunchStatus.UNKNOWN, Duration.ZERO
                )
            )
        }

        for (i in 1..2) {
            punches.add(
                Punch(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    null,
                    null,
                    30 + i,
                    SITime(),
                    SITime(),
                    SIRecordType.CONTROL, i, PunchStatus.UNKNOWN, Duration.ZERO
                )
            )
        }

        ResultsProcessor.evaluateClassics(punches, controlPoints, result)
        assertEquals(ResultStatus.DID_NOT_FINISH, result.resultStatus)

        //Check the punches
        assertEquals(5, result.points)
    }

    @Test
    fun testClassicsRankingOrNoRanking() {
        val result = Result()
        val punches = ArrayList<Punch>()
        val controlPoints = ArrayList<ControlPoint>()
        ResultsProcessor.evaluateClassics(punches, controlPoints, result)
        assertEquals(ResultStatus.NO_RANKING, result.resultStatus)

        punches.add(
            Punch(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                31,
                SITime(),
                SITime(),
                SIRecordType.CONTROL, 0, PunchStatus.UNKNOWN, Duration.ZERO
            )
        )
        controlPoints.add(
            ControlPoint(
                UUID.randomUUID(),
                UUID.randomUUID(),
                31,
                ControlPointType.CONTROL,
                0
            )
        )
        ResultsProcessor.evaluateClassics(punches, controlPoints, result)
        assertEquals(ResultStatus.OK, result.resultStatus)
        punches.add(
            Punch(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                32,
                SITime(),
                SITime(),
                SIRecordType.CONTROL, 1, PunchStatus.UNKNOWN, Duration.ZERO
            )
        )
        controlPoints.add(
            ControlPoint(
                UUID.randomUUID(),
                UUID.randomUUID(),
                32,
                ControlPointType.CONTROL,
                0
            )
        )
        ResultsProcessor.evaluateClassics(punches, controlPoints, result)
        assertEquals(ResultStatus.OK, result.resultStatus)
    }

    @Test
    fun testOrienteeringCorrectData() {
        val result = Result()
        val punches = ArrayList<Punch>()
        val controlPoints = ArrayList<ControlPoint>()

        for (i in 1..6) {
            punches.add(
                Punch(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    null,
                    null,
                    30 + i,
                    SITime(),
                    SITime(),
                    SIRecordType.CONTROL, i, PunchStatus.UNKNOWN, Duration.ZERO
                )
            )
            controlPoints.add(
                ControlPoint(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    30 + i,
                    ControlPointType.CONTROL,
                    i
                )
            )
        }
        ResultsProcessor.evaluateOrienteering(punches, controlPoints, result)
        assertEquals(ResultStatus.OK, result.resultStatus)
        assertEquals(6, result.points)
    }

    @Test
    fun testOrienteeringDataWithMistake() {
        val result = Result()
        val punches = ArrayList<Punch>()
        val controlPoints = ArrayList<ControlPoint>()

        for (i in 1..6) {
            punches.add(
                Punch(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    null,
                    null,
                    30 + i,
                    SITime(),
                    SITime(),
                    SIRecordType.CONTROL, i, PunchStatus.UNKNOWN, Duration.ZERO
                )
            )
            controlPoints.add(
                ControlPoint(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    30 + i,
                    ControlPointType.CONTROL,
                    i
                )
            )
        }
        punches.add(
            2, Punch(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                62,
                SITime(),
                SITime(),
                SIRecordType.CONTROL, 0, PunchStatus.UNKNOWN, Duration.ZERO
            )
        )
        punches.add(
            4, Punch(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                64,
                SITime(),
                SITime(),
                SIRecordType.CONTROL, 5, PunchStatus.UNKNOWN, Duration.ZERO

            )
        )
        ResultsProcessor.evaluateOrienteering(punches, controlPoints, result)
        assertEquals(ResultStatus.OK, result.resultStatus)
        assertEquals(6, result.points)
        assertEquals(PunchStatus.INVALID, punches[2].punchStatus)
        assertEquals(PunchStatus.INVALID, punches[4].punchStatus)
    }

    @Test
    fun testOrienteeringWithEmptyControls() {
        val result = Result()
        val punches = ArrayList<Punch>()
        val controlPoints = ArrayList<ControlPoint>()

        for (i in 1..6) {
            punches.add(
                Punch(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    null,
                    null,
                    30 + i,
                    SITime(),
                    SITime(),
                    SIRecordType.CONTROL, i, PunchStatus.UNKNOWN, Duration.ZERO
                )
            )
        }
        ResultsProcessor.evaluateOrienteering(punches, controlPoints, result)
        assertEquals(ResultStatus.OK, result.resultStatus)
    }

    @Test
    fun testOrienteeringIncorrectData() {
        val result = Result()
        val punches = ArrayList<Punch>()
        val controlPoints = ArrayList<ControlPoint>()

        for (i in 1..6) {
            punches.add(
                Punch(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    null,
                    null,
                    30 + i,
                    SITime(),
                    SITime(),
                    SIRecordType.CONTROL, i, PunchStatus.UNKNOWN, Duration.ZERO
                )
            )
            controlPoints.add(
                ControlPoint(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    30 + i,
                    ControlPointType.CONTROL,
                    i
                )
            )
        }
        punches[2].siCode = 44
        ResultsProcessor.evaluateOrienteering(punches, controlPoints, result)
        assertEquals(ResultStatus.MISPUNCHED, result.resultStatus)
    }

    @Test
    fun testSprintCorrectData() {
        val result = Result()
        val punches = ArrayList<Punch>()
        val controlPoints = ArrayList<ControlPoint>()
        ResultsProcessor.evaluateSprint(punches, controlPoints, result)
        assertEquals(ResultStatus.NO_RANKING, result.resultStatus)

        for (i in 1..12) {
            punches.add(
                Punch(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    null,
                    null,
                    30 + i,
                    SITime(),
                    SITime(),
                    SIRecordType.CONTROL, i, PunchStatus.UNKNOWN, Duration.ZERO
                )
            )
            controlPoints.add(
                ControlPoint(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    30 + i,
                    ControlPointType.CONTROL,
                    i
                )
            )
        }
        controlPoints[4].type = ControlPointType.SEPARATOR
        controlPoints[7].type = ControlPointType.SEPARATOR
        controlPoints.last().type = ControlPointType.BEACON

        ResultsProcessor.evaluateSprint(punches, controlPoints, result)
        assertEquals(ResultStatus.OK, result.resultStatus)
        assertEquals(9, result.points)
        punches.forEachIndexed { index, punch ->
            assertEquals(if (index == 7) PunchStatus.UNKNOWN else PunchStatus.VALID, punch.punchStatus)
        }

        //Add some random invalid data
        punches.add(
            5, Punch(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                99,
                SITime(),
                SITime(),
                SIRecordType.CONTROL, 15, PunchStatus.UNKNOWN, Duration.ZERO
            )
        )

        punches.add(
            9, Punch(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                67,
                SITime(),
                SITime(),
                SIRecordType.CONTROL, 15, PunchStatus.UNKNOWN, Duration.ZERO
            )
        )
        ResultsProcessor.evaluateSprint(punches, controlPoints, result)
        assertEquals(ResultStatus.OK, result.resultStatus)
        assertEquals(9, result.points)
        assertEquals(PunchStatus.UNKNOWN, punches[5].punchStatus)
        assertEquals(PunchStatus.UNKNOWN, punches[9].punchStatus)
    }

    @Test
    fun testSprintDatWithMistakes() {
        // Double punched separator
        val result = Result()
        val punches = ArrayList<Punch>()
        val controlPoints = ArrayList<ControlPoint>()
        ResultsProcessor.evaluateSprint(punches, controlPoints, result)
        assertEquals(ResultStatus.NO_RANKING, result.resultStatus)

        for (i in 1..12) {
            punches.add(
                Punch(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    null,
                    null,
                    30 + i,
                    SITime(),
                    SITime(),
                    SIRecordType.CONTROL, i, PunchStatus.UNKNOWN, Duration.ZERO
                )
            )
            controlPoints.add(
                ControlPoint(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    30 + i,
                    ControlPointType.CONTROL,
                    i
                )
            )
        }

        controlPoints[4].type = ControlPointType.SEPARATOR
        controlPoints[7].type = ControlPointType.SEPARATOR
        controlPoints.last().type = ControlPointType.BEACON

        punches.add(
            3, Punch(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                34,
                SITime(),
                SITime(),
                SIRecordType.CONTROL, 15, PunchStatus.UNKNOWN, Duration.ZERO
            )
        )

        punches.add(
            8, Punch(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                37,
                SITime(),
                SITime(),
                SIRecordType.CONTROL, 15, PunchStatus.UNKNOWN, Duration.ZERO
            )
        )
        for (pun in punches.withIndex()) {
            pun.value.order = pun.index + 1
        }

        ResultsProcessor.evaluateSprint(punches, controlPoints, result)
        assertEquals(ResultStatus.OK, result.resultStatus)
        assertEquals(9, result.points)
        assertEquals(PunchStatus.DUPLICATE, punches[4].punchStatus)
        assertEquals(PunchStatus.DUPLICATE, punches[8].punchStatus)
    }

    @Test
    fun testSprintUsesAliasesForLoopRoles() {
        val result = Result()
        val punches = arrayListOf(
            punch(171, 1),
            punch(161, 2),
            punch(137, 3),
            punch(162, 4),
            punch(172, 5),
            punch(136, 6)
        )
        val controlPoints = arrayListOf(
            controlPoint(161, ControlPointType.CONTROL, 1),
            controlPoint(162, ControlPointType.CONTROL, 2),
            controlPoint(137, ControlPointType.SEPARATOR, 3),
            controlPoint(171, ControlPointType.CONTROL, 4),
            controlPoint(172, ControlPointType.CONTROL, 5),
            controlPoint(136, ControlPointType.BEACON, 6)
        )
        val aliases = listOf(
            ControlPointAlias(controlPoints[0], Alias(161, "1")),
            ControlPointAlias(controlPoints[1], Alias(162, "2")),
            ControlPointAlias(controlPoints[2], Alias(137, "S")),
            ControlPointAlias(controlPoints[3], Alias(171, "1F")),
            ControlPointAlias(controlPoints[4], Alias(172, "2F")),
            ControlPointAlias(controlPoints[5], Alias(136, "B"))
        )

        ResultsProcessor.evaluateSprint(punches, controlPoints, result, aliases)

        assertEquals(ResultStatus.OK, result.resultStatus)
        assertEquals(2, result.points)
        assertEquals(PunchStatus.UNKNOWN, punches[0].punchStatus)
        assertEquals(PunchStatus.VALID, punches[1].punchStatus)
        assertEquals(PunchStatus.VALID, punches[2].punchStatus)
        assertEquals(PunchStatus.UNKNOWN, punches[3].punchStatus)
        assertEquals(PunchStatus.VALID, punches[4].punchStatus)
        assertEquals(PunchStatus.VALID, punches[5].punchStatus)
    }

    @Test
    fun testSprintAllSeparators() {
        val result = Result()
        val punches = ArrayList<Punch>()
        val controlPoints = ArrayList<ControlPoint>()

        for (i in 1..12) {
            punches.add(
                Punch(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    null,
                    null,
                    30 + i,
                    SITime(),
                    SITime(),
                    SIRecordType.CONTROL, i, PunchStatus.UNKNOWN, Duration.ZERO
                )
            )
            controlPoints.add(
                ControlPoint(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    30 + i,
                    ControlPointType.CONTROL,
                    i
                )
            )
        }

        ResultsProcessor.evaluateSprint(punches, controlPoints, result)
        assertEquals(ResultStatus.DID_NOT_FINISH, result.resultStatus)
        assertEquals(9, result.points)
        punches.forEachIndexed { index, punch ->
            assertEquals(if (index < 9) PunchStatus.VALID else PunchStatus.UNKNOWN, punch.punchStatus)
        }
    }

    @Test
    fun testWithStartPunches() {
        for (t in 0..50) {
            val result = Result(

            )
            val punches = ArrayList<Punch>()
            val controlPoints = ArrayList<ControlPoint>()

            val randLength = Random().nextInt(1000) + 1
            var randCode = 0

            for (i in 0..randLength) {

                randCode += Random().nextInt(10) + 1

                punches.add(
                    Punch(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        null,
                        null,
                        30 + i,
                        SITime(),
                        SITime(),
                        SIRecordType.CONTROL, i, PunchStatus.UNKNOWN, Duration.ZERO
                    )
                )
                controlPoints.add(
                    ControlPoint(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        randCode,
                        ControlPointType.CONTROL,
                        i
                    )
                )
            }

            controlPoints.last().type = ControlPointType.BEACON
        }
    }

    private fun punch(siCode: Int, order: Int): Punch =
        Punch(
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            null,
            siCode,
            SITime(),
            SITime(),
            SIRecordType.CONTROL,
            order,
            PunchStatus.UNKNOWN,
            Duration.ZERO
        )

    private fun controlPoint(siCode: Int, type: ControlPointType, order: Int): ControlPoint =
        ControlPoint(
            UUID.randomUUID(),
            UUID.randomUUID(),
            siCode,
            type,
            order
        )
}
