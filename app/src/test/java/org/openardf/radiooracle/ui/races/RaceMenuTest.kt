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

package org.openardf.radiooracle.ui.races

import android.view.View
import androidx.appcompat.widget.PopupMenu
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.openardf.radiooracle.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class RaceMenuTest {
    @Test
    fun sportIdentAppearsBetweenGlobalSettingsAndAbout() {
        val context = RuntimeEnvironment.getApplication()
        val popup = PopupMenu(context, View(context))
        popup.menuInflater.inflate(R.menu.fragment_menu_race, popup.menu)

        assertEquals(
            listOf(
                R.id.race_menu_event_series,
                R.id.race_menu_global_settings,
                R.id.race_menu_sportident,
                R.id.race_menu_about
            ),
            (0 until popup.menu.size()).map { popup.menu.getItem(it).itemId }
        )
        assertEquals("SPORTident", popup.menu.findItem(R.id.race_menu_sportident).title.toString())
    }
}
