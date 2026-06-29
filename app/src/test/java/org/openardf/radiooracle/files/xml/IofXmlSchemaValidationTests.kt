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

package org.openardf.radiooracle.files.xml

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.files.processors.IofXmlProcessor
import org.openardf.radiooracle.backend.results.ResultsProcessor
import org.openardf.radiooracle.backend.results.ResultsProcessor.toResultWrappers
import org.openardf.radiooracle.backend.room.entity.Alias
import org.openardf.radiooracle.backend.room.entity.Category
import org.openardf.radiooracle.backend.room.entity.Competitor
import org.openardf.radiooracle.backend.room.entity.Punch
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.room.entity.Result
import org.openardf.radiooracle.backend.room.entity.embeddeds.AliasPunch
import org.openardf.radiooracle.backend.room.entity.embeddeds.CategoryData
import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorCategory
import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorData
import org.openardf.radiooracle.backend.room.entity.embeddeds.ReadoutData
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.backend.sportident.SITime
import org.robolectric.RobolectricTestRunner
import org.xml.sax.SAXException
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.Schema
import javax.xml.validation.SchemaFactory

@RunWith(RobolectricTestRunner::class)
class IofXmlSchemaValidationTests {

    @Test
    fun androidStartListExportValidatesAgainstIof30Schema() = runBlocking {
        validateIofXml(startListXml())
    }

    @Test
    fun androidResultListExportValidatesAgainstIof30Schema() = runTest {
        validateIofXml(resultListXml())
    }

    @Test
    fun legacyAndroidStartNumberShapeFailsIof30Schema() {
        val legacyXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <StartList xmlns="http://www.orienteering.org/datastandard/3.0" iofVersion="3.0" creator="Radio-Oracle test">
              <Event>
                <Name>Legacy shape</Name>
                <StartTime>
                  <Date>2026-06-29</Date>
                  <Time>09:00:00</Time>
                </StartTime>
              </Event>
              <ClassStart>
                <Class>
                  <Name>M21</Name>
                </Class>
                <PersonStart>
                  <Person>
                    <Name>
                      <Family>Example</Family>
                      <Given>Ada</Given>
                    </Name>
                  </Person>
                  <Start>
                    <StartNumber>1</StartNumber>
                    <StartTime>2026-06-29T09:00:00</StartTime>
                  </Start>
                </PersonStart>
              </ClassStart>
            </StartList>
        """.trimIndent()

        val validator = schema().newValidator()
        assertThrows(SAXException::class.java) {
            validator.validate(StreamSource(StringReader(legacyXml)))
        }
    }

    private suspend fun startListXml(): String {
        val raceStart = LocalDateTime.of(2026, 6, 29, 9, 0, 0)
        val race = Race(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "Schema Race",
            "",
            raceStart,
            org.openardf.radiooracle.backend.room.enums.RaceType.CLASSIC,
            org.openardf.radiooracle.backend.room.enums.RaceLevel.PRACTICE,
            org.openardf.radiooracle.backend.room.enums.RaceBand.M80,
            Duration.ZERO
        )
        val category = Category(
            UUID.fromString("00000000-0000-0000-0000-000000000002"),
            race.id,
            "M21",
            true,
            null,
            5200,
            120,
            0,
            false,
            null,
            null,
            null,
            ""
        )
        val competitor = Competitor(
            UUID.fromString("00000000-0000-0000-0000-000000000003"),
            race.id,
            category.id,
            "Ada",
            "Example",
            "Club",
            "IDX1",
            true,
            1990,
            123456,
            false,
            7,
            Duration.ofMinutes(2)
        )
        val out = ByteArrayOutputStream()
        IofXmlProcessor.exportStartList(out, race, listOf(CategoryData(category, emptyList(), listOf(competitor))), dataProcessor())
        return out.toString("UTF-8")
    }

    private suspend fun resultListXml(): String {
        val race = Race()
        race.name = "Schema Race"
        race.startDateTime = LocalDateTime.of(2026, 6, 29, 9, 0, 0)

        val result = Result()
        result.startTime = SITime(LocalTime.of(9, 2, 0))
        result.finishTime = SITime(LocalTime.of(10, 17, 0))
        result.resultStatus = ResultStatus.OK
        result.runTime = Duration.ofMinutes(75)
        result.readoutTime = LocalDateTime.of(2026, 6, 29, 10, 18, 24)

        val punches = arrayListOf(
            Punch(31, SITime(LocalTime.of(9, 35, 0)), SIRecordType.CONTROL, 1),
            Punch(32, SITime(LocalTime.of(9, 43, 11)), SIRecordType.CONTROL, 1),
        )
        ResultsProcessor.calculateSplits(punches)
        val readoutData = ReadoutData(
            result = result,
            punches = punches.mapIndexed { index, punch ->
                AliasPunch(punch, Alias(punch.siCode, index.toString()))
            }
        )

        val category = Category("M21")
        val competitor = Competitor()
        competitor.firstName = "Ada"
        competitor.lastName = "Example"
        competitor.categoryId = category.id
        val competitorData = listOf(
            CompetitorData(
                CompetitorCategory(competitor, category),
                readoutData
            )
        )
        val out = ByteArrayOutputStream()
        IofXmlProcessor.exportResults(out, race, competitorData.toResultWrappers(), dataProcessor())
        return out.toString("UTF-8")
    }

    private fun validateIofXml(xml: String) {
        schema().newValidator().validate(StreamSource(StringReader(xml)))
    }

    private fun schema(): Schema {
        val schemaFile = iofSchemaPath().toFile()
        return SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
            .newSchema(schemaFile)
    }

    private fun iofSchemaPath(): Path {
        val configuredPath = configuredIofSchemaPath()
        val candidates = if (configuredPath != null) {
            listOf(configuredPath)
        } else {
            defaultIofSchemaPathCandidates()
        }
        return candidates.firstOrNull { Files.isRegularFile(it) }
            ?: throw AssertionError(
                "IOF XML 3.0 schema is required for this test. " +
                    "Set -PiofSchemaPath=/path/to/IOF.xsd or IOF_SCHEMA_PATH=/path/to/IOF.xsd. " +
                    "Checked: ${candidates.joinToString { it.toAbsolutePath().normalize().toString() }}"
            )
    }

    private fun configuredIofSchemaPath(): Path? =
        sequenceOf(
            System.getProperty(IOF_SCHEMA_PROPERTY),
            System.getenv(IOF_SCHEMA_ENV)
        )
            .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
            .firstOrNull()
            ?.let(Paths::get)

    private fun defaultIofSchemaPathCandidates(): List<Path> {
        val workingDirectory = Paths.get(System.getProperty("user.dir"))
        return listOf(
            workingDirectory.resolve("../IOF-XML-datastandard-v3/IOF.xsd"),
            workingDirectory.resolve("../../IOF-XML-datastandard-v3/IOF.xsd")
        )
    }

    private fun dataProcessor(): DataProcessor {
        val dataProcessor = mock(DataProcessor::class.java)
        `when`(dataProcessor.getAppVersion()).thenReturn("0.0.1")
        return dataProcessor
    }

    private companion object {
        const val IOF_SCHEMA_PROPERTY = "iof.schema.path"
        const val IOF_SCHEMA_ENV = "IOF_SCHEMA_PATH"
    }
}
