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
 * DEALINGS IN THE SOFTWARE.
 */

package org.openardf.radiooracle.shared.files

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.EventRace

class IofXmlImportsTest {

    @Test
    fun parsesCourseDataPreviewAndReportsUnsupportedValidContent() {
        val result = IofXmlImports.courseData(courseDataXml(), race())

        assertEquals("Example event", result.parsedData.eventName)
        assertEquals("2011-07-30", result.parsedData.startDate)
        assertEquals("10:00:00", result.parsedData.startTime)
        assertEquals(listOf("A"), result.parsedData.categories.map { it.category.name })
        assertEquals(listOf(31, 32), result.parsedData.categories.single().controlPoints.map { it.siCode })
        assertTrue(result.unsupportedItems.any { it.location.endsWith("/CourseFamily") })
        assertTrue(result.unsupportedItems.any { it.reason.contains("Start controls") })
        assertTrue(result.unsupportedItems.any { it.reason.contains("Finish controls") })
    }

    @Test
    fun validatesCourseDataBeforePreview() {
        val result = IofXmlImports.validatedCourseData(courseDataXml(), localIofXsd(), race())

        assertEquals("Example event", result.parsedData.eventName)
        assertEquals(listOf("A"), result.parsedData.categories.map { it.category.name })
    }

    @Test
    fun parsesStartListPreviewAndReportsUnsupportedTeamStarts() {
        val result = IofXmlImports.startList(startListXml())
        val entry = result.parsedData.entries.single()

        assertEquals("Start event", result.parsedData.eventName)
        assertEquals("2026-06-29", result.parsedData.startDate)
        assertEquals("09:00:00", result.parsedData.startTime)
        assertEquals("M21", entry.className)
        assertEquals("CZE001", entry.person.personId)
        assertEquals("CZE", entry.person.personIdType)
        assertEquals("Runner", entry.person.familyName)
        assertEquals("Alice", entry.person.givenName)
        assertEquals("OK Test", entry.person.organisationName)
        assertEquals("17", entry.bibNumber)
        assertEquals(123456, entry.controlCard)
        assertEquals("2026-06-29T09:12:00", entry.startTimeIso)
        assertEquals(720, entry.relativeStartTimeSeconds)
        assertTrue(result.unsupportedItems.any { it.location == "/StartList/TeamStart[1]" })
    }

    @Test
    fun validatedStartListRejectsSchemaInvalidLegacyStartNumber() {
        val exception = assertFailsWith<IofXmlImportException> {
            IofXmlImports.validatedStartList(
                startListXml().replace("<BibNumber>17</BibNumber>", "<StartNumber>17</StartNumber>"),
                localIofXsd()
            )
        }

        assertTrue(exception.message!!.startsWith("Invalid IOF XML:"))
        assertTrue(exception.message!!.contains("StartNumber"))
    }

    @Test
    fun parsesResultListPreviewAndReportsUnsupportedTeamResults() {
        val result = IofXmlImports.resultList(resultListXml())
        val entry = result.parsedData.entries.single()

        assertEquals("Result event", result.parsedData.eventName)
        assertEquals("W21", entry.className)
        assertEquals("US001", entry.person.personId)
        assertEquals("Example", entry.person.familyName)
        assertEquals("Ada", entry.person.givenName)
        assertEquals(234567, entry.controlCard)
        assertEquals("2026-06-29T09:02:00", entry.startTimeIso)
        assertEquals("2026-06-29T10:17:00", entry.finishTimeIso)
        assertEquals(4500, entry.timeSeconds)
        assertEquals(1, entry.position)
        assertEquals("OK", entry.status)
        assertEquals(listOf(31, 32), entry.splitControls)
        assertEquals(listOf(1200L, 2100L), entry.splitTimes.map { it.timeSeconds })
        assertTrue(result.unsupportedItems.any { it.location == "/ResultList/TeamResult[1]" })
    }

    @Test
    fun parsesIofRepositoryCourseDataExample() {
        val xml = iofExample("CourseData_Individual_Step2.xml") ?: return

        val result = IofXmlImports.courseData(xml, race())

        assertEquals("Example event", result.parsedData.eventName)
        assertEquals(listOf("A", "B"), result.parsedData.categories.map { it.category.name })
        assertEquals(listOf(31, 32, 33), result.parsedData.categories.first().controlPoints.take(3).map { it.siCode })
        assertTrue(result.unsupportedItems.any { it.reason.contains("Course family") })
        assertTrue(result.unsupportedItems.any { it.reason.contains("Race-level control definitions") })
        assertTrue(result.unsupportedItems.any { it.reason.contains("Class-course assignments") })
        assertTrue(result.unsupportedItems.any { it.reason.contains("Course leg lengths") })
    }

