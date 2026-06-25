package org.openardf.radiooracle.backend.commands

import org.openardf.radiooracle.shared.event.EventSeriesPackageEventFingerprint
import org.openardf.radiooracle.shared.event.EventSeriesPackageFingerprint

object EventSeriesCommandFingerprintLog {
    fun lines(
        source: String,
        byteCount: Int,
        fingerprint: EventSeriesPackageFingerprint
    ): List<String> =
        listOf(
            "series-package source=$source id=${fingerprint.seriesId} " +
                "name=${fingerprint.name} members=${fingerprint.events.size} bytes=$byteCount"
        ) + fingerprint.events.mapIndexed { index, event ->
            eventLine(source, fingerprint.seriesId, index + 1, event)
        }

    private fun eventLine(
        source: String,
        seriesId: String,
        position: Int,
        event: EventSeriesPackageEventFingerprint
    ): String =
        "series-package-member source=$source series=$seriesId position=$position " +
            "event=${event.seriesEventId} order=${event.order} path=${event.eventFilePath} " +
            "display=${event.displayName} start=${event.startDateTimeIso} format=${event.formatLabel} " +
            "race=${event.raceName} raceStart=${event.raceStartDateTimeIso} " +
            "type=${event.raceType} level=${event.raceLevel} band=${event.raceBand} " +
            "timeLimit=${event.timeLimitSeconds} link=${event.seriesLink?.seriesId}/${event.seriesLink?.seriesEventId}"
}
