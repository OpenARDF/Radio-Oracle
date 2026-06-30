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

import java.util.Locale
import kotlin.math.roundToInt

data class DesktopRouteExportDescription(
    val routeName: String? = null,
    val categoryLabel: String? = null,
    val categoryText: String? = null,
    val warningLines: List<String> = emptyList(),
    val horizontalLengthMeters: Double?,
    val climbMeters: Double?,
    val climbPercent: Double? = null,
    val effectiveLengthMeters: Double?
)

/**
 * Shared route-description text for Course Analyzer and Route Generator KML exports.
 *
 * The tools calculate routes differently, but route metadata should be formatted in one
 * place so exported KML descriptions do not drift in wording or units.
 */
object DesktopRouteExportDescriptions {
    fun kmlText(description: DesktopRouteExportDescription): String =
        buildList {
            description.routeName?.takeIf { it.isNotBlank() }?.let { add("Route: $it") }
            if (!description.categoryLabel.isNullOrBlank() && !description.categoryText.isNullOrBlank()) {
                add("${description.categoryLabel}: ${description.categoryText}")
            }
            addAll(description.warningLines)
            add("Horizontal Length: ${kilometers(description.horizontalLengthMeters)}")
            add("Climb: ${climb(description.climbMeters, description.climbPercent)}")
            add("Effective Length: ${kilometers(description.effectiveLengthMeters)}")
        }.joinToString("\n")

    private fun kilometers(meters: Double?): String =
        meters?.let { "${String.format(Locale.US, "%.2f", it / 1000.0)} km" } ?: "Unknown"

    private fun climb(climbMeters: Double?, climbPercent: Double?): String {
        val climb = climbMeters ?: return "Unknown"
        val percent = climbPercent?.let { " (${String.format(Locale.US, "%.1f", it)}%)" }.orEmpty()
        return "${climb.roundToInt()}m$percent"
    }
}
