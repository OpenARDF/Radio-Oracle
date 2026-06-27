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
