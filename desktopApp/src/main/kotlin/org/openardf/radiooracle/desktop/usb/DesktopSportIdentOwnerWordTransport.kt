package org.openardf.radiooracle.desktop.usb

import org.openardf.radiooracle.shared.sportident.SportIdentCommandResult
import org.openardf.radiooracle.shared.sportident.SportIdentSi8OwnerWriteRehearsal
import org.openardf.radiooracle.shared.sportident.SportIdentSi8OwnerWriteStage

/**
 * Single-port adapter for exact owner-word frames. Only the experimental CLI
 * constructs this on a real port.
 */
internal data class DesktopSportIdentOwnerWordTiming(
    val wordNumber: Int,
    val replyToWriteMicros: Long?,
    val writeToReplyMicros: Long
)

internal class DesktopSportIdentOwnerWordTransport(
    private val port: DesktopSerialPort,
    private val readTimeoutMs: Int = 1200,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val onWordResult: (Int, SportIdentCommandResult) -> Unit = { _, _ -> },
    private val onWordTiming: (DesktopSportIdentOwnerWordTiming) -> Unit = {}
) {
    private val replies = DesktopSportIdentFrameStream(port, nowMillis = nowMillis)

    /** Stops on the first ambiguous outcome; read-back remains a separate step. */
    fun exchange(rehearsal: SportIdentSi8OwnerWriteRehearsal,
        beforeWordAttempt: (Int) -> Unit = {}, stopAfterAcknowledgedWord: Int? = null) {
        require(stopAfterAcknowledgedWord == null || stopAfterAcknowledgedWord in 1..rehearsal.wordCount)
        check(port.isOpen) { "The SPORTident serial port must already be open." }
        check(rehearsal.stage == SportIdentSi8OwnerWriteStage.READY_FOR_WORD) {
            "The SI-Card8 word exchange is not ready to start."
        }
        var wordNumber = 0
        var previousReplyAtNanos: Long? = null
        while (rehearsal.stage == SportIdentSi8OwnerWriteStage.READY_FOR_WORD) {
            try {
                // A card event can arrive after the preceding reply without
                // sharing its serial read. Do not issue another owner word
                // while any input is already waiting in the driver queue.
                if (replies.hasBufferedBytes || port.readAvailable(512).isNotEmpty()) {
                    rehearsal.abortTransport()
                    return
                }
                val frame = rehearsal.takeNextWordFrame()
                beforeWordAttempt(wordNumber + 1)
                val writeStartedAtNanos = System.nanoTime()
                if (port.write(frame) != frame.size) {
                    rehearsal.abortTransport()
                    return
                }
                // An unfamiliar frame or bad CRC must reach the shared checker,
                // instead of being skipped in search of a later matching reply.
                val result = replies.nextCommandResult(nowMillis() + readTimeoutMs, requireValidCrc = false)
                val replyAtNanos = System.nanoTime()
                onWordResult(wordNumber + 1, result)
                onWordTiming(DesktopSportIdentOwnerWordTiming(wordNumber + 1,
                    previousReplyAtNanos?.let { (writeStartedAtNanos - it) / 1_000 },
                    (replyAtNanos - writeStartedAtNanos) / 1_000))
                previousReplyAtNanos = replyAtNanos
                rehearsal.acceptWordResult(result)
                wordNumber++
                if (wordNumber == stopAfterAcknowledgedWord) {
                    if (replies.hasBufferedBytes || port.readAvailable(512).isNotEmpty()) {
                        rehearsal.abortTransport()
                        return
                    }
                    if (rehearsal.stage == SportIdentSi8OwnerWriteStage.REQUIRES_READBACK) {
                        rehearsal.stopBeforeReadback()
                    } else {
                        rehearsal.stopAfterAcknowledgedWord()
                    }
                    return
                }
            } catch (error: Exception) {
                rehearsal.abortTransport()
                throw error
            }
        }
        if (rehearsal.stage == SportIdentSi8OwnerWriteStage.REQUIRES_READBACK && replies.hasBufferedBytes) {
            rehearsal.abortTransport()
        }
    }
}
