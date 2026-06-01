package org.openardf.radiooracle.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openardf.radiooracle.desktop.usb.DesktopSportIdentCardBlockDownload
import org.openardf.radiooracle.desktop.usb.DesktopSportIdentReadoutService
import org.openardf.radiooracle.desktop.usb.DesktopSportIdentStationProbe
import org.openardf.radiooracle.desktop.usb.JSerialCommDesktopSerialPortProvider
import org.openardf.radiooracle.shared.course.ControlPointValidationException
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
import org.openardf.radiooracle.shared.files.EventCsvImports
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.printing.FinishTicketRenderer
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

private data class FixedTableColumn(val title: String, val width: Dp)

private val TableColumnGap = 12.dp
private val ActionRailWidth = 104.dp
private val FixedGridRowHeight = 56.dp
private val ReadoutAddRailYOffset = 10.dp
private const val DesktopSiPollIntervalMs = 5_000L

private enum class DesktopSiReaderSeverity {
    DISCONNECTED,
    CONNECTED,
    WARNING,
    ERROR
}

private data class DesktopSiReaderUiState(
    val severity: DesktopSiReaderSeverity,
    val statusText: String,
    val warningTitle: String? = null,
    val warningMessage: String? = null,
    val warningKey: String? = null
) {
    companion object {
        fun disconnected(): DesktopSiReaderUiState =
            DesktopSiReaderUiState(
                severity = DesktopSiReaderSeverity.DISCONNECTED,
                statusText = "SI station disconnected"
            )
    }
}

private val CategoryTableColumns = listOf(
    FixedTableColumn("Name", 150.dp),
    FixedTableColumn("Length", 84.dp),
    FixedTableColumn("Climb", 84.dp),
    FixedTableColumn("Type", 96.dp),
    FixedTableColumn("Band", 104.dp),
    FixedTableColumn("Limit", 80.dp),
    FixedTableColumn("Controls", 180.dp),
    FixedTableColumn("", 92.dp),
    FixedTableColumn("", 92.dp),
    FixedTableColumn("", 92.dp)
)

private val CompetitorTableColumns = listOf(
    FixedTableColumn("First", 120.dp),
    FixedTableColumn("Last", 136.dp),
    FixedTableColumn("Club", 210.dp),
    FixedTableColumn("Index", 116.dp),
    FixedTableColumn("Birth", 72.dp),
    FixedTableColumn("Category", 136.dp),
    FixedTableColumn("Start no.", 86.dp),
    FixedTableColumn("SI no.", 110.dp),
    FixedTableColumn("", 92.dp),
    FixedTableColumn("", 92.dp),
    FixedTableColumn("", 92.dp),
    FixedTableColumn("", 92.dp),
    FixedTableColumn("", 92.dp)
)

private val ResultTableColumns = listOf(
    FixedTableColumn("Place", 64.dp),
    FixedTableColumn("Competitor", 240.dp),
    FixedTableColumn("Status", 136.dp),
    FixedTableColumn("Points", 80.dp),
    FixedTableColumn("Runtime", 104.dp),
    FixedTableColumn("", 104.dp)
)

private val ReadoutTableColumns = listOf(
    FixedTableColumn("SI no.", 112.dp),
    FixedTableColumn("Competitor", 240.dp),
    FixedTableColumn("Status", 136.dp),
    FixedTableColumn("Points", 80.dp),
    FixedTableColumn("Runtime", 104.dp),
    FixedTableColumn("Punches", 260.dp),
    FixedTableColumn("", 104.dp),
    FixedTableColumn("", 104.dp)
)

private val AliasTableColumns = listOf(
    FixedTableColumn("SI code", 112.dp),
    FixedTableColumn("Alias", 320.dp),
    FixedTableColumn("", 104.dp)
)

