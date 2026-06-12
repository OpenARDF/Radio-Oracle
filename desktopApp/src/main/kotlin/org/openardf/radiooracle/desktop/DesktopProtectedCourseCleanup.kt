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

        val assignedCategoryCount = DesktopImportPreviews.assignedCategoryUseCount(projectFile, setOf(controlId))
        require(assignedCategoryCount == 0) {
            "Control is still assigned to $assignedCategoryCount categor${if (assignedCategoryCount == 1) "y" else "ies"}."
        }

        val nextCourseInfoByCategoryId = protectedCourseInfoByCategoryId.toMutableMap()
        var clearedCourseCount = 0
        var prunedCourseCount = 0

        val nextCategories = projectFile.raceData.categories.map { categoryData ->
            val courseInfo = protectedCourseInfoByCategoryId[categoryData.category.id]
            if (courseInfo == null || !courseInfo.references(controlId)) {
                return@map categoryData
            }

            val hasAnyAssignedControls = categoryData.controlPoints.isNotEmpty() || categoryData.publicControlIds.isNotEmpty()
            if (!hasAnyAssignedControls) {
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
