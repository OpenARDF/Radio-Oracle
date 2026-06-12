package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.event.EventProjectFile
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object DesktopImportBackups {
    private val TimestampFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    fun backupDirectory(appDataDirectory: Path = DesktopAppDirectories.appDataDirectory()): Path =
        appDataDirectory.resolve("import-backups")

    fun writeBackup(
        projectFile: EventProjectFile,
        currentEventPath: Path?,
        importTitle: String,
        appDataDirectory: Path = DesktopAppDirectories.appDataDirectory(),
        now: LocalDateTime = LocalDateTime.now()
    ): Path {
        val eventSlug = currentEventPath
            ?.fileName
            ?.toString()
            ?.removeSuffix(".rom.json")
            ?.removeSuffix(".json")
            ?.takeIf { it.isNotBlank() }
            ?: projectFile.raceData.race.name
        val filename = listOf(
            sanitizeFileToken(eventSlug).ifBlank { "event" },
            "before",
            sanitizeFileToken(importTitle).ifBlank { "import" },
            TimestampFormatter.format(now)
        ).joinToString("-") + ".rom.json"
        val backupPath = backupDirectory(appDataDirectory).resolve(filename)
        DesktopProjectFiles.write(backupPath, projectFile)
        return backupPath
    }

    private fun sanitizeFileToken(value: String): String =
        value
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(80)
}
