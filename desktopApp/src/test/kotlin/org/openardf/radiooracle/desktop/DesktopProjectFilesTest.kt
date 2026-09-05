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
import org.openardf.radiooracle.shared.event.EventProjectFileJson
import org.openardf.radiooracle.shared.event.EventFileTransferPayloads
import org.openardf.radiooracle.shared.event.PRELIMINARY_RESULT_NOTICE
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.event.PublicResultsPublicationStatus
import org.openardf.radiooracle.shared.event.ProtectedCourseObjectPoint
import org.openardf.radiooracle.shared.event.ProtectedCourseObjectType
import org.openardf.radiooracle.shared.event.EventRace
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.EventReadoutData
import org.openardf.radiooracle.shared.event.EventResult
import org.openardf.radiooracle.shared.event.EventSeriesEvent
import org.openardf.radiooracle.shared.event.EventSeriesFile
import org.openardf.radiooracle.shared.event.EventSeriesLink
import org.openardf.radiooracle.shared.files.IofXmlSchemaResource
import org.openardf.radiooracle.shared.files.IofXmlValidator
import org.openardf.radiooracle.shared.event.PublicResultsPublication
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
    fun persistsPublicResultsUrlInStandaloneRaceFile() {
        val directory = Files.createTempDirectory("rom-public-results-publication")
        val path = directory.resolve("sample.rom.json")
        DesktopProjectFiles.write(path, EventProjectFile(raceData = raceData()))
        val session = DesktopProjectSession(DesktopProjectFiles)
        session.open(path)
        val publication = PublicResultsPublication(
            url = "https://openardf-results.pages.dev/2026-05-31-desktop-file-race/",
            publishedAtIso = "2026-05-31T12:00:00Z"
        )

        val updatedProject = persistPublicResultsPublication(null, session, publication)

        assertEquals(publication, updatedProject?.publicResultsPublication)
        assertEquals(publication, DesktopProjectFiles.read(path).publicResultsPublication)
        assertFalse(session.hasUnsavedChanges)
    }

    @Test
    fun persistsPublicResultsUrlInSeriesManifest() {
        val directory = Files.createTempDirectory("rom-series-public-results-publication")
        val manifestPath = directory.resolve("championship.series.radio-oracle.json")
        DesktopEventSeriesFiles.write(
            manifestPath,
            EventSeriesFile(
                seriesId = "series",
                name = "Championship",
                events = emptyList()
            )
        )
        val session = DesktopProjectSession(DesktopProjectFiles)
        session.newProject(EventProjectFile(raceData = raceData()))
        val publication = PublicResultsPublication(
            url = "https://openardf-results.pages.dev/2026-05-31-championship-series/",
            publishedAtIso = "2026-05-31T12:00:00Z"
        )

        val updatedProject = persistPublicResultsPublication(manifestPath, session, publication)

        assertEquals(null, updatedProject)
        assertEquals(publication, DesktopEventSeriesFiles.read(manifestPath).publicResultsPublication)
        assertEquals(null, session.currentProject?.publicResultsPublication)
    }

    @Test
    fun derivesPublicResultsUrlBeforeFirstPublicationWhenSettingsAreComplete() {
        val settings = DesktopCloudflarePagesPublishSettings(
            projectName = " openardf-results ",
            branch = "main",
            accountId = "account",
            apiToken = "token"
        )

        assertEquals(
            "https://openardf-results.pages.dev/2026-05-31-desktop-file-race/",
            configuredPublicResultsUrl(
                settings = settings,
                currentProject = EventProjectFile(raceData = raceData()),
                seriesFile = null
            )
        )
        assertEquals(
            null,
            configuredPublicResultsUrl(
                settings = settings.copy(apiToken = ""),
                currentProject = EventProjectFile(raceData = raceData()),
                seriesFile = null
            )
        )
    }

    @Test
    fun derivesSeriesPublicResultsUrlFromManifestWithoutLoadingRaceFiles() {
        val settings = DesktopCloudflarePagesPublishSettings(
            projectName = "openardf-results",
            branch = "main",
            accountId = "account",
            apiToken = "token"
        )
        val seriesFile = EventSeriesFile(
            seriesId = "series",
            name = "Championship",
            events = listOf(
                EventSeriesEvent(
                    seriesEventId = "first-event",
                    eventFilePath = "missing-first-event.json",
                    order = 0,
                    displayName = "First Event",
                    startDateTimeIso = "2026-06-03T09:00"
                )
            )
        )

        assertEquals(
            "https://openardf-results.pages.dev/2026-06-03-championship-series/",
            configuredPublicResultsUrl(
                settings = settings,
                currentProject = EventProjectFile(raceData = raceData()),
                seriesFile = seriesFile
            )
        )
    }

    @Test
    fun loadsConfiguredSeriesUrlWithoutReadingMemberRaceFiles() {
        val directory = Files.createTempDirectory("rom-series-url-state")
        val manifestPath = directory.resolve("championship.series.radio-oracle.json")
        val eventPath = directory.resolve("missing-first-event.json")
        val seriesFile = EventSeriesFile(
            seriesId = "series",
            name = "Championship",
            events = listOf(
                EventSeriesEvent(
                    seriesEventId = "first-event",
                    eventFilePath = eventPath.fileName.toString(),
                    order = 0,
                    displayName = "First Event",
                    startDateTimeIso = "2026-06-03T09:00"
                )
            )
        )
        DesktopEventSeriesFiles.write(manifestPath, seriesFile)
        var memberRaceReadCount = 0
        val manifestOnlyStore = object : EventSeriesStore by DesktopEventSeriesFiles {
            override fun readEvent(path: Path): EventProjectFile {
                memberRaceReadCount += 1
                error("Configured results URL must not load a member Race File: $path")
            }
        }
        val currentProject = EventProjectFile(
            raceData = raceData(),
            seriesLink = EventSeriesLink(seriesId = "series", seriesEventId = "first-event")
        )
        val settings = DesktopCloudflarePagesPublishSettings(
            projectName = "openardf-results",
            branch = "main",
            accountId = "account",
            apiToken = "token"
        )

        val state = loadPublicResultsUrlState(
            currentPath = eventPath,
            currentProject = currentProject,
            settings = settings,
            store = manifestOnlyStore
        )

        assertEquals(0, memberRaceReadCount)
        assertEquals("https://openardf-results.pages.dev/2026-06-03-championship-series/", state.configuredUrl)
    }

    @Test
    fun exportsResultsCsvFile() {
        val directory = Files.createTempDirectory("rom-desktop-results")
        val path = directory.resolve("results.csv")

        DesktopProjectFiles.exportResultsCsv(path, EventProjectFile(raceData = raceData()))

        assertEquals("", Files.readString(path))
    }

    @Test
    fun exportsSharedSplitResultCsvAndPdfFiles() {
        val directory = Files.createTempDirectory("rom-desktop-split-results")
        val projectFile = EventProjectFile(raceData = raceDataWithSplitReadout())
        val csvPath = directory.resolve("split-results.csv")
        val pdfPath = directory.resolve("split-results.pdf")

        DesktopProjectFiles.exportSplitResultsCsv(csvPath, projectFile)
        DesktopProjectFiles.exportSplitResultsPdf(pdfPath, projectFile)

        val csv = Files.readString(csvPath)
        val pdf = Files.readString(pdfPath)
        assertTrue(csv.contains("Split #;From;Control;SI Code;Punch Status;Leg Time"))
        assertTrue(csv.contains(";Start;31;31;OK;00:05:00;300;"))
        assertTrue(csv.contains(";31;Finish;;OK;00:15:00;900;"))
        assertTrue(pdf.startsWith("%PDF-1.4"))
        assertTrue(pdf.contains("Desktop File Race"))
        assertTrue(pdf.contains("RUNNER Alice"))
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
    fun opensModernAndroidTransferAndImportsPreviouslyMislabeledBackup() {
        val directory = Files.createTempDirectory("rom-desktop-modern-android-transfer")
        val project = EventProjectFile(
            raceData = raceDataWithReadout(),
            publicResultsPublication = PublicResultsPublication(
                url = "https://example.org/results/",
                publishedAtIso = "2026-09-05T18:00:00Z"
            )
        )
        val text = EventProjectFileJson.encode(project)
        val expected = EventProjectFileJson.decode(text)
        val transferName = EventFileTransferPayloads.singleEventFileName(project.raceData.race.name)
        assertTrue(DesktopProjectFilePaths.isProjectFileName(transferName))
        val modernPath = directory.resolve(transferName)
        val oldPath = directory.resolve("race.ardfjs")
        Files.writeString(modernPath, text)
        Files.writeString(oldPath, text)

        assertEquals(expected, DesktopProjectFiles.read(modernPath))
        assertEquals(expected, DesktopProjectFiles.importAndroidRaceBackupJson(oldPath) {
            error("Modern IDs must be retained")
        })
        assertEquals(text, Files.readString(oldPath))
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
        val raceData = raceDataWithReadout()
        val categoryId = raceData.categories.single().category.id

        DesktopProjectFiles.exportIofCourseDataXml(
            path = path,
            projectFile = EventProjectFile(raceData = raceData),
            protectedCourseInfoByCategoryId = mapOf(
                categoryId to ProtectedCourseInfo(
                    lengthMeters = 1_234,
                    climbMeters = 56,
                    courseObjects = listOf(
                        ProtectedCourseObjectPoint(
                            id = "start",
                            label = "Start",
                            type = ProtectedCourseObjectType.START,
                            latitude = 37.1,
                            longitude = -122.1,
                            elevationMeters = 10.0
                        ),
                        ProtectedCourseObjectPoint(
                            id = "finish",
                            label = "Finish",
                            type = ProtectedCourseObjectType.FINISH,
                            latitude = 37.2,
                            longitude = -122.2,
                            elevationMeters = 20.0
                        )
                    )
                )
            )
        )
        val exported = Files.readString(path)

        assertTrue(exported.contains("<CourseData"))
        assertTrue(exported.contains("<RaceCourseData>"))
        assertTrue(exported.contains("<Course>"))
        assertTrue(exported.contains("<Name>M21</Name>"))
        assertTrue(exported.contains("<Length>1234</Length>"))
        assertTrue(exported.contains("<Climb>56</Climb>"))
        assertTrue(exported.contains("""<Position lng="-122.1" lat="37.1" alt="10.0"/>"""))
        assertTrue(exported.contains("<ClassCourseAssignment>"))
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
    fun exportsResultsPostingReportFiles() {
        val directory = Files.createTempDirectory("rom-desktop-report-results")
        val projectFile = EventProjectFile(raceData = raceDataWithReadout())
        val htmlPath = directory.resolve("report-results.html")
        val xmlPath = directory.resolve("report-results.xml")
        val pdfPath = directory.resolve("report-results.pdf")

        DesktopProjectFiles.exportResultReportHtml(htmlPath, projectFile)
        DesktopProjectFiles.exportResultReportXml(xmlPath, projectFile)
        DesktopProjectFiles.exportResultReportPdf(pdfPath, projectFile)

        val html = Files.readString(htmlPath)
        val xml = Files.readString(xmlPath)
        val pdf = Files.readString(pdfPath)
        assertTrue(html.contains("<title>Desktop File Race results report</title>"))
        assertTrue(html.contains("<th>Bib #</th>"))
        assertTrue(html.contains("<td>RUNNER Alice</td>"))
        assertTrue(xml.contains("<ResultsReport"))
        assertTrue(xml.contains("<RaceName>Desktop File Race</RaceName>"))
        assertTrue(xml.contains("<Name>RUNNER Alice</Name>"))
        assertTrue(pdf.startsWith("%PDF-1.4"))
        assertTrue(pdf.contains("Desktop File Race"))
        assertTrue(pdf.contains("RUNNER Alice"))
        assertTrue(pdf.contains("Controls"))
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
        assertTrue(Files.exists(paths.splitResultsCsv))
        assertTrue(Files.exists(paths.splitResultsPdf))
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
        assertTrue(index.contains("""href="../">All published preliminary results</a>"""))
        assertFalse(index.contains("Unofficial"))
        assertFalse(rootIndex.contains("unofficial"))
        assertTrue(index.contains("downloads/final-results.json"))
        assertTrue(index.contains("downloads/split-results.pdf"))
        assertTrue(index.contains("downloads/split-results.csv"))
        val siteJs = Files.readString(paths.eventDirectory.resolve("assets").resolve("site.js"))
        assertTrue(siteJs.contains("data/public-results.json"))
        assertTrue(siteJs.contains("data-split-target"))
        assertTrue(siteJs.contains("Tap for splits"))
        assertTrue(siteJs.contains("<th>From</th><th>Control</th>"))
        assertTrue(siteJs.contains("result.bib"))
        assertTrue(publicJson.contains("\"name\": \"Desktop File Race\""))
        assertTrue(publicJson.contains("\"competitor\": \"RUNNER Alice\""))
        assertTrue(publicJson.contains("\"runtime\": \"00:20:00\""))
        assertTrue(publicJson.contains("\"splits\": ["))
        assertTrue(publicJson.contains("\"bib\":"))
        assertTrue(publicJson.contains("\"from\": \"Start\""))
        assertTrue(publicJson.contains("\"control\": \"31\""))
        assertTrue(publicJson.contains("\"control\": \"Finish\""))
        assertTrue(publicJson.contains("\"legTime\": \"00:05:00\""))
        assertTrue(publicJson.contains("\"legTime\": \"00:15:00\""))
        assertTrue(publicJson.contains("\"cumulativeTime\": \"00:20:00\""))
        assertTrue(Files.readString(paths.iofResultListXml).contains("<ResultList"))
        assertTrue(Files.readString(paths.splitResultsCsv).contains("Leg Place"))
        assertTrue(Files.readString(paths.splitResultsPdf).startsWith("%PDF-1.4"))
    }

    @Test
    fun exportsOfficialPublicResultsWithCompleteIofAndNoPreliminaryNotice() {
        val paths = DesktopProjectFiles.exportPublicResultsSite(
            directory = Files.createTempDirectory("rom-desktop-official-results"),
            projectFile = EventProjectFile(raceData = raceDataWithSplitReadout()),
            publicationStatus = PublicResultsPublicationStatus.OFFICIAL
        )

        assertTrue(Files.readString(paths.indexHtml).contains("Official Results"))
        assertTrue(Files.readString(paths.iofResultListXml).contains("status=\"Complete\""))
        assertTrue(Files.readString(paths.iofResultListXml).contains("<BibNumber>1</BibNumber>"))
        assertFalse(Files.readString(paths.printableResultsHtml).contains(PRELIMINARY_RESULT_NOTICE))
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
        assertTrue(index.contains("Radio-Oracle preliminary results"))
        assertTrue(index.contains("""href="../">All published preliminary results</a>"""))
        assertTrue(index.contains("<h2>Preliminary Results</h2>"))
        assertTrue(siteJs.contains("renderAwards"))
        assertTrue(publicJson.contains("\"publicationNotice\": \"$PRELIMINARY_RESULT_NOTICE\""))
        assertTrue(publicJson.contains("\"usaAwards\":"))
        assertTrue(publicJson.contains("\"medal\": \"Gold\""))
        assertTrue(finalJson.contains("\"publication_notice\": \"$PRELIMINARY_RESULT_NOTICE\""))
        assertTrue(finalJson.contains("\"usa_awards\""))
    }

    @Test
    fun exportsSeriesRacesWithResultsOnOnePage() {
        val directory = Files.createTempDirectory("rom-desktop-public-results-series")
        val firstRace = raceDataWithSplitReadout()
        val secondRace = raceDataWithSplitReadout().copy(
            race = firstRace.race.copy(
                id = "race-two",
                name = "Second Series Race",
                startDateTimeIso = "2026-06-01T10:00"
            )
        )
        val raceWithoutResults = raceData().copy(
            race = firstRace.race.copy(
                id = "race-three",
                name = "Unfinished Series Race",
                startDateTimeIso = "2026-06-02T10:00"
            )
        )

        val paths = DesktopProjectFiles.exportPublicResultsSeriesSite(
            directory = directory,
            seriesName = "Summer Series",
            races = listOf(
                DesktopPublicResultSeriesRace(EventProjectFile(raceData = firstRace)),
                DesktopPublicResultSeriesRace(EventProjectFile(raceData = secondRace)),
                DesktopPublicResultSeriesRace(EventProjectFile(raceData = raceWithoutResults))
            ),
            generatedAt = java.time.Instant.parse("2026-06-03T12:00:00Z")
        )

        val index = Files.readString(paths.indexHtml)
        val manifest = Files.readString(paths.publicResultsJson)
        val siteJs = Files.readString(paths.eventDirectory.resolve("assets").resolve("series-site.js"))
        assertEquals("2026-05-31-summer-series-series", paths.eventPath)
        assertTrue(index.contains("Summer Series"))
        assertTrue(index.contains("series-races"))
        assertTrue(manifest.contains("Desktop File Race"))
        assertTrue(manifest.contains("Second Series Race"))
        assertTrue(manifest.contains("Unfinished Series Race"))
        assertTrue(manifest.contains("\"resultCount\":0"))
        assertTrue(manifest.contains("\"unofficialResults\":true"))
        assertTrue(index.contains("Preliminary Results"))
        assertTrue(siteJs.contains("resultsLabel} Coming Soon"))
        assertTrue(siteJs.contains("Return after this race begins for"))
        assertTrue(siteJs.contains("courseGraphicsHtml(race)"))
        assertTrue(siteJs.indexOf("courseGraphicsHtml(race)") < siteJs.indexOf("race-results"))
        assertTrue(siteJs.contains("Promise.all(manifest.races"))
        val siteCss = Files.readString(paths.eventDirectory.resolve("assets").resolve("site.css"))
        assertTrue(siteCss.contains("max-width:700px"))
        assertTrue(Files.readString(paths.rootIndexHtml).contains("Summer Series"))
    }

    @Test
    fun exportsComingSoonSeriesWithoutPublishingCompetitorDownloads() {
        val directory = Files.createTempDirectory("rom-desktop-public-results-coming-soon")
        val upcomingRace = raceData().copy(
            race = raceData().race.copy(
                name = "Upcoming Championship",
                startDateTimeIso = "2026-08-13T09:00",
                raceLevel = RaceLevel.NATIONAL
            )
        )

        val paths = DesktopProjectFiles.exportPublicResultsSeriesSite(
            directory = directory,
            seriesName = "Championship Week",
            races = listOf(
                DesktopPublicResultSeriesRace(EventProjectFile(raceData = upcomingRace))
            ),
            generatedAt = java.time.Instant.parse("2026-07-27T12:00:00Z")
        )

        val manifest = Files.readString(paths.publicResultsJson)
        val eventData = Files.readString(
            directory.resolve("2026-08-13-upcoming-championship/data/public-results.json")
        )
        val eventIndex = Files.readString(
            directory.resolve("2026-08-13-upcoming-championship/index.html")
        )
        val seriesIndex = Files.readString(paths.indexHtml)
        val rootIndex = Files.readString(paths.rootIndexHtml)
        assertEquals("2026-08-13-championship-week-series", paths.eventPath)
        assertTrue(manifest.contains("\"resultCount\":0"))
        assertTrue(manifest.contains("\"unofficialResults\":true"))
        assertTrue(eventData.contains("\"resultCount\": 0"))
        assertTrue(seriesIndex.contains("Championship Week"))
        assertTrue(seriesIndex.contains("Radio-Oracle Race Series preliminary results"))
        assertTrue(seriesIndex.contains("All published preliminary results"))
        assertTrue(eventIndex.contains("<h2>Preliminary Results Coming Soon</h2>"))
        assertTrue(eventIndex.contains("All published preliminary results"))
        assertTrue(rootIndex.contains("Preliminary Results Coming Soon"))
        assertFalse(
            Files.exists(
                directory.resolve("2026-08-13-upcoming-championship/downloads/final-results.json")
            )
        )
    }

    @Test
    fun keepsPracticeComingSoonPageFreeOfUnofficialDisclaimer() {
        val directory = Files.createTempDirectory("rom-desktop-practice-coming-soon")

        val paths = DesktopProjectFiles.exportPublicResultsSite(
            directory,
            EventProjectFile(raceData = raceData())
        )

        val eventIndex = Files.readString(paths.indexHtml)
        val rootIndex = Files.readString(paths.rootIndexHtml)
        assertTrue(eventIndex.contains("<h2>Preliminary Results Coming Soon</h2>"))
        assertTrue(rootIndex.contains("Preliminary Results Coming Soon"))
        assertFalse(eventIndex.contains("Unofficial"))
        assertFalse(rootIndex.contains("Unofficial"))
    }

    @Test
    fun retainsPreviousEventsAndOverwritesCurrentRaceByStableIdentity() {
        val directory = Files.createTempDirectory("rom-desktop-retained-public-results")
        val firstRace = EventProjectFile(raceData = raceData())
        val otherRace = EventProjectFile(
            raceData = raceData().copy(
                race = raceData().race.copy(
                    id = "other-race",
                    name = "Other Race",
                    startDateTimeIso = "2026-06-01T10:00"
                )
            )
        )
        val firstPaths = DesktopProjectFiles.exportPublicResultsSite(directory, firstRace)
        val otherPaths = DesktopProjectFiles.exportPublicResultsSite(directory, otherRace)
        val renamedRace = firstRace.copy(
            raceData = firstRace.raceData.copy(
                race = firstRace.raceData.race.copy(
                    name = "Renamed Desktop Race",
                    startDateTimeIso = "2026-06-02T10:00"
                )
            )
        )

        val renamedPaths = DesktopProjectFiles.exportPublicResultsSite(directory, renamedRace)

        val rootIndex = Files.readString(renamedPaths.rootIndexHtml)
        val racesJson = Files.readString(directory.resolve("data/races.json"))
        assertTrue(rootIndex.contains("Renamed Desktop Race"))
        assertTrue(rootIndex.contains("Other Race"))
        assertFalse(rootIndex.contains("Desktop File Race"))
        assertTrue(racesJson.contains("\"publicationId\":\"race:race\""))
        assertTrue(racesJson.contains("\"publicationId\":\"race:other-race\""))
        assertFalse(Files.exists(firstPaths.eventDirectory))
        assertTrue(Files.exists(otherPaths.eventDirectory))
        assertTrue(Files.exists(renamedPaths.eventDirectory))

        val publishedPaths = DesktopCloudflarePagesSiteReader.read(directory)
            .assets
            .map(DesktopCloudflarePagesAsset::relativePath)
        assertTrue(publishedPaths.any { it.startsWith("${otherPaths.eventPath}/") })
        assertTrue(publishedPaths.any { it.startsWith("${renamedPaths.eventPath}/") })
        assertFalse(publishedPaths.any { it.startsWith("${firstPaths.eventPath}/") })
    }

    @Test
    fun replacesRenamedSeriesByStableIdentity() {
        val directory = Files.createTempDirectory("rom-desktop-retained-series")
        val race = DesktopPublicResultSeriesRace(EventProjectFile(raceData = raceData()))
        val first = DesktopProjectFiles.exportPublicResultsSeriesSite(
            directory = directory,
            seriesName = "Original Series",
            seriesId = "series-id",
            races = listOf(race)
        )

        val renamed = DesktopProjectFiles.exportPublicResultsSeriesSite(
            directory = directory,
            seriesName = "Renamed Series",
            seriesId = "series-id",
            races = listOf(race)
        )

        val rootIndex = Files.readString(renamed.rootIndexHtml)
        assertTrue(rootIndex.contains("Renamed Series"))
        assertFalse(rootIndex.contains("Original Series"))
        assertFalse(Files.exists(first.eventDirectory))
        assertTrue(Files.exists(renamed.eventDirectory))
    }

    @Test
    fun selectsOnePublishedDrawingForEachDistinctResultCourse() {
        val base = raceDataWithReadout()
        val firstCategoryData = base.categories.single()
        val firstCompetitorData = base.competitorData.single()
        val secondCategory = firstCategoryData.category.copy(
            id = "category-two",
            name = "W21",
            order = 2
        )
        val secondCompetitor = firstCompetitorData.competitorCategory.competitor.copy(
            id = "competitor-two",
            categoryId = secondCategory.id,
            firstName = "Bob",
            siNumber = 654321
        )
        val firstReadoutData = requireNotNull(firstCompetitorData.readoutData)
        val secondCompetitorData = firstCompetitorData.copy(
            competitorCategory = EventCompetitorCategory(secondCompetitor, secondCategory),
            readoutData = firstReadoutData.copy(
                result = firstReadoutData.result.copy(
                    id = "result-two",
                    competitorId = secondCompetitor.id,
                    siNumber = secondCompetitor.siNumber
                )
            )
        )
        val projectFile = EventProjectFile(
            raceData = base.copy(
                categories = listOf(
                    firstCategoryData,
                    EventCategoryData(secondCategory, controlPoints = emptyList(), competitors = listOf(secondCompetitor))
                ),
                competitorData = listOf(firstCompetitorData, secondCompetitorData)
            )
        )

        val courses = publicResultCoursesForGraphics(
            DesktopPublicResultSeriesRace(
                projectFile = projectFile,
                protectedCourseInfoByCategoryId = mapOf(
                    firstCategoryData.category.id to ProtectedCourseInfo(sourceName = "course-one.kml"),
                    secondCategory.id to ProtectedCourseInfo(sourceName = "course-two.kml")
                )
            )
        )

        assertEquals(listOf("W21", "M21"), courses.map { it.categoryName })
        assertEquals(listOf("course-two.kml", "course-one.kml"), courses.map { it.courseInfo.sourceName })
    }

    @Test
    fun promptsForLockedCourseDataOnlyWhenPublishingResultsThatCouldUseIt() {
        fun withLockedCourseData(raceData: EventRaceData): EventProjectFile =
            EventProjectFile(
                raceData = raceData.copy(
                    categories = raceData.categories.map { categoryData ->
                        categoryData.copy(
                            category = categoryData.category.copy(encryptedCourseInfo = "locked-course-data")
                        )
                    }
                )
            )

        val lockedRaceWithResults = withLockedCourseData(raceDataWithReadout())
        val unlockedRaceWithResults = EventProjectFile(raceData = raceDataWithReadout())
        val plaintextRaceWithResults = EventProjectFile(
            raceData = raceDataWithReadout().copy(
                categories = raceDataWithReadout().categories.map { categoryData ->
                    categoryData.copy(
                        category = categoryData.category.copy(
                            courseInfo = ProtectedCourseInfo(sourceName = "plaintext-course.kml")
                        )
                    )
                }
            )
        )
        val lockedRaceWithoutResults = withLockedCourseData(
            raceDataWithReadout().copy(competitorData = emptyList())
        )

        assertTrue(publicResultsNeedCourseUnlock(listOf(lockedRaceWithResults), false))
        assertTrue(publicResultsNeedCourseUnlock(listOf(unlockedRaceWithResults, lockedRaceWithResults), false))
        assertFalse(publicResultsNeedCourseUnlock(listOf(lockedRaceWithResults), true))
        assertFalse(publicResultsNeedCourseUnlock(listOf(lockedRaceWithoutResults), false))
        assertFalse(publicResultsNeedCourseUnlock(listOf(unlockedRaceWithResults), false))
        assertFalse(publicResultsNeedCourseUnlock(listOf(plaintextRaceWithResults), false))
        assertEquals(
            "plaintext-course.kml",
            decryptedPublicResultsCourseState(
                listOf(plaintextRaceWithResults),
                plaintextRaceWithResults,
                ""
            ).protectedCourseInfoByCategoryId.values.single().sourceName
        )
    }

    @Test
    fun reusesProtectedCourseDecryptionForSeriesPublication() {
        val password = "series-password"
        val protectedInfo = ProtectedCourseInfo(sourceName = "series-course.kml")
        fun encryptedProject(raceName: String): EventProjectFile {
            val raceData = raceDataWithReadout()
            return EventProjectFile(
                raceData = raceData.copy(
                    race = raceData.race.copy(name = raceName),
                    categories = raceData.categories.map { categoryData ->
                        categoryData.copy(
                            category = categoryData.category.copy(
                                encryptedCourseInfo = DesktopProtectedCourseOrder.encryptCourseInfo(
                                    protectedInfo,
                                    password
                                )
                            )
                        )
                    }
                )
            )
        }
        val first = encryptedProject("First Series Race")
        val second = encryptedProject("Second Series Race")

        val state = decryptedPublicResultsCourseState(listOf(first, second), first, password)

        assertEquals("series-course.kml", state.protectedCourseInfoByCategoryId.getValue("category").sourceName)
        assertTrue(
            runCatching {
                decryptedPublicResultsCourseState(listOf(first, second), first, "wrong-password")
            }.isFailure
        )
    }

    @Test
    fun matchesResultCategoryToProtectedCourseMappingWhenIdsDiffer() {
        val raceData = raceDataWithReadout()
        val activeCategory = raceData.categories.single()
        val mappingCategoryId = "course-mapping-m21"
        val protectedInfo = ProtectedCourseInfo(sourceName = "mapped-m21.kml")
        val password = "mapping-password"
        val projectFile = EventProjectFile(
            raceData = raceData.copy(
                categories = listOf(
                    activeCategory.copy(
                        category = activeCategory.category.copy(encryptedCourseInfo = null)
                    )
                ),
                courseMappings = listOf(
                    activeCategory.copy(
                        category = activeCategory.category.copy(
                            id = mappingCategoryId,
                            encryptedCourseInfo = DesktopProtectedCourseOrder.encryptCourseInfo(protectedInfo, password)
                        )
                    )
                )
            )
        )

        assertTrue(publicResultsNeedCourseUnlock(listOf(projectFile), false))
        val decryptedCourseInfo = decryptedProtectedCourseState(projectFile, password).protectedCourseInfoByCategoryId
        assertEquals(protectedInfo, decryptedCourseInfo.getValue(mappingCategoryId))
        assertEquals(
            protectedInfo,
            protectedCourseInfoForResultCategory(
                projectFile = projectFile,
                protectedCourseInfoByCategoryId = decryptedCourseInfo,
                resultCategoryId = activeCategory.category.id,
                resultCategoryName = activeCategory.category.name
            )
        )
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
            drawnStartTimeSeconds = null,
            bibNumber = "1"
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
