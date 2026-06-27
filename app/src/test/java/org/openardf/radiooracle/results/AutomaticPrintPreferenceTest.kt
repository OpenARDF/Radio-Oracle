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

package org.openardf.radiooracle.results

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.backend.results.ResultsProcessor

class AutomaticPrintPreferenceTest {
    @Test
    fun manualModeDoesNotPrintAutomatically() {
        val preference = ResultsProcessor.PRINT_AUTOMATIC_MANUALLY_VALUE

        assertFalse(
            ResultsProcessor.shouldPrintFinishTicketForPreference(
                preference,
                competitorMatched = true,
                categoryMatched = true
            )
        )
    }

    @Test
    fun alwaysModePrintsWithoutCompetitorOrCategoryMatch() {
        val preference = ResultsProcessor.PRINT_AUTOMATIC_ALWAYS_VALUE

        assertTrue(
            ResultsProcessor.shouldPrintFinishTicketForPreference(
                preference,
                competitorMatched = false,
                categoryMatched = false
            )
        )
    }

    @Test
    fun competitorMatchedModeRequiresCompetitorMatch() {
        val preference = ResultsProcessor.PRINT_AUTOMATIC_COMPETITOR_MATCHED_VALUE

        assertFalse(
            ResultsProcessor.shouldPrintFinishTicketForPreference(
                preference,
                competitorMatched = false,
                categoryMatched = false
            )
        )
        assertTrue(
            ResultsProcessor.shouldPrintFinishTicketForPreference(
                preference,
                competitorMatched = true,
                categoryMatched = false
            )
        )
    }

    @Test
    fun categoryMatchedModeRequiresCompetitorAndCategoryMatch() {
        val preference = ResultsProcessor.PRINT_AUTOMATIC_CATEGORY_MATCHED_VALUE

        assertFalse(
            ResultsProcessor.shouldPrintFinishTicketForPreference(
                preference,
                competitorMatched = true,
                categoryMatched = false
            )
        )
        assertTrue(
            ResultsProcessor.shouldPrintFinishTicketForPreference(
                preference,
                competitorMatched = true,
                categoryMatched = true
            )
        )
    }
}
