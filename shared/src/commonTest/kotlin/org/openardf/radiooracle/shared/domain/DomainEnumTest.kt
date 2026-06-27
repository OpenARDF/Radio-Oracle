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

package org.openardf.radiooracle.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class DomainEnumTest {
    @Test
    fun resolvesRaceMetadataEnumsByStoredValue() {
        assertEquals(RaceType.CLASSIC, RaceType.getByValue(-1))
        assertEquals(RaceType.SPRINT, RaceType.getByValue(2))
        assertEquals(RaceLevel.PRACTICE, RaceLevel.getByValue(-1))
        assertEquals(RaceLevel.DISTRICT, RaceLevel.getByValue(3))
        assertEquals(RaceBand.M80, RaceBand.getByValue(-1))
        assertEquals(RaceBand.COMBINED, RaceBand.getByValue(2))
    }

    @Test
    fun resolvesResultAndPunchEnumsByStoredValue() {
        assertEquals(ResultStatus.NO_RANKING, ResultStatus.getByValue(-1))
        assertEquals(ResultStatus.OVER_TIME_LIMIT, ResultStatus.getByValue(6))
        assertEquals(PunchStatus.VALID, PunchStatus.getByValue(-1))
        assertEquals(PunchStatus.DUPLICATE, PunchStatus.getByValue(2))
    }

    @Test
    fun resolvesControlProviderAndCategoryEnumsByStoredValue() {
        assertEquals(ControlPointType.CONTROL, ControlPointType.getByValue(-1))
        assertEquals(ControlPointType.BEACON, ControlPointType.getByValue(1))
        assertEquals(ProviderType.ROBIS, ProviderType.getByValue(-1))
        assertEquals(ProviderType.OFEED, ProviderType.getByValue(3))
        assertEquals(StandardCategoryType.INTERNATIONAL, StandardCategoryType.getByValue(-1))
        assertEquals(StandardCategoryType.CZECH, StandardCategoryType.getByValue(1))
    }
}
