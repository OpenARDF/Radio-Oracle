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

import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.event.EventControl
import org.openardf.radiooracle.shared.event.EventProjectEditor
import org.openardf.radiooracle.shared.event.EventProjectFile

internal data class DesktopTestEventDataInsertResult(
    val projectFile: EventProjectFile,
    val insertedCount: Int
)

/**
 * Inserts deterministic desktop test fixtures in stages.
 *
 * The fixture is deliberately ordinary Race File data: logical controls,
 * categories, categorized competitors, drawn starts, and SI numbers. Generated
 * SI downloads can then exercise Race Ops, Readouts, Results, exports, live
 * results, and status editing against the same course/category assignments.
 */
internal object DesktopTestEventData {
    private val testControls = listOf(
        TestControl("test-control-31", "TestF1", 31, ControlPointType.CONTROL, true, "1"),
        TestControl("test-control-32", "TestF2", 32, ControlPointType.CONTROL, true, "2"),
        TestControl("test-control-33", "TestF3", 33, ControlPointType.CONTROL, true, "3"),
        TestControl("test-control-34", "TestF4", 34, ControlPointType.CONTROL, true, "4"),
        TestControl("test-control-35", "TestF5", 35, ControlPointType.CONTROL, true, "5"),
        TestControl("test-control-36", "TestS", 36, ControlPointType.SEPARATOR, false, "S"),
        TestControl("test-control-90", "TestB", 90, ControlPointType.BEACON, false, "B")
    )

    private val testCategories = listOf(
        TestCategory("test-cat-m21", "Test M21", "31 32 33 34 35 90"),
        TestCategory("test-cat-m50", "Test M50", "31 33 35 90"),
        TestCategory("test-cat-w21", "Test W21", "32 34 35 90")
    )

    private val testCompetitors = listOf(
        TestCompetitor("test-competitor-01", "Alex", "Alpha", "test-cat-m21", 2005101, 901, 5 * 60L),
        TestCompetitor("test-competitor-02", "Blair", "Bravo", "test-cat-m21", 2005102, 902, 7 * 60L),
        TestCompetitor("test-competitor-03", "Casey", "Charlie", "test-cat-m21", 2005103, 903, 9 * 60L),
        TestCompetitor("test-competitor-04", "Devon", "Delta", "test-cat-m21", 2005104, 904, 11 * 60L),
        TestCompetitor("test-competitor-05", "Emery", "Echo", "test-cat-m50", 2005105, 905, 6 * 60L),
        TestCompetitor("test-competitor-06", "Finley", "Foxtrot", "test-cat-m50", 2005106, 906, 8 * 60L),
        TestCompetitor("test-competitor-07", "Gray", "Golf", "test-cat-m50", 2005107, 907, 10 * 60L),
        TestCompetitor("test-competitor-08", "Harper", "Hotel", "test-cat-m50", 2005108, 908, 12 * 60L),
        TestCompetitor("test-competitor-09", "Indigo", "India", "test-cat-w21", 2005109, 909, 13 * 60L),
        TestCompetitor("test-competitor-10", "Jules", "Juliet", "test-cat-w21", 2005110, 910, 15 * 60L),
        TestCompetitor("test-competitor-11", "Kai", "Kilo", "test-cat-w21", 2005111, 911, 17 * 60L),
        TestCompetitor("test-competitor-12", "Logan", "Lima", "test-cat-w21", 2005112, 912, 19 * 60L)
    )

    fun insertControls(projectFile: EventProjectFile): DesktopTestEventDataInsertResult {
        var workingProjectFile = projectFile
        var insertedCount = 0
        testControls.forEach { control ->
            if (workingProjectFile.raceData.controls.hasControlEquivalentTo(control)) {
                return@forEach
            }
            workingProjectFile = EventProjectEditor.addControl(
                projectFile = workingProjectFile,
                controlId = control.id,
                label = control.label,
                siCode = control.siCode.toString(),
                type = control.type,
                scored = control.scored,
                publicLabel = control.publicLabel,
                notes = "Generated desktop test fixture"
            )
            insertedCount += 1
        }
        return DesktopTestEventDataInsertResult(workingProjectFile, insertedCount)
    }

    fun insertCategories(projectFile: EventProjectFile): DesktopTestEventDataInsertResult {
        val controlsResult = insertControls(projectFile)
        var workingProjectFile = controlsResult.projectFile
        var insertedCount = 0
        testCategories.forEach { category ->
            if (workingProjectFile.raceData.categories.any { it.category.id == category.id }) {
                return@forEach
            }
            workingProjectFile = EventProjectEditor.addCategory(
                projectFile = workingProjectFile,
                categoryId = category.id,
                name = category.name
            )
            workingProjectFile = EventProjectEditor.updateCategoryControlPoints(
                projectFile = workingProjectFile,
                categoryId = category.id,
                controlPointsText = category.controlPointsText
            ) { index -> "${category.id}-control-$index" }
            insertedCount += 1
        }
        return DesktopTestEventDataInsertResult(workingProjectFile, insertedCount)
    }

    fun insertCompetitors(projectFile: EventProjectFile): DesktopTestEventDataInsertResult {
        val categoriesResult = insertCategories(projectFile)
        var workingProjectFile = categoriesResult.projectFile
        var insertedCount = 0
        testCompetitors.forEach { competitor ->
            if (
                workingProjectFile.raceData.competitorData.any {
                    val existing = it.competitorCategory.competitor
                    existing.id == competitor.id || existing.siNumber == competitor.siNumber
                }
            ) {
                return@forEach
            }
            workingProjectFile = EventProjectEditor.addCompetitor(
                projectFile = workingProjectFile,
                competitorId = competitor.id,
                firstName = competitor.firstName,
                lastName = competitor.lastName,
                startNumber = competitor.startNumber.toString(),
                siNumber = competitor.siNumber.toString()
            )
            workingProjectFile = EventProjectEditor.assignCompetitorCategory(
                projectFile = workingProjectFile,
                competitorId = competitor.id,
                categoryId = competitor.categoryId
            )
            workingProjectFile = EventProjectEditor.updateCompetitorClubBibCallSign(
                projectFile = workingProjectFile,
                competitorId = competitor.id,
                club = "Test Club",
                bibNumber = (1000 + competitor.startNumber).toString(),
                callSign = "T${competitor.startNumber}"
            )
            workingProjectFile = EventProjectEditor.updateCompetitorStartTime(
                projectFile = workingProjectFile,
                competitorId = competitor.id,
                startTime = secondsAsMinutesText(competitor.drawnStartSeconds)
            )
            insertedCount += 1
        }
        return DesktopTestEventDataInsertResult(workingProjectFile, insertedCount)
    }

    private fun List<EventControl>.hasControlEquivalentTo(control: TestControl): Boolean =
        any { existing -> existing.id == control.id || existing.siCode == control.siCode && existing.type == control.type }

    private fun secondsAsMinutesText(seconds: Long): String {
        val minutes = seconds / 60L
        val remainder = seconds % 60L
        return "%03d:%02d".format(minutes, remainder)
    }

    private data class TestControl(
        val id: String,
        val label: String,
        val siCode: Int,
        val type: ControlPointType,
        val scored: Boolean,
        val publicLabel: String
    )

    private data class TestCategory(
        val id: String,
        val name: String,
        val controlPointsText: String
    )

    private data class TestCompetitor(
        val id: String,
        val firstName: String,
        val lastName: String,
        val categoryId: String,
        val siNumber: Int,
        val startNumber: Int,
        val drawnStartSeconds: Long
    )
}
