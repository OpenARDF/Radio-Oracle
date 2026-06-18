package org.openardf.radiooracle.backend.room

import androidx.room.Room
import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.openardf.radiooracle.backend.room.database.EventDatabase
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.room.enums.RaceBand
import org.openardf.radiooracle.backend.room.enums.RaceLevel
import org.openardf.radiooracle.backend.room.enums.RaceType
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class ImportedRaceReplacementTests {
    private val database: EventDatabase = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication(),
        EventDatabase::class.java
    )
        .allowMainThreadQueries()
        .build()

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun savingImportedRaceReplacesPriorCopyForSameSourceOnly() = runBlocking {
        val sourceId = "event-file:desktop-race"
        val priorImport = race("Prior import", sourceId)
        val replacement = race("Replacement import", sourceId)
        val unrelatedImport = race("Other import", "event-file:other-race")
        val localRace = race("Local race", null)

        database.raceDao().createRace(priorImport)
        database.raceDao().createRace(unrelatedImport)
        database.raceDao().createRace(localRace)

        database.withTransaction {
            database.raceDao().deletePriorImportedCopies(sourceId, replacement.id)
            database.raceDao().createRace(replacement)
        }

        val races = database.raceDao().getRaces().first().sortedBy { it.name }

        assertEquals(
            listOf("Local race", "Other import", "Replacement import"),
            races.map { it.name }
        )
        assertEquals(sourceId, races.single { it.name == "Replacement import" }.importSourceId)
    }

    private fun race(name: String, importSourceId: String?): Race =
        Race(
            id = UUID.randomUUID(),
            name = name,
            apiKey = "",
            startDateTime = LocalDateTime.of(2026, 6, 18, 9, 0),
            raceType = RaceType.CLASSIC,
            raceLevel = RaceLevel.PRACTICE,
            raceBand = RaceBand.M80,
            timeLimit = Duration.ofHours(2),
            importSourceId = importSourceId,
            importFingerprint = importSourceId?.let { "fingerprint-$name" }
        )
}
