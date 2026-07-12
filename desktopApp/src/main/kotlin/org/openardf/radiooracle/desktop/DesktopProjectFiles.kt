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

import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventProjectFileJson
import org.openardf.radiooracle.shared.event.EventAwardDisplayMode
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.files.ArdfJsonExports
import org.openardf.radiooracle.shared.files.EventCsvExports
import org.openardf.radiooracle.shared.files.FinalResultJsonExports
import org.openardf.radiooracle.shared.files.HtmlResultExports
import org.openardf.radiooracle.shared.files.IofXmlExports
import org.openardf.radiooracle.shared.files.LiveResultJsonExports
import org.openardf.radiooracle.shared.files.ResultReportExports
import org.openardf.radiooracle.shared.files.RaceBackupJsonImports
import org.openardf.radiooracle.shared.files.RaceBackupJsonExports
import org.openardf.radiooracle.shared.files.TextResultExports
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/** Desktop filesystem adapter for shared `.rom.json` Race Files. */
object DesktopProjectFiles : ProjectFileStore {
    /** Reads and decodes a Race File from the supplied desktop filesystem path. */
    override fun read(path: Path): EventProjectFile =
        EventProjectFileJson.decode(Files.readString(path, StandardCharsets.UTF_8))

    /** Encodes and writes a Race File, creating parent directories when needed. */
    override fun write(path: Path, projectFile: EventProjectFile) {
        path.parent?.let { Files.createDirectories(it) }
        Files.writeString(path, EventProjectFileJson.encode(projectFile), StandardCharsets.UTF_8)
    }

    fun importAndroidRaceBackupJson(path: Path, idFactory: () -> String): EventProjectFile =
        RaceBackupJsonImports.projectFile(Files.readString(path, StandardCharsets.UTF_8), idFactory)

    fun exportCategoriesCsv(path: Path, projectFile: EventProjectFile, includeEncryptedIdealOrder: Boolean = false) {
        writeText(path, EventCsvExports.categories(projectFile.raceData, includeEncryptedIdealOrder))
    }

    fun exportCompetitorsCsv(path: Path, projectFile: EventProjectFile) {
        writeText(path, EventCsvExports.competitors(projectFile.raceData))
    }

    fun exportControlsCsv(path: Path, projectFile: EventProjectFile) {
        writeText(path, EventCsvExports.controls(projectFile.raceData))
    }

    fun exportCompetitorStartsCsv(path: Path, projectFile: EventProjectFile) {
        writeText(path, EventCsvExports.competitorStarts(projectFile.raceData))
    }

    fun exportCompetitorStartsByCategoryCsv(path: Path, projectFile: EventProjectFile) {
        writeText(path, EventCsvExports.competitorStartsByCategory(projectFile.raceData))
    }

    fun exportCompetitorStartsByMinuteCsv(path: Path, projectFile: EventProjectFile) {
        writeText(path, EventCsvExports.competitorStartsByMinute(projectFile.raceData))
    }

    fun exportRobisStartListCsv(path: Path, projectFile: EventProjectFile) {
        writeText(path, EventCsvExports.robisStartList(projectFile.raceData))
    }

    fun exportPrintableStartListPdf(path: Path, projectFile: EventProjectFile) {
        DesktopPrintableStartListPdf.exportPdf(path, projectFile)
    }

    fun exportReadoutsCsv(path: Path, projectFile: EventProjectFile) {
        writeText(path, EventCsvExports.readouts(projectFile.raceData))
    }

    fun exportResultsCsv(
        path: Path,
        projectFile: EventProjectFile,
        awardDisplayMode: EventAwardDisplayMode = EventAwardDisplayMode.FIRST_TO_THIRD
    ) {
        writeText(path, EventCsvExports.results(projectFile.raceData, awardDisplayMode))
    }

    fun exportArdfEventResultsCsv(path: Path, projectFile: EventProjectFile) {
        writeText(path, EventCsvExports.ardfEventResults(projectFile.raceData))
    }

    fun exportArdfJson(path: Path, projectFile: EventProjectFile) {
        writeText(path, ArdfJsonExports.event(projectFile.raceData.race.name, projectFile.raceData, emptyMap()))
    }

    fun exportAndroidRaceBackupJson(path: Path, projectFile: EventProjectFile) {
        writeText(path, RaceBackupJsonExports.race(projectFile.raceData))
    }

    fun exportLiveResultsJson(path: Path, projectFile: EventProjectFile) {
        writeText(path, LiveResultJsonExports.results(projectFile.raceData))
    }

