package org.openardf.radiooracle.desktop.usb

import org.openardf.radiooracle.shared.sportident.SportIdentCommandResult
import org.openardf.radiooracle.shared.sportident.SportIdentSi8OwnerWriteRehearsal
import org.openardf.radiooracle.shared.sportident.SportIdentSi8OwnerWriteStage

/**
 * Single-port adapter for exact owner-word frames. Only the experimental CLI
 * constructs this on a real port.
 */
internal class DesktopSportIdentOwnerWordTransport(
    private val port: DesktopSerialPort,
    private val readTimeoutMs: Int = 1200,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val onWordResult: (Int, SportIdentCommandResult) -> Unit = { _, _ -> }
) {
    private val replies = DesktopSportIdentFrameStream(port, nowMillis = nowMillis)

    /** Stops on the first ambiguous outcome; read-back remains a separate step. */
    fun exchange(rehearsal: SportIdentSi8OwnerWriteRehearsal) {
        check(port.isOpen) { "The SPORTident serial port must already be open." }
        check(rehearsal.stage == SportIdentSi8OwnerWriteStage.READY_FOR_WORD) {
            "The SI-Card8 word exchange is not ready to start."
        }
        var wordNumber = 0
        while (rehearsal.stage == SportIdentSi8OwnerWriteStage.READY_FOR_WORD) {
            if (replies.hasBufferedBytes) {
                rehearsal.abortTransport()
                return
            }
            val frame = rehearsal.takeNextWordFrame()
            try {
                if (port.write(frame) != frame.size) {
                    rehearsal.abortTransport()
                    return
                }
                // An unfamiliar frame or bad CRC must reach the shared checker,
                // instead of being skipped in search of a later matching reply.
                val result = replies.nextCommandResult(nowMillis() + readTimeoutMs, requireValidCrc = false)
                onWordResult(wordNumber + 1, result)
                rehearsal.acceptWordResult(result)
                wordNumber++
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
