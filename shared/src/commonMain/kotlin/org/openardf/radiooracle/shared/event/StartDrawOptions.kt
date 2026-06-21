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
    val options: StartDrawOptions = StartDrawOptions(),
    val lockedForSeriesOptimization: Boolean = false
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
 *   order, start number, and name. Desktop generation normally supplies a hidden
 *   non-default seed so each button press can look for a fresh start order.
 * - Any non-default seed activates repeatable pseudo-random tie-breaking. The
 *   rule filters are applied before seeded tie-breaks, so seeded draws remain
 *   constrained by category, club, starters-per-time, and first-fox safety rules
 *   whenever the field allows those rules to be satisfied.
 * - Preferred-thirds mode honors explicit competitor start-third assignments
 *   used by championship-style draws. It is opt-in because most local events do
 *   not collect that extra nomination data.
 */
@Serializable
data class StartDrawOptions(
    val clubHandling: StartDrawClubHandling = StartDrawClubHandling.AVOID_BACK_TO_BACK,
    val startersPerStartTime: Int = 1,
    val seed: String = DEFAULT_SEED,
    val startGroupMode: StartDrawStartGroupMode = StartDrawStartGroupMode.DISABLED,
    val idealFirstFoxByCategoryId: Map<String, Int> = emptyMap()
) {
    init {
        require(startersPerStartTime in MIN_STARTERS_PER_START_TIME..MAX_STARTERS_PER_START_TIME) {
            "Starters per start time must be between $MIN_STARTERS_PER_START_TIME and $MAX_STARTERS_PER_START_TIME."
        }
    }

    /** Normalizes old files or UI paths that left the internal seed blank. */
    fun withDefaultSeed(): StartDrawOptions =
        if (seed.isBlank()) copy(seed = DEFAULT_SEED) else this

    /** True when the Event File already uses the Start List defaults expected for National events. */
    fun hasNationalEventDefaults(): Boolean =
        clubHandling == StartDrawClubHandling.IGNORE &&
            startersPerStartTime == NATIONAL_EVENT_STARTERS_PER_START_TIME &&
            startGroupMode == StartDrawStartGroupMode.DISABLED

    /**
     * Returns the options an event-level Generate Start List action should use.
     *
     * Series balancing may store generated preferred thirds in the Event File so
     * the Series workflow can inspect its own target. Those series targets are
     * not event-level constraints: when an organizer redraws from the Event
     * Start List page, the event rules should govern and the separate Series
     * Start Fairness score should show the series tradeoff.
     */
    fun forEventStartListGeneration(): StartDrawOptions =
        if (startGroupMode == StartDrawStartGroupMode.BALANCED_MULTI_DAY_THIRDS) {
            copy(startGroupMode = StartDrawStartGroupMode.DISABLED)
        } else {
            this
        }

    /**
     * Applies the Start List policy Radio-Oracle expects for National events.
     *
     * The race-level default is intentionally limited to the three values that
     * define the National draw profile: ignore club separation, start two
     * competitors per start time, and do not use start groups. Seed and other
     * generator fields are preserved so accepting the prompt does not silently
     * discard repeatability choices unrelated to the National profile.
     */
    fun withNationalEventDefaults(): StartDrawOptions =
        copy(
            clubHandling = StartDrawClubHandling.IGNORE,
            startersPerStartTime = NATIONAL_EVENT_STARTERS_PER_START_TIME,
            startGroupMode = StartDrawStartGroupMode.DISABLED
        )

    companion object {
        const val DEFAULT_SEED = "default"
        const val MIN_STARTERS_PER_START_TIME = 1
        const val MAX_STARTERS_PER_START_TIME = 6
        const val NATIONAL_EVENT_STARTERS_PER_START_TIME = 2
    }
}

/** Controls whether club membership influences start ordering. */
@Serializable
enum class StartDrawClubHandling {
    IGNORE,
    AVOID_BACK_TO_BACK
}

/**
 * Controls whether the generator should honor championship-style preferred
 * start thirds assigned to individual competitors.
 */
@Serializable
enum class StartDrawStartGroupMode {
    DISABLED,
    PREFERRED_THIRDS,
    BALANCED_MULTI_DAY_THIRDS
}

fun EventRaceData.effectiveStartDrawSettings(): StartDrawSettings =
    startDrawSettings ?: StartDrawSettings.defaultFor(race.raceType)
