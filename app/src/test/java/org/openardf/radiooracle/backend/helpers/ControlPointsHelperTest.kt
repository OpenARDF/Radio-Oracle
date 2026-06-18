package org.openardf.radiooracle.backend.helpers

import android.content.Context
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import org.openardf.radiooracle.backend.room.entity.Alias
import org.openardf.radiooracle.backend.room.entity.ControlPoint
import org.openardf.radiooracle.backend.room.entity.embeddeds.ControlPointAlias
import org.openardf.radiooracle.backend.room.enums.ControlPointType
import org.openardf.radiooracle.backend.room.enums.RaceType
import java.util.UUID

class ControlPointsHelperTest {
    @Test
    fun parsesAliasControlTextBackToStoredSiCodes() {
        val raceId = UUID.randomUUID()
        val categoryId = UUID.randomUUID()
        val aliases = listOf(
            Alias(UUID.randomUUID(), raceId, 41, "F1"),
            Alias(UUID.randomUUID(), raceId, 42, "F2")
        )

        val controlPoints = ControlPointsHelper.getControlPointsFromDisplayString(
            "F1 F2",
            categoryId,
            RaceType.CLASSIC,
            aliases,
            mock(Context::class.java)
        )

        assertEquals("41 42", ControlPointsHelper.getStringFromControlPoints(controlPoints))
    }

    @Test
    fun preservesSpecialMarkersWhenParsingAliasControlText() {
        val raceId = UUID.randomUUID()
        val categoryId = UUID.randomUUID()
        val aliases = listOf(
            Alias(UUID.randomUUID(), raceId, 41, "F1"),
            Alias(UUID.randomUUID(), raceId, 90, "S1"),
            Alias(UUID.randomUUID(), raceId, 99, "B1")
        )

        val controlPoints = ControlPointsHelper.getControlPointsFromDisplayString(
            "F1 S1! B1B",
            categoryId,
            RaceType.SPRINT,
            aliases,
            mock(Context::class.java)
        )

        assertEquals("41 90! 99B", ControlPointsHelper.getStringFromControlPoints(controlPoints))
    }

    @Test
    fun formatsBeaconAliasWithoutDuplicatingMarker() {
        val raceId = UUID.randomUUID()
        val categoryId = UUID.randomUUID()
        val controlPoint = ControlPoint(
            UUID.randomUUID(),
            categoryId,
            136,
            ControlPointType.BEACON,
            1
        )
        val alias = Alias(UUID.randomUUID(), raceId, 136, "B")

        val display = ControlPointsHelper.formatEditableControlPointAliases(
            listOf(ControlPointAlias(controlPoint, alias)),
            useAlias = true
        )

        assertEquals("B", display)
    }

    @Test
    fun parsesStandaloneBeaconAliasAsBeacon() {
        val raceId = UUID.randomUUID()
        val categoryId = UUID.randomUUID()
        val aliases = listOf(Alias(UUID.randomUUID(), raceId, 136, "B"))

        val controlPoints = ControlPointsHelper.getControlPointsFromDisplayString(
            "B",
            categoryId,
            RaceType.SPRINT,
            aliases,
            mock(Context::class.java)
        )

        assertEquals("136B", ControlPointsHelper.getStringFromControlPoints(controlPoints))
    }
}
