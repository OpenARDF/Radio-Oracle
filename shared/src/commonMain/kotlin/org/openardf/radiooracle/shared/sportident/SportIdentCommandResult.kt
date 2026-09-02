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

sealed class SportIdentCommandResult {
    data class Reply(val frame: SportIdentFrame) : SportIdentCommandResult()
    data object NegativeAcknowledgement : SportIdentCommandResult()
    data object NoReply : SportIdentCommandResult()

    fun replyOrNull(): SportIdentFrame? = (this as? Reply)?.frame
}

class SportIdentExplicitWriteRejectedException : IllegalStateException(
    "SPORTident station rejected Write station time. Reseat the station on the " +
        "coupling stick and inspect it again. If rejection continues, disconnect and " +
        "reconnect the USB download station before retrying."
)

object SportIdentTimeSyncRetryPolicy {
    fun canRetry(
        error: Throwable,
        writeStarted: Boolean,
        attemptIndex: Int,
        maxAttempts: Int
    ): Boolean =
        attemptIndex < maxAttempts - 1 &&
            (!writeStarted || error is SportIdentExplicitWriteRejectedException)
}
