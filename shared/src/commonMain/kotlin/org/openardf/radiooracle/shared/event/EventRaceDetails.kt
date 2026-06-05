package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.time.DurationFormatter

const val NATIONAL_RACE_TIME_LIMIT_MINUTES = 180L

/** Shared read-only race details prepared for Android and desktop presentation. */
data class EventRaceDetails(
    val name: String,
    val startDateTimeIso: String,
    val raceType: RaceType,
    val raceTypeLabel: String,
    val raceLevel: RaceLevel,
    val raceLevelLabel: String,
    val raceBand: RaceBand,
    val raceBandLabel: String,
    val timeLimitMinutesText: String,
    val timeLimitText: String
) {
    companion object {
        /** Builds display-ready race details from portable event race metadata. */
        fun from(race: EventRace): EventRaceDetails =
            EventRaceDetails(
                name = race.name,
                startDateTimeIso = race.startDateTimeIso,
                raceType = race.raceType,
                raceTypeLabel = race.raceType.toDisplayLabel(),
                raceLevel = race.raceLevel,
                raceLevelLabel = race.raceLevel.toDisplayLabel(),
                raceBand = race.raceBand,
                raceBandLabel = race.raceBand.toDisplayLabel(),
                timeLimitMinutesText = (race.timeLimitSeconds / 60).toString(),
                timeLimitText = DurationFormatter.secondsToFormattedString(race.timeLimitSeconds, useMinutes = true)
            )
    }
}

/** English race-type labels matching the existing Android default resources. */
fun RaceType.toDisplayLabel(): String =
    when (this) {
        RaceType.CLASSIC -> "Classic"
        RaceType.SHORT -> "Short"
        RaceType.SPRINT -> "Sprint"
        RaceType.FOXORING -> "Foxoring"
        RaceType.ORIENTEERING -> "Orienteering"
    }

/** English race-level labels matching the existing Android default resources. */
fun RaceLevel.toDisplayLabel(): String =
    when (this) {
        RaceLevel.INTERNATIONAL -> "International"
        RaceLevel.NATIONAL -> "National"
        RaceLevel.REGIONAL -> "Regional"
        RaceLevel.DISTRICT -> "District"
        RaceLevel.PRACTICE -> "Practice"
        RaceLevel.OTHER -> "Other"
    }

/** Returns the race-level time-limit default that should replace the current Limit field, if any. */
fun RaceLevel.defaultTimeLimitMinutes(): Long? =
    when (this) {
        RaceLevel.NATIONAL -> NATIONAL_RACE_TIME_LIMIT_MINUTES
        else -> null
    }

/** English race-band labels matching the existing Android default resources. */
fun RaceBand.toDisplayLabel(): String =
    when (this) {
        RaceBand.M80 -> "80m"
        RaceBand.M2 -> "2m"
        RaceBand.COMBINED -> "Combined"
        RaceBand.NONE -> "None"
    }
