package org.openardf.radiooracle.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.openardf.radiooracle.shared.event.EventAliasDetails
import org.openardf.radiooracle.shared.event.EventCategoryDetails
import org.openardf.radiooracle.shared.event.EventCompetitorDetails
import org.openardf.radiooracle.shared.event.EventProjectEditor
import org.openardf.radiooracle.shared.event.EventProjectFactory
import org.openardf.radiooracle.shared.event.EventRaceDetails
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventProjectSummary
import org.openardf.radiooracle.shared.event.EventReadoutDetails
import org.openardf.radiooracle.shared.event.EventResultDetails
import org.openardf.radiooracle.shared.event.toDisplayLabel
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.domain.ResultStatus
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.UUID

/** Starts the first Compose Desktop shell for Radio-Oracle. */
fun main(args: Array<String>) = application {
    lateinit var requestWindowClose: () -> Unit
    Window(onCloseRequest = { requestWindowClose() }, title = "Radio-Oracle Desktop") {
        val startupPath = remember(args.toList()) { args.firstOrNull()?.let(Path::of) }
        val projectSession = remember { DesktopProjectSession(DesktopProjectFiles) }
        val startupStatus = remember(startupPath) { openStartupProject(projectSession, startupPath) }
        var projectFile by remember { mutableStateOf(projectSession.currentProject) }
        var projectStatusText by remember { mutableStateOf(startupStatus) }
        var hasUnsavedChanges by remember { mutableStateOf(projectSession.hasUnsavedChanges) }
        var pendingDirtyProjectAction by remember { mutableStateOf<PendingDirtyProjectAction?>(null) }

        fun syncProjectState() {
            projectFile = projectSession.currentProject
            hasUnsavedChanges = projectSession.hasUnsavedChanges
        }

        fun openProject(path: Path) {
            runCatching {
                projectFile = projectSession.open(path)
                hasUnsavedChanges = projectSession.hasUnsavedChanges
                projectStatusText = "Opened ${path.fileName}"
            }.onFailure { error ->
                projectStatusText = "Open failed: ${error.message ?: error::class.simpleName}"
            }
        }

        fun closeProject(discardUnsavedChanges: Boolean = false) {
            runCatching {
                projectSession.closeProject(discardUnsavedChanges)
                syncProjectState()
                projectStatusText = "No project open."
            }.onFailure { error ->
                projectStatusText = "Close failed: ${error.message ?: error::class.simpleName}"
            }
        }

        fun createNewProject() {
            val project = EventProjectFactory.createEmptyProject(
                raceId = UUID.randomUUID().toString(),
                raceName = "New Event",
                startDateTimeIso = LocalDateTime.now().withNano(0).toString()
            )
            projectSession.newProject(project)
            syncProjectState()
            projectStatusText = "New unsaved project."
        }

        fun saveCurrentProject(): Boolean {
            if (projectSession.currentPath == null) {
                val path = DesktopFileDialogs.chooseSaveProject() ?: return false
                return runCatching {
                    projectSession.saveAs(path)
                    syncProjectState()
                    projectStatusText = "Saved ${path.fileName}"
                }.onFailure { error ->
                    projectStatusText = "Save failed: ${error.message ?: error::class.simpleName}"
                }.isSuccess
            }
            return runCatching {
                projectSession.save()
                syncProjectState()
                projectStatusText = "Saved ${projectSession.currentPath?.fileName ?: "project"}"
            }.onFailure { error ->
                projectStatusText = "Save failed: ${error.message ?: error::class.simpleName}"
            }.isSuccess
        }

        fun exportCsv(title: String, export: (Path, EventProjectFile) -> Unit) {
            val currentProject = projectSession.currentProject ?: return
            DesktopFileDialogs.chooseExportCsv(title)?.let { path ->
                runCatching {
                    export(path, currentProject)
                    syncProjectState()
                    projectStatusText = "Exported ${path.fileName}"
                }.onFailure { error ->
                    projectStatusText = "Export failed: ${error.message ?: error::class.simpleName}"
                }
            }
        }

        fun continuePendingDirtyAction(saveFirst: Boolean) {
            val action = pendingDirtyProjectAction ?: return
            if (saveFirst && !saveCurrentProject()) {
                return
            }
            pendingDirtyProjectAction = null
            when (action) {
                PendingDirtyProjectAction.ExitApplication -> exitApplication()
                PendingDirtyProjectAction.NewProject -> createNewProject()
                is PendingDirtyProjectAction.OpenProject -> openProject(action.path)
                PendingDirtyProjectAction.CloseProject -> closeProject(
                    discardUnsavedChanges = DesktopDirtyProjectActions.shouldDiscardForClose(saveFirst)
                )
            }
        }

        requestWindowClose = {
            pendingDirtyProjectAction = DesktopDirtyProjectActions.pendingActionOrNull(
                hasUnsavedChanges,
                PendingDirtyProjectAction.ExitApplication
            )
            if (pendingDirtyProjectAction == null) {
                exitApplication()
            }
        }

        MenuBar {
            Menu("File") {
                Item("New Project", onClick = {
                    pendingDirtyProjectAction = DesktopDirtyProjectActions.pendingActionOrNull(
                        hasUnsavedChanges,
                        PendingDirtyProjectAction.NewProject
                    )
                    if (pendingDirtyProjectAction == null) {
                        createNewProject()
                    }
                })
                Item("Open...", onClick = {
                    DesktopFileDialogs.chooseOpenProject()?.let { path ->
                        pendingDirtyProjectAction = DesktopDirtyProjectActions.pendingActionOrNull(
                            hasUnsavedChanges,
                            PendingDirtyProjectAction.OpenProject(path)
                        )
                        if (pendingDirtyProjectAction == null) {
                            openProject(path)
                        }
                    }
                })
                Item("Save", enabled = projectFile != null && hasUnsavedChanges, onClick = {
                    saveCurrentProject()
                })
                Item("Save As...", enabled = projectFile != null, onClick = {
                    DesktopFileDialogs.chooseSaveProject()?.let { path ->
                        runCatching {
                            projectSession.saveAs(path)
                            projectFile = projectSession.currentProject
                            hasUnsavedChanges = projectSession.hasUnsavedChanges
                            projectStatusText = "Saved ${path.fileName}"
                        }.onFailure { error ->
                            projectStatusText = "Save failed: ${error.message ?: error::class.simpleName}"
                        }
                    }
                })
                Item("Export Copy...", enabled = projectFile != null, onClick = {
                    DesktopFileDialogs.chooseExportProject()?.let { path ->
                        runCatching {
                            projectSession.exportCopy(path)
                            syncProjectState()
                            projectStatusText = "Exported ${path.fileName}"
                        }.onFailure { error ->
                            projectStatusText = "Export failed: ${error.message ?: error::class.simpleName}"
                        }
                    }
                })
                Item("Export Categories CSV...", enabled = projectFile != null, onClick = {
                    exportCsv("Export Categories CSV", DesktopProjectFiles::exportCategoriesCsv)
                })
                Item("Export Competitors CSV...", enabled = projectFile != null, onClick = {
                    exportCsv("Export Competitors CSV", DesktopProjectFiles::exportCompetitorsCsv)
                })
                Item("Export Starts CSV...", enabled = projectFile != null, onClick = {
                    exportCsv("Export Starts CSV", DesktopProjectFiles::exportCompetitorStartsCsv)
                })
                Item("Export Readouts CSV...", enabled = projectFile != null, onClick = {
                    exportCsv("Export Readouts CSV", DesktopProjectFiles::exportReadoutsCsv)
                })
                Item("Export Results CSV...", enabled = projectFile != null, onClick = {
                    exportCsv("Export Results CSV", DesktopProjectFiles::exportResultsCsv)
                })
                Item("Close Project", enabled = projectFile != null, onClick = {
                    pendingDirtyProjectAction = DesktopDirtyProjectActions.pendingActionOrNull(
                        hasUnsavedChanges,
                        PendingDirtyProjectAction.CloseProject
                    )
                    if (pendingDirtyProjectAction == null) {
                        closeProject()
                    }
                })
            }
        }

        pendingDirtyProjectAction?.let {
            UnsavedChangesDialog(
                onSave = { continuePendingDirtyAction(saveFirst = true) },
                onDiscard = { continuePendingDirtyAction(saveFirst = false) },
                onCancel = { pendingDirtyProjectAction = null }
            )
        }

        RadioOManagerDesktopApp(
            projectFile = projectFile,
            projectStatusText = projectStatusText,
            hasUnsavedChanges = hasUnsavedChanges,
            onRenameRace = { name ->
                runCatching {
                    projectFile = projectSession.updateCurrentProject { currentProject ->
                        EventProjectEditor.renameRace(currentProject, name)
                    }
                    hasUnsavedChanges = projectSession.hasUnsavedChanges
                    projectStatusText = "Unsaved changes."
                }.onFailure { error ->
                    projectStatusText = "Edit failed: ${error.message ?: error::class.simpleName}"
                }
            },
            onUpdateRaceStartDateTime = { startDateTimeIso ->
                runCatching {
                    projectFile = projectSession.updateCurrentProject { currentProject ->
                        EventProjectEditor.updateRaceStartDateTime(currentProject, startDateTimeIso)
                    }
                    hasUnsavedChanges = projectSession.hasUnsavedChanges
                    projectStatusText = "Unsaved changes."
                }.onFailure { error ->
                    projectStatusText = "Edit failed: ${error.message ?: error::class.simpleName}"
                }
            },
            onUpdateRaceSettings = { raceType, raceLevel, raceBand, timeLimitMinutes ->
                runCatching {
                    projectFile = projectSession.updateCurrentProject { currentProject ->
                        EventProjectEditor.updateRaceSettings(
                            currentProject,
                            raceType,
                            raceLevel,
                            raceBand,
                            timeLimitMinutes
                        )
                    }
                    hasUnsavedChanges = projectSession.hasUnsavedChanges
                    projectStatusText = "Unsaved changes."
                }.onFailure { error ->
                    projectStatusText = "Edit failed: ${error.message ?: error::class.simpleName}"
                }
            },
            onRenameCategory = { categoryId, name ->
                runCatching {
                    projectFile = projectSession.updateCurrentProject { currentProject ->
                        EventProjectEditor.renameCategory(currentProject, categoryId, name)
                    }
                    hasUnsavedChanges = projectSession.hasUnsavedChanges
                    projectStatusText = "Unsaved changes."
                }.onFailure { error ->
                    projectStatusText = "Edit failed: ${error.message ?: error::class.simpleName}"
                }
            },
            onUpdateCategoryControlPoints = { categoryId, controlPointsText ->
                runCatching {
                    projectFile = projectSession.updateCurrentProject { currentProject ->
                        EventProjectEditor.updateCategoryControlPoints(
                            currentProject,
                            categoryId,
                            controlPointsText
                        ) {
                            UUID.randomUUID().toString()
                        }
                    }
                    hasUnsavedChanges = projectSession.hasUnsavedChanges
                    projectStatusText = "Unsaved changes."
                }.onFailure { error ->
                    projectStatusText = "Edit failed: ${error.message ?: error::class.simpleName}"
                }
            },
            onAddCategory = { name ->
                runCatching {
                    projectFile = projectSession.updateCurrentProject { currentProject ->
                        EventProjectEditor.addCategory(currentProject, UUID.randomUUID().toString(), name)
                    }
                    hasUnsavedChanges = projectSession.hasUnsavedChanges
                    projectStatusText = "Unsaved changes."
                }.onFailure { error ->
                    projectStatusText = "Edit failed: ${error.message ?: error::class.simpleName}"
                }
            },
            onRemoveCategory = { categoryId, deleteCompetitors ->
                runCatching {
                    projectFile = projectSession.updateCurrentProject { currentProject ->
                        EventProjectEditor.removeCategory(currentProject, categoryId, deleteCompetitors)
                    }
                    hasUnsavedChanges = projectSession.hasUnsavedChanges
                    projectStatusText = "Unsaved changes."
                }.onFailure { error ->
                    projectStatusText = "Edit failed: ${error.message ?: error::class.simpleName}"
                }
            },
            onRenameCompetitor = { competitorId, firstName, lastName ->
                runCatching {
                    projectFile = projectSession.updateCurrentProject { currentProject ->
                        EventProjectEditor.renameCompetitor(currentProject, competitorId, firstName, lastName)
                    }
                    hasUnsavedChanges = projectSession.hasUnsavedChanges
                    projectStatusText = "Unsaved changes."
                }.onFailure { error ->
                    projectStatusText = "Edit failed: ${error.message ?: error::class.simpleName}"
                }
            },
            onUpdateCompetitorNumbers = { competitorId, startNumber, siNumber ->
                runCatching {
                    projectFile = projectSession.updateCurrentProject { currentProject ->
                        EventProjectEditor.updateCompetitorNumbers(currentProject, competitorId, startNumber, siNumber)
                    }
                    hasUnsavedChanges = projectSession.hasUnsavedChanges
                    projectStatusText = "Unsaved changes."
                }.onFailure { error ->
                    projectStatusText = "Edit failed: ${error.message ?: error::class.simpleName}"
                }
            },
            onAddCompetitor = { firstName, lastName, startNumber, siNumber ->
                runCatching {
                    projectFile = projectSession.updateCurrentProject { currentProject ->
                        EventProjectEditor.addCompetitor(
                            currentProject,
                            UUID.randomUUID().toString(),
                            firstName,
                            lastName,
                            startNumber,
                            siNumber
                        )
                    }
                    hasUnsavedChanges = projectSession.hasUnsavedChanges
                    projectStatusText = "Unsaved changes."
                }.onFailure { error ->
                    projectStatusText = "Edit failed: ${error.message ?: error::class.simpleName}"
                }
            },
            onAssignCompetitorCategory = { competitorId, categoryId ->
                runCatching {
                    projectFile = projectSession.updateCurrentProject { currentProject ->
                        EventProjectEditor.assignCompetitorCategory(currentProject, competitorId, categoryId)
                    }
                    hasUnsavedChanges = projectSession.hasUnsavedChanges
                    projectStatusText = "Unsaved changes."
                }.onFailure { error ->
                    projectStatusText = "Edit failed: ${error.message ?: error::class.simpleName}"
                }
            },
            onRemoveCompetitor = { competitorId, deleteReadout ->
                runCatching {
                    projectFile = projectSession.updateCurrentProject { currentProject ->
                        EventProjectEditor.removeCompetitor(currentProject, competitorId, deleteReadout)
                    }
                    hasUnsavedChanges = projectSession.hasUnsavedChanges
                    projectStatusText = "Unsaved changes."
                }.onFailure { error ->
                    projectStatusText = "Edit failed: ${error.message ?: error::class.simpleName}"
                }
            },
            onRemoveReadout = { resultId ->
                runCatching {
                    projectFile = projectSession.updateCurrentProject { currentProject ->
                        EventProjectEditor.removeReadout(currentProject, resultId)
                    }
                    hasUnsavedChanges = projectSession.hasUnsavedChanges
                    projectStatusText = "Unsaved changes."
                }.onFailure { error ->
                    projectStatusText = "Edit failed: ${error.message ?: error::class.simpleName}"
                }
            },
            onUpdateReadoutStatus = { resultId, resultStatus ->
                runCatching {
                    projectFile = projectSession.updateCurrentProject { currentProject ->
                        EventProjectEditor.updateReadoutManualStatus(currentProject, resultId, resultStatus)
                    }
                    hasUnsavedChanges = projectSession.hasUnsavedChanges
                    projectStatusText = "Unsaved changes."
                }.onFailure { error ->
                    projectStatusText = "Edit failed: ${error.message ?: error::class.simpleName}"
                }
            },
            onAddManualReadout = { competitorId, siNumber, startSeconds, finishSeconds, controlCodes, resultStatus ->
                runCatching {
                    projectFile = projectSession.updateCurrentProject { currentProject ->
                        EventProjectEditor.addManualReadout(
                            projectFile = currentProject,
                            resultId = UUID.randomUUID().toString(),
                            competitorId = competitorId,
                            siNumber = siNumber,
                            startSeconds = startSeconds,
                            finishSeconds = finishSeconds,
                            controlCodes = controlCodes,
                            resultStatus = resultStatus,
                            readoutDateTimeIso = LocalDateTime.now().withNano(0).toString()
                        ) { index, type ->
                            "${UUID.randomUUID()}-$index-${type.name}"
                        }
                    }
                    hasUnsavedChanges = projectSession.hasUnsavedChanges
                    projectStatusText = "Unsaved changes."
                }.onFailure { error ->
                    projectStatusText = "Edit failed: ${error.message ?: error::class.simpleName}"
                }
            },
            onUpdateAlias = { aliasId, siCode, name ->
                runCatching {
                    projectFile = projectSession.updateCurrentProject { currentProject ->
                        EventProjectEditor.updateAlias(currentProject, aliasId, siCode, name)
                    }
                    hasUnsavedChanges = projectSession.hasUnsavedChanges
                    projectStatusText = "Unsaved changes."
                }.onFailure { error ->
                    projectStatusText = "Edit failed: ${error.message ?: error::class.simpleName}"
                }
            },
            onAddAlias = { siCode, name ->
                runCatching {
                    projectFile = projectSession.updateCurrentProject { currentProject ->
                        EventProjectEditor.addAlias(currentProject, UUID.randomUUID().toString(), siCode, name)
                    }
                    hasUnsavedChanges = projectSession.hasUnsavedChanges
                    projectStatusText = "Unsaved changes."
                }.onFailure { error ->
                    projectStatusText = "Edit failed: ${error.message ?: error::class.simpleName}"
                }
            },
            onRemoveAlias = { aliasId ->
                runCatching {
                    projectFile = projectSession.updateCurrentProject { currentProject ->
                        EventProjectEditor.removeAlias(currentProject, aliasId)
                    }
                    hasUnsavedChanges = projectSession.hasUnsavedChanges
                    projectStatusText = "Unsaved changes."
                }.onFailure { error ->
                    projectStatusText = "Edit failed: ${error.message ?: error::class.simpleName}"
                }
            }
        )
    }
}