    fun exportFinalResultsJson(
        path: Path,
        projectFile: EventProjectFile,
        protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>? = null,
        awardDisplayMode: EventAwardDisplayMode = EventAwardDisplayMode.FIRST_TO_THIRD
    ) {
        writeText(path, FinalResultJsonExports.results(projectFile.raceData, protectedCourseInfoByCategoryId, awardDisplayMode))
    }

    fun exportIofStartListXml(path: Path, projectFile: EventProjectFile) {
        writeText(path, IofXmlExports.startList(projectFile.raceData, protectedCourseInfoByCategoryId = emptyMap()))
    }

    fun exportIofCourseDataXml(path: Path, projectFile: EventProjectFile) {
        writeText(path, IofXmlExports.courseData(projectFile.raceData))
    }

    fun exportIofEntryListXml(path: Path, projectFile: EventProjectFile) {
        writeText(path, IofXmlExports.entryList(projectFile.raceData))
    }

    fun exportIofResultListXml(path: Path, projectFile: EventProjectFile) {
        writeText(path, IofXmlExports.resultList(projectFile.raceData))
    }

    fun exportResultsHtml(
        path: Path,
        projectFile: EventProjectFile,
        protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>? = null,
        awardDisplayMode: EventAwardDisplayMode = EventAwardDisplayMode.FIRST_TO_THIRD
    ) {
        writeText(
            path,
            HtmlResultExports.results(
                projectFile.raceData,
                protectedCourseInfoByCategoryId = protectedCourseInfoByCategoryId,
                awardDisplayMode = awardDisplayMode
            )
        )
    }

    fun exportResultReportHtml(
        path: Path,
        projectFile: EventProjectFile,
        protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>? = null,
        awardDisplayMode: EventAwardDisplayMode = EventAwardDisplayMode.FIRST_TO_THIRD
    ) {
        writeText(
            path,
            ResultReportExports.html(
                projectFile.raceData,
                protectedCourseInfoByCategoryId = protectedCourseInfoByCategoryId,
                awardDisplayMode = awardDisplayMode
            )
        )
    }

    fun exportResultReportXml(
        path: Path,
        projectFile: EventProjectFile,
        protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>? = null,
        awardDisplayMode: EventAwardDisplayMode = EventAwardDisplayMode.FIRST_TO_THIRD
    ) {
        writeText(
            path,
            ResultReportExports.xml(
                projectFile.raceData,
                protectedCourseInfoByCategoryId = protectedCourseInfoByCategoryId,
                awardDisplayMode = awardDisplayMode
            )
        )
    }

    fun exportResultReportPdf(
        path: Path,
        projectFile: EventProjectFile,
        protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>? = null,
        awardDisplayMode: EventAwardDisplayMode = EventAwardDisplayMode.FIRST_TO_THIRD
    ) {
        DesktopResultReportPdf.exportPdf(path, projectFile, protectedCourseInfoByCategoryId, awardDisplayMode)
    }

    fun exportResultsText(
        path: Path,
        projectFile: EventProjectFile,
        protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>? = null,
        awardDisplayMode: EventAwardDisplayMode = EventAwardDisplayMode.FIRST_TO_THIRD
    ) {
        writeText(
            path,
            TextResultExports.results(
                projectFile.raceData,
                protectedCourseInfoByCategoryId = protectedCourseInfoByCategoryId,
                awardDisplayMode = awardDisplayMode
            )
        )
    }

    fun exportPublicResultsSite(
        directory: Path,
        projectFile: EventProjectFile,
        protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>? = null,
        awardDisplayMode: EventAwardDisplayMode = EventAwardDisplayMode.FIRST_TO_THIRD
    ): DesktopPublicResultSiteExportPaths =
        DesktopPublicResultSiteExports.export(
            directory = directory,
            projectFile = projectFile,
            protectedCourseInfoByCategoryId = protectedCourseInfoByCategoryId,
            awardDisplayMode = awardDisplayMode
        )

    fun exportPublicResultsSeriesSite(
        directory: Path,
        seriesName: String,
        races: List<DesktopPublicResultSeriesRace>,
        generatedAt: java.time.Instant = java.time.Instant.now()
    ): DesktopPublicResultSiteExportPaths =
        DesktopPublicResultSiteExports.exportSeries(
            directory = directory,
            seriesName = seriesName,
            races = races,
            generatedAt = generatedAt
        )

    private fun writeText(path: Path, text: String) {
        path.parent?.let { Files.createDirectories(it) }
        Files.writeString(path, text, StandardCharsets.UTF_8)
    }
}
