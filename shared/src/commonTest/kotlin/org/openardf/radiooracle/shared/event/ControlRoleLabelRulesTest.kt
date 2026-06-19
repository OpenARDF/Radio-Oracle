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
    fun buildsFoxRoleWarningFromInferredRole() {
        val warning = ControlRoleLabelRules.foxRoleWarning("S", ControlPointType.SEPARATOR)

        assertTrue(warning.contains("Public label \"S\""))
        assertTrue(warning.contains("Spectator"))
        assertTrue(warning.contains("fox"))
    }
}
