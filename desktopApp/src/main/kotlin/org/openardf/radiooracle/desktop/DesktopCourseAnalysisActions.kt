package org.openardf.radiooracle.desktop

internal enum class CourseAnalysisApplyAction(val label: String, val tooltip: String) {
    ApplyCourse(
        "Apply Calculated Course",
        "Applies the calculated route without changing the fox numbering, and updates affected courses."
    ),
    RenumberAndApply(
        "Review Fox Renumbering…",
        "Reviews proposed fox number changes and affected courses before applying the calculated course."
    );

    fun disabledReason(summary: DesktopCourseAnalysisSummary?): String? {
        if (summary == null) return "Run analysis before applying a calculated course."
        val application = summary.calculatedRouteApplication
            ?: return "No calculated course is available to apply."
        if (this == RenumberAndApply && application.foxAssignments.none { it.originalLabel != it.calculatedLabel }) {
            return "The calculation proposes no fox number changes."
        }
        if (this == ApplyCourse && summary.routeSource == DesktopCourseRouteSource.Applied && summary.calculatedGeometryMatchesSource) {
            return if (application.foxAssignments.any { it.originalLabel != it.calculatedLabel }) {
                "Only the fox numbering differs; there are no route changes to apply. Use Review Fox Renumbering to inspect the proposed numbering."
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
            RenumberAndApply -> calculated
            ApplyCourse -> calculated.copy(
                idealOrderText = calculated.idealOrderWithoutRenumbering,
                foxAssignments = calculated.foxAssignments.map { it.copy(calculatedLabel = it.originalLabel) }
            )
        }
    }
}
