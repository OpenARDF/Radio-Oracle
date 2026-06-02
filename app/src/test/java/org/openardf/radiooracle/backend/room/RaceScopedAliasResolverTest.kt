package org.openardf.radiooracle.backend.room

import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.openardf.radiooracle.backend.room.entity.Alias
import org.openardf.radiooracle.backend.room.entity.ControlPoint
import org.openardf.radiooracle.backend.room.entity.Punch
import org.openardf.radiooracle.backend.room.entity.Result
import org.openardf.radiooracle.backend.room.entity.embeddeds.AliasPunch
import org.openardf.radiooracle.backend.room.entity.embeddeds.ControlPointAlias
import org.openardf.radiooracle.backend.room.entity.embeddeds.ResultData
import java.util.UUID

class RaceScopedAliasResolverTest {
    @Test
    fun controlPointAliasesUseAliasesFromTheRequestedRace() {
        val raceId = UUID.randomUUID()
        val otherRaceId = UUID.randomUUID()
        val controlPoint = ControlPoint(31)
        val wrongAlias = Alias(UUID.randomUUID(), otherRaceId, 31, "Other race")
        val raceAlias = Alias(UUID.randomUUID(), raceId, 31, "Fox 1")

        val resolved = RaceScopedAliasResolver.resolveControlPoints(
            listOf(ControlPointAlias(controlPoint, wrongAlias)),
            listOf(raceAlias)
        )

        assertEquals("Fox 1", resolved.single().alias?.name)
        assertEquals(raceId, resolved.single().alias?.raceId)
    }

    @Test
    fun resultPunchAliasesUseAliasesFromTheResultRace() {
        val raceId = UUID.randomUUID()
        val otherRaceId = UUID.randomUUID()
        val result = Result().apply { this.raceId = raceId }
        val punch = Punch().apply {
            this.raceId = raceId
            this.resultId = result.id
            this.siCode = 31
        }
        val wrongAlias = Alias(UUID.randomUUID(), otherRaceId, 31, "Other race")
        val raceAlias = Alias(UUID.randomUUID(), raceId, 31, "Fox 1")

        val resolved = RaceScopedAliasResolver.resolveResultData(
            ResultData(result, listOf(AliasPunch(punch, wrongAlias)), null),
            listOf(raceAlias)
        )

        assertEquals("Fox 1", resolved.punches.single().alias?.name)
        assertEquals(raceId, resolved.punches.single().alias?.raceId)
    }
}
