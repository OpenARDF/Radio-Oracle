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

package org.openardf.radiooracle.backend.sportident

import android.os.SystemClock
import org.openardf.radiooracle.shared.sportident.SportIdentCardCommandRead
import org.openardf.radiooracle.shared.sportident.SportIdentCardCommandReader
import org.openardf.radiooracle.shared.sportident.SportIdentCardReadRetryPolicy

/**
 * Reads idempotent SPORTident card commands with framing, CRC validation, an
 * overall per-attempt deadline, and bounded retry recovery.
 */
internal class AndroidSportIdentCardCommandReader(
    private val writeCommand: (command: Byte, payload: ByteArray?) -> Boolean,
    private val readChunk: (timeoutMillis: Int) -> ByteArray,
    private val cacheUnexpectedFrame: (ByteArray) -> Unit = {},
    private val sleepMillis: (Long) -> Unit = Thread::sleep,
    private val nowMillis: () -> Long = SystemClock::elapsedRealtime,
    private val attemptTimeoutMillis: Int = DEFAULT_ATTEMPT_TIMEOUT_MS,
    private val retryDelayMillis: Long = DEFAULT_RETRY_DELAY_MS,
    private val maxAttempts: Int = SportIdentCardReadRetryPolicy.DEFAULT_MAX_ATTEMPTS
) {
    private val reader = SportIdentCardCommandReader(
        writeCommand = writeCommand,
        readChunk = readChunk,
        cacheUnexpectedFrame = cacheUnexpectedFrame,
        sleepMillis = sleepMillis,
        nowMillis = nowMillis,
        attemptTimeoutMillis = attemptTimeoutMillis,
        retryDelayMillis = retryDelayMillis,
        maxAttempts = maxAttempts
    )

    fun read(
        command: Byte,
        payload: ByteArray?,
        expectedReplyBytes: Int
    ): SportIdentCardCommandRead = reader.read(command, payload, expectedReplyBytes)

    private companion object {
        const val DEFAULT_ATTEMPT_TIMEOUT_MS = SportIdentCardCommandReader.DEFAULT_ATTEMPT_TIMEOUT_MS
        const val DEFAULT_RETRY_DELAY_MS = SportIdentCardCommandReader.DEFAULT_RETRY_DELAY_MS
    }
}
