/*
 * MIT License
 *
 * Copyright (c) 2026 OpenARDF contributors
 */

package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.shared.sportident.SportIdentCardReadout
import org.openardf.radiooracle.shared.sportident.SportIdentProtocol

/** Race-local Practice download rules, shared by desktop and Android. */
object PracticeReadoutPolicy {
    fun duplicatePolicy(
        raceData: EventRaceData,
        readout: SportIdentCardReadout,
        configuredPolicy: EventReadoutDuplicatePolicy
    ): EventReadoutDuplicatePolicy = when {
        raceData.race.raceLevel != RaceLevel.PRACTICE -> configuredPolicy
        containsIdenticalReadout(raceData, readout) -> EventReadoutDuplicatePolicy.Reject
        else -> EventReadoutDuplicatePolicy.CreateNew
    }

    /** Compare recorded data, ignoring download time, identity text and calculated scoring. */
    fun containsIdenticalReadout(raceData: EventRaceData, readout: SportIdentCardReadout): Boolean =
        identicalReadout(raceData, readout) != null

    /** Returns the existing download so callers can recover an unmatched registration without duplicating it. */
    fun identicalReadout(raceData: EventRaceData, readout: SportIdentCardReadout): EventReadoutData? {
        val readouts = raceData.competitorData.mapNotNull { it.readoutData } + raceData.unmatchedReadoutData
        return readouts.firstOrNull { stored ->
            val result = stored.result
            // SI5 times are corrected from their twelve-hour clock during Android evaluation.
            fun normalized(seconds: Long?): Long? =
                if (result.cardType == SportIdentProtocol.SI_CARD5) seconds?.rem(12 * 3600L) else seconds
            val drawnStart = raceData.competitorData.firstOrNull {
                it.competitorCategory.competitor.id == result.competitorId
            }?.competitorCategory?.competitor?.drawnStartTimeSeconds
            val inferredStart = readout.startTime == null && drawnStart != null &&
                result.startTimeSeconds?.rem(86400L) ==
                raceStartSecondsOfDay(raceData.race.startDateTimeIso)?.let { (it + drawnStart) % 86400L }
            result.siNumber == readout.siNumber &&
                normalized(result.checkTimeSeconds) == normalized(readout.checkTime?.getSeconds()) &&
                (normalized(result.startTimeSeconds) == normalized(readout.startTime?.getSeconds()) || inferredStart) &&
                normalized(result.finishTimeSeconds) == normalized(readout.finishTime?.getSeconds()) &&
                stored.punches.map { it.punch }.filter { it.punchType == SIRecordType.CONTROL }
                    .sortedBy { it.order }.map { it.siCode to normalized(it.originalSiTimeSeconds) } ==
                readout.punches.map { it.siCode to normalized(it.siTime.getSeconds()) }
        }
    }


}

internal fun raceStartSecondsOfDay(startDateTimeIso: String): Long? {
    val time = startDateTimeIso.substringAfter('T', missingDelimiterValue = "")
        .substringBefore('.')
        .substringBefore('Z')
        .substringBefore('+')
        .substringBefore('-')
    if (time.isBlank()) {
        return null
    }
    val parts = time.split(":")
    if (parts.size < 2) {
        return null
    }
    val hour = parts[0].toLongOrNull() ?: return null
    val minute = parts[1].toLongOrNull() ?: return null
    val second = parts.getOrNull(2)?.toLongOrNull() ?: 0
    return if (hour in 0..23 && minute in 0..59 && second in 0..59) {
        hour * 3600 + minute * 60 + second
    } else {
        null
    }
}