/** Prompts for the standard save/discard/cancel decision before replacing or closing a dirty project. */
@Composable
private fun UnsavedChangesDialog(
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Unsaved changes") },
        text = { Text("Save changes before continuing?") },
        confirmButton = {
            Button(onClick = onSave) {
                Text("Save")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onDiscard) {
                    Text("Discard")
                }
                Button(onClick = onCancel) {
                    Text("Cancel")
                }
            }
        }
    )
}

/**
 * Builds the launchable desktop app shell.
 *
 * This composable owns only shell state for now. Event data, persistence, and
 * SI-reader workflows should be introduced through shared services in later
 * slices instead of being embedded directly in the desktop UI.
 */
@Composable
fun RadioOManagerDesktopApp(
    projectFile: EventProjectFile? = null,
    projectStatusText: String = "No project open.",
    hasUnsavedChanges: Boolean = false,
    onRenameRace: (String) -> Unit = {},
    onUpdateRaceStartDateTime: (String) -> Unit = {},
    onUpdateRaceSettings: (RaceType, RaceLevel, RaceBand, String) -> Unit = { _, _, _, _ -> },
    onRenameCategory: (String, String) -> Unit = { _, _ -> },
    onUpdateCategoryControlPoints: (String, String) -> Unit = { _, _ -> },
    onAddCategory: (String) -> Unit = {},
    onRemoveCategory: (String, Boolean) -> Unit = { _, _ -> },
    onRenameCompetitor: (String, String, String) -> Unit = { _, _, _ -> },
    onUpdateCompetitorNumbers: (String, String, String) -> Unit = { _, _, _ -> },
    onAddCompetitor: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    onAssignCompetitorCategory: (String, String?) -> Unit = { _, _ -> },
    onRemoveCompetitor: (String, Boolean) -> Unit = { _, _ -> },
    onRemoveReadout: (String) -> Unit = {},
    onUpdateReadoutStatus: (String, ResultStatus) -> Unit = { _, _ -> },
    onAddManualReadout: (String?, String, String, String, String, ResultStatus) -> Unit = { _, _, _, _, _, _ -> },
    onUpdateAlias: (String, String, String) -> Unit = { _, _, _ -> },
    onAddAlias: (String, String) -> Unit = { _, _ -> },
    onRemoveAlias: (String) -> Unit = {}
) {
    MaterialTheme(
        colors = MaterialTheme.colors.copy(
            primary = DesktopPalette.Primary,
            primaryVariant = DesktopPalette.PrimaryVariant,
            secondary = DesktopPalette.Secondary,
            error = DesktopPalette.Error,
            onPrimary = DesktopPalette.White,
            onSecondary = DesktopPalette.Black,
            onError = DesktopPalette.White
        )
    ) {
        var selectedSection by remember { mutableStateOf(DesktopSection.Races) }

        Surface(modifier = Modifier.fillMaxSize(), color = DesktopPalette.White) {
            Column(modifier = Modifier.fillMaxSize()) {
                AppTopBar()
                Row(modifier = Modifier.weight(1f)) {
                    NavigationRail(
                        selectedSection = selectedSection,
                        onSectionSelected = { selectedSection = it }
                    )
                    SectionWorkspace(
                        section = selectedSection,
                        projectFile = projectFile,
                        projectStatusText = projectStatusText,
                        onRenameRace = onRenameRace,
                        onUpdateRaceStartDateTime = onUpdateRaceStartDateTime,
                        onUpdateRaceSettings = onUpdateRaceSettings,
                        onRenameCategory = onRenameCategory,
                        onUpdateCategoryControlPoints = onUpdateCategoryControlPoints,
                        onAddCategory = onAddCategory,
                        onRemoveCategory = onRemoveCategory,
                        onRenameCompetitor = onRenameCompetitor,
                        onUpdateCompetitorNumbers = onUpdateCompetitorNumbers,
                        onAddCompetitor = onAddCompetitor,
                        onAssignCompetitorCategory = onAssignCompetitorCategory,
                        onRemoveCompetitor = onRemoveCompetitor,
                        onRemoveReadout = onRemoveReadout,
                        onUpdateReadoutStatus = onUpdateReadoutStatus,
                        onAddManualReadout = onAddManualReadout,
                        onUpdateAlias = onUpdateAlias,
                        onAddAlias = onAddAlias,
                        onRemoveAlias = onRemoveAlias
                    )
                }
                StatusStrip(projectStatusText, hasUnsavedChanges)
            }
        }
    }
}

