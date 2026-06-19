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
        assertEquals(ControlPointType.CONTROL, ControlRoleLabelRules.inferredRole("1"))
        assertEquals(ControlPointType.CONTROL, ControlRoleLabelRules.inferredRole("1F"))
        assertEquals(ControlPointType.CONTROL, ControlRoleLabelRules.inferredRole("F1"))
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
