/*
 * MIT License
 *
 * Copyright (c) 2025 Pavel Kolský
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.openardf.radiooracle.desktop.usb

import org.openardf.radiooracle.shared.sportident.SportIdentCardBlock
import org.openardf.radiooracle.shared.sportident.SportIdentCardBlockParser
import org.openardf.radiooracle.shared.sportident.SportIdentCardCommandReader
import org.openardf.radiooracle.shared.sportident.SportIdentCardEvent
import org.openardf.radiooracle.shared.sportident.SportIdentCardReadFailure
import org.openardf.radiooracle.shared.sportident.SportIdentCardReadRetryPolicy
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
    private val onProgress: (String) -> Unit = {},
    private val sleepMillis: (Long) -> Unit = Thread::sleep,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    private val cardReadAttemptTimeoutMs: Int = SportIdentCardCommandReader.DEFAULT_ATTEMPT_TIMEOUT_MS,
    private val cardReadRetryDelayMs: Long = SportIdentCardCommandReader.DEFAULT_RETRY_DELAY_MS,
    private val cardReadMaxAttempts: Int = SportIdentCardReadRetryPolicy.DEFAULT_MAX_ATTEMPTS
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
            port.configure(baudRate, minOf(readTimeoutMs, cardReadAttemptTimeoutMs), writeTimeoutMs)
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
        val commandReader = SportIdentCardCommandReader(
            writeCommand = { command, payload ->
                val request = SportIdentProtocol.buildExtendedMessage(command, payload ?: byteArrayOf())
                port.write(request) == request.size
            },
            readChunk = { _ -> port.read(MAX_FRAME_BYTES) },
            sleepMillis = sleepMillis,
            nowMillis = nowMillis,
            attemptTimeoutMillis = cardReadAttemptTimeoutMs,
            retryDelayMillis = cardReadRetryDelayMs,
            maxAttempts = cardReadMaxAttempts
        )
        val blocks = when (inserted.cardType) {
            SportIdentProtocol.SI_CARD5 -> emptyList()
            SportIdentProtocol.SI_CARD6 -> readSi6BlocksOnOpenPort(commandReader)
            SportIdentProtocol.SI_CARD8_9_SIAC -> readSi8Or9OrSiacBlocksOnOpenPort(commandReader)
            else -> error(
                "Only SI5/SI6/SI8/SI9/SIAC card download is supported; " +
                    "got ${inserted.cardType.toHexString()}."
            )
        }
        val readout = when (inserted.cardType) {
            SportIdentProtocol.SI_CARD5 -> readSi5ReadoutOnOpenPort(commandReader)
            SportIdentProtocol.SI_CARD6 -> SportIdentCardReadoutParser.parseSi6(combineSi6BlocksForAndroidParser(blocks))
            SportIdentProtocol.SI_CARD8_9_SIAC -> SportIdentCardReadoutParser.parseSi8Or9OrSiac(
                combineBlocksForAndroidParser(blocks)
            )
            else -> null
        } ?: error("Downloaded SI card blocks could not be parsed.")
        writeAck(port)
        return DesktopSportIdentCardBlockDownload(inserted, blocks, readout)
    }

    private fun readSi5ReadoutOnOpenPort(
        commandReader: SportIdentCardCommandReader
    ): SportIdentCardReadout {
        val reply = readCardCommandReply(
            commandReader = commandReader,
            command = SportIdentProtocol.GET_SI_CARD5,
            payload = null,
            expectedReplyBytes = SI5_REPLY_BYTES,
            description = "SI5 card payload"
        )
        val frame = requireNotNull(SportIdentFrameParser.firstFrame(reply, requireValidCrc = true))
        val readout = SportIdentCardReadoutParser.parseSi5(frame.data)
            ?: error("Downloaded SI5 card payload could not be parsed.")
        onProgress("Downloaded SI5 card payload; keep the card seated.")
        return readout
    }

    private fun readSi6BlocksOnOpenPort(
        commandReader: SportIdentCardCommandReader
    ): List<SportIdentCardBlock> =
        buildList {
            for (blockNumber in SI6_BLOCK_READ_ORDER) {
                val block = readCardBlockOnOpenPort(
                    commandReader = commandReader,
                    blockNumber = blockNumber,
                    command = SportIdentProtocol.GET_SI_CARD6,
                    parser = SportIdentCardBlockParser::si6Block
                )
                add(block)
                if (blockNumber != 0 && block.data.endsWithEmptyPunch()) {
                    break
                }
            }
        }

    private fun readSi8Or9OrSiacBlocksOnOpenPort(
        commandReader: SportIdentCardCommandReader
    ): List<SportIdentCardBlock> {
        val firstBlock = readCardBlockOnOpenPort(
            commandReader = commandReader,
            blockNumber = 0,
            command = SportIdentProtocol.GET_SI_CARD8_9_SIAC,
            parser = SportIdentCardBlockParser::si8Or9OrSiacBlock
        )
        val nextBlocks = if (firstBlock.data.cardSeries() == SI_CARD10_11_SIAC_SERIES) {
            (4..7).map {
                readCardBlockOnOpenPort(
                    commandReader = commandReader,
                    blockNumber = it,
                    command = SportIdentProtocol.GET_SI_CARD8_9_SIAC,
                    parser = SportIdentCardBlockParser::si8Or9OrSiacBlock
                )
            }
        } else {
            listOf(
                readCardBlockOnOpenPort(
                    commandReader = commandReader,
                    blockNumber = 1,
                    command = SportIdentProtocol.GET_SI_CARD8_9_SIAC,
                    parser = SportIdentCardBlockParser::si8Or9OrSiacBlock
                )
            )
        }
        return listOf(firstBlock) + nextBlocks
    }

    private fun readCardBlockOnOpenPort(
        commandReader: SportIdentCardCommandReader,
        blockNumber: Int,
        command: Byte,
        parser: (Int, org.openardf.radiooracle.shared.sportident.SportIdentFrame) -> SportIdentCardBlock?
    ): SportIdentCardBlock {
        val reply = readCardCommandReply(
            commandReader = commandReader,
            command = command,
            payload = byteArrayOf(blockNumber.toByte()),
            expectedReplyBytes = CARD_BLOCK_REPLY_BYTES,
            description = "SI card block $blockNumber"
        )
        val frame = requireNotNull(SportIdentFrameParser.firstFrame(reply, requireValidCrc = true))
        val block = parser(blockNumber, frame)
            ?: error("Downloaded SI card block $blockNumber could not be parsed.")
        onProgress("Downloaded block $blockNumber; keep the card seated.")
        return block
    }

    private fun readCardCommandReply(
        commandReader: SportIdentCardCommandReader,
        command: Byte,
        payload: ByteArray?,
        expectedReplyBytes: Int,
        description: String
    ): ByteArray {
        val result = commandReader.read(command, payload, expectedReplyBytes)
        result.reply?.let { reply ->
            if (result.attempts.size > 1) {
                onProgress("Recovered $description on attempt ${result.attempts.size}.")
            }
            return reply
        }

        val failure = result.attempts.lastOrNull()?.failure
            ?: SportIdentCardReadFailure.NO_COMPLETE_REPLY
        val attempts = result.attempts.size
        val message = when (failure) {
            SportIdentCardReadFailure.CARD_REMOVED ->
                "SI card was removed before $description could be downloaded. " +
                    "Keep the card seated until the download finishes."
            SportIdentCardReadFailure.WRITE_FAILED ->
                "Failed to write the $description request after $attempts attempts."
            SportIdentCardReadFailure.NEGATIVE_ACKNOWLEDGEMENT ->
                "SPORTident station rejected the $description request after $attempts attempts."
            SportIdentCardReadFailure.NO_COMPLETE_REPLY ->
                "No complete $description response was received after $attempts attempts."
            SportIdentCardReadFailure.INVALID_FRAME ->
                "$description response remained incomplete after $attempts attempts."
            SportIdentCardReadFailure.INVALID_CRC ->
                "$description response failed CRC validation after $attempts attempts."
            SportIdentCardReadFailure.UNEXPECTED_REPLY_SIZE ->
                "$description response had an unexpected size after $attempts attempts."
        }
        error(message)
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
        const val SI5_REPLY_BYTES = 136
        const val CARD_BLOCK_REPLY_BYTES = 137
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

private fun ByteArray.endsWithEmptyPunch(): Boolean {
    if (size < 4) {
        return false
    }
    return this[size - 4] == SI_CARD_EMPTY_BYTE &&
        this[size - 3] == SI_CARD_EMPTY_BYTE &&
        this[size - 2] == SI_CARD_EMPTY_BYTE &&
        this[size - 1] == SI_CARD_EMPTY_BYTE
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

private fun combineSi6BlocksForAndroidParser(blocks: List<SportIdentCardBlock>): ByteArray {
    val blockByNumber = blocks.associateBy { it.blockNumber }
    return ByteArray(blocks.size * SportIdentProtocol.SI_CARD_BLOCK_SIZE) { SI_CARD_EMPTY_BYTE }.also { combined ->
        blockByNumber[0]?.data?.copyInto(
            destination = combined,
            destinationOffset = 0
        )
        SI6_PUNCH_BLOCK_READ_ORDER.forEachIndexed { index, blockNumber ->
            blockByNumber[blockNumber]?.data?.copyInto(
                destination = combined,
                destinationOffset = (index + 1) * SportIdentProtocol.SI_CARD_BLOCK_SIZE
            )
        }
    }
}

private val SI_CARD_EMPTY_BYTE = 0xEE.toByte()
private val SI6_PUNCH_BLOCK_READ_ORDER = listOf(6, 7, 2, 3, 4, 5)
