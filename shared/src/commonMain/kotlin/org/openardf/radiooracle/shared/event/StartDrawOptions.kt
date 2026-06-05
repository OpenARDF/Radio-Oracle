package org.openardf.radiooracle.shared.event

import kotlinx.serialization.Serializable
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.time.DurationFormatter

/**
 * Persisted start-list generator settings.
 *
 * These settings are part of the portable Event File data, not desktop-local
 * preferences. A race opened on another machine should show the same interval,
 * seed, club handling, and starters-per-time controls that were last saved with
 * the event.
 */
@Serializable
data class StartDrawSettings(
    val intervalSeconds: Long = DEFAULT_CLASSIC_INTERVAL_SECONDS,
    val options: StartDrawOptions = StartDrawOptions()
) {
    init {
        require(intervalSeconds > 0) {
            "Start interval must be greater than zero."
        }
    }

    val intervalText: String
        get() = DurationFormatter.secondsToFormattedString(intervalSeconds, useMinutes = true)

    companion object {
        const val DEFAULT_CLASSIC_INTERVAL_SECONDS = 5 * 60L
        const val DEFAULT_FAST_INTERVAL_SECONDS = 2 * 60L

        fun defaultFor(raceType: RaceType): StartDrawSettings =
            StartDrawSettings(
                intervalSeconds = when (raceType) {
                    RaceType.CLASSIC -> DEFAULT_CLASSIC_INTERVAL_SECONDS
                    RaceType.SPRINT, RaceType.FOXORING -> DEFAULT_FAST_INTERVAL_SECONDS
                    RaceType.SHORT, RaceType.ORIENTEERING -> DEFAULT_FAST_INTERVAL_SECONDS
                }
            )
    }
}

/**
 * Shared options for assigning competitor start times.
 *
 * The generator has two modes:
 * - `DEFAULT_SEED` keeps the traditional deterministic order based on category
 *   order, start number, and name. It is still visible and persisted so the UI
 *   never has an empty seed field.
 * - Any non-default seed activates repeatable pseudo-random tie-breaking. The
 *   rule filters are applied before seeded tie-breaks, so seeded draws remain
 *   constrained by category, club, starters-per-time, and first-fox safety rules
 *   whenever the field allows those rules to be satisfied.
 */
@Serializable
data class StartDrawOptions(
    val clubHandling: StartDrawClubHandling = StartDrawClubHandling.AVOID_BACK_TO_BACK,
    val startersPerStartTime: Int = 1,
    val seed: String = DEFAULT_SEED,
    val idealFirstFoxByCategoryId: Map<String, Int> = emptyMap()
) {
    init {
        require(startersPerStartTime in MIN_STARTERS_PER_START_TIME..MAX_STARTERS_PER_START_TIME) {
            "Starters per start time must be between $MIN_STARTERS_PER_START_TIME and $MAX_STARTERS_PER_START_TIME."
        }
    }

    /** Normalizes old files or UI edits that left the seed blank. */
    fun withDefaultSeed(): StartDrawOptions =
        if (seed.isBlank()) copy(seed = DEFAULT_SEED) else this

    companion object {
        const val DEFAULT_SEED = "default"
        const val MIN_STARTERS_PER_START_TIME = 1
        const val MAX_STARTERS_PER_START_TIME = 6
    }
}

/** Controls whether club membership influences start ordering. */
@Serializable
enum class StartDrawClubHandling {
    IGNORE,
    AVOID_BACK_TO_BACK
}

fun EventRaceData.effectiveStartDrawSettings(): StartDrawSettings =
    startDrawSettings ?: StartDrawSettings.defaultFor(race.raceType)
