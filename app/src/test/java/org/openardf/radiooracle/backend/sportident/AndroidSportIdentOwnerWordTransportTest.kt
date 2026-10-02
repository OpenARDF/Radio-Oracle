package org.openardf.radiooracle.backend.sportident

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.sportident.SportIdentCardBlock
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameWriteRequest
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerReadVerification
import org.openardf.radiooracle.shared.sportident.SportIdentProtocol
import org.openardf.radiooracle.shared.sportident.SportIdentSi8OwnerWriteRehearsal
import org.openardf.radiooracle.shared.sportident.SportIdentSi8OwnerWriteStage
import org.openardf.radiooracle.shared.sportident.SportIdentSi8OwnerWriteStopReason

class AndroidSportIdentOwnerWordTransportTest {
    private val request = SportIdentOwnerNameWriteRequest(
        1,
        593_927,
        2_450_662,
        "Daisy",
        "Duck",
        "Donald",
        "Duck",
        true
    )

    @Test
    fun sendsEachWordOnceWithPacingAndRequiresIndependentReadback() {
        val replies = replies().toMutableList()
        val writes = mutableListOf<ByteArray>()
        val pauses = mutableListOf<Long>()
        val rehearsal = rehearsal()

        AndroidSportIdentOwnerWordTransport(
            writeFrame = { writes += it; true },
            readReply = { replies.removeAt(0) },
            pause = pauses::add
        ).exchange(rehearsal)

        assertEquals(3, writes.size)
        assertEquals(listOf(200L, 200L), pauses)
        assertEquals(SportIdentSi8OwnerWriteStage.REQUIRES_READBACK, rehearsal.stage)
    }

    @Test
    fun timeoutAndNakStopAfterOneAttemptWithoutRetry() {
        listOf<ByteArray?>(null, byteArrayOf(SportIdentProtocol.NAK)).forEach { reply ->
            var writes = 0
            val rehearsal = rehearsal()
            AndroidSportIdentOwnerWordTransport(
                writeFrame = { writes++; true },
                readReply = { reply },
                pause = {}
            ).exchange(rehearsal)

            assertEquals(1, writes)
            assertEquals(SportIdentSi8OwnerWriteStage.STOPPED, rehearsal.stage)
            assertTrue(
                rehearsal.stopReason == SportIdentSi8OwnerWriteStopReason.NO_REPLY ||
                    rehearsal.stopReason == SportIdentSi8OwnerWriteStopReason.NEGATIVE_ACKNOWLEDGEMENT
            )
        }
    }

    private fun rehearsal() = SportIdentSi8OwnerWriteRehearsal(
        request,
        10,
        fixture("Daisy;Duck;")
    )

    private fun replies(): List<ByteArray> = listOf(
        "02 ea 03 00 0a 08 00 2e 03",
        "02 ea 03 00 0a 09 01 2e 03",
        "02 ea 03 00 0a 0a 02 2e 03"
    ).map { hex -> hex.split(' ').map { it.toInt(16).toByte() }.toByteArray() }

    private fun fixture(owner: String) = SportIdentOwnerReadVerification.capture(
        593_927,
        listOf(
            SportIdentCardBlock(0, ByteArray(128).also { block ->
                block[22] = 0
                block[24] = 2
                block[25] = 0x25
                block[26] = 0x64
                block[27] = 0xe6.toByte()
                owner.forEachIndexed { index, character ->
                    block[32 + index] = character.code.toByte()
                }
            }),
            SportIdentCardBlock(1, ByteArray(128))
        )
    )
}
