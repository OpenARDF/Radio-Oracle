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

enum class SportIdentCardReadFailure {
    WRITE_FAILED,
    NEGATIVE_ACKNOWLEDGEMENT,
    NO_COMPLETE_REPLY,
    INVALID_FRAME,
    INVALID_CRC,
    UNEXPECTED_REPLY_SIZE,
    CARD_REMOVED
}

/** Retry policy for idempotent SPORTident card payload and block requests. */
object SportIdentCardReadRetryPolicy {
    const val DEFAULT_MAX_ATTEMPTS = 3

    fun canRetry(
        failure: SportIdentCardReadFailure,
        attemptIndex: Int,
        maxAttempts: Int = DEFAULT_MAX_ATTEMPTS
    ): Boolean =
        attemptIndex < maxAttempts - 1 && failure != SportIdentCardReadFailure.CARD_REMOVED
}
