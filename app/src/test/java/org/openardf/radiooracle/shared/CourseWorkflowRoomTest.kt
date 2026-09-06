package org.openardf.radiooracle.shared

import androidx.room.Room
import androidx.room.withTransaction
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.openardf.radiooracle.backend.room.AndroidCourseProtection
import org.openardf.radiooracle.backend.room.database.EventDatabase
import org.openardf.radiooracle.backend.room.database.MIGRATION_12_13
import org.openardf.radiooracle.backend.room.entity.embeddeds.RaceData
import org.openardf.radiooracle.backend.shared.toEventRaceData
import org.openardf.radiooracle.backend.shared.toRoomRaceData
import org.openardf.radiooracle.shared.event.*
import org.openardf.radiooracle.shared.publicresults.ProtectedCourseCipher

@RunWith(RobolectricTestRunner::class)
class CourseWorkflowRoomTest {
    @Test fun databaseAndProtectionPreserveCatalogInactiveMappingsAndDrafts() = runBlocking {
        val original = courseTransferFixture()
        val inactive = original.raceData.categories.single().let { data -> data.copy(
            category = data.category.copy(id = "w55", name = "W55"),
            controlPoints = data.controlPoints.map { it.copy(categoryId = "w55") }) }
        val draft = EventCourseDrafts.edit(original.copy(raceData = original.raceData.copy(courseMappings = listOf(inactive)))) { p ->
            EventProjectEditor.updateCategoryCourseInfo(p, "m21", p.raceData.categories.single().category.courseInfo!!.copy(lengthMeters = 123))
        }
        val native = draft.raceData.toRoomRaceData()
        val database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), EventDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            database.withTransaction {
                database.raceDao().createRace(native.race)
                native.categories.forEach { data ->
                    database.categoryDao().createOrUpdateCategory(data.category)
                    data.controlPoints.forEach { database.controlPointDao().createControlPoint(it) }
                }
                native.aliases.forEach { database.aliasDao().createOrUpdateAlias(it) }
            }
            suspend fun read() = RaceData(database.raceDao().getRace(native.race.id)!!,
                database.categoryDao().getCategoryDataForRace(native.race.id), database.aliasDao().getAliasesByRace(native.race.id),
                emptyList(), emptyList()).toEventRaceData()
            val reopened = read()
            assertEquals(original.raceData.controls.single().id, reopened.controls.single().id)
            EventCourseDrafts.requireCurrent(EventProjectFile(raceData = reopened))
            val protection = AndroidCourseProtection(database)
            protection.update(native.race.id, false, "fixture-password", true)
            val encrypted = EventProjectFile(raceData = read())
            assertTrue(EventCourseDrafts.protectedCategories(encrypted.raceData).all { it.category.courseInfo == null && it.category.encryptedCourseInfo != null })
            EventCourseDrafts.requireCurrent(encrypted)
            val cipherBefore = encrypted.raceData.courseMappings.single().category.encryptedCourseInfo
            try { protection.update(native.race.id, false, "wrong", false); fail("Wrong password must fail") }
            catch (_: IllegalArgumentException) { }
            assertEquals(cipherBefore, read().courseMappings.single().category.encryptedCourseInfo)
            protection.update(native.race.id, false, "fixture-password", false)
            val restored = EventProjectFile(raceData = read())
            EventCourseDrafts.requireCurrent(restored)
            assertEquals(123, EventCourseDrafts.candidate(restored).raceData.categories.single().category.courseInfo!!.lengthMeters)
            assertEquals(original.raceData.categories.single().category.courseInfo, restored.raceData.categories.single().category.courseInfo)
        } finally { database.close() }
    }

    @Test fun migrationAddsNullableFieldsWithoutChangingOldRows() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
            .name(null).callback(object : SupportSQLiteOpenHelper.Callback(12) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    listOf("race", "category", "control_point").forEach { table ->
                        db.execSQL("CREATE TABLE $table (id TEXT NOT NULL PRIMARY KEY, marker TEXT)")
                        db.execSQL("INSERT INTO $table VALUES ('original', 'retained')")
                    }
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        try {
            val db = helper.writableDatabase
            MIGRATION_12_13.migrate(db)
            for ((table, column) in listOf("race" to "portable_course_data", "category" to "portable_category_id", "control_point" to "portable_control_id")) {
                db.query("SELECT marker, $column FROM $table WHERE id = 'original'").use { row ->
                    assertTrue(row.moveToFirst()); assertEquals("retained", row.getString(0)); assertTrue(row.isNull(1))
                }
            }
        } finally { helper.close() }
    }
}