/** Starts the first Compose Desktop shell for Radio-Oracle. */
fun main(args: Array<String>) = application {
    lateinit var requestWindowClose: () -> Unit
    Window(onCloseRequest = { requestWindowClose() }, title = "Radio-Oracle Desktop") {
        val startupPath = remember(args.toList()) { args.firstOrNull()?.let(Path::of) }
        val projectSession = remember { DesktopProjectSession(DesktopProjectFiles) }
        val appCoroutineScope = rememberCoroutineScope()
        val startupStatus = remember(startupPath) { openStartupProject(projectSession, startupPath) }
        var projectFile by remember { mutableStateOf(projectSession.currentProject) }
        var projectStatusText by remember { mutableStateOf(startupStatus) }
        var hasUnsavedChanges by remember { mutableStateOf(projectSession.hasUnsavedChanges) }
        var pendingDirtyProjectAction by remember { mutableStateOf<PendingDirtyProjectAction?>(null) }
        var siReaderState by remember { mutableStateOf(DesktopSiReaderUiState.disconnected()) }
        var pendingSiModeWarning by remember { mutableStateOf<DesktopSiReaderUiState?>(null) }
        var lastShownSiModeWarningKey by remember { mutableStateOf<String?>(null) }
        var isDownloadingSiReadout by remember { mutableStateOf(false) }
        var isContinuousSiReadoutActive by remember { mutableStateOf(false) }
        var continuousSiReadoutStopRequested by remember { mutableStateOf<AtomicBoolean?>(null) }
        var siDownloadStatusText by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) {
            while (true) {
                val nextSiReaderState = withContext(Dispatchers.IO) {
                    detectDesktopSiReaderState()
                }
                siReaderState = nextSiReaderState
                if (
                    nextSiReaderState.warningKey != null &&
                    nextSiReaderState.warningKey != lastShownSiModeWarningKey
                ) {
                    pendingSiModeWarning = nextSiReaderState
                    lastShownSiModeWarningKey = nextSiReaderState.warningKey
                }
                delay(DesktopSiPollIntervalMs)
            }
        }

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

        fun appendSportIdentDownload(download: DesktopSportIdentCardBlockDownload) {
            projectFile = projectSession.updateCurrentProject { currentProject ->
                EventProjectEditor.addDownloadedSportIdentReadout(
                    projectFile = currentProject,
                    resultId = UUID.randomUUID().toString(),
                    cardType = download.inserted.cardType,
                    readout = download.readout,
                    readoutDateTimeIso = LocalDateTime.now().withNano(0).toString()
                ) { index, type ->
                    "${UUID.randomUUID()}-$index-${type.name}"
                }
            }
            hasUnsavedChanges = projectSession.hasUnsavedChanges
        }

        fun downloadSportIdentReadout() {
            if (isDownloadingSiReadout || isContinuousSiReadoutActive) {
                return
            }
            if (projectSession.currentProject == null) {
                projectStatusText = "Open or create a project before downloading SI cards."
                return
            }
            isDownloadingSiReadout = true
            siDownloadStatusText = "Waiting for SI card; keep it seated until the read finishes."
            projectStatusText = "Waiting for SI card..."
            appCoroutineScope.launch {
                val downloadResult = runCatching {
                    withContext(Dispatchers.IO) {
                        downloadDesktopSportIdentCardReadout()
                    }
                }
                downloadResult.onSuccess { download ->
                    runCatching {
                        appendSportIdentDownload(download)
                        projectStatusText = "Downloaded SI card ${download.readout.siNumber}."
                        siDownloadStatusText = null
                    }.onFailure { error ->
                        projectStatusText = "SI download failed: ${error.message ?: error::class.simpleName}"
                        siDownloadStatusText = projectStatusText
                    }
                }.onFailure { error ->
                    projectStatusText = "SI download failed: ${error.message ?: error::class.simpleName}"
                    siDownloadStatusText = projectStatusText
                }
                isDownloadingSiReadout = false
            }
        }

        fun stopContinuousSportIdentReadout() {
            continuousSiReadoutStopRequested?.set(true)
            if (isContinuousSiReadoutActive) {
                siDownloadStatusText = "Stopping continuous SI readout after the current card wait finishes."
                projectStatusText = "Stopping continuous SI readout..."
            }
        }

        fun startContinuousSportIdentReadout() {
            if (isDownloadingSiReadout || isContinuousSiReadoutActive) {
                return
            }
            if (projectSession.currentProject == null) {
                projectStatusText = "Open or create a project before downloading SI cards."
                return
            }
            val stopRequested = AtomicBoolean(false)
            continuousSiReadoutStopRequested = stopRequested
            isContinuousSiReadoutActive = true
            siDownloadStatusText = "Continuous SI readout is running; insert SI cards and keep each seated until it reads."
            projectStatusText = "Continuous SI readout running..."
            appCoroutineScope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        DesktopSportIdentReadoutService().downloadUntilTimeout(
                            maxCards = Int.MAX_VALUE,
                            onDownload = { download ->
                                appCoroutineScope.launch {
                                    runCatching {
                                        appendSportIdentDownload(download)
                                        projectStatusText = "Downloaded SI card ${download.readout.siNumber}."
                                        siDownloadStatusText = "Continuous SI readout running; waiting for the next card."
                                    }.onFailure { error ->
                                        projectStatusText = "SI download failed: ${error.message ?: error::class.simpleName}"
                                        siDownloadStatusText = projectStatusText
                                        stopRequested.set(true)
                                    }
                                }
                            },
                            onTimeout = {
                                appCoroutineScope.launch {
                                    if (!stopRequested.get()) {
                                        siDownloadStatusText = "Continuous SI readout timed out waiting for a card."
                                        projectStatusText = siDownloadStatusText ?: projectStatusText
                                    }
                                }
                            },
                            shouldContinue = { !stopRequested.get() }
                        )
                    }
                }
                result.onFailure { error ->
                    projectStatusText = "SI continuous download failed: ${error.message ?: error::class.simpleName}"
                    siDownloadStatusText = projectStatusText
                }
                if (stopRequested.get() && result.isSuccess) {
                    projectStatusText = "Continuous SI readout stopped."
                    siDownloadStatusText = null
                }
                isContinuousSiReadoutActive = false
                continuousSiReadoutStopRequested = null
            }
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

        fun exportArdfJson() {
            val currentProject = projectSession.currentProject ?: return
            DesktopFileDialogs.chooseExportArdfJson()?.let { path ->
                runCatching {
                    DesktopProjectFiles.exportArdfJson(path, currentProject)
                    syncProjectState()
                    projectStatusText = "Exported ${path.fileName}"
                }.onFailure { error ->
                    projectStatusText = "Export failed: ${error.message ?: error::class.simpleName}"
                }
            }
        }

        fun importCompetitorsCsv() {
            DesktopFileDialogs.chooseImportCsv("Import Competitors CSV")?.let { path ->
                runCatching {
                    val result = EventCsvImports.parseAndroidCompetitorRows(Files.readString(path))
                    projectFile = projectSession.updateCurrentProject { currentProject ->
                        EventProjectEditor.importCompetitorRows(
                            projectFile = currentProject,
                            rows = result.rows,
                            competitorIdFactory = { UUID.randomUUID().toString() },
                            categoryIdFactory = { UUID.randomUUID().toString() }
                        )
                    }
                    syncProjectState()
                    projectStatusText = importStatusText("Imported", result.rows.size, result.invalidLines.size, path.fileName.toString())
                }.onFailure { error ->
                    projectStatusText = "Import failed: ${error.message ?: error::class.simpleName}"
                }
            }
        }

        fun importCategoriesCsv() {
            DesktopFileDialogs.chooseImportCsv("Import Categories CSV")?.let { path ->
                runCatching {
                    val result = EventCsvImports.parseAndroidCategoryRows(Files.readString(path))
                    projectFile = projectSession.updateCurrentProject { currentProject ->
                        EventProjectEditor.importCategoryRows(
                            projectFile = currentProject,
                            rows = result.rows,
                            categoryIdFactory = { UUID.randomUUID().toString() },
                            controlPointIdFactory = { _, _ -> UUID.randomUUID().toString() }
                        )
                    }
                    syncProjectState()
                    projectStatusText = importStatusText("Imported", result.rows.size, result.invalidLines.size, path.fileName.toString())
                }.onFailure { error ->
                    projectStatusText = "Import failed: ${error.message ?: error::class.simpleName}"
                }
            }
        }

        fun importCompetitorStartsCsv() {
            DesktopFileDialogs.chooseImportCsv("Import Starts CSV")?.let { path ->
                runCatching {
                    val result = EventCsvImports.parseAndroidCompetitorStartRows(Files.readString(path))
                    projectFile = projectSession.updateCurrentProject { currentProject ->
                        EventProjectEditor.importCompetitorStartRows(currentProject, result.rows)
                    }
                    syncProjectState()
                    projectStatusText = importStatusText("Imported", result.rows.size, result.invalidLines.size, path.fileName.toString())
                }.onFailure { error ->
                    projectStatusText = "Import failed: ${error.message ?: error::class.simpleName}"
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
                Item("Export ARDF JSON...", enabled = projectFile != null, onClick = {
                    exportArdfJson()
                })
                Item("Import Categories CSV...", enabled = projectFile != null, onClick = {
                    importCategoriesCsv()
                })
                Item("Import Competitors CSV...", enabled = projectFile != null, onClick = {
                    importCompetitorsCsv()
                })
                Item("Import Starts CSV...", enabled = projectFile != null, onClick = {
                    importCompetitorStartsCsv()
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
        pendingSiModeWarning?.let { warning ->
            SiStationModeWarningDialog(
                title = warning.warningTitle ?: "SI station mode warning",
                message = warning.warningMessage ?: warning.statusText,
                onDismiss = { pendingSiModeWarning = null }
            )
        }

        RadioOManagerDesktopApp(
            projectFile = projectFile,
            projectStatusText = projectStatusText,
            hasUnsavedChanges = hasUnsavedChanges,
            siReaderState = siReaderState,
            isDownloadingSiReadout = isDownloadingSiReadout,
            isContinuousSiReadoutActive = isContinuousSiReadoutActive,
            siDownloadStatusText = siDownloadStatusText,
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
                    projectStatusText = "Edit failed: ${genericEditErrorText(error)}"
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
                    projectStatusText = "Edit failed: ${genericEditErrorText(error)}"
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
                    projectStatusText = "Edit failed: ${categoryControlPointErrorText(error)}"
                }
            },
            onUpdateCategoryPhysicalStats = { categoryId, lengthMeters, climbMeters ->
                runCatching {
                    projectFile = projectSession.updateCurrentProject { currentProject ->
                        EventProjectEditor.updateCategoryPhysicalStats(
                            currentProject,
                            categoryId,
                            lengthMeters,
                            climbMeters
                        )
                    }
                    hasUnsavedChanges = projectSession.hasUnsavedChanges
                    projectStatusText = "Unsaved changes."
                }.onFailure { error ->
                    projectStatusText = "Edit failed: ${error.message ?: error::class.simpleName}"
                }
            },
            onAddCategory = { name ->
                val result = runCatching {
                    projectFile = projectSession.updateCurrentProject { currentProject ->
                        EventProjectEditor.addCategory(currentProject, UUID.randomUUID().toString(), name)
                    }
                    hasUnsavedChanges = projectSession.hasUnsavedChanges
                    projectStatusText = "Unsaved changes."
                }
                result.onFailure { error ->
                    projectStatusText = "Edit failed: ${error.message ?: error::class.simpleName}"
                }
                result.isSuccess
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
            onUpdateCompetitorClubIndex = { competitorId, club, index ->
                runCatching {
                    projectFile = projectSession.updateCurrentProject { currentProject ->
                        EventProjectEditor.updateCompetitorClubIndex(currentProject, competitorId, club, index)
                    }
                    hasUnsavedChanges = projectSession.hasUnsavedChanges
                    projectStatusText = "Unsaved changes."
                }.onFailure { error ->
                    projectStatusText = "Edit failed: ${error.message ?: error::class.simpleName}"
                }
            },
            onUpdateCompetitorBirthYear = { competitorId, birthYear ->
                runCatching {
                    projectFile = projectSession.updateCurrentProject { currentProject ->
                        EventProjectEditor.updateCompetitorBirthYear(currentProject, competitorId, birthYear)
                    }
                    hasUnsavedChanges = projectSession.hasUnsavedChanges
                    projectStatusText = "Unsaved changes."
                }.onFailure { error ->
                    projectStatusText = "Edit failed: ${error.message ?: error::class.simpleName}"
                }
            },
            onAddCompetitor = { firstName, lastName, club, index, birthYear, categoryId, startNumber, siNumber ->
                val result = runCatching {
                    projectFile = projectSession.updateCurrentProject { currentProject ->
                        val competitorId = UUID.randomUUID().toString()
                        val added = EventProjectEditor.addCompetitor(
                            projectFile = currentProject,
                            competitorId = competitorId,
                            firstName = firstName,
                            lastName = lastName,
                            startNumber = startNumber,
                            siNumber = siNumber
                        )
                        val withInfo = EventProjectEditor.updateCompetitorClubIndex(
                            added,
                            competitorId,
                            club,
                            index
                        )
                        val withBirthYear = EventProjectEditor.updateCompetitorBirthYear(
                            withInfo,
                            competitorId,
                            birthYear
                        )
                        EventProjectEditor.assignCompetitorCategory(
                            withBirthYear,
                            competitorId,
                            categoryId
                        )
                    }
                    hasUnsavedChanges = projectSession.hasUnsavedChanges
                    projectStatusText = "Unsaved changes."
                }
                result.onFailure { error ->
                    projectStatusText = "Edit failed: ${error.message ?: error::class.simpleName}"
                }
                result.isSuccess
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
            onDownloadSportIdentReadout = ::downloadSportIdentReadout,
            onStartContinuousSportIdentReadout = ::startContinuousSportIdentReadout,
            onStopContinuousSportIdentReadout = ::stopContinuousSportIdentReadout,
            onPreviewFinishTicket = { resultId ->
                runCatching {
                    val currentProject = requireNotNull(projectSession.currentProject) {
                        "Open or create a project before previewing finish tickets."
                    }
                    FinishTicketRenderer.render(currentProject.raceData, resultId)
                }.getOrElse { error ->
                    "Ticket preview failed: ${error.message ?: error::class.simpleName}"
                }
            },
            onAddManualReadout = { competitorId, siNumber, startSeconds, finishSeconds, controlCodes, resultStatus ->
                val result = runCatching {
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
                }
                result.onFailure { error ->
                    projectStatusText = "Edit failed: ${error.message ?: error::class.simpleName}"
                }
                result.isSuccess
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
                val result = runCatching {
                    projectFile = projectSession.updateCurrentProject { currentProject ->
                        EventProjectEditor.addAlias(currentProject, UUID.randomUUID().toString(), siCode, name)
                    }
                    hasUnsavedChanges = projectSession.hasUnsavedChanges
                    projectStatusText = "Unsaved changes."
                }
                result.onFailure { error ->
                    projectStatusText = "Edit failed: ${error.message ?: error::class.simpleName}"
                }
                result.isSuccess
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
private fun RadioOManagerDesktopApp(
    projectFile: EventProjectFile? = null,
    projectStatusText: String = "No project open.",
    hasUnsavedChanges: Boolean = false,
    siReaderState: DesktopSiReaderUiState = DesktopSiReaderUiState.disconnected(),
    isDownloadingSiReadout: Boolean = false,
    isContinuousSiReadoutActive: Boolean = false,
    siDownloadStatusText: String? = null,
    onRenameRace: (String) -> Unit = {},
    onUpdateRaceStartDateTime: (String) -> Unit = {},
    onUpdateRaceSettings: (RaceType, RaceLevel, RaceBand, String) -> Unit = { _, _, _, _ -> },
    onRenameCategory: (String, String) -> Unit = { _, _ -> },
    onUpdateCategoryControlPoints: (String, String) -> Unit = { _, _ -> },
    onUpdateCategoryPhysicalStats: (String, String, String) -> Unit = { _, _, _ -> },
    onAddCategory: (String) -> Boolean = { false },
    onRemoveCategory: (String, Boolean) -> Unit = { _, _ -> },
    onRenameCompetitor: (String, String, String) -> Unit = { _, _, _ -> },
    onUpdateCompetitorNumbers: (String, String, String) -> Unit = { _, _, _ -> },
    onUpdateCompetitorClubIndex: (String, String, String) -> Unit = { _, _, _ -> },
    onUpdateCompetitorBirthYear: (String, String) -> Unit = { _, _ -> },
    onAddCompetitor: (String, String, String, String, String, String?, String, String) -> Boolean = { _, _, _, _, _, _, _, _ -> false },
    onAssignCompetitorCategory: (String, String?) -> Unit = { _, _ -> },
    onRemoveCompetitor: (String, Boolean) -> Unit = { _, _ -> },
    onRemoveReadout: (String) -> Unit = {},
    onUpdateReadoutStatus: (String, ResultStatus) -> Unit = { _, _ -> },
    onDownloadSportIdentReadout: () -> Unit = {},
    onStartContinuousSportIdentReadout: () -> Unit = {},
    onStopContinuousSportIdentReadout: () -> Unit = {},
    onPreviewFinishTicket: (String) -> String = { "" },
    onAddManualReadout: (String?, String, String, String, String, ResultStatus) -> Boolean = { _, _, _, _, _, _ -> false },
    onUpdateAlias: (String, String, String) -> Unit = { _, _, _ -> },
    onAddAlias: (String, String) -> Boolean = { _, _ -> false },
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
                        onUpdateCategoryPhysicalStats = onUpdateCategoryPhysicalStats,
                        onAddCategory = onAddCategory,
                        onRemoveCategory = onRemoveCategory,
                        onRenameCompetitor = onRenameCompetitor,
                        onUpdateCompetitorNumbers = onUpdateCompetitorNumbers,
                        onUpdateCompetitorClubIndex = onUpdateCompetitorClubIndex,
                        onUpdateCompetitorBirthYear = onUpdateCompetitorBirthYear,
                        onAddCompetitor = onAddCompetitor,
                        onAssignCompetitorCategory = onAssignCompetitorCategory,
                        onRemoveCompetitor = onRemoveCompetitor,
                        onRemoveReadout = onRemoveReadout,
                        onUpdateReadoutStatus = onUpdateReadoutStatus,
                        onDownloadSportIdentReadout = onDownloadSportIdentReadout,
                        onStartContinuousSportIdentReadout = onStartContinuousSportIdentReadout,
                        onStopContinuousSportIdentReadout = onStopContinuousSportIdentReadout,
                        onPreviewFinishTicket = onPreviewFinishTicket,
                        isDownloadingSiReadout = isDownloadingSiReadout,
                        isContinuousSiReadoutActive = isContinuousSiReadoutActive,
                        siDownloadStatusText = siDownloadStatusText,
                        onAddManualReadout = onAddManualReadout,
                        onUpdateAlias = onUpdateAlias,
                        onAddAlias = onAddAlias,
                        onRemoveAlias = onRemoveAlias
                    )
                }
                StatusStrip(projectStatusText, hasUnsavedChanges, siReaderState)
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
    onUpdateCategoryPhysicalStats: (String, String, String) -> Unit,
    onAddCategory: (String) -> Boolean,
    onRemoveCategory: (String, Boolean) -> Unit,
    onRenameCompetitor: (String, String, String) -> Unit,
    onUpdateCompetitorNumbers: (String, String, String) -> Unit,
    onUpdateCompetitorClubIndex: (String, String, String) -> Unit,
    onUpdateCompetitorBirthYear: (String, String) -> Unit,
    onAddCompetitor: (String, String, String, String, String, String?, String, String) -> Boolean,
    onAssignCompetitorCategory: (String, String?) -> Unit,
    onRemoveCompetitor: (String, Boolean) -> Unit,
    onRemoveReadout: (String) -> Unit,
    onUpdateReadoutStatus: (String, ResultStatus) -> Unit,
    onDownloadSportIdentReadout: () -> Unit,
    onStartContinuousSportIdentReadout: () -> Unit,
    onStopContinuousSportIdentReadout: () -> Unit,
    onPreviewFinishTicket: (String) -> String,
    isDownloadingSiReadout: Boolean,
    isContinuousSiReadoutActive: Boolean,
    siDownloadStatusText: String?,
    onAddManualReadout: (String?, String, String, String, String, ResultStatus) -> Boolean,
    onUpdateAlias: (String, String, String) -> Unit,
    onAddAlias: (String, String) -> Boolean,
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
                onUpdateCategoryPhysicalStats = onUpdateCategoryPhysicalStats,
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
                onUpdateCompetitorClubIndex = onUpdateCompetitorClubIndex,
                onUpdateCompetitorBirthYear = onUpdateCompetitorBirthYear,
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
                onDownloadSportIdentReadout = onDownloadSportIdentReadout,
                onStartContinuousSportIdentReadout = onStartContinuousSportIdentReadout,
                onStopContinuousSportIdentReadout = onStopContinuousSportIdentReadout,
                onPreviewFinishTicket = onPreviewFinishTicket,
                isDownloadingSiReadout = isDownloadingSiReadout,
                isContinuousSiReadoutActive = isContinuousSiReadoutActive,
                siDownloadStatusText = siDownloadStatusText,
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
        DetailRow("Live results", diagnostics.liveResultPlanText)
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
    val horizontalScrollState = rememberScrollState()
    val tableWidth = fixedTableWidth(ResultTableColumns)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.fillMaxWidth().horizontalScroll(horizontalScrollState)) {
            Column(
                modifier = Modifier.width(tableWidth),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FixedDetailHeaderRow(ResultTableColumns)
                results.forEach { result ->
                    ResultDetailRow(result, onUpdateReadoutStatus)
                }
            }
        }
        HorizontalScrollbar(
            adapter = rememberScrollbarAdapter(horizontalScrollState),
            modifier = Modifier.fillMaxWidth()
        )
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
        modifier = Modifier.width(fixedTableWidth(ResultTableColumns)),
        horizontalArrangement = Arrangement.spacedBy(TableColumnGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FixedTableText(result.placeText, ResultTableColumns[0].width)
        FixedTableText(result.competitorName, ResultTableColumns[1].width)
        ResultStatusPicker(
            selectedStatus = selectedStatus,
            onStatusSelected = { selectedStatus = it },
            modifier = Modifier.width(ResultTableColumns[2].width)
        )
        FixedTableText(result.pointsText, ResultTableColumns[3].width)
        FixedTableText(result.runTimeText, ResultTableColumns[4].width)
        Button(
            onClick = { onUpdateReadoutStatus(result.id, selectedStatus) },
            modifier = Modifier.width(ResultTableColumns[5].width),
            enabled = selectedStatus != result.resultStatus || result.automaticStatus
        ) {
            ButtonLabel("Status")
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
    onDownloadSportIdentReadout: () -> Unit,
    onStartContinuousSportIdentReadout: () -> Unit,
    onStopContinuousSportIdentReadout: () -> Unit,
    onPreviewFinishTicket: (String) -> String,
    isDownloadingSiReadout: Boolean,
    isContinuousSiReadoutActive: Boolean,
    siDownloadStatusText: String?,
    onAddManualReadout: (String?, String, String, String, String, ResultStatus) -> Boolean
) {
    val horizontalScrollState = rememberScrollState()
    val tableWidth = fixedTableWidth(ReadoutTableColumns)
    var selectedCompetitorId by remember { mutableStateOf<String?>(null) }
    var siNumberDraft by remember { mutableStateOf("") }
    var startSecondsDraft by remember { mutableStateOf("") }
    var finishSecondsDraft by remember { mutableStateOf("") }
    var controlCodesDraft by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf(ResultStatus.OK) }
    var ticketPreviewText by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TableColumnGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onDownloadSportIdentReadout,
                enabled = !isDownloadingSiReadout && !isContinuousSiReadoutActive
            ) {
                ButtonLabel(if (isDownloadingSiReadout) "Waiting" else "Download SI")
            }
            Button(
                onClick = onStartContinuousSportIdentReadout,
                enabled = !isDownloadingSiReadout && !isContinuousSiReadoutActive
            ) {
                ButtonLabel("Start SI")
            }
            Button(
                onClick = onStopContinuousSportIdentReadout,
                enabled = isContinuousSiReadoutActive
            ) {
                ButtonLabel("Stop SI")
            }
            Text(
                text = siDownloadStatusText ?: "Download SI5/SI6/SI8/SI9/SIAC cards from an attached READOUT/SI MASTER station.",
                color = DesktopPalette.Black,
                fontSize = 13.sp
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TableColumnGap),
            verticalAlignment = Alignment.Top
        ) {
            ManualReadoutAddButton(
                selectedCompetitorId = selectedCompetitorId,
                siNumberDraft = siNumberDraft,
                startSecondsDraft = startSecondsDraft,
                finishSecondsDraft = finishSecondsDraft,
                controlCodesDraft = controlCodesDraft,
                selectedStatus = selectedStatus,
                onAddManualReadout = onAddManualReadout,
                onManualReadoutAdded = {
                    selectedCompetitorId = null
                    siNumberDraft = ""
                    startSecondsDraft = ""
                    finishSecondsDraft = ""
                    controlCodesDraft = ""
                    selectedStatus = ResultStatus.OK
                },
                modifier = fixedActionRailModifier().offset(y = ReadoutAddRailYOffset)
            )
            Box(modifier = Modifier.weight(1f).horizontalScroll(horizontalScrollState)) {
                Column(
                    modifier = Modifier.width(tableWidth),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ManualReadoutAddRow(
                        competitors = competitors,
                        selectedCompetitorId = selectedCompetitorId,
                        onCompetitorSelected = { selectedCompetitorId = it },
                        siNumberDraft = siNumberDraft,
                        onSiNumberChange = { siNumberDraft = it },
                        startSecondsDraft = startSecondsDraft,
                        onStartSecondsChange = { startSecondsDraft = it },
                        finishSecondsDraft = finishSecondsDraft,
                        onFinishSecondsChange = { finishSecondsDraft = it },
                        controlCodesDraft = controlCodesDraft,
                        onControlCodesChange = { controlCodesDraft = it },
                        selectedStatus = selectedStatus,
                        onStatusSelected = { selectedStatus = it }
                    )
                    FixedDetailHeaderRow(ReadoutTableColumns)
                }
            }
        }
        HorizontalScrollbar(
            adapter = rememberScrollbarAdapter(horizontalScrollState),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TableColumnGap),
            verticalAlignment = Alignment.Top
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                readouts.forEach { readout ->
                    ReadoutDeleteButton(readout, onRemoveReadout)
                }
            }
            Box(modifier = Modifier.weight(1f).horizontalScroll(horizontalScrollState)) {
                Column(
                    modifier = Modifier.width(tableWidth),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    readouts.forEach { readout ->
                        ReadoutDetailRow(
                            readout = readout,
                            onUpdateReadoutStatus = onUpdateReadoutStatus,
                            onPreviewFinishTicket = { ticketPreviewText = onPreviewFinishTicket(readout.id) }
                        )
                    }
                }
            }
        }
        ticketPreviewText?.let { previewText ->
            FinishTicketPreviewDialog(
                text = previewText,
                onDismiss = { ticketPreviewText = null }
            )
        }
    }
}

