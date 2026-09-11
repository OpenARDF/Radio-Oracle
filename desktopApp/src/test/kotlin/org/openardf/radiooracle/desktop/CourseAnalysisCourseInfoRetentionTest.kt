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

package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.EventCategory
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventRace
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.event.ProtectedCourseRoutePoint

class CourseAnalysisCourseInfoRetentionTest {
    @Test
    fun retainsUnlockedRouteBearingCourseInfoWhenCurrentMapIsTemporarilyEmpty() {
        val courseInfo = protectedCourseInfo("imported.kml")
        val encryptedCourseInfo = DesktopProtectedCourseOrder.encryptCourseInfo(courseInfo, "password")
        val projectFile = projectFile(encryptedCourseInfo)
        val retained = retainedCourseAnalysisCourseInfo(
            projectFile = projectFile,
            currentCourseInfoByCategoryId = mapOf(CategoryId to courseInfo),
            previousRetainedCourseInfoByCategoryId = emptyMap()
        )

        val effective = effectiveCourseAnalysisCourseInfoByCategoryId(
            projectFile = projectFile,
            currentCourseInfoByCategoryId = emptyMap(),
            retainedCourseInfoByCategoryId = retained
        )

        assertTrue(CategoryId in retained)
        assertEquals(setOf(CategoryId), effective.keys)
        assertSame(courseInfo, effective.getValue(CategoryId))
    }

    @Test
    fun retainsInactiveCourseMappingRouteBearingCourseInfo() {
        val courseInfo = protectedCourseInfo("imported.gpx")
        val encryptedCourseInfo = DesktopProtectedCourseOrder.encryptCourseInfo(courseInfo, "password")
        val projectFile = inactiveCourseMappingProjectFile(encryptedCourseInfo)
        val decryptedState = decryptedProtectedCourseState(projectFile, "password")

        val retained = retainedCourseAnalysisCourseInfo(
            projectFile = projectFile,
            currentCourseInfoByCategoryId = decryptedState.protectedCourseInfoByCategoryId,
            previousRetainedCourseInfoByCategoryId = emptyMap()
        )
        val effective = effectiveCourseAnalysisCourseInfoByCategoryId(
            projectFile = projectFile,
            currentCourseInfoByCategoryId = emptyMap(),
            retainedCourseInfoByCategoryId = retained
        )
        val pickerCategories = courseAnalysisRouteCategories(
            projectFile = projectFile,
            analysisCourseInfoByCategoryId = effective
        )

        assertEquals(courseInfo, decryptedState.protectedCourseInfoByCategoryId.getValue(CategoryId))
        assertTrue(CategoryId in retained)
        assertEquals(setOf(CategoryId), effective.keys)
        assertEquals(courseInfo, effective.getValue(CategoryId))
        assertEquals(listOf(CategoryId), pickerCategories.map { it.category.id })
    }

    @Test
    fun doesNotReuseRetainedCourseInfoAfterEncryptedPayloadChanges() {
        val oldCourseInfo = protectedCourseInfo("old.kml")
        val oldEncryptedCourseInfo = DesktopProtectedCourseOrder.encryptCourseInfo(oldCourseInfo, "password")
        val retained = retainedCourseAnalysisCourseInfo(
            projectFile = projectFile(oldEncryptedCourseInfo),
            currentCourseInfoByCategoryId = mapOf(CategoryId to oldCourseInfo),
            previousRetainedCourseInfoByCategoryId = emptyMap()
        )
        val newProjectFile = projectFile(DesktopProtectedCourseOrder.encryptCourseInfo(protectedCourseInfo("new.kml"), "password"))

        val effective = effectiveCourseAnalysisCourseInfoByCategoryId(
            projectFile = newProjectFile,
            currentCourseInfoByCategoryId = emptyMap(),
            retainedCourseInfoByCategoryId = retained
        )

        assertFalse(CategoryId in effective)
    }

    @Test
    fun prunesRetainedCourseInfoWhenCategoryNoLongerHasEncryptedCourseData() {
        val courseInfo = protectedCourseInfo("imported.kml")
        val encryptedCourseInfo = DesktopProtectedCourseOrder.encryptCourseInfo(courseInfo, "password")
        val retained = retainedCourseAnalysisCourseInfo(
            projectFile = projectFile(encryptedCourseInfo),
            currentCourseInfoByCategoryId = mapOf(CategoryId to courseInfo),
            previousRetainedCourseInfoByCategoryId = emptyMap()
        )

        val pruned = retainedCourseAnalysisCourseInfo(
            projectFile = projectFile(encryptedCourseInfo = null),
            currentCourseInfoByCategoryId = emptyMap(),
            previousRetainedCourseInfoByCategoryId = retained
        )

        assertFalse(CategoryId in pruned)
    }

