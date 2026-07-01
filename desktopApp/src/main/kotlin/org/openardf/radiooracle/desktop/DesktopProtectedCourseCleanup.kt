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

import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo

object DesktopProtectedCourseCleanup {
    fun removeStaleControlReferencesForDeletedControl(
        projectFile: EventProjectFile,
        protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>,
        controlId: String,
        password: String
    ): DesktopProtectedCourseCleanupResult {
        val trimmedPassword = password.trim()
        require(trimmedPassword.isNotEmpty()) {
            "Race Password cannot be blank."
        }

        val nextCourseInfoByCategoryId = protectedCourseInfoByCategoryId.toMutableMap()
        var clearedCourseCount = 0
        var prunedCourseCount = 0

        val nextCategories = projectFile.raceData.categories.map { categoryData ->
            val courseInfo = protectedCourseInfoByCategoryId[categoryData.category.id]
            val isAssignedControl = categoryData.controlPoints.any { it.controlId == controlId } ||
                controlId in categoryData.publicControlIds
            if (courseInfo == null) {
                return@map categoryData
            }
            if (!courseInfo.references(controlId) && !isAssignedControl) {
                return@map categoryData
            }

            val hasRemainingAssignedControls = categoryData.controlPoints.any { it.controlId != controlId } ||
                categoryData.publicControlIds.any { it != controlId }
            if (!hasRemainingAssignedControls) {
                clearedCourseCount += 1
                nextCourseInfoByCategoryId.remove(categoryData.category.id)
                categoryData.copy(
                    category = categoryData.category.copy(
                        encryptedIdealOrder = null,
                        encryptedCourseInfo = null
                    )
                )
            } else {
                prunedCourseCount += 1
                val prunedCourseInfo = courseInfo.copy(
                    idealOrder = "",
                    controlPoints = courseInfo.controlPoints.filterNot { it.controlId == controlId },
                    courseObjects = courseInfo.courseObjects.filterNot { it.id == controlId }
                )
                nextCourseInfoByCategoryId[categoryData.category.id] = prunedCourseInfo
                categoryData.copy(
                    category = categoryData.category.copy(
                        encryptedIdealOrder = null,
                        encryptedCourseInfo = DesktopProtectedCourseOrder.encryptCourseInfo(prunedCourseInfo, trimmedPassword)
                    )
                )
            }
        }

        return DesktopProtectedCourseCleanupResult(
            projectFile = projectFile.copy(
                raceData = projectFile.raceData.copy(categories = nextCategories)
            ),
            protectedCourseInfoByCategoryId = nextCourseInfoByCategoryId,
            clearedCourseCount = clearedCourseCount,
            prunedCourseCount = prunedCourseCount
        )
    }

    private fun ProtectedCourseInfo.references(controlId: String): Boolean =
        controlPoints.any { it.controlId == controlId } ||
            courseObjects.any { it.id == controlId }
}

data class DesktopProtectedCourseCleanupResult(
    val projectFile: EventProjectFile,
    val protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>,
    val clearedCourseCount: Int,
    val prunedCourseCount: Int
)
