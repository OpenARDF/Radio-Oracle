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
}
