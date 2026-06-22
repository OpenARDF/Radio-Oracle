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
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
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
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
import java.awt.datatransfer.StringSelection
import java.awt.image.BufferedImage
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
import org.openardf.radiooracle.shared.event.ControlRoleLabelRules
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
import org.openardf.radiooracle.shared.event.EventSeriesIssueSeverity
import org.openardf.radiooracle.shared.event.EventSeriesSupport
import org.openardf.radiooracle.shared.event.EventSeriesValidationIssue
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
import java.net.URI
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
import kotlin.coroutines.coroutineContext

private data class FixedTableColumn(val title: String, val width: Dp)

private val TableColumnGap = 12.dp
private val ActionRailWidth = 104.dp
private val FixedGridRowHeight = 56.dp
private val ReadoutAddRailYOffset = 10.dp
private const val DesktopSiPollIntervalMs = 5_000L
private const val DesktopLiveResultSendIntervalMs = 15_000L
private const val CourseAnalysisCalculatedRouteElevationResolutionMeters = 10.0
private const val CourseAnalysisCalculatedRouteElevationBufferMeters = 50.0

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
    FixedTableColumn("Gender", 92.dp),
    FixedTableColumn("Length (m)", 96.dp),
    FixedTableColumn("Climb (m)", 92.dp),
    FixedTableColumn("Type", 96.dp),
    FixedTableColumn("Band", 104.dp),
    FixedTableColumn("Limit (min.)", 104.dp),
    FixedTableColumn("Assigned Controls", 320.dp)
)

private val CategoryTableColumnHints = mapOf(
    "Name" to "Category/class name used for competitor assignment, start lists, and results.",
    "Gender" to "Category gender used for exports and age/gender result grouping.",
    "Length (m)" to "Course length for this category in meters. This public value is used in exports and result displays.",
    "Climb (m)" to "Total climb for this category in meters. This public value is used in exports and result displays.",
    "Type" to "Race type used by this category. It normally follows the Event File setting unless category-specific properties are imported.",
    "Band" to "Frequency band used by this category. It normally follows the Event File setting unless category-specific properties are imported.",
    "Limit (min.)" to "Time limit for this category in minutes. It normally follows the Event File setting unless category-specific properties are imported.",
    "Assigned Controls" to "Ordered controls for this category. Separate entries with spaces, commas, or semicolons. Use the picker to insert Public labels. Manual entries may use SI codes, defined control labels, or Public label values; put labels containing spaces in single or double quotes, such as 'Fox 1'."
)
private val CategoryMenBackground = Color(0xFFE8F3FF)
private val CategoryWomenBackground = Color(0xFFFFECEC)
private val ControlStatsOkColor = Color(0xFF1B5E20)
private const val SeriesStartFairnessGoodThreshold = 90
private const val SeriesStartFairnessManualReviewThreshold = 70
private const val EventStartListUniqueDrawMaxAttempts = 64

