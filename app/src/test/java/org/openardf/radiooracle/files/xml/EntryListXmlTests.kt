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

import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.files.constants.DataType
import org.openardf.radiooracle.backend.files.processors.IofXmlProcessor
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.room.entity.embeddeds.CategoryData
import org.openardf.radiooracle.backend.room.enums.RaceBand
import org.openardf.radiooracle.backend.room.enums.RaceLevel
import org.openardf.radiooracle.backend.room.enums.RaceType
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class EntryListXmlTests {

    @Test
    fun testImportEntryListCreatesCompetitorsAndCategories() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val race = Race(
            UUID.fromString("00000000-0000-0000-0000-000000000101"),
            "Entry Race",
            "",
            LocalDateTime.of(2026, 6, 29, 9, 0, 0),
            RaceType.CLASSIC,
            RaceLevel.PRACTICE,
            RaceBand.M80,
            Duration.ZERO
        )
        val dataProcessor: DataProcessor = mock()
        `when`(dataProcessor.getContext()).thenReturn(context)
        `when`(dataProcessor.getCategoryDataForRace(race.id)).thenReturn(emptyList<CategoryData>())
        `when`(dataProcessor.getHighestCategoryOrder(race.id)).thenReturn(0)
        `when`(dataProcessor.getHighestStartNumberByRace(race.id)).thenReturn(4)

        val wrapper = IofXmlProcessor.importData(
            entryListXml().byteInputStream(),
            DataType.COMPETITORS,
            race,
            dataProcessor
        )

        assertTrue(wrapper.invalidLines.isEmpty())
        assertEquals(2, wrapper.competitorCategories.size)
        assertEquals(listOf("W21", "M21"), wrapper.categories.map { it.category.name })

        val alice = wrapper.competitorCategories.first().competitor
        assertEquals("Alice", alice.firstName)
        assertEquals("Runner", alice.lastName)
        assertEquals("US001", alice.index)
        assertEquals("", alice.bibNumber)
        assertEquals(123456, alice.siNumber)
        assertEquals(false, alice.siRent)
        assertEquals(5, alice.startNumber)

        val bob = wrapper.competitorCategories[1].competitor
        assertEquals("US002", bob.index)
        assertEquals("", bob.bibNumber)
        assertEquals(true, bob.siRent)
        assertEquals(6, bob.startNumber)
        assertTrue(wrapper.iofWarnings.any { it.contains("TeamEntry") })
        assertTrue(wrapper.iofWarnings.any { it.contains("RaceNumber") })
    }

    private fun entryListXml(): String = """
        <EntryList xmlns="http://www.orienteering.org/datastandard/3.0" iofVersion="3.0">
          <Event>
            <Name>Entry Race</Name>
            <StartTime>
              <Date>2026-06-29</Date>
              <Time>09:00:00</Time>
            </StartTime>
          </Event>
          <TeamEntry>
            <Name>Relay Team</Name>
            <Class><Name>Relay</Name></Class>
          </TeamEntry>
          <PersonEntry>
            <Person sex="F">
              <Id type="USA">US001</Id>
              <Name>
                <Family>Runner</Family>
                <Given>Alice</Given>
              </Name>
              <BirthDate>1990-04-02</BirthDate>
            </Person>
            <Organisation><Name>OK Test</Name></Organisation>
            <ControlCard>123456</ControlCard>
            <Class><Name>W21</Name></Class>
            <RaceNumber>1</RaceNumber>
          </PersonEntry>
          <PersonEntry>
            <Person>
              <Id>US002</Id>
              <Name>
                <Family>Rental</Family>
                <Given>Bob</Given>
              </Name>
            </Person>
            <Class><Name>M21</Name></Class>
          </PersonEntry>
        </EntryList>
    """.trimIndent()
}
