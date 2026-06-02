package org.openardf.radiooracle.ui.aliases

import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.openardf.radiooracle.backend.room.entity.Alias
import org.openardf.radiooracle.backend.wrappers.AliasEditItemWrapper
import java.util.UUID

class AliasRecyclerViewAdapterTest {
    @Test
    fun getSortedAliasesReturnsAliasesOrderedBySiCode() {
        val raceId = UUID.randomUUID()
        val adapter = AliasRecyclerViewAdapter(
            arrayListOf(
                AliasEditItemWrapper(Alias(UUID.randomUUID(), raceId, 133, "A3"), true, true),
                AliasEditItemWrapper(Alias(UUID.randomUUID(), raceId, 131, "A1"), true, true),
                AliasEditItemWrapper(Alias(UUID.randomUUID(), raceId, 132, "A2"), true, true)
            ),
            raceId
        )

        assertEquals(listOf(131, 132, 133), adapter.getSortedAliases().map { it.siCode })
    }

    @Test
    fun getSortedAliasesKeepsInvalidRowsAfterValidCodes() {
        val raceId = UUID.randomUUID()
        val adapter = AliasRecyclerViewAdapter(
            arrayListOf(
                AliasEditItemWrapper(Alias(UUID.randomUUID(), raceId, 0, ""), false, false),
                AliasEditItemWrapper(Alias(UUID.randomUUID(), raceId, 31, "F1"), true, true)
            ),
            raceId
        )

        assertEquals(listOf(31, 0), adapter.getSortedAliases().map { it.siCode })
    }
}
