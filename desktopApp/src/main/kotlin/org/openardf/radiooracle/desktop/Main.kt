package org.openardf.radiooracle.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.Image
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Checkbox
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.openardf.radiooracle.desktop.printing.DesktopPrinterDiagnostics
import org.openardf.radiooracle.desktop.printing.DesktopTicketPrinter
import org.openardf.radiooracle.desktop.printing.DesktopTicketPrinterSelector
import org.openardf.radiooracle.desktop.usb.DesktopSportIdentCardBlockDownload
import org.openardf.radiooracle.desktop.usb.DesktopSportIdentReadoutService
import org.openardf.radiooracle.desktop.usb.DesktopSportIdentStationProbe
import org.openardf.radiooracle.desktop.usb.JSerialCommDesktopSerialPortProvider
import org.openardf.radiooracle.shared.course.ControlPointValidationException
import org.openardf.radiooracle.shared.event.EventCategoryDetails
import org.openardf.radiooracle.shared.event.EventCategorySort
import org.openardf.radiooracle.shared.event.EventCompetitorDetails
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
import org.openardf.radiooracle.shared.event.EventProjectSummary
import org.openardf.radiooracle.shared.event.EventReadoutDuplicatePolicy
import org.openardf.radiooracle.shared.event.EventReadoutDetails
import org.openardf.radiooracle.shared.event.EventResultDetails
import org.openardf.radiooracle.shared.event.EventStartListDetails
import org.openardf.radiooracle.shared.event.EventStartListRuleSeverity
import org.openardf.radiooracle.shared.event.EventStartListRow
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.event.ProtectedIdealOrderRules
import org.openardf.radiooracle.shared.event.StartDrawClubHandling
import org.openardf.radiooracle.shared.event.StartDrawOptions
import org.openardf.radiooracle.shared.event.StartDrawStartGroupMode
import org.openardf.radiooracle.shared.event.defaultScored
import org.openardf.radiooracle.shared.event.defaultTimeLimitMinutes
import org.openardf.radiooracle.shared.event.effectiveStartDrawSettings
import org.openardf.radiooracle.shared.event.toDisplayLabel
import org.openardf.radiooracle.shared.files.EventCsvImports
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.printing.FinishTicketRenderer
import org.openardf.radiooracle.shared.results.EventResultSending
import org.openardf.radiooracle.shared.time.DurationFormatter
import org.jetbrains.skia.Image as SkiaImage
import java.awt.Toolkit
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

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

private val CompetitorTableColumns = listOf(
    FixedTableColumn("First", 120.dp),
    FixedTableColumn("Last", 136.dp),
    FixedTableColumn("Club", 210.dp),
    FixedTableColumn("Index", 116.dp),
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
    "Index" to "Optional external registration index kept for imported data and exports.",
    "Birth" to "Optional birth year.",
    "Category" to "Competition category assigned to this competitor.",
    "Start no." to "Competitor start number or bib number.",
    "Start time" to "Drawn start time in minutes and seconds from the event start, such as 012:00.",
    "SI no." to "SPORTident card number assigned to this competitor."
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
    FixedTableColumn("Assign to", 240.dp),
    FixedTableColumn("", 104.dp),
    FixedTableColumn("", 104.dp),
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
    "Role" to "How this station is interpreted for scoring. Radio-o Fox controls score 1 point; Beacon and Spectator are mandatory zero-point punches.",
    "Public label" to "Optional public-facing name used on tickets, readout displays, course lists, and exported results. Short labels can also be typed in category Controls fields.",
    "Notes" to "Private organizer notes for this logical control."
)

