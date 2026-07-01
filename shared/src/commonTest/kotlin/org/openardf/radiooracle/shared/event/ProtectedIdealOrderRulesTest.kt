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
    fun resolvesSlotEquivalentPublicLabelsToAssignedControls() {
        val controls = listOf(
            EventControl("control-fox-1", "race", "F1", 101, ControlPointType.CONTROL, publicLabel = "Fox 1"),
            EventControl("control-fox-2", "race", "fox-2", 102, ControlPointType.CONTROL, publicLabel = "Fox2"),
            EventControl("control-beacon", "race", "M", 99, ControlPointType.BEACON, publicLabel = "Beacon")
        )

        assertEquals(
            listOf("control-fox-1", "control-fox-2", "control-beacon"),
            ProtectedIdealOrderRules.resolveControlIds("31 32 Beacon", controls)
        )
        assertEquals(
            listOf("control-fox-1", "control-fox-2", "control-beacon"),
            ProtectedIdealOrderRules.resolveControlIds("MOE moi Beacon", controls)
        )
        assertEquals(
            listOf("control-sprint-1"),
            ProtectedIdealOrderRules.resolveControlIds(
                "S1",
                listOf(EventControl("control-sprint-1", "race", "S1", 201, ControlPointType.CONTROL, publicLabel = "Sprint 1"))
            )
        )
    }

    @Test
    fun storedLabelsTakePrecedenceOverConflictingPublicLabels() {
        val controls = listOf(
            EventControl("control-a", "race", "31", 31, ControlPointType.CONTROL, publicLabel = "33"),
            EventControl("control-b", "race", "32", 32, ControlPointType.CONTROL, publicLabel = "32"),
            EventControl("control-c", "race", "33", 33, ControlPointType.CONTROL, publicLabel = "31")
        )

        assertEquals(
            listOf("control-a", "control-b", "control-c"),
            ProtectedIdealOrderRules.resolveControlIds("31 32 33", controls)
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
