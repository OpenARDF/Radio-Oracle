package org.openardf.radiooracle.shared.files

/** Operator guidance; KML parsing and writing remain in the existing format services. */
data class KmlFormatGuide(val id: String, val actionNote: String) {
    val introduction: String = "Open and edit KML/KMZ files in Google Earth or similar compatible mapping apps. KML describes geographic features; KMZ packages KML in a compressed file."
    val structure: String = "Radio-Oracle uses named point placemarks for controls, Start, Finish, Beacon, Spectator, and other course points. LineStrings describe routes. Name each category route using a regulation category, for example M21 route."
    val routeRule: String = "Place an explicit route vertex at each intended control. A line merely passing near or across a control does not establish course membership. Additional vertices describe bends and mandatory route geometry."
    val details: List<Pair<String, String>> = listOf(
        "Point name" to "Keep course-element names recognizable: Start, Finish, Beacon, Spectator, or Fox 1. Sprint starter files use 1–5 and 1F–5F.",
        "Location" to "Move each point to its actual position. KML coordinate order is longitude, latitude, and optional elevation in meters.",
        "SI=31" to "Put this in the point description to supply a SPORTident station-code hint. Keep it consistent with the intended control.",
        "Text=\"Fox 1\"" to "Optional description property for display text separate from the placemark name.",
        "SS=0.80" to "Optional analysis speed factor in the description. On a course point it applies to the following leg. Accepted values are 0.01 through 4.99.",
        "Icons and styles" to "Control appearance. Keep recognizable names and identifiers when changing icons or colors.",
        "ExtendedData" to "Preserve metadata written by Radio-Oracle or supported course-setting software. Arbitrary custom properties are not automatically recognized.",
        "Route name and geometry" to "Use a regulation category, such as M21, in the route name. Draw the LineString from Start to Finish with vertices at the intended controls. Review category associations during import.",
        "Mandatory waypoints" to "Affect the route line but receive no point markers or labels in Radio-Oracle’s 2D reports.",
        "Elevation" to "Google Earth ground display does not guarantee valid terrain elevations in the file. Do not assume a zero altitude is the actual terrain elevation.",
        "Getting started" to "Use Course Tools > Create Course for a starter KML, then move its example points and edit its example routes for the real course.",
        "Import and exchange" to "Review the import preview before applying changes. KML/KMZ exchanges course geometry, not the complete Race File."
    )
}

object KmlFormatGuides {
    fun controlsImport() = KmlFormatGuide("controls-import",
        "Import named point placemarks to match or create controls and import their locations. Route LineStrings are optional for this action.")
    fun courseImport() = KmlFormatGuide("course-import",
        "Import course points and route LineStrings for course analysis. Include category routes and review their associations before applying the import.")
    fun courseExport() = KmlFormatGuide("course-export",
        "Export the Race File’s controls and available applied course-route geometry. Open the resulting KML/KMZ in Google Earth or a similar app to inspect or edit it.")
}