private enum class SeriesStartFairnessStatus(val label: String) {
    GenerateStarts("Generate start lists before checking fairness"),
    AddIdentityData("Add SI, bib, or call sign identity data"),
    MoreHistoryNeeded("More identified start history needed"),
    AllEventsLocked("All Event Files are locked for Series optimization"),
    NoOptimizationNeeded("No optimization needed"),
    ManualReviewRecommended("Manual start-parameter review recommended"),
    NoBetterOptimizationsFound("No better optimizations found"),
    OptimizationRecommended("Optimization recommended")
}

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
    FixedTableColumn("Start", 72.dp),
    FixedTableColumn("Time", 96.dp),
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
        val publicResultSitePublisher = remember { DesktopCloudflarePagesPublisher() }
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
        var pendingControlRoleWarning by remember { mutableStateOf<String?>(null) }
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
        var localResultsWebServerDirectory by remember { mutableStateOf<Path?>(null) }
        var localResultsWebServerEventPath by remember { mutableStateOf<String?>(null) }
        var localResultsWebServer by remember { mutableStateOf<DesktopPublicResultSitePreviewServer?>(null) }
        var localResultsWebServerUrl by remember { mutableStateOf<String?>(null) }
        var localResultsWebServerRefreshJob by remember { mutableStateOf<Job?>(null) }
        var publicResultSiteDirectory by remember { mutableStateOf<Path?>(null) }
        var publicResultSiteEventPath by remember { mutableStateOf<String?>(null) }
        var publicResultSitePreviewServer by remember { mutableStateOf<DesktopPublicResultSitePreviewServer?>(null) }
        var publicResultSitePreviewUrl by remember { mutableStateOf<String?>(null) }
        var publishedPublicResultSiteUrl by remember { mutableStateOf<String?>(null) }
        var isPublishingPublicResultSite by remember { mutableStateOf(false) }
        var eventFileTransferServer by remember { mutableStateOf<DesktopEventFileTransferServer?>(null) }
        var eventFileTransferDialog by remember { mutableStateOf<DesktopEventFileTransferDialogState?>(null) }
        var eventFileTransferResultDialog by remember { mutableStateOf<DesktopEventFileTransferResultDialogState?>(null) }
        var eventFileTransferRequestId by remember { mutableStateOf(0) }
        var androidFileReceiveServer by remember { mutableStateOf<DesktopAndroidFileReceiveServer?>(null) }
        var androidFileReceiveDialog by remember { mutableStateOf<DesktopAndroidFileReceiveDialogState?>(null) }
        var androidFileReceiveResultDialog by remember { mutableStateOf<DesktopAndroidFileReceiveResultDialogState?>(null) }
        var isAboutDialogVisible by remember { mutableStateOf(false) }
        var isUpdateCheckingEnabled by remember {
            mutableStateOf(DesktopAppSettingsPreferences.isUpdateCheckingEnabled())
        }
        var cloudflarePagesPublishSettings by remember {
            mutableStateOf(DesktopAppSettingsPreferences.cloudflarePagesPublishSettings())
        }
        var updateCheckStatus by remember { mutableStateOf<DesktopAppUpdateStatus?>(null) }
        var appUpdateDialogStatus by remember { mutableStateOf<DesktopAppUpdateStatus?>(null) }
        var raceClockTick by remember { mutableStateOf(0L) }
        var printerDiagnostics by remember { mutableStateOf(DesktopPrinterDiagnostics.from(emptyList())) }
        var lastLoggedSiReaderStatus by remember { mutableStateOf<String?>(null) }
        var protectedCoursePassword by remember { mutableStateOf<String?>(null) }
        var protectedIdealOrderByCategoryId by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
        var protectedCourseInfoByCategoryId by remember { mutableStateOf<Map<String, ProtectedCourseInfo>>(emptyMap()) }
        var recentImportReport by remember { mutableStateOf<DesktopImportReport?>(null) }
        var recentImportCheckpoint by remember { mutableStateOf<DesktopImportCheckpoint?>(null) }
        var recentActivityLog by remember { mutableStateOf<List<String>>(emptyList()) }
        var seriesEventSummaries by remember { mutableStateOf<List<DesktopEventSeriesEventSummary>>(emptyList()) }
        var seriesStartFairnessSummary by remember { mutableStateOf<DesktopEventSeriesStartFairnessSummary?>(null) }
        var seriesStartFairnessOptimizationResult by remember {
            mutableStateOf<DesktopEventSeriesStartFairnessOptimizationResult?>(null)
        }
        var seriesStartFairnessSolutionNumbers by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
        var eventStartListDrawNumbers by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
        var eventStartListDrawProjects by remember { mutableStateOf<Map<String, EventProjectFile>>(emptyMap()) }
        var eventStartListDrawNumbering by remember { mutableStateOf<DesktopStartListDrawNumbering?>(null) }
        var eventStartListDrawEventPath by remember { mutableStateOf<Path?>(null) }
        var eventStartListDrawExhaustedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
        var seriesCompetitorMatchSummaries by remember {
            mutableStateOf<List<DesktopEventSeriesCompetitorMatchSummary>>(emptyList())
        }
        var seriesCompetitorIdentityCoverageSummaries by remember {
            mutableStateOf<List<DesktopEventSeriesCompetitorIdentityCoverageSummary>>(emptyList())
        }
        var eventSeriesUiContext by remember { mutableStateOf<EventSeriesUiContext?>(null) }
        var eventSeriesValidationState by remember { mutableStateOf<EventSeriesValidationUiState?>(null) }
        var eventSeriesValidationEventPath by remember { mutableStateOf<Path?>(null) }
        var isEventRegImportDialogVisible by remember { mutableStateOf(false) }
        var isEventRegCompetitorCsvImportDialogVisible by remember { mutableStateOf(false) }
        var pendingCourseKmlKmzUnlockAction by remember { mutableStateOf<CourseKmlKmzUnlockAction?>(null) }
        var pendingProtectedControlDeleteId by remember { mutableStateOf<String?>(null) }
        var pendingBulkCategoryAction by remember { mutableStateOf<BulkCategoryAction?>(null) }
        var isDeleteAllControlsDialogVisible by remember { mutableStateOf(false) }
        var isDeleteAllCompetitorsDialogVisible by remember { mutableStateOf(false) }
        var pendingCourseKmlKmzImportReview by remember { mutableStateOf<PendingCourseKmlKmzImportReview?>(null) }
        var pendingCourseKmlKmzImportWarning by remember { mutableStateOf<PendingCourseKmlKmzImportWarning?>(null) }
        var pendingCourseKmlKmzCategoryMapping by remember { mutableStateOf<PendingCourseKmlKmzCategoryMapping?>(null) }
        var pendingCategoriesCsvImportReview by remember { mutableStateOf<PendingCategoriesCsvImportReview?>(null) }
        var pendingControlsCsvImportReview by remember { mutableStateOf<PendingControlsCsvImportReview?>(null) }
        var courseKmlKmzElevationProgress by remember { mutableStateOf<CourseKmlKmzElevationProgressUiState?>(null) }
        var courseKmlKmzElevationJob by remember { mutableStateOf<Job?>(null) }
        var suppressNextCourseElevationCancelStatus by remember { mutableStateOf(false) }
        var venueElevationCacheProgress by remember { mutableStateOf<VenueElevationCacheProgressUiState?>(null) }
        var venueElevationCacheJob by remember { mutableStateOf<Job?>(null) }
        var venueElevationCacheRefreshToken by remember { mutableStateOf(0) }
        var pendingDemFileImportReview by remember { mutableStateOf<DesktopVenueElevationDemImportReview?>(null) }
        var eventRegImportUrl by remember { mutableStateOf(DesktopEventRegImportPreferences.lastRegistrationUrl()) }
        var isImportingEventRegWebsite by remember { mutableStateOf(false) }
        var isImportingEventRegCompetitorCsvs by remember { mutableStateOf(false) }
        var isImportingCourseKmlKmz by remember { mutableStateOf(false) }
        var pendingCompetitorsCsvImportReview by remember { mutableStateOf<PendingCompetitorsCsvImportReview?>(null) }
        var syncCompetitorsCsvImport by remember { mutableStateOf(false) }
        val siPortMutex = remember { Mutex() }
        val activeEventFileTransferServer by rememberUpdatedState(eventFileTransferServer)
        val activeAndroidFileReceiveServer by rememberUpdatedState(androidFileReceiveServer)
        val activeLocalResultsWebServer by rememberUpdatedState(localResultsWebServer)
        val activeLocalResultsWebServerRefreshJob by rememberUpdatedState(localResultsWebServerRefreshJob)
        val activePublicResultSitePreviewServer by rememberUpdatedState(publicResultSitePreviewServer)

        LaunchedEffect(projectFile?.raceData?.race?.id) {
            publishedPublicResultSiteUrl = null
            localResultsWebServerRefreshJob?.cancel()
            localResultsWebServerRefreshJob = null
            localResultsWebServer?.stop()
            localResultsWebServer = null
            localResultsWebServerUrl = null
            localResultsWebServerDirectory = null
            localResultsWebServerEventPath = null
        }

        DisposableEffect(Unit) {
            onDispose {
                activeEventFileTransferServer?.stop()
                activeAndroidFileReceiveServer?.stop()
                activeLocalResultsWebServerRefreshJob?.cancel()
                activeLocalResultsWebServer?.stop()
                activePublicResultSitePreviewServer?.stop()
            }
        }

        LaunchedEffect(Unit) {
            DesktopDebugLog.initialize()
            DesktopDebugLog.info("App", "Desktop app started version=${DesktopBuildInfo.displayVersion}")
            if (isUpdateCheckingEnabled) {
                val status = DesktopAppUpdateSupport.status(currentVersion = DesktopBuildInfo.baseVersion)
                updateCheckStatus = status
                if (DesktopAppUpdateSupport.shouldShowAutomaticNotice(status)) {
                    appUpdateDialogStatus = status
                    DesktopDebugLog.info("Update", "jDeploy reported a Radio-Oracle update is available.")
                }
            }
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

        fun currentSeriesManifestPath(): Path? {
            val currentPath = projectSession.currentPath ?: return null
            return DesktopEventSeriesActions.findManifestNearEvent(
                eventPath = currentPath,
                seriesLink = projectSession.currentProject?.seriesLink,
                store = DesktopEventSeriesFiles
            )
        }

        fun refreshSavedEventSeriesMetadata(savedProject: EventProjectFile, savedPath: Path): String? {
            if (savedProject.seriesLink == null) {
                return null
            }
            val manifestPath = DesktopEventSeriesActions.findManifestNearEvent(
                eventPath = savedPath,
                seriesLink = savedProject.seriesLink,
                store = DesktopEventSeriesFiles
            )
                ?: return "Event Series metadata was not refreshed because no series manifest was found near this Event File."
            return runCatching {
                val seriesFile = DesktopEventSeriesFiles.read(manifestPath)
                val seriesFolder = requireNotNull(manifestPath.parent) {
                    "Event Series manifest has no parent folder."
                }
                val refreshedSeriesFile = DesktopEventSeriesActions.refreshLinkedEventMetadata(
                    seriesFile = seriesFile,
                    eventPath = savedPath,
                    seriesFolder = seriesFolder,
                    eventProjectFile = savedProject
                )
                if (refreshedSeriesFile != seriesFile) {
                    DesktopEventSeriesFiles.write(manifestPath, refreshedSeriesFile)
                    DesktopDebugLog.info(
                        "EventSeries",
                        "Refreshed saved Event File metadata in ${manifestPath.fileName} for ${savedPath.fileName}."
                    )
                }
                null
            }.getOrElse { error ->
                val message = error.message ?: error::class.simpleName ?: "Unknown error"
                DesktopDebugLog.error("EventSeries", "Failed to refresh saved Event File metadata: $message")
                "Event Series metadata refresh failed: $message"
            }
        }

        fun refreshSeriesEventSummaries() {
            val currentPath = projectSession.currentPath
            val manifestPath = currentPath?.let {
                DesktopEventSeriesActions.findManifestNearEvent(
                    eventPath = it,
                    seriesLink = projectSession.currentProject?.seriesLink,
                    store = DesktopEventSeriesFiles
                )
            }
            if (manifestPath == null) {
                seriesEventSummaries = emptyList()
                seriesStartFairnessSummary = null
                seriesCompetitorMatchSummaries = emptyList()
                seriesCompetitorIdentityCoverageSummaries = emptyList()
                eventSeriesUiContext = null
            } else {
                runCatching {
                    val seriesFile = DesktopEventSeriesFiles.read(manifestPath)
                    eventSeriesUiContext = EventSeriesUiContext(
                        manifestPath = manifestPath,
                        seriesName = seriesFile.name
                    )
                    seriesEventSummaries = DesktopEventSeriesActions.eventSummaries(
                        store = DesktopEventSeriesFiles,
                        manifestPath = manifestPath,
                        currentEventPath = currentPath
                    )
                    seriesStartFairnessSummary = DesktopEventSeriesActions.startFairnessSummary(
                        store = DesktopEventSeriesFiles,
                        manifestPath = manifestPath,
                        currentEventPath = currentPath,
                        currentProjectFile = projectSession.currentProject
                    )
                    seriesCompetitorMatchSummaries = DesktopEventSeriesActions.competitorMatchingSummaries(
                        store = DesktopEventSeriesFiles,
                        manifestPath = manifestPath,
                        currentEventPath = currentPath
                    )
                    seriesCompetitorIdentityCoverageSummaries =
                        DesktopEventSeriesActions.competitorIdentityCoverageSummaries(
                            store = DesktopEventSeriesFiles,
                            manifestPath = manifestPath
                        )
                }.getOrElse {
                    DesktopDebugLog.error(
                        "EventSeries",
                        "Failed to refresh Event Series events manifest=$manifestPath: ${it.message ?: it::class.simpleName}"
                    )
                    seriesEventSummaries = emptyList()
                    seriesStartFairnessSummary = null
                    seriesCompetitorMatchSummaries = emptyList()
                    seriesCompetitorIdentityCoverageSummaries = emptyList()
                    eventSeriesUiContext = null
                }
            }
        }

        fun syncProjectState() {
            projectFile = projectSession.currentProject
            hasUnsavedChanges = projectSession.hasUnsavedChanges
            refreshSeriesEventSummaries()
            val currentEventPath = projectSession.currentPath?.toAbsolutePath()?.normalize()
            if (eventSeriesValidationEventPath != currentEventPath) {
                eventSeriesValidationState = null
                eventSeriesValidationEventPath = currentEventPath
            }
            if (eventStartListDrawEventPath != currentEventPath) {
                eventStartListDrawNumbering = null
                eventStartListDrawEventPath = currentEventPath
            } else {
                val currentProject = projectSession.currentProject
                val currentNumbering = eventStartListDrawNumbering
                if (
                    currentProject != null &&
                    currentNumbering != null &&
                    currentNumbering.assignmentSignature != DesktopStartListDrawNumbers.startAssignmentSignature(currentProject)
                ) {
                    eventStartListDrawNumbering = null
                }
            }
        }

        fun clearEventStartListDrawHistory() {
            eventStartListDrawNumbers = emptyMap()
            eventStartListDrawProjects = emptyMap()
            eventStartListDrawNumbering = null
            eventStartListDrawExhaustedKeys = emptySet()
        }

        LaunchedEffect(Unit) {
            refreshSeriesEventSummaries()
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

        fun EventProjectFile.startDrawSettingsLogText(): String {
            val settings = raceData.effectiveStartDrawSettings()
            return "startDraw interval=${settings.intervalText} club=${settings.options.clubHandling.name} " +
                "starters=${settings.options.startersPerStartTime} groups=${settings.options.startGroupMode.name} " +
                "seed=${settings.options.seed} seriesLock=${settings.lockedForSeriesOptimization}"
        }

        fun deleteControlAfterProtectedRouteCheck(controlId: String, promptIfLocked: Boolean = true): Boolean {
            val currentProject = projectSession.currentProject
            if (currentProject == null) {
                projectStatusText = "Edit failed: Load an Event File before deleting controls."
                return false
            }
            if (currentProject.hasProtectedCategoryData() && protectedCoursePassword == null) {
                val controlLabel = currentProject.raceData.controls
                    .firstOrNull { it.id == controlId }
                    ?.publicDisplayLabel()
                    ?: "this control"
                if (promptIfLocked) {
                    pendingProtectedControlDeleteId = controlId
                    projectStatusText = "Unlock course data to delete $controlLabel."
                } else {
                    projectStatusText = "Edit failed: Course data is locked. Unlock course data before deleting controls so protected route references can be cleaned."
                }
                return false
            }
            val result = runCatching {
                val currentProjectForDelete = projectSession.currentProject
                    ?: throw IllegalStateException("Load an Event File before deleting controls.")
                val cleanupResult = if (protectedCourseInfoByCategoryId.isNotEmpty()) {
                    val password = protectedCoursePassword
                        ?: throw IllegalStateException("Unlock course data before deleting controls so protected route references can be cleaned.")
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
                    EventProjectEditor.removeControl(
                        cleanupResult.projectFile,
                        controlId,
                        clearProtectedCourseData = false
                    )
                }
                protectedCourseInfoByCategoryId = cleanupResult.protectedCourseInfoByCategoryId
                protectedIdealOrderByCategoryId = emptyMap()
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
                projectSession.open(path)
                newEventDraftProject = null
                hasUnsavedEventDefinitionChanges = false
                isEventDefinitionSaveDialogVisible = false
                syncProjectState()
                DesktopLastEventFilePreferences.rememberEventFile(path)
                runCatching {
                    DesktopEventSeriesActions.rememberOpenedSeriesEvent(
                        store = DesktopEventSeriesFiles,
                        eventPath = path,
                        lastSeriesEventStore = DesktopLastSeriesEventPreferences
                    )
                }.onFailure { error ->
                    DesktopDebugLog.error(
                        "EventSeries",
                        "Could not remember last opened Event Series member: ${error.message ?: error::class.simpleName}"
                    )
                }
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

        fun localResultsWebPageUrl(rootUrl: String, eventPath: String?): String =
            eventPath
                ?.takeIf { it.isNotBlank() }
                ?.let { path -> "$rootUrl${path.trim('/')}/" }
                ?: rootUrl

        fun regenerateLocalResultsWebPage(): String {
            val currentProject = requireNotNull(projectSession.currentProject) {
                "Open or create an Event File before starting the local web server."
            }
            val directory = localResultsWebServerDirectory
                ?: Files.createTempDirectory("radio-oracle-local-results-web").also {
                    localResultsWebServerDirectory = it
                }
            val paths = DesktopProjectFiles.exportPublicResultsSite(
                directory,
                currentProject,
                protectedCourseInfoByCategoryId.takeIf { protectedCoursePassword != null } ?: emptyMap()
            )
            localResultsWebServerDirectory = paths.directory
            localResultsWebServerEventPath = paths.eventPath
            DesktopDebugLog.info(
                "PublicResults",
                "Regenerated local results web page root=${paths.directory} event=${paths.eventPath}"
            )
            return paths.eventPath
        }

        fun scheduleLocalResultsWebPageRefresh() {
            if (localResultsWebServer == null) {
                return
            }
            localResultsWebServerRefreshJob?.cancel()
            localResultsWebServerRefreshJob = appCoroutineScope.launch {
                delay(60_000L)
                runCatching {
                    regenerateLocalResultsWebPage()
                }.onSuccess {
                    val url = localResultsWebServerUrl
                    projectStatusText = if (url == null) {
                        "Local results web page refreshed."
                    } else {
                        "Local results web page refreshed at $url"
                    }
                }.onFailure { error ->
                    projectStatusText = "Local results web page refresh failed: ${error.message ?: error::class.simpleName}"
                    DesktopDebugLog.error("PublicResults", projectStatusText)
                }
            }
            projectStatusText = "Local results web page will refresh 1 minute after the latest SI download."
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
            scheduleLocalResultsWebPageRefresh()
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
                    val seriesRefreshWarning = projectSession.currentProject?.let { savedProject ->
                        refreshSavedEventSeriesMetadata(savedProject, path)
                    }
                    newEventDraftProject = null
                    hasUnsavedEventDefinitionChanges = false
                    isEventDefinitionSaveDialogVisible = false
                    syncProjectState()
                    DesktopLastEventFilePreferences.rememberEventFile(path)
                    projectStatusText = buildString {
                        append("Saved ${path.fileName}")
                        if (seriesRefreshWarning != null) {
                            append(". ")
                            append(seriesRefreshWarning)
                        }
                    }
                    val settingsLog = projectSession.currentProject?.startDrawSettingsLogText().orEmpty()
                    DesktopDebugLog.info("EventFile", "Saved ${path.fileName} $settingsLog")
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
                val savedPath = projectSession.currentPath
                val seriesRefreshWarning = if (savedPath != null) {
                    projectSession.currentProject?.let { savedProject ->
                        refreshSavedEventSeriesMetadata(savedProject, savedPath)
                    }
                } else {
                    null
                }
                newEventDraftProject = null
                hasUnsavedEventDefinitionChanges = false
                isEventDefinitionSaveDialogVisible = false
                syncProjectState()
                projectSession.currentPath?.let(DesktopLastEventFilePreferences::rememberEventFile)
                projectStatusText = buildString {
                    append("Saved ${projectSession.currentPath?.fileName ?: "Event File"}")
                    if (seriesRefreshWarning != null) {
                        append(". ")
                        append(seriesRefreshWarning)
                    }
                }
                val settingsLog = projectSession.currentProject?.startDrawSettingsLogText().orEmpty()
                DesktopDebugLog.info(
                    "EventFile",
                    "Saved ${projectSession.currentPath?.fileName ?: "Event File"} $settingsLog"
                )
            }.onFailure { error ->
                projectStatusText = "Save failed: ${error.message ?: error::class.simpleName}"
                DesktopDebugLog.error("EventFile", "Save failed: ${error.message ?: error::class.simpleName}")
            }.isSuccess
        }

        fun createEventSeriesWithCurrentEvent() {
            val currentProject = projectSession.currentProject ?: run {
                projectStatusText = "Open or create an Event File before creating an Event Series."
                return
            }
            val currentPath = projectSession.currentPath ?: run {
                projectStatusText = "Save this Event File before creating an Event Series."
                return
            }
            val seriesFolder = currentPath.parent ?: run {
                projectStatusText = "Event Series creation failed: Event File has no parent folder."
                return
            }
            runCatching {
                val result = DesktopEventSeriesActions.createSeriesWithEvent(
                    seriesFolder = seriesFolder,
                    seriesId = "${UUID.randomUUID()}-series",
                    seriesName = DesktopEventSeriesActions.DEFAULT_SERIES_NAME,
                    eventPath = currentPath,
                    eventProjectFile = currentProject
                )
                DesktopEventSeriesFiles.write(result.manifestPath, result.seriesFile)
                projectFile = projectSession.updateCurrentProject { result.eventProjectFile }
                projectSession.save()
                syncProjectState()
                projectStatusText = "Created Event Series ${result.manifestPath.fileName} and linked this Event File."
                DesktopDebugLog.info(
                    "EventSeries",
                    "Created ${result.manifestPath.fileName} for ${currentPath.fileName} " +
                        "seriesId=${result.seriesFile.seriesId} " +
                        "seriesEventId=${result.eventProjectFile.seriesLink?.seriesEventId.orEmpty()}"
                )
                recordActivity(projectStatusText)
            }.onFailure { error ->
                projectStatusText = "Create Event Series failed: ${error.message ?: error::class.simpleName}"
                DesktopDebugLog.error("EventSeries", projectStatusText)
            }
        }

        fun linkCurrentEventToSeries() {
            val currentProject = projectSession.currentProject ?: run {
                projectStatusText = "Open or create an Event File before linking to an Event Series."
                return
            }
            val currentPath = projectSession.currentPath ?: run {
                projectStatusText = "Save this Event File before linking to an Event Series."
                return
            }
            val manifestPath = DesktopFileDialogs.chooseOpenEventSeries() ?: return
            runCatching {
                val seriesFile = DesktopEventSeriesFiles.read(manifestPath)
                val seriesFolder = requireNotNull(manifestPath.parent) {
                    "Event Series manifest has no parent folder."
                }
                val result = DesktopEventSeriesActions.linkCurrentEvent(
                    seriesFile = seriesFile,
                    eventPath = currentPath,
                    seriesFolder = seriesFolder,
                    eventProjectFile = currentProject
                )
                DesktopEventSeriesFiles.write(manifestPath, result.seriesFile)
                projectFile = projectSession.updateCurrentProject { result.eventProjectFile }
                projectSession.save()
                syncProjectState()
                projectStatusText = "Linked this Event File to ${manifestPath.fileName}."
                DesktopDebugLog.info(
                    "EventSeries",
                    "Linked ${currentPath.fileName} to ${manifestPath.fileName} " +
                        "seriesId=${result.seriesFile.seriesId} " +
                        "seriesEventId=${result.eventProjectFile.seriesLink?.seriesEventId.orEmpty()}"
                )
                recordActivity(projectStatusText)
            }.onFailure { error ->
                projectStatusText = "Link Event Series failed: ${error.message ?: error::class.simpleName}"
                DesktopDebugLog.error("EventSeries", projectStatusText)
            }
        }

        fun removeCurrentEventFromSeries() {
            val currentProject = projectSession.currentProject ?: run {
                projectStatusText = "Open or create an Event File before removing a series link."
                return
            }
            val manifestPath = currentSeriesManifestPath() ?: run {
                projectStatusText = "Series manifest not found near this Event File; no link was removed."
                return
            }
            runCatching {
                val seriesFile = DesktopEventSeriesFiles.read(manifestPath)
                val result = DesktopEventSeriesActions.removeCurrentEvent(seriesFile, currentProject)
                DesktopEventSeriesFiles.write(manifestPath, result.seriesFile)
                projectFile = projectSession.updateCurrentProject { result.eventProjectFile }
                projectSession.save()
                syncProjectState()
                projectStatusText = "Removed this Event File from ${manifestPath.fileName}."
                recordActivity(projectStatusText)
            }.onFailure { error ->
                projectStatusText = "Remove Event Series link failed: ${error.message ?: error::class.simpleName}"
            }
        }

        fun validateCurrentEventSeries() {
            val manifestPath = currentSeriesManifestPath() ?: run {
                eventSeriesValidationState = null
                eventSeriesValidationEventPath = projectSession.currentPath?.toAbsolutePath()?.normalize()
                projectStatusText = "Series manifest not found near this Event File."
                return
            }
            runCatching {
                val session = DesktopEventSeriesSession(DesktopEventSeriesFiles)
                session.open(manifestPath)
                val issues = session.validateLinkedEvents()
                eventSeriesValidationState = EventSeriesValidationUiState(
                    manifestPath = manifestPath,
                    issues = issues
                )
                eventSeriesValidationEventPath = projectSession.currentPath?.toAbsolutePath()?.normalize()
                projectStatusText = if (issues.isEmpty()) {
                    "Event Series validation passed."
                } else {
                    "Event Series validation found ${issues.size} issue${if (issues.size == 1) "" else "s"}: ${issues.first().message}"
                }
            }.onFailure { error ->
                val message = error.message ?: error::class.simpleName ?: "Unknown error"
                eventSeriesValidationState = EventSeriesValidationUiState(
                    manifestPath = manifestPath,
                    issues = emptyList(),
                    errorMessage = message
                )
                eventSeriesValidationEventPath = projectSession.currentPath?.toAbsolutePath()?.normalize()
                projectStatusText = "Event Series validation failed: $message"
            }
        }

        fun addEventToCurrentSeries() {
            val currentProject = projectSession.currentProject ?: run {
                projectStatusText = "Open or create an Event File before adding another event to a series."
                return
            }
            if (currentProject.seriesLink == null) {
                projectStatusText = "Link this Event File to an Event Series before adding another series event."
                return
            }
            val manifestPath = currentSeriesManifestPath() ?: run {
                projectStatusText = "Series manifest not found near this Event File."
                return
            }
            val eventPath = DesktopFileDialogs.chooseEventSeriesMemberEventFile() ?: return
            runCatching {
                val seriesFile = DesktopEventSeriesFiles.read(manifestPath)
                val seriesFolder = requireNotNull(manifestPath.parent) {
                    "Event Series manifest has no parent folder."
                }
                val eventProjectFile = DesktopEventSeriesFiles.readEvent(eventPath)
                val result = DesktopEventSeriesActions.addEventToSeries(
                    seriesFile = seriesFile,
                    eventPath = eventPath,
                    seriesFolder = seriesFolder,
                    eventProjectFile = eventProjectFile
                )
                val addedLink = requireNotNull(result.eventProjectFile.seriesLink) {
                    "Added Event File did not receive an Event Series backlink."
                }
                // The added Event File is not the currently open document, so write it through the series store.
                DesktopEventSeriesFiles.write(manifestPath, result.seriesFile)
                DesktopEventSeriesFiles.writeEvent(eventPath, result.eventProjectFile)
                val verifiedSeriesFile = DesktopEventSeriesFiles.read(manifestPath)
                val verifiedEvent = requireNotNull(
                    verifiedSeriesFile.events.firstOrNull { it.seriesEventId == addedLink.seriesEventId }
                ) {
                    "Event Series manifest write did not include ${eventPath.fileName}."
                }
                refreshSeriesEventSummaries()
                DesktopDebugLog.info(
                    "EventSeries",
                    "Added event manifest=$manifestPath eventFile=$eventPath beforeCount=${seriesFile.events.size} " +
                        "afterCount=${verifiedSeriesFile.events.size} seriesEventId=${addedLink.seriesEventId} " +
                        "eventFilePath=${verifiedEvent.eventFilePath}"
                )
                val actionVerb = if (verifiedSeriesFile.events.size > seriesFile.events.size) "Added" else "Updated"
                projectStatusText = "$actionVerb ${eventPath.fileName} in ${manifestPath.fileName}; series now has ${verifiedSeriesFile.events.size} events."
                recordActivity(projectStatusText)
            }.onFailure { error ->
                projectStatusText = "Add Event to Series failed: ${error.message ?: error::class.simpleName}"
                DesktopDebugLog.error("EventSeries", projectStatusText)
            }
        }

        fun exportCurrentEventSeries() {
            val manifestPath = currentSeriesManifestPath() ?: run {
                projectStatusText = "Series manifest not found near this Event File."
                return
            }
            val targetFolder = DesktopFileDialogs.chooseExportEventSeriesDirectory() ?: return
            runCatching {
                val result = DesktopEventSeriesActions.exportSeries(DesktopEventSeriesFiles, manifestPath, targetFolder)
                projectStatusText = "Exported Event Series to ${result.manifestPath.parent} with ${result.eventFilePaths.size} Event File${if (result.eventFilePaths.size == 1) "" else "s"}."
                recordActivity(projectStatusText)
            }.onFailure { error ->
                projectStatusText = "Export Event Series failed: ${error.message ?: error::class.simpleName}"
            }
        }

        fun updateCurrentEventSeriesName(name: String): Boolean {
            val trimmedName = name.trim()
            if (trimmedName.isBlank()) {
                projectStatusText = "Series name was not changed because it cannot be blank."
                return false
            }
            val manifestPath = currentSeriesManifestPath() ?: run {
                projectStatusText = "Series manifest not found near this Event File."
                return false
            }
            return runCatching {
                val seriesFile = DesktopEventSeriesFiles.read(manifestPath)
                val updatedSeriesFile = DesktopEventSeriesActions.renameSeries(seriesFile, trimmedName)
                if (updatedSeriesFile != seriesFile) {
                    DesktopEventSeriesFiles.write(manifestPath, updatedSeriesFile)
                }
                refreshSeriesEventSummaries()
                projectStatusText = "Renamed Event Series to $trimmedName."
                recordActivity(projectStatusText)
                true
            }.getOrElse { error ->
                projectStatusText = "Rename Event Series failed: ${error.message ?: error::class.simpleName}"
                false
            }
        }

        fun updateCurrentEventSeriesFileName(fileNameStem: String): Boolean {
            val trimmedFileNameStem = fileNameStem.trim()
            if (trimmedFileNameStem.isBlank()) {
                projectStatusText = "Series file name was not changed because it cannot be blank."
                return false
            }
            val manifestPath = currentSeriesManifestPath() ?: run {
                projectStatusText = "Series manifest not found near this Event File."
                return false
            }
            return runCatching {
                val seriesFile = DesktopEventSeriesFiles.read(manifestPath)
                val updatedManifestPath = DesktopEventSeriesActions.renameSeriesManifestFile(
                    store = DesktopEventSeriesFiles,
                    manifestPath = manifestPath,
                    seriesFile = seriesFile,
                    fileNameStem = trimmedFileNameStem
                )
                refreshSeriesEventSummaries()
                projectStatusText = "Renamed Event Series file to ${updatedManifestPath.fileName}."
                recordActivity(projectStatusText)
                true
            }.getOrElse { error ->
                projectStatusText = "Rename Event Series file failed: ${error.message ?: error::class.simpleName}"
                false
            }
        }

        fun balanceStartListFromEventSeries() {
            val currentProject = projectSession.currentProject ?: run {
                projectStatusText = "Open or create an Event File before balancing the open event for its series."
                return
            }
            val link = currentProject.seriesLink ?: run {
                projectStatusText = "Link this Event File to an Event Series before balancing the open event for its series."
                return
            }
            val manifestPath = currentSeriesManifestPath() ?: run {
                projectStatusText = "Series manifest not found near this Event File."
                return
            }
            runCatching {
                val session = DesktopEventSeriesSession(DesktopEventSeriesFiles)
                val seriesFile = session.open(manifestPath)
                val linkedEvents = session.loadLinkedEvents()
                val validationIssues = session.validateLinkedEvents()
                require(validationIssues.none { it.severity == EventSeriesIssueSeverity.ERROR }) {
                    validationIssues.first().message
                }
                val settings = currentProject.raceData.effectiveStartDrawSettings()
                DesktopDebugLog.info("StartList", "Balance Open Event for Series using ${currentProject.startDrawSettingsLogText()}")
                val balanced = EventSeriesSupport.drawStartListWithSeriesBalancedStartGroups(
                    seriesFile = seriesFile,
                    linkedEvents = linkedEvents,
                    currentSeriesEventId = link.seriesEventId,
                    currentProjectFile = currentProject,
                    intervalText = settings.intervalText,
                    options = settings.options
                )
                projectFile = projectSession.updateCurrentProject { balanced }
                seriesStartFairnessOptimizationResult = null
                clearEventStartListDrawHistory()
                syncProjectState()
                projectStatusText = "Balanced the open Event File for its series. Save the Event File to keep the draw."
                recordActivity(projectStatusText)
                DesktopDebugLog.info("StartList", "Balanced open event for series ${balanced.startDrawSettingsLogText()}")
            }.onFailure { error ->
                projectStatusText = "Balance Open Event for Series failed: ${error.message ?: error::class.simpleName}"
                DesktopDebugLog.error("StartList", projectStatusText)
            }
        }

        fun optimizeSeriesStartFairness() {
            val currentPath = projectSession.currentPath ?: run {
                projectStatusText = "Open and save an Event File in this Event Series before optimizing series starts."
                return
            }
            if (projectSession.hasUnsavedChanges) {
                projectStatusText = "Save the current Event File before optimizing series starts."
                return
            }
            val manifestPath = currentSeriesManifestPath() ?: run {
                projectStatusText = "Series manifest not found near this Event File."
                return
            }
            runCatching {
                val result = DesktopEventSeriesActions.optimizeStartFairness(
                    store = DesktopEventSeriesFiles,
                    manifestPath = manifestPath,
                    currentEventPath = currentPath,
                    seedSalt = UUID.randomUUID().toString()
                )
                val normalizedCurrentPath = currentPath.toAbsolutePath().normalize()
                val currentEventUpdate = result.updatedEventFiles.firstOrNull { updated ->
                    updated.path.toAbsolutePath().normalize() == normalizedCurrentPath
                }

                // The optimizer evaluates the series as one problem, but each Event File remains
                // independently stored. Write non-open files directly and route the open file
                // through the project session so dirty-state and in-memory UI state stay coherent.
                result.updatedEventFiles
                    .filterNot { updated -> updated.path.toAbsolutePath().normalize() == normalizedCurrentPath }
                    .forEach { updated ->
                        DesktopEventSeriesFiles.writeEvent(updated.path, updated.projectFile)
                    }
                currentEventUpdate?.let { updated ->
                    projectFile = projectSession.updateCurrentProject { updated.projectFile }
                    projectSession.save()
                }

                val solutionNumbering = DesktopEventSeriesStartFairnessSolutionNumbers.assign(
                    existingNumbers = seriesStartFairnessSolutionNumbers,
                    manifestPath = manifestPath,
                    solutionSignature = result.solutionSignature
                )
                seriesStartFairnessSolutionNumbers = solutionNumbering.solutionNumbers
                val numberedResult = result.copy(
                    solutionNumber = solutionNumbering.solutionNumber,
                    repeatedSolution = solutionNumbering.repeatedSolution
                )

                seriesStartFairnessOptimizationResult = numberedResult
                syncProjectState()
                projectStatusText = when {
                    numberedResult.improved ->
                        "Optimized Series Start Fairness: ${numberedResult.initialUnevenHistoryCount} -> " +
                            "${numberedResult.finalUnevenHistoryCount} uneven histories across ${numberedResult.optimizedEventCount} updated events. " +
                            "${numberedResult.solutionLabel()}."
                    numberedResult.alternateSolution ->
                        "Found an alternate Series Start Fairness solution with the same fairness score across ${numberedResult.optimizedEventCount} updated events. " +
                            "${numberedResult.solutionLabel()}."
                    else ->
                        "Series Start Fairness optimizer found no alternate or improved start lists after ${numberedResult.attemptedCandidateCount} candidates. " +
                            "${numberedResult.solutionLabel()}."
                }
                recordActivity(projectStatusText)
            }.onFailure { error ->
                projectStatusText = "Series Start Fairness optimizer failed: ${error.message ?: error::class.simpleName}"
            }
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

        fun deleteAllControls(password: String): Boolean {
            val currentProject = projectSession.currentProject ?: return false
            val affectedCategoryCount = currentProject.raceData.categories.count { categoryData ->
                categoryData.controlPoints.isNotEmpty() ||
                    categoryData.publicControlIds.isNotEmpty() ||
                    categoryData.category.controlPointsString.isNotBlank() ||
                    categoryData.category.lengthMeters != 0 ||
                    categoryData.category.climbMeters != 0 ||
                    categoryData.category.encryptedIdealOrder?.isNotBlank() == true ||
                    categoryData.category.encryptedCourseInfo?.isNotBlank() == true
            }
            if (currentProject.raceData.controls.isEmpty() && affectedCategoryCount == 0) {
                projectStatusText = "No controls to delete."
                return false
            }
            if (currentProject.hasProtectedCategoryData()) {
                if (!unlockProtectedCourseOrder(password)) {
                    return false
                }
            }
            return runCatching {
                val controlCount = currentProject.raceData.controls.size
                projectFile = projectSession.updateCurrentProject { project ->
                    EventProjectEditor.removeAllControls(project)
                }
                protectedIdealOrderByCategoryId = emptyMap()
                protectedCourseInfoByCategoryId = emptyMap()
                hasUnsavedChanges = projectSession.hasUnsavedChanges
                recordActivity("Deleted all controls.")
                projectStatusText = buildString {
                    append("Deleted $controlCount controls.")
                    if (affectedCategoryCount > 0) {
                        append(" Cleared course assignments and length/climb data from $affectedCategoryCount categor")
                        append(if (affectedCategoryCount == 1) "y." else "ies.")
                    }
                    append(" Unsaved changes.")
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

        fun updateCourseAnalyzerSpeedFactor(factor: Double): String =
            runCatching {
                projectFile ?: throw IllegalStateException("Load an Event File before updating Course Analyzer speed.")
                projectFile = projectSession.updateCurrentProject { project ->
                    EventProjectEditor.updateCourseAnalyzerSpeedCompensationFactor(project, factor)
                }
                hasUnsavedChanges = projectSession.hasUnsavedChanges
                projectStatusText = "Course Analyzer speed factor updated. Unsaved changes."
                projectStatusText
            }.getOrElse { error ->
                projectStatusText = "Speed factor update failed: ${error.message ?: error::class.simpleName}"
                projectStatusText
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

        suspend fun fetchProtectedCourseElevationsForAnalysis(
            sourceName: String,
            categoryIds: List<String>,
            password: String,
            allowInternetDownload: Boolean
        ): Pair<CourseAnalysisElevationPreparationResult, DesktopRouteElevationResult> {
            val currentProject = projectSession.currentProject ?: error("No Event File open.")
            val currentJob = coroutineContext[Job]
            if (courseKmlKmzElevationJob?.isActive == true && courseKmlKmzElevationJob != currentJob) {
                error("Course elevation retrieval is already running.")
            }
            courseKmlKmzElevationJob = currentJob
            courseKmlKmzElevationProgress = CourseKmlKmzElevationProgressUiState(
                sourceName = sourceName,
                categoryName = "",
                completedPointCount = 0,
                totalPointCount = 1
            )
            return try {
                val fetchResult = if (allowInternetDownload) {
                    DesktopCourseKmlImporter.fetchProtectedCourseElevations(
                        projectFile = currentProject,
                        categoryIds = categoryIds,
                        password = password,
                        onProgress = { progress ->
                            courseKmlKmzElevationProgress = CourseKmlKmzElevationProgressUiState(
                                sourceName = sourceName,
                                categoryName = progress.categoryName,
                                completedPointCount = progress.completedPointCount,
                                totalPointCount = progress.totalPointCount,
                                downloadedPointCount = progress.downloadedPointCount,
                                cachedPointCount = progress.cachedPointCount
                            )
                        }
                    )
                } else {
                    DesktopCourseKmlImporter.fetchProtectedCourseElevations(
                        projectFile = currentProject,
                        categoryIds = categoryIds,
                        password = password,
                        elevationProvider = { null },
                        onProgress = { progress ->
                            courseKmlKmzElevationProgress = CourseKmlKmzElevationProgressUiState(
                                sourceName = sourceName,
                                categoryName = progress.categoryName,
                                completedPointCount = progress.completedPointCount,
                                totalPointCount = progress.totalPointCount,
                                downloadedPointCount = progress.downloadedPointCount,
                                cachedPointCount = progress.cachedPointCount
                            )
                        }
                    )
                }
                val (updatedProject, elevationResult) = fetchResult
                val shouldPersistResult = allowInternetDownload || elevationResult.resolvedPointCount > 0
                val resultProject = if (shouldPersistResult) {
                    projectFile = projectSession.updateCurrentProject { updatedProject }
                    syncProtectedCourseState(updatedProject, password)
                    updatedProject
                } else {
                    currentProject
                }
                val statusText = when {
                    elevationResult.resolvedPointCount > 0 ->
                        "Resolved ${elevationResult.resolvedPointCount} course elevations for ${elevationResult.categoryCount} categories (${elevationResult.cachedPointCount} cached, ${elevationResult.elevatedPointCount} downloaded). Unsaved changes."
                    elevationResult.sampledPointCount == 0 ->
                        "No missing course elevations found for ${elevationResult.categoryCount} categories."
                    allowInternetDownload ->
                        "Course elevation retrieval completed, but no elevation values were returned for ${elevationResult.sampledPointCount} requested points."
                    else ->
                        "No missing course elevations were available in the local elevation cache."
                }
                projectStatusText = statusText
                CourseAnalysisElevationPreparationResult(
                    projectFile = resultProject,
                    protectedCourseInfoByCategoryId = protectedCourseInfoByCategoryId,
                    statusText = statusText
                ) to elevationResult
            } finally {
                courseKmlKmzElevationProgress = null
                if (courseKmlKmzElevationJob == currentJob) {
                    courseKmlKmzElevationJob = null
                }
            }
        }

        suspend fun downloadCalculatedRouteElevationCacheForAnalysis(
            summary: DesktopCourseAnalysisSummary
        ): String {
            val boundingBox = summary.calculatedRouteElevationBoundingBox
                ?: error("Calculated route elevation download failed: route bounds are unavailable.")
            val currentJob = coroutineContext[Job]
            if (venueElevationCacheJob?.isActive == true && venueElevationCacheJob != currentJob) {
                error("Elevation cache download is already running.")
            }
            val venueName = "${projectSession.currentProject?.raceData?.race?.name ?: "Course Analysis"} ${summary.categoryName} calculated route"
            val estimatedRawBytes = DesktopVenueElevationCache.estimate(
                boundingBox,
                CourseAnalysisCalculatedRouteElevationResolutionMeters,
                CourseAnalysisCalculatedRouteElevationBufferMeters
            ).rawBytes
            venueElevationCacheJob = currentJob
            venueElevationCacheProgress = VenueElevationCacheProgressUiState(
                venueName = venueName,
                completedPointCount = 0,
                totalPointCount = 1,
                estimatedRawBytes = estimatedRawBytes
            )
            return try {
                val cacheSummary = DesktopVenueElevationCache.download(
                    venueName = venueName,
                    boundingBox = boundingBox,
                    resolutionMeters = CourseAnalysisCalculatedRouteElevationResolutionMeters,
                    bufferMeters = CourseAnalysisCalculatedRouteElevationBufferMeters,
                    source = DesktopVenueElevationCacheSource.Usgs3Dep,
                    sourceUrl = "",
                    onProgress = { progress ->
                        venueElevationCacheProgress = VenueElevationCacheProgressUiState(
                            venueName = progress.venueName,
                            completedPointCount = progress.completedPointCount,
                            totalPointCount = progress.totalPointCount,
                            estimatedRawBytes = progress.estimatedRawBytes ?: estimatedRawBytes,
                            cancelRequested = venueElevationCacheProgress?.cancelRequested == true
                        )
                    }
                )
                venueElevationCacheRefreshToken++
                "Downloaded ${cacheSummary.sourceName} elevation cache for ${cacheSummary.venueName}: ${cacheSummary.resolvedPointCount}/${cacheSummary.pointCount} points at ${cacheSummary.resolutionMeters.roundToInt()} m."
            } finally {
                venueElevationCacheProgress = null
                if (venueElevationCacheJob == currentJob) {
                    venueElevationCacheJob = null
                }
            }
        }

        suspend fun resolveCachedCourseAnalysisElevations(categoryId: String): CourseAnalysisElevationPreparationResult? {
            val password = protectedCoursePassword ?: return null
            val categoryName = projectSession.currentProject
                ?.raceData
                ?.categories
                ?.firstOrNull { it.category.id == categoryId }
                ?.category
                ?.name
                ?: "Course Analysis"
            val (preparation, result) = fetchProtectedCourseElevationsForAnalysis(
                sourceName = "Course Analysis local cache",
                categoryIds = listOf(categoryId),
                password = password,
                allowInternetDownload = false
            )
            return preparation.takeIf { result.resolvedPointCount > 0 }?.copy(
                statusText = "Resolved ${result.resolvedPointCount} stored course elevations for $categoryName from the local elevation cache. Unsaved changes."
            )
        }

        suspend fun downloadMissingCourseAnalysisElevations(
            categoryId: String,
            summary: DesktopCourseAnalysisSummary
        ): CourseAnalysisElevationPreparationResult {
            val password = protectedCoursePassword
            var latestProject = projectSession.currentProject ?: error("No Event File open.")
            var latestCourseInfo = protectedCourseInfoByCategoryId
            val statusParts = mutableListOf<String>()
            if (summary.hasMissingElevationData) {
                if (password == null) {
                    error("Unlock course order before downloading stored route elevations.")
                }
                val (preparation, result) = fetchProtectedCourseElevationsForAnalysis(
                    sourceName = "Course Analysis internet download",
                    categoryIds = listOf(categoryId),
                    password = password,
                    allowInternetDownload = true
                )
                latestProject = preparation.projectFile
                latestCourseInfo = preparation.protectedCourseInfoByCategoryId
                statusParts += when {
                    result.resolvedPointCount > 0 ->
                        "resolved ${result.resolvedPointCount} stored course elevations (${result.cachedPointCount} cached, ${result.elevatedPointCount} downloaded)"
                    result.sampledPointCount == 0 ->
                        "stored course elevations were already complete"
                    else ->
                        "stored course elevation download returned no values for ${result.sampledPointCount} requested points"
                }
            }
            if (summary.hasMissingCalculatedRouteElevationData) {
                statusParts += downloadCalculatedRouteElevationCacheForAnalysis(summary)
            }
            val statusText = if (statusParts.isEmpty()) {
                "No missing elevation downloads were needed."
            } else {
                statusParts.joinToString(separator = "; ").replaceFirstChar { it.uppercase() }
            }
            projectStatusText = statusText
            return CourseAnalysisElevationPreparationResult(
                projectFile = latestProject,
                protectedCourseInfoByCategoryId = latestCourseInfo,
                statusText = statusText
            )
        }

        fun startProtectedCourseElevationFetch(sourceName: String, categoryIds: List<String>, password: String) {
            if (courseKmlKmzElevationJob?.isActive == true) {
                return
            }
            projectStatusText = "Retrieving course elevations..."
            courseKmlKmzElevationJob = appCoroutineScope.launch {
                val result = runCatching {
                    fetchProtectedCourseElevationsForAnalysis(
                        sourceName = sourceName,
                        categoryIds = categoryIds,
                        password = password,
                        allowInternetDownload = true
                    )
                }
                result.onSuccess {
                }.onFailure { error ->
                    if (error !is CancellationException) {
                        DesktopDebugLog.error(
                            "CourseElevation",
                            "Course elevation retrieval failed for $sourceName categories=${categoryIds.joinToString()}: ${error::class.simpleName}: ${error.message}"
                        )
                    }
                    projectStatusText = if (error is CancellationException) {
                        if (suppressNextCourseElevationCancelStatus) {
                            suppressNextCourseElevationCancelStatus = false
                            projectStatusText
                        } else {
                            "Course elevation retrieval canceled. Imported route data kept without fetched elevations."
                        }
                    } else {
                        "Course elevation retrieval failed: ${error.message ?: error::class.simpleName}"
                    }
                }
            }
        }

        fun startCourseKmlKmzElevationFetch(review: PendingCourseKmlKmzImportReview) {
            startProtectedCourseElevationFetch(
                sourceName = review.sourceName,
                categoryIds = review.summary.matchedCategoryIds,
                password = review.password
            )
        }

        fun startVenueElevationCacheDownload(
            venueName: String,
            boundingBox: DesktopVenueElevationBoundingBox?,
            resolutionMeters: Double,
            bufferMeters: Double,
            source: DesktopVenueElevationCacheSource,
            sourceUrl: String = ""
        ) {
            if (source == DesktopVenueElevationCacheSource.LocalLidarRaster && sourceUrl.isBlank()) {
                projectStatusText = "Local LiDAR import requires a source file."
                return
            }
            if (venueElevationCacheJob?.isActive == true) {
                return
            }
            val cleanVenueName = venueName.trim().ifBlank { "Venue" }
            val isLocalFileImport = source == DesktopVenueElevationCacheSource.LocalLidarRaster
            val localSourceTypes = if (isLocalFileImport) {
                DesktopVenueElevationCache.desktopLocalElevationSourceTypes(sourceUrl)
            } else {
                emptyList()
            }
            val localSourceIsPointCloud = localSourceTypes.isNotEmpty() &&
                localSourceTypes.all { it == LocalElevationSourceType.LasPointCloud }
            val cacheActionText = if (isLocalFileImport) "Importing" else "Downloading"
            val operationText = if (isLocalFileImport) "file import" else "download"
            val estimatedRawBytes = if (localSourceIsPointCloud) {
                null
            } else {
                boundingBox?.takeIf { resolutionMeters > 0.0 }?.let { bounds ->
                    runCatching {
                        DesktopVenueElevationCache.estimate(bounds, resolutionMeters, bufferMeters).rawBytes
                    }.getOrNull()
                }
            }
            projectStatusText = if (isLocalFileImport) {
                "Importing local elevation file data for $cleanVenueName..."
            } else {
                "Downloading ${source.label} elevation cache for $cleanVenueName..."
            }
            DesktopDebugLog.info(
                "ElevationCache",
                "$cacheActionText started venue=$cleanVenueName source=${source.label} resolution=${resolutionMeters}m buffer=${bufferMeters}m " +
                    "bounds=${boundingBox?.let { "${it.minLatitude},${it.minLongitude}..${it.maxLatitude},${it.maxLongitude}" } ?: "full-source"}"
            )
            venueElevationCacheProgress = VenueElevationCacheProgressUiState(
                venueName = cleanVenueName,
                completedPointCount = 0,
                totalPointCount = 1,
                isLocalFileImport = isLocalFileImport,
                estimatedRawBytes = estimatedRawBytes
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
                            totalPointCount = progress.totalPointCount,
                            isLocalFileImport = isLocalFileImport,
                            estimatedRawBytes = progress.estimatedRawBytes ?: estimatedRawBytes,
                            cancelRequested = venueElevationCacheProgress?.cancelRequested == true
                        )
                    }
                )
                }
                result.onSuccess { summary ->
                    venueElevationCacheRefreshToken++
                    projectStatusText = if (isLocalFileImport) {
                        "Imported ${summary.sourceName} elevation cache for ${summary.venueName}: ${summary.resolvedPointCount}/${summary.pointCount} points at ${summary.resolutionMeters.roundToInt()} m."
                    } else {
                        "Downloaded ${summary.sourceName} elevation cache for ${summary.venueName}: ${summary.resolvedPointCount}/${summary.pointCount} points at ${summary.resolutionMeters.roundToInt()} m."
                    }
                }.onFailure { error ->
                    projectStatusText = if (error is CancellationException) {
                        DesktopDebugLog.info(
                            "ElevationCache",
                            "$cacheActionText canceled venue=$cleanVenueName source=${source.label}"
                        )
                        "Elevation cache $operationText canceled."
                    } else {
                        DesktopDebugLog.error(
                            "ElevationCache",
                            "$cacheActionText failed venue=$cleanVenueName source=${source.label}: ${error.message ?: error::class.simpleName}"
                        )
                        "Elevation cache $operationText failed: ${error.message ?: error::class.simpleName}"
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

        fun currentEventFileWorkingFolder(): Path =
            projectSession.currentPath?.parent ?: DesktopEventFileLocations.preferredEventFileDirectory()

        fun openEventFileWorkingFolder() {
            val directory = currentEventFileWorkingFolder()
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
                projectStatusText = "Opened Event File folder: $directory"
            }.onFailure { error ->
                projectStatusText = "Could not open Event File folder: ${error.message ?: error::class.simpleName}"
            }
        }

        fun browseExternalUrl(url: String): Result<Unit> =
            runCatching {
                if (!Desktop.isDesktopSupported()) {
                    error("Opening links is not supported on this system.")
                }
                val desktop = Desktop.getDesktop()
                if (!desktop.isSupported(Desktop.Action.BROWSE)) {
                    error("Opening links is not supported on this system.")
                }
                desktop.browse(URI(url))
            }

        fun openExternalUrl(url: String) {
            browseExternalUrl(url).onSuccess {
                projectStatusText = "Opened link: $url"
            }.onFailure { error ->
                projectStatusText = "Could not open link: ${error.message ?: error::class.simpleName}"
            }
        }

        fun checkForUpdates() {
            if (!isUpdateCheckingEnabled) {
                updateCheckStatus = null
                projectStatusText = "Radio-Oracle update checks are disabled in App Settings."
                return
            }
            val status = DesktopAppUpdateSupport.status(currentVersion = DesktopBuildInfo.baseVersion)
            updateCheckStatus = status
            projectStatusText = when (status.jdeployUpdatesAvailable) {
                true -> "jDeploy reported a Radio-Oracle update is available."
                false -> "jDeploy did not report a Radio-Oracle update at launch."
                null -> if (status.launchedByJdeploy) {
                    "Radio-Oracle was launched by jDeploy, but update status was not reported."
                } else {
                    "Radio-Oracle was not launched by jDeploy, so update status is unavailable in this run."
                }
            }
            DesktopDebugLog.info("Update", projectStatusText)
        }

        fun setUpdateCheckingEnabled(enabled: Boolean) {
            isUpdateCheckingEnabled = enabled
            DesktopAppSettingsPreferences.setUpdateCheckingEnabled(enabled)
            if (!enabled) {
                updateCheckStatus = null
                appUpdateDialogStatus = null
            }
            projectStatusText = if (enabled) {
                "Radio-Oracle update checks enabled."
            } else {
                "Radio-Oracle update checks disabled."
            }
        }

        fun setCloudflarePagesPublishSettings(settings: DesktopCloudflarePagesPublishSettings): Boolean {
            val normalized = settings.normalized()
            cloudflarePagesPublishSettings = normalized
            DesktopAppSettingsPreferences.setCloudflarePagesPublishSettings(normalized)
            projectStatusText = "Cloudflare Pages publishing settings saved."
            return true
        }

        fun importReviewedDemFiles(review: DesktopVenueElevationDemImportReview): Boolean =
            runCatching {
                val summary = DesktopVenueElevationCache.importReviewedDemFiles(review)
                venueElevationCacheRefreshToken++
                projectStatusText = buildString {
                    append("Imported ${summary.importedCount} DEM cache file")
                    append(if (summary.importedCount == 1) "" else "s")
                    append(" to ${summary.targetDirectory}.")
                    if (summary.overwrittenCount > 0) {
                        append(" Overwrote ${summary.overwrittenCount} existing cached venue")
                        append(if (summary.overwrittenCount == 1) "." else "s.")
                    }
                    if (review.issues.isNotEmpty()) {
                        append(" Skipped ${review.issues.size} invalid file")
                        append(if (review.issues.size == 1) "." else "s.")
                    }
                }
                true
            }.getOrElse { error ->
                projectStatusText = "DEM import failed: ${error.message ?: error::class.simpleName}"
                false
            }

        fun chooseImportDemFiles() {
            val paths = DesktopFileDialogs.chooseImportDemFiles()
            if (paths.isEmpty()) {
                return
            }
            val review = DesktopVenueElevationCache.reviewDemFileImport(paths)
            when {
                review.importableCount == 0 -> {
                    pendingDemFileImportReview = review
                    projectStatusText = "No valid DEM cache files were selected."
                }
                review.overwriteCount > 0 || review.issues.isNotEmpty() ->
                    pendingDemFileImportReview = review
                else ->
                    importReviewedDemFiles(review)
            }
        }

        fun applyCourseKmlKmzImport(
            review: PendingCourseKmlKmzImportReview,
            fetchElevations: Boolean,
            applyCategoryAssignments: Boolean,
            createMissingCategories: Boolean
        ) {
            val formatLabel = controlsRouteImportFormatLabel(review.sourceName)
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
            val isDuplicateElevationRetry = fetchElevations &&
                selectedSummary.importedCategoryCount == 0 &&
                selectedSummary.duplicateCategoryCount > 0 &&
                selectedSummary.hasDuplicateMissingElevations
            val projectToApply = if (isDuplicateElevationRetry) {
                projectSession.currentProject ?: selectedProject
            } else {
                selectedProject
            }
            val updatedProject = if (applyCategoryAssignments) {
                DesktopCourseKmlImporter.applyCategoryAssignmentUpdates(
                    projectFile = projectToApply,
                    updates = selectedSummary.categoryAssignmentUpdates
                )
            } else {
                projectToApply
            }
            checkpointBeforeImport("controls/route $formatLabel import ${review.sourceName}")
            projectFile = projectSession.updateCurrentProject { updatedProject }
            syncProtectedCourseState(updatedProject, review.password)
            pendingCourseKmlKmzImportReview = null
            recordActivity("Applied controls/route $formatLabel import ${review.sourceName}.")
            recentImportReport = DesktopImportReport(
                title = "Controls/route $formatLabel: ${review.sourceName}",
                lines = withRollbackBackupLine(listOf(
                    "${selectedSummary.importedCategoryCount} categories received stored route data.",
                    "${selectedSummary.duplicateCategoryCount} duplicate categories skipped.",
                    "${selectedSummary.controlIdentityUpdateCount} control identities updated.",
                    "${selectedSummary.changedControlLocationCount} control locations updated.",
                    "${selectedSummary.categoryAssignmentUpdates.size.takeIf { applyCategoryAssignments } ?: 0} assigned-control lists replaced.",
                    "${selectedSummary.createdCategoryNames.size} missing categories created.",
                    "${selectedSummary.createdControlNames.size} missing controls created.",
                    "${selectedSummary.missingCategoryNames.size} category names were missing before review."
                ) + listOf(updatedProject.resultImpactWarning("Course data changed").trim()).filter { it.isNotBlank() } +
                    selectedSummary.categoryAssumptions.map { assumption ->
                        "No category indication was found for route ${assumption.routeName}; assumed ${assumption.categoryName}."
                    } +
                    selectedSummary.eventTypeWarnings)
            )
            if (fetchElevations) {
                startCourseKmlKmzElevationFetch(review.copy(summary = selectedSummary))
            } else {
                projectStatusText = if (selectedSummary.isDuplicateOnly) {
                    "Duplicate controls/route $formatLabel request: identical file already imported. No route data reloaded."
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
                    val createdControlsText = selectedSummary.createdControlNames
                        .takeIf { it.isNotEmpty() }
                        ?.let { " Created ${it.size} controls." }
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
                        "Updated ${selectedSummary.changedControlLocationCount} control locations.$assignedText$duplicateText$createdText$createdControlsText Unsaved changes."
                    } else if (
                        selectedSummary.importedCategoryCount == 0 &&
                        selectedSummary.assignedCategoryControlCount > 0 &&
                        applyCategoryAssignments
                    ) {
                        "Updated assigned controls for ${selectedSummary.categoryAssignmentUpdates.size} categories.$duplicateText$createdText$createdControlsText Unsaved changes."
                    } else {
                        "Imported controls/route data for ${selectedSummary.importedCategoryCount} categories.$locationText$assignedText$duplicateText$createdText$createdControlsText Unsaved changes."
                    }
                }
            }
        }

        fun startCourseKmlKmzImport(
            path: Path,
            password: String,
            categoryOverrideId: String? = null,
            requireRoutes: Boolean = true
        ) {
            val currentProject = projectSession.currentProject ?: return
            val formatLabel = controlsRouteImportFormatLabel(path.fileName.toString())
            if (isImportingCourseKmlKmz) {
                return
            }
            if (courseKmlKmzElevationJob?.isActive == true) {
                suppressNextCourseElevationCancelStatus = true
                courseKmlKmzElevationProgress = null
                courseKmlKmzElevationJob?.cancel()
                DesktopDebugLog.info(
                    "CourseElevation",
                    "Canceled active course elevation retrieval before controls/route $formatLabel import retry."
                )
            }
            suspend fun buildPreview(baseProject: EventProjectFile): CourseKmlKmzImportPreview =
                withContext(Dispatchers.IO) {
                    val preview = DesktopCourseKmlImporter.importProtectedCourseInfo(
                        path = path,
                        projectFile = baseProject,
                        password = password,
                        categoryOverrideId = categoryOverrideId,
                        requireRoutes = requireRoutes
                    )
                    val createdPreview = preview.second
                        .takeIf { it.missingCategoryNames.isNotEmpty() || it.missingControlNames.isNotEmpty() }
                        ?.let {
                            DesktopCourseKmlImporter.importProtectedCourseInfo(
                                path = path,
                                projectFile = baseProject,
                                password = password,
                                categoryOverrideId = categoryOverrideId,
                                createMissingCategories = true,
                                createMissingControls = true,
                                requireRoutes = requireRoutes
                            )
                        }
                    CourseKmlKmzImportPreview(
                        updatedProject = preview.first,
                        summary = preview.second,
                        createdMissingCategoryProject = createdPreview?.first,
                        createdMissingCategorySummary = createdPreview?.second
                    )
                }

            suspend fun resolveDuplicateElevationsFromLocalCache(summary: DesktopCourseKmlImportSummary): EventProjectFile? {
                if (!summary.hasDuplicateMissingElevations) {
                    return null
                }
                val projectBeforeRepair = projectSession.currentProject ?: return null
                val (updatedProject, elevationResult) = DesktopCourseKmlImporter.fetchProtectedCourseElevations(
                    projectFile = projectBeforeRepair,
                    categoryIds = summary.matchedCategoryIds,
                    password = password,
                    elevationProvider = { null }
                )
                if (elevationResult.resolvedPointCount == 0) {
                    return null
                }
                projectFile = projectSession.updateCurrentProject { updatedProject }
                syncProtectedCourseState(updatedProject, password)
                projectStatusText =
                    "Resolved ${elevationResult.resolvedPointCount} stored course elevations from the local elevation cache. Unsaved changes."
                return updatedProject
            }

            isImportingCourseKmlKmz = true
            projectStatusText = "Importing controls/route $formatLabel..."
            appCoroutineScope.launch {
                val result = runCatching {
                    val initialPreview = buildPreview(currentProject)
                    val repairedProject = resolveDuplicateElevationsFromLocalCache(initialPreview.summary)
                    if (repairedProject == null) {
                        initialPreview
                    } else {
                        buildPreview(repairedProject)
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
                        summary.missingCategoryNames.isEmpty() &&
                        summary.categoryAssumptions.isEmpty() &&
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
                        projectStatusText = "Choose the Event File category for this $formatLabel route."
                    } else if (
                        summary.routeCount > 0 &&
                        summary.matchedCategoryCount == 0 &&
                        categoryOptions.isEmpty() &&
                        summary.missingCategoryNames.isEmpty()
                    ) {
                        pendingCourseKmlKmzImportReview = null
                        pendingCourseKmlKmzCategoryMapping = null
                        projectStatusText =
                            "$formatLabel route data was not applied because the Event File has no categories."
                    } else if (summary.isControlLocationNoOp && !summary.hasLabelConversions) {
                        pendingCourseKmlKmzImportReview = null
                        pendingCourseKmlKmzCategoryMapping = null
                        projectStatusText =
                            "$formatLabel import found ${summary.matchedControlPointCount} matching controls, but no control locations changed."
                    } else if (summary.isDuplicateOnly && !summary.hasDuplicateMissingElevations) {
                        pendingCourseKmlKmzImportReview = null
                        pendingCourseKmlKmzCategoryMapping = null
                        projectStatusText =
                            "Duplicate controls/route $formatLabel request: identical file already imported and all elevations are available."
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
                    val errorMessage = error.message ?: error::class.simpleName.orEmpty()
                    if (error is DesktopCourseKmlMissingRouteException) {
                        pendingCourseKmlKmzImportWarning = PendingCourseKmlKmzImportWarning(
                            title = "Course route required",
                            message = errorMessage
                        )
                    }
                    projectStatusText = "Controls/route $formatLabel import failed: $errorMessage"
                }
                isImportingCourseKmlKmz = false
            }
        }

        fun chooseImportCourseKmlKmzUnlocked(password: String, requireRoutes: Boolean = true) {
            if (isImportingCourseKmlKmz) {
                return
            }
            DesktopFileDialogs.chooseImportKmlKmz()?.let { path ->
                startCourseKmlKmzImport(path, password, requireRoutes = requireRoutes)
            }
        }

        fun chooseImportCourseGpxUnlocked(password: String, requireRoutes: Boolean = true) {
            if (isImportingCourseKmlKmz) {
                return
            }
            DesktopFileDialogs.chooseImportGpx()?.let { path ->
                startCourseKmlKmzImport(path, password, requireRoutes = requireRoutes)
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

        fun chooseImportCourseGpx() {
            val password = protectedCoursePassword
            if (password == null) {
                projectStatusText = "Unlock course order before importing GPX controls/route data."
                pendingCourseKmlKmzUnlockAction = CourseKmlKmzUnlockAction.ImportGpx
                return
            }
            chooseImportCourseGpxUnlocked(password)
        }

        fun chooseImportControlsKmlKmz() {
            val password = protectedCoursePassword
            if (password == null) {
                projectStatusText = "Unlock course order before importing KML/KMZ controls data."
                pendingCourseKmlKmzUnlockAction = CourseKmlKmzUnlockAction.ImportControls
                return
            }
            chooseImportCourseKmlKmzUnlocked(password, requireRoutes = false)
        }

        fun chooseImportControlsGpx() {
            val password = protectedCoursePassword
            if (password == null) {
                projectStatusText = "Unlock course order before importing GPX controls data."
                pendingCourseKmlKmzUnlockAction = CourseKmlKmzUnlockAction.ImportControlsGpx
                return
            }
            chooseImportCourseGpxUnlocked(password, requireRoutes = false)
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

        fun chooseExportCourseGpxUnlocked(password: String) {
            val currentProject = projectSession.currentProject ?: return
            DesktopFileDialogs.chooseExportControlsRouteGpx(currentProject.raceData.race.name)?.let { target ->
                runCatching {
                    val summary = DesktopControlsRouteKmlKmzExporter.exportEncryptedZip(
                        target = target,
                        projectFile = currentProject,
                        password = password
                    )
                    syncProjectState()
                    projectStatusText =
                        "Exported ${target.path.fileName} as an encrypted ZIP containing GPX " +
                            "with ${summary.controlCatalogCount} controls and ${summary.routeCount} routes."
                }.onFailure { error ->
                    projectStatusText = "Controls/route GPX export failed: ${error.message ?: error::class.simpleName}"
                }
            }
        }

        fun chooseExportCourseKmlKmz() {
            projectStatusText = "Enter the Event Password before exporting protected controls/route KML/KMZ data."
            pendingCourseKmlKmzUnlockAction = CourseKmlKmzUnlockAction.Export
        }

        fun chooseExportCourseGpx() {
            projectStatusText = "Enter the Event Password before exporting protected controls/route GPX data."
            pendingCourseKmlKmzUnlockAction = CourseKmlKmzUnlockAction.ExportGpx
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

        fun generatePublicResultsSite() {
            val currentProject = projectSession.currentProject ?: return
            DesktopFileDialogs.chooseExportPublicResultsSiteDirectory()?.let { directory ->
                runCatching {
                    val paths = DesktopProjectFiles.exportPublicResultsSite(
                        directory,
                        currentProject,
                        protectedCourseInfoByCategoryId.takeIf { protectedCoursePassword != null } ?: emptyMap()
                    )
                    publicResultSiteDirectory = paths.directory
                    publicResultSiteEventPath = paths.eventPath
                    publishedPublicResultSiteUrl = null
                    syncProjectState()
                    projectStatusText = "Generated public results site at ${paths.eventDirectory}"
                    DesktopDebugLog.info(
                        "PublicResults",
                        "Generated public results site root=${paths.directory} event=${paths.eventPath} eventDirectory=${paths.eventDirectory}"
                    )
                }.onFailure { error ->
                    projectStatusText = "Public results site generation failed: ${error.message ?: error::class.simpleName}"
                    DesktopDebugLog.error("PublicResults", projectStatusText)
                }
            }
        }

        fun startPublicResultsSitePreview() {
            val directory = publicResultSiteDirectory ?: run {
                projectStatusText = "Generate a public results site before starting preview."
                return
            }
            runCatching {
                publicResultSitePreviewServer?.stop()
                val server = DesktopPublicResultSitePreviewServer(directory)
                val serverUrl = server.start()
                val url = publicResultSiteEventPath
                    ?.takeIf { it.isNotBlank() }
                    ?.let { path -> "$serverUrl${path.trim('/')}/" }
                    ?: serverUrl
                publicResultSitePreviewServer = server
                publicResultSitePreviewUrl = url
                browseExternalUrl(url).onSuccess {
                    projectStatusText = "Public results site preview running at $url"
                    DesktopDebugLog.info("PublicResults", projectStatusText)
                }.onFailure { error ->
                    projectStatusText =
                        "Public results site preview running at $url, but the browser did not open: ${error.message ?: error::class.simpleName}"
                    DesktopDebugLog.warn("PublicResults", projectStatusText)
                }
            }.onFailure { error ->
                publicResultSitePreviewServer = null
                publicResultSitePreviewUrl = null
                projectStatusText = "Public results site preview failed: ${error.message ?: error::class.simpleName}"
                DesktopDebugLog.error("PublicResults", projectStatusText)
            }
        }

        fun stopPublicResultsSitePreview() {
            publicResultSitePreviewServer?.stop()
            publicResultSitePreviewServer = null
            publicResultSitePreviewUrl = null
            projectStatusText = "Public results site preview stopped."
        }

        fun openPublicResultsSitePreview() {
            val runningUrl = publicResultSitePreviewUrl
            if (runningUrl != null) {
                openExternalUrl(runningUrl)
                return
            }
            startPublicResultsSitePreview()
        }

        fun startLocalResultsWebServer(sharedAddress: DesktopEventFileTransferAddress? = null, regenerate: Boolean = false) {
            runCatching {
                if (regenerate || localResultsWebServerDirectory == null || localResultsWebServerEventPath == null) {
                    regenerateLocalResultsWebPage()
                }
                val directory = requireNotNull(localResultsWebServerDirectory)
                localResultsWebServer?.stop()
                val server = DesktopPublicResultSitePreviewServer(
                    siteDirectory = directory,
                    sharedAddress = sharedAddress
                )
                val rootUrl = server.start()
                val pageUrl = localResultsWebPageUrl(rootUrl, localResultsWebServerEventPath)
                localResultsWebServer = server
                localResultsWebServerUrl = pageUrl
                browseExternalUrl(pageUrl).onSuccess {
                    val scope = if (sharedAddress == null) "on this computer" else "on Wi-Fi"
                    projectStatusText = "Local results web page running $scope at $pageUrl"
                    DesktopDebugLog.info("PublicResults", projectStatusText)
                }.onFailure { error ->
                    projectStatusText =
                        "Local results web page running at $pageUrl, but the browser did not open: ${error.message ?: error::class.simpleName}"
                    DesktopDebugLog.warn("PublicResults", projectStatusText)
                }
            }.onFailure { error ->
                localResultsWebServer?.stop()
                localResultsWebServer = null
                localResultsWebServerUrl = null
                projectStatusText = "Local results web server failed: ${error.message ?: error::class.simpleName}"
                DesktopDebugLog.error("PublicResults", projectStatusText)
            }
        }

        fun openLocalResultsWebPage() {
            val runningUrl = localResultsWebServerUrl
            if (runningUrl != null) {
                openExternalUrl(runningUrl)
                return
            }
            startLocalResultsWebServer()
        }

        fun previewLocalResultsWebPage() {
            startLocalResultsWebServer(regenerate = true)
        }

        fun startLocalResultsWebServerOnWifi() {
            val address = discoverDesktopEventFileTransferAddresses().firstOrNull()
            if (address == null) {
                projectStatusText = "No Wi-Fi or LAN address is available for the local results web server."
                DesktopDebugLog.warn("PublicResults", projectStatusText)
                return
            }
            startLocalResultsWebServer(sharedAddress = address, regenerate = true)
        }

        fun stopLocalResultsWebServer() {
            localResultsWebServerRefreshJob?.cancel()
            localResultsWebServerRefreshJob = null
            localResultsWebServer?.stop()
            localResultsWebServer = null
            localResultsWebServerUrl = null
            projectStatusText = "Local results web server stopped."
        }

        fun publishPublicResultsSite() {
            val directory = publicResultSiteDirectory ?: run {
                projectStatusText = "Generate a public results site before publishing."
                return
            }
            if (isPublishingPublicResultSite) {
                projectStatusText = "Public results site publishing is already in progress."
                return
            }
            isPublishingPublicResultSite = true
            projectStatusText = "Publishing public results site to Cloudflare Pages..."
            DesktopDebugLog.info("PublicResults", "$projectStatusText root=$directory")
            appCoroutineScope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        publicResultSitePublisher.publish(
                            cloudflarePagesPublishSettings.request(directory)
                        )
                    }
                }.onSuccess { result ->
                    val publicUrl = DesktopCloudflarePagesPublisher.publicResultsUrl(result.url, publicResultSiteEventPath)
                    publishedPublicResultSiteUrl = publicUrl
                    projectStatusText = "Published public results site to $publicUrl"
                    DesktopDebugLog.info("PublicResults", "$projectStatusText root=$directory project=${result.projectName} branch=${result.branch}")
                }.onFailure { error ->
                    projectStatusText = "Public results site publish failed: ${error.message ?: error::class.simpleName}"
                    DesktopDebugLog.error("PublicResults", projectStatusText)
                }
                isPublishingPublicResultSite = false
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
                exitApplication()
            }
        }

        fun openOrImportSelectedEventFile(path: Path) {
            if (DesktopProjectFilePaths.isEventSeriesManifestName(path.fileName.toString())) {
                runCatching {
                    DesktopEventSeriesActions.eventPathToOpenFromManifest(
                        store = DesktopEventSeriesFiles,
                        manifestPath = path,
                        lastSeriesEventStore = DesktopLastSeriesEventPreferences
                    )
                }.onSuccess { eventPath ->
                    openOrImportSelectedEventFile(eventPath)
                }.onFailure { error ->
                    projectStatusText = "Open Event Series failed: ${error.message ?: error::class.simpleName}"
                    DesktopDebugLog.error("EventSeries", projectStatusText)
                }
                return
            }
            val action = when {
                DesktopProjectFilePaths.isAndroidRaceBackupJsonFileName(path.fileName.toString()) ->
                    PendingDirtyProjectAction.ImportAndroidRaceBackup(path)
                DesktopProjectFilePaths.isProjectFileName(path.fileName.toString()) ->
                    PendingDirtyProjectAction.OpenProject(path)
                else -> {
                    projectStatusText = "Unsupported Event File type: ${path.fileName}"
                    return
                }
            }
            pendingDirtyProjectAction = DesktopDirtyProjectActions.pendingActionOrNull(
                hasProtectedUnsavedChanges(),
                action
            )
            if (pendingDirtyProjectAction == null) {
                when (action) {
                    is PendingDirtyProjectAction.OpenProject -> openProject(action.path)
                    is PendingDirtyProjectAction.ImportAndroidRaceBackup -> importAndroidRaceBackupJson(action.path)
                    else -> Unit
                }
            }
        }

        fun chooseOpenEventFile() {
            DesktopFileDialogs.chooseOpenProject()?.let(::openOrImportSelectedEventFile)
        }

        fun openSeriesEvent(summary: DesktopEventSeriesEventSummary) {
            when {
                summary.isCurrentEvent -> projectStatusText = "${summary.displayName} is already open."
                !summary.exists -> projectStatusText = "Series event file is missing: ${summary.eventFilePath}"
                else -> openOrImportSelectedEventFile(summary.resolvedPath)
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
                val seriesRefreshWarning = projectSession.currentProject?.let { savedProject ->
                    refreshSavedEventSeriesMetadata(savedProject, path)
                }
                hasUnsavedEventDefinitionChanges = false
                isEventDefinitionSaveDialogVisible = false
                syncProjectState()
                DesktopLastEventFilePreferences.rememberEventFile(path)
                projectStatusText = buildString {
                    append("Saved ${path.fileName}")
                    if (seriesRefreshWarning != null) {
                        append(". ")
                        append(seriesRefreshWarning)
                    }
                }
                val settingsLog = projectSession.currentProject?.startDrawSettingsLogText().orEmpty()
                DesktopDebugLog.info("EventFile", "Saved As ${path.fileName} $settingsLog")
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

        fun sendEventFileToAndroid() {
            if (projectSession.currentProject == null) {
                projectStatusText = "Open or create an Event File before sending to Android."
                return
            }
            if (projectSession.currentPath == null || hasProtectedUnsavedChanges()) {
                if (!saveCurrentProject()) {
                    projectStatusText = "Save the Event File before sending it to Android."
                    return
                }
            }

            val path = projectSession.currentPath ?: run {
                projectStatusText = "Save the Event File before sending it to Android."
                return
            }

            eventFileTransferServer?.stop()
            eventFileTransferRequestId += 1
            val transferRequestId = eventFileTransferRequestId
            eventFileTransferDialog = null

            runCatching {
                val fileName = path.fileName.toString()
                val byteCount = Files.size(path)
                val server = DesktopEventFileTransferServer(
                    filePath = path,
                    onStopped = { reason ->
                        appCoroutineScope.launch {
                            if (transferRequestId != eventFileTransferRequestId) {
                                return@launch
                            }
                            val message = when (reason) {
                                DesktopEventFileTransferStopReason.Downloaded ->
                                    "Event File downloaded by Android. Transfer stopped."
                                DesktopEventFileTransferStopReason.Cancelled ->
                                    "Event File transfer cancelled."
                                DesktopEventFileTransferStopReason.Timeout ->
                                    "Event File transfer expired after 10 minutes."
                            }
                            projectStatusText = message
                            eventFileTransferDialog = null
                            eventFileTransferServer = null
                            when (reason) {
                                DesktopEventFileTransferStopReason.Downloaded ->
                                    eventFileTransferResultDialog = DesktopEventFileTransferResultDialogState.downloaded(
                                        fileName = fileName,
                                        path = path,
                                        byteCount = byteCount
                                    )
                                DesktopEventFileTransferStopReason.Timeout ->
                                    eventFileTransferResultDialog = DesktopEventFileTransferResultDialogState.failure(
                                        title = "Android Transfer Expired",
                                        message = "No Android device downloaded $fileName before the 10 minute transfer link expired.",
                                        path = path
                                    )
                                DesktopEventFileTransferStopReason.Cancelled -> Unit
                            }
                            DesktopDebugLog.info("EventFile", "Android transfer stopped: $reason")
                        }
                    }
                )
                val addresses = server.addresses
                val session = server.start(addresses.first())
                eventFileTransferServer = server
                eventFileTransferDialog = DesktopEventFileTransferDialogState(
                    addresses = addresses,
                    selectedAddress = session.address,
                    session = session,
                    qrCode = desktopEventFileTransferQrCode(session.url),
                    statusText = "Waiting for Android to download ${session.fileName}. The link expires after 10 minutes or one download."
                )
                projectStatusText = "Event File transfer ready at ${session.url}"
                DesktopDebugLog.info(
                    "EventFile",
                    "Started Android transfer for ${path.fileName}: selectedHost=${session.address.host} port=${session.port} candidates=${
                        addresses.joinToString { address ->
                            "${address.interfaceName.ifBlank { "unknown" }}:${address.host}"
                        }
                    }"
                )
            }.onFailure { error ->
                eventFileTransferServer = null
                eventFileTransferDialog = null
                projectStatusText = "Could not start Android transfer: ${error.message ?: error::class.simpleName}"
                DesktopDebugLog.error("EventFile", projectStatusText)
            }
        }

        fun handleReceivedAndroidFile(result: DesktopAndroidFileReceiveResult): DesktopAndroidFileReceiveResultDialogState {
            val savedText = "Received ${result.fileName} from Android (${result.byteCount} bytes)."
            recordActivity(savedText)
            DesktopDebugLog.info("EventFile", "$savedText Saved to ${result.path}.")

            val lowerFileName = result.fileName.lowercase()
            val outcome = when {
                lowerFileName.endsWith(DesktopProjectFilePaths.ANDROID_RACE_BACKUP_JSON_EXTENSION) -> {
                    if (hasProtectedUnsavedChanges()) {
                        projectStatusText =
                            "$savedText Saved to ${result.path}. Save or close the current Event File before importing it."
                        DesktopAndroidFileReceiveResultDialogState.savedOnly(
                            fileName = result.fileName,
                            path = result.path,
                            reason = "The current desktop Event File has unsaved changes, so the received Android Event File was not loaded."
                        )
                    } else {
                        runCatching {
                            clearAssignedControlsWarning()
                            lockProtectedCourseOrder()
                            val imported = DesktopProjectFiles.importAndroidRaceBackupJson(result.path) {
                                UUID.randomUUID().toString()
                            }
                            projectFile = projectSession.newProject(imported)
                            newEventDraftProject = null
                            hasUnsavedEventDefinitionChanges = false
                            isEventDefinitionSaveDialogVisible = false
                            syncProjectState()
                            projectStatusText = "Imported ${result.path.fileName} from Android. Save it to choose its desktop Event File path."
                            DesktopAndroidFileReceiveResultDialogState.loaded(
                                fileName = result.fileName,
                                path = result.path,
                                loadedMessage = "The received Android Event File is now open as an unsaved desktop Event File."
                            )
                        }.getOrElse { error ->
                            projectStatusText =
                                "$savedText Saved to ${result.path}, but import failed: ${error.message ?: error::class.simpleName}"
                            DesktopDebugLog.error("EventFile", projectStatusText)
                            DesktopAndroidFileReceiveResultDialogState.failedToLoad(
                                fileName = result.fileName,
                                path = result.path,
                                failure = "Import failed: ${error.message ?: error::class.simpleName}"
                            )
                        }
                    }
                }
                DesktopProjectFilePaths.isProjectFileName(result.fileName) -> {
                    if (hasProtectedUnsavedChanges()) {
                        projectStatusText =
                            "$savedText Saved to ${result.path}. Save or close the current Event File before opening it."
                        DesktopAndroidFileReceiveResultDialogState.savedOnly(
                            fileName = result.fileName,
                            path = result.path,
                            reason = "The current desktop Event File has unsaved changes, so the received desktop Event File was not opened."
                        )
                    } else {
                        runCatching {
                            clearAssignedControlsWarning()
                            lockProtectedCourseOrder()
                            projectFile = projectSession.open(result.path)
                            newEventDraftProject = null
                            hasUnsavedEventDefinitionChanges = false
                            isEventDefinitionSaveDialogVisible = false
                            hasUnsavedChanges = projectSession.hasUnsavedChanges
                            DesktopLastEventFilePreferences.rememberEventFile(result.path)
                            projectStatusText = "Opened ${result.path.fileName} from Android."
                            DesktopDebugLog.info("EventFile", "Opened ${result.path.fileName} from Android receive")
                            DesktopAndroidFileReceiveResultDialogState.loaded(
                                fileName = result.fileName,
                                path = result.path,
                                loadedMessage = "The received desktop Event File is now open."
                            )
                        }.getOrElse { error ->
                            projectStatusText =
                                "$savedText Saved to ${result.path}, but open failed: ${error.message ?: error::class.simpleName}"
                            DesktopDebugLog.error("EventFile", projectStatusText)
                            DesktopAndroidFileReceiveResultDialogState.failedToLoad(
                                fileName = result.fileName,
                                path = result.path,
                                failure = "Open failed: ${error.message ?: error::class.simpleName}"
                            )
                        }
                    }
                }
                else -> {
                    projectStatusText = "$savedText Saved to ${result.path}."
                    DesktopAndroidFileReceiveResultDialogState.savedOnly(
                        fileName = result.fileName,
                        path = result.path,
                        reason = "This file type is not automatically opened by the desktop app."
                    )
                }
            }
            return outcome
        }

        fun receiveFileFromAndroid() {
            androidFileReceiveServer?.stop()
            androidFileReceiveDialog = null

            runCatching {
                val server = DesktopAndroidFileReceiveServer(
                    receiveDirectory = DesktopAndroidFileReceiveLocations.receiveDirectory(),
                    onReceived = { result ->
                        appCoroutineScope.launch {
                            androidFileReceiveDialog = null
                            androidFileReceiveResultDialog = handleReceivedAndroidFile(result)
                        }
                    },
                    onStopped = { reason ->
                        appCoroutineScope.launch {
                            val message = when (reason) {
                                DesktopAndroidFileReceiveStopReason.Received ->
                                    "Android file received. Transfer stopped."
                                DesktopAndroidFileReceiveStopReason.Cancelled ->
                                    "Android file receive cancelled."
                                DesktopAndroidFileReceiveStopReason.Timeout ->
                                    "Android file receive expired after 10 minutes."
                            }
                            when (reason) {
                                DesktopAndroidFileReceiveStopReason.Received -> {
                                    androidFileReceiveDialog = null
                                }
                                DesktopAndroidFileReceiveStopReason.Timeout -> {
                                    projectStatusText = message
                                    androidFileReceiveDialog = null
                                    androidFileReceiveResultDialog = DesktopAndroidFileReceiveResultDialogState.failure(
                                        title = "Android Receive Expired",
                                        message = "No file was received before the 10 minute receive link expired."
                                    )
                                }
                                DesktopAndroidFileReceiveStopReason.Cancelled -> {
                                    projectStatusText = message
                                    androidFileReceiveDialog = null
                                }
                            }
                            DesktopDebugLog.info("EventFile", "Android receive stopped: $reason")
                        }
                    }
                )
                val addresses = server.addresses
                val session = server.start(addresses.first())
                androidFileReceiveServer = server
                androidFileReceiveDialog = DesktopAndroidFileReceiveDialogState(
                    addresses = addresses,
                    selectedAddress = session.address,
                    session = session,
                    qrCode = desktopEventFileTransferQrCode(session.url),
                    statusText = "Waiting for Android to upload a file. The link expires after 10 minutes or one upload."
                )
                projectStatusText = "Android file receive ready at ${session.url}"
                DesktopDebugLog.info(
                    "EventFile",
                    "Started Android file receive: selectedHost=${session.address.host} port=${session.port} candidates=${
                        addresses.joinToString { address ->
                            "${address.interfaceName.ifBlank { "unknown" }}:${address.host}"
                        }
                    }"
                )
            }.onFailure { error ->
                androidFileReceiveServer = null
                androidFileReceiveDialog = null
                projectStatusText = "Could not start Android file receive: ${error.message ?: error::class.simpleName}"
                DesktopDebugLog.error("EventFile", projectStatusText)
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
                DesktopNavAction.ReceiveFileFromAndroid,
                DesktopNavAction.ImportEventRegWebsite,
                DesktopNavAction.ImportDemFile -> true
                DesktopNavAction.ImportEventRegCompetitorsCsv -> projectFile != null
                DesktopNavAction.ShowDebugLogHelp,
                DesktopNavAction.ShowAbout -> true
                DesktopNavAction.SaveEventFile -> canSaveEventFile()
                DesktopNavAction.StopContinuousSiReadout -> isContinuousSiReadoutActive
                DesktopNavAction.OpenLocalResultsWebPage,
                DesktopNavAction.PreviewLocalResultsWebPage,
                DesktopNavAction.StartLocalResultsWebServer -> projectFile != null
                DesktopNavAction.StopLocalResultsWebServer -> localResultsWebServerUrl != null
                DesktopNavAction.OpenPublicResultsSitePreview -> publicResultSiteDirectory != null
                DesktopNavAction.PublishPublicResultsSite ->
                    publicResultSiteDirectory != null && !isPublishingPublicResultSite
                DesktopNavAction.StopPublicResultsSitePreview -> publicResultSitePreviewUrl != null
                DesktopNavAction.SendRobis -> projectFile != null && !isSendingLiveResults
                DesktopNavAction.SendEventFileToAndroid -> projectFile != null
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
                DesktopNavAction.ImportCourseGpx,
                DesktopNavAction.ImportControlsKmlKmz,
                DesktopNavAction.ImportControlsGpx,
                DesktopNavAction.ImportControlsCsv,
                DesktopNavAction.ImportCompetitorsCsv,
                DesktopNavAction.ImportStartsCsv,
                DesktopNavAction.DeleteAllControls,
                DesktopNavAction.DeleteAllCategoryAssignedControls,
                DesktopNavAction.DeleteAllCategories,
                DesktopNavAction.DeleteAllCompetitors,
                DesktopNavAction.ExportEventFileCopy,
                DesktopNavAction.SendEventFileToAndroid,
                DesktopNavAction.ExportCategoriesCsv,
                DesktopNavAction.ExportControlsCsv,
                DesktopNavAction.ExportCourseKmlKmz,
                DesktopNavAction.ExportCourseGpx,
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
                DesktopNavAction.GeneratePublicResultsSite,
                DesktopNavAction.ExportArdfJson,
                DesktopNavAction.ExportAndroidRaceBackupJson,
                DesktopNavAction.ExportLiveResultsJson,
                DesktopNavAction.ExportFinalResultsJson,
                DesktopNavAction.ExportIofStartListXml,
                DesktopNavAction.ExportIofResultListXml,
                DesktopNavAction.CreateEventSeriesWithCurrentEvent,
                DesktopNavAction.LinkCurrentEventToSeries,
                DesktopNavAction.ChangeCurrentEventSeriesLink,
                DesktopNavAction.RemoveCurrentEventFromSeries,
                DesktopNavAction.ValidateCurrentEventSeriesLink,
                DesktopNavAction.BalanceStartListFromEventSeries,
                DesktopNavAction.AddEventToSeries,
                DesktopNavAction.ValidateEventSeries,
                DesktopNavAction.ExportEventSeries ->
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
                DesktopNavAction.OpenLocalResultsWebPage,
                DesktopNavAction.PreviewLocalResultsWebPage,
                DesktopNavAction.StartLocalResultsWebServer ->
                    "Open or create an Event File before starting the local web server."
                DesktopNavAction.StopLocalResultsWebServer ->
                    "The local results web server is not running."
                DesktopNavAction.OpenPublicResultsSitePreview ->
                    "Generate a public results site before opening preview."
                DesktopNavAction.PublishPublicResultsSite ->
                    when {
                        publicResultSiteDirectory == null -> "Generate a public results site before publishing."
                        isPublishingPublicResultSite -> "The public results site is already being published."
                        else -> "Public results site publishing is not available right now."
                    }
                DesktopNavAction.StopPublicResultsSitePreview ->
                    "The public results site preview is not running."
                DesktopNavAction.SendRobis ->
                    if (projectFile == null) {
                        "Open or create an Event File before sending ROBIS results."
                    } else {
                        "ROBIS results are already being sent."
                    }
                DesktopNavAction.NewEventFile,
                DesktopNavAction.OpenEventFile,
                DesktopNavAction.ReceiveFileFromAndroid,
                DesktopNavAction.ImportEventRegWebsite,
                DesktopNavAction.ImportDemFile,
                DesktopNavAction.ShowDebugLogHelp,
                DesktopNavAction.ShowAbout -> null
            }
        }

        fun handleNavAction(action: DesktopNavAction) {
            when (action) {
                DesktopNavAction.NewEventFile -> requestNewEventFile()
                DesktopNavAction.OpenEventFile -> chooseOpenEventFile()
                DesktopNavAction.ReceiveFileFromAndroid -> receiveFileFromAndroid()
                DesktopNavAction.ImportEventRegWebsite -> showEventRegImportDialog()
                DesktopNavAction.ImportEventRegCompetitorsCsv -> showEventRegCompetitorCsvImportDialog()
                DesktopNavAction.SaveEventFile -> saveCurrentProject()
                DesktopNavAction.CloseEventFile -> requestCloseEventFile()
                DesktopNavAction.ImportCategoriesCsv -> importCategoriesCsv()
                DesktopNavAction.ImportControlsCsv -> importControlsCsv()
                DesktopNavAction.ImportCourseKmlKmz -> chooseImportCourseKmlKmz()
                DesktopNavAction.ImportCourseGpx -> chooseImportCourseGpx()
                DesktopNavAction.ImportControlsKmlKmz -> chooseImportControlsKmlKmz()
                DesktopNavAction.ImportControlsGpx -> chooseImportControlsGpx()
                DesktopNavAction.ImportDemFile -> chooseImportDemFiles()
                DesktopNavAction.DeleteAllControls ->
                    isDeleteAllControlsDialogVisible = true
                DesktopNavAction.DeleteAllCategoryAssignedControls ->
                    pendingBulkCategoryAction = BulkCategoryAction.DeleteAllAssignedControls
                DesktopNavAction.DeleteAllCategories ->
                    pendingBulkCategoryAction = BulkCategoryAction.DeleteAllCategories
                DesktopNavAction.DeleteAllCompetitors ->
                    isDeleteAllCompetitorsDialogVisible = true
                DesktopNavAction.ImportCompetitorsCsv -> importCompetitorsCsv()
                DesktopNavAction.ImportStartsCsv -> importCompetitorStartsCsv()
                DesktopNavAction.ExportEventFileCopy -> exportEventFileCopy()
                DesktopNavAction.SendEventFileToAndroid -> sendEventFileToAndroid()
                DesktopNavAction.ExportCategoriesCsv -> exportCategoriesCsv()
                DesktopNavAction.ExportControlsCsv ->
                    exportCsv("Export Controls CSV", "controls", DesktopProjectFiles::exportControlsCsv)
                DesktopNavAction.ExportCourseKmlKmz -> chooseExportCourseKmlKmz()
                DesktopNavAction.ExportCourseGpx -> chooseExportCourseGpx()
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
                DesktopNavAction.GeneratePublicResultsSite -> generatePublicResultsSite()
                DesktopNavAction.PublishPublicResultsSite -> publishPublicResultsSite()
                DesktopNavAction.OpenPublicResultsSitePreview -> openPublicResultsSitePreview()
                DesktopNavAction.StopPublicResultsSitePreview -> stopPublicResultsSitePreview()
                DesktopNavAction.ExportArdfJson -> exportArdfJson()
                DesktopNavAction.ExportAndroidRaceBackupJson -> exportAndroidRaceBackupJson()
                DesktopNavAction.ExportLiveResultsJson -> exportLiveResultsJson()
                DesktopNavAction.ExportFinalResultsJson -> exportFinalResultsJson()
                DesktopNavAction.ExportIofStartListXml -> exportIofStartListXml()
                DesktopNavAction.ExportIofResultListXml -> exportIofResultListXml()
                DesktopNavAction.DownloadSiCard -> downloadSportIdentReadout()
                DesktopNavAction.StartContinuousSiReadout -> startContinuousSportIdentReadout()
                DesktopNavAction.StopContinuousSiReadout -> stopContinuousSportIdentReadout()
                DesktopNavAction.OpenLocalResultsWebPage -> openLocalResultsWebPage()
                DesktopNavAction.PreviewLocalResultsWebPage -> previewLocalResultsWebPage()
                DesktopNavAction.StartLocalResultsWebServer -> startLocalResultsWebServerOnWifi()
                DesktopNavAction.StopLocalResultsWebServer -> stopLocalResultsWebServer()
                DesktopNavAction.SendRobis -> sendRobisLiveResults()
                DesktopNavAction.CreateEventSeriesWithCurrentEvent -> createEventSeriesWithCurrentEvent()
                DesktopNavAction.LinkCurrentEventToSeries,
                DesktopNavAction.ChangeCurrentEventSeriesLink -> linkCurrentEventToSeries()
                DesktopNavAction.RemoveCurrentEventFromSeries -> removeCurrentEventFromSeries()
                DesktopNavAction.ValidateCurrentEventSeriesLink,
                DesktopNavAction.ValidateEventSeries -> validateCurrentEventSeries()
                DesktopNavAction.BalanceStartListFromEventSeries -> balanceStartListFromEventSeries()
                DesktopNavAction.AddEventToSeries -> addEventToCurrentSeries()
                DesktopNavAction.ExportEventSeries -> exportCurrentEventSeries()
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
                Item("Send Event to Android", enabled = projectFile != null, onClick = ::sendEventFileToAndroid)
                Item("Receive File from Android", onClick = ::receiveFileFromAndroid)
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
            AboutRadioOracleDialog(
                isUpdateCheckingEnabled = isUpdateCheckingEnabled,
                updateCheckStatus = updateCheckStatus,
                onCheckForUpdates = ::checkForUpdates,
                onOpenUpdateLink = ::openExternalUrl,
                onDismiss = { isAboutDialogVisible = false }
            )
        }
        eventFileTransferDialog?.let { dialogState ->
            EventFileTransferDialog(
                state = dialogState,
                onAddressSelected = { address ->
                    val server = eventFileTransferServer
                    if (server != null) {
                        runCatching {
                            val session = server.sessionFor(address)
                            eventFileTransferDialog = dialogState.copy(
                                selectedAddress = address,
                                session = session,
                                qrCode = desktopEventFileTransferQrCode(session.url)
                            )
                            projectStatusText = "Event File transfer ready at ${session.url}"
                        }.onFailure { error ->
                            projectStatusText = "Could not update transfer URL: ${error.message ?: error::class.simpleName}"
                        }
                    }
                },
                onCopyUrl = {
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(
                        StringSelection(dialogState.session.url),
                        null
                    )
                    projectStatusText = "Copied Android transfer URL."
                },
                onCancel = {
                    eventFileTransferServer?.stop()
                    eventFileTransferServer = null
                    eventFileTransferDialog = null
                }
            )
        }
        eventFileTransferResultDialog?.let { dialogState ->
            EventFileTransferResultDialog(
                state = dialogState,
                onDismiss = { eventFileTransferResultDialog = null }
            )
        }
        androidFileReceiveDialog?.let { dialogState ->
            AndroidFileReceiveDialog(
                state = dialogState,
                onAddressSelected = { address ->
                    val server = androidFileReceiveServer
                    if (server != null) {
                        runCatching {
                            val session = server.sessionFor(address)
                            androidFileReceiveDialog = dialogState.copy(
                                selectedAddress = address,
                                session = session,
                                qrCode = desktopEventFileTransferQrCode(session.url)
                            )
                            projectStatusText = "Android file receive ready at ${session.url}"
                        }.onFailure { error ->
                            projectStatusText = "Could not update receive URL: ${error.message ?: error::class.simpleName}"
                        }
                    }
                },
                onCopyUrl = {
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(
                        StringSelection(dialogState.session.url),
                        null
                    )
                    projectStatusText = "Copied Android file receive URL."
                },
                onCancel = {
                    androidFileReceiveServer?.stop()
                    androidFileReceiveServer = null
                    androidFileReceiveDialog = null
                }
            )
        }
        androidFileReceiveResultDialog?.let { dialogState ->
            AndroidFileReceiveResultDialog(
                state = dialogState,
                onDismiss = { androidFileReceiveResultDialog = null }
            )
        }
        appUpdateDialogStatus?.let { status ->
            RadioOracleUpdateDialog(
                status = status,
                onOpenUpdateLink = { openExternalUrl(DesktopAppUpdateSupport.updatePageUrl) },
                onDismiss = { appUpdateDialogStatus = null }
            )
        }
        pendingCourseKmlKmzImportWarning?.let { warning ->
            AlertDialog(
                onDismissRequest = { pendingCourseKmlKmzImportWarning = null },
                title = { Text(warning.title) },
                text = { Text(warning.message) },
                confirmButton = {
                    Button(onClick = { pendingCourseKmlKmzImportWarning = null }) {
                        ButtonLabel("OK")
                    }
                }
            )
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
                    CourseKmlKmzUnlockAction.ImportGpx -> "Unlock course order"
                    CourseKmlKmzUnlockAction.ImportControls -> "Unlock control locations"
                    CourseKmlKmzUnlockAction.ImportControlsGpx -> "Unlock control locations"
                    CourseKmlKmzUnlockAction.Export -> "Export protected controls/routes"
                    CourseKmlKmzUnlockAction.ExportGpx -> "Export protected controls/routes"
                },
                description = when (unlockAction) {
                    CourseKmlKmzUnlockAction.Import ->
                        "KML/KMZ controls/route data includes coordinates and route details that require the Event Password."
                    CourseKmlKmzUnlockAction.ImportGpx ->
                        "GPX controls/route data includes coordinates and route details that require the Event Password."
                    CourseKmlKmzUnlockAction.ImportControls ->
                        "KML/KMZ control-location data includes coordinates that require the Event Password."
                    CourseKmlKmzUnlockAction.ImportControlsGpx ->
                        "GPX control-location data includes coordinates that require the Event Password."
                    CourseKmlKmzUnlockAction.Export ->
                        "Controls/route KML/KMZ export includes sensitive coordinates and routes. The exported file will be placed inside a password-locked ZIP."
                    CourseKmlKmzUnlockAction.ExportGpx ->
                        "Controls/route GPX export includes sensitive coordinates and routes. The exported file will be placed inside a password-locked ZIP."
                },
                confirmLabel = when (unlockAction) {
                    CourseKmlKmzUnlockAction.Import -> "Unlock and Import"
                    CourseKmlKmzUnlockAction.ImportGpx -> "Unlock and Import"
                    CourseKmlKmzUnlockAction.ImportControls -> "Unlock and Import"
                    CourseKmlKmzUnlockAction.ImportControlsGpx -> "Unlock and Import"
                    CourseKmlKmzUnlockAction.Export -> "Export"
                    CourseKmlKmzUnlockAction.ExportGpx -> "Export"
                },
                onUnlock = { password ->
                    if (unlockProtectedCourseOrder(password)) {
                        val unlockedPassword = password.trim()
                        pendingCourseKmlKmzUnlockAction = null
                        when (unlockAction) {
                            CourseKmlKmzUnlockAction.Import -> chooseImportCourseKmlKmzUnlocked(unlockedPassword)
                            CourseKmlKmzUnlockAction.ImportGpx -> chooseImportCourseGpxUnlocked(unlockedPassword)
                            CourseKmlKmzUnlockAction.ImportControls ->
                                chooseImportCourseKmlKmzUnlocked(unlockedPassword, requireRoutes = false)
                            CourseKmlKmzUnlockAction.ImportControlsGpx ->
                                chooseImportCourseGpxUnlocked(unlockedPassword, requireRoutes = false)
                            CourseKmlKmzUnlockAction.Export -> chooseExportCourseKmlKmzUnlocked(unlockedPassword)
                            CourseKmlKmzUnlockAction.ExportGpx -> chooseExportCourseGpxUnlocked(unlockedPassword)
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
                description = "The Event File contains password-protected imported course or route data. Before deleting $controlLabel, Radio-Oracle needs the Event Password so it can clean any stored course references to that control.",
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
        if (isDeleteAllControlsDialogVisible) {
            val currentProject = projectSession.currentProject
            DeleteAllControlsDialog(
                controlCount = currentProject?.raceData?.controls?.size ?: 0,
                affectedCategoryCount = currentProject?.raceData?.categories?.count { categoryData ->
                    categoryData.controlPoints.isNotEmpty() ||
                        categoryData.publicControlIds.isNotEmpty() ||
                        categoryData.category.controlPointsString.isNotBlank() ||
                        categoryData.category.lengthMeters != 0 ||
                        categoryData.category.climbMeters != 0 ||
                        categoryData.category.encryptedIdealOrder?.isNotBlank() == true ||
                        categoryData.category.encryptedCourseInfo?.isNotBlank() == true
                } ?: 0,
                hasProtectedCategoryData = currentProject?.hasProtectedCategoryData() == true,
                onConfirm = { password ->
                    if (deleteAllControls(password)) {
                        isDeleteAllControlsDialogVisible = false
                        true
                    } else {
                        false
                    }
                },
                onCancel = { isDeleteAllControlsDialogVisible = false }
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
                    projectStatusText =
                        "Controls/route ${controlsRouteImportFormatLabel(mapping.sourceName)} import canceled. No changes applied."
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
                    projectStatusText =
                        "Controls/route ${controlsRouteImportFormatLabel(review.sourceName)} import canceled. No changes applied."
                }
            )
        }
        if (isImportingCourseKmlKmz) {
            IndeterminateProgressDialog(
                title = "Importing controls/route data",
                message = "Reading and reviewing the selected file. This can take a while for large KML/KMZ or GPX files."
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
        pendingDemFileImportReview?.let { review ->
            DemFileImportReviewDialog(
                review = review,
                onImport = {
                    if (importReviewedDemFiles(review)) {
                        pendingDemFileImportReview = null
                    }
                },
                onCancel = {
                    pendingDemFileImportReview = null
                    projectStatusText = "DEM import canceled. No changes applied."
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
        pendingControlRoleWarning?.let { warning ->
            ControlRoleWarningDialog(
                warning = warning,
                onDismiss = { pendingControlRoleWarning = null }
            )
        }

        RadioOManagerDesktopApp(
            projectFile = projectFile,
            eventFilePath = projectSession.currentPath,
            eventFileWorkingFolder = currentEventFileWorkingFolder(),
            eventSeriesUiContext = eventSeriesUiContext,
            seriesEventSummaries = seriesEventSummaries,
            seriesStartFairnessSummary = seriesStartFairnessSummary,
            seriesStartFairnessOptimizationResult = seriesStartFairnessOptimizationResult,
            eventStartListDrawNumbering = eventStartListDrawNumbering,
            seriesCompetitorMatchSummaries = seriesCompetitorMatchSummaries,
            seriesCompetitorIdentityCoverageSummaries = seriesCompetitorIdentityCoverageSummaries,
            eventSeriesValidationState = eventSeriesValidationState,
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
            localResultsWebServerUrl = localResultsWebServerUrl,
            publishedPublicResultSiteUrl = publishedPublicResultSiteUrl,
            printerDiagnostics = printerDiagnostics,
            isUpdateCheckingEnabled = isUpdateCheckingEnabled,
            cloudflarePagesPublishSettings = cloudflarePagesPublishSettings,
            raceClockTick = raceClockTick,
            isNavActionEnabled = ::isNavActionEnabled,
            disabledNavActionReason = ::disabledNavActionReason,
            onOptimizeSeriesStartFairness = ::optimizeSeriesStartFairness,
            onOpenSeriesEvent = ::openSeriesEvent,
            onUpdateEventSeriesName = ::updateCurrentEventSeriesName,
            onUpdateEventSeriesFileName = ::updateCurrentEventSeriesFileName,
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
            onResolveCachedCourseAnalysisElevations = ::resolveCachedCourseAnalysisElevations,
            onDownloadMissingCourseAnalysisElevations = ::downloadMissingCourseAnalysisElevations,
            onDownloadVenueElevationCache = ::startVenueElevationCacheDownload,
            onOpenVenueElevationCacheFolder = ::openVenueElevationCacheFolder,
            onOpenEventFileWorkingFolder = ::openEventFileWorkingFolder,
            elevationCacheRefreshToken = venueElevationCacheRefreshToken,
            onUnlockProtectedCourseOrder = ::unlockProtectedCourseOrder,
            onUpdateProtectedIdealOrder = ::updateProtectedIdealOrder,
            onUseCalculatedCourseAnalysisRoute = ::useCalculatedCourseAnalysisRoute,
            onApplyCourseAnalysisFoxRenumberingOnly = ::applyCourseAnalysisFoxRenumberingOnly,
            onUpdateCourseAnalyzerSpeedFactor = ::updateCourseAnalyzerSpeedFactor,
            onReadCompetitorSiCardForAddRow = ::readCompetitorSiCardForAddRow,
            onUpdateProtectedControlLocation = ::updateProtectedControlLocation,
            onUpdateProtectedCoursePassword = ::updateProtectedCoursePassword,
            onSetUpdateCheckingEnabled = ::setUpdateCheckingEnabled,
            onSetCloudflarePagesPublishSettings = ::setCloudflarePagesPublishSettings,
            onOpenPublishedPublicResultsSite = ::openExternalUrl,
            onCopyPublishedPublicResultsSite = { url ->
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(url), null)
                projectStatusText = "Copied public results link."
            },
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
            onUpdateCategoryGender = { categoryId, isMan ->
                runCatching {
                    projectFile = projectSession.updateCurrentProject { currentProject ->
                        EventProjectEditor.updateCategoryGender(currentProject, categoryId, isMan)
                    }
                    hasUnsavedChanges = projectSession.hasUnsavedChanges
                    recordActivity("Updated category gender.")
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
                    projectStatusText = "Created category without course data. Add assigned controls or import KML/KMZ or GPX course data before Race Ops."
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
                    projectSession.updateCurrentProject { currentProject ->
                        EventProjectEditor.updateCompetitorStartTime(currentProject, competitorId, startTime)
                    }
                    seriesStartFairnessOptimizationResult = null
                    clearEventStartListDrawHistory()
                    syncProjectState()
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
                    eventStartListDrawNumbering = null
                    projectStatusText = "Unsaved changes."
                    DesktopDebugLog.info("StartList", "Updated settings ${updatedProject.startDrawSettingsLogText()}")
                }.onFailure { error ->
                    projectStatusText = "Start list settings failed: ${error.message ?: error::class.simpleName}"
                    DesktopDebugLog.error("StartList", projectStatusText)
                }
            },
            onUpdateStartDrawSeriesOptimizationLock = { locked ->
                runCatching {
                    val updatedProject = projectSession.updateCurrentProject { currentProject ->
                        EventProjectEditor.updateStartDrawSeriesOptimizationLock(currentProject, locked)
                    }
                    projectFile = updatedProject
                    hasUnsavedChanges = projectSession.hasUnsavedChanges
                    seriesStartFairnessOptimizationResult = null
                    syncProjectState()
                    projectStatusText = if (locked) {
                        "Start list locked for Series optimization."
                    } else {
                        "Start list unlocked for Series optimization."
                    }
                    DesktopDebugLog.info("StartList", projectStatusText)
                }.onFailure { error ->
                    projectStatusText = "Start list lock failed: ${error.message ?: error::class.simpleName}"
                    DesktopDebugLog.error("StartList", projectStatusText)
                }
            },
            onDrawStartList = { interval, options ->
                runCatching {
                    val currentProject = requireNotNull(projectSession.currentProject) {
                        "Open or create an Event File before generating starts."
                    }
                    val currentPath = projectSession.currentPath
                    val protectedOptions = options.copy(
                        idealFirstFoxByCategoryId = unlockedIdealFirstFoxByCategoryId()
                    ).forEventStartListGeneration()
                    val drawContextKey = DesktopStartListDrawNumbers.drawContextKey(interval, protectedOptions)
                    val currentEventKey = DesktopStartListDrawNumbers.eventKey(currentPath, currentProject)
                    val currentDrawExhaustedKey = "$currentEventKey|context:$drawContextKey"
                    val knownOrderCount = DesktopStartListDrawNumbers.knownOrderCount(
                        existingNumbers = eventStartListDrawNumbers,
                        eventPath = currentPath,
                        projectFile = currentProject,
                        drawContextKey = drawContextKey
                    )

                    fun knownOrderProject(orderNumber: Int): EventProjectFile? =
                        eventStartListDrawProjects[
                            DesktopStartListDrawNumbers.orderProjectKey(
                                eventPath = currentPath,
                                projectFile = currentProject,
                                orderNumber = orderNumber,
                                drawContextKey = drawContextKey
                            )
                        ]

                    fun nextKnownOrderNumber(orderCount: Int): Int {
                        val currentOrderNumber = eventStartListDrawNumbering?.orderNumber ?: 0
                        return if (currentOrderNumber in 1 until orderCount) {
                            currentOrderNumber + 1
                        } else {
                            1
                        }
                    }

                    fun numberingFor(project: EventProjectFile): DesktopStartListDrawNumbering =
                        DesktopStartListDrawNumbers.assign(
                            existingNumbers = eventStartListDrawNumbers,
                            eventPath = currentPath,
                            projectFile = project,
                            drawContextKey = drawContextKey
                        )

                    val drawResult = if (currentDrawExhaustedKey in eventStartListDrawExhaustedKeys && knownOrderCount > 0) {
                        // Once all discoverable orders are known, button presses should cycle those orders predictably.
                        val nextOrderNumber = nextKnownOrderNumber(knownOrderCount)
                        val nextProject = knownOrderProject(nextOrderNumber) ?: currentProject
                        nextProject to numberingFor(nextProject)
                    } else {
                        var chosenProject: EventProjectFile? = null
                        var chosenNumbering: DesktopStartListDrawNumbering? = null
                        for (attempt in 1..EventStartListUniqueDrawMaxAttempts) {
                            val candidateProject = EventProjectEditor.drawStartList(
                                currentProject,
                                interval,
                                protectedOptions.copy(seed = "event-draw-${UUID.randomUUID()}")
                            )
                            val candidateNumbering = DesktopStartListDrawNumbers.assign(
                                existingNumbers = eventStartListDrawNumbers,
                                eventPath = currentPath,
                                projectFile = candidateProject,
                                drawContextKey = drawContextKey
                            )
                            chosenProject = candidateProject
                            chosenNumbering = candidateNumbering
                            if (!candidateNumbering.repeatedOrder) {
                                eventStartListDrawProjects = eventStartListDrawProjects + (
                                    DesktopStartListDrawNumbers.orderProjectKey(
                                        eventPath = currentPath,
                                        projectFile = candidateProject,
                                        orderNumber = candidateNumbering.orderNumber,
                                        drawContextKey = drawContextKey
                                    ) to candidateProject
                                    )
                                eventStartListDrawExhaustedKeys = eventStartListDrawExhaustedKeys - currentDrawExhaustedKey
                                break
                            }
                        }
                        val candidateProject = requireNotNull(chosenProject)
                        val candidateNumbering = requireNotNull(chosenNumbering)
                        if (!candidateNumbering.repeatedOrder) {
                            candidateProject to candidateNumbering
                        } else {
                            val updatedKnownOrderCount = DesktopStartListDrawNumbers.knownOrderCount(
                                existingNumbers = eventStartListDrawNumbers,
                                eventPath = currentPath,
                                projectFile = currentProject,
                                drawContextKey = drawContextKey
                            )
                            eventStartListDrawExhaustedKeys = eventStartListDrawExhaustedKeys + currentDrawExhaustedKey
                            val nextOrderNumber = nextKnownOrderNumber(updatedKnownOrderCount)
                            val nextProject = knownOrderProject(nextOrderNumber) ?: candidateProject
                            nextProject to numberingFor(nextProject)
                        }
                    }
                    val drawnProject = drawResult.first
                    val drawNumbering = drawResult.second
                    projectSession.updateCurrentProject { drawnProject }
                    eventStartListDrawNumbers = drawNumbering.orderNumbers
                    eventStartListDrawNumbering = drawNumbering
                    eventStartListDrawEventPath = currentPath?.toAbsolutePath()?.normalize()
                    seriesStartFairnessOptimizationResult = null
                    syncProjectState()
                    val drawStatus = startListDrawStatusText(EventStartListDetails.from(drawnProject.raceData))
                    val orderStatus = "Start order #${drawNumbering.orderNumber}."
                    projectStatusText = "$drawStatus $orderStatus"
                    DesktopDebugLog.info("StartList", "Generated $orderStatus ${drawnProject.startDrawSettingsLogText()}")
                }.onFailure { error ->
                    projectStatusText = "Draw failed: ${error.message ?: error::class.simpleName}"
                    DesktopDebugLog.error("StartList", projectStatusText)
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
                    var roleWarning: String? = null
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
                        val updatedProject = EventProjectEditor.updateControl(
                            currentProject,
                            controlId,
                            label,
                            siCode,
                            type,
                            scored,
                            publicLabel,
                            notes
                        )
                        roleWarning = combinedControlRoleWarning(
                            controlRoleMismatchWarning(type, publicLabel),
                            duplicateControlRoleWarning(updatedProject.raceData.controls, type),
                            controlCourseRuleWarning(
                                controls = updatedProject.raceData.controls,
                                raceType = updatedProject.raceData.race.raceType,
                                changedRole = type
                            )
                        )
                        updatedProject
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
                    pendingControlRoleWarning = roleWarning
                }.onFailure { error ->
                    projectStatusText = "Edit failed: ${error.message ?: error::class.simpleName}"
                }
            },
            onAddControl = { label, siCode, type, scored, publicLabel, notes ->
                var roleWarning: String? = null
                val result = runCatching {
                    projectFile = projectSession.updateCurrentProject { currentProject ->
                        val updatedProject = EventProjectEditor.addControl(
                            currentProject,
                            UUID.randomUUID().toString(),
                            label,
                            siCode,
                            type,
                            scored,
                            publicLabel,
                            notes
                        )
                        roleWarning = combinedControlRoleWarning(
                            controlRoleMismatchWarning(type, publicLabel),
                            duplicateControlRoleWarning(updatedProject.raceData.controls, type),
                            controlCourseRuleWarning(
                                controls = updatedProject.raceData.controls,
                                raceType = updatedProject.raceData.race.raceType,
                                changedRole = type
                            )
                        )
                        updatedProject
                    }
                    hasUnsavedChanges = projectSession.hasUnsavedChanges
                    projectStatusText = "Unsaved changes."
                    pendingControlRoleWarning = roleWarning
                }
                result.onFailure { error ->
                    projectStatusText = "Edit failed: ${error.message ?: error::class.simpleName}"
                }
                result.isSuccess
            },
            onRemoveControl = { controlId ->
                deleteControlAfterProtectedRouteCheck(controlId)
            },
            onImportControlsRouteKmlKmz = ::chooseImportControlsKmlKmz,
            onImportControlsRouteGpx = ::chooseImportControlsGpx,
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

@Composable
private fun ControlRoleWarningDialog(
    warning: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Check control role") },
        text = { Text(warning) },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
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
    fun submitPassword() {
        if (passwordDraft.isNotBlank() && onUnlock(passwordDraft)) {
            passwordDraft = ""
        }
    }

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
                    modifier = Modifier
                        .fillMaxWidth()
                        .commitOnEnter(::submitPassword)
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
                onClick = ::submitPassword,
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
    val canSubmit = categoryCount > 0 && (!hasProtectedCategoryData || passwordDraft.isNotBlank())
    fun submit() {
        if (canSubmit && onConfirm(passwordDraft)) {
            passwordDraft = ""
        }
    }
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .commitOnEnter(::submit)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = ::submit,
                enabled = canSubmit
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
private fun DeleteAllControlsDialog(
    controlCount: Int,
    affectedCategoryCount: Int,
    hasProtectedCategoryData: Boolean,
    onConfirm: (String) -> Boolean,
    onCancel: () -> Unit
) {
    var passwordDraft by remember { mutableStateOf("") }
    val canSubmit = (controlCount > 0 || affectedCategoryCount > 0) &&
        (!hasProtectedCategoryData || passwordDraft.isNotBlank())
    fun submit() {
        if (canSubmit && onConfirm(passwordDraft)) {
            passwordDraft = ""
        }
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Delete all controls") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "This removes all $controlCount controls from the Event File and clears dependent category control assignments, length, and climb data."
                )
                Text(
                    text = if (affectedCategoryCount > 0) {
                        "$affectedCategoryCount categories have course data that will be cleared."
                    } else {
                        "No category course assignments are currently attached to controls."
                    },
                    fontSize = 13.sp,
                    color = Color.DarkGray
                )
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .commitOnEnter(::submit)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = ::submit,
                enabled = canSubmit
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

private fun controlsRouteImportFormatLabel(sourceName: String): String =
    if (sourceName.endsWith(".gpx", ignoreCase = true)) {
        "GPX"
    } else {
        "KML/KMZ"
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
    val formatLabel = controlsRouteImportFormatLabel(review.sourceName)
    var createMissingCategories by remember(
        review.sourceName,
        summary.sourceSha256,
        summary.missingCategoryNames,
        summary.missingControlNames
    ) {
        mutableStateOf(summary.missingCategoryNames.isNotEmpty() || summary.missingControlNames.isNotEmpty())
    }
    val selectedSummary = if (createMissingCategories) {
        review.createdMissingCategorySummary ?: summary
    } else {
        summary
    }
    val categoriesText = selectedSummary.matchedCategoryNames
        .ifEmpty { listOf("None") }
        .joinToString()
    val missingStoredElevationPointCount = selectedSummary.missingElevationPointCount +
        selectedSummary.duplicateMissingElevationPointCount
    val canFetchElevations = selectedSummary.matchedCategoryIds.isNotEmpty() &&
        selectedSummary.hasMissingStoredElevations
    var fetchElevations by remember(
        review.sourceName,
        selectedSummary.sourceSha256,
        selectedSummary.isDuplicateOnly,
        missingStoredElevationPointCount
    ) {
        mutableStateOf(canFetchElevations)
    }
    var applyCategoryAssignments by remember(review.sourceName, summary.sourceSha256) {
        mutableStateOf(false)
    }
    val scrollState = rememberScrollState()
    Dialog(
        onDismissRequest = onCancel,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 320.dp, max = 560.dp)
                .heightIn(max = 640.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colors.surface
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    if (summary.isDuplicateOnly) {
                        "Duplicate controls/route import"
                    } else {
                        "Review controls/route import"
                    },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .clipToBounds()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState),
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
                        selectedSummary.categoryAssumptions.forEach { assumption ->
                            Text(
                                text = "No category indication was found for route ${assumption.routeName}; assuming ${assumption.categoryName}.",
                                color = Color(0xFFC46A00),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text("Matched categories: ${selectedSummary.matchedCategoryCount} of ${selectedSummary.routeCount} routes")
                        Text("Categories: $categoriesText")
                        if (summary.missingCategoryNames.isNotEmpty() || summary.missingControlNames.isNotEmpty()) {
                            if (summary.missingCategoryNames.isNotEmpty()) {
                                Text("Categories listed in $formatLabel but not in the Event File: ${summary.missingCategoryNames.joinToString()}")
                            }
                            if (summary.missingControlNames.isNotEmpty()) {
                                Text("Controls listed in $formatLabel but not in the Event File: ${summary.missingControlNames.joinToString()}")
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Checkbox(
                                    checked = createMissingCategories,
                                    onCheckedChange = { createMissingCategories = it }
                                )
                                Text("Create missing categories and controls, then save course data")
                            }
                            Text(
                                text = if (createMissingCategories) {
                                    "Created categories will be saved without competitors. Created controls will be added to Setup > Controls so route analysis and category assignments can use them."
                                } else {
                                    "Missing categories or controls will be left out of this import. Add them or reimport the $formatLabel later to store full course data."
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
                                Text("Replace category assigned controls with matched $formatLabel controls")
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
                        if (selectedSummary.controlIdentityUpdateCount > 0) {
                            Text("Control identities to update from SI= lines: ${selectedSummary.controlIdentityUpdateCount}")
                        }
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
                                if (selectedSummary.isDuplicateOnly) {
                                    "Stored route/control elevations missing: $missingStoredElevationPointCount course points"
                                } else {
                                    "Imported route/control elevations missing: $missingStoredElevationPointCount course points"
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
                                        "Download missing elevations for the stored route"
                                    } else {
                                        "Download missing elevations for the imported route after keeping it"
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
                                "Keep imported data to use these $formatLabel names as matches to existing Event File labels. Control labels and public labels are not renamed. No route facts, assigned controls, or control locations will change. Cancel leaves the Event File unchanged."
                            } else if (
                                selectedSummary.importedCategoryCount == 0 &&
                                (selectedSummary.changedControlLocationCount > 0 || selectedSummary.controlIdentityUpdateCount > 0)
                            ) {
                                "Keep imported data to update control locations. Affected stored route geometry is invalidated so Course Analyzer can recalculate route facts. Category assigned controls are changed only when the assignment checkbox is selected. Cancel leaves the Event File unchanged."
                            } else if (selectedSummary.importedCategoryCount == 0 && selectedSummary.assignedCategoryControlCount > 0) {
                                "Keep imported data to review matched $formatLabel control points. Category assigned controls are changed only when the assignment checkbox is selected. Cancel leaves the Event File unchanged."
                            } else if (selectedSummary.hasLabelConversions) {
                                "Keep imported data to use these $formatLabel names as matches to existing Event File labels, update route facts, ideal order, and any changed control locations. Category assigned controls are changed only when the assignment checkbox is selected. Control labels and public labels are not renamed. Cancel leaves the Event File unchanged."
                            } else {
                                "Keep imported data to update route facts, ideal order, and any changed control locations. Category assigned controls are changed only when the assignment checkbox is selected. Elevation retrieval samples missing USGS 3DEP route and course-object points after the import is kept. Cancel leaves the Event File unchanged."
                            },
                            fontSize = 13.sp,
                            color = Color.DarkGray
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(onClick = onCancel) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onKeep(fetchElevations, applyCategoryAssignments, createMissingCategories) }) {
                        Text(
                            if (selectedSummary.isDuplicateOnly) {
                                "Continue"
                            } else {
                                "Keep Imported Data"
                            }
                        )
                    }
                }
            }
        }
    }
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
                    Text("Protected controls/route course data will be preserved for ${preview.categoriesWithProtectedCoursePreservedCount} updated categor${if (preview.categoriesWithProtectedCoursePreservedCount == 1) "y" else "ies"}.")
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
    val formatLabel = controlsRouteImportFormatLabel(mapping.sourceName)
    var selectedCategoryId by remember(mapping.sourceName, mapping.categoryOptions) {
        mutableStateOf(mapping.categoryOptions.firstOrNull()?.first)
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Choose route category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("File: ${mapping.sourceName}")
                Text("No route name or file name matched an Event File category.")
                Text("Choose the category this $formatLabel route should update.")
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
    val remaining = (total - completed).coerceAtLeast(0)
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Retrieving course elevations") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
                Text("$remaining of $total elevation points left to download")
            }
        },
        confirmButton = {},
        dismissButton = {
            Button(
                onClick = onCancel,
                enabled = !progress.cancelRequested
            ) {
                Text(if (progress.cancelRequested) "Canceling..." else "Cancel")
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
    val remaining = (total - completed).coerceAtLeast(0)
    val fraction = completed.toFloat() / total.toFloat()
    val estimatedSizeText = progress.estimatedRawBytes?.let(::bytesText) ?: "calculating..."
    AlertDialog(
        onDismissRequest = {},
        title = { Text(if (progress.isLocalFileImport) "Importing elevation file" else "Creating elevation cache") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (progress.isLocalFileImport) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("Converting local elevation file data and creating the elevation cache.")
                    Text("Estimated output size: $estimatedSizeText")
                    Text(
                        text = "This can take a long time for large elevation files.",
                        fontSize = 13.sp,
                        color = Color.DarkGray
                    )
                } else {
                    LinearProgressIndicator(
                        progress = fraction,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("$remaining of $total elevation grid points left to download")
                    Text("Estimated output size: $estimatedSizeText")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Button(
                onClick = onCancel,
                enabled = !progress.cancelRequested
            ) {
                Text(if (progress.cancelRequested) "Canceling..." else "Cancel")
            }
        }
    )
}

@Composable
private fun DemFileImportReviewDialog(
    review: DesktopVenueElevationDemImportReview,
    onImport: () -> Unit,
    onCancel: () -> Unit
) {
    val hasImportableFiles = review.importableCount > 0
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Import DEM files") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (hasImportableFiles) {
                        "Radio-Oracle found ${review.importableCount} valid DEM cache file${if (review.importableCount == 1) "" else "s"} to import."
                    } else {
                        "Radio-Oracle did not find any valid DEM cache files to import."
                    },
                    color = DesktopPalette.Black,
                    fontSize = 14.sp
                )
                if (review.overwriteCount > 0) {
                    Text(
                        text = "${review.overwriteCount} imported file${if (review.overwriteCount == 1) "" else "s"} will overwrite existing cached venue data.",
                        color = DesktopPalette.Warning,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (review.candidates.isNotEmpty()) {
                    Text("Valid DEM files", fontWeight = FontWeight.Bold)
                    review.candidates.take(8).forEach { candidate ->
                        Text(
                            text = buildString {
                                append(candidate.targetPath.fileName)
                                append(" - ")
                                append(candidate.venueName)
                                append(" ")
                                append(candidate.resolutionMeters.roundToInt())
                                append(" m, ")
                                append(candidate.rowCount)
                                append(" x ")
                                append(candidate.columnCount)
                                if (candidate.willOverwrite) {
                                    append(" (overwrite)")
                                }
                            },
                            color = if (candidate.willOverwrite) DesktopPalette.Warning else DesktopPalette.Black,
                            fontSize = 12.sp
                        )
                    }
                    if (review.candidates.size > 8) {
                        Text(
                            text = "+${review.candidates.size - 8} more valid file${if (review.candidates.size - 8 == 1) "" else "s"}",
                            color = DesktopPalette.Black,
                            fontSize = 12.sp
                        )
                    }
                }
                if (review.issues.isNotEmpty()) {
                    Text("Skipped files", fontWeight = FontWeight.Bold)
                    review.issues.take(8).forEach { issue ->
                        Text(
                            text = "${issue.displayName}: ${issue.reason}",
                            color = DesktopPalette.Error,
                            fontSize = 12.sp
                        )
                    }
                    if (review.issues.size > 8) {
                        Text(
                            text = "+${review.issues.size - 8} more skipped file${if (review.issues.size - 8 == 1) "" else "s"}",
                            color = DesktopPalette.Error,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onImport,
                enabled = hasImportableFiles
            ) {
                Text(if (review.overwriteCount > 0) "Proceed" else "Import")
            }
        },
        dismissButton = {
            Button(onClick = onCancel) {
                Text(if (hasImportableFiles) "Abort" else "Close")
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .commitOnEnter {
                            if (!isImporting && url.isNotBlank()) {
                                onImport()
                            }
                        }
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

private data class DesktopEventFileTransferDialogState(
    val addresses: List<DesktopEventFileTransferAddress>,
    val selectedAddress: DesktopEventFileTransferAddress,
    val session: DesktopEventFileTransferSession,
    val qrCode: BufferedImage,
    val statusText: String
)

private data class DesktopEventFileTransferResultDialogState(
    val title: String,
    val summary: String,
    val sourcePath: Path?,
    val details: List<String>
) {
    companion object {
        fun downloaded(fileName: String, path: Path, byteCount: Long): DesktopEventFileTransferResultDialogState =
            DesktopEventFileTransferResultDialogState(
                title = "Android Download Complete",
                summary = "Android downloaded the Event File.",
                sourcePath = path,
                details = listOf(
                    "Downloaded file: $fileName",
                    "Bytes sent: $byteCount",
                    "Android imports the downloaded Event File into its race list."
                )
            )

        fun failure(title: String, message: String, path: Path? = null): DesktopEventFileTransferResultDialogState =
            DesktopEventFileTransferResultDialogState(
                title = title,
                summary = message,
                sourcePath = path,
                details = emptyList()
            )
    }
}

private data class DesktopAndroidFileReceiveDialogState(
    val addresses: List<DesktopEventFileTransferAddress>,
    val selectedAddress: DesktopEventFileTransferAddress,
    val session: DesktopAndroidFileReceiveSession,
    val qrCode: BufferedImage,
    val statusText: String
)

private data class DesktopAndroidFileReceiveResultDialogState(
    val title: String,
    val summary: String,
    val savedPath: Path?,
    val details: List<String>
) {
    companion object {
        fun loaded(fileName: String, path: Path, loadedMessage: String): DesktopAndroidFileReceiveResultDialogState =
            DesktopAndroidFileReceiveResultDialogState(
                title = "Android File Received",
                summary = loadedMessage,
                savedPath = path,
                details = listOf(
                    "Received file: $fileName",
                    "A saved copy remains at the path below."
                )
            )

        fun savedOnly(fileName: String, path: Path, reason: String): DesktopAndroidFileReceiveResultDialogState =
            DesktopAndroidFileReceiveResultDialogState(
                title = "Android File Saved",
                summary = "The received file was saved but not loaded.",
                savedPath = path,
                details = listOf(
                    "Received file: $fileName",
                    reason
                )
            )

        fun failedToLoad(fileName: String, path: Path, failure: String): DesktopAndroidFileReceiveResultDialogState =
            DesktopAndroidFileReceiveResultDialogState(
                title = "Android File Saved",
                summary = "The received file was saved, but the desktop app could not load it.",
                savedPath = path,
                details = listOf(
                    "Received file: $fileName",
                    failure
                )
            )

        fun failure(title: String, message: String): DesktopAndroidFileReceiveResultDialogState =
            DesktopAndroidFileReceiveResultDialogState(
                title = title,
                summary = message,
                savedPath = null,
                details = emptyList()
            )
    }
}

@Composable
private fun EventFileTransferDialog(
    state: DesktopEventFileTransferDialogState,
    onAddressSelected: (DesktopEventFileTransferAddress) -> Unit,
    onCopyUrl: () -> Unit,
    onCancel: () -> Unit
) {
    var isAddressMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Send Event to Android") },
        text = {
            Column(
                modifier = Modifier.widthIn(min = 420.dp, max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Scan this code from Android, or enter the URL manually. Use trusted Wi-Fi or a phone hotspot.")
                Image(
                    bitmap = state.qrCode.toComposeImageBitmap(),
                    contentDescription = "Android Event File transfer QR code",
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(260.dp)
                        .height(260.dp),
                    contentScale = ContentScale.Fit
                )
                if (state.addresses.size > 1) {
                    Box {
                        Button(onClick = { isAddressMenuExpanded = true }) {
                            ButtonLabel(state.selectedAddress.label)
                        }
                        DropdownMenu(
                            expanded = isAddressMenuExpanded,
                            onDismissRequest = { isAddressMenuExpanded = false }
                        ) {
                            state.addresses.forEach { address ->
                                DropdownMenuItem(
                                    onClick = {
                                        isAddressMenuExpanded = false
                                        onAddressSelected(address)
                                    }
                                ) {
                                    Text(address.label)
                                }
                            }
                        }
                    }
                } else {
                    Text("Address: ${state.selectedAddress.label}")
                }
                SelectionContainer {
                    Text(
                        state.session.url,
                        style = MaterialTheme.typography.body2
                    )
                }
                Text(state.statusText)
            }
        },
        confirmButton = {
            Button(onClick = onCopyUrl) {
                ButtonLabel("Copy URL")
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
private fun EventFileTransferResultDialog(
    state: DesktopEventFileTransferResultDialogState,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(state.title) },
        text = {
            Column(
                modifier = Modifier.widthIn(min = 420.dp, max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(state.summary)
                state.details.forEach { detail ->
                    Text(detail)
                }
                state.sourcePath?.let { path ->
                    Text("Desktop Event File location:")
                    SelectionContainer {
                        Text(path.toString(), style = MaterialTheme.typography.body2)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                ButtonLabel("OK")
            }
        }
    )
}

@Composable
private fun AndroidFileReceiveResultDialog(
    state: DesktopAndroidFileReceiveResultDialogState,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(state.title) },
        text = {
            Column(
                modifier = Modifier.widthIn(min = 420.dp, max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(state.summary)
                state.details.forEach { detail ->
                    Text(detail)
                }
                state.savedPath?.let { path ->
                    Text("Saved location:")
                    SelectionContainer {
                        Text(path.toString(), style = MaterialTheme.typography.body2)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                ButtonLabel("OK")
            }
        }
    )
}

@Composable
private fun AndroidFileReceiveDialog(
    state: DesktopAndroidFileReceiveDialogState,
    onAddressSelected: (DesktopEventFileTransferAddress) -> Unit,
    onCopyUrl: () -> Unit,
    onCancel: () -> Unit
) {
    var isAddressMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Receive File from Android") },
        text = {
            Column(
                modifier = Modifier.widthIn(min = 420.dp, max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Scan this code from Android, or enter the URL manually. Use trusted Wi-Fi or a phone hotspot.")
                Image(
                    bitmap = state.qrCode.toComposeImageBitmap(),
                    contentDescription = "Android file upload QR code",
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(260.dp)
                        .height(260.dp),
                    contentScale = ContentScale.Fit
                )
                if (state.addresses.size > 1) {
                    Box {
                        Button(onClick = { isAddressMenuExpanded = true }) {
                            ButtonLabel(state.selectedAddress.label)
                        }
                        DropdownMenu(
                            expanded = isAddressMenuExpanded,
                            onDismissRequest = { isAddressMenuExpanded = false }
                        ) {
                            state.addresses.forEach { address ->
                                DropdownMenuItem(
                                    onClick = {
                                        isAddressMenuExpanded = false
                                        onAddressSelected(address)
                                    }
                                ) {
                                    Text(address.label)
                                }
                            }
                        }
                    }
                } else {
                    Text("Address: ${state.selectedAddress.label}")
                }
                SelectionContainer {
                    Text(
                        state.session.url,
                        style = MaterialTheme.typography.body2
                    )
                }
                Text(state.statusText)
            }
        },
        confirmButton = {
            Button(onClick = onCopyUrl) {
                ButtonLabel("Copy URL")
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
private fun AboutRadioOracleDialog(
    isUpdateCheckingEnabled: Boolean,
    updateCheckStatus: DesktopAppUpdateStatus?,
    onCheckForUpdates: () -> Unit,
    onOpenUpdateLink: (String) -> Unit,
    onDismiss: () -> Unit
) {
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
                Text(updateCheckStatusText(isUpdateCheckingEnabled, updateCheckStatus))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = isUpdateCheckingEnabled,
                        onClick = onCheckForUpdates
                    ) {
                        Text("Check for Updates")
                    }
                    if (updateCheckStatus != null) {
                        Button(onClick = { onOpenUpdateLink(DesktopAppUpdateSupport.updatePageUrl) }) {
                            Text("Open Update Link")
                        }
                    }
                }
                if (updateCheckStatus != null) {
                    SelectionContainer {
                        Text(
                            text = DesktopAppUpdateSupport.updatePageUrl,
                            color = Color.DarkGray,
                            fontSize = 13.sp
                        )
                    }
                }
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
private fun RadioOracleUpdateDialog(
    status: DesktopAppUpdateStatus,
    onOpenUpdateLink: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Radio-Oracle Updates") },
        text = {
            SelectionContainer {
                Text(DesktopAppUpdateSupport.dialogMessage(status))
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onOpenUpdateLink()
                    onDismiss()
                }
            ) {
                Text("Open Update Link")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

private fun updateCheckStatusText(
    isUpdateCheckingEnabled: Boolean,
    updateCheckStatus: DesktopAppUpdateStatus?
): String =
    when {
        !isUpdateCheckingEnabled -> "Update checks are disabled in App Settings."
        updateCheckStatus == null -> "Update status has not been checked."
        updateCheckStatus.jdeployUpdatesAvailable == true ->
            "jDeploy reported a Radio-Oracle update is available. Current version: ${updateCheckStatus.currentVersion}."
        updateCheckStatus.jdeployUpdatesAvailable == false ->
            "jDeploy did not report a Radio-Oracle update at launch. Current version: ${updateCheckStatus.currentVersion}."
        updateCheckStatus.launchedByJdeploy ->
            "Radio-Oracle was launched by jDeploy, but update status was not reported."
        else -> "Radio-Oracle was not launched by jDeploy, so update status is not available in this run."
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

private data class ControlRoleCounts(
    val foxes: Int,
    val beacons: Int,
    val spectators: Int
) {
    companion object {
        fun from(types: Iterable<ControlPointType>): ControlRoleCounts {
            var foxes = 0
            var beacons = 0
            var spectators = 0
            types.forEach { type ->
                when (type) {
                    ControlPointType.CONTROL -> foxes += 1
                    ControlPointType.BEACON -> beacons += 1
                    ControlPointType.SEPARATOR -> spectators += 1
                }
            }
            return ControlRoleCounts(foxes = foxes, beacons = beacons, spectators = spectators)
        }
    }
}

private data class SprintLoopControlGroups(
    val slowLoopFoxes: List<EventControlDetails>,
    val fastLoopFoxes: List<EventControlDetails>
) {
    companion object {
        fun from(controls: List<EventControlDetails>): SprintLoopControlGroups {
            val foxes = controls.filter { it.type == ControlPointType.CONTROL }
            val labeledGroups = foxes
                .mapNotNull { control -> control.sprintLoopFromLabel()?.let { control to it } }
                .toMap()
            if (labeledGroups.isNotEmpty() && labeledGroups.size == foxes.size) {
                return SprintLoopControlGroups(
                    slowLoopFoxes = foxes.filter { labeledGroups[it] == SprintLoop.Slow },
                    fastLoopFoxes = foxes.filter { labeledGroups[it] == SprintLoop.Fast }
                )
            }

            val sortedFoxes = foxes.sortedWith(compareBy<EventControlDetails> { it.siCode }.thenBy { it.publicDisplayLabel() })
            val slowFoxes = sortedFoxes.take(5)
            return SprintLoopControlGroups(
                slowLoopFoxes = slowFoxes,
                fastLoopFoxes = sortedFoxes.drop(slowFoxes.size)
            )
        }
    }
}

private enum class SprintLoop {
    Slow,
    Fast
}

private data class SprintControlDisplaySlot(
    val group: Int,
    val order: Int
) : Comparable<SprintControlDisplaySlot> {
    override fun compareTo(other: SprintControlDisplaySlot): Int =
        compareValuesBy(this, other, SprintControlDisplaySlot::group, SprintControlDisplaySlot::order)

    companion object {
        fun forControl(control: EventControlDetails, groups: SprintLoopControlGroups): SprintControlDisplaySlot {
            val publicSlot = control.publicLabel.trim().takeIf { it.isNotEmpty() }?.sprintDisplaySlot()
            if (publicSlot != null) {
                return publicSlot
            }
            return when (control.type) {
                ControlPointType.CONTROL -> {
                    val slowIndex = groups.slowLoopFoxes.indexOfFirst { it.id == control.id }
                    if (slowIndex >= 0) {
                        SprintControlDisplaySlot(0, slowIndex)
                    } else {
                        val fastIndex = groups.fastLoopFoxes.indexOfFirst { it.id == control.id }
                        SprintControlDisplaySlot(2, fastIndex.takeIf { it >= 0 } ?: control.siCode)
                    }
                }
                ControlPointType.SEPARATOR -> SprintControlDisplaySlot(1, control.siCode)
                ControlPointType.BEACON -> SprintControlDisplaySlot(3, control.siCode)
            }
        }
    }
}

private data class ControlStatsItem(
    val label: String,
    val count: Int,
    val isCompliant: Boolean
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

private data class PendingCourseKmlKmzImportWarning(
    val title: String,
    val message: String
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
    ImportGpx,
    ImportControls,
    ImportControlsGpx,
    Export,
    ExportGpx
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

internal data class RetainedCourseAnalysisCourseInfo(
    val encryptedCourseInfo: String,
    val courseInfo: ProtectedCourseInfo
)

internal fun retainedCourseAnalysisCourseInfo(
    projectFile: EventProjectFile,
    currentCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>,
    previousRetainedCourseInfoByCategoryId: Map<String, RetainedCourseAnalysisCourseInfo>
): Map<String, RetainedCourseAnalysisCourseInfo> {
    val currentCategoriesById = projectFile.raceData.categories.associateBy { it.category.id }
    val retained = previousRetainedCourseInfoByCategoryId
        .filter { (categoryId, retainedInfo) ->
            currentCategoriesById[categoryId]
                ?.category
                ?.encryptedCourseInfo
                ?.takeIf { it.isNotBlank() } == retainedInfo.encryptedCourseInfo
        }
        .toMutableMap()
    projectFile.raceData.categories.forEach { categoryData ->
        val encryptedCourseInfo = categoryData.category.encryptedCourseInfo?.takeIf { it.isNotBlank() }
            ?: return@forEach
        val courseInfo = currentCourseInfoByCategoryId[categoryData.category.id]
            ?.takeIf { it.route.size >= 2 }
            ?: return@forEach
        retained[categoryData.category.id] = RetainedCourseAnalysisCourseInfo(
            encryptedCourseInfo = encryptedCourseInfo,
            courseInfo = courseInfo
        )
    }
    return retained
}

internal fun effectiveCourseAnalysisCourseInfoByCategoryId(
    projectFile: EventProjectFile,
    currentCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>,
    retainedCourseInfoByCategoryId: Map<String, RetainedCourseAnalysisCourseInfo>
): Map<String, ProtectedCourseInfo> =
    projectFile.raceData.categories.mapNotNull { categoryData ->
        val categoryId = categoryData.category.id
        currentCourseInfoByCategoryId[categoryId]
            ?.takeIf { it.route.size >= 2 }
            ?.let { return@mapNotNull categoryId to it }
        val encryptedCourseInfo = categoryData.category.encryptedCourseInfo?.takeIf { it.isNotBlank() }
            ?: return@mapNotNull null
        val retainedCourseInfo = retainedCourseInfoByCategoryId[categoryId]
            ?.takeIf { it.encryptedCourseInfo == encryptedCourseInfo }
            ?.courseInfo
            ?.takeIf { it.route.size >= 2 }
            ?: return@mapNotNull null
        categoryId to retainedCourseInfo
    }.toMap()

private data class CourseAnalysisElevationPreparationResult(
    val projectFile: EventProjectFile,
    val protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>,
    val statusText: String
)

private data class CourseKmlKmzElevationProgressUiState(
    val sourceName: String,
    val categoryName: String,
    val completedPointCount: Int,
    val totalPointCount: Int,
    val downloadedPointCount: Int = 0,
    val cachedPointCount: Int = 0,
    val cancelRequested: Boolean = false
)

private data class VenueElevationCacheProgressUiState(
    val venueName: String,
    val completedPointCount: Int,
    val totalPointCount: Int,
    val isLocalFileImport: Boolean = false,
    val estimatedRawBytes: Long? = null,
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

private data class EventSeriesValidationUiState(
    val manifestPath: Path,
    val issues: List<EventSeriesValidationIssue>,
    val errorMessage: String? = null
)

internal data class EventSeriesUiContext(
    val manifestPath: Path,
    val seriesName: String
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
    eventFileWorkingFolder: Path = DesktopEventFileLocations.preferredEventFileDirectory(),
    eventSeriesUiContext: EventSeriesUiContext? = null,
    seriesEventSummaries: List<DesktopEventSeriesEventSummary> = emptyList(),
    seriesStartFairnessSummary: DesktopEventSeriesStartFairnessSummary? = null,
    seriesStartFairnessOptimizationResult: DesktopEventSeriesStartFairnessOptimizationResult? = null,
    eventStartListDrawNumbering: DesktopStartListDrawNumbering? = null,
    seriesCompetitorMatchSummaries: List<DesktopEventSeriesCompetitorMatchSummary> = emptyList(),
    seriesCompetitorIdentityCoverageSummaries: List<DesktopEventSeriesCompetitorIdentityCoverageSummary> = emptyList(),
    eventSeriesValidationState: EventSeriesValidationUiState? = null,
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
    localResultsWebServerUrl: String? = null,
    publishedPublicResultSiteUrl: String? = null,
    printerDiagnostics: DesktopPrinterDiagnostics = DesktopPrinterDiagnostics.from(emptyList()),
    isUpdateCheckingEnabled: Boolean = true,
    cloudflarePagesPublishSettings: DesktopCloudflarePagesPublishSettings = DesktopCloudflarePagesPublishSettings(),
    raceClockTick: Long = 0L,
    onRenameRace: (String) -> Unit = {},
    onUpdateRaceStartDateTime: (String) -> Unit = {},
    onUpdateRaceSettings: (RaceType, RaceLevel, RaceBand, String) -> Unit = { _, _, _, _ -> },
    onUpdateEventFileName: (String) -> Boolean = { false },
    onRenameCategory: (String, String) -> Unit = { _, _ -> },
    onUpdateCategoryGender: (String, Boolean) -> Unit = { _, _ -> },
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
    onUpdateStartDrawSeriesOptimizationLock: (Boolean) -> Unit = {},
    onDrawStartList: (String, StartDrawOptions) -> Unit = { _, _ -> },
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
    onImportControlsRouteGpx: () -> Unit = {},
    onSendRobisLiveResults: () -> Unit = {},
    onSetBackgroundLiveResultSendingEnabled: (Boolean) -> Unit = {},
    onSetReadoutDuplicatePolicy: (EventReadoutDuplicatePolicy) -> Unit = {},
    onSetReadoutAlertSoundEnabled: (Boolean) -> Unit = {},
    onSetAliasesEnabled: (Boolean) -> Unit = {},
    isProtectedCourseOrderUnlocked: Boolean = false,
    protectedIdealOrderByCategoryId: Map<String, String> = emptyMap(),
    protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo> = emptyMap(),
    recentImportReport: DesktopImportReport? = null,
    recentImportCheckpoint: DesktopImportCheckpoint? = null,
    recentActivityLog: List<String> = emptyList(),
    onResolveCachedCourseAnalysisElevations: suspend (String) -> CourseAnalysisElevationPreparationResult? = { null },
    onDownloadMissingCourseAnalysisElevations: suspend (String, DesktopCourseAnalysisSummary) -> CourseAnalysisElevationPreparationResult? = { _, _ -> null },
    onDownloadVenueElevationCache: (String, DesktopVenueElevationBoundingBox?, Double, Double, DesktopVenueElevationCacheSource, String) -> Unit = { _, _, _, _, _, _ -> },
    onOpenVenueElevationCacheFolder: () -> Unit = {},
    onOpenEventFileWorkingFolder: () -> Unit = {},
    onOpenPublishedPublicResultsSite: (String) -> Unit = {},
    onCopyPublishedPublicResultsSite: (String) -> Unit = {},
    elevationCacheRefreshToken: Int = 0,
    onUnlockProtectedCourseOrder: (String) -> Boolean = { false },
    onUpdateProtectedIdealOrder: (String, String) -> Unit = { _, _ -> },
    onUseCalculatedCourseAnalysisRoute: (DesktopCourseCalculatedRouteApplication) -> String = { "" },
    onApplyCourseAnalysisFoxRenumberingOnly: (DesktopCourseWaitRenumbering) -> String = { "" },
    onUpdateCourseAnalyzerSpeedFactor: (Double) -> String = { "" },
    onReadCompetitorSiCardForAddRow: suspend () -> DesktopCompetitorSiCardDraft = {
        error("SI card reader is not configured.")
    },
    onUpdateProtectedControlLocation: (String, String, String) -> String = { _, _, _ -> "" },
    onUpdateProtectedCoursePassword: (String, String, String) -> Boolean = { _, _, _ -> false },
    onSetUpdateCheckingEnabled: (Boolean) -> Unit = {},
    onSetCloudflarePagesPublishSettings: (DesktopCloudflarePagesPublishSettings) -> Boolean = { false },
    onLockProtectedCourseOrder: () -> Unit = {},
    isNavActionEnabled: (DesktopNavAction) -> Boolean = { false },
    disabledNavActionReason: (DesktopNavAction) -> String? = { null },
    onOptimizeSeriesStartFairness: () -> Unit = {},
    onOpenSeriesEvent: (DesktopEventSeriesEventSummary) -> Unit = {},
    onUpdateEventSeriesName: (String) -> Boolean = { false },
    onUpdateEventSeriesFileName: (String) -> Boolean = { false },
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
        var courseAnalysisResult by remember(projectFile?.raceData?.race?.id, protectedCourseInfoByCategoryId) {
            mutableStateOf<DesktopCourseAnalysisSummary?>(null)
        }
        var courseAnalysisApplyStatusText by remember(projectFile?.raceData?.race?.id) {
            mutableStateOf<String?>(null)
        }
        val baseNavigationReadiness = DesktopNavigationReadiness.from(projectFile)
        val hasValidSeriesContext = hasDesktopEventSeriesContext(projectFile, seriesEventSummaries)
        val navigationReadiness = baseNavigationReadiness.copy(hasSeriesContext = hasValidSeriesContext)
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
                    AppTopBar(
                        projectFile = projectFile,
                        eventSeriesUiContext = eventSeriesUiContext.takeIf { hasValidSeriesContext }
                    )
                    Row(modifier = Modifier.weight(1f)) {
                        NavigationRail(
                            navState = navState,
                            navigationReadiness = navigationReadiness,
                            isNavActionEnabled = isNavActionEnabled,
                            disabledNavActionReason = disabledNavActionReason,
                            courseAnalysisResult = courseAnalysisResult.takeIf {
                                navState.selectedSection == DesktopSection.CourseAnalysis
                            },
                            isCourseAnalysisBusy = false,
                            onApplyCalculatedRoute = {
                                val application = courseAnalysisResult?.calculatedRouteApplication ?: return@NavigationRail
                                courseAnalysisApplyStatusText = onUseCalculatedCourseAnalysisRoute(application)
                                courseAnalysisResult = null
                            },
                            onApplyFoxRenumberingOnly = {
                                val renumbering = courseAnalysisResult?.waitRenumbering?.takeIf { it.improvesWait }
                                    ?: return@NavigationRail
                                courseAnalysisApplyStatusText = onApplyCourseAnalysisFoxRenumberingOnly(renumbering)
                                courseAnalysisResult = null
                            },
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
                eventFileWorkingFolder = eventFileWorkingFolder,
                eventSeriesUiContext = eventSeriesUiContext.takeIf { hasValidSeriesContext },
                seriesEventSummaries = seriesEventSummaries,
                seriesStartFairnessSummary = seriesStartFairnessSummary,
                seriesStartFairnessOptimizationResult = seriesStartFairnessOptimizationResult,
                eventStartListDrawNumbering = eventStartListDrawNumbering,
                seriesCompetitorMatchSummaries = seriesCompetitorMatchSummaries,
                seriesCompetitorIdentityCoverageSummaries = seriesCompetitorIdentityCoverageSummaries,
                eventSeriesValidationState = eventSeriesValidationState,
                projectStatusText = projectStatusText,
                                    siReaderState = siReaderState,
                                    onRenameRace = onRenameRace,
                                    onUpdateRaceStartDateTime = onUpdateRaceStartDateTime,
                                    onUpdateRaceSettings = onUpdateRaceSettings,
                                    onUpdateEventFileName = onUpdateEventFileName,
                                    onOpenEventFileWorkingFolder = onOpenEventFileWorkingFolder,
                                    onRenameCategory = onRenameCategory,
                                    onUpdateCategoryGender = onUpdateCategoryGender,
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
                                    onUpdateStartDrawSeriesOptimizationLock = onUpdateStartDrawSeriesOptimizationLock,
                                    onDrawStartList = onDrawStartList,
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
                                    onImportControlsRouteGpx = onImportControlsRouteGpx,
                                    isSendingLiveResults = isSendingLiveResults,
                                    isBackgroundLiveResultSendingEnabled = isBackgroundLiveResultSendingEnabled,
                                    readoutDuplicatePolicy = readoutDuplicatePolicy,
                                    isReadoutAlertSoundEnabled = isReadoutAlertSoundEnabled,
                                    areAliasesEnabled = areAliasesEnabled,
                                    localResultsWebServerUrl = localResultsWebServerUrl,
                                    publishedPublicResultSiteUrl = publishedPublicResultSiteUrl,
                                    printerDiagnostics = printerDiagnostics,
                                    isUpdateCheckingEnabled = isUpdateCheckingEnabled,
                                    cloudflarePagesPublishSettings = cloudflarePagesPublishSettings,
                                    raceClockTick = raceClockTick,
                                    onSendRobisLiveResults = onSendRobisLiveResults,
                                    onSetBackgroundLiveResultSendingEnabled = onSetBackgroundLiveResultSendingEnabled,
                                    onSetReadoutDuplicatePolicy = onSetReadoutDuplicatePolicy,
                                    onSetReadoutAlertSoundEnabled = onSetReadoutAlertSoundEnabled,
                                    onSetAliasesEnabled = onSetAliasesEnabled,
                                    isProtectedCourseOrderUnlocked = isProtectedCourseOrderUnlocked,
                                    protectedIdealOrderByCategoryId = protectedIdealOrderByCategoryId,
                                    protectedCourseInfoByCategoryId = protectedCourseInfoByCategoryId,
                                    courseAnalysisResult = courseAnalysisResult,
                                    onCourseAnalysisResultChange = { courseAnalysisResult = it },
                                    courseAnalysisApplyStatusText = courseAnalysisApplyStatusText,
                                    onCourseAnalysisApplyStatusTextChange = { courseAnalysisApplyStatusText = it },
                                    recentImportReport = recentImportReport,
                                    recentImportCheckpoint = recentImportCheckpoint,
                                    recentActivityLog = recentActivityLog,
                                    onRecalculateResults = onRecalculateResults,
                                    onResolveCachedCourseAnalysisElevations = onResolveCachedCourseAnalysisElevations,
                                    onDownloadMissingCourseAnalysisElevations = onDownloadMissingCourseAnalysisElevations,
                                    onDownloadVenueElevationCache = onDownloadVenueElevationCache,
                                    onOpenVenueElevationCacheFolder = onOpenVenueElevationCacheFolder,
                                    onOpenPublishedPublicResultsSite = onOpenPublishedPublicResultsSite,
                                    onCopyPublishedPublicResultsSite = onCopyPublishedPublicResultsSite,
                                    elevationCacheRefreshToken = elevationCacheRefreshToken,
                                    onUnlockProtectedCourseOrder = onUnlockProtectedCourseOrder,
                                    onUpdateProtectedIdealOrder = onUpdateProtectedIdealOrder,
                                    onUseCalculatedCourseAnalysisRoute = onUseCalculatedCourseAnalysisRoute,
                                    onApplyCourseAnalysisFoxRenumberingOnly = onApplyCourseAnalysisFoxRenumberingOnly,
                                    onUpdateCourseAnalyzerSpeedFactor = onUpdateCourseAnalyzerSpeedFactor,
                                    onReadCompetitorSiCardForAddRow = onReadCompetitorSiCardForAddRow,
                                    onUpdateProtectedControlLocation = onUpdateProtectedControlLocation,
                                    onUpdateProtectedCoursePassword = onUpdateProtectedCoursePassword,
                                    onSetUpdateCheckingEnabled = onSetUpdateCheckingEnabled,
                                    onSetCloudflarePagesPublishSettings = onSetCloudflarePagesPublishSettings,
                                    isNavActionEnabled = isNavActionEnabled,
                                    onOptimizeSeriesStartFairness = onOptimizeSeriesStartFairness,
                                    onOpenSeriesEvent = onOpenSeriesEvent,
                                    onUpdateEventSeriesName = onUpdateEventSeriesName,
                                    onUpdateEventSeriesFileName = onUpdateEventSeriesFileName,
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
private fun AppTopBar(projectFile: EventProjectFile?, eventSeriesUiContext: EventSeriesUiContext?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (eventSeriesUiContext == null) 56.dp else 68.dp)
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
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            eventSeriesUiContext?.let { context ->
                Text(
                    text = desktopTopBarSeriesText(context),
                    color = DesktopPalette.SeriesNavigation,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = desktopTopBarEventText(projectFile),
                color = DesktopPalette.White,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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
        ?.let { "Event: $it" }
        ?: "No event file loaded"

internal fun desktopTopBarSeriesText(seriesContext: EventSeriesUiContext): String =
    "Series: ${seriesContext.seriesName.ifBlank { "Untitled Series" }}"

internal fun desktopParentSeriesText(seriesContext: EventSeriesUiContext?): String? =
    seriesContext?.seriesName
        ?.ifBlank { "Untitled Series" }
        ?.let { "Parent Series: $it" }

internal fun desktopEventFileFolderText(eventFilePath: Path?, workingFolder: Path): String {
    val directory = eventFilePath?.parent ?: workingFolder
    val directoryText = directory.toAbsolutePath().normalize().toString()
    return if (eventFilePath == null) {
        "Event File Folder: $directoryText (first save default)"
    } else {
        "Event File Folder: $directoryText"
    }
}

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

@Composable
private fun navigationItemButtonColors(
    workflow: DesktopWorkflow,
    action: DesktopNavAction?,
    useSeriesNavigationColor: Boolean = workflow == DesktopWorkflow.Series
) =
    when {
        action == DesktopNavAction.SaveEventFile -> saveEventButtonColors()
        useSeriesNavigationColor -> seriesNavigationButtonColors()
        else -> ButtonDefaults.buttonColors()
    }

@Composable
private fun workflowButtonColors(workflow: DesktopWorkflow, isBypassedDisabled: Boolean) =
    ButtonDefaults.buttonColors(
        backgroundColor = if (workflow == DesktopWorkflow.Series) {
            DesktopPalette.SeriesNavigation
        } else {
            DesktopPalette.PrimaryVariant
        },
        contentColor = if (workflow == DesktopWorkflow.Series) {
            DesktopPalette.Black
        } else {
            DesktopPalette.White
        },
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

@Composable
private fun seriesNavigationButtonColors() =
    ButtonDefaults.buttonColors(
        backgroundColor = DesktopPalette.SeriesNavigation,
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
    courseAnalysisResult: DesktopCourseAnalysisSummary?,
    isCourseAnalysisBusy: Boolean,
    onApplyCalculatedRoute: () -> Unit,
    onApplyFoxRenumberingOnly: () -> Unit,
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
                            colors = navigationItemButtonColors(
                                navState.workflow,
                                item.action,
                                DesktopNavigation.usesSeriesNavigationColor(navState, item)
                            )
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
            CourseAnalysisNavigationActions(
                result = courseAnalysisResult,
                isBusy = isCourseAnalysisBusy,
                onApplyCalculatedRoute = onApplyCalculatedRoute,
                onApplyFoxRenumberingOnly = onApplyFoxRenumberingOnly
            )
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

@Composable
private fun CourseAnalysisNavigationActions(
    result: DesktopCourseAnalysisSummary?,
    isBusy: Boolean,
    onApplyCalculatedRoute: () -> Unit,
    onApplyFoxRenumberingOnly: () -> Unit
) {
    if (result == null) {
        return
    }
    Divider(color = DesktopPalette.LightGrey, modifier = Modifier.padding(bottom = 2.dp))
    DisabledReasonTooltip(
        reason = if (isBusy) null else calculatedRouteApplyDisabledReason(result),
        placement = DisabledReasonTooltipPlacement.RightOfCursor
    ) {
        Button(
            onClick = onApplyCalculatedRoute,
            enabled = result.calculatedRouteApplication != null && !isBusy,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 34.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = "Apply Calculated Route",
                fontSize = 13.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    DisabledReasonTooltip(
        reason = if (isBusy) null else foxRenumberingApplyDisabledReason(result),
        placement = DisabledReasonTooltipPlacement.RightOfCursor
    ) {
        Button(
            onClick = onApplyFoxRenumberingOnly,
            enabled = result.waitRenumbering?.improvesWait == true && !isBusy,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 34.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = "Apply Fox Renumbering Only",
                fontSize = 13.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    Divider(color = DesktopPalette.LightGrey, modifier = Modifier.padding(top = 2.dp))
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
            .height(64.dp)
            .background(Color(0xFFE7E7E7))
            .border(2.dp, DesktopPalette.Disconnected)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DesktopWorkflow.bottomBarEntries(navigationReadiness).forEach { workflow ->
            val isSelected = workflow == selectedWorkflow
            val isEnabled = DesktopNavigation.isWorkflowEnabled(workflow, navigationReadiness)
            val canLongClickOverride = DesktopNavigation.canLongClickOverrideDisabledWorkflow(workflow, navigationReadiness)
            val isBypassedDisabled = bypassedDisabledNavigation?.workflow == workflow
            Box(
                modifier = Modifier
                    .fillMaxHeight()
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
                            .fillMaxHeight()
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) DesktopPalette.Black else DesktopPalette.LightGrey
                            ),
                        colors = workflowButtonColors(workflow, isBypassedDisabled),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = DesktopWorkflow.bottomBarLabel(workflow, navigationReadiness),
                            fontSize = 12.sp,
                            lineHeight = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
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
    eventFileWorkingFolder: Path,
    eventSeriesUiContext: EventSeriesUiContext?,
    seriesEventSummaries: List<DesktopEventSeriesEventSummary>,
    seriesStartFairnessSummary: DesktopEventSeriesStartFairnessSummary?,
    seriesStartFairnessOptimizationResult: DesktopEventSeriesStartFairnessOptimizationResult?,
    eventStartListDrawNumbering: DesktopStartListDrawNumbering?,
    seriesCompetitorMatchSummaries: List<DesktopEventSeriesCompetitorMatchSummary>,
    seriesCompetitorIdentityCoverageSummaries: List<DesktopEventSeriesCompetitorIdentityCoverageSummary>,
    eventSeriesValidationState: EventSeriesValidationUiState?,
    projectStatusText: String,
    siReaderState: DesktopSiReaderUiState,
    onRenameRace: (String) -> Unit,
    onUpdateRaceStartDateTime: (String) -> Unit,
    onUpdateRaceSettings: (RaceType, RaceLevel, RaceBand, String) -> Unit,
    onUpdateEventFileName: (String) -> Boolean,
    onOpenEventFileWorkingFolder: () -> Unit,
    onRenameCategory: (String, String) -> Unit,
    onUpdateCategoryGender: (String, Boolean) -> Unit,
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
    onUpdateStartDrawSeriesOptimizationLock: (Boolean) -> Unit,
    onDrawStartList: (String, StartDrawOptions) -> Unit,
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
    onImportControlsRouteGpx: () -> Unit,
    isSendingLiveResults: Boolean,
    isBackgroundLiveResultSendingEnabled: Boolean,
    readoutDuplicatePolicy: EventReadoutDuplicatePolicy,
    isReadoutAlertSoundEnabled: Boolean,
    areAliasesEnabled: Boolean,
    localResultsWebServerUrl: String?,
    publishedPublicResultSiteUrl: String?,
    printerDiagnostics: DesktopPrinterDiagnostics,
    isUpdateCheckingEnabled: Boolean,
    cloudflarePagesPublishSettings: DesktopCloudflarePagesPublishSettings,
    raceClockTick: Long,
    onSendRobisLiveResults: () -> Unit,
    onSetBackgroundLiveResultSendingEnabled: (Boolean) -> Unit,
    onSetReadoutDuplicatePolicy: (EventReadoutDuplicatePolicy) -> Unit,
    onSetReadoutAlertSoundEnabled: (Boolean) -> Unit,
    onSetAliasesEnabled: (Boolean) -> Unit,
    isProtectedCourseOrderUnlocked: Boolean,
    protectedIdealOrderByCategoryId: Map<String, String>,
    protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>,
    courseAnalysisResult: DesktopCourseAnalysisSummary?,
    onCourseAnalysisResultChange: (DesktopCourseAnalysisSummary?) -> Unit,
    courseAnalysisApplyStatusText: String?,
    onCourseAnalysisApplyStatusTextChange: (String?) -> Unit,
    recentImportReport: DesktopImportReport?,
    recentImportCheckpoint: DesktopImportCheckpoint?,
    recentActivityLog: List<String>,
    onResolveCachedCourseAnalysisElevations: suspend (String) -> CourseAnalysisElevationPreparationResult?,
    onDownloadMissingCourseAnalysisElevations: suspend (String, DesktopCourseAnalysisSummary) -> CourseAnalysisElevationPreparationResult?,
    onDownloadVenueElevationCache: (String, DesktopVenueElevationBoundingBox?, Double, Double, DesktopVenueElevationCacheSource, String) -> Unit,
    onOpenVenueElevationCacheFolder: () -> Unit,
    onOpenPublishedPublicResultsSite: (String) -> Unit,
    onCopyPublishedPublicResultsSite: (String) -> Unit,
    elevationCacheRefreshToken: Int,
    onUnlockProtectedCourseOrder: (String) -> Boolean,
    onUpdateProtectedIdealOrder: (String, String) -> Unit,
    onUseCalculatedCourseAnalysisRoute: (DesktopCourseCalculatedRouteApplication) -> String,
    onApplyCourseAnalysisFoxRenumberingOnly: (DesktopCourseWaitRenumbering) -> String,
    onUpdateCourseAnalyzerSpeedFactor: (Double) -> String,
    onReadCompetitorSiCardForAddRow: suspend () -> DesktopCompetitorSiCardDraft,
    onUpdateProtectedControlLocation: (String, String, String) -> String,
    onUpdateProtectedCoursePassword: (String, String, String) -> Boolean,
    onSetUpdateCheckingEnabled: (Boolean) -> Unit,
    onSetCloudflarePagesPublishSettings: (DesktopCloudflarePagesPublishSettings) -> Boolean,
    isNavActionEnabled: (DesktopNavAction) -> Boolean,
    onOptimizeSeriesStartFairness: () -> Unit,
    onOpenSeriesEvent: (DesktopEventSeriesEventSummary) -> Unit,
    onUpdateEventSeriesName: (String) -> Boolean,
    onUpdateEventSeriesFileName: (String) -> Boolean,
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
        if (section == DesktopSection.PublicResultsSite) {
            PublicResultsSiteWorkflowPanel()
        }
        if (section == DesktopSection.PublicResultsLink) {
            PublicResultsSiteLinkPanel(
                publishedUrl = publishedPublicResultSiteUrl,
                onOpenUrl = onOpenPublishedPublicResultsSite,
                onCopyUrl = onCopyPublishedPublicResultsSite
            )
        }
        if (section == DesktopSection.Races && projectFile != null) {
            RaceDetailsPanel(
                details = EventRaceDetails.from(projectFile.raceData.race),
                eventFilePath = eventFilePath,
                eventFileWorkingFolder = eventFileWorkingFolder,
                parentSeriesText = desktopParentSeriesText(eventSeriesUiContext),
                onRenameRace = onRenameRace,
                onUpdateRaceStartDateTime = onUpdateRaceStartDateTime,
                onUpdateRaceSettings = onUpdateRaceSettings,
                onUpdateEventFileName = onUpdateEventFileName,
                onOpenEventFileWorkingFolder = onOpenEventFileWorkingFolder
            )
        }
        if (section == DesktopSection.Categories && projectFile != null) {
            CategoryDetailsPanel(
                categories = EventCategoryDetails.from(projectFile.raceData, useAliases = areAliasesEnabled),
                controls = EventControlDetails.from(projectFile.raceData),
                onRenameCategory = onRenameCategory,
                onUpdateCategoryGender = onUpdateCategoryGender,
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
                eventFilePath = eventFilePath,
                isUnlocked = isProtectedCourseOrderUnlocked,
                protectedIdealOrderByCategoryId = protectedIdealOrderByCategoryId,
                protectedCourseInfoByCategoryId = protectedCourseInfoByCategoryId,
                analysisResult = courseAnalysisResult,
                onAnalysisResultChange = onCourseAnalysisResultChange,
                applyStatusText = courseAnalysisApplyStatusText,
                onApplyStatusTextChange = onCourseAnalysisApplyStatusTextChange,
                onResolveCachedElevations = onResolveCachedCourseAnalysisElevations,
                onDownloadMissingElevations = onDownloadMissingCourseAnalysisElevations,
                onUnlock = onUnlockProtectedCourseOrder,
                onUpdateSpeedFactor = onUpdateCourseAnalyzerSpeedFactor
            )
        }
        if (section == DesktopSection.KmlMoveCourse) {
            KmlToolsPanel()
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
                seriesStartFairnessSummary = seriesStartFairnessSummary,
                eventStartListDrawNumbering = eventStartListDrawNumbering,
                onUpdateStartDrawSettings = onUpdateStartDrawSettings,
                onUpdateStartDrawSeriesOptimizationLock = onUpdateStartDrawSeriesOptimizationLock,
                onDrawStartList = onDrawStartList
            )
        }
        if (section == DesktopSection.SeriesEvents) {
            EventSeriesEventsPanel(
                summaries = seriesEventSummaries,
                onOpenEvent = onOpenSeriesEvent
            )
        }
        if (section == DesktopSection.SeriesStartFairness) {
            EventSeriesStartFairnessPanel(
                summary = seriesStartFairnessSummary,
                optimizationResult = seriesStartFairnessOptimizationResult,
                onOptimizeStartFairness = onOptimizeSeriesStartFairness
            )
        }
        if (section == DesktopSection.SeriesValidation) {
            EventSeriesValidationPanel(
                state = eventSeriesValidationState,
                onValidate = { onNavAction(DesktopNavAction.ValidateEventSeries) }
            )
        }
        if (section == DesktopSection.SeriesCompetitorMatching) {
            EventSeriesCompetitorMatchingPanel(
                summaries = seriesCompetitorMatchSummaries,
                identityCoverageSummaries = seriesCompetitorIdentityCoverageSummaries
            )
        }
        if (section == DesktopSection.SeriesSettings) {
            EventSeriesSettingsPanel(
                seriesContext = eventSeriesUiContext,
                onUpdateSeriesName = onUpdateEventSeriesName,
                onUpdateSeriesFileName = onUpdateEventSeriesFileName
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
        if (section == DesktopSection.LiveResultsOverview) {
            LiveResultsOverviewPanel()
        }
        if (section == DesktopSection.LocalResultsWebServer) {
            LocalResultsWebServerPanel(
                localResultsWebServerUrl = localResultsWebServerUrl
            )
        }
        if (section == DesktopSection.RobisLiveResults) {
            RobisLiveResultsPanel(
                diagnostics = DesktopProjectDiagnostics.from(
                    projectFile,
                    protectedCourseInfoByCategoryId.takeIf { isProtectedCourseOrderUnlocked } ?: emptyMap()
                ),
                isSendingLiveResults = isSendingLiveResults,
                isBackgroundLiveResultSendingEnabled = isBackgroundLiveResultSendingEnabled,
                onSendRobisLiveResults = onSendRobisLiveResults,
                onSetBackgroundLiveResultSendingEnabled = onSetBackgroundLiveResultSendingEnabled
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
                isUpdateCheckingEnabled = isUpdateCheckingEnabled,
                cloudflarePagesPublishSettings = cloudflarePagesPublishSettings,
                isCourseDataUnlocked = isProtectedCourseOrderUnlocked,
                onSetUpdateCheckingEnabled = onSetUpdateCheckingEnabled,
                onSetCloudflarePagesPublishSettings = onSetCloudflarePagesPublishSettings,
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
                    color = DesktopPalette.Error,
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

/** Explains the separate live-results paths. */
@Composable
private fun LiveResultsOverviewPanel() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Choose Local Web Server or ROBIS from the Live Results menu.",
            color = DesktopPalette.Black,
            fontSize = 14.sp
        )
    }
}

/** Shows local public-results web server status and actions. */
@Composable
private fun LocalResultsWebServerPanel(
    localResultsWebServerUrl: String?
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DetailRow("Web server", localResultsWebServerUrl ?: "Stopped")
        localResultsWebServerUrl?.let { url ->
            if (isShareableLocalResultsWebServerUrl(url)) {
                val qrCode = remember(url) {
                    desktopEventFileTransferQrCode(url, size = 320)
                }
                Image(
                    bitmap = qrCode.toComposeImageBitmap(),
                    contentDescription = "Local results web server QR code",
                    modifier = Modifier
                        .width(220.dp)
                        .height(220.dp)
                        .align(Alignment.Start),
                    contentScale = ContentScale.Fit
                )
            }
            SelectionContainer {
                Text(
                    text = url,
                    color = DesktopPalette.Primary,
                    fontSize = 14.sp,
                    textDecoration = TextDecoration.Underline,
                    softWrap = true
                )
            }
        }
    }
}

internal fun isShareableLocalResultsWebServerUrl(url: String): Boolean =
    runCatching {
        val host = URI(url).host?.lowercase()?.trim('[', ']') ?: return@runCatching false
        host !in setOf("127.0.0.1", "localhost", "::1", "0:0:0:0:0:0:0:1")
    }.getOrDefault(false)

/** Shows ROBIS result-sending settings. */
@Composable
private fun RobisLiveResultsPanel(
    diagnostics: DesktopProjectDiagnostics,
    isSendingLiveResults: Boolean,
    isBackgroundLiveResultSendingEnabled: Boolean,
    onSendRobisLiveResults: () -> Unit,
    onSetBackgroundLiveResultSendingEnabled: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DetailRow("ROBIS results", diagnostics.liveResultPlanText)
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
    isUpdateCheckingEnabled: Boolean,
    cloudflarePagesPublishSettings: DesktopCloudflarePagesPublishSettings,
    isCourseDataUnlocked: Boolean,
    onSetUpdateCheckingEnabled: (Boolean) -> Unit,
    onSetCloudflarePagesPublishSettings: (DesktopCloudflarePagesPublishSettings) -> Boolean,
    onUpdateCoursePassword: (String, String, String) -> Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppSettingsSection("Updates") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = isUpdateCheckingEnabled,
                    onCheckedChange = onSetUpdateCheckingEnabled
                )
                Text(
                    text = "Check for Radio-Oracle Updates",
                    color = DesktopPalette.Black,
                    fontSize = 13.sp
                )
            }
            Text(
                text = if (isUpdateCheckingEnabled) {
                    "Radio-Oracle can show jDeploy-reported app update notices and provide the update link."
                } else {
                    "Radio-Oracle will not show jDeploy-reported app update notices."
                },
                color = Color.DarkGray,
                fontSize = 13.sp
            )
        }
        AppSettingsSection("Cloudflare Pages publishing") {
            CloudflarePagesPublishSettingsPanel(
                settings = cloudflarePagesPublishSettings,
                onSave = onSetCloudflarePagesPublishSettings
            )
        }
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
private fun CloudflarePagesPublishSettingsPanel(
    settings: DesktopCloudflarePagesPublishSettings,
    onSave: (DesktopCloudflarePagesPublishSettings) -> Boolean
) {
    var projectNameDraft by remember(settings) { mutableStateOf(settings.projectName) }
    var branchDraft by remember(settings) { mutableStateOf(settings.branch) }
    var accountIdDraft by remember(settings) { mutableStateOf(settings.accountId) }
    var apiTokenDraft by remember(settings) { mutableStateOf(settings.apiToken) }
    var saveConfirmationText by remember { mutableStateOf<String?>(null) }
    val savedSettings = settings.normalized()
    val rawDraftSettings = DesktopCloudflarePagesPublishSettings(
        projectName = projectNameDraft,
        branch = branchDraft,
        accountId = accountIdDraft,
        apiToken = apiTokenDraft
    )
    val draftSettings = rawDraftSettings.normalized()
    val disabledReason = cloudflarePagesSettingsDisabledReason(rawDraftSettings, draftSettings, savedSettings)
    fun submitSettings() {
        if (disabledReason == null && onSave(draftSettings)) {
            saveConfirmationText = "Cloudflare Pages settings saved."
        }
    }
    fun clearSaveConfirmation() {
        saveConfirmationText = null
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = projectNameDraft,
                onValueChange = {
                    projectNameDraft = it
                    clearSaveConfirmation()
                },
                label = { Text("Pages project") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .commitOnEnter(::submitSettings)
            )
            TextField(
                value = branchDraft,
                onValueChange = {
                    branchDraft = it
                    clearSaveConfirmation()
                },
                label = { Text("Branch") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .commitOnEnter(::submitSettings)
            )
        }
        TextField(
            value = accountIdDraft,
            onValueChange = {
                accountIdDraft = it
                clearSaveConfirmation()
            },
            label = { Text("Account ID") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .commitOnEnter(::submitSettings)
        )
        TextField(
            value = apiTokenDraft,
            onValueChange = {
                apiTokenDraft = it
                clearSaveConfirmation()
            },
            label = { Text("API token") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .commitOnEnter(::submitSettings)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DisabledReasonTooltip(disabledReason) {
                Button(
                    onClick = ::submitSettings,
                    enabled = disabledReason == null
                ) {
                    ButtonLabel("Save Cloudflare Settings")
                }
            }
            saveConfirmationText?.let { confirmation ->
                Text(
                    text = confirmation,
                    color = DesktopPalette.Connected,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

private fun cloudflarePagesSettingsDisabledReason(
    rawDraftSettings: DesktopCloudflarePagesPublishSettings,
    draftSettings: DesktopCloudflarePagesPublishSettings,
    savedSettings: DesktopCloudflarePagesPublishSettings
): String? =
    when {
        rawDraftSettings.projectName.isBlank() -> "Enter a Cloudflare Pages project name."
        rawDraftSettings.branch.isBlank() -> "Enter a Cloudflare Pages branch."
        draftSettings == savedSettings -> "Cloudflare Pages publishing settings are already saved."
        else -> null
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
    fun submitPasswordChange() {
        if (projectFile != null && canSubmit && onUpdateCoursePassword(oldPasswordDraft, newPasswordDraft, confirmPasswordDraft)) {
            oldPasswordDraft = ""
            newPasswordDraft = ""
            confirmPasswordDraft = ""
        }
    }

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
                    modifier = Modifier
                        .width(190.dp)
                        .commitOnEnter(::submitPasswordChange)
                )
            }
            TextField(
                value = newPasswordDraft,
                onValueChange = { newPasswordDraft = it },
                label = { Text(if (hasCoursePassword) "New Event Password" else "Event Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                enabled = projectFile != null,
                modifier = Modifier
                    .width(190.dp)
                    .commitOnEnter(::submitPasswordChange)
            )
            TextField(
                value = confirmPasswordDraft,
                onValueChange = { confirmPasswordDraft = it },
                label = { Text("Confirm") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                enabled = projectFile != null,
                modifier = Modifier
                    .width(190.dp)
                    .commitOnEnter(::submitPasswordChange)
            )
            DisabledReasonTooltip(coursePasswordSubmitDisabledReason(projectFile, hasCoursePassword, oldPasswordDraft, newPasswordDraft, confirmPasswordDraft)) {
                Button(
                    onClick = ::submitPasswordChange,
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
    seriesStartFairnessSummary: DesktopEventSeriesStartFairnessSummary?,
    eventStartListDrawNumbering: DesktopStartListDrawNumbering?,
    onUpdateStartDrawSettings: (String, StartDrawOptions) -> Unit,
    onUpdateStartDrawSeriesOptimizationLock: (Boolean) -> Unit,
    onDrawStartList: (String, StartDrawOptions) -> Unit
) {
    val horizontalScrollState = rememberScrollState()
    val tableWidth = fixedTableWidth(StartListTableColumns)
    val settings = details.settings
    var intervalDraft by remember(settings.intervalSeconds) { mutableStateOf(settings.intervalText) }
    var clubHandling by remember(settings.options.clubHandling) { mutableStateOf(settings.options.clubHandling) }
    var startersPerStartTime by remember(settings.options.startersPerStartTime) {
        mutableStateOf(settings.options.startersPerStartTime)
    }
    var startGroupMode by remember(settings.options.startGroupMode) {
        mutableStateOf(settings.options.forEventStartListGeneration().startGroupMode)
    }
    val startListLocked = settings.lockedForSeriesOptimization
    fun startDrawOptions(
        clubHandlingValue: StartDrawClubHandling = clubHandling,
        startersPerStartTimeValue: Int = startersPerStartTime,
        startGroupModeValue: StartDrawStartGroupMode = startGroupMode
    ): StartDrawOptions =
        StartDrawOptions(
            clubHandling = clubHandlingValue,
            startersPerStartTime = startersPerStartTimeValue,
            seed = settings.options.seed,
            startGroupMode = startGroupModeValue
        )
    fun persistSettingsIfIntervalIsValid(
        intervalValue: String,
        options: StartDrawOptions,
        fallbackToSavedInterval: Boolean = false
    ) {
        if (isValidStartListInterval(intervalValue)) {
            onUpdateStartDrawSettings(intervalValue, options)
        } else if (fallbackToSavedInterval) {
            onUpdateStartDrawSettings(settings.intervalText, options)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = intervalDraft,
                enabled = !startListLocked,
                onValueChange = {
                    intervalDraft = it
                    persistSettingsIfIntervalIsValid(it, startDrawOptions())
                },
                label = { Text("Interval") },
                modifier = Modifier
                    .width(132.dp)
                    .commitOnEnter {
                        persistSettingsIfIntervalIsValid(intervalDraft, startDrawOptions())
                    }
            )
            EnumPicker(
                selectedValue = clubHandling,
                values = StartDrawClubHandling.entries,
                label = StartDrawClubHandling::toDisplayLabel,
                onValueSelected = {
                    clubHandling = it
                    persistSettingsIfIntervalIsValid(
                        intervalDraft,
                        startDrawOptions(clubHandlingValue = it),
                        fallbackToSavedInterval = true
                    )
                },
                modifier = Modifier.width(190.dp),
                enabled = !startListLocked
            )
            EnumPicker(
                selectedValue = startersPerStartTime,
                values = (StartDrawOptions.MIN_STARTERS_PER_START_TIME..StartDrawOptions.MAX_STARTERS_PER_START_TIME).toList(),
                label = { "$it per time" },
                onValueSelected = {
                    startersPerStartTime = it
                    persistSettingsIfIntervalIsValid(
                        intervalDraft,
                        startDrawOptions(startersPerStartTimeValue = it),
                        fallbackToSavedInterval = true
                    )
                },
                modifier = Modifier.width(132.dp),
                enabled = !startListLocked
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EnumPicker(
                selectedValue = startGroupMode,
                values = listOf(StartDrawStartGroupMode.DISABLED, StartDrawStartGroupMode.PREFERRED_THIRDS),
                label = StartDrawStartGroupMode::toDisplayLabel,
                onValueSelected = {
                    startGroupMode = it
                    persistSettingsIfIntervalIsValid(
                        intervalDraft,
                        startDrawOptions(startGroupModeValue = it),
                        fallbackToSavedInterval = true
                    )
                },
                modifier = Modifier.width(190.dp),
                enabled = !startListLocked
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                enabled = !startListLocked,
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
            eventStartListDrawNumbering?.let { numbering ->
                Text(
                    text = "List #${numbering.orderNumber}",
                    color = DesktopPalette.Black,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = settings.lockedForSeriesOptimization,
                onCheckedChange = onUpdateStartDrawSeriesOptimizationLock
            )
            Text(
                text = "Lock this start list",
                color = DesktopPalette.Black,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        DetailHeaderRow(listOf("Scheduled", "No start time"))
        DetailGridRow(listOf(details.scheduledCount.toString(), details.unscheduledCount.toString()))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.width(210.dp)) {
                FairnessScoreBlock(
                    label = "Event Start Fairness Score",
                    score = details.quality.score,
                    color = details.quality.severity.toStartListColor()
                )
                Text(
                    text = details.quality.summary,
                    color = details.quality.severity.toStartListColor(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            seriesStartFairnessSummary?.let { summary ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.width(230.dp)) {
                    FairnessScoreBlock(
                        label = "Series Start Fairness Score",
                        score = summary.fairnessNumber,
                        color = summary.seriesStartFairnessNumberColor()
                    )
                    Text(
                        text = summary.seriesStartFairnessStatus(null).label,
                        color = summary.seriesStartFairnessStatusColor(null),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
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
        FixedTableText(row.startSequenceText, StartListTableColumns[0].width, rowColor)
        FixedTableText(row.startTimeText, StartListTableColumns[1].width, rowColor)
        FixedTableText(row.competitorName, StartListTableColumns[2].width, rowColor)
        FixedTableText(row.categoryName, StartListTableColumns[3].width, rowColor)
        FixedTableText(row.siNumberText, StartListTableColumns[4].width, rowColor)
    }
}

/** Shows the manifest-owned Event Files in the active Event Series. */
@Composable
private fun EventSeriesEventsPanel(
    summaries: List<DesktopEventSeriesEventSummary>,
    onOpenEvent: (DesktopEventSeriesEventSummary) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Add Event stays in the left menu; this panel only contains row-specific Open actions.
        if (summaries.isEmpty()) {
            Text(
                text = "No Event Series manifest was found for this Event File.",
                color = DesktopPalette.Black,
                fontSize = 13.sp
            )
            return@Column
        }
        DetailHeaderRow(listOf("Events", "Missing files"))
        DetailGridRow(
            listOf(
                summaries.size.toString(),
                summaries.count { !it.exists }.toString()
            )
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            EventSeriesEventHeaderRow()
            summaries.forEach { summary ->
                EventSeriesEventRow(summary, onOpenEvent)
            }
        }
    }
}

/** Shows the latest validation result for the active Event Series manifest. */
@Composable
private fun EventSeriesValidationPanel(
    state: EventSeriesValidationUiState?,
    onValidate: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // Keep validation as a panel-local action because this screen owns the results display.
            // Do not also add a left-menu action for the same command.
            Button(onClick = onValidate) {
                ButtonLabel("Validate Series")
            }
            Text(
                text = state?.manifestPath?.fileName?.toString() ?: "No validation results",
                color = DesktopPalette.Disconnected,
                fontSize = 13.sp
            )
        }

        if (state == null) {
            Text(
                text = "No validation results.",
                color = DesktopPalette.Black,
                fontSize = 13.sp
            )
            return@Column
        }

        val errorMessage = state.errorMessage
        if (errorMessage != null) {
            Text(
                text = "Validation failed: $errorMessage",
                color = DesktopPalette.Error,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            return@Column
        }

        val errorCount = state.issues.count { it.severity == EventSeriesIssueSeverity.ERROR }
        val warningCount = state.issues.count { it.severity == EventSeriesIssueSeverity.WARNING }
        DetailHeaderRow(listOf("Errors", "Warnings", "Total issues"))
        DetailGridRow(
            listOf(
                errorCount.toString(),
                warningCount.toString(),
                state.issues.size.toString()
            )
        )

        if (state.issues.isEmpty()) {
            Text(
                text = "No issues found.",
                color = DesktopPalette.PrimaryVariant,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                EventSeriesValidationHeaderRow()
                state.issues.forEach { issue ->
                    EventSeriesValidationIssueRow(issue)
                }
            }
        }
    }
}

@Composable
private fun EventSeriesValidationHeaderRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Severity", modifier = Modifier.width(96.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("Series Event", modifier = Modifier.width(168.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("Issue", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun EventSeriesValidationIssueRow(issue: EventSeriesValidationIssue) {
    val color = when (issue.severity) {
        EventSeriesIssueSeverity.ERROR -> DesktopPalette.Error
        EventSeriesIssueSeverity.WARNING -> DesktopPalette.Warning
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = issue.severity.name.lowercase().replaceFirstChar { it.uppercase() },
            modifier = Modifier.width(96.dp),
            color = color,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = issue.seriesEventId.orEmpty(),
            modifier = Modifier.width(168.dp),
            color = DesktopPalette.Disconnected,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = issue.message,
            modifier = Modifier.weight(1f),
            color = DesktopPalette.Black,
            fontSize = 13.sp
        )
    }
}

/** Summarizes generated start-list fairness across all Event Files in the active Event Series. */
@Composable
private fun EventSeriesStartFairnessPanel(
    summary: DesktopEventSeriesStartFairnessSummary?,
    optimizationResult: DesktopEventSeriesStartFairnessOptimizationResult?,
    onOptimizeStartFairness: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Reviews generated start lists across all manifest-listed events in this series.",
            color = DesktopPalette.Black,
            fontSize = 13.sp
        )
        Text(
            text = "Each generated start list is split by competitor order into early, middle, and late thirds. Competitors are compared by SI number, bib number, or call sign; start/order numbers are not identity keys.",
            color = DesktopPalette.Disconnected,
            fontSize = 13.sp
        )
        summary?.let {
            Text(
                text = "History is shown left to right by ${it.historyOrderDescription}. Event Series > Events uses the same ordering.",
                color = DesktopPalette.Disconnected,
                fontSize = 13.sp
            )
            Text(
                text = if (it.lockedForOptimizationEventCount == 0) {
                    "All ${it.unlockedForOptimizationEventCount} readable Event Files are available to Series optimization."
                } else {
                    "Series optimization can adjust ${it.unlockedForOptimizationEventCount} unlocked Event File" +
                        "${if (it.unlockedForOptimizationEventCount == 1) "" else "s"}; " +
                        "${it.lockedForOptimizationEventCount} locked Event File" +
                        "${if (it.lockedForOptimizationEventCount == 1) " is" else "s are"} preserved."
                },
                color = if (it.lockedForOptimizationEventCount == 0) DesktopPalette.Disconnected else DesktopPalette.Reading,
                fontSize = 13.sp
            )
        }
        Text(
            text = "Balance Open Event for Series redraws only the open Event File, using other series events with generated starts as start-third history.",
            color = DesktopPalette.Disconnected,
            fontSize = 13.sp
        )
        if (summary == null) {
            Text(
                text = "No start fairness summary is available for this Event File.",
                color = DesktopPalette.Black,
                fontSize = 13.sp
            )
            return@Column
        }

        val fairnessStatus = summary.seriesStartFairnessStatus(optimizationResult)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            FairnessScoreBlock(
                label = "Fairness",
                score = summary.fairnessNumber,
                color = summary.seriesStartFairnessNumberColor()
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onOptimizeStartFairness,
                        enabled = summary.generatedStartRowCount > 0 &&
                            summary.identifiedGeneratedStartRowCount > 0 &&
                            summary.unlockedForOptimizationEventCount > 0
                    ) {
                        Text("Optimize Series Starts")
                    }
                    Text(
                        text = fairnessStatus.label,
                        color = summary.seriesStartFairnessStatusColor(optimizationResult),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = "Tries randomized valid starts for each Event File, keeps only changes that improve or preserve whole-series fairness, and numbers distinct solutions found this session.",
                    color = DesktopPalette.Disconnected,
                    fontSize = 13.sp
                )
                if (fairnessStatus == SeriesStartFairnessStatus.ManualReviewRecommended) {
                    Text(
                        text = "The optimizer could not find a fairer draw with the current event start settings. Consider adjusting one or more event start-list settings, such as start interval, competitors per start time, or inserted empty starts, then optimize again.",
                        color = DesktopPalette.Error,
                        fontSize = 13.sp
                    )
                }
            }
        }

        optimizationResult?.let { result ->
            val solutionText = result.solutionLabel()
            val resultText = when {
                result.improved ->
                    "Optimizer improved uneven histories from ${result.initialUnevenHistoryCount} to " +
                        "${result.finalUnevenHistoryCount}, with ${result.optimizedEventCount} Event File" +
                        "${if (result.optimizedEventCount == 1) "" else "s"} updated."
                result.alternateSolution ->
                    "Optimizer found an alternate valid draw with the same fairness score, with " +
                        "${result.optimizedEventCount} Event File${if (result.optimizedEventCount == 1) "" else "s"} updated."
                else ->
                    "Optimizer did not find an alternate or improved valid draw after ${result.attemptedCandidateCount} candidates."
            }
            Text(
                text = "$solutionText. $resultText Accepted ${result.acceptedCandidateCount} of ${result.attemptedCandidateCount} candidates across ${result.completedPassCount} pass${if (result.completedPassCount == 1) "" else "es"}.",
                color = if (result.improved || result.alternateSolution) DesktopPalette.Black else DesktopPalette.Disconnected,
                fontSize = 13.sp
            )
        }

        if (summary.generatedStartRowCount == 0) {
            Text(
                text = "No generated start lists were found in this series yet.",
                color = DesktopPalette.Error,
                fontSize = 13.sp
            )
        } else if (summary.identifiedGeneratedStartRowCount == 0) {
            Text(
                text = "Generated starts were found, but none have SI number, bib number, or call sign identity data.",
                color = DesktopPalette.Error,
                fontSize = 13.sp
            )
        }

        DetailHeaderRow(listOf("Events in series", "With generated starts", "Without generated starts", "Missing files"))
        DetailGridRow(
            listOf(
                summary.seriesEventCount.toString(),
                summary.eventsWithGeneratedStartsCount.toString(),
                summary.eventsWithoutGeneratedStartsCount.toString(),
                summary.missingEventFileCount.toString()
            )
        )
        DetailHeaderRow(listOf("Generated starts", "Identified starts", "Unidentified starts", "Competitor histories"))
        DetailGridRow(
            listOf(
                summary.generatedStartRowCount.toString(),
                summary.identifiedGeneratedStartRowCount.toString(),
                summary.unidentifiedGeneratedStartRowCount.toString(),
                summary.competitorsWithIdentifiedHistoryCount.toString()
            )
        )
        DetailHeaderRow(listOf("First third", "Middle third", "Late third", "Uneven histories"))
        DetailGridRow(
            listOf(
                summary.firstThirdStartCount.toString(),
                summary.middleThirdStartCount.toString(),
                summary.lateThirdStartCount.toString(),
                summary.competitorsWithUnevenHistoryCount.toString()
            )
        )
        Text(
            text = "Uneven means a competitor's early/middle/late counts differ by more than one. Use the Action column when manually editing starts or regenerating affected event start lists.",
            color = DesktopPalette.Disconnected,
            fontSize = 13.sp
        )
        if (summary.competitorHistories.isNotEmpty()) {
            EventSeriesStartFairnessHistoryHeaderRow()
            summary.competitorHistories.forEach { history ->
                EventSeriesStartFairnessHistoryRow(history)
            }
        }
    }
}

@Composable
private fun FairnessScoreBlock(
    label: String,
    score: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = label,
            color = DesktopPalette.Disconnected,
            fontSize = 12.sp
        )
        Text(
            text = score.toString(),
            color = color,
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "/100",
            color = DesktopPalette.Disconnected,
            fontSize = 12.sp
        )
    }
}

private fun DesktopEventSeriesStartFairnessOptimizationResult.solutionLabel(): String {
    val number = solutionNumber ?: return "Unnumbered solution"
    return if (repeatedSolution) {
        "Repeated solution #$number"
    } else {
        "Solution #$number"
    }
}

private fun DesktopEventSeriesStartFairnessSummary.seriesStartFairnessStatus(
    optimizationResult: DesktopEventSeriesStartFairnessOptimizationResult?
): SeriesStartFairnessStatus =
    when {
        generatedStartRowCount == 0 -> SeriesStartFairnessStatus.GenerateStarts
        identifiedGeneratedStartRowCount == 0 -> SeriesStartFairnessStatus.AddIdentityData
        fairnessScoreCompetitorCount == 0 -> SeriesStartFairnessStatus.MoreHistoryNeeded
        unlockedForOptimizationEventCount == 0 -> SeriesStartFairnessStatus.AllEventsLocked
        fairnessNumber >= SeriesStartFairnessGoodThreshold -> SeriesStartFairnessStatus.NoOptimizationNeeded
        optimizationResult != null && !optimizationResult.improved &&
            fairnessNumber < SeriesStartFairnessManualReviewThreshold ->
            SeriesStartFairnessStatus.ManualReviewRecommended
        optimizationResult != null && !optimizationResult.improved -> SeriesStartFairnessStatus.NoBetterOptimizationsFound
        else -> SeriesStartFairnessStatus.OptimizationRecommended
    }

private fun DesktopEventSeriesStartFairnessSummary.seriesStartFairnessNumberColor(): Color =
    when {
        fairnessScoreCompetitorCount == 0 -> DesktopPalette.Disconnected
        fairnessNumber >= SeriesStartFairnessGoodThreshold -> ControlStatsOkColor
        fairnessNumber >= SeriesStartFairnessManualReviewThreshold -> Color(0xFFC46A00)
        else -> DesktopPalette.Error
    }

private fun DesktopEventSeriesStartFairnessSummary.seriesStartFairnessStatusColor(
    optimizationResult: DesktopEventSeriesStartFairnessOptimizationResult?
): Color =
    when (seriesStartFairnessStatus(optimizationResult)) {
        SeriesStartFairnessStatus.NoOptimizationNeeded -> ControlStatsOkColor
        SeriesStartFairnessStatus.ManualReviewRecommended -> DesktopPalette.Error
        SeriesStartFairnessStatus.OptimizationRecommended -> Color(0xFFC46A00)
        else -> DesktopPalette.Disconnected
    }

@Composable
private fun EventSeriesStartFairnessHistoryHeaderRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Competitor", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("ID", modifier = Modifier.width(96.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("Starts", modifier = Modifier.width(52.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("History", modifier = Modifier.width(72.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("Early", modifier = Modifier.width(48.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("Mid", modifier = Modifier.width(48.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("Late", modifier = Modifier.width(48.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("Action", modifier = Modifier.width(132.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun EventSeriesStartFairnessHistoryRow(
    history: DesktopEventSeriesStartFairnessCompetitorHistory
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = history.competitorName,
            modifier = Modifier.weight(1f),
            color = DesktopPalette.Black,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(history.identityLabel, modifier = Modifier.width(96.dp), color = DesktopPalette.Disconnected, fontSize = 13.sp)
        Text(history.generatedStartCount.toString(), modifier = Modifier.width(52.dp), fontSize = 13.sp)
        Text(history.thirdHistoryText, modifier = Modifier.width(72.dp), color = DesktopPalette.Disconnected, fontSize = 13.sp)
        Text(history.firstThirdCount.toString(), modifier = Modifier.width(48.dp), fontSize = 13.sp)
        Text(history.middleThirdCount.toString(), modifier = Modifier.width(48.dp), fontSize = 13.sp)
        Text(history.lateThirdCount.toString(), modifier = Modifier.width(48.dp), fontSize = 13.sp)
        Text(
            text = history.recommendation,
            modifier = Modifier.width(132.dp),
            color = if (history.isUneven) DesktopPalette.Error else DesktopPalette.Disconnected,
            fontSize = 13.sp,
            fontWeight = if (history.isUneven) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/** Shows competitor identity matching diagnostics for the active Event Series. */
@Composable
private fun EventSeriesCompetitorMatchingPanel(
    summaries: List<DesktopEventSeriesCompetitorMatchSummary>,
    identityCoverageSummaries: List<DesktopEventSeriesCompetitorIdentityCoverageSummary>
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Compares competitor identity matches across all manifest-listed events in this series.",
            color = DesktopPalette.Black,
            fontSize = 13.sp
        )
        Text(
            text = "Matches are competitor pairs found by unique SI number, bib number, call sign, or by a manual override when one is configured. Issues are duplicate identity values that make an automatic match ambiguous.",
            color = DesktopPalette.Disconnected,
            fontSize = 13.sp
        )
        if (summaries.isEmpty()) {
            Text(
                text = "At least two readable series events are needed for competitor matching.",
                color = DesktopPalette.Black,
                fontSize = 13.sp
            )
            return@Column
        }
        val eventCount = summaries
            .flatMap { listOf(it.firstSeriesEventId, it.secondSeriesEventId) }
            .distinct()
            .size
        val allEventIdentityCount = identityCoverageSummaries.count {
            it.presentEventCount == it.totalReadableEventCount && it.duplicateEventNames.isEmpty()
        }
        val partialIdentityCount = identityCoverageSummaries.count { it.missingEventNames.isNotEmpty() }
        val duplicateIdentityCount = identityCoverageSummaries.count { it.duplicateEventNames.isNotEmpty() }

        DetailHeaderRow(listOf("Events in series", "Identified competitors", "All events", "Partial", "Duplicate issues"))
        DetailGridRow(
            listOf(
                eventCount.toString(),
                identityCoverageSummaries.size.toString(),
                allEventIdentityCount.toString(),
                partialIdentityCount.toString(),
                duplicateIdentityCount.toString()
            )
        )
        if (identityCoverageSummaries.isEmpty()) {
            Text(
                text = "No competitors with SI numbers, bib numbers, call signs, or manual overrides were found in the readable series events.",
                color = DesktopPalette.Disconnected,
                fontSize = 13.sp
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                EventSeriesCompetitorIdentityCoverageHeaderRow()
                identityCoverageSummaries.forEach { summary ->
                    EventSeriesCompetitorIdentityCoverageRow(summary)
                }
            }
        }
        DetailHeaderRow(listOf("Comparison rows", "Matched pairs", "SI matches", "Bib matches", "Call matches"))
        DetailGridRow(
            listOf(
                summaries.size.toString(),
                summaries.sumOf { it.matchCount }.toString(),
                summaries.sumOf { it.siNumberMatchCount }.toString(),
                summaries.sumOf { it.bibNumberMatchCount }.toString(),
                summaries.sumOf { it.callSignMatchCount }.toString()
            )
        )
        if (summaries.any { it.matchCount > 0 } && summaries.any { it.matchCount == 0 }) {
            Text(
                text = "Some event pairs have matches and some do not. Rows marked Current include the loaded Event File.",
                color = DesktopPalette.Disconnected,
                fontSize = 13.sp
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            EventSeriesCompetitorMatchingHeaderRow()
            summaries.forEach { summary ->
                EventSeriesCompetitorMatchingRow(summary)
            }
        }
    }
}

@Composable
private fun EventSeriesCompetitorIdentityCoverageHeaderRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Identity", modifier = Modifier.width(120.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("Competitor", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("Coverage", modifier = Modifier.width(96.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("Events", modifier = Modifier.weight(1.1f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("Missing", modifier = Modifier.weight(1.1f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("Issues", modifier = Modifier.width(96.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun EventSeriesCompetitorIdentityCoverageRow(summary: DesktopEventSeriesCompetitorIdentityCoverageSummary) {
    val hasIssue = summary.missingEventNames.isNotEmpty() || summary.duplicateEventNames.isNotEmpty()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = summary.identityLabel,
            modifier = Modifier.width(120.dp),
            color = DesktopPalette.Black,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = summary.competitorName,
            modifier = Modifier.weight(1f),
            color = DesktopPalette.Black,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${summary.presentEventCount}/${summary.totalReadableEventCount}",
            modifier = Modifier.width(96.dp),
            color = if (hasIssue) DesktopPalette.Warning else DesktopPalette.Disconnected,
            fontSize = 13.sp,
            fontWeight = if (hasIssue) FontWeight.SemiBold else FontWeight.Normal
        )
        Text(
            text = summary.presentEventNames.joinToString(", "),
            modifier = Modifier.weight(1.1f),
            color = DesktopPalette.Disconnected,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = summary.missingEventNames.joinToString(", "),
            modifier = Modifier.weight(1.1f),
            color = if (summary.missingEventNames.isNotEmpty()) DesktopPalette.Warning else DesktopPalette.Disconnected,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (summary.duplicateEventNames.isEmpty()) "" else "Duplicate",
            modifier = Modifier.width(96.dp),
            color = if (summary.duplicateEventNames.isNotEmpty()) DesktopPalette.Warning else DesktopPalette.Disconnected,
            fontSize = 13.sp,
            fontWeight = if (summary.duplicateEventNames.isNotEmpty()) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun EventSeriesCompetitorMatchingHeaderRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Event Pair", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("Competitors", modifier = Modifier.width(120.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("Current", modifier = Modifier.width(64.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("Matched", modifier = Modifier.width(80.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("SI", modifier = Modifier.width(56.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("Bib", modifier = Modifier.width(56.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("Call", modifier = Modifier.width(56.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("Override", modifier = Modifier.width(76.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("Issues", modifier = Modifier.width(64.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun EventSeriesCompetitorMatchingRow(summary: DesktopEventSeriesCompetitorMatchSummary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${summary.firstEventName} / ${summary.secondEventName}",
            modifier = Modifier.weight(1f),
            color = DesktopPalette.Black,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${summary.firstCompetitorCount} / ${summary.secondCompetitorCount}",
            modifier = Modifier.width(120.dp),
            color = DesktopPalette.Disconnected,
            fontSize = 13.sp
        )
        Text(
            text = if (summary.includesCurrentEvent) "Yes" else "",
            modifier = Modifier.width(64.dp),
            color = if (summary.includesCurrentEvent) DesktopPalette.Black else DesktopPalette.Disconnected,
            fontSize = 13.sp
        )
        Text(summary.matchCount.toString(), modifier = Modifier.width(80.dp), fontSize = 13.sp)
        Text(summary.siNumberMatchCount.toString(), modifier = Modifier.width(56.dp), fontSize = 13.sp)
        Text(summary.bibNumberMatchCount.toString(), modifier = Modifier.width(56.dp), fontSize = 13.sp)
        Text(summary.callSignMatchCount.toString(), modifier = Modifier.width(56.dp), fontSize = 13.sp)
        Text(summary.overrideMatchCount.toString(), modifier = Modifier.width(76.dp), fontSize = 13.sp)
        Text(
            text = summary.issueCount.toString(),
            modifier = Modifier.width(64.dp),
            color = if (summary.issueCount > 0) DesktopPalette.Warning else DesktopPalette.Disconnected,
            fontSize = 13.sp,
            fontWeight = if (summary.issueCount > 0) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/** Lets organizers edit manifest-owned settings for the active Event Series. */
@Composable
private fun EventSeriesSettingsPanel(
    seriesContext: EventSeriesUiContext?,
    onUpdateSeriesName: (String) -> Boolean,
    onUpdateSeriesFileName: (String) -> Boolean
) {
    if (seriesContext == null) {
        Text(
            text = "No Event Series manifest was found for this Event File.",
            color = DesktopPalette.Black,
            fontSize = 14.sp
        )
        return
    }

    var nameDraft by remember(seriesContext.seriesName) { mutableStateOf(seriesContext.seriesName) }
    val trimmedName = nameDraft.trim()
    val canApplyName = trimmedName.isNotBlank() && trimmedName != seriesContext.seriesName
    val currentFileNameStem = DesktopEventSeriesActions.manifestFileDisplayStem(seriesContext.manifestPath)
    var fileNameDraft by remember(seriesContext.manifestPath) { mutableStateOf(currentFileNameStem) }
    val trimmedFileName = fileNameDraft.trim()
    val canApplyFileName = trimmedFileName.isNotBlank() && trimmedFileName != currentFileNameStem
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Manifest: ${seriesContext.manifestPath}",
            color = DesktopPalette.Disconnected,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = nameDraft,
                onValueChange = { nameDraft = it },
                label = { Text("Series name") },
                modifier = Modifier
                    .widthIn(min = 360.dp, max = 640.dp)
                    .commitOnEnter {
                        if (canApplyName && onUpdateSeriesName(trimmedName)) {
                            nameDraft = trimmedName
                        }
                    },
                singleLine = true
            )
            Button(
                enabled = canApplyName,
                onClick = {
                    if (onUpdateSeriesName(trimmedName)) {
                        nameDraft = trimmedName
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = DesktopPalette.SeriesNavigation,
                    contentColor = DesktopPalette.Black
                )
            ) {
                Text("Apply Name")
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = fileNameDraft,
                onValueChange = { fileNameDraft = it },
                label = { Text("Series file name") },
                modifier = Modifier
                    .widthIn(min = 320.dp, max = 520.dp)
                    .commitOnEnter {
                        if (canApplyFileName && onUpdateSeriesFileName(trimmedFileName)) {
                            fileNameDraft = trimmedFileName
                        }
                    },
                singleLine = true
            )
            Text(
                text = ".series.radio-oracle.json",
                color = DesktopPalette.Disconnected,
                fontSize = 13.sp
            )
            Button(
                enabled = canApplyFileName,
                onClick = {
                    if (onUpdateSeriesFileName(trimmedFileName)) {
                        fileNameDraft = trimmedFileName
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = DesktopPalette.SeriesNavigation,
                    contentColor = DesktopPalette.Black
                )
            ) {
                Text("Rename File")
            }
        }
    }
}

@Composable
private fun EventSeriesEventHeaderRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Seq", modifier = Modifier.width(64.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("Event", modifier = Modifier.weight(1.2f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("Start", modifier = Modifier.width(160.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("File", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("Status", modifier = Modifier.width(112.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Spacer(modifier = Modifier.width(88.dp))
    }
}

@Composable
private fun EventSeriesEventRow(
    summary: DesktopEventSeriesEventSummary,
    onOpenEvent: (DesktopEventSeriesEventSummary) -> Unit
) {
    val statusText = when {
        summary.isCurrentEvent -> "Current"
        !summary.exists -> "Missing"
        else -> "Ready"
    }
    val statusColor = when {
        summary.isCurrentEvent -> DesktopPalette.PrimaryVariant
        !summary.exists -> DesktopPalette.Error
        else -> DesktopPalette.Disconnected
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(summary.displayPosition.toString(), modifier = Modifier.width(64.dp), color = DesktopPalette.Black, fontSize = 13.sp)
        Text(summary.displayName, modifier = Modifier.weight(1.2f), color = DesktopPalette.Black, fontSize = 13.sp)
        Text(
            summary.startDateTimeIso?.let(DesktopDateTimeText::displayIsoOrRaw).orEmpty(),
            modifier = Modifier.width(160.dp),
            color = DesktopPalette.Disconnected,
            fontSize = 13.sp
        )
        Text(
            summary.eventFilePath,
            modifier = Modifier.weight(1f),
            color = DesktopPalette.Disconnected,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(statusText, modifier = Modifier.width(112.dp), color = statusColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Button(
            onClick = { onOpenEvent(summary) },
            enabled = summary.exists && !summary.isCurrentEvent,
            modifier = Modifier.width(88.dp)
        ) {
            ButtonLabel("Open")
        }
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
    val canAddManualReadout = selectedCompetitorId != null ||
        siNumberDraft.isNotBlank() ||
        startSecondsDraft.isNotBlank() ||
        finishSecondsDraft.isNotBlank() ||
        controlCodesDraft.isNotBlank()
    fun addManualReadout() {
        val didAdd = onAddManualReadout(
            selectedCompetitorId,
            siNumberDraft,
            startSecondsDraft,
            finishSecondsDraft,
            controlCodesDraft,
            selectedStatus
        )
        if (didAdd) {
            selectedCompetitorId = null
            siNumberDraft = ""
            startSecondsDraft = ""
            finishSecondsDraft = ""
            controlCodesDraft = ""
            selectedStatus = ResultStatus.OK
        }
    }

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
                canAdd = canAddManualReadout,
                onAdd = ::addManualReadout,
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
                        onStatusSelected = { selectedStatus = it },
                        onCommit = {
                            if (canAddManualReadout) {
                                addManualReadout()
                            }
                        }
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
    canAdd: Boolean,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onAdd,
        modifier = modifier,
        enabled = canAdd
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
    onStatusSelected: (ResultStatus) -> Unit,
    onCommit: () -> Unit
) {
    Row(
        modifier = Modifier.width(fixedTableWidth(ReadoutTableColumns)),
        horizontalArrangement = Arrangement.spacedBy(TableColumnGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = siNumberDraft,
            onValueChange = onSiNumberChange,
            modifier = Modifier
                .width(ReadoutTableColumns[0].width)
                .commitOnEnter(onCommit),
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
            modifier = Modifier
                .width(ReadoutTableColumns[3].width)
                .commitOnEnter(onCommit),
            singleLine = true,
            label = { Text("Start s") }
        )
        TextField(
            value = finishSecondsDraft,
            onValueChange = onFinishSecondsChange,
            modifier = Modifier
                .width(ReadoutTableColumns[4].width)
                .commitOnEnter(onCommit),
            singleLine = true,
            label = { Text("Finish s") }
        )
        TextField(
            value = controlCodesDraft,
            onValueChange = onControlCodesChange,
            modifier = Modifier
                .width(ReadoutTableColumns[5].width)
                .commitOnEnter(onCommit),
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
    fun saveDraft() {
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
                        enabled = !draft.isPractice,
                        onCommit = ::saveDraft
                    )
                    LabeledTextField(
                        label = "Finish elapsed",
                        value = finishSeconds,
                        onValueChange = { finishSeconds = it },
                        modifier = Modifier.width(160.dp),
                        onCommit = ::saveDraft
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
                        modifier = Modifier
                            .width(420.dp)
                            .height(88.dp)
                            .commitOnEnter(::saveDraft)
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
                onClick = ::saveDraft
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
    var firstNameDraft by remember { mutableStateOf("") }
    var lastNameDraft by remember { mutableStateOf("") }
    var clubDraft by remember { mutableStateOf("") }
    var bibNumberDraft by remember { mutableStateOf("") }
    var callSignDraft by remember { mutableStateOf("") }
    var birthYearDraft by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var startNumberDraft by remember { mutableStateOf("") }
    var siNumberDraft by remember { mutableStateOf("") }
    var isReadingSiCardForAdd by remember { mutableStateOf(false) }
    var readSiCardStatusText by remember { mutableStateOf<String?>(null) }
    val canAddCompetitor = firstNameDraft.isNotBlank() &&
            lastNameDraft.isNotBlank()
    fun addCompetitor() {
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
            startNumberDraft = ""
            siNumberDraft = ""
        }
    }

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
                onClick = ::addCompetitor,
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
                        onSiNumberChange = { siNumberDraft = it },
                        onCommit = {
                            if (canAddCompetitor) {
                                addCompetitor()
                            }
                        }
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
    onSiNumberChange: (String) -> Unit,
    onCommit: () -> Unit
) {
    Row(
        modifier = Modifier.width(fixedTableWidth(CompetitorTableColumns)),
        horizontalArrangement = Arrangement.spacedBy(TableColumnGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = firstNameDraft,
            onValueChange = onFirstNameChange,
            modifier = Modifier
                .width(CompetitorTableColumns[0].width)
                .commitOnEnter(onCommit),
            singleLine = true,
            label = { Text("First") }
        )
        TextField(
            value = lastNameDraft,
            onValueChange = onLastNameChange,
            modifier = Modifier
                .width(CompetitorTableColumns[1].width)
                .commitOnEnter(onCommit),
            singleLine = true,
            label = { Text("Last") }
        )
        TextField(
            value = clubDraft,
            onValueChange = onClubChange,
            modifier = Modifier
                .width(CompetitorTableColumns[2].width)
                .commitOnEnter(onCommit),
            singleLine = true,
            label = { Text("Club") }
        )
        TextField(
            value = bibNumberDraft,
            onValueChange = onBibNumberChange,
            modifier = Modifier
                .width(CompetitorTableColumns[3].width)
                .commitOnEnter(onCommit),
            singleLine = true,
            label = { Text("Bib") }
        )
        TextField(
            value = callSignDraft,
            onValueChange = onCallSignChange,
            modifier = Modifier
                .width(CompetitorTableColumns[4].width)
                .commitOnEnter(onCommit),
            singleLine = true,
            label = { Text("Call") }
        )
        TextField(
            value = birthYearDraft,
            onValueChange = onBirthYearChange,
            modifier = Modifier
                .width(CompetitorTableColumns[5].width)
                .commitOnEnter(onCommit),
            singleLine = true,
            label = { Text("Birth") }
        )
        CategoryPicker(
            selectedCategoryId = selectedCategoryId,
            categories = categories,
            onCategorySelected = onCategorySelected,
            modifier = Modifier.width(CompetitorTableColumns[6].width)
        )
        Spacer(modifier = Modifier.width(CompetitorTableColumns[7].width))
        Spacer(modifier = Modifier.width(CompetitorTableColumns[8].width))
        TextField(
            value = siNumberDraft,
            onValueChange = onSiNumberChange,
            modifier = Modifier
                .width(CompetitorTableColumns[9].width)
                .commitOnEnter(onCommit),
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
        if (siNumberDraft != competitor.siNumberText) {
            if (siNumberDraft.isBlank() || siNumberDraft.trim().toIntOrNull() != null) {
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
                }
                .commitOnEnter(::applyPendingDrafts),
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
                }
                .commitOnEnter(::applyPendingDrafts),
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
                }
                .commitOnEnter(::applyPendingDrafts),
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
                }
                .commitOnEnter(::applyPendingDrafts),
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
                }
                .commitOnEnter(::applyPendingDrafts),
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
                }
                .commitOnEnter(::applyPendingDrafts),
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
        FixedTableText(competitor.startNumberText, CompetitorTableColumns[7].width)
        TextField(
            value = startTimeDraft,
            onValueChange = { startTimeDraft = it },
            modifier = Modifier
                .width(CompetitorTableColumns[8].width)
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused) {
                        applyPendingDrafts()
                    }
                }
                .commitOnEnter(::applyPendingDrafts),
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
                }
                .commitOnEnter(::applyPendingDrafts),
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
    val orderedControls = remember(controls, raceType) { controls.customaryDisplayOrder(raceType) }
    var siCodeDraft by remember { mutableStateOf("") }
    var typeDraft by remember { mutableStateOf(ControlPointType.CONTROL) }
    var publicLabelDraft by remember { mutableStateOf("") }
    var notesDraft by remember { mutableStateOf("") }
    fun addControl() {
        val didAdd = onAddControl("", siCodeDraft, typeDraft, typeDraft.defaultScored(), publicLabelDraft, notesDraft)
        if (didAdd) {
            siCodeDraft = ""
            typeDraft = ControlPointType.CONTROL
            publicLabelDraft = ""
            notesDraft = ""
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ControlStatsRow(
            controls = controls,
            raceType = raceType
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TableColumnGap),
            verticalAlignment = Alignment.Top
        ) {
            Button(
                onClick = ::addControl,
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
                        onNotesChange = { notesDraft = it },
                        onCommit = {
                            if (siCodeDraft.isNotBlank()) {
                                addControl()
                            }
                        }
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
private fun ControlStatsRow(
    controls: List<EventControlDetails>,
    raceType: RaceType
) {
    val items = controlStatsItems(controls, raceType)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            ControlStatsText(
                label = item.label,
                count = item.count,
                isCompliant = item.isCompliant
            )
        }
    }
}

@Composable
private fun ControlStatsText(
    label: String,
    count: Int,
    isCompliant: Boolean
) {
    Text(
        text = "$label: $count",
        color = if (isCompliant) ControlStatsOkColor else DesktopPalette.Error,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold
    )
}

private fun controlStatsItems(controls: List<EventControlDetails>, raceType: RaceType): List<ControlStatsItem> {
    val counts = ControlRoleCounts.from(controls.map { it.type })
    return when (raceType) {
        RaceType.SPRINT -> {
            val loopGroups = SprintLoopControlGroups.from(controls)
            listOf(
                ControlStatsItem("Slow-loop Foxes", loopGroups.slowLoopFoxes.size, loopGroups.slowLoopFoxes.size == 5),
                ControlStatsItem("Fast-loop Foxes", loopGroups.fastLoopFoxes.size, loopGroups.fastLoopFoxes.size == 5),
                ControlStatsItem("Beacons", counts.beacons, counts.beacons == 1),
                ControlStatsItem("Spectators", counts.spectators, counts.spectators <= 1)
            )
        }
        RaceType.CLASSIC,
        RaceType.SHORT -> listOf(
            ControlStatsItem("Foxes", counts.foxes, counts.foxes == 5),
            ControlStatsItem("Beacons", counts.beacons, counts.beacons == 1)
        )
        RaceType.FOXORING -> listOf(
            ControlStatsItem("Foxes", counts.foxes, counts.foxes in 4..12),
            ControlStatsItem("Beacons", counts.beacons, counts.beacons == 1)
        )
        RaceType.ORIENTEERING -> listOf(
            ControlStatsItem("Controls", counts.foxes, counts.foxes > 0)
        )
    }
}

private fun List<EventControlDetails>.customaryDisplayOrder(raceType: RaceType): List<EventControlDetails> =
    when (raceType) {
        RaceType.SPRINT -> {
            val groups = SprintLoopControlGroups.from(this)
            sortedWith(
                compareBy<EventControlDetails> { SprintControlDisplaySlot.forControl(it, groups) }
                    .thenBy { it.siCode }
                    .thenBy { it.publicDisplayLabel() }
            )
        }
        RaceType.CLASSIC,
        RaceType.SHORT,
        RaceType.FOXORING -> sortedWith(
            compareBy<EventControlDetails> {
                when (it.type) {
                    ControlPointType.CONTROL -> 0
                    ControlPointType.SEPARATOR -> 1
                    ControlPointType.BEACON -> 2
                }
            }
                .thenBy { it.siCode }
                .thenBy { it.publicDisplayLabel() }
        )
        RaceType.ORIENTEERING -> sortedWith(compareBy<EventControlDetails> { it.siCode }.thenBy { it.publicDisplayLabel() })
    }

private fun EventControlDetails.sprintLoopFromLabel(): SprintLoop? =
    publicLabel.trim().takeIf { it.isNotEmpty() }?.sprintLoopLabel()
        ?: label.trim().takeIf { it.isNotEmpty() }?.sprintLoopLabel()

private fun String.sprintLoopLabel(): SprintLoop? =
    when {
        sprintFastNumber() != null || trim().uppercase().contains("FAST") -> SprintLoop.Fast
        sprintSlowNumber() != null -> SprintLoop.Slow
        else -> null
    }

private fun String.sprintDisplaySlot(): SprintControlDisplaySlot? {
    val normalized = trim().uppercase()
    return when {
        normalized in setOf("S", "SP", "SPEC", "SPECTATOR", "SEP", "SEPARATOR") ->
            SprintControlDisplaySlot(1, 0)
        normalized in setOf("B", "BB", "M", "MO", "BEACON", "FINISH BEACON") ->
            SprintControlDisplaySlot(3, 0)
        sprintFastNumber() != null -> SprintControlDisplaySlot(2, sprintFastNumber() ?: 0)
        sprintSlowNumber() != null -> SprintControlDisplaySlot(0, sprintSlowNumber() ?: 0)
        normalized.contains("FAST") -> SprintControlDisplaySlot(2, normalized.sprintLabelNumber() ?: Int.MAX_VALUE)
        else -> null
    }
}

private fun String.sprintSlowNumber(): Int? {
    val normalized = trim().uppercase()
    if (normalized.endsWith("F") || normalized.startsWith("F") || normalized.contains("FAST")) {
        return null
    }
    return normalized.sprintLabelNumber()
}

private fun String.sprintFastNumber(): Int? {
    val normalized = trim().uppercase()
    val suffix = normalized.takeIf { it.endsWith("F") }?.dropLast(1)?.toIntOrNull()
    val prefix = normalized.takeIf { it.startsWith("F") }?.drop(1)?.toIntOrNull()
    return (suffix ?: prefix ?: normalized.takeIf { it.contains("FAST") }?.sprintLabelNumber())?.takeIf { it in 1..5 }
}

private fun String.sprintLabelNumber(): Int? =
    Regex("""\b([1-5])\b""").find(this)?.groupValues?.get(1)?.toIntOrNull()

@Composable
private fun KmlToolsPanel() {
    var selectedPath by remember { mutableStateOf<Path?>(null) }
    var latitudeDraft by remember { mutableStateOf("") }
    var longitudeDraft by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf<String?>(null) }
    val parsedLatitude = latitudeDraft.trim().toDoubleOrNull()
    val parsedLongitude = longitudeDraft.trim().toDoubleOrNull()
    val canApply = selectedPath != null &&
        parsedLatitude != null &&
        parsedLatitude in -90.0..90.0 &&
        parsedLongitude != null &&
        parsedLongitude in -180.0..180.0

    fun chooseFile() {
        DesktopFileDialogs.chooseKmlToolsFile()?.let { path ->
            selectedPath = path
            statusText = null
        }
    }

    fun openOutputFolder(path: Path): String? =
        runCatching {
            val directory = path.parent ?: Path.of(".").toAbsolutePath()
            if (!Desktop.isDesktopSupported()) {
                error("Opening folders is not supported on this system.")
            }
            val desktop = Desktop.getDesktop()
            if (!desktop.isSupported(Desktop.Action.OPEN)) {
                error("Opening folders is not supported on this system.")
            }
            desktop.open(directory.toFile())
        }.exceptionOrNull()?.let { error ->
            " Could not open output folder: ${error.message ?: error::class.simpleName}"
        }

    fun applyMoveCourse() {
        val path = selectedPath ?: return
        val latitude = parsedLatitude ?: return
        val longitude = parsedLongitude ?: return
        runCatching {
            DesktopKmlTools.moveCourse(
                sourcePath = path,
                newStart = DesktopKmlToolsPoint(latitude = latitude, longitude = longitude)
            )
        }.onSuccess { result ->
            val folderStatus = openOutputFolder(result.outputPath).orEmpty()
            statusText = "Created ${result.outputPath.fileName}; moved ${result.translatedCoordinateCount} coordinates.$folderStatus"
        }.onFailure { error ->
            statusText = "Move Course failed: ${error.message ?: error::class.simpleName}"
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Move Course",
            color = DesktopPalette.Black,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = ::chooseFile) {
                ButtonLabel("Choose KML/KMZ...")
            }
            Text(
                text = selectedPath?.fileName?.toString() ?: "No file selected",
                color = DesktopPalette.Black,
                fontSize = 13.sp
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = latitudeDraft,
                onValueChange = { latitudeDraft = it },
                label = { Text("New Start latitude") },
                singleLine = true,
                modifier = Modifier
                    .width(180.dp)
                    .commitOnEnter(::applyMoveCourse)
            )
            TextField(
                value = longitudeDraft,
                onValueChange = { longitudeDraft = it },
                label = { Text("New Start longitude") },
                singleLine = true,
                modifier = Modifier
                    .width(180.dp)
                    .commitOnEnter(::applyMoveCourse)
            )
            Button(
                onClick = ::applyMoveCourse,
                enabled = canApply
            ) {
                ButtonLabel("Apply")
            }
        }
        statusText?.let { text ->
            Text(
                text = text,
                color = if (text.startsWith("Move Course failed")) DesktopPalette.Error else DesktopPalette.Disconnected,
                fontSize = 13.sp
            )
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
            KmlImportInstruction("Controls more than 50 meters from a matched category route are not included in the imported route order.")
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
                val totalPointCount = listing.rowCount.toLong() * listing.columnCount.toLong()
                val pointText = listing.resolvedPointCount?.let { resolved ->
                    "$resolved/$totalPointCount points"
                } ?: "$totalPointCount grid points"
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "${listing.venueName} - ${listing.sourceName} ${listing.resolutionMeters.roundToInt()} m - $pointText",
                        color = DesktopPalette.Black,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${listing.path.fileName}  created ${listing.createdAtIso}  modified ${listing.fileModifiedAtIso}  ${bytesText(listing.fileSizeBytes)}",
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
    onDownloadCache: (String, DesktopVenueElevationBoundingBox?, Double, Double, DesktopVenueElevationCacheSource, String) -> Unit
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
    var resolutionMetersDraft by remember { mutableStateOf("3") }
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
    val boundingBoxFieldsAreBlank = minLatitudeDraft.isBlank() &&
        maxLatitudeDraft.isBlank() &&
        minLongitudeDraft.isBlank() &&
        maxLongitudeDraft.isBlank()
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
            LabeledTextField("Resolution (m)", resolutionMetersDraft, { resolutionMetersDraft = it }, Modifier.width(120.dp))
            LabeledTextField("Buffer (m)", bufferMetersDraft, { bufferMetersDraft = it }, Modifier.width(120.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LabeledTextField("Min lat", minLatitudeDraft, { minLatitudeDraft = it }, Modifier.width(150.dp), placeholder = "ex: 35.123456")
            LabeledTextField("Max lat", maxLatitudeDraft, { maxLatitudeDraft = it }, Modifier.width(150.dp), placeholder = "ex: 35.234567")
            LabeledTextField("Min lon", minLongitudeDraft, { minLongitudeDraft = it }, Modifier.width(150.dp), placeholder = "ex: -82.345678")
            LabeledTextField("Max lon", maxLongitudeDraft, { maxLongitudeDraft = it }, Modifier.width(150.dp), placeholder = "ex: -82.234567")
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
                val localSourceTypes = DesktopVenueElevationCache.desktopLocalElevationSourceTypes(localRasterPathDraft)
                val localSourceCanCreate = resolutionMeters != null &&
                    resolutionMeters > 0.0 &&
                    localRasterPathDraft.isNotBlank() &&
                    localSourceTypes.isNotEmpty() &&
                    (parsedBoundingBox != null || boundingBoxFieldsAreBlank)
                Button(
                    onClick = {
                        val paths = DesktopFileDialogs.chooseElevationRaster()
                        if (paths.isNotEmpty()) {
                            localRasterPathDraft = paths.joinToString("; ") { it.toString() }
                        }
                    }
                ) {
                    ButtonLabel("Select Local Source...")
                }
                Button(
                    onClick = {
                        val resolution = resolutionMeters ?: return@Button
                        val bounds = parsedBoundingBox
                            ?: if (boundingBoxFieldsAreBlank) null else return@Button
                        onDownloadCache(
                            venueNameDraft,
                            bounds,
                            resolution,
                            bufferMeters,
                            DesktopVenueElevationCacheSource.LocalLidarRaster,
                            localRasterPathDraft
                        )
                    },
                    enabled = localSourceCanCreate
                ) {
                    ButtonLabel("Create Cache from Local Source")
                }
            }
            LabeledTextField(
                "Local source file(s)",
                localRasterPathDraft,
                { localRasterPathDraft = it },
                Modifier.width(640.dp)
            )
            Text(
                text = "Use a local GeoTIFF raster (.tif/.tiff), GeoTIFF ZIP (.zip), or one or more LAS/LAZ point clouds (.las/.laz), such as countywide LiDAR DEM files, to create a cache without downloading elevation data.",
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
            text = "Import course KML/KMZ or GPX data with at least one route path before running analysis.",
            color = DesktopPalette.Black,
            fontSize = 13.sp
        )
        Text(
            text = "KML/KMZ course files must contain named control Point placemarks and at least one LineString route. GPX course files must contain named control waypoints and at least one route or track named by Event File category, such as M21. Control-only files belong under Setup > Controls > Import/Export.",
            color = DesktopPalette.Black,
            fontSize = 13.sp
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            KmlImportInstruction("Choose a category, then Analyze to compare the imported route with the calculated route candidate.")
            KmlImportInstruction("Export Analysis writes the displayed analysis plus route/control data for external review.")
            KmlImportInstruction("Apply Calculated Route replaces imported route and numbering data when the calculated route is available.")
            KmlImportInstruction("Apply Fox Renumbering Only applies the Section 1 wait-time renumbering when an improvement is available.")
        }
        Text(
            text = "Use the left-column menu buttons Import Course KML/KMZ... and Import Course GPX... to import route-bearing course data for a category before running analysis.",
            color = DesktopPalette.Black,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun CourseAnalysisPanel(
    projectFile: EventProjectFile,
    eventFilePath: Path?,
    isUnlocked: Boolean,
    protectedIdealOrderByCategoryId: Map<String, String>,
    protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>,
    analysisResult: DesktopCourseAnalysisSummary?,
    onAnalysisResultChange: (DesktopCourseAnalysisSummary?) -> Unit,
    applyStatusText: String?,
    onApplyStatusTextChange: (String?) -> Unit,
    onResolveCachedElevations: suspend (String) -> CourseAnalysisElevationPreparationResult?,
    onDownloadMissingElevations: suspend (String, DesktopCourseAnalysisSummary) -> CourseAnalysisElevationPreparationResult?,
    onUnlock: (String) -> Boolean,
    onUpdateSpeedFactor: (Double) -> String
) {
    var passwordDraft by remember(projectFile.raceData.race.id, isUnlocked) { mutableStateOf("") }
    if (!isUnlocked) {
        fun unlock() {
            if (passwordDraft.isNotBlank() && onUnlock(passwordDraft)) {
                passwordDraft = ""
            }
        }
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
                modifier = Modifier
                    .width(260.dp)
                    .commitOnEnter(::unlock)
            )
            DisabledReasonTooltip(
                if (passwordDraft.isBlank()) {
                    "Enter the Event Password to view route data and run analysis."
                } else {
                    null
                }
            ) {
                Button(
                    onClick = ::unlock,
                    enabled = passwordDraft.isNotBlank()
                ) {
                    ButtonLabel("Unlock")
                }
            }
        }
        return
    }

    val courseInfoRetentionKey = projectFile.raceData.categories.map { categoryData ->
        categoryData.category.id to categoryData.category.encryptedCourseInfo.orEmpty()
    }
    var retainedCourseInfoByCategoryId by remember(projectFile.raceData.race.id) {
        mutableStateOf(
            retainedCourseAnalysisCourseInfo(
                projectFile = projectFile,
                currentCourseInfoByCategoryId = protectedCourseInfoByCategoryId,
                previousRetainedCourseInfoByCategoryId = emptyMap()
            )
        )
    }
    LaunchedEffect(courseInfoRetentionKey, protectedCourseInfoByCategoryId) {
        retainedCourseInfoByCategoryId = retainedCourseAnalysisCourseInfo(
            projectFile = projectFile,
            currentCourseInfoByCategoryId = protectedCourseInfoByCategoryId,
            previousRetainedCourseInfoByCategoryId = retainedCourseInfoByCategoryId
        )
    }
    val analysisCourseInfoByCategoryId = effectiveCourseAnalysisCourseInfoByCategoryId(
        projectFile = projectFile,
        currentCourseInfoByCategoryId = protectedCourseInfoByCategoryId,
        retainedCourseInfoByCategoryId = retainedCourseInfoByCategoryId
    )
    val categories = projectFile.raceData.categories
        .filter { categoryData ->
            analysisCourseInfoByCategoryId[categoryData.category.id]?.route.orEmpty().size >= 2
        }
        .sortedWith(EventCategorySort.byDisplayName)
    var selectedCategoryId by remember(projectFile.raceData.race.id, categories.map { it.category.id }) {
        mutableStateOf(categories.firstOrNull()?.category?.id)
    }
    val effectiveSelectedCategoryId = selectedCategoryId
        ?.takeIf { selectedId -> categories.any { it.category.id == selectedId } }
        ?: categories.firstOrNull()?.category?.id
    var pendingMissingDataResult by remember(projectFile.raceData.race.id, protectedCourseInfoByCategoryId) {
        mutableStateOf<CourseAnalysisMissingDataPrompt?>(null)
    }
    var exportStatusText by remember(projectFile.raceData.race.id) { mutableStateOf<String?>(null) }
    var speedStatusText by remember(projectFile.raceData.race.id) { mutableStateOf<String?>(null) }
    var isAnalyzing by remember(projectFile.raceData.race.id) { mutableStateOf(false) }
    var analysisProgressMessage by remember(projectFile.raceData.race.id) {
        mutableStateOf("Calculating route metrics, route optimization, wait times, rule checks, and report graphics.")
    }
    val analysisScope = rememberCoroutineScope()
    var speedFactorDraft by remember(
        projectFile.raceData.race.id,
        projectFile.raceData.race.courseAnalyzerSpeedCompensationFactor
    ) {
        mutableStateOf(twoDecimalText(projectFile.raceData.race.courseAnalyzerSpeedCompensationFactor))
    }
    var speedFactorFocused by remember(projectFile.raceData.race.id) { mutableStateOf(false) }

    fun applySpeedFactorDraft() {
        val factor = speedFactorDraft.trim().toDoubleOrNull()
        if (factor == null) {
            speedStatusText = "Speed factor must be a number from 0.25 to 2.00."
            return
        }
        val currentFactor = projectFile.raceData.race.courseAnalyzerSpeedCompensationFactor
        if (abs(factor - currentFactor) < 0.0001) {
            speedFactorDraft = twoDecimalText(currentFactor)
            return
        }
        speedStatusText = onUpdateSpeedFactor(factor)
        onAnalysisResultChange(null)
        pendingMissingDataResult = null
    }

    suspend fun analyzeSelectedCourse(
        categoryId: String,
        analysisProjectFile: EventProjectFile = projectFile,
        analysisProtectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo> = analysisCourseInfoByCategoryId
    ): DesktopCourseAnalysisSummary =
        withContext(Dispatchers.Default) {
            DesktopCourseAnalyzer.analyze(
                projectFile = analysisProjectFile,
                categoryId = categoryId,
                protectedCourseInfo = analysisProtectedCourseInfoByCategoryId[categoryId],
                protectedIdealOrderText = protectedIdealOrderByCategoryId[categoryId],
                eventFileName = eventFilePath?.fileName?.toString(),
                elevationLookup = DesktopVenueElevationCache::elevationMeters,
                elevationCacheNotes = DesktopVenueElevationCache::analysisSourceNotes
            )
        }
    fun analyzeDisabledReason(categoryId: String?): String? =
        when {
            categoryId == null ->
                "Import controls/route KML/KMZ or GPX data for a category before running analysis."
            analysisCourseInfoByCategoryId[categoryId]?.route.orEmpty().size < 2 ->
                "Stored course route data is unavailable for the selected category. Import course KML/KMZ or GPX data before running analysis."
            else -> null
        }

    suspend fun analyzeWithLocalCachePreparation(categoryId: String): DesktopCourseAnalysisSummary {
        var summary = analyzeSelectedCourse(categoryId)
        if (summary.hasMissingElevationData) {
            analysisProgressMessage = "Checking the local elevation cache before asking for an internet download."
            val preparation = onResolveCachedElevations(categoryId)
            if (preparation != null) {
                exportStatusText = preparation.statusText
                summary = analyzeSelectedCourse(
                    categoryId = categoryId,
                    analysisProjectFile = preparation.projectFile,
                    analysisProtectedCourseInfoByCategoryId = preparation.protectedCourseInfoByCategoryId
                )
            }
        }
        return summary
    }

    fun acceptAnalysisSummary(categoryId: String, summary: DesktopCourseAnalysisSummary) {
        if (summary.missingElements.isEmpty()) {
            onAnalysisResultChange(summary)
        } else if (shouldPromptForCourseAnalysisMissingData(summary)) {
            pendingMissingDataResult = CourseAnalysisMissingDataPrompt(
                categoryId = categoryId,
                summary = summary
            )
        } else {
            onAnalysisResultChange(summary)
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        CourseAnalyzerGuidance()
        if (categories.isEmpty()) {
            Text(
                text = "Import controls/route KML/KMZ or GPX data for a category before running course analysis.",
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
                    onAnalysisResultChange(null)
                    pendingMissingDataResult = null
                    exportStatusText = null
                    onApplyStatusTextChange(null)
                },
                modifier = Modifier.width(280.dp)
            )
            TextField(
                value = speedFactorDraft,
                onValueChange = {
                    speedFactorDraft = it
                    speedStatusText = null
                },
                label = { Text("Speed factor") },
                singleLine = true,
                modifier = Modifier
                    .width(126.dp)
                    .onFocusChanged { state ->
                        if (speedFactorFocused && !state.isFocused) {
                            applySpeedFactorDraft()
                        }
                        speedFactorFocused = state.isFocused
                    }
                    .commitOnEnter(::applySpeedFactorDraft)
            )
            DisabledReasonTooltip(
                analyzeDisabledReason(effectiveSelectedCategoryId)
            ) {
                Button(
                    onClick = {
                        val categoryId = effectiveSelectedCategoryId ?: return@Button
                        if (isAnalyzing) return@Button
                        isAnalyzing = true
                        exportStatusText = null
                        onApplyStatusTextChange(null)
                        onAnalysisResultChange(null)
                        pendingMissingDataResult = null
                        analysisScope.launch {
                            try {
                                // Let Compose paint the progress dialog before route optimization
                                // begins; Foxoring hybrid search can otherwise make the UI appear
                                // unresponsive on slower machines.
                                delay(100)
                                analysisProgressMessage = "Calculating route metrics, route optimization, wait times, rule checks, and report graphics."
                                val summary = analyzeWithLocalCachePreparation(categoryId)
                                acceptAnalysisSummary(categoryId, summary)
                            } catch (error: Throwable) {
                                exportStatusText = "Analysis failed: ${error.message ?: error::class.simpleName}"
                                DesktopDebugLog.error("CourseAnalysis", "Analysis failed: ${error.message ?: error::class.simpleName}")
                            } finally {
                                isAnalyzing = false
                            }
                        }
                    },
                    enabled = analyzeDisabledReason(effectiveSelectedCategoryId) == null && !isAnalyzing
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
                            defaultFileName = DesktopCourseAnalysisExports.defaultPdfFileName(summary)
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
        if (isAnalyzing) {
            IndeterminateProgressDialog(
                title = "Analyzing course",
                message = analysisProgressMessage
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
        Text(
            text = speedStatusText
                ?: "Speed factor is event-wide: 1.00 normal, below 1.00 slower conditions, above 1.00 faster conditions.",
            color = if (speedStatusText?.contains("failed", ignoreCase = true) == true ||
                speedStatusText?.contains("must be", ignoreCase = true) == true
            ) {
                DesktopPalette.Error
            } else {
                DesktopPalette.Disconnected
            },
            fontSize = 13.sp
        )
        CourseAnalysisResultView(analysisResult)
    }

    pendingMissingDataResult?.let { prompt ->
        val summary = prompt.summary
        val canDownloadMissingElevationData = shouldOfferCalculatedRouteElevationDownload(summary)
        var downloadBeforeAnalyzing by remember(prompt) { mutableStateOf(canDownloadMissingElevationData) }
        AlertDialog(
            onDismissRequest = { pendingMissingDataResult = null },
            title = { Text("Course analysis data is incomplete") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    if (canDownloadMissingElevationData) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = downloadBeforeAnalyzing,
                                onCheckedChange = { downloadBeforeAnalyzing = it }
                            )
                            Text(
                                text = "Download missing elevation data from the internet before analyzing",
                                color = DesktopPalette.Black,
                                fontSize = 13.sp
                            )
                        }
                        Text(
                            text = "The imported route already has elevation data. Downloading uses internet elevation data to fill the local calculated-route cache before comparison.",
                            color = DesktopPalette.Disconnected,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingMissingDataResult = null
                        if (!downloadBeforeAnalyzing || !canDownloadMissingElevationData) {
                            onAnalysisResultChange(summary)
                            return@Button
                        }
                        if (isAnalyzing) {
                            return@Button
                        }
                        isAnalyzing = true
                        analysisProgressMessage = "Downloading missing elevation data from the internet."
                        analysisScope.launch {
                            try {
                                val preparation = onDownloadMissingElevations(prompt.categoryId, summary)
                                if (preparation != null) {
                                    exportStatusText = preparation.statusText
                                }
                                analysisProgressMessage = "Re-running analysis with the latest available elevation data."
                                val refreshedSummary = analyzeSelectedCourse(
                                    categoryId = prompt.categoryId,
                                    analysisProjectFile = preparation?.projectFile ?: projectFile,
                                    analysisProtectedCourseInfoByCategoryId = preparation?.protectedCourseInfoByCategoryId
                                        ?: protectedCourseInfoByCategoryId
                                )
                                acceptAnalysisSummary(prompt.categoryId, refreshedSummary)
                            } catch (error: Throwable) {
                                exportStatusText = if (error is CancellationException) {
                                    "Elevation download canceled. Analysis was not run."
                                } else {
                                    "Elevation download failed: ${error.message ?: error::class.simpleName}"
                                }
                                DesktopDebugLog.error("CourseAnalysis", "Elevation preparation failed: ${error.message ?: error::class.simpleName}")
                            } finally {
                                isAnalyzing = false
                                analysisProgressMessage =
                                    "Calculating route metrics, route optimization, wait times, rule checks, and report graphics."
                            }
                        }
                    }
                ) {
                    ButtonLabel(
                        if (downloadBeforeAnalyzing && canDownloadMissingElevationData) {
                            "Download Missing Elevations"
                        } else {
                            "Analyze with Missing Elevations"
                        }
                    )
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

private fun shouldPromptForCourseAnalysisMissingData(summary: DesktopCourseAnalysisSummary): Boolean =
    shouldOfferCalculatedRouteElevationDownload(summary) ||
        summary.missingElements.any { missing -> !missing.isCourseAnalysisElevationOnlyWarning() }

private fun shouldOfferCalculatedRouteElevationDownload(summary: DesktopCourseAnalysisSummary): Boolean =
    summary.hasMissingCalculatedRouteElevationData && !summary.hasMissingElevationData

private fun String.isCourseAnalysisElevationOnlyWarning(): Boolean =
    this == "Route elevation samples are missing or incomplete." ||
        this == "Course object elevations are missing or incomplete." ||
        this == "Control location elevations are missing or incomplete." ||
        startsWith("Calculated route elevation samples are missing from the local elevation cache")

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
        section.speedModel?.let { speedModel ->
            CourseAnalysisRow("Assumed running speed", courseAnalysisSpeedModelText(speedModel))
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
            text = "Imported-route wait-time analysis",
            color = DesktopPalette.Black,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "This subsection estimates Classic fox arrival phases on the imported route and checks whether assigning different fox numbers to the same locations could reduce waiting. If a competitor reaches a fox while it is off the air, timing waits for that fox to transmit, then adds 30 seconds to find and punch before departure. It uses the same elite baseline speed and effective-length movement estimates as the route analysis. Because map passability and accumulated fatigue are not fully modeled, barriers, slow terrain, fatigue, and competitor profile can shift real arrival times and change wait-time outcomes.",
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
        result.summaryGroups.forEachIndexed { index, group ->
            if (index > 0) {
                Divider(color = DesktopPalette.LightGrey, modifier = Modifier.padding(vertical = 4.dp))
            }
            CourseAnalysisSummaryGroupView(group)
        }
        Divider(color = DesktopPalette.LightGrey, modifier = Modifier.padding(vertical = 4.dp))
        Text(
            text = "Course Recommendation",
            color = DesktopPalette.Black,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = result.courseRecommendation.paragraph,
            color = DesktopPalette.Black,
            fontSize = 13.sp
        )
        CourseAnalysisSpeedFactorDetails(result)
        CourseAnalysisMetricRows(result.goodnessMetrics)
        CourseAnalysisProfileComparison(result.profileComparison, result.elevationCacheNotes)
        CourseAnalysisRouteMaps(result.routeMaps)
    }
}

@Composable
private fun CourseAnalysisSummaryGroupView(group: DesktopCourseAnalysisSummaryGroup) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = group.title,
            color = DesktopPalette.Black,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        group.rows.forEach { row ->
            CourseAnalysisRow(row.label, row.value)
        }
    }
}

@Composable
private fun CourseAnalysisSpeedFactorDetails(result: DesktopCourseAnalysisSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Speed model factors",
            color = DesktopPalette.Black,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = courseAnalysisSpeedFactorExplanation(result.speedModel),
            color = DesktopPalette.Black,
            fontSize = 13.sp
        )
        result.categorySpeedFactors.forEach { factor ->
            CourseAnalysisRow(
                label = factor.categoryCodes.joinToString("/"),
                value = "x${twoDecimalText(factor.multiplier)}"
            )
        }
        CourseAnalysisRow("Unmatched categories", "x1.00")
    }
}

@Composable
private fun CourseAnalysisDetailRows(result: DesktopCourseAnalysisSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CourseAnalysisRow("Routes compared", result.calculatedRouteCount.toString())
        if (result.idealOrderMatches == true) {
            CourseAnalysisRow("Imported route", result.providedIdealOrder.joinToString(" -> ").ifBlank { "Unknown" })
            CourseAnalysisRow("Order comparison", "Imported and calculated routes match")
        } else {
            CourseAnalysisRow("Calculated ideal route (calculated fox numbering)", result.calculatedIdealOrder.joinToString(" -> ").ifBlank { "Unknown" })
            CourseAnalysisRow("Imported route", result.providedIdealOrder.joinToString(" -> ").ifBlank { "Unknown" })
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
        CourseAnalysisRow("Imported straight-line length", kilometersText(result.providedStraightLineMeters))
        CourseAnalysisRow("Imported route length", kilometersText(result.routeLengthMeters))
        CourseAnalysisRow("Climb", climbText(result.climbMeters))
        CourseAnalysisRow(
            "Effective length",
            result.metrics.firstOrNull { it.label == "Effective length" }?.value
                ?: kilometersText(result.effectiveLengthMeters)
        )
        CourseAnalysisRow("Assumed running speed", courseAnalysisSpeedModelText(result.speedModel))
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
private fun CourseAnalysisMetricRows(goodnessMetrics: DesktopCourseGoodnessMetrics) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Goodness metrics",
            color = DesktopPalette.Black,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        goodnessMetrics.sharedMetrics.forEach { metric ->
            CourseAnalysisMetricRow(metric)
        }
        goodnessMetrics.groups.forEachIndexed { index, group ->
            if (index > 0 || goodnessMetrics.sharedMetrics.isNotEmpty()) {
                Divider(color = DesktopPalette.LightGrey, modifier = Modifier.padding(vertical = 4.dp))
            }
            Text(
                text = group.title,
                color = DesktopPalette.Black,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            group.metrics.forEach { metric ->
                CourseAnalysisMetricRow(metric)
            }
        }
    }
}

@Composable
private fun CourseAnalysisMetricRow(metric: DesktopCourseGoodnessMetric) {
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
                "Renumbering the fox transmit slots is likely to reduce wait time by ${secondsText(renumbering.currentTotalWaitSeconds - renumbering.bestTotalWaitSeconds)} on this imported route."
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
            CourseAnalysisWaitRows("Renumbered wait times", renumbering.suggestedWaitRows)
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

private fun courseAnalysisSpeedModelText(speedModel: DesktopCourseSpeedModel): String =
    "${twoDecimalText(speedModel.effectiveSpeedMetersPerSecond)} m/s; " +
        "${speedModel.categoryModelLabel} x${twoDecimalText(speedModel.categorySpeedMultiplier)}, " +
        "event x${twoDecimalText(speedModel.compensationFactor)}"

private fun courseAnalysisSpeedFactorExplanation(speedModel: DesktopCourseSpeedModel): String =
    "Assumed running speed equals race-format baseline speed x category multiplier x event speed factor. " +
        "${speedModel.categoryFactorSourceLabel}: ${speedModel.categoryFactorExplanation} " +
        "The event speed factor is adjustable, saved in the Event File, and applies to every category; the current event factor is x${twoDecimalText(speedModel.compensationFactor)}."

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
    onNotesChange: (String) -> Unit,
    onCommit: () -> Unit
) {
    Column(
        modifier = Modifier.width(fixedTableWidth(ControlTableColumns)),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(TableColumnGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = siCodeDraft,
                onValueChange = onSiCodeChange,
                modifier = Modifier
                    .width(ControlTableColumns[0].width)
                    .commitOnEnter(onCommit),
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
                modifier = Modifier
                    .width(ControlTableColumns[2].width)
                    .commitOnEnter(onCommit),
                singleLine = true,
                label = { Text("Public label") }
            )
            TextField(
                value = notesDraft,
                onValueChange = onNotesChange,
                modifier = Modifier
                    .width(ControlTableColumns[3].width)
                    .commitOnEnter(onCommit),
                singleLine = true,
                label = { Text("Notes") }
            )
            Spacer(modifier = Modifier.width(ControlTableColumns[4].width))
        }
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
    var publicLabelDraft by remember(control.id) { mutableStateOf(control.publicLabel) }
    var isPublicLabelFocused by remember(control.id) { mutableStateOf(false) }

    LaunchedEffect(control.siCodeText, isSiCodeFocused) {
        if (!isSiCodeFocused) {
            siCodeDraft = control.siCodeText
        }
    }
    LaunchedEffect(control.publicLabel, isPublicLabelFocused) {
        if (!isPublicLabelFocused) {
            publicLabelDraft = control.publicLabel
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

    fun commitPublicLabelDraft() {
        val normalizedDraft = publicLabelDraft.trim()
        if (normalizedDraft == control.publicLabel) {
            publicLabelDraft = control.publicLabel
            return
        }
        updateControl(publicLabel = normalizedDraft)
    }

    Column(
        modifier = Modifier.width(fixedTableWidth(ControlTableColumns)),
    ) {
        Row(
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
                    }
                    .commitOnEnter(::commitSiCodeDraft),
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
                value = publicLabelDraft,
                onValueChange = { publicLabelDraft = it },
                modifier = Modifier
                    .width(ControlTableColumns[2].width)
                    .onFocusChanged { focusState ->
                        val wasFocused = isPublicLabelFocused
                        isPublicLabelFocused = focusState.isFocused
                        if (wasFocused && !focusState.isFocused) {
                            commitPublicLabelDraft()
                        }
                    }
                    .commitOnEnter(::commitPublicLabelDraft),
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
        Button(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = controlRoleBackgroundColor(type),
                contentColor = Color.Black
            )
        ) {
            ButtonLabel(controlRoleLabel(type, raceType))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            controlRoleOptions(raceType).forEach { option ->
                DropdownMenuItem(
                    modifier = Modifier.background(controlRoleBackgroundColor(option)),
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

private fun controlRoleMismatchWarning(type: ControlPointType, publicLabel: String): String? {
    val inferredRole = ControlRoleLabelRules.inferredRole(publicLabel) ?: return null
    return if (inferredRole == type) {
        null
    } else {
        ControlRoleLabelRules.mismatchWarning(publicLabel, type, inferredRole)
    }
}

private fun duplicateControlRoleWarning(controls: List<EventControl>, changedRole: ControlPointType): String? {
    val counts = ControlRoleCounts.from(controls.map { it.type })
    return when {
        changedRole == ControlPointType.BEACON && counts.beacons > 1 ->
            "This Event File now has ${counts.beacons} Beacon controls. Radio-orienteering events should have exactly one Beacon."
        changedRole == ControlPointType.SEPARATOR && counts.spectators > 1 ->
            "This Event File now has ${counts.spectators} Spectator controls. Sprint courses should have no more than one Spectator."
        else -> null
    }
}

private fun controlCourseRuleWarning(
    controls: List<EventControl>,
    raceType: RaceType,
    changedRole: ControlPointType
): String? {
    if (changedRole != ControlPointType.CONTROL) {
        return null
    }
    val details = controls.map { it.toControlDetails() }
    return when (raceType) {
        RaceType.SPRINT -> {
            val groups = SprintLoopControlGroups.from(details)
            listOfNotNull(
                sprintLoopFoxLimitWarning("Slow-loop", groups.slowLoopFoxes.size),
                sprintLoopFoxLimitWarning("Fast-loop", groups.fastLoopFoxes.size)
            ).joinToString("\n\n").takeIf { it.isNotBlank() }
        }
        RaceType.CLASSIC,
        RaceType.SHORT -> {
            val foxes = details.count { it.type == ControlPointType.CONTROL }
            if (foxes > 5) {
                "This ${raceType.toDisplayLabel()} Event File now has $foxes Fox controls. ${raceType.toDisplayLabel()} events should have exactly five Foxes."
            } else {
                null
            }
        }
        RaceType.FOXORING,
        RaceType.ORIENTEERING -> null
    }
}

private fun sprintLoopFoxLimitWarning(loopLabel: String, foxes: Int): String? =
    if (foxes > 5) {
        "This Sprint Event File now has $foxes $loopLabel Fox controls. Sprint events should have exactly five $loopLabel Foxes."
    } else {
        null
    }

private fun EventControl.toControlDetails(): EventControlDetails =
    EventControlDetails(
        id = id,
        label = label,
        siCode = siCode,
        siCodeText = siCode.toString(),
        type = type,
        typeLabel = EventControlDetails.typeLabel(type),
        scored = scored,
        publicLabel = publicLabel.orEmpty(),
        notes = notes.orEmpty()
    )

private fun combinedControlRoleWarning(vararg warnings: String?): String? =
    warnings.filterNotNull().joinToString("\n\n").takeIf { it.isNotBlank() }

private fun controlRoleBackgroundColor(type: ControlPointType): Color =
    when (type) {
        ControlPointType.CONTROL -> Color(0xFFCDEFD1)
        ControlPointType.SEPARATOR, ControlPointType.BEACON -> Color(0xFFD8E9FF)
    }

/** Shows editable category names with read-only effective race settings. */
@Composable
private fun CategoryDetailsPanel(
    categories: List<EventCategoryDetails>,
    controls: List<EventControlDetails>,
    onRenameCategory: (String, String) -> Unit,
    onUpdateCategoryGender: (String, Boolean) -> Unit,
    onUpdateCategoryControlPoints: (String, String, Boolean) -> Unit,
    onUpdateCategoryPhysicalStats: (String, String, String) -> Unit,
    onAddCategory: (String) -> Boolean,
    onRemoveCategory: (String, Boolean) -> Unit
) {
    val horizontalScrollState = rememberScrollState()
    val tableWidth = fixedTableWidth(CategoryTableColumns)
    val orderedCategories = rememberEditableRowOrder(categories) { it.id }
    var categoryNameDraft by remember { mutableStateOf("") }
    fun addCategory() {
        val didAdd = onAddCategory(categoryNameDraft)
        if (didAdd) {
            categoryNameDraft = ""
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TableColumnGap),
            verticalAlignment = Alignment.Top
        ) {
            Button(
                onClick = ::addCategory,
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
                        onCategoryNameChange = { categoryNameDraft = it },
                        onCommit = {
                            if (categoryNameDraft.isNotBlank()) {
                                addCategory()
                            }
                        }
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
                                onUpdateCategoryGender = onUpdateCategoryGender,
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
        fun unlock() {
            if (passwordDraft.isNotBlank() && onUnlock(passwordDraft)) {
                passwordDraft = ""
            }
        }
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
                modifier = Modifier
                    .width(260.dp)
                    .commitOnEnter(::unlock)
            )
            Button(
                onClick = ::unlock,
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
                onIdealOrderDraftChange = { idealOrderText ->
                    idealOrderDrafts = idealOrderDrafts + (categoryId to idealOrderText)
                },
                onIdealOrderCommit = { idealOrderText ->
                    idealOrderDrafts = idealOrderDrafts + (categoryId to idealOrderText.trim())
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
    fun applyLocationUpdate() {
        val controlId = selectedControlId ?: return
        if (canApply) {
            onStatusTextChange(onUpdateControlLocation(controlId, latitudeDraft, longitudeDraft))
        }
    }

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
                modifier = Modifier
                    .width(150.dp)
                    .commitOnEnter(::applyLocationUpdate)
            )
            TextField(
                value = longitudeDraft,
                onValueChange = { longitudeDraft = it },
                label = { Text("Longitude") },
                singleLine = true,
                modifier = Modifier
                    .width(150.dp)
                    .commitOnEnter(::applyLocationUpdate)
            )
            Button(
                onClick = ::applyLocationUpdate,
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
    onIdealOrderDraftChange: (String) -> Unit,
    onIdealOrderCommit: (String) -> Unit
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
            onIdealOrderDraftChange = onIdealOrderDraftChange,
            onIdealOrderCommit = onIdealOrderCommit,
            modifier = Modifier.width(ProtectedCourseOrderTableColumns[1].width)
        )
    }
}

@Composable
private fun ProtectedIdealOrderEditor(
    idealOrderDraft: String,
    assignedControls: List<EventControl>,
    onIdealOrderDraftChange: (String) -> Unit,
    onIdealOrderCommit: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var hasPendingTextEdit by remember { mutableStateOf(false) }
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
            onValueChange = {
                hasPendingTextEdit = true
                onIdealOrderDraftChange(it)
            },
            enabled = assignedControls.isNotEmpty(),
            modifier = Modifier
                .width(ProtectedIdealOrderTextFieldWidth)
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused && hasPendingTextEdit) {
                        hasPendingTextEdit = false
                        onIdealOrderCommit(idealOrderDraft)
                    }
                }
                .commitOnEnter {
                    if (hasPendingTextEdit) {
                        hasPendingTextEdit = false
                        onIdealOrderCommit(idealOrderDraft)
                    }
                },
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
                            val nextText = appendPublicControlLabel(idealOrderDraft, publicLabel)
                            hasPendingTextEdit = false
                            onIdealOrderDraftChange(nextText)
                            onIdealOrderCommit(nextText)
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
    onCategoryNameChange: (String) -> Unit,
    onCommit: () -> Unit
) {
    Row(
        modifier = Modifier.width(fixedTableWidth(CategoryTableColumns)),
        horizontalArrangement = Arrangement.spacedBy(TableColumnGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = categoryNameDraft,
            onValueChange = onCategoryNameChange,
            modifier = Modifier
                .width(CategoryTableColumns[0].width)
                .commitOnEnter(onCommit),
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
    }
}

/** Shows one editable category-name row plus read-only derived category settings. */
@Composable
private fun CategoryDetailRow(
    category: EventCategoryDetails,
    controls: List<EventControlDetails>,
    onRenameCategory: (String, String) -> Unit,
    onUpdateCategoryGender: (String, Boolean) -> Unit,
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
        modifier = Modifier
            .width(fixedTableWidth(CategoryTableColumns))
            .background(categoryGenderBackground(category.isMan)),
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
                }
                .commitOnEnter(::applyCategoryNameDraft),
            singleLine = true,
            label = { Text("Category") }
        )
        CategoryGenderPicker(
            isMan = category.isMan,
            onGenderChange = { isMan -> onUpdateCategoryGender(category.id, isMan) },
            modifier = Modifier.width(CategoryTableColumns[1].width)
        )
        TextField(
            value = lengthMetersDraft,
            onValueChange = {
                lengthMetersDraft = it
                applyPhysicalStats(nextLength = it)
            },
            modifier = Modifier
                .width(CategoryTableColumns[2].width)
                .commitOnEnter { applyPhysicalStats() },
            singleLine = true,
            label = { Text("Length m") }
        )
        TextField(
            value = climbMetersDraft,
            onValueChange = {
                climbMetersDraft = it
                applyPhysicalStats(nextClimb = it)
            },
            modifier = Modifier
                .width(CategoryTableColumns[3].width)
                .commitOnEnter { applyPhysicalStats() },
            singleLine = true,
            label = { Text("Climb m") }
        )
        Text(
            category.raceTypeLabel,
            modifier = Modifier.width(CategoryTableColumns[4].width),
            color = DesktopPalette.Black,
            fontSize = 13.sp
        )
        Text(
            category.raceBandLabel,
            modifier = Modifier.width(CategoryTableColumns[5].width),
            color = DesktopPalette.Black,
            fontSize = 13.sp
        )
        Text(
            desktopTimeLimitText(category.timeLimitText),
            modifier = Modifier.width(CategoryTableColumns[6].width),
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
            modifier = Modifier.width(CategoryTableColumns[7].width)
        )
    }
}

@Composable
private fun CategoryGenderPicker(
    isMan: Boolean,
    onGenderChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Button(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = if (isMan) Color(0xFFD5EAFE) else Color(0xFFFFD8D8),
                contentColor = DesktopPalette.Black
            )
        ) {
            ButtonLabel(if (isMan) "Men" else "Women")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                onClick = {
                    expanded = false
                    if (!isMan) {
                        onGenderChange(true)
                    }
                }
            ) {
                Text("Men")
            }
            DropdownMenuItem(
                onClick = {
                    expanded = false
                    if (isMan) {
                        onGenderChange(false)
                    }
                }
            ) {
                Text("Women")
            }
        }
    }
}

private fun categoryGenderBackground(isMan: Boolean): Color =
    if (isMan) CategoryMenBackground else CategoryWomenBackground

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
                }
                .commitOnEnter {
                    if (hasPendingTextEdit) {
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
    eventFileWorkingFolder: Path,
    parentSeriesText: String?,
    onRenameRace: (String) -> Unit,
    onUpdateRaceStartDateTime: (String) -> Unit,
    onUpdateRaceSettings: (RaceType, RaceLevel, RaceBand, String) -> Unit,
    onUpdateEventFileName: (String) -> Boolean,
    onOpenEventFileWorkingFolder: () -> Unit
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

    fun commitRaceNameDraft() {
        if (shouldPromptForEventStartAfterNameEdit) {
            shouldPromptForEventStartAfterNameEdit = false
            promptForEventStart("event name")
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        parentSeriesText?.let {
            Text(
                text = it,
                color = DesktopPalette.SeriesNavigation,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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
                            commitRaceNameDraft()
                        }
                        wasRaceNameFocused = focusState.isFocused
                    }
                    .commitOnEnter(::commitRaceNameDraft),
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
                .commitOnEnter(::commitEventFileNameDraft),
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SelectionContainer(modifier = Modifier.weight(1f)) {
                Text(
                    text = desktopEventFileFolderText(eventFilePath, eventFileWorkingFolder),
                    color = DesktopPalette.Disconnected,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Button(onClick = onOpenEventFileWorkingFolder) {
                ButtonLabel("Open Folder")
            }
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
    fun useSelectedDateTime() {
        selectedDateTime?.let(onValueSelected)
    }

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
                        modifier = Modifier
                            .width(64.dp)
                            .commitOnEnter(::useSelectedDateTime),
                        label = { Text("Hour") }
                    )
                    TextField(
                        value = minuteText,
                        onValueChange = { minuteText = it.take(2) },
                        modifier = Modifier
                            .width(64.dp)
                            .commitOnEnter(::useSelectedDateTime),
                        label = { Text("Min") }
                    )
                    TextField(
                        value = secondText,
                        onValueChange = { secondText = it.take(2) },
                        modifier = Modifier
                            .width(64.dp)
                            .commitOnEnter(::useSelectedDateTime),
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
                onClick = ::useSelectedDateTime,
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
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Button(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled
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

private fun Modifier.commitOnEnter(onCommit: () -> Unit): Modifier =
    onPreviewKeyEvent { event ->
        if (event.key == Key.Enter) {
            if (event.type == KeyEventType.KeyUp) {
                onCommit()
            }
            true
        } else {
            false
        }
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
private fun PublicResultsSiteWorkflowPanel() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Cloudflare Pages workflow",
            color = DesktopPalette.Disconnected,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Save Cloudflare Settings once with the Pages project name, branch, account ID, and API token. Generate Public Results Site writes the static site folder for the current event. Public Site Preview opens the generated event folder locally for review. Publish Public Results Site uploads the generated site root to Cloudflare Pages.",
            color = DesktopPalette.Black,
            fontSize = 14.sp
        )
        Text(
            text = "Generate after result changes, preview before publishing, then publish when the preview matches what web visitors should see.",
            color = DesktopPalette.Black,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun PublicResultsSiteLinkPanel(
    publishedUrl: String?,
    onOpenUrl: (String) -> Unit,
    onCopyUrl: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Published public results",
            color = DesktopPalette.Disconnected,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        if (publishedUrl.isNullOrBlank()) {
            Text(
                text = "Publish the public results site first. After a successful publish, this screen will show the public event link and QR code.",
                color = DesktopPalette.Black,
                fontSize = 14.sp
            )
            return@Column
        }

        val qrCode = remember(publishedUrl) {
            desktopEventFileTransferQrCode(publishedUrl, size = 360)
        }
        Image(
            bitmap = qrCode.toComposeImageBitmap(),
            contentDescription = "Published public results QR code",
            modifier = Modifier
                .width(280.dp)
                .height(280.dp)
                .align(Alignment.Start),
            contentScale = ContentScale.Fit
        )
        Text(
            text = "Scan the QR code or open the link below to view the published event results.",
            color = DesktopPalette.Black,
            fontSize = 14.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { onOpenUrl(publishedUrl) }) {
                ButtonLabel("Open Public Results")
            }
            Button(onClick = { onCopyUrl(publishedUrl) }) {
                ButtonLabel("Copy Link")
            }
        }
        Text(
            text = publishedUrl,
            color = DesktopPalette.Primary,
            fontSize = 14.sp,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenUrl(publishedUrl) },
            softWrap = true
        )
        TextField(
            value = publishedUrl,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
        )
    }
}

@Composable
private fun LabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "",
    onCommit: (() -> Unit)? = null
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
            placeholder = placeholder.takeIf { it.isNotBlank() }?.let { placeholderText ->
                { Text(placeholderText) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onCommit != null) Modifier.commitOnEnter(onCommit) else Modifier),
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
        bytes >= 1_000_000_000L -> "${oneDecimal(bytes.toDouble() / 1_000_000_000.0)} GB"
        bytes >= 1_000_000L -> "${oneDecimal(bytes.toDouble() / 1_000_000.0)} MB"
        bytes >= 1_000L -> "${oneDecimal(bytes.toDouble() / 1_000.0)} KB"
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
