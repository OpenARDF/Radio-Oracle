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
