package org.openardf.radiooracle.shared.event

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProtectedIdealOrderRulesTest {
    @Test
    fun resolvesMixedControlCodesAndAliases() {
        val aliases = listOf(
            EventAlias("alias-fox", "race", 31, "Fox"),
            EventAlias("alias-90b", "race", 90, "90B"),
            EventAlias("alias-256", "race", 41, "256")
        )

        assertEquals(listOf(31, 32, 90, 41), ProtectedIdealOrderRules.resolveControlCodes("Fox 32 90B 256", aliases))
        assertEquals(31, ProtectedIdealOrderRules.firstControlCode("Fox 32 90B 256", aliases))
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
            ProtectedIdealOrderRules.resolveControlCodes("256", emptyList())
        }
    }
}