/** Starts the first Compose Desktop shell for Radio-Oracle. */
fun main(args: Array<String>) = application {
    lateinit var requestWindowClose: () -> Unit
    Window(onCloseRequest = { requestWindowClose() }, title = DesktopBuildInfo.windowTitle) {
        val startupPath = remember(args.toList()) { args.firstOrNull()?.let(Path::of) }
        val projectSession = remember { DesktopProjectSession(DesktopProjectFiles) }
        val localResultServer = remember {
            DesktopLocalResultServer(projectSupplier = { projectSession.currentProject })
        }
        val ticketPrinter = remember { DesktopTicketPrinter() }
        val appCoroutineScope = rememberCoroutineScope()
        val startupStatus = remember(startupPath) { openStartupProject(projectSession, startupPath) }
        var projectFile by remember { mutableStateOf(projectSession.currentProject) }
        var projectStatusText by remember { mutableStateOf(startupStatus) }
        var hasUnsavedChanges by remember { mutableStateOf(projectSession.hasUnsavedChanges) }
        var newEventDraftProject by remember { mutableStateOf<EventProjectFile?>(null) }
        var pendingDirtyProjectAction by remember { mutableStateOf<PendingDirtyProjectAction?>(null) }
        var pendingAssignedControlsWarning by remember { mutableStateOf<EventAssignedControlWarning?>(null) }
        var assignedControlsWarningJob by remember { mutableStateOf<Job?>(null) }
        var isNationalStartListDefaultsDialogVisible by remember { mutableStateOf(false) }
        var siReaderState by remember { mutableStateOf(DesktopSiReaderUiState.disconnected()) }
        var pendingSiModeWarning by remember { mutableStateOf<DesktopSiReaderUiState?>(null) }
        var lastShownSiModeWarningKey by remember { mutableStateOf<String?>(null) }
        var isDownloadingSiReadout by remember { mutableStateOf(false) }
        var isContinuousSiReadoutActive by remember { mutableStateOf(false) }
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
        var isEventRegImportDialogVisible by remember { mutableStateOf(false) }
        var isEventRegCompetitorCsvImportDialogVisible by remember { mutableStateOf(false) }
        var eventRegImportUrl by remember { mutableStateOf(DesktopEventRegImportPreferences.lastRegistrationUrl()) }
        var isImportingEventRegWebsite by remember { mutableStateOf(false) }
        var isImportingEventRegCompetitorCsvs by remember { mutableStateOf(false) }
        var isImportingCourseKmlKmz by remember { mutableStateOf(false) }
        var pendingCompetitorsCsvImportPath by remember { mutableStateOf<Path?>(null) }
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

        fun scheduleAssignedControlsWarning(warning: EventAssignedControlWarning?) {
            assignedControlsWarningJob?.cancel()
            assignedControlsWarningJob = null
            if (warning == null) {
                pendingAssignedControlsWarning = null
                return
            }
            assignedControlsWarningJob = appCoroutineScope.launch {
                delay(700L)
                pendingAssignedControlsWarning = warning
                assignedControlsWarningJob = null
            }
        }

        fun lockProtectedCourseOrder() {
            val wasUnlocked = protectedCoursePassword != null
            protectedCoursePassword = null
            protectedIdealOrderByCategoryId = emptyMap()
            protectedCourseInfoByCategoryId = emptyMap()
            if (wasUnlocked) {
                projectStatusText = "Protected course order locked."
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
                hasUnsavedChanges = projectSession.hasUnsavedChanges
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
            if (isDownloadingSiReadout || isContinuousSiReadoutActive) {
                return
            }
            if (projectSession.currentProject == null) {
                projectStatusText = "Open or create an Event File before downloading SI cards."
                DesktopDebugLog.warn("SI", "Single SI download requested with no Event File open")
                return
            }
            isDownloadingSiReadout = true
            siDownloadStatusText = "Waiting for SI card; keep it seated until the read finishes."
            projectStatusText = "Waiting for SI card..."
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
                                projectStatusText = "Replaced existing readout for SI card ${download.readout.siNumber}."
                                DesktopDebugLog.info("SI", "Duplicate SI card ${download.readout.siNumber} replaced")
                            }
                            DesktopSportIdentAppendOutcome.DuplicateCreatedNew -> {
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

        fun startContinuousSportIdentReadout() {
            if (isDownloadingSiReadout || isContinuousSiReadoutActive) {
                return
            }
            if (projectSession.currentProject == null) {
                projectStatusText = "Open or create an Event File before downloading SI cards."
                DesktopDebugLog.warn("SI", "Continuous SI readout requested with no Event File open")
                return
            }
            val stopRequested = AtomicBoolean(false)
            continuousSiReadoutStopRequested = stopRequested
            isContinuousSiReadoutActive = true
            siDownloadStatusText = "Continuous SI readout is running; insert SI cards and keep each seated until it reads."
            projectStatusText = "Continuous SI readout running..."
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
            isSendingLiveResults = true
            if (!automatic) {
                projectStatusText = "Sending live results to ROBIS..."
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

        fun saveCurrentProject(): Boolean {
            if (projectSession.currentPath == null) {
                val path = DesktopFileDialogs.chooseSaveProject(
                    projectSession.currentProject?.raceData?.race?.name
                ) ?: return false
                return runCatching {
                    projectSession.saveAs(path)
                    newEventDraftProject = null
                    syncProjectState()
                    projectStatusText = "Saved ${path.fileName}"
                    DesktopDebugLog.info("EventFile", "Saved ${path.fileName}")
                }.onFailure { error ->
                    projectStatusText = "Save failed: ${error.message ?: error::class.simpleName}"
                    DesktopDebugLog.error("EventFile", "Save failed: ${error.message ?: error::class.simpleName}")
                }.isSuccess
            }
            return runCatching {
                projectSession.save()
                newEventDraftProject = null
                syncProjectState()
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
                projectStatusText = "Protected course order password cannot be blank."
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
                projectStatusText = error.message ?: "Protected course order unlock failed."
                return false
            }
            val decryptedCourseInfo = runCatching {
                currentProject.raceData.categories.mapNotNull { categoryData ->
                    categoryData.category.encryptedCourseInfo?.takeIf { it.isNotBlank() }?.let { encryptedValue ->
                        categoryData.category.id to DesktopProtectedCourseOrder.decryptCourseInfo(encryptedValue, trimmedPassword)
                    }
                }.toMap()
            }.getOrElse { error ->
                projectStatusText = error.message ?: "Protected course data unlock failed."
                return false
            }

            protectedCoursePassword = trimmedPassword
            protectedIdealOrderByCategoryId = decrypted
            protectedCourseInfoByCategoryId = decryptedCourseInfo
            projectStatusText = "Protected course order unlocked."
            return true
        }

        fun updateProtectedIdealOrder(categoryId: String, idealOrderText: String) {
            val password = protectedCoursePassword ?: run {
                projectStatusText = "Unlock protected course order before editing."
                return
            }
            runCatching {
                projectFile?.raceData?.controls?.let { controls ->
                    ProtectedIdealOrderRules.validate(idealOrderText, controls)
                }
                val encryptedIdealOrder = idealOrderText.trim().takeIf { it.isNotEmpty() }?.let {
                    DesktopProtectedCourseOrder.encrypt(it, password)
                }
                projectFile = projectSession.updateCurrentProject { currentProject ->
                    EventProjectEditor.updateCategoryEncryptedIdealOrder(currentProject, categoryId, encryptedIdealOrder)
                }
                protectedIdealOrderByCategoryId = protectedIdealOrderByCategoryId + (categoryId to idealOrderText.trim())
                hasUnsavedChanges = projectSession.hasUnsavedChanges
                projectStatusText = "Unsaved changes."
            }.onFailure { error ->
                projectStatusText = "Edit failed: ${error.message ?: error::class.simpleName}"
            }
        }

        fun updateProtectedCoursePassword(oldPassword: String, newPassword: String, confirmPassword: String): Boolean {
            val currentProject = projectSession.currentProject ?: return false
            val currentPassword = protectedCoursePassword ?: run {
                projectStatusText = "Unlock protected course order before changing its password."
                return false
            }
            val trimmedOldPassword = oldPassword.trim()
            val trimmedNewPassword = newPassword.trim()
            val trimmedConfirmPassword = confirmPassword.trim()
            if (trimmedOldPassword.isEmpty()) {
                projectStatusText = "Old password cannot be blank."
                return false
            }
            if (trimmedNewPassword.isEmpty()) {
                projectStatusText = "New password cannot be blank."
                return false
            }
            if (trimmedNewPassword != trimmedConfirmPassword) {
                projectStatusText = "New protected course passwords do not match."
                return false
            }

            val hasEncryptedCourseProtection = currentProject.raceData.categories.any { categoryData ->
                !categoryData.category.encryptedIdealOrder.isNullOrBlank() ||
                    !categoryData.category.encryptedCourseInfo.isNullOrBlank()
            }
            if (!hasEncryptedCourseProtection && trimmedOldPassword != currentPassword) {
                projectStatusText = "Old password did not match the unlocked protected course password."
                return false
            }

            return runCatching {
                val updatedProject = DesktopProtectedCourseOrder.reencryptProjectCourseProtection(
                    currentProject,
                    oldPassword = trimmedOldPassword,
                    newPassword = trimmedNewPassword
                )
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
                projectStatusText = "Protected course password updated. Unsaved changes."
                true
            }.getOrElse { error ->
                projectStatusText = "Password update failed: ${error.message ?: error::class.simpleName}"
                false
            }
        }

        fun chooseImportCourseKmlKmz() {
            val currentProject = projectSession.currentProject ?: return
            val password = protectedCoursePassword ?: run {
                projectStatusText = "Unlock protected course order before importing KML/KMZ course data."
                return
            }
            if (isImportingCourseKmlKmz) {
                return
            }
            DesktopFileDialogs.chooseImportKmlKmz()?.let { path ->
                isImportingCourseKmlKmz = true
                projectStatusText = "Importing protected course KML/KMZ..."
                appCoroutineScope.launch {
                    val result = runCatching {
                        withContext(Dispatchers.IO) {
                            DesktopCourseKmlImporter.importProtectedCourseInfo(
                                path = path,
                                projectFile = currentProject,
                                password = password
                            )
                        }
                    }
                    result.onSuccess { (updatedProject, summary) ->
                        projectFile = projectSession.updateCurrentProject { updatedProject }
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
                        projectStatusText =
                            "Imported protected course data for ${summary.matchedCategoryCount} categories; sampled ${summary.sampledElevationCount} USGS elevation points."
                    }.onFailure { error ->
                        projectStatusText = "Course KML/KMZ import failed: ${error.message ?: error::class.simpleName}"
                    }
                    isImportingCourseKmlKmz = false
                }
            }
        }

        fun importAndroidRaceBackupJson(path: Path) {
            runCatching {
                lockProtectedCourseOrder()
                val imported = DesktopProjectFiles.importAndroidRaceBackupJson(path) { UUID.randomUUID().toString() }
                projectFile = projectSession.newProject(imported)
                newEventDraftProject = null
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
                    DesktopProjectFiles.exportFinalResultsJson(path, currentProject)
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
                    DesktopProjectFiles.exportResultsHtml(path, currentProject)
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
                    DesktopProjectFiles.exportResultsText(path, currentProject)
                    syncProjectState()
                    projectStatusText = "Exported ${path.fileName}"
                }.onFailure { error ->
                    projectStatusText = "Export failed: ${error.message ?: error::class.simpleName}"
                }
            }
        }

        fun importCompetitorsCsv() {
            DesktopFileDialogs.chooseImportCsv("Import Competitors CSV")?.let { path ->
                pendingCompetitorsCsvImportPath = path
                syncCompetitorsCsvImport = false
            }
        }

        fun importCompetitorsCsv(path: Path, synchronizeToCsv: Boolean) {
            runCatching {
                val csvText = Files.readString(path)
                val result = EventCsvImports.parseAndroidCompetitorRows(csvText)
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
                        deleteMissingByImportKey = synchronizeToCsv
                    )
                    importWarnings = outcome.warnings
                    importedRows = outcome.importedCount
                    updatedRows = outcome.updatedCount
                    skippedRows = outcome.skippedCount
                    deletedRows = outcome.deletedCount
                    outcome.projectFile
                }
                syncProjectState()
                pendingCompetitorsCsvImportPath = null
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
                    lockProtectedCourseOrder()
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

        fun importControlsCsv() {
            DesktopFileDialogs.chooseImportCsv("Import Controls CSV")?.let { path ->
                runCatching {
                    val result = EventCsvImports.parseControlRows(Files.readString(path))
                    projectFile = projectSession.updateCurrentProject { currentProject ->
                        EventProjectEditor.importControlRows(
                            currentProject,
                            result.rows,
                            controlIdFactory = { UUID.randomUUID().toString() }
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

        fun saveAsCurrentProject() {
            DesktopFileDialogs.chooseSaveProject(projectSession.currentProject?.raceData?.race?.name)?.let { path ->
                runCatching {
                    projectSession.saveAs(path)
                    projectFile = projectSession.currentProject
                    hasUnsavedChanges = projectSession.hasUnsavedChanges
                    projectStatusText = "Saved ${path.fileName}"
                    DesktopDebugLog.info("EventFile", "Saved As ${path.fileName}")
                }.onFailure { error ->
                    projectStatusText = "Save failed: ${error.message ?: error::class.simpleName}"
                    DesktopDebugLog.error("EventFile", "Save As failed: ${error.message ?: error::class.simpleName}")
                }
            }
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

        fun handleNavAction(action: DesktopNavAction) {
            when (action) {
                DesktopNavAction.NewEventFile -> requestNewEventFile()
                DesktopNavAction.OpenEventFile -> chooseOpenEventFile()
                DesktopNavAction.ImportAndroidRaceBackup -> chooseImportAndroidRaceBackupJson()
                DesktopNavAction.ImportEventRegWebsite -> showEventRegImportDialog()
                DesktopNavAction.ImportEventRegCompetitorsCsv -> showEventRegCompetitorCsvImportDialog()
                DesktopNavAction.SaveEventFile -> saveCurrentProject()
                DesktopNavAction.SaveEventFileAs -> saveAsCurrentProject()
                DesktopNavAction.CloseEventFile -> requestCloseEventFile()
                DesktopNavAction.ImportCategoriesCsv -> importCategoriesCsv()
                DesktopNavAction.ImportControlsCsv -> importControlsCsv()
                DesktopNavAction.ImportCourseKmlKmz -> chooseImportCourseKmlKmz()
                DesktopNavAction.ImportCompetitorsCsv -> importCompetitorsCsv()
                DesktopNavAction.ImportStartsCsv -> importCompetitorStartsCsv()
                DesktopNavAction.ExportEventFileCopy -> exportEventFileCopy()
                DesktopNavAction.ExportCategoriesCsv -> exportCategoriesCsv()
                DesktopNavAction.ExportControlsCsv ->
                    exportCsv("Export Controls CSV", "controls", DesktopProjectFiles::exportControlsCsv)
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
                Item("Load File...", onClick = ::chooseOpenEventFile)
                Item("Import EventReg Website...", onClick = ::showEventRegImportDialog)
                Item(
                    "Save Event",
                    enabled = canSaveEventFile(),
                    onClick = {
                        saveCurrentProject()
                    }
                )
                Item("Save As...", enabled = projectFile != null, onClick = {
                    saveAsCurrentProject()
                })
                Item("Close Event File", enabled = projectFile != null, onClick = ::requestCloseEventFile)
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
        pendingAssignedControlsWarning?.let { warning ->
            AssignedControlsWarningDialog(
                warning = warning,
                onDismiss = { pendingAssignedControlsWarning = null }
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
        pendingCompetitorsCsvImportPath?.let { importPath ->
            CompetitorCsvImportOptionsDialog(
                fileName = importPath.fileName.toString(),
                synchronizeToCsv = syncCompetitorsCsvImport,
                onSynchronizeToCsvChange = { syncCompetitorsCsvImport = it },
                onImport = { importCompetitorsCsv(importPath, syncCompetitorsCsvImport) },
                onCancel = { pendingCompetitorsCsvImportPath = null }
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
            isSendingLiveResults = isSendingLiveResults,
            isBackgroundLiveResultSendingEnabled = isBackgroundLiveResultSendingEnabled,
            readoutDuplicatePolicy = readoutDuplicatePolicy,
            isReadoutAlertSoundEnabled = isReadoutAlertSoundEnabled,
            areAliasesEnabled = areAliasesEnabled,
            localResultServerUrl = localResultServerUrl,
            printerDiagnostics = printerDiagnostics,
            raceClockTick = raceClockTick,
            isNavActionEnabled = ::isNavActionEnabled,
            onNavAction = ::handleNavAction,
            isProtectedCourseOrderUnlocked = protectedCoursePassword != null,
            protectedIdealOrderByCategoryId = protectedIdealOrderByCategoryId,
            protectedCourseInfoByCategoryId = protectedCourseInfoByCategoryId,
            onUnlockProtectedCourseOrder = ::unlockProtectedCourseOrder,
            onUpdateProtectedIdealOrder = ::updateProtectedIdealOrder,
            onUpdateProtectedCoursePassword = ::updateProtectedCoursePassword,
            onLockProtectedCourseOrder = ::lockProtectedCourseOrder,
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
                    val currentProject = projectSession.currentProject
                    val shouldPromptForNationalDefaults = currentProject != null &&
                        currentProject.raceData.race.raceLevel != RaceLevel.NATIONAL &&
                        raceLevel == RaceLevel.NATIONAL &&
                        shouldOfferNationalStartListDefaults(currentProject)
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
            onUpdateCategoryControlPoints = { categoryId, controlPointsText ->
                runCatching {
                    val updatedProject = projectSession.updateCurrentProject { currentProject ->
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
                    projectStatusText = "Unsaved changes."
                    scheduleAssignedControlsWarning(
                        EventAssignedControlWarnings.forCategory(updatedProject.raceData, categoryId)
                    )
                }.onFailure { error ->
                    clearAssignedControlsWarning()
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
                    FinishTicketRenderer.render(currentProject.raceData, resultId, useAliases = areAliasesEnabled)
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
                                    useAliases = areAliasesEnabled
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
                    projectFile = projectSession.updateCurrentProject { currentProject ->
                        EventProjectEditor.updateControl(currentProject, controlId, label, siCode, type, scored, publicLabel, notes)
                    }
                    hasUnsavedChanges = projectSession.hasUnsavedChanges
                    projectStatusText = "Unsaved changes."
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
                runCatching {
                    projectFile = projectSession.updateCurrentProject { currentProject ->
                        EventProjectEditor.removeControl(currentProject, controlId)
                    }
                    hasUnsavedChanges = projectSession.hasUnsavedChanges
                    projectStatusText = "Unsaved changes."
                }.onFailure { error ->
                    projectStatusText = "Edit failed: ${error.message ?: error::class.simpleName}"
                }
            },
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
            onSaveEventFileForNavigation = ::saveCurrentProject,
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
    onDismiss: () -> Unit
) {
    val missingLines = buildList {
        if (warning.missingBeaconLabels.isNotEmpty()) {
            add("Beacon: ${warning.missingBeaconLabels.joinToString(", ")}")
        }
        if (warning.missingSpectatorLabels.isNotEmpty()) {
            add("Spectator: ${warning.missingSpectatorLabels.joinToString(", ")}")
        }
    }.joinToString("\n")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assigned Controls warning") },
        text = {
            Text(
                "Category ${warning.categoryName} is missing required control assignments:\n" +
                    "$missingLines\n\n" +
                    "Every category should include each defined beacon. " +
                    "Sprint categories should also include each defined spectator."
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
    synchronizeToCsv: Boolean,
    onSynchronizeToCsvChange: (Boolean) -> Unit,
    onImport: () -> Unit,
    onCancel: () -> Unit
) {
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
            }
        },
        confirmButton = {
            Button(onClick = onImport) {
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
    data class Workflow(val workflow: DesktopWorkflow) : DesktopPendingNavigation
    data class Item(val itemId: String) : DesktopPendingNavigation
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
    projectStatusText: String = "No Event File open.",
    hasUnsavedChanges: Boolean = false,
    siReaderState: DesktopSiReaderUiState = DesktopSiReaderUiState.disconnected(),
    isDownloadingSiReadout: Boolean = false,
    isContinuousSiReadoutActive: Boolean = false,
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
    onRenameCategory: (String, String) -> Unit = { _, _ -> },
    onUpdateCategoryControlPoints: (String, String) -> Unit = { _, _ -> },
    onUpdateCategoryPhysicalStats: (String, String, String) -> Unit = { _, _, _ -> },
    onAddCategory: (String) -> Boolean = { false },
    onRemoveCategory: (String, Boolean) -> Unit = { _, _ -> },
    onRenameCompetitor: (String, String, String) -> Unit = { _, _, _ -> },
    onUpdateCompetitorNumbers: (String, String, String) -> Unit = { _, _, _ -> },
    onUpdateCompetitorClubIndex: (String, String, String) -> Unit = { _, _, _ -> },
    onUpdateCompetitorBirthYear: (String, String) -> Unit = { _, _ -> },
    onUpdateCompetitorStartTime: (String, String) -> Unit = { _, _ -> },
    onUpdateStartDrawSettings: (String, StartDrawOptions) -> Unit = { _, _ -> },
    onDrawStartList: (String, StartDrawOptions) -> Unit = { _, _ -> },
    onDrawBalancedStartList: (String, StartDrawOptions) -> Unit = { _, _ -> },
    onAddCompetitor: (String, String, String, String, String, String?, String, String) -> Boolean = { _, _, _, _, _, _, _, _ -> false },
    onAssignCompetitorCategory: (String, String?) -> Unit = { _, _ -> },
    onRemoveCompetitor: (String, Boolean) -> Unit = { _, _ -> },
    onRemoveReadout: (String) -> Unit = {},
    onUpdateReadoutStatus: (String, ResultStatus) -> Unit = { _, _ -> },
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
    onUnlockProtectedCourseOrder: (String) -> Boolean = { false },
    onUpdateProtectedIdealOrder: (String, String) -> Unit = { _, _ -> },
    onUpdateProtectedCoursePassword: (String, String, String) -> Boolean = { _, _, _ -> false },
    onLockProtectedCourseOrder: () -> Unit = {},
    isNavActionEnabled: (DesktopNavAction) -> Boolean = { false },
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
        var pendingNavigation by remember { mutableStateOf<DesktopPendingNavigation?>(null) }
        var pendingDirtySubmenuNavigation by remember { mutableStateOf<DesktopPendingNavigation?>(null) }
        val navigationReadiness = DesktopNavigationReadiness.from(projectFile)

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
            navState = nextState
            action?.let(onNavAction)
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

        Surface(modifier = Modifier.fillMaxSize(), color = DesktopPalette.White) {
            Column(modifier = Modifier.fillMaxSize()) {
                AppTopBar()
                Row(modifier = Modifier.weight(1f)) {
                    NavigationRail(
                        navState = navState,
                        navigationReadiness = navigationReadiness,
                        isNavActionEnabled = isNavActionEnabled,
                        onBack = { requestNavigation(DesktopPendingNavigation.Back) },
                        onSaveEvent = { onNavAction(DesktopNavAction.SaveEventFile) },
                        onItemSelected = { item ->
                            requestNavigation(DesktopPendingNavigation.Item(item.id))
                        }
                    )
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            SectionWorkspace(
                                workflow = navState.workflow,
                                section = navState.selectedSection,
                                title = DesktopNavigation.selectedLabel(navState),
                                breadcrumb = DesktopNavigation.breadcrumb(navState),
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
                                onUpdateCompetitorStartTime = onUpdateCompetitorStartTime,
                                onUpdateStartDrawSettings = onUpdateStartDrawSettings,
                                onDrawStartList = onDrawStartList,
                                onDrawBalancedStartList = onDrawBalancedStartList,
                                onAddCompetitor = onAddCompetitor,
                                onAssignCompetitorCategory = onAssignCompetitorCategory,
                                onRemoveCompetitor = onRemoveCompetitor,
                                onRemoveReadout = onRemoveReadout,
                                onUpdateReadoutStatus = onUpdateReadoutStatus,
                                onAssignUnmatchedReadout = onAssignUnmatchedReadout,
                                onDownloadSportIdentReadout = onDownloadSportIdentReadout,
                                onStartContinuousSportIdentReadout = onStartContinuousSportIdentReadout,
                                onStopContinuousSportIdentReadout = onStopContinuousSportIdentReadout,
                                onPreviewFinishTicket = onPreviewFinishTicket,
                                onPrintFinishTicket = onPrintFinishTicket,
                                isDownloadingSiReadout = isDownloadingSiReadout,
                                isContinuousSiReadoutActive = isContinuousSiReadoutActive,
                                siDownloadStatusText = siDownloadStatusText,
                                onAddManualReadout = onAddManualReadout,
                                onUpdateControl = onUpdateControl,
                                onAddControl = onAddControl,
                                onRemoveControl = onRemoveControl,
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
                                onUnlockProtectedCourseOrder = onUnlockProtectedCourseOrder,
                                onUpdateProtectedIdealOrder = onUpdateProtectedIdealOrder,
                                onUpdateProtectedCoursePassword = onUpdateProtectedCoursePassword
                            )
                        }
                        WorkflowBar(
                            selectedWorkflow = navState.workflow,
                            navigationReadiness = navigationReadiness,
                            onWorkflowSelected = { workflow ->
                                requestNavigation(DesktopPendingNavigation.Workflow(workflow))
                            }
                        )
                    }
                }
                StatusStrip(
                    projectStatusText = projectStatusText,
                    hasUnsavedChanges = hasUnsavedChanges,
                    siReaderState = siReaderState,
                    isEventFileOpen = projectFile != null
                )
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

@Composable
private fun saveEventButtonColors() =
    ButtonDefaults.buttonColors(
        backgroundColor = DesktopPalette.Connected,
        contentColor = DesktopPalette.Black,
        disabledBackgroundColor = DesktopPalette.LightGrey,
        disabledContentColor = DesktopPalette.Disconnected
    )

/** Shows workflow-specific navigation with optional submenu replacement. */
@Composable
private fun NavigationRail(
    navState: DesktopNavState,
    navigationReadiness: DesktopNavigationReadiness,
    isNavActionEnabled: (DesktopNavAction) -> Boolean,
    onBack: () -> Unit,
    onSaveEvent: () -> Unit,
    onItemSelected: (DesktopNavItem) -> Unit
) {
    val items = DesktopNavigation.currentItems(navState)
    val hasMenuSaveEvent = items.any { it.action == DesktopNavAction.SaveEventFile }
    Column(
        modifier = Modifier
            .width(220.dp)
            .fillMaxHeight()
            .background(Color(0xFFF5F5F5))
            .border(1.dp, DesktopPalette.LightGrey)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEach { item ->
            val isSelected = item.id == navState.selectedItemId && item.children.isEmpty()
            val isEnabled = DesktopNavigation.isItemEnabled(item, navigationReadiness) &&
                (item.action?.let(isNavActionEnabled) ?: true)
            Button(
                onClick = { onItemSelected(item) },
                enabled = isEnabled,
                modifier = Modifier.fillMaxWidth(),
                colors = if (item.action == DesktopNavAction.SaveEventFile) {
                    saveEventButtonColors()
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Text(
                    text = if (item.children.isEmpty()) item.label else "${item.label} >",
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
        if (navState.submenuStack.isNotEmpty()) {
            Spacer(modifier = Modifier.weight(1f))
            if (!hasMenuSaveEvent) {
                Button(
                    onClick = onSaveEvent,
                    enabled = isNavActionEnabled(DesktopNavAction.SaveEventFile),
                    modifier = Modifier.fillMaxWidth(),
                    colors = saveEventButtonColors()
                ) {
                    Text("Save Event")
                }
            }
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = DesktopPalette.SecondaryVariant,
                    contentColor = DesktopPalette.White
                )
            ) {
                Text("< Back")
            }
        }
    }
}

/** Keeps the primary workflow groups visible as the desktop return-to-top path. */
@Composable
private fun WorkflowBar(
    selectedWorkflow: DesktopWorkflow,
    navigationReadiness: DesktopNavigationReadiness,
    onWorkflowSelected: (DesktopWorkflow) -> Unit
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
            Button(
                onClick = { onWorkflowSelected(workflow) },
                enabled = isEnabled,
                modifier = Modifier
                    .weight(1f)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) DesktopPalette.Black else DesktopPalette.LightGrey
                    ),
                colors = if (isSelected) {
                    ButtonDefaults.buttonColors(
                        backgroundColor = DesktopPalette.PrimaryVariant,
                        contentColor = DesktopPalette.White
                    )
                } else {
                    ButtonDefaults.buttonColors(
                        backgroundColor = DesktopPalette.White,
                        contentColor = DesktopPalette.Black
                    )
                }
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

/** Displays an Android-style empty state for the selected section. */
@Composable
private fun SectionWorkspace(
    workflow: DesktopWorkflow,
    section: DesktopSection,
    title: String,
    breadcrumb: String,
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
    onUpdateCompetitorStartTime: (String, String) -> Unit,
    onUpdateStartDrawSettings: (String, StartDrawOptions) -> Unit,
    onDrawStartList: (String, StartDrawOptions) -> Unit,
    onDrawBalancedStartList: (String, StartDrawOptions) -> Unit,
    onAddCompetitor: (String, String, String, String, String, String?, String, String) -> Boolean,
    onAssignCompetitorCategory: (String, String?) -> Unit,
    onRemoveCompetitor: (String, Boolean) -> Unit,
    onRemoveReadout: (String) -> Unit,
    onUpdateReadoutStatus: (String, ResultStatus) -> Unit,
    onAssignUnmatchedReadout: (String, String) -> Unit,
    onDownloadSportIdentReadout: () -> Unit,
    onStartContinuousSportIdentReadout: () -> Unit,
    onStopContinuousSportIdentReadout: () -> Unit,
    onPreviewFinishTicket: (String) -> String,
    onPrintFinishTicket: (String) -> Unit,
    isDownloadingSiReadout: Boolean,
    isContinuousSiReadoutActive: Boolean,
    siDownloadStatusText: String?,
    onAddManualReadout: (String?, String, String, String, String, ResultStatus) -> Boolean,
    onUpdateControl: (String, String, String, ControlPointType, Boolean, String, String) -> Unit,
    onAddControl: (String, String, ControlPointType, Boolean, String, String) -> Boolean,
    onRemoveControl: (String) -> Unit,
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
    onUnlockProtectedCourseOrder: (String) -> Boolean,
    onUpdateProtectedIdealOrder: (String, String) -> Unit,
    onUpdateProtectedCoursePassword: (String, String, String) -> Boolean
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
            text = sectionSummary(section, projectFile),
            color = DesktopPalette.Black,
            fontSize = 14.sp
        )
        if (section == DesktopSection.WorkflowHome) {
            WorkflowHomePanel(workflow, projectFile)
        }
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
                onUpdatePassword = onUpdateProtectedCoursePassword
            )
        }
        if (section == DesktopSection.Competitors && projectFile != null) {
            CompetitorDetailsPanel(
                competitors = EventCompetitorDetails.from(projectFile.raceData),
                categories = EventCategoryDetails.from(projectFile.raceData, useAliases = areAliasesEnabled),
                onRenameCompetitor = onRenameCompetitor,
                onUpdateCompetitorNumbers = onUpdateCompetitorNumbers,
                onUpdateCompetitorClubIndex = onUpdateCompetitorClubIndex,
                onUpdateCompetitorBirthYear = onUpdateCompetitorBirthYear,
                onUpdateCompetitorStartTime = onUpdateCompetitorStartTime,
                onAddCompetitor = onAddCompetitor,
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
                results = EventResultDetails.from(projectFile.raceData),
                onUpdateReadoutStatus = onUpdateReadoutStatus
            )
        }
        if (section == DesktopSection.Settings) {
            SettingsDetailsPanel(
                diagnostics = DesktopProjectDiagnostics.from(projectFile),
                isSendingLiveResults = isSendingLiveResults,
                isBackgroundLiveResultSendingEnabled = isBackgroundLiveResultSendingEnabled,
                readoutDuplicatePolicy = readoutDuplicatePolicy,
                isReadoutAlertSoundEnabled = isReadoutAlertSoundEnabled,
                areAliasesEnabled = areAliasesEnabled,
                localResultServerUrl = localResultServerUrl,
                printerDiagnostics = printerDiagnostics,
                onSendRobisLiveResults = onSendRobisLiveResults,
                onSetBackgroundLiveResultSendingEnabled = onSetBackgroundLiveResultSendingEnabled,
                onSetReadoutDuplicatePolicy = onSetReadoutDuplicatePolicy,
                onSetReadoutAlertSoundEnabled = onSetReadoutAlertSoundEnabled,
                onSetAliasesEnabled = onSetAliasesEnabled,
                onStartLocalResultServer = onStartLocalResultServer,
                onStopLocalResultServer = onStopLocalResultServer
            )
        }
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

/** Shows read-only Event File diagnostics and the desktop-beta scope boundary. */
@Composable
private fun SettingsDetailsPanel(
    diagnostics: DesktopProjectDiagnostics,
    isSendingLiveResults: Boolean,
    isBackgroundLiveResultSendingEnabled: Boolean,
    readoutDuplicatePolicy: EventReadoutDuplicatePolicy,
    isReadoutAlertSoundEnabled: Boolean,
    areAliasesEnabled: Boolean,
    localResultServerUrl: String?,
    printerDiagnostics: DesktopPrinterDiagnostics,
    onSendRobisLiveResults: () -> Unit,
    onSetBackgroundLiveResultSendingEnabled: (Boolean) -> Unit,
    onSetReadoutDuplicatePolicy: (EventReadoutDuplicatePolicy) -> Unit,
    onSetReadoutAlertSoundEnabled: (Boolean) -> Unit,
    onSetAliasesEnabled: (Boolean) -> Unit,
    onStartLocalResultServer: () -> Unit,
    onStopLocalResultServer: () -> Unit
) {
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
        DetailRow("Duplicate SI cards", readoutDuplicatePolicy.toDisplayLabel())
        ReadoutDuplicatePolicyPicker(
            selectedPolicy = readoutDuplicatePolicy,
            onPolicySelected = onSetReadoutDuplicatePolicy
        )
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = areAliasesEnabled,
                onCheckedChange = onSetAliasesEnabled,
                enabled = diagnostics.projectState == "Event File open"
            )
            Text(
                text = "Use control labels",
                color = DesktopPalette.Black,
                fontSize = 13.sp
            )
        }
        DetailRow("Printer", printerDiagnostics.readinessText)
        DetailRow(
            "Detected printers",
            printerDiagnostics.detectedPrinterNames.joinToString().ifBlank { "None" }
        )
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
    onAssignUnmatchedReadout: (String, String) -> Unit,
    onPreviewFinishTicket: () -> Unit
) {
    var selectedStatus by remember(readout.id, readout.resultStatus) { mutableStateOf(readout.resultStatus) }
    var selectedCompetitorId by remember(readout.id) { mutableStateOf<String?>(null) }

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
            onClick = { onUpdateReadoutStatus(readout.id, selectedStatus) },
            modifier = Modifier.width(ReadoutTableColumns[8].width),
            enabled = selectedStatus != readout.resultStatus || readout.automaticStatus
        ) {
            ButtonLabel("Status")
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
    onUpdateCompetitorStartTime: (String, String) -> Unit,
    onAddCompetitor: (String, String, String, String, String, String?, String, String) -> Boolean,
    onAssignCompetitorCategory: (String, String?) -> Unit,
    onRemoveCompetitor: (String, Boolean) -> Unit
) {
    val horizontalScrollState = rememberScrollState()
    val tableWidth = fixedTableWidth(CompetitorTableColumns)
    val orderedCompetitors = rememberEditableRowOrder(competitors) { it.id }
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
                                onUpdateCompetitorClubIndex = onUpdateCompetitorClubIndex,
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
        Spacer(modifier = Modifier.width(CompetitorTableColumns[7].width))
        TextField(
            value = siNumberDraft,
            onValueChange = onSiNumberChange,
            modifier = Modifier.width(CompetitorTableColumns[8].width),
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
    onUpdateCompetitorClubIndex: (String, String, String) -> Unit,
    onUpdateCompetitorBirthYear: (String, String) -> Unit,
    onUpdateCompetitorStartTime: (String, String) -> Unit,
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
        if (clubDraft != competitor.club || indexDraft != competitor.index) {
            onUpdateCompetitorClubIndex(competitor.id, clubDraft, indexDraft)
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
            value = indexDraft,
            onValueChange = { indexDraft = it },
            modifier = Modifier
                .width(CompetitorTableColumns[3].width)
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused) {
                        applyPendingDrafts()
                    }
                },
            singleLine = true,
            label = { Text("Index") }
        )
        TextField(
            value = birthYearDraft,
            onValueChange = { birthYearDraft = it },
            modifier = Modifier
                .width(CompetitorTableColumns[4].width)
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
            modifier = Modifier.width(CompetitorTableColumns[5].width)
        )
        TextField(
            value = startNumberDraft,
            onValueChange = { startNumberDraft = it },
            modifier = Modifier
                .width(CompetitorTableColumns[6].width)
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
                .width(CompetitorTableColumns[7].width)
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
                .width(CompetitorTableColumns[8].width)
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
    onUpdateCategoryControlPoints: (String, String) -> Unit,
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
    onUpdatePassword: (String, String, String) -> Boolean
) {
    var passwordDraft by remember(projectFile.raceData.race.id, isUnlocked) { mutableStateOf("") }
    var oldPasswordDraft by remember(projectFile.raceData.race.id, isUnlocked) { mutableStateOf("") }
    var newPasswordDraft by remember(projectFile.raceData.race.id, isUnlocked) { mutableStateOf("") }
    var confirmPasswordDraft by remember(projectFile.raceData.race.id, isUnlocked) { mutableStateOf("") }

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
    val changedDrafts = idealOrderDrafts.filter { (categoryId, draft) ->
        draft.trim() != idealOrderByCategoryId[categoryId].orEmpty()
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = oldPasswordDraft,
                onValueChange = { oldPasswordDraft = it },
                label = { Text("Old password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.width(190.dp)
            )
            TextField(
                value = newPasswordDraft,
                onValueChange = { newPasswordDraft = it },
                label = { Text("New password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.width(190.dp)
            )
            TextField(
                value = confirmPasswordDraft,
                onValueChange = { confirmPasswordDraft = it },
                label = { Text("Confirm new") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.width(190.dp)
            )
            Button(
                onClick = {
                    if (onUpdatePassword(oldPasswordDraft, newPasswordDraft, confirmPasswordDraft)) {
                        oldPasswordDraft = ""
                        newPasswordDraft = ""
                        confirmPasswordDraft = ""
                    }
                },
                enabled = oldPasswordDraft.isNotBlank() &&
                    newPasswordDraft.isNotBlank() &&
                    confirmPasswordDraft.isNotBlank()
            ) {
                ButtonLabel("Update Password")
            }
        }
        Button(
            onClick = {
                changedDrafts.forEach { (categoryId, idealOrderText) ->
                    onUpdateIdealOrder(categoryId, idealOrderText)
                }
            },
            enabled = changedDrafts.isNotEmpty()
        ) {
            ButtonLabel("Save")
        }
        DetailHeaderRow(listOf("Category", "Protected ideal order", "Length", "Climb", "Route"))
        categories.forEach { categoryData ->
            val categoryId = categoryData.category.id
            val courseInfo = protectedCourseInfoByCategoryId[categoryId]
            ProtectedCourseOrderRow(
                categoryName = categoryData.category.name,
                idealOrderDraft = idealOrderDrafts[categoryId].orEmpty(),
                protectedCourseInfo = courseInfo,
                onIdealOrderChange = { idealOrderText ->
                    idealOrderDrafts = idealOrderDrafts + (categoryId to idealOrderText)
                }
            )
        }
    }
}

@Composable
private fun ProtectedCourseOrderRow(
    categoryName: String,
    idealOrderDraft: String,
    protectedCourseInfo: ProtectedCourseInfo?,
    onIdealOrderChange: (String) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = categoryName,
            modifier = Modifier.width(160.dp),
            color = DesktopPalette.Black,
            fontSize = 13.sp
        )
        TextField(
            value = idealOrderDraft,
            onValueChange = onIdealOrderChange,
            modifier = Modifier.width(360.dp),
            singleLine = true,
            label = { Text("Ideal order") }
        )
        Text(
            text = protectedCourseInfo?.lengthMeters?.let { "$it m" } ?: "",
            modifier = Modifier.width(96.dp),
            color = DesktopPalette.Black,
            fontSize = 13.sp
        )
        Text(
            text = protectedCourseInfo?.climbMeters?.let { "$it m" } ?: "",
            modifier = Modifier.width(96.dp),
            color = DesktopPalette.Black,
            fontSize = 13.sp
        )
        Text(
            text = protectedCourseInfo?.let { "${it.sampledPointCount} pts" } ?: "",
            modifier = Modifier.width(120.dp),
            color = DesktopPalette.Black,
            fontSize = 13.sp
        )
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
            onControlPointsChange = {
                controlPointsDraft = it
                onUpdateCategoryControlPoints(category.id, it)
            },
            modifier = Modifier.width(CategoryTableColumns[6].width)
        )
    }
}

@Composable
private fun AssignedControlsEditor(
    controlPointsDraft: String,
    controls: List<EventControlDetails>,
    onControlPointsChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val publicLabelControls = remember(controls) {
        controls
            .filter { isPickerSafePublicLabel(it.publicDisplayLabel()) }
            .groupBy { it.publicDisplayLabel() }
            .filterValues { controlsForLabel -> controlsForLabel.size == 1 }
            .values
            .map { controlsForLabel -> controlsForLabel.single() }
            .sortedWith(compareBy<EventControlDetails> { it.publicDisplayLabel().lowercase() }.thenBy { it.siCode })
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(TableColumnGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = controlPointsDraft,
            onValueChange = onControlPointsChange,
            modifier = Modifier.width(232.dp),
            singleLine = true,
            label = { Text("Assigned") }
        )
        Box(modifier = Modifier.width(76.dp)) {
            Button(
                onClick = { expanded = true },
                enabled = publicLabelControls.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                ButtonLabel("Pick")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                publicLabelControls.forEach { control ->
                    DropdownMenuItem(
                        onClick = {
                            expanded = false
                            onControlPointsChange(appendPublicControlLabel(controlPointsDraft, control.publicDisplayLabel()))
                        }
                    ) {
                        Text(control.publicDisplayLabel())
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
    onRenameRace: (String) -> Unit,
    onUpdateRaceStartDateTime: (String) -> Unit,
    onUpdateRaceSettings: (RaceType, RaceLevel, RaceBand, String) -> Unit
) {
    var raceNameDraft by remember(details.name) { mutableStateOf(details.name) }
    var startDateTimeDraft by remember(details.startDateTimeIso) {
        mutableStateOf(
            DesktopDateTimeText.parseIsoOrNull(details.startDateTimeIso) ?: DesktopDateTimeText.defaultStartDateTime()
        )
    }
    var selectedRaceType by remember(details.raceType) { mutableStateOf(details.raceType) }
    var selectedRaceLevel by remember(details.raceLevel) { mutableStateOf(details.raceLevel) }
    var selectedRaceBand by remember(details.raceBand) { mutableStateOf(details.raceBand) }
    var timeLimitMinutesDraft by remember(details.timeLimitMinutesText) {
        mutableStateOf(details.timeLimitMinutesText)
    }

    fun applyRaceSettings(
        raceType: RaceType = selectedRaceType,
        raceLevel: RaceLevel = selectedRaceLevel,
        raceBand: RaceBand = selectedRaceBand,
        timeLimitMinutes: String = timeLimitMinutesDraft
    ) {
        val timeLimit = timeLimitMinutes.trim().toLongOrNull()
        if (timeLimit != null && timeLimit >= 0) {
            onUpdateRaceSettings(raceType, raceLevel, raceBand, timeLimitMinutes)
        }
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
                },
                modifier = Modifier.weight(1f),
                label = { Text("Event name") }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DateTimePickerField(
                label = "Start date/time",
                value = startDateTimeDraft,
                onValueChange = {
                    startDateTimeDraft = it
                    onUpdateRaceStartDateTime(DesktopDateTimeText.isoText(it))
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RaceTypePicker(
                selectedRaceType,
                {
                    selectedRaceType = it
                    applyRaceSettings(raceType = it)
                },
                Modifier.weight(1f)
            )
            RaceLevelPicker(
                selectedRaceLevel,
                {
                    val defaultLimitMinutes = it.defaultTimeLimitMinutes()?.toString()
                    val nextTimeLimitMinutes = defaultLimitMinutes ?: timeLimitMinutesDraft
                    selectedRaceLevel = it
                    if (defaultLimitMinutes != null) {
                        timeLimitMinutesDraft = nextTimeLimitMinutes
                    }
                    applyRaceSettings(raceLevel = it, timeLimitMinutes = nextTimeLimitMinutes)
                },
                Modifier.weight(1f)
            )
            RaceBandPicker(
                selectedRaceBand,
                {
                    selectedRaceBand = it
                    applyRaceSettings(raceBand = it)
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
        title = { Text("Pick start date/time") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
            delayMillis = 350
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

private fun EventControlDetails.publicDisplayLabel(): String =
    publicLabel.trim().ifEmpty { label }

private fun pickerControlToken(publicLabel: String): String {
    val label = publicLabel.trim()
    val needsQuoting = label.any { it.isWhitespace() || it == ',' || it == ';' }
    return if (needsQuoting) "'$label'" else label
}

private fun isPickerSafePublicLabel(publicLabel: String): Boolean {
    val label = publicLabel.trim()
    return label.isNotEmpty() && '\'' !in label && '"' !in label
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

@Composable
private fun WorkflowHomePanel(workflow: DesktopWorkflow, projectFile: EventProjectFile?) {
    val logoBitmap = remember {
        val logoBytes = requireNotNull(
            Thread.currentThread().contextClassLoader.getResourceAsStream("radio-oracle-logo.png")
        ) {
            "Radio-Oracle desktop logo resource is missing."
        }.use { stream -> stream.readBytes() }
        SkiaImage.makeFromEncoded(logoBytes).toComposeImageBitmap()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            bitmap = logoBitmap,
            contentDescription = "Radio-Oracle logo",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .width(128.dp)
                .height(128.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Radio-Oracle",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = DesktopPalette.Black
            )
            Text(
                text = "Event administration for radio orienteering: prepare the Event File, run SI-card download operations, and publish results from one desktop workspace.",
                color = DesktopPalette.Black,
                fontSize = 15.sp
            )
            Text(
                text = "Current workflow: ${workflow.label}",
                color = DesktopPalette.Disconnected,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = projectFile?.let { "Event File open: ${EventProjectSummary.from(it).raceName}" }
                    ?: "Create a new Event File or open an existing one to begin.",
                color = DesktopPalette.Disconnected,
                fontSize = 13.sp
            )
        }
    }
}

/** Provides section-specific content summaries without introducing editing behavior. */
private fun sectionSummary(section: DesktopSection, projectFile: EventProjectFile?): String {
    val summary = projectFile?.let(EventProjectSummary::from)
    fun requiresEventFile(action: String): String =
        "Open or create an Event File before $action."
    return when (section) {
        DesktopSection.WorkflowHome -> "Workflow overview for event setup, race operations, results, exports, and app support."
        DesktopSection.EventFile -> summary?.let { "Event File open: ${it.raceName}" }
            ?: "Create a new Event File or open an existing one to begin."
        DesktopSection.Races -> summary?.raceName ?: "Create a new Event File or open an existing one to begin."
        DesktopSection.Categories -> summary?.let { "${it.categoryCount} categories loaded." }
            ?: requiresEventFile("editing categories")
        DesktopSection.ProtectedCourseOrder -> summary?.let { "${it.categoryCount} categories loaded." }
            ?: requiresEventFile("editing protected course order")
        DesktopSection.Competitors -> summary?.let { "${it.competitorCount} competitors loaded." }
            ?: requiresEventFile("editing competitors")
        DesktopSection.StartList -> summary?.let { "Competitors sorted by drawn start time." }
            ?: requiresEventFile("working with the start list")
        DesktopSection.Controls -> projectFile?.let { "${it.raceData.controls.size} controls loaded." }
            ?: requiresEventFile("editing controls")
        DesktopSection.Readouts -> summary?.let { "${it.readoutCount} SI-card readouts loaded." }
            ?: requiresEventFile("working with SI-card readouts")
        DesktopSection.InForest -> summary?.let { "Started competitors without readouts." }
            ?: requiresEventFile("reviewing competitors in the forest")
        DesktopSection.Results -> summary?.let { "${it.resultCount} results loaded." }
            ?: requiresEventFile("viewing results")
        DesktopSection.Settings -> "Event File diagnostics and desktop beta scope."
    }
}

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
    siReaderState: DesktopSiReaderUiState,
    isEventFileOpen: Boolean
) {
    val effectiveSeverity = if (siReaderState.severity == DesktopSiReaderSeverity.CONNECTED && !isEventFileOpen) {
        DesktopSiReaderSeverity.WARNING
    } else {
        siReaderState.severity
    }
    val backgroundColor = when (effectiveSeverity) {
        DesktopSiReaderSeverity.DISCONNECTED -> DesktopPalette.Disconnected
        DesktopSiReaderSeverity.CONNECTED -> DesktopPalette.Connected
        DesktopSiReaderSeverity.WARNING -> DesktopPalette.Warning
        DesktopSiReaderSeverity.ERROR -> DesktopPalette.Error
    }
    val textColor = when (effectiveSeverity) {
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
