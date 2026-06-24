package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class DesktopFoxoringCourseGeneratorTest {
    @Test
    fun generatesFoxoringCourseGroupsFromFourThroughOneLessThanTotalFoxes() {
        val path = Files.createTempFile("foxoring-course-points", ".kml")
        Files.writeString(path, foxoringCoursePointsKml(foxCount = 7, includeBeacon = true))

        val result = DesktopFoxoringCourseGenerator.generate(path, elevationLookup = { null })

        assertEquals("Foxoring Course Generator", result.generatorTitle)
        assertEquals("Foxoring", result.formatLabel)
        assertEquals(7, result.foxes.size)
        assertEquals(listOf(4, 5, 6), result.groups.map { it.foxCount })
        assertEquals(35, result.groups.single { it.foxCount == 4 }.rows.size)
        assertEquals(21, result.groups.single { it.foxCount == 5 }.rows.size)
        assertEquals(7, result.groups.single { it.foxCount == 6 }.rows.size)
        assertTrue(result.rows.all { row ->
            row.orderLabels.first() == "S" &&
                row.orderLabels.takeLast(2) == listOf("B", "F")
        })
        assertTrue(result.rows.any { it.hasCategoryMatch })
        assertTrue(DesktopFoxoringCourseGenerator.reportText(result).contains("FOUR-FOX COURSES"))
        assertTrue(DesktopFoxoringCourseGenerator.reportText(result).contains("SIX-FOX COURSES"))
    }

    @Test
    fun exportsFoxoringPdfAndGreenCourseCandidateKml() {
        val path = Files.createTempFile("foxoring-course-points", ".kml")
        Files.writeString(path, foxoringCoursePointsKml(foxCount = 7, includeBeacon = false))
        val result = DesktopFoxoringCourseGenerator.generate(path, elevationLookup = { null })
        val pdfPath = Files.createTempFile("foxoring-course-generator", ".pdf")

        val exports = DesktopFoxoringCourseGenerator.exportPdfAndKml(pdfPath, result)

        val pdfText = Files.readString(exports.pdfPath)
        assertTrue(pdfText.startsWith("%PDF-1.4"))
        assertTrue(pdfText.contains("Foxoring Course Generator"))
        assertTrue(pdfText.contains("FOUR-FOX COURSES"))

        val kmlText = Files.readString(exports.kmlPath)
        assertTrue(kmlText.contains("Foxoring Course Generator"))
        assertTrue(kmlText.contains("<name>Course Objects</name>"))
        assertTrue(kmlText.contains("<name>Category-matching course candidates</name>"))
        assertEquals(result.rows.count { it.hasCategoryMatch }, kmlText.countOccurrences("<LineString>"))
        assertFalse(kmlText.contains("No category match"))
    }

    @Test
    fun rejectsFoxoringCoursePointFilesWithMoreThanTwelveFoxes() {
        val path = Files.createTempFile("foxoring-course-points-too-many", ".kml")
        Files.writeString(path, foxoringCoursePointsKml(foxCount = 13, includeBeacon = false))

        val error = runCatching { DesktopFoxoringCourseGenerator.generate(path) }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("between 5 and 12 fox points"))
    }

    private fun foxoringCoursePointsKml(foxCount: Int, includeBeacon: Boolean): String {
        val foxes = (1..foxCount).joinToString("\n") { index ->
            pointPlacemark("FOX$index", -95.000 + index * 0.008, 39.000, 100.0, "SI=${220 + index}")
        }
        val beacon = if (includeBeacon) {
            pointPlacemark("Beacon", -95.000 + (foxCount + 1) * 0.008, 39.000, 100.0, "SI=299")
        } else {
            ""
        }
        val finishOffset = if (includeBeacon) foxCount + 2 else foxCount + 1
        return """
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Document>
                ${pointPlacemark("Start", -95.000, 39.000, 100.0, "SI=200")}
                $foxes
                $beacon
                ${pointPlacemark("Finish", -95.000 + finishOffset * 0.008, 39.000, 100.0, "SI=201")}
                <Placemark>
                  <name>Ignored route</name>
                  <LineString><coordinates>-90.0,35.0,0 -90.1,35.1,0</coordinates></LineString>
                </Placemark>
              </Document>
            </kml>
        """.trimIndent()
    }

    private fun pointPlacemark(
        name: String,
        longitude: Double,
        latitude: Double,
        elevation: Double,
        description: String
    ): String =
        """
            <Placemark>
              <name>$name</name>
              <description>$description</description>
              <Point><coordinates>$longitude,$latitude,$elevation</coordinates></Point>
            </Placemark>
        """.trimIndent()

    private fun String.countOccurrences(needle: String): Int =
        split(needle).size - 1
}
