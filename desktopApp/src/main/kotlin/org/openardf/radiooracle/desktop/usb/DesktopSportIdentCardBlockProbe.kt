package org.openardf.radiooracle.desktop.usb

import org.openardf.radiooracle.shared.sportident.SportIdentCardBlock
import org.openardf.radiooracle.shared.sportident.SportIdentCardBlockParser
import org.openardf.radiooracle.shared.sportident.SportIdentCardEvent
import org.openardf.radiooracle.shared.sportident.SportIdentCardEventParser
import org.openardf.radiooracle.shared.sportident.SportIdentCardReadout
import org.openardf.radiooracle.shared.sportident.SportIdentCardReadoutParser
import org.openardf.radiooracle.shared.sportident.SportIdentFrameParser
import org.openardf.radiooracle.shared.sportident.SportIdentProtocol

fun main(args: Array<String>) {
    val requestedPort = args.firstOrNull() ?: System.getenv("RADIO_ORACLE_SI_PORT")
    val provider = JSerialCommDesktopSerialPortProvider
    val ports = provider.listPorts()
    val port = if (requestedPort.isNullOrBlank()) {
        ports.firstOrNull { it.info.matchesSportIdent() } ?: error("No SPORTident USB serial port found.")
    } else {
        provider.getPort(requestedPort)
    }

    println("Radio-Oracle desktop SPORTident card-block probe")
    println("Using serial port: ${port.info.describe()}")

    try {
        val station = DesktopSportIdentStationProbe().connectKeepingPortOpen(port)
        println(
            "Station ready at ${station.baudRate} baud: serial=${station.stationInfo.serialNumber} " +
                "extended=${station.stationInfo.extendedMode} " +
                "codeNumber=${station.stationInfo.stationCodeNumber ?: "unknown"} " +
                "modeCode=${station.stationInfo.stationModeCode ?: "unknown"} " +
                "mode=${station.stationInfo.stationModeLabel ?: "unknown"}"
        )
        warnIfNotReadoutMode(station)
        println("Insert an SI5/SI6/SI8/SI9/SIAC card when prompted.")

        val download = DesktopSportIdentCardBlockReader(onProgress = ::println)
            .readFirstSupportedCardAfterInsertOnOpenPort(port)
        val inserted = download.inserted

        println("Card inserted: type=${inserted.cardType.toHexString()} si=${inserted.siNumber}")
        download.blocks.forEach { block ->
            println(
                "Downloaded block ${block.blockNumber}: bytes=${block.data.size} " +
                    "series=${block.data.cardSeries()} punchCount=${block.data.punchCount()} " +
                    "cardNumber=${block.data.cardNumber()}"
            )
        }
        println(
            "Parsed card: si=${download.readout.siNumber} series=${download.readout.series} " +
                "check=${download.readout.checkTime?.getTimeString() ?: "none"} " +
                "start=${download.readout.startTime?.getTimeString() ?: "none"} " +
                "finish=${download.readout.finishTime?.getTimeString() ?: "none"} " +
                "punches=${download.readout.punches.size}"
        )
    } finally {
        if (port.isOpen) {
            port.close()
        }
    }
}

private fun warnIfNotReadoutMode(station: DesktopSportIdentStationConnection) {
    if (station.stationInfo.isReadoutMode == false) {
        println(
            "WARNING: SPORTident station ${station.stationInfo.serialNumber} is in " +
                "${station.stationInfo.stationModeLabel} mode instead of READOUT/SI MASTER. " +
                "Reprogram it in a download-capable mode before using it for SI-card downloads."
        )
    }
}

data class DesktopSportIdentCardBlockDownload(
    val inserted: SportIdentCardEvent.Inserted,
    val blocks: List<SportIdentCardBlock>,
    val readout: SportIdentCardReadout
)

