package org.openardf.radiooracle.desktop

private const val MaxSubmenuDepth = 2

enum class DesktopWorkflow(val label: String, val shortLabel: String) {
    Setup("Preparation/Setup", "Setup"),
    RaceOps("Race Operations", "Race Ops"),
    ResultsExport("Results/File Export", "Results"),
    SettingsHelp("Help/About/App Settings", "Settings")
}

enum class DesktopNavAction {
    NewEventFile,
    OpenEventFile,
    ImportAndroidRaceBackup,
    SaveEventFile,
    SaveEventFileAs,
    CloseEventFile,
    ImportCategoriesCsv,
    ImportCompetitorsCsv,
    ImportStartsCsv,
    ExportEventFileCopy,
    ExportCategoriesCsv,
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

data class DesktopNavItem(
    val id: String,
    val label: String,
    val workflow: DesktopWorkflow,
    val section: DesktopSection? = null,
    val action: DesktopNavAction? = null,
    val requiresEventFile: Boolean = false,
    val children: List<DesktopNavItem> = emptyList()
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
        val nextStack = submenuStack.dropLast(1)
        return if (nextStack.isEmpty()) {
            copy(
                submenuStack = emptyList(),
                selectedSection = DesktopNavigation.defaultSection(workflow),
                selectedItemId = DesktopNavigation.defaultItemId(workflow)
            )
        } else {
            copy(submenuStack = nextStack)
        }
    }
}

object DesktopNavigation {
    val roots: Map<DesktopWorkflow, List<DesktopNavItem>> =
        DesktopWorkflow.entries.associateWith(::rootItems)

    fun rootItems(workflow: DesktopWorkflow): List<DesktopNavItem> =
        when (workflow) {
            DesktopWorkflow.Setup -> listOf(
                group("setup.event-file", "Event File", workflow, eventFileActions(workflow), DesktopSection.EventFile),
                item("setup.race", "Race", workflow, DesktopSection.Races),
                item("setup.categories", "Categories", workflow, DesktopSection.Categories),
                item("setup.competitors", "Competitors", workflow, DesktopSection.Competitors),
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
                    )
                ),
                item("setup.aliases", "Aliases", workflow, DesktopSection.Aliases),
                group(
                    "setup.imports",
                    "Imports",
                    workflow,
                    listOf(
                        action("setup.import-categories", "Import Categories CSV...", workflow, DesktopNavAction.ImportCategoriesCsv),
                        action("setup.import-competitors", "Import Competitors CSV...", workflow, DesktopNavAction.ImportCompetitorsCsv)
                    )
                ),
                group(
                    "setup.exports",
                    "Setup Exports",
                    workflow,
                    listOf(
                        action("setup.export-categories", "Export Categories CSV...", workflow, DesktopNavAction.ExportCategoriesCsv),
                        action("setup.export-competitors", "Export Competitors CSV...", workflow, DesktopNavAction.ExportCompetitorsCsv)
                    )
                ),
                group(
                    "setup.utils",
                    "Utils",
                    workflow,
                    listOf(item("setup.utils.diagnostics", "Event File Diagnostics", workflow, DesktopSection.Settings))
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
                item("race.hardware", "Hardware Status", workflow, DesktopSection.Settings)
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
                        item("results.live-settings", "Live Result Settings", workflow, DesktopSection.Settings)
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
                                action("results.export-ardf-json", "Export ARDF JSON...", workflow, DesktopNavAction.ExportArdfJson),
                                action(
                                    "results.export-android-json",
                                    "Export Android Race Backup JSON...",
                                    workflow,
                                    DesktopNavAction.ExportAndroidRaceBackupJson
                                )
                            )
                        ),
                        action("results.export-copy", "Export Event File Copy...", workflow, DesktopNavAction.ExportEventFileCopy)
                    )
                )
            )
            DesktopWorkflow.SettingsHelp -> listOf(
                item("settings.app", "App Settings", workflow, DesktopSection.Settings),
                item("settings.hardware", "Hardware Preferences", workflow, DesktopSection.Settings),
                group(
                    "settings.help",
                    "Help",
                    workflow,
                    listOf(
                        item("settings.beta-scope", "Beta Scope", workflow, DesktopSection.Settings),
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
                    )
                )
            )
        }

    fun currentItems(state: DesktopNavState): List<DesktopNavItem> =
        state.submenuStack.fold(roots.getValue(state.workflow)) { items, id ->
            items.firstOrNull { it.id == id }?.children ?: items
        }

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
                requiresEventFile = false,
                section = DesktopSection.Races
            ),
            action(
                "setup.event-file.import-android",
                "Import Android Race Backup JSON...",
                workflow,
                DesktopNavAction.ImportAndroidRaceBackup,
                requiresEventFile = false,
                section = DesktopSection.Races
            ),
            action("setup.event-file.save", "Save", workflow, DesktopNavAction.SaveEventFile, section = DesktopSection.EventFile)
        )

    private fun item(id: String, label: String, workflow: DesktopWorkflow, section: DesktopSection): DesktopNavItem =
        DesktopNavItem(id = id, label = label, workflow = workflow, section = section)

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
        section: DesktopSection? = null
    ): DesktopNavItem =
        DesktopNavItem(id = id, label = label, workflow = workflow, section = section, children = children)
}
