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
