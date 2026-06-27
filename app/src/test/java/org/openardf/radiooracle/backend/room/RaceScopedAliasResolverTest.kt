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

package org.openardf.radiooracle.backend.room

import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.openardf.radiooracle.backend.room.entity.Alias
import org.openardf.radiooracle.backend.room.entity.ControlPoint
import org.openardf.radiooracle.backend.room.entity.Punch
import org.openardf.radiooracle.backend.room.entity.Result
import org.openardf.radiooracle.backend.room.entity.embeddeds.AliasPunch
import org.openardf.radiooracle.backend.room.entity.embeddeds.ControlPointAlias
import org.openardf.radiooracle.backend.room.entity.embeddeds.ResultData
import java.util.UUID

class RaceScopedAliasResolverTest {
    @Test
    fun controlPointAliasesUseAliasesFromTheRequestedRace() {
        val raceId = UUID.randomUUID()
        val otherRaceId = UUID.randomUUID()
        val controlPoint = ControlPoint(31)
        val wrongAlias = Alias(UUID.randomUUID(), otherRaceId, 31, "Other race")
        val raceAlias = Alias(UUID.randomUUID(), raceId, 31, "Fox 1")

        val resolved = RaceScopedAliasResolver.resolveControlPoints(
            listOf(ControlPointAlias(controlPoint, wrongAlias)),
            listOf(raceAlias)
        )

        assertEquals("Fox 1", resolved.single().alias?.name)
        assertEquals(raceId, resolved.single().alias?.raceId)
    }

    @Test
    fun resultPunchAliasesUseAliasesFromTheResultRace() {
        val raceId = UUID.randomUUID()
        val otherRaceId = UUID.randomUUID()
        val result = Result().apply { this.raceId = raceId }
        val punch = Punch().apply {
            this.raceId = raceId
            this.resultId = result.id
            this.siCode = 31
        }
        val wrongAlias = Alias(UUID.randomUUID(), otherRaceId, 31, "Other race")
        val raceAlias = Alias(UUID.randomUUID(), raceId, 31, "Fox 1")

        val resolved = RaceScopedAliasResolver.resolveResultData(
            ResultData(result, listOf(AliasPunch(punch, wrongAlias)), null),
            listOf(raceAlias)
        )

        assertEquals("Fox 1", resolved.punches.single().alias?.name)
        assertEquals(raceId, resolved.punches.single().alias?.raceId)
    }
}
