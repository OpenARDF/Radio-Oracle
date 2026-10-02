package org.openardf.radiooracle.backend.sportident

import org.openardf.radiooracle.shared.sportident.SportIdentCardBlock
import org.openardf.radiooracle.shared.sportident.SportIdentCardOwnerInspection
import org.openardf.radiooracle.shared.sportident.SportIdentCardOwnerInspector
import org.openardf.radiooracle.shared.sportident.SportIdentCardReadout
import org.openardf.radiooracle.shared.sportident.SportIdentCardReadoutParser
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerReadFixture
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerReadVerification
import org.openardf.radiooracle.shared.sportident.SportIdentStationInfo

data class AndroidSportIdentCardInspection(
    val stationInfo: SportIdentStationInfo,
    val owner: SportIdentCardOwnerInspection,
    val controlPunchCount: Int,
    /** Complete SI-Card8 blocks used by the guarded writer; absent for other families. */
    val si8RawRead: SportIdentOwnerReadFixture? = null
)

/** Live USB presence states exposed only to the Android card-tool UI. */
enum class AndroidSportIdentCardPresenceState {
    NOT_PRESENT,
    READING,
    PRESENT
}

/** Pure assembly keeps Android USB reads separate from shared card interpretation. */
internal object AndroidSportIdentCardInspectionAssembler {
    fun fromSi5(
        stationInfo: SportIdentStationInfo,
        reply: ByteArray
    ): AndroidSportIdentCardInspection =
        result(
            stationInfo,
            requireNotNull(SportIdentCardReadoutParser.parseSi5(reply)) {
                "The SI-Card5 reply could not be parsed."
            },
            emptyList()
        )

    fun fromSi6(
        stationInfo: SportIdentStationInfo,
        blocks: List<SportIdentCardBlock>
    ): AndroidSportIdentCardInspection {
        val byNumber = uniqueBlocks(blocks)
        val readoutOrder = listOf(0, 6, 7, 2, 3, 4, 5)
        val readout = requireNotNull(
            SportIdentCardReadoutParser.parseSi6(
                readoutOrder.flatMap { byNumber.getValue(it).data.toList() }.toByteArray()
            )
        ) { "The SI-Card6 blocks could not be parsed." }
        return result(stationInfo, readout, blocks)
    }

    fun fromSi8OrNewer(
        stationInfo: SportIdentStationInfo,
        blocks: List<SportIdentCardBlock>
    ): AndroidSportIdentCardInspection {
        val byNumber = uniqueBlocks(blocks)
        val series = byNumber.getValue(0).data[24].toInt() and 0x0f
        val readoutOrder = if (series == 15) listOf(0, 4, 5, 6, 7) else listOf(0, 1)
        val readout = requireNotNull(
            SportIdentCardReadoutParser.parseSi8Or9OrSiac(
                readoutOrder.flatMap { byNumber.getValue(it).data.toList() }.toByteArray()
            )
        ) { "The SI-card blocks could not be parsed." }
        return result(stationInfo, readout, blocks)
    }

    private fun result(
        stationInfo: SportIdentStationInfo,
        readout: SportIdentCardReadout,
        blocks: List<SportIdentCardBlock>
    ): AndroidSportIdentCardInspection {
        val owner = SportIdentCardOwnerInspector.inspect(readout, blocks)
        val raw = if (readout.series == 2) {
            SportIdentOwnerReadVerification.captureRaw(stationInfo.serialNumber, blocks)
        } else {
            null
        }
        return AndroidSportIdentCardInspection(
            stationInfo = stationInfo,
            owner = owner,
            controlPunchCount = readout.punches.size,
            si8RawRead = raw
        )
    }

    private fun uniqueBlocks(blocks: List<SportIdentCardBlock>): Map<Int, SportIdentCardBlock> =
        blocks.associateBy { it.blockNumber }.also {
            require(it.size == blocks.size) { "SI-card read contained duplicate blocks." }
        }
}