/** Shows a compact manual readout entry row for desktop beta testing. */
@Composable
private fun ManualReadoutAddButton(
    selectedCompetitorId: String?,
    siNumberDraft: String,
    startSecondsDraft: String,
    finishSecondsDraft: String,
    controlCodesDraft: String,
    selectedStatus: ResultStatus,
    onAddManualReadout: (String?, String, String, String, String, ResultStatus) -> Boolean,
    onManualReadoutAdded: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = {
            val didAdd = onAddManualReadout(
                selectedCompetitorId,
                siNumberDraft,
                startSecondsDraft,
                finishSecondsDraft,
                controlCodesDraft,
                selectedStatus
            )
            if (didAdd) {
                onManualReadoutAdded()
            }
        },
        modifier = modifier,
        enabled = selectedCompetitorId != null ||
                siNumberDraft.isNotBlank() ||
                startSecondsDraft.isNotBlank() ||
                finishSecondsDraft.isNotBlank() ||
                controlCodesDraft.isNotBlank()
    ) {
        ButtonLabel("Add")
    }
}

/** Shows a compact manual readout entry row for desktop beta testing. */
@Composable
private fun ManualReadoutAddRow(
    competitors: List<EventCompetitorDetails>,
    selectedCompetitorId: String?,
    onCompetitorSelected: (String?) -> Unit,
    siNumberDraft: String,
    onSiNumberChange: (String) -> Unit,
    startSecondsDraft: String,
    onStartSecondsChange: (String) -> Unit,
    finishSecondsDraft: String,
    onFinishSecondsChange: (String) -> Unit,
    controlCodesDraft: String,
    onControlCodesChange: (String) -> Unit,
    selectedStatus: ResultStatus,
    onStatusSelected: (ResultStatus) -> Unit
) {
    Row(
        modifier = Modifier.width(fixedTableWidth(ReadoutTableColumns)),
        horizontalArrangement = Arrangement.spacedBy(TableColumnGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = siNumberDraft,
            onValueChange = onSiNumberChange,
            modifier = Modifier.width(ReadoutTableColumns[0].width),
            singleLine = true,
            label = { Text("SI") }
        )
        CompetitorPicker(
            selectedCompetitorId = selectedCompetitorId,
            competitors = competitors,
            onCompetitorSelected = onCompetitorSelected,
            modifier = Modifier.width(ReadoutTableColumns[1].width)
        )
        ResultStatusPicker(
            selectedStatus = selectedStatus,
            onStatusSelected = onStatusSelected,
            modifier = Modifier.width(ReadoutTableColumns[2].width)
        )
        TextField(
            value = startSecondsDraft,
            onValueChange = onStartSecondsChange,
            modifier = Modifier.width(ReadoutTableColumns[3].width),
            singleLine = true,
            label = { Text("Start s") }
        )
        TextField(
            value = finishSecondsDraft,
            onValueChange = onFinishSecondsChange,
            modifier = Modifier.width(ReadoutTableColumns[4].width),
            singleLine = true,
            label = { Text("Finish s") }
        )
        TextField(
            value = controlCodesDraft,
            onValueChange = onControlCodesChange,
            modifier = Modifier.width(ReadoutTableColumns[5].width),
            singleLine = true,
            label = { Text("Controls") }
        )
        Spacer(modifier = Modifier.width(ReadoutTableColumns[6].width))
        Spacer(modifier = Modifier.width(ReadoutTableColumns[7].width))
    }
}

