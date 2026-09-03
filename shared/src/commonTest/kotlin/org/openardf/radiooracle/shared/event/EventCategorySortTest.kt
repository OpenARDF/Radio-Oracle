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

class EventCategorySortTest {
    @Test
    fun comparesCategoryNamesUsingDesktopDisplayOrder() {
        val names = listOf("M19", "W65", "M21", "W55", "M50", "M60")

        val sorted = names.sortedWith(EventCategorySort::compareNames)

        assertEquals(listOf("W55", "W65", "M19", "M21", "M50", "M60"), sorted)
    }

    @Test
    fun sortsWomenThenMenFromYoungestToOldest() {
        val names = listOf(
            "M80", "W75", "M21", "W21", "M50", "W16", "M19", "W45",
            "M70", "W35", "M16", "W65", "M60", "W19", "M40", "W55"
        )

        val sorted = names.sortedWith(EventCategorySort::compareNames)

        assertEquals(
            listOf(
                "W16", "W19", "W21", "W35", "W45", "W55", "W65", "W75",
                "M16", "M19", "M21", "M40", "M50", "M60", "M70", "M80"
            ),
            sorted
        )
    }
}
