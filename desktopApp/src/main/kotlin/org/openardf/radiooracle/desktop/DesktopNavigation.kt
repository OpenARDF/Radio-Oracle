package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.event.EventProjectFile

private const val MaxSubmenuDepth = 2

enum class DesktopWorkflow(
    val label: String,
    val shortLabel: String,
    val requiresEventFileInBottomBar: Boolean = true
) {
    Setup("Setup", "Setup", requiresEventFileInBottomBar = false),
    RaceOps("Race Operations", "Race Ops"),
    ResultsExport("Results/File Export", "Results"),
    SettingsHelp("Help/About/App Settings", "Settings", requiresEventFileInBottomBar = false);

    companion object {
        val bottomBarEntries: List<DesktopWorkflow> = listOf(Setup, RaceOps, ResultsExport)
    }
}

enum class DesktopNavAction {
    NewEventFile,
    OpenEventFile,
    ImportAndroidRaceBackup,
    ImportEventRegWebsite,
    ImportEventRegCompetitorsCsv,
    SaveEventFile,
    SaveEventFileAs,
    CloseEventFile,
    ImportCategoriesCsv,
    ImportCourseKmlKmz,
    ImportControlsCsv,
    ImportCompetitorsCsv,
    ImportStartsCsv,
    ExportEventFileCopy,
    ExportCategoriesCsv,
    ExportControlsCsv,
    ExportCompetitorsCsv,
    ExportStartsCsv,
    ExportStartsByCategoryCsv,
    ExportStartsByMinuteCsv,
    ExportRobisStartListCsv,
    ExportReadoutsCsv,
    ExportResultsCsv,
    ExportArdfEventResultsCsv,
    ExportResultsText,
    ExportResultsHtml,
    ExportArdfJson,
    ExportAndroidRaceBackupJson,
    ExportLiveResultsJson,
    ExportFinalResultsJson,
    ExportIofStartListXml,
    ExportIofResultListXml,
    DownloadSiCard,
    StartContinuousSiReadout,
    StopContinuousSiReadout,
    StartLocalResultDisplay,
    StopLocalResultDisplay,
    SendRobis,
    ShowDebugLogHelp,
    ShowAbout
}

data class DesktopNavigationReadiness(
    val hasEventFile: Boolean = false,
    val hasControls: Boolean = false,
    val hasCategories: Boolean = false,
    val hasCompetitors: Boolean = false,
    val hasAssignedCompetitors: Boolean = false,
    val hasStartList: Boolean = false,
    val hasRaceOpsData: Boolean = false
) {
    val isSetupComplete: Boolean
        get() = hasEventFile &&
            hasControls &&
            hasCategories &&
            hasAssignedCompetitors &&
            hasStartList

    companion object {
        fun from(projectFile: EventProjectFile?): DesktopNavigationReadiness {
            val raceData = projectFile?.raceData ?: return DesktopNavigationReadiness()
            val categoryIds = raceData.categories.map { it.category.id }.toSet()
            val competitors = raceData.competitorData.map { it.competitorCategory.competitor }
            val hasCompetitors = competitors.isNotEmpty()
            val hasAssignedCompetitors = hasCompetitors &&
                competitors.all { competitor ->
                    competitor.categoryId != null && categoryIds.contains(competitor.categoryId)
                }

            return DesktopNavigationReadiness(
                hasEventFile = true,
                hasControls = raceData.controls.isNotEmpty(),
                hasCategories = raceData.categories.isNotEmpty(),
                hasCompetitors = hasCompetitors,
                hasAssignedCompetitors = hasAssignedCompetitors,
                hasStartList = hasCompetitors && competitors.all { it.drawnStartTimeSeconds != null },
                hasRaceOpsData = raceData.competitorData.any { it.readoutData != null } ||
                    raceData.unmatchedReadoutData.isNotEmpty()
            )
        }
    }
}

data class DesktopNavItem(
    val id: String,
    val label: String,
    val workflow: DesktopWorkflow,
    val section: DesktopSection? = null,
    val action: DesktopNavAction? = null,
    val requiresEventFile: Boolean = false,
    val children: List<DesktopNavItem> = emptyList()
)

data class DesktopNavSelection(
    val state: DesktopNavState,
    val action: DesktopNavAction? = null
)