    @Test
    fun parsesIofRepositoryPersonCourseAssignmentExample() {
        val xml = iofExample("CourseData_Individual_Step4.xml") ?: return

        val result = IofXmlImports.courseData(xml, race())

        assertEquals(listOf("A", "B"), result.parsedData.categories.map { it.category.name })
        assertTrue(result.unsupportedItems.any { it.reason.contains("Person-course assignments") })
        assertTrue(result.unsupportedItems.any { it.severity == IofXmlImportSeverity.UNSUPPORTED })
    }

    @Test
    fun parsesIofRepositoryRelayCourseAssignmentExample() {
        val xml = iofExample("CourseData_Relay_Step4.xml") ?: return

        val result = IofXmlImports.courseData(xml, race())

        assertTrue(result.parsedData.categories.map { it.category.name }.contains("A"))
        assertTrue(result.unsupportedItems.any { it.reason.contains("Team and relay course assignments") })
        assertTrue(result.unsupportedItems.any { it.severity == IofXmlImportSeverity.UNSUPPORTED })
    }

    @Test
    fun parsesIofRepositoryStartListExample() {
        val xml = iofExample("StartList_Individual_Step3.xml") ?: return

        val result = IofXmlImports.startList(xml)

        assertEquals("Example event", result.parsedData.eventName)
        assertEquals(2, result.parsedData.entries.size)
        assertEquals("Men Open", result.parsedData.entries.first().className)
        assertEquals(0, result.parsedData.entries.first().relativeStartTimeSeconds)
        assertEquals(180, result.parsedData.entries[1].relativeStartTimeSeconds)
    }

    @Test
    fun parsesIofRepositoryResultListExample() {
        val xml = iofExample("ResultList1.xml") ?: return

        val result = IofXmlImports.resultList(xml)

        assertEquals("Example event", result.parsedData.eventName)
        assertTrue(result.parsedData.entries.isNotEmpty())
        assertEquals("Men Elite", result.parsedData.entries.first().className)
        assertEquals("OK", result.parsedData.entries.first().status)
        assertTrue(result.parsedData.entries.first().splitControls.contains(31))
    }

    @Test
    fun malformedXmlFailsClearly() {
        val exception = assertFailsWith<IofXmlImportException> {
            IofXmlImports.courseData("<CourseData iofVersion=\"3.0\"><RaceCourseData>", race())
        }

        assertTrue(exception.message!!.contains("Unterminated XML element"))
    }

    @Test
    fun wrongIofVersionFailsClearly() {
        val exception = assertFailsWith<IofXmlImportException> {
            IofXmlImports.rootMessageType("<CourseData iofVersion=\"2.0.3\"/>")
        }

        assertEquals("Unsupported IOF XML version: 2.0.3. Expected 3.0.", exception.message)
    }

    @Test
    fun unsupportedRootFailsClearly() {
        val exception = assertFailsWith<IofXmlImportException> {
            IofXmlImports.rootMessageType("<NotIof iofVersion=\"3.0\"/>")
        }

        assertEquals("Unsupported IOF XML root element: NotIof", exception.message)
    }

    @Test
    fun wrongSupportedRootForCourseImportFailsClearly() {
        val exception = assertFailsWith<IofXmlImportException> {
            IofXmlImports.courseData("<StartList iofVersion=\"3.0\"/>", race())
        }

        assertEquals("Expected IOF CourseData XML but found StartList.", exception.message)
    }

    @Test
    fun validatedRootMessageTypePreservesClearVersionFailure() {
        val exception = assertFailsWith<IofXmlImportException> {
            IofXmlImports.validatedRootMessageType("<CourseData iofVersion=\"2.0.3\"/>", localIofXsd())
        }

        assertEquals("Unsupported IOF XML version: 2.0.3. Expected 3.0.", exception.message)
    }

    private fun courseDataXml(): String = """
        <CourseData xmlns="http://www.orienteering.org/datastandard/3.0" iofVersion="3.0">
          <Event>
            <Name>Example event</Name>
            <StartTime>
              <Date>2011-07-30</Date>
              <Time>10:00:00</Time>
            </StartTime>
          </Event>
          <RaceCourseData>
            <Course>
              <Name>A</Name>
              <CourseFamily>Open long</CourseFamily>
              <Length>2960</Length>
              <Climb>95</Climb>
              <CourseControl type="Start"><Control>S</Control></CourseControl>
              <CourseControl type="Control"><Control>31</Control></CourseControl>
              <CourseControl type="Control"><Control>32</Control></CourseControl>
              <CourseControl type="Finish"><Control>F</Control></CourseControl>
            </Course>
          </RaceCourseData>
        </CourseData>
    """.trimIndent()

