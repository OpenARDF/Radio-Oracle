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

import java.time.LocalDateTime

/** JSON DTO for final result exports grouped with categories and aliases. */
data class FinalResultsJson(
    val categories: List<CategoryJson>,
    val aliases: List<AliasJson>,
    val competitors: List<CompetitorJson>,
)

/** JSON DTO for one competitor result in live-result exports. */
data class ResultCompetitorJson(
    val competitor_index: String?,
    val si_number: Int?,
    val last_name: String,
    val first_name: String,
    val competitor_category: String,
    val result: ResultJson
)

/** JSON DTO for one result/readout payload. */
data class ResultJson(
    val check_time: LocalDateTime?,
    val start_time: LocalDateTime?,
    val finish_time: LocalDateTime?,
    val run_time: String?,
    val place: Int?,
    val readoutTime: LocalDateTime?,
    val modified: Boolean?,
    val punch_count: Int?,
    val result_status: String,
    val automatic_status: Boolean?,
    val punches: List<PunchJson>
)

/** JSON DTO for one punch in a result payload. */
data class PunchJson(
    var code: String,
    var si_code: Int?,
    val control_type: String,
    val punch_status: String,
    val split_time: String
)

/** JSON DTO for a readout that is not matched to a competitor. */
data class UnmatchedResultJson(
    val si_number: Int?,
    val check_time: LocalDateTime?,
    val start_time: LocalDateTime,
    val finish_time: LocalDateTime,
    val run_time: String,
    val punches: List<PunchJson>
)