data class DesktopNavState(
    val workflow: DesktopWorkflow = DesktopWorkflow.Setup,
    val submenuStack: List<String> = emptyList(),
    val selectedSection: DesktopSection = DesktopSection.WorkflowHome,
    val selectedItemId: String = "setup.home"
) {
    fun switchWorkflow(nextWorkflow: DesktopWorkflow): DesktopNavState =
        copy(
            workflow = nextWorkflow,
            submenuStack = emptyList(),
            selectedSection = DesktopNavigation.defaultSection(nextWorkflow),
            selectedItemId = DesktopNavigation.defaultItemId(nextWorkflow)
        )

    fun enter(item: DesktopNavItem): DesktopNavState =
        when {
            item.children.isNotEmpty() && submenuStack.size < MaxSubmenuDepth ->
                copy(
                    submenuStack = submenuStack + item.id,
                    selectedSection = item.section ?: selectedSection,
                    selectedItemId = item.id
                )
            item.section != null ->
                copy(selectedSection = item.section, selectedItemId = item.id)
            item.action != null ->
                copy(selectedItemId = item.id)
            else -> this
        }

    fun back(): DesktopNavState {
        if (submenuStack.isEmpty()) {
            return this
        }
        if (
            selectedItemId != submenuStack.last() &&
            DesktopNavigation.currentItems(this).any { it.id == selectedItemId }
        ) {
            val currentMenu = DesktopNavigation.itemById(workflow, submenuStack.last())
            return copy(
                selectedSection = currentMenu?.section ?: selectedSection,
                selectedItemId = submenuStack.last()
            )
        }
        val nextStack = submenuStack.dropLast(1)
        return if (nextStack.isEmpty()) {
            copy(
                submenuStack = emptyList(),
                selectedSection = DesktopNavigation.defaultSection(workflow),
                selectedItemId = DesktopNavigation.defaultItemId(workflow)
            )
        } else {
            val parentItem = DesktopNavigation.itemById(workflow, nextStack.last())
            copy(
                submenuStack = nextStack,
                selectedSection = parentItem?.section ?: selectedSection,
                selectedItemId = nextStack.last()
            )
        }
    }
}

object DesktopNavigation {
    val roots: Map<DesktopWorkflow, List<DesktopNavItem>> =
        DesktopWorkflow.entries.associateWith(::rootItems)

