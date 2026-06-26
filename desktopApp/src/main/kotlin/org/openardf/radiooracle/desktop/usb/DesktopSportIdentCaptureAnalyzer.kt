package org.openardf.radiooracle.desktop.usb

import java.nio.file.Files
import java.nio.file.Path
import org.openardf.radiooracle.shared.sportident.SportIdentFrame
import org.openardf.radiooracle.shared.sportident.SportIdentFrameParser
import org.openardf.radiooracle.shared.sportident.SportIdentProtocol

fun main(args: Array<String>) {
    val input = when {
        args.isEmpty() -> generateSequence(::readLine).joinToString("\n")
        args.size == 1 && Files.exists(Path.of(args[0])) -> Files.readString(Path.of(args[0]))
        else -> args.joinToString(" ")
    }
    val bytes = DesktopSportIdentCaptureAnalyzer.hexToBytes(input)
    val frames = DesktopSportIdentCaptureAnalyzer.framesFrom(bytes)

    println("SPORTident capture bytes: ${bytes.size}")
    println("SPORTident frames: ${frames.size}")
    frames.forEachIndexed { index, frame ->
        println(DesktopSportIdentCaptureAnalyzer.describeFrame(index + 1, frame))
    }
}

object DesktopSportIdentCaptureAnalyzer {
    fun hexToBytes(text: String): ByteArray {
        val compactHex = HEX_PAIR_REGEX.findAll(text)
            .joinToString("") { it.value }
        require(compactHex.length % 2 == 0) { "Hex input must contain complete byte pairs." }
        return compactHex.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    fun framesFrom(bytes: ByteArray): List<SportIdentFrame> {
        val frames = mutableListOf<SportIdentFrame>()
        var remaining = bytes
        while (remaining.isNotEmpty()) {
            val frame = SportIdentFrameParser.firstFrame(remaining, requireValidCrc = false) ?: break
            frames += frame
            val rawStart = remaining.indexOf(frame.raw)
            if (rawStart < 0) {
                break
            }
            remaining = remaining.copyOfRange(rawStart + frame.raw.size, remaining.size)
        }
        return frames
    }

    fun describeFrame(index: Int, frame: SportIdentFrame): String =
        buildString {
            append("#")
            append(index)
            append(" command=")
            append(frame.command.toHexByte())
            append(" ")
            append(commandLabel(frame.command))
            append(" extended=")
            append(frame.extended)
            append(" crc=")
            append(frame.crcValid?.toString() ?: "n/a")
            append(" dataLen=")
            append(frame.data.size)
            if (frame.data.isNotEmpty()) {
                append(" data=")
                append(frame.data.toHexString())
            }
        }

    private fun commandLabel(command: Byte): String =
        when (command) {
            SportIdentProtocol.ACK -> "ACK"
            SportIdentProtocol.NAK -> "NAK"
            SportIdentProtocol.GET_SYSTEM_INFO -> "GET_SYSTEM_INFO"
            SportIdentProtocol.PROBE_COMMAND -> "PROBE"
            SportIdentProtocol.GET_SI_CARD5 -> "GET_SI_CARD5"
            SportIdentProtocol.GET_SI_CARD6 -> "GET_SI_CARD6"
            SportIdentProtocol.GET_SI_CARD8_9_SIAC -> "GET_SI_CARD8_9_SIAC"
            SportIdentProtocol.SI_CARD5 -> "SI_CARD5"
            SportIdentProtocol.SI_CARD6 -> "SI_CARD6"
            SportIdentProtocol.SI_CARD8_9_SIAC -> "SI_CARD8_9_SIAC"
            SportIdentProtocol.SI_CARD_REMOVED -> "SI_CARD_REMOVED"
            else -> "UNKNOWN"
        }

    private fun ByteArray.indexOf(needle: ByteArray): Int {
        if (needle.isEmpty() || needle.size > size) {
            return -1
        }
        for (candidate in 0..(size - needle.size)) {
            var matches = true
            for (offset in needle.indices) {
                if (this[candidate + offset] != needle[offset]) {
                    matches = false
                    break
                }
            }
            if (matches) {
                return candidate
            }
        }
        return -1
    }

    private fun Byte.toHexByte(): String =
        "0x${(toInt() and 0xff).toString(16).uppercase().padStart(2, '0')}"

    private fun ByteArray.toHexString(): String =
        joinToString(" ") { (it.toInt() and 0xff).toString(16).uppercase().padStart(2, '0') }

    private val HEX_PAIR_REGEX = Regex("(?i)(?<![0-9a-f])[0-9a-f]{2}(?![0-9a-f])")
}
