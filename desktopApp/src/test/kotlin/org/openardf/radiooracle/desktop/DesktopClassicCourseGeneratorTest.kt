package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class DesktopClassicCourseGeneratorTest {
    @Test
    fun generatesIdealClassicCourseCombinationsFromPointPlacemarksOnly() {
        val path = Files.createTempFile("classic-course-points", ".kml")
        Files.writeString(path, coursePointsKml(includeLineString = true))

        val result = DesktopClassicCourseGenerator.generate(path)

        assertEquals(5, result.foxes.size)
        assertEquals(listOf(3, 4, 5), result.groups.map { it.foxCount })
        assertEquals(10, result.groups.single { it.foxCount == 3 }.rows.size)
        assertEquals(5, result.groups.single { it.foxCount == 4 }.rows.size)
        assertEquals(1, result.groups.single { it.foxCount == 5 }.rows.size)
        assertTrue(result.rows.zipWithNext().all { (left, right) ->
            if (left.foxCount == right.foxCount) {
                left.effectiveLengthMeters <= right.effectiveLengthMeters
            } else {
                true
            }
        })
        assertTrue(result.rows.all { row ->
            row.orderLabels.first() == "S" &&
                row.orderLabels.takeLast(2) == listOf("B", "F")
        })
        assertTrue(result.rows.any { it.matchingCategories.isNotEmpty() })
        assertTrue(DesktopClassicCourseGenerator.reportText(result).contains("THREE-FOX COURSES"))
    }

    @Test
    fun categoryMatchingRequiresKnownClimbData() {
        val path = Files.createTempFile("classic-course-points-no-elev", ".kml")
        Files.writeString(path, coursePointsKml(includeElevations = false))

        val result = DesktopClassicCourseGenerator.generate(path)

        assertTrue(result.rows.all { it.matchingCategories.isEmpty() })
    }

    @Test
    fun rejectsCoursePointsOutsideClassicGeneratorShape() {
        val path = Files.createTempFile("classic-course-points-spectator", ".kml")
        Files.writeString(
            path,
            coursePointsKml(extraPlacemark = pointPlacemark("Spectator", -94.97, 39.0, 100.0))
        )

        val error = runCatching { DesktopClassicCourseGenerator.generate(path) }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("spectator", ignoreCase = true))
    }

    @Test
    fun exportsPdfAndGreenCourseCandidateKml() {
        val path = Files.createTempFile("classic-course-points", ".kml")
        Files.writeString(path, coursePointsKml())
        val result = DesktopClassicCourseGenerator.generate(path)
        val pdfPath = Files.createTempFile("classic-course-generator", ".pdf")

        val exports = DesktopClassicCourseGenerator.exportPdfAndKml(pdfPath, result)

        val bytes = Files.readAllBytes(exports.pdfPath)
        assertTrue(String(bytes.take(8).toByteArray()).startsWith("%PDF-1.4"))
        val text = String(bytes)
        assertTrue(text.contains("Classic Course Generator"))
        assertTrue(text.contains("THREE-FOX COURSES"))
        assertFalse(text.contains("LineString"))

        val kmlText = Files.readString(exports.kmlPath)
        assertTrue(kmlText.contains("<name>Course Objects</name>"))
        assertTrue(kmlText.contains("<name>Category-matching course candidates</name>"))
        assertEquals(result.rows.count { it.hasCategoryMatch }, kmlText.countOccurrences("<LineString>"))
        assertTrue(kmlText.contains("<Style id=\"${DesktopCourseKmlStyle.StartStyleId}\">"))
        assertTrue(kmlText.contains("<Style id=\"${DesktopCourseKmlStyle.FinishStyleId}\">"))
        assertTrue(kmlText.contains("<Style id=\"${DesktopCourseKmlStyle.DonutStyleId}\">"))
        assertTrue(kmlText.contains(DesktopCourseKmlStyle.StartIconUrl))
        assertTrue(kmlText.contains(DesktopCourseKmlStyle.FinishIconUrl))
        assertTrue(kmlText.contains(DesktopCourseKmlStyle.DonutIconUrl))
        assertTrue(kmlText.contains("<color>${DesktopCourseKmlStyle.MarkerColor}</color>"))
        assertEquals(1, kmlText.countOccurrences("<styleUrl>#${DesktopCourseKmlStyle.StartStyleId}</styleUrl>"))
        assertEquals(1, kmlText.countOccurrences("<styleUrl>#${DesktopCourseKmlStyle.FinishStyleId}</styleUrl>"))
        assertEquals(6, kmlText.countOccurrences("<styleUrl>#${DesktopCourseKmlStyle.DonutStyleId}</styleUrl>"))
        val routeStyles = kmlText.routeLineStyleBlocks()
        assertEquals(result.rows.count { it.hasCategoryMatch }, routeStyles.size)
        assertTrue(routeStyles.all { it.contains("<width>3</width>") })
        val routeColors = routeStyles.mapNotNull { Regex("""<color>([^<]+)</color>""").find(it)?.groupValues?.get(1) }
        assertEquals(routeStyles.size, routeColors.size)
        assertEquals(routeColors.size, routeColors.toSet().size)
        assertFalse(routeColors.any { it in setOf("ffffffff", "ff000000", "ff00ff00") })
        assertEquals(1, kmlText.countOccurrences("<name>Start</name>"))
        assertEquals(1, kmlText.countOccurrences("<name>Finish</name>"))
        assertEquals(1, kmlText.countOccurrences("<name>Beacon</name>"))
        assertEquals(1, kmlText.countOccurrences("<name>FOX1</name>"))
        assertFalse(kmlText.contains("No category match"))
    }

    private fun coursePointsKml(
        includeLineString: Boolean = false,
        includeElevations: Boolean = true,
        extraPlacemark: String = ""
    ): String {
        fun elevation(value: Double): Double? = value.takeIf { includeElevations }
        val lineString = if (includeLineString) {
            """
            <Placemark>
              <name>Ignored route</name>
              <LineString><coordinates>-90.0,35.0,0 -90.1,35.1,0</coordinates></LineString>
            </Placemark>
            """.trimIndent()
        } else {
            ""
        }
        return """
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Document>
                ${pointPlacemark("Start", -95.000, 39.000, elevation(100.0))}
                ${pointPlacemark("FOX1", -94.990, 39.000, elevation(100.0))}
                ${pointPlacemark("FOX2", -94.980, 39.000, elevation(105.0))}
                ${pointPlacemark("FOX3", -94.970, 39.000, elevation(105.0))}
                ${pointPlacemark("FOX4", -94.960, 39.000, elevation(110.0))}
                ${pointPlacemark("FOX5", -94.950, 39.000, elevation(110.0))}
                ${pointPlacemark("Beacon", -94.940, 39.000, elevation(110.0))}
                ${pointPlacemark("Finish", -94.930, 39.000, elevation(110.0))}
                $extraPlacemark
                $lineString
              </Document>
            </kml>
        """.trimIndent()
    }

    private fun pointPlacemark(name: String, longitude: Double, latitude: Double, elevation: Double?): String {
        val coordinateText = if (elevation == null) {
            "$longitude,$latitude"
        } else {
            "$longitude,$latitude,$elevation"
        }
        return """
            <Placemark>
              <name>$name</name>
              <Point><coordinates>$coordinateText</coordinates></Point>
            </Placemark>
        """.trimIndent()
    }

    private fun String.countOccurrences(needle: String): Int =
        split(needle).size - 1

    private fun String.routeLineStyleBlocks(): List<String> =
        Regex("""<Style id="classicCourseCandidateRoute-\d+">.*?</Style>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(this)
            .map { it.value }
            .toList()
}
