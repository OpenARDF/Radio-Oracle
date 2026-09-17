package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.event.EventAwardDisplayMode
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.files.*

internal sealed interface DesktopFileFormatGuide {
    val key: String
    data class Csv(val guide: CsvFormatGuide) : DesktopFileFormatGuide {
        override val key: String get() = "csv-${guide.id}-${guide.importable}"
    }
    data class Kml(val guide: KmlFormatGuide) : DesktopFileFormatGuide {
        override val key: String get() = "kml-${guide.id}"
    }
}

/** One traversal of the navigation menu keeps all format boxes in button order. */
internal object DesktopFileFormatGuides {
    fun kmlForAction(action: DesktopNavAction): KmlFormatGuide? = when (action) {
        DesktopNavAction.ImportControlsKmlKmz -> KmlFormatGuides.controlsImport()
        DesktopNavAction.ImportCourseKmlKmz -> KmlFormatGuides.courseImport()
        DesktopNavAction.ExportCourseKmlKmz -> KmlFormatGuides.courseExport()
        else -> null
    }

    fun navigationActions(state: DesktopNavState): List<DesktopNavAction> {
        val selected = DesktopNavigation.itemById(state.workflow, state.selectedItemId)
        selected?.action?.let {
            return if (DesktopCsvFormatGuides.supports(it) || kmlForAction(it) != null) listOf(it) else emptyList()
        }
        val items = DesktopNavigation.menuItemsForStack(state.workflow, state.submenuStack)
        if (selected?.label !in setOf("Import", "Export", "Result Files") &&
            items.none { it.action?.let(::kmlForAction) != null }) return emptyList()
        return items.mapNotNull { it.action }
    }

    fun forNavigation(state: DesktopNavState, project: EventProjectFile?, includeEncryptedIdealOrder: Boolean,
        awardDisplayMode: EventAwardDisplayMode): List<DesktopFileFormatGuide> =
        navigationActions(state).mapNotNull { action ->
            DesktopCsvFormatGuides.forAction(action, project, includeEncryptedIdealOrder, awardDisplayMode)
                ?.let { DesktopFileFormatGuide.Csv(it) }
                ?: kmlForAction(action)?.let { DesktopFileFormatGuide.Kml(it) }
        }.distinctBy { it.key }
}