    fun rootItems(workflow: DesktopWorkflow): List<DesktopNavItem> =
        when (workflow) {
            DesktopWorkflow.Setup -> listOf(
                group(
                    "setup.event-file",
                    "Event File",
                    workflow,
                    eventFileActions(workflow),
                    DesktopSection.Races,
                    requiresEventFile = false
                ),
                group(
                    "setup.controls",
                    "Controls",
                    workflow,
                    listOf(
                        item("setup.controls.define", "Define Controls", workflow, DesktopSection.Controls),
                        action(
                            "setup.controls.import",
                            "Import Controls CSV...",
                            workflow,
                            DesktopNavAction.ImportControlsCsv
                        ),
                        action(
                            "setup.controls.import-course-kml",
                            "Import Course KML/KMZ...",
                            workflow,
                            DesktopNavAction.ImportCourseKmlKmz
                        ),
                        action(
                            "setup.controls.export",
                            "Export Controls CSV...",
                            workflow,
                            DesktopNavAction.ExportControlsCsv
                        )
                    ),
                    DesktopSection.Controls
                ),
                group(
                    "setup.categories",
                    "Categories",
                    workflow,
                    listOf(
                        item(
                            "setup.categories.protected-course-order",
                            "Protected Course Order",
                            workflow,
                            DesktopSection.ProtectedCourseOrder
                        ),
                        action(
                            "setup.categories.import",
                            "Import Categories CSV...",
                            workflow,
                            DesktopNavAction.ImportCategoriesCsv
                        ),
                        action(
                            "setup.categories.export",
                            "Export Categories CSV...",
                            workflow,
                            DesktopNavAction.ExportCategoriesCsv
                        )
                    ),
                    DesktopSection.Categories
                ),
                group(
                    "setup.competitors",
                    "Competitors",
                    workflow,
                    listOf(
                        item("setup.competitors.view", "Competitors", workflow, DesktopSection.Competitors),
                        action(
                            "setup.competitors.import",
                            "Import Competitors CSV...",
                            workflow,
                            DesktopNavAction.ImportCompetitorsCsv
                        ),
                        action(
                            "setup.competitors.import-eventreg",
                            "Import EventReg Website...",
                            workflow,
                            DesktopNavAction.ImportEventRegCompetitorsCsv
                        ),
                        action(
                            "setup.competitors.export",
                            "Export Competitors CSV...",
                            workflow,
                            DesktopNavAction.ExportCompetitorsCsv
                        )
                    ),
                    DesktopSection.Competitors
                ),
                group(
                    "setup.start-list",
                    "Start List",
                    workflow,
                    listOf(
                        item("setup.start-list.view", "Start List", workflow, DesktopSection.StartList),
                        action("setup.start-list.import", "Import Starts CSV...", workflow, DesktopNavAction.ImportStartsCsv),
                        group(
                            "setup.start-list.exports",
                            "Exports",
                            workflow,
                            listOf(
                                action("setup.start-list.export-csv", "Export Starts CSV...", workflow, DesktopNavAction.ExportStartsCsv),
                                action(
                                    "setup.start-list.export-category",
                                    "Export Starts by Category CSV...",
                                    workflow,
                                    DesktopNavAction.ExportStartsByCategoryCsv
                                ),
                                action(
                                    "setup.start-list.export-minute",
                                    "Export Starts by Minute CSV...",
                                    workflow,
                                    DesktopNavAction.ExportStartsByMinuteCsv
                                ),
                                action(
                                    "setup.start-list.export-robis",
                                    "Export ROBIS Start List CSV...",
                                    workflow,
                                    DesktopNavAction.ExportRobisStartListCsv
                                ),
                                action(
                                    "setup.start-list.export-iof",
                                    "Export IOF Start List XML...",
                                    workflow,
                                    DesktopNavAction.ExportIofStartListXml
                                )
                            )
                        )
                    ),
                    DesktopSection.StartList
                )
            )
            DesktopWorkflow.RaceOps -> listOf(
                item("race.readouts", "Readouts", workflow, DesktopSection.Readouts),
                group(
                    "race.si-readout",
                    "SI Readout",
                    workflow,
                    listOf(
                        action("race.download-si", "Download SI Card", workflow, DesktopNavAction.DownloadSiCard),
                        action("race.start-continuous", "Start Continuous SI", workflow, DesktopNavAction.StartContinuousSiReadout),
                        action("race.stop-continuous", "Stop Continuous SI", workflow, DesktopNavAction.StopContinuousSiReadout)
                    )
                ),
                item("race.in-forest", "In Forest", workflow, DesktopSection.InForest),
                item("race.unmatched", "Unmatched Readouts", workflow, DesktopSection.Readouts),
                item("race.finish-tickets", "Finish Tickets", workflow, DesktopSection.Readouts),
                item("race.hardware", "Hardware Status", workflow, DesktopSection.Settings, requiresEventFile = false)
            )
            DesktopWorkflow.ResultsExport -> listOf(
                item("results.results", "Results", workflow, DesktopSection.Results),
                group(
                    "results.live",
                    "Live Results",
                    workflow,
                    listOf(
                        action("results.start-display", "Start Display", workflow, DesktopNavAction.StartLocalResultDisplay),
                        action("results.stop-display", "Stop Display", workflow, DesktopNavAction.StopLocalResultDisplay),
                        action("results.send-robis", "Send ROBIS", workflow, DesktopNavAction.SendRobis),
                        item("results.live-settings", "Live Result Settings", workflow, DesktopSection.Settings, requiresEventFile = false)
                    )
                ),
                group(
                    "results.exports",
                    "Exports",
                    workflow,
                    listOf(
                        group(
                            "results.exports.result-files",
                            "Result Files",
                            workflow,
                            listOf(
                                action("results.export-csv", "Export Results CSV...", workflow, DesktopNavAction.ExportResultsCsv),
                                action(
                                    "results.export-ardfevent",
                                    "Export ARDFEvent Results CSV...",
                                    workflow,
                                    DesktopNavAction.ExportArdfEventResultsCsv
                                ),
                                action("results.export-text", "Export Results TXT...", workflow, DesktopNavAction.ExportResultsText),
                                action("results.export-html", "Export Results HTML...", workflow, DesktopNavAction.ExportResultsHtml),
                                action("results.export-readouts", "Export Readouts CSV...", workflow, DesktopNavAction.ExportReadoutsCsv)
                            )
                        ),
                        group(
                            "results.exports-json-xml",
                            "JSON/XML",
                            workflow,
                            listOf(
                                action("results.export-live-json", "Export Live Results JSON...", workflow, DesktopNavAction.ExportLiveResultsJson),
                                action("results.export-final-json", "Export Final Results JSON...", workflow, DesktopNavAction.ExportFinalResultsJson),
                                action("results.export-iof", "Export IOF Result List XML...", workflow, DesktopNavAction.ExportIofResultListXml),
                                action("results.export-ardf-json", "Export ARDF JSON...", workflow, DesktopNavAction.ExportArdfJson)
                            )
                        ),
                        action("results.export-copy", "Export Event File Copy...", workflow, DesktopNavAction.ExportEventFileCopy)
                    )
                )
            )
            DesktopWorkflow.SettingsHelp -> listOf(
                item("settings.app", "App Settings", workflow, DesktopSection.Settings, requiresEventFile = false),
                item("settings.hardware", "Hardware Preferences", workflow, DesktopSection.Settings, requiresEventFile = false),
                group(
                    "settings.help",
                    "Help",
                    workflow,
                    listOf(
                        item("settings.beta-scope", "Beta Scope", workflow, DesktopSection.Settings, requiresEventFile = false),
                        action(
                            "settings.logs",
                            "Logs",
                            workflow,
                            DesktopNavAction.ShowDebugLogHelp,
                            requiresEventFile = false,
                            section = DesktopSection.Settings
                        ),
                        action(
                            "settings.about",
                            "About Radio-Oracle",
                            workflow,
                            DesktopNavAction.ShowAbout,
                            requiresEventFile = false,
                            section = DesktopSection.Settings
                        )
                    ),
                    requiresEventFile = false
                )
            )
        }

