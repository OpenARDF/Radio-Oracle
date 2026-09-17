package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.event.*
import org.openardf.radiooracle.shared.files.*

/** Uses the visible menu to put guidance beside existing CSV actions without changing their behavior. */
internal object DesktopCsvFormatGuides {
    val importActions = setOf(DesktopNavAction.ImportCategoriesCsv, DesktopNavAction.ImportCompetitorsCsv,
        DesktopNavAction.ImportControlsCsv, DesktopNavAction.ImportStartsCsv)
    private val exportActions = setOf(DesktopNavAction.ExportCategoriesCsv, DesktopNavAction.ExportCompetitorsCsv,
        DesktopNavAction.ExportControlsCsv, DesktopNavAction.ExportStartsCsv, DesktopNavAction.ExportStartsByCategoryCsv,
        DesktopNavAction.ExportStartsByMinuteCsv, DesktopNavAction.ExportRobisStartListCsv, DesktopNavAction.ExportArdfEventResultsCsv,
        DesktopNavAction.ExportReadoutsCsv, DesktopNavAction.ExportResultsCsv, DesktopNavAction.ExportSplitResultsCsv)
    fun supports(action: DesktopNavAction): Boolean = action in importActions || action in exportActions
    fun selectedAction(state: DesktopNavState): DesktopNavAction? =
        DesktopNavigation.itemById(state.workflow, state.selectedItemId)?.action?.takeIf(::supports)

    fun forNavigation(state: DesktopNavState, project: EventProjectFile?, includeEncryptedIdealOrder: Boolean,
        awardDisplayMode: EventAwardDisplayMode): List<CsvFormatGuide> {
        return DesktopFileFormatGuides.navigationActions(state)
            .mapNotNull { forAction(it, project, includeEncryptedIdealOrder, awardDisplayMode) }
            .distinctBy { it.id to it.importable }
    }

    fun forAction(action: DesktopNavAction, project: EventProjectFile?, includeEncryptedIdealOrder: Boolean = false,
        awardDisplayMode: EventAwardDisplayMode = EventAwardDisplayMode.FIRST_TO_THIRD): CsvFormatGuide? = when (action) {
        DesktopNavAction.ImportCategoriesCsv -> CsvFormatGuides.categories(true)
        DesktopNavAction.ImportCompetitorsCsv -> CsvFormatGuides.competitors(true)
        DesktopNavAction.ImportControlsCsv -> CsvFormatGuides.controls(true)
        DesktopNavAction.ImportStartsCsv -> CsvFormatGuides.starts(true)
        DesktopNavAction.ExportCategoriesCsv -> CsvFormatGuides.categories(includeEncryptedIdealOrder = includeEncryptedIdealOrder)
        DesktopNavAction.ExportCompetitorsCsv -> CsvFormatGuides.competitors()
        DesktopNavAction.ExportControlsCsv -> CsvFormatGuides.controls()
        DesktopNavAction.ExportStartsCsv, DesktopNavAction.ExportStartsByCategoryCsv, DesktopNavAction.ExportStartsByMinuteCsv -> CsvFormatGuides.starts()
        DesktopNavAction.ExportRobisStartListCsv -> CsvFormatGuides.robisStarts()
        DesktopNavAction.ExportArdfEventResultsCsv -> CsvFormatGuides.ardfEventResults()
        DesktopNavAction.ExportReadoutsCsv -> {
            val race = project?.raceData
            val readouts = race?.let { it.competitorData.mapNotNull { data -> data.readoutData } + it.unmatchedReadoutData }.orEmpty()
            CsvFormatGuides.readouts(EventCsvExports.readoutPunchColumnCount(readouts))
        }
        DesktopNavAction.ExportResultsCsv -> CsvFormatGuides.results(
            project?.let { EventAwardDetails.from(it.raceData, awardDisplayMode).hasAwards } ?: false,
            project?.let { DesktopClassicRouteAnalysis.projection(it).isNotEmpty() } ?: false)
        DesktopNavAction.ExportSplitResultsCsv -> CsvFormatGuides.splits(
            project?.let { SplitResultExports.hasRouteLengthColumns(SplitResultExports.model(it.raceData, awardDisplayMode,
                routeLengths = DesktopClassicRouteAnalysis.projection(it))) } ?: false)
        else -> null
    }
}
