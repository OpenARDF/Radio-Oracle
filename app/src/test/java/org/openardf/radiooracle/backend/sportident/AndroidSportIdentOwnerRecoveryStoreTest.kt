package org.openardf.radiooracle.backend.sportident

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.openardf.radiooracle.shared.sportident.SportIdentCardBlock
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameWriteRequest
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerReadVerification

class AndroidSportIdentOwnerRecoveryStoreTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun survivesRecreationAndReservesTheFullAttemptBeforeWriting() {
        val file = File(temporary.root, AndroidSportIdentOwnerRecoveryStore.FILE_NAME)
        val store = AndroidSportIdentOwnerRecoveryStore(file)
        val request = request()

        store.begin(request, fixture())
        store.reserveWordSequence(request)

        val reloaded = AndroidSportIdentOwnerRecoveryStore(file).load()
            as AndroidSportIdentOwnerRecoveryState.Pending
        assertEquals(request, reloaded.attempt.request)
        assertEquals(3, reloaded.attempt.attemptedWords)
    }

    @Test
    fun corruptRecordBlocksNewWritesInsteadOfBeingIgnored() {
        val file = File(temporary.root, AndroidSportIdentOwnerRecoveryStore.FILE_NAME)
        file.writeText("not a recovery record")
        val store = AndroidSportIdentOwnerRecoveryStore(file)

        assertEquals(AndroidSportIdentOwnerRecoveryState.Unavailable, store.load())
        assertTrue(!store.isClear())
    }

    private fun request() = SportIdentOwnerNameWriteRequest(
        1,
        593_927,
        2_450_662,
        "Daisy",
        "Duck",
        "Donald",
        "Duck",
        true
    )

    private fun fixture() = SportIdentOwnerReadVerification.capture(
        593_927,
        listOf(
            SportIdentCardBlock(0, ByteArray(128).also { block ->
                block[22] = 0
                block[24] = 2
                block[25] = 0x25
                block[26] = 0x64
                block[27] = 0xe6.toByte()
                "Daisy;Duck;".forEachIndexed { index, character ->
                    block[32 + index] = character.code.toByte()
                }
            }),
            SportIdentCardBlock(1, ByteArray(128))
        )
    )
}
