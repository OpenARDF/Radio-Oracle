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
