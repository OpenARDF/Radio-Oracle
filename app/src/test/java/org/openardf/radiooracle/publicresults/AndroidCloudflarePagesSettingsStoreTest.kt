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

package org.openardf.radiooracle.publicresults

import androidx.preference.PreferenceManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openardf.radiooracle.backend.publicresults.AndroidCloudflarePagesPublishSettings
import org.openardf.radiooracle.backend.publicresults.AndroidCloudflarePagesSettingsStore
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AndroidCloudflarePagesSettingsStoreTest {
    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun clearPreferences() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    @After
    fun cleanUpPreferences() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    @Test
    fun rejectionAppliesOnlyToTheRejectedSettingsAndCanBeClearedForRetry() {
        val rejected = AndroidCloudflarePagesPublishSettings(
            accountId = "account",
            apiToken = "token"
        )
        AndroidCloudflarePagesSettingsStore.recordRejection(context, rejected)

        assertTrue(AndroidCloudflarePagesSettingsStore.isRejected(context, rejected))
        assertFalse(
            AndroidCloudflarePagesSettingsStore.isRejected(
                context,
                rejected.copy(apiToken = "replacement-token")
            )
        )

        AndroidCloudflarePagesSettingsStore.clearRejection(context)

        assertFalse(AndroidCloudflarePagesSettingsStore.isRejected(context, rejected))
    }
}