class DesktopSportIdentCardBlockReader(
    private val readTimeoutMs: Int = 5000,
    private val writeTimeoutMs: Int = 5000,
    private val openWaitTimeMs: Int = 200,
    private val postAckSettleMs: Long = 500,
    private val onProgress: (String) -> Unit = {}
) {
    fun readFirstSi8Or9OrSiacBlockAfterInsert(
        port: DesktopSerialPort,
        baudRate: Int
    ): DesktopSportIdentCardBlockDownload =
        readFirstSupportedCardAfterInsert(port, baudRate)

    fun readFirstSupportedCardAfterInsert(
        port: DesktopSerialPort,
        baudRate: Int
    ): DesktopSportIdentCardBlockDownload {
        try {
            port.configure(baudRate, readTimeoutMs, writeTimeoutMs)
            if (!port.open(openWaitTimeMs)) {
                error("Failed to open serial port ${port.info.systemPortPath}.")
            }

            val event = DesktopSportIdentCardEventMonitor(
                readTimeoutMs = readTimeoutMs,
                writeTimeoutMs = writeTimeoutMs
            ).waitForInsertEventOnOpenPort(
                port,
                System.currentTimeMillis() + DesktopSportIdentCardEventMonitor.defaultMaxWaitMs
            ) ?: error("No SPORTident card insert event received before timeout.")
            onProgress("Card inserted: type=${event.cardType.toHexString()} si=${event.siNumber}; keep it seated.")

            return readInsertedCardOnOpenPort(port, event)
        } finally {
            if (port.isOpen) {
                port.close()
            }
        }
    }

    fun readFirstSi8Or9OrSiacBlockAfterInsertOnOpenPort(
        port: DesktopSerialPort
    ): DesktopSportIdentCardBlockDownload =
        readFirstSupportedCardAfterInsertOnOpenPort(port)

    fun readFirstSupportedCardAfterInsertOnOpenPort(
        port: DesktopSerialPort
    ): DesktopSportIdentCardBlockDownload {
        val event = DesktopSportIdentCardEventMonitor(
            readTimeoutMs = readTimeoutMs,
            writeTimeoutMs = writeTimeoutMs
        ).waitForInsertEventOnOpenPort(
            port,
            System.currentTimeMillis() + DesktopSportIdentCardEventMonitor.defaultMaxWaitMs
        ) ?: error("No SPORTident card insert event received before timeout.")
        onProgress("Card inserted: type=${event.cardType.toHexString()} si=${event.siNumber}; keep it seated.")

        return readInsertedCardOnOpenPort(port, event)
    }

    private fun readInsertedCardOnOpenPort(
        port: DesktopSerialPort,
        inserted: SportIdentCardEvent.Inserted
    ): DesktopSportIdentCardBlockDownload {
        val blocks = when (inserted.cardType) {
            SportIdentProtocol.SI_CARD5 -> emptyList()
            SportIdentProtocol.SI_CARD6 -> readSi6BlocksOnOpenPort(port)
            SportIdentProtocol.SI_CARD8_9_SIAC -> readSi8Or9OrSiacBlocksOnOpenPort(port)
            else -> error(
                "Only SI5/SI6/SI8/SI9/SIAC download is supported by this diagnostic; " +
                    "got ${inserted.cardType.toHexString()}."
            )
        }
        val readout = when (inserted.cardType) {
            SportIdentProtocol.SI_CARD5 -> readSi5ReadoutOnOpenPort(port)
            SportIdentProtocol.SI_CARD6 -> SportIdentCardReadoutParser.parseSi6(combineBlocksForAndroidParser(blocks))
            SportIdentProtocol.SI_CARD8_9_SIAC -> SportIdentCardReadoutParser.parseSi8Or9OrSiac(
                combineBlocksForAndroidParser(blocks)
            )
            else -> null
        } ?: error("Downloaded SI card blocks could not be parsed.")
        writeAck(port)
        return DesktopSportIdentCardBlockDownload(inserted, blocks, readout)
    }

    private fun readSi5ReadoutOnOpenPort(port: DesktopSerialPort): SportIdentCardReadout {
        val request = SportIdentProtocol.buildExtendedMessage(SportIdentProtocol.GET_SI_CARD5)
        val written = port.write(request)
        if (written != request.size) {
            error("Failed to write SI5 card request.")
        }

        val deadline = System.currentTimeMillis() + readTimeoutMs
        var lastRawRead: ByteArray? = null
        while (System.currentTimeMillis() < deadline) {
            val raw = port.read(MAX_FRAME_BYTES)
            if (raw.isEmpty()) {
                continue
            }
            lastRawRead = raw

            val event = SportIdentFrameParser.firstFrame(
                raw,
                requireValidCrc = false
            )?.let(SportIdentCardEventParser::fromFrame)
            if (event is SportIdentCardEvent.Removed) {
                error(
                    "SI card ${event.siNumber} was removed before it could be downloaded. " +
                        "Keep the card seated until the download finishes."
                )
            }

            val frame = SportIdentFrameParser.firstFrame(
                raw,
                commandFilter = SportIdentProtocol.GET_SI_CARD5,
                requireValidCrc = false
            ) ?: continue

            if (frame.crcValid == false) {
                continue
            }
            val readout = SportIdentCardReadoutParser.parseSi5(frame.data)
            if (readout != null) {
                onProgress("Downloaded SI5 card payload; keep the card seated.")
                return readout
            }
        }

        error(
            lastRawRead?.let { "No valid SI5 card response received. Last raw read: ${it.toHexString()}" }
                ?: "No valid SI5 card response received."
        )
    }

    private fun readSi6BlocksOnOpenPort(port: DesktopSerialPort): List<SportIdentCardBlock> =
        SI6_BLOCK_READ_ORDER.map { blockNumber ->
            readCardBlockOnOpenPort(
                port = port,
                blockNumber = blockNumber,
                command = SportIdentProtocol.GET_SI_CARD6,
                parser = SportIdentCardBlockParser::si6Block
            )
        }

    private fun readSi8Or9OrSiacBlocksOnOpenPort(port: DesktopSerialPort): List<SportIdentCardBlock> {
        val firstBlock = readCardBlockOnOpenPort(
            port = port,
            blockNumber = 0,
            command = SportIdentProtocol.GET_SI_CARD8_9_SIAC,
            parser = SportIdentCardBlockParser::si8Or9OrSiacBlock
        )
        val nextBlocks = if (firstBlock.data.cardSeries() == SI_CARD10_11_SIAC_SERIES) {
            (4..7).map {
                readCardBlockOnOpenPort(
                    port = port,
                    blockNumber = it,
                    command = SportIdentProtocol.GET_SI_CARD8_9_SIAC,
                    parser = SportIdentCardBlockParser::si8Or9OrSiacBlock
                )
            }
        } else {
            listOf(
                readCardBlockOnOpenPort(
                    port = port,
                    blockNumber = 1,
                    command = SportIdentProtocol.GET_SI_CARD8_9_SIAC,
                    parser = SportIdentCardBlockParser::si8Or9OrSiacBlock
                )
            )
        }
        return listOf(firstBlock) + nextBlocks
    }

    private fun readCardBlockOnOpenPort(
        port: DesktopSerialPort,
        blockNumber: Int,
        command: Byte,
        parser: (Int, org.openardf.radiooracle.shared.sportident.SportIdentFrame) -> SportIdentCardBlock?
    ): SportIdentCardBlock {

        val request = SportIdentProtocol.buildExtendedMessage(
            command,
            byteArrayOf(blockNumber.toByte())
        )
        val written = port.write(request)
        if (written != request.size) {
            error("Failed to write SI card block request.")
        }

        val deadline = System.currentTimeMillis() + readTimeoutMs
        var lastUnexpectedShape: String? = null
        var lastRawRead: ByteArray? = null
        while (System.currentTimeMillis() < deadline) {
            val raw = port.read(MAX_FRAME_BYTES)
            if (raw.isEmpty()) {
                continue
            }
            lastRawRead = raw

            val event = SportIdentFrameParser.firstFrame(
                raw,
                requireValidCrc = false
            )?.let(SportIdentCardEventParser::fromFrame)
            if (event is SportIdentCardEvent.Removed) {
                error(
                    "SI card ${event.siNumber} was removed before block $blockNumber could be downloaded. " +
                        "Keep the card seated until the download finishes."
                )
            }

            val frame = SportIdentFrameParser.firstFrame(
                raw,
                commandFilter = command,
                requireValidCrc = false
            ) ?: continue

            if (frame.crcValid == true) {
                val block = parser(blockNumber, frame)
                if (block != null) {
                    onProgress("Downloaded block $blockNumber; keep the card seated.")
                    return block
                }
            }

            lastUnexpectedShape =
                "command=${frame.command.toHexString()} dataBytes=${frame.data.size} " +
                    "crcValid=${frame.crcValid} raw=${frame.raw.toHexString()}"
        }

        error(lastUnexpectedShape?.let { "SI card block response had unexpected shape: $it" }
            ?: lastRawRead?.let { "No valid SI card block response received. Last raw read: ${it.toHexString()}" }
            ?: "No valid SI card block response received.")
    }

    private fun writeAck(port: DesktopSerialPort) {
        val ack = SportIdentProtocol.buildAckMessage()
        val written = port.write(ack)
        if (written != ack.size) {
            error("Failed to write SI card-read ACK.")
        }
        onProgress("Sent card-read ACK.")
        Thread.sleep(postAckSettleMs)
    }

    private companion object {
        const val MAX_FRAME_BYTES = 512
        const val SI_CARD10_11_SIAC_SERIES = 15
        val SI6_BLOCK_READ_ORDER = listOf(0, 6, 7, 2, 3, 4, 5)
    }
}

private fun Byte.toHexString(): String =
    "0x%02x".format(toInt() and 0xff)

private fun ByteArray.toHexString(): String =
    joinToString(" ") { "%02x".format(it.toInt() and 0xff) }

private fun ByteArray.cardSeries(): Int =
    getOrNull(24)?.toInt()?.and(0x0f) ?: -1

private fun ByteArray.punchCount(): Int =
    getOrNull(22)?.toInt()?.and(0xff) ?: -1

private fun ByteArray.cardNumber(): Int =
    if (size > 27) {
        ((this[25].toInt() and 0xff) shl 16) +
            ((this[26].toInt() and 0xff) shl 8) +
            (this[27].toInt() and 0xff)
    } else {
        -1
    }

private fun combineBlocksForAndroidParser(blocks: List<SportIdentCardBlock>): ByteArray =
    ByteArray(blocks.size * SportIdentProtocol.SI_CARD_BLOCK_SIZE).also { combined ->
        blocks.forEachIndexed { index, block ->
            block.data.copyInto(
                destination = combined,
                destinationOffset = index * SportIdentProtocol.SI_CARD_BLOCK_SIZE
            )
        }
    }