/** Shows one readout row with deletion routed through shared project-editing rules. */
@Composable
private fun ReadoutDetailRow(
    readout: EventReadoutDetails,
    onUpdateReadoutStatus: (String, ResultStatus) -> Unit,
    onPreviewFinishTicket: () -> Unit
) {
    var selectedStatus by remember(readout.id, readout.resultStatus) { mutableStateOf(readout.resultStatus) }

    Row(
        modifier = Modifier.width(fixedTableWidth(ReadoutTableColumns)),
        horizontalArrangement = Arrangement.spacedBy(TableColumnGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FixedTableText(readout.siNumberText, ReadoutTableColumns[0].width)
        FixedTableText(readout.competitorName, ReadoutTableColumns[1].width)
        ResultStatusPicker(
            selectedStatus = selectedStatus,
            onStatusSelected = { selectedStatus = it },
            modifier = Modifier.width(ReadoutTableColumns[2].width)
        )
        FixedTableText(readout.pointsText, ReadoutTableColumns[3].width)
        FixedTableText(readout.runTimeText, ReadoutTableColumns[4].width)
        FixedTableText(readout.punchCodesText, ReadoutTableColumns[5].width)
        Button(
            onClick = { onUpdateReadoutStatus(readout.id, selectedStatus) },
            modifier = Modifier.width(ReadoutTableColumns[6].width),
            enabled = selectedStatus != readout.resultStatus || readout.automaticStatus
        ) {
            ButtonLabel("Status")
        }
        Button(
            onClick = onPreviewFinishTicket,
            modifier = Modifier.width(ReadoutTableColumns[7].width)
        ) {
            ButtonLabel("Ticket")
        }
    }
}

@Composable
private fun FinishTicketPreviewDialog(
    text: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Finish ticket preview") },
        text = {
            Text(
                text = text,
                color = DesktopPalette.Black,
                fontSize = 13.sp
            )
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

@Composable
private fun ReadoutDeleteButton(
    readout: EventReadoutDetails,
    onRemoveReadout: (String) -> Unit
) {
    var showDeleteDialog by remember(readout.id) { mutableStateOf(false) }

    Button(
        onClick = { showDeleteDialog = true },
        modifier = fixedActionRailModifier()
    ) {
        ButtonLabel("Delete")
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
    onUpdateCompetitorClubIndex: (String, String, String) -> Unit,
    onUpdateCompetitorBirthYear: (String, String) -> Unit,
    onAddCompetitor: (String, String, String, String, String, String?, String, String) -> Boolean,
    onAssignCompetitorCategory: (String, String?) -> Unit,
    onRemoveCompetitor: (String, Boolean) -> Unit
) {
    val horizontalScrollState = rememberScrollState()
    val tableWidth = fixedTableWidth(CompetitorTableColumns)
    val nextStartNumber = remember(competitors) { nextCompetitorStartNumber(competitors) }
    var firstNameDraft by remember { mutableStateOf("") }
    var lastNameDraft by remember { mutableStateOf("") }
    var clubDraft by remember { mutableStateOf("") }
    var indexDraft by remember { mutableStateOf("") }
    var birthYearDraft by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var startNumberDraft by remember(nextStartNumber) { mutableStateOf(nextStartNumber) }
    var siNumberDraft by remember { mutableStateOf("") }
    val canAddCompetitor = firstNameDraft.isNotBlank() &&
            lastNameDraft.isNotBlank() &&
            startNumberDraft.isNotBlank()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TableColumnGap),
            verticalAlignment = Alignment.Top
        ) {
            Button(
                onClick = {
                    val didAdd = onAddCompetitor(
                        firstNameDraft,
                        lastNameDraft,
                        clubDraft,
                        indexDraft,
                        birthYearDraft,
                        selectedCategoryId,
                        startNumberDraft,
                        siNumberDraft
                    )
                    if (didAdd) {
                        firstNameDraft = ""
                        lastNameDraft = ""
                        clubDraft = ""
                        indexDraft = ""
                        birthYearDraft = ""
                        selectedCategoryId = null
                        startNumberDraft = nextCompetitorStartNumber(competitors)
                        siNumberDraft = ""
                    }
                },
                modifier = fixedActionRailModifier(),
                enabled = canAddCompetitor
            ) {
                ButtonLabel("Add")
            }
            Box(modifier = Modifier.weight(1f).horizontalScroll(horizontalScrollState)) {
                Column(
                    modifier = Modifier.width(tableWidth),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CompetitorAddRow(
                        categories = categories,
                        firstNameDraft = firstNameDraft,
                        onFirstNameChange = { firstNameDraft = it },
                        lastNameDraft = lastNameDraft,
                        onLastNameChange = { lastNameDraft = it },
                        clubDraft = clubDraft,
                        onClubChange = { clubDraft = it },
                        indexDraft = indexDraft,
                        onIndexChange = { indexDraft = it },
                        birthYearDraft = birthYearDraft,
                        onBirthYearChange = { birthYearDraft = it },
                        selectedCategoryId = selectedCategoryId,
                        onCategorySelected = { selectedCategoryId = it },
                        startNumberDraft = startNumberDraft,
                        onStartNumberChange = { startNumberDraft = it },
                        siNumberDraft = siNumberDraft,
                        onSiNumberChange = { siNumberDraft = it }
                    )
                    FixedDetailHeaderRow(CompetitorTableColumns)
                }
            }
        }
        HorizontalScrollbar(
            adapter = rememberScrollbarAdapter(horizontalScrollState),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TableColumnGap),
            verticalAlignment = Alignment.Top
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                competitors.forEach { competitor ->
                    CompetitorDeleteButton(competitor, onRemoveCompetitor)
                }
            }
            Box(modifier = Modifier.weight(1f).horizontalScroll(horizontalScrollState)) {
                Column(
                    modifier = Modifier.width(tableWidth),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    competitors.forEach { competitor ->
                        CompetitorDetailRow(
                            competitor = competitor,
                            categories = categories,
                            onRenameCompetitor = onRenameCompetitor,
                            onUpdateCompetitorNumbers = onUpdateCompetitorNumbers,
                            onUpdateCompetitorClubIndex = onUpdateCompetitorClubIndex,
                            onUpdateCompetitorBirthYear = onUpdateCompetitorBirthYear,
                            onAssignCompetitorCategory = onAssignCompetitorCategory
                        )
                    }
                }
            }
        }
    }
}

