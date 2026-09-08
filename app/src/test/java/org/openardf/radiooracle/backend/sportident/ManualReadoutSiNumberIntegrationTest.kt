package org.openardf.radiooracle.backend.sportident

import java.time.Duration
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.results.ResultsProcessor
import org.openardf.radiooracle.backend.room.ARDFRepository
import org.openardf.radiooracle.backend.room.entity.Competitor
import org.openardf.radiooracle.backend.room.entity.Punch
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.room.entity.Result
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ManualReadoutSiNumberIntegrationTest {
    private val race = Race().copy(timeLimit = Duration.ofHours(3))
    private val competitor = Competitor().copy(
        raceId = race.id, firstName = "Ruth", lastName = "Bromer", siNumber = 8101649
    )
    private val manualResult = Result().copy(
        raceId = race.id, competitorId = competitor.id, siNumber = null, cardType = 0,
        checkTime = null, startTime = SITime(36000), finishTime = SITime(42245), modified = true
    )

    @Before
    fun initializeBackend() {
        val context = RuntimeEnvironment.getApplication()
        DataProcessor.resetForTests()
        ARDFRepository.resetForTests()
        ARDFRepository.initialize(context)
        DataProcessor.initialize(context)
    }

    @Test
    fun savingManualEntryStoresRegisteredSiOnResultAndPunchesWithoutChangingTimes() = runBlocking {
        val repository = ARDFRepository.get()
        repository.createRace(race)
        repository.createCompetitor(competitor)
        val control = Punch().copy(
            raceId = race.id, resultId = manualResult.id, cardNumber = null, siCode = 132,
            siTime = SITime(36821), origSiTime = SITime(36600), order = 1
        )
        ResultsProcessor.processManualPunchData(
            manualResult, arrayListOf(control), null, race, DataProcessor.get(), modified = false
        )
        val stored = DataProcessor.get().getResultData(manualResult.id)
        assertEquals(8101649, stored.result.siNumber)
        assertEquals(competitor.id, stored.result.competitorId)
        assertEquals(true, stored.result.modified)
        assertEquals(36000L, stored.result.startTime?.getSeconds())
        assertEquals(42245L, stored.result.finishTime?.getSeconds())
        assertEquals(6245L, stored.result.runTime.seconds)
        assertEquals(listOf(8101649, 8101649, 8101649), stored.getPunchList().map { it.cardNumber })
        val storedControl = stored.getPunchList().single { it.siCode == 132 }
        assertEquals(36821L, storedControl.siTime.getSeconds())
        assertEquals(36600L, storedControl.origSiTime.getSeconds())
    }

    @Test
    fun previewFollowsSelectedCompetitorWithoutMutatingTheDraft() {
        assertEquals(8101649, ResultsProcessor.manualReadoutSiNumber(manualResult, competitor, emptyList()))
        assertEquals(2005005, ResultsProcessor.manualReadoutSiNumber(
            manualResult, competitor.copy(siNumber = 2005005), emptyList()
        ))
        assertNull(manualResult.siNumber)
    }

    @Test
    fun existingRecordedSiIsPreservedWhenCompetitorRegistrationDiffers() {
        assertEquals(123456, ResultsProcessor.manualReadoutSiNumber(
            manualResult.copy(siNumber = 123456), competitor, emptyList()
        ))
    }

    @Test
    fun downloadedReadoutDoesNotAcquireAnAssumedHistoricalCardNumber() {
        assertNull(ResultsProcessor.manualReadoutSiNumber(
            manualResult.copy(cardType = SIConstants.SI_CARD6), competitor, emptyList()
        ))
    }

    @Test
    fun missingRegistrationConflictingPunchesAndWrongCompetitorAreNotGuessed() {
        assertNull(ResultsProcessor.manualReadoutSiNumber(manualResult, null, emptyList()))
        assertNull(ResultsProcessor.manualReadoutSiNumber(manualResult, competitor.copy(siNumber = null), emptyList()))
        assertNull(ResultsProcessor.manualReadoutSiNumber(manualResult, Competitor(), emptyList()))
        assertNull(ResultsProcessor.manualReadoutSiNumber(
            manualResult, competitor, listOf(Punch().copy(cardNumber = 123456))
        ))
    }
}
