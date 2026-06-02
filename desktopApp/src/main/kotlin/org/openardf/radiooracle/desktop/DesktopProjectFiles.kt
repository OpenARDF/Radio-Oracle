package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventProjectFileJson
import org.openardf.radiooracle.shared.files.ArdfJsonExports
import org.openardf.radiooracle.shared.files.EventCsvExports
import org.openardf.radiooracle.shared.files.FinalResultJsonExports
import org.openardf.radiooracle.shared.files.HtmlResultExports
import org.openardf.radiooracle.shared.files.IofXmlExports
import org.openardf.radiooracle.shared.files.LiveResultJsonExports
import org.openardf.radiooracle.shared.files.TextResultExports
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/** Desktop filesystem adapter for shared `.rom.json` project files. */
object DesktopProjectFiles : ProjectFileStore {
    /** Reads and decodes a project file from the supplied desktop filesystem path. */
    override fun read(path: Path): EventProjectFile =
        EventProjectFileJson.decode(Files.readString(path, StandardCharsets.UTF_8))

    /** Encodes and writes a project file, creating parent directories when needed. */
    override fun write(path: Path, projectFile: EventProjectFile) {
        path.parent?.let { Files.createDirectories(it) }
        Files.writeString(path, EventProjectFileJson.encode(projectFile), StandardCharsets.UTF_8)
    }

    fun exportCategoriesCsv(path: Path, projectFile: EventProjectFile) {
        writeText(path, EventCsvExports.categories(projectFile.raceData))
    }

    fun exportCompetitorsCsv(path: Path, projectFile: EventProjectFile) {
        writeText(path, EventCsvExports.competitors(projectFile.raceData))
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

    fun exportReadoutsCsv(path: Path, projectFile: EventProjectFile) {
        writeText(path, EventCsvExports.readouts(projectFile.raceData))
    }

    fun exportResultsCsv(path: Path, projectFile: EventProjectFile) {
        writeText(path, EventCsvExports.results(projectFile.raceData))
    }

    fun exportArdfJson(path: Path, projectFile: EventProjectFile) {
        writeText(path, ArdfJsonExports.event(projectFile.raceData.race.name, projectFile.raceData))
    }

    fun exportLiveResultsJson(path: Path, projectFile: EventProjectFile) {
        writeText(path, LiveResultJsonExports.results(projectFile.raceData))
    }

    fun exportFinalResultsJson(path: Path, projectFile: EventProjectFile) {
        writeText(path, FinalResultJsonExports.results(projectFile.raceData))
    }

    fun exportIofStartListXml(path: Path, projectFile: EventProjectFile) {
        writeText(path, IofXmlExports.startList(projectFile.raceData))
    }

    fun exportIofResultListXml(path: Path, projectFile: EventProjectFile) {
        writeText(path, IofXmlExports.resultList(projectFile.raceData))
    }

    fun exportResultsHtml(path: Path, projectFile: EventProjectFile) {
        writeText(path, HtmlResultExports.results(projectFile.raceData))
    }

    fun exportResultsText(path: Path, projectFile: EventProjectFile) {
        writeText(path, TextResultExports.results(projectFile.raceData))
    }

    private fun writeText(path: Path, text: String) {
        path.parent?.let { Files.createDirectories(it) }
        Files.writeString(path, text, StandardCharsets.UTF_8)
    }
}
