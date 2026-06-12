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
            "Event Password cannot be blank."
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
