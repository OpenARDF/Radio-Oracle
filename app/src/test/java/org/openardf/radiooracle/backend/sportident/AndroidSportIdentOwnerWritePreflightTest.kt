package org.openardf.radiooracle.backend.sportident

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.openardf.radiooracle.shared.sportident.SportIdentCardBlock
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameWriteRequest
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerReadVerification

class AndroidSportIdentOwnerWritePreflightTest {
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
    fun establishesLiveBoundaryBeforeDiscardingOlderCardEvents() {
        val probes = ArrayDeque(
            listOf(
                AndroidSportIdentCardPresenceResult.NO_REPLY,
                AndroidSportIdentCardPresenceResult.MATCHING_BLOCK
            )
        )
        var discardCount = 0

        val requiresRemoval = AndroidSportIdentOwnerWritePreflight.prepareFreshInsertionBoundary(
            probeCurrentCard = { probes.removeFirst() },
            discardQueuedCardEvents = { discardCount++ }
        )

        assertEquals(true, requiresRemoval)
        assertEquals(0, probes.size)
        assertEquals(1, discardCount)
    }

    @Test
    fun freshInsertionBoundaryFailsClosedForChangedOrInvalidCard() {
        listOf(
            AndroidSportIdentCardPresenceResult.DIFFERENT_BLOCK,
            AndroidSportIdentCardPresenceResult.INVALID_REPLY
        ).forEach { result ->
            var discarded = false
            assertThrows(IllegalArgumentException::class.java) {
                AndroidSportIdentOwnerWritePreflight.prepareFreshInsertionBoundary(
                    probeCurrentCard = { result },
                    discardQueuedCardEvents = { discarded = true }
                )
            }
            assertEquals(false, discarded)
        }

    }

    @Test
    fun alreadyRemovedCardUsesNextInsertionAsFreshBoundary() {
        var probeCount = 0
        var discardCount = 0
        val requiresRemoval = AndroidSportIdentOwnerWritePreflight.prepareFreshInsertionBoundary(
            probeCurrentCard = {
                probeCount++
                AndroidSportIdentCardPresenceResult.NO_REPLY
            },
            discardQueuedCardEvents = { discardCount++ }
        )

        assertEquals(false, requiresRemoval)
        assertEquals(2, probeCount)
        assertEquals(1, discardCount)
    }

    @Test
    fun acceptsOnlyTheExactInspectedCardImage() {
        val expected = fixture(request.cardNumber)
        AndroidSportIdentOwnerWritePreflight.requireUnchangedTarget(
            request,
            expected,
            fixture(request.cardNumber)
        )

        assertThrows(IllegalArgumentException::class.java) {
            AndroidSportIdentOwnerWritePreflight.requireUnchangedTarget(
                request,
                expected,
                fixture(request.cardNumber + 1)
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            val changed = fixture(request.cardNumber).let { fixture ->
                val blocks = fixture.blocks.map { block ->
                    val bytes = ByteArray(128) { index ->
                        block.hexData.substring(index * 2, index * 2 + 2).toInt(16).toByte()
                    }
                    if (block.blockNumber == 1) bytes[8] = 99
                    SportIdentCardBlock(block.blockNumber, bytes)
                }
                SportIdentOwnerReadVerification.capture(fixture.stationNumber, blocks)
            }
            AndroidSportIdentOwnerWritePreflight.requireUnchangedTarget(
                request,
                expected,
                changed
            )
        }
    }

    private fun fixture(cardNumber: Int) = SportIdentOwnerReadVerification.capture(
        request.stationNumber,
        listOf(
            SportIdentCardBlock(0, ByteArray(128).also { block ->
                block[22] = 0
                block[24] = 2
                block[25] = (cardNumber shr 16).toByte()
                block[26] = (cardNumber shr 8).toByte()
                block[27] = cardNumber.toByte()
                "Daisy;Duck;".forEachIndexed { index, character ->
                    block[32 + index] = character.code.toByte()
                }
            }),
            SportIdentCardBlock(1, ByteArray(128))
        )
    )
}
