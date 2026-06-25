package org.openardf.radiooracle.shared.event

data class EventSeriesPackageFingerprint(
    val seriesId: String,
    val name: String,
    val events: List<EventSeriesPackageEventFingerprint>,
    val competitorMatchOverrides: List<EventSeriesCompetitorMatchOverride>
)

data class EventSeriesPackageEventFingerprint(
    val seriesEventId: String,
    val eventFilePath: String,
    val order: Int,
    val displayName: String,
    val startDateTimeIso: String,
    val formatLabel: String,
    val raceName: String,
    val raceStartDateTimeIso: String,
    val raceType: String,
    val raceLevel: String,
    val raceBand: String,
    val timeLimitSeconds: Long,
    val seriesLink: EventSeriesLink?
)

/** Shared semantic comparison hook for Event Series packages from Android or desktop ZIP adapters. */
object EventSeriesPackageFingerprints {
    fun fromTextEntries(entries: Map<String, String>): EventSeriesPackageFingerprint {
        val normalizedEntries = entries.mapKeys { (path, _) ->
            EventSeriesPackageContents.normalizedPackagePath(path)
        }
        val manifestEntry = normalizedEntries.entries
            .filter { (path, _) -> isEventSeriesFileName(path.substringAfterLast('/')) }
            .also { manifestEntries ->
                require(manifestEntries.size == 1) {
                    "Event Series package must contain exactly one series manifest."
                }
            }
            .single()
        val seriesFile = EventSeriesFileJson.decode(manifestEntry.value)

        return EventSeriesPackageFingerprint(
            seriesId = seriesFile.seriesId,
            name = seriesFile.name,
            events = seriesFile.sortedEvents().map { event ->
                val eventPath = EventSeriesPackageContents.normalizedPackagePath(event.eventFilePath)
                val projectFile = EventProjectFileJson.decode(
                    requireNotNull(normalizedEntries[eventPath]) {
                        "Event Series package is missing Event File entry: $eventPath"
                    }
                )
                EventSeriesPackageEventFingerprint(
                    seriesEventId = event.seriesEventId,
                    eventFilePath = eventPath,
                    order = event.order,
                    displayName = event.displayName,
                    startDateTimeIso = event.startDateTimeIso,
                    formatLabel = event.formatLabel,
                    raceName = projectFile.raceData.race.name,
                    raceStartDateTimeIso = projectFile.raceData.race.startDateTimeIso,
                    raceType = projectFile.raceData.race.raceType.name,
                    raceLevel = projectFile.raceData.race.raceLevel.name,
                    raceBand = projectFile.raceData.race.raceBand.name,
                    timeLimitSeconds = projectFile.raceData.race.timeLimitSeconds,
                    seriesLink = projectFile.seriesLink
                )
            },
            competitorMatchOverrides = seriesFile.competitorMatchOverrides
        )
    }
}
