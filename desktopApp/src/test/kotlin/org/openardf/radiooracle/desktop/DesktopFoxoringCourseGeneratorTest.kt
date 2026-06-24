package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class DesktopFoxoringCourseGeneratorTest {
    @Test
    fun generatesFoxoringCourseGroupsFromFourThroughAllFoxes() {
        val path = Files.createTempFile("foxoring-course-points", ".kml")
        Files.writeString(path, foxoringCoursePointsKml(foxCount = 7, includeBeacon = true))

        val result = DesktopFoxoringCourseGenerator.generate(path, elevationLookup = { null })

        assertEquals("Foxoring Course Generator", result.generatorTitle)
        assertEquals("Foxoring", result.formatLabel)
        assertEquals(7, result.foxes.size)
        assertEquals(listOf(4, 5, 6, 7), result.groups.map { it.foxCount })
        assertEquals(35, result.groups.single { it.foxCount == 4 }.rows.size)
        assertEquals(21, result.groups.single { it.foxCount == 5 }.rows.size)
        assertEquals(7, result.groups.single { it.foxCount == 6 }.rows.size)
        assertEquals(1, result.groups.single { it.foxCount == 7 }.rows.size)
        assertTrue(result.rows.all { row ->
            row.orderLabels.first() == "S" &&
                row.orderLabels.takeLast(2) == listOf("B", "F")
        })
        assertTrue(result.rows.any { it.hasCategoryMatch })
        assertTrue(DesktopFoxoringCourseGenerator.reportText(result).contains("FOUR-FOX COURSES"))
        assertTrue(DesktopFoxoringCourseGenerator.reportText(result).contains("SIX-FOX COURSES"))
        assertTrue(DesktopFoxoringCourseGenerator.reportText(result).contains("SEVEN-FOX COURSES"))
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
    fun recommendsFoxoringCourseCombinationsCoveringAllCategories() {
        val path = Files.createTempFile("foxoring-course-points-recommendations", ".kml")
        Files.writeString(path, outAndBackFoxoringCoursePointsKml())

        val result = DesktopFoxoringCourseGenerator.generate(path, elevationLookup = { null })
        val reportText = DesktopFoxoringCourseGenerator.reportText(result)
        val pdfPath = Files.createTempFile("foxoring-course-generator-recommendations", ".pdf")
        DesktopFoxoringCourseGenerator.exportPdf(pdfPath, result)
        val pdfText = Files.readString(pdfPath)

        val firstSet = result.recommendedCourseSets.firstOrNull()
            ?: error("Expected at least one recommended Foxoring course combination.")
        assertTrue(firstSet.courseCount in 3..4)
        assertEquals(14, firstSet.coveredCategories.size)
        assertTrue(firstSet.coveredCategories.contains("M21"))
        assertTrue(firstSet.coveredCategories.contains("W75"))
        assertEquals(firstSet.rows.size, firstSet.courseCount)
        assertEquals(uniqueFirstFoxCount(firstSet.rows), firstSet.uniqueFirstFoxCount)
        assertTrue(firstSet.categoryFoxMinimum >= 4)
        assertTrue(firstSet.categoryFoxTotal > firstSet.coveredCategories.size * 4)
        assertTrue(reportText.contains("RECOMMENDED FOXORING COURSE SETS"))
        assertTrue(reportText.contains("Set #1"))
        assertFalse(reportText.contains("Combination 1:"))
        assertTrue(
            Regex("""Set #1\n\d+\.\d{2} km : S -> .* \([^)]+\)""")
                .containsMatchIn(reportText)
        )
        assertTrue(pdfText.contains("RECOMMENDED FOXORING COURSE SETS"))
        assertTrue(pdfText.contains("Set #1"))
    }

    @Test
    fun leavesClassicGeneratorWithoutCourseSetRecommendations() {
        val path = Files.createTempFile("classic-course-points-no-recommendations", ".kml")
        Files.writeString(path, foxoringCoursePointsKml(foxCount = 5, includeBeacon = true))

        val result = DesktopClassicCourseGenerator.generate(path, elevationLookup = { null })

        assertTrue(result.recommendedCourseSets.isEmpty())
        assertFalse(DesktopClassicCourseGenerator.reportText(result).contains("RECOMMENDED FOXORING COURSE SETS"))
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

    private fun outAndBackFoxoringCoursePointsKml(): String {
        val centerLatitude = 39.000
        val centerLongitude = -95.000
        val metersPerLongitudeDegree = 86_200.0
        val foxDistancesMeters = listOf(600, 1_000, 1_400, 1_800, 2_200, 2_600, 3_000, 3_400, 3_800, 4_200, 4_600, 5_000)
        val foxes = foxDistancesMeters.mapIndexed { index, distanceMeters ->
            val foxNumber = index + 1
            val longitude = centerLongitude + distanceMeters / metersPerLongitudeDegree
            pointPlacemark("FOX$foxNumber", longitude, centerLatitude, 100.0, "SI=${220 + foxNumber}")
        }.joinToString("\n")
        return """
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Document>
                ${pointPlacemark("Start", centerLongitude, centerLatitude, 100.0, "SI=200")}
                $foxes
                ${pointPlacemark("Finish", centerLongitude + 100.0 / metersPerLongitudeDegree, centerLatitude, 100.0, "SI=201")}
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

    private fun uniqueFirstFoxCount(rows: List<ClassicCourseGeneratorRow>): Int {
        val firstFoxCounts = rows
            .map { it.orderLabels[1] }
            .groupingBy { it }
            .eachCount()
        return rows.count { firstFoxCounts[it.orderLabels[1]] == 1 }
    }
}
