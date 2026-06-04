package org.openardf.radiooracle.shared.event

import kotlinx.serialization.Serializable

/** Shared options for assigning competitor start times. */
@Serializable
data class StartDrawOptions(
    val clubHandling: StartDrawClubHandling = StartDrawClubHandling.AVOID_BACK_TO_BACK
)

/** Controls whether club membership influences start ordering. */
@Serializable
enum class StartDrawClubHandling {
    IGNORE,
    AVOID_BACK_TO_BACK
}
