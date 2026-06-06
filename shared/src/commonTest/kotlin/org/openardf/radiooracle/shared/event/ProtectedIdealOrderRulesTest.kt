package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.domain.ControlPointType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProtectedIdealOrderRulesTest {
    @Test
    fun resolvesMixedControlCodesAndAliases() {
        val controls = listOf(
            EventControl("control-fox", "race", "Fox", 31, ControlPointType.CONTROL),
            EventControl("control-90b", "race", "90B", 90, ControlPointType.CONTROL),
            EventControl("control-512", "race", "512", 41, ControlPointType.CONTROL)
        )

        assertEquals(listOf(31, 32, 90, 511, 41), ProtectedIdealOrderRules.resolveControlCodes("Fox 32 90B 511 512", controls))
        assertEquals(31, ProtectedIdealOrderRules.firstControlCode("Fox 32 90B 511 512", controls))
    }

    @Test
    fun resolvesQuotedPublicLabelsAndExplicitDelimitedLabelsWithSpaces() {
        val controls = listOf(
            EventControl("control-31", "race", "F1", 31, ControlPointType.CONTROL, publicLabel = "Fox 1"),
            EventControl("control-32", "race", "F2", 32, ControlPointType.CONTROL, publicLabel = "Fox 2"),
            EventControl("control-beacon", "race", "M", 99, ControlPointType.BEACON, publicLabel = "Finish beacon")
        )

        assertEquals(
            listOf(31, 32, 99),
            ProtectedIdealOrderRules.resolveControlCodes("'Fox 1' \"Fox 2\" 'Finish beacon'", controls)
        )
        assertEquals(
            listOf(31, 32, 99),
            ProtectedIdealOrderRules.resolveControlCodes("Fox 1, Fox 2; Finish beacon", controls)
        )
    }

    @Test
    fun assignedCategoryValidationAllowsOnlyAssignedControls() {
        val assignedControls = listOf(
            EventControl("control-31", "race", "F1", 31, ControlPointType.CONTROL, publicLabel = "Fox 1"),
            EventControl("control-beacon", "race", "M", 99, ControlPointType.BEACON, publicLabel = "Beacon")
        )

        ProtectedIdealOrderRules.validateAssignedToCategory("'Fox 1' Beacon", assignedControls)

        assertFailsWith<IllegalArgumentException> {
            ProtectedIdealOrderRules.validateAssignedToCategory("'Fox 1' 32 Beacon", assignedControls)
        }
    }

    @Test
    fun assignedCategoryValidationRejectsDuplicateControls() {
        val assignedControls = listOf(
            EventControl("control-31", "race", "F1", 31, ControlPointType.CONTROL, publicLabel = "Fox 1")
        )

        assertFailsWith<IllegalArgumentException> {
            ProtectedIdealOrderRules.validateAssignedToCategory("'Fox 1' 31", assignedControls)
        }
    }

    @Test
    fun rejectsUnknownAliasTokens() {
        assertFailsWith<IllegalArgumentException> {
            ProtectedIdealOrderRules.resolveControlCodes("Fox 32", emptyList())
        }
    }

    @Test
    fun rejectsInvalidNumericControlCodes() {
        assertFailsWith<IllegalArgumentException> {
            ProtectedIdealOrderRules.resolveControlCodes("0", emptyList())
        }
    }

    @Test
    fun treatsNumericTokensAboveTheControlCodeMaximumAsAliasNames() {
        assertFailsWith<IllegalArgumentException> {
            ProtectedIdealOrderRules.resolveControlCodes("512", emptyList())
        }
    }
}
