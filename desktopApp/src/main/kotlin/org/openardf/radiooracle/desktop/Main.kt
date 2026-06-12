package org.openardf.radiooracle.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.Image
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Checkbox
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.openardf.radiooracle.desktop.printing.DesktopPrinterDiagnostics
import org.openardf.radiooracle.desktop.printing.DesktopTicketPrinter
import org.openardf.radiooracle.desktop.printing.DesktopTicketPrinterSelector
import org.openardf.radiooracle.desktop.usb.DesktopSportIdentCardBlockDownload
import org.openardf.radiooracle.desktop.usb.DesktopSportIdentReadoutService
import org.openardf.radiooracle.desktop.usb.DesktopSportIdentStationProbe
import org.openardf.radiooracle.desktop.usb.JSerialCommDesktopSerialPortProvider
import org.openardf.radiooracle.shared.course.ControlPointRules
import org.openardf.radiooracle.shared.course.ControlPointDefinition
import org.openardf.radiooracle.shared.course.ControlPointValidationException
import org.openardf.radiooracle.shared.event.EventCategoryDetails
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventCategorySort
import org.openardf.radiooracle.shared.event.EventCompetitorDetails
import org.openardf.radiooracle.shared.event.EventControl
import org.openardf.radiooracle.shared.event.EventControlCatalog
import org.openardf.radiooracle.shared.event.EventControlDetails
import org.openardf.radiooracle.shared.event.EventAssignedControlWarning
import org.openardf.radiooracle.shared.event.EventAssignedControlWarnings
import org.openardf.radiooracle.shared.event.EventInForestDetails
import org.openardf.radiooracle.shared.event.EventLastReadoutDetails
import org.openardf.radiooracle.shared.event.EventLastReadoutSeverity
import org.openardf.radiooracle.shared.event.CompetitorCsvImportDuplicatePolicy
import org.openardf.radiooracle.shared.event.EventProjectEditor
import org.openardf.radiooracle.shared.event.EventProjectFactory
import org.openardf.radiooracle.shared.event.EventRaceDetails
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventReadoutDuplicatePolicy
import org.openardf.radiooracle.shared.event.EventReadoutDetails
import org.openardf.radiooracle.shared.event.EventResultDetails
import org.openardf.radiooracle.shared.event.EventStartListDetails
import org.openardf.radiooracle.shared.event.EventStartListRuleSeverity
import org.openardf.radiooracle.shared.event.EventStartListRow
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.event.ProtectedIdealOrderRules
import org.openardf.radiooracle.shared.event.ResultRecalculationOutcome
import org.openardf.radiooracle.shared.event.StartDrawClubHandling
import org.openardf.radiooracle.shared.event.StartDrawOptions
import org.openardf.radiooracle.shared.event.StartDrawStartGroupMode
import org.openardf.radiooracle.shared.event.defaultScored
import org.openardf.radiooracle.shared.event.defaultTimeLimitMinutes
import org.openardf.radiooracle.shared.event.effectiveStartDrawSettings
import org.openardf.radiooracle.shared.event.toDisplayLabel
import org.openardf.radiooracle.shared.files.CategoryCsvImportRow
import org.openardf.radiooracle.shared.files.ControlCsvImportRow
import org.openardf.radiooracle.shared.files.EventCsvImports
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.shared.printing.FinishTicketRenderer
import org.openardf.radiooracle.shared.results.EventResultSending
import org.openardf.radiooracle.shared.time.DurationFormatter
import org.jetbrains.skia.Image as SkiaImage
import java.awt.Desktop
import java.awt.Toolkit
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private data class FixedTableColumn(val title: String, val width: Dp)

private val TableColumnGap = 12.dp
private val ActionRailWidth = 104.dp
private val FixedGridRowHeight = 56.dp
private val ReadoutAddRailYOffset = 10.dp
private const val DesktopSiPollIntervalMs = 5_000L
private const val DesktopLiveResultSendIntervalMs = 15_000L

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

private fun EventRaceData.containsReadoutForSiNumber(siNumber: Int): Boolean =
    competitorData.any { it.readoutData?.result?.siNumber == siNumber } ||
        unmatchedReadoutData.any { it.result.siNumber == siNumber }

private fun desktopTimeLimitText(timeLimitText: String): String {
    val minutes = timeLimitText.substringBefore(':').toLongOrNull()
    return if (minutes != null) "$minutes min" else timeLimitText
}

private enum class DesktopSportIdentAppendOutcome {
    Added,
    DuplicateIgnored,
    DuplicateReplaced,
    DuplicateCreatedNew
}

private val CategoryTableColumns = listOf(
    FixedTableColumn("Name", 150.dp),
    FixedTableColumn("Length (m)", 96.dp),
    FixedTableColumn("Climb (m)", 92.dp),
    FixedTableColumn("Type", 96.dp),
    FixedTableColumn("Band", 104.dp),
    FixedTableColumn("Limit (min.)", 104.dp),
    FixedTableColumn("Assigned Controls", 320.dp)
)

private val CategoryTableColumnHints = mapOf(
    "Name" to "Category/class name used for competitor assignment, start lists, and results.",
    "Length (m)" to "Course length for this category in meters. This public value is used in exports and result displays.",
    "Climb (m)" to "Total climb for this category in meters. This public value is used in exports and result displays.",
    "Type" to "Race type used by this category. It normally follows the Event File setting unless category-specific properties are imported.",
    "Band" to "Frequency band used by this category. It normally follows the Event File setting unless category-specific properties are imported.",
    "Limit (min.)" to "Time limit for this category in minutes. It normally follows the Event File setting unless category-specific properties are imported.",
    "Assigned Controls" to "Ordered controls for this category. Separate entries with spaces, commas, or semicolons. Use the picker to insert Public labels. Manual entries may use SI codes, defined control labels, or Public label values; put labels containing spaces in single or double quotes, such as 'Fox 1'."
)

private val ProtectedCourseOrderTableColumns = listOf(
    FixedTableColumn("Category", 120.dp),
    FixedTableColumn("Stored ideal order", 360.dp)
)
private val ProtectedIdealOrderPickerWidth = 76.dp
private val ProtectedIdealOrderTextFieldWidth =
    ProtectedCourseOrderTableColumns[1].width - ProtectedIdealOrderPickerWidth - TableColumnGap

private val CompetitorTableColumns = listOf(
    FixedTableColumn("First", 120.dp),
    FixedTableColumn("Last", 136.dp),
    FixedTableColumn("Club", 210.dp),
    FixedTableColumn("Bib no.", 96.dp),
    FixedTableColumn("Call sign", 116.dp),
    FixedTableColumn("Birth", 72.dp),
    FixedTableColumn("Category", 136.dp),
    FixedTableColumn("Start no.", 86.dp),
    FixedTableColumn("Start time", 104.dp),
    FixedTableColumn("SI no.", 110.dp)
)

private val CompetitorTableColumnHints = mapOf(
    "First" to "Competitor first or given name.",
    "Last" to "Competitor last or family name.",
    "Club" to "Club, society, school, or team used for reports and start-list fairness checks.",
    "Bib no." to "Persistent bib number worn by the competitor across one or more events in a competition.",
    "Call sign" to "Optional radio call sign or on-air identifier for this competitor.",
    "Birth" to "Optional birth year.",
    "Category" to "Competition category assigned to this competitor.",
    "Start no." to "Event-specific start number used by this Event File and its start list.",
    "Start time" to "Drawn start time in minutes and seconds from the event start, such as 012:00.",
    "SI no." to "SPORTident card number assigned to this competitor."
)

private val ResultTableColumns = listOf(
    FixedTableColumn("Place", 64.dp),
    FixedTableColumn("Competitor", 240.dp),
    FixedTableColumn("Status", 136.dp),
    FixedTableColumn("Points", 80.dp),
    FixedTableColumn("Runtime", 104.dp),
    FixedTableColumn("Punches", 240.dp),
    FixedTableColumn("Edit", 104.dp)
)

private val ReadoutTableColumns = listOf(
    FixedTableColumn("SI no.", 112.dp),
    FixedTableColumn("Competitor", 240.dp),
    FixedTableColumn("Status", 136.dp),
    FixedTableColumn("Points", 80.dp),
    FixedTableColumn("Runtime", 104.dp),
    FixedTableColumn("Punches", 260.dp),
    FixedTableColumn("Assign to", 240.dp),
    FixedTableColumn("", 104.dp),
    FixedTableColumn("Edit", 104.dp),
    FixedTableColumn("", 104.dp)
)

private val StartListTableColumns = listOf(
    FixedTableColumn("Start", 96.dp),
    FixedTableColumn("No.", 72.dp),
    FixedTableColumn("Competitor", 260.dp),
    FixedTableColumn("Category", 120.dp),
    FixedTableColumn("SI no.", 112.dp)
)

private val InForestTableColumns = listOf(
    FixedTableColumn("Competitor", 260.dp),
    FixedTableColumn("Category", 120.dp),
    FixedTableColumn("Start", 96.dp),
    FixedTableColumn("Elapsed", 96.dp),
    FixedTableColumn("Limit", 96.dp),
    FixedTableColumn("State", 112.dp)
)

private val ControlTableColumns = listOf(
    FixedTableColumn("SI code", 112.dp),
    FixedTableColumn("Role", 120.dp),
    FixedTableColumn("Public label", 160.dp),
    FixedTableColumn("Notes", 220.dp),
    FixedTableColumn("", 104.dp)
)

private val ControlTableColumnHints = mapOf(
    "SI code" to "Physical SPORTident control code recorded by the station. Category control lists can refer to this code, the generated control label, or the Public label.",
    "Role" to "How this station is interpreted for scoring. Radio-o Fox controls score 1 point; Beacon is a required zero-point punch. Sprint Spectator is optional for a course, but when assigned it is a required zero-point loop-transition punch in addition to the Beacon.",
    "Public label" to "Optional public-facing name used on tickets, readout displays, course lists, and exported results. Short labels can also be typed in category Controls fields.",
    "Notes" to "Private organizer notes for this logical control."
)

/** Starts the first Compose Desktop shell for Radio-Oracle. */
fun main(args: Array<String>) = application {
    lateinit var requestWindowClose: () -> Unit
    Window(onCloseRequest = { requestWindowClose() }, title = DesktopBuildInfo.windowTitle) {
        val startupPath = remember(args.toList()) {
            startupProjectPath(args.firstOrNull()?.let(Path::of))
        }
        val projectSession = remember { DesktopProjectSession(DesktopProjectFiles) }
        val localResultServer = remember {
            DesktopLocalResultServer(projectSupplier = { projectSession.currentProject })
        }
        val ticketPrinter = remember { DesktopTicketPrinter() }
        val appCoroutineScope = rememberCoroutineScope()
        val startupStatus = remember(startupPath) {
            openStartupProject(projectSession, startupPath, DesktopLastEventFilePreferences::rememberEventFile)
        }
        var projectFile by remember { mutableStateOf(projectSession.currentProject) }
        var projectStatusText by remember { mutableStateOf(startupStatus) }
        var hasUnsavedChanges by remember { mutableStateOf(projectSession.hasUnsavedChanges) }
        var newEventDraftProject by remember { mutableStateOf<EventProjectFile?>(null) }
        var pendingDirtyProjectAction by remember { mutableStateOf<PendingDirtyProjectAction?>(null) }
        var hasUnsavedEventDefinitionChanges by remember { mutableStateOf(false) }
        var isEventDefinitionSaveDialogVisible by remember { mutableStateOf(false) }
        var pendingAssignedControlsWarning by remember { mutableStateOf<PendingAssignedControlsWarning?>(null) }
        var assignedControlsWarningJob by remember { mutableStateOf<Job?>(null) }
        var isNationalStartListDefaultsDialogVisible by remember { mutableStateOf(false) }
        var pendingReadoutEdit by remember { mutableStateOf<DesktopReadoutEditDraft?>(null) }
        var siReaderState by remember { mutableStateOf(DesktopSiReaderUiState.disconnected()) }
        var pendingSiModeWarning by remember { mutableStateOf<DesktopSiReaderUiState?>(null) }
        var lastShownSiModeWarningKey by remember { mutableStateOf<String?>(null) }
        var isDownloadingSiReadout by remember { mutableStateOf(false) }
        var isContinuousSiReadoutActive by remember { mutableStateOf(false) }
        var isReadingCompetitorSiCard by remember { mutableStateOf(false) }
        var continuousSiReadoutStopRequested by remember { mutableStateOf<AtomicBoolean?>(null) }
        var siDownloadStatusText by remember { mutableStateOf<String?>(null) }
        var isSendingLiveResults by remember { mutableStateOf(false) }
        var isBackgroundLiveResultSendingEnabled by remember { mutableStateOf(false) }
        var readoutDuplicatePolicy by remember { mutableStateOf(EventReadoutDuplicatePolicy.Reject) }
        var isReadoutAlertSoundEnabled by remember { mutableStateOf(true) }
        var areAliasesEnabled by remember { mutableStateOf(true) }
        var localResultServerUrl by remember { mutableStateOf<String?>(null) }
        var isAboutDialogVisible by remember { mutableStateOf(false) }
        var raceClockTick by remember { mutableStateOf(0L) }
        var printerDiagnostics by remember { mutableStateOf(DesktopPrinterDiagnostics.from(emptyList())) }
        var lastLoggedSiReaderStatus by remember { mutableStateOf<String?>(null) }
        var protectedCoursePassword by remember { mutableStateOf<String?>(null) }
        var protectedIdealOrderByCategoryId by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
        var protectedCourseInfoByCategoryId by remember { mutableStateOf<Map<String, ProtectedCourseInfo>>(emptyMap()) }
        var recentImportReport by remember { mutableStateOf<DesktopImportReport?>(null) }
        var recentImportCheckpoint by remember { mutableStateOf<DesktopImportCheckpoint?>(null) }
        var recentActivityLog by remember { mutableStateOf<List<String>>(emptyList()) }
        var isEventRegImportDialogVisible by remember { mutableStateOf(false) }
        var isEventRegCompetitorCsvImportDialogVisible by remember { mutableStateOf(false) }
        var pendingCourseKmlKmzUnlockAction by remember { mutableStateOf<CourseKmlKmzUnlockAction?>(null) }
        var pendingProtectedControlDeleteId by remember { mutableStateOf<String?>(null) }
        var pendingBulkCategoryAction by remember { mutableStateOf<BulkCategoryAction?>(null) }
        var isDeleteAllCompetitorsDialogVisible by remember { mutableStateOf(false) }
        var pendingCourseKmlKmzImportReview by remember { mutableStateOf<PendingCourseKmlKmzImportReview?>(null) }
        var pendingCourseKmlKmzCategoryMapping by remember { mutableStateOf<PendingCourseKmlKmzCategoryMapping?>(null) }
        var pendingCategoriesCsvImportReview by remember { mutableStateOf<PendingCategoriesCsvImportReview?>(null) }
        var pendingControlsCsvImportReview by remember { mutableStateOf<PendingControlsCsvImportReview?>(null) }
        var courseKmlKmzElevationProgress by remember { mutableStateOf<CourseKmlKmzElevationProgressUiState?>(null) }
        var courseKmlKmzElevationJob by remember { mutableStateOf<Job?>(null) }
        var venueElevationCacheProgress by remember { mutableStateOf<VenueElevationCacheProgressUiState?>(null) }
        var venueElevationCacheJob by remember { mutableStateOf<Job?>(null) }
        var venueElevationCacheRefreshToken by remember { mutableStateOf(0) }
        var eventRegImportUrl by remember { mutableStateOf(DesktopEventRegImportPreferences.lastRegistrationUrl()) }
        var isImportingEventRegWebsite by remember { mutableStateOf(false) }
        var isImportingEventRegCompetitorCsvs by remember { mutableStateOf(false) }
        var isImportingCourseKmlKmz by remember { mutableStateOf(false) }
        var pendingCompetitorsCsvImportReview by remember { mutableStateOf<PendingCompetitorsCsvImportReview?>(null) }
        var syncCompetitorsCsvImport by remember { mutableStateOf(false) }
        val siPortMutex = remember { Mutex() }

        LaunchedEffect(Unit) {
            DesktopDebugLog.initialize()
            DesktopDebugLog.info("App", "Desktop app started version=${DesktopBuildInfo.displayVersion}")
        }

        LaunchedEffect(Unit) {
            while (true) {
                val nextSiReaderState = withContext(Dispatchers.IO) {
                    siPortMutex.withLock {
                        detectDesktopSiReaderState()
                    }
                }
                siReaderState = nextSiReaderState
                if (nextSiReaderState.statusText != lastLoggedSiReaderStatus) {
                    DesktopDebugLog.info("SI", nextSiReaderState.statusText)
                    lastLoggedSiReaderStatus = nextSiReaderState.statusText
                }
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

        LaunchedEffect(Unit) {
            val printers = withContext(Dispatchers.IO) {
                ticketPrinter.listPrinters()
            }
            printerDiagnostics = DesktopPrinterDiagnostics.from(printers)
        }

        fun syncProjectState() {
            projectFile = projectSession.currentProject
            hasUnsavedChanges = projectSession.hasUnsavedChanges
        }

        fun markEventDefinitionChangeIfLoaded(previousProject: EventProjectFile?, updatedProject: EventProjectFile) {
            if (projectSession.currentPath != null && previousProject != null && updatedProject != previousProject) {
                hasUnsavedEventDefinitionChanges = true
            }
        }

        fun recordActivity(message: String) {
            val timestamp = LocalTime.now().withNano(0).toString()
            recentActivityLog = (listOf("$timestamp - $message") + recentActivityLog).take(12)
        }

        fun deleteControlAfterProtectedRouteCheck(controlId: String, promptIfLocked: Boolean = true): Boolean {
            val currentProject = projectSession.currentProject
            if (currentProject == null) {
                projectStatusText = "Edit failed: Load an Event File before deleting controls."
                return false
            }
            if (currentProject.hasLockedProtectedCourseData(protectedCoursePassword != null)) {
                val controlLabel = currentProject.raceData.controls
                    .firstOrNull { it.id == controlId }
                    ?.publicDisplayLabel()
                    ?: "this control"
                if (promptIfLocked) {
                    pendingProtectedControlDeleteId = controlId
                    projectStatusText = "Unlock course data to delete $controlLabel."
                } else {
                    projectStatusText = "Edit failed: Course data is locked. Unlock course data before deleting controls so protected route references can be checked."
                }
                return false
            }
            val result = runCatching {
                val currentProjectForDelete = projectSession.currentProject
                    ?: throw IllegalStateException("Load an Event File before deleting controls.")
                val protectedUseCount = DesktopImportPreviews.protectedCourseUseCount(
                    protectedCourseInfoByCategoryId,
                    setOf(controlId)
                )
                val cleanupResult = if (protectedUseCount > 0) {
                    val password = protectedCoursePassword
                        ?: throw IllegalStateException("Unlock course data before deleting controls so protected route references can be checked.")
                    DesktopProtectedCourseCleanup.removeStaleControlReferencesForDeletedControl(
                        projectFile = currentProjectForDelete,
                        protectedCourseInfoByCategoryId = protectedCourseInfoByCategoryId,
                        controlId = controlId,
                        password = password
                    )
                } else {
                    DesktopProtectedCourseCleanupResult(
                        projectFile = currentProjectForDelete,
                        protectedCourseInfoByCategoryId = protectedCourseInfoByCategoryId,
                        clearedCourseCount = 0,
                        prunedCourseCount = 0
                    )
                }
                projectFile = projectSession.updateCurrentProject { project ->
                    EventProjectEditor.removeControl(cleanupResult.projectFile, controlId)
                }
                protectedCourseInfoByCategoryId = cleanupResult.protectedCourseInfoByCategoryId
                hasUnsavedChanges = projectSession.hasUnsavedChanges
                recordActivity("Removed control.")
                val cleanedCourseCount = cleanupResult.clearedCourseCount + cleanupResult.prunedCourseCount
                projectStatusText = if (cleanedCourseCount > 0) {
                    "Removed control and cleaned stale protected course references from $cleanedCourseCount stored course${if (cleanedCourseCount == 1) "" else "s"}. Unsaved changes."
                } else {
                    "Unsaved changes."
                }
            }
            result.onFailure { error ->
                projectStatusText = "Edit failed: ${error.message ?: error::class.simpleName}"
            }
            return result.isSuccess
        }

        fun checkpointBeforeImport(title: String) {
            val currentProject = projectSession.currentProject ?: return
            val backupPath = DesktopImportBackups.writeBackup(
                projectFile = currentProject,
                currentEventPath = projectSession.currentPath,
                importTitle = title
            )
            recentImportCheckpoint = DesktopImportCheckpoint(
                title = title,
                backupPath = backupPath,
                projectFile = currentProject,
                protectedCoursePassword = protectedCoursePassword,
                protectedIdealOrderByCategoryId = protectedIdealOrderByCategoryId,
                protectedCourseInfoByCategoryId = protectedCourseInfoByCategoryId
            )
            recordActivity("Saved persistent rollback backup ${backupPath.fileName}.")
        }

        fun withRollbackBackupLine(lines: List<String>): List<String> {
            val backupPath = recentImportCheckpoint?.backupPath ?: return lines
            return lines + "Rollback backup file: $backupPath"
        }

        fun restoreRecentImportCheckpoint() {
            val checkpoint = recentImportCheckpoint ?: return
            runCatching {
                projectFile = projectSession.updateCurrentProject { checkpoint.projectFile }
                protectedCoursePassword = checkpoint.protectedCoursePassword
                protectedIdealOrderByCategoryId = checkpoint.protectedIdealOrderByCategoryId
                protectedCourseInfoByCategoryId = checkpoint.protectedCourseInfoByCategoryId
                syncProjectState()
                recentImportReport = DesktopImportReport(
                    title = "Restored ${checkpoint.title}",
                    lines = listOf(
                        "The Event File was restored to the in-memory checkpoint captured before that import.",
                        "Persistent rollback backup file: ${checkpoint.backupPath}"
                    )
                )
                recordActivity("Restored checkpoint before ${checkpoint.title}.")
                projectStatusText = "Restored checkpoint from before ${checkpoint.title}. Unsaved changes."
            }.onFailure { error ->
                projectStatusText = "Restore failed: ${error.message ?: error::class.simpleName}"
            }
        }

        fun recalculateResults() {
            runCatching {
                var outcome: ResultRecalculationOutcome? = null
                projectFile = projectSession.updateCurrentProject { currentProject ->
                    EventProjectEditor.recalculateResults(currentProject).also {
                        outcome = it
                    }.projectFile
                }
                syncProjectState()
                val result = requireNotNull(outcome)
                val skippedText = result.skippedStatusOnlyCount
                    .takeIf { it > 0 }
                    ?.let { " $it status-only results preserved." }
                    .orEmpty()
                projectStatusText =
                    "Recalculated ${result.recalculatedCount} results; ${result.changedCount} changed.$skippedText"
                recordActivity(projectStatusText)
                DesktopDebugLog.info("Results", projectStatusText)
            }.onFailure { error ->
                projectStatusText = "Result recalculation failed: ${error.message ?: error::class.simpleName}"
                DesktopDebugLog.error("Results", projectStatusText)
            }
        }

        fun isDefaultUnsavedNewEventFileDraft(): Boolean =
            newEventDraftProject != null &&
                projectSession.currentPath == null &&
                projectSession.currentProject == newEventDraftProject

        fun hasEditedUnsavedNewEventFileDraft(): Boolean =
            newEventDraftProject != null &&
                projectSession.currentPath == null &&
                projectSession.currentProject != newEventDraftProject

        fun hasProtectedUnsavedChanges(): Boolean =
            hasUnsavedChanges && !isDefaultUnsavedNewEventFileDraft()

        fun canSaveEventFile(): Boolean =
            projectFile != null && (projectSession.currentPath == null || hasProtectedUnsavedChanges())

        fun clearAssignedControlsWarning() {
            assignedControlsWarningJob?.cancel()
            assignedControlsWarningJob = null
            pendingAssignedControlsWarning = null
        }

        fun scheduleAssignedControlsWarning(
            warning: EventAssignedControlWarning?,
            previousControlPointsText: String? = null
        ) {
            assignedControlsWarningJob?.cancel()
            assignedControlsWarningJob = null
            if (warning == null) {
                pendingAssignedControlsWarning = null
                return
            }
            assignedControlsWarningJob = appCoroutineScope.launch {
                delay(700L)
                pendingAssignedControlsWarning = PendingAssignedControlsWarning(
                    warning = warning,
                    previousControlPointsText = previousControlPointsText
                )
                assignedControlsWarningJob = null
            }
        }

        fun lockProtectedCourseOrder() {
            val wasUnlocked = protectedCoursePassword != null
            protectedCoursePassword = null
            protectedIdealOrderByCategoryId = emptyMap()
            protectedCourseInfoByCategoryId = emptyMap()
            if (wasUnlocked) {
                projectStatusText = "Event Password lock applied."
            }
        }

        fun unlockedIdealFirstFoxByCategoryId(): Map<String, Int> =
            protectedIdealOrderByCategoryId.mapNotNull { (categoryId, idealOrderText) ->
                projectFile?.raceData?.controls?.let { controls ->
                    ProtectedIdealOrderRules.firstControlCode(idealOrderText, controls)?.let { categoryId to it }
                }
            }.toMap()

        fun shouldOfferNationalStartListDefaults(project: EventProjectFile): Boolean =
            !project.raceData.effectiveStartDrawSettings().options.hasNationalEventDefaults()

        fun applyNationalStartListDefaults() {
            runCatching {
                val updatedProject = projectSession.updateCurrentProject { currentProject ->
                    val settings = currentProject.raceData.effectiveStartDrawSettings()
                    EventProjectEditor.updateStartDrawSettings(
                        currentProject,
                        settings.intervalText,
                        settings.options.withNationalEventDefaults()
                    )
                }
                projectFile = updatedProject
                hasUnsavedChanges = projectSession.hasUnsavedChanges
                projectStatusText = "National Start List defaults applied."
            }.onFailure { error ->
                projectStatusText = "Start list defaults failed: ${error.message ?: error::class.simpleName}"
            }
        }

        fun openProject(path: Path) {
            runCatching {
                clearAssignedControlsWarning()
                lockProtectedCourseOrder()
                projectFile = projectSession.open(path)
                newEventDraftProject = null
                hasUnsavedEventDefinitionChanges = false
                isEventDefinitionSaveDialogVisible = false
                hasUnsavedChanges = projectSession.hasUnsavedChanges
                DesktopLastEventFilePreferences.rememberEventFile(path)
                projectStatusText = "Opened ${path.fileName}"
                DesktopDebugLog.info("EventFile", "Opened ${path.fileName}")
            }.onFailure { error ->
                projectStatusText = "Open failed: ${error.message ?: error::class.simpleName}"
                DesktopDebugLog.error("EventFile", "Open failed: ${error.message ?: error::class.simpleName}")
            }
        }

        fun closeProject(discardUnsavedChanges: Boolean = false) {
            runCatching {
                clearAssignedControlsWarning()
                lockProtectedCourseOrder()
                projectSession.closeProject(discardUnsavedChanges)
                newEventDraftProject = null
                hasUnsavedEventDefinitionChanges = false
                isEventDefinitionSaveDialogVisible = false
                syncProjectState()
                projectStatusText = "No Event File open."
                DesktopDebugLog.info("EventFile", "Closed Event File")
            }.onFailure { error ->
                projectStatusText = "Close failed: ${error.message ?: error::class.simpleName}"
                DesktopDebugLog.error("EventFile", "Close failed: ${error.message ?: error::class.simpleName}")
            }
        }

        fun createNewProject() {
            clearAssignedControlsWarning()
            lockProtectedCourseOrder()
            val project = EventProjectFactory.createEmptyProject(
                raceId = UUID.randomUUID().toString(),
                raceName = "New Event",
                startDateTimeIso = DesktopDateTimeText.isoText(DesktopDateTimeText.defaultStartDateTime())
            )
            projectSession.newProject(project)
            newEventDraftProject = project
            hasUnsavedEventDefinitionChanges = false
            isEventDefinitionSaveDialogVisible = false
            syncProjectState()
            projectStatusText = "New unsaved Event File."
            DesktopDebugLog.info("EventFile", "Created new unsaved Event File")
        }

        fun appendSportIdentDownload(download: DesktopSportIdentCardBlockDownload): DesktopSportIdentAppendOutcome {
            val currentProject = projectSession.currentProject ?: return DesktopSportIdentAppendOutcome.DuplicateIgnored
            val isDuplicate = currentProject.raceData.containsReadoutForSiNumber(download.readout.siNumber)
            if (isDuplicate && readoutDuplicatePolicy == EventReadoutDuplicatePolicy.Reject) {
                return DesktopSportIdentAppendOutcome.DuplicateIgnored
            }
            projectFile = projectSession.updateCurrentProject { currentProject ->
                EventProjectEditor.addDownloadedSportIdentReadout(
                    projectFile = currentProject,
                    resultId = UUID.randomUUID().toString(),
                    cardType = download.inserted.cardType,
                    readout = download.readout,
                    readoutDateTimeIso = LocalDateTime.now().withNano(0).toString(),
                    duplicatePolicy = readoutDuplicatePolicy
                ) { index, type ->
                    "${UUID.randomUUID()}-$index-${type.name}"
                }
            }
            hasUnsavedChanges = projectSession.hasUnsavedChanges
            val lastReadoutSeverity = projectSession.currentProject
                ?.let { EventLastReadoutDetails.from(it.raceData).severity }
                ?: EventLastReadoutSeverity.None
            if (isReadoutAlertSoundEnabled && lastReadoutSeverity == EventLastReadoutSeverity.Error) {
                beepDesktopReadoutAlert()
            }
            return when {
                !isDuplicate -> DesktopSportIdentAppendOutcome.Added
                readoutDuplicatePolicy == EventReadoutDuplicatePolicy.Replace ->
                    DesktopSportIdentAppendOutcome.DuplicateReplaced
                else -> DesktopSportIdentAppendOutcome.DuplicateCreatedNew
            }
        }

        fun downloadSportIdentReadout() {
            if (isDownloadingSiReadout || isContinuousSiReadoutActive || isReadingCompetitorSiCard) {
                return
            }
            val currentProject = projectSession.currentProject
            if (currentProject == null) {
                projectStatusText = "Open or create an Event File before downloading SI cards."
                DesktopDebugLog.warn("SI", "Single SI download requested with no Event File open")
                return
            }
            val preflightWarning = raceOpsPreflightWarning(
                currentProject,
                protectedCourseInfoByCategoryId.takeIf { protectedCoursePassword != null } ?: emptyMap(),
                protectedCoursePassword != null
            )
            isDownloadingSiReadout = true
            siDownloadStatusText = "Waiting for SI card; keep it seated until the read finishes."
            projectStatusText = preflightWarning ?: "Waiting for SI card..."
            preflightWarning?.let { DesktopDebugLog.warn("Readiness", it) }
            DesktopDebugLog.info("SI", "Single SI download started")
            appCoroutineScope.launch {
                val downloadResult = runCatching {
                    withContext(Dispatchers.IO) {
                        siPortMutex.withLock {
                            downloadDesktopSportIdentCardReadout()
                        }
                    }
                }
                downloadResult.onSuccess { download ->
                    runCatching {
                        when (appendSportIdentDownload(download)) {
                            DesktopSportIdentAppendOutcome.Added -> {
                                recordActivity("Downloaded SI card ${download.readout.siNumber}.")
                                projectStatusText = "Downloaded SI card ${download.readout.siNumber}."
                                DesktopDebugLog.info("SI", "Downloaded SI card ${download.readout.siNumber}")
                            }
                            DesktopSportIdentAppendOutcome.DuplicateIgnored -> {
                                projectStatusText = "SI card ${download.readout.siNumber} was already downloaded."
                                DesktopDebugLog.warn("SI", "Duplicate SI card ${download.readout.siNumber} ignored")
                                if (isReadoutAlertSoundEnabled) {
                                    beepDesktopReadoutAlert()
                                }
                            }
                            DesktopSportIdentAppendOutcome.DuplicateReplaced -> {
                                recordActivity("Replaced SI readout ${download.readout.siNumber}.")
                                projectStatusText = "Replaced existing readout for SI card ${download.readout.siNumber}."
                                DesktopDebugLog.info("SI", "Duplicate SI card ${download.readout.siNumber} replaced")
                            }
                            DesktopSportIdentAppendOutcome.DuplicateCreatedNew -> {
                                recordActivity("Stored duplicate SI readout ${download.readout.siNumber}.")
                                projectStatusText = "Created new duplicate readout for SI card ${download.readout.siNumber}."
                                DesktopDebugLog.info("SI", "Duplicate SI card ${download.readout.siNumber} stored as new readout")
                            }
                        }
                        siDownloadStatusText = null
                    }.onFailure { error ->
                        projectStatusText = "SI download failed: ${error.message ?: error::class.simpleName}"
                        siDownloadStatusText = projectStatusText
                        DesktopDebugLog.error("SI", projectStatusText)
                    }
                }.onFailure { error ->
                    projectStatusText = "SI download failed: ${error.message ?: error::class.simpleName}"
                    siDownloadStatusText = projectStatusText
                    DesktopDebugLog.error("SI", projectStatusText)
                }
                isDownloadingSiReadout = false
            }
        }

        fun stopContinuousSportIdentReadout() {
            continuousSiReadoutStopRequested?.set(true)
            if (isContinuousSiReadoutActive) {
                siDownloadStatusText = "Stopping continuous SI readout after the current card wait finishes."
                projectStatusText = "Stopping continuous SI readout..."
                DesktopDebugLog.info("SI", "Continuous SI readout stop requested")
            }
        }

        fun insertTestSportIdentDownloads() {
            val currentProject = projectSession.currentProject
            if (currentProject == null) {
                projectStatusText = "Open or create an Event File before inserting test SI downloads."
                return
            }
            runCatching {
                val result = DesktopTestSportIdentDownloads.insert(currentProject)
                if (result.insertedCount > 0) {
                    projectFile = projectSession.updateCurrentProject { result.projectFile }
                    hasUnsavedChanges = projectSession.hasUnsavedChanges
                    projectStatusText =
                        "Inserted ${result.insertedCount} test SI-card download${if (result.insertedCount == 1) "" else "s"}."
                } else {
                    projectStatusText =
                        "No test SI downloads inserted; all eligible competitors already have readouts or lack SI numbers."
                }
                DesktopDebugLog.info("SI", "Inserted ${result.insertedCount} test SI-card downloads")
            }.onFailure { error ->
                projectStatusText = "Test SI download insert failed: ${error.message ?: error::class.simpleName}"
                DesktopDebugLog.error("SI", projectStatusText)
            }
        }

        fun insertTestControls() {
            val currentProject = projectSession.currentProject
            if (currentProject == null) {
                projectStatusText = "Open or create an Event File before inserting test controls."
                return
            }
            runCatching {
                val result = DesktopTestEventData.insertControls(currentProject)
                if (result.insertedCount > 0) {
                    projectFile = projectSession.updateCurrentProject { result.projectFile }
                    hasUnsavedChanges = projectSession.hasUnsavedChanges
                    projectStatusText =
                        "Inserted ${result.insertedCount} test control${if (result.insertedCount == 1) "" else "s"}."
                } else {
                    projectStatusText = "No test controls inserted; compatible test controls already exist."
                }
                DesktopDebugLog.info("Testing", "Inserted ${result.insertedCount} test controls")
            }.onFailure { error ->
                projectStatusText = "Test control insert failed: ${error.message ?: error::class.simpleName}"
                DesktopDebugLog.error("Testing", projectStatusText)
            }
        }

        fun insertTestCategories() {
            val currentProject = projectSession.currentProject
            if (currentProject == null) {
                projectStatusText = "Open or create an Event File before inserting test categories."
                return
            }
            runCatching {
                val result = DesktopTestEventData.insertCategories(currentProject)
                if (result.insertedCount > 0) {
                    projectFile = projectSession.updateCurrentProject { result.projectFile }
                    hasUnsavedChanges = projectSession.hasUnsavedChanges
                    projectStatusText =
                        "Inserted ${result.insertedCount} test categor${if (result.insertedCount == 1) "y" else "ies"}."
                } else {
                    projectStatusText = "No test categories inserted; compatible test categories already exist."
                }
                DesktopDebugLog.info("Testing", "Inserted ${result.insertedCount} test categories")
            }.onFailure { error ->
                projectStatusText = "Test category insert failed: ${error.message ?: error::class.simpleName}"
                DesktopDebugLog.error("Testing", projectStatusText)
            }
        }

        fun insertTestCompetitors() {
            val currentProject = projectSession.currentProject
            if (currentProject == null) {
                projectStatusText = "Open or create an Event File before inserting test competitors."
                return
            }
            runCatching {
                val result = DesktopTestEventData.insertCompetitors(currentProject)
                if (result.insertedCount > 0) {
                    projectFile = projectSession.updateCurrentProject { result.projectFile }
                    hasUnsavedChanges = projectSession.hasUnsavedChanges
                    projectStatusText =
                        "Inserted ${result.insertedCount} test competitor${if (result.insertedCount == 1) "" else "s"}."
                } else {
                    projectStatusText = "No test competitors inserted; compatible test competitors already exist."
                }
                DesktopDebugLog.info("Testing", "Inserted ${result.insertedCount} test competitors")
            }.onFailure { error ->
                projectStatusText = "Test competitor insert failed: ${error.message ?: error::class.simpleName}"
                DesktopDebugLog.error("Testing", projectStatusText)
            }
        }

        fun startContinuousSportIdentReadout() {
            if (isDownloadingSiReadout || isContinuousSiReadoutActive || isReadingCompetitorSiCard) {
                return
            }
            val currentProject = projectSession.currentProject
            if (currentProject == null) {
                projectStatusText = "Open or create an Event File before downloading SI cards."
                DesktopDebugLog.warn("SI", "Continuous SI readout requested with no Event File open")
                return
            }
            val preflightWarning = raceOpsPreflightWarning(
                currentProject,
                protectedCourseInfoByCategoryId.takeIf { protectedCoursePassword != null } ?: emptyMap(),
                protectedCoursePassword != null
            )
            val stopRequested = AtomicBoolean(false)
            continuousSiReadoutStopRequested = stopRequested
            isContinuousSiReadoutActive = true
            siDownloadStatusText = "Continuous SI readout is running; insert SI cards and keep each seated until it reads."
            projectStatusText = preflightWarning ?: "Continuous SI readout running..."
            preflightWarning?.let { DesktopDebugLog.warn("Readiness", it) }
            DesktopDebugLog.info("SI", "Continuous SI readout started")
            appCoroutineScope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        siPortMutex.withLock {
                            DesktopSportIdentReadoutService().downloadUntilTimeout(
                                maxCards = Int.MAX_VALUE,
                                onDownload = { download ->
                                    appCoroutineScope.launch {
                                        runCatching {
                                            when (appendSportIdentDownload(download)) {
                                                DesktopSportIdentAppendOutcome.Added -> {
                                                    projectStatusText = "Downloaded SI card ${download.readout.siNumber}."
                                                    recordActivity("Downloaded SI card ${download.readout.siNumber}.")
                                                    DesktopDebugLog.info("SI", "Downloaded SI card ${download.readout.siNumber}")
                                                    siDownloadStatusText =
                                                        "Continuous SI readout running; waiting for the next card."
                                                }
                                                DesktopSportIdentAppendOutcome.DuplicateIgnored -> {
                                                    projectStatusText =
                                                        "SI card ${download.readout.siNumber} was already downloaded."
                                                    DesktopDebugLog.warn(
                                                        "SI",
                                                        "Duplicate SI card ${download.readout.siNumber} ignored"
                                                    )
                                                    if (isReadoutAlertSoundEnabled) {
                                                        beepDesktopReadoutAlert()
                                                    }
                                                    siDownloadStatusText =
                                                        "Duplicate SI card ignored; continuous SI readout is still running."
                                                }
                                                DesktopSportIdentAppendOutcome.DuplicateReplaced -> {
                                                    projectStatusText =
                                                        "Replaced existing readout for SI card ${download.readout.siNumber}."
                                                    recordActivity("Replaced SI readout ${download.readout.siNumber}.")
                                                    DesktopDebugLog.info(
                                                        "SI",
                                                        "Duplicate SI card ${download.readout.siNumber} replaced"
                                                    )
                                                    siDownloadStatusText =
                                                        "Duplicate SI card replaced; waiting for the next card."
                                                }
                                                DesktopSportIdentAppendOutcome.DuplicateCreatedNew -> {
                                                    projectStatusText =
                                                        "Created new duplicate readout for SI card ${download.readout.siNumber}."
                                                    recordActivity("Stored duplicate SI readout ${download.readout.siNumber}.")
                                                    DesktopDebugLog.info(
                                                        "SI",
                                                        "Duplicate SI card ${download.readout.siNumber} stored as new readout"
                                                    )
                                                    siDownloadStatusText =
                                                        "Duplicate SI card stored as a new readout; waiting for the next card."
                                                }
                                            }
                                        }.onFailure { error ->
                                            projectStatusText = "SI download failed: ${error.message ?: error::class.simpleName}"
                                            siDownloadStatusText = projectStatusText
                                            DesktopDebugLog.error("SI", projectStatusText)
                                            stopRequested.set(true)
                                        }
                                    }
                                },
                                onTimeout = {
                                    appCoroutineScope.launch {
                                        if (!stopRequested.get()) {
                                            siDownloadStatusText = "Continuous SI readout timed out waiting for a card."
                                            projectStatusText = siDownloadStatusText ?: projectStatusText
                                            DesktopDebugLog.warn("SI", "Continuous SI readout timed out waiting for a card")
                                        }
                                    }
                                },
                                shouldContinue = { !stopRequested.get() }
                            )
                        }
                    }
                }
                result.onFailure { error ->
                    projectStatusText = "SI continuous download failed: ${error.message ?: error::class.simpleName}"
                    siDownloadStatusText = projectStatusText
                    DesktopDebugLog.error("SI", projectStatusText)
                }
                if (stopRequested.get() && result.isSuccess) {
                    projectStatusText = "Continuous SI readout stopped."
                    siDownloadStatusText = null
                    DesktopDebugLog.info("SI", "Continuous SI readout stopped")
                }
                isContinuousSiReadoutActive = false
                continuousSiReadoutStopRequested = null
            }
        }

        suspend fun readCompetitorSiCardForAddRow(): DesktopCompetitorSiCardDraft {
            check(!isDownloadingSiReadout && !isContinuousSiReadoutActive && !isReadingCompetitorSiCard) {
                "Another SI card read is already active."
            }
            isReadingCompetitorSiCard = true
            siDownloadStatusText = "Waiting for SI card for competitor entry. Keep the card seated until the read finishes."
            projectStatusText = "Waiting for SI card..."
            DesktopDebugLog.info("SI", "Competitor SI-card read started")
            return try {
                val download = withContext(Dispatchers.IO) {
                    siPortMutex.withLock {
                        downloadDesktopSportIdentCardReadout()
                    }
                }
                val cardHolder = download.readout.cardHolder
                val draft = DesktopCompetitorSiCardDraft(
                    siNumber = download.readout.siNumber,
                    firstName = cardHolder?.firstName,
                    lastName = cardHolder?.lastName,
                    club = cardHolder?.club
                )
                projectStatusText = "Read SI card ${download.readout.siNumber} for competitor entry."
                siDownloadStatusText = null
                DesktopDebugLog.info("SI", "Read SI card ${download.readout.siNumber} for competitor entry")
                draft
            } catch (error: Throwable) {
                projectStatusText = "SI card read failed: ${error.message ?: error::class.simpleName}"
                siDownloadStatusText = projectStatusText
                DesktopDebugLog.error("SI", projectStatusText)
                throw error
            } finally {
                isReadingCompetitorSiCard = false
            }
        }

        fun sendRobisLiveResults(automatic: Boolean = false) {
            if (isSendingLiveResults) {
                return
            }
            val currentProjectForPlan = projectSession.currentProject
            if (currentProjectForPlan == null) {
                if (!automatic) {
                    projectStatusText = "Open or create an Event File before sending live results."
                }
                return
            }
            if (currentProjectForPlan.raceData.race.apiKey.isBlank()) {
                if (!automatic) {
                    projectStatusText = "Set the race API key before sending ROBIS live results."
                }
                return
            }
            if (!EventResultSending.plan(currentProjectForPlan.raceData).hasCandidates) {
                if (!automatic) {
                    projectStatusText = "There are no unsent matched results to send."
                }
                return
            }
            val preflightWarning = raceOpsPreflightWarning(
                currentProjectForPlan,
                protectedCourseInfoByCategoryId.takeIf { protectedCoursePassword != null } ?: emptyMap(),
                protectedCoursePassword != null
            )
            isSendingLiveResults = true
            if (!automatic) {
                projectStatusText = preflightWarning?.let { "$it Sending live results to ROBIS..." }
                    ?: "Sending live results to ROBIS..."
                preflightWarning?.let { DesktopDebugLog.warn("Readiness", it) }
            }
            appCoroutineScope.launch {
                val sendResult = runCatching {
                    withContext(Dispatchers.IO) {
                        val currentProject = requireNotNull(projectSession.currentProject)
                        DesktopRobisLiveResultSender().sendUnsent(
                            projectFile = currentProject,
                            apiKey = currentProject.raceData.race.apiKey
                        )
                    }
                }
                sendResult.onSuccess { result ->
                    projectFile = projectSession.updateCurrentProject { result.projectFile }
                    syncProjectState()
                    recordActivity("Sent ${result.sentCount} live results to ROBIS.")
                    projectStatusText = "Sent ${result.sentCount} live result${if (result.sentCount == 1) "" else "s"} to ROBIS."
                }.onFailure { error ->
                    projectStatusText = "ROBIS send failed: ${error.message ?: error::class.simpleName}"
                }
                isSendingLiveResults = false
            }
        }

        LaunchedEffect(Unit) {
            while (true) {
                delay(DesktopLiveResultSendIntervalMs)
                if (isBackgroundLiveResultSendingEnabled) {
                    sendRobisLiveResults(automatic = true)
                }
            }
        }

        LaunchedEffect(Unit) {
            while (true) {
                delay(30_000L)
                raceClockTick += 1
            }
        }

        fun saveCurrentProject(overwriteEventDefinitionChanges: Boolean = false): Boolean {
            if (projectSession.currentPath == null) {
                val path = DesktopFileDialogs.chooseSaveProject(
                    projectSession.currentProject?.raceData?.race?.name
                ) ?: return false
                return runCatching {
                    projectSession.saveAs(path)
                    newEventDraftProject = null
                    hasUnsavedEventDefinitionChanges = false
                    isEventDefinitionSaveDialogVisible = false
                    syncProjectState()
                    DesktopLastEventFilePreferences.rememberEventFile(path)
                    projectStatusText = "Saved ${path.fileName}"
                    DesktopDebugLog.info("EventFile", "Saved ${path.fileName}")
                }.onFailure { error ->
                    projectStatusText = "Save failed: ${error.message ?: error::class.simpleName}"
                    DesktopDebugLog.error("EventFile", "Save failed: ${error.message ?: error::class.simpleName}")
                }.isSuccess
            }
            if (
                DesktopEventDefinitionSaveProtection.shouldPromptBeforeSave(
                    currentPath = projectSession.currentPath,
                    hasUnsavedEventDefinitionChanges = hasUnsavedEventDefinitionChanges,
                    overwriteEventDefinitionChanges = overwriteEventDefinitionChanges
                )
            ) {
                isEventDefinitionSaveDialogVisible = true
                projectStatusText = DesktopEventDefinitionSaveProtection.statusText
                return false
            }
            return runCatching {
                projectSession.save()
                newEventDraftProject = null
                hasUnsavedEventDefinitionChanges = false
                isEventDefinitionSaveDialogVisible = false
                syncProjectState()
                projectSession.currentPath?.let(DesktopLastEventFilePreferences::rememberEventFile)
                projectStatusText = "Saved ${projectSession.currentPath?.fileName ?: "Event File"}"
                DesktopDebugLog.info("EventFile", "Saved ${projectSession.currentPath?.fileName ?: "Event File"}")
            }.onFailure { error ->
                projectStatusText = "Save failed: ${error.message ?: error::class.simpleName}"
                DesktopDebugLog.error("EventFile", "Save failed: ${error.message ?: error::class.simpleName}")
            }.isSuccess
        }

        fun exportCsv(title: String, suffix: String, export: (Path, EventProjectFile) -> Unit) {
            val currentProject = projectSession.currentProject ?: return
            DesktopFileDialogs.chooseExportCsv(title, currentProject.raceData.race.name, suffix)?.let { path ->
                runCatching {
                    export(path, currentProject)
                    syncProjectState()
                    projectStatusText = "Exported ${path.fileName}"
                }.onFailure { error ->
                    projectStatusText = "Export failed: ${error.message ?: error::class.simpleName}"
                }
            }
        }

        fun exportCategoriesCsv() {
            val currentProject = projectSession.currentProject ?: return
            DesktopFileDialogs.chooseExportCsv("Export Categories CSV", currentProject.raceData.race.name, "categories")?.let { path ->
                runCatching {
                    DesktopProjectFiles.exportCategoriesCsv(
                        path,
                        currentProject,
                        includeEncryptedIdealOrder = protectedCoursePassword != null
                    )
                    syncProjectState()
                    projectStatusText = "Exported ${path.fileName}"
                }.onFailure { error ->
                    projectStatusText = "Export failed: ${error.message ?: error::class.simpleName}"
                }
            }
        }

        fun unlockProtectedCourseOrder(password: String): Boolean {
            val currentProject = projectSession.currentProject ?: return false
            val trimmedPassword = password.trim()
            if (trimmedPassword.isEmpty()) {
                projectStatusText = "Event Password cannot be blank."
                return false
            }

            val decrypted = runCatching {
                currentProject.raceData.categories.associate { categoryData ->
                    val encryptedValue = categoryData.category.encryptedIdealOrder
                    categoryData.category.id to if (encryptedValue.isNullOrBlank()) {
                        ""
                    } else {
                        DesktopProtectedCourseOrder.decrypt(encryptedValue, trimmedPassword)
                    }
                }
            }.getOrElse { error ->
                projectStatusText = error.message ?: "Course order unlock failed."
                return false
            }
            val decryptedCourseInfo = runCatching {
                currentProject.raceData.categories.mapNotNull { categoryData ->
                    categoryData.category.encryptedCourseInfo?.takeIf { it.isNotBlank() }?.let { encryptedValue ->
                        categoryData.category.id to DesktopProtectedCourseOrder.decryptCourseInfo(encryptedValue, trimmedPassword)
                    }
                }.toMap()
            }.getOrElse { error ->
                projectStatusText = error.message ?: "Course data unlock failed."
                return false
            }

            protectedCoursePassword = trimmedPassword
            protectedIdealOrderByCategoryId = decrypted
            protectedCourseInfoByCategoryId = decryptedCourseInfo
            projectStatusText = "Course order unlocked."
            return true
        }

        fun applyBulkCategoryAction(action: BulkCategoryAction, password: String): Boolean {
            val currentProject = projectSession.currentProject ?: return false
            if (currentProject.raceData.categories.isEmpty()) {
                projectStatusText = "No categories to update."
                return false
            }
            if (currentProject.hasProtectedCategoryData()) {
                if (!unlockProtectedCourseOrder(password)) {
                    return false
                }
            }
            return runCatching {
                projectFile = projectSession.updateCurrentProject { project ->
                    when (action) {
                        BulkCategoryAction.DeleteAllAssignedControls ->
                            EventProjectEditor.removeAllAssignedCategoryControls(project)
                        BulkCategoryAction.DeleteAllCategories ->
                            EventProjectEditor.removeAllCategories(project)
                    }
                }
                protectedIdealOrderByCategoryId = emptyMap()
                protectedCourseInfoByCategoryId = emptyMap()
                hasUnsavedChanges = projectSession.hasUnsavedChanges
                when (action) {
                    BulkCategoryAction.DeleteAllAssignedControls -> {
                        recordActivity("Deleted all assigned category controls.")
                        projectStatusText = "Deleted all assigned controls and cleared category length/climb data. Category names were kept. Unsaved changes."
                    }
                    BulkCategoryAction.DeleteAllCategories -> {
                        recordActivity("Deleted all categories.")
                        projectStatusText = "Deleted all categories and cleared category assignments from competitors. Unsaved changes."
                    }
                }
                true
            }.getOrElse { error ->
                projectStatusText = "Edit failed: ${error.message ?: error::class.simpleName}"
                false
            }
        }

        fun deleteAllCompetitors(): Boolean {
            val currentProject = projectSession.currentProject ?: return false
            if (currentProject.raceData.competitorData.isEmpty()) {
                projectStatusText = "No competitors to delete."
                return false
            }
            return runCatching {
                val competitorCount = currentProject.raceData.competitorData.size
                val readoutCount = currentProject.raceData.competitorData.count { it.readoutData != null }
                projectFile = projectSession.updateCurrentProject { project ->
                    EventProjectEditor.removeAllCompetitors(project)
                }
                hasUnsavedChanges = projectSession.hasUnsavedChanges
                recordActivity("Deleted all competitors.")
                projectStatusText = buildString {
                    append("Deleted $competitorCount competitors.")
                    if (readoutCount > 0) {
                        append(" Preserved $readoutCount matched readouts as unmatched readouts.")
                    }
                    append(" Unsaved changes.")
                }
                true
            }.getOrElse { error ->
                projectStatusText = "Edit failed: ${error.message ?: error::class.simpleName}"
                false
            }
        }

        fun updateProtectedIdealOrder(categoryId: String, idealOrderText: String) {
            val password = protectedCoursePassword ?: run {
                projectStatusText = "Unlock course order before editing."
                return
            }
            runCatching {
                val currentProject = projectFile
                    ?: throw IllegalStateException("Load an Event File before editing course order.")
                val trimmedIdealOrder = idealOrderText.trim()
                if (trimmedIdealOrder.isNotEmpty()) {
                    val assignedControls = assignedProtectedIdealOrderControls(currentProject, categoryId)
                    require(assignedControls.isNotEmpty()) {
                        "Assign controls to this category before editing course order."
                    }
                    ProtectedIdealOrderRules.validateAssignedToCategory(trimmedIdealOrder, assignedControls)
                }
                val encryptedIdealOrder = trimmedIdealOrder.takeIf { it.isNotEmpty() }?.let {
                    DesktopProtectedCourseOrder.encrypt(it, password)
                }
                projectFile = projectSession.updateCurrentProject { currentProject ->
                    EventProjectEditor.updateCategoryEncryptedIdealOrder(currentProject, categoryId, encryptedIdealOrder)
                }
                protectedIdealOrderByCategoryId = protectedIdealOrderByCategoryId + (categoryId to trimmedIdealOrder)
                hasUnsavedChanges = projectSession.hasUnsavedChanges
                projectStatusText = "Unsaved changes."
            }.onFailure { error ->
                projectStatusText = "Edit failed: ${error.message ?: error::class.simpleName}"
            }
        }

        fun syncProtectedCourseState(updatedProject: EventProjectFile, password: String) {
            protectedIdealOrderByCategoryId = updatedProject.raceData.categories.associate { categoryData ->
                val encryptedValue = categoryData.category.encryptedIdealOrder
                categoryData.category.id to if (encryptedValue.isNullOrBlank()) {
                    ""
                } else {
                    DesktopProtectedCourseOrder.decrypt(encryptedValue, password)
                }
            }
            protectedCourseInfoByCategoryId = updatedProject.raceData.categories.mapNotNull { categoryData ->
                categoryData.category.encryptedCourseInfo?.takeIf { it.isNotBlank() }?.let { encryptedValue ->
                    categoryData.category.id to DesktopProtectedCourseOrder.decryptCourseInfo(encryptedValue, password)
                }
            }.toMap()
            hasUnsavedChanges = projectSession.hasUnsavedChanges
        }

        fun useCalculatedCourseAnalysisRoute(application: DesktopCourseCalculatedRouteApplication): String {
            val password = protectedCoursePassword ?: run {
                projectStatusText = "Unlock course order before applying calculated route."
                return projectStatusText
            }
            return runCatching {
                val currentProject = projectFile
                    ?: throw IllegalStateException("Load an Event File before applying calculated route.")
                val currentCourseInfo = protectedCourseInfoByCategoryId[application.categoryId]
                    ?: throw IllegalStateException("Course data is missing for the selected category.")
                val (updatedProject, updatedCourseInfo) = DesktopCourseAnalysisApplier.applyCalculatedRoute(
                    projectFile = currentProject,
                    courseInfo = currentCourseInfo,
                    application = application,
                    password = password
                )
                projectFile = projectSession.updateCurrentProject { updatedProject }
                protectedIdealOrderByCategoryId = protectedIdealOrderByCategoryId + (application.categoryId to application.idealOrderText)
                protectedCourseInfoByCategoryId = protectedCourseInfoByCategoryId + (application.categoryId to updatedCourseInfo)
                hasUnsavedChanges = projectSession.hasUnsavedChanges
                projectStatusText = "Applied calculated route and fox numbering. Unsaved changes."
                projectStatusText
            }.getOrElse { error ->
                projectStatusText = "Apply calculated route failed: ${error.message ?: error::class.simpleName}"
                projectStatusText
            }
        }

        fun applyCourseAnalysisFoxRenumberingOnly(renumbering: DesktopCourseWaitRenumbering): String {
            val password = protectedCoursePassword ?: run {
                projectStatusText = "Unlock course order before applying fox renumbering."
                return projectStatusText
            }
            return runCatching {
                val currentProject = projectFile
                    ?: throw IllegalStateException("Load an Event File before applying fox renumbering.")
                val result = DesktopCourseAnalysisApplier.applyFoxRenumberingOnly(
                    projectFile = currentProject,
                    renumbering = renumbering,
                    password = password
                )
                projectFile = projectSession.updateCurrentProject { result.projectFile }
                syncProtectedCourseState(result.projectFile, password)
                projectStatusText =
                    "Applied fox renumbering to ${result.changedControlCount} controls across ${result.affectedCategoryCount} categories. Unsaved changes."
                projectStatusText
            }.getOrElse { error ->
                projectStatusText = "Apply fox renumbering failed: ${error.message ?: error::class.simpleName}"
                projectStatusText
            }
        }

        fun updateProtectedControlLocation(controlId: String, latitudeText: String, longitudeText: String): String {
            val password = protectedCoursePassword ?: run {
                projectStatusText = "Unlock course order before updating control locations."
                return projectStatusText
            }
            return runCatching {
                val currentProject = projectFile
                    ?: throw IllegalStateException("Load an Event File before updating control locations.")
                val result = DesktopProtectedControlLocationUpdater.applyControlLocation(
                    projectFile = currentProject,
                    courseInfoByCategoryId = protectedCourseInfoByCategoryId,
                    controlId = controlId,
                    latitudeText = latitudeText,
                    longitudeText = longitudeText,
                    password = password,
                    elevationLookup = DesktopVenueElevationCache::elevationMeters
                )
                projectFile = projectSession.updateCurrentProject { result.projectFile }
                protectedCourseInfoByCategoryId = result.courseInfoByCategoryId
                hasUnsavedChanges = projectSession.hasUnsavedChanges
                projectStatusText = if (result.affectedCategoryCount > 0) {
                    "Updated ${result.controlLabel} location in ${result.affectedCategoryCount} stored course(s). Stored route geometry invalidated. Unsaved changes."
                } else {
                    "Updated ${result.controlLabel} location. No stored courses referenced it. Unsaved changes."
                }
                projectStatusText
            }.getOrElse { error ->
                projectStatusText = "Control location update failed: ${error.message ?: error::class.simpleName}"
                projectStatusText
            }
        }

        fun updateProtectedCoursePassword(oldPassword: String, newPassword: String, confirmPassword: String): Boolean {
            val currentProject = projectSession.currentProject ?: return false
            val trimmedOldPassword = oldPassword.trim()
            val trimmedNewPassword = newPassword.trim()
            val trimmedConfirmPassword = confirmPassword.trim()
            if (trimmedNewPassword.isEmpty()) {
                projectStatusText = "New Event Password cannot be blank."
                return false
            }
            if (trimmedNewPassword != trimmedConfirmPassword) {
                projectStatusText = "New Event Passwords do not match."
                return false
            }

            val hasEncryptedCourseProtection = currentProject.raceData.categories.any { categoryData ->
                !categoryData.category.encryptedIdealOrder.isNullOrBlank() ||
                    !categoryData.category.encryptedCourseInfo.isNullOrBlank()
            }
            if (hasEncryptedCourseProtection && trimmedOldPassword.isEmpty()) {
                projectStatusText = "Current Event Password cannot be blank."
                return false
            }

            return runCatching {
                val updatedProject = if (hasEncryptedCourseProtection) {
                    DesktopProtectedCourseOrder.reencryptProjectCourseProtection(
                        currentProject,
                        oldPassword = trimmedOldPassword,
                        newPassword = trimmedNewPassword
                    )
                } else {
                    currentProject.copy(
                        raceData = currentProject.raceData.copy(
                            categories = currentProject.raceData.categories.map { categoryData ->
                                categoryData.copy(
                                    category = categoryData.category.copy(
                                        encryptedIdealOrder = DesktopProtectedCourseOrder.encrypt("", trimmedNewPassword)
                                    )
                                )
                            }
                        )
                    )
                }
                projectFile = projectSession.updateCurrentProject { updatedProject }
                protectedCoursePassword = trimmedNewPassword
                protectedIdealOrderByCategoryId = updatedProject.raceData.categories.associate { categoryData ->
                    val encryptedValue = categoryData.category.encryptedIdealOrder
                    categoryData.category.id to if (encryptedValue.isNullOrBlank()) {
                        ""
                    } else {
                        DesktopProtectedCourseOrder.decrypt(encryptedValue, trimmedNewPassword)
                    }
                }
                protectedCourseInfoByCategoryId = updatedProject.raceData.categories.mapNotNull { categoryData ->
                    categoryData.category.encryptedCourseInfo?.takeIf { it.isNotBlank() }?.let { encryptedValue ->
                        categoryData.category.id to DesktopProtectedCourseOrder.decryptCourseInfo(encryptedValue, trimmedNewPassword)
                    }
                }.toMap()
                hasUnsavedChanges = projectSession.hasUnsavedChanges
                projectStatusText = if (hasEncryptedCourseProtection) {
                    "Event Password updated. Unsaved changes."
                } else {
                    "Event Password set. Unsaved changes."
                }
                true
            }.getOrElse { error ->
                projectStatusText = "Event Password update failed: ${error.message ?: error::class.simpleName}"
                false
            }
        }

        fun startProtectedCourseElevationFetch(sourceName: String, categoryIds: List<String>, password: String) {
            val currentProject = projectSession.currentProject ?: return
            if (courseKmlKmzElevationJob?.isActive == true) {
                return
            }
            projectStatusText = "Retrieving course elevations..."
            courseKmlKmzElevationProgress = CourseKmlKmzElevationProgressUiState(
                sourceName = sourceName,
                categoryName = "",
                completedPointCount = 0,
                totalPointCount = 1
            )
            courseKmlKmzElevationJob = appCoroutineScope.launch {
                val result = runCatching {
                    DesktopCourseKmlImporter.fetchProtectedCourseElevations(
                        projectFile = currentProject,
                        categoryIds = categoryIds,
                        password = password,
                        onProgress = { progress ->
                            courseKmlKmzElevationProgress = CourseKmlKmzElevationProgressUiState(
                                sourceName = sourceName,
                                categoryName = progress.categoryName,
                                completedPointCount = progress.completedPointCount,
                                totalPointCount = progress.totalPointCount
                            )
                        }
                    )
                }
                result.onSuccess { (updatedProject, elevationResult) ->
                    projectFile = projectSession.updateCurrentProject { updatedProject }
                    syncProtectedCourseState(updatedProject, password)
                    projectStatusText = when {
                        elevationResult.resolvedPointCount > 0 ->
                            "Resolved ${elevationResult.resolvedPointCount} course elevations for ${elevationResult.categoryCount} categories (${elevationResult.cachedPointCount} cached, ${elevationResult.elevatedPointCount} downloaded). Unsaved changes."
                        elevationResult.sampledPointCount == 0 ->
                            "No missing course elevations found for ${elevationResult.categoryCount} categories."
                        else ->
                            "Course elevation retrieval completed, but no elevation values were returned for ${elevationResult.sampledPointCount} requested points."
                    }
                }.onFailure { error ->
                    projectStatusText = if (error is CancellationException) {
                        "Course elevation retrieval canceled. Imported route data kept without fetched elevations."
                    } else {
                        "Course elevation retrieval failed: ${error.message ?: error::class.simpleName}"
                    }
                }
                courseKmlKmzElevationProgress = null
                courseKmlKmzElevationJob = null
            }
        }

        fun startCourseKmlKmzElevationFetch(review: PendingCourseKmlKmzImportReview) {
            startProtectedCourseElevationFetch(
                sourceName = review.sourceName,
                categoryIds = review.summary.matchedCategoryIds,
                password = review.password
            )
        }

        fun startCourseAnalysisElevationFetch(categoryId: String) {
            val password = protectedCoursePassword ?: run {
                projectStatusText = "Unlock course order before retrieving course elevations."
                return
            }
            val categoryName = projectSession.currentProject
                ?.raceData
                ?.categories
                ?.firstOrNull { it.category.id == categoryId }
                ?.category
                ?.name
                ?: "Course Analysis"
            startProtectedCourseElevationFetch(
                sourceName = "Course Analysis",
                categoryIds = listOf(categoryId),
                password = password
            )
            projectStatusText = "Retrieving course elevations for $categoryName..."
        }

        fun startVenueElevationCacheDownload(
            venueName: String,
            boundingBox: DesktopVenueElevationBoundingBox,
            resolutionMeters: Double,
            bufferMeters: Double,
            source: DesktopVenueElevationCacheSource,
            sourceUrl: String = ""
        ) {
            if (source == DesktopVenueElevationCacheSource.LocalLidarRaster && sourceUrl.isBlank()) {
                projectStatusText = "Local LiDAR Raster requires a source file."
                return
            }
            if (venueElevationCacheJob?.isActive == true) {
                return
            }
            val cleanVenueName = venueName.trim().ifBlank { "Venue" }
            val cacheActionText = if (source == DesktopVenueElevationCacheSource.LocalLidarRaster) {
                "Creating"
            } else {
                "Downloading"
            }
            projectStatusText = "$cacheActionText ${source.label} elevation cache for $cleanVenueName..."
            DesktopDebugLog.info(
                "ElevationCache",
                "$cacheActionText started venue=$cleanVenueName source=${source.label} resolution=${resolutionMeters}m buffer=${bufferMeters}m " +
                    "bounds=${boundingBox.minLatitude},${boundingBox.minLongitude}..${boundingBox.maxLatitude},${boundingBox.maxLongitude}"
            )
            venueElevationCacheProgress = VenueElevationCacheProgressUiState(
                venueName = cleanVenueName,
                completedPointCount = 0,
                totalPointCount = 1
            )
            venueElevationCacheJob = appCoroutineScope.launch {
                val result = runCatching {
                    DesktopVenueElevationCache.download(
                        venueName = cleanVenueName,
                        boundingBox = boundingBox,
                        resolutionMeters = resolutionMeters,
                        bufferMeters = bufferMeters,
                        source = source,
                        sourceUrl = sourceUrl,
                        onProgress = { progress ->
                            venueElevationCacheProgress = VenueElevationCacheProgressUiState(
                                venueName = progress.venueName,
                                completedPointCount = progress.completedPointCount,
                                totalPointCount = progress.totalPointCount
                            )
                        }
                    )
                }
                result.onSuccess { summary ->
                    venueElevationCacheRefreshToken++
                    projectStatusText =
                        "Downloaded ${summary.sourceName} elevation cache for ${summary.venueName}: ${summary.resolvedPointCount}/${summary.pointCount} points at ${summary.resolutionMeters.roundToInt()} m."
                }.onFailure { error ->
                    projectStatusText = if (error is CancellationException) {
                        DesktopDebugLog.info(
                            "ElevationCache",
                            "$cacheActionText canceled venue=$cleanVenueName source=${source.label}"
                        )
                        "Elevation cache ${cacheActionText.lowercase()} canceled."
                    } else {
                        DesktopDebugLog.error(
                            "ElevationCache",
                            "$cacheActionText failed venue=$cleanVenueName source=${source.label}: ${error.message ?: error::class.simpleName}"
                        )
                        "Elevation cache ${cacheActionText.lowercase()} failed: ${error.message ?: error::class.simpleName}"
                    }
                }
                venueElevationCacheProgress = null
                venueElevationCacheJob = null
            }
        }

        fun openVenueElevationCacheFolder() {
            val directory = DesktopVenueElevationCache.cacheDirectory()
            runCatching {
                Files.createDirectories(directory)
                if (!Desktop.isDesktopSupported()) {
                    error("Opening folders is not supported on this system.")
                }
                val desktop = Desktop.getDesktop()
                if (!desktop.isSupported(Desktop.Action.OPEN)) {
                    error("Opening folders is not supported on this system.")
                }
                desktop.open(directory.toFile())
            }.onSuccess {
                projectStatusText = "Opened elevation cache folder: $directory"
            }.onFailure { error ->
                projectStatusText = "Could not open elevation cache folder: ${error.message ?: error::class.simpleName}"
            }
        }

        fun applyCourseKmlKmzImport(
            review: PendingCourseKmlKmzImportReview,
            fetchElevations: Boolean,
            applyCategoryAssignments: Boolean,
            createMissingCategories: Boolean
        ) {
            val selectedSummary = if (createMissingCategories) {
                review.createdMissingCategorySummary ?: review.summary
            } else {
                review.summary
            }
            val selectedProject = if (createMissingCategories) {
                review.createdMissingCategoryProject ?: review.updatedProject
            } else {
                review.updatedProject
            }
            val updatedProject = if (applyCategoryAssignments) {
                DesktopCourseKmlImporter.applyCategoryAssignmentUpdates(
                    projectFile = selectedProject,
                    updates = selectedSummary.categoryAssignmentUpdates
                )
            } else {
                selectedProject
            }
            checkpointBeforeImport("controls/route KML/KMZ import ${review.sourceName}")
            projectFile = projectSession.updateCurrentProject { updatedProject }
            syncProtectedCourseState(updatedProject, review.password)
            pendingCourseKmlKmzImportReview = null
            recordActivity("Applied controls/route KML/KMZ import ${review.sourceName}.")
            recentImportReport = DesktopImportReport(
                title = "Controls/route KML/KMZ: ${review.sourceName}",
                lines = withRollbackBackupLine(listOf(
                    "${selectedSummary.importedCategoryCount} categories received stored route data.",
                    "${selectedSummary.duplicateCategoryCount} duplicate categories skipped.",
                    "${selectedSummary.changedControlLocationCount} control locations updated.",
                    "${selectedSummary.categoryAssignmentUpdates.size.takeIf { applyCategoryAssignments } ?: 0} assigned-control lists replaced.",
                    "${selectedSummary.createdCategoryNames.size} missing categories created.",
                    "${selectedSummary.missingCategoryNames.size} category names were missing before review."
                ) + listOf(updatedProject.resultImpactWarning("Course data changed").trim()).filter { it.isNotBlank() } +
                    selectedSummary.eventTypeWarnings)
            )
            if (fetchElevations) {
                startCourseKmlKmzElevationFetch(review.copy(summary = selectedSummary))
            } else {
                projectStatusText = if (selectedSummary.isDuplicateOnly) {
                    "Duplicate controls/route KML/KMZ request: identical file already imported. No route data reloaded."
                } else {
                    val duplicateText = selectedSummary.duplicateCategoryCount
                        .takeIf { it > 0 }
                        ?.let { " $it duplicate categories skipped." }
                        .orEmpty()
                    val locationText = selectedSummary.changedControlLocationCount
                        .takeIf { it > 0 }
                        ?.let { " Updated $it control locations." }
                        .orEmpty()
                    val createdText = selectedSummary.createdCategoryNames
                        .takeIf { it.isNotEmpty() }
                        ?.let { " Created ${it.size} categories without competitors." }
                        .orEmpty()
                    val assignedText = if (applyCategoryAssignments) {
                        selectedSummary.categoryAssignmentUpdates.size
                            .takeIf { it > 0 }
                            ?.let { " Updated assigned controls for $it categories." }
                            .orEmpty()
                    } else {
                        ""
                    }
                    if (selectedSummary.importedCategoryCount == 0 && selectedSummary.changedControlLocationCount > 0) {
                        "Updated ${selectedSummary.changedControlLocationCount} control locations.$assignedText$duplicateText$createdText Unsaved changes."
                    } else if (
                        selectedSummary.importedCategoryCount == 0 &&
                        selectedSummary.assignedCategoryControlCount > 0 &&
                        applyCategoryAssignments
                    ) {
                        "Updated assigned controls for ${selectedSummary.categoryAssignmentUpdates.size} categories.$duplicateText$createdText Unsaved changes."
                    } else {
                        "Imported controls/route data for ${selectedSummary.importedCategoryCount} categories.$locationText$assignedText$duplicateText$createdText Unsaved changes."
                    }
                }
            }
        }

        fun startCourseKmlKmzImport(path: Path, password: String, categoryOverrideId: String? = null) {
            val currentProject = projectSession.currentProject ?: return
            if (isImportingCourseKmlKmz) {
                return
            }
            isImportingCourseKmlKmz = true
            projectStatusText = "Importing controls/route KML/KMZ..."
            appCoroutineScope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        val preview = DesktopCourseKmlImporter.importProtectedCourseInfo(
                            path = path,
                            projectFile = currentProject,
                            password = password,
                            categoryOverrideId = categoryOverrideId
                        )
                        val createdPreview = preview.second.missingCategoryNames
                            .takeIf { it.isNotEmpty() }
                            ?.let {
                                DesktopCourseKmlImporter.importProtectedCourseInfo(
                                    path = path,
                                    projectFile = currentProject,
                                    password = password,
                                    categoryOverrideId = categoryOverrideId,
                                    createMissingCategories = true
                                )
                            }
                        CourseKmlKmzImportPreview(
                            updatedProject = preview.first,
                            summary = preview.second,
                            createdMissingCategoryProject = createdPreview?.first,
                            createdMissingCategorySummary = createdPreview?.second
                        )
                    }
                }
                result.onSuccess { preview ->
                    val updatedProject = preview.updatedProject
                    val summary = preview.summary
                    val categoryOptions = currentProject.raceData.categories
                        .sortedWith(EventCategorySort.byDisplayName)
                        .map { it.category.id to it.category.name }
                    if (
                        categoryOverrideId == null &&
                        summary.routeCount == 1 &&
                        summary.matchedCategoryCount == 0 &&
                        categoryOptions.isNotEmpty()
                    ) {
                        pendingCourseKmlKmzImportReview = null
                        pendingCourseKmlKmzCategoryMapping = PendingCourseKmlKmzCategoryMapping(
                            sourceName = path.fileName.toString(),
                            path = path,
                            password = password,
                            categoryOptions = categoryOptions,
                            matchedControlPointCount = summary.matchedControlPointCount,
                            matchedFoxCount = summary.matchedFoxCount,
                            matchedBeaconCount = summary.matchedBeaconCount,
                            matchedSpectatorCount = summary.matchedSpectatorCount,
                            controlPointCount = summary.controlPointCount,
                            labelConversions = summary.labelConversions
                        )
                        projectStatusText = "Choose the Event File category for this KML/KMZ route."
                    } else if (
                        summary.routeCount > 0 &&
                        summary.matchedCategoryCount == 0 &&
                        categoryOptions.isEmpty() &&
                        summary.missingCategoryNames.isEmpty()
                    ) {
                        pendingCourseKmlKmzImportReview = null
                        pendingCourseKmlKmzCategoryMapping = null
                        projectStatusText =
                            "KML/KMZ route data was not applied because the Event File has no categories."
                    } else if (summary.isControlLocationNoOp && !summary.hasLabelConversions) {
                        pendingCourseKmlKmzImportReview = null
                        pendingCourseKmlKmzCategoryMapping = null
                        projectStatusText =
                            "KML/KMZ import found ${summary.matchedControlPointCount} matching controls, but no control locations changed."
                    } else if (summary.isDuplicateOnly && !summary.hasDuplicateMissingElevations) {
                        pendingCourseKmlKmzImportReview = null
                        pendingCourseKmlKmzCategoryMapping = null
                        projectStatusText =
                            "Duplicate controls/route KML/KMZ request: identical file already imported and all elevations are available."
                    } else {
                        pendingCourseKmlKmzCategoryMapping = null
                        pendingCourseKmlKmzImportReview = PendingCourseKmlKmzImportReview(
                            sourceName = path.fileName.toString(),
                            path = path,
                            updatedProject = updatedProject,
                            summary = summary,
                            createdMissingCategoryProject = preview.createdMissingCategoryProject,
                            createdMissingCategorySummary = preview.createdMissingCategorySummary,
                            password = password
                        )
                        projectStatusText = if (summary.isDuplicateOnly) {
                            "Identical controls/route data already imported. Review missing elevation retrieval option."
                        } else {
                            "Review imported controls/route data before applying it."
                        }
                    }
                }.onFailure { error ->
                    pendingCourseKmlKmzCategoryMapping = null
                    projectStatusText = "Controls/route KML/KMZ import failed: ${error.message ?: error::class.simpleName}"
                }
                isImportingCourseKmlKmz = false
            }
        }

        fun chooseImportCourseKmlKmzUnlocked(password: String) {
            if (isImportingCourseKmlKmz) {
                return
            }
            DesktopFileDialogs.chooseImportKmlKmz()?.let { path ->
                startCourseKmlKmzImport(path, password)
            }
        }

        fun chooseImportCourseKmlKmz() {
            val password = protectedCoursePassword
            if (password == null) {
                projectStatusText = "Unlock course order before importing KML/KMZ controls/route data."
                pendingCourseKmlKmzUnlockAction = CourseKmlKmzUnlockAction.Import
                return
            }
            chooseImportCourseKmlKmzUnlocked(password)
        }

        fun chooseExportCourseKmlKmzUnlocked(password: String) {
            val currentProject = projectSession.currentProject ?: return
            DesktopFileDialogs.chooseExportControlsRouteKmlKmz(currentProject.raceData.race.name)?.let { target ->
                runCatching {
                    val summary = DesktopControlsRouteKmlKmzExporter.exportEncryptedZip(
                        target = target,
                        projectFile = currentProject,
                        password = password
                    )
                    syncProjectState()
                    val formatName = summary.outputFormat.contentExtension.uppercase()
                    projectStatusText =
                        "Exported ${target.path.fileName} as an encrypted ZIP containing $formatName " +
                            "with ${summary.controlCatalogCount} controls and ${summary.routeCount} routes."
                }.onFailure { error ->
                    projectStatusText = "Controls/route KML/KMZ export failed: ${error.message ?: error::class.simpleName}"
                }
            }
        }

        fun chooseExportCourseKmlKmz() {
            projectStatusText = "Enter the Event Password before exporting protected controls/route KML/KMZ data."
            pendingCourseKmlKmzUnlockAction = CourseKmlKmzUnlockAction.Export
        }

        fun importAndroidRaceBackupJson(path: Path) {
            runCatching {
                lockProtectedCourseOrder()
                val imported = DesktopProjectFiles.importAndroidRaceBackupJson(path) { UUID.randomUUID().toString() }
                projectFile = projectSession.newProject(imported)
                newEventDraftProject = null
                hasUnsavedEventDefinitionChanges = false
                isEventDefinitionSaveDialogVisible = false
                syncProjectState()
                projectStatusText = "Imported ${path.fileName}"
            }.onFailure { error ->
                projectStatusText = "Import failed: ${error.message ?: error::class.simpleName}"
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

        fun exportAndroidRaceBackupJson() {
            val currentProject = projectSession.currentProject ?: return
            DesktopFileDialogs.chooseExportAndroidRaceBackupJson(currentProject.raceData.race.name)?.let { path ->
                runCatching {
                    DesktopProjectFiles.exportAndroidRaceBackupJson(path, currentProject)
                    syncProjectState()
                    projectStatusText = "Exported ${path.fileName}"
                }.onFailure { error ->
                    projectStatusText = "Export failed: ${error.message ?: error::class.simpleName}"
                }
            }
        }

        fun exportLiveResultsJson() {
            val currentProject = projectSession.currentProject ?: return
            DesktopFileDialogs.chooseExportLiveResultsJson()?.let { path ->
                runCatching {
                    DesktopProjectFiles.exportLiveResultsJson(path, currentProject)
                    syncProjectState()
                    projectStatusText = "Exported ${path.fileName}"
                }.onFailure { error ->
                    projectStatusText = "Export failed: ${error.message ?: error::class.simpleName}"
                }
            }
        }

        fun exportFinalResultsJson() {
            val currentProject = projectSession.currentProject ?: return
            DesktopFileDialogs.chooseExportFinalResultsJson()?.let { path ->
                runCatching {
                    DesktopProjectFiles.exportFinalResultsJson(
                        path,
                        currentProject,
                        protectedCourseInfoByCategoryId.takeIf { protectedCoursePassword != null } ?: emptyMap()
                    )
                    syncProjectState()
                    projectStatusText = "Exported ${path.fileName}"
                }.onFailure { error ->
                    projectStatusText = "Export failed: ${error.message ?: error::class.simpleName}"
                }
            }
        }

        fun exportIofStartListXml() {
            val currentProject = projectSession.currentProject ?: return
            DesktopFileDialogs.chooseExportIofXml("Export IOF Start List XML")?.let { path ->
                runCatching {
                    DesktopProjectFiles.exportIofStartListXml(path, currentProject)
                    syncProjectState()
                    projectStatusText = "Exported ${path.fileName}"
                }.onFailure { error ->
                    projectStatusText = "Export failed: ${error.message ?: error::class.simpleName}"
                }
            }
        }

        fun exportIofResultListXml() {
            val currentProject = projectSession.currentProject ?: return
            DesktopFileDialogs.chooseExportIofXml("Export IOF Result List XML")?.let { path ->
                runCatching {
                    DesktopProjectFiles.exportIofResultListXml(path, currentProject)
                    syncProjectState()
                    projectStatusText = "Exported ${path.fileName}"
                }.onFailure { error ->
                    projectStatusText = "Export failed: ${error.message ?: error::class.simpleName}"
                }
            }
        }

        fun exportResultsHtml() {
            val currentProject = projectSession.currentProject ?: return
            DesktopFileDialogs.chooseExportHtml("Export Results HTML")?.let { path ->
                runCatching {
                    DesktopProjectFiles.exportResultsHtml(
                        path,
                        currentProject,
                        protectedCourseInfoByCategoryId.takeIf { protectedCoursePassword != null } ?: emptyMap()
                    )
                    syncProjectState()
                    projectStatusText = "Exported ${path.fileName}"
                }.onFailure { error ->
                    projectStatusText = "Export failed: ${error.message ?: error::class.simpleName}"
                }
            }
        }

        fun exportResultsText() {
            val currentProject = projectSession.currentProject ?: return
            DesktopFileDialogs.chooseExportTxt("Export Results TXT")?.let { path ->
                runCatching {
                    DesktopProjectFiles.exportResultsText(
                        path,
                        currentProject,
                        protectedCourseInfoByCategoryId.takeIf { protectedCoursePassword != null } ?: emptyMap()
                    )
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
                    pendingCompetitorsCsvImportReview = PendingCompetitorsCsvImportReview(
                        path = path,
                        missingCategoryNames = missingCompetitorCategoryNames(
                            path = path,
                            projectFile = requireNotNull(projectSession.currentProject)
                        )
                    )
                    syncCompetitorsCsvImport = false
                }.onFailure { error ->
                    projectStatusText = "Import failed: ${error.message ?: error::class.simpleName}"
                }
            }
        }

        fun importCompetitorsCsv(path: Path, synchronizeToCsv: Boolean, createMissingCategories: Boolean) {
            runCatching {
                val csvText = Files.readString(path)
                val result = EventCsvImports.parseAndroidCompetitorRows(csvText)
                checkpointBeforeImport("competitors CSV import ${path.fileName}")
                var importWarnings = emptyList<String>()
                var importedRows = 0
                var updatedRows = 0
                var skippedRows = 0
                var deletedRows = 0
                projectFile = projectSession.updateCurrentProject { currentProject ->
                    val outcome = EventProjectEditor.importCompetitorRowsWithOutcome(
                        projectFile = currentProject,
                        rows = result.rows,
                        competitorIdFactory = { UUID.randomUUID().toString() },
                        categoryIdFactory = { UUID.randomUUID().toString() },
                        duplicatePolicy = if (synchronizeToCsv) {
                            CompetitorCsvImportDuplicatePolicy.UPDATE_EXISTING_BY_IMPORT_KEY
                        } else {
                            CompetitorCsvImportDuplicatePolicy.SKIP_EXISTING_BY_IMPORT_KEY
                        },
                        deleteMissingByImportKey = synchronizeToCsv,
                        createMissingCategories = createMissingCategories
                    )
                    importWarnings = outcome.warnings
                    importedRows = outcome.importedCount
                    updatedRows = outcome.updatedCount
                    skippedRows = outcome.skippedCount
                    deletedRows = outcome.deletedCount
                    outcome.projectFile
                }
                syncProjectState()
                pendingCompetitorsCsvImportReview = null
                recordActivity("Applied competitors CSV import ${path.fileName}.")
                recentImportReport = DesktopImportReport(
                    title = "Competitors CSV: ${path.fileName}",
                    lines = withRollbackBackupLine(listOf(
                        "$importedRows competitors added.",
                        "$updatedRows competitors updated.",
                        "$skippedRows competitors skipped.",
                        "$deletedRows competitors removed by sync.",
                        "${result.invalidLines.size} invalid rows skipped."
                    ) + importWarnings)
                )
                projectStatusText = competitorImportStatusText(
                    importedRows = importedRows,
                    updatedRows = updatedRows,
                    skippedRows = skippedRows,
                    deletedRows = deletedRows,
                    invalidRows = result.invalidLines.size,
                    fileName = path.fileName.toString()
                ) + warningStatusSuffix(importWarnings)
            }.onFailure { error ->
                projectStatusText = "Import failed: ${error.message ?: error::class.simpleName}"
            }
        }

        fun importCategoriesCsv() {
            DesktopFileDialogs.chooseImportCsv("Import Categories CSV")?.let { path ->
                runCatching {
                    val currentProject = requireNotNull(projectSession.currentProject)
                    val result = EventCsvImports.parseAndroidCategoryRows(Files.readString(path))
                    pendingCategoriesCsvImportReview = PendingCategoriesCsvImportReview(
                        path = path,
                        rows = result.rows,
                        invalidLineCount = result.invalidLines.size,
                        preview = DesktopImportPreviews.categoryCsvPreview(
                            projectFile = currentProject,
                            sourceName = path.fileName.toString(),
                            rows = result.rows
                        )
                    )
                    projectStatusText = "Review categories CSV import before applying it."
                }.onFailure { error ->
                    projectStatusText = "Import failed: ${error.message ?: error::class.simpleName}"
                }
            }
        }

        fun applyCategoriesCsvImport(review: PendingCategoriesCsvImportReview) {
            runCatching {
                lockProtectedCourseOrder()
                checkpointBeforeImport("categories CSV import ${review.path.fileName}")
                var importedRows = 0
                var updatedRows = 0
                projectFile = projectSession.updateCurrentProject { currentProject ->
                    val outcome = EventProjectEditor.importCategoryRowsWithOutcome(
                        projectFile = currentProject,
                        rows = review.rows,
                        categoryIdFactory = { UUID.randomUUID().toString() },
                        controlPointIdFactory = { _, _ -> UUID.randomUUID().toString() }
                    )
                    importedRows = outcome.importedCount
                    updatedRows = outcome.updatedCount
                    outcome.projectFile
                }
                syncProjectState()
                pendingCategoriesCsvImportReview = null
                recordActivity("Applied categories CSV import ${review.path.fileName}.")
                recentImportReport = DesktopImportReport(
                    title = "Categories CSV: ${review.path.fileName}",
                    lines = withRollbackBackupLine(listOf(
                        "$importedRows categories added.",
                        "$updatedRows categories updated by name.",
                        "${review.invalidLineCount} invalid rows skipped.",
                        "${review.preview.affectedCompetitorCount} competitors are in updated categories.",
                        "${review.preview.categoriesWithAssignedControlsReplacedCount} existing assigned-control lists replaced.",
                        "${review.preview.categoriesWithProtectedCoursePreservedCount} protected course records preserved."
                    ) + listOf(projectFile?.resultImpactWarning("Category course data changed")?.trim().orEmpty()).filter { it.isNotBlank() } +
                        review.preview.eventTypeWarnings)
                )
                projectStatusText =
                    "Imported ${review.path.fileName}: $importedRows added, $updatedRows updated, ${review.invalidLineCount} invalid."
            }.onFailure { error ->
                projectStatusText = "Import failed: ${error.message ?: error::class.simpleName}"
            }
        }

        fun importControlsCsv() {
            DesktopFileDialogs.chooseImportCsv("Import Controls CSV")?.let { path ->
                runCatching {
                    val currentProject = requireNotNull(projectSession.currentProject)
                    val result = EventCsvImports.parseControlRows(Files.readString(path))
                    pendingControlsCsvImportReview = PendingControlsCsvImportReview(
                        path = path,
                        rows = result.rows,
                        invalidLineCount = result.invalidLines.size,
                        preview = DesktopImportPreviews.controlsCsvPreview(
                            projectFile = currentProject,
                            sourceName = path.fileName.toString(),
                            rows = result.rows,
                            protectedCourseInfoByCategoryId = protectedCourseInfoByCategoryId
                        )
                    )
                    projectStatusText = "Review controls CSV import before applying it."
                }.onFailure { error ->
                    projectStatusText = "Import failed: ${error.message ?: error::class.simpleName}"
                }
            }
        }

        fun applyControlsCsvImport(review: PendingControlsCsvImportReview, syncMissingControls: Boolean) {
            runCatching {
                checkpointBeforeImport("controls CSV import ${review.path.fileName}")
                var deletedMissingControls = 0
                var skippedMissingControls = 0
                projectFile = projectSession.updateCurrentProject { currentProject ->
                    require(!syncMissingControls || !currentProject.hasLockedProtectedCourseData(protectedCoursePassword != null)) {
                        "Course data is locked. Unlock course data before synchronizing control deletions."
                    }
                    val importedIdentities = review.rows.mapTo(mutableSetOf()) { it.siCode to it.type }
                    val missingExistingControls = currentProject.raceData.controls
                        .filterNot { it.siCode to it.type in importedIdentities }
                    val usedControlIds = currentProject.raceData.categories
                        .flatMap { it.controlPoints.map { controlPoint -> controlPoint.controlId } + it.publicControlIds }
                        .toSet() +
                        protectedCourseInfoByCategoryId.values.flatMap { courseInfo ->
                            courseInfo.controlPoints.map { it.controlId } + courseInfo.courseObjects.map { it.id }
                        }
                    val removableMissingControlIds = if (syncMissingControls) {
                        missingExistingControls.map { it.id }.filterNot { it in usedControlIds }
                    } else {
                        emptyList()
                    }
                    skippedMissingControls = if (syncMissingControls) {
                        missingExistingControls.size - removableMissingControlIds.size
                    } else {
                        0
                    }
                    deletedMissingControls = removableMissingControlIds.size
                    val importedProject = EventProjectEditor.importControlRows(
                        currentProject,
                        review.rows,
                        controlIdFactory = { UUID.randomUUID().toString() }
                    )
                    removableMissingControlIds.fold(importedProject) { project, controlId ->
                        EventProjectEditor.removeControl(project, controlId)
                    }
                }
                syncProjectState()
                pendingControlsCsvImportReview = null
                recordActivity("Applied controls CSV import ${review.path.fileName}.")
                recentImportReport = DesktopImportReport(
                    title = "Controls CSV: ${review.path.fileName}",
                    lines = withRollbackBackupLine(listOf(
                        "${review.preview.addedCount} controls added.",
                        "${review.preview.changedCount} controls updated.",
                        "${review.preview.unchangedCount} controls unchanged.",
                        "${review.invalidLineCount} invalid rows skipped.",
                        "${review.preview.affectedAssignedCategoryCount} assigned categories affected.",
                        "${review.preview.affectedProtectedCourseCount} stored courses affected."
                    ) + (
                        if (syncMissingControls) {
                            listOf(
                                "$deletedMissingControls controls missing from the CSV removed.",
                                "$skippedMissingControls missing controls kept because they are used."
                            )
                        } else {
                            listOf("${review.preview.missingExistingCount} existing controls were missing from the CSV and kept.")
                        }
                        ) + listOf(projectFile?.resultImpactWarning("Control definitions changed")?.trim().orEmpty()).filter { it.isNotBlank() } +
                        review.preview.eventTypeWarnings)
                )
                projectStatusText = importStatusText(
                    "Imported",
                    review.rows.size,
                    review.invalidLineCount,
                    review.path.fileName.toString()
                )
            }.onFailure { error ->
                projectStatusText = "Import failed: ${error.message ?: error::class.simpleName}"
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
                PendingDirtyProjectAction.ExitApplication -> {
                    localResultServer.stop()
                    exitApplication()
                }
                PendingDirtyProjectAction.NewProject -> createNewProject()
                is PendingDirtyProjectAction.OpenProject -> openProject(action.path)
                is PendingDirtyProjectAction.ImportAndroidRaceBackup -> importAndroidRaceBackupJson(action.path)
                PendingDirtyProjectAction.CloseProject -> closeProject(
                    discardUnsavedChanges = DesktopDirtyProjectActions.shouldDiscardForClose(saveFirst)
                )
            }
        }

        requestWindowClose = {
            pendingDirtyProjectAction = DesktopDirtyProjectActions.pendingActionOrNull(
                hasProtectedUnsavedChanges(),
                PendingDirtyProjectAction.ExitApplication
            )
            if (pendingDirtyProjectAction == null) {
                localResultServer.stop()
                exitApplication()
            }
        }

        fun chooseOpenEventFile() {
            DesktopFileDialogs.chooseOpenProject()?.let { path ->
                pendingDirtyProjectAction = DesktopDirtyProjectActions.pendingActionOrNull(
                    hasProtectedUnsavedChanges(),
                    PendingDirtyProjectAction.OpenProject(path)
                )
                if (pendingDirtyProjectAction == null) {
                    openProject(path)
                }
            }
        }

        fun chooseImportAndroidRaceBackupJson() {
            DesktopFileDialogs.chooseImportAndroidRaceBackupJson()?.let { path ->
                pendingDirtyProjectAction = DesktopDirtyProjectActions.pendingActionOrNull(
                    hasProtectedUnsavedChanges(),
                    PendingDirtyProjectAction.ImportAndroidRaceBackup(path)
                )
                if (pendingDirtyProjectAction == null) {
                    importAndroidRaceBackupJson(path)
                }
            }
        }

        fun showEventRegImportDialog() {
            eventRegImportUrl = DesktopEventRegImportPreferences.lastRegistrationUrl()
            isEventRegImportDialogVisible = true
        }

        fun showEventRegCompetitorCsvImportDialog() {
            eventRegImportUrl = DesktopEventRegImportPreferences.lastRegistrationUrl()
            isEventRegCompetitorCsvImportDialogVisible = true
        }

        fun importEventRegWebsite(url: String) {
            if (isImportingEventRegWebsite) {
                return
            }
            val trimmedUrl = url.trim()
            isImportingEventRegWebsite = true
            projectStatusText = "Importing EventReg website..."
            DesktopEventRegImportPreferences.rememberRegistrationUrl(trimmedUrl)
            appCoroutineScope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        DesktopEventRegImporter.importFromWebsite(
                            url = trimmedUrl,
                            outputDirectory = DesktopEventFileLocations.preparePreferredEventFileDirectory(),
                            startDateTimeIso = DesktopDateTimeText.isoText(DesktopDateTimeText.defaultStartDateTime())
                        )
                    }
                }
                result.onSuccess { importResult ->
                    val totalCompetitors = importResult.generatedFiles.sumOf { it.competitorCount }
                    projectStatusText =
                        "Generated ${importResult.generatedFiles.size} Event Files with $totalCompetitors competitor entries in ${importResult.outputDirectory}."
                    isEventRegImportDialogVisible = false
                    DesktopDebugLog.info(
                        "EventReg",
                        "Generated ${importResult.generatedFiles.size} Event Files from ${importResult.sourceUrl}"
                    )
                }.onFailure { error ->
                    projectStatusText = "EventReg import failed: ${error.message ?: error::class.simpleName}"
                    DesktopDebugLog.error("EventReg", projectStatusText)
                }
                isImportingEventRegWebsite = false
            }
        }

        fun importEventRegCompetitorCsvs(url: String) {
            if (isImportingEventRegCompetitorCsvs) {
                return
            }
            val trimmedUrl = url.trim()
            isImportingEventRegCompetitorCsvs = true
            projectStatusText = "Importing EventReg website..."
            DesktopEventRegImportPreferences.rememberRegistrationUrl(trimmedUrl)
            appCoroutineScope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        DesktopEventRegImporter.importCompetitorCsvsFromWebsite(
                            url = trimmedUrl,
                            outputDirectory = DesktopEventFileLocations.preparePreferredEventFileDirectory(),
                            startDateTimeIso = DesktopDateTimeText.isoText(DesktopDateTimeText.defaultStartDateTime())
                        )
                    }
                }
                result.onSuccess { importResult ->
                    val totalCompetitors = importResult.generatedFiles.sumOf { it.competitorCount }
                    projectStatusText =
                        "Generated ${importResult.generatedFiles.size} competitor CSV files with $totalCompetitors competitor entries in ${importResult.outputDirectory}."
                    isEventRegCompetitorCsvImportDialogVisible = false
                    DesktopDebugLog.info(
                        "EventReg",
                        "Generated ${importResult.generatedFiles.size} competitor CSV files from ${importResult.sourceUrl}"
                    )
                }.onFailure { error ->
                    projectStatusText = "EventReg competitor CSV import failed: ${error.message ?: error::class.simpleName}"
                    DesktopDebugLog.error("EventReg", projectStatusText)
                }
                isImportingEventRegCompetitorCsvs = false
            }
        }

        fun saveAsCurrentProject(suggestedFileName: String? = null): Boolean {
            val path = DesktopFileDialogs.chooseSaveProject(
                raceName = projectSession.currentProject?.raceData?.race?.name,
                suggestedFileName = suggestedFileName
            )
                ?: return false
            return runCatching {
                projectSession.saveAs(path)
                projectFile = projectSession.currentProject
                hasUnsavedChanges = projectSession.hasUnsavedChanges
                hasUnsavedEventDefinitionChanges = false
                isEventDefinitionSaveDialogVisible = false
                DesktopLastEventFilePreferences.rememberEventFile(path)
                projectStatusText = "Saved ${path.fileName}"
                DesktopDebugLog.info("EventFile", "Saved As ${path.fileName}")
            }.onFailure { error ->
                projectStatusText = "Save failed: ${error.message ?: error::class.simpleName}"
                DesktopDebugLog.error("EventFile", "Save As failed: ${error.message ?: error::class.simpleName}")
            }.isSuccess
        }

        fun exportEventFileCopy() {
            DesktopFileDialogs.chooseExportProject()?.let { path ->
                runCatching {
                    projectSession.exportCopy(path)
                    syncProjectState()
                    projectStatusText = "Exported ${path.fileName}"
                    DesktopDebugLog.info("EventFile", "Exported copy ${path.fileName}")
                }.onFailure { error ->
                    projectStatusText = "Export failed: ${error.message ?: error::class.simpleName}"
                    DesktopDebugLog.error("EventFile", "Export copy failed: ${error.message ?: error::class.simpleName}")
                }
            }
        }

        fun requestNewEventFile() {
            pendingDirtyProjectAction = DesktopDirtyProjectActions.pendingActionOrNull(
                hasProtectedUnsavedChanges(),
                PendingDirtyProjectAction.NewProject
            )
            if (pendingDirtyProjectAction == null) {
                createNewProject()
            }
        }

        fun requestCloseEventFile() {
            pendingDirtyProjectAction = DesktopDirtyProjectActions.pendingActionOrNull(
                hasProtectedUnsavedChanges(),
                PendingDirtyProjectAction.CloseProject
            )
            if (pendingDirtyProjectAction == null) {
                closeProject(discardUnsavedChanges = isDefaultUnsavedNewEventFileDraft())
            }
        }

        fun isNavActionEnabled(action: DesktopNavAction): Boolean =
            when (action) {
                DesktopNavAction.NewEventFile,
                DesktopNavAction.OpenEventFile,
                DesktopNavAction.ImportAndroidRaceBackup,
                DesktopNavAction.ImportEventRegWebsite -> true
                DesktopNavAction.ImportEventRegCompetitorsCsv -> projectFile != null
                DesktopNavAction.ShowDebugLogHelp,
                DesktopNavAction.ShowAbout -> true
                DesktopNavAction.SaveEventFile -> canSaveEventFile()
                DesktopNavAction.StopContinuousSiReadout -> isContinuousSiReadoutActive
                DesktopNavAction.StartLocalResultDisplay -> projectFile != null && localResultServerUrl == null
                DesktopNavAction.StopLocalResultDisplay -> localResultServerUrl != null
                DesktopNavAction.SendRobis -> projectFile != null && !isSendingLiveResults
                DesktopNavAction.DownloadSiCard -> projectFile != null && !isDownloadingSiReadout && !isContinuousSiReadoutActive
                DesktopNavAction.StartContinuousSiReadout ->
                    projectFile != null && !isDownloadingSiReadout && !isContinuousSiReadoutActive
                else -> projectFile != null
            }

        fun disabledNavActionReason(action: DesktopNavAction): String? {
            if (isNavActionEnabled(action)) {
                return null
            }
            return when (action) {
                DesktopNavAction.SaveEventFile ->
                    if (projectFile == null) {
                        "Open or create an Event File before saving."
                    } else {
                        "There are no Event File changes to save."
                    }
                DesktopNavAction.CloseEventFile,
                DesktopNavAction.ImportEventRegCompetitorsCsv,
                DesktopNavAction.ImportCategoriesCsv,
                DesktopNavAction.ImportCourseKmlKmz,
                DesktopNavAction.ImportControlsCsv,
                DesktopNavAction.ImportCompetitorsCsv,
                DesktopNavAction.ImportStartsCsv,
                DesktopNavAction.DeleteAllCategoryAssignedControls,
                DesktopNavAction.DeleteAllCategories,
                DesktopNavAction.DeleteAllCompetitors,
                DesktopNavAction.ExportEventFileCopy,
                DesktopNavAction.ExportCategoriesCsv,
                DesktopNavAction.ExportControlsCsv,
                DesktopNavAction.ExportCourseKmlKmz,
                DesktopNavAction.ExportCompetitorsCsv,
                DesktopNavAction.ExportStartsCsv,
                DesktopNavAction.ExportStartsByCategoryCsv,
                DesktopNavAction.ExportStartsByMinuteCsv,
                DesktopNavAction.ExportRobisStartListCsv,
                DesktopNavAction.ExportReadoutsCsv,
                DesktopNavAction.ExportResultsCsv,
                DesktopNavAction.ExportArdfEventResultsCsv,
                DesktopNavAction.ExportResultsText,
                DesktopNavAction.ExportResultsHtml,
                DesktopNavAction.ExportArdfJson,
                DesktopNavAction.ExportAndroidRaceBackupJson,
                DesktopNavAction.ExportLiveResultsJson,
                DesktopNavAction.ExportFinalResultsJson,
                DesktopNavAction.ExportIofStartListXml,
                DesktopNavAction.ExportIofResultListXml ->
                    "Open or create an Event File first."
                DesktopNavAction.DownloadSiCard ->
                    when {
                        projectFile == null -> "Open or create an Event File before downloading SI cards."
                        isDownloadingSiReadout -> "An SI card download is already in progress."
                        isContinuousSiReadoutActive -> "Stop continuous SI readout before downloading one card."
                        else -> "SI card download is not available right now."
                    }
                DesktopNavAction.StartContinuousSiReadout ->
                    when {
                        projectFile == null -> "Open or create an Event File before starting continuous SI readout."
                        isDownloadingSiReadout -> "Wait for the current SI card download to finish."
                        isContinuousSiReadoutActive -> "Continuous SI readout is already running."
                        else -> "Continuous SI readout is not available right now."
                    }
                DesktopNavAction.StopContinuousSiReadout ->
                    "Continuous SI readout is not running."
                DesktopNavAction.StartLocalResultDisplay ->
                    if (projectFile == null) {
                        "Open or create an Event File before starting the local result display."
                    } else {
                        "The local result display is already running."
                    }
                DesktopNavAction.StopLocalResultDisplay ->
                    "The local result display is not running."
                DesktopNavAction.SendRobis ->
                    if (projectFile == null) {
                        "Open or create an Event File before sending ROBIS results."
                    } else {
                        "ROBIS results are already being sent."
                    }
                DesktopNavAction.NewEventFile,
                DesktopNavAction.OpenEventFile,
                DesktopNavAction.ImportAndroidRaceBackup,
                DesktopNavAction.ImportEventRegWebsite,
                DesktopNavAction.ShowDebugLogHelp,
                DesktopNavAction.ShowAbout -> null
            }
        }

        fun handleNavAction(action: DesktopNavAction) {
            when (action) {
                DesktopNavAction.NewEventFile -> requestNewEventFile()
                DesktopNavAction.OpenEventFile -> chooseOpenEventFile()
                DesktopNavAction.ImportAndroidRaceBackup -> chooseImportAndroidRaceBackupJson()
                DesktopNavAction.ImportEventRegWebsite -> showEventRegImportDialog()
                DesktopNavAction.ImportEventRegCompetitorsCsv -> showEventRegCompetitorCsvImportDialog()
                DesktopNavAction.SaveEventFile -> saveCurrentProject()
                DesktopNavAction.CloseEventFile -> requestCloseEventFile()
                DesktopNavAction.ImportCategoriesCsv -> importCategoriesCsv()
                DesktopNavAction.ImportControlsCsv -> importControlsCsv()
                DesktopNavAction.ImportCourseKmlKmz -> chooseImportCourseKmlKmz()
                DesktopNavAction.DeleteAllCategoryAssignedControls ->
                    pendingBulkCategoryAction = BulkCategoryAction.DeleteAllAssignedControls
                DesktopNavAction.DeleteAllCategories ->
                    pendingBulkCategoryAction = BulkCategoryAction.DeleteAllCategories
                DesktopNavAction.DeleteAllCompetitors ->
                    isDeleteAllCompetitorsDialogVisible = true
                DesktopNavAction.ImportCompetitorsCsv -> importCompetitorsCsv()
                DesktopNavAction.ImportStartsCsv -> importCompetitorStartsCsv()
                DesktopNavAction.ExportEventFileCopy -> exportEventFileCopy()
                DesktopNavAction.ExportCategoriesCsv -> exportCategoriesCsv()
                DesktopNavAction.ExportControlsCsv ->
                    exportCsv("Export Controls CSV", "controls", DesktopProjectFiles::exportControlsCsv)
                DesktopNavAction.ExportCourseKmlKmz -> chooseExportCourseKmlKmz()
                DesktopNavAction.ExportCompetitorsCsv ->
                    exportCsv("Export Competitors CSV", "competitors", DesktopProjectFiles::exportCompetitorsCsv)
                DesktopNavAction.ExportStartsCsv ->
                    exportCsv("Export Starts CSV", "starts", DesktopProjectFiles::exportCompetitorStartsCsv)
                DesktopNavAction.ExportStartsByCategoryCsv ->
                    exportCsv(
                        "Export Starts by Category CSV",
                        "starts by category",
                        DesktopProjectFiles::exportCompetitorStartsByCategoryCsv
                    )
                DesktopNavAction.ExportStartsByMinuteCsv ->
                    exportCsv(
                        "Export Starts by Minute CSV",
                        "starts by minute",
                        DesktopProjectFiles::exportCompetitorStartsByMinuteCsv
                    )
                DesktopNavAction.ExportRobisStartListCsv ->
                    exportCsv("Export ROBIS Start List CSV", "robis start list", DesktopProjectFiles::exportRobisStartListCsv)
                DesktopNavAction.ExportReadoutsCsv ->
                    exportCsv("Export Readouts CSV", "readouts", DesktopProjectFiles::exportReadoutsCsv)
                DesktopNavAction.ExportResultsCsv ->
                    exportCsv("Export Results CSV", "results", DesktopProjectFiles::exportResultsCsv)
                DesktopNavAction.ExportArdfEventResultsCsv ->
                    exportCsv("Export ARDFEvent Results CSV", "ardfevent results", DesktopProjectFiles::exportArdfEventResultsCsv)
                DesktopNavAction.ExportResultsText -> exportResultsText()
                DesktopNavAction.ExportResultsHtml -> exportResultsHtml()
                DesktopNavAction.ExportArdfJson -> exportArdfJson()
                DesktopNavAction.ExportAndroidRaceBackupJson -> exportAndroidRaceBackupJson()
                DesktopNavAction.ExportLiveResultsJson -> exportLiveResultsJson()
                DesktopNavAction.ExportFinalResultsJson -> exportFinalResultsJson()
                DesktopNavAction.ExportIofStartListXml -> exportIofStartListXml()
                DesktopNavAction.ExportIofResultListXml -> exportIofResultListXml()
                DesktopNavAction.DownloadSiCard -> downloadSportIdentReadout()
                DesktopNavAction.StartContinuousSiReadout -> startContinuousSportIdentReadout()
                DesktopNavAction.StopContinuousSiReadout -> stopContinuousSportIdentReadout()
                DesktopNavAction.StartLocalResultDisplay -> {
                    val url = localResultServer.start()
                    localResultServerUrl = url
                    projectStatusText = "Local result display running at $url"
                }
                DesktopNavAction.StopLocalResultDisplay -> {
                    localResultServer.stop()
                    localResultServerUrl = null
                    projectStatusText = "Local result display stopped."
                }
                DesktopNavAction.SendRobis -> sendRobisLiveResults()
                DesktopNavAction.ShowDebugLogHelp -> {
                    val logDirectory = DesktopDebugLog.logDirectory()
                    DesktopDebugLog.info("App", "User requested desktop log location")
                    projectStatusText = "Desktop logs: $logDirectory"
                }
                DesktopNavAction.ShowAbout -> {
                    isAboutDialogVisible = true
                }
            }
        }

        MenuBar {
            Menu("File") {
                Item("New Event File", onClick = ::requestNewEventFile)
                Item("Load Event File...", onClick = ::chooseOpenEventFile)
                Item("Import EventReg Website...", onClick = ::showEventRegImportDialog)
                Item(
                    "Save Event",
                    enabled = canSaveEventFile(),
                    onClick = {
                        saveCurrentProject()
                    }
                )
                Item("Close Event File", enabled = projectFile != null, onClick = ::requestCloseEventFile)
            }
        }

        if (!isEventDefinitionSaveDialogVisible) {
            pendingDirtyProjectAction?.let {
                UnsavedChangesDialog(
                    onSave = { continuePendingDirtyAction(saveFirst = true) },
                    onDiscard = { continuePendingDirtyAction(saveFirst = false) },
                    onCancel = { pendingDirtyProjectAction = null }
                )
            }
        }
        if (isEventDefinitionSaveDialogVisible) {
            EventDefinitionSaveDialog(
                currentPath = projectSession.currentPath,
                onSaveAsNew = {
                    if (saveAsCurrentProject() && pendingDirtyProjectAction != null) {
                        continuePendingDirtyAction(saveFirst = false)
                    }
                },
                onOverwrite = {
                    if (saveCurrentProject(overwriteEventDefinitionChanges = true) && pendingDirtyProjectAction != null) {
                        continuePendingDirtyAction(saveFirst = false)
                    }
                },
                onCancel = { isEventDefinitionSaveDialogVisible = false }
            )
        }
        pendingSiModeWarning?.let { warning ->
            SiStationModeWarningDialog(
                title = warning.warningTitle ?: "SI station mode warning",
                message = warning.warningMessage ?: warning.statusText,
                onDismiss = { pendingSiModeWarning = null }
            )
        }
        pendingAssignedControlsWarning?.let { pendingWarning ->
            AssignedControlsWarningDialog(
                warning = pendingWarning.warning,
                canRestore = pendingWarning.previousControlPointsText != null,
                onKeep = { pendingAssignedControlsWarning = null },
                onRestore = {
                    val warning = pendingWarning.warning
                    val previousText = pendingWarning.previousControlPointsText
                    pendingAssignedControlsWarning = null
                    if (previousText != null) {
                        runCatching {
                            projectFile = projectSession.updateCurrentProject { currentProject ->
                                EventProjectEditor.updateCategoryControlPoints(
                                    currentProject,
                                    warning.categoryId,
                                    previousText
                                ) {
                                    UUID.randomUUID().toString()
                                }
                            }
                            hasUnsavedChanges = projectSession.hasUnsavedChanges
                            projectStatusText = "Assigned controls restored. Unsaved changes."
                        }.onFailure { error ->
                            projectStatusText = "Could not restore assigned controls: ${categoryControlPointErrorText(error)}"
                        }
                    }
                }
            )
        }
        if (isNationalStartListDefaultsDialogVisible) {
            NationalStartListDefaultsDialog(
                onReset = {
                    isNationalStartListDefaultsDialogVisible = false
                    applyNationalStartListDefaults()
                },
                onKeepCurrent = {
                    isNationalStartListDefaultsDialogVisible = false
                    projectStatusText = "National Start List defaults skipped."
                }
            )
        }
        if (isAboutDialogVisible) {
            AboutRadioOracleDialog(onDismiss = { isAboutDialogVisible = false })
        }
        if (isEventRegImportDialogVisible) {
            EventRegImportDialog(
                url = eventRegImportUrl,
                isImporting = isImportingEventRegWebsite,
                onUrlChange = { eventRegImportUrl = it },
                onImport = { importEventRegWebsite(eventRegImportUrl) },
                onCancel = {
                    if (!isImportingEventRegWebsite) {
                        isEventRegImportDialogVisible = false
                    }
                }
            )
        }
        if (isEventRegCompetitorCsvImportDialogVisible) {
            EventRegImportDialog(
                title = "Import EventReg Competitors",
                idleDescription = "Creates one competitor CSV file for each competition class column with registered competitors.",
                importingDescription = "Downloading registration table and generating competitor CSV files...",
                url = eventRegImportUrl,
                isImporting = isImportingEventRegCompetitorCsvs,
                onUrlChange = { eventRegImportUrl = it },
                onImport = { importEventRegCompetitorCsvs(eventRegImportUrl) },
                onCancel = {
                    if (!isImportingEventRegCompetitorCsvs) {
                        isEventRegCompetitorCsvImportDialogVisible = false
                    }
                }
            )
        }
        pendingCourseKmlKmzUnlockAction?.let { unlockAction ->
            CourseKmlKmzUnlockDialog(
                title = when (unlockAction) {
                    CourseKmlKmzUnlockAction.Import -> "Unlock course order"
                    CourseKmlKmzUnlockAction.Export -> "Export protected controls/routes"
                },
                description = when (unlockAction) {
                    CourseKmlKmzUnlockAction.Import ->
                        "KML/KMZ controls/route data includes coordinates and route details that require the Event Password."
                    CourseKmlKmzUnlockAction.Export ->
                        "Controls/route KML/KMZ export includes sensitive coordinates and routes. The exported file will be placed inside a password-locked ZIP."
                },
                confirmLabel = when (unlockAction) {
                    CourseKmlKmzUnlockAction.Import -> "Unlock and Import"
                    CourseKmlKmzUnlockAction.Export -> "Export"
                },
                onUnlock = { password ->
                    if (unlockProtectedCourseOrder(password)) {
                        val unlockedPassword = password.trim()
                        pendingCourseKmlKmzUnlockAction = null
                        when (unlockAction) {
                            CourseKmlKmzUnlockAction.Import -> chooseImportCourseKmlKmzUnlocked(unlockedPassword)
                            CourseKmlKmzUnlockAction.Export -> chooseExportCourseKmlKmzUnlocked(unlockedPassword)
                        }
                        true
                    } else {
                        false
                    }
                },
                onCancel = { pendingCourseKmlKmzUnlockAction = null }
            )
        }
        pendingProtectedControlDeleteId?.let { controlId ->
            val controlLabel = projectSession.currentProject?.raceData?.controls
                ?.firstOrNull { it.id == controlId }
                ?.publicDisplayLabel()
                ?: "this control"
            CourseKmlKmzUnlockDialog(
                title = "Unlock course data to delete control",
                description = "The Event File contains password-protected imported course or route data. Before deleting $controlLabel, Radio-Oracle needs the Event Password so it can check whether that control is referenced by stored course routes.",
                confirmLabel = "Unlock and Delete",
                onUnlock = { password ->
                    if (unlockProtectedCourseOrder(password)) {
                        pendingProtectedControlDeleteId = null
                        deleteControlAfterProtectedRouteCheck(controlId, promptIfLocked = false)
                        true
                    } else {
                        false
                    }
                },
                onCancel = { pendingProtectedControlDeleteId = null }
            )
        }
        pendingBulkCategoryAction?.let { action ->
            val currentProject = projectSession.currentProject
            BulkCategoryActionDialog(
                action = action,
                categoryCount = currentProject?.raceData?.categories?.size ?: 0,
                hasProtectedCategoryData = currentProject?.hasProtectedCategoryData() == true,
                onConfirm = { password ->
                    if (applyBulkCategoryAction(action, password)) {
                        pendingBulkCategoryAction = null
                        true
                    } else {
                        false
                    }
                },
                onCancel = { pendingBulkCategoryAction = null }
            )
        }
        if (isDeleteAllCompetitorsDialogVisible) {
            val currentProject = projectSession.currentProject
            DeleteAllCompetitorsDialog(
                competitorCount = currentProject?.raceData?.competitorData?.size ?: 0,
                matchedReadoutCount = currentProject?.raceData?.competitorData?.count { it.readoutData != null } ?: 0,
                onConfirm = {
                    if (deleteAllCompetitors()) {
                        isDeleteAllCompetitorsDialogVisible = false
                    }
                },
                onCancel = { isDeleteAllCompetitorsDialogVisible = false }
            )
        }
        pendingCourseKmlKmzCategoryMapping?.let { mapping ->
            CourseKmlKmzCategoryMappingDialog(
                mapping = mapping,
                onApply = { categoryId ->
                    pendingCourseKmlKmzCategoryMapping = null
                    startCourseKmlKmzImport(mapping.path, mapping.password, categoryId)
                },
                onCancel = {
                    pendingCourseKmlKmzCategoryMapping = null
                    projectStatusText = "Controls/route KML/KMZ import canceled. No changes applied."
                }
            )
        }
        pendingCourseKmlKmzImportReview?.let { review ->
            CourseKmlKmzImportReviewDialog(
                review = review,
                onKeep = { fetchElevations, applyCategoryAssignments, createMissingCategories ->
                    applyCourseKmlKmzImport(
                        review = review,
                        fetchElevations = fetchElevations,
                        applyCategoryAssignments = applyCategoryAssignments,
                        createMissingCategories = createMissingCategories
                    )
                },
                onCancel = {
                    pendingCourseKmlKmzImportReview = null
                    projectStatusText = "Controls/route KML/KMZ import canceled. No changes applied."
                }
            )
        }
        if (isImportingCourseKmlKmz) {
            IndeterminateProgressDialog(
                title = "Importing controls/route KML/KMZ",
                message = "Reading and reviewing the selected file. This can take a while for large KML/KMZ files."
            )
        }
        courseKmlKmzElevationProgress?.let { progress ->
            CourseKmlKmzElevationProgressDialog(
                progress = progress,
                onCancel = {
                    courseKmlKmzElevationProgress = progress.copy(cancelRequested = true)
                    courseKmlKmzElevationJob?.cancel()
                }
            )
        }
        venueElevationCacheProgress?.let { progress ->
            VenueElevationCacheProgressDialog(
                progress = progress,
                onCancel = {
                    venueElevationCacheProgress = progress.copy(cancelRequested = true)
                    venueElevationCacheJob?.cancel()
                }
            )
        }
        pendingCategoriesCsvImportReview?.let { review ->
            CategoriesCsvImportReviewDialog(
                review = review,
                onImport = { applyCategoriesCsvImport(review) },
                onCancel = {
                    pendingCategoriesCsvImportReview = null
                    projectStatusText = "Categories CSV import canceled. No changes applied."
                }
            )
        }
        pendingControlsCsvImportReview?.let { review ->
            ControlsCsvImportReviewDialog(
                review = review,
                onImport = { syncMissingControls -> applyControlsCsvImport(review, syncMissingControls) },
                onCancel = {
                    pendingControlsCsvImportReview = null
                    projectStatusText = "Controls CSV import canceled. No changes applied."
                }
            )
        }
        pendingCompetitorsCsvImportReview?.let { review ->
            CompetitorCsvImportOptionsDialog(
                fileName = review.path.fileName.toString(),
                missingCategoryNames = review.missingCategoryNames,
                synchronizeToCsv = syncCompetitorsCsvImport,
                onSynchronizeToCsvChange = { syncCompetitorsCsvImport = it },
                onImport = { createMissingCategories ->
                    importCompetitorsCsv(review.path, syncCompetitorsCsvImport, createMissingCategories)
                },
                onCancel = { pendingCompetitorsCsvImportReview = null }
            )
        }
        pendingReadoutEdit?.let { draft ->
            ReadoutEditDialog(
                draft = draft,
                categories = projectFile?.let { EventCategoryDetails.from(it.raceData) } ?: emptyList(),
                controls = projectFile?.raceData?.controls ?: emptyList(),
                onSave = { updatedDraft ->
                    runCatching {
                        projectFile = projectSession.updateCurrentProject { currentProject ->
                            EventProjectEditor.updateReadoutEdit(
                                projectFile = currentProject,
                                resultId = updatedDraft.resultId,
                                startSeconds = updatedDraft.startSeconds,
                                finishSeconds = updatedDraft.finishSeconds,
                                controlPunchesText = updatedDraft.controlPunchesText,
                                resultStatus = updatedDraft.resultStatus,
                                categoryId = updatedDraft.categoryId,
                                updateCompetitorCategory = updatedDraft.updateCompetitorCategory,
                                punchIdFactory = { index, type -> "edited-punch-${UUID.randomUUID()}-$index-${type.name}" }
                            )
                        }
                        hasUnsavedChanges = projectSession.hasUnsavedChanges
                        pendingReadoutEdit = null
                        projectStatusText = "Result edit applied."
                        DesktopDebugLog.info(
                            "Results",
                            "Edited result ${updatedDraft.resultId}; category=${updatedDraft.categoryId ?: "none"}; " +
                                "updateCompetitorCategory=${updatedDraft.updateCompetitorCategory}"
                        )
                    }.onFailure { error ->
                        projectStatusText = "Result edit failed: ${error.message ?: error::class.simpleName}"
                        DesktopDebugLog.error("Results", projectStatusText)
                    }
                },
                onCancel = { pendingReadoutEdit = null }
            )
        }

        RadioOManagerDesktopApp(
            projectFile = projectFile,
            eventFilePath = projectSession.currentPath,
            projectStatusText = projectStatusText,
            hasUnsavedChanges = hasUnsavedChanges,
            siReaderState = siReaderState,
            isDownloadingSiReadout = isDownloadingSiReadout,
            isContinuousSiReadoutActive = isContinuousSiReadoutActive,
            isReadingCompetitorSiCard = isReadingCompetitorSiCard,
            siDownloadStatusText = siDownloadStatusText,
            isSendingLiveResults = isSendingLiveResults,
            isBackgroundLiveResultSendingEnabled = isBackgroundLiveResultSendingEnabled,
            readoutDuplicatePolicy = readoutDuplicatePolicy,
            isReadoutAlertSoundEnabled = isReadoutAlertSoundEnabled,
            areAliasesEnabled = areAliasesEnabled,
            localResultServerUrl = localResultServerUrl,
            printerDiagnostics = printerDiagnostics,
            raceClockTick = raceClockTick,
            isNavActionEnabled = ::isNavActionEnabled,
            disabledNavActionReason = ::disabledNavActionReason,
            onInsertTestControls = ::insertTestControls,
            onInsertTestCategories = ::insertTestCategories,
            onInsertTestCompetitors = ::insertTestCompetitors,
            onInsertTestSportIdentDownloads = ::insertTestSportIdentDownloads,
            onRestoreRecentImportCheckpoint = ::restoreRecentImportCheckpoint,
            onRecalculateResults = ::recalculateResults,
            onNavAction = ::handleNavAction,
            isProtectedCourseOrderUnlocked = protectedCoursePassword != null,
            protectedIdealOrderByCategoryId = protectedIdealOrderByCategoryId,
            protectedCourseInfoByCategoryId = protectedCourseInfoByCategoryId,
            recentImportReport = recentImportReport,
            recentImportCheckpoint = recentImportCheckpoint,
            recentActivityLog = recentActivityLog,
            onRetrieveMissingCourseElevations = ::startCourseAnalysisElevationFetch,
            onDownloadVenueElevationCache = ::startVenueElevationCacheDownload,
            onOpenVenueElevationCacheFolder = ::openVenueElevationCacheFolder,
            elevationCacheRefreshToken = venueElevationCacheRefreshToken,
            onUnlockProtectedCourseOrder = ::unlockProtectedCourseOrder,
            onUpdateProtectedIdealOrder = ::updateProtectedIdealOrder,
            onUseCalculatedCourseAnalysisRoute = ::useCalculatedCourseAnalysisRoute,
            onApplyCourseAnalysisFoxRenumberingOnly = ::applyCourseAnalysisFoxRenumberingOnly,
            onReadCompetitorSiCardForAddRow = ::readCompetitorSiCardForAddRow,
            onUpdateProtectedControlLocation = ::updateProtectedControlLocation,
            onUpdateProtectedCoursePassword = ::updateProtectedCoursePassword,
            onLockProtectedCourseOrder = ::lockProtectedCourseOrder,
            onUpdateEventFileName = { fileName -> saveAsCurrentProject(fileName) },
            onRenameRace = { name ->
                runCatching {
                    val previousProject = projectSession.currentProject
                    projectFile = projectSession.updateCurrentProject { currentProject ->
                        EventProjectEditor.renameRace(currentProject, name)
                    }
                    projectFile?.let { markEventDefinitionChangeIfLoaded(previousProject, it) }
                    hasUnsavedChanges = projectSession.hasUnsavedChanges
                    projectStatusText = "Unsaved changes."
                }.onFailure { error ->
                    projectStatusText = "Edit failed: ${error.message ?: error::class.simpleName}"
                }
            },
            onUpdateRaceStartDateTime = { startDateTimeIso ->
                runCatching {
                    val previousProject = projectSession.currentProject
                    projectFile = projectSession.updateCurrentProject { currentProject ->
                        EventProjectEditor.updateRaceStartDateTime(currentProject, startDateTimeIso)
                    }
                    projectFile?.let { markEventDefinitionChangeIfLoaded(previousProject, it) }
                    hasUnsavedChanges = projectSession.hasUnsavedChanges
                    projectStatusText = "Unsaved changes."
                }.onFailure { error ->
                    projectStatusText = "Edit failed: ${error.message ?: error::class.simpleName}"
                }
            },
            onUpdateRaceSettings = { raceType, raceLevel, raceBand, timeLimitMinutes ->
                runCatching {
                    val currentProject = projectSession.currentProject
                    val shouldPromptForNationalDefaults = currentProject != null &&
                        currentProject.raceData.race.raceLevel != RaceLevel.NATIONAL &&
                        raceLevel == RaceLevel.NATIONAL &&
                        shouldOfferNationalStartListDefaults(currentProject)
                    val previousProject = projectSession.currentProject
                    projectFile = projectSession.updateCurrentProject { currentProject ->
                        EventProjectEditor.updateRaceSettings(
                            currentProject,
                            raceType,
                            raceLevel,
                            raceBand,
                            timeLimitMinutes
                        )
                    }
                    projectFile?.let { markEventDefinitionChangeIfLoaded(previousProject, it) }
                    hasUnsavedChanges = projectSession.hasUnsavedChanges
                    projectStatusText = "Unsaved changes."
                    if (shouldPromptForNationalDefaults) {
                        isNationalStartListDefaultsDialogVisible = true
                    }
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
            onUpdateCategoryControlPoints = { categoryId, controlPointsText, shouldCheckRequiredControls ->
                runCatching {
                    var lockedCourseWarning = ""
                    val previousControlPointsText = projectSession.currentProject
                        ?.raceData
                        ?.categories
                        ?.firstOrNull { it.category.id == categoryId }
                        ?.restorableControlPointsText()
                        ?.takeIf { it.isNotBlank() }
                    val updatedProject = projectSession.updateCurrentProject { currentProject ->
                        lockedCourseWarning = currentProject.lockedCategoryCourseWarning(categoryId, protectedCoursePassword != null)
                        EventProjectEditor.updateCategoryControlPoints(
                            currentProject,
                            categoryId,
                            controlPointsText
                        ) {
                            UUID.randomUUID().toString()
                        }
                    }
                    projectFile = updatedProject
                    hasUnsavedChanges = projectSession.hasUnsavedChanges
                    recordActivity("Updated category assigned controls.")
                    projectStatusText = "Unsaved changes.$lockedCourseWarning${updatedProject.resultImpactWarning("Category course data changed")}"
                    if (shouldCheckRequiredControls) {
                        val warning = EventAssignedControlWarnings.forCategory(updatedProject.raceData, categoryId)
                        scheduleAssignedControlsWarning(
                            warning = warning,
                            previousControlPointsText = previousControlPointsText
                                ?.takeIf { warning?.isClearingAllAssignments == true && controlPointsText.isBlank() }
                        )
                    }
                }.onFailure { error ->
                    clearAssignedControlsWarning()
                    projectStatusText = "Edit failed: ${categoryControlPointErrorText(error)}"
                }
            },
            onUpdateCategoryPhysicalStats = { categoryId, lengthMeters, climbMeters ->
                runCatching {
                    var lockedCourseWarning = ""
                    projectFile = projectSession.updateCurrentProject { currentProject ->
                        lockedCourseWarning = currentProject.lockedCategoryCourseWarning(categoryId, protectedCoursePassword != null)
                        EventProjectEditor.updateCategoryPhysicalStats(
                            currentProject,
                            categoryId,
                            lengthMeters,
                            climbMeters
                        )
                    }
                    hasUnsavedChanges = projectSession.hasUnsavedChanges
                    recordActivity("Updated category length/climb.")
                    projectStatusText = "Unsaved changes.$lockedCourseWarning"
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
                    projectStatusText = "Created category without course data. Add assigned controls or import KML/KMZ course data before Race Ops."
                }
                result.onFailure { error ->
                    projectStatusText = "Edit failed: ${error.message ?: error::class.simpleName}"
                }
                result.isSuccess
            },
            onRemoveCategory = { categoryId, deleteCompetitors ->
                runCatching {
                    val currentProject = requireNotNull(projectSession.currentProject)
                    require(!currentProject.categoryHasLockedProtectedCourseData(categoryId, protectedCoursePassword != null)) {
                        "Category has locked protected course data. Unlock course data before deleting it."
                    }
                    projectFile = projectSession.updateCurrentProject { currentProject ->
                        EventProjectEditor.removeCategory(currentProject, categoryId, deleteCompetitors)
                    }
                    hasUnsavedChanges = projectSession.hasUnsavedChanges
                    recordActivity("Removed category.")
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
            onUpdateCompetitorClubIdentity = { competitorId, club, bibNumber, callSign ->
                runCatching {
                    projectFile = projectSession.updateCurrentProject { currentProject ->
                        EventProjectEditor.updateCompetitorClubBibCallSign(
                            projectFile = currentProject,
                            competitorId = competitorId,
                            club = club,
                            bibNumber = bibNumber,
                            callSign = callSign,
                            legacyIndex = bibNumber
                        )
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
            onUpdateCompetitorStartTime = { competitorId, startTime ->
                runCatching {
                    projectFile = projectSession.updateCurrentProject { currentProject ->
                        EventProjectEditor.updateCompetitorStartTime(currentProject, competitorId, startTime)
                    }
                    hasUnsavedChanges = projectSession.hasUnsavedChanges
                    projectStatusText = "Unsaved changes."
                }.onFailure { error ->
                    projectStatusText = "Edit failed: ${error.message ?: error::class.simpleName}"
                }
            },
            onUpdateStartDrawSettings = { interval, options ->
                runCatching {
                    val updatedProject = projectSession.updateCurrentProject { currentProject ->
                        EventProjectEditor.updateStartDrawSettings(currentProject, interval, options)
                    }
                    projectFile = updatedProject
                    hasUnsavedChanges = projectSession.hasUnsavedChanges
                    projectStatusText = "Unsaved changes."
                }.onFailure { error ->
                    projectStatusText = "Start list settings failed: ${error.message ?: error::class.simpleName}"
                }
            },
            onDrawStartList = { interval, options ->
                runCatching {
                    val protectedOptions = options.copy(
                        idealFirstFoxByCategoryId = unlockedIdealFirstFoxByCategoryId()
                    )
                    val drawnProject = projectSession.updateCurrentProject { currentProject ->
                        EventProjectEditor.drawStartList(currentProject, interval, protectedOptions)
                    }
                    projectFile = drawnProject
                    hasUnsavedChanges = projectSession.hasUnsavedChanges
                    projectStatusText = startListDrawStatusText(EventStartListDetails.from(drawnProject.raceData))
                }.onFailure { error ->
                    projectStatusText = "Draw failed: ${error.message ?: error::class.simpleName}"
                }
            },
            onDrawBalancedStartList = { interval, options ->
                val paths = DesktopFileDialogs.chooseImportCsvFiles("Select Previous Starts CSV Files")
                if (paths.isNotEmpty()) runCatching {
                    val previousStartLists = paths.map { path ->
                        EventCsvImports.parseAndroidCompetitorStartRows(Files.readString(path)).also { result ->
                            require(result.invalidLines.isEmpty()) {
                                "${path.fileName} has ${result.invalidLines.size} invalid start rows."
                            }
                        }.rows
                    }
                    val protectedOptions = options.copy(
                        startGroupMode = StartDrawStartGroupMode.BALANCED_MULTI_DAY_THIRDS,
                        idealFirstFoxByCategoryId = unlockedIdealFirstFoxByCategoryId()
                    )
                    val drawnProject = projectSession.updateCurrentProject { currentProject ->
                        EventProjectEditor.drawStartListWithBalancedStartGroups(
                            currentProject,
                            interval,
                            protectedOptions,
                            previousStartLists
                        )
                    }
                    projectFile = drawnProject
                    hasUnsavedChanges = projectSession.hasUnsavedChanges
                    projectStatusText = "Balanced starts from ${paths.size} prior CSV file(s); " +
                        startListDrawStatusText(EventStartListDetails.from(drawnProject.raceData))
                }.onFailure { error ->
                    projectStatusText = "Balanced draw failed: ${error.message ?: error::class.simpleName}"
                }
            },
            onAddCompetitor = { firstName, lastName, club, bibNumber, callSign, birthYear, categoryId, startNumber, siNumber ->
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
                        val withCallSign = EventProjectEditor.updateCompetitorClubBibCallSign(
                            projectFile = added,
                            competitorId = competitorId,
                            club = club,
                            bibNumber = bibNumber,
                            callSign = callSign,
                            legacyIndex = bibNumber
                        )
                        val withBirthYear = EventProjectEditor.updateCompetitorBirthYear(
                            withCallSign,
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
                    DesktopDebugLog.info("Results", "Updated result $resultId status to ${resultStatus.name}")
                }.onFailure { error ->
                    projectStatusText = "Edit failed: ${error.message ?: error::class.simpleName}"
                    DesktopDebugLog.error("Results", projectStatusText)
                }
            },
            onEditReadout = { resultId ->
                val currentProject = projectSession.currentProject
                val draft = currentProject?.raceData?.readoutEditDraft(resultId)
                if (draft == null) {
                    projectStatusText = "Readout was not found: $resultId"
                } else {
                    pendingReadoutEdit = draft
                }
            },
            onAssignUnmatchedReadout = { resultId, competitorId ->
                runCatching {
                    projectFile = projectSession.updateCurrentProject { currentProject ->
                        EventProjectEditor.assignUnmatchedReadout(currentProject, resultId, competitorId)
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
                        "Open or create an Event File before previewing finish tickets."
                    }
                    FinishTicketRenderer.render(
                        currentProject.raceData,
                        resultId,
                        useAliases = areAliasesEnabled,
                        protectedCourseInfoByCategoryId = protectedCourseInfoByCategoryId
                            .takeIf { protectedCoursePassword != null }
                    )
                }.getOrElse { error ->
                    "Ticket preview failed: ${error.message ?: error::class.simpleName}"
                }
            },
            onPrintFinishTicket = { resultId ->
                val currentProject = projectSession.currentProject
                if (currentProject == null) {
                    projectStatusText = "Open or create an Event File before printing finish tickets."
                } else {
                    projectStatusText = "Printing finish ticket..."
                    appCoroutineScope.launch {
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                val markedUpTicketText = FinishTicketRenderer.render(
                                    currentProject.raceData,
                                    resultId,
                                    useAliases = areAliasesEnabled,
                                    protectedCourseInfoByCategoryId = protectedCourseInfoByCategoryId
                                        .takeIf { protectedCoursePassword != null }
                                )
                                val printerName = DesktopTicketPrinterSelector.selectPrinterName(ticketPrinter.listPrinters())
                                ticketPrinter.printFinishTicket(markedUpTicketText, printerName)
                            }
                        }
                        projectStatusText = result.fold(
                            onSuccess = { it.summary() },
                            onFailure = { error ->
                                "Ticket print failed: ${error.message ?: error::class.simpleName}"
                            }
                        )
                    }
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
            onUpdateControl = { controlId, label, siCode, type, scored, publicLabel, notes ->
                runCatching {
                    var affectedAssignedCategories = 0
                    var affectedProtectedCourses = 0
                    var identityChanged = false
                    var lockedCourseWarning = ""
                    projectFile = projectSession.updateCurrentProject { currentProject ->
                        val existingControl = currentProject.raceData.controls.firstOrNull { it.id == controlId }
                        identityChanged = existingControl?.let { control ->
                            control.siCode.toString() != siCode.trim() ||
                                control.type != type ||
                                control.publicLabel.orEmpty() != publicLabel.trim() ||
                                control.label != label.trim()
                        } == true
                        if (identityChanged) {
                            val controlIds = setOf(controlId)
                            affectedAssignedCategories = DesktopImportPreviews.assignedCategoryUseCount(currentProject, controlIds)
                            affectedProtectedCourses = DesktopImportPreviews.protectedCourseUseCount(
                                protectedCourseInfoByCategoryId,
                                controlIds
                            )
                            lockedCourseWarning = currentProject.lockedProtectedCourseWarning(protectedCoursePassword != null)
                        }
                        EventProjectEditor.updateControl(currentProject, controlId, label, siCode, type, scored, publicLabel, notes)
                    }
                    hasUnsavedChanges = projectSession.hasUnsavedChanges
                    projectStatusText = if (identityChanged) {
                        recordActivity("Updated control identity.")
                        val impactWarning = projectFile?.resultImpactWarning("Control identity changed") ?: ""
                        "Control identity updated. This control is used by $affectedAssignedCategories assigned categor${if (affectedAssignedCategories == 1) "y" else "ies"} and $affectedProtectedCourses stored course${if (affectedProtectedCourses == 1) "" else "s"}.$lockedCourseWarning$impactWarning"
                    } else {
                        recordActivity("Updated control details.")
                        "Unsaved changes."
                    }
                }.onFailure { error ->
                    projectStatusText = "Edit failed: ${error.message ?: error::class.simpleName}"
                }
            },
            onAddControl = { label, siCode, type, scored, publicLabel, notes ->
                val result = runCatching {
                    projectFile = projectSession.updateCurrentProject { currentProject ->
                        EventProjectEditor.addControl(
                            currentProject,
                            UUID.randomUUID().toString(),
                            label,
                            siCode,
                            type,
                            scored,
                            publicLabel,
                            notes
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
            onRemoveControl = { controlId ->
                deleteControlAfterProtectedRouteCheck(controlId)
            },
            onImportControlsRouteKmlKmz = ::chooseImportCourseKmlKmz,
            onSendRobisLiveResults = { sendRobisLiveResults() },
            onSetBackgroundLiveResultSendingEnabled = { enabled ->
                isBackgroundLiveResultSendingEnabled = enabled
                projectStatusText = if (enabled) {
                    "Background ROBIS sending enabled."
                } else {
                    "Background ROBIS sending disabled."
                }
            },
            onSetReadoutDuplicatePolicy = { policy ->
                readoutDuplicatePolicy = policy
                projectStatusText = "Duplicate SI card action set to ${policy.toDisplayLabel()}."
            },
            onSetReadoutAlertSoundEnabled = { enabled ->
                isReadoutAlertSoundEnabled = enabled
                projectStatusText = if (enabled) {
                    "Readout alert sounds enabled."
                } else {
                    "Readout alert sounds disabled."
                }
            },
            onStartLocalResultServer = {
                runCatching {
                    localResultServer.start()
                }.onSuccess { url ->
                    localResultServerUrl = url
                    projectStatusText = "Local result display running at $url"
                }.onFailure { error ->
                    projectStatusText = "Local result display failed: ${error.message ?: error::class.simpleName}"
                }
            },
            onStopLocalResultServer = {
                localResultServer.stop()
                localResultServerUrl = null
                projectStatusText = "Local result display stopped."
            },
            hasDefaultUnsavedNewEventFileDraft = isDefaultUnsavedNewEventFileDraft(),
            hasEditedUnsavedNewEventFileDraft = hasEditedUnsavedNewEventFileDraft(),
            onSaveEventFileForNavigation = { saveCurrentProject() },
            onDiscardUnsavedNewEventFile = {
                closeProject(discardUnsavedChanges = true)
                newEventDraftProject = null
                projectStatusText = "New Event File discarded."
            }
        )
    }
}

/** Prompts for the standard save/discard/cancel decision before replacing or closing a dirty Event File. */
@Composable
private fun UnsavedChangesDialog(
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Unsaved changes") },
        text = {
            Text(
                "The current Event File has unsaved changes. Save before continuing, discard those changes, or cancel to avoid losing edits."
            )
        },
        confirmButton = {
            Button(onClick = onSave) {
                Text("Save")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onDiscard) {
                    Text("Discard Changes")
                }
                Button(onClick = onCancel) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
private fun EventDefinitionSaveDialog(
    currentPath: Path?,
    onSaveAsNew: () -> Unit,
    onOverwrite: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Event definition changed") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "The Event name, start time, format, event type, band, or time limit was changed for an existing Event File."
                )
                Text(
                    "These fields affect competitors, controls, courses, Race Ops, and Results. Save as a new Event File unless you intentionally want to replace the loaded file."
                )
                currentPath?.let { path ->
                    Text(
                        text = "Current file: ${path.fileName}",
                        color = DesktopPalette.Disconnected,
                        fontSize = 13.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onSaveAsNew) {
                Text("Save As New Event File...")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOverwrite) {
                    Text("Overwrite Current File")
                }
                Button(onClick = onCancel) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
private fun UnsavedNewEventFileDialog(
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Unsaved new Event File") },
        text = {
            Text("Save this new Event File before leaving this page, or discard it?")
        },
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

@Composable
private fun CourseKmlKmzUnlockDialog(
    title: String,
    description: String,
    confirmLabel: String,
    onUnlock: (String) -> Boolean,
    onCancel: () -> Unit
) {
    var passwordDraft by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(
                    value = passwordDraft,
                    onValueChange = { passwordDraft = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = Color.DarkGray
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (onUnlock(passwordDraft)) {
                        passwordDraft = ""
                    }
                },
                enabled = passwordDraft.isNotBlank()
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            Button(onClick = onCancel) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun BulkCategoryActionDialog(
    action: BulkCategoryAction,
    categoryCount: Int,
    hasProtectedCategoryData: Boolean,
    onConfirm: (String) -> Boolean,
    onCancel: () -> Unit
) {
    var passwordDraft by remember(action) { mutableStateOf("") }
    val title = when (action) {
        BulkCategoryAction.DeleteAllAssignedControls -> "Delete all assigned controls"
        BulkCategoryAction.DeleteAllCategories -> "Delete all categories"
    }
    val description = when (action) {
        BulkCategoryAction.DeleteAllAssignedControls ->
            "This removes all assigned controls, beacons, category length/climb data, and protected course/order data from $categoryCount categories. Category names and competitors are kept."
        BulkCategoryAction.DeleteAllCategories ->
            "This removes all category names, assigned controls, category length/climb data, and protected course/order data from the Event File. Competitors are kept but become uncategorized."
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(description)
                if (hasProtectedCategoryData) {
                    Text(
                        text = "Because protected course data will be affected, enter the Event Password to continue.",
                        fontSize = 13.sp,
                        color = Color.DarkGray
                    )
                    TextField(
                        value = passwordDraft,
                        onValueChange = { passwordDraft = it },
                        label = { Text("Event Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (onConfirm(passwordDraft)) {
                        passwordDraft = ""
                    }
                },
                enabled = categoryCount > 0 && (!hasProtectedCategoryData || passwordDraft.isNotBlank())
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            Button(onClick = onCancel) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DeleteAllCompetitorsDialog(
    competitorCount: Int,
    matchedReadoutCount: Int,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Delete all competitors") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "This removes all $competitorCount competitors from the Event File and clears competitor lists from categories."
                )
                Text(
                    text = if (matchedReadoutCount > 0) {
                        "$matchedReadoutCount matched readouts will be preserved as unmatched readouts for review."
                    } else {
                        "No matched readouts are currently attached to competitors."
                    },
                    fontSize = 13.sp,
                    color = Color.DarkGray
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = competitorCount > 0
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            Button(onClick = onCancel) {
                Text("Cancel")
            }
        }
    )
}

private fun courseControlMatchSummary(
    foxCount: Int,
    beaconCount: Int,
    spectatorCount: Int
): String {
    val parts = buildList {
        if (foxCount > 0) {
            add("$foxCount ${if (foxCount == 1) "fox" else "foxes"}")
        }
        if (beaconCount > 0) {
            add(if (beaconCount == 1) "Beacon" else "$beaconCount beacons")
        }
        if (spectatorCount > 0) {
            add(if (spectatorCount == 1) "Spectator" else "$spectatorCount spectators")
        }
    }
    val nonEmptyParts = parts.ifEmpty { listOf("none") }
    return when (nonEmptyParts.size) {
        1 -> nonEmptyParts.single()
        2 -> nonEmptyParts.joinToString(" and ")
        else -> nonEmptyParts.dropLast(1).joinToString(", ") + ", and " + nonEmptyParts.last()
    }
}

@Suppress("DEPRECATION")
private fun EventCategoryData.restorableControlPointsText(): String =
    category.controlPointsString.takeIf { it.isNotBlank() }
        ?: ControlPointRules.formatControlPoints(
            controlPoints.map { controlPoint ->
                ControlPointDefinition(controlPoint.siCode, controlPoint.type, controlPoint.order)
            }
        )

@Composable
private fun CourseKmlKmzImportReviewDialog(
    review: PendingCourseKmlKmzImportReview,
    onKeep: (fetchElevations: Boolean, applyCategoryAssignments: Boolean, createMissingCategories: Boolean) -> Unit,
    onCancel: () -> Unit
) {
    val summary = review.summary
    var createMissingCategories by remember(review.sourceName, summary.sourceSha256, summary.missingCategoryNames) {
        mutableStateOf(summary.missingCategoryNames.isNotEmpty())
    }
    val selectedSummary = if (createMissingCategories) {
        review.createdMissingCategorySummary ?: summary
    } else {
        summary
    }
    val categoriesText = selectedSummary.matchedCategoryNames
        .ifEmpty { listOf("None") }
        .joinToString()
    val canFetchElevations = selectedSummary.matchedCategoryIds.isNotEmpty()
    var fetchElevations by remember(review.sourceName, selectedSummary.sourceSha256, selectedSummary.isDuplicateOnly) {
        mutableStateOf(canFetchElevations && selectedSummary.isDuplicateOnly && selectedSummary.hasDuplicateMissingElevations)
    }
    var applyCategoryAssignments by remember(review.sourceName, summary.sourceSha256) {
        mutableStateOf(false)
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                if (summary.isDuplicateOnly) {
                    "Duplicate controls/route import"
                } else {
                    "Review controls/route import"
                }
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("File: ${review.sourceName}")
                selectedSummary.eventTypeWarnings.forEach { warning ->
                    Text(
                        text = warning,
                        color = DesktopPalette.Error,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text("Matched categories: ${selectedSummary.matchedCategoryCount} of ${selectedSummary.routeCount} route placemarks")
                Text("Categories: $categoriesText")
                if (summary.missingCategoryNames.isNotEmpty()) {
                    Text("Categories listed in KML/KMZ but not in the Event File: ${summary.missingCategoryNames.joinToString()}")
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = createMissingCategories,
                            onCheckedChange = { createMissingCategories = it }
                        )
                        Text("Create missing categories and save their course data")
                    }
                    Text(
                        text = if (createMissingCategories) {
                            "Created categories will be saved without competitors. Competitors imported later with the same category names will use this stored course data."
                        } else {
                            "Missing categories will be left out of this import. Add those categories or reimport the KML/KMZ later to store their course data."
                        },
                        fontSize = 12.sp,
                        color = Color.DarkGray
                    )
                }
                if (selectedSummary.importedCategoryCount > 0) {
                    Text("Categories to update: ${selectedSummary.importedCategoryCount}")
                }
                if (selectedSummary.assignedCategoryControlCount > 0) {
                    Text("Category assigned control points available to copy: ${selectedSummary.assignedCategoryControlCount}")
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = applyCategoryAssignments,
                            onCheckedChange = { applyCategoryAssignments = it }
                        )
                        Text("Replace category assigned controls with matched KML/KMZ controls")
                    }
                    Text(
                        "If selected, existing assigned controls for the matched category are replaced and stored in neutral fox-label order, not route order.",
                        fontSize = 12.sp,
                        color = Color.DarkGray
                    )
                }
                if (selectedSummary.duplicateCategoryCount > 0) {
                    Text("Duplicate categories already imported: ${selectedSummary.duplicateCategoryCount}")
                }
                Text("Matched course controls: ${courseControlMatchSummary(selectedSummary.matchedFoxCount, selectedSummary.matchedBeaconCount, selectedSummary.matchedSpectatorCount)}")
                if (selectedSummary.labelConversions.isNotEmpty()) {
                    Text("Imported control names to treat as existing Event File labels:")
                    selectedSummary.labelConversions.take(8).forEach { conversion ->
                        Text("${conversion.importedName} -> ${conversion.eventControlLabel}")
                    }
                    if (selectedSummary.labelConversions.size > 8) {
                        Text("Additional likely name matches: ${selectedSummary.labelConversions.size - 8}")
                    }
                }
                if (selectedSummary.changedControlLocationCount > 0) {
                    Text("Control locations to update: ${selectedSummary.changedControlLocationCount}")
                    Text("Stored courses affected by location changes: ${selectedSummary.controlLocationAffectedCategoryCount}")
                }
                if (canFetchElevations) {
                    Text(
                        if (selectedSummary.duplicateMissingElevationPointCount > 0) {
                            "Stored route elevations missing: ${selectedSummary.duplicateMissingElevationPointCount} course points"
                        } else {
                            "Imported route elevations: not stored yet"
                        }
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = fetchElevations,
                            onCheckedChange = { fetchElevations = it }
                        )
                        Text(
                            if (selectedSummary.isDuplicateOnly) {
                                "Fetch missing elevations for the stored route"
                            } else {
                                "Fetch missing elevations for the imported route after keeping it"
                            }
                        )
                    }
                }
                Text(
                    text = if (selectedSummary.isDuplicateOnly) {
                        "This file has the same SHA-256 hash as route data already stored in the Event File, so controls and route data will not be reloaded. Elevation retrieval can still fill missing USGS 3DEP route and course-object points. Cancel leaves the Event File unchanged."
                    } else if (
                        selectedSummary.hasLabelConversions &&
                        selectedSummary.importedCategoryCount == 0 &&
                        selectedSummary.assignedCategoryControlCount == 0 &&
                        selectedSummary.changedControlLocationCount == 0
                    ) {
                        "Keep imported data to use these KML/KMZ names as matches to existing Event File labels. Control labels and public labels are not renamed. No route facts, assigned controls, or control locations will change. Cancel leaves the Event File unchanged."
                    } else if (selectedSummary.importedCategoryCount == 0 && selectedSummary.changedControlLocationCount > 0) {
                        "Keep imported data to update control locations. Affected stored route geometry is invalidated so Course Analyzer can recalculate route facts. Category assigned controls are changed only when the assignment checkbox is selected. Cancel leaves the Event File unchanged."
                    } else if (selectedSummary.importedCategoryCount == 0 && selectedSummary.assignedCategoryControlCount > 0) {
                        "Keep imported data to review matched KML/KMZ control points. Category assigned controls are changed only when the assignment checkbox is selected. Cancel leaves the Event File unchanged."
                    } else if (selectedSummary.hasLabelConversions) {
                        "Keep imported data to use these KML/KMZ names as matches to existing Event File labels, update route facts, ideal order, and any changed control locations. Category assigned controls are changed only when the assignment checkbox is selected. Control labels and public labels are not renamed. Cancel leaves the Event File unchanged."
                    } else {
                        "Keep imported data to update route facts, ideal order, and any changed control locations. Category assigned controls are changed only when the assignment checkbox is selected. Elevation retrieval samples missing USGS 3DEP route and course-object points after the import is kept. Cancel leaves the Event File unchanged."
                    },
                    fontSize = 13.sp,
                    color = Color.DarkGray
                )
            }
        },
        confirmButton = {
            Button(onClick = { onKeep(fetchElevations, applyCategoryAssignments, createMissingCategories) }) {
                Text(
                    if (selectedSummary.isDuplicateOnly) {
                        "Continue"
                    } else {
                        "Keep Imported Data"
                    }
                )
            }
        },
        dismissButton = {
            Button(onClick = onCancel) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun CategoriesCsvImportReviewDialog(
    review: PendingCategoriesCsvImportReview,
    onImport: () -> Unit,
    onCancel: () -> Unit
) {
    val preview = review.preview
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Review categories CSV import") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("File: ${review.path.fileName}")
                preview.eventTypeWarnings.forEach { warning ->
                    Text(
                        text = warning,
                        color = DesktopPalette.Error,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text("Categories to add: ${preview.addedCount}")
                Text("Categories to update: ${preview.updatedCount}")
                if (review.invalidLineCount > 0) {
                    Text(
                        text = "Invalid rows skipped: ${review.invalidLineCount}",
                        color = DesktopPalette.Error
                    )
                }
                if (preview.affectedCompetitorCount > 0) {
                    Text("Updated categories currently include ${preview.affectedCompetitorCount} competitor${if (preview.affectedCompetitorCount == 1) "" else "s"}.")
                }
                if (preview.categoriesWithAssignedControlsReplacedCount > 0) {
                    Text(
                        text = "Assigned controls will be replaced for ${preview.categoriesWithAssignedControlsReplacedCount} existing categor${if (preview.categoriesWithAssignedControlsReplacedCount == 1) "y" else "ies"}.",
                        color = DesktopPalette.Warning
                    )
                }
                if (preview.categoriesWithProtectedCoursePreservedCount > 0) {
                    Text("Protected KML/KMZ course data will be preserved for ${preview.categoriesWithProtectedCoursePreservedCount} updated categor${if (preview.categoriesWithProtectedCoursePreservedCount == 1) "y" else "ies"}.")
                }
                Text(
                    "This import updates existing categories by name and appends new names. It does not delete categories missing from the CSV.",
                    fontSize = 12.sp,
                    color = Color.DarkGray
                )
            }
        },
        confirmButton = {
            Button(onClick = onImport) {
                ButtonLabel("Import Categories")
            }
        },
        dismissButton = {
            Button(onClick = onCancel) {
                ButtonLabel("Cancel")
            }
        }
    )
}

@Composable
private fun ControlsCsvImportReviewDialog(
    review: PendingControlsCsvImportReview,
    onImport: (syncMissingControls: Boolean) -> Unit,
    onCancel: () -> Unit
) {
    val preview = review.preview
    var syncMissingControls by remember(review.path, preview.missingExistingCount) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Review controls CSV import") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("File: ${review.path.fileName}")
                preview.eventTypeWarnings.forEach { warning ->
                    Text(
                        text = warning,
                        color = DesktopPalette.Error,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text("Controls to add: ${preview.addedCount}")
                Text("Controls to update: ${preview.changedCount}")
                Text("Unchanged controls: ${preview.unchangedCount}")
                if (preview.missingExistingCount > 0) {
                    Text("Existing controls missing from CSV: ${preview.missingExistingCount}")
                    Text("Unused missing controls that can be removed: ${preview.removableMissingCount}")
                    if (preview.usedMissingCount > 0) {
                        Text(
                            text = "Missing controls kept because they are used: ${preview.usedMissingCount}",
                            color = DesktopPalette.Warning
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = syncMissingControls,
                            onCheckedChange = { syncMissingControls = it }
                        )
                        Text("Remove unused existing controls missing from the CSV")
                    }
                }
                if (review.invalidLineCount > 0) {
                    Text(
                        text = "Invalid rows skipped: ${review.invalidLineCount}",
                        color = DesktopPalette.Error
                    )
                }
                if (preview.affectedAssignedCategoryCount > 0 || preview.affectedProtectedCourseCount > 0) {
                    Text(
                        text = "Changed controls are already used by ${preview.affectedAssignedCategoryCount} assigned categor${if (preview.affectedAssignedCategoryCount == 1) "y" else "ies"} and ${preview.affectedProtectedCourseCount} stored course${if (preview.affectedProtectedCourseCount == 1) "" else "s"}.",
                        color = DesktopPalette.Error
                    )
                }
                Text(
                    "This import adds new controls and updates matching controls by SI code and type. Missing controls are deleted only when the sync checkbox is selected and the control is unused.",
                    fontSize = 12.sp,
                    color = Color.DarkGray
                )
            }
        },
        confirmButton = {
            Button(onClick = { onImport(syncMissingControls) }) {
                ButtonLabel("Import Controls")
            }
        },
        dismissButton = {
            Button(onClick = onCancel) {
                ButtonLabel("Cancel")
            }
        }
    )
}

@Composable
private fun CourseKmlKmzCategoryMappingDialog(
    mapping: PendingCourseKmlKmzCategoryMapping,
    onApply: (categoryId: String) -> Unit,
    onCancel: () -> Unit
) {
    var selectedCategoryId by remember(mapping.sourceName, mapping.categoryOptions) {
        mutableStateOf(mapping.categoryOptions.firstOrNull()?.first)
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Choose route category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("File: ${mapping.sourceName}")
                Text("No LineString name or file name matched an Event File category.")
                Text("Choose the category this KML/KMZ route should update.")
                CourseAnalysisCategoryPicker(
                    selectedCategoryId = selectedCategoryId,
                    categories = mapping.categoryOptions,
                    onCategorySelected = { selectedCategoryId = it },
                    modifier = Modifier.width(280.dp)
                )
                Text("Matched course controls: ${courseControlMatchSummary(mapping.matchedFoxCount, mapping.matchedBeaconCount, mapping.matchedSpectatorCount)}")
                if (mapping.labelConversions.isNotEmpty()) {
                    Text("Imported control names to treat as existing Event File labels:")
                    mapping.labelConversions.take(8).forEach { conversion ->
                        Text("${conversion.importedName} -> ${conversion.eventControlLabel}")
                    }
                    if (mapping.labelConversions.size > 8) {
                        Text("Additional likely name matches: ${mapping.labelConversions.size - 8}")
                    }
                }
                Text(
                    text = "The selected category will be used only for this import. Control labels and public labels are not renamed. Cancel leaves the Event File unchanged.",
                    fontSize = 13.sp,
                    color = Color.DarkGray
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { selectedCategoryId?.let(onApply) },
                enabled = selectedCategoryId != null
            ) {
                Text("Use Selected Category")
            }
        },
        dismissButton = {
            Button(onClick = onCancel) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun IndeterminateProgressDialog(
    title: String,
    message: String
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = message,
                    fontSize = 13.sp,
                    color = Color.DarkGray
                )
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

@Composable
private fun CourseKmlKmzElevationProgressDialog(
    progress: CourseKmlKmzElevationProgressUiState,
    onCancel: () -> Unit
) {
    val total = progress.totalPointCount.coerceAtLeast(1)
    val completed = progress.completedPointCount.coerceIn(0, total)
    val fraction = completed.toFloat() / total.toFloat()
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Retrieving course elevations") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("File: ${progress.sourceName}")
                if (progress.categoryName.isNotBlank()) {
                    Text("Category: ${progress.categoryName}")
                }
                LinearProgressIndicator(
                    progress = fraction,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("$completed of $total course points")
                Text(
                    text = if (progress.cancelRequested) {
                        "Canceling after the current elevation request finishes..."
                    } else {
                        "Samples are fetched along route legs at the elevation data resolution."
                    },
                    fontSize = 13.sp,
                    color = Color.DarkGray
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            Button(
                onClick = onCancel,
                enabled = !progress.cancelRequested
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun VenueElevationCacheProgressDialog(
    progress: VenueElevationCacheProgressUiState,
    onCancel: () -> Unit
) {
    val total = progress.totalPointCount.coerceAtLeast(1)
    val completed = progress.completedPointCount.coerceIn(0, total)
    val fraction = completed.toFloat() / total.toFloat()
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Creating elevation cache") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Venue: ${progress.venueName}")
                LinearProgressIndicator(
                    progress = fraction,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("$completed of $total elevation grid points")
                Text(
                    text = if (progress.cancelRequested) {
                        "Canceling after the current elevation request finishes..."
                    } else {
                        "The elevation grid will be reused for route and control elevations inside its bounding box."
                    },
                    fontSize = 13.sp,
                    color = Color.DarkGray
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            Button(
                onClick = onCancel,
                enabled = !progress.cancelRequested
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun VenueElevationCacheListingProgressDialog() {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Loading Elevation Data") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = "Reading cached venue elevation files...",
                    fontSize = 13.sp,
                    color = Color.DarkGray
                )
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

@Composable
private fun UnsavedSubmenuChangesDialog(
    onSave: () -> Unit,
    onDontSave: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Unsaved Event changes") },
        text = { Text("Save Event changes before leaving this menu?") },
        confirmButton = {
            Button(
                onClick = onSave,
                colors = saveEventButtonColors()
            ) {
                Text("Save Event")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onDontSave) {
                    Text("Don't Save")
                }
                Button(onClick = onCancel) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
private fun AssignedControlsWarningDialog(
    warning: EventAssignedControlWarning,
    canRestore: Boolean,
    onKeep: () -> Unit,
    onRestore: () -> Unit
) {
    val missingLines = buildList {
        if (warning.hasNoAssignedFoxes) {
            add("No fox controls are assigned.")
        }
        if (warning.missingBeaconLabels.isNotEmpty()) {
            add("Missing beacon: ${warning.missingBeaconLabels.joinToString(", ")}")
        }
    }.joinToString("\n")
    val message = if (warning.isClearingAllAssignments) {
        "All foxes and required finish controls are being removed from ${warning.categoryName}.\n\n" +
            "This category will not have a valid course unless assignments are restored or replaced.\n\n" +
            missingLines
        } else {
        "Category ${warning.categoryName} has incomplete assigned controls:\n" +
            "$missingLines\n\n" +
            "A valid radio-orienteering category should include at least one fox and a beacon. " +
            "Sprint spectator controls are optional; when assigned they require a beacon, and when no spectator is assigned the finish beacon is also used as the slow-to-fast loop transition."
    }

    AlertDialog(
        onDismissRequest = if (canRestore) onRestore else onKeep,
        title = { Text("Assigned Controls warning") },
        text = {
            Text(message)
        },
        confirmButton = {
            Button(onClick = onKeep) {
                Text(if (canRestore) "Remove All" else "OK")
            }
        },
        dismissButton = if (canRestore) {
            {
                Button(onClick = onRestore) {
                    Text("Restore")
                }
            }
        } else {
            {}
        }
    )
}

@Composable
private fun NationalStartListDefaultsDialog(
    onReset: () -> Unit,
    onKeepCurrent: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onKeepCurrent,
        title = { Text("Reset Start List settings?") },
        text = {
            Text(
                "National events usually use Ignore clubs, 2 per time, and No start groups. " +
                    "Reset the current Start List settings to those defaults?"
            )
        },
        confirmButton = {
            Button(onClick = onReset) {
                Text("Reset")
            }
        },
        dismissButton = {
            Button(onClick = onKeepCurrent) {
                Text("Keep current")
            }
        }
    )
}

@Composable
private fun EventRegImportDialog(
    title: String = "Import EventReg Website",
    idleDescription: String = "Creates one Event File for each competition class column with registered competitors.",
    importingDescription: String = "Downloading registration table and generating Event Files...",
    url: String,
    isImporting: Boolean,
    onUrlChange: (String) -> Unit,
    onImport: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(
                    value = url,
                    onValueChange = onUrlChange,
                    enabled = !isImporting,
                    label = { Text("Registration list URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = if (isImporting) {
                        importingDescription
                    } else {
                        idleDescription
                    },
                    fontSize = 13.sp,
                    color = Color.DarkGray
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onImport,
                enabled = !isImporting && url.isNotBlank()
            ) {
                Text(if (isImporting) "Importing..." else "Import")
            }
        },
        dismissButton = {
            Button(onClick = onCancel, enabled = !isImporting) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun CompetitorCsvImportOptionsDialog(
    fileName: String,
    missingCategoryNames: List<String>,
    synchronizeToCsv: Boolean,
    onSynchronizeToCsvChange: (Boolean) -> Unit,
    onImport: (createMissingCategories: Boolean) -> Unit,
    onCancel: () -> Unit
) {
    var createMissingCategories by remember(fileName, missingCategoryNames) {
        mutableStateOf(missingCategoryNames.isNotEmpty())
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Import Competitors CSV") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Import $fileName",
                    fontSize = 13.sp,
                    color = Color.DarkGray
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = synchronizeToCsv,
                        onCheckedChange = onSynchronizeToCsvChange
                    )
                    Text(
                        text = "Synchronize competitors to CSV",
                        fontSize = 13.sp,
                        color = DesktopPalette.Black
                    )
                }
                Text(
                    text = if (synchronizeToCsv) {
                        "Updates matching competitors and removes current competitors not included in the CSV."
                    } else {
                        "Default: adds new competitors and leaves matching existing competitors unchanged."
                    },
                    fontSize = 13.sp,
                    color = Color.DarkGray
                )
                if (missingCategoryNames.isNotEmpty()) {
                    Text(
                        text = "New categories in CSV: ${missingCategoryNames.joinToString()}",
                        fontSize = 13.sp,
                        color = DesktopPalette.Black
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = createMissingCategories,
                            onCheckedChange = { createMissingCategories = it }
                        )
                        Text(
                            text = "Create missing categories",
                            fontSize = 13.sp,
                            color = DesktopPalette.Black
                        )
                    }
                    Text(
                        text = if (createMissingCategories) {
                            "Created categories will not have course data unless route data was imported for them separately. Review Course Analyzer and add course data before the event."
                        } else {
                            "Competitors in these categories will be imported without a category assignment."
                        },
                        fontSize = 13.sp,
                        color = Color.DarkGray
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onImport(createMissingCategories) }) {
                Text("Import")
            }
        },
        dismissButton = {
            Button(onClick = onCancel) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun AboutRadioOracleDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("About Radio-Oracle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Radio-Oracle Desktop")
                Text("Version ${DesktopBuildInfo.displayVersion}")
                Text("Desktop event administration beta for radio orienteering events.")
                Text("Maintained by OpenARDF.")
                Text("GitHub: https://github.com/OpenARDF/Radio-Oracle")
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

private sealed interface DesktopPendingNavigation {
    data object Back : DesktopPendingNavigation
    data class Workflow(val workflow: DesktopWorkflow, val bypassedDisabled: Boolean = false) : DesktopPendingNavigation
    data class Item(val itemId: String, val bypassedDisabled: Boolean = false) : DesktopPendingNavigation
}

private data class BypassedDisabledNavigation(
    val workflow: DesktopWorkflow?,
    val itemId: String?
)

private const val DisabledNavigationExplorationStatus = "Disabled menu being explored; some commands may not work until required event data is complete."

private fun DesktopPendingNavigation.updatedBypassedDisabledNavigation(
    previous: BypassedDisabledNavigation?,
    appliedState: DesktopNavState,
    readiness: DesktopNavigationReadiness
): BypassedDisabledNavigation? =
    run {
        val retainedWorkflow = previous?.workflow?.takeIf { workflow ->
            workflow == appliedState.workflow && !DesktopNavigation.isWorkflowEnabled(workflow, readiness)
        }
        val nextWorkflow = when (this) {
            DesktopPendingNavigation.Back,
            is DesktopPendingNavigation.Item -> retainedWorkflow
            is DesktopPendingNavigation.Workflow -> if (
                bypassedDisabled &&
                !DesktopNavigation.isWorkflowEnabled(appliedState.workflow, readiness)
            ) {
                appliedState.workflow
            } else {
                null
            }
        }
        val nextItemId = when (this) {
            is DesktopPendingNavigation.Item -> appliedState.selectedItemId.takeIf {
                bypassedDisabled && DesktopNavigation.itemById(appliedState.workflow, it)
                    ?.let { item -> !DesktopNavigation.isItemEnabled(item, readiness) } == true
            }
            DesktopPendingNavigation.Back,
            is DesktopPendingNavigation.Workflow -> null
        }
        if (nextWorkflow == null && nextItemId == null) {
            null
        } else {
            BypassedDisabledNavigation(workflow = nextWorkflow, itemId = nextItemId)
        }
    }

private fun BypassedDisabledNavigation.activeFor(
    navState: DesktopNavState,
    readiness: DesktopNavigationReadiness
): BypassedDisabledNavigation? {
    val activeWorkflow = workflow?.takeIf { it == navState.workflow && !DesktopNavigation.isWorkflowEnabled(it, readiness) }
    val activeItemId = itemId?.takeIf {
        it == navState.selectedItemId && DesktopNavigation.itemById(navState.workflow, it)
            ?.let { !DesktopNavigation.isItemEnabled(it, readiness) }
            ?: false
    }
    return if (activeWorkflow == null && activeItemId == null) {
        null
    } else {
        BypassedDisabledNavigation(workflow = activeWorkflow, itemId = activeItemId)
    }
}

private data class PendingAssignedControlsWarning(
    val warning: EventAssignedControlWarning,
    val previousControlPointsText: String?
)

private data class DesktopImportReport(
    val title: String,
    val lines: List<String>
)

private data class DesktopImportCheckpoint(
    val title: String,
    val backupPath: Path,
    val projectFile: EventProjectFile,
    val protectedCoursePassword: String?,
    val protectedIdealOrderByCategoryId: Map<String, String>,
    val protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>
)

private data class PendingCourseKmlKmzImportReview(
    val sourceName: String,
    val path: Path,
    val updatedProject: EventProjectFile,
    val summary: DesktopCourseKmlImportSummary,
    val createdMissingCategoryProject: EventProjectFile?,
    val createdMissingCategorySummary: DesktopCourseKmlImportSummary?,
    val password: String
)

private data class CourseKmlKmzImportPreview(
    val updatedProject: EventProjectFile,
    val summary: DesktopCourseKmlImportSummary,
    val createdMissingCategoryProject: EventProjectFile?,
    val createdMissingCategorySummary: DesktopCourseKmlImportSummary?
)

private data class PendingCompetitorsCsvImportReview(
    val path: Path,
    val missingCategoryNames: List<String>
)

private data class PendingCategoriesCsvImportReview(
    val path: Path,
    val rows: List<CategoryCsvImportRow>,
    val invalidLineCount: Int,
    val preview: DesktopCategoryCsvImportPreview
)

private data class PendingControlsCsvImportReview(
    val path: Path,
    val rows: List<ControlCsvImportRow>,
    val invalidLineCount: Int,
    val preview: DesktopControlsCsvImportPreview
)

private enum class CourseKmlKmzUnlockAction {
    Import,
    Export
}

private enum class BulkCategoryAction {
    DeleteAllAssignedControls,
    DeleteAllCategories
}

private data class PendingCourseKmlKmzCategoryMapping(
    val sourceName: String,
    val path: Path,
    val password: String,
    val categoryOptions: List<Pair<String, String>>,
    val matchedControlPointCount: Int,
    val matchedFoxCount: Int,
    val matchedBeaconCount: Int,
    val matchedSpectatorCount: Int,
    val controlPointCount: Int,
    val labelConversions: List<DesktopCourseKmlLabelConversion>
)

private data class CourseAnalysisMissingDataPrompt(
    val categoryId: String,
    val summary: DesktopCourseAnalysisSummary
)

private data class CourseKmlKmzElevationProgressUiState(
    val sourceName: String,
    val categoryName: String,
    val completedPointCount: Int,
    val totalPointCount: Int,
    val cancelRequested: Boolean = false
)

private data class VenueElevationCacheProgressUiState(
    val venueName: String,
    val completedPointCount: Int,
    val totalPointCount: Int,
    val cancelRequested: Boolean = false
)

private data class ProtectedControlLocationSummary(
    val controlId: String,
    val label: String,
    val latitude: Double?,
    val longitude: Double?,
    val affectedCategoryCount: Int
)

private data class DesktopCompetitorSiCardDraft(
    val siNumber: Int,
    val firstName: String? = null,
    val lastName: String? = null,
    val club: String? = null
)

private data class DesktopReadoutEditDraft(
    val resultId: String,
    val competitorName: String,
    val matched: Boolean,
    val startSeconds: String,
    val finishSeconds: String,
    val controlPunchesText: String,
    val resultStatus: ResultStatus,
    val categoryId: String?,
    val originalCategoryId: String?,
    val isPractice: Boolean,
    val updateCompetitorCategory: Boolean = false
)

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
    eventFilePath: Path? = null,
    projectStatusText: String = "No Event File open.",
    hasUnsavedChanges: Boolean = false,
    siReaderState: DesktopSiReaderUiState = DesktopSiReaderUiState.disconnected(),
    isDownloadingSiReadout: Boolean = false,
    isContinuousSiReadoutActive: Boolean = false,
    isReadingCompetitorSiCard: Boolean = false,
    siDownloadStatusText: String? = null,
    isSendingLiveResults: Boolean = false,
    isBackgroundLiveResultSendingEnabled: Boolean = false,
    readoutDuplicatePolicy: EventReadoutDuplicatePolicy = EventReadoutDuplicatePolicy.Reject,
    isReadoutAlertSoundEnabled: Boolean = true,
    areAliasesEnabled: Boolean = true,
    localResultServerUrl: String? = null,
    printerDiagnostics: DesktopPrinterDiagnostics = DesktopPrinterDiagnostics.from(emptyList()),
    raceClockTick: Long = 0L,
    onRenameRace: (String) -> Unit = {},
    onUpdateRaceStartDateTime: (String) -> Unit = {},
    onUpdateRaceSettings: (RaceType, RaceLevel, RaceBand, String) -> Unit = { _, _, _, _ -> },
    onUpdateEventFileName: (String) -> Boolean = { false },
    onRenameCategory: (String, String) -> Unit = { _, _ -> },
    onUpdateCategoryControlPoints: (String, String, Boolean) -> Unit = { _, _, _ -> },
    onUpdateCategoryPhysicalStats: (String, String, String) -> Unit = { _, _, _ -> },
    onAddCategory: (String) -> Boolean = { false },
    onRemoveCategory: (String, Boolean) -> Unit = { _, _ -> },
    onRenameCompetitor: (String, String, String) -> Unit = { _, _, _ -> },
    onUpdateCompetitorNumbers: (String, String, String) -> Unit = { _, _, _ -> },
    onUpdateCompetitorClubIdentity: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    onUpdateCompetitorBirthYear: (String, String) -> Unit = { _, _ -> },
    onUpdateCompetitorStartTime: (String, String) -> Unit = { _, _ -> },
    onUpdateStartDrawSettings: (String, StartDrawOptions) -> Unit = { _, _ -> },
    onDrawStartList: (String, StartDrawOptions) -> Unit = { _, _ -> },
    onDrawBalancedStartList: (String, StartDrawOptions) -> Unit = { _, _ -> },
    onAddCompetitor: (String, String, String, String, String, String, String?, String, String) -> Boolean = { _, _, _, _, _, _, _, _, _ -> false },
    onAssignCompetitorCategory: (String, String?) -> Unit = { _, _ -> },
    onRemoveCompetitor: (String, Boolean) -> Unit = { _, _ -> },
    onRemoveReadout: (String) -> Unit = {},
    onUpdateReadoutStatus: (String, ResultStatus) -> Unit = { _, _ -> },
    onEditReadout: (String) -> Unit = {},
    onAssignUnmatchedReadout: (String, String) -> Unit = { _, _ -> },
    onDownloadSportIdentReadout: () -> Unit = {},
    onStartContinuousSportIdentReadout: () -> Unit = {},
    onStopContinuousSportIdentReadout: () -> Unit = {},
    onPreviewFinishTicket: (String) -> String = { "" },
    onPrintFinishTicket: (String) -> Unit = {},
    onAddManualReadout: (String?, String, String, String, String, ResultStatus) -> Boolean = { _, _, _, _, _, _ -> false },
    onUpdateControl: (String, String, String, ControlPointType, Boolean, String, String) -> Unit = { _, _, _, _, _, _, _ -> },
    onAddControl: (String, String, ControlPointType, Boolean, String, String) -> Boolean = { _, _, _, _, _, _ -> false },
    onRemoveControl: (String) -> Unit = {},
    onImportControlsRouteKmlKmz: () -> Unit = {},
    onSendRobisLiveResults: () -> Unit = {},
    onSetBackgroundLiveResultSendingEnabled: (Boolean) -> Unit = {},
    onSetReadoutDuplicatePolicy: (EventReadoutDuplicatePolicy) -> Unit = {},
    onSetReadoutAlertSoundEnabled: (Boolean) -> Unit = {},
    onSetAliasesEnabled: (Boolean) -> Unit = {},
    onStartLocalResultServer: () -> Unit = {},
    onStopLocalResultServer: () -> Unit = {},
    isProtectedCourseOrderUnlocked: Boolean = false,
    protectedIdealOrderByCategoryId: Map<String, String> = emptyMap(),
    protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo> = emptyMap(),
    recentImportReport: DesktopImportReport? = null,
    recentImportCheckpoint: DesktopImportCheckpoint? = null,
    recentActivityLog: List<String> = emptyList(),
    onRetrieveMissingCourseElevations: (String) -> Unit = {},
    onDownloadVenueElevationCache: (String, DesktopVenueElevationBoundingBox, Double, Double, DesktopVenueElevationCacheSource, String) -> Unit = { _, _, _, _, _, _ -> },
    onOpenVenueElevationCacheFolder: () -> Unit = {},
    elevationCacheRefreshToken: Int = 0,
    onUnlockProtectedCourseOrder: (String) -> Boolean = { false },
    onUpdateProtectedIdealOrder: (String, String) -> Unit = { _, _ -> },
    onUseCalculatedCourseAnalysisRoute: (DesktopCourseCalculatedRouteApplication) -> String = { "" },
    onApplyCourseAnalysisFoxRenumberingOnly: (DesktopCourseWaitRenumbering) -> String = { "" },
    onReadCompetitorSiCardForAddRow: suspend () -> DesktopCompetitorSiCardDraft = {
        error("SI card reader is not configured.")
    },
    onUpdateProtectedControlLocation: (String, String, String) -> String = { _, _, _ -> "" },
    onUpdateProtectedCoursePassword: (String, String, String) -> Boolean = { _, _, _ -> false },
    onLockProtectedCourseOrder: () -> Unit = {},
    isNavActionEnabled: (DesktopNavAction) -> Boolean = { false },
    disabledNavActionReason: (DesktopNavAction) -> String? = { null },
    onInsertTestControls: () -> Unit = {},
    onInsertTestCategories: () -> Unit = {},
    onInsertTestCompetitors: () -> Unit = {},
    onInsertTestSportIdentDownloads: () -> Unit = {},
    onRestoreRecentImportCheckpoint: () -> Unit = {},
    onRecalculateResults: () -> Unit = {},
    onNavAction: (DesktopNavAction) -> Unit = {},
    hasDefaultUnsavedNewEventFileDraft: Boolean = false,
    hasEditedUnsavedNewEventFileDraft: Boolean = false,
    onSaveEventFileForNavigation: () -> Boolean = { false },
    onDiscardUnsavedNewEventFile: () -> Unit = {}
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
        var navState by remember { mutableStateOf(DesktopNavState()) }
        var isSplashVisible by remember { mutableStateOf(true) }
        var pendingNavigation by remember { mutableStateOf<DesktopPendingNavigation?>(null) }
        var pendingDirtySubmenuNavigation by remember { mutableStateOf<DesktopPendingNavigation?>(null) }
        var bypassedDisabledNavigation by remember { mutableStateOf<BypassedDisabledNavigation?>(null) }
        val navigationReadiness = DesktopNavigationReadiness.from(projectFile)
        val activeBypassedDisabledNavigation = bypassedDisabledNavigation
            ?.activeFor(navState, navigationReadiness)
        val workspaceBackgroundColor = when {
            activeBypassedDisabledNavigation != null -> DesktopPalette.WarningBackground
            navState.submenuStack.isNotEmpty() -> DesktopPalette.NavigationBackground
            else -> DesktopPalette.White
        }

        fun selectionFor(intent: DesktopPendingNavigation): Pair<DesktopNavState, DesktopNavAction?> =
            when (intent) {
                DesktopPendingNavigation.Back -> navState.back() to null
                is DesktopPendingNavigation.Workflow -> navState.switchWorkflow(intent.workflow) to null
                is DesktopPendingNavigation.Item -> DesktopNavigation.currentItems(navState)
                    .firstOrNull { it.id == intent.itemId }
                    ?.let {
                        val selection = DesktopNavigation.selectItem(navState, it)
                        selection.state to selection.action
                    }
                    ?: (navState to null)
            }

        fun applyNavigation(intent: DesktopPendingNavigation) {
            val (nextState, action) = selectionFor(intent)
            if (isProtectedCourseOrderUnlocked && DesktopNavigation.isLeavingCategoriesMenu(navState, nextState)) {
                onLockProtectedCourseOrder()
            }
            var appliedState = nextState
            action?.let {
                onNavAction(it)
                appliedState = DesktopNavigation.returnToParentMenuAfterAction(nextState, it)
            }
            navState = appliedState
            bypassedDisabledNavigation = intent.updatedBypassedDisabledNavigation(
                previous = activeBypassedDisabledNavigation,
                appliedState = appliedState,
                readiness = navigationReadiness
            )
        }

        fun requestNavigation(intent: DesktopPendingNavigation) {
            val (nextState, action) = selectionFor(intent)
            val shouldBypassGuard = action == DesktopNavAction.SaveEventFile
            if (
                !shouldBypassGuard &&
                DesktopNavigation.shouldGuardUnsavedNewEventDraft(
                    navState,
                    nextState,
                    hasEditedUnsavedNewEventFileDraft
                )
            ) {
                pendingNavigation = intent
            } else if (
                !shouldBypassGuard &&
                !hasDefaultUnsavedNewEventFileDraft &&
                DesktopNavigation.shouldGuardDirtySubmenuExit(
                    currentState = navState,
                    nextState = nextState,
                    hasUnsavedChanges = hasUnsavedChanges
                )
            ) {
                pendingDirtySubmenuNavigation = intent
            } else {
                if (
                    !shouldBypassGuard &&
                    hasDefaultUnsavedNewEventFileDraft &&
                    DesktopNavigation.isLeavingNewEventFilePage(navState, nextState)
                ) {
                    onDiscardUnsavedNewEventFile()
                }
                applyNavigation(intent)
            }
        }

        if (isSplashVisible) {
            RadioOracleSplashScreen(onDismiss = { isSplashVisible = false })
        } else {
            Surface(modifier = Modifier.fillMaxSize(), color = DesktopPalette.White) {
                Column(modifier = Modifier.fillMaxSize()) {
                    AppTopBar(projectFile)
                    Row(modifier = Modifier.weight(1f)) {
                        NavigationRail(
                            navState = navState,
                            navigationReadiness = navigationReadiness,
                            isNavActionEnabled = isNavActionEnabled,
                            disabledNavActionReason = disabledNavActionReason,
                            onBack = { requestNavigation(DesktopPendingNavigation.Back) },
                            onSaveEvent = { onNavAction(DesktopNavAction.SaveEventFile) },
                            onItemSelected = { item, bypassedDisabled ->
                                requestNavigation(DesktopPendingNavigation.Item(item.id, bypassedDisabled))
                            }
                        )
                        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .background(workspaceBackgroundColor)
                            ) {
                                SectionWorkspace(
                                    workflow = navState.workflow,
                                    section = navState.selectedSection,
                                    title = DesktopNavigation.selectedLabel(navState),
                                    breadcrumb = DesktopNavigation.breadcrumb(navState),
                                    menuDescription = DesktopNavigation.selectedDescription(navState),
                                    projectFile = projectFile,
                                    eventFilePath = eventFilePath,
                                    projectStatusText = projectStatusText,
                                    siReaderState = siReaderState,
                                    onRenameRace = onRenameRace,
                                    onUpdateRaceStartDateTime = onUpdateRaceStartDateTime,
                                    onUpdateRaceSettings = onUpdateRaceSettings,
                                    onUpdateEventFileName = onUpdateEventFileName,
                                    onRenameCategory = onRenameCategory,
                                    onUpdateCategoryControlPoints = onUpdateCategoryControlPoints,
                                    onUpdateCategoryPhysicalStats = onUpdateCategoryPhysicalStats,
                                    onAddCategory = onAddCategory,
                                    onRemoveCategory = onRemoveCategory,
                                    onRenameCompetitor = onRenameCompetitor,
                                    onUpdateCompetitorNumbers = onUpdateCompetitorNumbers,
                                    onUpdateCompetitorClubIdentity = onUpdateCompetitorClubIdentity,
                                    onUpdateCompetitorBirthYear = onUpdateCompetitorBirthYear,
                                    onUpdateCompetitorStartTime = onUpdateCompetitorStartTime,
                                    onUpdateStartDrawSettings = onUpdateStartDrawSettings,
                                    onDrawStartList = onDrawStartList,
                                    onDrawBalancedStartList = onDrawBalancedStartList,
                                    onAddCompetitor = onAddCompetitor,
                                    onAssignCompetitorCategory = onAssignCompetitorCategory,
                                    onRemoveCompetitor = onRemoveCompetitor,
                                    onRemoveReadout = onRemoveReadout,
                                    onUpdateReadoutStatus = onUpdateReadoutStatus,
                                    onEditReadout = onEditReadout,
                                    onAssignUnmatchedReadout = onAssignUnmatchedReadout,
                                    onDownloadSportIdentReadout = onDownloadSportIdentReadout,
                                    onStartContinuousSportIdentReadout = onStartContinuousSportIdentReadout,
                                    onStopContinuousSportIdentReadout = onStopContinuousSportIdentReadout,
                                    onPreviewFinishTicket = onPreviewFinishTicket,
                                    onPrintFinishTicket = onPrintFinishTicket,
                                    isDownloadingSiReadout = isDownloadingSiReadout,
                                    isContinuousSiReadoutActive = isContinuousSiReadoutActive,
                                    isReadingCompetitorSiCard = isReadingCompetitorSiCard,
                                    siDownloadStatusText = siDownloadStatusText,
                                    onAddManualReadout = onAddManualReadout,
                                    onUpdateControl = onUpdateControl,
                                    onAddControl = onAddControl,
                                    onRemoveControl = onRemoveControl,
                                    onImportControlsRouteKmlKmz = onImportControlsRouteKmlKmz,
                                    isSendingLiveResults = isSendingLiveResults,
                                    isBackgroundLiveResultSendingEnabled = isBackgroundLiveResultSendingEnabled,
                                    readoutDuplicatePolicy = readoutDuplicatePolicy,
                                    isReadoutAlertSoundEnabled = isReadoutAlertSoundEnabled,
                                    areAliasesEnabled = areAliasesEnabled,
                                    localResultServerUrl = localResultServerUrl,
                                    printerDiagnostics = printerDiagnostics,
                                    raceClockTick = raceClockTick,
                                    onSendRobisLiveResults = onSendRobisLiveResults,
                                    onSetBackgroundLiveResultSendingEnabled = onSetBackgroundLiveResultSendingEnabled,
                                    onSetReadoutDuplicatePolicy = onSetReadoutDuplicatePolicy,
                                    onSetReadoutAlertSoundEnabled = onSetReadoutAlertSoundEnabled,
                                    onSetAliasesEnabled = onSetAliasesEnabled,
                                    onStartLocalResultServer = onStartLocalResultServer,
                                    onStopLocalResultServer = onStopLocalResultServer,
                                    isProtectedCourseOrderUnlocked = isProtectedCourseOrderUnlocked,
                                    protectedIdealOrderByCategoryId = protectedIdealOrderByCategoryId,
                                    protectedCourseInfoByCategoryId = protectedCourseInfoByCategoryId,
                                    recentImportReport = recentImportReport,
                                    recentImportCheckpoint = recentImportCheckpoint,
                                    recentActivityLog = recentActivityLog,
                                    onRecalculateResults = onRecalculateResults,
                                    onRetrieveMissingCourseElevations = onRetrieveMissingCourseElevations,
                                    onDownloadVenueElevationCache = onDownloadVenueElevationCache,
                                    onOpenVenueElevationCacheFolder = onOpenVenueElevationCacheFolder,
                                    elevationCacheRefreshToken = elevationCacheRefreshToken,
                                    onUnlockProtectedCourseOrder = onUnlockProtectedCourseOrder,
                                    onUpdateProtectedIdealOrder = onUpdateProtectedIdealOrder,
                                    onUseCalculatedCourseAnalysisRoute = onUseCalculatedCourseAnalysisRoute,
                                    onApplyCourseAnalysisFoxRenumberingOnly = onApplyCourseAnalysisFoxRenumberingOnly,
                                    onReadCompetitorSiCardForAddRow = onReadCompetitorSiCardForAddRow,
                                    onUpdateProtectedControlLocation = onUpdateProtectedControlLocation,
                                    onUpdateProtectedCoursePassword = onUpdateProtectedCoursePassword,
                                    isNavActionEnabled = isNavActionEnabled,
                                    onInsertTestControls = onInsertTestControls,
                                    onInsertTestCategories = onInsertTestCategories,
                                    onInsertTestCompetitors = onInsertTestCompetitors,
                                    onInsertTestSportIdentDownloads = onInsertTestSportIdentDownloads,
                                    onRestoreRecentImportCheckpoint = onRestoreRecentImportCheckpoint,
                                    onNavAction = onNavAction
                                )
                            }
                            WorkflowBar(
                                selectedWorkflow = navState.workflow,
                                navigationReadiness = navigationReadiness,
                                bypassedDisabledNavigation = activeBypassedDisabledNavigation,
                                onWorkflowSelected = { workflow, bypassedDisabled ->
                                    requestNavigation(DesktopPendingNavigation.Workflow(workflow, bypassedDisabled))
                                }
                            )
                        }
                    }
                    StatusStrip(
                        projectStatusText = projectStatusText,
                        hasUnsavedChanges = hasUnsavedChanges,
                        navigationDisabledSummary = DesktopNavigation.primaryDisabledSummary(navigationReadiness),
                        isDisabledNavigationExploration = activeBypassedDisabledNavigation != null,
                        siReaderState = siReaderState,
                        isEventFileOpen = projectFile != null,
                        isProtectedCourseOrderUnlocked = isProtectedCourseOrderUnlocked,
                        onLockProtectedCourseOrder = onLockProtectedCourseOrder
                    )
                }
            }
        }
        pendingNavigation?.let { navigation ->
            UnsavedNewEventFileDialog(
                onSave = {
                    if (onSaveEventFileForNavigation()) {
                        pendingNavigation = null
                        applyNavigation(navigation)
                    }
                },
                onDiscard = {
                    onDiscardUnsavedNewEventFile()
                    pendingNavigation = null
                    applyNavigation(navigation)
                },
                onCancel = { pendingNavigation = null }
            )
        }
        pendingDirtySubmenuNavigation?.let { navigation ->
            UnsavedSubmenuChangesDialog(
                onSave = {
                    if (onSaveEventFileForNavigation()) {
                        pendingDirtySubmenuNavigation = null
                        applyNavigation(navigation)
                    }
                },
                onDontSave = {
                    pendingDirtySubmenuNavigation = null
                    applyNavigation(navigation)
                },
                onCancel = { pendingDirtySubmenuNavigation = null }
            )
        }
    }
}

/** Renders the Android-style app bar used at the top of the desktop window. */
@Composable
private fun AppTopBar(projectFile: EventProjectFile?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(DesktopPalette.Primary)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Radio-Oracle",
            color = DesktopPalette.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = desktopTopBarEventText(projectFile),
            modifier = Modifier.weight(1f),
            color = DesktopPalette.White,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun RadioOracleSplashScreen(onDismiss: () -> Unit) {
    val logoBitmap = rememberRadioOracleLogoBitmap()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .onPointerEvent(PointerEventType.Press) { onDismiss() }
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent {
                onDismiss()
                true
            },
        color = DesktopPalette.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                bitmap = logoBitmap,
                contentDescription = "Radio-Oracle logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .width(180.dp)
                    .height(180.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Radio-Oracle",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = DesktopPalette.Black
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Desktop event administration for radio orienteering. Prepare Event Files, import competitors, controls, courses, and elevation data, manage SPORTident readouts, analyze courses, and publish results from one workspace.",
                color = DesktopPalette.Black,
                fontSize = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
            )
            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = "Press any key or click to continue.",
                color = DesktopPalette.Disconnected,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun rememberRadioOracleLogoBitmap() = remember {
    val logoBytes = requireNotNull(
        Thread.currentThread().contextClassLoader.getResourceAsStream("radio-oracle-logo.png")
    ) {
        "Radio-Oracle desktop logo resource is missing."
    }.use { stream -> stream.readBytes() }
    SkiaImage.makeFromEncoded(logoBytes).toComposeImageBitmap()
}

internal fun desktopTopBarEventText(projectFile: EventProjectFile?): String =
    projectFile?.raceData?.race?.name
        ?.takeIf { it.isNotBlank() }
        ?.let { "Event:$it" }
        ?: "No event file loaded"

private fun EventRaceData.readoutEditDraft(resultId: String): DesktopReadoutEditDraft? {
    competitorData.forEach { data ->
        val readoutData = data.readoutData ?: return@forEach
        if (readoutData.result.id == resultId) {
            val competitor = data.competitorCategory.competitor
            val isPractice = race.raceLevel == RaceLevel.PRACTICE
            val elapsedBaseSeconds = if (isPractice) readoutData.result.startTimeSeconds else raceStartSecondsOfDay(race.startDateTimeIso)
            return DesktopReadoutEditDraft(
                resultId = resultId,
                competitorName = competitor.fullName(),
                matched = true,
                startSeconds = if (isPractice && readoutData.result.startTimeSeconds != null) {
                    0L.toReadoutElapsedText()
                } else {
                    elapsedRaceTimeText(readoutData.result.startTimeSeconds)
                },
                finishSeconds = elapsedRaceTimeText(readoutData.result.finishTimeSeconds, elapsedBaseSeconds),
                controlPunchesText = readoutData.controlPunchText(this, elapsedBaseSeconds),
                resultStatus = readoutData.result.resultStatus,
                categoryId = readoutData.result.categoryId ?: competitor.categoryId,
                originalCategoryId = competitor.categoryId,
                isPractice = isPractice
            )
        }
    }
    unmatchedReadoutData.forEach { readoutData ->
        if (readoutData.result.id == resultId) {
            val isPractice = race.raceLevel == RaceLevel.PRACTICE
            val elapsedBaseSeconds = if (isPractice) readoutData.result.startTimeSeconds else raceStartSecondsOfDay(race.startDateTimeIso)
            return DesktopReadoutEditDraft(
                resultId = resultId,
                competitorName = readoutData.result.cardName ?: "",
                matched = false,
                startSeconds = if (isPractice && readoutData.result.startTimeSeconds != null) {
                    0L.toReadoutElapsedText()
                } else {
                    elapsedRaceTimeText(readoutData.result.startTimeSeconds)
                },
                finishSeconds = elapsedRaceTimeText(readoutData.result.finishTimeSeconds, elapsedBaseSeconds),
                controlPunchesText = readoutData.controlPunchText(this, elapsedBaseSeconds),
                resultStatus = readoutData.result.resultStatus,
                categoryId = readoutData.result.categoryId,
                originalCategoryId = null,
                isPractice = isPractice
            )
        }
    }
    return null
}

private fun EventRaceData.elapsedRaceTimeText(
    daySeconds: Long?,
    elapsedBaseSeconds: Long? = raceStartSecondsOfDay(race.startDateTimeIso)
): String =
    daySeconds?.let { seconds ->
        val elapsedSeconds = if (elapsedBaseSeconds == null) {
            seconds
        } else {
            val secondsInDay = 24 * 60 * 60
            ((seconds - elapsedBaseSeconds) % secondsInDay + secondsInDay) % secondsInDay
        }
        elapsedSeconds.toReadoutElapsedText()
    }.orEmpty()

private fun Long.toReadoutElapsedText(): String =
    DurationFormatter.secondsToFormattedString(this, useMinutes = this < 3_600)

private fun org.openardf.radiooracle.shared.event.EventReadoutData.controlPunchText(
    raceData: EventRaceData,
    elapsedBaseSeconds: Long?
): String =
    punches
        .map { aliasPunch -> aliasPunch.punch to aliasPunch.alias?.name }
        .filter { (punch, _) -> punch.punchType == SIRecordType.CONTROL }
        .joinToString("\n") { (punch, aliasName) ->
            val control = raceData.controls.firstOrNull { it.siCode == punch.siCode }
            val label = control?.editPunchToken(raceData.controls)
                ?: aliasName?.takeIf { it.isNotBlank() }
                ?: punch.siCode.toString()
            "$label @ ${raceData.elapsedRaceTimeText(punch.siTimeSeconds, elapsedBaseSeconds)}"
        }

private fun raceStartSecondsOfDay(startDateTimeIso: String): Long? {
    val time = startDateTimeIso.substringAfter('T', missingDelimiterValue = "")
        .substringBefore('.')
        .substringBefore('Z')
        .substringBefore('+')
        .substringBefore('-')
    if (time.isBlank()) {
        return null
    }
    val parts = time.split(":")
    if (parts.size < 2) {
        return null
    }
    val hour = parts[0].toLongOrNull() ?: return null
    val minute = parts[1].toLongOrNull() ?: return null
    val second = parts.getOrNull(2)?.toLongOrNull() ?: 0
    return if (hour in 0..23 && minute in 0..59 && second in 0..59) {
        hour * 3_600 + minute * 60 + second
    } else {
        null
    }
}

@Composable
private fun saveEventButtonColors() =
    ButtonDefaults.buttonColors(
        backgroundColor = DesktopPalette.Connected,
        contentColor = DesktopPalette.Black,
        disabledBackgroundColor = DesktopPalette.LightGrey,
        disabledContentColor = DesktopPalette.Disconnected
    )

private const val DisabledMenuOverrideHoldMillis = 3_000L

private fun Modifier.disabledMenuLongClickOverride(
    available: Boolean,
    onOverride: () -> Unit
): Modifier =
    if (!available) {
        this
    } else {
        pointerInput(onOverride) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                val releasedBeforeThreshold = withTimeoutOrNull(DisabledMenuOverrideHoldMillis) {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.none { it.pressed }) {
                            return@withTimeoutOrNull true
                        }
                    }
                    false
                } ?: false
                if (!releasedBeforeThreshold) {
                    onOverride()
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.none { it.pressed }) {
                            break
                        }
                    }
                }
            }
        }
    }

/** Shows workflow-specific navigation with optional submenu replacement. */
@Composable
private fun NavigationRail(
    navState: DesktopNavState,
    navigationReadiness: DesktopNavigationReadiness,
    isNavActionEnabled: (DesktopNavAction) -> Boolean,
    disabledNavActionReason: (DesktopNavAction) -> String?,
    onBack: () -> Unit,
    onSaveEvent: () -> Unit,
    onItemSelected: (DesktopNavItem, Boolean) -> Unit
) {
    val items = DesktopNavigation.currentItems(navState)
    val navigationItems = items.filterNot { it.action == DesktopNavAction.SaveEventFile }
    Column(
        modifier = Modifier
            .width(220.dp)
            .fillMaxHeight()
            .background(Color(0xFFF5F5F5))
            .border(1.dp, DesktopPalette.LightGrey)
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            navigationItems.forEach { item ->
                val isSelected = item.id == navState.selectedItemId && item.children.isEmpty()
                val isNavigationEnabled = DesktopNavigation.isItemEnabled(item, navigationReadiness)
                val actionEnabled = item.action?.let(isNavActionEnabled) ?: true
                val isEnabled = isNavigationEnabled && actionEnabled
                val canLongClickOverride = DesktopNavigation.canLongClickOverrideDisabledMenu(item, navigationReadiness)
                val disabledReason = DesktopNavigation.disabledItemReasonWithMenuOverrideHint(item, navigationReadiness)
                    ?: item.action?.let(disabledNavActionReason)
                DisabledReasonTooltip(
                    reason = disabledReason,
                    placement = DisabledReasonTooltipPlacement.RightOfCursor
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .disabledMenuLongClickOverride(canLongClickOverride) { onItemSelected(item, true) }
                    ) {
                        Button(
                            onClick = { onItemSelected(item, false) },
                            enabled = isEnabled,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 34.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            colors = if (item.action == DesktopNavAction.SaveEventFile) {
                                saveEventButtonColors()
                            } else {
                                ButtonDefaults.buttonColors()
                            }
                        ) {
                            Text(
                                text = if (DesktopNavigation.showsMenuIndicator(item)) "${item.label} >" else item.label,
                                fontSize = 13.sp,
                                lineHeight = 15.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DisabledReasonTooltip(
                reason = disabledNavActionReason(DesktopNavAction.SaveEventFile),
                placement = DisabledReasonTooltipPlacement.RightOfCursor
            ) {
                Button(
                    onClick = onSaveEvent,
                    enabled = isNavActionEnabled(DesktopNavAction.SaveEventFile),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 34.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    colors = saveEventButtonColors()
                ) {
                    Text(
                        text = "Save Event",
                        fontSize = 13.sp,
                        lineHeight = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (DesktopNavigation.canGoBack(navState)) {
                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 34.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = DesktopPalette.SecondaryVariant,
                        contentColor = DesktopPalette.White
                    )
                ) {
                    Text(
                        text = "< Back",
                        fontSize = 13.sp,
                        lineHeight = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** Keeps the primary workflow groups visible as the desktop return-to-top path. */
@Composable
private fun WorkflowBar(
    selectedWorkflow: DesktopWorkflow,
    navigationReadiness: DesktopNavigationReadiness,
    bypassedDisabledNavigation: BypassedDisabledNavigation?,
    onWorkflowSelected: (DesktopWorkflow, Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Color(0xFFE7E7E7))
            .border(2.dp, DesktopPalette.Disconnected)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DesktopWorkflow.bottomBarEntries.forEach { workflow ->
            val isSelected = workflow == selectedWorkflow
            val isEnabled = DesktopNavigation.isWorkflowEnabled(workflow, navigationReadiness)
            val canLongClickOverride = DesktopNavigation.canLongClickOverrideDisabledWorkflow(workflow, navigationReadiness)
            val isBypassedDisabled = bypassedDisabledNavigation?.workflow == workflow
            Box(
                modifier = Modifier
                    .weight(1f)
                    .disabledMenuLongClickOverride(canLongClickOverride) { onWorkflowSelected(workflow, true) }
            ) {
                DisabledReasonTooltip(
                    DesktopNavigation.disabledWorkflowReasonWithOverrideHint(workflow, navigationReadiness)
                ) {
                    Button(
                        onClick = { onWorkflowSelected(workflow, false) },
                        enabled = isEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) DesktopPalette.Black else DesktopPalette.LightGrey
                            ),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = DesktopPalette.PrimaryVariant,
                            contentColor = DesktopPalette.White,
                            disabledBackgroundColor = if (isBypassedDisabled) {
                                DesktopPalette.Warning
                            } else {
                                DesktopPalette.LightGrey
                            },
                            disabledContentColor = if (isBypassedDisabled) {
                                DesktopPalette.Black
                            } else {
                                DesktopPalette.Disconnected
                            }
                        )
                    ) {
                        Text(
                            text = workflow.shortLabel,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

private enum class DisabledReasonTooltipPlacement {
    AboveCursor,
    RightOfCursor
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun DisabledReasonTooltip(
    reason: String?,
    placement: DisabledReasonTooltipPlacement = DisabledReasonTooltipPlacement.AboveCursor,
    content: @Composable () -> Unit
) {
    if (reason == null) {
        content()
    } else if (placement == DisabledReasonTooltipPlacement.RightOfCursor) {
        FollowCursorDisabledReasonTooltip(reason, content)
    } else {
        TooltipArea(
            tooltip = {
                DisabledReasonTooltipContent(reason)
            },
            delayMillis = 850,
            tooltipPlacement = TooltipPlacement.CursorPoint(
                offset = DpOffset(0.dp, (-12).dp),
                alignment = Alignment.BottomCenter
            )
        ) {
            content()
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun FollowCursorDisabledReasonTooltip(
    reason: String,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    var isHovering by remember { mutableStateOf(false) }
    var showTooltip by remember { mutableStateOf(false) }
    var pointerPosition by remember { mutableStateOf(Offset.Zero) }
    var hostSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(isHovering, reason) {
        showTooltip = false
        if (isHovering) {
            delay(850)
            if (isHovering) {
                showTooltip = true
            }
        }
    }

    Box(
        modifier = Modifier
            .onSizeChanged { hostSize = it }
            .onPointerEvent(PointerEventType.Enter) { event ->
                pointerPosition = event.changes.firstOrNull()?.position ?: Offset.Zero
                isHovering = true
            }
            .onPointerEvent(PointerEventType.Move) { event ->
                pointerPosition = event.changes.firstOrNull()?.position ?: pointerPosition
            }
            .onPointerEvent(PointerEventType.Exit) {
                isHovering = false
                showTooltip = false
            }
    ) {
        content()
        if (showTooltip) {
            val horizontalGap = with(density) { 12.dp.roundToPx() }
            val verticalCursorOffset = with(density) { 18.dp.roundToPx() }
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(
                    x = max(hostSize.width + horizontalGap, pointerPosition.x.roundToInt() + horizontalGap),
                    y = max(0, pointerPosition.y.roundToInt() - verticalCursorOffset)
                ),
                properties = PopupProperties(focusable = false)
            ) {
                DisabledReasonTooltipContent(reason)
            }
        }
    }
}

@Composable
private fun DisabledReasonTooltipContent(reason: String) {
    Surface(
        color = DesktopPalette.PrimaryVariant,
        contentColor = DesktopPalette.White,
        elevation = 4.dp
    ) {
        Text(
            text = reason,
            modifier = Modifier.width(280.dp).padding(8.dp),
            fontSize = 12.sp
        )
    }
}

/** Displays an Android-style empty state for the selected section. */
@Composable
private fun SectionWorkspace(
    workflow: DesktopWorkflow,
    section: DesktopSection,
    title: String,
    breadcrumb: String,
    menuDescription: String,
    projectFile: EventProjectFile?,
    eventFilePath: Path?,
    projectStatusText: String,
    siReaderState: DesktopSiReaderUiState,
    onRenameRace: (String) -> Unit,
    onUpdateRaceStartDateTime: (String) -> Unit,
    onUpdateRaceSettings: (RaceType, RaceLevel, RaceBand, String) -> Unit,
    onUpdateEventFileName: (String) -> Boolean,
    onRenameCategory: (String, String) -> Unit,
    onUpdateCategoryControlPoints: (String, String, Boolean) -> Unit,
    onUpdateCategoryPhysicalStats: (String, String, String) -> Unit,
    onAddCategory: (String) -> Boolean,
    onRemoveCategory: (String, Boolean) -> Unit,
    onRenameCompetitor: (String, String, String) -> Unit,
    onUpdateCompetitorNumbers: (String, String, String) -> Unit,
    onUpdateCompetitorClubIdentity: (String, String, String, String) -> Unit,
    onUpdateCompetitorBirthYear: (String, String) -> Unit,
    onUpdateCompetitorStartTime: (String, String) -> Unit,
    onUpdateStartDrawSettings: (String, StartDrawOptions) -> Unit,
    onDrawStartList: (String, StartDrawOptions) -> Unit,
    onDrawBalancedStartList: (String, StartDrawOptions) -> Unit,
    onAddCompetitor: (String, String, String, String, String, String, String?, String, String) -> Boolean,
    onAssignCompetitorCategory: (String, String?) -> Unit,
    onRemoveCompetitor: (String, Boolean) -> Unit,
    onRemoveReadout: (String) -> Unit,
    onUpdateReadoutStatus: (String, ResultStatus) -> Unit,
    onEditReadout: (String) -> Unit,
    onAssignUnmatchedReadout: (String, String) -> Unit,
    onDownloadSportIdentReadout: () -> Unit,
    onStartContinuousSportIdentReadout: () -> Unit,
    onStopContinuousSportIdentReadout: () -> Unit,
    onPreviewFinishTicket: (String) -> String,
    onPrintFinishTicket: (String) -> Unit,
    isDownloadingSiReadout: Boolean,
    isContinuousSiReadoutActive: Boolean,
    isReadingCompetitorSiCard: Boolean,
    siDownloadStatusText: String?,
    onAddManualReadout: (String?, String, String, String, String, ResultStatus) -> Boolean,
    onUpdateControl: (String, String, String, ControlPointType, Boolean, String, String) -> Unit,
    onAddControl: (String, String, ControlPointType, Boolean, String, String) -> Boolean,
    onRemoveControl: (String) -> Unit,
    onImportControlsRouteKmlKmz: () -> Unit,
    isSendingLiveResults: Boolean,
    isBackgroundLiveResultSendingEnabled: Boolean,
    readoutDuplicatePolicy: EventReadoutDuplicatePolicy,
    isReadoutAlertSoundEnabled: Boolean,
    areAliasesEnabled: Boolean,
    localResultServerUrl: String?,
    printerDiagnostics: DesktopPrinterDiagnostics,
    raceClockTick: Long,
    onSendRobisLiveResults: () -> Unit,
    onSetBackgroundLiveResultSendingEnabled: (Boolean) -> Unit,
    onSetReadoutDuplicatePolicy: (EventReadoutDuplicatePolicy) -> Unit,
    onSetReadoutAlertSoundEnabled: (Boolean) -> Unit,
    onSetAliasesEnabled: (Boolean) -> Unit,
    onStartLocalResultServer: () -> Unit,
    onStopLocalResultServer: () -> Unit,
    isProtectedCourseOrderUnlocked: Boolean,
    protectedIdealOrderByCategoryId: Map<String, String>,
    protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>,
    recentImportReport: DesktopImportReport?,
    recentImportCheckpoint: DesktopImportCheckpoint?,
    recentActivityLog: List<String>,
    onRetrieveMissingCourseElevations: (String) -> Unit,
    onDownloadVenueElevationCache: (String, DesktopVenueElevationBoundingBox, Double, Double, DesktopVenueElevationCacheSource, String) -> Unit,
    onOpenVenueElevationCacheFolder: () -> Unit,
    elevationCacheRefreshToken: Int,
    onUnlockProtectedCourseOrder: (String) -> Boolean,
    onUpdateProtectedIdealOrder: (String, String) -> Unit,
    onUseCalculatedCourseAnalysisRoute: (DesktopCourseCalculatedRouteApplication) -> String,
    onApplyCourseAnalysisFoxRenumberingOnly: (DesktopCourseWaitRenumbering) -> String,
    onReadCompetitorSiCardForAddRow: suspend () -> DesktopCompetitorSiCardDraft,
    onUpdateProtectedControlLocation: (String, String, String) -> String,
    onUpdateProtectedCoursePassword: (String, String, String) -> Boolean,
    isNavActionEnabled: (DesktopNavAction) -> Boolean,
    onInsertTestControls: () -> Unit,
    onInsertTestCategories: () -> Unit,
    onInsertTestCompetitors: () -> Unit,
    onInsertTestSportIdentDownloads: () -> Unit,
    onRestoreRecentImportCheckpoint: () -> Unit,
    onRecalculateResults: () -> Unit,
    onNavAction: (DesktopNavAction) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = breadcrumb,
            color = DesktopPalette.Disconnected,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = DesktopPalette.Black
        )
        Text(
            text = menuDescription,
            color = DesktopPalette.Black,
            fontSize = 14.sp
        )
        if (section == DesktopSection.WorkflowHome) {
            WorkflowHomePanel(workflow)
        }
        if (section == DesktopSection.Races && projectFile != null) {
            RaceDetailsPanel(
                details = EventRaceDetails.from(projectFile.raceData.race),
                eventFilePath = eventFilePath,
                onRenameRace = onRenameRace,
                onUpdateRaceStartDateTime = onUpdateRaceStartDateTime,
                onUpdateRaceSettings = onUpdateRaceSettings,
                onUpdateEventFileName = onUpdateEventFileName
            )
        }
        if (section == DesktopSection.Categories && projectFile != null) {
            CategoryDetailsPanel(
                categories = EventCategoryDetails.from(projectFile.raceData, useAliases = areAliasesEnabled),
                controls = EventControlDetails.from(projectFile.raceData),
                onRenameCategory = onRenameCategory,
                onUpdateCategoryControlPoints = onUpdateCategoryControlPoints,
                onUpdateCategoryPhysicalStats = onUpdateCategoryPhysicalStats,
                onAddCategory = onAddCategory,
                onRemoveCategory = onRemoveCategory
            )
        }
        if (section == DesktopSection.ProtectedCourseOrder && projectFile != null) {
            ProtectedCourseOrderPanel(
                projectFile = projectFile,
                isUnlocked = isProtectedCourseOrderUnlocked,
                idealOrderByCategoryId = protectedIdealOrderByCategoryId,
                protectedCourseInfoByCategoryId = protectedCourseInfoByCategoryId,
                onUnlock = onUnlockProtectedCourseOrder,
                onUpdateIdealOrder = onUpdateProtectedIdealOrder,
                onUpdateControlLocation = onUpdateProtectedControlLocation
            )
        }
        if (section == DesktopSection.Competitors && projectFile != null) {
            CompetitorDetailsPanel(
                competitors = EventCompetitorDetails.from(projectFile.raceData),
                categories = EventCategoryDetails.from(projectFile.raceData, useAliases = areAliasesEnabled),
                onRenameCompetitor = onRenameCompetitor,
                onUpdateCompetitorNumbers = onUpdateCompetitorNumbers,
                onUpdateCompetitorClubIdentity = onUpdateCompetitorClubIdentity,
                onUpdateCompetitorBirthYear = onUpdateCompetitorBirthYear,
                onUpdateCompetitorStartTime = onUpdateCompetitorStartTime,
                onAddCompetitor = onAddCompetitor,
                isReadCompetitorSiCardEnabled = siReaderState.severity == DesktopSiReaderSeverity.CONNECTED &&
                    !isDownloadingSiReadout &&
                    !isContinuousSiReadoutActive &&
                    !isReadingCompetitorSiCard,
                onReadCompetitorSiCardForAddRow = onReadCompetitorSiCardForAddRow,
                onAssignCompetitorCategory = onAssignCompetitorCategory,
                onRemoveCompetitor = onRemoveCompetitor
            )
        }
        if (section == DesktopSection.Controls && projectFile != null) {
            ControlDetailsPanel(
                controls = EventControlDetails.from(projectFile.raceData),
                raceType = projectFile.raceData.race.raceType,
                onUpdateControl = onUpdateControl,
                onAddControl = onAddControl,
                onRemoveControl = onRemoveControl
            )
        }
        if (section == DesktopSection.CourseAnalysis && projectFile != null) {
            CourseAnalysisPanel(
                projectFile = projectFile,
                isUnlocked = isProtectedCourseOrderUnlocked,
                protectedIdealOrderByCategoryId = protectedIdealOrderByCategoryId,
                protectedCourseInfoByCategoryId = protectedCourseInfoByCategoryId,
                onRetrieveMissingElevations = onRetrieveMissingCourseElevations,
                onUnlock = onUnlockProtectedCourseOrder,
                onUseCalculatedRoute = onUseCalculatedCourseAnalysisRoute,
                onApplyFoxRenumberingOnly = onApplyCourseAnalysisFoxRenumberingOnly
            )
        }
        if (section == DesktopSection.ElevationCache && projectFile != null) {
            VenueElevationCachePanel(
                refreshToken = elevationCacheRefreshToken,
                onOpenCacheFolder = onOpenVenueElevationCacheFolder
            )
        }
        if (section == DesktopSection.ElevationCacheImport && projectFile != null) {
            VenueElevationCacheImportPanel(
                projectFile = projectFile,
                protectedCourseInfoByCategoryId = protectedCourseInfoByCategoryId,
                onDownloadCache = onDownloadVenueElevationCache
            )
        }
        if (section == DesktopSection.ControlsRouteKmlImport && projectFile != null) {
            ControlsRouteKmlImportPanel(onSelectFile = onImportControlsRouteKmlKmz)
        }
        if (section == DesktopSection.StartList && projectFile != null) {
            StartListDetailsPanel(
                details = EventStartListDetails.from(projectFile.raceData),
                onUpdateStartDrawSettings = onUpdateStartDrawSettings,
                onDrawStartList = onDrawStartList,
                onDrawBalancedStartList = onDrawBalancedStartList
            )
        }
        if (section == DesktopSection.Readouts && projectFile != null) {
            ReadoutDetailsPanel(
                readouts = EventReadoutDetails.from(projectFile.raceData, useAliases = areAliasesEnabled),
                lastReadout = EventLastReadoutDetails.from(projectFile.raceData),
                competitors = EventCompetitorDetails.from(projectFile.raceData),
                onRemoveReadout = onRemoveReadout,
                onUpdateReadoutStatus = onUpdateReadoutStatus,
                onEditReadout = onEditReadout,
                onAssignUnmatchedReadout = onAssignUnmatchedReadout,
                onDownloadSportIdentReadout = onDownloadSportIdentReadout,
                onStartContinuousSportIdentReadout = onStartContinuousSportIdentReadout,
                onStopContinuousSportIdentReadout = onStopContinuousSportIdentReadout,
                onPreviewFinishTicket = onPreviewFinishTicket,
                onPrintFinishTicket = onPrintFinishTicket,
                isDownloadingSiReadout = isDownloadingSiReadout,
                isContinuousSiReadoutActive = isContinuousSiReadoutActive,
                siDownloadStatusText = siDownloadStatusText,
                onAddManualReadout = onAddManualReadout
            )
        }
        if (section == DesktopSection.InForest && projectFile != null) {
            InForestDetailsPanel(
                details = EventInForestDetails.from(
                    raceData = projectFile.raceData,
                    raceElapsedSeconds = desktopRaceElapsedSeconds(
                        projectFile.raceData.race.startDateTimeIso,
                        raceClockTick
                    )
                )
            )
        }
        if (section == DesktopSection.Results && projectFile != null) {
            ResultDetailsPanel(
                results = EventResultDetails.from(projectFile.raceData, useAliases = areAliasesEnabled),
                onUpdateReadoutStatus = onUpdateReadoutStatus,
                onEditReadout = onEditReadout
            )
        }
        if (section == DesktopSection.EventDiagnostics) {
            EventDiagnosticsPanel(
                diagnostics = DesktopProjectDiagnostics.from(
                    projectFile,
                    protectedCourseInfoByCategoryId.takeIf { isProtectedCourseOrderUnlocked } ?: emptyMap()
                ),
                recentImportReport = recentImportReport,
                recentImportCheckpoint = recentImportCheckpoint,
                recentActivityLog = recentActivityLog,
                onRestoreRecentImportCheckpoint = onRestoreRecentImportCheckpoint,
                onRecalculateResults = onRecalculateResults,
                onInsertTestControls = onInsertTestControls,
                onInsertTestCategories = onInsertTestCategories,
                onInsertTestCompetitors = onInsertTestCompetitors,
                onInsertTestSportIdentDownloads = onInsertTestSportIdentDownloads
            )
        }
        if (section == DesktopSection.SiReadoutSettings) {
            SiReadoutSettingsPanel(
                readoutDuplicatePolicy = readoutDuplicatePolicy,
                isReadoutAlertSoundEnabled = isReadoutAlertSoundEnabled,
                onSetReadoutDuplicatePolicy = onSetReadoutDuplicatePolicy,
                onSetReadoutAlertSoundEnabled = onSetReadoutAlertSoundEnabled,
                onInsertTestSportIdentDownloads = onInsertTestSportIdentDownloads,
                isEventFileOpen = projectFile != null
            )
        }
        if (section == DesktopSection.LiveResultSettings) {
            LiveResultSettingsPanel(
                diagnostics = DesktopProjectDiagnostics.from(
                    projectFile,
                    protectedCourseInfoByCategoryId.takeIf { isProtectedCourseOrderUnlocked } ?: emptyMap()
                ),
                isSendingLiveResults = isSendingLiveResults,
                isBackgroundLiveResultSendingEnabled = isBackgroundLiveResultSendingEnabled,
                localResultServerUrl = localResultServerUrl,
                onSendRobisLiveResults = onSendRobisLiveResults,
                onSetBackgroundLiveResultSendingEnabled = onSetBackgroundLiveResultSendingEnabled,
                onStartLocalResultServer = onStartLocalResultServer,
                onStopLocalResultServer = onStopLocalResultServer
            )
        }
        if (section == DesktopSection.DisplaySettings) {
            DisplaySettingsPanel(
                areAliasesEnabled = areAliasesEnabled,
                onSetAliasesEnabled = onSetAliasesEnabled,
                isEventFileOpen = projectFile != null
            )
        }
        if (section == DesktopSection.Settings) {
            AppSettingsPanel(
                projectFile = projectFile,
                diagnostics = DesktopProjectDiagnostics.from(
                    projectFile,
                    protectedCourseInfoByCategoryId.takeIf { isProtectedCourseOrderUnlocked } ?: emptyMap()
                ),
                printerDiagnostics = printerDiagnostics,
                isCourseDataUnlocked = isProtectedCourseOrderUnlocked,
                onUpdateCoursePassword = onUpdateProtectedCoursePassword
            )
        }
        if (section != DesktopSection.WorkflowHome) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(DesktopPalette.LightGrey)
            )
            Text(
                text = if (projectFile != null) {
                    "${DesktopDateTimeText.displayIsoOrRaw(projectFile.raceData.race.startDateTimeIso)} - $projectStatusText"
                } else {
                    projectStatusText
                },
                color = DesktopPalette.Disconnected,
                fontSize = 13.sp
            )
        }
    }
}

/** Shows event readiness, read-only diagnostics, recent imports, and desktop test tools. */
@Composable
private fun EventDiagnosticsPanel(
    diagnostics: DesktopProjectDiagnostics,
    recentImportReport: DesktopImportReport?,
    recentImportCheckpoint: DesktopImportCheckpoint?,
    recentActivityLog: List<String>,
    onRestoreRecentImportCheckpoint: () -> Unit,
    onRecalculateResults: () -> Unit,
    onInsertTestControls: () -> Unit,
    onInsertTestCategories: () -> Unit,
    onInsertTestCompetitors: () -> Unit,
    onInsertTestSportIdentDownloads: () -> Unit
) {
    val isEventFileOpen = diagnostics.projectState == "Event File open"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DetailRow("Event File", diagnostics.projectState)
        DetailRow("Schema", diagnostics.schemaText.ifBlank { "None" })
        DetailRow("Race ID", diagnostics.raceId.ifBlank { "None" })
        DetailRow("Event name", diagnostics.raceName.ifBlank { "None" })
        DetailRow(
            "Start",
            diagnostics.startDateTimeIso.takeIf { it.isNotBlank() }
                ?.let(DesktopDateTimeText::displayIsoOrRaw)
                ?: "None"
        )
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
        if (diagnostics.resultCount > 0 || diagnostics.readoutCount > 0) {
            Button(onClick = onRecalculateResults) {
                ButtonLabel("Recalculate Results")
            }
            Text(
                text = "Re-evaluates stored readouts against the current controls, categories, and course assignments. Changed results are marked unsent for reposting.",
                color = Color.DarkGray,
                fontSize = 12.sp
            )
        }
        Text(
            text = "Event Readiness",
            color = DesktopPalette.PrimaryVariant,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
        if (diagnostics.readinessIssues.isEmpty()) {
            Text(
                text = "No readiness issues detected.",
                color = DesktopPalette.Disconnected,
                fontSize = 13.sp
            )
        } else {
            diagnostics.readinessIssues.forEach { issue ->
                Text(
                    text = issue,
                    color = DesktopPalette.Warning,
                    fontSize = 13.sp
                )
            }
        }
        recentImportReport?.let { report ->
            Text(
                text = "Recent Import",
                color = DesktopPalette.PrimaryVariant,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Text(
                text = report.title,
                color = DesktopPalette.Disconnected,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            report.lines.filter { it.isNotBlank() }.forEach { line ->
                Text(
                    text = line,
                    color = DesktopPalette.Disconnected,
                    fontSize = 13.sp
                )
            }
            if (recentImportCheckpoint != null) {
                Button(onClick = onRestoreRecentImportCheckpoint) {
                    ButtonLabel("Restore Before Import")
                }
                Text(
                    text = "Restores the in-memory Event File state captured before ${recentImportCheckpoint.title}. A persistent .rom.json rollback copy was also saved at ${recentImportCheckpoint.backupPath}. Save after restore if you want to keep the in-app rollback.",
                    color = Color.DarkGray,
                    fontSize = 12.sp
                )
            }
        }
        if (recentActivityLog.isNotEmpty()) {
            Text(
                text = "Recent Activity",
                color = DesktopPalette.PrimaryVariant,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            recentActivityLog.forEach { line ->
                Text(
                    text = line,
                    color = DesktopPalette.Disconnected,
                    fontSize = 13.sp
                )
            }
        }
        Text(
            text = "Generated test data is inserted in stages. Categories include course assignments, competitors include test SI numbers and drawn start times, and test SI downloads use those competitors and assigned categories.",
            color = DesktopPalette.Disconnected,
            fontSize = 13.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onInsertTestControls,
                enabled = isEventFileOpen
            ) {
                ButtonLabel("Insert Test Controls")
            }
            Button(
                onClick = onInsertTestCategories,
                enabled = isEventFileOpen
            ) {
                ButtonLabel("Insert Test Categories")
            }
            Button(
                onClick = onInsertTestCompetitors,
                enabled = isEventFileOpen
            ) {
                ButtonLabel("Insert Test Competitors")
            }
            Button(
                onClick = onInsertTestSportIdentDownloads,
                enabled = isEventFileOpen
            ) {
                ButtonLabel("Insert Test SI Downloads")
            }
        }
        diagnostics.validationIssues.forEach { issue ->
            Text(
                text = issue,
                color = DesktopPalette.Error,
                fontSize = 13.sp
            )
        }
    }
}

/** Shows SI-card readout behavior settings used by Race Ops. */
@Composable
private fun SiReadoutSettingsPanel(
    readoutDuplicatePolicy: EventReadoutDuplicatePolicy,
    isReadoutAlertSoundEnabled: Boolean,
    onSetReadoutDuplicatePolicy: (EventReadoutDuplicatePolicy) -> Unit,
    onSetReadoutAlertSoundEnabled: (Boolean) -> Unit,
    onInsertTestSportIdentDownloads: () -> Unit,
    isEventFileOpen: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DetailRow("Duplicate SI cards", readoutDuplicatePolicy.toDisplayLabel())
        ReadoutDuplicatePolicyPicker(
            selectedPolicy = readoutDuplicatePolicy,
            onPolicySelected = onSetReadoutDuplicatePolicy
        )
        Button(
            onClick = onInsertTestSportIdentDownloads,
            enabled = isEventFileOpen
        ) {
            ButtonLabel("Insert Test SI Downloads")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = isReadoutAlertSoundEnabled,
                onCheckedChange = onSetReadoutAlertSoundEnabled
            )
            Text(
                text = "Readout alert sounds",
                color = DesktopPalette.Black,
                fontSize = 13.sp
            )
        }
    }
}

/** Shows live result display and ROBIS result-sending settings. */
@Composable
private fun LiveResultSettingsPanel(
    diagnostics: DesktopProjectDiagnostics,
    isSendingLiveResults: Boolean,
    isBackgroundLiveResultSendingEnabled: Boolean,
    localResultServerUrl: String?,
    onSendRobisLiveResults: () -> Unit,
    onSetBackgroundLiveResultSendingEnabled: (Boolean) -> Unit,
    onStartLocalResultServer: () -> Unit,
    onStopLocalResultServer: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DetailRow("Local result display", localResultServerUrl ?: "Stopped")
        Row(horizontalArrangement = Arrangement.spacedBy(TableColumnGap)) {
            Button(
                onClick = onStartLocalResultServer,
                enabled = diagnostics.projectState == "Event File open" && localResultServerUrl == null
            ) {
                ButtonLabel("Start Display")
            }
            Button(
                onClick = onStopLocalResultServer,
                enabled = localResultServerUrl != null
            ) {
                ButtonLabel("Stop Display")
            }
        }
        DetailRow("Live results", diagnostics.liveResultPlanText)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = isBackgroundLiveResultSendingEnabled,
                onCheckedChange = onSetBackgroundLiveResultSendingEnabled,
                enabled = diagnostics.projectState == "Event File open"
            )
            Text(
                text = "Background ROBIS sending",
                color = DesktopPalette.Black,
                fontSize = 13.sp
            )
        }
        Button(
            onClick = onSendRobisLiveResults,
            enabled = diagnostics.projectState == "Event File open" && !isSendingLiveResults
        ) {
            ButtonLabel(if (isSendingLiveResults) "Sending" else "Send ROBIS")
        }
    }
}

/** Shows display choices that affect readout and result presentation. */
@Composable
private fun DisplaySettingsPanel(
    areAliasesEnabled: Boolean,
    onSetAliasesEnabled: (Boolean) -> Unit,
    isEventFileOpen: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = areAliasesEnabled,
                onCheckedChange = onSetAliasesEnabled,
                enabled = isEventFileOpen
            )
            Text(
                text = "Use control labels",
                color = DesktopPalette.Black,
                fontSize = 13.sp
            )
        }
    }
}

/** Shows application-level status and desktop-beta scope. */
@Composable
private fun AppSettingsPanel(
    projectFile: EventProjectFile?,
    diagnostics: DesktopProjectDiagnostics,
    printerDiagnostics: DesktopPrinterDiagnostics,
    isCourseDataUnlocked: Boolean,
    onUpdateCoursePassword: (String, String, String) -> Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppSettingsSection("Printer information") {
            DetailRow("Printer", printerDiagnostics.readinessText)
            DetailRow(
                "Detected printers",
                printerDiagnostics.detectedPrinterNames.joinToString().ifBlank { "None" }
            )
        }
        AppSettingsSection(if (projectFile?.hasCoursePasswordSet() == true) "Reset Event Password" else "Set Event Password") {
            CoursePasswordSettingsPanel(
                projectFile = projectFile,
                isCourseDataUnlocked = isCourseDataUnlocked,
                onUpdateCoursePassword = onUpdateCoursePassword
            )
        }
        AppSettingsSection("Desktop beta scope") {
            diagnostics.betaLimitations.forEach { limitation ->
                Text(
                    text = limitation,
                    color = DesktopPalette.Black,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun AppSettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DesktopPalette.LightGrey)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            color = DesktopPalette.Disconnected,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        content()
    }
}

@Composable
private fun CoursePasswordSettingsPanel(
    projectFile: EventProjectFile?,
    isCourseDataUnlocked: Boolean,
    onUpdateCoursePassword: (String, String, String) -> Boolean
) {
    val hasCoursePassword = remember(projectFile) { projectFile?.hasCoursePasswordSet() == true }
    var oldPasswordDraft by remember(projectFile?.raceData?.race?.id, hasCoursePassword) { mutableStateOf("") }
    var newPasswordDraft by remember(projectFile?.raceData?.race?.id, hasCoursePassword) { mutableStateOf("") }
    var confirmPasswordDraft by remember(projectFile?.raceData?.race?.id, hasCoursePassword) { mutableStateOf("") }
    val canSubmit = newPasswordDraft.isNotBlank() &&
        confirmPasswordDraft.isNotBlank() &&
        (!hasCoursePassword || oldPasswordDraft.isNotBlank())

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = when {
                projectFile == null ->
                    "Open or create an Event File before setting an Event Password."
                hasCoursePassword ->
                    "Resetting the Event Password requires the current Event Password. Sensitive Event File data is ${if (isCourseDataUnlocked) "currently unlocked" else "currently locked"}."
                else ->
                    "Set an Event Password before importing route data or editing stored course order. Accessing sensitive data can still create an Event Password when none exists."
            },
            color = DesktopPalette.Black,
            fontSize = 13.sp
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasCoursePassword) {
                TextField(
                    value = oldPasswordDraft,
                    onValueChange = { oldPasswordDraft = it },
                    label = { Text("Current Event Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.width(190.dp)
                )
            }
            TextField(
                value = newPasswordDraft,
                onValueChange = { newPasswordDraft = it },
                label = { Text(if (hasCoursePassword) "New Event Password" else "Event Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                enabled = projectFile != null,
                modifier = Modifier.width(190.dp)
            )
            TextField(
                value = confirmPasswordDraft,
                onValueChange = { confirmPasswordDraft = it },
                label = { Text("Confirm") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                enabled = projectFile != null,
                modifier = Modifier.width(190.dp)
            )
            DisabledReasonTooltip(coursePasswordSubmitDisabledReason(projectFile, hasCoursePassword, oldPasswordDraft, newPasswordDraft, confirmPasswordDraft)) {
                Button(
                    onClick = {
                        if (onUpdateCoursePassword(oldPasswordDraft, newPasswordDraft, confirmPasswordDraft)) {
                            oldPasswordDraft = ""
                            newPasswordDraft = ""
                            confirmPasswordDraft = ""
                        }
                    },
                    enabled = projectFile != null && canSubmit
                ) {
                    ButtonLabel(if (hasCoursePassword) "Reset Event Password" else "Set Event Password")
                }
            }
        }
    }
}

private fun EventProjectFile.hasCoursePasswordSet(): Boolean =
    raceData.categories.any { categoryData ->
        !categoryData.category.encryptedIdealOrder.isNullOrBlank() ||
            !categoryData.category.encryptedCourseInfo.isNullOrBlank()
    }

private fun coursePasswordSubmitDisabledReason(
    projectFile: EventProjectFile?,
    hasCoursePassword: Boolean,
    oldPassword: String,
    newPassword: String,
    confirmPassword: String
): String? =
    when {
        projectFile == null -> "Open or create an Event File before setting an Event Password."
        hasCoursePassword && oldPassword.isBlank() -> "Enter the current Event Password before resetting it."
        newPassword.isBlank() -> "Enter a new Event Password."
        confirmPassword.isBlank() -> "Confirm the new Event Password."
        else -> null
    }

@Composable
private fun ReadoutDuplicatePolicyPicker(
    selectedPolicy: EventReadoutDuplicatePolicy,
    onPolicySelected: (EventReadoutDuplicatePolicy) -> Unit
) {
    EnumPicker(
        selectedValue = selectedPolicy,
        values = EventReadoutDuplicatePolicy.entries,
        label = EventReadoutDuplicatePolicy::toDisplayLabel,
        onValueSelected = onPolicySelected,
        modifier = Modifier.width(240.dp)
    )
}

/** Shows read-only competitor result rows. */
@Composable
private fun ResultDetailsPanel(
    results: List<EventResultDetails>,
    onUpdateReadoutStatus: (String, ResultStatus) -> Unit,
    onEditReadout: (String) -> Unit
) {
    val horizontalScrollState = rememberScrollState()
    val tableWidth = fixedTableWidth(ResultTableColumns)
    val groupedResults = results.groupBy { it.categoryId to it.categoryName }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.fillMaxWidth().horizontalScroll(horizontalScrollState)) {
            Column(
                modifier = Modifier.width(tableWidth),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                groupedResults.forEach { (category, categoryResults) ->
                    ResultCategoryHeader(category.second, categoryResults.size, tableWidth)
                    FixedDetailHeaderRow(ResultTableColumns)
                    categoryResults.forEach { result ->
                        ResultDetailRow(result, onUpdateReadoutStatus, onEditReadout)
                    }
                }
            }
        }
        HorizontalScrollbar(
            adapter = rememberScrollbarAdapter(horizontalScrollState),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Shows the competition category for the result rows that follow. */
@Composable
private fun ResultCategoryHeader(
    categoryName: String,
    resultCount: Int,
    tableWidth: Dp
) {
    Text(
        text = "$categoryName ($resultCount)",
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .width(tableWidth)
            .background(MaterialTheme.colors.onSurface.copy(alpha = 0.06f))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    )
}

/** Shows one ranked result row with explicit manual status editing. */
@Composable
private fun ResultDetailRow(
    result: EventResultDetails,
    onUpdateReadoutStatus: (String, ResultStatus) -> Unit,
    onEditReadout: (String) -> Unit
) {
    Row(
        modifier = Modifier.width(fixedTableWidth(ResultTableColumns)),
        horizontalArrangement = Arrangement.spacedBy(TableColumnGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FixedTableText(result.placeText, ResultTableColumns[0].width)
        FixedTableText(result.competitorName, ResultTableColumns[1].width)
        ResultStatusPicker(
            selectedStatus = result.resultStatus,
            onStatusSelected = { status ->
                if (status != result.resultStatus) {
                    onUpdateReadoutStatus(result.id, status)
                }
            },
            modifier = Modifier.width(ResultTableColumns[2].width)
        )
        FixedTableText(result.pointsText, ResultTableColumns[3].width)
        FixedTableText(result.runTimeText, ResultTableColumns[4].width)
        FixedTableText(result.punchCodesText, ResultTableColumns[5].width)
        Button(
            onClick = { onEditReadout(result.id) },
            modifier = Modifier.width(ResultTableColumns[6].width)
        ) {
            ButtonLabel("Edit")
        }
    }
}

/** Shows competitors sorted by drawn start time for race-day start-list inspection. */
@Composable
private fun StartListDetailsPanel(
    details: EventStartListDetails,
    onUpdateStartDrawSettings: (String, StartDrawOptions) -> Unit,
    onDrawStartList: (String, StartDrawOptions) -> Unit,
    onDrawBalancedStartList: (String, StartDrawOptions) -> Unit
) {
    val horizontalScrollState = rememberScrollState()
    val tableWidth = fixedTableWidth(StartListTableColumns)
    val settings = details.settings
    var intervalDraft by remember(settings.intervalSeconds) { mutableStateOf(settings.intervalText) }
    var clubHandling by remember(settings.options.clubHandling) { mutableStateOf(settings.options.clubHandling) }
    var startersPerStartTime by remember(settings.options.startersPerStartTime) {
        mutableStateOf(settings.options.startersPerStartTime)
    }
    var seedDraft by remember(settings.options.seed) { mutableStateOf(settings.options.seed) }
    var startGroupMode by remember(settings.options.startGroupMode) { mutableStateOf(settings.options.startGroupMode) }
    fun startDrawOptions(
        clubHandlingValue: StartDrawClubHandling = clubHandling,
        startersPerStartTimeValue: Int = startersPerStartTime,
        seedValue: String = seedDraft,
        startGroupModeValue: StartDrawStartGroupMode = startGroupMode
    ): StartDrawOptions =
        StartDrawOptions(
            clubHandling = clubHandlingValue,
            startersPerStartTime = startersPerStartTimeValue,
            seed = seedValue,
            startGroupMode = startGroupModeValue
        )
    fun persistSettingsIfIntervalIsValid(intervalValue: String, options: StartDrawOptions) {
        if (isValidStartListInterval(intervalValue)) {
            onUpdateStartDrawSettings(intervalValue, options)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = intervalDraft,
                onValueChange = {
                    intervalDraft = it
                    persistSettingsIfIntervalIsValid(it, startDrawOptions())
                },
                label = { Text("Interval") },
                modifier = Modifier.width(132.dp)
            )
            EnumPicker(
                selectedValue = clubHandling,
                values = StartDrawClubHandling.entries,
                label = StartDrawClubHandling::toDisplayLabel,
                onValueSelected = {
                    clubHandling = it
                    persistSettingsIfIntervalIsValid(intervalDraft, startDrawOptions(clubHandlingValue = it))
                },
                modifier = Modifier.width(190.dp)
            )
            EnumPicker(
                selectedValue = startersPerStartTime,
                values = (StartDrawOptions.MIN_STARTERS_PER_START_TIME..StartDrawOptions.MAX_STARTERS_PER_START_TIME).toList(),
                label = { "$it per time" },
                onValueSelected = {
                    startersPerStartTime = it
                    persistSettingsIfIntervalIsValid(intervalDraft, startDrawOptions(startersPerStartTimeValue = it))
                },
                modifier = Modifier.width(132.dp)
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = seedDraft,
                onValueChange = {
                    seedDraft = it
                    if (it.isNotBlank()) {
                        persistSettingsIfIntervalIsValid(intervalDraft, startDrawOptions(seedValue = it))
                    }
                },
                label = { Text("Seed") },
                modifier = Modifier.width(180.dp)
            )
            EnumPicker(
                selectedValue = startGroupMode,
                values = listOf(StartDrawStartGroupMode.DISABLED, StartDrawStartGroupMode.PREFERRED_THIRDS),
                label = StartDrawStartGroupMode::toDisplayLabel,
                onValueSelected = {
                    startGroupMode = it
                    persistSettingsIfIntervalIsValid(intervalDraft, startDrawOptions(startGroupModeValue = it))
                },
                modifier = Modifier.width(190.dp)
            )
            Button(
                onClick = {
                    onDrawBalancedStartList(
                        intervalDraft,
                        startDrawOptions(startGroupModeValue = StartDrawStartGroupMode.BALANCED_MULTI_DAY_THIRDS)
                    )
                },
                enabled = startGroupMode != StartDrawStartGroupMode.PREFERRED_THIRDS
            ) {
                Text("Balance from CSVs")
            }
        }
        Row(
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    onDrawStartList(
                        intervalDraft,
                        startDrawOptions()
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = DesktopPalette.Connected,
                    contentColor = DesktopPalette.Black
                )
            ) {
                Text("Generate Start List")
            }
        }
        DetailHeaderRow(listOf("Scheduled", "No start time"))
        DetailGridRow(listOf(details.scheduledCount.toString(), details.unscheduledCount.toString()))
        Text(
            text = "Score ${details.quality.score}/100 - ${details.quality.summary}",
            color = details.quality.severity.toStartListColor(),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        details.quality.messages.take(3).forEach { message ->
            Text(
                text = message,
                color = details.quality.severity.toStartListColor(),
                fontSize = 12.sp
            )
        }
        if (details.rows.isEmpty()) {
            Text(
                text = "No competitors are loaded.",
                color = DesktopPalette.Black,
                fontSize = 13.sp
            )
        } else {
            Box(modifier = Modifier.fillMaxWidth().horizontalScroll(horizontalScrollState)) {
                Column(
                    modifier = Modifier.width(tableWidth),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FixedDetailHeaderRow(StartListTableColumns)
                    details.rows.forEach { row ->
                        StartListDetailRow(row)
                    }
                }
            }
            HorizontalScrollbar(
                adapter = rememberScrollbarAdapter(horizontalScrollState),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun StartListDetailRow(row: EventStartListRow) {
    Row(
        modifier = Modifier.width(fixedTableWidth(StartListTableColumns)),
        horizontalArrangement = Arrangement.spacedBy(TableColumnGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val rowColor = row.ruleSeverity.toStartListColor()
        FixedTableText(row.startTimeText, StartListTableColumns[0].width, rowColor)
        FixedTableText(row.startNumberText, StartListTableColumns[1].width, rowColor)
        FixedTableText(row.competitorName, StartListTableColumns[2].width, rowColor)
        FixedTableText(row.categoryName, StartListTableColumns[3].width, rowColor)
        FixedTableText(row.siNumberText, StartListTableColumns[4].width, rowColor)
    }
}

/** Shows runners who have started but do not yet have a readout. */
@Composable
private fun InForestDetailsPanel(details: EventInForestDetails) {
    val horizontalScrollState = rememberScrollState()
    val tableWidth = fixedTableWidth(InForestTableColumns)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DetailHeaderRow(listOf("In forest", "Finished", "Not started", "No start time"))
        DetailGridRow(
            listOf(
                details.inForestCount.toString(),
                details.finishedCount.toString(),
                details.notStartedCount.toString(),
                details.unscheduledCount.toString()
            )
        )
        if (details.inForestRows.isEmpty()) {
            Text(
                text = "No started competitors are waiting for readout.",
                color = DesktopPalette.Black,
                fontSize = 13.sp
            )
        } else {
            Box(modifier = Modifier.fillMaxWidth().horizontalScroll(horizontalScrollState)) {
                Column(
                    modifier = Modifier.width(tableWidth),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FixedDetailHeaderRow(InForestTableColumns)
                    details.inForestRows.forEach { row ->
                        InForestDetailRow(row)
                    }
                }
            }
            HorizontalScrollbar(
                adapter = rememberScrollbarAdapter(horizontalScrollState),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun InForestDetailRow(row: org.openardf.radiooracle.shared.event.EventInForestRow) {
    Row(
        modifier = Modifier.width(fixedTableWidth(InForestTableColumns)),
        horizontalArrangement = Arrangement.spacedBy(TableColumnGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FixedTableText(row.competitorName, InForestTableColumns[0].width)
        FixedTableText(row.categoryName, InForestTableColumns[1].width)
        FixedTableText(row.startTimeText, InForestTableColumns[2].width)
        FixedTableText(row.elapsedText, InForestTableColumns[3].width)
        FixedTableText(row.limitText, InForestTableColumns[4].width)
        Text(
            text = if (row.overLimit) "Over limit" else "Running",
            modifier = Modifier.width(InForestTableColumns[5].width),
            color = if (row.overLimit) DesktopPalette.Error else DesktopPalette.Black,
            fontSize = 13.sp
        )
    }
}

/** Shows read-only matched and unmatched SI-card readout rows. */
@Composable
private fun ReadoutDetailsPanel(
    readouts: List<EventReadoutDetails>,
    lastReadout: EventLastReadoutDetails,
    competitors: List<EventCompetitorDetails>,
    onRemoveReadout: (String) -> Unit,
    onUpdateReadoutStatus: (String, ResultStatus) -> Unit,
    onEditReadout: (String) -> Unit,
    onAssignUnmatchedReadout: (String, String) -> Unit,
    onDownloadSportIdentReadout: () -> Unit,
    onStartContinuousSportIdentReadout: () -> Unit,
    onStopContinuousSportIdentReadout: () -> Unit,
    onPreviewFinishTicket: (String) -> String,
    onPrintFinishTicket: (String) -> Unit,
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
    var ticketPreviewResultId by remember { mutableStateOf<String?>(null) }
    val competitorsWithoutReadouts = competitors.filterNot { it.hasReadout }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LastReadoutStatusPanel(lastReadout)
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
                        competitors = competitorsWithoutReadouts,
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
                            competitors = competitorsWithoutReadouts,
                            onUpdateReadoutStatus = onUpdateReadoutStatus,
                            onEditReadout = onEditReadout,
                            onAssignUnmatchedReadout = onAssignUnmatchedReadout,
                            onPreviewFinishTicket = {
                                ticketPreviewResultId = readout.id
                                ticketPreviewText = onPreviewFinishTicket(readout.id)
                            }
                        )
                    }
                }
            }
        }
        ticketPreviewText?.let { previewText ->
            FinishTicketPreviewDialog(
                text = previewText,
                onPrint = {
                    ticketPreviewResultId?.let(onPrintFinishTicket)
                },
                onDismiss = {
                    ticketPreviewText = null
                    ticketPreviewResultId = null
                }
            )
        }
    }
}

@Composable
private fun LastReadoutStatusPanel(lastReadout: EventLastReadoutDetails) {
    val statusColor = when (lastReadout.severity) {
        EventLastReadoutSeverity.None -> DesktopPalette.Black
        EventLastReadoutSeverity.Normal -> DesktopPalette.Connected
        EventLastReadoutSeverity.Warning -> DesktopPalette.Warning
        EventLastReadoutSeverity.Error -> DesktopPalette.Error
    }

    DetailHeaderRow(listOf("Last SI", "Competitor", "Status", "Read at"))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (lastReadout.hasReadout) {
            DetailValue(lastReadout.siNumberText.ifBlank { "None" }, Modifier.weight(1f), color = statusColor)
            DetailValue(lastReadout.competitorName.ifBlank { "Unmatched" }, Modifier.weight(1f), color = statusColor)
            DetailValue(lastReadout.statusLabel, Modifier.weight(1f), color = statusColor)
            DetailValue(lastReadout.readoutDateTimeIso, Modifier.weight(1f), color = statusColor)
        } else {
            DetailValue("None", Modifier.weight(1f), color = statusColor)
            DetailValue("None", Modifier.weight(1f), color = statusColor)
            DetailValue("None", Modifier.weight(1f), color = statusColor)
            DetailValue("None", Modifier.weight(1f), color = statusColor)
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
        Spacer(modifier = Modifier.width(ReadoutTableColumns[8].width))
        Spacer(modifier = Modifier.width(ReadoutTableColumns[9].width))
    }
}

/** Shows one readout row with deletion routed through shared Event File editing rules. */
@Composable
private fun ReadoutDetailRow(
    readout: EventReadoutDetails,
    competitors: List<EventCompetitorDetails>,
    onUpdateReadoutStatus: (String, ResultStatus) -> Unit,
    onEditReadout: (String) -> Unit,
    onAssignUnmatchedReadout: (String, String) -> Unit,
    onPreviewFinishTicket: () -> Unit
) {
    var selectedCompetitorId by remember(readout.id) { mutableStateOf<String?>(null) }

    Row(
        modifier = Modifier.width(fixedTableWidth(ReadoutTableColumns)),
        horizontalArrangement = Arrangement.spacedBy(TableColumnGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FixedTableText(readout.siNumberText, ReadoutTableColumns[0].width)
        FixedTableText(readout.competitorName, ReadoutTableColumns[1].width)
        ResultStatusPicker(
            selectedStatus = readout.resultStatus,
            onStatusSelected = { status ->
                if (status != readout.resultStatus) {
                    onUpdateReadoutStatus(readout.id, status)
                }
            },
            modifier = Modifier.width(ReadoutTableColumns[2].width)
        )
        FixedTableText(readout.pointsText, ReadoutTableColumns[3].width)
        FixedTableText(readout.runTimeText, ReadoutTableColumns[4].width)
        FixedTableText(readout.punchCodesText, ReadoutTableColumns[5].width)
        if (readout.matched) {
            FixedTableText("", ReadoutTableColumns[6].width)
            Spacer(modifier = Modifier.width(ReadoutTableColumns[7].width))
        } else {
            CompetitorPicker(
                selectedCompetitorId = selectedCompetitorId,
                competitors = competitors,
                onCompetitorSelected = { selectedCompetitorId = it },
                modifier = Modifier.width(ReadoutTableColumns[6].width)
            )
            Button(
                onClick = {
                    selectedCompetitorId?.let { competitorId ->
                        onAssignUnmatchedReadout(readout.id, competitorId)
                    }
                },
                modifier = Modifier.width(ReadoutTableColumns[7].width),
                enabled = selectedCompetitorId != null
            ) {
                ButtonLabel("Assign")
            }
        }
        Button(
            onClick = { onEditReadout(readout.id) },
            modifier = Modifier.width(ReadoutTableColumns[8].width)
        ) {
            ButtonLabel("Edit")
        }
        Button(
            onClick = onPreviewFinishTicket,
            modifier = Modifier.width(ReadoutTableColumns[9].width)
        ) {
            ButtonLabel("Ticket")
        }
    }
}

@Composable
private fun FinishTicketPreviewDialog(
    text: String,
    onPrint: () -> Unit,
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
        dismissButton = {
            Button(onClick = onPrint) {
                Text("Print")
            }
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

@Composable
private fun ReadoutEditDialog(
    draft: DesktopReadoutEditDraft,
    categories: List<EventCategoryDetails>,
    controls: List<EventControl>,
    onSave: (DesktopReadoutEditDraft) -> Unit,
    onCancel: () -> Unit
) {
    var startSeconds by remember(draft.resultId) { mutableStateOf(draft.startSeconds) }
    var finishSeconds by remember(draft.resultId) { mutableStateOf(draft.finishSeconds) }
    var controlPunchesText by remember(draft.resultId) { mutableStateOf(draft.controlPunchesText) }
    var resultStatus by remember(draft.resultId) { mutableStateOf(draft.resultStatus) }
    var categoryId by remember(draft.resultId) { mutableStateOf(draft.categoryId) }
    var updateCompetitorCategory by remember(draft.resultId) { mutableStateOf(draft.updateCompetitorCategory) }
    var selectedControlId by remember(draft.resultId, controls) { mutableStateOf(controls.firstOrNull()?.id) }
    val categoryChanged = draft.matched && categoryId != draft.originalCategoryId
    val sortedControls = remember(controls) {
        controls.sortedWith(compareBy<EventControl> { it.siCode }.thenBy { it.publicDisplayLabel() })
    }
    val usedPunchControlKeys = remember(controlPunchesText) { controlPunchesText.usedPunchControlKeys() }
    val availableControls = remember(sortedControls, usedPunchControlKeys) {
        sortedControls.filter { control ->
            control.entryKeys().none { it in usedPunchControlKeys }
        }
    }

    LaunchedEffect(availableControls, selectedControlId) {
        if (selectedControlId == null || availableControls.none { it.id == selectedControlId }) {
            selectedControlId = availableControls.firstOrNull()?.id
        }
    }

    LaunchedEffect(categoryChanged) {
        if (!categoryChanged) {
            updateCompetitorCategory = false
        }
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Edit Result") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = draft.competitorName.ifBlank { "Unmatched readout" },
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabeledTextField(
                        label = "Start elapsed",
                        value = startSeconds,
                        onValueChange = {
                            if (!draft.isPractice) {
                                startSeconds = it
                            }
                        },
                        modifier = Modifier.width(160.dp),
                        enabled = !draft.isPractice
                    )
                    LabeledTextField(
                        label = "Finish elapsed",
                        value = finishSeconds,
                        onValueChange = { finishSeconds = it },
                        modifier = Modifier.width(160.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Control punches", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        ReadoutControlPicker(
                            selectedControlId = selectedControlId,
                            controls = availableControls,
                            onControlSelected = { selectedControlId = it },
                            modifier = Modifier.width(260.dp)
                        )
                        Button(
                            onClick = {
                                val control = availableControls.firstOrNull { it.id == selectedControlId } ?: return@Button
                                val punchTime = startSeconds.ifBlank { "00:00" }
                                controlPunchesText = appendReadoutPunchLine(
                                    controlPunchesText,
                                    "${control.editPunchToken(sortedControls)} @ $punchTime"
                                )
                                selectedControlId = availableControls.firstOrNull { it.id != control.id }?.id
                            },
                            enabled = selectedControlId != null
                        ) {
                            ButtonLabel("Add Punch")
                        }
                    }
                    TextField(
                        value = controlPunchesText,
                        onValueChange = { controlPunchesText = it },
                        singleLine = false,
                        modifier = Modifier.width(420.dp).height(88.dp)
                    )
                    Text(
                        text = "Use one punch per line, such as Fox 1 @ 15:00. The picker inserts valid labels; typed SI codes, public labels, and control labels are also accepted.",
                        color = DesktopPalette.Disconnected,
                        fontSize = 12.sp
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ResultStatusPicker(
                        selectedStatus = resultStatus,
                        onStatusSelected = { resultStatus = it },
                        modifier = Modifier.width(160.dp)
                    )
                    CategoryPicker(
                        selectedCategoryId = categoryId,
                        categories = categories,
                        onCategorySelected = { categoryId = it },
                        modifier = Modifier.width(220.dp)
                    )
                }
                if (draft.matched) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = categoryChanged && updateCompetitorCategory,
                            onCheckedChange = { updateCompetitorCategory = it },
                            enabled = categoryChanged
                        )
                        Text(
                            "Also change competitor category",
                            color = if (categoryChanged) DesktopPalette.Black else DesktopPalette.Disconnected
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        draft.copy(
                            startSeconds = startSeconds,
                            finishSeconds = finishSeconds,
                            controlPunchesText = controlPunchesText,
                            resultStatus = resultStatus,
                            categoryId = categoryId,
                            updateCompetitorCategory = updateCompetitorCategory
                        )
                    )
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Button(onClick = onCancel) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ReadoutControlPicker(
    selectedControlId: String?,
    controls: List<EventControl>,
    onControlSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedControl = controls.firstOrNull { it.id == selectedControlId }
    val selectedText = selectedControl?.publicDisplayLabel() ?: "No controls available"

    Box(modifier = modifier) {
        Button(
            onClick = { expanded = true },
            enabled = controls.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(selectedText)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            controls.forEach { control ->
                DropdownMenuItem(
                    onClick = {
                        expanded = false
                        onControlSelected(control.id)
                    }
                ) {
                    Text("${control.publicDisplayLabel()} (${control.siCode})")
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
    onUpdateCompetitorClubIdentity: (String, String, String, String) -> Unit,
    onUpdateCompetitorBirthYear: (String, String) -> Unit,
    onUpdateCompetitorStartTime: (String, String) -> Unit,
    onAddCompetitor: (String, String, String, String, String, String, String?, String, String) -> Boolean,
    isReadCompetitorSiCardEnabled: Boolean,
    onReadCompetitorSiCardForAddRow: suspend () -> DesktopCompetitorSiCardDraft,
    onAssignCompetitorCategory: (String, String?) -> Unit,
    onRemoveCompetitor: (String, Boolean) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val horizontalScrollState = rememberScrollState()
    val tableWidth = fixedTableWidth(CompetitorTableColumns)
    val orderedCompetitors = rememberEditableRowOrder(competitors) { it.id }
    val nextStartNumber = remember(competitors) { nextCompetitorStartNumber(competitors) }
    var firstNameDraft by remember { mutableStateOf("") }
    var lastNameDraft by remember { mutableStateOf("") }
    var clubDraft by remember { mutableStateOf("") }
    var bibNumberDraft by remember { mutableStateOf("") }
    var callSignDraft by remember { mutableStateOf("") }
    var birthYearDraft by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var startNumberDraft by remember(nextStartNumber) { mutableStateOf(nextStartNumber) }
    var siNumberDraft by remember { mutableStateOf("") }
    var isReadingSiCardForAdd by remember { mutableStateOf(false) }
    var readSiCardStatusText by remember { mutableStateOf<String?>(null) }
    val canAddCompetitor = firstNameDraft.isNotBlank() &&
            lastNameDraft.isNotBlank() &&
            startNumberDraft.isNotBlank()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    if (!isReadingSiCardForAdd) {
                        isReadingSiCardForAdd = true
                        readSiCardStatusText = "Waiting for SI card..."
                        coroutineScope.launch {
                            runCatching {
                                onReadCompetitorSiCardForAddRow()
                            }.onSuccess { draft ->
                                siNumberDraft = draft.siNumber.toString()
                                draft.firstName?.takeIf { it.isNotBlank() }?.let { firstNameDraft = it }
                                draft.lastName?.takeIf { it.isNotBlank() }?.let { lastNameDraft = it }
                                draft.club?.takeIf { it.isNotBlank() }?.let { clubDraft = it }
                                val nameStatus = listOfNotNull(draft.firstName, draft.lastName)
                                    .joinToString(" ")
                                    .ifBlank { "no card-holder name" }
                                readSiCardStatusText = "Read SI ${draft.siNumber}; $nameStatus. Review fields, then click Add."
                            }.onFailure { error ->
                                readSiCardStatusText = "SI card read failed: ${error.message ?: error::class.simpleName}"
                            }
                            isReadingSiCardForAdd = false
                        }
                    }
                },
                enabled = isReadCompetitorSiCardEnabled && !isReadingSiCardForAdd,
                modifier = Modifier.width(180.dp)
            ) {
                ButtonLabel(if (isReadingSiCardForAdd) "Reading SI Card" else "Read From SI Card")
            }
            readSiCardStatusText?.let { statusText ->
                Text(
                    text = statusText,
                    color = if (statusText.startsWith("SI card read failed")) {
                        DesktopPalette.Error
                    } else {
                        DesktopPalette.Disconnected
                    },
                    fontSize = 13.sp
                )
            }
        }
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
                        bibNumberDraft,
                        callSignDraft,
                        birthYearDraft,
                        selectedCategoryId,
                        startNumberDraft,
                        siNumberDraft
                    )
                    if (didAdd) {
                        firstNameDraft = ""
                        lastNameDraft = ""
                        clubDraft = ""
                        bibNumberDraft = ""
                        callSignDraft = ""
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
                        bibNumberDraft = bibNumberDraft,
                        onBibNumberChange = { bibNumberDraft = it },
                        callSignDraft = callSignDraft,
                        onCallSignChange = { callSignDraft = it },
                        birthYearDraft = birthYearDraft,
                        onBirthYearChange = { birthYearDraft = it },
                        selectedCategoryId = selectedCategoryId,
                        onCategorySelected = { selectedCategoryId = it },
                        startNumberDraft = startNumberDraft,
                        onStartNumberChange = { startNumberDraft = it },
                        siNumberDraft = siNumberDraft,
                        onSiNumberChange = { siNumberDraft = it }
                    )
                    FixedDetailHeaderRow(CompetitorTableColumns, CompetitorTableColumnHints)
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
                orderedCompetitors.forEach { competitor ->
                    key(competitor.id) {
                        CompetitorDeleteButton(competitor, onRemoveCompetitor)
                    }
                }
            }
            Box(modifier = Modifier.weight(1f).horizontalScroll(horizontalScrollState)) {
                Column(
                    modifier = Modifier.width(tableWidth),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    orderedCompetitors.forEach { competitor ->
                        key(competitor.id) {
                            CompetitorDetailRow(
                                competitor = competitor,
                                categories = categories,
                                onRenameCompetitor = onRenameCompetitor,
                                onUpdateCompetitorNumbers = onUpdateCompetitorNumbers,
                                onUpdateCompetitorClubIdentity = onUpdateCompetitorClubIdentity,
                                onUpdateCompetitorBirthYear = onUpdateCompetitorBirthYear,
                                onUpdateCompetitorStartTime = onUpdateCompetitorStartTime,
                                onAssignCompetitorCategory = onAssignCompetitorCategory
                            )
                        }
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
    bibNumberDraft: String,
    onBibNumberChange: (String) -> Unit,
    callSignDraft: String,
    onCallSignChange: (String) -> Unit,
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
            value = bibNumberDraft,
            onValueChange = onBibNumberChange,
            modifier = Modifier.width(CompetitorTableColumns[3].width),
            singleLine = true,
            label = { Text("Bib") }
        )
        TextField(
            value = callSignDraft,
            onValueChange = onCallSignChange,
            modifier = Modifier.width(CompetitorTableColumns[4].width),
            singleLine = true,
            label = { Text("Call") }
        )
        TextField(
            value = birthYearDraft,
            onValueChange = onBirthYearChange,
            modifier = Modifier.width(CompetitorTableColumns[5].width),
            singleLine = true,
            label = { Text("Birth") }
        )
        CategoryPicker(
            selectedCategoryId = selectedCategoryId,
            categories = categories,
            onCategorySelected = onCategorySelected,
            modifier = Modifier.width(CompetitorTableColumns[6].width)
        )
        TextField(
            value = startNumberDraft,
            onValueChange = onStartNumberChange,
            modifier = Modifier.width(CompetitorTableColumns[7].width),
            singleLine = true,
            label = { Text("Start") }
        )
        Spacer(modifier = Modifier.width(CompetitorTableColumns[8].width))
        TextField(
            value = siNumberDraft,
            onValueChange = onSiNumberChange,
            modifier = Modifier.width(CompetitorTableColumns[9].width),
            singleLine = true,
            label = { Text("SI") }
        )
    }
}

/** Shows one editable competitor-name row plus read-only assignment fields. */
@Composable
private fun CompetitorDetailRow(
    competitor: EventCompetitorDetails,
    categories: List<EventCategoryDetails>,
    onRenameCompetitor: (String, String, String) -> Unit,
    onUpdateCompetitorNumbers: (String, String, String) -> Unit,
    onUpdateCompetitorClubIdentity: (String, String, String, String) -> Unit,
    onUpdateCompetitorBirthYear: (String, String) -> Unit,
    onUpdateCompetitorStartTime: (String, String) -> Unit,
    onAssignCompetitorCategory: (String, String?) -> Unit
) {
    var firstNameDraft by remember(competitor.id, competitor.firstName) { mutableStateOf(competitor.firstName) }
    var lastNameDraft by remember(competitor.id, competitor.lastName) { mutableStateOf(competitor.lastName) }
    var clubDraft by remember(competitor.id, competitor.club) { mutableStateOf(competitor.club) }
    var bibNumberDraft by remember(competitor.id, competitor.bibNumber) { mutableStateOf(competitor.bibNumber) }
    var callSignDraft by remember(competitor.id, competitor.callSign) { mutableStateOf(competitor.callSign) }
    var birthYearDraft by remember(competitor.id, competitor.birthYearText) {
        mutableStateOf(competitor.birthYearText)
    }
    var startNumberDraft by remember(competitor.id, competitor.startNumberText) {
        mutableStateOf(competitor.startNumberText)
    }
    var startTimeDraft by remember(competitor.id, competitor.startTimeText) {
        mutableStateOf(competitor.startTimeText)
    }
    var siNumberDraft by remember(competitor.id, competitor.siNumberText) { mutableStateOf(competitor.siNumberText) }
    var selectedCategoryId by remember(competitor.id, competitor.categoryId) { mutableStateOf(competitor.categoryId) }
    fun applyPendingDrafts() {
        if (firstNameDraft != competitor.firstName || lastNameDraft != competitor.lastName) {
            if (firstNameDraft.isNotBlank() && lastNameDraft.isNotBlank()) {
                onRenameCompetitor(competitor.id, firstNameDraft, lastNameDraft)
            }
        }
        if (startNumberDraft != competitor.startNumberText || siNumberDraft != competitor.siNumberText) {
            if (startNumberDraft.trim().toIntOrNull() != null &&
                (siNumberDraft.isBlank() || siNumberDraft.trim().toIntOrNull() != null)
            ) {
                onUpdateCompetitorNumbers(competitor.id, startNumberDraft, siNumberDraft)
            }
        }
        if (
            clubDraft != competitor.club ||
            bibNumberDraft != competitor.bibNumber ||
            callSignDraft != competitor.callSign
        ) {
            onUpdateCompetitorClubIdentity(competitor.id, clubDraft, bibNumberDraft, callSignDraft)
        }
        if (birthYearDraft != competitor.birthYearText) {
            if (birthYearDraft.isBlank() || birthYearDraft.trim().toIntOrNull() != null) {
                onUpdateCompetitorBirthYear(competitor.id, birthYearDraft)
            }
        }
        if (startTimeDraft != competitor.startTimeText) {
            runCatching {
                if (startTimeDraft.isNotBlank()) {
                    DurationFormatter.minuteStringToSeconds(startTimeDraft)
                }
            }.onSuccess {
                onUpdateCompetitorStartTime(competitor.id, startTimeDraft)
            }
        }
        if (selectedCategoryId != competitor.categoryId) {
            onAssignCompetitorCategory(competitor.id, selectedCategoryId)
        }
    }
    val applyLatestPendingDrafts by rememberUpdatedState(::applyPendingDrafts)
    DisposableEffect(competitor.id) {
        onDispose { applyLatestPendingDrafts() }
    }
    Row(
        modifier = Modifier.width(fixedTableWidth(CompetitorTableColumns)),
        horizontalArrangement = Arrangement.spacedBy(TableColumnGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = firstNameDraft,
            onValueChange = { firstNameDraft = it },
            modifier = Modifier
                .width(CompetitorTableColumns[0].width)
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused) {
                        applyPendingDrafts()
                    }
                },
            singleLine = true,
            label = { Text("First") }
        )
        TextField(
            value = lastNameDraft,
            onValueChange = { lastNameDraft = it },
            modifier = Modifier
                .width(CompetitorTableColumns[1].width)
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused) {
                        applyPendingDrafts()
                    }
                },
            singleLine = true,
            label = { Text("Last") }
        )
        TextField(
            value = clubDraft,
            onValueChange = { clubDraft = it },
            modifier = Modifier
                .width(CompetitorTableColumns[2].width)
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused) {
                        applyPendingDrafts()
                    }
                },
            singleLine = true,
            label = { Text("Club") }
        )
        TextField(
            value = bibNumberDraft,
            onValueChange = { bibNumberDraft = it },
            modifier = Modifier
                .width(CompetitorTableColumns[3].width)
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused) {
                        applyPendingDrafts()
                    }
                },
            singleLine = true,
            label = { Text("Bib") }
        )
        TextField(
            value = callSignDraft,
            onValueChange = { callSignDraft = it },
            modifier = Modifier
                .width(CompetitorTableColumns[4].width)
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused) {
                        applyPendingDrafts()
                    }
                },
            singleLine = true,
            label = { Text("Call") }
        )
        TextField(
            value = birthYearDraft,
            onValueChange = { birthYearDraft = it },
            modifier = Modifier
                .width(CompetitorTableColumns[5].width)
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused) {
                        applyPendingDrafts()
                    }
                },
            singleLine = true,
            label = { Text("Birth") }
        )
        CategoryPicker(
            selectedCategoryId = selectedCategoryId,
            categories = categories,
            onCategorySelected = {
                selectedCategoryId = it
                onAssignCompetitorCategory(competitor.id, it)
            },
            modifier = Modifier.width(CompetitorTableColumns[6].width)
        )
        TextField(
            value = startNumberDraft,
            onValueChange = { startNumberDraft = it },
            modifier = Modifier
                .width(CompetitorTableColumns[7].width)
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused) {
                        applyPendingDrafts()
                    }
                },
            singleLine = true,
            label = { Text("Start") }
        )
        TextField(
            value = startTimeDraft,
            onValueChange = { startTimeDraft = it },
            modifier = Modifier
                .width(CompetitorTableColumns[8].width)
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused) {
                        applyPendingDrafts()
                    }
                },
            singleLine = true,
            label = { Text("mmm:ss") }
        )
        TextField(
            value = siNumberDraft,
            onValueChange = { siNumberDraft = it },
            modifier = Modifier
                .width(CompetitorTableColumns[9].width)
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused) {
                        applyPendingDrafts()
                    }
                },
            singleLine = true,
            label = { Text("SI") }
        )
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

/** Shows editable global logical controls backed by shared Event File editing rules. */
@Composable
private fun ControlDetailsPanel(
    controls: List<EventControlDetails>,
    raceType: RaceType,
    onUpdateControl: (String, String, String, ControlPointType, Boolean, String, String) -> Unit,
    onAddControl: (String, String, ControlPointType, Boolean, String, String) -> Boolean,
    onRemoveControl: (String) -> Unit
) {
    val horizontalScrollState = rememberScrollState()
    val tableWidth = fixedTableWidth(ControlTableColumns)
    val orderedControls = rememberEditableRowOrder(controls) { it.id }
    var siCodeDraft by remember { mutableStateOf("") }
    var typeDraft by remember { mutableStateOf(ControlPointType.CONTROL) }
    var publicLabelDraft by remember { mutableStateOf("") }
    var notesDraft by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TableColumnGap),
            verticalAlignment = Alignment.Top
        ) {
            Button(
                onClick = {
                    val didAdd = onAddControl("", siCodeDraft, typeDraft, typeDraft.defaultScored(), publicLabelDraft, notesDraft)
                    if (didAdd) {
                        siCodeDraft = ""
                        typeDraft = ControlPointType.CONTROL
                        publicLabelDraft = ""
                        notesDraft = ""
                    }
                },
                modifier = fixedActionRailModifier(),
                enabled = siCodeDraft.isNotBlank()
            ) {
                ButtonLabel("Add")
            }
            Box(modifier = Modifier.weight(1f).horizontalScroll(horizontalScrollState)) {
                Column(
                    modifier = Modifier.width(tableWidth),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ControlAddRow(
                        siCodeDraft = siCodeDraft,
                        onSiCodeChange = { siCodeDraft = it },
                        typeDraft = typeDraft,
                        raceType = raceType,
                        onTypeChange = { typeDraft = it },
                        publicLabelDraft = publicLabelDraft,
                        onPublicLabelChange = { publicLabelDraft = it },
                        notesDraft = notesDraft,
                        onNotesChange = { notesDraft = it }
                    )
                    FixedDetailHeaderRow(ControlTableColumns, ControlTableColumnHints)
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
                orderedControls.forEach { control ->
                    key(control.id) {
                        ControlDeleteButton(control, onRemoveControl)
                    }
                }
            }
            Box(modifier = Modifier.weight(1f).horizontalScroll(horizontalScrollState)) {
                Column(
                    modifier = Modifier.width(tableWidth),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    orderedControls.forEach { control ->
                        key(control.id) {
                            ControlDetailRow(
                                control = control,
                                raceType = raceType,
                                onUpdateControl = onUpdateControl
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlsRouteKmlImportPanel(onSelectFile: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Button(onClick = onSelectFile) {
            ButtonLabel("Import Controls KML/KMZ...")
        }
        Text(
            text = "Controls CSV files update control identity fields only: SI code, role, scoring, public label, and notes. They do not contain latitude/longitude columns and cannot update control locations. Control coordinates require the Event Password and are not written to public control fields.",
            color = DesktopPalette.Black,
            fontSize = 13.sp
        )
        Text(
            text = "KML/KMZ file requirements",
            color = DesktopPalette.Black,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KmlImportInstruction("Use KML Placemark elements. Each imported Placemark must have a nonblank name.")
            KmlImportInstruction("Control points are Point placemarks. Their names must match existing Event File controls.")
            KmlImportInstruction("Control point names may use an SI code, control label, or public label.")
            KmlImportInstruction("Matched point placemarks update control locations when their latitude/longitude differs from stored course data.")
            KmlImportInstruction("Use visible labels such as 31, M, Beacon, S, or Spectator; do not add type suffixes to SI codes.")
            KmlImportInstruction("Routes are LineString placemarks with at least two coordinates. A KML/KMZ with point placemarks only can still update changed control locations.")
            KmlImportInstruction("Each route LineString name must match an Event File category name, such as M21.")
            KmlImportInstruction("Matching ignores case, trims leading/trailing spaces, and collapses repeated whitespace.")
            KmlImportInstruction("Coordinates are read as longitude,latitude,elevation. Elevation may be omitted.")
            KmlImportInstruction("Controls more than 50 meters from a matched category route are not included in the stored ideal order.")
            KmlImportInstruction("For KMZ files, the first .kml document in the archive is read.")
        }
        Text(
            text = "Minimal example",
            color = DesktopPalette.Black,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = """
                <Placemark>
                  <name>31</name>
                  <Point>
                    <coordinates>-95.0000,39.0000,0</coordinates>
                  </Point>
                </Placemark>

                <Placemark>
                  <name>M21</name>
                  <LineString>
                    <coordinates>
                      -95.0000,39.0000,0
                      -94.9990,39.0000,0
                      -94.9980,39.0000,0
                    </coordinates>
                  </LineString>
                </Placemark>
            """.trimIndent(),
            color = DesktopPalette.Black,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun VenueElevationCachePanel(
    refreshToken: Int,
    onOpenCacheFolder: () -> Unit
) {
    var cacheListings by remember { mutableStateOf<List<DesktopVenueElevationCacheListing>>(emptyList()) }
    var isLoadingCacheListings by remember { mutableStateOf(true) }
    var cacheListingError by remember { mutableStateOf<String?>(null) }
    val spotCheckScope = rememberCoroutineScope()
    var spotCheckInProgress by remember { mutableStateOf<DesktopVenueElevationCacheListing?>(null) }
    var spotCheckResult by remember { mutableStateOf<DesktopVenueElevationSpotCheckSummary?>(null) }
    var spotCheckError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refreshToken) {
        isLoadingCacheListings = true
        cacheListingError = null
        runCatching {
            withContext(Dispatchers.IO) {
                DesktopVenueElevationCache.listings()
            }
        }.onSuccess { listings ->
            cacheListings = listings
        }.onFailure { error ->
            cacheListings = emptyList()
            cacheListingError = error.message ?: error::class.simpleName
        }
        isLoadingCacheListings = false
    }

    fun startSpotCheck(
        listing: DesktopVenueElevationCacheListing,
        source: DesktopVenueElevationReferenceSource
    ) {
        spotCheckInProgress = listing
        spotCheckResult = null
        spotCheckError = null
        spotCheckScope.launch {
            runCatching {
                DesktopVenueElevationCache.spotCheck(
                    cachePath = listing.path,
                    referenceSource = source,
                    samplePointCount = 100
                )
            }.onSuccess { summary ->
                spotCheckResult = summary
            }.onFailure { error ->
                spotCheckError = error.message ?: error::class.simpleName
            }
            spotCheckInProgress = null
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isLoadingCacheListings) {
            VenueElevationCacheListingProgressDialog()
        }
        Text(
            text = "Cache folder: ${DesktopVenueElevationCache.cacheDirectory()}",
            color = DesktopPalette.Black,
            fontSize = 13.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Cached venues",
                color = DesktopPalette.Black,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            if (cacheListings.isNotEmpty()) {
                Button(onClick = onOpenCacheFolder) {
                    ButtonLabel("Open Folder")
                }
            }
        }
        cacheListingError?.let { error ->
            Text(
                text = "Could not load cached venues: $error",
                color = DesktopPalette.Error,
                fontSize = 13.sp
            )
        }
        if (isLoadingCacheListings) {
            Text(
                text = "Loading cached venues...",
                color = DesktopPalette.Black,
                fontSize = 14.sp
            )
        } else if (cacheListings.isEmpty() && cacheListingError == null) {
            Text(
                text = "No venue elevation caches found.",
                color = DesktopPalette.Black,
                fontSize = 14.sp
            )
        } else {
            cacheListings.forEach { listing ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "${listing.venueName} - ${listing.sourceName} ${listing.resolutionMeters.roundToInt()} m - ${listing.resolvedPointCount}/${listing.rowCount * listing.columnCount} points",
                        color = DesktopPalette.Black,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${listing.path.fileName}  ${listing.createdAtIso}",
                        color = DesktopPalette.Black,
                        fontSize = 12.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DesktopVenueElevationReferenceSource.entries.forEach { source ->
                            Button(
                                onClick = { startSpotCheck(listing, source) },
                                enabled = spotCheckInProgress == null
                            ) {
                                ButtonLabel(
                                    if (spotCheckInProgress?.path == listing.path) {
                                        "Checking..."
                                    } else {
                                        "Spot Check ${source.label}"
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        spotCheckError?.let { error ->
            Text(
                text = "Spot check failed: $error",
                color = DesktopPalette.Error,
                fontSize = 13.sp
            )
        }
        spotCheckResult?.let { result ->
            VenueElevationSpotCheckResultPanel(result)
        }
    }
}

@Composable
private fun VenueElevationCacheImportPanel(
    projectFile: EventProjectFile,
    protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>,
    onDownloadCache: (String, DesktopVenueElevationBoundingBox, Double, Double, DesktopVenueElevationCacheSource, String) -> Unit
) {
    val importedBounds = remember(protectedCourseInfoByCategoryId) {
        protectedCourseInfoByCategoryId.values.flatMap { it.courseGeoPoints() }.venueBoundingBoxOrNull()
    }
    var venueNameDraft by remember(projectFile.raceData.race.name) {
        mutableStateOf(projectFile.raceData.race.name.ifBlank { "Venue" })
    }
    var minLatitudeDraft by remember { mutableStateOf("") }
    var maxLatitudeDraft by remember { mutableStateOf("") }
    var minLongitudeDraft by remember { mutableStateOf("") }
    var maxLongitudeDraft by remember { mutableStateOf("") }
    var bufferMetersDraft by remember { mutableStateOf("500") }
    var resolutionMetersDraft by remember { mutableStateOf("10") }
    var localRasterPathDraft by remember { mutableStateOf("") }

    fun applyBoundingBox(bounds: DesktopVenueElevationBoundingBox) {
        minLatitudeDraft = bounds.minLatitude.decimalText()
        maxLatitudeDraft = bounds.maxLatitude.decimalText()
        minLongitudeDraft = bounds.minLongitude.decimalText()
        maxLongitudeDraft = bounds.maxLongitude.decimalText()
    }

    val parsedBoundingBox = remember(minLatitudeDraft, maxLatitudeDraft, minLongitudeDraft, maxLongitudeDraft) {
        val minLatitude = minLatitudeDraft.toDoubleOrNull()
        val maxLatitude = maxLatitudeDraft.toDoubleOrNull()
        val minLongitude = minLongitudeDraft.toDoubleOrNull()
        val maxLongitude = maxLongitudeDraft.toDoubleOrNull()
        if (minLatitude == null || maxLatitude == null || minLongitude == null || maxLongitude == null) {
            null
        } else {
            runCatching {
                DesktopVenueElevationBoundingBox(
                    minLatitude = minLatitude,
                    maxLatitude = maxLatitude,
                    minLongitude = minLongitude,
                    maxLongitude = maxLongitude
                )
            }.getOrNull()
        }
    }
    val resolutionMeters = resolutionMetersDraft.toDoubleOrNull()
    val bufferMeters = bufferMetersDraft.toDoubleOrNull() ?: 0.0
    val estimate = remember(parsedBoundingBox, resolutionMeters, bufferMeters) {
        if (parsedBoundingBox != null && resolutionMeters != null && resolutionMeters > 0.0) {
            runCatching {
                DesktopVenueElevationCache.estimate(parsedBoundingBox, resolutionMeters, bufferMeters)
            }.getOrNull()
        } else {
            null
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { importedBounds?.let(::applyBoundingBox) },
                enabled = importedBounds != null
            ) {
                ButtonLabel("Use Imported Course Bounds")
            }
            Text(
                text = importedBounds?.let { "Imported route/control data available." }
                    ?: "Unlock course data to derive bounds from imported routes.",
                color = DesktopPalette.Black,
                fontSize = 13.sp
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LabeledTextField("Venue", venueNameDraft, { venueNameDraft = it }, Modifier.width(320.dp))
            LabeledTextField("Resolution m", resolutionMetersDraft, { resolutionMetersDraft = it }, Modifier.width(120.dp))
            LabeledTextField("Buffer m", bufferMetersDraft, { bufferMetersDraft = it }, Modifier.width(120.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LabeledTextField("Min lat", minLatitudeDraft, { minLatitudeDraft = it }, Modifier.width(150.dp))
            LabeledTextField("Max lat", maxLatitudeDraft, { maxLatitudeDraft = it }, Modifier.width(150.dp))
            LabeledTextField("Min lon", minLongitudeDraft, { minLongitudeDraft = it }, Modifier.width(150.dp))
            LabeledTextField("Max lon", maxLongitudeDraft, { maxLongitudeDraft = it }, Modifier.width(150.dp))
        }
        estimate?.let {
            Text(
                text = "Estimated grid: ${it.columnCount} x ${it.rowCount} (${it.pointCount} points), raw ${bytesText(it.rawBytes)}. Expanded area ${oneDecimal(it.boundingBox.widthMeters() / 1000.0)} km x ${oneDecimal(it.boundingBox.heightMeters() / 1000.0)} km.",
                color = DesktopPalette.Black,
                fontSize = 14.sp
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val bounds = parsedBoundingBox ?: return@Button
                    val resolution = resolutionMeters ?: return@Button
                    onDownloadCache(
                        venueNameDraft,
                        bounds,
                        resolution,
                        bufferMeters,
                        DesktopVenueElevationCacheSource.Usgs3Dep,
                        ""
                    )
                },
                enabled = parsedBoundingBox != null && resolutionMeters != null && resolutionMeters > 0.0
            ) {
                ButtonLabel("Download USGS 3DEP")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        val bounds = parsedBoundingBox ?: return@Button
                        val resolution = resolutionMeters ?: return@Button
                        onDownloadCache(
                            venueNameDraft,
                            bounds,
                            resolution,
                            bufferMeters,
                            DesktopVenueElevationCacheSource.WashingtonDnrLidarDtm,
                            ""
                        )
                    },
                    enabled = parsedBoundingBox != null && resolutionMeters != null && resolutionMeters > 0.0
                ) {
                    ButtonLabel("Download WA DNR LiDAR DTM")
                }
                Button(
                    onClick = {
                        val bounds = parsedBoundingBox ?: return@Button
                        val resolution = resolutionMeters ?: return@Button
                        onDownloadCache(
                            venueNameDraft,
                            bounds,
                            resolution,
                            bufferMeters,
                            DesktopVenueElevationCacheSource.OregonDogamiLidarDtm,
                            ""
                        )
                    },
                    enabled = parsedBoundingBox != null && resolutionMeters != null && resolutionMeters > 0.0
                ) {
                    ButtonLabel("Download OR DOGAMI LiDAR DTM")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        DesktopFileDialogs.chooseElevationRaster()?.let { path ->
                            localRasterPathDraft = path.toString()
                        }
                    }
                ) {
                    ButtonLabel("Select Local Raster...")
                }
                Button(
                    onClick = {
                        val bounds = parsedBoundingBox ?: return@Button
                        val resolution = resolutionMeters ?: return@Button
                        onDownloadCache(
                            venueNameDraft,
                            bounds,
                            resolution,
                            bufferMeters,
                            DesktopVenueElevationCacheSource.LocalLidarRaster,
                            localRasterPathDraft
                        )
                    },
                    enabled = parsedBoundingBox != null &&
                        resolutionMeters != null &&
                        resolutionMeters > 0.0 &&
                        localRasterPathDraft.isNotBlank()
                ) {
                    ButtonLabel("Create Cache from Local Raster")
                }
            }
            LabeledTextField(
                "Local raster file",
                localRasterPathDraft,
                { localRasterPathDraft = it },
                Modifier.width(640.dp)
            )
            Text(
                text = "Use a local GeoTIFF raster (.tif/.tiff) or GeoTIFF ZIP (.zip), such as a countywide LiDAR DEM, to create a venue-sized cache without downloading elevation data.",
                color = DesktopPalette.Black,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun VenueElevationSpotCheckResultPanel(
    result: DesktopVenueElevationSpotCheckSummary
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Spot check: ${result.venueName} vs ${result.referenceSource.label}",
            color = DesktopPalette.Black,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = buildString {
                append("${result.comparedPointCount}/${result.requestedPointCount} points compared")
                append("  avg diff ${result.averageDifferenceMeters?.let(::signedMetersText) ?: "n/a"}")
                append("  avg abs ${result.averageAbsoluteDifferenceMeters?.let(::metersText) ?: "n/a"}")
                append("  max abs ${result.maximumAbsoluteDifferenceMeters?.let(::metersText) ?: "n/a"}")
                if (result.missingCacheCount > 0 || result.missingReferenceCount > 0) {
                    append("  missing cache ${result.missingCacheCount}, reference ${result.missingReferenceCount}")
                }
            },
            color = DesktopPalette.Black,
            fontSize = 13.sp
        )
        SpotCheckHeatMap(result)
        Text(
            text = "Largest differences",
            color = DesktopPalette.Black,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        result.rows
            .filter { it.differenceMeters != null }
            .take(10)
            .forEach { row ->
                Text(
                    text = "row ${row.row}, col ${row.column}: cache ${row.cachedMeters?.let(::metersText) ?: "n/a"}, ${result.referenceSource.label} ${row.referenceMeters?.let(::metersText) ?: "n/a"}, diff ${row.differenceMeters?.let(::signedMetersText) ?: "n/a"} at ${row.latitude.decimalText()}, ${row.longitude.decimalText()}",
                    color = DesktopPalette.Black,
                    fontSize = 12.sp
                )
            }
    }
}

@Composable
private fun SpotCheckHeatMap(result: DesktopVenueElevationSpotCheckSummary) {
    val rows = result.rows
    val maxAbsDifference = result.maximumAbsoluteDifferenceMeters?.coerceAtLeast(0.1) ?: 0.1
    Canvas(
        modifier = Modifier
            .width(220.dp)
            .height(150.dp)
            .border(1.dp, DesktopPalette.LightGrey)
            .background(Color(0xFFF8F8F8))
    ) {
        if (rows.isEmpty()) {
            return@Canvas
        }
        val minRow = rows.minOf { it.row }
        val maxRow = rows.maxOf { it.row }.coerceAtLeast(minRow + 1)
        val minColumn = rows.minOf { it.column }
        val maxColumn = rows.maxOf { it.column }.coerceAtLeast(minColumn + 1)
        val cellWidth = size.width / (maxColumn - minColumn + 1).toFloat()
        val cellHeight = size.height / (maxRow - minRow + 1).toFloat()
        rows.forEach { row ->
            val difference = row.differenceMeters
            val intensity = difference?.let { (abs(it) / maxAbsDifference).coerceIn(0.15, 1.0).toFloat() } ?: 0.3f
            val color = when {
                difference == null -> Color.Gray.copy(alpha = 0.35f)
                difference > 0.0 -> Color(0xFFD32F2F).copy(alpha = intensity)
                difference < 0.0 -> Color(0xFF1976D2).copy(alpha = intensity)
                else -> Color(0xFF2E7D32).copy(alpha = 0.35f)
            }
            drawRect(
                color = color,
                topLeft = Offset(
                    x = (row.column - minColumn) * cellWidth,
                    y = (maxRow - row.row) * cellHeight
                ),
                size = Size(cellWidth.coerceAtLeast(1f), cellHeight.coerceAtLeast(1f))
            )
        }
    }
    Text(
        text = "Red: cache higher. Blue: cache lower. Gray: missing comparison point.",
        color = DesktopPalette.Black,
        fontSize = 12.sp
    )
}

@Composable
private fun KmlImportInstruction(text: String) {
    Text(
        text = "- $text",
        color = DesktopPalette.Black,
        fontSize = 14.sp
    )
}

@Composable
private fun CourseAnalyzerGuidance() {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Use Setup > Controls > Course Analyzer > Import Controls KML/KMZ... to import or update control locations and category routes before running analysis.",
            color = DesktopPalette.Black,
            fontSize = 13.sp
        )
        Text(
            text = "KML/KMZ import files should contain named Placemark elements. Control locations are Point placemarks named by SI code, control label, or public label. Stored category routes are LineString placemarks named by Event File category, such as M21. Coordinates are read as longitude,latitude,elevation; elevation may be omitted. For KMZ files, the first .kml document in the archive is read.",
            color = DesktopPalette.Black,
            fontSize = 13.sp
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            KmlImportInstruction("Choose a category, then Analyze to compare the stored route with the calculated route candidate.")
            KmlImportInstruction("Export Analysis writes the displayed analysis plus route/control data for external review.")
            KmlImportInstruction("Apply Calculated Route replaces stored route and numbering data when the calculated route is available.")
            KmlImportInstruction("Apply Fox Renumbering Only applies the Section 1 wait-time renumbering when an improvement is available.")
        }
    }
}

@Composable
private fun CourseAnalysisPanel(
    projectFile: EventProjectFile,
    isUnlocked: Boolean,
    protectedIdealOrderByCategoryId: Map<String, String>,
    protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>,
    onRetrieveMissingElevations: (String) -> Unit,
    onUnlock: (String) -> Boolean,
    onUseCalculatedRoute: (DesktopCourseCalculatedRouteApplication) -> String,
    onApplyFoxRenumberingOnly: (DesktopCourseWaitRenumbering) -> String
) {
    var passwordDraft by remember(projectFile.raceData.race.id, isUnlocked) { mutableStateOf("") }
    if (!isUnlocked) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = passwordDraft,
                onValueChange = { passwordDraft = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.width(260.dp)
            )
            DisabledReasonTooltip(
                if (passwordDraft.isBlank()) {
                    "Enter the Event Password to view route data and run analysis."
                } else {
                    null
                }
            ) {
                Button(
                    onClick = {
                        if (onUnlock(passwordDraft)) {
                            passwordDraft = ""
                        }
                    },
                    enabled = passwordDraft.isNotBlank()
                ) {
                    ButtonLabel("Unlock")
                }
            }
        }
        return
    }

    val categories = projectFile.raceData.categories
        .filter { categoryData ->
            protectedCourseInfoByCategoryId[categoryData.category.id]?.route.orEmpty().size >= 2
        }
        .sortedWith(EventCategorySort.byDisplayName)
    var selectedCategoryId by remember(projectFile.raceData.race.id, categories.map { it.category.id }) {
        mutableStateOf(categories.firstOrNull()?.category?.id)
    }
    val effectiveSelectedCategoryId = selectedCategoryId
        ?.takeIf { selectedId -> categories.any { it.category.id == selectedId } }
        ?: categories.firstOrNull()?.category?.id
    var analysisResult by remember(projectFile.raceData.race.id, protectedCourseInfoByCategoryId) {
        mutableStateOf<DesktopCourseAnalysisSummary?>(null)
    }
    var pendingMissingDataResult by remember(projectFile.raceData.race.id, protectedCourseInfoByCategoryId) {
        mutableStateOf<CourseAnalysisMissingDataPrompt?>(null)
    }
    var exportStatusText by remember(projectFile.raceData.race.id) { mutableStateOf<String?>(null) }
    var applyStatusText by remember(projectFile.raceData.race.id) { mutableStateOf<String?>(null) }
    var isAnalyzing by remember(projectFile.raceData.race.id) { mutableStateOf(false) }
    val analysisScope = rememberCoroutineScope()

    suspend fun analyzeSelectedCourse(categoryId: String): DesktopCourseAnalysisSummary =
        withContext(Dispatchers.Default) {
            DesktopCourseAnalyzer.analyze(
                projectFile = projectFile,
                categoryId = categoryId,
                protectedCourseInfo = protectedCourseInfoByCategoryId[categoryId],
                protectedIdealOrderText = protectedIdealOrderByCategoryId[categoryId],
                elevationLookup = DesktopVenueElevationCache::elevationMeters,
                elevationCacheNotes = DesktopVenueElevationCache::analysisSourceNotes
            )
        }

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        CourseAnalyzerGuidance()
        if (categories.isEmpty()) {
            Text(
                text = "Import controls/route KML/KMZ data for a category before running course analysis.",
                color = DesktopPalette.Black,
                fontSize = 14.sp
            )
            return@Column
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CourseAnalysisCategoryPicker(
                selectedCategoryId = effectiveSelectedCategoryId,
                categories = categories.map { it.category.id to it.category.name },
                onCategorySelected = {
                    selectedCategoryId = it
                    analysisResult = null
                    pendingMissingDataResult = null
                    exportStatusText = null
                    applyStatusText = null
                },
                modifier = Modifier.width(280.dp)
            )
            DisabledReasonTooltip(
                when {
                    effectiveSelectedCategoryId == null ->
                        "Import controls/route KML/KMZ data for a category before running analysis."
                    else -> null
                }
            ) {
                Button(
                    onClick = {
                        val categoryId = effectiveSelectedCategoryId ?: return@Button
                        if (isAnalyzing) return@Button
                        isAnalyzing = true
                        exportStatusText = null
                        applyStatusText = null
                        pendingMissingDataResult = null
                        analysisScope.launch {
                            try {
                                // Let Compose paint the progress dialog before route optimization
                                // begins; Foxoring hybrid search can otherwise make the UI appear
                                // unresponsive on slower machines.
                                delay(100)
                                val summary = analyzeSelectedCourse(categoryId)
                                if (summary.missingElements.isEmpty()) {
                                    analysisResult = summary
                                } else {
                                    pendingMissingDataResult = CourseAnalysisMissingDataPrompt(
                                        categoryId = categoryId,
                                        summary = summary
                                    )
                                }
                            } catch (error: Throwable) {
                                exportStatusText = "Analysis failed: ${error.message ?: error::class.simpleName}"
                                DesktopDebugLog.error("CourseAnalysis", "Analysis failed: ${error.message ?: error::class.simpleName}")
                            } finally {
                                isAnalyzing = false
                            }
                        }
                    },
                    enabled = effectiveSelectedCategoryId != null && !isAnalyzing
                ) {
                    ButtonLabel("Analyze")
                }
            }
            DisabledReasonTooltip(
                when {
                    isAnalyzing -> null
                    analysisResult == null -> "Run analysis before exporting."
                    else -> null
                }
            ) {
                Button(
                    onClick = {
                        val summary = analysisResult ?: return@Button
                        DesktopFileDialogs.chooseExportCourseAnalysisPdf(
                            eventName = projectFile.raceData.race.name,
                            categoryName = summary.categoryName
                        )?.let { path ->
                            runCatching {
                                val exportPaths = DesktopCourseAnalysisExports.exportPdfAndKml(path, summary)
                                exportStatusText = "Exported ${exportPaths.pdfPath.fileName} and ${exportPaths.kmlPath.fileName}"
                                DesktopDebugLog.info(
                                    "CourseAnalysis",
                                    "Exported analysis PDF ${exportPaths.pdfPath.fileName} and KML ${exportPaths.kmlPath.fileName}"
                                )
                            }.onFailure { error ->
                                exportStatusText = "Export failed: ${error.message ?: error::class.simpleName}"
                                DesktopDebugLog.error("CourseAnalysis", "Analysis export failed: ${error.message ?: error::class.simpleName}")
                            }
                        }
                    },
                    enabled = analysisResult != null && !isAnalyzing
                ) {
                    ButtonLabel("Export Analysis...")
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DisabledReasonTooltip(if (isAnalyzing) null else calculatedRouteApplyDisabledReason(analysisResult)) {
                Button(
                    onClick = {
                        val application = analysisResult?.calculatedRouteApplication ?: return@Button
                        applyStatusText = onUseCalculatedRoute(application)
                        analysisResult = null
                    },
                    enabled = analysisResult?.calculatedRouteApplication != null && !isAnalyzing
                ) {
                    ButtonLabel("Apply Calculated Route")
                }
            }
            DisabledReasonTooltip(if (isAnalyzing) null else foxRenumberingApplyDisabledReason(analysisResult)) {
                Button(
                    onClick = {
                        val renumbering = analysisResult?.waitRenumbering?.takeIf { it.improvesWait } ?: return@Button
                        applyStatusText = onApplyFoxRenumberingOnly(renumbering)
                        analysisResult = null
                    },
                    enabled = analysisResult?.waitRenumbering?.improvesWait == true && !isAnalyzing
                ) {
                    ButtonLabel("Apply Fox Renumbering Only")
                }
            }
        }
        if (isAnalyzing) {
            IndeterminateProgressDialog(
                title = "Analyzing course",
                message = "Calculating route metrics, route optimization, wait times, rule checks, and report graphics."
            )
        }
        applyStatusText?.let { statusText ->
            Text(
                text = statusText,
                color = if (statusText.startsWith("Apply") && statusText.contains("failed")) {
                    DesktopPalette.Error
                } else {
                    DesktopPalette.Disconnected
                },
                fontSize = 13.sp
            )
        }
        exportStatusText?.let { statusText ->
            Text(
                text = statusText,
                color = if (statusText.startsWith("Export failed")) DesktopPalette.Error else DesktopPalette.Disconnected,
                fontSize = 13.sp
            )
        }
        CourseAnalysisResultView(analysisResult)
    }

    pendingMissingDataResult?.let { prompt ->
        val summary = prompt.summary
        AlertDialog(
            onDismissRequest = { pendingMissingDataResult = null },
            title = { Text("Course analysis data is incomplete") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "The analyzer can continue, but the result may be partial.",
                        color = DesktopPalette.Black,
                        fontSize = 14.sp
                    )
                    summary.missingElements.forEach { missing ->
                        Text(
                            text = "- $missing",
                            color = DesktopPalette.Black,
                            fontSize = 13.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        analysisResult = summary
                        pendingMissingDataResult = null
                    }
                ) {
                    ButtonLabel("Proceed")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (summary.hasMissingElevationData) {
                        Button(
                            onClick = {
                                pendingMissingDataResult = null
                                onRetrieveMissingElevations(prompt.categoryId)
                            }
                        ) {
                            ButtonLabel("Retrieve Elevations")
                        }
                    }
                    Button(onClick = { pendingMissingDataResult = null }) {
                        ButtonLabel("Cancel")
                    }
                }
            }
        )
    }
}

private fun calculatedRouteApplyDisabledReason(analysisResult: DesktopCourseAnalysisSummary?): String? =
    when {
        analysisResult == null -> "Run analysis before applying a calculated route."
        analysisResult.calculatedRouteApplication == null -> "No different calculated route is available to apply."
        else -> null
    }

private fun foxRenumberingApplyDisabledReason(analysisResult: DesktopCourseAnalysisSummary?): String? =
    when {
        analysisResult == null -> "Run analysis before applying fox renumbering."
        analysisResult.waitRenumbering?.improvesWait != true -> "No improved Section 1 fox renumbering is available."
        else -> null
    }

@Composable
private fun CourseAnalysisCategoryPicker(
    selectedCategoryId: String?,
    categories: List<Pair<String, String>>,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedCategoryName = categories.firstOrNull { it.first == selectedCategoryId }?.second ?: "Select category"

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
            categories.forEach { (categoryId, categoryName) ->
                DropdownMenuItem(
                    onClick = {
                        expanded = false
                        onCategorySelected(categoryId)
                    }
                ) {
                    Text(categoryName)
                }
            }
        }
    }
}

@Composable
private fun CourseAnalysisResultView(result: DesktopCourseAnalysisSummary?) {
    if (result == null) {
        Text(
            text = "Select a category and run analysis.",
            color = DesktopPalette.Black,
            fontSize = 14.sp
        )
        return
    }
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = result.categoryName,
                color = DesktopPalette.Black,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Rules applied: ${result.rulesDocumentLabel}",
                color = DesktopPalette.Black,
                fontSize = 13.sp
            )
            result.providedRouteSection?.let { section ->
                CourseAnalysisSectionView(section, includeRenumbering = true)
            }
            result.calculatedRouteSection?.let { section ->
                CourseAnalysisSectionView(section, includeRenumbering = false)
            }
            CourseAnalysisSummarySection(result)
            if (result.missingElements.isNotEmpty()) {
                Text(
                    text = "Partial analysis: ${result.missingElements.joinToString(" ")}",
                    color = DesktopPalette.Error,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun CourseAnalysisSectionView(section: DesktopCourseAnalysisSection, includeRenumbering: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = section.title,
            color = DesktopPalette.Black,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = section.explanation,
            color = DesktopPalette.Black,
            fontSize = 13.sp
        )
        CourseAnalysisRow(section.routeOrderLabel, section.routeOrder.joinToString(" -> ").ifBlank { "Unknown" })
        CourseAnalysisRuleCheckRows(section.ruleChecks)
        if (section.summaryOnly) {
            return@Column
        }
        section.secondaryRouteOrderLabel?.let { label ->
            CourseAnalysisRow(label, section.secondaryRouteOrder.joinToString(" -> ").ifBlank { "Unknown" })
        }
        CourseAnalysisRow(section.comparisonLengthLabel, sectionComparisonLengthText(section))
        CourseAnalysisRow("Horizontal length", kilometersText(section.straightLineMeters))
        CourseAnalysisRow("Route length", kilometersText(section.routeLengthMeters))
        CourseAnalysisRow("Climb", climbText(section.climbMeters))
        if (section.comparisonLengthLabel != "Effective length") {
            CourseAnalysisRow("Effective length", kilometersText(section.effectiveLengthMeters))
        }
        CourseAnalysisRow("Estimated ideal time", secondsText(section.estimatedIdealSeconds))
        CourseAnalysisTimingBreakdown(section.legRows, section.estimatedIdealSeconds)
        CourseAnalysisLegRows("Leg analysis", section.legRows)
        if (includeRenumbering) {
            CourseAnalysisProvidedRouteWaitAnalysis(section.waitRows, section.waitRenumbering)
        } else {
            CourseAnalysisWaitRows("Optimized wait times", section.waitRows)
        }
    }
}

@Composable
private fun CourseAnalysisTimingBreakdown(legs: List<DesktopCourseLegRow>, estimatedIdealSeconds: Int?) {
    val totalSeconds = estimatedIdealSeconds ?: return
    val waitSeconds = legs.sumOf { it.waitSeconds ?: 0 }
    val findPunchSeconds = legs.sumOf { it.findPunchSeconds ?: 0 }
    if (waitSeconds == 0 && findPunchSeconds == 0) {
        return
    }
    val movementSeconds = (totalSeconds - waitSeconds - findPunchSeconds).coerceAtLeast(0)
    CourseAnalysisRow("Movement time", secondsText(movementSeconds))
    CourseAnalysisRow("Fox wait time", secondsText(waitSeconds))
    CourseAnalysisRow("Find/punch allowance", secondsText(findPunchSeconds))
}

@Composable
private fun CourseAnalysisProvidedRouteWaitAnalysis(
    waitRows: List<DesktopCourseWaitRow>,
    renumbering: DesktopCourseWaitRenumbering?
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Stored-route wait-time analysis",
            color = DesktopPalette.Black,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "This subsection estimates Classic fox arrival phases on the stored route and checks whether assigning different fox numbers to the same locations could reduce waiting. If a competitor reaches a fox while it is off the air, timing waits for that fox to transmit, then adds 30 seconds to find and punch before departure. It uses the same elite baseline speed and effective-length movement estimates as the route analysis. Because map passability, fatigue, and category age/gender speed differences are not modeled, barriers, slow terrain, and competitor profile can shift real arrival times and change wait-time outcomes.",
            color = DesktopPalette.Black,
            fontSize = 13.sp
        )
        CourseAnalysisWaitRows("Current wait times", waitRows)
        if (renumbering != null) {
            CourseAnalysisWaitRenumbering(renumbering)
        }
    }
}

@Composable
private fun CourseAnalysisSummarySection(result: DesktopCourseAnalysisSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Section 3: Summary",
            color = DesktopPalette.Black,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = result.summaryExplanation,
            color = DesktopPalette.Black,
            fontSize = 13.sp
        )
        CourseAnalysisDetailRows(result)
        CourseAnalysisMetricRows(result.metrics)
        CourseAnalysisProfileComparison(result.profileComparison, result.elevationCacheNotes)
        CourseAnalysisRouteMaps(result.routeMaps)
    }
}

@Composable
private fun CourseAnalysisDetailRows(result: DesktopCourseAnalysisSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CourseAnalysisRow("Routes compared", result.calculatedRouteCount.toString())
        if (result.idealOrderMatches == true) {
            CourseAnalysisRow("Stored ideal route", result.providedIdealOrder.joinToString(" -> ").ifBlank { "Unknown" })
            CourseAnalysisRow("Order comparison", "Stored and calculated routes match")
        } else {
            CourseAnalysisRow("Calculated ideal route (calculated fox numbering)", result.calculatedIdealOrder.joinToString(" -> ").ifBlank { "Unknown" })
            CourseAnalysisRow("Stored ideal route", result.providedIdealOrder.joinToString(" -> ").ifBlank { "Unknown" })
            CourseAnalysisRow(
                "Order comparison",
                when (result.idealOrderMatches) {
                    true -> "Agrees"
                    false -> "Differs"
                    null -> "Unknown"
                }
            )
            CourseAnalysisRow("Calculated straight-line length", kilometersText(result.calculatedStraightLineMeters))
        }
        CourseAnalysisRow("Stored straight-line length", kilometersText(result.providedStraightLineMeters))
        CourseAnalysisRow("Stored route length", kilometersText(result.routeLengthMeters))
        CourseAnalysisRow("Climb", climbText(result.climbMeters))
        CourseAnalysisRow(
            "Effective length",
            result.metrics.firstOrNull { it.label == "Effective length" }?.value
                ?: kilometersText(result.effectiveLengthMeters)
        )
        CourseAnalysisRow("Estimated ideal time", secondsText(result.estimatedIdealSeconds))
    }
}

@Composable
private fun CourseAnalysisLegRows(title: String, legs: List<DesktopCourseLegRow>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            color = DesktopPalette.Black,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        if (legs.isEmpty()) {
            Text(
                text = "No leg rows available.",
                color = DesktopPalette.Black,
                fontSize = 13.sp
            )
            return@Column
        }
        legs.forEach { leg ->
            val waitText = leg.waitSeconds?.let { " (waits ${secondsText(it)})" }.orEmpty()
            CourseAnalysisRow(
                label = "${leg.fromLabel} -> ${leg.toLabel}",
                value = "${kilometersText(leg.lengthMeters)}  split ${secondsText(leg.splitSeconds)}  cumulative ${secondsText(leg.cumulativeSeconds)}$waitText"
            )
        }
    }
}

@Composable
private fun CourseAnalysisProfileComparison(
    profiles: List<DesktopCourseElevationProfileSummary>,
    elevationCacheNotes: List<String>
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Elevation profiles (cache grid; source resolution varies)",
            color = DesktopPalette.Black,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        if (profiles.isEmpty() || profiles.all { it.profile.isEmpty() }) {
            Text(
                text = "No elevation profiles available because local elevation data is incomplete.",
                color = DesktopPalette.Black,
                fontSize = 13.sp
            )
            return@Column
        }
        elevationCacheNotes.forEach { note ->
            Text(
                text = note,
                color = DesktopPalette.Black,
                fontSize = 13.sp
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            profiles.forEach { profile ->
                CourseAnalysisElevationProfile(profile.title, profile.profile, profile.markers, Modifier.width(300.dp))
            }
        }
    }
}

@Composable
private fun CourseAnalysisElevationProfile(
    title: String,
    profile: List<DesktopCourseElevationProfilePoint>,
    markers: List<DesktopCourseElevationProfileMarker>,
    modifier: Modifier = Modifier.width(620.dp)
) {
    if (profile.isEmpty()) {
        return
    }
    val minElevation = profile.minOf { it.elevationMeters }
    val maxElevation = profile.maxOf { it.elevationMeters }
    val totalDistanceMeters = profile.lastOrNull()?.distanceMeters ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            color = DesktopPalette.Black,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "0.00 km to ${twoDecimalText(totalDistanceMeters / 1000.0)} km, " +
                "${minElevation.roundToInt()} m to ${maxElevation.roundToInt()} m",
            color = DesktopPalette.Black,
            fontSize = 13.sp
        )
        Canvas(
            modifier = Modifier
                .then(modifier)
                .height(180.dp)
                .border(1.dp, DesktopPalette.LightGrey)
                .padding(8.dp)
        ) {
            val leftPadding = 36f
            val rightPadding = 10f
            val topPadding = 12f
            val bottomPadding = 24f
            val chartWidth = size.width - leftPadding - rightPadding
            val chartHeight = size.height - topPadding - bottomPadding
            if (chartWidth <= 0f || chartHeight <= 0f) {
                return@Canvas
            }
            val elevationRange = max(1.0, maxElevation - minElevation)
            val distanceRange = max(1.0, totalDistanceMeters.toDouble())
            fun xFor(distanceMeters: Int): Float =
                leftPadding + (distanceMeters / distanceRange).toFloat() * chartWidth
            fun yFor(elevationMeters: Double): Float =
                topPadding + ((maxElevation - elevationMeters) / elevationRange).toFloat() * chartHeight

            repeat(4) { index ->
                val fraction = index / 3f
                val y = topPadding + fraction * chartHeight
                drawLine(
                    color = DesktopPalette.LightGrey,
                    start = Offset(leftPadding, y),
                    end = Offset(leftPadding + chartWidth, y),
                    strokeWidth = 1f
                )
            }
            drawLine(
                color = DesktopPalette.Disconnected,
                start = Offset(leftPadding, topPadding),
                end = Offset(leftPadding, topPadding + chartHeight),
                strokeWidth = 1.5f
            )
            drawLine(
                color = DesktopPalette.Disconnected,
                start = Offset(leftPadding, topPadding + chartHeight),
                end = Offset(leftPadding + chartWidth, topPadding + chartHeight),
                strokeWidth = 1.5f
            )
            profile.zipWithNext().forEach { (start, end) ->
                drawLine(
                    color = DesktopPalette.Primary,
                    start = Offset(xFor(start.distanceMeters), yFor(start.elevationMeters)),
                    end = Offset(xFor(end.distanceMeters), yFor(end.elevationMeters)),
                    strokeWidth = 3f
                )
            }
            markers.forEach { marker ->
                drawCircle(
                    color = DesktopPalette.Warning,
                    radius = 4.5f,
                    center = Offset(xFor(marker.distanceMeters), yFor(marker.elevationMeters))
                )
            }
        }
        val markerText = markers.takeIf { it.isNotEmpty() }
            ?.joinToString("  ") { "${it.label} ${twoDecimalText(it.distanceMeters / 1000.0)} km" }
        markerText?.let {
            Text(
                text = it,
                color = DesktopPalette.Black,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CourseAnalysisRouteMaps(routeMaps: List<DesktopCourseRouteMap>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "2D route depictions",
            color = DesktopPalette.Black,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        if (routeMaps.isEmpty()) {
            Text(
                text = "No route depictions available.",
                color = DesktopPalette.Black,
                fontSize = 13.sp
            )
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            routeMaps.forEach { routeMap ->
                CourseAnalysisRouteMap(routeMap)
            }
        }
    }
}

@Composable
private fun CourseAnalysisRouteMap(routeMap: DesktopCourseRouteMap) {
    val mapWidth = 300.dp
    val mapHeight = 190.dp
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = routeMap.title,
            color = DesktopPalette.Black,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Box(
            modifier = Modifier
                .width(mapWidth)
                .height(mapHeight)
                .border(1.dp, DesktopPalette.LightGrey)
                .padding(8.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val byLabel = routeMap.points.associateBy { it.label }
                fun x(point: DesktopCourseRouteMapPoint): Float =
                    (point.xFraction.coerceIn(0.0, 1.0) * size.width).toFloat()
                fun y(point: DesktopCourseRouteMapPoint): Float =
                    (point.yFraction.coerceIn(0.0, 1.0) * size.height).toFloat()
                routeMap.routeLabels.zipWithNext().forEach { (fromLabel, toLabel) ->
                    val from = byLabel[fromLabel] ?: return@forEach
                    val to = byLabel[toLabel] ?: return@forEach
                    drawLine(
                        color = DesktopPalette.Primary,
                        start = Offset(x(from), y(from)),
                        end = Offset(x(to), y(to)),
                        strokeWidth = 2f
                    )
                }
                routeMap.points.forEach { point ->
                    drawCircle(
                        color = routeMapPointColor(point.type),
                        radius = 5f,
                        center = Offset(x(point), y(point))
                    )
                }
            }
            routeMap.points.forEach { point ->
                Text(
                    text = point.label,
                    color = DesktopPalette.Black,
                    fontSize = 11.sp,
                    modifier = Modifier.offset(
                        x = (point.xFraction.coerceIn(0.0, 1.0) * 250.0 + 8.0).dp,
                        y = (point.yFraction.coerceIn(0.0, 1.0) * 150.0 + 8.0).dp
                    )
                )
            }
        }
    }
}

private fun routeMapPointColor(type: DesktopCourseRouteMapPointType): Color =
    when (type) {
        DesktopCourseRouteMapPointType.Start -> DesktopPalette.Connected
        DesktopCourseRouteMapPointType.Finish -> DesktopPalette.Error
        DesktopCourseRouteMapPointType.Control -> DesktopPalette.Primary
        DesktopCourseRouteMapPointType.Beacon -> DesktopPalette.Warning
        DesktopCourseRouteMapPointType.Spectator -> DesktopPalette.Disconnected
    }

@Composable
private fun CourseAnalysisMetricRows(metrics: List<DesktopCourseGoodnessMetric>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Goodness metrics",
            color = DesktopPalette.Black,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        metrics.forEach { metric ->
            CourseAnalysisRow(
                label = metric.label,
                value = metric.value,
                valueColor = when (metric.status) {
                    DesktopCourseMetricStatus.Good -> DesktopPalette.Connected
                    DesktopCourseMetricStatus.Warning -> DesktopPalette.Error
                    DesktopCourseMetricStatus.Unknown -> DesktopPalette.Disconnected
                }
            )
        }
    }
}

private fun sectionComparisonLengthText(section: DesktopCourseAnalysisSection): String =
    section.ruleChecks
        .firstOrNull { it.label.endsWith("course length") }
        ?.value
        ?.replace("${section.comparisonLengthLabel} ", "")
        ?: kilometersText(section.comparisonLengthMeters)

@Composable
private fun CourseAnalysisRuleCheckRows(ruleChecks: List<DesktopCourseGoodnessMetric>) {
    if (ruleChecks.isEmpty()) {
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "USA rules checks",
            color = DesktopPalette.Black,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        ruleChecks.forEach { check ->
            CourseAnalysisRow(
                label = check.label,
                value = check.value,
                valueColor = when (check.status) {
                    DesktopCourseMetricStatus.Good -> DesktopPalette.Connected
                    DesktopCourseMetricStatus.Warning -> DesktopPalette.Error
                    DesktopCourseMetricStatus.Unknown -> DesktopPalette.Disconnected
                }
            )
        }
    }
}

@Composable
private fun CourseAnalysisWaitRows(title: String, waitRows: List<DesktopCourseWaitRow>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            color = DesktopPalette.Black,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        if (waitRows.isEmpty()) {
            Text(
                text = "No wait-time rows available.",
                color = DesktopPalette.Black,
                fontSize = 13.sp
            )
            return@Column
        }
        waitRows.forEach { row ->
            CourseAnalysisRow(
                label = row.controlLabel,
                value = listOfNotNull(
                    "arrival ${secondsText(row.arrivalSeconds)}",
                    row.slotLabel?.let { "fox $it" },
                    "wait ${secondsText(row.waitSeconds)}"
                ).joinToString(", ")
            )
        }
    }
}

@Composable
private fun CourseAnalysisWaitRenumbering(renumbering: DesktopCourseWaitRenumbering?) {
    if (renumbering == null) {
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Wait-time renumbering check",
            color = DesktopPalette.Black,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        CourseAnalysisRow(
            label = "Current / best wait",
            value = "${secondsText(renumbering.currentTotalWaitSeconds)} / ${secondsText(renumbering.bestTotalWaitSeconds)}",
            valueColor = if (renumbering.improvesWait) DesktopPalette.Error else DesktopPalette.Connected
        )
        CourseAnalysisRow(
            label = "Likely improvement",
            value = secondsText((renumbering.currentTotalWaitSeconds - renumbering.bestTotalWaitSeconds).coerceAtLeast(0)),
            valueColor = if (renumbering.improvesWait) DesktopPalette.Connected else DesktopPalette.Disconnected
        )
        Text(
            text = if (renumbering.improvesWait) {
                "Renumbering the fox transmit slots is likely to reduce wait time by ${secondsText(renumbering.currentTotalWaitSeconds - renumbering.bestTotalWaitSeconds)} on this stored route."
            } else {
                "Current fox numbering is already best for ideal-route wait time."
            },
            color = DesktopPalette.Black,
            fontSize = 13.sp
        )
        if (renumbering.improvesWait) {
            renumbering.assignments.forEach { assignment ->
                CourseAnalysisRow(
                    label = assignment.controlLabel,
                    value = "${assignment.currentSlotLabel} -> ${assignment.suggestedSlotLabel}"
                )
            }
        }
    }
}

@Composable
private fun CourseAnalysisRow(label: String, value: String, valueColor: Color = DesktopPalette.Black) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            modifier = Modifier.width(236.dp),
            color = DesktopPalette.Disconnected,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 13.sp
        )
    }
}

private fun kilometersText(value: Int?): String =
    value?.let { "${twoDecimalText(it / 1000.0)} km" } ?: "Unknown"

private fun metersText(value: Double): String =
    "${oneDecimal(value)} m"

private fun signedMetersText(value: Double): String =
    "${if (value >= 0.0) "+" else ""}${oneDecimal(value)} m"

private fun climbText(value: Int?): String =
    value?.let { "$it m" } ?: "Unknown"

private fun secondsText(value: Int?): String =
    value?.let(::compactSecondsText) ?: "Unknown"

private fun compactSecondsText(value: Int): String {
    val sign = if (value < 0) "-" else ""
    val absoluteSeconds = abs(value)
    val hours = absoluteSeconds / 3600
    val minutes = (absoluteSeconds % 3600) / 60
    val seconds = absoluteSeconds % 60
    return if (hours == 0) {
        "$sign$minutes:${seconds.toString().padStart(2, '0')}"
    } else {
        "$sign$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }
}

private fun twoDecimalText(value: Double): String =
    (value * 100.0).roundToInt().let { "${it / 100}.${(abs(it % 100)).toString().padStart(2, '0')}" }

@Composable
private fun ControlAddRow(
    siCodeDraft: String,
    onSiCodeChange: (String) -> Unit,
    typeDraft: ControlPointType,
    raceType: RaceType,
    onTypeChange: (ControlPointType) -> Unit,
    publicLabelDraft: String,
    onPublicLabelChange: (String) -> Unit,
    notesDraft: String,
    onNotesChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.width(fixedTableWidth(ControlTableColumns)),
        horizontalArrangement = Arrangement.spacedBy(TableColumnGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = siCodeDraft,
            onValueChange = onSiCodeChange,
            modifier = Modifier.width(ControlTableColumns[0].width),
            singleLine = true,
            label = { Text("SI code") }
        )
        ControlTypeDropdown(
            type = typeDraft,
            raceType = raceType,
            onTypeChange = onTypeChange,
            modifier = Modifier.width(ControlTableColumns[1].width)
        )
        TextField(
            value = publicLabelDraft,
            onValueChange = onPublicLabelChange,
            modifier = Modifier.width(ControlTableColumns[2].width),
            singleLine = true,
            label = { Text("Public label") }
        )
        TextField(
            value = notesDraft,
            onValueChange = onNotesChange,
            modifier = Modifier.width(ControlTableColumns[3].width),
            singleLine = true,
            label = { Text("Notes") }
        )
        Spacer(modifier = Modifier.width(ControlTableColumns[4].width))
    }
}

@Composable
private fun ControlDetailRow(
    control: EventControlDetails,
    raceType: RaceType,
    onUpdateControl: (String, String, String, ControlPointType, Boolean, String, String) -> Unit
) {
    var siCodeDraft by remember(control.id) { mutableStateOf(control.siCodeText) }
    var isSiCodeFocused by remember(control.id) { mutableStateOf(false) }

    LaunchedEffect(control.siCodeText, isSiCodeFocused) {
        if (!isSiCodeFocused) {
            siCodeDraft = control.siCodeText
        }
    }

    fun updateControl(
        siCodeText: String = control.siCodeText,
        type: ControlPointType = control.type,
        scored: Boolean = control.scored,
        publicLabel: String = control.publicLabel,
        notes: String = control.notes
    ) {
        if (siCodeText.trim().toIntOrNull() == null) {
            return
        }
        val nextLabel = if (siCodeText != control.siCodeText || type != control.type) {
            ""
        } else {
            control.label
        }
        onUpdateControl(control.id, nextLabel, siCodeText, type, scored, publicLabel, notes)
    }

    fun commitSiCodeDraft() {
        val normalizedDraft = siCodeDraft.trim()
        if (normalizedDraft == control.siCodeText) {
            return
        }
        if (normalizedDraft.toIntOrNull() == null) {
            siCodeDraft = control.siCodeText
            return
        }
        updateControl(siCodeText = normalizedDraft)
    }

    Row(
        modifier = Modifier.width(fixedTableWidth(ControlTableColumns)),
        horizontalArrangement = Arrangement.spacedBy(TableColumnGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = siCodeDraft,
            onValueChange = { siCodeDraft = it },
            modifier = Modifier
                .width(ControlTableColumns[0].width)
                .onFocusChanged { focusState ->
                    val wasFocused = isSiCodeFocused
                    isSiCodeFocused = focusState.isFocused
                    if (wasFocused && !focusState.isFocused) {
                        commitSiCodeDraft()
                    }
                },
            singleLine = true,
            label = { Text("SI code") }
        )
        ControlTypeDropdown(
            type = control.type,
            raceType = raceType,
            onTypeChange = { updateControl(type = it, scored = it.defaultScored()) },
            modifier = Modifier.width(ControlTableColumns[1].width)
        )
        TextField(
            value = control.publicLabel,
            onValueChange = { updateControl(publicLabel = it) },
            modifier = Modifier.width(ControlTableColumns[2].width),
            singleLine = true,
            label = { Text("Public label") }
        )
        TextField(
            value = control.notes,
            onValueChange = { updateControl(notes = it) },
            modifier = Modifier.width(ControlTableColumns[3].width),
            singleLine = true,
            label = { Text("Notes") }
        )
        Spacer(modifier = Modifier.width(ControlTableColumns[4].width))
    }
}

@Composable
private fun ControlDeleteButton(
    control: EventControlDetails,
    onRemoveControl: (String) -> Unit
) {
    var showDeleteDialog by remember(control.id) { mutableStateOf(false) }
    val displayLabel = control.publicLabel.takeIf { it.isNotBlank() } ?: control.label

    Button(
        onClick = { showDeleteDialog = true },
        modifier = fixedActionRailModifier()
    ) {
        ButtonLabel("Delete")
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete control") },
            text = { Text("Delete control $displayLabel (${control.siCodeText})?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onRemoveControl(control.id)
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

@Composable
private fun ControlTypeDropdown(
    type: ControlPointType,
    raceType: RaceType,
    onTypeChange: (ControlPointType) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Button(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            ButtonLabel(controlRoleLabel(type, raceType))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            controlRoleOptions(raceType).forEach { option ->
                DropdownMenuItem(
                    onClick = {
                        expanded = false
                        onTypeChange(option)
                    }
                ) {
                    Text(controlRoleLabel(option, raceType))
                }
            }
        }
    }
}

private fun controlRoleOptions(raceType: RaceType): List<ControlPointType> =
    when (raceType) {
        RaceType.SPRINT -> listOf(ControlPointType.CONTROL, ControlPointType.SEPARATOR, ControlPointType.BEACON)
        RaceType.CLASSIC, RaceType.SHORT, RaceType.FOXORING -> listOf(ControlPointType.CONTROL, ControlPointType.BEACON)
        RaceType.ORIENTEERING -> listOf(ControlPointType.CONTROL)
    }

private fun controlRoleLabel(type: ControlPointType, raceType: RaceType): String =
    when {
        raceType == RaceType.ORIENTEERING && type == ControlPointType.CONTROL -> "Control"
        type == ControlPointType.CONTROL -> "Fox"
        else -> EventControlDetails.typeLabel(type)
    }

/** Shows editable category names with read-only effective race settings. */
@Composable
private fun CategoryDetailsPanel(
    categories: List<EventCategoryDetails>,
    controls: List<EventControlDetails>,
    onRenameCategory: (String, String) -> Unit,
    onUpdateCategoryControlPoints: (String, String, Boolean) -> Unit,
    onUpdateCategoryPhysicalStats: (String, String, String) -> Unit,
    onAddCategory: (String) -> Boolean,
    onRemoveCategory: (String, Boolean) -> Unit
) {
    val horizontalScrollState = rememberScrollState()
    val tableWidth = fixedTableWidth(CategoryTableColumns)
    val orderedCategories = rememberEditableRowOrder(categories) { it.id }
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
                    FixedDetailHeaderRow(CategoryTableColumns, CategoryTableColumnHints)
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
                orderedCategories.forEach { category ->
                    key(category.id) {
                        CategoryDeleteButton(category, onRemoveCategory)
                    }
                }
            }
            Box(modifier = Modifier.weight(1f).horizontalScroll(horizontalScrollState)) {
                Column(
                    modifier = Modifier.width(tableWidth),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    orderedCategories.forEach { category ->
                        key(category.id) {
                            CategoryDetailRow(
                                category = category,
                                controls = controls,
                                onRenameCategory = onRenameCategory,
                                onUpdateCategoryControlPoints = onUpdateCategoryControlPoints,
                                onUpdateCategoryPhysicalStats = onUpdateCategoryPhysicalStats
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProtectedCourseOrderPanel(
    projectFile: EventProjectFile,
    isUnlocked: Boolean,
    idealOrderByCategoryId: Map<String, String>,
    protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>,
    onUnlock: (String) -> Boolean,
    onUpdateIdealOrder: (String, String) -> Unit,
    onUpdateControlLocation: (String, String, String) -> String
) {
    var passwordDraft by remember(projectFile.raceData.race.id, isUnlocked) { mutableStateOf("") }
    var locationStatusText by remember(projectFile.raceData.race.id, isUnlocked) { mutableStateOf<String?>(null) }

    if (!isUnlocked) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = passwordDraft,
                onValueChange = { passwordDraft = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.width(260.dp)
            )
            Button(
                onClick = {
                    if (onUnlock(passwordDraft)) {
                        passwordDraft = ""
                    }
                },
                enabled = passwordDraft.isNotBlank()
            ) {
                ButtonLabel("Unlock")
            }
        }
        return
    }

    val categories = projectFile.raceData.categories
        .sortedWith(EventCategorySort.byDisplayName)
    var idealOrderDrafts by remember(projectFile.raceData.race.id, idealOrderByCategoryId) {
        mutableStateOf(
            categories.associate { categoryData ->
                categoryData.category.id to idealOrderByCategoryId[categoryData.category.id].orEmpty()
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ProtectedControlLocationUpdatePanel(
            projectFile = projectFile,
            protectedCourseInfoByCategoryId = protectedCourseInfoByCategoryId,
            statusText = locationStatusText,
            onStatusTextChange = { locationStatusText = it },
            onUpdateControlLocation = onUpdateControlLocation
        )
        Box(
            modifier = Modifier
                .width(fixedTableWidth(ProtectedCourseOrderTableColumns))
                .height(1.dp)
                .background(DesktopPalette.LightGrey)
        )
        FixedDetailHeaderRow(ProtectedCourseOrderTableColumns)
        categories.forEach { categoryData ->
            val categoryId = categoryData.category.id
            val assignedIdealOrderControls = assignedProtectedIdealOrderControls(projectFile, categoryId)
            ProtectedCourseOrderRow(
                categoryName = categoryData.category.name,
                idealOrderDraft = idealOrderDrafts[categoryId].orEmpty(),
                assignedControls = assignedIdealOrderControls,
                onIdealOrderChange = { idealOrderText ->
                    idealOrderDrafts = idealOrderDrafts + (categoryId to idealOrderText)
                    onUpdateIdealOrder(categoryId, idealOrderText)
                }
            )
        }
    }
}

@Composable
private fun ProtectedControlLocationUpdatePanel(
    projectFile: EventProjectFile,
    protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>,
    statusText: String?,
    onStatusTextChange: (String?) -> Unit,
    onUpdateControlLocation: (String, String, String) -> String
) {
    val summaries = remember(projectFile.raceData.controls, protectedCourseInfoByCategoryId) {
        protectedControlLocationSummaries(projectFile, protectedCourseInfoByCategoryId)
    }
    var selectedControlId by remember(projectFile.raceData.race.id, summaries.map { it.controlId }) {
        mutableStateOf(summaries.firstOrNull()?.controlId)
    }
    val selectedSummary = summaries.firstOrNull { it.controlId == selectedControlId }
    var latitudeDraft by remember(projectFile.raceData.race.id, selectedControlId, selectedSummary?.latitude) {
        mutableStateOf(selectedSummary?.latitude?.decimalText().orEmpty())
    }
    var longitudeDraft by remember(projectFile.raceData.race.id, selectedControlId, selectedSummary?.longitude) {
        mutableStateOf(selectedSummary?.longitude?.decimalText().orEmpty())
    }
    val parsedLatitude = latitudeDraft.trim().toDoubleOrNull()
    val parsedLongitude = longitudeDraft.trim().toDoubleOrNull()
    val canApply = selectedControlId != null &&
        parsedLatitude != null &&
        parsedLatitude in -90.0..90.0 &&
        parsedLongitude != null &&
        parsedLongitude in -180.0..180.0

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Update control location",
            color = DesktopPalette.Black,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProtectedControlLocationPicker(
                selectedControlId = selectedControlId,
                summaries = summaries,
                onControlSelected = { controlId ->
                    selectedControlId = controlId
                    onStatusTextChange(null)
                },
                modifier = Modifier.width(260.dp)
            )
            TextField(
                value = latitudeDraft,
                onValueChange = { latitudeDraft = it },
                label = { Text("Latitude") },
                singleLine = true,
                modifier = Modifier.width(150.dp)
            )
            TextField(
                value = longitudeDraft,
                onValueChange = { longitudeDraft = it },
                label = { Text("Longitude") },
                singleLine = true,
                modifier = Modifier.width(150.dp)
            )
            Button(
                onClick = {
                    val controlId = selectedControlId ?: return@Button
                    onStatusTextChange(onUpdateControlLocation(controlId, latitudeDraft, longitudeDraft))
                },
                enabled = canApply
            ) {
                ButtonLabel("Update Location")
            }
        }
        selectedSummary?.let { summary ->
            Text(
                text = "Stored courses using this control: ${summary.affectedCategoryCount}",
                color = DesktopPalette.Disconnected,
                fontSize = 13.sp
            )
        }
        statusText?.let { text ->
            Text(
                text = text,
                color = if (text.startsWith("Control location update failed")) DesktopPalette.Error else DesktopPalette.Disconnected,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun ProtectedControlLocationPicker(
    selectedControlId: String?,
    summaries: List<ProtectedControlLocationSummary>,
    onControlSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = summaries.firstOrNull { it.controlId == selectedControlId }?.label ?: "Select control"
    Box(modifier = modifier) {
        Button(
            onClick = { expanded = true },
            enabled = summaries.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(selectedLabel)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            summaries.forEach { summary ->
                DropdownMenuItem(
                    onClick = {
                        expanded = false
                        onControlSelected(summary.controlId)
                    }
                ) {
                    Text(summary.label)
                }
            }
        }
    }
}

@Composable
private fun ProtectedCourseOrderRow(
    categoryName: String,
    idealOrderDraft: String,
    assignedControls: List<EventControl>,
    onIdealOrderChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.width(fixedTableWidth(ProtectedCourseOrderTableColumns)),
        horizontalArrangement = Arrangement.spacedBy(TableColumnGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = categoryName,
            modifier = Modifier.width(ProtectedCourseOrderTableColumns[0].width),
            color = DesktopPalette.Black,
            fontSize = 13.sp
        )
        ProtectedIdealOrderEditor(
            idealOrderDraft = idealOrderDraft,
            assignedControls = assignedControls,
            onIdealOrderChange = onIdealOrderChange,
            modifier = Modifier.width(ProtectedCourseOrderTableColumns[1].width)
        )
    }
}

@Composable
private fun ProtectedIdealOrderEditor(
    idealOrderDraft: String,
    assignedControls: List<EventControl>,
    onIdealOrderChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val pickerControls = remember(assignedControls) {
        assignedControls
            .filter { it.type == ControlPointType.CONTROL || it.type == ControlPointType.BEACON }
            .filter { isPickerSafePublicLabel(it.publicDisplayLabel()) }
            .groupBy { it.publicDisplayLabel() }
            .filterValues { controlsForLabel -> controlsForLabel.size == 1 }
            .values
            .map { controlsForLabel -> controlsForLabel.single() }
            .sortedWith(compareBy<EventControl> { it.siCode }.thenBy { it.type.name }.thenBy { it.publicDisplayLabel() })
    }
    val selectedControlIds = remember(idealOrderDraft, assignedControls) {
        selectedProtectedIdealOrderControlIds(idealOrderDraft, assignedControls)
    }
    val availablePickerControls = remember(pickerControls, selectedControlIds) {
        pickerControls.filter { control -> control.id !in selectedControlIds }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(TableColumnGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = idealOrderDraft,
            onValueChange = onIdealOrderChange,
            enabled = assignedControls.isNotEmpty(),
            modifier = Modifier.width(ProtectedIdealOrderTextFieldWidth),
            singleLine = true,
            label = { Text("Ideal order") }
        )
        Box(modifier = Modifier.width(ProtectedIdealOrderPickerWidth)) {
            Button(
                onClick = { expanded = true },
                enabled = availablePickerControls.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                ButtonLabel("Pick")
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                availablePickerControls.forEach { control ->
                    val publicLabel = control.publicDisplayLabel()
                    DropdownMenuItem(
                        onClick = {
                            onIdealOrderChange(appendPublicControlLabel(idealOrderDraft, publicLabel))
                            if (availablePickerControls.size <= 1) {
                                expanded = false
                            }
                        }
                    ) {
                        Text(publicLabel)
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
    }
}

/** Shows one editable category-name row plus read-only derived category settings. */
@Composable
private fun CategoryDetailRow(
    category: EventCategoryDetails,
    controls: List<EventControlDetails>,
    onRenameCategory: (String, String) -> Unit,
    onUpdateCategoryControlPoints: (String, String, Boolean) -> Unit,
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
    fun applyCategoryNameDraft() {
        if (categoryNameDraft != category.name && categoryNameDraft.isNotBlank()) {
            onRenameCategory(category.id, categoryNameDraft)
        }
    }
    fun applyPhysicalStats(nextLength: String = lengthMetersDraft, nextClimb: String = climbMetersDraft) {
        if (
            nextLength.trim().toIntOrNull() != null &&
            nextClimb.trim().toIntOrNull() != null &&
            (nextLength != category.lengthMetersText || nextClimb != category.climbMetersText)
        ) {
            onUpdateCategoryPhysicalStats(category.id, nextLength, nextClimb)
        }
    }
    val applyLatestCategoryNameDraft by rememberUpdatedState(::applyCategoryNameDraft)
    DisposableEffect(category.id) {
        onDispose { applyLatestCategoryNameDraft() }
    }
    Row(
        modifier = Modifier.width(fixedTableWidth(CategoryTableColumns)),
        horizontalArrangement = Arrangement.spacedBy(TableColumnGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = categoryNameDraft,
            onValueChange = { categoryNameDraft = it },
            modifier = Modifier
                .width(CategoryTableColumns[0].width)
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused) {
                        applyCategoryNameDraft()
                    }
                },
            singleLine = true,
            label = { Text("Category") }
        )
        TextField(
            value = lengthMetersDraft,
            onValueChange = {
                lengthMetersDraft = it
                applyPhysicalStats(nextLength = it)
            },
            modifier = Modifier.width(CategoryTableColumns[1].width),
            singleLine = true,
            label = { Text("Length m") }
        )
        TextField(
            value = climbMetersDraft,
            onValueChange = {
                climbMetersDraft = it
                applyPhysicalStats(nextClimb = it)
            },
            modifier = Modifier.width(CategoryTableColumns[2].width),
            singleLine = true,
            label = { Text("Climb m") }
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
            desktopTimeLimitText(category.timeLimitText),
            modifier = Modifier.width(CategoryTableColumns[5].width),
            color = DesktopPalette.Black,
            fontSize = 13.sp
        )
        AssignedControlsEditor(
            controlPointsDraft = controlPointsDraft,
            controls = controls,
            onControlPointsDraftChange = {
                controlPointsDraft = it
            },
            onControlPointsCommit = { text, shouldCheckRequiredControls ->
                controlPointsDraft = text
                onUpdateCategoryControlPoints(category.id, text, shouldCheckRequiredControls)
            },
            modifier = Modifier.width(CategoryTableColumns[6].width)
        )
    }
}

@Composable
private fun AssignedControlsEditor(
    controlPointsDraft: String,
    controls: List<EventControlDetails>,
    onControlPointsDraftChange: (String) -> Unit,
    onControlPointsCommit: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var hasPendingTextEdit by remember { mutableStateOf(false) }
    var pendingPickerText by remember { mutableStateOf<String?>(null) }
    val publicLabelControls = remember(controls) {
        controls
            .filter { isPickerSafePublicLabel(it.publicDisplayLabel()) }
            .groupBy { it.publicDisplayLabel() }
            .filterValues { controlsForLabel -> controlsForLabel.size == 1 }
            .values
            .map { controlsForLabel -> controlsForLabel.single() }
            .sortedWith(compareBy<EventControlDetails> { it.publicDisplayLabel().lowercase() }.thenBy { it.siCode })
    }
    val selectedPublicLabels = remember(controlPointsDraft, controls) {
        selectedPublicControlLabels(controlPointsDraft, controls)
    }
    val availablePublicLabelControls = remember(publicLabelControls, selectedPublicLabels) {
        publicLabelControls.filter { control -> control.publicDisplayLabel() !in selectedPublicLabels }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(TableColumnGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = controlPointsDraft,
            onValueChange = {
                hasPendingTextEdit = true
                onControlPointsDraftChange(it)
            },
            modifier = Modifier
                .width(232.dp)
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused && hasPendingTextEdit) {
                        hasPendingTextEdit = false
                        onControlPointsCommit(controlPointsDraft, true)
                    }
                },
            singleLine = true,
            label = { Text("Assigned") }
        )
        Box(modifier = Modifier.width(76.dp)) {
            Button(
                onClick = { expanded = true },
                enabled = availablePublicLabelControls.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                ButtonLabel("Pick")
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = {
                    expanded = false
                    pendingPickerText?.let { text ->
                        pendingPickerText = null
                        onControlPointsCommit(text, true)
                    }
                }
            ) {
                availablePublicLabelControls.forEach { control ->
                    val publicLabel = control.publicDisplayLabel()
                    DropdownMenuItem(
                        onClick = {
                            val nextText = appendPublicControlLabel(controlPointsDraft, publicLabel)
                            pendingPickerText = nextText
                            onControlPointsDraftChange(nextText)
                            onControlPointsCommit(nextText, false)
                            if (availablePublicLabelControls.size <= 1) {
                                expanded = false
                                pendingPickerText = null
                                onControlPointsCommit(nextText, true)
                            }
                        }
                    ) {
                        Text(publicLabel)
                    }
                }
            }
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

/** Shows editable race metadata backed by shared Event File editing rules. */
@Composable
private fun RaceDetailsPanel(
    details: EventRaceDetails,
    eventFilePath: Path?,
    onRenameRace: (String) -> Unit,
    onUpdateRaceStartDateTime: (String) -> Unit,
    onUpdateRaceSettings: (RaceType, RaceLevel, RaceBand, String) -> Unit,
    onUpdateEventFileName: (String) -> Boolean
) {
    var raceNameDraft by remember(details.name) { mutableStateOf(details.name) }
    var startDateTimeDraft by remember(details.startDateTimeIso) {
        mutableStateOf(
            DesktopDateTimeText.parseIsoOrNull(details.startDateTimeIso) ?: DesktopDateTimeText.defaultStartDateTime()
        )
    }
    var selectedRaceFormat by remember(details.raceType, details.raceBand) {
        mutableStateOf(DesktopRaceFormat.from(details.raceType, details.raceBand))
    }
    var selectedRaceLevel by remember(details.raceLevel) { mutableStateOf(details.raceLevel) }
    var timeLimitMinutesDraft by remember(details.timeLimitMinutesText) {
        mutableStateOf(details.timeLimitMinutesText)
    }
    var wasRaceNameFocused by remember { mutableStateOf(false) }
    var shouldPromptForEventStartAfterNameEdit by remember { mutableStateOf(false) }
    var isEventStartPromptVisible by remember { mutableStateOf(false) }
    var eventStartPromptReason by remember { mutableStateOf("event definition") }
    val currentEventFileName = eventFilePath?.fileName?.toString()
    var eventFileNameDraft by remember(currentEventFileName, details.name) {
        mutableStateOf(
            currentEventFileName
                ?.let(DesktopProjectFilePaths::projectFileDisplayStem)
                ?: DesktopProjectFilePaths.defaultProjectFileName(details.name)
                    .let(DesktopProjectFilePaths::projectFileDisplayStem)
        )
    }
    var hasEventFileNameDraftChanged by remember(currentEventFileName) { mutableStateOf(false) }
    var wasEventFileNameFocused by remember { mutableStateOf(false) }

    fun applyRaceSettings(
        raceType: RaceType = selectedRaceFormat.raceType,
        raceLevel: RaceLevel = selectedRaceLevel,
        raceBand: RaceBand = selectedRaceFormat.raceBand,
        timeLimitMinutes: String = timeLimitMinutesDraft
    ) {
        val timeLimit = timeLimitMinutes.trim().toLongOrNull()
        if (timeLimit != null && timeLimit >= 0) {
            onUpdateRaceSettings(raceType, raceLevel, raceBand, timeLimitMinutes)
        }
    }

    fun promptForEventStart(reason: String) {
        eventStartPromptReason = reason
        isEventStartPromptVisible = true
    }

    fun commitEventFileNameDraft() {
        if (!hasEventFileNameDraftChanged) {
            return
        }
        val fallbackName = currentEventFileName ?: DesktopProjectFilePaths.defaultProjectFileName(details.name)
        val fallbackDisplayName = DesktopProjectFilePaths.projectFileDisplayStem(fallbackName)
        val normalizedFileName = eventFileNameDraft
            .takeIf { it.isNotBlank() }
            ?.let(DesktopProjectFilePaths::defaultProjectFileName)
            ?: fallbackName
        if (normalizedFileName == currentEventFileName) {
            eventFileNameDraft = fallbackDisplayName
            hasEventFileNameDraftChanged = false
            return
        }
        hasEventFileNameDraftChanged = false
        val didSave = onUpdateEventFileName(normalizedFileName)
        eventFileNameDraft = DesktopProjectFilePaths.projectFileDisplayStem(
            if (didSave) normalizedFileName else fallbackName
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = raceNameDraft,
                onValueChange = {
                    raceNameDraft = it
                    onRenameRace(it)
                    if (it.trim().isNotEmpty() && it.trim() != details.name.trim()) {
                        shouldPromptForEventStartAfterNameEdit = true
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focusState ->
                        if (wasRaceNameFocused && !focusState.isFocused && shouldPromptForEventStartAfterNameEdit) {
                            shouldPromptForEventStartAfterNameEdit = false
                            promptForEventStart("event name")
                        }
                        wasRaceNameFocused = focusState.isFocused
                    },
                label = { Text("Event name") }
            )
        }
        TextField(
            value = eventFileNameDraft,
            onValueChange = {
                eventFileNameDraft = it
                hasEventFileNameDraftChanged = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (wasEventFileNameFocused && !focusState.isFocused) {
                        commitEventFileNameDraft()
                    }
                    wasEventFileNameFocused = focusState.isFocused
                }
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.Enter) {
                        if (event.type == KeyEventType.KeyUp) {
                            commitEventFileNameDraft()
                        }
                        true
                    } else {
                        false
                    }
                },
            label = { Text("Event file name") }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DateTimePickerField(
                label = "Event Start date/time",
                value = startDateTimeDraft,
                onValueChange = {
                    startDateTimeDraft = it
                    onUpdateRaceStartDateTime(DesktopDateTimeText.isoText(it))
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            text = "Used for Race Ops elapsed time, in-forest status, finish/result timestamps, and exports. Set this to the event's actual start, not the file creation time.",
            color = DesktopPalette.Disconnected,
            fontSize = 13.sp
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DesktopRaceFormatPicker(
                selectedRaceFormat,
                {
                    val changed = it != selectedRaceFormat
                    selectedRaceFormat = it
                    applyRaceSettings(raceType = it.raceType, raceBand = it.raceBand)
                    if (changed) {
                        promptForEventStart("event format")
                    }
                },
                Modifier.weight(1f)
            )
            RaceLevelPicker(
                selectedRaceLevel,
                {
                    val changed = it != selectedRaceLevel
                    val defaultLimitMinutes = it.defaultTimeLimitMinutes()?.toString()
                    val nextTimeLimitMinutes = defaultLimitMinutes ?: timeLimitMinutesDraft
                    selectedRaceLevel = it
                    if (defaultLimitMinutes != null) {
                        timeLimitMinutesDraft = nextTimeLimitMinutes
                    }
                    applyRaceSettings(raceLevel = it, timeLimitMinutes = nextTimeLimitMinutes)
                    if (changed) {
                        promptForEventStart("event type")
                    }
                },
                Modifier.weight(1f)
            )
            TextField(
                value = timeLimitMinutesDraft,
                onValueChange = {
                    timeLimitMinutesDraft = it
                    applyRaceSettings(timeLimitMinutes = it)
                },
                modifier = Modifier.weight(1f),
                label = { Text("Limit (min.)") }
            )
        }
    }

    if (isEventStartPromptVisible) {
        DateTimePickerDialog(
            initialValue = startDateTimeDraft,
            title = "Enter Event Start date/time",
            description = "The $eventStartPromptReason changed. Confirm or update the Event Start date/time for this event.",
            onValueSelected = {
                isEventStartPromptVisible = false
                startDateTimeDraft = it
                onUpdateRaceStartDateTime(DesktopDateTimeText.isoText(it))
            },
            onDismiss = { isEventStartPromptVisible = false }
        )
    }
}

@Composable
private fun DateTimePickerField(
    label: String,
    value: LocalDateTime,
    onValueChange: (LocalDateTime) -> Unit,
    modifier: Modifier = Modifier
) {
    var isPickerOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            TextField(
                value = DesktopDateTimeText.displayText(value),
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(label) }
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable { isPickerOpen = true }
            )
        }
    }
    if (isPickerOpen) {
        DateTimePickerDialog(
            initialValue = value,
            onValueSelected = {
                isPickerOpen = false
                onValueChange(it)
            },
            onDismiss = { isPickerOpen = false }
        )
    }
}

@Composable
private fun DateTimePickerDialog(
    initialValue: LocalDateTime,
    title: String = "Pick Event Start date/time",
    description: String? = null,
    onValueSelected: (LocalDateTime) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedDate by remember(initialValue) { mutableStateOf(initialValue.toLocalDate()) }
    var visibleMonth by remember(initialValue) { mutableStateOf(YearMonth.from(initialValue)) }
    var hourText by remember(initialValue) { mutableStateOf(initialValue.hour.toString().padStart(2, '0')) }
    var minuteText by remember(initialValue) { mutableStateOf(initialValue.minute.toString().padStart(2, '0')) }
    var secondText by remember(initialValue) { mutableStateOf(initialValue.second.toString().padStart(2, '0')) }

    fun setFromDateTime(value: LocalDateTime) {
        selectedDate = value.toLocalDate()
        visibleMonth = YearMonth.from(selectedDate)
        hourText = value.hour.toString().padStart(2, '0')
        minuteText = value.minute.toString().padStart(2, '0')
        secondText = value.second.toString().padStart(2, '0')
    }

    val selectedDateTime = DesktopDateTimeText.parseOrNull(
        selectedDate.toString(),
        "$hourText:$minuteText:$secondText"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                description?.let {
                    Text(
                        text = it,
                        color = DesktopPalette.Disconnected,
                        fontSize = 13.sp
                    )
                }
                CalendarMonthPicker(
                    visibleMonth = visibleMonth,
                    selectedDate = selectedDate,
                    onPreviousMonth = { visibleMonth = visibleMonth.minusMonths(1) },
                    onNextMonth = { visibleMonth = visibleMonth.plusMonths(1) },
                    onDateSelected = { selectedDate = it }
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = hourText,
                        onValueChange = { hourText = it.take(2) },
                        modifier = Modifier.width(64.dp),
                        label = { Text("Hour") }
                    )
                    TextField(
                        value = minuteText,
                        onValueChange = { minuteText = it.take(2) },
                        modifier = Modifier.width(64.dp),
                        label = { Text("Min") }
                    )
                    TextField(
                        value = secondText,
                        onValueChange = { secondText = it.take(2) },
                        modifier = Modifier.width(64.dp),
                        label = { Text("Sec") }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { setFromDateTime(DesktopDateTimeText.defaultStartDateTime()) }) {
                        Text("Now")
                    }
                    Button(
                        onClick = { selectedDateTime?.minusHours(1)?.let(::setFromDateTime) },
                        enabled = selectedDateTime != null
                    ) {
                        Text("-1 hr")
                    }
                    Button(
                        onClick = { selectedDateTime?.plusHours(1)?.let(::setFromDateTime) },
                        enabled = selectedDateTime != null
                    ) {
                        Text("+1 hr")
                    }
                    Button(
                        onClick = { selectedDateTime?.plusMinutes(15)?.let(::setFromDateTime) },
                        enabled = selectedDateTime != null
                    ) {
                        Text("+15 min")
                    }
                }
                if (selectedDateTime == null) {
                    Text("Enter a valid 24-hour time.", color = DesktopPalette.Error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { selectedDateTime?.let(onValueSelected) },
                enabled = selectedDateTime != null
            ) {
                Text("Use")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun CalendarMonthPicker(
    visibleMonth: YearMonth,
    selectedDate: LocalDate,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDateSelected: (LocalDate) -> Unit
) {
    val dayCellWidth = 50.dp
    val dayCellHeight = 36.dp

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onPreviousMonth) {
                Text("<")
            }
            Text(
                text = "${visibleMonth.month.name.lowercase().replaceFirstChar(Char::titlecase)} ${visibleMonth.year}",
                fontWeight = FontWeight.Bold
            )
            Button(onClick = onNextMonth) {
                Text(">")
            }
        }
        CalendarWeekRow(listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")) { label ->
            Text(
                text = label,
                modifier = Modifier.width(dayCellWidth),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        val firstDayOffset = visibleMonth.atDay(1).dayOfWeek.value - 1
        val dayCells = List<LocalDate?>(firstDayOffset) { null } +
                (1..visibleMonth.lengthOfMonth()).map { visibleMonth.atDay(it) }
        dayCells.chunked(7).forEach { week ->
            CalendarWeekRow(week) { date ->
                if (date == null) {
                    Spacer(modifier = Modifier.width(dayCellWidth).height(dayCellHeight))
                } else {
                    Button(
                        onClick = { onDateSelected(date) },
                        modifier = Modifier.width(dayCellWidth).height(dayCellHeight),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = if (date.isEqual(selectedDate)) {
                                DesktopPalette.SecondaryVariant
                            } else {
                                MaterialTheme.colors.primary
                            },
                            contentColor = Color.White
                        )
                    ) {
                        Text(date.dayOfMonth.toString(), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> CalendarWeekRow(values: List<T>, content: @Composable (T) -> Unit) {
    val dayCellWidth = 50.dp
    val dayCellHeight = 36.dp

    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        values.forEach { value ->
            content(value)
        }
        repeat(7 - values.size) {
            Spacer(modifier = Modifier.width(dayCellWidth).height(dayCellHeight))
        }
    }
}

/** Shows desktop race format choices that determine both race type and band. */
@Composable
private fun DesktopRaceFormatPicker(
    selectedRaceFormat: DesktopRaceFormat,
    onRaceFormatSelected: (DesktopRaceFormat) -> Unit,
    modifier: Modifier = Modifier
) {
    EnumPicker(
        selectedValue = selectedRaceFormat,
        values = DesktopRaceFormat.selectableEntries,
        label = { it.label },
        onValueSelected = onRaceFormatSelected,
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
private fun FixedDetailHeaderRow(
    columns: List<FixedTableColumn>,
    tooltipByTitle: Map<String, String> = emptyMap()
) {
    Row(
        modifier = Modifier.width(fixedTableWidth(columns)),
        horizontalArrangement = Arrangement.spacedBy(TableColumnGap)
    ) {
        columns.forEach { column ->
            FixedDetailHeaderCell(column, tooltipByTitle[column.title])
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun FixedDetailHeaderCell(column: FixedTableColumn, tooltipText: String?) {
    val header: @Composable () -> Unit = {
        Text(
            text = column.title,
            modifier = Modifier.width(column.width),
            color = DesktopPalette.Disconnected,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
    if (tooltipText == null || column.title.isBlank()) {
        header()
    } else {
        TooltipArea(
            tooltip = {
                Surface(
                    color = DesktopPalette.PrimaryVariant,
                    contentColor = DesktopPalette.White,
                    elevation = 4.dp
                ) {
                    Text(
                        text = tooltipText,
                        modifier = Modifier.width(280.dp).padding(8.dp),
                        fontSize = 12.sp
                    )
                }
            },
            delayMillis = 2350
        ) {
            header()
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
private fun FixedTableText(text: String, width: Dp, color: Color = DesktopPalette.Black) {
    Text(
        text = text,
        modifier = Modifier.width(width),
        color = color,
        fontSize = 13.sp,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis
    )
}

private fun appendPublicControlLabel(controlPointsText: String, publicLabel: String): String {
    val token = pickerControlToken(publicLabel)
    val existingText = controlPointsText.trim()
    return if (existingText.isEmpty()) token else "$existingText $token"
}

private fun appendReadoutPunchLine(controlPunchesText: String, punchLine: String): String {
    val existingText = controlPunchesText.trim()
    return if (existingText.isEmpty()) punchLine else "$existingText\n$punchLine"
}

private fun String.usedPunchControlKeys(): Set<String> =
    lines()
        .flatMap { line -> line.split(";") }
        .flatMap { entry ->
            val controlText = entry.substringBefore("@").trim()
            val numericTokens = controlText.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (
                "@" !in entry &&
                numericTokens.size > 1 &&
                numericTokens.all { it.toIntOrNull() != null }
            ) {
                numericTokens
            } else {
                listOf(controlText)
            }
        }
        .map { it.normalizedReadoutControlToken() }
        .filter { it.isNotEmpty() }
        .toSet()

private fun EventControl.editPunchToken(allControls: List<EventControl>): String {
    val publicLabel = publicDisplayLabel()
    val publicLabelKey = publicLabel.normalizedReadoutControlToken()
    val isAmbiguous = allControls
        .filter { control -> publicLabelKey in control.entryKeys() }
        .map { it.siCode }
        .distinct()
        .size > 1
    return if (isAmbiguous) siCode.toString() else publicLabel
}

private fun EventControl.entryKeys(): Set<String> =
    buildSet {
        add(siCode.toString().normalizedReadoutControlToken())
        add(label.normalizedReadoutControlToken())
        add(publicDisplayLabel().normalizedReadoutControlToken())
        publicLabel?.takeIf { it.isNotBlank() }?.let { add(it.normalizedReadoutControlToken()) }
    }

private fun String.normalizedReadoutControlToken(): String =
    trim().lowercase().replace(Regex("\\s+"), " ")

private fun selectedPublicControlLabels(
    controlPointsText: String,
    controls: List<EventControlDetails>
): Set<String> {
    val uniqueControlsByPublicLabel = controls
        .filter { isPickerSafePublicLabel(it.publicDisplayLabel()) }
        .groupBy { it.publicDisplayLabel() }
        .filterValues { controlsForLabel -> controlsForLabel.size == 1 }
        .mapValues { (_, controlsForLabel) -> controlsForLabel.single() }
    return ControlPointRules.tokenizeControlPoints(controlPointsText)
        .mapNotNull { token -> uniqueControlsByPublicLabel[token.trim()]?.publicDisplayLabel() }
        .toSet()
}

private fun selectedProtectedIdealOrderControlIds(
    idealOrderText: String,
    controls: List<EventControl>
): Set<String> =
    runCatching {
        ProtectedIdealOrderRules.resolveControlIds(idealOrderText, controls).toSet()
    }.getOrDefault(emptySet())

private fun EventProjectFile.hasLockedProtectedCourseData(isProtectedCourseOrderUnlocked: Boolean): Boolean =
    !isProtectedCourseOrderUnlocked &&
        raceData.categories.any { it.category.encryptedCourseInfo?.isNotBlank() == true }

private fun EventProjectFile.hasProtectedCategoryData(): Boolean =
    raceData.categories.any {
        it.category.encryptedIdealOrder?.isNotBlank() == true ||
            it.category.encryptedCourseInfo?.isNotBlank() == true
    }

private fun EventProjectFile.categoryHasLockedProtectedCourseData(
    categoryId: String,
    isProtectedCourseOrderUnlocked: Boolean
): Boolean =
    !isProtectedCourseOrderUnlocked &&
        raceData.categories.any {
            it.category.id == categoryId && it.category.encryptedCourseInfo?.isNotBlank() == true
        }

private fun EventProjectFile.lockedProtectedCourseWarning(isProtectedCourseOrderUnlocked: Boolean): String =
    if (hasLockedProtectedCourseData(isProtectedCourseOrderUnlocked)) {
        " Course data is locked; unlock it to run full protected-route safety checks."
    } else {
        ""
    }

private fun EventProjectFile.lockedCategoryCourseWarning(
    categoryId: String,
    isProtectedCourseOrderUnlocked: Boolean
): String =
    if (categoryHasLockedProtectedCourseData(categoryId, isProtectedCourseOrderUnlocked)) {
        " This category has locked protected course data; unlock it to compare stored route data."
    } else {
        ""
    }

private fun EventProjectFile.hasRecordedReadoutsOrResults(): Boolean =
    raceData.competitorData.any { it.readoutData != null } || raceData.unmatchedReadoutData.isNotEmpty()

private fun EventProjectFile.resultImpactWarning(action: String): String =
    if (hasRecordedReadoutsOrResults()) {
        " $action after readouts exist; review or recalculate affected results."
    } else {
        ""
    }

private fun raceOpsPreflightWarning(
    projectFile: EventProjectFile,
    protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>,
    isProtectedCourseOrderUnlocked: Boolean
): String? {
    val activeIssues = DesktopProjectDiagnostics.from(projectFile, protectedCourseInfoByCategoryId)
        .readinessIssues
        .filterNot { it.contains("has course data but no competitors") }
        .toMutableList()
    if (projectFile.hasLockedProtectedCourseData(isProtectedCourseOrderUnlocked)) {
        activeIssues += "Course data is locked; unlock it to run full protected-route safety checks."
    }
    return activeIssues
        .takeIf { it.isNotEmpty() }
        ?.let { issues ->
            val suffix = if (issues.size > 1) " ${issues.size - 1} more readiness issues." else ""
            "Readiness warning: ${issues.first()}$suffix"
        }
}

private fun protectedControlLocationSummaries(
    projectFile: EventProjectFile,
    protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>
): List<ProtectedControlLocationSummary> {
    val protectedControlPointsById = protectedCourseInfoByCategoryId.values
        .flatMap { it.controlPoints }
        .groupBy { it.controlId }
    val affectedCategoryCounts = projectFile.raceData.controls.associate { control ->
        control.id to protectedCourseInfoByCategoryId.values.count { courseInfo ->
            courseInfo.controlPoints.any { it.controlId == control.id } ||
                courseInfo.courseObjects.any { it.id == control.id }
        }
    }
    return projectFile.raceData.controls
        .sortedWith(compareBy<EventControl> { it.siCode }.thenBy { it.publicDisplayLabel() })
        .map { control ->
            val protectedPoint = protectedControlPointsById[control.id]?.firstOrNull()
            ProtectedControlLocationSummary(
                controlId = control.id,
                label = control.publicDisplayLabel().ifBlank { control.siCode.toString() },
                latitude = protectedPoint?.latitude,
                longitude = protectedPoint?.longitude,
                affectedCategoryCount = affectedCategoryCounts[control.id] ?: 0
            )
        }
}

private fun EventControlDetails.publicDisplayLabel(): String =
    publicLabel.trim().ifEmpty { label }

private fun EventControl.publicDisplayLabel(): String =
    publicLabel?.trim()?.takeIf { it.isNotEmpty() } ?: label

private fun pickerControlToken(publicLabel: String): String {
    val label = publicLabel.trim()
    val needsQuoting = label.any { it.isWhitespace() || it == ',' || it == ';' }
    return if (needsQuoting) "'$label'" else label
}

private fun isPickerSafePublicLabel(publicLabel: String): Boolean {
    val label = publicLabel.trim()
    return label.isNotEmpty() && '\'' !in label && '"' !in label
}

@Suppress("DEPRECATION")
private fun assignedProtectedIdealOrderControls(
    projectFile: EventProjectFile,
    categoryId: String
): List<EventControl> {
    val categoryData = projectFile.raceData.categories.firstOrNull { it.category.id == categoryId }
        ?: return emptyList()
    val controlsById = projectFile.raceData.controls.associateBy { it.id }
    val categoryControlPoints = if (categoryData.controlPoints.isNotEmpty()) {
        categoryData.controlPoints
    } else {
        categoryData.publicControlIds.mapIndexedNotNull { index, controlId ->
            val control = controlsById[controlId] ?: return@mapIndexedNotNull null
            org.openardf.radiooracle.shared.event.EventControlPoint(
                id = "public-$controlId",
                categoryId = categoryId,
                siCode = control.siCode,
                type = control.type,
                order = index + 1,
                controlId = control.id
            )
        }
    }
    return categoryControlPoints
        .map { controlPoint ->
            controlsById[controlPoint.controlId]
                ?: EventControlCatalog.controlForDefinition(
                    projectFile.raceData.race.id,
                    org.openardf.radiooracle.shared.course.ControlPointDefinition(
                        siCode = controlPoint.siCode,
                        type = controlPoint.type,
                        order = controlPoint.order
                    )
                )
        }
        .filter { it.type == ControlPointType.CONTROL || it.type == ControlPointType.BEACON }
        .distinctBy { it.id }
}

/**
 * Keeps editable detail-table rows in the same on-screen order while a menu is open.
 *
 * Several setup tables are initially sorted by values the user is allowed to edit,
 * such as SI code, start number, competitor name, or category name. If the table
 * renders directly from the freshly sorted model after every commit, a valid edit
 * can move the row out from under the user and make adjacent fields appear to
 * change unexpectedly. This helper preserves the current row-id order for the
 * active editing session, removes deleted rows, and appends newly added rows in
 * the order supplied by the model.
 */
@Composable
private fun <T> rememberEditableRowOrder(items: List<T>, itemId: (T) -> String): List<T> {
    var rowOrder by remember { mutableStateOf<List<String>>(emptyList()) }
    val incomingIds = items.map(itemId)

    LaunchedEffect(incomingIds) {
        rowOrder = rowOrder.filter { it in incomingIds } +
            incomingIds.filterNot { it in rowOrder }
    }

    val activeOrder = rowOrder.ifEmpty { incomingIds }
    val itemsById = items.associateBy(itemId)
    val activeIdSet = activeOrder.toSet()
    return activeOrder.mapNotNull { itemsById[it] } +
        items.filter { itemId(it) !in activeIdSet }
}

private fun EventStartListRuleSeverity.toStartListColor(): Color =
    when (this) {
        EventStartListRuleSeverity.GREEN -> Color(0xFF1B7F3A)
        EventStartListRuleSeverity.ORANGE -> Color(0xFFC46A00)
        EventStartListRuleSeverity.RED -> Color(0xFFB00020)
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

private fun missingCompetitorCategoryNames(path: Path, projectFile: EventProjectFile): List<String> {
    val result = EventCsvImports.parseAndroidCompetitorRows(Files.readString(path))
    val existingCategoryNames = projectFile.raceData.categories.mapTo(mutableSetOf()) { it.category.name }
    return result.rows
        .map { row -> row.categoryName.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .filterNot { it in existingCategoryNames }
}

private fun startListDrawStatusText(details: EventStartListDetails): String {
    val scheduledText = "${details.scheduledCount} scheduled"
    val qualityText = "score ${details.quality.score}/100, ${details.quality.summary}"
    return if (details.unscheduledCount == 0) {
        "Drew start list; $scheduledText; $qualityText"
    } else {
        "Drew start list; $scheduledText, ${details.unscheduledCount} without start times; $qualityText"
    }
}

private fun isValidStartListInterval(intervalText: String): Boolean =
    runCatching { DurationFormatter.minuteStringToSeconds(intervalText.trim()) > 0 }
        .getOrDefault(false)

private fun competitorImportStatusText(
    importedRows: Int,
    updatedRows: Int,
    skippedRows: Int,
    deletedRows: Int,
    invalidRows: Int,
    fileName: String
): String {
    val actions = listOf(
        "imported $importedRows",
        "updated $updatedRows",
        "skipped $skippedRows",
        "deleted $deletedRows"
    )
    val summary = "Competitor CSV import from $fileName: ${actions.joinToString(", ")}."
    return if (invalidRows == 0) {
        summary
    } else {
        "$summary Skipped $invalidRows invalid rows."
    }
}

private fun warningStatusSuffix(warnings: List<String>): String =
    if (warnings.isEmpty()) {
        ""
    } else {
        " Warnings: " + warnings.take(3).joinToString(" ") +
                if (warnings.size > 3) " +${warnings.size - 3} more." else ""
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

private fun beepDesktopReadoutAlert() {
    runCatching {
        Toolkit.getDefaultToolkit().beep()
    }
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

@Composable
private fun DetailValue(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = DesktopPalette.Black
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = 13.sp,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis
    )
}

/** Displays a compact label/value pair for read-only desktop event details. */
@Composable
private fun DetailRow(label: String, value: String, valueColor: Color = DesktopPalette.Black) {
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
            color = valueColor,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun WorkflowHomePanel(workflow: DesktopWorkflow) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Current workflow: ${workflow.label}",
            color = DesktopPalette.Disconnected,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun LabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier) {
        Text(
            text = label,
            color = if (enabled) DesktopPalette.Black else DesktopPalette.Disconnected,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        TextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled
        )
    }
}

private fun ProtectedCourseInfo.courseGeoPoints(): List<CourseGeoPoint> =
    route.map { CourseGeoPoint(it.latitude, it.longitude) } +
        controlPoints.map { CourseGeoPoint(it.latitude, it.longitude) } +
        courseObjects.map { CourseGeoPoint(it.latitude, it.longitude) }

private fun List<CourseGeoPoint>.venueBoundingBoxOrNull(): DesktopVenueElevationBoundingBox? =
    takeIf { it.isNotEmpty() }?.let { points ->
        DesktopVenueElevationBoundingBox(
            minLatitude = points.minOf { it.latitude },
            maxLatitude = points.maxOf { it.latitude },
            minLongitude = points.minOf { it.longitude },
            maxLongitude = points.maxOf { it.longitude }
        )
    }

private fun Double.decimalText(): String =
    "%.${6}f".format(this)

private fun bytesText(bytes: Long): String =
    when {
        bytes >= 1024L * 1024L -> "${oneDecimal(bytes.toDouble() / (1024.0 * 1024.0))} MiB"
        bytes >= 1024L -> "${oneDecimal(bytes.toDouble() / 1024.0)} KiB"
        else -> "$bytes B"
    }

private fun oneDecimal(value: Double): String =
    (value * 10.0).roundToInt().let { "${it / 10}.${abs(it % 10)}" }

private fun desktopRaceElapsedSeconds(startDateTimeIso: String, tick: Long): Long {
    return runCatching {
        Duration.between(LocalDateTime.parse(startDateTimeIso), LocalDateTime.now()).seconds
    }.getOrDefault(0L)
}

private fun EventReadoutDuplicatePolicy.toDisplayLabel(): String =
    when (this) {
        EventReadoutDuplicatePolicy.Reject -> "Ignore"
        EventReadoutDuplicatePolicy.Replace -> "Replace"
        EventReadoutDuplicatePolicy.CreateNew -> "Create new readout"
    }

private fun StartDrawClubHandling.toDisplayLabel(): String =
    when (this) {
        StartDrawClubHandling.AVOID_BACK_TO_BACK -> "Avoid same club"
        StartDrawClubHandling.IGNORE -> "Ignore clubs"
    }

private fun StartDrawStartGroupMode.toDisplayLabel(): String =
    when (this) {
        StartDrawStartGroupMode.DISABLED -> "No start groups"
        StartDrawStartGroupMode.PREFERRED_THIRDS -> "Preferred thirds"
        StartDrawStartGroupMode.BALANCED_MULTI_DAY_THIRDS -> "Balanced thirds"
    }

/** Shows the current SI-reader connection state and Event File save status. */
@Composable
private fun StatusStrip(
    projectStatusText: String,
    hasUnsavedChanges: Boolean,
    navigationDisabledSummary: String?,
    isDisabledNavigationExploration: Boolean,
    siReaderState: DesktopSiReaderUiState,
    isEventFileOpen: Boolean,
    isProtectedCourseOrderUnlocked: Boolean,
    onLockProtectedCourseOrder: () -> Unit
) {
    val effectiveSeverity = if (siReaderState.severity == DesktopSiReaderSeverity.CONNECTED && !isEventFileOpen) {
        DesktopSiReaderSeverity.WARNING
    } else {
        siReaderState.severity
    }
    val backgroundColor = if (isDisabledNavigationExploration || hasUnsavedChanges) {
        DesktopPalette.Warning
    } else {
        when (effectiveSeverity) {
            DesktopSiReaderSeverity.DISCONNECTED -> DesktopPalette.Disconnected
            DesktopSiReaderSeverity.CONNECTED -> DesktopPalette.Connected
            DesktopSiReaderSeverity.WARNING -> DesktopPalette.Warning
            DesktopSiReaderSeverity.ERROR -> DesktopPalette.Error
        }
    }
    val textColor = if (isDisabledNavigationExploration || hasUnsavedChanges) {
        DesktopPalette.Black
    } else {
        when (effectiveSeverity) {
            DesktopSiReaderSeverity.WARNING,
            DesktopSiReaderSeverity.CONNECTED -> DesktopPalette.Black
            DesktopSiReaderSeverity.DISCONNECTED,
            DesktopSiReaderSeverity.ERROR -> DesktopPalette.White
        }
    }
    val statusText = buildString {
        append(siReaderState.statusText)
        if (isDisabledNavigationExploration) {
            append(" - ")
            append(DisabledNavigationExplorationStatus)
        }
        append(" - ")
        append(projectStatusText)
        if (hasUnsavedChanges) {
            append(" *")
        }
        if (!navigationDisabledSummary.isNullOrBlank()) {
            append(" - ")
            append(navigationDisabledSummary)
        }
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
            text = statusText,
            modifier = Modifier.weight(1f),
            color = textColor,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (isProtectedCourseOrderUnlocked) {
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Unlocked",
                modifier = Modifier
                    .clickable(onClick = onLockProtectedCourseOrder)
                    .border(1.dp, textColor)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
