package org.openardf.radiooracle.desktop.usb

import org.openardf.radiooracle.shared.sportident.SportIdentCardEvent
import org.openardf.radiooracle.shared.sportident.SportIdentCardEventParser
import org.openardf.radiooracle.shared.sportident.SportIdentFrameParser
import org.openardf.radiooracle.shared.sportident.SportIdentProtocol

/** Read-only hardware check of the nonblocking serial queue used before owner words. */
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
        val quietStart = System.nanoTime()
        require(port.readAvailable(512).isEmpty()) { "Unexpected serial input before card insertion." }
        val quietMillis = (System.nanoTime() - quietStart) / 1_000_000
        println("Station $expectedStation ready; empty nonblocking queue check took ${quietMillis}ms. " +
            "Insert SI-Card8 $expectedCard now. No card write will be sent.")

        val deadline = System.currentTimeMillis() + 120_000L
        var buffered = byteArrayOf()
        while (System.currentTimeMillis() < deadline) {
            val chunk = port.readAvailable(512)
            if (chunk.isEmpty()) {
                Thread.sleep(10)
                continue
            }
            buffered += chunk
            require(buffered.size <= 1024) { "Queued event exceeded the expected frame size." }
            val frame = SportIdentFrameParser.firstFrame(buffered, requireValidCrc = false) ?: continue
            require(frame.crcValid != false) { "Queued card event had a bad CRC." }
            val event = SportIdentCardEventParser.fromFrame(frame)
            require(event is SportIdentCardEvent.Inserted &&
                event.cardType == SportIdentProtocol.SI_CARD8_9_SIAC && event.siNumber == expectedCard) {
                "Queued frame was not the expected SI-Card8 insertion event."
            }
            println("Nonblocking queue read observed SI-Card8 $expectedCard insertion; " +
                "frameBytes=${frame.raw.size}. No owner word or recovery record was written. Remove the card now.")
            return
        }
        error("No queued SI-Card8 insertion event observed before timeout.")
    } finally {
        if (port.isOpen) port.close()
    }
}