/** Shows the new-competitor entry row above existing competitor definitions. */
@Composable
private fun CompetitorAddRow(
    categories: List<EventCategoryDetails>,
    firstNameDraft: String,
    onFirstNameChange: (String) -> Unit,
    lastNameDraft: String,
    onLastNameChange: (String) -> Unit,
    clubDraft: String,
    onClubChange: (String) -> Unit,
    indexDraft: String,
    onIndexChange: (String) -> Unit,
    birthYearDraft: String,
    onBirthYearChange: (String) -> Unit,
    selectedCategoryId: String?,
    onCategorySelected: (String?) -> Unit,
    startNumberDraft: String,
    onStartNumberChange: (String) -> Unit,
    siNumberDraft: String,
    onSiNumberChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.width(fixedTableWidth(CompetitorTableColumns)),
        horizontalArrangement = Arrangement.spacedBy(TableColumnGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = firstNameDraft,
            onValueChange = onFirstNameChange,
            modifier = Modifier.width(CompetitorTableColumns[0].width),
            singleLine = true,
            label = { Text("First") }
        )
        TextField(
            value = lastNameDraft,
            onValueChange = onLastNameChange,
            modifier = Modifier.width(CompetitorTableColumns[1].width),
            singleLine = true,
            label = { Text("Last") }
        )
        TextField(
            value = clubDraft,
            onValueChange = onClubChange,
            modifier = Modifier.width(CompetitorTableColumns[2].width),
            singleLine = true,
            label = { Text("Club") }
        )
        TextField(
            value = indexDraft,
            onValueChange = onIndexChange,
            modifier = Modifier.width(CompetitorTableColumns[3].width),
            singleLine = true,
            label = { Text("Index") }
        )
        TextField(
            value = birthYearDraft,
            onValueChange = onBirthYearChange,
            modifier = Modifier.width(CompetitorTableColumns[4].width),
            singleLine = true,
            label = { Text("Birth") }
        )
        CategoryPicker(
            selectedCategoryId = selectedCategoryId,
            categories = categories,
            onCategorySelected = onCategorySelected,
            modifier = Modifier.width(CompetitorTableColumns[5].width)
        )
        TextField(
            value = startNumberDraft,
            onValueChange = onStartNumberChange,
            modifier = Modifier.width(CompetitorTableColumns[6].width),
            singleLine = true,
            label = { Text("Start") }
        )
        TextField(
            value = siNumberDraft,
            onValueChange = onSiNumberChange,
            modifier = Modifier.width(CompetitorTableColumns[7].width),
            singleLine = true,
            label = { Text("SI") }
        )
        Spacer(modifier = Modifier.width(CompetitorTableColumns[8].width))
        Spacer(modifier = Modifier.width(CompetitorTableColumns[9].width))
        Spacer(modifier = Modifier.width(CompetitorTableColumns[10].width))
        Spacer(modifier = Modifier.width(CompetitorTableColumns[11].width))
        Spacer(modifier = Modifier.width(CompetitorTableColumns[12].width))
    }
}