    @Test
    fun reopeningAppliedCoursesOffersBothForAnalysisWithoutAnUnlockCache() {
        val path = java.nio.file.Files.createTempFile("applied-analyzer-startup", ".kml")
        try {
            java.nio.file.Files.writeString(path, DesktopAuthoritativeCourseImportTest.kml())
            val (imported, summary) = DesktopCourseKmlImporter.importProtectedCourseInfo(
                path, DesktopAuthoritativeCourseImportTest.project(), null, elevationProvider = { 100.0 })
            val applied = DesktopAuthoritativeCourseImport.prepare(imported, summary.matchedCategoryIds.toSet(), null)
            val reopened = org.openardf.radiooracle.shared.event.EventProjectFileJson.decode(
                org.openardf.radiooracle.shared.event.EventProjectFileJson.encode(applied))
            val effective = effectiveCourseAnalysisCourseInfoByCategoryId(reopened, emptyMap(), emptyMap())
            val categories = courseAnalysisRouteCategories(reopened, effective)
            assertEquals(setOf("Full", "Short"), categories.map { it.category.name }.toSet())
            categories.forEach { data ->
                val info = effective.getValue(data.category.id)
                assertEquals(null, DesktopCourseAnalyzer.analysisUnavailableReason(reopened, data.category.id, info, null))
                val analysis = DesktopCourseAnalyzer.analyze(reopened, data.category.id, info, null,
                    controlIdentityMode = DesktopCourseControlIdentityMode.RESULT_CONTROLS, allowFoxRenumbering = false)
                assertTrue(analysis.providedRouteSection != null)
                assertTrue(analysis.routeMaps.isNotEmpty())
            }
        } finally { java.nio.file.Files.deleteIfExists(path) }
    }

    @Test
    fun plaintextCourseInRaceTakesPrecedenceOverStaleCachedGeometry() {
        val stored = protectedCourseInfo("applied.kml")
        val project = projectFile(null).withStoredCourseInfo(CategoryId, stored, null)
        val stale = protectedCourseInfo("previous.kml")
        val effective = effectiveCourseAnalysisCourseInfoByCategoryId(project, mapOf(CategoryId to stale), emptyMap())
        assertEquals(stored, effective.getValue(CategoryId))
    }

    @Test
    fun removingPlaintextCourseDoesNotResurrectItFromTheCache() {
        val effective = effectiveCourseAnalysisCourseInfoByCategoryId(projectFile(null),
            mapOf(CategoryId to protectedCourseInfo("removed.kml")), emptyMap())
        assertTrue(effective.isEmpty())
    }

    @Test
    fun encryptedCourseNeverFallsBackToAPlaintextFieldWithoutUnlocking() {
        val info = protectedCourseInfo("protected.kml")
        val original = projectFile(DesktopProtectedCourseOrder.encryptCourseInfo(info, "password"))
        val project = original.copy(raceData = original.raceData.copy(categories = original.raceData.categories.map {
            it.copy(category = it.category.copy(courseInfo = info))
        }))
        assertTrue(effectiveCourseAnalysisCourseInfoByCategoryId(project, emptyMap(), emptyMap()).isEmpty())
    }

    private fun protectedCourseInfo(sourceName: String): ProtectedCourseInfo =
        ProtectedCourseInfo(
            idealOrder = "31",
            sourceName = sourceName,
            sampledPointCount = 2,
            route = listOf(
                ProtectedCourseRoutePoint(39.0, -95.0),
                ProtectedCourseRoutePoint(39.0, -94.99)
            )
        )

    private fun projectFile(encryptedCourseInfo: String?): EventProjectFile {
        val category = EventCategory(
            id = CategoryId,
            raceId = RaceId,
            name = "M21",
            isMan = true,
            maxAge = 21,
            lengthMeters = 0,
            climbMeters = 0,
            order = 1,
            differentProperties = false,
            raceType = null,
            raceBand = null,
            timeLimitSeconds = null,
            controlPointsString = "",
            encryptedCourseInfo = encryptedCourseInfo
        )
        return EventProjectFile(
            raceData = EventRaceData(
                race = EventRace(
                    id = RaceId,
                    name = "Test Event",
                    apiKey = "",
                    startDateTimeIso = "2026-06-22T09:00:00",
                    raceType = RaceType.CLASSIC,
                    raceLevel = RaceLevel.PRACTICE,
                    raceBand = RaceBand.M80,
                    timeLimitSeconds = 7_200
                ),
                categories = listOf(
                    EventCategoryData(
                        category = category,
                        controlPoints = emptyList(),
                        competitors = emptyList()
                    )
                ),
                aliases = emptyList(),
                competitorData = emptyList(),
                unmatchedReadoutData = emptyList(),
                controls = emptyList()
            )
        )
    }

    private fun inactiveCourseMappingProjectFile(encryptedCourseInfo: String?): EventProjectFile {
        val projectFile = projectFile(encryptedCourseInfo)
        return projectFile.copy(
            raceData = projectFile.raceData.copy(
                categories = emptyList(),
                courseMappings = projectFile.raceData.categories
            )
        )
    }

    private companion object {
        const val RaceId = "race"
        const val CategoryId = "cat-m21"
    }
}
