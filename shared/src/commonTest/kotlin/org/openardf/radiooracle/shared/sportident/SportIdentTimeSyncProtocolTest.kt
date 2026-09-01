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

package org.openardf.radiooracle.shared.sportident

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class SportIdentTimeSyncProtocolTest {
    @Test
    fun sharedProtocolBuildsCapturedRemoteTimeSyncFrames() {
        assertContentEquals(
            hex("FF 02 F0 01 53 73 0A 03"),
            SportIdentTimeSyncProtocol.enterRemoteModeStep().frameBytes
        )
        assertContentEquals(
            hex("FF 02 83 02 00 75 BD 29 03"),
            SportIdentTimeSyncProtocol.readCompatibleSystemInfoStep().frameBytes
        )
        assertContentEquals(
            hex("FF 02 F7 00 F7 00 03"),
            SportIdentTimeSyncProtocol.readStationTimeStep("read").frameBytes
        )
        assertContentEquals(
            hex("FF 02 F6 07 1A 06 1B 0D 44 04 00 10 91 03"),
            SportIdentTimeSyncProtocol.writeStationTimeStep(
                LocalDateTime.parse("2026-06-27T16:50:12")
            ).frameBytes
        )
        assertContentEquals(
            hex("FF 02 F9 01 01 17 0A 03"),
            SportIdentTimeSyncProtocol.applyStationTimeStep().frameBytes
        )
        assertContentEquals(
            hex("FF 02 F0 01 4D 6D 0A 03"),
            SportIdentTimeSyncProtocol.exitRemoteModeStep().frameBytes
        )
    }

    @Test
    fun stationClockCodecRoundTripsFractionalTicksAndReplyPrefixes() {
        val source = LocalDateTime.parse("2026-06-27T03:10:18.500")
        val payload = SportIdentStationTimeCodec.encodePayload(source)
        val decoded = assertNotNull(SportIdentStationTimeCodec.decodePayload(byteArrayOf(0, 1) + payload))

        assertEquals(source.withNano(0), decoded.dateTime)
        assertEquals(128, decoded.tick)
        assertEquals(source, decoded.preciseDateTime)
        assertEquals("AM", decoded.halfDayLabel)
    }

    @Test
    fun stationClockCodecRejectsYearsOutsideStationRange() {
        assertFailsWith<IllegalArgumentException> {
            SportIdentStationTimeCodec.encodePayload(LocalDateTime.parse("2100-01-01T00:00:00"))
        }
    }

    private fun hex(value: String): ByteArray =
        value.split(' ').map { it.toInt(16).toByte() }.toByteArray()
}
