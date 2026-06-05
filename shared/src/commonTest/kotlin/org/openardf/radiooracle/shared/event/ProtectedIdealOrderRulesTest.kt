package org.openardf.radiooracle.shared.event

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProtectedIdealOrderRulesTest {
    @Test
    fun resolvesMixedControlCodesAndAliases() {
        val controls = listOf(
            EventControl("control-fox", "race", "Fox", 31, org.openardf.radiooracle.shared.domain.ControlPointType.CONTROL),
            EventControl("control-90b", "race", "90B", 90, org.openardf.radiooracle.shared.domain.ControlPointType.CONTROL),
            EventControl("control-512", "race", "512", 41, org.openardf.radiooracle.shared.domain.ControlPointType.CONTROL)
        )

        assertEquals(listOf(31, 32, 90, 511, 41), ProtectedIdealOrderRules.resolveControlCodes("Fox 32 90B 511 512", controls))
        assertEquals(31, ProtectedIdealOrderRules.firstControlCode("Fox 32 90B 511 512", controls))
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
