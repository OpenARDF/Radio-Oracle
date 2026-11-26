package org.openardf.radiooracle.backend.sportident

import androidx.room.Room
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.openardf.radiooracle.backend.room.database.EventDatabase
import org.openardf.radiooracle.backend.room.database.MIGRATION_13_14
import org.openardf.radiooracle.backend.room.database.MIGRATION_14_15
import org.openardf.radiooracle.backend.room.database.MIGRATION_15_16
import org.openardf.radiooracle.backend.room.entity.Category
import org.openardf.radiooracle.backend.room.entity.Punch
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.room.entity.Result
import org.openardf.radiooracle.backend.room.enums.SIRecordType
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReadoutSiNumberMigrationTest {
    @Test
    fun upgradeRecoversOnlyUnambiguousControlCardNumbersAndPreservesReadouts() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "si-recovery-${UUID.randomUUID()}"
        val before = Room.databaseBuilder(context, EventDatabase::class.java, name)
            .allowMainThreadQueries().build()
        val expectedResults = mutableListOf<Result>()
        val expectedPunches = mutableListOf<Punch>()
        val race = Race()
        val otherRace = Race()
        val oldCategories = listOf(
            Category("Long").copy(raceId = race.id, isMan = false),
            Category("Short").copy(raceId = race.id, isMan = true)
        )
        try {
            before.raceDao().createRace(race)
            before.raceDao().createRace(otherRace)
            oldCategories.forEach { before.categoryDao().createOrUpdateCategory(it) }
            suspend fun example(
                numbers: List<Int?>,
                existingSi: Int? = null,
                expectedSi: Int? = existingSi,
                controlType: SIRecordType = SIRecordType.CONTROL,
                punchRace: Race = race
            ) {
                val result = Result().copy(
                    raceId = race.id, siNumber = existingSi, sent = true, cardName = "Bromer Ruth"
                )
                before.resultDao().createOrUpdateResult(result)
                expectedResults += result.copy(siNumber = expectedSi, sent = expectedSi == existingSi)
                // Old duplicates have null card numbers on generated start/finish punches.
                val punches = listOf(Punch().copy(
                    raceId = race.id, resultId = result.id, cardNumber = null,
                    punchType = SIRecordType.START
                )) + numbers.mapIndexed { index, number ->
                    Punch().copy(
                        raceId = punchRace.id, resultId = result.id, cardNumber = number,
                        punchType = controlType, order = index + 1, siCode = 31 + index
                    )
                }
                punches.forEach { before.punchDao().createOrUpdatePunch(it) }
                expectedPunches += punches
            }
            example(listOf(123456, 123456), expectedSi = 123456)
            example(listOf(null, 123456), expectedSi = 123456)
            example(listOf(123456, 654321)) // Conflicting cards: no guess.
            example(listOf(null, 0, -1))
            example(emptyList())
            example(listOf(123456), existingSi = 987654) // Preserve existing SI.
            example(listOf(123456), controlType = SIRecordType.FINISH)
            example(listOf(123456), punchRace = otherRace)
            // Recreate the pre-Open category schema before exercising the full Room upgrade.
            before.openHelper.writableDatabase.execSQL("ALTER TABLE category DROP COLUMN is_open")
            before.openHelper.writableDatabase.execSQL("ALTER TABLE competitor DROP COLUMN pending_category")
            before.openHelper.writableDatabase.version = 13
        } finally {
            before.close()
        }
        val after = Room.databaseBuilder(context, EventDatabase::class.java, name)
            .allowMainThreadQueries().addMigrations(MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16).build()
        try {
            expectedResults.forEach { result ->
                assertEquals(result.toString(), after.resultDao().getResult(result.id).toString())
            }
            expectedPunches.forEach { punch ->
                assertEquals(punch.toString(), after.punchDao().getPunch(punch.id).toString())
            }
            assertEquals(16, after.openHelper.writableDatabase.version)
            oldCategories.forEach { assertEquals(it, after.categoryDao().getCategory(it.id)) }
            // Running the repair again cannot change a recovered or unresolved readout.
            MIGRATION_13_14.migrate(after.openHelper.writableDatabase)
            expectedResults.forEach { result ->
                assertEquals(result.toString(), after.resultDao().getResult(result.id).toString())
            }
        } finally {
            after.close()
            context.deleteDatabase(name)
        }
    }
}
