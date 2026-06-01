package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventProjectFileJson
import org.openardf.radiooracle.shared.files.EventCsvExports
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

    fun exportReadoutsCsv(path: Path, projectFile: EventProjectFile) {
        writeText(path, EventCsvExports.readouts(projectFile.raceData))
    }

    private fun writeText(path: Path, text: String) {
        path.parent?.let { Files.createDirectories(it) }
        Files.writeString(path, text, StandardCharsets.UTF_8)
    }
}