/** Renders the Android-style app bar used at the top of the desktop window. */
@Composable
private fun AppTopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(DesktopPalette.Primary)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Radio-Ōracle",
            color = DesktopPalette.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = "Desktop event-admin preview",
            color = DesktopPalette.White,
            fontSize = 14.sp
        )
    }
}

/** Shows the main event-admin sections using the same names as Android. */
@Composable
private fun NavigationRail(
    selectedSection: DesktopSection,
    onSectionSelected: (DesktopSection) -> Unit
) {
    Column(
        modifier = Modifier
            .width(180.dp)
            .fillMaxHeight()
            .background(Color(0xFFF5F5F5))
            .border(1.dp, DesktopPalette.LightGrey)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        DesktopSection.entries.forEach { section ->
            Button(
                onClick = { onSectionSelected(section) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = section.label,
                    fontWeight = if (section == selectedSection) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

/** Displays an Android-style empty state for the selected section. */
@Composable
private fun SectionWorkspace(
    section: DesktopSection,
    projectFile: EventProjectFile?,
    projectStatusText: String,
    onRenameRace: (String) -> Unit,
    onUpdateRaceStartDateTime: (String) -> Unit,
    onUpdateRaceSettings: (RaceType, RaceLevel, RaceBand, String) -> Unit,
    onRenameCategory: (String, String) -> Unit,
    onUpdateCategoryControlPoints: (String, String) -> Unit,
    onAddCategory: (String) -> Unit,
    onRemoveCategory: (String, Boolean) -> Unit,
    onRenameCompetitor: (String, String, String) -> Unit,
    onUpdateCompetitorNumbers: (String, String, String) -> Unit,
    onAddCompetitor: (String, String, String, String) -> Unit,
    onAssignCompetitorCategory: (String, String?) -> Unit,
    onRemoveCompetitor: (String, Boolean) -> Unit,
    onRemoveReadout: (String) -> Unit,
    onUpdateReadoutStatus: (String, ResultStatus) -> Unit,
    onAddManualReadout: (String?, String, String, String, String, ResultStatus) -> Unit,
    onUpdateAlias: (String, String, String) -> Unit,
    onAddAlias: (String, String) -> Unit,
    onRemoveAlias: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = section.label,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = DesktopPalette.Black
        )
        Text(
            text = sectionSummary(section, projectFile),
            color = DesktopPalette.Black,
            fontSize = 14.sp
        )
        if (section == DesktopSection.Races && projectFile != null) {
            RaceDetailsPanel(
                details = EventRaceDetails.from(projectFile.raceData.race),
                onRenameRace = onRenameRace,
                onUpdateRaceStartDateTime = onUpdateRaceStartDateTime,
                onUpdateRaceSettings = onUpdateRaceSettings
            )
        }
        if (section == DesktopSection.Categories && projectFile != null) {
            CategoryDetailsPanel(
                categories = EventCategoryDetails.from(projectFile.raceData),
                onRenameCategory = onRenameCategory,
                onUpdateCategoryControlPoints = onUpdateCategoryControlPoints,
                onAddCategory = onAddCategory,
                onRemoveCategory = onRemoveCategory
            )
        }
        if (section == DesktopSection.Competitors && projectFile != null) {
            CompetitorDetailsPanel(
                competitors = EventCompetitorDetails.from(projectFile.raceData),
                categories = EventCategoryDetails.from(projectFile.raceData),
                onRenameCompetitor = onRenameCompetitor,
                onUpdateCompetitorNumbers = onUpdateCompetitorNumbers,
                onAddCompetitor = onAddCompetitor,
                onAssignCompetitorCategory = onAssignCompetitorCategory,
                onRemoveCompetitor = onRemoveCompetitor
            )
        }
        if (section == DesktopSection.Aliases && projectFile != null) {
            AliasDetailsPanel(
                aliases = EventAliasDetails.from(projectFile.raceData),
                onUpdateAlias = onUpdateAlias,
                onAddAlias = onAddAlias,
                onRemoveAlias = onRemoveAlias
            )
        }
        if (section == DesktopSection.Readouts && projectFile != null) {
            ReadoutDetailsPanel(
                readouts = EventReadoutDetails.from(projectFile.raceData),
                competitors = EventCompetitorDetails.from(projectFile.raceData),
                onRemoveReadout = onRemoveReadout,
                onUpdateReadoutStatus = onUpdateReadoutStatus,
                onAddManualReadout = onAddManualReadout
            )
        }
        if (section == DesktopSection.Results && projectFile != null) {
            ResultDetailsPanel(
                results = EventResultDetails.from(projectFile.raceData),
                onUpdateReadoutStatus = onUpdateReadoutStatus
            )
        }
        if (section == DesktopSection.Settings) {
            SettingsDetailsPanel(DesktopProjectDiagnostics.from(projectFile))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(DesktopPalette.LightGrey)
        )
        Text(
            text = if (projectFile != null) {
                "${projectFile.raceData.race.startDateTimeIso} - $projectStatusText"
            } else {
                projectStatusText
            },
            color = DesktopPalette.Disconnected,
            fontSize = 13.sp
        )
    }
}

/** Shows read-only project diagnostics and the desktop-beta scope boundary. */
@Composable
private fun SettingsDetailsPanel(diagnostics: DesktopProjectDiagnostics) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DetailRow("Project", diagnostics.projectState)
        DetailRow("Schema", diagnostics.schemaText.ifBlank { "None" })
        DetailRow("Race ID", diagnostics.raceId.ifBlank { "None" })
        DetailRow("Race name", diagnostics.raceName.ifBlank { "None" })
        DetailRow("Start", diagnostics.startDateTimeIso.ifBlank { "None" })
        DetailHeaderRow(listOf("Categories", "Competitors", "Readouts", "Results"))
        DetailGridRow(
            listOf(
                diagnostics.categoryCount.toString(),
                diagnostics.competitorCount.toString(),
                diagnostics.readoutCount.toString(),
                diagnostics.resultCount.toString()
            )
        )
        DetailRow("Validation", diagnostics.validationState)
        diagnostics.validationIssues.forEach { issue ->
            Text(
                text = issue,
                color = DesktopPalette.Error,
                fontSize = 13.sp
            )
        }
        Text(
            text = "Desktop beta scope",
            color = DesktopPalette.Disconnected,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        diagnostics.betaLimitations.forEach { limitation ->
            Text(
                text = limitation,
                color = DesktopPalette.Black,
                fontSize = 13.sp
            )
        }
    }
}

/** Shows read-only competitor result rows. */
@Composable
private fun ResultDetailsPanel(
    results: List<EventResultDetails>,
    onUpdateReadoutStatus: (String, ResultStatus) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DetailHeaderRow(listOf("Place", "Competitor", "Status", "Points", "Runtime", ""))
        results.forEach { result ->
            ResultDetailRow(result, onUpdateReadoutStatus)
        }
    }
}

/** Shows one ranked result row with explicit manual status editing. */
@Composable
private fun ResultDetailRow(
    result: EventResultDetails,
    onUpdateReadoutStatus: (String, ResultStatus) -> Unit
) {
    var selectedStatus by remember(result.id, result.resultStatus) { mutableStateOf(result.resultStatus) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(result.placeText, modifier = Modifier.weight(1f), color = DesktopPalette.Black, fontSize = 13.sp)
        Text(result.competitorName, modifier = Modifier.weight(1f), color = DesktopPalette.Black, fontSize = 13.sp)
        ResultStatusPicker(
            selectedStatus = selectedStatus,
            onStatusSelected = { selectedStatus = it },
            modifier = Modifier.weight(1f)
        )
        Text(result.pointsText, modifier = Modifier.weight(1f), color = DesktopPalette.Black, fontSize = 13.sp)
        Text(result.runTimeText, modifier = Modifier.weight(1f), color = DesktopPalette.Black, fontSize = 13.sp)
        Button(
            onClick = { onUpdateReadoutStatus(result.id, selectedStatus) },
            modifier = Modifier.weight(1f),
            enabled = selectedStatus != result.resultStatus || result.automaticStatus
        ) {
            Text("Status")
        }
    }
}

/** Shows read-only matched and unmatched SI-card readout rows. */
@Composable
private fun ReadoutDetailsPanel(
    readouts: List<EventReadoutDetails>,
    competitors: List<EventCompetitorDetails>,
    onRemoveReadout: (String) -> Unit,
    onUpdateReadoutStatus: (String, ResultStatus) -> Unit,
    onAddManualReadout: (String?, String, String, String, String, ResultStatus) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ManualReadoutAddRow(
            competitors = competitors,
            onAddManualReadout = onAddManualReadout
        )
        DetailHeaderRow(listOf("SI no.", "Competitor", "Status", "Points", "Runtime", "Punches", "", ""))
        readouts.forEach { readout ->
            ReadoutDetailRow(readout, onRemoveReadout, onUpdateReadoutStatus)
        }
    }
}

