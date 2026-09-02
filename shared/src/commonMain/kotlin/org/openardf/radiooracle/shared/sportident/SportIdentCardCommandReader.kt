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

data class SportIdentCardCommandAttempt(
    val attemptNumber: Int,
    val reply: ByteArray?,
    val failure: SportIdentCardReadFailure?
)

data class SportIdentCardCommandRead(
    val reply: ByteArray?,
    val attempts: List<SportIdentCardCommandAttempt>
)

/**
 * Reads idempotent SPORTident card commands with split-frame assembly, CRC
 * validation, an overall per-attempt deadline, and bounded retry recovery.
 * Platform code supplies serial I/O, a monotonic clock, and sleeping.
 */
class SportIdentCardCommandReader(
    private val writeCommand: (command: Byte, payload: ByteArray?) -> Boolean,
    private val readChunk: (timeoutMillis: Int) -> ByteArray,
    private val cacheUnexpectedFrame: (ByteArray) -> Unit = {},
    private val sleepMillis: (Long) -> Unit,
    private val nowMillis: () -> Long,
    private val attemptTimeoutMillis: Int = DEFAULT_ATTEMPT_TIMEOUT_MS,
    private val retryDelayMillis: Long = DEFAULT_RETRY_DELAY_MS,
    private val maxAttempts: Int = SportIdentCardReadRetryPolicy.DEFAULT_MAX_ATTEMPTS
) {
    init {
        require(attemptTimeoutMillis > 0) { "Card-read attempt timeout must be positive." }
        require(retryDelayMillis >= 0) { "Card-read retry delay cannot be negative." }
        require(maxAttempts > 0) { "Card-read attempt count must be positive." }
    }

    fun read(
        command: Byte,
        payload: ByteArray?,
        expectedReplyBytes: Int
    ): SportIdentCardCommandRead {
        require(expectedReplyBytes > 0) { "Expected card-reply size must be positive." }
        val attempts = mutableListOf<SportIdentCardCommandAttempt>()

        repeat(maxAttempts) { attemptIndex ->
            val attempt = if (writeCommand(command, payload)) {
                readAttempt(
                    command = command,
                    expectedReplyBytes = expectedReplyBytes,
                    attemptNumber = attemptIndex + 1
                )
            } else {
                SportIdentCardCommandAttempt(
                    attemptNumber = attemptIndex + 1,
                    reply = null,
                    failure = SportIdentCardReadFailure.WRITE_FAILED
                )
            }
            attempts += attempt

            if (attempt.reply != null) {
                return SportIdentCardCommandRead(attempt.reply, attempts)
            }

            val failure = requireNotNull(attempt.failure)
            if (!SportIdentCardReadRetryPolicy.canRetry(failure, attemptIndex, maxAttempts)) {
                return SportIdentCardCommandRead(reply = null, attempts = attempts)
            }
            if (retryDelayMillis > 0) {
                sleepMillis(retryDelayMillis)
            }
        }

        return SportIdentCardCommandRead(reply = null, attempts = attempts)
    }

    private fun readAttempt(
        command: Byte,
        expectedReplyBytes: Int,
        attemptNumber: Int
    ): SportIdentCardCommandAttempt {
        val deadlineMillis = nowMillis() + attemptTimeoutMillis
        var buffered = ByteArray(0)

        while (nowMillis() < deadlineMillis) {
            val nakIndex = buffered.indexOfFirst { it == SportIdentProtocol.NAK }
            val frameStart = buffered.indexOfFirst { it == SportIdentProtocol.STX }
            if (nakIndex >= 0 && (frameStart < 0 || nakIndex < frameStart)) {
                return failedAttempt(attemptNumber, SportIdentCardReadFailure.NEGATIVE_ACKNOWLEDGEMENT)
            }

            val frame = SportIdentFrameParser.firstFrame(
                bytes = buffered,
                requireValidCrc = false
            )
            if (frame != null) {
                buffered = buffered.discardThrough(frame.raw)
                when {
                    frame.command == SportIdentProtocol.SI_CARD_REMOVED && frame.crcValid != false ->
                        return failedAttempt(attemptNumber, SportIdentCardReadFailure.CARD_REMOVED)

                    frame.command == SportIdentProtocol.NAK && frame.crcValid != false ->
                        return failedAttempt(attemptNumber, SportIdentCardReadFailure.NEGATIVE_ACKNOWLEDGEMENT)

                    frame.command != command -> {
                        if (frame.crcValid != false) {
                            cacheUnexpectedFrame(frame.raw)
                        }
                    }

                    frame.crcValid == false ->
                        return failedAttempt(attemptNumber, SportIdentCardReadFailure.INVALID_CRC)

                    frame.raw.size != expectedReplyBytes ->
                        return failedAttempt(attemptNumber, SportIdentCardReadFailure.UNEXPECTED_REPLY_SIZE)

                    else ->
                        return SportIdentCardCommandAttempt(
                            attemptNumber = attemptNumber,
                            reply = frame.raw,
                            failure = null
                        )
                }
                continue
            }

            val remainingMillis = deadlineMillis - nowMillis()
            if (remainingMillis <= 0) {
                break
            }
            val chunk = readChunk(remainingMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            if (chunk.isNotEmpty()) {
                buffered += chunk
                if (buffered.size > MAX_BUFFER_BYTES) {
                    buffered = buffered.takeLast(MAX_BUFFER_BYTES).toByteArray()
                }
            }
        }

        val failure = if (buffered.indexOfFirst { it == SportIdentProtocol.STX } >= 0) {
            SportIdentCardReadFailure.INVALID_FRAME
        } else {
            SportIdentCardReadFailure.NO_COMPLETE_REPLY
        }
        return failedAttempt(attemptNumber, failure)
    }

    private fun failedAttempt(
        attemptNumber: Int,
        failure: SportIdentCardReadFailure
    ): SportIdentCardCommandAttempt =
        SportIdentCardCommandAttempt(
            attemptNumber = attemptNumber,
            reply = null,
            failure = failure
        )

    private fun ByteArray.discardThrough(frame: ByteArray): ByteArray {
        val start = indexOf(frame)
        return if (start < 0) {
            ByteArray(0)
        } else {
            copyOfRange(start + frame.size, size)
        }
    }

    private fun ByteArray.indexOf(needle: ByteArray): Int {
        if (needle.isEmpty() || needle.size > size) {
            return -1
        }
        for (candidate in 0..(size - needle.size)) {
            if (needle.indices.all { offset -> this[candidate + offset] == needle[offset] }) {
                return candidate
            }
        }
        return -1
    }

    companion object {
        const val DEFAULT_ATTEMPT_TIMEOUT_MS = 2_000
        const val DEFAULT_RETRY_DELAY_MS = 100L
        private const val MAX_BUFFER_BYTES = 4_096
    }
}
