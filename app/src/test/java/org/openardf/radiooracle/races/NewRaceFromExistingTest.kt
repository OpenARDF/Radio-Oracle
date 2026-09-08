package org.openardf.radiooracle.races

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.room.ARDFRepository
import org.openardf.radiooracle.backend.room.entity.*
import org.openardf.radiooracle.backend.room.entity.embeddeds.*
import org.openardf.radiooracle.backend.shared.toEventRaceData
import org.openardf.radiooracle.backend.shared.toRoomRaceData
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.*
import org.openardf.radiooracle.shared.publicresults.ProtectedCourseCipher
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class NewRaceFromExistingTest {
    private lateinit var processor: DataProcessor

    @Before fun setup() {
        DataProcessor.resetForTests()
        ARDFRepository.resetForTests()
        ARDFRepository.initialize(RuntimeEnvironment.getApplication())
        DataProcessor.initialize(RuntimeEnvironment.getApplication())
        processor = DataProcessor.get()
    }

    @Test fun copiesSetupAndOptionalRegistrationsWithoutReplacingSourceOrKeepingResults() = runBlocking {
        for (encrypted in listOf(false, true)) {
            val source = sourceRace(encrypted)
            val before = processor.getRaceData(source.id).toEventRaceData()
            for (includeCompetitors in listOf(false, true)) {
                val settings = source.copy(id = UUID.randomUUID(), name = "New race $includeCompetitors", apiKey = "",
                    startDateTime = LocalDateTime.parse("2026-10-01T09:30:00"))
                val created = processor.createRaceFromExisting(source.id, settings, includeCompetitors)
                val copied = processor.getRaceData(created.id)
                assertNotEquals(source.id, created.id)
                assertEquals(settings.name, created.name)
                assertEquals(settings.startDateTime, created.startDateTime)
                assertEquals(source.raceType, created.raceType)
                assertEquals(source.raceLevel, created.raceLevel)
                assertEquals(source.raceBand, created.raceBand)
                assertEquals(source.timeLimit, created.timeLimit)
                assertEquals("", created.apiKey)
                assertNull(created.importSourceId)
                assertNull(created.importFingerprint)
                assertNull(created.publicResultsUrl)
                assertNull(created.publicResultsPublishedAtIso)
                assertNull(processor.getEventSeriesForRace(created.id))
                assertTrue(processor.getResultDataFlowByRace(created.id).first().isEmpty())
                assertEquals(if (includeCompetitors) 1 else 0, copied.competitorData.size)
                copied.competitorData.singleOrNull()?.let {
                    val competitor = it.competitorCategory.competitor
                    assertNotEquals(before.competitorData.first().competitorCategory.competitor.id, competitor.id.toString())
                    assertEquals(created.id, competitor.raceId)
                    assertEquals("Runner", competitor.firstName)
                    assertEquals(2005018, competitor.siNumber)
                    assertEquals("person-1", competitor.index)
                    assertEquals("42", competitor.bibNumber)
                    assertNull(competitor.drawnRelativeStartTime)
                    assertEquals(0, competitor.startNumber)
                    assertNull(it.readoutData)
                }
                val category = copied.categories.single()
                assertNotEquals(before.categories.single().category.id, category.category.id.toString())
                assertEquals(4200, category.category.length)
                assertEquals(85, category.category.climb)
                assertEquals(listOf(31, 32), category.controlPoints.map { it.siCode })
                assertTrue(category.controlPoints.all { it.categoryId == category.category.id })
                assertTrue(category.controlPoints.none { point -> before.categories.single().controlPoints.any { it.id == point.id.toString() } })
                assertEquals(if (includeCompetitors) 1 else 0, category.competitors.size)
                if (encrypted) {
                    assertEquals("31,32", ProtectedCourseCipher.decrypt(category.category.encryptedIdealOrder!!, "test-password"))
                    assertEquals("Saved course", ProtectedCourseCipher.decryptCourseInfo(category.category.encryptedCourseInfo!!, "test-password").sourceName)
                } else assertEquals("31,32", category.category.idealOrder)
                assertEquals(before, processor.getRaceData(source.id).toEventRaceData())
            }
        }
        assertEquals(6, processor.getRaces().first().size)
    }

    @Test fun seriesMemberCopiesAreStandaloneAndCanJoinEitherSeries() = runBlocking {
        val source = sourceRace(false)
        val originalSeries = processor.createEventSeriesFromRace(source.id, "Original series")
        val otherRace = Race().copy(name = "Other race")
        processor.createRace(otherRace)
        val otherSeries = processor.createEventSeriesFromRace(otherRace.id, "Other series")
        val originalMembers = processor.getEventSeriesForRace(source.id)!!.members
        for (destination in listOf(originalSeries, otherSeries)) {
            val created = processor.createRaceFromExisting(source.id,
                source.copy(id = UUID.randomUUID(), name = "Standalone copy", apiKey = ""), includeCompetitors = true)
            assertNull(processor.getEventSeriesForRace(created.id))
            assertEquals(originalSeries.series.seriesId, processor.getEventSeriesForRace(source.id)!!.series.seriesId)
            processor.addRaceToEventSeries(created.id, destination.series.seriesId)
            assertEquals(destination.series.seriesId, processor.getEventSeriesForRace(created.id)!!.series.seriesId)
        }
        assertTrue(processor.getEventSeriesForRace(source.id)!!.members.containsAll(originalMembers))
        assertEquals(3, processor.getResultDataFlowByRace(source.id).first().size)
    }

    private suspend fun sourceRace(encrypted: Boolean): Race {
        val race = Race().copy(name = "Source $encrypted", raceLevel = RaceLevel.NATIONAL, raceType = RaceType.CLASSIC,
            timeLimit = Duration.ofMinutes(135), apiKey = "test-source-id")
        val category = Category("M21").copy(raceId = race.id, length = 4200, climb = 85, idealOrder = "31,32",
            courseInfo = ProtectedCourseCipher.encodeCourseInfo(ProtectedCourseInfo(sourceName = "Saved course")))
        val competitor = Competitor().copy(raceId = race.id, categoryId = category.id, firstName = "Runner", siNumber = 2005018,
            index = "person-1", bibNumber = "42", startNumber = 3, drawnRelativeStartTime = Duration.ofMinutes(5))
        val rows = RaceData(race, listOf(CategoryData(category, listOf(31, 32).mapIndexed { index, code ->
            ControlPoint(code).copy(categoryId = category.id, order = index)
        }, listOf(competitor))), emptyList(), listOf(CompetitorData(CompetitorCategory(competitor, category), null)), emptyList())
        val project = EventProjectFile(raceData = rows.toEventRaceData())
        val prepared = if (encrypted) ProtectedCourseCipher.protectProjectCourseData(project, "test-password") else project
        val stored = prepared.raceData.toRoomRaceData()
        processor.saveRaceData(stored.copy(race = stored.race.copy(importSourceId = "import-${race.id}", importFingerprint = "fingerprint",
            publicResultsUrl = "https://example.invalid/source", publicResultsPublishedAtIso = "2026-09-08T12:00:00")))
        repeat(2) { processor.createOrUpdateResult(Result().copy(raceId = race.id, competitorId = competitor.id, siNumber = competitor.siNumber)) }
        processor.createOrUpdateResult(Result().copy(raceId = race.id, competitorId = null, siNumber = 2005019))
        return processor.getRace(race.id)!!
    }
}
