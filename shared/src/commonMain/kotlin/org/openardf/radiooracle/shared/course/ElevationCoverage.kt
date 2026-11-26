package org.openardf.radiooracle.shared.course

import org.openardf.radiooracle.shared.event.CourseCoordinateRules
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.nextDown
import kotlin.math.nextUp
import kotlin.math.roundToInt

/** Closed geographic bounds. A single control or a collinear course can have zero extent. */
data class ElevationBounds(
    val minLatitude: Double, val maxLatitude: Double,
    val minLongitude: Double, val maxLongitude: Double
) {
    init {
        require(CourseCoordinateRules.isValidLatitude(minLatitude) && CourseCoordinateRules.isValidLatitude(maxLatitude)) {
            "Latitude values must be finite and between -90 and 90."
        }
        require(CourseCoordinateRules.isValidLongitude(minLongitude) && CourseCoordinateRules.isValidLongitude(maxLongitude)) {
            "Longitude values must be finite and between -180 and 180."
        }
        require(minLatitude <= maxLatitude) { "Minimum latitude must not exceed maximum latitude." }
        require(minLongitude <= maxLongitude) { "Minimum longitude must not exceed maximum longitude." }
    }

    fun intersection(other: ElevationBounds): ElevationBounds? {
        val south = maxOf(minLatitude, other.minLatitude)
        val north = minOf(maxLatitude, other.maxLatitude)
        val west = maxOf(minLongitude, other.minLongitude)
        val east = minOf(maxLongitude, other.maxLongitude)
        return if (south <= north && west <= east) ElevationBounds(south, north, west, east) else null
    }

    internal fun outside(inner: ElevationBounds): List<ElevationBounds> = buildList {
        // The seam belongs to the tile. Keep touching tiles and zero-width holes distinct.
        if (minLatitude < inner.minLatitude) add(copy(maxLatitude = inner.minLatitude.nextDown()))
        if (maxLatitude > inner.maxLatitude) add(copy(minLatitude = inner.maxLatitude.nextUp()))
        val strip = copy(minLatitude = inner.minLatitude, maxLatitude = inner.maxLatitude)
        if (minLongitude < inner.minLongitude) add(strip.copy(maxLongitude = inner.minLongitude.nextDown()))
        if (maxLongitude > inner.maxLongitude) add(strip.copy(minLongitude = inner.maxLongitude.nextUp()))
    }
}

/** A local terrain grid supplies elevation wherever its interpolation stencil has a usable node. */
class ElevationCoverageGrid(
    val bounds: ElevationBounds,
    private val rows: Int,
    private val columns: Int,
    internal val complete: Boolean,
    private val hasSample: (Int, Int) -> Boolean
) {
    private class Axis(val low: Double, val high: Double, val count: Int) {
        fun position(index: Int) = when (index) {
            0 -> low
            count - 1 -> high
            else -> low + (high - low) * index / (count - 1)
        }

        fun indices(from: Double, to: Double): IntRange {
            return floor(index(from)).toInt()..ceil(index(to)).toInt()
        }

        fun index(value: Double): Double {
            if (high == low || count <= 1) return 0.0
            val index = ((value - low) / (high - low) * (count - 1)).coerceIn(0.0, (count - 1).toDouble())
            val nearest = index.roundToInt()
            // Converting a stored grid coordinate back to an index can round just across a grid line.
            return if (value == position(nearest)) nearest.toDouble() else index
        }
    }

    private val lat = Axis(bounds.minLatitude, bounds.maxLatitude, rows)
    private val lon = Axis(bounds.minLongitude, bounds.maxLongitude, columns)

    fun rowPosition(latitude: Double): Double = lat.index(latitude)
    fun columnPosition(longitude: Double): Double = lon.index(longitude)

    internal fun anyHole(area: ElevationBounds, checkCancelled: () -> Unit, visit: (ElevationBounds) -> Boolean): Boolean {
        if (rows <= 0 || columns <= 0) return visit(area)
        if (complete) return false
        val rowRange = lat.indices(area.minLatitude, area.maxLatitude)
        val colRange = lon.indices(area.minLongitude, area.maxLongitude)
        for (row in rowRange) {
            checkCancelled()
            // At a grid line the lookup uses one row. Between lines both rows must lack samples.
            for (next in row..minOf(row + 1, rowRange.last)) {
                val south = maxOf(area.minLatitude, lat.position(row))
                val north = minOf(area.maxLatitude, if (rows == 1) lat.high else lat.position(next))
                if (south > north) continue
                var start = colRange.first
                for (col in colRange) {
                    if (hasSample(row, col) || hasSample(next, col)) {
                        start = col + 1
                        continue
                    }
                    if (col < colRange.last && !hasSample(row, col + 1) && !hasSample(next, col + 1)) continue
                    val west = maxOf(area.minLongitude, lon.position(start))
                    val east = minOf(area.maxLongitude, if (columns == 1) lon.high else lon.position(col))
                    if (west <= east && visit(ElevationBounds(south, north, west, east))) return true
                }
            }
        }
        return false
    }
}

/** Each subsequent tile can fill both uncovered territory and internal holes in earlier tiles. */
class ElevationCoverage(grids: List<ElevationCoverageGrid>, private val checkCancelled: () -> Unit = {}) {
    // A complete overlapping tile can settle the question without walking a high-resolution partial grid.
    private val grids = grids.sortedByDescending { it.complete }

    /** Clip the modeled leg to each gap, including point holes and gaps narrower than the profile sampling interval. */
    fun legHasGap(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Boolean {
        val bounds = ElevationBounds(minOf(fromLat, toLat), maxOf(fromLat, toLat), minOf(fromLon, toLon), maxOf(fromLon, toLon))
        return hasGap(bounds) { region ->
            var enter = 0.0
            var leave = 1.0
            fun clip(start: Double, end: Double, low: Double, high: Double): Boolean {
                if (start == end) return start in low..high
                val a = (low - start) / (end - start)
                val b = (high - start) / (end - start)
                enter = maxOf(enter, minOf(a, b))
                leave = minOf(leave, maxOf(a, b))
                return enter <= leave
            }
            if (!clip(fromLat, toLat, region.minLatitude, region.maxLatitude) ||
                !clip(fromLon, toLon, region.minLongitude, region.maxLongitude)) return@hasGap false
            if (enter < leave) return@hasGap true
            // Distinct coordinates immediately on either side of a tile corner can round to the
            // same fraction. Verify a tangency in geographic coordinates before reporting a gap.
            fun touches(start: Double, end: Double, low: Double, high: Double): Boolean =
                low == high || (start + (end - start) * enter) in low..high
            touches(fromLat, toLat, region.minLatitude, region.maxLatitude) &&
                touches(fromLon, toLon, region.minLongitude, region.maxLongitude)
        }
    }

    fun hasGap(area: ElevationBounds, intersects: (ElevationBounds) -> Boolean = { true }): Boolean {
        val local = grids.filter { it.bounds.intersection(area) != null }
        fun missing(region: ElevationBounds, index: Int): Boolean {
            checkCancelled()
            if (!intersects(region)) return false
            for (i in index until local.size) {
                val tile = local[i]
                val inside = tile.bounds.intersection(region) ?: continue
                return region.outside(inside).any { missing(it, i + 1) } ||
                    tile.anyHole(inside, checkCancelled) { missing(it, i + 1) }
            }
            return true
        }
        return missing(area, 0)
    }
}
