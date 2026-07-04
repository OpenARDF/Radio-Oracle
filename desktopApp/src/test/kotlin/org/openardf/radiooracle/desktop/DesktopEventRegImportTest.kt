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

package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.files.EventCsvImports
import java.nio.file.Files

class DesktopEventRegImportTest {
    @Test
    fun parsesRegistrationTableIntoCompetitionGroups() {
        val registration = DesktopEventRegRegistrationParser.parse(sampleRegistrationHtml())

        assertEquals("Sample Radio Championships", registration.eventName)
        assertEquals(listOf("Sprint", "FoxO", "2m", "SprMod-NC"), registration.competitions.map { it.name })
        assertEquals(
            listOf("Fala", "Kerns"),
            registration.competitions.first { it.name == "Sprint" }.competitors.map { it.lastName }
        )
        assertEquals(
            "SprMod-NC",
            registration.competitions.first { it.name == "SprMod-NC" }.competitors.single().categoryName
        )
    }

    @Test
    fun parsesGoogleSheetRegistrationSummaryWithSiCardsAndBibNumbers() {
        val registration = DesktopEventRegSpreadsheetParser.parseCsv(
            csvText = sampleGoogleSheetCsv(),
            eventName = "Radio-O Champs Reg Summary"
        )

        assertEquals("Radio-O Champs Reg Summary", registration.eventName)
        assertEquals(listOf("Sprint", "FoxO", "SprMod-NC"), registration.competitions.map { it.name })
        assertEquals(
            listOf("Fala", "Kerns"),
            registration.competitions.first { it.name == "Sprint" }.competitors.map { it.lastName }
        )
        assertEquals(
            listOf("Kerns", "Boyd"),
            registration.competitions.first { it.name == "SprMod-NC" }.competitors.map { it.lastName }
        )

        val fala = registration.competitions.first { it.name == "Sprint" }.competitors.first()
        assertEquals(8400555, fala.siNumber)
        assertEquals("101", fala.bibNumber)
        assertEquals("BOK", fala.club)
        assertEquals("K4FAL", fala.callSign)
        assertEquals(1991, fala.birthYear)
        assertEquals(true, fala.isMan)
        assertEquals("conf-1", fala.personId)
        assertEquals("05:00", fala.startTimeText)
    }

    @Test
    fun generatesOneEventFilePerCompetitionWithCompetitors() {
        val outputDirectory = Files.createTempDirectory("radio-oracle-eventreg-test")
        val ids = generateSequence(1) { it + 1 }.map { "id-$it" }.iterator()

        val result = DesktopEventRegImporter.importFromWebsite(
            url = "https://eventreg.example.test/reglist",
            outputDirectory = outputDirectory,
            startDateTimeIso = "2026-06-05T09:00",
            fetchHtml = { sampleRegistrationHtml() },
            idFactory = { ids.next() }
        )

        assertEquals(4, result.generatedFiles.size)
        assertEquals(
            listOf(
                "Sample Radio Championships - Sprint.json",
                "Sample Radio Championships - FoxO.json",
                "Sample Radio Championships - 2m.json",
                "Sample Radio Championships - SprMod-NC.json"
            ),
            result.generatedFiles.map { it.path.fileName.toString() }
        )
        result.generatedFiles.forEach { generated ->
            assertTrue(Files.isRegularFile(generated.path))
        }

        val sprintProject = DesktopProjectFiles.read(
            result.generatedFiles.first { it.competitionName == "Sprint" }.path
        )
        assertEquals(RaceType.SPRINT, sprintProject.raceData.race.raceType)
        assertEquals(RaceBand.NONE, sprintProject.raceData.race.raceBand)
        assertEquals(2, sprintProject.raceData.competitorData.size)
        assertEquals(
            listOf("M-21", "W-65"),
            sprintProject.raceData.categories.map { it.category.name }.sorted()
        )
        assertEquals(true, sprintProject.raceData.categories.single { it.category.name == "M-21" }.category.isMan)
        assertEquals(false, sprintProject.raceData.categories.single { it.category.name == "W-65" }.category.isMan)
        assertEquals(
            "BOK",
            sprintProject.raceData.competitorData
                .first { it.competitorCategory.competitor.lastName == "Fala" }
                .competitorCategory
                .competitor
                .club
        )

        val twoMeterProject = DesktopProjectFiles.read(
            result.generatedFiles.first { it.competitionName == "2m" }.path
        )
        assertEquals(RaceBand.M2, twoMeterProject.raceData.race.raceBand)
        assertFalse(twoMeterProject.raceData.competitorData.any { it.competitorCategory.competitor.lastName == "Boyd" })
    }

