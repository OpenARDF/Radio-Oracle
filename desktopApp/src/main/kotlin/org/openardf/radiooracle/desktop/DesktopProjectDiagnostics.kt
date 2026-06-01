package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventProjectSummary

/** Read-only project and desktop-beta diagnostics shown in the Settings section. */
data class DesktopProjectDiagnostics(
    val projectState: String,
    val schemaText: String,
    val raceId: String,
    val raceName: String,
    val startDateTimeIso: String,
    val categoryCount: Int,
    val competitorCount: Int,
    val readoutCount: Int,
    val resultCount: Int,
    val betaLimitations: List<String>
) {
    companion object {
        fun from(projectFile: EventProjectFile?): DesktopProjectDiagnostics {
            val summary = projectFile?.let(EventProjectSummary::from)
            return DesktopProjectDiagnostics(
                projectState = if (projectFile == null) "No project open" else "Project open",
                schemaText = projectFile?.let { "${it.appName} schema ${it.schemaVersion}" } ?: "",
                raceId = projectFile?.raceData?.race?.id ?: "",
                raceName = summary?.raceName ?: "",
                startDateTimeIso = projectFile?.raceData?.race?.startDateTimeIso ?: "",
                categoryCount = summary?.categoryCount ?: 0,
                competitorCount = summary?.competitorCount ?: 0,
                readoutCount = summary?.readoutCount ?: 0,
                resultCount = summary?.resultCount ?: 0,
                betaLimitations = listOf(
                    "Live SPORTident download remains post-beta.",
                    "Printing remains post-beta.",
                    "Live result sending remains post-beta.",
                    "Project storage is file-backed .rom.json, not shared SQL."
                )
            )
        }
    }
}