/** Shows a compact manual readout entry row for desktop beta testing. */
@Composable
private fun ManualReadoutAddRow(
    competitors: List<EventCompetitorDetails>,
    onAddManualReadout: (String?, String, String, String, String, ResultStatus) -> Unit
) {
    var selectedCompetitorId by remember { mutableStateOf<String?>(null) }
    var siNumberDraft by remember { mutableStateOf("") }
    var startSecondsDraft by remember { mutableStateOf("") }
    var finishSecondsDraft by remember { mutableStateOf("") }
    var controlCodesDraft by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf(ResultStatus.OK) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompetitorPicker(
            selectedCompetitorId = selectedCompetitorId,
            competitors = competitors,
            onCompetitorSelected = { selectedCompetitorId = it },
            modifier = Modifier.weight(1f)
        )
        TextField(
            value = siNumberDraft,
            onValueChange = { siNumberDraft = it },
            modifier = Modifier.weight(1f),
            label = { Text("SI") }
        )
        TextField(
            value = startSecondsDraft,
            onValueChange = { startSecondsDraft = it },
            modifier = Modifier.weight(1f),
            label = { Text("Start s") }
        )
        TextField(
            value = finishSecondsDraft,
            onValueChange = { finishSecondsDraft = it },
            modifier = Modifier.weight(1f),
            label = { Text("Finish s") }
        )
        TextField(
            value = controlCodesDraft,
            onValueChange = { controlCodesDraft = it },
            modifier = Modifier.weight(1f),
            label = { Text("Controls") }
        )
        ResultStatusPicker(
            selectedStatus = selectedStatus,
            onStatusSelected = { selectedStatus = it },
            modifier = Modifier.weight(1f)
        )
        Button(
            onClick = {
                onAddManualReadout(
                    selectedCompetitorId,
                    siNumberDraft,
                    startSecondsDraft,
                    finishSecondsDraft,
                    controlCodesDraft,
                    selectedStatus
                )
            },
            modifier = Modifier.weight(1f),
            enabled = selectedCompetitorId != null ||
                    siNumberDraft.isNotBlank() ||
                    startSecondsDraft.isNotBlank() ||
                    finishSecondsDraft.isNotBlank() ||
                    controlCodesDraft.isNotBlank()
        ) {
            Text("Add")
        }
    }
}

