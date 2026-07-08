/*
 * MIT License
 *
 * Copyright (c) 2025 Pavel Kolský
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

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
    const val ORIENTEERING_FLAG_ORANGE_ARGB = 0xFFFF7A00L
    const val CONNECTED_ARGB = 0xFF0AE62FL
    const val WARNING_ARGB = 0xFFE86F00L
    const val WARNING_BACKGROUND_ARGB = 0xFFFFFDE7L
    const val NAVIGATION_BACKGROUND_ARGB = 0xFFEAF4FFL
    const val SERIES_NAVIGATION_ARGB = 0xFFFFD59EL
    const val TOOLS_NAVIGATION_ARGB = 0xFFFFF176L
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
    val OrienteeringFlagOrange = Color(ORIENTEERING_FLAG_ORANGE_ARGB)
    val Connected = Color(CONNECTED_ARGB)
    val Warning = Color(WARNING_ARGB)
    val WarningBackground = Color(WARNING_BACKGROUND_ARGB)
    val NavigationBackground = Color(NAVIGATION_BACKGROUND_ARGB)
    val SeriesNavigation = Color(SERIES_NAVIGATION_ARGB)
    val ToolsNavigation = Color(TOOLS_NAVIGATION_ARGB)
    val LightGrey = Color(LIGHT_GREY_ARGB)
}

/**
 * Navigation destinations mirrored from the Android bottom navigation.
 *
 * The initial desktop app is intentionally a shell, but using the Android
 * vocabulary now keeps later race-admin screens familiar to existing users.
 */
enum class DesktopSection(val label: String) {
    WorkflowHome("Radio-Oracle"),
    EventFile("Race File"),
    Races("Races"),
    Categories("Categories"),
    ProtectedCourseOrder("Course Order"),
    Competitors("Competitors"),
    CompetitorsImportExport("Competitor Files"),
    StartList("Start List"),
    Series("Race Series"),
    SeriesEvents("Series Races"),
    SeriesStartFairness("Series Start Fairness"),
    SeriesCompetitorMatching("Series Competitor Matching"),
    SeriesValidation("Series Validation"),
    SeriesSettings("Series Settings"),
    Controls("Controls"),
    CourseAnalysis("Course Analyzer"),
    ElevationCache("Elevation Data"),
    ElevationCacheImport("Import Elevation Data"),
    Tools("More..."),
    EventValidator("Race Validator"),
    KmlTools("Course Tools"),
    SportIdentTools("SPORTident"),
    SportIdentTimeSync("Time Sync"),
    KmlMoveCourse("Move Course"),
    KmlCreateCourse("Create Course"),
    Kml2dGraphic("2D Graphic"),
    KmlRouteGenerator("Route Generator"),
    ControlsImportExport("Control Files"),
    ControlsRouteKmlImport("Import Controls KML/KMZ"),
    Readouts("Readouts"),
    SiReadoutSettings("SI Readout Settings"),
    InForest("In Forest"),
    Results("Results"),
    AwardsResults("Awards Results"),
    PublicResultsSite("Cloudflare Website"),
    PublicResultsLink("View Public Results"),
    LiveResultsOverview("Live Results"),
    LocalResultsWebServer("Local Web Server"),
    RobisLiveResults("ROBIS"),
    DisplaySettings("Display Settings"),
    EventDiagnostics("Readiness"),
    Settings("Settings")
}
