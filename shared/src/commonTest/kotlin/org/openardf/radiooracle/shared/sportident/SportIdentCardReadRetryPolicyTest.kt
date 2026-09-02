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

package org.openardf.radiooracle.shared.sportident

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SportIdentCardReadRetryPolicyTest {
    @Test
    fun retriesTransientReadFailuresBeforeAttemptLimit() {
        SportIdentCardReadFailure.entries
            .filterNot { it == SportIdentCardReadFailure.CARD_REMOVED }
            .forEach { failure ->
                assertTrue(
                    SportIdentCardReadRetryPolicy.canRetry(
                        failure = failure,
                        attemptIndex = 0
                    ),
                    "Expected $failure to be retryable"
                )
            }
    }

    @Test
    fun neverRetriesAfterCardRemoval() {
        assertFalse(
            SportIdentCardReadRetryPolicy.canRetry(
                failure = SportIdentCardReadFailure.CARD_REMOVED,
                attemptIndex = 0
            )
        )
    }

    @Test
    fun stopsAtAttemptLimit() {
        assertFalse(
            SportIdentCardReadRetryPolicy.canRetry(
                failure = SportIdentCardReadFailure.NO_COMPLETE_REPLY,
                attemptIndex = SportIdentCardReadRetryPolicy.DEFAULT_MAX_ATTEMPTS - 1
            )
        )
    }
}