/** Shows one readout row with deletion routed through shared project-editing rules. */
@Composable
private fun ReadoutDetailRow(
    readout: EventReadoutDetails,
    onRemoveReadout: (String) -> Unit,
    onUpdateReadoutStatus: (String, ResultStatus) -> Unit
) {
    var showDeleteDialog by remember(readout.id) { mutableStateOf(false) }
    var selectedStatus by remember(readout.id, readout.resultStatus) { mutableStateOf(readout.resultStatus) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(readout.siNumberText, modifier = Modifier.weight(1f), color = DesktopPalette.Black, fontSize = 13.sp)
        Text(readout.competitorName, modifier = Modifier.weight(1f), color = DesktopPalette.Black, fontSize = 13.sp)
        ResultStatusPicker(
            selectedStatus = selectedStatus,
            onStatusSelected = { selectedStatus = it },
            modifier = Modifier.weight(1f)
        )
        Text(readout.pointsText, modifier = Modifier.weight(1f), color = DesktopPalette.Black, fontSize = 13.sp)
        Text(readout.runTimeText, modifier = Modifier.weight(1f), color = DesktopPalette.Black, fontSize = 13.sp)
        Text(readout.punchCodesText, modifier = Modifier.weight(1f), color = DesktopPalette.Black, fontSize = 13.sp)
        Button(
            onClick = { onUpdateReadoutStatus(readout.id, selectedStatus) },
            modifier = Modifier.weight(1f),
            enabled = selectedStatus != readout.resultStatus || readout.automaticStatus
        ) {
            Text("Status")
        }
        Button(
            onClick = { showDeleteDialog = true },
            modifier = Modifier.weight(1f)
        ) {
            Text("Delete")
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete readout") },
            text = { Text("Delete readout for SI ${readout.siNumberText}?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onRemoveReadout(readout.id)
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                Button(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/** Shows result statuses as explicit manual-status choices. */
@Composable
private fun ResultStatusPicker(
    selectedStatus: ResultStatus,
    onStatusSelected: (ResultStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Button(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(selectedStatus.toDisplayLabel())
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            ResultStatus.entries.forEach { status ->
                DropdownMenuItem(
                    onClick = {
                        expanded = false
                        onStatusSelected(status)
                    }
                ) {
                    Text(status.toDisplayLabel())
                }
            }
        }
    }
}

/** Shows a compact competitor selector with an explicit unmatched-readout option. */
@Composable
private fun CompetitorPicker(
    selectedCompetitorId: String?,
    competitors: List<EventCompetitorDetails>,
    onCompetitorSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedCompetitorName = competitors.firstOrNull { it.id == selectedCompetitorId }?.fullName ?: "Unmatched"

    Box(modifier = modifier) {
        Button(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(selectedCompetitorName)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                onClick = {
                    expanded = false
                    onCompetitorSelected(null)
                }
            ) {
                Text("Unmatched")
            }
            competitors.forEach { competitor ->
                DropdownMenuItem(
                    onClick = {
                        expanded = false
                        onCompetitorSelected(competitor.id)
                    }
                ) {
                    Text(competitor.fullName)
                }
            }
        }
    }
}

/** Shows editable competitor names using shared category lookup and formatting. */
@Composable
private fun CompetitorDetailsPanel(
    competitors: List<EventCompetitorDetails>,
    categories: List<EventCategoryDetails>,
    onRenameCompetitor: (String, String, String) -> Unit,
    onUpdateCompetitorNumbers: (String, String, String) -> Unit,
    onAddCompetitor: (String, String, String, String) -> Unit,
    onAssignCompetitorCategory: (String, String?) -> Unit,
    onRemoveCompetitor: (String, Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CompetitorAddRow(onAddCompetitor)
        DetailHeaderRow(listOf("First", "Last", "Category", "Start no.", "SI no.", "", "", "", ""))
        competitors.forEach { competitor ->
            CompetitorDetailRow(
                competitor = competitor,
                categories = categories,
                onRenameCompetitor = onRenameCompetitor,
                onUpdateCompetitorNumbers = onUpdateCompetitorNumbers,
                onAssignCompetitorCategory = onAssignCompetitorCategory,
                onRemoveCompetitor = onRemoveCompetitor
            )
        }
    }
}

/** Shows the new-competitor entry row above existing competitor definitions. */
@Composable
private fun CompetitorAddRow(onAddCompetitor: (String, String, String, String) -> Unit) {
    var firstNameDraft by remember { mutableStateOf("") }
    var lastNameDraft by remember { mutableStateOf("") }
    var startNumberDraft by remember { mutableStateOf("") }
    var siNumberDraft by remember { mutableStateOf("") }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = firstNameDraft,
            onValueChange = { firstNameDraft = it },
            modifier = Modifier.weight(1f),
            label = { Text("First") }
        )
        TextField(
            value = lastNameDraft,
            onValueChange = { lastNameDraft = it },
            modifier = Modifier.weight(1f),
            label = { Text("Last") }
        )
        TextField(
            value = startNumberDraft,
            onValueChange = { startNumberDraft = it },
            modifier = Modifier.weight(1f),
            label = { Text("Start") }
        )
        TextField(
            value = siNumberDraft,
            onValueChange = { siNumberDraft = it },
            modifier = Modifier.weight(1f),
            label = { Text("SI") }
        )
        Button(
            onClick = { onAddCompetitor(firstNameDraft, lastNameDraft, startNumberDraft, siNumberDraft) },
            modifier = Modifier.weight(1f),
            enabled = firstNameDraft.isNotBlank() ||
                    lastNameDraft.isNotBlank() ||
                    startNumberDraft.isNotBlank() ||
                    siNumberDraft.isNotBlank()
        ) {
            Text("Add")
        }
    }
}

/** Shows one editable competitor-name row plus read-only assignment fields. */
@Composable
private fun CompetitorDetailRow(
    competitor: EventCompetitorDetails,
    categories: List<EventCategoryDetails>,
    onRenameCompetitor: (String, String, String) -> Unit,
    onUpdateCompetitorNumbers: (String, String, String) -> Unit,
    onAssignCompetitorCategory: (String, String?) -> Unit,
    onRemoveCompetitor: (String, Boolean) -> Unit
) {
    var firstNameDraft by remember(competitor.id, competitor.firstName) { mutableStateOf(competitor.firstName) }
    var lastNameDraft by remember(competitor.id, competitor.lastName) { mutableStateOf(competitor.lastName) }
    var startNumberDraft by remember(competitor.id, competitor.startNumberText) {
        mutableStateOf(competitor.startNumberText)
    }
    var siNumberDraft by remember(competitor.id, competitor.siNumberText) { mutableStateOf(competitor.siNumberText) }
    var selectedCategoryId by remember(competitor.id, competitor.categoryId) { mutableStateOf(competitor.categoryId) }
    var showDeleteDialog by remember(competitor.id) { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = firstNameDraft,
            onValueChange = { firstNameDraft = it },
            modifier = Modifier.weight(1f),
            label = { Text("First") }
        )
        TextField(
            value = lastNameDraft,
            onValueChange = { lastNameDraft = it },
            modifier = Modifier.weight(1f),
            label = { Text("Last") }
        )
        CategoryPicker(
            selectedCategoryId = selectedCategoryId,
            categories = categories,
            onCategorySelected = { selectedCategoryId = it },
            modifier = Modifier.weight(1f)
        )
        TextField(
            value = startNumberDraft,
            onValueChange = { startNumberDraft = it },
            modifier = Modifier.weight(1f),
            label = { Text("Start") }
        )
        TextField(
            value = siNumberDraft,
            onValueChange = { siNumberDraft = it },
            modifier = Modifier.weight(1f),
            label = { Text("SI") }
        )
        Button(
            onClick = { onRenameCompetitor(competitor.id, firstNameDraft, lastNameDraft) },
            modifier = Modifier.weight(1f),
            enabled = firstNameDraft != competitor.firstName || lastNameDraft != competitor.lastName
        ) {
            Text("Name")
        }
        Button(
            onClick = { onUpdateCompetitorNumbers(competitor.id, startNumberDraft, siNumberDraft) },
            modifier = Modifier.weight(1f),
            enabled = startNumberDraft != competitor.startNumberText || siNumberDraft != competitor.siNumberText
        ) {
            Text("Nos.")
        }
        Button(
            onClick = { onAssignCompetitorCategory(competitor.id, selectedCategoryId) },
            modifier = Modifier.weight(1f),
            enabled = selectedCategoryId != competitor.categoryId
        ) {
            Text("Cat.")
        }
        Button(
            onClick = { showDeleteDialog = true },
            modifier = Modifier.weight(1f)
        ) {
            Text("Delete")
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete competitor") },
            text = { Text("Delete ${competitor.fullName}? The readout can be kept as unmatched or deleted too.") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            showDeleteDialog = false
                            onRemoveCompetitor(competitor.id, false)
                        }
                    ) {
                        Text("Keep readout")
                    }
                    Button(
                        onClick = {
                            showDeleteDialog = false
                            onRemoveCompetitor(competitor.id, true)
                        }
                    ) {
                        Text("Delete readout")
                    }
                }
            },
            dismissButton = {
                Button(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/** Shows a compact category selector with an explicit unassigned option. */
@Composable
private fun CategoryPicker(
    selectedCategoryId: String?,
    categories: List<EventCategoryDetails>,
    onCategorySelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedCategoryName = categories.firstOrNull { it.id == selectedCategoryId }?.name ?: "Unassigned"

    Box(modifier = modifier) {
        Button(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(selectedCategoryName)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                onClick = {
                    expanded = false
                    onCategorySelected(null)
                }
            ) {
                Text("Unassigned")
            }
            categories.forEach { category ->
                DropdownMenuItem(
                    onClick = {
                        expanded = false
                        onCategorySelected(category.id)
                    }
                ) {
                    Text(category.name)
                }
            }
        }
    }
}

/** Shows editable alias rows backed by shared alias validation rules. */
@Composable
private fun AliasDetailsPanel(
    aliases: List<EventAliasDetails>,
    onUpdateAlias: (String, String, String) -> Unit,
    onAddAlias: (String, String) -> Unit,
    onRemoveAlias: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AliasAddRow(onAddAlias)
        DetailHeaderRow(listOf("SI code", "Alias", "", ""))
        aliases.forEach { alias ->
            AliasDetailRow(alias, onUpdateAlias, onRemoveAlias)
        }
    }
}

/** Shows the new-alias entry row above the existing alias mappings. */
@Composable
private fun AliasAddRow(onAddAlias: (String, String) -> Unit) {
    var siCodeDraft by remember { mutableStateOf("") }
    var nameDraft by remember { mutableStateOf("") }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = siCodeDraft,
            onValueChange = { siCodeDraft = it },
            modifier = Modifier.weight(1f),
            label = { Text("New SI code") }
        )
        TextField(
            value = nameDraft,
            onValueChange = { nameDraft = it },
            modifier = Modifier.weight(1f),
            label = { Text("New alias") }
        )
        Button(
            onClick = { onAddAlias(siCodeDraft, nameDraft) },
            modifier = Modifier.weight(1f),
            enabled = siCodeDraft.isNotBlank() || nameDraft.isNotBlank()
        ) {
            Text("Add")
        }
    }
}