/** Shows one editable competitor-name row plus read-only assignment fields. */
@Composable
private fun CompetitorDetailRow(
    competitor: EventCompetitorDetails,
    categories: List<EventCategoryDetails>,
    onRenameCompetitor: (String, String, String) -> Unit,
    onUpdateCompetitorNumbers: (String, String, String) -> Unit,
    onUpdateCompetitorClubIndex: (String, String, String) -> Unit,
    onUpdateCompetitorBirthYear: (String, String) -> Unit,
    onAssignCompetitorCategory: (String, String?) -> Unit
) {
    var firstNameDraft by remember(competitor.id, competitor.firstName) { mutableStateOf(competitor.firstName) }
    var lastNameDraft by remember(competitor.id, competitor.lastName) { mutableStateOf(competitor.lastName) }
    var clubDraft by remember(competitor.id, competitor.club) { mutableStateOf(competitor.club) }
    var indexDraft by remember(competitor.id, competitor.index) { mutableStateOf(competitor.index) }
    var birthYearDraft by remember(competitor.id, competitor.birthYearText) {
        mutableStateOf(competitor.birthYearText)
    }
    var startNumberDraft by remember(competitor.id, competitor.startNumberText) {
        mutableStateOf(competitor.startNumberText)
    }
    var siNumberDraft by remember(competitor.id, competitor.siNumberText) { mutableStateOf(competitor.siNumberText) }
    var selectedCategoryId by remember(competitor.id, competitor.categoryId) { mutableStateOf(competitor.categoryId) }
    Row(
        modifier = Modifier.width(fixedTableWidth(CompetitorTableColumns)),
        horizontalArrangement = Arrangement.spacedBy(TableColumnGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = firstNameDraft,
            onValueChange = { firstNameDraft = it },
            modifier = Modifier.width(CompetitorTableColumns[0].width),
            singleLine = true,
            label = { Text("First") }
        )
        TextField(
            value = lastNameDraft,
            onValueChange = { lastNameDraft = it },
            modifier = Modifier.width(CompetitorTableColumns[1].width),
            singleLine = true,
            label = { Text("Last") }
        )
        TextField(
            value = clubDraft,
            onValueChange = { clubDraft = it },
            modifier = Modifier.width(CompetitorTableColumns[2].width),
            singleLine = true,
            label = { Text("Club") }
        )
        TextField(
            value = indexDraft,
            onValueChange = { indexDraft = it },
            modifier = Modifier.width(CompetitorTableColumns[3].width),
            singleLine = true,
            label = { Text("Index") }
        )
        TextField(
            value = birthYearDraft,
            onValueChange = { birthYearDraft = it },
            modifier = Modifier.width(CompetitorTableColumns[4].width),
            singleLine = true,
            label = { Text("Birth") }
        )
        CategoryPicker(
            selectedCategoryId = selectedCategoryId,
            categories = categories,
            onCategorySelected = { selectedCategoryId = it },
            modifier = Modifier.width(CompetitorTableColumns[5].width)
        )
        TextField(
            value = startNumberDraft,
            onValueChange = { startNumberDraft = it },
            modifier = Modifier.width(CompetitorTableColumns[6].width),
            singleLine = true,
            label = { Text("Start") }
        )
        TextField(
            value = siNumberDraft,
            onValueChange = { siNumberDraft = it },
            modifier = Modifier.width(CompetitorTableColumns[7].width),
            singleLine = true,
            label = { Text("SI") }
        )
        Button(
            onClick = { onRenameCompetitor(competitor.id, firstNameDraft, lastNameDraft) },
            modifier = Modifier.width(CompetitorTableColumns[8].width),
            enabled = firstNameDraft != competitor.firstName || lastNameDraft != competitor.lastName
        ) {
            ButtonLabel("Name")
        }
        Button(
            onClick = { onUpdateCompetitorNumbers(competitor.id, startNumberDraft, siNumberDraft) },
            modifier = Modifier.width(CompetitorTableColumns[9].width),
            enabled = startNumberDraft != competitor.startNumberText || siNumberDraft != competitor.siNumberText
        ) {
            ButtonLabel("Nos.")
        }
        Button(
            onClick = { onUpdateCompetitorClubIndex(competitor.id, clubDraft, indexDraft) },
            modifier = Modifier.width(CompetitorTableColumns[10].width),
            enabled = clubDraft != competitor.club || indexDraft != competitor.index
        ) {
            ButtonLabel("Info")
        }
        Button(
            onClick = { onUpdateCompetitorBirthYear(competitor.id, birthYearDraft) },
            modifier = Modifier.width(CompetitorTableColumns[11].width),
            enabled = birthYearDraft != competitor.birthYearText
        ) {
            ButtonLabel("Birth")
        }
        Button(
            onClick = { onAssignCompetitorCategory(competitor.id, selectedCategoryId) },
            modifier = Modifier.width(CompetitorTableColumns[12].width),
            enabled = selectedCategoryId != competitor.categoryId
        ) {
            ButtonLabel("Cat.")
        }
    }
}

