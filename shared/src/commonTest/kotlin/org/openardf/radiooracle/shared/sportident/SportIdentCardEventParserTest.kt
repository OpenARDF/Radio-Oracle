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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class SportIdentCardEventParserTest {
    @Test
    fun parsesCardInsertedEvent() {
        val frame = assertNotNull(
            SportIdentFrameParser.firstFrame(
                SportIdentProtocol.buildExtendedMessage(
                    command = SportIdentProtocol.SI_CARD8_9_SIAC,
                    data = byteArrayOf(0x00, 0x00, 0x00, 0x08, 0x77, 0x90.toByte())
                ),
                commandFilter = SportIdentProtocol.SI_CARD8_9_SIAC
            )
        )

        val event = assertIs<SportIdentCardEvent.Inserted>(
            SportIdentCardEventParser.fromFrame(frame)
        )

        assertEquals(SportIdentProtocol.SI_CARD8_9_SIAC, event.cardType)
        assertEquals(554896, event.siNumber)
    }

    @Test
    fun parsesCardRemovedEvent() {
        val frame = assertNotNull(
            SportIdentFrameParser.firstFrame(
                SportIdentProtocol.buildExtendedMessage(
                    command = SportIdentProtocol.SI_CARD_REMOVED,
                    data = byteArrayOf(0x00, 0x00, 0x00, 0x08, 0x77, 0x90.toByte())
                ),
                commandFilter = SportIdentProtocol.SI_CARD_REMOVED
            )
        )

        val event = assertIs<SportIdentCardEvent.Removed>(
            SportIdentCardEventParser.fromFrame(frame)
        )

        assertEquals(554896, event.siNumber)
    }
}
