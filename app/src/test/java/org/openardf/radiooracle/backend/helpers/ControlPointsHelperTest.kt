/*
 * MIT License
 *
 * Copyright (c) 2025 Pavel Kolský
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

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
