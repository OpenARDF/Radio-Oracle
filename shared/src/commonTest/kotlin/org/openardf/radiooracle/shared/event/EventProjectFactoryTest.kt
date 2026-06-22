package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EventProjectFactoryTest {
    @Test
    fun createsEmptyProjectWithAndroidCompatibleRaceDefaults() {
        val projectFile = EventProjectFactory.createEmptyProject(
            raceId = "race-1",
            raceName = " New Event ",
            startDateTimeIso = "2026-05-31T14:30:00"
        )

        val race = projectFile.raceData.race
        assertEquals("race-1", race.id)
        assertEquals("New Event", race.name)
        assertEquals("", race.apiKey)
        assertEquals("2026-05-31T14:30:00", race.startDateTimeIso)
        assertEquals(RaceType.CLASSIC, race.raceType)
        assertEquals(RaceLevel.PRACTICE, race.raceLevel)
        assertEquals(RaceBand.M80, race.raceBand)
        assertEquals(7_200, race.timeLimitSeconds)
        assertEquals(emptyList(), projectFile.raceData.categories)
        assertEquals(emptyList(), projectFile.raceData.aliases)
        assertEquals(emptyList(), projectFile.raceData.competitorData)
        assertEquals(emptyList(), projectFile.raceData.unmatchedReadoutData)
        assertEquals(emptyList(), projectFile.raceData.controls)
    }

    @Test
    fun rejectsInvalidNewProjectInputs() {
        assertFailsWith<IllegalArgumentException> {
            EventProjectFactory.createEmptyProject("", "Event", "2026-05-31T14:30:00")
        }
        assertFailsWith<IllegalArgumentException> {
            EventProjectFactory.createEmptyProject("race-1", " ", "2026-05-31T14:30:00")
        }
        assertFailsWith<IllegalArgumentException> {
            EventProjectFactory.createEmptyProject("race-1", "Event", " ")
        }
    }
}