@Composable
private fun CompetitorDeleteButton(
    competitor: EventCompetitorDetails,
    onRemoveCompetitor: (String, Boolean) -> Unit
) {
    var showDeleteDialog by remember(competitor.id) { mutableStateOf(false) }

    Button(
        onClick = { showDeleteDialog = true },
        modifier = fixedActionRailModifier()
    ) {
        ButtonLabel("Delete")
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
    onAddAlias: (String, String) -> Boolean,
    onRemoveAlias: (String) -> Unit
) {
    val horizontalScrollState = rememberScrollState()
    val tableWidth = fixedTableWidth(AliasTableColumns)
    var siCodeDraft by remember { mutableStateOf("") }
    var nameDraft by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TableColumnGap),
            verticalAlignment = Alignment.Top
        ) {
            Button(
                onClick = {
                    val didAdd = onAddAlias(siCodeDraft, nameDraft)
                    if (didAdd) {
                        siCodeDraft = ""
                        nameDraft = ""
                    }
                },
                modifier = fixedActionRailModifier(),
                enabled = siCodeDraft.isNotBlank() || nameDraft.isNotBlank()
            ) {
                ButtonLabel("Add")
            }
            Box(modifier = Modifier.weight(1f).horizontalScroll(horizontalScrollState)) {
                Column(
                    modifier = Modifier.width(tableWidth),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AliasAddRow(
                        siCodeDraft = siCodeDraft,
                        onSiCodeChange = { siCodeDraft = it },
                        nameDraft = nameDraft,
                        onNameChange = { nameDraft = it }
                    )
                    FixedDetailHeaderRow(AliasTableColumns)
                }
            }
        }
        HorizontalScrollbar(
            adapter = rememberScrollbarAdapter(horizontalScrollState),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TableColumnGap),
            verticalAlignment = Alignment.Top
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                aliases.forEach { alias ->
                    AliasDeleteButton(alias, onRemoveAlias)
                }
            }
            Box(modifier = Modifier.weight(1f).horizontalScroll(horizontalScrollState)) {
                Column(
                    modifier = Modifier.width(tableWidth),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    aliases.forEach { alias ->
                        AliasDetailRow(alias, onUpdateAlias)
                    }
                }
            }
        }
    }
}

/** Shows the new-alias entry row above the existing alias mappings. */
@Composable
private fun AliasAddRow(
    siCodeDraft: String,
    onSiCodeChange: (String) -> Unit,
    nameDraft: String,
    onNameChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.width(fixedTableWidth(AliasTableColumns)),
        horizontalArrangement = Arrangement.spacedBy(TableColumnGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = siCodeDraft,
            onValueChange = onSiCodeChange,
            modifier = Modifier.width(AliasTableColumns[0].width),
            singleLine = true,
            label = { Text("New SI code") }
        )
        TextField(
            value = nameDraft,
            onValueChange = onNameChange,
            modifier = Modifier.width(AliasTableColumns[1].width),
            singleLine = true,
            label = { Text("New alias") }
        )
        Spacer(modifier = Modifier.width(AliasTableColumns[2].width))
    }
}

/** Shows one editable alias row for a SportIdent control code mapping. */
@Composable
private fun AliasDetailRow(
    alias: EventAliasDetails,
    onUpdateAlias: (String, String, String) -> Unit
) {
    var siCodeDraft by remember(alias.id, alias.siCodeText) { mutableStateOf(alias.siCodeText) }
    var nameDraft by remember(alias.id, alias.name) { mutableStateOf(alias.name) }

    Row(
        modifier = Modifier.width(fixedTableWidth(AliasTableColumns)),
        horizontalArrangement = Arrangement.spacedBy(TableColumnGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = siCodeDraft,
            onValueChange = { siCodeDraft = it },
            modifier = Modifier.width(AliasTableColumns[0].width),
            singleLine = true,
            label = { Text("SI code") }
        )
        TextField(
            value = nameDraft,
            onValueChange = { nameDraft = it },
            modifier = Modifier.width(AliasTableColumns[1].width),
            singleLine = true,
            label = { Text("Alias") }
        )
        Button(
            onClick = { onUpdateAlias(alias.id, siCodeDraft, nameDraft) },
            modifier = Modifier.width(AliasTableColumns[2].width),
            enabled = siCodeDraft != alias.siCodeText || nameDraft != alias.name
        ) {
            ButtonLabel("Apply")
        }
    }
}

@Composable
private fun AliasDeleteButton(
    alias: EventAliasDetails,
    onRemoveAlias: (String) -> Unit
) {
    var showDeleteDialog by remember(alias.id) { mutableStateOf(false) }

    Button(
        onClick = { showDeleteDialog = true },
        modifier = fixedActionRailModifier()
    ) {
        ButtonLabel("Delete")
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete alias") },
            text = { Text("Delete alias ${alias.siCodeText} -> ${alias.name}?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onRemoveAlias(alias.id)
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

/** Shows editable category names with read-only effective race settings. */
@Composable
private fun CategoryDetailsPanel(
    categories: List<EventCategoryDetails>,
    onRenameCategory: (String, String) -> Unit,
    onUpdateCategoryControlPoints: (String, String) -> Unit,
    onUpdateCategoryPhysicalStats: (String, String, String) -> Unit,
    onAddCategory: (String) -> Boolean,
    onRemoveCategory: (String, Boolean) -> Unit
) {
    val horizontalScrollState = rememberScrollState()
    val tableWidth = fixedTableWidth(CategoryTableColumns)
    var categoryNameDraft by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TableColumnGap),
            verticalAlignment = Alignment.Top
        ) {
            Button(
                onClick = {
                    val didAdd = onAddCategory(categoryNameDraft)
                    if (didAdd) {
                        categoryNameDraft = ""
                    }
                },
                modifier = fixedActionRailModifier(),
                enabled = categoryNameDraft.isNotBlank()
            ) {
                ButtonLabel("Add")
            }
            Box(modifier = Modifier.weight(1f).horizontalScroll(horizontalScrollState)) {
                Column(
                    modifier = Modifier.width(tableWidth),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CategoryAddRow(
                        categoryNameDraft = categoryNameDraft,
                        onCategoryNameChange = { categoryNameDraft = it }
                    )
                    FixedDetailHeaderRow(CategoryTableColumns)
                }
            }
        }
        HorizontalScrollbar(
            adapter = rememberScrollbarAdapter(horizontalScrollState),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TableColumnGap),
            verticalAlignment = Alignment.Top
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { category ->
                    CategoryDeleteButton(category, onRemoveCategory)
                }
            }
            Box(modifier = Modifier.weight(1f).horizontalScroll(horizontalScrollState)) {
                Column(
                    modifier = Modifier.width(tableWidth),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { category ->
                        CategoryDetailRow(
                            category,
                            onRenameCategory,
                            onUpdateCategoryControlPoints,
                            onUpdateCategoryPhysicalStats
                        )
                    }
                }
            }
        }
    }
}

/** Shows the new-category entry row above existing category definitions. */
@Composable
private fun CategoryAddRow(
    categoryNameDraft: String,
    onCategoryNameChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.width(fixedTableWidth(CategoryTableColumns)),
        horizontalArrangement = Arrangement.spacedBy(TableColumnGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = categoryNameDraft,
            onValueChange = onCategoryNameChange,
            modifier = Modifier.width(CategoryTableColumns[0].width),
            singleLine = true,
            label = { Text("New category") }
        )
        Spacer(modifier = Modifier.width(CategoryTableColumns[1].width))
        Spacer(modifier = Modifier.width(CategoryTableColumns[2].width))
        Spacer(modifier = Modifier.width(CategoryTableColumns[3].width))
        Spacer(modifier = Modifier.width(CategoryTableColumns[4].width))
        Spacer(modifier = Modifier.width(CategoryTableColumns[5].width))
        Spacer(modifier = Modifier.width(CategoryTableColumns[6].width))
        Spacer(modifier = Modifier.width(CategoryTableColumns[7].width))
        Spacer(modifier = Modifier.width(CategoryTableColumns[8].width))
        Spacer(modifier = Modifier.width(CategoryTableColumns[9].width))
    }
}

