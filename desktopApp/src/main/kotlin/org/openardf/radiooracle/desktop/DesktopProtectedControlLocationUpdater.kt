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

/** Applies course-password control location edits across affected stored course payloads. */
object DesktopProtectedControlLocationUpdater {
    fun applyControlLocation(
        projectFile: EventProjectFile,
        courseInfoByCategoryId: Map<String, ProtectedCourseInfo>,
        controlId: String,
        latitudeText: String,
        longitudeText: String,
        password: String,
        elevationLookup: (CourseGeoPoint) -> Double? = { null }
    ): DesktopProtectedControlLocationUpdateResult {
        val trimmedPassword = password.trim()
        require(trimmedPassword.isNotEmpty()) {
            "Race Password is required."
        }
        val latitude = parseLatitude(latitudeText)
        val longitude = parseLongitude(longitudeText)
        return applyControlLocations(
            projectFile = projectFile,
            courseInfoByCategoryId = courseInfoByCategoryId,
            updates = listOf(
                DesktopProtectedControlLocationUpdate(
                    controlId = controlId,
                    latitude = latitude,
                    longitude = longitude
                )
            ),
            password = trimmedPassword,
            elevationLookup = elevationLookup
        )
    }

    fun applyControlLocations(
        projectFile: EventProjectFile,
        courseInfoByCategoryId: Map<String, ProtectedCourseInfo>,
        updates: List<DesktopProtectedControlLocationUpdate>,
        password: String,
        elevationLookup: (CourseGeoPoint) -> Double? = { null },
        invalidateAllReferencedProtectedCourses: Boolean = true
    ): DesktopProtectedControlLocationUpdateResult {
        val trimmedPassword = password.trim()
        require(trimmedPassword.isNotEmpty()) {
            "Race Password is required."
        }
        val uniqueUpdates = updates.distinctBy { it.controlId }
        require(uniqueUpdates.isNotEmpty()) {
            "No control location updates were provided."
        }
        uniqueUpdates.forEach { update ->
            require(update.latitude.isValidLatitude()) {
                "Latitude must be between -90 and 90."
            }
            require(update.longitude.isValidLongitude()) {
                "Longitude must be between -180 and 180."
            }
        }
        val updatesByControlId = uniqueUpdates.associateBy { it.controlId }
        val missingControlId = uniqueUpdates.firstOrNull { update ->
            projectFile.raceData.controls.none { it.id == update.controlId }
        }?.controlId
        require(missingControlId == null) {
            "Control was not found: $missingControlId"
        }
        val elevationByControlId = updatesByControlId.mapValues { (_, update) ->
            elevationLookup(CourseGeoPoint(latitude = update.latitude, longitude = update.longitude))
        }
        val updatedControls = projectFile.raceData.controls.map { eventControl ->
            if (eventControl.latitude != null || eventControl.longitude != null) {
                eventControl.copy(latitude = null, longitude = null)
            } else {
                eventControl
            }
        }

        val categoryNamesById = projectFile.raceData.categories.associate { categoryData ->
            categoryData.category.id to categoryData.category.name
        }
        val updatedInfoByCategoryId = courseInfoByCategoryId.toMutableMap()
        val affectedCategoryIds = linkedSetOf<String>()
        val encryptedCourseInfoByCategoryId = mutableMapOf<String, String>()
        courseInfoByCategoryId.forEach { (categoryId, courseInfo) ->
            val hasControlPoint = courseInfo.controlPoints.any { controlPoint ->
                val update = updatesByControlId[controlPoint.controlId]
                update != null &&
                    (invalidateAllReferencedProtectedCourses || controlPoint.locationDiffersFrom(update))
            }
            val hasCourseObject = courseInfo.courseObjects.any { courseObject ->
                val update = updatesByControlId[courseObject.id]
                update != null &&
                    (invalidateAllReferencedProtectedCourses || courseObject.locationDiffersFrom(update))
            }
            if (hasControlPoint || hasCourseObject) {
                val updatedInfo = courseInfo.copy(
                    lengthMeters = null,
                    climbMeters = null,
                    sourceName = "Control location update; stored route invalidated",
                    sourceSha256 = "",
                    sampledPointCount = 0,
                    route = emptyList(),
                    controlPoints = courseInfo.controlPoints.map { controlPoint ->
                        val update = updatesByControlId[controlPoint.controlId]
                        if (update != null) {
                            controlPoint.copy(
                                latitude = update.latitude,
                                longitude = update.longitude,
                                elevationMeters = elevationByControlId[controlPoint.controlId]
                            )
                        } else {
                            controlPoint
                        }
                    },
                    courseObjects = courseInfo.courseObjects.map { courseObject ->
                        val update = updatesByControlId[courseObject.id]
                        if (update != null) {
                            courseObject.copy(
                                latitude = update.latitude,
                                longitude = update.longitude,
                                elevationMeters = elevationByControlId[courseObject.id]
                            )
                        } else {
                            courseObject
                        }
                    }
                )
                updatedInfoByCategoryId[categoryId] = updatedInfo
                encryptedCourseInfoByCategoryId[categoryId] =
                    DesktopProtectedCourseOrder.encryptCourseInfo(updatedInfo, trimmedPassword)
                affectedCategoryIds += categoryId
            }
        }

        val updatedCategories = projectFile.raceData.categories.map { categoryData ->
            encryptedCourseInfoByCategoryId[categoryData.category.id]?.let { encryptedCourseInfo ->
                categoryData.copy(
                    category = categoryData.category.copy(encryptedCourseInfo = encryptedCourseInfo)
                )
            } ?: categoryData
        }

        return DesktopProtectedControlLocationUpdateResult(
            projectFile = projectFile.copy(
                raceData = projectFile.raceData.copy(
                    controls = updatedControls,
                    categories = updatedCategories
                )
            ),
            courseInfoByCategoryId = updatedInfoByCategoryId,
            controlLabel = uniqueUpdates.singleOrNull()?.let { update ->
                projectFile.raceData.controls.first { it.id == update.controlId }.publicControlLabel()
            } ?: "${uniqueUpdates.size} controls",
            updatedControlCount = uniqueUpdates.size,
            affectedCategoryNames = affectedCategoryIds.mapNotNull(categoryNamesById::get),
            affectedCategoryIds = affectedCategoryIds.toList()
        )
    }

