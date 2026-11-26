package org.openardf.radiooracle.shared.course

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ElevationCoverageTest {
    private val area = ElevationBounds(0.0, 4.0, 0.0, 4.0)
    private fun solid(bounds: ElevationBounds) = ElevationCoverageGrid(bounds, 2, 2, true) { _, _ -> true }

    @Test fun adjacentTilesCoverAreaButFourCoveredCornersCanHideASeamGap() {
        val west = solid(area.copy(maxLongitude = 2.0))
        val east = solid(area.copy(minLongitude = 2.0))
        assertFalse(ElevationCoverage(listOf(west, east)).hasGap(area))
        val separated = ElevationCoverage(listOf(west, solid(area.copy(minLongitude = 2.000001))))
        assertTrue(separated.hasGap(area))
        assertTrue(separated.legHasGap(0.0, 0.0, 4.0, 4.0))
        assertFalse(separated.legHasGap(0.0, 2.0, 4.0, 2.0))
    }

    @Test fun nativeGridHolesIncludeIsolatedNodesAndCanBeFilledByOtherTiles() {
        val hole = ElevationCoverageGrid(area, 5, 5, false) { row, col -> row !in 1..2 || col !in 1..2 }
        val surface = ElevationCoverage(listOf(hole))
        assertTrue(surface.hasGap(area))
        assertTrue(surface.legHasGap(0.0, 2.5, 2.5, 0.0))
        assertFalse(surface.legHasGap(0.0, 0.0, 0.5, 0.5))
        val patch = solid(ElevationBounds(1.0, 2.0, 1.0, 2.0))
        assertFalse(ElevationCoverage(listOf(hole, patch)).hasGap(area))
        assertFalse(ElevationCoverage(listOf(patch, hole)).hasGap(area))
        val node = ElevationCoverage(listOf(ElevationCoverageGrid(area, 5, 5, false) { r, c -> r != 2 || c != 2 }))
        assertTrue(node.legHasGap(4.0, 0.0, 0.0, 4.0))
        assertFalse(node.legHasGap(0.0, 2.25, 4.0, 2.25))
    }

    @Test fun diagonalCourseCanCrossACommonCornerWhileTheEventBoxHasUncoveredQuadrants() {
        val sw = solid(ElevationBounds(-1.0, 0.0, -1.0, 0.0))
        val ne = solid(ElevationBounds(0.0, 1.0, 0.0, 1.0))
        val surface = ElevationCoverage(listOf(sw, ne))
        assertTrue(surface.hasGap(ElevationBounds(-1.0, 1.0, -1.0, 1.0)))
        assertFalse(surface.legHasGap(-1.0, -1.0, 1.0, 1.0))
        assertFalse(surface.legHasGap(1.0, 1.0, -1.0, -1.0))
    }

    @Test fun pointAndLineExtentsAndSingleRowTilesUseTheSameCoverageRule() {
        val surface = ElevationCoverage(listOf(ElevationCoverageGrid(area, 1, 5, false) { _, col -> col != 2 }))
        assertTrue(surface.hasGap(ElevationBounds(3.0, 3.0, 2.0, 2.0)))
        assertFalse(surface.hasGap(ElevationBounds(0.0, 4.0, 2.1, 2.1)))
        assertTrue(ElevationCoverage(emptyList()).hasGap(ElevationBounds(3.0, 3.0, 2.0, 2.0)))
    }
}
