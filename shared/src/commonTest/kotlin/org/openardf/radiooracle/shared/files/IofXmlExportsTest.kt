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

package org.openardf.radiooracle.shared.files

import org.openardf.radiooracle.shared.domain.ControlPointType
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
import org.openardf.radiooracle.shared.event.EventControl
import org.openardf.radiooracle.shared.event.EventControlPoint
import org.openardf.radiooracle.shared.event.EventPunch
import org.openardf.radiooracle.shared.event.EventRace
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.EventReadoutData
import org.openardf.radiooracle.shared.event.EventResult
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.event.ProtectedCourseObjectPoint
import org.openardf.radiooracle.shared.event.ProtectedCourseObjectType
import org.openardf.radiooracle.shared.event.PublicResultsPublicationStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IofXmlExportsTest {
    @Test
    fun exportsIofCourseDataUsingCategoryCourses() {
        val xml = IofXmlExports.courseData(raceDataWithCourseControls(), creator = "Radio-Oracle 1.0")

        assertTrue(xml.startsWith("""<?xml version="1.0" encoding="UTF-8"?>"""))
        assertTrue(xml.contains("""<CourseData xmlns="http://www.orienteering.org/datastandard/3.0" iofVersion="3.0" creator="Radio-Oracle 1.0">"""))
        assertTrue(xml.contains("<RaceCourseData>"))
        assertTrue(xml.contains("<Course>"))
        assertTrue(xml.contains("<Name>M21</Name>"))
        assertTrue(xml.contains("<Length>5200</Length>"))
        assertTrue(xml.contains("<Climb>120</Climb>"))
        assertTrue(xml.contains("""<CourseControl type="Start">"""))
        assertTrue(xml.contains("<Control>S</Control>"))
        assertTrue(xml.contains("""<CourseControl type="Control" randomOrder="true">"""))
        assertTrue(xml.contains("<Control>31</Control>"))
        assertTrue(xml.contains("<Control>32</Control>"))
        assertTrue(xml.contains("""<CourseControl type="Finish">"""))
        assertTrue(xml.contains("<Control>F</Control>"))
    }

    @Test
    fun exportedCourseDataCanBeImportedBySharedIofImporter() {
        val raceData = raceDataWithCourseControls()
        val xml = IofXmlExports.courseData(raceData)

        val imported = IofXmlImports.courseData(xml, raceData.race)

        assertEquals("IOF & Start Race", imported.parsedData.eventName)
        assertEquals(listOf("M21", "W21"), imported.parsedData.categories.map { it.category.name })
        assertEquals(listOf(31, 32), imported.parsedData.categories.first().controlPoints.map { it.siCode })
        assertTrue(imported.unsupportedItems.any { it.reason.contains("Start controls") })
        assertTrue(imported.unsupportedItems.any { it.reason.contains("Finish controls") })
    }

    @Test
    fun exportsCompleteArdfCourseDataWithProtectedPositionsAndAssignments() {
        val raceData = sprintRaceDataWithCourseControls()
        val categoryId = raceData.categories.single().category.id
        val xml = IofXmlExports.courseData(
            raceData = raceData,
            protectedCourseInfoByCategoryId = mapOf(
                categoryId to ProtectedCourseInfo(
                    lengthMeters = 4_300,
                    climbMeters = 90,
                    courseObjects = listOf(
                        protectedObject("start", "Start", ProtectedCourseObjectType.START, 37.1, -122.1, 10.0),
                        protectedObject("slow", "1", ProtectedCourseObjectType.CONTROL, 37.2, -122.2, 20.0),
                        protectedObject("spectator", "Spectator", ProtectedCourseObjectType.SPECTATOR, 37.3, -122.3, 30.0),
                        protectedObject("fast", "1F", ProtectedCourseObjectType.CONTROL, 37.4, -122.4, 40.0),
                        protectedObject("beacon", "B", ProtectedCourseObjectType.BEACON, 37.5, -122.5, 50.0),
                        protectedObject("finish", "Finish", ProtectedCourseObjectType.FINISH, 37.6, -122.6, 60.0)
                    )
                )
            ),
            creator = "Radio-Oracle 1.0"
        )

        assertTrue(xml.contains("""<Control type="Start">"""))
        assertTrue(xml.contains("""<Position lng="-122.1" lat="37.1" alt="10.0"/>"""))
        assertTrue(xml.contains("<Id>137</Id>"))
        assertTrue(xml.contains("<Name>Spectator</Name>"))
        assertTrue(xml.contains("<Id>136</Id>"))
        assertTrue(xml.contains("<Name>B</Name>"))
        assertTrue(xml.contains("<Length>4300</Length>"))
        assertTrue(xml.contains("<Climb>90</Climb>"))
        assertEquals(2, Regex("""<CourseControl type="Control" randomOrder="true">""").findAll(xml).count())
        assertEquals(2, Regex("""<CourseControl type="Control">""").findAll(xml).count())
        assertTrue(xml.indexOf("<Control>31</Control>") < xml.indexOf("<Control>137</Control>"))
        assertTrue(xml.indexOf("<Control>137</Control>") < xml.indexOf("<Control>41</Control>"))
        assertTrue(xml.indexOf("<Control>41</Control>") < xml.indexOf("<Control>136</Control>"))
        assertTrue(xml.contains("<ClassCourseAssignment>"))
        assertTrue(xml.contains("<ClassId>m21</ClassId>"))
        assertTrue(xml.contains("<ClassName>M21</ClassName>"))
        assertTrue(xml.contains("<CourseName>M21</CourseName>"))
        val validation = IofXmlValidator.validate(xml, IofXmlSchemaResource.loadBundledSchema())
        assertTrue(validation.valid, validation.errors.joinToString { it.message })
    }

    @Test
    fun exportsIofEntryListUsingCompetitors() {
        val xml = IofXmlExports.entryList(raceData(includeSecondCategory = true), creator = "Radio-Oracle 1.0")

        assertTrue(xml.startsWith("""<?xml version="1.0" encoding="UTF-8"?>"""))
        assertTrue(xml.contains("""<EntryList xmlns="http://www.orienteering.org/datastandard/3.0" iofVersion="3.0" creator="Radio-Oracle 1.0">"""))
        assertTrue(xml.contains("<PersonEntry>"))
        assertTrue(xml.contains("""<Person sex="M">"""))
        assertTrue(xml.contains("""<Id type="CZE">OK001</Id>"""))
        assertTrue(xml.contains("<Family>Runner</Family>"))
        assertTrue(xml.contains("<Given>Alice</Given>"))
        assertTrue(xml.contains("<Organisation>"))
        assertTrue(xml.contains("<Name>OK &amp; Test</Name>"))
        assertTrue(xml.contains("<ControlCard>123456</ControlCard>"))
        assertTrue(xml.contains("<Class>"))
        assertTrue(xml.contains("<Name>M21</Name>"))
        assertFalse(xml.contains("<BibNumber>"))
    }

    @Test
    fun exportedEntryListCanBeImportedBySharedIofImporter() {
        val xml = IofXmlExports.entryList(raceData(includeSecondCategory = true))

        val imported = IofXmlImports.entryList(xml)

        assertEquals(emptyList(), imported.unsupportedItems)
        assertEquals("IOF & Start Race", imported.parsedData.eventName)
        assertEquals("2026-06-01", imported.parsedData.startDate)
        assertEquals("10:00:00", imported.parsedData.startTime)
        assertEquals(listOf("M21", "W21"), imported.parsedData.entries.map { it.categoryName })
        assertEquals(listOf("Runner", "NoTime"), imported.parsedData.entries.map { it.lastName })
        assertEquals(listOf("Alice", "Bob"), imported.parsedData.entries.map { it.firstName })
        assertEquals(listOf("OK001", ""), imported.parsedData.entries.map { it.personId })
        assertEquals(listOf(123456, null), imported.parsedData.entries.map { it.siNumber })
        assertEquals(listOf("", ""), imported.parsedData.entries.map { it.bibNumber })
    }

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
        assertTrue(xml.contains("<BibNumber>1007</BibNumber>"))
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
    fun omitsNonNumericBibNumbersFromIofStartList() {
        val baseRaceData = raceData()
        val raceData = baseRaceData.copy(
            competitorData = baseRaceData.competitorData.map { competitorData ->
                competitorData.copy(
                    competitorCategory = competitorData.competitorCategory.copy(
                        competitor = competitorData.competitorCategory.competitor.copy(bibNumber = "B007")
                    )
                )
            }
        )

        val xml = IofXmlExports.startList(raceData)

        assertFalse(xml.contains("<BibNumber>"))
    }

    @Test
    fun omitsCategoriesWithoutCompetitorsFromStartListsAndWithoutResultsFromResultLists() {
        val startXml = IofXmlExports.startList(raceData())
        val resultXml = IofXmlExports.resultList(raceData(includeReadout = true))

        assertEquals(1, Regex("<ClassStart>").findAll(startXml).count())
        assertEquals(1, Regex("<ClassResult>").findAll(resultXml).count())
        assertTrue(startXml.contains("<Name>M21</Name>"))
        assertTrue(resultXml.contains("<Name>M21</Name>"))
        assertFalse(startXml.contains("<Name>W21</Name>"))
        assertFalse(resultXml.contains("<Name>W21</Name>"))
    }

    @Test
    fun explicitProtectedStartListExportDoesNotFallBackToPublicCourseStats() {
        val xml = IofXmlExports.startList(
            raceData(),
            protectedCourseInfoByCategoryId = emptyMap()
        )

        assertFalse(xml.contains("<Length>5200</Length>"))
        assertFalse(xml.contains("<Climb>120</Climb>"))
        assertFalse(xml.contains("<Course>"))
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
        assertFalse(xml.contains("<Family>NoTime</Family>"))
        assertFalse(xml.contains("<Status>Active</Status>"))
    }

    @Test
    fun resultListSortsWomenBeforeMenRegardlessOfStoredOrder() {
        val base = raceData(includeSecondCategory = true, includeReadout = true)
        val withBothResults = base.copy(
            competitorData = base.competitorData.map { competitorData ->
                if (competitorData.readoutData == null) {
                    competitorData.copy(
                        readoutData = readout(ResultStatus.OK).let { readout ->
                            readout.copy(result = readout.result.copy(id = "result-w21"))
                        }
                    )
                } else {
                    competitorData
                }
            }
        )

        val xml = IofXmlExports.resultList(withBothResults)

        assertTrue(xml.indexOf("<Name>W21</Name>") < xml.indexOf("<Name>M21</Name>"))
    }

    @Test
    fun officialIofResultListIsCompleteAndIncludesBibNumber() {
        val xml = IofXmlExports.resultList(
            raceData(includeReadout = true),
            publicationStatus = PublicResultsPublicationStatus.OFFICIAL
        )

        assertTrue(xml.contains("status=\"Complete\""))
        assertTrue(xml.contains("<BibNumber>1007</BibNumber>"))
    }

    @Test
    fun resultListOnlyWritesPositionForOkResults() {
        val xml = IofXmlExports.resultList(raceData(includeReadout = true, resultStatus = ResultStatus.MISPUNCHED))

        assertTrue(xml.contains("<Status>MissingPunch</Status>"))
        assertFalse(xml.contains("<Position>"))
    }

    @Test
    fun exportedStartListCanBeImportedBySharedIofImporter() {
        val xml = IofXmlExports.startList(raceData(includeSecondCategory = true))

        val imported = IofXmlImports.startList(xml)

        assertEquals(emptyList(), imported.unsupportedItems)
        assertEquals("IOF & Start Race", imported.parsedData.eventName)
        assertEquals("2026-06-01", imported.parsedData.startDate)
        assertEquals("10:00:00", imported.parsedData.startTime)
        assertEquals(listOf("M21", "W21"), imported.parsedData.entries.map { it.className })
        assertEquals(listOf("Runner", "NoTime"), imported.parsedData.entries.map { it.person.familyName })
        assertEquals(listOf("Alice", "Bob"), imported.parsedData.entries.map { it.person.givenName })
        assertEquals(listOf("OK001", null), imported.parsedData.entries.map { it.person.personId })
        assertEquals(listOf("1007", "1008"), imported.parsedData.entries.map { it.bibNumber })
        assertEquals(listOf(123456, null), imported.parsedData.entries.map { it.controlCard })
        assertEquals(listOf(600L, 0L), imported.parsedData.entries.map { it.relativeStartTimeSeconds })
    }

    @Test
    fun exportedResultListCanBeImportedBySharedIofImporter() {
        val xml = IofXmlExports.resultList(raceData(includeSecondCategory = true, includeReadout = true))

        val imported = IofXmlImports.resultList(xml)
        val finished = imported.parsedData.entries.first { it.person.familyName == "Runner" }

        assertEquals(emptyList(), imported.unsupportedItems)
        assertEquals("IOF & Start Race", imported.parsedData.eventName)
        assertEquals("2026-06-01", imported.parsedData.startDate)
        assertEquals("10:00:00", imported.parsedData.startTime)
        assertEquals("M21", finished.className)
        assertEquals("OK001", finished.person.personId)
        assertEquals(123456, finished.controlCard)
        assertEquals("2026-06-01T10:00:00", finished.startTimeIso)
        assertEquals("2026-06-01T10:45:00", finished.finishTimeIso)
        assertEquals(2700, finished.timeSeconds)
        assertEquals(1, finished.position)
        assertEquals("OK", finished.status)
        assertEquals(listOf(31, 32), finished.splitControls)
        assertEquals(listOf(600L, 1500L), finished.splitTimes.map { it.timeSeconds })
        assertEquals(1, imported.parsedData.entries.size)
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
                    bibNumber = "1007",
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
                        bibNumber = "1008",
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

    private fun raceDataWithCourseControls(): EventRaceData {
        val raceData = raceData(includeSecondCategory = true)
        val categories = raceData.categories.map { categoryData ->
            categoryData.copy(
                controlPoints = when (categoryData.category.name) {
                    "M21" -> listOf(
                        courseControl(categoryData.category.id, 31, order = 1),
                        courseControl(categoryData.category.id, 32, order = 2)
                    )
                    "W21" -> listOf(
                        courseControl(categoryData.category.id, 41, order = 1),
                        courseControl(categoryData.category.id, 42, order = 2)
                    )
                    else -> emptyList()
                }
            )
        }
        return raceData.copy(categories = categories)
    }

    private fun sprintRaceDataWithCourseControls(): EventRaceData {
        val original = raceData()
        val base = original.copy(race = original.race.copy(raceType = RaceType.SPRINT))
        val category = base.categories.first().category
        val controls = listOf(
            eventControl(base.race.id, "slow", "Slow 1", "1", 31, ControlPointType.CONTROL),
            eventControl(base.race.id, "spectator", "Spectator", "Spectator", 137, ControlPointType.SEPARATOR),
            eventControl(base.race.id, "fast", "Fast 1", "1F", 41, ControlPointType.CONTROL),
            eventControl(base.race.id, "beacon", "Beacon", "B", 136, ControlPointType.BEACON)
        )
        val controlPoints = controls.mapIndexed { index, control ->
            EventControlPoint(
                id = "point-${control.id}",
                categoryId = category.id,
                siCode = control.siCode,
                type = control.type,
                order = index + 1,
                controlId = control.id
            )
        }
        return base.copy(
            controls = controls,
            categories = listOf(
                EventCategoryData(category, controlPoints = controlPoints, competitors = base.categories.first().competitors)
            )
        )
    }

    private fun eventControl(
        raceId: String,
        id: String,
        label: String,
        publicLabel: String,
        siCode: Int,
        type: ControlPointType
    ): EventControl = EventControl(
        id = id,
        raceId = raceId,
        label = label,
        siCode = siCode,
        type = type,
        publicLabel = publicLabel
    )

    private fun protectedObject(
        id: String,
        label: String,
        type: ProtectedCourseObjectType,
        latitude: Double,
        longitude: Double,
        elevationMeters: Double
    ): ProtectedCourseObjectPoint = ProtectedCourseObjectPoint(
        id = id,
        label = label,
        type = type,
        latitude = latitude,
        longitude = longitude,
        elevationMeters = elevationMeters
    )

    private fun courseControl(categoryId: String, siCode: Int, order: Int): EventControlPoint =
        EventControlPoint(
            id = "control-$categoryId-$siCode",
            categoryId = categoryId,
            siCode = siCode,
            type = ControlPointType.CONTROL,
            order = order
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
        bibNumber: String = "",
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
            bibNumber = bibNumber,
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
