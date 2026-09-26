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

import org.openardf.radiooracle.shared.event.CourseControlLocationEdits
import org.openardf.radiooracle.shared.event.CourseControlLocationUpdate
import org.openardf.radiooracle.shared.event.CourseCoordinateRules
import org.openardf.radiooracle.shared.event.EventProjectEditor
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo

/** Applies course-password control location edits across affected stored course payloads. */
object DesktopProtectedControlLocationUpdater {
    fun applyControlLocation(
        projectFile: EventProjectFile,
        courseInfoByCategoryId: Map<String, ProtectedCourseInfo>,
        controlId: String,
        latitudeText: String,
        longitudeText: String,
        password: String?,
        elevationLookup: (CourseGeoPoint) -> Double? = { null }
    ): DesktopProtectedControlLocationUpdateResult {
        val storagePassword = projectFile.courseDataPassword(password)
        val latitude = CourseCoordinateRules.requireLatitude(latitudeText)
        val longitude = CourseCoordinateRules.requireLongitude(longitudeText)
        return applyControlLocations(
            projectFile = projectFile,
            courseInfoByCategoryId = courseInfoByCategoryId,
            updates = listOf(
                CourseControlLocationUpdate(
                    controlId = controlId,
                    latitude = latitude,
                    longitude = longitude
                )
            ),
            password = storagePassword,
            elevationLookup = elevationLookup
        )
    }

    fun applyControlLocations(
        projectFile: EventProjectFile,
        courseInfoByCategoryId: Map<String, ProtectedCourseInfo>,
        updates: List<CourseControlLocationUpdate>,
        password: String?,
        elevationLookup: (CourseGeoPoint) -> Double? = { null }
    ): DesktopProtectedControlLocationUpdateResult {
        val storagePassword = projectFile.courseDataPassword(password)
        val uniqueUpdates = updates.distinctBy { it.controlId }
        val categories = projectFile.raceData.categories + projectFile.raceData.courseMappings
        require(courseInfoByCategoryId.keys.all { id -> categories.any { it.category.id == id } }) {
            "A course category changed since its locations were loaded. Reload course data before editing."
        }
        val currentInfo = categories.mapNotNull { data ->
            val stored = data.category.storedCourseInfo(storagePassword)
            val loaded = courseInfoByCategoryId[data.category.id]
            require(stored == null || loaded == null || stored == loaded) {
                "Course data changed for ${data.category.name}. Reload course data before editing."
            }
            (stored ?: loaded)?.let { data.category.id to it }
        }.toMap()
        val categoryNamesById = (projectFile.raceData.categories + projectFile.raceData.courseMappings).associate { categoryData ->
            categoryData.category.id to categoryData.category.name
        }
        val mutation = CourseControlLocationEdits.apply(
            controls = projectFile.raceData.controls,
            courseInfoByCategoryId = currentInfo,
            updates = updates,
            elevationLookup = { location ->
                elevationLookup(CourseGeoPoint(location.latitude, location.longitude, location.elevationMeters))
            }
        )

        var updatedProject = projectFile.copy(raceData = projectFile.raceData.copy(controls = mutation.controls))
        mutation.affectedCategoryIds.forEach { categoryId ->
            updatedProject = updatedProject.withStoredCourseInfo(
                categoryId,
                mutation.courseInfoByCategoryId[categoryId],
                storagePassword
            ).withStoredIdealOrder(categoryId, null, storagePassword)
            updatedProject = EventProjectEditor.updateCategoryPhysicalStats(updatedProject, categoryId, "0", "0")
        }

        return DesktopProtectedControlLocationUpdateResult(
            projectFile = updatedProject,
            courseInfoByCategoryId = mutation.courseInfoByCategoryId,
            controlLabel = uniqueUpdates.singleOrNull()?.let { update ->
                projectFile.raceData.controls.first { it.id == update.controlId }.publicControlLabel()
            } ?: "${uniqueUpdates.size} controls",
            updatedControlCount = uniqueUpdates.size,
            affectedCategoryNames = mutation.affectedCategoryIds.mapNotNull(categoryNamesById::get),
            affectedCategoryIds = mutation.affectedCategoryIds
        )
    }

    private fun org.openardf.radiooracle.shared.event.EventControl.publicControlLabel(): String =
        publicLabel?.trim()?.takeIf { it.isNotEmpty() } ?: label.ifBlank { siCode.toString() }

}

data class DesktopProtectedControlLocationUpdateResult(
    val projectFile: EventProjectFile,
    val courseInfoByCategoryId: Map<String, ProtectedCourseInfo>,
    val controlLabel: String,
    val updatedControlCount: Int,
    val affectedCategoryNames: List<String>,
    val affectedCategoryIds: List<String> = emptyList()
) {
    val affectedCategoryCount: Int
        get() = affectedCategoryNames.size
}
