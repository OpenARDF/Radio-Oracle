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
}
