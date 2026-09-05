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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.EventCategory
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventCompetitor
import org.openardf.radiooracle.shared.event.EventCompetitorCategory
import org.openardf.radiooracle.shared.event.EventCompetitorData
import org.openardf.radiooracle.shared.event.EventControl
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventRace
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.ProtectedCourseControlPoint
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo

class DesktopCategoryActionsTest {
    @Test
    fun activatesPlaintextCourseMappingWithoutRequestingUnlock() {
        val control = control()
        val courseInfo = courseInfo(control)
        val mapping = EventCategoryData(
            category = category("mapping-m70", "M70").copy(
                lengthMeters = 2_133,
                climbMeters = 40,
                idealOrder = courseInfo.idealOrder,
                courseInfo = courseInfo
            ),
            controlPoints = emptyList(),
            competitors = emptyList()
        )
        val base = projectFile()
        val project = base.copy(
            raceData = base.raceData.copy(
                courseMappings = listOf(mapping),
                controls = listOf(control)
            )
        )

        val attempt = DesktopCategoryActions.attemptListEdit(
            projectFile = project,
            edit = DesktopCategoryListEdit.Add("M70"),
            protectedCourseInfoByCategoryId = emptyMap(),
            protectedIdealOrderByCategoryId = emptyMap()
        )

        assertTrue(attempt is DesktopCategoryListEditAttempt.Applied)
        val result = (attempt as DesktopCategoryListEditAttempt.Applied).result
        val activated = result.projectFile.raceData.categories.first { it.category.name == "M70" }
        assertEquals(2_133, activated.category.lengthMeters)
        assertEquals(40, activated.category.climbMeters)
        assertEquals(listOf(control.id), activated.publicControlIds)
        assertEquals(courseInfo, result.protectedCourseInfoByCategoryId[activated.category.id])
        assertEquals("2", result.protectedIdealOrderByCategoryId[activated.category.id])
        assertFalse(result.projectFile.raceData.courseMappings.any { it.category.name == "M70" })
    }

    @Test
    fun requestsUnlockBeforeActivatingEncryptedCourseMapping() {
        val mapping = EventCategoryData(
            category = category("mapping-m70", "M70", encryptedCourseInfo = "encrypted-course"),
            controlPoints = emptyList(),
            competitors = emptyList()
        )
        val base = projectFile()
        val project = base.copy(raceData = base.raceData.copy(courseMappings = listOf(mapping)))

        val attempt = DesktopCategoryActions.attemptListEdit(
            projectFile = project,
            edit = DesktopCategoryListEdit.Add(" M70 "),
            protectedCourseInfoByCategoryId = emptyMap(),
            protectedIdealOrderByCategoryId = emptyMap()
        )

        assertEquals(
            DesktopCategoryListEditAttempt.RequiresCourseUnlock(
                requestedName = "M70",
                mappingName = "M70"
            ),
            attempt
        )
    }

    @Test
    fun removesProtectedCategoryAndAssignedCompetitorsWhenRequested() {
        val projectFile = projectFile()

        val result = DesktopCategoryActions.removeCategory(
            projectFile = projectFile,
            categoryIdOrName = "M21",
            deleteCompetitors = true
        )

        assertTrue(result.hadProtectedCourseData)
        assertEquals("cat-m21", result.categoryId)
        assertEquals("M21", result.categoryName)
        assertEquals(1, result.removedCompetitorCount)
        assertFalse(result.projectFile.raceData.categories.any { it.category.id == "cat-m21" })
        assertEquals(listOf("cat-w21"), result.projectFile.raceData.categories.map { it.category.id })
        assertEquals(listOf("comp-w21"), result.projectFile.raceData.competitorData.map {
            it.competitorCategory.competitor.id
        })
    }

    private fun projectFile(): EventProjectFile {
        val m21 = category("cat-m21", "M21", encryptedCourseInfo = "encrypted-course")
        val w21 = category("cat-w21", "W21")
        return EventProjectFile(
            raceData = EventRaceData(
                race = EventRace(
                    id = "race",
                    name = "Category Action Test",
                    apiKey = "",
                    startDateTimeIso = "2026-06-03T10:00",
                    raceType = RaceType.CLASSIC,
                    raceLevel = RaceLevel.PRACTICE,
                    raceBand = RaceBand.M80,
                    timeLimitSeconds = 7_200
                ),
                categories = listOf(
                    EventCategoryData(m21, controlPoints = emptyList(), competitors = emptyList()),
                    EventCategoryData(w21, controlPoints = emptyList(), competitors = emptyList())
                ),
                aliases = emptyList(),
                competitorData = listOf(
                    competitor("comp-m21", m21),
                    competitor("comp-w21", w21)
                ),
                unmatchedReadoutData = emptyList()
            )
        )
    }

    private fun category(id: String, name: String, encryptedCourseInfo: String? = null): EventCategory =
        EventCategory(
            id = id,
            raceId = "race",
            name = name,
            isMan = true,
            maxAge = null,
            lengthMeters = 0,
            climbMeters = 0,
            order = 0,
            differentProperties = false,
            raceType = null,
            raceBand = null,
            timeLimitSeconds = null,
            controlPointsString = "",
            encryptedIdealOrder = null,
            encryptedCourseInfo = encryptedCourseInfo
        )

    private fun control(): EventControl = EventControl(
        id = "control-fox-2",
        raceId = "race",
        label = "2",
        siCode = 132,
        type = ControlPointType.CONTROL
    )

    private fun courseInfo(control: EventControl): ProtectedCourseInfo = ProtectedCourseInfo(
        idealOrder = "2",
        lengthMeters = 2_133,
        climbMeters = 40,
        controlPoints = listOf(
            ProtectedCourseControlPoint(
                controlId = control.id,
                label = control.label,
                latitude = 36.1,
                longitude = -78.8,
                type = control.type
            )
        )
    )

    private fun competitor(id: String, category: EventCategory): EventCompetitorData =
        EventCompetitorData(
            competitorCategory = EventCompetitorCategory(
                competitor = EventCompetitor(
                    id = id,
                    raceId = "race",
                    categoryId = category.id,
                    firstName = id,
                    lastName = "Runner",
                    club = "OPEN",
                    index = "",
                    isMan = true,
                    birthYear = null,
                    siNumber = null,
                    siRent = false,
                    startNumber = null,
                    drawnStartTimeSeconds = null
                ),
                category = category
            ),
            readoutData = null
        )
}
