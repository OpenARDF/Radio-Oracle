package org.openardf.radiooracle.backend.sportident

import androidx.preference.PreferenceManager
import java.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.results.ResultsProcessor
import org.openardf.radiooracle.backend.room.ARDFRepository
import org.openardf.radiooracle.backend.room.entity.Competitor
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.room.enums.RaceLevel
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DuplicateReadoutSiNumberIntegrationTest {
    @Before
    fun initializeBackend() {
        val context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(context.getString(R.string.key_readout_error_sounds), false)
            .putString(context.getString(R.string.key_readout_duplicate),
                context.getString(R.string.preferences_readout_duplicate_new_value))
            .commit()
        DataProcessor.resetForTests()
        ARDFRepository.resetForTests()
        ARDFRepository.initialize(context)
        DataProcessor.initialize(context)
    }

    @Test
    fun duplicateNamedCardRetainsItsSiNumberWithoutReplacingTheOriginal() = runBlocking {
        verifyDuplicateReadouts(registered = false)
    }

    @Test
    fun duplicateRegisteredCardRetainsItsSiNumberWithoutTakingTheOriginalCompetitor() = runBlocking {
        verifyDuplicateReadouts(registered = true)
    }

    private suspend fun verifyDuplicateReadouts(registered: Boolean) {
        val processor = DataProcessor.get()
        val repository = ARDFRepository.get()
        val context = RuntimeEnvironment.getApplication()
        val card = SIPort.CardData(
            cardType = SIConstants.SI_CARD6,
            siNumber = 123456,
            cardName = "Bromer Ruth",
            startTime = SITime(9 * 3600),
            finishTime = SITime(10 * 3600),
            punchData = arrayListOf(SIPort.PunchData(31, SITime(9 * 3600 + 600)))
        )
        RaceLevel.entries.filter { it != RaceLevel.PRACTICE }.forEach { level ->
            val race = Race().copy(raceLevel = level, timeLimit = Duration.ofHours(2))
            repository.createRace(race)
            val competitor = if (registered) Competitor().copy(
                raceId = race.id, firstName = "Ruth", lastName = "Bromer", siNumber = card.siNumber
            ).also { repository.createCompetitor(it) } else null
            assertTrue(ResultsProcessor.processCardData(card, race, context, processor))
            val original = processor.getResultDataFlowByRace(race.id).first().single().result
            repeat(2) {
                assertTrue(ResultsProcessor.processCardData(card, race, context, processor))
            }
            val stored = processor.getResultDataFlowByRace(race.id).first()
            assertEquals(3, stored.size)
            assertEquals(original.toString(), stored.single { it.result.id == original.id }.result.toString())
            assertEquals(competitor?.id, original.competitorId)
            stored.forEach { readout ->
                assertEquals(card.siNumber, readout.result.siNumber)
                assertEquals(card.cardName, readout.result.cardName)
                assertTrue(readout.punches.isNotEmpty())
                assertTrue(readout.punches.all { it.punch.cardNumber == card.siNumber })
                if (readout.result.id != original.id) assertNull(readout.result.competitorId)
            }
        }
    }
}
