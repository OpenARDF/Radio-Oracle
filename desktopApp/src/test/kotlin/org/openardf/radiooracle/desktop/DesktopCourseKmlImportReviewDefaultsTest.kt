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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.event.EventProjectEditor
import org.openardf.radiooracle.shared.event.EventProjectFactory

class DesktopCourseKmlImportReviewDefaultsTest {
    @Test
    fun defaultsAssignmentReplacementOnWhenMatchedCategoryHasNoAssignedControls() {
        val project = projectWithM21Category()

        assertTrue(
            defaultApplyCategoryAssignments(
                projectFile = project,
                updates = listOf(m21AssignmentUpdate()),
                categoryAssumptions = emptyList()
            )
        )
    }

    @Test
    fun leavesAssignmentReplacementOffWhenCategoryWasOnlyAssumed() {
        val project = projectWithM21Category()

        assertFalse(
            defaultApplyCategoryAssignments(
                projectFile = project,
                updates = listOf(m21AssignmentUpdate()),
                categoryAssumptions = listOf(DesktopCourseKmlCategoryAssumption("Course", "M21"))
            )
        )
    }

    @Test
    fun leavesAssignmentReplacementOffWhenMatchedCategoryAlreadyHasAssignedControls() {
        val project = EventProjectEditor.updateCategoryControlPoints(
            projectWithM21Category(),
            categoryId = "cat-m21",
            controlPointsText = "31"
        ) { index ->
            "cat-m21-control-$index"
        }

        assertFalse(
            defaultApplyCategoryAssignments(
                projectFile = project,
                updates = listOf(m21AssignmentUpdate()),
                categoryAssumptions = emptyList()
            )
        )
    }

    private fun projectWithM21Category() =
        EventProjectEditor.addCategory(
            EventProjectFactory.createEmptyProject("race", "Course Test", "2026-06-05T09:00"),
            categoryId = "cat-m21",
            name = "M21"
        )

    private fun m21AssignmentUpdate() =
        DesktopCourseKmlCategoryAssignmentUpdate(
            categoryId = "cat-m21",
            categoryName = "M21",
            controlPointsText = "31 32",
            controls = listOf(
                DesktopCourseKmlAssignedControl("control-31", 31, ControlPointType.CONTROL),
                DesktopCourseKmlAssignedControl("control-32", 32, ControlPointType.CONTROL)
            )
        )
}
