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

/** Shared semantic comparison hook for Race Series packages from Android or desktop ZIP adapters. */
object EventSeriesPackageFingerprints {
    fun fromTextEntries(entries: Map<String, String>): EventSeriesPackageFingerprint {
        val normalizedEntries = entries.mapKeys { (path, _) ->
            EventSeriesPackageContents.normalizedPackagePath(path)
        }
        val manifestEntry = normalizedEntries.entries
            .filter { (path, _) -> isEventSeriesFileName(path.substringAfterLast('/')) }
            .also { manifestEntries ->
                require(manifestEntries.size == 1) {
                    "Race Series package must contain exactly one series manifest."
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
                        "Race Series package is missing Race File entry: $eventPath"
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