    fun currentItems(state: DesktopNavState): List<DesktopNavItem> =
        state.submenuStack.fold(roots.getValue(state.workflow)) { items, id ->
            items.firstOrNull { it.id == id }?.children ?: items
        }

    fun selectItem(state: DesktopNavState, item: DesktopNavItem): DesktopNavSelection =
        when {
            item.children.isNotEmpty() -> DesktopNavSelection(state.enter(item))
            item.action == DesktopNavAction.OpenEventFile -> DesktopNavSelection(state.returnToCurrentMenu(item), item.action)
            item.action != null -> DesktopNavSelection(if (item.section == null) state else state.enter(item), item.action)
            item.section != null -> DesktopNavSelection(state.enter(item))
            else -> DesktopNavSelection(state)
        }

    fun findCurrentItemByLabel(state: DesktopNavState, label: String): DesktopNavItem? =
        currentItems(state).firstOrNull { it.label == label }

    fun itemById(workflow: DesktopWorkflow, id: String): DesktopNavItem? =
        allItems(workflow).firstOrNull { it.id == id }

    fun isWorkflowEnabled(workflow: DesktopWorkflow, readiness: DesktopNavigationReadiness): Boolean =
        when (workflow) {
            DesktopWorkflow.Setup,
            DesktopWorkflow.SettingsHelp -> true
            DesktopWorkflow.RaceOps -> readiness.isSetupComplete
            DesktopWorkflow.ResultsExport -> readiness.hasRaceOpsData
        }

    fun isItemEnabled(item: DesktopNavItem, readiness: DesktopNavigationReadiness): Boolean {
        if (item.requiresEventFile && !readiness.hasEventFile) {
            return false
        }
        return when {
            item.id.startsWith("setup.categories") -> readiness.hasControls
            item.id.startsWith("setup.competitors") -> readiness.hasControls && readiness.hasCategories
            item.id.startsWith("setup.start-list") -> readiness.hasControls &&
                readiness.hasCategories &&
                readiness.hasAssignedCompetitors
            item.workflow == DesktopWorkflow.RaceOps -> readiness.isSetupComplete || !item.requiresEventFile
            item.workflow == DesktopWorkflow.ResultsExport -> readiness.hasRaceOpsData
            else -> true
        }
    }

    fun shouldGuardUnsavedNewEventDraft(
        currentState: DesktopNavState,
        nextState: DesktopNavState,
        hasEditedUnsavedNewEventDraft: Boolean
    ): Boolean =
        hasEditedUnsavedNewEventDraft &&
            currentState.selectedItemId == "setup.event-file.new" &&
            currentState != nextState

    fun shouldGuardDirtySubmenuExit(
        currentState: DesktopNavState,
        nextState: DesktopNavState,
        hasUnsavedChanges: Boolean
    ): Boolean =
        hasUnsavedChanges &&
            currentState.submenuStack.isNotEmpty() &&
            (
                currentState.workflow != nextState.workflow ||
                    nextState.submenuStack.size < currentState.submenuStack.size
            )

    fun isLeavingNewEventFilePage(currentState: DesktopNavState, nextState: DesktopNavState): Boolean =
        currentState.selectedItemId == "setup.event-file.new" && currentState != nextState

    fun isLeavingCategoriesMenu(currentState: DesktopNavState, nextState: DesktopNavState): Boolean =
        currentState.submenuStack.contains("setup.categories") &&
            !nextState.submenuStack.contains("setup.categories")

