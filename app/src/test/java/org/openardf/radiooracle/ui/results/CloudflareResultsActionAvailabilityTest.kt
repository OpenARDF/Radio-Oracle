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

package org.openardf.radiooracle.ui.results

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudflareResultsActionAvailabilityTest {
    @Test
    fun completeAcceptedSettingsEnablePublishAndPublishedResultViewing() {
        val availability = cloudflareResultsActionAvailability(
            hasTarget = true,
            hasPublishedUrl = true,
            settingsComplete = true,
            settingsRejected = false,
            publishing = false
        )

        assertTrue(availability.publishEnabled)
        assertTrue(availability.viewEnabled)
    }

    @Test
    fun missingSettingsDisableBothWebsiteActions() {
        val availability = cloudflareResultsActionAvailability(
            hasTarget = true,
            hasPublishedUrl = true,
            settingsComplete = false,
            settingsRejected = false,
            publishing = false
        )

        assertFalse(availability.publishEnabled)
        assertFalse(availability.viewEnabled)
    }

    @Test
    fun rejectedSettingsDisableBothWebsiteActions() {
        val availability = cloudflareResultsActionAvailability(
            hasTarget = true,
            hasPublishedUrl = true,
            settingsComplete = true,
            settingsRejected = true,
            publishing = false
        )

        assertFalse(availability.publishEnabled)
        assertFalse(availability.viewEnabled)
    }

    @Test
    fun unpublishedTargetKeepsViewDisabled() {
        val availability = cloudflareResultsActionAvailability(
            hasTarget = true,
            hasPublishedUrl = false,
            settingsComplete = true,
            settingsRejected = false,
            publishing = false
        )

        assertTrue(availability.publishEnabled)
        assertFalse(availability.viewEnabled)
    }

    @Test
    fun publishingDisablesBothWebsiteActions() {
        val availability = cloudflareResultsActionAvailability(
            hasTarget = true,
            hasPublishedUrl = true,
            settingsComplete = true,
            settingsRejected = false,
            publishing = true
        )

        assertFalse(availability.publishEnabled)
        assertFalse(availability.viewEnabled)
    }
}
