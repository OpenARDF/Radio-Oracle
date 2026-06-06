package org.openardf.radiooracle.shared.course

import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.RaceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ControlPointRulesTest {
    @Test
    fun parsesAndFormatsControlPointStrings() {
        val controlPoints = ControlPointRules.parseControlPoints("31 32 36B", RaceType.CLASSIC)

        assertEquals(
            listOf(
                ControlPointDefinition(31, ControlPointType.CONTROL, 1),
                ControlPointDefinition(32, ControlPointType.CONTROL, 2),
                ControlPointDefinition(36, ControlPointType.BEACON, 3)
            ),
            controlPoints
        )
        assertEquals("31 32 36B", ControlPointRules.formatControlPoints(controlPoints))
    }

    @Test
    fun acceptsCommonControlPointSeparators() {
        val controlPoints = ControlPointRules.parseControlPoints("31, 32;36B", RaceType.CLASSIC)

        assertEquals(
            listOf(
                ControlPointDefinition(31, ControlPointType.CONTROL, 1),
                ControlPointDefinition(32, ControlPointType.CONTROL, 2),
                ControlPointDefinition(36, ControlPointType.BEACON, 3)
            ),
            controlPoints
        )
    }

    @Test
    fun normalizesAssignedClassicControlsWithoutImplyingRouteOrder() {
        val controlPoints = ControlPointRules.parseAssignedControlPoints("36B 33 31", RaceType.CLASSIC)

        assertEquals(
            listOf(
                ControlPointDefinition(31, ControlPointType.CONTROL, 3),
                ControlPointDefinition(33, ControlPointType.CONTROL, 2),
                ControlPointDefinition(36, ControlPointType.BEACON, 1)
            ),
            controlPoints
        )
        assertEquals("31 33 36B", ControlPointRules.formatControlPoints(controlPoints))
    }

    @Test
    fun normalizesAssignedSprintControlsWithSpectatorBetweenSlowAndFastFoxes() {
        val controlPoints = ControlPointRules.parseAssignedControlPoints("99B 42 31 46! 41 32", RaceType.SPRINT)

        assertEquals(
            listOf(
                ControlPointDefinition(31, ControlPointType.CONTROL, 3),
                ControlPointDefinition(32, ControlPointType.CONTROL, 6),
                ControlPointDefinition(46, ControlPointType.SEPARATOR, 4),
                ControlPointDefinition(41, ControlPointType.CONTROL, 5),
                ControlPointDefinition(42, ControlPointType.CONTROL, 2),
                ControlPointDefinition(99, ControlPointType.BEACON, 1)
            ),
            controlPoints
        )
        assertEquals("31 32 46! 41 42 99B", ControlPointRules.formatControlPoints(controlPoints))
    }

    @Test
    fun tokenizesQuotedControlLabels() {
        assertEquals(
            listOf("Fox 1", "32", "Fox 3"),
            ControlPointRules.tokenizeControlPoints("'Fox 1', 32; \"Fox 3\"")
        )
    }

    @Test
    fun tokenizesUnquotedControlLabelsWhenCommasOrSemicolonsAreUsed() {
        assertEquals(
            listOf("Fox 1", "Fox 2", "Beacon"),
            ControlPointRules.tokenizeControlPoints("Fox 1, Fox 2; Beacon")
        )
    }

    @Test
    fun rejectsUnknownSpecifiersAndOutOfRangeCodes() {
        assertEquals(
            ControlPointValidationError.UNKNOWN_SPECIFIER,
            assertFailsWith<ControlPointValidationException> {
                ControlPointRules.parseControlPoints("31X", RaceType.CLASSIC)
            }.error
        )
        assertEquals(
            listOf(ControlPointDefinition(511, ControlPointType.CONTROL, 1)),
            ControlPointRules.parseControlPoints("511", RaceType.CLASSIC)
        )
        assertEquals(
            ControlPointValidationError.INVALID_RANGE,
            assertFailsWith<ControlPointValidationException> {
                ControlPointRules.parseControlPoints("512", RaceType.CLASSIC)
            }.error
        )
    }

    @Test
    fun validatesClassicSequences() {
        assertEquals(
            ControlPointValidationError.CLASSIC_DUPLICATE,
            assertFailsWith<ControlPointValidationException> {
                ControlPointRules.parseControlPoints("31 32 31", RaceType.CLASSIC)
            }.error
        )
        assertEquals(
            ControlPointValidationError.NON_LAST_BEACON,
            assertFailsWith<ControlPointValidationException> {
                ControlPointRules.parseControlPoints("31 36B 32", RaceType.CLASSIC)
            }.error
        )
        assertEquals(
            ControlPointValidationError.CLASSIC_SPECTATOR_NOT_ALLOWED,
            assertFailsWith<ControlPointValidationException> {
                ControlPointRules.parseControlPoints("31 36!", RaceType.CLASSIC)
            }.error
        )
    }

    @Test
    fun validatesSprintSequences() {
        assertEquals(
            ControlPointValidationError.SPRINT_DUPLICATE,
            assertFailsWith<ControlPointValidationException> {
                ControlPointRules.parseControlPoints("31 32 31", RaceType.SPRINT)
            }.error
        )

        val multiLap = ControlPointRules.parseControlPoints("31 32 40! 31 32 36B", RaceType.SPRINT)
        assertEquals("31 32 40! 31 32 36B", ControlPointRules.formatControlPoints(multiLap))
    }

    @Test
    fun validatesOrienteeringSequences() {
        assertEquals(
            ControlPointValidationError.ORIENTEERING_SPECIAL,
            assertFailsWith<ControlPointValidationException> {
                ControlPointRules.parseControlPoints("31 36B", RaceType.ORIENTEERING)
            }.error
        )
        assertEquals(
            ControlPointValidationError.TWO_IN_ROW,
            assertFailsWith<ControlPointValidationException> {
                ControlPointRules.parseControlPoints("31 31", RaceType.ORIENTEERING)
            }.error
        )
    }

    @Test
    fun formatsDisplayTokensWithOptionalAliases() {
        val tokens = listOf(
            ControlPointDisplayToken(siCode = 31, aliasName = "F1"),
            ControlPointDisplayToken(siCode = 32, aliasName = null),
            ControlPointDisplayToken(siCode = 33, aliasName = "F3", include = false)
        )

        assertEquals("F1 32 ", ControlPointRules.formatDisplayTokens(tokens, useAlias = true))
        assertEquals("31 32 ", ControlPointRules.formatDisplayTokens(tokens, useAlias = false))
        assertEquals(
            "F1 32 ",
            ControlPointRules.formatIncludedDisplayTokensWithTrailingSpaces(tokens, useAlias = true)
        )
    }

    @Test
    fun formatsEditableDisplayTokensWithQuotesForLabelsContainingSeparators() {
        val tokens = listOf(
            ControlPointDisplayToken(siCode = 31, aliasName = "Fox 1"),
            ControlPointDisplayToken(siCode = 32, aliasName = "Fox, 2"),
            ControlPointDisplayToken(siCode = 99, aliasName = "Beacon")
        )

        assertEquals("'Fox 1' 'Fox, 2' Beacon", ControlPointRules.formatEditableDisplayTokens(tokens, useAlias = true))
        assertEquals("31 32 99", ControlPointRules.formatEditableDisplayTokens(tokens, useAlias = false))
    }
}
