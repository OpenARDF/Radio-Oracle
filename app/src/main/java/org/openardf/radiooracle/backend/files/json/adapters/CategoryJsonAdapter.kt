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

package org.openardf.radiooracle.backend.files.json.adapters

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import org.openardf.radiooracle.backend.files.json.temps.CategoryJson
import org.openardf.radiooracle.backend.files.json.temps.ControlPointJson
import org.openardf.radiooracle.backend.helpers.ControlPointsHelper
import org.openardf.radiooracle.backend.room.entity.Category
import org.openardf.radiooracle.backend.room.entity.ControlPoint
import org.openardf.radiooracle.backend.room.entity.embeddeds.CategoryData
import org.openardf.radiooracle.backend.room.enums.ControlPointType
import java.util.UUID

/**
 * Moshi adapter for converting category Room aggregates to the race JSON schema.
 *
 * Older Android JSON can contain category-level race settings. They are deliberately normalized
 * here because the owning race is the only source of truth for race type, band, and time limit.
 */
class CategoryJsonAdapter(val raceId: UUID) {
    /** Serializes a category, including its ordered control points. */
    @ToJson
    fun toJson(categoryData: CategoryData): CategoryJson {
        val category = categoryData.category
        return CategoryJson(
            category_name = category.name,
            category_gender = category.isMan,
            category_max_age = category.maxAge,
            category_length = category.length,
            category_climb = category.climb,
            category_different_properties = false,
            category_race_type = null,
            category_time_limit = "",
            category_band = null,
            category_control_points = categoryData.controlPoints.map { cp ->
                ControlPointJson(cp.siCode, cp.type)
            }
        )
    }

    /** Deserializes a category and recreates its control points with fresh local identifiers. */
    @FromJson
    fun fromJson(categoryJson: CategoryJson): CategoryData {
        val catId = UUID.randomUUID()

        val controlPoints = categoryJson.category_control_points.mapIndexed { index, json ->
            ControlPoint(
                UUID.randomUUID(),
                catId,
                json.si_code,
                ControlPointType.CONTROL,
                index
            )
        }

        val category = Category(
            id = catId,
            raceId = raceId,
            name = categoryJson.category_name,
            isMan = categoryJson.category_gender,
            maxAge = categoryJson.category_max_age,
            length = categoryJson.category_length ?: 0,
            climb = categoryJson.category_climb ?: 0,
            order = 0,
            differentProperties = false,
            raceType = null,
            categoryBand = null,
            timeLimit = null,
            controlPointsString = ControlPointsHelper.getStringFromControlPoints(controlPoints)
        )

        return CategoryData(category, controlPoints, emptyList())
    }
}
