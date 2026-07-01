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

package org.openardf.radiooracle.shared.event

/** Shared read-only summary of a portable Race File. */
data class EventProjectSummary(
    val raceName: String,
    val categoryCount: Int,
    val competitorCount: Int,
    val readoutCount: Int,
    val resultCount: Int
) {
    companion object {
        /** Builds a summary from a Race File without changing Race File data. */
        fun from(projectFile: EventProjectFile): EventProjectSummary {
            val raceData = projectFile.raceData
            val competitorReadoutCount = raceData.competitorData.count { it.readoutData != null }
            return EventProjectSummary(
                raceName = raceData.race.name,
                categoryCount = raceData.categories.size,
                competitorCount = raceData.competitorData.size,
                readoutCount = competitorReadoutCount + raceData.unmatchedReadoutData.size,
                resultCount = competitorReadoutCount
            )
        }
    }
}
