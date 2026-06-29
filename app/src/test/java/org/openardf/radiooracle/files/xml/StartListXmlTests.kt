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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

import org.openardf.radiooracle.backend.files.constants.DataType
import org.openardf.radiooracle.backend.files.processors.IofXmlProcessor
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.room.entity.Category
import org.openardf.radiooracle.backend.room.entity.Competitor
import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorCategory
import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorData
import org.openardf.radiooracle.backend.room.entity.embeddeds.CategoryData
import org.openardf.radiooracle.backend.room.entity.embeddeds.RaceData
import org.openardf.radiooracle.backend.room.enums.ResultStatus
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.xmlunit.builder.DiffBuilder
import org.xmlunit.diff.DefaultNodeMatcher
import org.xmlunit.diff.ElementSelectors


@RunWith(RobolectricTestRunner::class)
class StartListXmlTests {

    @Test
    fun testExportStartList() = runBlocking {
        // Prepare a race with a fixed start date/time
        val raceStart = LocalDateTime.of(2023, 6, 15, 9, 30, 0)
        val race = Race(
            UUID.randomUUID(),
            "Test Race",
            "",
            raceStart,
            org.openardf.radiooracle.backend.room.enums.RaceType.CLASSIC,
            org.openardf.radiooracle.backend.room.enums.RaceLevel.PRACTICE,
            org.openardf.radiooracle.backend.room.enums.RaceBand.M80,
            Duration.ZERO
        )

        // Prepare category
        val category = Category(
            UUID.randomUUID(),
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

        // Prepare two competitors with relative start times
        val comp1 = Competitor(
            UUID.randomUUID(),
            race.id,
            category.id,
            "Jan",
            "Novak",
            "Club A",
            "IDX1",
            true,
            1990,
            111,
            false,
            1,
            Duration.ofSeconds(0)
        )
        val comp2 = Competitor(
            UUID.randomUUID(),
            race.id,
            category.id,
            "Petr",
            "Svoboda",
            "Club B",
            "IDX2",
            true,
            1992,
            222,
            false,
            2,
            Duration.ofSeconds(60)
        )

        val catData = CategoryData(category, emptyList(), listOf(comp1, comp2))

        val out = ByteArrayOutputStream()

        val dataProcessor: DataProcessor = mock()
        `when`(dataProcessor.getAppVersion()).thenReturn("0.0.1")

        // Call the suspend export function
        IofXmlProcessor.exportStartList(out, race, listOf(catData), dataProcessor)

        val xml = out.toString()
        val stream =
            this::class.java.classLoader?.getResourceAsStream("xml/xml_startlist_example.xml")!!
        val valid = stream.bufferedReader().use { it.readText() }

        // assertEquals(valid, xml)
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
    fun testImportStartListUpdatesMatchedCompetitorStartTime() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val race = Race(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "Test Race",
            "",
            LocalDateTime.of(2023, 6, 15, 9, 30, 0),
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
            "Jan",
            "Novak",
            "Club A",
            "IDX1",
            true,
            1990,
            111,
            false,
            1,
            null
        )
        val competitorData = CompetitorData(
            CompetitorCategory(competitor, category),
            readoutData = null
        )
        val raceData = RaceData(
            race = race,
            categories = listOf(CategoryData(category, emptyList(), listOf(competitor))),
            aliases = emptyList(),
            competitorData = listOf(competitorData),
            unmatchedReadoutData = emptyList()
        )
        val dataProcessor: DataProcessor = mock()
        `when`(dataProcessor.getContext()).thenReturn(context)
        `when`(dataProcessor.getRaceData(race.id)).thenReturn(raceData)

        val xml = """
            <StartList xmlns="http://www.orienteering.org/datastandard/3.0" iofVersion="3.0">
              <Event>
                <Name>Test Race</Name>
                <StartTime>
                  <Date>2023-06-15</Date>
                  <Time>09:30:00</Time>
                </StartTime>
              </Event>
              <ClassStart>
                <Class><Name>M21</Name></Class>
                <PersonStart>
                  <Person>
                    <Id type="CZE">IDX1</Id>
                    <Name><Family>Novak</Family><Given>Jan</Given></Name>
                  </Person>
                  <Start>
                    <BibNumber>1</BibNumber>
                    <StartTime>2023-06-15T09:42:00</StartTime>
                    <ControlCard>111</ControlCard>
                  </Start>
                </PersonStart>
              </ClassStart>
            </StartList>
        """.trimIndent()

        val wrapper = IofXmlProcessor.importData(
            xml.byteInputStream(),
            DataType.COMPETITOR_STARTS,
            race,
            dataProcessor
        )

        assertTrue(wrapper.invalidLines.isEmpty())
        assertEquals(Duration.ofMinutes(12), wrapper.competitorCategories.single().competitor.drawnRelativeStartTime)
    }
}
