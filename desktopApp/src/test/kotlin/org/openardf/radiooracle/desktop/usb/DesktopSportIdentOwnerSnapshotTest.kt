package org.openardf.radiooracle.desktop.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.openardf.radiooracle.shared.sportident.SportIdentCardBlock
import org.openardf.radiooracle.shared.sportident.SportIdentCardEvent
import org.openardf.radiooracle.shared.sportident.SportIdentCardReadoutParser
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerReadVerification
import org.openardf.radiooracle.shared.sportident.SportIdentProtocol

class DesktopSportIdentOwnerSnapshotTest {
    @Test
    fun automaticSi8ReadRetainsBothRawBlocksForNativeEligibilityAndRecovery() {
        val block0 = ByteArray(128) { 0xEE.toByte() }
        block0[22] = 0
        block0[24] = 2
        block0[25] = 0x25
        block0[26] = 0x64
        block0[27] = 0xE6.toByte()
        "Donald;Duck;".forEachIndexed { index, char -> block0[32 + index] = char.code.toByte() }
        val block1 = ByteArray(128) { 0xEE.toByte() }
        val readout = requireNotNull(SportIdentCardReadoutParser.parseSi8Or9OrSiac(block0 + block1))
        val download = DesktopSportIdentCardBlockDownload(
            SportIdentCardEvent.Inserted(SportIdentProtocol.SI_CARD8_9_SIAC, 2450662),
            listOf(SportIdentCardBlock(0, block0), SportIdentCardBlock(1, block1)), readout)

        val snapshot = DesktopSportIdentOwnerSnapshot.fromDownload(download, "/dev/cu.reader", 554900)
        val raw = requireNotNull(snapshot.rawRead)
        val decoded = SportIdentOwnerReadVerification.nativeRead(raw)

        assertEquals(554900, raw.stationNumber)
        assertEquals(2450662, decoded.cardNumber)
        assertEquals("Donald", decoded.firstName)
        assertEquals("Duck", decoded.lastName)
        assertEquals(2, raw.blocks.size)
    }

    @Test
    fun incompleteBlocksCannotBeUsedAsNativeRecoveryEvidence() {
        val block0 = ByteArray(128)
        block0[22] = 0
        block0[24] = 2
        block0[25] = 0x25
        block0[26] = 0x64
        block0[27] = 0xE6.toByte()
        val readout = requireNotNull(SportIdentCardReadoutParser.parseSi8Or9OrSiac(block0 + ByteArray(128)))
        val download = DesktopSportIdentCardBlockDownload(
            SportIdentCardEvent.Inserted(SportIdentProtocol.SI_CARD8_9_SIAC, 2450662),
            listOf(SportIdentCardBlock(0, block0)), readout)

        val snapshot = DesktopSportIdentOwnerSnapshot.fromDownload(download, "/dev/cu.reader", 554900)

        assertNull(snapshot.rawRead)
    }
}
