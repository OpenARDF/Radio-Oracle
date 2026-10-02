package org.openardf.radiooracle.backend.sportident

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.openardf.radiooracle.shared.sportident.SportIdentCardBlock
import org.openardf.radiooracle.shared.sportident.SportIdentCardFamily
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerDataStatus
import org.openardf.radiooracle.shared.sportident.SportIdentStationInfo

class AndroidSportIdentCardInspectionAssemblerTest {
    private val station = SportIdentStationInfo(
        serialNumber = 1_234,
        extendedMode = true,
        stationCodeNumber = 1,
        stationModeCode = 5
    )

    @Test
    fun assemblesSi8OwnerNamesPunchCountAndRecoveryFixture() {
        val data = ByteArray(256) { 0xEE.toByte() }
        data[22] = 0
        data[24] = 2
        putCardNumber(data, 2_450_662)
        put(data, 32, "Daisy;Duck;")
        val blocks = data.toBlocks()

        val result = AndroidSportIdentCardInspectionAssembler.fromSi8OrNewer(
            station,
            blocks
        )

        assertEquals(SportIdentCardFamily.SI8, result.owner.family)
        assertEquals(SportIdentOwnerDataStatus.READ, result.owner.status)
        assertEquals("Daisy", result.owner.holder?.firstName)
        assertEquals("Duck", result.owner.holder?.lastName)
        assertEquals(0, result.controlPunchCount)
        assertEquals(2_450_662, result.owner.siNumber)
        assertNotNull(result.si8RawRead)
    }

    @Test
    fun readsModernOwnerDataFromOwnerBlocksWhilePunchesUseBlocksFourToSeven() {
        val blocks = (0..7).associateWith { ByteArray(128) }.toMutableMap()
        val ownerBytes = ByteArray(512)
        put(ownerBytes, 32, "Alice;Runner;F;2000;Orienteering Club;")
        ownerBytes[447] = 1
        (0..3).forEach { block ->
            ownerBytes.copyOfRange(block * 128, (block + 1) * 128)
                .copyInto(blocks.getValue(block))
        }
        blocks.getValue(0)[22] = 0
        blocks.getValue(0)[24] = 15
        putCardNumber(blocks.getValue(0), 8_000_001)

        val result = AndroidSportIdentCardInspectionAssembler.fromSi8OrNewer(
            station,
            blocks.map { SportIdentCardBlock(it.key, it.value) }
        )

        assertEquals(SportIdentCardFamily.SIAC, result.owner.family)
        assertEquals("Alice", result.owner.holder?.firstName)
        assertEquals("Runner", result.owner.holder?.lastName)
        assertEquals("Orienteering Club", result.owner.holder?.club)
        assertEquals(null, result.si8RawRead)
    }

    private fun ByteArray.toBlocks(): List<SportIdentCardBlock> =
        toList().chunked(128).mapIndexed { index, bytes ->
            SportIdentCardBlock(index, bytes.toByteArray())
        }

    private fun putCardNumber(data: ByteArray, number: Int) {
        data[25] = (number shr 16).toByte()
        data[26] = (number shr 8).toByte()
        data[27] = number.toByte()
    }

    private fun put(data: ByteArray, offset: Int, text: String) {
        text.forEachIndexed { index, character ->
            data[offset + index] = character.code.toByte()
        }
    }
}
