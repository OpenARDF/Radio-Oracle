package org.openardf.radiooracle.backend.commands

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class AppCommandEventIdsTest {
    @Test
    fun parsesCommaSeparatedEventIds() {
        val first = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val second = UUID.fromString("22222222-2222-2222-2222-222222222222")

        assertEquals(
            listOf(first, second),
            AppCommandEventIds.parse("$first, $second")
        )
    }

    @Test
    fun rejectsMissingOrInvalidEventIds() {
        assertNull(AppCommandEventIds.parse(null))
        assertNull(AppCommandEventIds.parse("   "))
        assertNull(AppCommandEventIds.parse("11111111-1111-1111-1111-111111111111, invalid"))
    }
}
