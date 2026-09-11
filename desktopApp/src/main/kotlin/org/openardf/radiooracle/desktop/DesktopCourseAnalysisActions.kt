package org.openardf.radiooracle.desktop

internal enum class CourseAnalysisApplyAction(val label: String, val tooltip: String) {
    CalculatedRoute(
        "Apply Calculated Course",
        "Applies the calculated route and fox numbering, and updates affected courses."
    ),
    CalculatedWithoutRenumbering(
        "Apply Calculated without renumbering",
        "Applies the calculated route without changing the fox numbering, and updates affected courses."
    );

    fun disabledReason(summary: DesktopCourseAnalysisSummary?): String? {
        if (summary == null) return "Run analysis before applying a calculated course."
        val application = summary.calculatedRouteApplication
            ?: return "No calculated course is available to apply."
        if (this == CalculatedWithoutRenumbering && summary.calculatedGeometryMatchesSource) {
            return if (application.foxAssignments.any { it.originalLabel != it.calculatedLabel }) {
                "Only the fox numbering differs; there are no route changes to apply without renumbering. Use Apply Calculated Course to apply the proposed numbering."
            } else {
                "The calculated route already matches the course being analyzed; there are no route changes to apply without renumbering."
            }
        }
        if (summary.routeSource == DesktopCourseRouteSource.Applied && summary.calculatedRouteSection?.summaryOnly == true) {
            return "The calculated course matches the applied course; there are no changes to apply."
        }
        return null
    }

    /** Both actions use the same reviewed geometry and existing all-course application transaction. */
    fun application(summary: DesktopCourseAnalysisSummary): DesktopCourseCalculatedRouteApplication {
        require(disabledReason(summary) == null) { disabledReason(summary).orEmpty() }
        val calculated = requireNotNull(summary.calculatedRouteApplication)
        return when (this) {
            CalculatedRoute -> calculated
            CalculatedWithoutRenumbering -> calculated.copy(
                idealOrderText = calculated.idealOrderWithoutRenumbering,
                foxAssignments = calculated.foxAssignments.map { it.copy(calculatedLabel = it.originalLabel) }
            )
        }
    }
}