    @Test
    fun generatesEventFilesFromGoogleSheetWithSiCardsAndBibNumbers() {
        val outputDirectory = Files.createTempDirectory("radio-oracle-google-sheet-test")
        val ids = generateSequence(1) { it + 1 }.map { "id-$it" }.iterator()

        val result = DesktopEventRegImporter.importFromGoogleSheet(
            url = "https://docs.google.com/spreadsheets/d/test-spreadsheet-id/edit",
            outputDirectory = outputDirectory,
            startDateTimeIso = "2026-07-03T09:00",
            fetchSpreadsheet = {
                SpreadsheetDownload(
                    bytes = sampleGoogleSheetCsv().toByteArray(),
                    contentType = "text/csv",
                    fileName = "Radio-O Champs Reg Summary_02jul26.csv"
                )
            },
            idFactory = { ids.next() }
        )

        assertEquals(3, result.generatedFiles.size)
        assertEquals(
            listOf(
                "Radio-O Champs Reg Summary_02jul26 - Sprint.json",
                "Radio-O Champs Reg Summary_02jul26 - FoxO.json",
                "Radio-O Champs Reg Summary_02jul26 - SprMod-NC.json"
            ),
            result.generatedFiles.map { it.path.fileName.toString() }
        )
        val generatedFileNames = Files.list(outputDirectory).use { paths ->
            paths.map { it.fileName.toString() }.sorted().toList()
        }
        assertEquals(
            listOf(
                "Radio-O Champs Reg Summary_02jul26 - FoxO.json",
                "Radio-O Champs Reg Summary_02jul26 - SprMod-NC.json",
                "Radio-O Champs Reg Summary_02jul26 - Sprint.json"
            ),
            generatedFileNames
        )

        val sprintProject = DesktopProjectFiles.read(
            result.generatedFiles.first { it.competitionName == "Sprint" }.path
        )
        val fala = sprintProject.raceData.competitorData
            .first { it.competitorCategory.competitor.lastName == "Fala" }
            .competitorCategory
            .competitor
        assertEquals(8400555, fala.siNumber)
        assertEquals("101", fala.bibNumber)
        assertEquals("K4FAL", fala.callSign)
        assertEquals("conf-1", fala.index)
        assertEquals(RaceType.SPRINT, sprintProject.raceData.race.raceType)

        val sprintCategoryRows = sprintProject.categoryImportRows()
        assertEquals(listOf("M-21", "W-65"), sprintCategoryRows.map { it.name })

        val sprintCompetitorRows = sprintProject.competitorImportRows()
        assertEquals(listOf("Fala", "Kerns"), sprintCompetitorRows.map { it.lastName })
        assertEquals(listOf(8400555, 1800859), sprintCompetitorRows.map { it.siNumber })
        assertEquals(listOf("101", "102"), sprintCompetitorRows.map { it.bibNumber })
        assertEquals(listOf("M-21", "W-65"), sprintCompetitorRows.map { it.categoryName })

        val foxProject = DesktopProjectFiles.read(
            result.generatedFiles.first { it.competitionName == "FoxO" }.path
        )
        assertEquals(RaceType.FOXORING, foxProject.raceData.race.raceType)
        assertTrue(foxProject.raceData.competitorData.any { it.competitorCategory.competitor.lastName == "Boyd" })
        assertFalse(foxProject.raceData.competitorData.any { it.competitorCategory.competitor.lastName == "Kerns" })
    }

