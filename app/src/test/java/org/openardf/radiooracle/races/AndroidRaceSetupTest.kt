package org.openardf.radiooracle.races

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.room.ARDFRepository
import org.openardf.radiooracle.backend.room.entity.*
import org.openardf.radiooracle.backend.room.entity.embeddeds.*
import org.openardf.radiooracle.backend.shared.toEventRaceData
import org.openardf.radiooracle.backend.sportident.SITime
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.PunchStatus
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.shared.event.*
import org.openardf.radiooracle.shared.publicresults.ProtectedCourseCipher
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.Duration
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class AndroidRaceSetupTest {
    private val repository get() = ARDFRepository.get()
    private val setup get() = repository.raceSetup
    private val processor get() = DataProcessor.get()
    private val race = Race().copy(name = "Practice")
    private val long = Category("Long").copy(raceId = race.id, order = 1, isOpen = true)
    private val short = Category("Short").copy(raceId = race.id, order = 2)

    @Before fun initialize() {
        val context = RuntimeEnvironment.getApplication()
        DataProcessor.resetForTests()
        ARDFRepository.resetForTests()
        context.deleteDatabase("event-database")
        ARDFRepository.initialize(context)
        DataProcessor.initialize(context)
    }

    @After fun closeDatabase() = ARDFRepository.resetForTests()

    private fun course(category: Category) = CategoryData(category, listOf(
        ControlPoint(31).copy(categoryId = category.id, order = 1),
        ControlPoint(99).copy(categoryId = category.id, order = 2, type = ControlPointType.BEACON)
    ), emptyList())

    private suspend fun courses() = processor.saveRaceData(RaceData(race, listOf(course(long), course(short)),
        listOf(Alias(31, "Fox 1").copy(raceId = race.id)), emptyList(), emptyList()))

    @Test fun replacingAStationKeepsCourseIdentityAndSurvivesRaceSettingsAndFileTransfer() = runBlocking {
        courses()
        val base = setup.stationCatalog(race.id)
        val fox = base.raceData.controls.single { it.siCode == 31 }
        assertTrue(runCatching { setup.reviewStationCode(base, fox.id, 99, null) }.isFailure)
        assertTrue(runCatching { setup.reviewStationCode(base, fox.id, -1, null) }.isFailure)
        val pending = setup.reviewStationCode(base, fox.id, 49, null)
        assertEquals(31, setup.stationCatalog(race.id).raceData.controls.single { it.id == fox.id }.siCode)
        setup.applyStationCode(pending)
        assertTrue(runCatching { setup.applyStationCode(pending) }.isFailure)

        // A settings form opened before the station review cannot restore the old portable catalog.
        processor.updateRace(race.copy(name = "Renamed practice"))
        val stored = processor.getRaceData(race.id)
        assertEquals(listOf(49), stored.aliases.filter { it.name == "Fox 1" }.map { it.siCode })
        val exported = EventProjectFileJson.decode(EventProjectFileJson.encode(EventProjectFile(raceData = stored.toEventRaceData())))
        assertEquals(49, exported.raceData.controls.single { it.id == fox.id }.siCode)
        assertTrue(stored.categories.all { it.controlPoints.single { p -> p.portableControlId == fox.id }.siCode == 49 })
        assertTrue(exported.raceData.categories.all { fox.id in it.publicControlIds })
        assertTrue(runCatching { setup.reviewStationCode(EventCourseDrafts.start(exported), fox.id, 50, null) }.isFailure)
    }

    @Test fun aReadoutArrivingDuringReviewLocksBothMatchedAndUnmatchedCases() = runBlocking {
        courses()
        val project = setup.stationCatalog(race.id)
        val change = setup.reviewStationCode(project, project.raceData.controls.first().id, 41, null)
        val runner = Competitor().copy(raceId = race.id, categoryId = long.id)
        repository.createCompetitor(runner)
        for (owner in listOf(null, runner.id)) {
            val result = Result().copy(raceId = race.id, competitorId = owner)
            repository.createOrUpdateResult(result)
            val failure = runCatching { setup.applyStationCode(change) }.exceptionOrNull()
            assertEquals(EventCourseDrafts.RecordedActivityDesignRestriction, failure?.message)
            assertTrue(runCatching { setup.stationCatalog(race.id) }.isFailure)
            repository.deleteResult(result.id)
        }
    }

    @Test fun protectedReplacementReencryptsBeforeWriting() = runBlocking {
        courses()
        repository.createCategory(long.copy(idealOrder = "1", courseInfo = ProtectedCourseCipher.encodeCourseInfo(
            ProtectedCourseInfo(sourceName = "Protected practice")
        )))
        repository.courseProtection.update(race.id, false, "race-password", true)
        val project = setup.stationCatalog(race.id)
        val fox = project.raceData.controls.single { it.siCode == 31 }
        assertTrue(runCatching { setup.reviewStationCode(project, fox.id, 49, "wrong") }.isFailure)
        setup.applyStationCode(setup.reviewStationCode(project, fox.id, 49, "race-password"))
        val stored = repository.getCategory(long.id)!!
        assertNull(stored.courseInfo)
        assertEquals("Protected practice", ProtectedCourseCipher.decryptCourseInfo(stored.encryptedCourseInfo!!, "race-password").sourceName)
    }

    @Test fun categoryReviewPreservesMissingReferencesAcrossImportAndAppliesOnlySelectedChanges() = runBlocking {
        val absent = Category("Medium").copy(raceId = race.id)
        val equivalent = long.copy(id = java.util.UUID.randomUUID())
        fun registration(category: Category) = CompetitorData(CompetitorCategory(
            Competitor().copy(raceId = race.id, categoryId = category.id, siNumber = null, isMan = false), category), null)
        val data = RaceData(race, listOf(course(long), course(short)), emptyList(),
            listOf(registration(absent), registration(equivalent)), emptyList())
        val input = EventProjectFileJson.encode(EventProjectFile(raceData = data.toEventRaceData()))
        val imported = requireNotNull(processor.importRaceData(input))
        processor.saveRaceData(imported)
        val id = imported.race.id
        val review = setup.categoryReviews(id).first()
        assertEquals(setOf("Long", "Medium"), review.missingCategories.map { it.categoryName }.toSet())
        val creation = review.missingCategories.single { it.categoryName == "Medium" }
        val repair = review.missingCategories.single { it.categoryName == "Long" }
        assertNotNull(repair.existingEquivalentCategoryId)
        assertTrue(review.emptyCategories.single().hasCourseData)
        val outcome = processor.applyCategoryReview(id, review, setOf(repair.categoryId), emptySet())
        assertEquals(1, outcome.repairedAssignmentCount)
        assertEquals(0, outcome.removedCategoryCount)
        val after = processor.getRaceData(id).toEventRaceData()
        val reopened = EventProjectFileJson.decode(EventProjectFileJson.encode(EventProjectFile(raceData = after)))
        assertEquals(listOf("Medium"), EventCategoryCompetitorSync.plan(reopened.raceData).missingCategories.map { it.categoryName })
        val next = setup.categoryReviews(id).first()
        processor.applyCategoryReview(id, next, setOf(creation.categoryId), setOf(next.emptyCategories.single().categoryId))
        val final = processor.getRaceData(id).toEventRaceData()
        assertEquals(setOf("Long", "Medium"), final.categories.map { it.category.name }.toSet())
        assertTrue(final.competitorData.all { !it.competitorCategory.competitor.isMan })
        assertFalse(EventCategoryCompetitorSync.plan(final).hasMismatch)
    }

    @Test fun categoryRepairRescoresEverySavedAttemptAndQueuesOnlyTheAffectedResults() = runBlocking {
        val equivalent = long.copy(id = UUID.randomUUID())
        val runner = Competitor().copy(raceId = race.id, categoryId = equivalent.id, siNumber = 1001)
        val waiting = runner.copy(id = UUID.randomUUID(), siNumber = 1002)
        val other = runner.copy(id = UUID.randomUUID(), categoryId = short.id, siNumber = 1003)
        val registrations = listOf(runner to equivalent, waiting to equivalent, other to short).map { (person, category) ->
            CompetitorData(CompetitorCategory(person, category), null)
        }
        processor.saveRaceData(RaceData(race.copy(timeLimit = Duration.ofHours(2)), listOf(course(long), course(short)),
            emptyList(), registrations, emptyList()))

        val automatic = Result().copy(raceId = race.id, competitorId = runner.id, siNumber = runner.siNumber,
            startTime = SITime(30000), finishTime = SITime(30600), resultStatus = ResultStatus.NO_RANKING, sent = true)
        val manual = automatic.copy(id = UUID.randomUUID(), automaticStatus = false, resultStatus = ResultStatus.DISQUALIFIED)
        val noControls = automatic.copy(id = UUID.randomUUID())
        val unrelated = automatic.copy(id = UUID.randomUUID(), competitorId = other.id, siNumber = other.siNumber)
        val unmatched = automatic.copy(id = UUID.randomUUID(), competitorId = null, siNumber = waiting.siNumber)
        for (result in listOf(automatic, manual)) {
            val punches = listOf(31, 99).mapIndexed { index, code ->
                Punch(code, SITime(30100L + index * 60), SIRecordType.CONTROL, index + 1)
                    .copy(raceId = race.id, resultId = result.id, cardNumber = result.siNumber)
            }
            repository.saveResultPunches(result, punches)
        }
        listOf(noControls, unrelated, unmatched).forEach { repository.createOrUpdateResult(it) }

        val review = setup.categoryReviews(race.id).first()
        processor.applyCategoryReview(race.id, review, setOf(review.missingCategories.single().categoryId), emptySet())

        for (original in listOf(automatic, manual)) {
            val saved = repository.getResult(original.id)
            assertFalse(saved.sent)
            assertEquals(1, saved.points)
            assertEquals(Duration.ofMinutes(10), saved.runTime)
            assertEquals(original.automaticStatus, saved.automaticStatus)
            assertEquals(if (original.automaticStatus) ResultStatus.OK else ResultStatus.DISQUALIFIED, saved.resultStatus)
            val controls = repository.getPunchesByResult(saved.id).filter { it.punchType == SIRecordType.CONTROL }
            assertEquals(listOf(31, 99), controls.map { it.siCode })
            assertTrue(controls.all { it.punchStatus == PunchStatus.VALID })
        }
        assertEquals(ResultStatus.DID_NOT_FINISH, repository.getResult(noControls.id).resultStatus)
        assertFalse(repository.getResult(noControls.id).sent)
        assertTrue(repository.getResult(unrelated.id).sent)
        assertEquals(ResultStatus.NO_RANKING, repository.getResult(unrelated.id).resultStatus)
        assertTrue(repository.getResult(unmatched.id).sent)
        assertNull(repository.getResult(unmatched.id).competitorId)
    }

    @Test fun repairingALegacyCrossRaceReferenceKeepsBothRacesIndependent() = runBlocking {
        courses()
        val other = Race().copy(name = "Separate race")
        processor.createRace(other)
        val foreign = Category("Medium").copy(raceId = other.id)
        repository.createCategory(foreign)
        val runner = Competitor().copy(raceId = race.id, categoryId = foreign.id)
        repository.createCompetitor(runner)
        val otherRunner = Competitor().copy(raceId = other.id, categoryId = long.id)
        repository.createCompetitor(otherRunner)
        val review = setup.categoryReviews(race.id).first()
        val selected = setOf(review.missingCategories.single().categoryId)
        // This category only looks empty within the current race. Its foreign owner must survive the whole transaction.
        assertTrue(runCatching { processor.applyCategoryReview(race.id, review, selected, setOf(long.id.toString())) }.isFailure)
        assertEquals(otherRunner, repository.getCompetitor(otherRunner.id))
        assertEquals(foreign.id, repository.getCompetitor(runner.id)?.categoryId)
        processor.applyCategoryReview(race.id, review, selected, emptySet())
        val repaired = requireNotNull(repository.getCompetitor(runner.id)?.categoryId)
        assertNotEquals(foreign.id, repaired)
        assertEquals(race.id, repository.getCategory(repaired)?.raceId)
        assertEquals(foreign, repository.getCategory(foreign.id))
    }

    @Test fun staleEmptyCategorySelectionsCannotDeleteNewRegistrationsOrPreparedCourses() = runBlocking {
        courses()
        val idle = setup.categoryReviews(race.id).first()
        assertFalse(idle.hasRegisteredCompetitors)
        assertFalse(idle.hasMismatch)
        val runner = Competitor().copy(raceId = race.id, categoryId = long.id)
        repository.createCompetitor(runner)
        val review = setup.categoryReviews(race.id).first()
        repository.createCompetitor(runner.copy(categoryId = short.id))
        assertTrue(runCatching { processor.applyCategoryReview(race.id, review, emptySet(), setOf(short.id.toString())) }.isFailure)
        repository.deleteCompetitor(runner.id)
        assertTrue(runCatching { processor.applyCategoryReview(race.id, review, emptySet(), setOf(short.id.toString())) }.isFailure)
        assertNotNull(repository.getCategory(short.id))
    }
}
