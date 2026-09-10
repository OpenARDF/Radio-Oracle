package org.openardf.radiooracle.shared.event

import kotlinx.serialization.Serializable

/** Optional desktop-owned metadata. It does not participate in scoring or Android exports. */
@Serializable
data class StoredClassicRouteAnalysis(
    val version: Int = 1,
    val contexts: Map<String, ClassicRouteReference> = emptyMap(),
    val results: Map<String, ClassicRouteSnapshot> = emptyMap()
)

@Serializable
data class RouteElevationSource(
    val contentSha256: String,
    val name: String,
    val resolutionMeters: Double
)

@Serializable
data class ClassicRouteReference(
    val categoryId: String,
    val courseFingerprint: String,
    val method: String,
    val elevationSources: List<RouteElevationSource>,
    val idealOrderFingerprint: String,
    val horizontalMeters: Int,
    val climbMeters: Int,
    val effectiveMeters: Int,
    val calculatedAt: String,
    val permutations: Int,
    val previousEffectiveMeters: Int? = null,
    val practiceSavedDirection: Boolean = false
)

@Serializable
data class ClassicRouteSnapshot(
    val categoryId: String,
    val inputFingerprint: String,
    val contextId: String,
    val calculatedAt: String,
    val length: ResultRouteLength? = null,
    val unavailableReason: String? = null,
    val punchPolicy: String = "strict-v1",
    /** Zero-based indexes in CONTROL punches sorted by recorded order, bound by inputFingerprint. */
    val ignoredControlPunchIndexes: List<Int> = emptyList()
)

/** Explicit opt-in public projection; default shared exporters never discover private metadata. */
@Serializable
data class ResultRouteLength(
    val horizontalMeters: Int,
    val climbMeters: Int,
    val effectiveMeters: Int,
    val idealEffectiveMeters: Int,
    val comparison: String,
    @kotlinx.serialization.Transient val idealRoute: String? = null,
    @kotlinx.serialization.Transient val missingAssignedPunches: Boolean = false,
    val practiceSavedDirection: Boolean = false
) {
    val text: String get() = "${kilometers(effectiveMeters.toLong())} km (" +
        if (missingAssignedPunches) "missing punches)"
        else if (practiceSavedDirection && comparison == "Practice saved direction") "Practice saved direction)"
        else if (comparison == "Ideal order") "ideal order)"
        else "difference from ${if (practiceSavedDirection) "Practice reference" else "ideal order"}: ${kilometers(effectiveMeters.toLong() - idealEffectiveMeters)} km)"
    val categoryHeadingSuffix: String get() = " - ${if (practiceSavedDirection) "Practice reference route" else "Ideal route"}" +
        "${idealRoute?.let { ": $it" }.orEmpty()} (EL: ${kilometers(idealEffectiveMeters.toLong())} km)"
    companion object {
        const val LABEL = "Estimated effective route length"
        fun kilometers(meters: Long): String {
            val hundredths = (kotlin.math.abs(meters) + 5) / 10
            return (if (meters < 0 && hundredths != 0L) "-" else "") + "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}"
        }
        fun coverage(ready: Int, total: Int): String =
            "Estimated effective route lengths: $ready/$total available. Blank is not zero. Analysis ideal: terrain reference with known leg constraints. Other legs may require unmodeled detours."
    }
}
