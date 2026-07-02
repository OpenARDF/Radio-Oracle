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

package org.openardf.radiooracle.backend.prints

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class PrintProcessorTest {
    @Test
    fun printResultsFailsInsteadOfThrowingWhenReadyFlagHasNoPrinter() = runTest {
        val processor = PrintProcessor(RuntimeEnvironment.getApplication(), mock(DataProcessor::class.java))
        processor.javaClass.getDeclaredField("printerReady").apply {
            isAccessible = true
            set(processor, true)
        }

        val result = processor.printResults(emptyList(), race())

        assertEquals(PrintAttemptResult.FAILED, result)
    }

    private fun race(): Race =
        Race(
            id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            name = "Test Race",
            apiKey = "",
            startDateTime = LocalDateTime.of(2026, 1, 1, 9, 0),
            raceType = RaceType.CLASSIC,
            raceLevel = RaceLevel.PRACTICE,
            raceBand = RaceBand.M80,
            timeLimit = Duration.ofHours(2)
        )
}
