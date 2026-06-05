package org.openardf.radiooracle.shared.event

import kotlinx.serialization.Serializable

/** Shared options for assigning competitor start times. */
@Serializable
data class StartDrawOptions(
    val clubHandling: StartDrawClubHandling = StartDrawClubHandling.AVOID_BACK_TO_BACK,
    val startersPerStartTime: Int = 1,
    val idealFirstFoxByCategoryId: Map<String, Int> = emptyMap()
) {
    init {
        require(startersPerStartTime in MIN_STARTERS_PER_START_TIME..MAX_STARTERS_PER_START_TIME) {
            "Starters per start time must be between $MIN_STARTERS_PER_START_TIME and $MAX_STARTERS_PER_START_TIME."
        }
    }

    companion object {
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