/** Shows one editable alias row for a SportIdent control code mapping. */
@Composable
private fun AliasDetailRow(
    alias: EventAliasDetails,
    onUpdateAlias: (String, String, String) -> Unit,
    onRemoveAlias: (String) -> Unit
) {
    var siCodeDraft by remember(alias.id, alias.siCodeText) { mutableStateOf(alias.siCodeText) }
    var nameDraft by remember(alias.id, alias.name) { mutableStateOf(alias.name) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = siCodeDraft,
            onValueChange = { siCodeDraft = it },
            modifier = Modifier.weight(1f),
            label = { Text("SI code") }
        )
        TextField(
            value = nameDraft,
            onValueChange = { nameDraft = it },
            modifier = Modifier.weight(1f),
            label = { Text("Alias") }
        )
        Button(
            onClick = { onUpdateAlias(alias.id, siCodeDraft, nameDraft) },
            modifier = Modifier.weight(1f),
            enabled = siCodeDraft != alias.siCodeText || nameDraft != alias.name
        ) {
            Text("Apply")
        }
        Button(
            onClick = { onRemoveAlias(alias.id) },
            modifier = Modifier.weight(1f)
        ) {
            Text("Delete")
        }
    }
}

/** Shows editable category names with read-only effective race settings. */
@Composable
private fun CategoryDetailsPanel(
    categories: List<EventCategoryDetails>,
    onRenameCategory: (String, String) -> Unit,
    onUpdateCategoryControlPoints: (String, String) -> Unit,
    onAddCategory: (String) -> Unit,
    onRemoveCategory: (String, Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CategoryAddRow(onAddCategory)
        DetailHeaderRow(listOf("Name", "Type", "Band", "Limit", "Controls", "", "", ""))
        categories.forEach { category ->
            CategoryDetailRow(category, onRenameCategory, onUpdateCategoryControlPoints, onRemoveCategory)
        }
    }
}

