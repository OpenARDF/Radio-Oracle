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
