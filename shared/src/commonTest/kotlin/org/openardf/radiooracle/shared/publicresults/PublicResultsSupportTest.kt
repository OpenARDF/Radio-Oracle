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

package org.openardf.radiooracle.shared.publicresults

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
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventPunch
import org.openardf.radiooracle.shared.event.EventRace
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.EventReadoutData
import org.openardf.radiooracle.shared.event.EventResult
import org.openardf.radiooracle.shared.event.ProtectedCourseControlPoint
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.event.ProtectedCourseRoutePoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PublicResultsSupportTest {
    @Test
    fun protectedCourseCipherRoundTripsSharedDesktopAndroidFormat() {
        val course = protectedCourse()
        val encrypted = ProtectedCourseCipher.encryptCourseInfo(course, "race-password")

        assertNotEquals(course.sourceName, encrypted)
        assertEquals(course, ProtectedCourseCipher.decryptCourseInfo(encrypted, "race-password"))
        assertTrue(
            runCatching {
                ProtectedCourseCipher.decryptCourseInfo(encrypted, "wrong-password")
            }.isFailure
        )
    }

    @Test
    fun catalogReplacesMatchingPublicationAndRetainsOtherEvents() {
        val oldCurrent = entry(
            path = "2026-01-01-old",
            publicationId = "race:current"
        )
        val other = entry(
            path = "2025-12-01-other",
            publicationId = "race:other"
        )
        val current = entry(
            path = "2026-01-02-current",
            publicationId = "race:current"
        )

        val merge = PublicResultsSiteCatalog.merge(listOf(oldCurrent, other), current)
        val encoded = PublicResultsSiteCatalog.encode(merge.entries)

        assertEquals(setOf("2026-01-01-old"), merge.replacedPaths)
        assertEquals(
            setOf("2026-01-02-current", "2025-12-01-other"),
            merge.entries.map { it.path }.toSet()
        )
        assertEquals(merge.entries, PublicResultsSiteCatalog.parse(encoded))
    }

    @Test
    fun rendererLabelsChampionshipComingSoonAsUnofficial() {
        val rendered = PublicResultsSiteRenderer.renderRace(
            request = PublicResultsRaceRenderRequest(
                projectFile = EventProjectFile(raceData = raceData(RaceLevel.NATIONAL, false))
            ),
            generatedAtIso = "2026-07-27T12:00:00Z",
            appVersion = "test"
        )
        val html = rendered.files.getValue("index.html").decodeToString()

        assertTrue(html.contains("Unofficial Results Coming Soon"))
        assertTrue(html.contains("All published unofficial results"))
        assertEquals(0, rendered.resultCount)
    }

    @Test
    fun rendererOmitsUnofficialLanguageForPracticeComingSoon() {
        val rendered = PublicResultsSiteRenderer.renderRace(
            request = PublicResultsRaceRenderRequest(
                projectFile = EventProjectFile(raceData = raceData(RaceLevel.PRACTICE, false))
            ),
            generatedAtIso = "2026-07-27T12:00:00Z",
            appVersion = "test"
        )
        val html = rendered.files.getValue("index.html").decodeToString()

        assertTrue(html.contains(">Results Coming Soon<"))
        assertFalse(html.contains("Unofficial"))
    }

    @Test
    fun rendererPublishesPartialResultsAndSvgCourseDiagram() {
        val rendered = PublicResultsSiteRenderer.renderRace(
            request = PublicResultsRaceRenderRequest(
                projectFile = EventProjectFile(raceData = raceData(RaceLevel.PRACTICE, true)),
                protectedCourseInfoByCategoryId = mapOf("category" to protectedCourse())
            ),
            generatedAtIso = "2026-07-27T12:00:00Z",
            appVersion = "test"
        )
        val html = rendered.files.getValue("index.html").decodeToString()
        val svgPath = rendered.files.keys.single { it.endsWith(".svg") }
        val svg = rendered.files.getValue(svgPath).decodeToString()

        assertEquals(1, rendered.resultCount)
        assertTrue(html.contains("2D Course Diagrams"))
        assertTrue(html.contains(svgPath))
        assertTrue(svg.contains("<polyline"))
        assertTrue(svg.contains("Fox 1"))
        assertTrue("downloads/live-results.json" in rendered.files)
        assertTrue("downloads/iof-result-list.xml" in rendered.files)
    }

    @Test
    fun rendererSkipsIncompleteCourseGeometryWithoutBlockingResults() {
        val rendered = PublicResultsSiteRenderer.renderRace(
            request = PublicResultsRaceRenderRequest(
                projectFile = EventProjectFile(raceData = raceData(RaceLevel.PRACTICE, true)),
                protectedCourseInfoByCategoryId = mapOf(
                    "category" to ProtectedCourseInfo(idealOrder = "Fox 1")
                )
            ),
            generatedAtIso = "2026-07-27T12:00:00Z",
            appVersion = "test"
        )

        assertEquals(1, rendered.resultCount)
        assertTrue(rendered.courseGraphics.isEmpty())
        assertTrue(rendered.files.keys.none { it.endsWith(".svg") })
        assertTrue("downloads/live-results.json" in rendered.files)
    }

    private fun entry(path: String, publicationId: String) =
        PublishedPublicResultsEntry(
            path = path,
            name = path,
            start = path.take(10),
            generatedAt = "2026-07-27T12:00:00Z",
            resultCount = 1,
            unofficialResults = false,
            publicationId = publicationId
        )

    private fun raceData(level: RaceLevel, withResult: Boolean): EventRaceData {
        val race = EventRace(
            id = "race",
            name = "Shared Results Race",
            apiKey = "",
            startDateTimeIso = "2026-07-27T10:00:00",
            raceType = RaceType.CLASSIC,
            raceLevel = level,
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
            controlPointsString = ""
        )
        val competitor = EventCompetitor(
            id = "competitor",
            raceId = race.id,
            categoryId = category.id,
            firstName = "Alice",
            lastName = "Runner",
            club = "Club",
            index = "P1",
            isMan = true,
            birthYear = null,
            siNumber = 123456,
            siRent = false,
            startNumber = 1,
            drawnStartTimeSeconds = null
        )
        return EventRaceData(
            race = race,
            categories = listOf(
                EventCategoryData(
                    category = category,
                    controlPoints = emptyList(),
                    competitors = listOf(competitor)
                )
            ),
            aliases = emptyList(),
            competitorData = listOf(
                EventCompetitorData(
                    competitorCategory = EventCompetitorCategory(competitor, category),
                    readoutData = if (withResult) readout() else null
                )
            ),
            unmatchedReadoutData = emptyList()
        )
    }

    private fun readout(): EventReadoutData =
        EventReadoutData(
            result = EventResult(
                id = "result",
                raceId = "race",
                competitorId = "competitor",
                siNumber = 123456,
                cardType = 5,
                checkTimeSeconds = null,
                startTimeSeconds = 36_000,
                finishTimeSeconds = 37_200,
                readoutDateTimeIso = "2026-07-27T10:20:00",
                automaticStatus = true,
                resultStatus = ResultStatus.OK,
                points = 1,
                runTimeSeconds = 1_200,
                modified = false,
                sent = false,
                place = 1,
                categoryId = "category"
            ),
            punches = listOf(
                EventAliasPunch(
                    punch = EventPunch(
                        id = "punch",
                        raceId = "race",
                        resultId = "result",
                        cardNumber = 123456,
                        siCode = 31,
                        siTimeSeconds = 36_600,
                        originalSiTimeSeconds = 36_600,
                        punchType = SIRecordType.CONTROL,
                        order = 1,
                        punchStatus = PunchStatus.VALID,
                        splitSeconds = 600
                    ),
                    alias = null
                )
            )
        )

    private fun protectedCourse(): ProtectedCourseInfo =
        ProtectedCourseInfo(
            idealOrder = "Fox 1",
            sourceName = "course.kml",
            route = listOf(
                ProtectedCourseRoutePoint(35.75, -78.70),
                ProtectedCourseRoutePoint(35.76, -78.69)
            ),
            controlPoints = listOf(
                ProtectedCourseControlPoint(
                    controlId = "fox-1",
                    label = "Fox 1",
                    latitude = 35.755,
                    longitude = -78.695
                )
            )
        )
}
