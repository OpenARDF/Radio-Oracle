package org.openardf.radiooracle.races

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.openardf.radiooracle.backend.room.AndroidCourseProtection
import org.openardf.radiooracle.backend.room.database.EventDatabase
import org.openardf.radiooracle.backend.room.entity.Category
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.publicresults.ProtectedCourseCipher

/** Exercises real Android crypto and Room without opening or changing the user's database. */
@RunWith(AndroidJUnit4::class)
class AndroidCourseProtectionInstrumentedTest {
    @Test fun encryptAuthenticateAndRemoveEncryptionOnDevice() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, EventDatabase::class.java).build()
        try {
            val protection = AndroidCourseProtection(database)
            val race = Race().apply { name = "Isolated protection test" }
            database.raceDao().createRace(race)
            assertFalse(protection.state(race.id, false).hasCourseData)
            val category = Category("M21").apply {
                raceId = race.id
                idealOrder = "1,2,3"
                courseInfo = ProtectedCourseCipher.encodeCourseInfo(ProtectedCourseInfo(sourceName = "Test course"))
                length = 4000
            }
            database.categoryDao().createOrUpdateCategory(category)
            assertFalse(protection.state(race.id, false).encrypted)
            protection.update(race.id, false, "test-password", true)
            val encrypted = database.categoryDao().getCategory(category.id)!!
            assertNull(encrypted.courseInfo)
            assertNull(encrypted.idealOrder)
            assertEquals("Test course", ProtectedCourseCipher.decryptCourseInfo(encrypted.encryptedCourseInfo!!, "test-password").sourceName)
            assertTrue(runCatching { protection.update(race.id, false, "wrong-password", false) }.isFailure)
            assertEquals(encrypted, database.categoryDao().getCategory(category.id))
            protection.update(race.id, false, "test-password", false)
            assertEquals(category, database.categoryDao().getCategory(category.id))
            assertFalse(protection.state(race.id, false).encrypted)
        } finally {
            database.close()
        }
    }
}
