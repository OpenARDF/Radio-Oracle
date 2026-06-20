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
    ImportEventRegWebsite,
    ImportEventRegCompetitorsCsv,
    SaveEventFile,
    CloseEventFile,
    ImportCategoriesCsv,
    ImportCourseKmlKmz,
    ImportCourseGpx,
    ImportControlsKmlKmz,
    ImportControlsGpx,
    ImportControlsCsv,
    ImportCompetitorsCsv,
    ImportStartsCsv,
    ImportDemFile,
    DeleteAllControls,
    DeleteAllCategoryAssignedControls,
    DeleteAllCategories,
    DeleteAllCompetitors,
    ExportEventFileCopy,
    SendEventFileToAndroid,
    ReceiveFileFromAndroid,
    ExportCategoriesCsv,
    ExportControlsCsv,
    ExportCourseKmlKmz,
    ExportCourseGpx,
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
    GeneratePublicResultsSite,
    PublishPublicResultsSite,
    OpenPublicResultsSitePreview,
    StopPublicResultsSitePreview,
    ExportArdfJson,
    ExportAndroidRaceBackupJson,
    ExportLiveResultsJson,
    ExportFinalResultsJson,
    ExportIofStartListXml,
    ExportIofResultListXml,
    DownloadSiCard,
    StartContinuousSiReadout,
    StopContinuousSiReadout,
    OpenLocalResultsWebPage,
    PreviewLocalResultsWebPage,
    StartLocalResultsWebServer,
    StopLocalResultsWebServer,
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
    val hasRaceOpsData: Boolean = false,
    val competitorCount: Int = 0,
    val unassignedCompetitorCount: Int = 0,
    val unscheduledCompetitorCount: Int = 0
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
            val unassignedCompetitorCount = competitors.count { competitor ->
                competitor.categoryId == null || !categoryIds.contains(competitor.categoryId)
            }
            val unscheduledCompetitorCount = competitors.count { it.drawnStartTimeSeconds == null }
            val hasAssignedCompetitors = hasCompetitors &&
                unassignedCompetitorCount == 0

            return DesktopNavigationReadiness(
                hasEventFile = true,
                hasControls = raceData.controls.isNotEmpty(),
                hasCategories = raceData.categories.isNotEmpty(),
                hasCompetitors = hasCompetitors,
                hasAssignedCompetitors = hasAssignedCompetitors,
                hasStartList = hasCompetitors && unscheduledCompetitorCount == 0,
                hasRaceOpsData = raceData.competitorData.any { it.readoutData != null } ||
                    raceData.unmatchedReadoutData.isNotEmpty(),
                competitorCount = competitors.size,
                unassignedCompetitorCount = unassignedCompetitorCount,
                unscheduledCompetitorCount = unscheduledCompetitorCount
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
        if (nextWorkflow == DesktopWorkflow.ResultsExport) {
            copy(
                workflow = nextWorkflow,
                submenuStack = emptyList(),
                selectedSection = DesktopSection.Results,
                selectedItemId = "results.results"
            )
        } else {
            copy(
                workflow = nextWorkflow,
                submenuStack = emptyList(),
                selectedSection = DesktopNavigation.defaultSection(nextWorkflow),
                selectedItemId = DesktopNavigation.defaultItemId(nextWorkflow)
            )
        }

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
            return if (selectedItemId == DesktopNavigation.defaultItemId(workflow)) {
                this
            } else {
                copy(
                    selectedSection = DesktopNavigation.defaultSection(workflow),
                    selectedItemId = DesktopNavigation.defaultItemId(workflow)
                )
            }
        }
        val activeMenuItems = DesktopNavigation.menuItemsForStack(workflow, submenuStack)
        if (
            selectedItemId != submenuStack.last() &&
            activeMenuItems.any { it.id == selectedItemId }
        ) {
            return DesktopNavigation.returnToParentMenu(this)
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
                        group(
                            "setup.controls.elevation-cache",
                            "Elevation Data",
                            workflow,
                            listOf(
                                item(
                                    "setup.controls.elevation-cache.import",
                                    "Import Elevation Data",
                                    workflow,
                                    DesktopSection.ElevationCacheImport
                                ),
                                action(
                                    "setup.controls.elevation-cache.import-dem",
                                    "Import DEM File...",
                                    workflow,
                                    DesktopNavAction.ImportDemFile
                                )
                            ),
                            DesktopSection.ElevationCache
                        ),
                        group(
                            "setup.controls.course-analysis",
                            "Course Analyzer",
                            workflow,
                            listOf(
                                action(
                                    "setup.controls.course-analysis.import-kml-kmz",
                                    "Import Course KML/KMZ...",
                                    workflow,
                                    DesktopNavAction.ImportCourseKmlKmz
                                ),
                                action(
                                    "setup.controls.course-analysis.import-gpx",
                                    "Import Course GPX...",
                                    workflow,
                                    DesktopNavAction.ImportCourseGpx
                                )
                            ),
                            DesktopSection.CourseAnalysis
                        ),
                        group(
                            "setup.controls.import-export",
                            "Import/Export",
                            workflow,
                            listOf(
                                action(
                                    "setup.controls.import-controls",
                                    "Import Controls CSV...",
                                    workflow,
                                    DesktopNavAction.ImportControlsCsv
                                ),
                                action(
                                    "setup.controls.export-controls",
                                    "Export Controls CSV...",
                                    workflow,
                                    DesktopNavAction.ExportControlsCsv
                                ),
                                action(
                                    "setup.controls.import-kml-kmz",
                                    "Import Controls KML/KMZ...",
                                    workflow,
                                    DesktopNavAction.ImportControlsKmlKmz
                                ),
                                action(
                                    "setup.controls.export-kml-kmz",
                                    "Export Controls KML/KMZ...",
                                    workflow,
                                    DesktopNavAction.ExportCourseKmlKmz
                                ),
                                action(
                                    "setup.controls.import-gpx",
                                    "Import Controls GPX...",
                                    workflow,
                                    DesktopNavAction.ImportControlsGpx
                                ),
                                action(
                                    "setup.controls.export-gpx",
                                    "Export Controls GPX...",
                                    workflow,
                                    DesktopNavAction.ExportCourseGpx
                                )
                            ),
                            DesktopSection.ControlsImportExport
                        ),
                        action(
                            "setup.controls.delete-all",
                            "Delete All Controls...",
                            workflow,
                            DesktopNavAction.DeleteAllControls
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
                            "Course Order",
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
                        ),
                        action(
                            "setup.categories.delete-all-assigned-controls",
                            "Delete All Control Assignments...",
                            workflow,
                            DesktopNavAction.DeleteAllCategoryAssignedControls
                        ),
                        action(
                            "setup.categories.delete-all-categories",
                            "Delete All Categories...",
                            workflow,
                            DesktopNavAction.DeleteAllCategories
                        ),
                    ),
                    DesktopSection.Categories
                ),
                group(
                    "setup.competitors",
                    "Competitors",
                    workflow,
                    listOf(
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
                        ),
                        action(
                            "setup.competitors.delete-all",
                            "Delete All Competitors...",
                            workflow,
                            DesktopNavAction.DeleteAllCompetitors
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
                item("race.finish-tickets", "Finish Tickets", workflow, DesktopSection.Readouts)
            )
            DesktopWorkflow.ResultsExport -> listOf(
                group(
                    "results.live",
                    "Live Results",
                    workflow,
                    listOf(
                        group(
                            "results.local-web-server",
                            "Local Web Server",
                            workflow,
                            listOf(
                                action(
                                    "results.open-local-web-page",
                                    "Open Web Page",
                                    workflow,
                                    DesktopNavAction.OpenLocalResultsWebPage,
                                    section = DesktopSection.LocalResultsWebServer
                                ),
                                action(
                                    "results.preview-local-web-page",
                                    "Preview Web Page",
                                    workflow,
                                    DesktopNavAction.PreviewLocalResultsWebPage,
                                    section = DesktopSection.LocalResultsWebServer
                                ),
                                action(
                                    "results.start-local-web-server",
                                    "Start Web Server",
                                    workflow,
                                    DesktopNavAction.StartLocalResultsWebServer,
                                    section = DesktopSection.LocalResultsWebServer
                                ),
                                action(
                                    "results.stop-local-web-server",
                                    "Stop Web Server",
                                    workflow,
                                    DesktopNavAction.StopLocalResultsWebServer,
                                    section = DesktopSection.LocalResultsWebServer
                                )
                            ),
                            DesktopSection.LocalResultsWebServer
                        ),
                        group(
                            "results.robis",
                            "ROBIS",
                            workflow,
                            listOf(
                                action(
                                    "results.send-robis",
                                    "Send ROBIS",
                                    workflow,
                                    DesktopNavAction.SendRobis,
                                    section = DesktopSection.RobisLiveResults
                                )
                            ),
                            DesktopSection.RobisLiveResults
                        )
                    ),
                    DesktopSection.LiveResultsOverview
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
                            "results.exports.cloudflare-website",
                            "Cloudflare Website",
                            workflow,
                            listOf(
                                action(
                                    "results.generate-public-site",
                                    "Generate Public Results Site...",
                                    workflow,
                                    DesktopNavAction.GeneratePublicResultsSite,
                                    section = DesktopSection.PublicResultsSite
                                ),
                                action(
                                    "results.publish-public-site",
                                    "Publish Public Results Site",
                                    workflow,
                                    DesktopNavAction.PublishPublicResultsSite,
                                    section = DesktopSection.PublicResultsSite
                                ),
                                group(
                                    "results.public-site-preview",
                                    "Public Site Preview",
                                    workflow,
                                    listOf(
                                        action(
                                            "results.open-public-site-preview",
                                            "Open Public Site Preview",
                                            workflow,
                                            DesktopNavAction.OpenPublicResultsSitePreview,
                                            section = DesktopSection.PublicResultsSite
                                        ),
                                        action(
                                            "results.stop-public-site-preview",
                                            "Stop Public Site Preview",
                                            workflow,
                                            DesktopNavAction.StopPublicResultsSitePreview,
                                            section = DesktopSection.PublicResultsSite
                                        )
                                    ),
                                    DesktopSection.PublicResultsSite
                                ),
                                item(
                                    "results.view-public-results",
                                    "View Public Results",
                                    workflow,
                                    DesktopSection.PublicResultsLink
                                ),
                                item(
                                    "results.cloudflare-settings",
                                    "Cloudflare Settings",
                                    workflow,
                                    DesktopSection.Settings,
                                    requiresEventFile = false
                                )
                            ),
                            DesktopSection.PublicResultsSite
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
                            "Logs...",
                            workflow,
                            DesktopNavAction.ShowDebugLogHelp,
                            requiresEventFile = false,
                            section = DesktopSection.Settings
                        ),
                        action(
                            "settings.about",
                            "About Radio-Oracle...",
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

    fun currentItems(state: DesktopNavState): List<DesktopNavItem> {
        val activeMenuItems = menuItemsForStack(state.workflow, state.submenuStack)
        val selectedLeaf = activeMenuItems.firstOrNull { it.id == state.selectedItemId && it.children.isEmpty() }
        return selectedLeaf?.children ?: activeMenuItems
    }

    fun canGoBack(state: DesktopNavState): Boolean =
        state.submenuStack.isNotEmpty() || state.selectedItemId != defaultItemId(state.workflow)

    fun showsMenuIndicator(item: DesktopNavItem): Boolean =
        item.action == null

    fun menuItemsForStack(workflow: DesktopWorkflow, submenuStack: List<String>): List<DesktopNavItem> =
        submenuStack.fold(roots.getValue(workflow)) { items, id ->
            items.firstOrNull { it.id == id }?.children ?: items
        }

    fun selectItem(state: DesktopNavState, item: DesktopNavItem): DesktopNavSelection =
        when {
            item.children.isNotEmpty() -> DesktopNavSelection(state.enter(item))
            item.action != null -> DesktopNavSelection(state.enter(item), item.action)
            item.section != null -> DesktopNavSelection(state.enter(item))
            else -> DesktopNavSelection(state)
        }

    fun shouldReturnToParentMenuAfterAction(action: DesktopNavAction): Boolean =
        action != DesktopNavAction.NewEventFile

    fun returnToParentMenuAfterAction(state: DesktopNavState, action: DesktopNavAction): DesktopNavState =
        if (shouldReturnToParentMenuAfterAction(action)) {
            state.withParentMenuSelected()
        } else {
            state
        }

    fun returnToParentMenu(state: DesktopNavState): DesktopNavState =
        state.withParentMenuSelected()

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

    fun disabledWorkflowReason(workflow: DesktopWorkflow, readiness: DesktopNavigationReadiness): String? {
        if (isWorkflowEnabled(workflow, readiness)) {
            return null
        }
        return when (workflow) {
            DesktopWorkflow.RaceOps ->
                setupIncompleteReason(readiness, "Race Ops")
            DesktopWorkflow.ResultsExport ->
                if (!readiness.hasRaceOpsData) {
                    "Results need at least one SI-card readout or unmatched readout."
                } else {
                    null
                }
            DesktopWorkflow.Setup,
            DesktopWorkflow.SettingsHelp -> null
        }
    }

    fun canLongClickOverrideDisabledWorkflow(
        workflow: DesktopWorkflow,
        readiness: DesktopNavigationReadiness
    ): Boolean =
        !isWorkflowEnabled(workflow, readiness)

    fun disabledWorkflowReasonWithOverrideHint(
        workflow: DesktopWorkflow,
        readiness: DesktopNavigationReadiness
    ): String? {
        val reason = disabledWorkflowReason(workflow, readiness) ?: return null
        return if (canLongClickOverrideDisabledWorkflow(workflow, readiness)) {
            "$reason Long-click for 3 seconds to explore this workflow."
        } else {
            reason
        }
    }

    fun disabledItemReason(item: DesktopNavItem, readiness: DesktopNavigationReadiness): String? {
        if (isItemEnabled(item, readiness)) {
            return null
        }
        if (item.requiresEventFile && !readiness.hasEventFile) {
            return "Open or create an Event File first."
        }
        return when {
            item.id.startsWith("setup.categories") ->
                "Enter controls before working with categories."
            item.id.startsWith("setup.competitors") ->
                setupCompetitorsReason(readiness)
            item.id.startsWith("setup.start-list") ->
                startListReason(readiness)
            item.workflow == DesktopWorkflow.RaceOps ->
                setupIncompleteReason(readiness, "Race Ops")
            item.workflow == DesktopWorkflow.ResultsExport ->
                "Results need at least one SI-card readout or unmatched readout."
            else -> null
        }
    }

    fun canLongClickOverrideDisabledMenu(item: DesktopNavItem, readiness: DesktopNavigationReadiness): Boolean =
        item.action == null && !isItemEnabled(item, readiness)

    fun disabledItemReasonWithMenuOverrideHint(
        item: DesktopNavItem,
        readiness: DesktopNavigationReadiness
    ): String? {
        val reason = disabledItemReason(item, readiness) ?: return null
        return if (canLongClickOverrideDisabledMenu(item, readiness)) {
            "$reason Long-click for 3 seconds to explore this menu."
        } else {
            reason
        }
    }

    fun primaryDisabledSummary(readiness: DesktopNavigationReadiness): String? =
        disabledWorkflowReason(DesktopWorkflow.RaceOps, readiness)
            ?: disabledWorkflowReason(DesktopWorkflow.ResultsExport, readiness)

    private fun setupIncompleteReason(
        readiness: DesktopNavigationReadiness,
        target: String
    ): String =
        "$target disabled: ${setupRequirementReason(readiness)}"

    private fun setupRequirementReason(readiness: DesktopNavigationReadiness): String =
        when {
            !readiness.hasEventFile -> "open or create an Event File first."
            !readiness.hasControls -> "enter controls first."
            !readiness.hasCategories -> "enter categories first."
            !readiness.hasCompetitors -> "enter competitors first."
            !readiness.hasAssignedCompetitors -> {
                val count = readiness.unassignedCompetitorCount
                if (count > 0) {
                    "$count competitor${if (count == 1) " is" else "s are"} not assigned to a category."
                } else {
                    "assign every competitor to a category."
                }
            }
            !readiness.hasStartList -> {
                val count = readiness.unscheduledCompetitorCount
                if (count > 0) {
                    "generate a Start List; $count competitor${if (count == 1) " has" else "s have"} no drawn start time."
                } else {
                    "generate a Start List first."
                }
            }
            else -> "complete setup first."
        }

    private fun setupCompetitorsReason(readiness: DesktopNavigationReadiness): String =
        when {
            !readiness.hasControls -> "Enter controls before adding competitors."
            !readiness.hasCategories -> "Enter categories before adding competitors."
            else -> "Competitors are not available yet."
        }

    private fun startListReason(readiness: DesktopNavigationReadiness): String =
        when {
            !readiness.hasControls -> "Enter controls before drawing a Start List."
            !readiness.hasCategories -> "Enter categories before drawing a Start List."
            !readiness.hasCompetitors -> "Enter competitors before drawing a Start List."
            !readiness.hasAssignedCompetitors -> {
                val count = readiness.unassignedCompetitorCount
                if (count > 0) {
                    "Assign $count competitor${if (count == 1) "" else "s"} to categories before drawing a Start List."
                } else {
                    "Assign every competitor to a category before drawing a Start List."
                }
            }
            else -> "Start List is not available yet."
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
            currentState != nextState &&
            currentState.submenuStack.isNotEmpty() &&
            (
                currentState.workflow != nextState.workflow ||
                    nextState.submenuStack.size < currentState.submenuStack.size ||
                    currentState.selectedItemId != currentState.submenuStack.last()
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
        menuItemsForStack(state.workflow, state.submenuStack)
            .firstOrNull { it.id == state.selectedItemId }
            ?.let { selected ->
            if (selected.label !in labels) {
                labels += selected.label
            }
        } ?: run {
            if (
                state.selectedItemId == "results.results" &&
                state.selectedSection.label !in labels
            ) {
                labels += state.selectedSection.label
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
        allItems(state.workflow).firstOrNull { it.id == state.selectedItemId }?.label
            ?: if (state.selectedSection == DesktopSection.WorkflowHome) {
                state.workflow.label
            } else {
                state.selectedSection.label
            }

    fun selectedDescription(state: DesktopNavState): String {
        val item = allItems(state.workflow).firstOrNull { it.id == state.selectedItemId }
        return itemDescriptions[state.selectedItemId]
            ?: item?.let { itemDescriptions[it.id] }
            ?: workflowDescriptions.getValue(state.workflow)
    }

    private fun allItems(workflow: DesktopWorkflow): List<DesktopNavItem> {
        fun flatten(items: List<DesktopNavItem>): List<DesktopNavItem> =
            items + items.flatMap { flatten(it.children) }
        return flatten(roots.getValue(workflow))
    }

    private val workflowDescriptions: Map<DesktopWorkflow, String> = mapOf(
        DesktopWorkflow.Setup to
            "Use Setup to create or open an Event File, define controls and courses, maintain categories and competitors, and draw or import the start list before competition operations begin.",
        DesktopWorkflow.RaceOps to
            "Use Race Ops during competition to download SI cards, monitor active competitors, review unmatched readouts, and print finish tickets after setup data is complete.",
        DesktopWorkflow.ResultsExport to
            "Use Results/File Export after readouts are available to review scored results, run local or ROBIS live result publishing, and export final result files.",
        DesktopWorkflow.SettingsHelp to
            "Use Settings for app preferences, hardware-related options, logs, beta-scope information, and about information that is not tied to one event workflow."
    )

    private val itemDescriptions: Map<String, String> = mapOf(
        "setup.event-file" to
            "Use Event File to create, open, import, save, close, and inspect event files, including settings and readiness tools that affect the whole event.",
        "setup.controls" to
            "Use Controls to manually enter controls, import controls and courses from supported file types, analyze courses with appropriate elevation data applied, and import elevation DEMs.",
        "setup.controls.define" to
            "Use Define Controls to manually enter controls, review imported controls, and edit public labels, SI codes, roles, and control metadata used by courses and readouts.",
        "setup.controls.elevation-cache" to
            "Use Elevation Data to inspect cached venue elevation files used for stored course routes, climb estimates, and course-analysis calculations.",
        "setup.controls.elevation-cache.import" to
            "Use Import Elevation Data to create a venue elevation cache from online elevation services, a local raster, or a LAS/LAZ point cloud.",
        "setup.controls.elevation-cache.import-dem" to
            "Use Import DEM File to add one or more existing Radio-Oracle DEM cache JSON files, including cache files packaged inside a ZIP archive.",
        "setup.controls.course-analysis" to
            "Use Course Analyzer to inspect stored course routes, ideal routes, climb, distance, time estimates, and classic wait-slot behavior.",
        "setup.controls.course-analysis.import-kml-kmz" to
            "Use Import Course KML/KMZ to bring in control placemarks and required category route lines for course analysis and category course assignments.",
        "setup.controls.course-analysis.import-gpx" to
            "Use Import Course GPX to bring in control waypoints and required category routes or tracks for course analysis and category course assignments.",
        "setup.controls.import-export" to
            "Use Import/Export to synchronize control CSV files and protected controls/route KML/KMZ or GPX files with the current Event File.",
        "setup.controls.import-controls" to
            "Use Import Controls CSV to review and apply additions or updates to the event control catalog.",
        "setup.controls.import-kml-kmz" to
            "Use Import Controls KML/KMZ to import control locations and category route geometry from mapping files.",
        "setup.controls.import-gpx" to
            "Use Import Controls GPX to import control waypoints and category route geometry from GPX files.",
        "setup.controls.export-controls" to
            "Use Export Controls CSV to write the current control catalog for review, backup, or editing outside the app.",
        "setup.controls.export-kml-kmz" to
            "Use Export Controls KML/KMZ to write protected control and route geometry inside a password-locked ZIP file.",
        "setup.controls.export-gpx" to
            "Use Export Controls GPX to write protected control and route geometry as standard GPX inside a password-locked ZIP file.",
        "setup.controls.delete-all" to
            "Use Delete All Controls to remove every control and clear category course assignments, length, climb, and protected course data that depends on them.",
        "setup.categories" to
            "Use Categories to create, import, export, and maintain competition classes, assigned controls, course metadata, and protected course order data.",
        "setup.categories.protected-course-order" to
            "Use Course Order to unlock and review protected ideal routes and course-location data that may affect scoring and analysis.",
        "setup.categories.import" to
            "Use Import Categories CSV to review and apply late category additions or corrections without replacing unrelated event data.",
        "setup.categories.delete-all-assigned-controls" to
            "Use Delete All Control Assignments to clear every category course, length, climb, and protected course field while keeping category names and competitors.",
        "setup.categories.delete-all-categories" to
            "Use Delete All Categories to remove every category name and course assignment while keeping competitor records uncategorized.",
        "setup.categories.export" to
            "Use Export Categories CSV to write the current category list and course fields for review, backup, or external editing.",
        "setup.competitors" to
            "Use Competitors to add, import, export, edit, and assign competitors to categories before drawing starts or running race operations.",
        "setup.competitors.import" to
            "Use Import Competitors CSV to append or update competitor lists while preserving existing event setup data.",
        "setup.competitors.import-eventreg" to
            "Use Import EventReg Website to bring competitor data from EventReg exports into the current Event File.",
        "setup.competitors.export" to
            "Use Export Competitors CSV to write the current competitor list for review, backup, or external editing.",
        "setup.competitors.delete-all" to
            "Use Delete All Competitors to remove every competitor while preserving downloaded readouts as unmatched records for review.",
        "setup.start-list" to
            "Use Start List to import, draw, balance, review, and export competitor start times once categories and assignments are ready.",
        "setup.start-list.view" to
            "Use Start List to review drawn start times, start-list settings, and scheduling status for competitors.",
        "setup.start-list.import" to
            "Use Import Starts CSV to apply externally prepared start times to the competitors in this Event File.",
        "setup.start-list.exports" to
            "Use Exports to write start lists in formats useful for starts, category checks, ROBIS, and IOF-compatible workflows.",
        "setup.start-list.export-csv" to
            "Use Export Starts CSV to write the complete start list as a general-purpose CSV file.",
        "setup.start-list.export-category" to
            "Use Export Starts by Category CSV to write start lists grouped by competition category.",
        "setup.start-list.export-minute" to
            "Use Export Starts by Minute CSV to write a minute-by-minute start-board view.",
        "setup.start-list.export-robis" to
            "Use Export ROBIS Start List CSV to generate a start-list file for ROBIS workflows.",
        "setup.start-list.export-iof" to
            "Use Export IOF Start List XML to write an IOF-compatible start-list file.",
        "race.readouts" to
            "Use Readouts to download, review, match, edit, remove, print, and manually add SI-card readouts during race operations.",
        "race.si-readout" to
            "Use SI Readout to download one SI card or run continuous card downloads while competitors finish.",
        "race.download-si" to
            "Use Download SI Card to read one SI card from an attached READOUT or SI MASTER station.",
        "race.start-continuous" to
            "Use Start Continuous SI to keep the reader waiting for successive cards during finish operations.",
        "race.stop-continuous" to
            "Use Stop Continuous SI to end continuous readout after the current card wait finishes.",
        "race.in-forest" to
            "Use In Forest to monitor started competitors who do not yet have finish readouts.",
        "race.unmatched" to
            "Use Unmatched Readouts to review SI-card readouts that are not yet assigned to competitors.",
        "race.finish-tickets" to
            "Use Finish Tickets to preview and print competitor finish tickets from available readout and result data.",
        "results.results" to
            "Use Results to review scored competitor results, adjust manual readout status, and inspect result details after readouts are matched.",
        "results.live" to
            "Live Results has two separate and independent options. Local Web Server serves a public results web page from this computer for local preview or devices on the same Wi-Fi network. ROBIS sends eligible matched live results to the configured external ROBIS endpoint.",
        "results.local-web-server" to
            "Local Web Server serves the public results web page from this computer. Use Open Web Page to start or reopen the current page on this Mac. Preview Web Page regenerates and opens a local-only preview on this computer. Start Web Server restarts the server on a LAN address for nearby devices. Stop Web Server shuts it down.",
        "results.open-local-web-page" to
            "Use Open Web Page to start or reopen the local results web page on this computer.",
        "results.preview-local-web-page" to
            "Use Preview Web Page to regenerate the public results web page immediately and open it on this computer.",
        "results.start-local-web-server" to
            "Use Start Web Server to serve the public results web page to devices on the same Wi-Fi network.",
        "results.stop-local-web-server" to
            "Use Stop Web Server to shut down the local results web server.",
        "results.robis" to
            "Use ROBIS to send eligible matched live results to the configured ROBIS endpoint, either manually or with background sending enabled.",
        "results.send-robis" to
            "Use Send ROBIS to send unsent matched live results to the configured ROBIS endpoint.",
        "results.exports" to
            "Use Exports to write result, readout, event-copy, JSON, XML, and ARDF-compatible files after race data is available.",
        "results.exports.result-files" to
            "Use Result Files to export scored results and readouts in CSV, TXT, HTML, and ARDFEvent-compatible formats.",
        "results.exports.cloudflare-website" to
            "Use Cloudflare Website to generate the public results site, preview the event folder locally, publish the generated site to Cloudflare Pages, and open the Cloudflare settings used by publishing.",
        "results.export-csv" to
            "Use Export Results CSV to write scored results as a spreadsheet-friendly file.",
        "results.export-ardfevent" to
            "Use Export ARDFEvent Results CSV to write results for ARDFEvent-compatible consumers.",
        "results.export-text" to
            "Use Export Results TXT to write a plain-text results report.",
        "results.export-html" to
            "Use Export Results HTML to write a browser-readable results report.",
        "results.generate-public-site" to
            "Use Generate Public Results Site to write a Cloudflare Pages-ready static site folder.",
        "results.publish-public-site" to
            "Use Publish Public Results Site to deploy the generated public results site to Cloudflare Pages.",
        "results.view-public-results" to
            "Use View Public Results after publishing to show the public event link and QR code for competitors and spectators.",
        "results.public-site-preview" to
            "Use Public Site Preview to open or stop the local preview of the generated public results site.",
        "results.open-public-site-preview" to
            "Use Open Public Site Preview to start or reopen the generated public results site in your browser.",
        "results.stop-public-site-preview" to
            "Use Stop Public Site Preview to shut down the generated public results site preview.",
        "results.cloudflare-settings" to
            "Use Cloudflare Settings to enter the Pages project name, branch, account ID, and API token used when publishing the public results site.",
        "results.export-readouts" to
            "Use Export Readouts CSV to write downloaded and unmatched readout records for review or backup.",
        "results.exports-json-xml" to
            "Use JSON/XML to export live/final result payloads and standards-oriented result files.",
        "results.export-live-json" to
            "Use Export Live Results JSON to write the current live-results payload.",
        "results.export-final-json" to
            "Use Export Final Results JSON to write a final-results JSON payload.",
        "results.export-iof" to
            "Use Export IOF Result List XML to write an IOF-compatible result-list file.",
        "results.export-ardf-json" to
            "Use Export ARDF JSON to write ARDF-oriented event and result data.",
        "results.export-copy" to
            "Use Export Event File Copy to save a copy of the complete Event File without changing the current working file.",
        "settings.app" to
            "Use App Settings to review desktop app settings, readiness information, and event-level support options.",
        "settings.hardware" to
            "Use Hardware Preferences to review printer and hardware-related desktop settings.",
        "settings.help" to
            "Use Help to view beta-scope notes, desktop logs, and application information.",
        "settings.beta-scope" to
            "Use Beta Scope to review what this desktop beta is intended to support and where caution is still needed.",
        "settings.logs" to
            "Use Logs to find the desktop debug-log location and related diagnostic information.",
        "settings.about" to
            "Use About Radio-Oracle to view the app version, project identity, and maintainer information.",
        "setup.event-file.new" to
            "Use New Event File to create a fresh event setup draft.",
        "setup.event-file.open" to
            "Use Load Event File to open a desktop Event File or import an Android Event File by file extension.",
        "setup.event-file.import-eventreg" to
            "Use Import EventReg Website to create event files from EventReg website exports.",
        "setup.event-file.android" to
            "Use Android to share Event Files with Android devices or save an Android-compatible Event File.",
        "setup.event-file.export-android" to
            "Use Save Android Event File to write a backup JSON file for Android compatibility.",
        "setup.event-file.send-android" to
            "Use Send Event to Android to share the saved Event File over local Wi-Fi.",
        "setup.event-file.receive-android" to
            "Use Receive File from Android to accept an Event File or supporting file over local Wi-Fi.",
        "setup.event-file.settings" to
            "Use Settings to adjust event-related readout, live result, display, app, and readiness options.",
        "setup.event-file.si-settings" to
            "Use SI Readout Settings to configure SI-card download behavior used during Race Ops.",
        "setup.event-file.live-settings" to
            "Use Live Results to choose between the local web server and ROBIS live-result workflows.",
        "setup.event-file.display-settings" to
            "Use Display Settings to configure readout and result display preferences.",
        "setup.event-file.app-settings" to
            "Use App Settings to review app-level settings, hardware status, and event password options.",
        "setup.event-file.diagnostics" to
            "Use Readiness to inspect event consistency, recent imports, generated test data tools, and diagnostics.",
        "setup.event-file.save" to
            "Use Save Event to write the current Event File to its existing path.",
        "setup.event-file.close" to
            "Use Close Event File to close the active event after handling any unsaved changes."
    )

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
                "Load Event File...",
                workflow,
                DesktopNavAction.OpenEventFile,
                requiresEventFile = false
            ),
            action(
                "setup.event-file.import-eventreg",
                "Import EventReg Website...",
                workflow,
                DesktopNavAction.ImportEventRegWebsite,
                requiresEventFile = false,
                section = DesktopSection.Races
            ),
            group(
                "setup.event-file.android",
                "Android...",
                workflow,
                listOf(
                    action(
                        "setup.event-file.send-android",
                        "Send Event to Android",
                        workflow,
                        DesktopNavAction.SendEventFileToAndroid,
                        section = DesktopSection.EventFile
                    ),
                    action(
                        "setup.event-file.receive-android",
                        "Receive File from Android",
                        workflow,
                        DesktopNavAction.ReceiveFileFromAndroid,
                        requiresEventFile = false,
                        section = DesktopSection.Races
                    ),
                    action(
                        "setup.event-file.export-android",
                        "Save Android Event File...",
                        workflow,
                        DesktopNavAction.ExportAndroidRaceBackupJson,
                        section = DesktopSection.EventFile
                    )
                ),
                DesktopSection.EventFile,
                requiresEventFile = false
            ),
            group(
                "setup.event-file.settings",
                "Settings",
                workflow,
                listOf(
                    item(
                        "setup.event-file.si-settings",
                        "SI Readout Settings",
                        workflow,
                        DesktopSection.SiReadoutSettings,
                        requiresEventFile = false
                    ),
                    item(
                        "setup.event-file.live-settings",
                        "Live Results",
                        workflow,
                        DesktopSection.LiveResultsOverview,
                        requiresEventFile = false
                    ),
                    item(
                        "setup.event-file.display-settings",
                        "Display Settings",
                        workflow,
                        DesktopSection.DisplaySettings,
                        requiresEventFile = false
                    ),
                    item(
                        "setup.event-file.app-settings",
                        "App Settings",
                        workflow,
                        DesktopSection.Settings,
                        requiresEventFile = false
                    ),
                    item(
                        "setup.event-file.diagnostics",
                        "Readiness",
                        workflow,
                        DesktopSection.EventDiagnostics,
                        requiresEventFile = false
                    )
                ),
                DesktopSection.Settings,
                requiresEventFile = false
            ),
            action("setup.event-file.save", "Save Event", workflow, DesktopNavAction.SaveEventFile),
            action("setup.event-file.close", "Close Event File", workflow, DesktopNavAction.CloseEventFile)
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

    private fun DesktopNavState.withParentMenuSelected(): DesktopNavState {
        val currentMenuId = submenuStack.lastOrNull() ?: return this
        val currentMenu = itemById(workflow, currentMenuId)
        return copy(
            selectedSection = currentMenu?.section ?: selectedSection,
            selectedItemId = currentMenuId
        )
    }
}
