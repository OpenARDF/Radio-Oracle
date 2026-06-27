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

package org.openardf.radiooracle.shared.results

import org.openardf.radiooracle.shared.domain.ResultStatus

/** Comparable ranking key for sorting results by status, points, and runtime. */
data class ResultRankingKey(
    /** Result status priority. */
    val status: ResultStatus,
    /** Score used for ARDF-style ranking, sorted descending. */
    val points: Int,
    /** Runtime used as the final tie-breaker, sorted ascending. */
    val runTimeNanos: Long
) : Comparable<ResultRankingKey> {
    /** Orders by status, then higher points, then lower runtime. */
    override fun compareTo(other: ResultRankingKey): Int {
        return when {
            status != other.status -> status.compareTo(other.status)
            points != other.points -> other.points.compareTo(points)
            else -> runTimeNanos.compareTo(other.runTimeNanos)
        }
    }
}
