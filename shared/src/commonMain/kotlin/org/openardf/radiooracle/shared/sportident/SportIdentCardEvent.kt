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

sealed class SportIdentCardEvent {
    data class Inserted(
        val cardType: Byte,
        val siNumber: Int
    ) : SportIdentCardEvent()

    data class Removed(
        val siNumber: Int
    ) : SportIdentCardEvent()
}

object SportIdentCardEventParser {
    fun fromFrame(frame: SportIdentFrame): SportIdentCardEvent? =
        when (frame.command) {
            SportIdentProtocol.SI_CARD5,
            SportIdentProtocol.SI_CARD6,
            SportIdentProtocol.SI_CARD8_9_SIAC -> insertedEvent(frame)
            SportIdentProtocol.SI_CARD_REMOVED -> removedEvent(frame)
            else -> null
        }

    private fun insertedEvent(frame: SportIdentFrame): SportIdentCardEvent.Inserted? {
        if (frame.data.size < INSERTED_CARD_NUMBER_OFFSET + INSERTED_CARD_NUMBER_BYTES) {
            return null
        }

        val siNumber =
            (frame.data[INSERTED_CARD_NUMBER_OFFSET].toUnsignedInt() shl 16) +
                (frame.data[INSERTED_CARD_NUMBER_OFFSET + 1].toUnsignedInt() shl 8) +
                frame.data[INSERTED_CARD_NUMBER_OFFSET + 2].toUnsignedInt()

        return SportIdentCardEvent.Inserted(
            cardType = frame.command,
            siNumber = siNumber
        )
    }

    private fun removedEvent(frame: SportIdentFrame): SportIdentCardEvent.Removed? {
        if (frame.data.size < REMOVED_CARD_NUMBER_OFFSET + REMOVED_CARD_NUMBER_BYTES) {
            return null
        }

        val siNumber =
            (frame.data[REMOVED_CARD_NUMBER_OFFSET].toUnsignedInt() shl 24) +
                (frame.data[REMOVED_CARD_NUMBER_OFFSET + 1].toUnsignedInt() shl 16) +
                (frame.data[REMOVED_CARD_NUMBER_OFFSET + 2].toUnsignedInt() shl 8) +
                frame.data[REMOVED_CARD_NUMBER_OFFSET + 3].toUnsignedInt()

        return SportIdentCardEvent.Removed(siNumber)
    }

    private const val INSERTED_CARD_NUMBER_OFFSET = 3
    private const val INSERTED_CARD_NUMBER_BYTES = 3
    private const val REMOVED_CARD_NUMBER_OFFSET = 2
    private const val REMOVED_CARD_NUMBER_BYTES = 4
}

private fun Byte.toUnsignedInt(): Int = toInt() and 0xff
