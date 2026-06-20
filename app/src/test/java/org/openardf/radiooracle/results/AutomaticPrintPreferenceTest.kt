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
