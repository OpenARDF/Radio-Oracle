package org.openardf.radiooracle.desktop

/** Whether the Analyzer input is a pending design or the course currently used by the race. */
enum class DesktopCourseRouteSource(val label: String) {
    Draft("Draft"),
    Applied("Applied");

    companion object {
        fun forProject(project: org.openardf.radiooracle.shared.event.EventProjectFile?): DesktopCourseRouteSource =
            if (project?.raceData?.courseDraft != null) Draft else Applied
    }

    val lowerLabel: String get() = label.lowercase()
    val routeLabel: String get() = "$label route"
    val lowerRouteLabel: String get() = "$lowerLabel route"
    val waitAnalysisHeading: String get() = "$label-route wait-time analysis"
    val description: String get() = when (this) {
        Draft -> "This section analyzes the pending course draft. Save Race preserves it; Apply Calculated Course makes the calculated design active for race downloads and results."
        Applied -> "This section analyzes the course currently applied to the race and used for downloads and results."
    }
}
