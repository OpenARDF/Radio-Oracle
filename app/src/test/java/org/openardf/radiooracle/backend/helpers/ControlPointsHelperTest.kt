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
    fun standaloneBeaconStaysSeparateFromTheFinalFox() {
        val aliases = listOf(Alias(41, "Fox 1"), Alias(42, "Fox 2"), Alias(136, "B"))
        val aliasesByCode = aliases.associateBy { it.siCode }
        listOf(
            "Fox1 Fox2 B",
            "Fox 1 Fox 2 B",
            "'Fox 1' 'Fox 2' B",
            "Fox 1, Fox 2, B",
            "Fox 1 Fox 2   B"
        ).forEach { input ->
            var text = input
            repeat(2) {
                val controls = ControlPointsHelper.getControlPointsFromDisplayString(
                    text, UUID.randomUUID(), RaceType.CLASSIC, aliases, mock(Context::class.java)
                )
                assertEquals("41 42 136B", ControlPointsHelper.getStringFromControlPoints(controls))
                text = ControlPointsHelper.formatEditableControlPointAliases(
                    controls.map { ControlPointAlias(it, aliasesByCode[it.siCode]) }, useAlias = true
                )
                assertEquals("'Fox 1' 'Fox 2' B", text)
            }
        }
    }

    @Test
    fun attachedBeaconMarkerStillAppliesToItsOwnAlias() {
        val controls = ControlPointsHelper.getControlPointsFromDisplayString(
            "Fox1 Fox2B", UUID.randomUUID(), RaceType.CLASSIC,
            listOf(Alias(41, "Fox 1"), Alias(42, "Fox 2"), Alias(136, "B")),
            mock(Context::class.java)
        )
        assertEquals("41 42B", ControlPointsHelper.getStringFromControlPoints(controls))
    }

    @Test
    fun parsesMultiwordAliasesWithSpacesQuotesOrCommas() {
        val raceId = UUID.randomUUID()
        val categoryId = UUID.randomUUID()
        val aliases = listOf(
            Alias(UUID.randomUUID(), raceId, 41, "Fox"),
            Alias(UUID.randomUUID(), raceId, 42, "Fox 2"),
            Alias(UUID.randomUUID(), raceId, 90, "Spectator control"),
            Alias(UUID.randomUUID(), raceId, 99, "Beacon control")
        )

        listOf(
            "Fox 2 Spectator control! Beacon controlB",
            "\"Fox 2\" \"Spectator control!\" \"Beacon controlB\"",
            "Fox 2, Spectator control!, Beacon controlB"
        ).forEach { input ->
            val controls = ControlPointsHelper.getControlPointsFromDisplayString(
                input, categoryId, RaceType.SPRINT, aliases, mock(Context::class.java)
            )
            assertEquals("42 90! 99B", ControlPointsHelper.getStringFromControlPoints(controls))
        }
    }

    @Test
    fun formattedMultiwordAliasesPreserveControlCodesAndMarkersWhenEdited() {
        val raceId = UUID.randomUUID()
        val categoryId = UUID.randomUUID()
        val aliases = listOf(
            Alias(UUID.randomUUID(), raceId, 42, "Fox 2"),
            Alias(UUID.randomUUID(), raceId, 90, "Spectator control"),
            Alias(UUID.randomUUID(), raceId, 99, "Beacon control")
        )
        val original = ControlPointsHelper.getControlPointsFromString(
            "42 90! 99B", categoryId, RaceType.SPRINT, mock(Context::class.java)
        )
        val text = ControlPointsHelper.formatEditableControlPointAliases(
            original.zip(aliases) { control, alias -> ControlPointAlias(control, alias) },
            useAlias = true
        )
        assertEquals("'Fox 2' 'Spectator control!' 'Beacon controlB'", text)

        val reparsed = ControlPointsHelper.getControlPointsFromDisplayString(
            text, categoryId, RaceType.SPRINT, aliases, mock(Context::class.java)
        )
        assertEquals("42 90! 99B", ControlPointsHelper.getStringFromControlPoints(reparsed))
    }

    @Test
    fun foxNamesWithOrWithoutSpacesResolveToTheConfiguredName() {
        listOf("Fox2", "Fox 2").forEach { configuredName ->
            val alias = Alias(42, configuredName)
            listOf("Fox2", "Fox 2", "fox2", "FOX  2", "Fox-2", "MOI").forEach { input ->
                val controls = ControlPointsHelper.getControlPointsFromDisplayString(
                    input, UUID.randomUUID(), RaceType.CLASSIC, listOf(alias), mock(Context::class.java)
                )
                assertEquals(listOf(42), controls.map { it.siCode })
                val canonicalText = ControlPointsHelper.formatEditableControlPointAliases(
                    controls.map { ControlPointAlias(it, alias) }, useAlias = true
                )
                assertEquals(configuredName, canonicalText.trim('\''))
            }
        }
    }

    @Test
    fun spacingVariantsPreserveSpectatorAndBeaconMarkers() {
        val controls = ControlPointsHelper.getControlPointsFromDisplayString(
            "Fox2 SpectatorControl! BeaconControlB", UUID.randomUUID(), RaceType.SPRINT,
            listOf(Alias(42, "Fox 2"), Alias(90, "Spectator Control"), Alias(99, "Beacon Control")),
            mock(Context::class.java)
        )
        assertEquals("42 90! 99B", ControlPointsHelper.getStringFromControlPoints(controls))
    }

    @Test
    fun separateNumericCodesAreNotCombinedIntoAnAlias() {
        val controls = ControlPointsHelper.getControlPointsFromDisplayString(
            "31 32", UUID.randomUUID(), RaceType.CLASSIC,
            listOf(Alias(42, "3132")), mock(Context::class.java)
        )
        assertEquals(listOf(31, 32), controls.map { it.siCode })
    }

    @Test
    fun exactNamesTakePrecedenceWhenSpacingVariantsReferToDifferentControls() {
        val controls = ControlPointsHelper.getControlPointsFromDisplayString(
            "Fox2 Fox 2", UUID.randomUUID(), RaceType.CLASSIC,
            listOf(Alias(41, "Fox2"), Alias(42, "Fox 2")), mock(Context::class.java)
        )
        assertEquals(listOf(41, 42), controls.map { it.siCode })
    }

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