/** Shows one editable category-name row plus read-only derived category settings. */
@Composable
private fun CategoryDetailRow(
    category: EventCategoryDetails,
    onRenameCategory: (String, String) -> Unit,
    onUpdateCategoryControlPoints: (String, String) -> Unit,
    onUpdateCategoryPhysicalStats: (String, String, String) -> Unit
) {
    var categoryNameDraft by remember(category.id, category.name) { mutableStateOf(category.name) }
    var lengthMetersDraft by remember(category.id, category.lengthMetersText) {
        mutableStateOf(category.lengthMetersText)
    }
    var climbMetersDraft by remember(category.id, category.climbMetersText) {
        mutableStateOf(category.climbMetersText)
    }
    var controlPointsDraft by remember(category.id, category.controlPointsText) {
        mutableStateOf(category.controlPointsText)
    }
    Row(
        modifier = Modifier.width(fixedTableWidth(CategoryTableColumns)),
        horizontalArrangement = Arrangement.spacedBy(TableColumnGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = categoryNameDraft,
            onValueChange = { categoryNameDraft = it },
            modifier = Modifier.width(CategoryTableColumns[0].width),
            singleLine = true,
            label = { Text("Category") }
        )
        TextField(
            value = lengthMetersDraft,
            onValueChange = { lengthMetersDraft = it },
            modifier = Modifier.width(CategoryTableColumns[1].width),
            singleLine = true,
            label = { Text("Length") }
        )
        TextField(
            value = climbMetersDraft,
            onValueChange = { climbMetersDraft = it },
            modifier = Modifier.width(CategoryTableColumns[2].width),
            singleLine = true,
            label = { Text("Climb") }
        )
        Text(
            category.raceTypeLabel,
            modifier = Modifier.width(CategoryTableColumns[3].width),
            color = DesktopPalette.Black,
            fontSize = 13.sp
        )
        Text(
            category.raceBandLabel,
            modifier = Modifier.width(CategoryTableColumns[4].width),
            color = DesktopPalette.Black,
            fontSize = 13.sp
        )
        Text(
            category.timeLimitText,
            modifier = Modifier.width(CategoryTableColumns[5].width),
            color = DesktopPalette.Black,
            fontSize = 13.sp
        )
        TextField(
            value = controlPointsDraft,
            onValueChange = { controlPointsDraft = it },
            modifier = Modifier.width(CategoryTableColumns[6].width),
            singleLine = true,
            label = { Text("Controls") }
        )
        Button(
            onClick = { onRenameCategory(category.id, categoryNameDraft) },
            modifier = Modifier.width(CategoryTableColumns[7].width),
            enabled = categoryNameDraft != category.name
        ) {
            ButtonLabel("Apply")
        }
        Button(
            onClick = {
                onUpdateCategoryPhysicalStats(category.id, lengthMetersDraft, climbMetersDraft)
            },
            modifier = Modifier.width(CategoryTableColumns[8].width),
            enabled = lengthMetersDraft != category.lengthMetersText ||
                    climbMetersDraft != category.climbMetersText
        ) {
            ButtonLabel("Stats")
        }
        Button(
            onClick = { onUpdateCategoryControlPoints(category.id, controlPointsDraft) },
            modifier = Modifier.width(CategoryTableColumns[9].width),
            enabled = controlPointsDraft != category.controlPointsText
        ) {
            ButtonLabel("Ctrls")
        }
    }
}

@Composable
private fun CategoryDeleteButton(
    category: EventCategoryDetails,
    onRemoveCategory: (String, Boolean) -> Unit
) {
    var showDeleteDialog by remember(category.id) { mutableStateOf(false) }

    Button(
        onClick = { showDeleteDialog = true },
        modifier = fixedActionRailModifier()
    ) {
        ButtonLabel("Delete")
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

/** Displays a fixed-width header row for horizontally scrollable desktop detail grids. */
@Composable
private fun FixedDetailHeaderRow(columns: List<FixedTableColumn>) {
    Row(
        modifier = Modifier.width(fixedTableWidth(columns)),
        horizontalArrangement = Arrangement.spacedBy(TableColumnGap)
    ) {
        columns.forEach { column ->
            Text(
                text = column.title,
                modifier = Modifier.width(column.width),
                color = DesktopPalette.Disconnected,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ButtonLabel(text: String) {
    Text(
        text = text,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Visible
    )
}

@Composable
private fun FixedTableText(text: String, width: Dp) {
    Text(
        text = text,
        modifier = Modifier.width(width),
        color = DesktopPalette.Black,
        fontSize = 13.sp,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis
    )
}

private fun fixedActionRailModifier(): Modifier =
    Modifier
        .width(ActionRailWidth)
        .height(FixedGridRowHeight)

private fun categoryControlPointErrorText(error: Throwable): String =
    if (error is ControlPointValidationException) {
        DesktopControlPointValidationText.messageFor(error)
    } else {
        genericEditErrorText(error)
    }

private fun genericEditErrorText(error: Throwable): String =
    error.message ?: error::class.simpleName ?: "Unknown error"

private fun fixedTableWidth(columns: List<FixedTableColumn>): Dp =
    columns.fold(0.dp) { total, column -> total + column.width } +
            TableColumnGap * (columns.size - 1)

private fun nextCompetitorStartNumber(competitors: List<EventCompetitorDetails>): String =
    ((competitors.maxOfOrNull { it.startNumber } ?: 0) + 1).toString()

private fun importStatusText(action: String, importedRows: Int, invalidRows: Int, fileName: String): String =
    if (invalidRows == 0) {
        "$action $importedRows rows from $fileName."
    } else {
        "$action $importedRows rows from $fileName; skipped $invalidRows invalid rows."
    }

private fun detectDesktopSiReaderState(): DesktopSiReaderUiState {
    val provider = JSerialCommDesktopSerialPortProvider
    val port = provider.listPorts().firstOrNull { it.info.matchesSportIdent() }
        ?: return DesktopSiReaderUiState.disconnected()

    return runCatching {
        val connection = DesktopSportIdentStationProbe().connect(port)
        val stationInfo = connection.stationInfo
        val modeLabel = stationInfo.stationModeLabel ?: "unknown"
        when (stationInfo.isReadoutMode) {
            false -> {
                val statusText = "SI station ${stationInfo.serialNumber} is in $modeLabel mode"
                DesktopSiReaderUiState(
                    severity = DesktopSiReaderSeverity.WARNING,
                    statusText = statusText,
                    warningTitle = "SI station mode warning",
                    warningMessage = "$statusText instead of READOUT/SI MASTER. Reprogram the station in a download-capable mode before using it for SI-card downloads.",
                    warningKey = "${stationInfo.serialNumber}:${stationInfo.stationModeCode}"
                )
            }
            true -> DesktopSiReaderUiState(
                severity = DesktopSiReaderSeverity.CONNECTED,
                statusText = "SI station ${stationInfo.serialNumber} connected in $modeLabel mode"
            )
            null -> DesktopSiReaderUiState(
                severity = DesktopSiReaderSeverity.CONNECTED,
                statusText = "SI station ${stationInfo.serialNumber} connected; mode unknown"
            )
        }
    }.getOrElse { error ->
        DesktopSiReaderUiState(
            severity = DesktopSiReaderSeverity.ERROR,
            statusText = "SI station error: ${error.message ?: error::class.simpleName}"
        )
    }
}

private fun downloadDesktopSportIdentCardReadout(): DesktopSportIdentCardBlockDownload {
    return DesktopSportIdentReadoutService().downloadOne()
}

@Composable
private fun SiStationModeWarningDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
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
    hasUnsavedChanges: Boolean,
    siReaderState: DesktopSiReaderUiState
) {
    val backgroundColor = when (siReaderState.severity) {
        DesktopSiReaderSeverity.DISCONNECTED -> DesktopPalette.Disconnected
        DesktopSiReaderSeverity.CONNECTED -> DesktopPalette.Connected
        DesktopSiReaderSeverity.WARNING -> DesktopPalette.Warning
        DesktopSiReaderSeverity.ERROR -> DesktopPalette.Error
    }
    val textColor = when (siReaderState.severity) {
        DesktopSiReaderSeverity.WARNING,
        DesktopSiReaderSeverity.CONNECTED -> DesktopPalette.Black
        DesktopSiReaderSeverity.DISCONNECTED,
        DesktopSiReaderSeverity.ERROR -> DesktopPalette.White
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(backgroundColor)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${siReaderState.statusText} - $projectStatusText${if (hasUnsavedChanges) " *" else ""}",
            color = textColor,
            fontSize = 13.sp
        )
    }
}
