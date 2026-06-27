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

package org.openardf.radiooracle.backend.sportident

import org.openardf.radiooracle.shared.sportident.SportIdentCodes
import org.openardf.radiooracle.shared.sportident.SportIdentUsbDevice

/** SportIdent protocol constants and validation helpers used by the Android reader. */
object SIConstants {
    const val SI_VENDOR_ID = SportIdentUsbDevice.VENDOR_ID
    const val SI_PRODUCT_ID = SportIdentUsbDevice.PRODUCT_ID
    const val SI_MIN_NUMBER = SportIdentCodes.SI_MIN_NUMBER
    const val SI_MAX_NUMBER = SportIdentCodes.SI_MAX_NUMBER

    const val STX: Byte = 0x02 // Transmission start.
    const val ETX: Byte = 0x03 // Transmission end.
    const val ACK: Byte = 0x06 // Acknowledgment.
    const val NAK: Byte = 0x15 // Negative acknowledgment.
    const val DLE: Byte = 0x10 // Delimiter.
    const val WAKEUP: Byte = 0xFF.toByte() // Wake up station.
    const val GET_SYSTEM_INFO: Byte = 0x83.toByte()
    const val EXTENDED_MODE: Byte = 0x01.toByte()
    const val ZERO: Byte = 0x00
    const val NULL: Byte = 0xEE.toByte()

    const val PERIOD = 200L
    const val READ_WRITE_TIMEOUT = 300
    const val BAUDRATE_LOW = 4800
    const val BAUDRATE_HIGH = 38400
    const val POLYNOM = 0x8005 // CRC polynomial used by the SI protocol.

    const val SECONDS_DAY = SportIdentCodes.SECONDS_DAY
    const val SECONDS_WEEK = SportIdentCodes.SECONDS_WEEK

    const val SI_CARD5: Byte = 0xE5.toByte()
    const val GET_SI_CARD5: Byte = 0xB1.toByte()
    const val SI_CARD6: Byte = 0xE6.toByte()
    const val GET_SI_CARD6: Byte = 0xE1.toByte()
    const val SI_CARD8_9_SIAC: Byte = 0xE8.toByte()
    const val GET_SI_CARD8_9_SIAC: Byte = 0xEF.toByte()
    const val SI_CARD_REMOVED: Byte = 0xE7.toByte()

    // SI card series values determine which readout process to use.
    const val SI_CARD8_SERIES = 2
    const val SI_CARD9_SERIES = 1
    const val SI_CARD_PCARD_SERIES = 4
    const val SI_CARD10_11_SIAC_SERIES = 15
    const val SI_CARD_10_11_SIAC_MIN_NUMBER = 7E6

    // Maximum number of punches stored by each card family.
    const val SI_CARD5_MAX_PUNCHES = 30
    const val SI_CARD6_MAX_PUNCHES = 192 // TODO: Verify against the SI6 card specification.
    const val SI_CARD8_MAX_PUNCHES = 30
    const val SI_CARD9_MAX_PUNCHES = 50
    const val SI_CARD10_11_SIAC_MAX_PUNCHES = 128
    const val SI_CARD_PCARD_MAX_PUNCHES = 20

    // Valid SportIdent control-code range.
    const val SI_MIN_CODE = SportIdentCodes.SI_MIN_CODE
    const val SI_MAX_CODE = SportIdentCodes.SI_MAX_CODE

    // Android foreground-service notification identifiers.
    const val NOTIFICATION_CHANNEL_ID = "si_reader_channel"
    const val NOTIFICATION_CHANNEL_NAME = "SI Reader"
    const val NOTIFICATION_PERMISSION_CODE = 1001

    // Android USB permission action used by the reader service.
    const val INTENT_ACTION = "org.openardf.radiooracle.USB_PERMISSION"

    /** Returns whether a card number is inside the supported SportIdent card-number range. */
    fun isSINumberValid(siNumber: Int): Boolean {
        return SportIdentCodes.isSINumberValid(siNumber)
    }

    /** Returns whether a control code is inside the supported SportIdent control-code range. */
    fun isSICodeValid(siCode: Int): Boolean {
        return SportIdentCodes.isSICodeValid(siCode)
    }
}
