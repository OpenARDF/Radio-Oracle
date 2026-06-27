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

package org.openardf.radiooracle.shared.cli

import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.shared.domain.StandardCategoryType
import org.openardf.radiooracle.shared.course.ControlPointDisplayToken
import org.openardf.radiooracle.shared.course.ControlPointRules
import org.openardf.radiooracle.shared.event.EventAlias
import org.openardf.radiooracle.shared.event.EventCategory
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventCompetitor
import org.openardf.radiooracle.shared.event.EventCompetitorCategory
import org.openardf.radiooracle.shared.event.EventCompetitorData
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventProjectFileJson
import org.openardf.radiooracle.shared.event.EventRace
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.EventReadoutData
import org.openardf.radiooracle.shared.event.EventResult
import org.openardf.radiooracle.shared.event.EventValidationRules
import org.openardf.radiooracle.shared.event.StandardCategoryRules
import org.openardf.radiooracle.shared.files.EventCsvRows
import org.openardf.radiooracle.shared.files.FileConstants
import org.openardf.radiooracle.shared.files.TemplateRenderer
import org.openardf.radiooracle.shared.files.TimedPunchCsvField
import org.openardf.radiooracle.shared.network.NetworkEndpoints
import org.openardf.radiooracle.shared.results.CourseEvaluator
import org.openardf.radiooracle.shared.results.EventResultPlacement
import org.openardf.radiooracle.shared.results.EventResultSending
import org.openardf.radiooracle.shared.results.EvaluationControlPoint
import org.openardf.radiooracle.shared.results.EvaluationPunch
import org.openardf.radiooracle.shared.sportident.SportIdentCodes

/** Runs a small desktop smoke check over shared rules, models, and formatting helpers. */
fun main() {
    check(SportIdentCodes.isSICodeValid(31))

    val evaluation = CourseEvaluator.evaluate(
        RaceType.CLASSIC,
        punches = listOf(
            EvaluationPunch(31, SIRecordType.CONTROL),
            EvaluationPunch(32, SIRecordType.CONTROL)
        ),
        controlPoints = listOf(
            EvaluationControlPoint(31, ControlPointType.CONTROL),
            EvaluationControlPoint(32, ControlPointType.CONTROL)
        )
    )

    check(evaluation.points == 2)
    val raceData = sampleRaceData()
    val projectFile = EventProjectFile(raceData = raceData)
    check(EventProjectFileJson.decode(EventProjectFileJson.encode(projectFile)) == projectFile)
    check(EventValidationRules.validateRaceData(raceData).isEmpty())
    check(
        EventResultPlacement.sortByPlace(raceData.competitorData)
            .single()
            .readoutData
            ?.result
            ?.place == 1
    )
    check(
        EventCsvRows.readoutRow(
            siNumber = 123456,
            checkTimeText = null,
            startTimeText = "10:00:00",
            finishTimeText = "10:45:00",
            controlPunches = listOf(TimedPunchCsvField(31, "10:15:00"))
        ) == "123456;;10:00:00;10:45:00;1;31;10:15:00"
    )
    check(
        EventCsvRows.competitorStartRow(
            competitor = raceData.competitorData.single().competitorCategory.competitor,
            categoryName = "M21",
            startTimeText = "10:00"
        ) == "1;Runner;Test;M21;;10:00;;;;123456"
    )
    check(
        ControlPointRules.formatDisplayTokens(
            listOf(ControlPointDisplayToken(siCode = 31, aliasName = "F1")),
            useAlias = true
        ) == "F1"
    )
    check(
        TemplateRenderer.render(
            "{{race_name}}${FileConstants.KEY_TAB}{{title_results}}",
            mapOf(
                FileConstants.KEY_RACE_NAME to "Desktop smoke",
                FileConstants.KEY_TITLE_RESULTS to "Results"
            )
        ) == "Desktop smoke\tResults"
    )
    check(StandardCategoryRules.parseDefinition("M21;1;39")?.maxAge == 39)
    check(StandardCategoryRules.definitionsFor(StandardCategoryType.INTERNATIONAL).size == 12)
    check(NetworkEndpoints.ORESULTS_RESULTS_API_URL == "https://api.oresults.eu")
    check(EventResultSending.unsentCompetitorIds(raceData.competitorData) == setOf("competitor"))
    println("Radio-Oracle desktop shared smoke OK")
}

/** Builds a minimal event used by the desktop smoke check. */
private fun sampleRaceData(): EventRaceData =
    EventRaceData(
        race = EventRace(
            id = "race",
            name = "Desktop smoke",
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
                    id = "M21",
                    raceId = "race",
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
                ),
                controlPoints = emptyList(),
                competitors = emptyList()
            )
        ),
        aliases = listOf(EventAlias(id = "alias", raceId = "race", siCode = 31, name = "F1")),
        competitorData = listOf(
            EventCompetitorData(
                competitorCategory = EventCompetitorCategory(
                    competitor = EventCompetitor(
                        id = "competitor",
                        raceId = "race",
                        categoryId = "M21",
                        firstName = "Test",
                        lastName = "Runner",
                        club = "",
                        index = "",
                        isMan = true,
                        birthYear = null,
                        siNumber = 123456,
                        siRent = false,
                        startNumber = 1,
                        drawnStartTimeSeconds = 0
                    ),
                    category = null
                ),
                readoutData = EventReadoutData(
                    result = EventResult(
                        id = "result",
                        raceId = "race",
                        competitorId = "competitor",
                        siNumber = 123456,
                        cardType = 5,
                        checkTimeSeconds = null,
                        startTimeSeconds = null,
                        finishTimeSeconds = null,
                        readoutDateTimeIso = "2026-05-30T10:45",
                        automaticStatus = true,
                        resultStatus = ResultStatus.OK,
                        points = 2,
                        runTimeSeconds = 2_700,
                        modified = false,
                        sent = false
                    ),
                    punches = emptyList()
                )
            )
        ),
        unmatchedReadoutData = emptyList()
    )
