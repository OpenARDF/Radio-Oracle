package org.openardf.radiooracle.desktop.usb

import org.openardf.radiooracle.shared.sportident.SportIdentCardBlockParser
import org.openardf.radiooracle.shared.sportident.SportIdentCommandResult
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerReadVerification
import org.openardf.radiooracle.shared.sportident.SportIdentProtocol

internal enum class DesktopSportIdentCardPresenceResult {
    MATCHING_BLOCK,
    DIFFERENT_BLOCK,
    INVALID_REPLY,
    NEGATIVE_ACKNOWLEDGEMENT,
    NO_REPLY
}

/** One read-only block-0 request. A match is evidence at the time of this request, not a presence lock. */
internal class DesktopSportIdentCardPresenceProbe(
    private val client: DesktopSportIdentStationCommandClient = DesktopSportIdentStationCommandClient()
) {
    fun check(port: DesktopSerialPort, expectedBlock0: ByteArray): DesktopSportIdentCardPresenceResult {
        check(port.isOpen) { "The SPORTident port must already be open." }
        require(expectedBlock0.size == SportIdentProtocol.SI_CARD_BLOCK_SIZE) { "Expected SI-card block 0 must be complete." }
        return when (val result = client.sendCommandResult(
            port, SportIdentProtocol.GET_SI_CARD8_9_SIAC, byteArrayOf(0)
        )) {
            SportIdentCommandResult.NoReply -> DesktopSportIdentCardPresenceResult.NO_REPLY
            SportIdentCommandResult.NegativeAcknowledgement ->
                DesktopSportIdentCardPresenceResult.NEGATIVE_ACKNOWLEDGEMENT
            is SportIdentCommandResult.Reply -> {
                val block = SportIdentCardBlockParser.si8Or9OrSiacBlock(0, result.frame)
                when {
                    block == null -> DesktopSportIdentCardPresenceResult.INVALID_REPLY
                    block.data.contentEquals(expectedBlock0) -> DesktopSportIdentCardPresenceResult.MATCHING_BLOCK
                    else -> DesktopSportIdentCardPresenceResult.DIFFERENT_BLOCK
                }
            }
        }
    }
}

/** Read-only hardware diagnostic; no owner-word command can be sent by this entry point. */
fun main(args: Array<String>) {
    require(args.size == 2) { "Usage: <expected-station> <expected-SI-Card8-number>" }
    val expectedStation = args[0].toInt().also { require(it > 0) }
    val expectedCard = args[1].toInt().also { require(it > 0) }
    val port = DesktopSportIdentPortSelector().selectPort() ?: error("No SPORTident USB station found.")
    try {
        val station = DesktopSportIdentStationProbe().connectKeepingPortOpen(port).stationInfo
        require(station.serialNumber == expectedStation && station.extendedMode &&
            station.isDownloadCapableMode == true) {
            "Connected station ${station.serialNumber} is not the expected download station $expectedStation."
        }
        println("Station $expectedStation ready. Insert SI-Card8 $expectedCard and keep it seated until asked to remove it.")
        val download = DesktopSportIdentCardBlockReader().readFirstSupportedCardAfterInsertOnOpenPort(port)
        require(download.inserted.cardType == SportIdentProtocol.SI_CARD8_9_SIAC &&
            download.inserted.siNumber == expectedCard && download.readout.siNumber == expectedCard &&
            download.readout.series == 2) { "The downloaded card is not the expected SI-Card8." }
        val before = SportIdentOwnerReadVerification.capture(expectedStation, download.blocks)
        require(SportIdentOwnerReadVerification.nativeRead(before).cardNumber == expectedCard) {
            "The raw card block does not match the expected card."
        }
        val block0 = download.blocks.single { it.blockNumber == 0 }.data
        val probe = DesktopSportIdentCardPresenceProbe()
        println("Read-only seated block-0 recheck: ${probe.check(port, block0)}")
        println("Remove SI-Card8 $expectedCard now; waiting for its removal event.")
        val removed = DesktopSportIdentCardEventMonitor().waitForRemoveEventOnOpenPort(
            port, expectedCard, System.currentTimeMillis() + 20_000L
        )
        if (removed == null) {
            println("No target-card removal event observed; no after-removal request sent.")
        } else {
            println("Read-only after-removal block-0 recheck: ${probe.check(port, block0)}")
        }
    } finally {
        if (port.isOpen) port.close()
    }
}