    fun breadcrumb(state: DesktopNavState): String {
        val labels = mutableListOf(state.workflow.label)
        var items = roots.getValue(state.workflow)
        state.submenuStack.forEach { id ->
            val item = items.firstOrNull { it.id == id } ?: return labels.joinToString(" > ")
            labels += item.label
            items = item.children
        }
        currentItems(state).firstOrNull { it.id == state.selectedItemId }?.let { selected ->
            if (selected.label !in labels) {
                labels += selected.label
            }
        }
        return labels.joinToString(" > ")
    }

    fun defaultSection(workflow: DesktopWorkflow): DesktopSection =
        when (workflow) {
            DesktopWorkflow.Setup,
            DesktopWorkflow.RaceOps,
            DesktopWorkflow.ResultsExport,
            DesktopWorkflow.SettingsHelp -> DesktopSection.WorkflowHome
        }

    fun defaultItemId(workflow: DesktopWorkflow): String =
        when (workflow) {
            DesktopWorkflow.Setup -> "setup.home"
            DesktopWorkflow.RaceOps -> "race.home"
            DesktopWorkflow.ResultsExport -> "results.home"
            DesktopWorkflow.SettingsHelp -> "settings.home"
        }

    fun selectedLabel(state: DesktopNavState): String =
        if (state.selectedSection == DesktopSection.WorkflowHome) {
            state.workflow.label
        } else {
            allItems(state.workflow).firstOrNull { it.id == state.selectedItemId }?.label
                ?: state.selectedSection.label
        }

    private fun allItems(workflow: DesktopWorkflow): List<DesktopNavItem> {
        fun flatten(items: List<DesktopNavItem>): List<DesktopNavItem> =
            items + items.flatMap { flatten(it.children) }
        return flatten(roots.getValue(workflow))
    }

    private fun eventFileActions(workflow: DesktopWorkflow): List<DesktopNavItem> =
        listOf(
            action(
                "setup.event-file.new",
                "New Event File",
                workflow,
                DesktopNavAction.NewEventFile,
                requiresEventFile = false,
                section = DesktopSection.Races
            ),
            action(
                "setup.event-file.open",
                "Open...",
                workflow,
                DesktopNavAction.OpenEventFile,
                requiresEventFile = false
            ),
            action(
                "setup.event-file.import-android",
                "Import Android Event File...",
                workflow,
                DesktopNavAction.ImportAndroidRaceBackup,
                requiresEventFile = false,
                section = DesktopSection.Races
            ),
            action(
                "setup.event-file.import-eventreg",
                "Import EventReg Website...",
                workflow,
                DesktopNavAction.ImportEventRegWebsite,
                requiresEventFile = false,
                section = DesktopSection.Races
            ),
            action(
                "setup.event-file.export-android",
                "Export Android Event File...",
                workflow,
                DesktopNavAction.ExportAndroidRaceBackupJson,
                section = DesktopSection.EventFile
            ),
            item(
                "setup.event-file.diagnostics",
                "Settings",
                workflow,
                DesktopSection.Settings
            ),
            action("setup.event-file.save", "Save Event", workflow, DesktopNavAction.SaveEventFile)
        )

    private fun item(
        id: String,
        label: String,
        workflow: DesktopWorkflow,
        section: DesktopSection,
        requiresEventFile: Boolean = true
    ): DesktopNavItem =
        DesktopNavItem(
            id = id,
            label = label,
            workflow = workflow,
            section = section,
            requiresEventFile = requiresEventFile
        )

    private fun action(
        id: String,
        label: String,
        workflow: DesktopWorkflow,
        action: DesktopNavAction,
        requiresEventFile: Boolean = true,
        section: DesktopSection? = null
    ): DesktopNavItem =
        DesktopNavItem(
            id = id,
            label = label,
            workflow = workflow,
            section = section,
            action = action,
            requiresEventFile = requiresEventFile
        )

    private fun group(
        id: String,
        label: String,
        workflow: DesktopWorkflow,
        children: List<DesktopNavItem>,
        section: DesktopSection? = null,
        requiresEventFile: Boolean = true
    ): DesktopNavItem =
        DesktopNavItem(
            id = id,
            label = label,
            workflow = workflow,
            section = section,
            requiresEventFile = requiresEventFile,
            children = children
        )

    private fun DesktopNavState.returnToCurrentMenu(item: DesktopNavItem): DesktopNavState {
        val currentMenuId = submenuStack.lastOrNull() ?: return this
        val currentMenu = itemById(workflow, currentMenuId)
        return copy(
            selectedSection = item.section ?: currentMenu?.section ?: selectedSection,
            selectedItemId = currentMenuId
        )
    }
}
