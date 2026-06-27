package org.openardf.radiooracle.desktop

internal object DesktopCourseKmlStyle {
    const val MarkerScale = "1.2"

    // KML colors are encoded as AABBGGRR; this is 100% opacity with RGB 237:114:239.
    const val MarkerColor = "ffef72ed"

    const val DonutIconUrl = "http://maps.google.com/mapfiles/kml/shapes/donut.png"
    const val StartIconUrl = "http://maps.google.com/mapfiles/kml/shapes/triangle.png"
    const val FinishIconUrl = "http://maps.google.com/mapfiles/kml/shapes/target.png"
    const val WaypointIconUrl = "http://maps.google.com/mapfiles/kml/shapes/placemark_circle.png"

    const val DonutStyleId = "courseControlDoughnutStyle"
    const val StartStyleId = "courseStartStyle"
    const val FinishStyleId = "courseFinishStyle"
    const val WaypointStyleId = "courseWaypointCircleStyle"

    fun pointStyleDefinitions(indent: String = "    ", includeWaypoint: Boolean = true): String =
        buildString {
            appendPointStyle(indent, DonutStyleId, DonutIconUrl)
            appendPointStyle(indent, FinishStyleId, FinishIconUrl)
            appendPointStyle(indent, StartStyleId, StartIconUrl)
            if (includeWaypoint) {
                appendPointStyle(indent, WaypointStyleId, WaypointIconUrl)
            }
        }

    private fun StringBuilder.appendPointStyle(indent: String, styleId: String, iconUrl: String) {
        appendLine("$indent<Style id=\"$styleId\">")
        appendLine("$indent  <IconStyle><scale>$MarkerScale</scale><color>$MarkerColor</color><colorMode>normal</colorMode>")
        appendLine("$indent    <Icon><href>$iconUrl</href></Icon>")
        appendLine("$indent  </IconStyle>")
        appendLine("$indent  <LabelStyle><color>$MarkerColor</color><colorMode>normal</colorMode></LabelStyle>")
        appendLine("$indent</Style>")
    }
}
