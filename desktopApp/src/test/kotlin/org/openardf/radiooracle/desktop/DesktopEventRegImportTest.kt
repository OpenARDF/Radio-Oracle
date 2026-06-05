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
    }

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
