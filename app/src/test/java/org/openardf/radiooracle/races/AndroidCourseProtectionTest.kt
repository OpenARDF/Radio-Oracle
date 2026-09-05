package org.openardf.radiooracle.races

import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openardf.radiooracle.backend.room.AndroidCourseProtection
import org.openardf.radiooracle.backend.room.database.EventDatabase
import org.openardf.radiooracle.backend.room.entity.Category
import org.openardf.radiooracle.backend.room.entity.EventSeries
import org.openardf.radiooracle.backend.room.entity.EventSeriesMember
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.publicresults.ProtectedCourseCipher
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AndroidCourseProtectionTest {
    private lateinit var database: EventDatabase
    private lateinit var protection: AndroidCourseProtection

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), EventDatabase::class.java).build()
        protection = AndroidCourseProtection(database)
    }

    @After fun close() = database.close()

    @Test fun roundTripUsesDesktopCipherAndPreservesCategoryMetadata() = runBlocking {
        val (race, category) = fixture("Practice")
        assertFalse(protection.state(race.id, false).encrypted)
        protection.update(race.id, false, "test-password", encrypt = true)
        val encrypted = database.categoryDao().getCategory(category.id)!!
        assertNull(encrypted.idealOrder)
        assertNull(encrypted.courseInfo)
        assertEquals(category.idealOrder, ProtectedCourseCipher.decrypt(encrypted.encryptedIdealOrder!!, "test-password"))
        assertEquals("test course", ProtectedCourseCipher.decryptCourseInfo(encrypted.encryptedCourseInfo!!, "test-password").sourceName)
        assertTrue(protection.state(race.id, false).encrypted)
        protection.update(race.id, false, "test-password", encrypt = false)
        assertEquals(category, database.categoryDao().getCategory(category.id))
        assertEquals(race, database.raceDao().getRace(race.id))
        assertFalse(protection.state(race.id, false).encrypted)
    }

    @Test fun wrongPasswordInLaterSeriesMemberLeavesEveryRowUnchanged() = runBlocking {
        val (first, firstCategory) = fixture("Day 1")
        val (second, secondCategory) = fixture("Day 2")
        series(first, second)
        protection.update(first.id, false, "first-password", true)
        protection.update(second.id, false, "second-password", true)
        val before = listOf(firstCategory, secondCategory).map { database.categoryDao().getCategory(it.id) }
        val error = runCatching { protection.update(first.id, true, "first-password", false) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message!!.contains("Day 2"))
        assertEquals(before, listOf(firstCategory, secondCategory).map { database.categoryDao().getCategory(it.id) })
    }

    @Test fun seriesRoundTripAndMixedPlaintextMembersAreSupported() = runBlocking {
        val (first, firstCategory) = fixture("Day 1")
        val (second, secondCategory) = fixture("Day 2")
        series(first, second)
        protection.update(first.id, true, "series-password", true)
        assertTrue(protection.state(second.id, false).encrypted)
        protection.update(first.id, false, "series-password", false)
        assertTrue(protection.state(first.id, true).encrypted)
        protection.update(first.id, true, "series-password", false)
        assertEquals(firstCategory, database.categoryDao().getCategory(firstCategory.id))
        assertEquals(secondCategory, database.categoryDao().getCategory(secondCategory.id))
        assertFalse(protection.state(first.id, true).encrypted)
    }

    @Test fun emptyRaceHasNoPasswordAndCannotBeMarkedEncrypted() = runBlocking {
        val race = Race()
        database.raceDao().createRace(race)
        val state = protection.state(race.id, false)
        assertFalse(state.encrypted)
        assertFalse(state.hasCourseData)
        assertTrue(runCatching { protection.update(race.id, false, "test-password", true) }.isFailure)
    }

    private suspend fun fixture(name: String): Pair<Race, Category> {
        val race = Race().apply { this.name = name }
        database.raceDao().createRace(race)
        val category = Category("M21").apply {
            raceId = race.id
            idealOrder = "1,2,3"
            courseInfo = ProtectedCourseCipher.encodeCourseInfo(ProtectedCourseInfo(sourceName = "test course"))
            length = 4200
            climb = 85
            differentProperties = true
            controlPointsString = "31 32 33"
        }
        database.categoryDao().createOrUpdateCategory(category)
        return race to category
    }

    private suspend fun series(vararg races: Race) {
        database.eventSeriesDao().upsertSeries(EventSeries("test-series", "Test Series"))
        races.forEachIndexed { index, race ->
            database.eventSeriesDao().upsertMember(EventSeriesMember(
                "test-series", "day-$index", race.id, "$index.json", index, race.name,
                race.startDateTime.toString(), "Classic"
            ))
        }
    }
}
