package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.event.EventProjectEditor
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.files.ControlCsvImportRow

/** Rechecks eligibility at acceptance, before control updates or synchronization deletions. */
internal object DesktopControlsCsvImport {
    fun applyTo(project: EventProjectFile, rows: List<ControlCsvImportRow>, synchronize: Boolean,
        controlIdFactory: () -> String): DesktopControlImportPruneResult {
        DesktopCourseImportAvailability.requireAvailable(project)
        val imported = EventProjectEditor.importControlRows(project, rows, controlIdFactory)
        if (!synchronize) return DesktopControlImportPruneResult(imported, emptyList())
        val identities = rows.mapTo(mutableSetOf()) { it.siCode to it.type }
        val importedIds = imported.raceData.controls.filter { it.siCode to it.type in identities }
            .mapTo(mutableSetOf()) { it.id }
        return DesktopControlImportPruning.pruneUnmatchedControlsExceedingRaceLimits(imported, importedIds)
    }
}