    @Test
    fun generatesOneCompetitorCsvPerCompetitionWithEventFileStem() {
        val outputDirectory = Files.createTempDirectory("radio-oracle-eventreg-competitors-test")
        val ids = generateSequence(1) { it + 1 }.map { "id-$it" }.iterator()

        val result = DesktopEventRegImporter.importCompetitorCsvsFromWebsite(
            url = "https://eventreg.example.test/reglist",
            outputDirectory = outputDirectory,
            startDateTimeIso = "2026-06-05T09:00",
            fetchHtml = { sampleRegistrationHtml() },
            idFactory = { ids.next() }
        )

        assertEquals(4, result.generatedFiles.size)
        assertEquals(
            listOf(
                "Sample Radio Championships - Sprint competitors.csv",
                "Sample Radio Championships - FoxO competitors.csv",
                "Sample Radio Championships - 2m competitors.csv",
                "Sample Radio Championships - SprMod-NC competitors.csv"
            ),
            result.generatedFiles.map { it.path.fileName.toString() }
        )

        val sprintCsv = Files.readString(result.generatedFiles.first { it.competitionName == "Sprint" }.path)
        val sprintRows = EventCsvImports.parseAndroidCompetitorRows(sprintCsv).rows
        assertEquals(listOf("Fala", "Kerns"), sprintRows.map { it.lastName })
        assertEquals(listOf("M-21", "W-65"), sprintRows.map { it.categoryName })
        assertEquals(listOf(true, false), sprintRows.map { it.isMan })
    }

    private fun sampleGoogleSheetCsv(): String =
        """
        ,First,Last,ConfNum,Status,Bib#,YearBorn,Sex,Club,E-Punch ID,RentPunch,Sprint Class,Sprint Crs,Sprint Start,FoxO Class,FoxO Crs,FoxO Start,SprMod-NC Crs, Call--Call
        0,Gheorghe,Fala,conf-1,Confirmed,101,1991-01-01 00:00:00,M,BOK,8400555,N,M-21,M-21,05:00,M-21,M-21,,NC,K4FAL
        1,Kathleen,Kerns,conf-2,Confirmed,102,1959-01-01 00:00:00,F,MTHD,1800859,N,W-65,W-65,,NC,,,Competing,K7KER
        2,Gerald,Boyd,conf-3,Confirmed,103,1957-01-01 00:00:00,M,NMO,247347,N,NC,,,M-60,M-60,,Competing,WB8WFK
        """.trimIndent()

    private fun sampleRegistrationHtml(): String =
        """
        <!DOCTYPE html>
        <html>
          <head>
            <title>EventReg | Sample Radio Championships Registration List</title>
          </head>
          <body>
            <table id="reglistTable">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Conf?</th>
                  <th>Club</th>
                  <th>Sprint Class</th>
                  <th>Sprint Crs</th>
                  <th>Start</th>
                  <th></th>
                  <th>FoxO Class</th>
                  <th>FoxO Crs</th>
                  <th>Start</th>
                  <th></th>
                  <th>2m Class</th>
                  <th>2m Crs</th>
                  <th>Start</th>
                  <th></th>
                  <th>SprMod-NC Class</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td>Fala, Gheorghe</td>
                  <td></td>
                  <td>BOK</td>
                  <td>M-21</td>
                  <td>M-21</td>
                  <td>05:00</td>
                  <td></td>
                  <td>M-21</td>
                  <td>M-21</td>
                  <td></td>
                  <td></td>
                  <td>M-21</td>
                  <td>M-21</td>
                  <td></td>
                  <td></td>
                  <td></td>
                  <td></td>
                </tr>
                <tr>
                  <td>Kerns, Kathleen</td>
                  <td></td>
                  <td>MTHD</td>
                  <td>W-65</td>
                  <td>W-65</td>
                  <td></td>
                  <td></td>
                  <td></td>
                  <td></td>
                  <td></td>
                  <td></td>
                  <td>W-65</td>
                  <td>W-65</td>
                  <td></td>
                  <td></td>
                  <td></td>
                  <td></td>
                </tr>
                <tr>
                  <td>Boyd, Gerald</td>
                  <td></td>
                  <td>NMO</td>
                  <td></td>
                  <td></td>
                  <td></td>
                  <td></td>
                  <td>M-60</td>
                  <td>M-60</td>
                  <td></td>
                  <td></td>
                  <td></td>
                  <td></td>
                  <td></td>
                  <td></td>
                  <td>Y</td>
                  <td></td>
                </tr>
              </tbody>
            </table>
          </body>
        </html>
        """.trimIndent()
}
