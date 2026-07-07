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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.domain.PunchStatus
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.shared.event.EventAliasPunch
import org.openardf.radiooracle.shared.event.EventCategory
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventCompetitor
import org.openardf.radiooracle.shared.event.EventCompetitorCategory
import org.openardf.radiooracle.shared.event.EventCompetitorData
import org.openardf.radiooracle.shared.event.EventPunch
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.PRELIMINARY_RESULT_NOTICE
import org.openardf.radiooracle.shared.event.EventRace
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.EventReadoutData
import org.openardf.radiooracle.shared.event.EventResult
import org.openardf.radiooracle.shared.files.IofXmlSchemaResource
import org.openardf.radiooracle.shared.files.IofXmlValidator
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class DesktopProjectFilesTest {
    @Test
    fun writesAndReadsSharedProjectFiles() {
        val directory = Files.createTempDirectory("rom-desktop-project")
        val path = directory.resolve("sample.rom.json")
        val projectFile = EventProjectFile(raceData = raceData())

        DesktopProjectFiles.write(path, projectFile)

        assertEquals(projectFile, DesktopProjectFiles.read(path))
    }

    @Test
    fun exportsResultsCsvFile() {
        val directory = Files.createTempDirectory("rom-desktop-results")
        val path = directory.resolve("results.csv")

        DesktopProjectFiles.exportResultsCsv(path, EventProjectFile(raceData = raceData()))

        assertEquals("", Files.readString(path))
    }

    @Test
    fun exportsRobisStartListCsvFile() {
        val directory = Files.createTempDirectory("rom-desktop-robis-start-list")
        val path = directory.resolve("robis-start-list.csv")

        DesktopProjectFiles.exportRobisStartListCsv(path, EventProjectFile(raceData = raceDataWithReadout()))

        assertTrue(Files.readString(path).contains("\"\";Runner;Alice;M21;\"\";;IDX;\"\";\"CZE\";123456"))
    }

    @Test
    fun exportsArdfEventResultsCsvFile() {
        val directory = Files.createTempDirectory("rom-desktop-ardfevent-results")
        val path = directory.resolve("ardfevent-results.csv")

        DesktopProjectFiles.exportArdfEventResultsCsv(path, EventProjectFile(raceData = raceDataWithReadout()))
        val exported = Files.readString(path)

        assertTrue(exported.contains("Kategorie;Pořadí;Jméno;Person ID;Čas;TX;Status;Kontroly"))
        assertTrue(exported.contains("M21;;RUNNER Alice;IDX;00:20:00;0;OK;"))
    }

    @Test
    fun exportsArdfJsonFile() {
        val directory = Files.createTempDirectory("rom-desktop-ardf-json")
        val path = directory.resolve("event.ardf.json")

        DesktopProjectFiles.exportArdfJson(path, EventProjectFile(raceData = raceData()))
        val exported = Files.readString(path)

        assertTrue(exported.contains("\"format_version\": 1"))
        assertTrue(exported.contains("\"event_name\": \"Desktop File Race\""))
        assertTrue(exported.contains("\"race_name\": \"Desktop File Race\""))
    }

    @Test
    fun exportsAndroidRaceBackupJsonFile() {
        val directory = Files.createTempDirectory("rom-desktop-android-race-backup-json")
        val path = directory.resolve("race.ardfjs")

        DesktopProjectFiles.exportAndroidRaceBackupJson(path, EventProjectFile(raceData = raceDataWithReadout()))
        val exported = Files.readString(path)

        assertTrue(exported.contains("\"race_name\": \"Desktop File Race\""))
        assertTrue(exported.contains("\"race_time_limit\": \"120\""))
        assertTrue(exported.contains("\"unmatched_results\""))
        assertTrue(exported.contains("\"competitor_category\": \"M21\""))
    }

    @Test
    fun importsAndroidRaceBackupJsonFile() {
        val directory = Files.createTempDirectory("rom-desktop-import-android-race-backup-json")
        val path = directory.resolve("race.ardfjs")
        DesktopProjectFiles.exportAndroidRaceBackupJson(path, EventProjectFile(raceData = raceDataWithReadout()))
        var nextId = 0

        val imported = DesktopProjectFiles.importAndroidRaceBackupJson(path) {
            "imported-${nextId++}"
        }

        assertEquals("Desktop File Race", imported.raceData.race.name)
        assertEquals("M21", imported.raceData.categories.single().category.name)
        assertEquals("Alice", imported.raceData.competitorData.single().competitorCategory.competitor.firstName)
        assertEquals(ResultStatus.OK, imported.raceData.competitorData.single().readoutData?.result?.resultStatus)
    }

    @Test
    fun exportsLiveResultsJsonFile() {
        val directory = Files.createTempDirectory("rom-desktop-live-results-json")
        val path = directory.resolve("event.live-results.json")

        DesktopProjectFiles.exportLiveResultsJson(path, EventProjectFile(raceData = raceDataWithReadout()))
        val exported = Files.readString(path)

        assertTrue(exported.contains("\"competitor_category\": \"M21\""))
        assertTrue(exported.contains("\"result_status\": \"OK\""))
    }

    @Test
    fun exportsFinalResultsJsonFile() {
        val directory = Files.createTempDirectory("rom-desktop-final-results-json")
        val path = directory.resolve("event.final-results.json")

        DesktopProjectFiles.exportFinalResultsJson(path, EventProjectFile(raceData = raceDataWithReadout()))
        val exported = Files.readString(path)

        assertTrue(exported.contains("\"categories\""))
        assertTrue(exported.contains("\"aliases\""))
        assertTrue(exported.contains("\"competitors\""))
        assertTrue(exported.contains("\"competitor_category\": \"M21\""))
        assertTrue(exported.contains("\"result_status\": \"OK\""))
    }

    @Test
    fun exportsIofStartListXmlFile() {
        val directory = Files.createTempDirectory("rom-desktop-iof-start-list")
        val path = directory.resolve("event.iof.xml")

        DesktopProjectFiles.exportIofStartListXml(path, EventProjectFile(raceData = raceDataWithReadout()))
        val exported = Files.readString(path)

        assertTrue(exported.contains("<StartList"))
        assertTrue(exported.contains("<ClassStart>"))
        assertTrue(exported.contains("<Family>Runner</Family>"))
        assertTrue(exported.contains("<StartTime>2026-05-31T10:00:00</StartTime>"))
        assertTrue(exported.contains("<ControlCard>123456</ControlCard>"))
        assertIofSchemaValid(exported)
    }

    @Test
    fun exportsIofCourseDataXmlFile() {
        val directory = Files.createTempDirectory("rom-desktop-iof-course-data")
        val path = directory.resolve("event.iof.xml")

        DesktopProjectFiles.exportIofCourseDataXml(path, EventProjectFile(raceData = raceDataWithReadout()))
        val exported = Files.readString(path)

        assertTrue(exported.contains("<CourseData"))
        assertTrue(exported.contains("<RaceCourseData>"))
        assertTrue(exported.contains("<Course>"))
        assertTrue(exported.contains("<Name>M21</Name>"))
        assertIofSchemaValid(exported)
    }

    @Test
    fun exportsIofEntryListXmlFile() {
        val directory = Files.createTempDirectory("rom-desktop-iof-entry-list")
        val path = directory.resolve("event.iof.xml")

        DesktopProjectFiles.exportIofEntryListXml(path, EventProjectFile(raceData = raceDataWithReadout()))
        val exported = Files.readString(path)

        assertTrue(exported.contains("<EntryList"))
        assertTrue(exported.contains("<PersonEntry>"))
        assertTrue(exported.contains("<Family>Runner</Family>"))
        assertTrue(exported.contains("<ControlCard>123456</ControlCard>"))
        assertIofSchemaValid(exported)
    }

    @Test
    fun exportsIofResultListXmlFile() {
        val directory = Files.createTempDirectory("rom-desktop-iof-result-list")
        val path = directory.resolve("event.iof.xml")

        DesktopProjectFiles.exportIofResultListXml(path, EventProjectFile(raceData = raceDataWithReadout()))
        val exported = Files.readString(path)

        assertTrue(exported.contains("<ResultList"))
        assertTrue(exported.contains("<ClassResult>"))
        assertTrue(exported.contains("<Family>Runner</Family>"))
        assertTrue(exported.contains("<StartTime>2026-05-31T10:00:00</StartTime>"))
        assertTrue(exported.contains("<FinishTime>2026-05-31T10:20:00</FinishTime>"))
        assertTrue(exported.contains("<Status>OK</Status>"))
        assertIofSchemaValid(exported)
    }

    @Test
    fun exportsResultsHtmlFile() {
        val directory = Files.createTempDirectory("rom-desktop-results-html")
        val path = directory.resolve("results.html")

        DesktopProjectFiles.exportResultsHtml(path, EventProjectFile(raceData = raceDataWithReadout()))
        val exported = Files.readString(path)

        assertTrue(exported.contains("<!doctype html>"))
        assertTrue(exported.contains("<h1>Desktop File Race</h1>"))
        assertTrue(exported.contains("<h2>M21</h2>"))
        assertTrue(exported.contains("<td>RUNNER Alice</td>"))
        assertTrue(exported.contains("<td>00:20:00</td>"))
    }

    @Test
    fun exportsPublicResultsSiteDirectory() {
        val directory = Files.createTempDirectory("rom-desktop-public-results-site")

        val paths = DesktopProjectFiles.exportPublicResultsSite(
            directory,
            EventProjectFile(raceData = raceDataWithSplitReadout())
        )

        assertTrue(Files.exists(paths.indexHtml))
        assertTrue(Files.exists(paths.rootIndexHtml))
        assertTrue(Files.exists(paths.eventDirectory))
        assertTrue(Files.exists(paths.publicResultsJson))
        assertTrue(Files.exists(paths.finalResultsJson))
        assertTrue(Files.exists(paths.liveResultsJson))
        assertTrue(Files.exists(paths.iofResultListXml))
        assertTrue(Files.exists(paths.printableResultsHtml))
        assertTrue(Files.exists(directory.resolve("_headers")))
        assertTrue(Files.exists(paths.eventDirectory.resolve("assets").resolve("site.css")))
        assertTrue(Files.exists(paths.eventDirectory.resolve("assets").resolve("site.js")))

        val rootIndex = Files.readString(paths.rootIndexHtml)
        val index = Files.readString(paths.indexHtml)
        val publicJson = Files.readString(paths.publicResultsJson)

        assertEquals("2026-05-31-desktop-file-race", paths.eventPath)
        assertTrue(rootIndex.contains("OpenARDF Results"))
        assertTrue(rootIndex.contains("""href="2026-05-31-desktop-file-race/""""))
        assertTrue(index.contains("Desktop File Race"))
        assertTrue(index.contains("""href="../">All published results</a>"""))
        assertTrue(index.contains("downloads/final-results.json"))
        val siteJs = Files.readString(paths.eventDirectory.resolve("assets").resolve("site.js"))
        assertTrue(siteJs.contains("data/public-results.json"))
        assertTrue(siteJs.contains("data-split-target"))
        assertTrue(siteJs.contains("Tap for splits"))
        assertTrue(publicJson.contains("\"name\": \"Desktop File Race\""))
        assertTrue(publicJson.contains("\"competitor\": \"RUNNER Alice\""))
        assertTrue(publicJson.contains("\"runtime\": \"00:20:00\""))
        assertTrue(publicJson.contains("\"splits\": ["))
        assertTrue(publicJson.contains("\"control\": \"31\""))
        assertTrue(publicJson.contains("\"legTime\": \"00:05:00\""))
        assertTrue(publicJson.contains("\"cumulativeTime\": \"00:20:00\""))
        assertTrue(Files.readString(paths.iofResultListXml).contains("<ResultList"))
    }

    @Test
    fun exportsPublicResultsSiteAwardsAndPreliminaryNoticeForNonPracticeRaces() {
        val directory = Files.createTempDirectory("rom-desktop-public-results-site-awards")

        val paths = DesktopProjectFiles.exportPublicResultsSite(
            directory,
            EventProjectFile(raceData = raceDataWithAwardReadout())
        )
        val index = Files.readString(paths.indexHtml)
        val publicJson = Files.readString(paths.publicResultsJson)
        val finalJson = Files.readString(paths.finalResultsJson)
        val siteJs = Files.readString(paths.eventDirectory.resolve("assets").resolve("site.js"))

        assertTrue(index.contains("awards-panel"))
        assertTrue(siteJs.contains("renderAwards"))
        assertTrue(publicJson.contains("\"publicationNotice\": \"$PRELIMINARY_RESULT_NOTICE\""))
        assertTrue(publicJson.contains("\"usaAwards\":"))
        assertTrue(publicJson.contains("\"medal\": \"Gold\""))
        assertTrue(finalJson.contains("\"publication_notice\": \"$PRELIMINARY_RESULT_NOTICE\""))
        assertTrue(finalJson.contains("\"usa_awards\""))
    }

    @Test
    fun exportsResultsTextFile() {
        val directory = Files.createTempDirectory("rom-desktop-results-text")
        val path = directory.resolve("results.txt")

        DesktopProjectFiles.exportResultsText(path, EventProjectFile(raceData = raceDataWithReadout()))
        val exported = Files.readString(path)

        assertTrue(exported.contains("Results"))
        assertTrue(exported.contains("Race: Desktop File Race"))
        assertTrue(exported.contains("Category M21"))
        assertTrue(exported.contains("1.\tRUNNER Alice"))
        assertTrue(exported.contains("00:20:00"))
    }



    private fun raceData(): EventRaceData =
        EventRaceData(
            race = EventRace(
                id = "race",
                name = "Desktop File Race",
                apiKey = "",
                startDateTimeIso = "2026-05-31T10:00",
                raceType = RaceType.CLASSIC,
                raceLevel = RaceLevel.PRACTICE,
                raceBand = RaceBand.M80,
                timeLimitSeconds = 7_200
            ),
            categories = emptyList(),
            aliases = emptyList(),
            competitorData = emptyList(),
            unmatchedReadoutData = emptyList()
        )

    private fun raceDataWithReadout(): EventRaceData {
        val race = raceData().race
        val category = EventCategory(
            id = "category",
            raceId = race.id,
            name = "M21",
            isMan = true,
            maxAge = null,
            lengthMeters = 0,
            climbMeters = 0,
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
            club = "",
            index = "IDX",
            isMan = false,
            birthYear = null,
            siNumber = 123456,
            siRent = false,
            startNumber = 1,
            drawnStartTimeSeconds = null
        )
        return raceData().copy(
            categories = listOf(EventCategoryData(category, controlPoints = emptyList(), competitors = listOf(competitor))),
            competitorData = listOf(
                EventCompetitorData(
                    competitorCategory = EventCompetitorCategory(competitor, category),
                    readoutData = EventReadoutData(
                        result = EventResult(
                            id = "result",
                            raceId = race.id,
                            competitorId = competitor.id,
                            siNumber = 123456,
                            cardType = 5,
                            checkTimeSeconds = null,
                            startTimeSeconds = 36_000,
                            finishTimeSeconds = 37_200,
                            readoutDateTimeIso = "2026-05-31T10:21:00",
                            automaticStatus = true,
                            resultStatus = ResultStatus.OK,
                            points = 0,
                            runTimeSeconds = 1_200,
                            modified = false,
                            sent = false
                        ),
                        punches = emptyList()
                    )
                )
            )
        )
    }

    private fun raceDataWithSplitReadout(): EventRaceData {
        val raceData = raceDataWithReadout()
        val competitorData = raceData.competitorData.single()
        val readoutData = competitorData.readoutData!!
        return raceData.copy(
            competitorData = listOf(
                competitorData.copy(
                    readoutData = readoutData.copy(
                        punches = listOf(
                            EventAliasPunch(
                                punch = EventPunch(
                                    id = "punch-control",
                                    raceId = raceData.race.id,
                                    resultId = readoutData.result.id,
                                    cardNumber = 123456,
                                    siCode = 31,
                                    siTimeSeconds = 36_300,
                                    originalSiTimeSeconds = 36_300,
                                    punchType = SIRecordType.CONTROL,
                                    order = 1,
                                    punchStatus = PunchStatus.VALID,
                                    splitSeconds = 300
                                ),
                                alias = null
                            ),
                            EventAliasPunch(
                                punch = EventPunch(
                                    id = "punch-finish",
                                    raceId = raceData.race.id,
                                    resultId = readoutData.result.id,
                                    cardNumber = 123456,
                                    siCode = 0,
                                    siTimeSeconds = 37_200,
                                    originalSiTimeSeconds = 37_200,
                                    punchType = SIRecordType.FINISH,
                                    order = 2,
                                    punchStatus = PunchStatus.VALID,
                                    splitSeconds = 900
                                ),
                                alias = null
                            )
                        )
                    )
                )
            )
        )
    }

    private fun raceDataWithAwardReadout(): EventRaceData {
        val raceData = raceDataWithSplitReadout()
        val categoryData = raceData.categories.single()
        val competitorData = raceData.competitorData.single()
        val competitor = competitorData.competitorCategory.competitor.copy(usaChampEligible = true)
        return raceData.copy(
            race = raceData.race.copy(raceLevel = RaceLevel.NATIONAL),
            categories = listOf(categoryData.copy(competitors = listOf(competitor))),
            competitorData = listOf(
                competitorData.copy(
                    competitorCategory = EventCompetitorCategory(competitor, categoryData.category)
                )
            )
        )
    }

    private fun assertIofSchemaValid(xml: String) {
        val schema = configuredIofSchemaPath()
            ?.takeIf { Files.isRegularFile(it) }
            ?.let(Files::readString)
            ?: IofXmlSchemaResource.loadBundledSchema()
        val validation = IofXmlValidator.validate(xml, schema)
        assertTrue(validation.errors.joinToString { it.message }, validation.valid)
    }

    private fun configuredIofSchemaPath(): Path? =
        sequenceOf(
            System.getProperty(IOF_SCHEMA_PROPERTY),
            System.getenv(IOF_SCHEMA_ENV)
        )
            .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
            .firstOrNull()
            ?.let(Paths::get)

    private companion object {
        const val IOF_SCHEMA_PROPERTY = "iof.schema.path"
        const val IOF_SCHEMA_ENV = "IOF_SCHEMA_PATH"
    }
}
