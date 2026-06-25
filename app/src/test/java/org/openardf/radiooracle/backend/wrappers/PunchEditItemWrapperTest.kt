package org.openardf.radiooracle.backend.wrappers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.backend.room.entity.Alias
import org.openardf.radiooracle.backend.room.entity.Punch
import org.openardf.radiooracle.backend.room.entity.embeddeds.AliasPunch
import org.openardf.radiooracle.backend.room.enums.SIRecordType
import org.openardf.radiooracle.backend.sportident.SITime

class PunchEditItemWrapperTest {
    @Test
    fun displaysAliasNameWhenPresent() {
        val punch = Punch(31, SITime(), SIRecordType.CONTROL, 1)
        val wrappers = PunchEditItemWrapper.getWrappers(
            arrayListOf(AliasPunch(punch, Alias(31, "1F")))
        )

        assertEquals("1F", wrappers.single().displayCodeText())
        assertTrue(wrappers.single().matchesDisplayCodeText("1F"))
        assertEquals(31, PunchEditItemWrapper.getPunches(wrappers).single().siCode)
    }

    @Test
    fun displaysSiCodeWhenAliasIsMissing() {
        val punch = Punch(32, SITime(), SIRecordType.CONTROL, 1)
        val wrappers = PunchEditItemWrapper.getWrappers(
            arrayListOf(AliasPunch(punch, null))
        )

        assertEquals("32", wrappers.single().displayCodeText())
        assertTrue(wrappers.single().matchesDisplayCodeText("32"))
    }
}
