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

package org.openardf.radiooracle.backend.files.json.temps

import org.openardf.radiooracle.backend.room.enums.ControlPointType
import org.openardf.radiooracle.backend.room.enums.RaceBand
import org.openardf.radiooracle.backend.room.enums.RaceLevel
import org.openardf.radiooracle.backend.room.enums.RaceType
import java.time.LocalDateTime

/** JSON DTO for full race import/export payloads. */
data class RaceJson(
    val race_name: String,
    val race_start: LocalDateTime?,
    val race_type: RaceType?,
    val race_band: RaceBand?,
    val race_level: RaceLevel?,
    val race_time_limit: String?,
    val race_api_key: String?,
    val categories: List<CategoryJson>,
    val aliases: List<AliasJson>?,
    val competitors: List<CompetitorJson>,
    val unmatched_results: List<UnmatchedResultJson>?
)

/** JSON DTO for one category in a race import/export payload. */
data class CategoryJson(
    val category_name: String,
    val category_gender: Boolean,
    val category_max_age: Int?,
    val category_length: Int?,
    val category_climb: Int?,
    val category_control_points: List<ControlPointJson>,
    val category_different_properties: Boolean,
    val category_race_type: RaceType?,
    val category_time_limit: String?,
    val category_band: RaceBand?
)

/** JSON DTO for one control point in a category course. */
data class ControlPointJson(
    val si_code: Int,
    val control_type: ControlPointType
)

/** JSON DTO for one control-point alias in a race payload. */
data class AliasJson(
    val alias_si_code: Int,
    val alias_name: String
)

/** JSON DTO for one competitor and optional result in a race payload. */
data class CompetitorJson(
    val first_name: String,
    val last_name: String,
    val competitor_club: String?,
    val competitor_category: String,
    val competitor_index: String?,
    val competitor_gender: Boolean,
    val birth_year: Int?,
    val si_number: Int?,
    val si_rent: Boolean?,
    val start_number: Int?,
    val competitor_start_time: String?,
    val result: ResultJson?
)
