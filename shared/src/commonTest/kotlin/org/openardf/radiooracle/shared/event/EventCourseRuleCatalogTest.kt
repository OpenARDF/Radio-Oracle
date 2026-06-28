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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.openardf.radiooracle.shared.domain.RaceType

class EventCourseRuleCatalogTest {
    @Test
    fun sprintCategoryFoxRequirementsDoubleClassicExceptOnlyM21RequiresAllFoxes() {
        val w55Sprint = EventCourseRuleCatalog.categoryRequirement("W55", RaceType.SPRINT)
        assertEquals(6, w55Sprint?.minControls)
        assertEquals(8, w55Sprint?.maxControls)

        val m21Sprint = EventCourseRuleCatalog.categoryRequirement("M21", RaceType.SPRINT)
        assertEquals(10, m21Sprint?.minControls)
        assertEquals(10, m21Sprint?.maxControls)
    }

    @Test
    fun spacingAndClimbRulesAreSharedSportRules() {
        assertEquals(6.0, EventCourseRuleCatalog.CLIMB_LIMIT_PERCENT)
        assertTrue(EventCourseRuleCatalog.hasClimbLimit(RaceType.CLASSIC))
        assertTrue(EventCourseRuleCatalog.hasClimbLimit(RaceType.SPRINT))
        assertFalse(EventCourseRuleCatalog.hasClimbLimit(RaceType.ORIENTEERING))

        val youthClassic = EventCourseRuleCatalog.spacingRuleSet(RaceType.CLASSIC, "W14")
        assertEquals("Youth Classic", youthClassic?.formatLabel)
        assertEquals(500, youthClassic?.startMinMeters)
        assertEquals(400, youthClassic?.pairMinMeters)
        assertTrue(youthClassic?.includeBeaconInStartCheck == true)
        assertFalse(youthClassic?.includeSpectatorInPairCheck == true)

        val sprint = EventCourseRuleCatalog.spacingRuleSet(RaceType.SPRINT, "M21")
        assertEquals("Sprint", sprint?.formatLabel)
        assertEquals(100, sprint?.startMinMeters)
        assertEquals(100, sprint?.pairMinMeters)
        assertFalse(sprint?.includeBeaconInStartCheck == true)
        assertTrue(sprint?.includeSpectatorInPairCheck == true)

        assertNull(EventCourseRuleCatalog.spacingRuleSet(RaceType.ORIENTEERING, "M21"))
    }
}