    private fun startListXml(): String = """
        <StartList xmlns="http://www.orienteering.org/datastandard/3.0" iofVersion="3.0">
          <Event>
            <Name>Start event</Name>
            <StartTime>
              <Date>2026-06-29</Date>
              <Time>09:00:00</Time>
            </StartTime>
          </Event>
          <ClassStart>
            <Class><Name>M21</Name></Class>
            <PersonStart>
              <Person>
                <Id type="CZE">CZE001</Id>
                <Name>
                  <Family>Runner</Family>
                  <Given>Alice</Given>
                </Name>
              </Person>
              <Organisation><Name>OK Test</Name></Organisation>
              <Start>
                <BibNumber>17</BibNumber>
                <StartTime>2026-06-29T09:12:00</StartTime>
                <ControlCard>123456</ControlCard>
              </Start>
            </PersonStart>
          </ClassStart>
          <TeamStart/>
        </StartList>
    """.trimIndent()

    private fun resultListXml(): String = """
        <ResultList xmlns="http://www.orienteering.org/datastandard/3.0" iofVersion="3.0">
          <Event>
            <Name>Result event</Name>
            <StartTime>
              <Date>2026-06-29</Date>
              <Time>09:00:00</Time>
            </StartTime>
          </Event>
          <ClassResult>
            <Class><Name>W21</Name></Class>
            <PersonResult>
              <Person>
                <Id type="USA">US001</Id>
                <Name>
                  <Family>Example</Family>
                  <Given>Ada</Given>
                </Name>
              </Person>
              <Result>
                <ControlCard>234567</ControlCard>
                <StartTime>2026-06-29T09:02:00</StartTime>
                <FinishTime>2026-06-29T10:17:00</FinishTime>
                <Time>4500</Time>
                <Position>1</Position>
                <Status>OK</Status>
                <SplitTime>
                  <ControlCode>31</ControlCode>
                  <Time>1200</Time>
                </SplitTime>
                <SplitTime>
                  <ControlCode>32</ControlCode>
                  <Time>2100</Time>
                </SplitTime>
              </Result>
            </PersonResult>
          </ClassResult>
          <TeamResult/>
        </ResultList>
    """.trimIndent()

    private fun iofExample(fileName: String): String? {
        val file = File("$IOF_EXAMPLES_PATH/$fileName")
        return if (file.isFile) file.readText() else null
    }

    private fun localIofXsd(): String =
        Files.readString(iofSchemaPath())

    private fun iofSchemaPath(): Path {
        val configuredPath = sequenceOf(
            System.getProperty(IOF_SCHEMA_PROPERTY),
            System.getenv(IOF_SCHEMA_ENV)
        )
            .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
            .firstOrNull()
            ?.let(Paths::get)
        val workingDirectory = Paths.get(System.getProperty("user.dir"))
        val candidates = if (configuredPath != null) {
            listOf(configuredPath)
        } else {
            listOf(
                workingDirectory.resolve("../IOF-XML-datastandard-v3/IOF.xsd"),
                workingDirectory.resolve("../../IOF-XML-datastandard-v3/IOF.xsd")
            )
        }
        return candidates.firstOrNull { Files.isRegularFile(it) }
            ?: throw AssertionError(
                "IOF XML 3.0 schema is required for this test. " +
                    "Set -PiofSchemaPath=/path/to/IOF.xsd or IOF_SCHEMA_PATH=/path/to/IOF.xsd. " +
                    "Checked: ${candidates.joinToString { it.toAbsolutePath().normalize().toString() }}"
            )
    }

    private fun race(): EventRace =
        EventRace(
            id = "race",
            name = "Race",
            apiKey = "",
            startDateTimeIso = "2026-06-29T09:00:00",
            raceType = RaceType.CLASSIC,
            raceLevel = RaceLevel.PRACTICE,
            raceBand = RaceBand.M80,
            timeLimitSeconds = 0
        )

    private companion object {
        const val IOF_EXAMPLES_PATH = "/Users/charlesscharlau/Documents/GitHub/IOF-XML-datastandard-v3/examples"
        const val IOF_SCHEMA_PROPERTY = "iof.schema.path"
        const val IOF_SCHEMA_ENV = "IOF_SCHEMA_PATH"
    }
}