    private fun parseLatitude(latitudeText: String): Double {
        val latitude = latitudeText.trim().toDoubleOrNull()
            ?: throw IllegalArgumentException("Latitude must be a number.")
        require(latitude.isValidLatitude()) {
            "Latitude must be between -90 and 90."
        }
        return latitude
    }

    private fun parseLongitude(longitudeText: String): Double {
        val longitude = longitudeText.trim().toDoubleOrNull()
            ?: throw IllegalArgumentException("Longitude must be a number.")
        require(longitude.isValidLongitude()) {
            "Longitude must be between -180 and 180."
        }
        return longitude
    }

    private fun org.openardf.radiooracle.shared.event.EventControl.publicControlLabel(): String =
        publicLabel?.trim()?.takeIf { it.isNotEmpty() } ?: label.ifBlank { siCode.toString() }

    private fun org.openardf.radiooracle.shared.event.ProtectedCourseControlPoint.locationDiffersFrom(
        update: DesktopProtectedControlLocationUpdate
    ): Boolean =
        !sameCoordinate(latitude, update.latitude) || !sameCoordinate(longitude, update.longitude)

    private fun org.openardf.radiooracle.shared.event.ProtectedCourseObjectPoint.locationDiffersFrom(
        update: DesktopProtectedControlLocationUpdate
    ): Boolean =
        !sameCoordinate(latitude, update.latitude) || !sameCoordinate(longitude, update.longitude)

    private fun sameCoordinate(first: Double, second: Double): Boolean =
        kotlin.math.abs(first - second) < 0.0000001
}

data class DesktopProtectedControlLocationUpdate(
    val controlId: String,
    val latitude: Double,
    val longitude: Double
)

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
