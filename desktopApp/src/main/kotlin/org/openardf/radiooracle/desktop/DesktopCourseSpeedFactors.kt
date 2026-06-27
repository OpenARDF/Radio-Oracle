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

data class DesktopCourseSpeedFactorTable(
    val categoryFactors: List<DesktopCourseCategorySpeedFactor>,
    val unmatchedCategoryMultiplier: Double = 1.00,
    val sourceLabel: String,
    val explanation: String
) {
    fun categoryMultiplier(categoryKey: String?): Double =
        categoryFactors
            .firstOrNull { factor -> categoryKey in factor.categoryCodes }
            ?.multiplier
            ?: unmatchedCategoryMultiplier
}

data class DesktopCourseCategorySpeedFactor(
    val categoryCodes: List<String>,
    val multiplier: Double
)

object DesktopCourseSpeedFactors {
    val provisionalCategoryTable = DesktopCourseSpeedFactorTable(
        categoryFactors = listOf(
            DesktopCourseCategorySpeedFactor(listOf("M21"), 1.00),
            DesktopCourseCategorySpeedFactor(listOf("M19", "M40"), 0.95),
            DesktopCourseCategorySpeedFactor(listOf("M50"), 0.86),
            DesktopCourseCategorySpeedFactor(listOf("M60"), 0.76),
            DesktopCourseCategorySpeedFactor(listOf("M70"), 0.65),
            DesktopCourseCategorySpeedFactor(listOf("M80"), 0.55),
            DesktopCourseCategorySpeedFactor(listOf("M16"), 0.80),
            DesktopCourseCategorySpeedFactor(listOf("M14"), 0.65),
            DesktopCourseCategorySpeedFactor(listOf("M12"), 0.55),
            DesktopCourseCategorySpeedFactor(listOf("W21"), 0.88),
            DesktopCourseCategorySpeedFactor(listOf("W19", "W35"), 0.84),
            DesktopCourseCategorySpeedFactor(listOf("W45"), 0.74),
            DesktopCourseCategorySpeedFactor(listOf("W55"), 0.64),
            DesktopCourseCategorySpeedFactor(listOf("W65"), 0.55),
            DesktopCourseCategorySpeedFactor(listOf("W75"), 0.47),
            DesktopCourseCategorySpeedFactor(listOf("W16"), 0.72),
            DesktopCourseCategorySpeedFactor(listOf("W14"), 0.60),
            DesktopCourseCategorySpeedFactor(listOf("W12"), 0.52)
        ),
        sourceLabel = "Provisional built-in category assumptions",
        explanation = "These category multipliers are provisional built-in assumptions, not a rules-derived " +
            "or event-calibrated table. Future route modeling should keep this category table as one input " +
            "to per-leg speed adjustment alongside vegetation, runnability, climb, barriers, and other " +
            "map-derived course-condition factors."
    )
}
