package org.openardf.radiooracle.desktop

internal const val COURSE_LEG_TIMING_NOTE =
    "Arrival and time found/departure are elapsed times from the start. " +
        "Total wait is the waiting at that control; it excludes the separately shown find/punch allowance."

/** Shared by the analyzer window and its text/PDF exports. */
internal fun DesktopCourseLegRow.analysisText(): String {
    val speedText = speedFactorOverride?.let { " (speed x${java.lang.String.format(java.util.Locale.ROOT, "%.2f", it)})" }.orEmpty()
    return "${DesktopCourseAnalyzer.summaryLengthText(lengthMeters)}  Split: ${DesktopCourseAnalyzer.summaryDurationText(splitSeconds)}$speedText\n" +
        "Arrival: ${DesktopCourseAnalyzer.summaryDurationText(arrivalSeconds)}  " +
        "Time found / departure: ${DesktopCourseAnalyzer.summaryDurationText(departureSeconds)}\n" +
        "Total wait: ${DesktopCourseAnalyzer.summaryDurationText(waitSeconds)}  " +
        "Find/punch allowance: ${DesktopCourseAnalyzer.summaryDurationText(findPunchSeconds)}"
}
