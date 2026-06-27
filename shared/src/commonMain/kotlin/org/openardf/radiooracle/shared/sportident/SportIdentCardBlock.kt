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

data class SportIdentCardBlock(
    val blockNumber: Int,
    val data: ByteArray
) {
    init {
        require(data.size == SportIdentProtocol.SI_CARD_BLOCK_SIZE) {
            "SI card block must contain ${SportIdentProtocol.SI_CARD_BLOCK_SIZE} bytes."
        }
    }
}

object SportIdentCardBlockParser {
    fun si6Block(blockNumber: Int, frame: SportIdentFrame): SportIdentCardBlock? {
        if (frame.command != SportIdentProtocol.GET_SI_CARD6) {
            return null
        }
        return blockWithOptionalPrefix(blockNumber, frame.data)
    }

    fun si8Or9OrSiacBlock(blockNumber: Int, frame: SportIdentFrame): SportIdentCardBlock? {
        if (frame.command != SportIdentProtocol.GET_SI_CARD8_9_SIAC) {
            return null
        }
        return blockWithOptionalPrefix(blockNumber, frame.data)
    }

    private fun blockWithOptionalPrefix(blockNumber: Int, data: ByteArray): SportIdentCardBlock? {
        val blockData = when (data.size) {
            SportIdentProtocol.SI_CARD_BLOCK_SIZE -> data
            SportIdentProtocol.SI_CARD_BLOCK_SIZE + SI8_9_SIAC_BLOCK_PREFIX_SIZE ->
                data.copyOfRange(SI_BLOCK_PREFIX_SIZE, data.size)
            else -> return null
        }

        return SportIdentCardBlock(
            blockNumber = blockNumber,
            data = blockData
        )
    }

    private const val SI8_9_SIAC_BLOCK_PREFIX_SIZE = 3
    private const val SI_BLOCK_PREFIX_SIZE = 3
}
