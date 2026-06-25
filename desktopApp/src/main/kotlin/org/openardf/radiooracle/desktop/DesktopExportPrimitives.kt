package org.openardf.radiooracle.desktop

import java.util.Locale

object DesktopExportPrimitives {
    fun kmlCoordinate(point: CourseGeoPoint): String =
        if (point.elevationMeters == null) {
            String.format(Locale.US, "%.8f,%.8f", point.longitude, point.latitude)
        } else {
            String.format(Locale.US, "%.8f,%.8f,%.2f", point.longitude, point.latitude, point.elevationMeters)
        }

    fun compactKmlCoordinate(
        longitude: Double,
        latitude: Double,
        elevationMeters: Double?
    ): String =
        listOfNotNull(
            compactDecimal(longitude),
            compactDecimal(latitude),
            elevationMeters?.let(::compactDecimal)
        ).joinToString(",")

    fun compactDecimal(value: Double, maxDecimalPlaces: Int = 8): String =
        String.format(Locale.US, "%.${maxDecimalPlaces}f", value).trimEnd('0').trimEnd('.')

    fun xmlText(text: String): String =
        text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    fun pdfText(text: String): String =
        text.map { character ->
            when (character) {
                '\\' -> "\\\\"
                '(' -> "\\("
                ')' -> "\\)"
                in ' '..'~' -> character.toString()
                else -> "?"
            }
        }.joinToString("")
}