/** Shows the new-category entry row above existing category definitions. */
@Composable
private fun CategoryAddRow(onAddCategory: (String) -> Unit) {
    var categoryNameDraft by remember { mutableStateOf("") }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = categoryNameDraft,
            onValueChange = { categoryNameDraft = it },
            modifier = Modifier.weight(5f),
            label = { Text("New category") }
        )
        Button(
            onClick = { onAddCategory(categoryNameDraft) },
            modifier = Modifier.weight(1f),
            enabled = categoryNameDraft.isNotBlank()
        ) {
            Text("Add")
        }
    }
}

/** Shows one editable category-name row plus read-only derived category settings. */
@Composable
private fun CategoryDetailRow(
    category: EventCategoryDetails,
    onRenameCategory: (String, String) -> Unit,
    onUpdateCategoryControlPoints: (String, String) -> Unit,
    onRemoveCategory: (String, Boolean) -> Unit
) {
    var categoryNameDraft by remember(category.id, category.name) { mutableStateOf(category.name) }
    var controlPointsDraft by remember(category.id, category.controlPointsText) {
        mutableStateOf(category.controlPointsText)
    }
    var showDeleteDialog by remember(category.id) { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = categoryNameDraft,
            onValueChange = { categoryNameDraft = it },
            modifier = Modifier.weight(1f),
            label = { Text("Category") }
        )
        Text(category.raceTypeLabel, modifier = Modifier.weight(1f), color = DesktopPalette.Black, fontSize = 13.sp)
        Text(category.raceBandLabel, modifier = Modifier.weight(1f), color = DesktopPalette.Black, fontSize = 13.sp)
        Text(category.timeLimitText, modifier = Modifier.weight(1f), color = DesktopPalette.Black, fontSize = 13.sp)
        TextField(
            value = controlPointsDraft,
            onValueChange = { controlPointsDraft = it },
            modifier = Modifier.weight(1f),
            label = { Text("Controls") }
        )
        Button(
            onClick = { onRenameCategory(category.id, categoryNameDraft) },
            modifier = Modifier.weight(1f),
            enabled = categoryNameDraft != category.name
        ) {
            Text("Apply")
        }
        Button(
            onClick = { onUpdateCategoryControlPoints(category.id, controlPointsDraft) },
            modifier = Modifier.weight(1f),
            enabled = controlPointsDraft != category.controlPointsText
        ) {
            Text("Ctrls")
        }
        Button(
            onClick = { showDeleteDialog = true },
            modifier = Modifier.weight(1f)
        ) {
            Text("Delete")
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete category") },
            text = { Text("Delete ${category.name}? Competitors can be kept for reassignment or deleted too.") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            showDeleteDialog = false
                            onRemoveCategory(category.id, false)
                        }
                    ) {
                        Text("Keep competitors")
                    }
                    Button(
                        onClick = {
                            showDeleteDialog = false
                            onRemoveCategory(category.id, true)
                        }
                    ) {
                        Text("Delete competitors")
                    }
                }
            },
            dismissButton = {
                Button(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/** Shows editable race metadata backed by shared project-editing rules. */
@Composable
private fun RaceDetailsPanel(
    details: EventRaceDetails,
    onRenameRace: (String) -> Unit,
    onUpdateRaceStartDateTime: (String) -> Unit,
    onUpdateRaceSettings: (RaceType, RaceLevel, RaceBand, String) -> Unit
) {
    var raceNameDraft by remember(details.name) { mutableStateOf(details.name) }
    var startDateTimeDraft by remember(details.startDateTimeIso) {
        mutableStateOf(details.startDateTimeIso)
    }
    var selectedRaceType by remember(details.raceType) { mutableStateOf(details.raceType) }
    var selectedRaceLevel by remember(details.raceLevel) { mutableStateOf(details.raceLevel) }
    var selectedRaceBand by remember(details.raceBand) { mutableStateOf(details.raceBand) }
    var timeLimitMinutesDraft by remember(details.timeLimitMinutesText) {
        mutableStateOf(details.timeLimitMinutesText)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = raceNameDraft,
                onValueChange = { raceNameDraft = it },
                modifier = Modifier.weight(1f),
                label = { Text("Race name") }
            )
            Button(
                onClick = { onRenameRace(raceNameDraft) },
                enabled = raceNameDraft != details.name
            ) {
                Text("Apply")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = startDateTimeDraft,
                onValueChange = { startDateTimeDraft = it },
                modifier = Modifier.weight(1f),
                label = { Text("Start date/time") }
            )
            Button(
                onClick = { onUpdateRaceStartDateTime(startDateTimeDraft) },
                enabled = startDateTimeDraft != details.startDateTimeIso
            ) {
                Text("Apply")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RaceTypePicker(selectedRaceType, { selectedRaceType = it }, Modifier.weight(1f))
            RaceLevelPicker(selectedRaceLevel, { selectedRaceLevel = it }, Modifier.weight(1f))
            RaceBandPicker(selectedRaceBand, { selectedRaceBand = it }, Modifier.weight(1f))
            TextField(
                value = timeLimitMinutesDraft,
                onValueChange = { timeLimitMinutesDraft = it },
                modifier = Modifier.weight(1f),
                label = { Text("Limit min") }
            )
            Button(
                onClick = {
                    onUpdateRaceSettings(
                        selectedRaceType,
                        selectedRaceLevel,
                        selectedRaceBand,
                        timeLimitMinutesDraft
                    )
                },
                enabled = selectedRaceType != details.raceType ||
                        selectedRaceLevel != details.raceLevel ||
                        selectedRaceBand != details.raceBand ||
                        timeLimitMinutesDraft != details.timeLimitMinutesText
            ) {
                Text("Settings")
            }
        }
        DetailRow("Time limit", details.timeLimitText)
    }
}

/** Shows the race type selector using Android-compatible labels. */
@Composable
private fun RaceTypePicker(
    selectedRaceType: RaceType,
    onRaceTypeSelected: (RaceType) -> Unit,
    modifier: Modifier = Modifier
) {
    EnumPicker(
        selectedValue = selectedRaceType,
        values = RaceType.entries,
        label = RaceType::toDisplayLabel,
        onValueSelected = onRaceTypeSelected,
        modifier = modifier
    )
}

/** Shows the race level selector using Android-compatible labels. */
@Composable
private fun RaceLevelPicker(
    selectedRaceLevel: RaceLevel,
    onRaceLevelSelected: (RaceLevel) -> Unit,
    modifier: Modifier = Modifier
) {
    EnumPicker(
        selectedValue = selectedRaceLevel,
        values = RaceLevel.entries,
        label = RaceLevel::toDisplayLabel,
        onValueSelected = onRaceLevelSelected,
        modifier = modifier
    )
}

/** Shows the race band selector using Android-compatible labels. */
@Composable
private fun RaceBandPicker(
    selectedRaceBand: RaceBand,
    onRaceBandSelected: (RaceBand) -> Unit,
    modifier: Modifier = Modifier
) {
    EnumPicker(
        selectedValue = selectedRaceBand,
        values = RaceBand.entries,
        label = RaceBand::toDisplayLabel,
        onValueSelected = onRaceBandSelected,
        modifier = modifier
    )
}

/** Generic dropdown for small enum-backed desktop selectors. */
@Composable
private fun <T> EnumPicker(
    selectedValue: T,
    values: List<T>,
    label: (T) -> String,
    onValueSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Button(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(label(selectedValue))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            values.forEach { value ->
                DropdownMenuItem(
                    onClick = {
                        expanded = false
                        onValueSelected(value)
                    }
                ) {
                    Text(label(value))
                }
            }
        }
    }
}

