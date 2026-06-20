package org.openardf.radiooracle.desktop

import androidx.compose.ui.graphics.Color

/**
 * Android-derived colors used by the first desktop UI shell.
 *
 * Keep these values aligned with `app/src/main/res/values/colors.xml` until the
 * two platforms share a generated or common design-token source.
 */
object DesktopPalette {
    const val PRIMARY_ARGB = 0xFF6200EEL
    const val PRIMARY_VARIANT_ARGB = 0xFF3700B3L
    const val SECONDARY_ARGB = 0xFF03DAC5L
    const val SECONDARY_VARIANT_ARGB = 0xFF018786L
    const val BLACK_ARGB = 0xFF000000L
    const val WHITE_ARGB = 0xFFFFFFFFL
    const val ERROR_ARGB = 0xFFC62828L
    const val DISCONNECTED_ARGB = 0xFF505050L
    const val READING_ARGB = 0xFFFD8204L
    const val CONNECTED_ARGB = 0xFF0AE62FL
    const val WARNING_ARGB = 0xFFFFFF00L
    const val WARNING_BACKGROUND_ARGB = 0xFFFFFDE7L
    const val NAVIGATION_BACKGROUND_ARGB = 0xFFEAF4FFL
    const val LIGHT_GREY_ARGB = 0xFFCBC8C8L

    val Primary = Color(PRIMARY_ARGB)
    val PrimaryVariant = Color(PRIMARY_VARIANT_ARGB)
    val Secondary = Color(SECONDARY_ARGB)
    val SecondaryVariant = Color(SECONDARY_VARIANT_ARGB)
    val Black = Color(BLACK_ARGB)
    val White = Color(WHITE_ARGB)
    val Error = Color(ERROR_ARGB)
    val Disconnected = Color(DISCONNECTED_ARGB)
    val Reading = Color(READING_ARGB)
    val Connected = Color(CONNECTED_ARGB)
    val Warning = Color(WARNING_ARGB)
    val WarningBackground = Color(WARNING_BACKGROUND_ARGB)
    val NavigationBackground = Color(NAVIGATION_BACKGROUND_ARGB)
    val LightGrey = Color(LIGHT_GREY_ARGB)
}

/**
 * Navigation destinations mirrored from the Android bottom navigation.
 *
 * The initial desktop app is intentionally a shell, but using the Android
 * vocabulary now keeps later event-admin screens familiar to existing users.
 */
enum class DesktopSection(val label: String) {
    WorkflowHome("Radio-Oracle"),
    EventFile("Event File"),
    Races("Races"),
    Categories("Categories"),
    ProtectedCourseOrder("Course Order"),
    Competitors("Competitors"),
    StartList("Start List"),
    Series("Event Series"),
    SeriesEvents("Series Events"),
    SeriesStartFairness("Series Start Fairness"),
    SeriesCompetitorMatching("Series Competitor Matching"),
    SeriesValidation("Series Validation"),
    SeriesSettings("Series Settings"),
    Controls("Controls"),
    CourseAnalysis("Course Analyzer"),
    ElevationCache("Elevation Data"),
    ElevationCacheImport("Import Elevation Data"),
    ControlsImportExport("Import/Export"),
    ControlsRouteKmlImport("Import Controls KML/KMZ"),
    Readouts("Readouts"),
    SiReadoutSettings("SI Readout Settings"),
    InForest("In Forest"),
    Results("Results"),
    PublicResultsSite("Cloudflare Website"),
    PublicResultsLink("View Public Results"),
    LiveResultsOverview("Live Results"),
    LocalResultsWebServer("Local Web Server"),
    RobisLiveResults("ROBIS"),
    DisplaySettings("Display Settings"),
    EventDiagnostics("Readiness"),
    Settings("Settings")
}
