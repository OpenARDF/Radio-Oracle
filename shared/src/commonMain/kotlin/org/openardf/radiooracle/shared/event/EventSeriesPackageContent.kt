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

package org.openardf.radiooracle.shared.event

const val EVENT_SERIES_PACKAGE_CONTENT_TYPE = "application/zip"

data class EventSeriesPackageEventFile(
    val event: EventSeriesEvent,
    val projectFile: EventProjectFile
)

data class EventSeriesPackageTextEntry(
    val path: String,
    val text: String
)

data class EventSeriesPackageContent(
    val fileName: String,
    val entries: List<EventSeriesPackageTextEntry>
)

enum class EventSeriesPackageEntryKind {
    MANIFEST,
    EVENT_FILE,
    IGNORED
}

data class EventSeriesPackageEntry(
    val path: String,
    val kind: EventSeriesPackageEntryKind
)

object EventSeriesPackageContents {
    fun build(
        seriesFile: EventSeriesFile,
        eventFiles: List<EventSeriesPackageEventFile>,
        manifestEntryPath: String = EVENT_SERIES_FILE_NAME,
        packageFileNameStem: String = seriesFile.name
    ): EventSeriesPackageContent {
        val eventsById = eventFiles.associateBy { it.event.seriesEventId }
        val sortedEvents = seriesFile.sortedEvents()
        require(sortedEvents.isNotEmpty()) {
            "Event Series package requires at least one event."
        }
        require(eventFiles.size == sortedEvents.size && sortedEvents.all { it.seriesEventId in eventsById }) {
            "Event Series package must include one Event File for each manifest event."
        }

        val entries = buildList {
            add(
                EventSeriesPackageTextEntry(
                    path = normalizedPackagePath(manifestEntryPath),
                    text = EventSeriesFileJson.encode(seriesFile)
                )
            )
            sortedEvents.forEach { event ->
                val eventFile = requireNotNull(eventsById[event.seriesEventId]) {
                    "Missing Event File for '${event.displayName}'."
                }
                add(
                    EventSeriesPackageTextEntry(
                        path = normalizedPackagePath(event.eventFilePath),
                        text = EventProjectFileJson.encode(eventFile.projectFile)
                    )
                )
            }
        }
        return EventSeriesPackageContent(
            fileName = "${safePackageFileStem(packageFileNameStem)}.zip",
            entries = entries
        )
    }

    fun safePackageFileStem(name: String): String =
        name
            .substringBeforeLast(".zip")
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "event-series" }

    fun normalizedPackagePath(path: String): String {
        val packagePath = path.replace('\\', '/')
        require(!packagePath.startsWith("/")) {
            "Event Series package contains an unsafe path: $path"
        }
        val segments = packagePath.split('/').filterNot { it == "." }
        require(segments.isNotEmpty() && segments.none { it == ".." || it.isBlank() }) {
            "Event Series package contains an unsafe path: $path"
        }
        return segments.joinToString("/")
    }

    fun classifyEntryPath(path: String): EventSeriesPackageEntry {
        val normalizedPath = normalizedPackagePath(path)
        val fileName = normalizedPath.substringAfterLast('/')
        val kind = when {
            normalizedPath.startsWith("__MACOSX/") -> EventSeriesPackageEntryKind.IGNORED
            isEventSeriesFileName(fileName) -> EventSeriesPackageEntryKind.MANIFEST
            isEventFileName(fileName) -> EventSeriesPackageEntryKind.EVENT_FILE
            else -> EventSeriesPackageEntryKind.IGNORED
        }
        return EventSeriesPackageEntry(normalizedPath, kind)
    }

    private fun isEventFileName(fileName: String): Boolean =
        fileName.endsWith(".json", ignoreCase = true) ||
            fileName.endsWith(".rom.json", ignoreCase = true)
}