/** Displays a compact header row for read-only desktop detail grids. */
@Composable
private fun DetailHeaderRow(values: List<String>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        values.forEach { value ->
            Text(
                text = value,
                modifier = Modifier.weight(1f),
                color = DesktopPalette.Disconnected,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** Displays a compact value row for read-only desktop detail grids. */
@Composable
private fun DetailGridRow(values: List<String>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        values.forEach { value ->
            Text(
                text = value,
                modifier = Modifier.weight(1f),
                color = DesktopPalette.Black,
                fontSize = 13.sp
            )
        }
    }
}

/** Displays a compact label/value pair for read-only desktop event details. */
@Composable
private fun DetailRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            modifier = Modifier.width(96.dp),
            color = DesktopPalette.Disconnected,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            color = DesktopPalette.Black,
            fontSize = 13.sp
        )
    }
}

/** Provides section-specific content summaries without introducing editing behavior. */
private fun sectionSummary(section: DesktopSection, projectFile: EventProjectFile?): String {
    val summary = projectFile?.let(EventProjectSummary::from)
    return when (section) {
        DesktopSection.Races -> summary?.raceName ?: "No races loaded."
        DesktopSection.Categories -> "${summary?.categoryCount ?: 0} categories loaded."
        DesktopSection.Competitors -> "${summary?.competitorCount ?: 0} competitors loaded."
        DesktopSection.Aliases -> "${projectFile?.raceData?.aliases?.size ?: 0} aliases loaded."
        DesktopSection.Readouts -> "${summary?.readoutCount ?: 0} SI-card readouts loaded."
        DesktopSection.Results -> "${summary?.resultCount ?: 0} results loaded."
        DesktopSection.Settings -> "Project diagnostics and desktop beta scope."
    }
}

/** Shows the current SI-reader connection state and project-save status. */
@Composable
private fun StatusStrip(
    projectStatusText: String,
    hasUnsavedChanges: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(DesktopPalette.Disconnected)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "SI station disconnected - $projectStatusText${if (hasUnsavedChanges) " *" else ""}",
            color = DesktopPalette.White,
            fontSize = 13.sp
        )
    }
}
