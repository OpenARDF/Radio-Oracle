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

package org.openardf.radiooracle.files.xml

import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.files.constants.DataType
import org.openardf.radiooracle.backend.files.processors.IofXmlProcessor
import org.openardf.radiooracle.backend.results.ResultsProcessor
import org.openardf.radiooracle.backend.results.ResultsProcessor.toResultWrappers
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
import org.openardf.radiooracle.backend.room.enums.ControlPointType
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.backend.sportident.SITime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import org.xmlunit.builder.DiffBuilder
import org.xmlunit.diff.DefaultNodeMatcher
import org.xmlunit.diff.ElementSelectors

@RunWith(RobolectricTestRunner::class)
class ResultXmlTests {
    @Test
    fun testResultExport() = runTest {
        val dataProcessor = mock(DataProcessor::class.java)
        `when`(dataProcessor.getAppVersion()).thenReturn("0.0.1")

        val race = Race()
        race.name = "TEST"
        race.startDateTime = LocalDateTime.of(2025, 1, 1, 10, 0, 0)

        val result = Result()
        result.startTime = SITime(LocalTime.of(13, 0, 0))
        result.finishTime = SITime(LocalTime.of(14, 15, 0))
        result.resultStatus = ResultStatus.OK
        result.runTime = Duration.ofMinutes(75)
        result.readoutTime = LocalDateTime.of(2025, 9, 25, 14, 18, 24)

        val punches = arrayListOf(
            Punch(13, SITime(LocalTime.of(13, 0, 0)), SIRecordType.START, 1),
            Punch(31, SITime(LocalTime.of(13, 35, 0)), SIRecordType.CONTROL, 1),
            Punch(32, SITime(LocalTime.of(13, 43, 11)), SIRecordType.CONTROL, 1),
            Punch(33, SITime(LocalTime.of(14, 5, 50)), SIRecordType.CONTROL, 1),
            Punch(34, SITime(LocalTime.of(14, 10, 22)), SIRecordType.CONTROL, 1),
        )
        ResultsProcessor.calculateSplits(punches)

        val ap = punches.mapIndexed { index, punch ->
            AliasPunch(
                punch,
                Alias(punch.siCode, index.toString())
            )
        }
        val readoutData = ReadoutData(result, ap)

        val comp = Competitor()
        val compData = listOf(
            CompetitorData(
                CompetitorCategory(comp, Category("A")),
                readoutData
            )
        )

        val out = ByteArrayOutputStream()
        IofXmlProcessor.exportResults(out, race, compData.toResultWrappers(), dataProcessor)
        val xml = out.toString("UTF-8")

        val stream =
            this::class.java.classLoader?.getResourceAsStream("xml/xml_results_example.xml")!!
        val valid = stream.bufferedReader().use { it.readText() }

       // assertEquals(xml, "") // For debug

        //Use XMLUnit to compare structure, ignoring whitespace and attribute order
        val diff = DiffBuilder.compare(valid)
            .withTest(xml)
            .ignoreWhitespace()
            .ignoreComments()
            .withNodeMatcher(DefaultNodeMatcher(ElementSelectors.byNameAndAllAttributes))
            .checkForSimilar()
            .build()

        assertEquals("XMLs are different: $diff", false, diff.hasDifferences())
    }

    @Test
    fun testResultImportCreatesMatchedReadout() = runTest {
        val race = Race()
        race.id = UUID.fromString("00000000-0000-0000-0000-000000000001")
        race.name = "Test Race"
        race.startDateTime = LocalDateTime.of(2025, 1, 1, 10, 0, 0)

        val category = Category("M21")
        category.id = UUID.fromString("00000000-0000-0000-0000-000000000002")
        category.raceId = race.id
        val controlPoint = ControlPoint(
            UUID.fromString("00000000-0000-0000-0000-000000000004"),
            category.id,
            31,
            ControlPointType.CONTROL,
            1
        )

        val competitor = Competitor()
        competitor.id = UUID.fromString("00000000-0000-0000-0000-000000000003")
        competitor.raceId = race.id
        competitor.categoryId = category.id
        competitor.firstName = "Jan"
        competitor.lastName = "Novak"
        competitor.index = "IDX1"
        competitor.siNumber = 111

        val raceData = RaceData(
            race = race,
            categories = listOf(CategoryData(category, listOf(controlPoint), listOf(competitor))),
            aliases = emptyList(),
            competitorData = listOf(CompetitorData(CompetitorCategory(competitor, category), null)),
            unmatchedReadoutData = emptyList()
        )
        val dataProcessor = mock(DataProcessor::class.java)
        `when`(dataProcessor.getContext()).thenReturn(RuntimeEnvironment.getApplication())
        `when`(dataProcessor.getRaceData(race.id)).thenReturn(raceData)

        val xml = """
            <ResultList xmlns="http://www.orienteering.org/datastandard/3.0" iofVersion="3.0">
              <Event>
                <Name>Test Race</Name>
                <StartTime>
                  <Date>2025-01-01</Date>
                  <Time>10:00:00</Time>
                </StartTime>
              </Event>
              <ClassResult>
                <Class><Name>M21</Name></Class>
                <PersonResult>
                  <Person>
                    <Id type="CZE">IDX1</Id>
                    <Name><Family>Novak</Family><Given>Jan</Given></Name>
                  </Person>
                  <Result>
                    <StartTime>2025-01-01T10:00:00</StartTime>
                    <FinishTime>2025-01-01T10:12:00</FinishTime>
                    <Time>720</Time>
                    <Status>OK</Status>
                    <SplitTime>
                      <ControlCode>31</ControlCode>
                      <Time>300</Time>
                    </SplitTime>
                    <ControlCard>111</ControlCard>
                  </Result>
                </PersonResult>
              </ClassResult>
            </ResultList>
        """.trimIndent()

        val wrapper = IofXmlProcessor.importData(
            xml.byteInputStream(),
            DataType.RESULTS_LIVE,
            race,
            dataProcessor
        )

        assertEquals(1, wrapper.readoutData.size)
        val readout = wrapper.readoutData.single()
        assertEquals(competitor.id, readout.result.competitorId)
        assertEquals(111, readout.result.siNumber)
        assertEquals(ResultStatus.OK, readout.result.resultStatus)
        assertEquals(Duration.ofSeconds(720), readout.result.runTime)
        assertEquals(3, readout.punches.size)
        assertEquals(SIRecordType.START, readout.punches[0].punch.punchType)
        assertEquals(31, readout.punches[1].punch.siCode)
        assertEquals(Duration.ofSeconds(300), readout.punches[1].punch.split)
        assertEquals(SIRecordType.FINISH, readout.punches[2].punch.punchType)
    }
}
