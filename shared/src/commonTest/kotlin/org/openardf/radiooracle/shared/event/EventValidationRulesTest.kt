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

package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.domain.PunchStatus
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.SIRecordType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventValidationRulesTest {
    @Test
    fun acceptsValidRaceData() {
        assertTrue(EventValidationRules.validateRaceData(raceData()).isEmpty())
    }

    @Test
    fun reportsDuplicateEventData() {
        val raceData = raceData(
            race = race(name = ""),
            categories = listOf(categoryData("M21"), categoryData("M21")),
            aliases = listOf(alias("F1", 31), alias("F1", 32), alias("F2", 31)),
            competitors = listOf(
                competitorData("one", startNumber = 1, siNumber = 123, bibNumber = "B1", callSign = "K0ABC"),
                competitorData("two", startNumber = 1, siNumber = 123, bibNumber = "B1", callSign = "k0abc")
            )
        )

        val issues = EventValidationRules.validateRaceData(raceData)

        assertTrue(issues.contains(EventValidationIssue.BlankRaceName))
        assertTrue(issues.contains(EventValidationIssue.DuplicateCategoryNames(setOf("M21"))))
        assertTrue(issues.contains(EventValidationIssue.DuplicateAliasNames(setOf("F1"))))
        assertTrue(issues.contains(EventValidationIssue.DuplicateAliasCodes(setOf(31))))
        assertTrue(issues.contains(EventValidationIssue.DuplicateSINumbers(setOf(123))))
        assertTrue(issues.contains(EventValidationIssue.DuplicateBibNumbers(setOf("B1"))))
        assertTrue(issues.contains(EventValidationIssue.DuplicateCallSigns(setOf("K0ABC"))))
    }

    @Test
    fun allowsDuplicateSiNumbersForRecordedPracticeRepeats() {
        val raceData = raceData(
            race = race().copy(raceLevel = RaceLevel.PRACTICE),
            competitors = listOf(
                competitorData("one", siNumber = 123, readoutData = readout()),
                competitorData("two", siNumber = 123, readoutData = readout())
            )
        )

        val issues = EventValidationRules.validateRaceData(raceData)

        assertFalse(issues.contains(EventValidationIssue.DuplicateSINumbers(setOf(123))))
    }

    @Test
    fun stillReportsDuplicateSiNumbersForUnrecordedPracticeCompetitors() {
        val raceData = raceData(
            race = race().copy(raceLevel = RaceLevel.PRACTICE),
            competitors = listOf(
                competitorData("one", siNumber = 123, readoutData = readout()),
                competitorData("two", siNumber = 123, readoutData = null)
            )
        )

        val issues = EventValidationRules.validateRaceData(raceData)

        assertTrue(issues.contains(EventValidationIssue.DuplicateSINumbers(setOf(123))))
    }

    @Test
    fun reportsDuplicateStartAndFinishPunches() {
        val issues = EventValidationRules.validateRaceData(
            raceData(
                competitors = listOf(
                    competitorData(
                        id = "one",
                        readoutData = readout(
                            punches = listOf(
                                punch(SIRecordType.START),
                                punch(SIRecordType.START),
                                punch(SIRecordType.FINISH),
                                punch(SIRecordType.FINISH)
                            )
                        )
                    )
                )
            )
        )

        assertTrue(issues.contains(EventValidationIssue.MultipleStartPunches(123)))
        assertTrue(issues.contains(EventValidationIssue.MultipleFinishPunches(123)))
    }

    @Test
    fun reportsInvalidCategoryControlPoints() {
        val issues = EventValidationRules.validateRaceData(
            raceData(
                categories = listOf(categoryData("M21", controlPointsString = "31 32 31"))
            )
        )

        assertTrue(
            issues.contains(
                EventValidationIssue.InvalidCategoryControlPoints(
                    categoryName = "M21",
                    error = org.openardf.radiooracle.shared.course.ControlPointValidationError.ASSIGNED_DUPLICATE,
                    token = null,
                    siCode = 31
                )
            )
        )
    }

    @Test
    fun reportsEventReadinessProblems() {
        val issues = EventValidationRules.validateRaceData(
            raceData(
                categories = emptyList(),
                controls = listOf(control("fox-extra", 36, "Extra")),
                competitors = listOf(competitorData("one", siNumber = null))
            )
        )

        assertTrue(issues.contains(EventValidationIssue.NoCategories))
        assertTrue(
            issues.contains(
                EventValidationIssue.ControlInventoryIssue("Classic events should define exactly 5 fox controls; found 1.")
            )
        )
        assertTrue(issues.contains(EventValidationIssue.MissingCompetitorSiNumbers(setOf("RUNNER one"))))
    }

    @Test
    fun reportsUnusedControlsAndPublicLabelProblems() {
        val issues = EventValidationRules.validateRaceData(
            raceData(
                controls = classicControls() +
                    control("unused", 36, "Fox 1") +
                    control("missing-label", 37, "")
            )
        )

        assertTrue(issues.contains(EventValidationIssue.UnusedControls(setOf("Fox 1", "37"))))
        assertTrue(issues.contains(EventValidationIssue.MissingPublicLabels(setOf("37"))))
        assertTrue(issues.contains(EventValidationIssue.DuplicatePublicLabels(setOf("Fox 1"))))
    }

    @Test
    fun warnsAndValidatesAgainstEventRaceTypeWhenCategoryHasLegacySettings() {
        val issues = EventValidationRules.validateRaceData(
            raceData(
                race = race().copy(raceType = RaceType.CLASSIC),
                categories = listOf(
                    categoryData(
                        name = "W21",
                        controlPointsString = "31 32 33 50B",
                        differentProperties = true,
                        raceType = RaceType.SPRINT
                    )
                )
            )
        )

        assertTrue(
            issues.contains(
                EventValidationIssue.LegacyCategoryRaceSettings(categoryName = "W21")
            )
        )
        assertFalse(
            issues.any {
                it is EventValidationIssue.CategoryCourseRequirementIssue &&
                    it.categoryName == "W21" &&
                    it.message.contains("Sprint category must assign exactly 10 foxes")
            }
        )
    }

    @Test
    fun warnsAboutControlCodesAboveLegacyCompatibilityRange() {
        val issues = EventValidationRules.validateRaceData(
            raceData(
                categories = listOf(categoryData("M21", controlPointsString = "31 256")),
                aliases = listOf(alias("F256", 256))
            )
        )

        assertTrue(issues.contains(EventValidationIssue.LegacyIncompatibleCategoryControlCodes("M21", setOf(256))))
        assertTrue(issues.contains(EventValidationIssue.LegacyIncompatibleAliasCodes(setOf(256))))
    }

    private fun raceData(
        race: EventRace = race(),
        categories: List<EventCategoryData> = listOf(categoryData("M21")),
        aliases: List<EventAlias> = listOf(alias("F1", 31)),
        competitors: List<EventCompetitorData> = listOf(competitorData("one")),
        unmatchedReadoutData: List<EventReadoutData> = emptyList(),
        controls: List<EventControl> = classicControls()
    ): EventRaceData =
        EventRaceData(
            race = race,
            categories = categories,
            aliases = aliases,
            competitorData = competitors,
            unmatchedReadoutData = unmatchedReadoutData,
            controls = controls
        )

    private fun race(name: String = "Race"): EventRace =
        EventRace(
            id = "race",
            name = name,
            apiKey = "",
            startDateTimeIso = "2026-05-30T10:00",
            raceType = RaceType.CLASSIC,
            raceLevel = RaceLevel.PRACTICE,
            raceBand = RaceBand.M80,
            timeLimitSeconds = 7_200
        )

    private fun categoryData(
        name: String,
        controlPointsString: String = "",
        differentProperties: Boolean = false,
        raceType: RaceType? = null
    ): EventCategoryData =
        EventCategoryData(
            category = EventCategory(
                id = name,
                raceId = "race",
                name = name,
                isMan = true,
                maxAge = null,
                lengthMeters = 0,
                climbMeters = 0,
                order = 0,
                differentProperties = differentProperties,
                raceType = raceType,
                raceBand = null,
                timeLimitSeconds = null,
                controlPointsString = controlPointsString
            ),
            controlPoints = if (controlPointsString.isBlank()) classicControlPoints(name) else emptyList(),
            competitors = emptyList()
        )

    private fun classicControlPoints(categoryId: String): List<EventControlPoint> =
        classicControls().mapIndexed { index, control ->
            EventControlPoint(
                id = "$categoryId-${control.id}",
                categoryId = categoryId,
                siCode = control.siCode,
                type = control.type,
                order = index + 1,
                controlId = control.id
            )
        }

    private fun classicControls(): List<EventControl> =
        (1..5).map { number ->
            control("fox-$number", 30 + number, "Fox $number")
        } + control("beacon", 50, "B", ControlPointType.BEACON)

    private fun control(
        id: String,
        siCode: Int,
        publicLabel: String,
        type: ControlPointType = ControlPointType.CONTROL
    ): EventControl =
        EventControl(
            id = id,
            raceId = "race",
            label = siCode.toString(),
            siCode = siCode,
            type = type,
            publicLabel = publicLabel.takeIf { it.isNotBlank() }
        )

    private fun alias(name: String, siCode: Int): EventAlias =
        EventAlias(id = "$name-$siCode", raceId = "race", siCode = siCode, name = name)

    private fun competitorData(
        id: String,
        startNumber: Int? = null,
        siNumber: Int? = 123,
        bibNumber: String = "",
        callSign: String = "",
        readoutData: EventReadoutData? = null
    ): EventCompetitorData =
        EventCompetitorData(
            competitorCategory = EventCompetitorCategory(
                competitor = EventCompetitor(
                    id = id,
                    raceId = "race",
                    categoryId = "M21",
                    firstName = id,
                    lastName = "Runner",
                    club = "",
                    index = "",
                    isMan = true,
                    birthYear = null,
                    siNumber = siNumber,
                    siRent = false,
                    startNumber = startNumber,
                    drawnStartTimeSeconds = null,
                    bibNumber = bibNumber,
                    callSign = callSign
                ),
                category = null
            ),
            readoutData = readoutData
        )

    private fun readout(punches: List<EventPunch> = emptyList()): EventReadoutData =
        EventReadoutData(
            result = EventResult(
                id = "result",
                raceId = "race",
                competitorId = "one",
                siNumber = 123,
                cardType = 5,
                checkTimeSeconds = null,
                startTimeSeconds = null,
                finishTimeSeconds = null,
                readoutDateTimeIso = "2026-05-30T10:00",
                automaticStatus = true,
                resultStatus = ResultStatus.OK,
                points = 0,
                runTimeSeconds = 0,
                modified = false,
                sent = false
            ),
            punches = punches.map { EventAliasPunch(it, alias = null) }
        )

    private fun punch(type: SIRecordType): EventPunch =
        EventPunch(
            id = type.name,
            raceId = "race",
            resultId = "result",
            cardNumber = null,
            siCode = 0,
            siTimeSeconds = 0,
            originalSiTimeSeconds = 0,
            punchType = type,
            order = 0,
            punchStatus = PunchStatus.UNKNOWN,
            splitSeconds = 0
        )
}
