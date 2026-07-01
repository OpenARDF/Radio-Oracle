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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.openardf.radiooracle.shared.domain.ControlPointType

class ControlRoleLabelRulesTest {
    @Test
    fun infersSpecialRolesFromPublicLabels() {
        assertEquals(ControlPointType.BEACON, ControlRoleLabelRules.inferredSpecialRole("B"))
        assertEquals(ControlPointType.BEACON, ControlRoleLabelRules.inferredSpecialRole("Finish beacon"))
        assertEquals(ControlPointType.SEPARATOR, ControlRoleLabelRules.inferredSpecialRole("S"))
        assertEquals(ControlPointType.SEPARATOR, ControlRoleLabelRules.inferredSpecialRole("Spectator"))
        assertNull(ControlRoleLabelRules.inferredSpecialRole("Fox 1"))
    }

    @Test
    fun infersFoxRolesFromPublicLabels() {
        assertEquals(ControlPointType.CONTROL, ControlRoleLabelRules.inferredRole("Fox 1"))
        assertEquals(ControlPointType.CONTROL, ControlRoleLabelRules.inferredRole("Fox-1"))
        assertEquals(ControlPointType.CONTROL, ControlRoleLabelRules.inferredRole("1"))
        assertEquals(ControlPointType.CONTROL, ControlRoleLabelRules.inferredRole("1F"))
        assertEquals(ControlPointType.CONTROL, ControlRoleLabelRules.inferredRole("F1"))
        assertEquals(ControlPointType.CONTROL, ControlRoleLabelRules.inferredRole("Mos"))
        assertEquals(1, ControlRoleLabelRules.foxNumber("MOE"))
        assertEquals(2, ControlRoleLabelRules.foxNumber("moi"))
        assertEquals(3, ControlRoleLabelRules.foxNumber("Mos"))
        assertEquals(4, ControlRoleLabelRules.foxNumber("moh"))
        assertEquals(5, ControlRoleLabelRules.foxNumber("MO5"))
    }

    @Test
    fun buildsSpecialLabelWithFoxRoleWarning() {
        val warning = ControlRoleLabelRules.mismatchWarning("S", ControlPointType.CONTROL, ControlPointType.SEPARATOR)

        assertTrue(warning.contains("Public label \"S\""))
        assertTrue(warning.contains("Spectator"))
        assertTrue(warning.contains("Fox"))
    }

    @Test
    fun buildsFoxLabelWithSpecialRoleWarning() {
        val warning = ControlRoleLabelRules.mismatchWarning("Fox 1", ControlPointType.BEACON, ControlPointType.CONTROL)

        assertTrue(warning.contains("Public label \"Fox 1\""))
        assertTrue(warning.contains("Fox"))
        assertTrue(warning.contains("Beacon"))
    }
}
