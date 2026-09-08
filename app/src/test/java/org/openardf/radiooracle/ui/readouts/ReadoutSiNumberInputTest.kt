package org.openardf.radiooracle.ui.readouts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.openardf.radiooracle.R

class ReadoutSiNumberInputTest {
    @Test
    fun acceptsUnknownAndSupportedCardNumbers() {
        listOf("", "  ", "8101649", " 2005018 ").forEach {
            assertNull(readoutSiNumberInputError(it))
        }
    }

    @Test
    fun rejectsInvalidAndOverflowingCardNumbers() {
        listOf("0", "-1", "abc", "1.5", "99999999999999").forEach {
            assertEquals(R.string.si_number_invalid_range, readoutSiNumberInputError(it))
        }
    }
}
