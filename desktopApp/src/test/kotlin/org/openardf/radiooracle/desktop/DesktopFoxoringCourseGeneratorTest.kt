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

        assertEquals("Foxoring Route Generator", result.generatorTitle)
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
        assertTrue(pdfText.contains("Foxoring Route Generator"))
        assertTrue(pdfText.contains("FOUR-FOX COURSES"))

        val kmlText = Files.readString(exports.kmlPath)
        assertTrue(kmlText.contains("Foxoring Route Generator"))
        assertTrue(kmlText.contains("<name>Course Objects</name>"))
        assertTrue(kmlText.contains("<name>Category-matching course candidates</name>"))
        assertEquals(result.rows.count { it.hasCategoryMatch }, kmlText.countOccurrences("<LineString>"))
        assertFalse(kmlText.contains("No category match"))
    }

    @Test
    fun recommendsFoxoringCourseCombinationsCoveringStandardCategories() {
        val path = Files.createTempFile("foxoring-course-points-recommendations", ".kml")
        Files.writeString(path, outAndBackFoxoringCoursePointsKml())

        val result = DesktopFoxoringCourseGenerator.generate(path, elevationLookup = { null })
        val reportText = DesktopFoxoringCourseGenerator.reportText(result)
        val pdfPath = Files.createTempFile("foxoring-course-generator-recommendations", ".pdf")
        val exports = DesktopFoxoringCourseGenerator.exportPdfAndKml(pdfPath, result)
        val pdfText = Files.readString(pdfPath)
        val kmlText = Files.readString(exports.kmlPath)

        val firstSet = result.recommendedCourseSets.firstOrNull()
            ?: error("Expected at least one recommended Foxoring course combination.")
        assertTrue(firstSet.courseCount in 3..4)
        assertTrue(result.rows.any { "M80" in it.matchingCategories })
        assertTrue(result.rows.any { "W75" in it.matchingCategories })
        assertEquals(12, firstSet.coveredCategories.size)
        assertTrue(firstSet.coveredCategories.contains("M21"))
        assertTrue(firstSet.coveredCategories.contains("W65"))
        assertFalse(firstSet.coveredCategories.contains("M80"))
        assertFalse(firstSet.coveredCategories.contains("W75"))
        assertEquals(firstSet.rows.size, firstSet.courseCount)
        assertEquals(uniqueFirstFoxCount(firstSet.rows), firstSet.uniqueFirstFoxCount)
        val recommendedFoxCountByCategory = bestMatchingFoxCountByCategory(firstSet.rows, firstSet.coveredCategories)
        assertEquals(firstSet.coveredCategories.toSet(), recommendedFoxCountByCategory.keys)
        assertEquals(recommendedFoxCountByCategory.values.minOrNull(), firstSet.categoryFoxMinimum)
        assertEquals(recommendedFoxCountByCategory.values.sum(), firstSet.categoryFoxTotal)
        assertEquals(
            result.recommendedCourseSets.sortedWith(
                compareByDescending<ClassicCourseGeneratorRecommendedSet> { it.categoryFoxMinimum }
                    .thenByDescending { it.categoryFoxTotal }
                    .thenByDescending { it.uniqueFirstFoxCount }
                    .thenByDescending { it.rows.size }
            ),
            result.recommendedCourseSets
        )
        assertTrue(reportText.contains("RECOMMENDED FOXORING COURSE SETS"))
        assertTrue(reportText.contains("Set #1"))
        assertFalse(reportText.contains("Combination 1:"))
        assertTrue(
            Regex("""Set #1\n\d+\.\d{2} km : S -> .* \([^)]+\)""")
                .containsMatchIn(reportText)
        )
        assertTrue(pdfText.contains("Points: Start, 12 foxes, no beacon, Finish"))
        assertTrue(pdfText.contains("Elevation: complete point elevations available."))
        assertTrue(pdfText.contains("Recommended Foxoring course sets"))
        assertTrue(pdfText.contains("Set #1"))
        assertEquals(1, kmlText.countOccurrences("<name>Course Objects</name>"))
        assertEquals(result.recommendedCourseSets.size, Regex("""<name>Recommended Set #\d+</name>""").findAll(kmlText).count())
        assertTrue(kmlText.contains("<name>Recommended Set #1</name>"))
        assertTrue(kmlText.contains("<name>Recommended Set #1 Course #1:"))
        val exportedCandidateRows = result.rows
            .filter { it.hasCategoryMatch || it.foxCount == result.foxes.size }
            .distinct()
        assertEquals(
            exportedCandidateRows.size + result.recommendedCourseSets.sumOf { it.rows.size },
            kmlText.countOccurrences("<LineString>")
        )
        assertEquals(1, kmlText.countOccurrences("<name>Start</name>"))
        assertEquals(1, kmlText.countOccurrences("<name>FOX1</name>"))
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

    @Test
    fun rejectsClassicCoursePointsWhenFoxoringGeneratorSelected() {
        val path = Files.createTempFile("Classic Practice", ".kml")
        Files.writeString(path, foxoringCoursePointsKml(foxCount = 5, includeBeacon = true))

        val error = runCatching { DesktopFoxoringCourseGenerator.generate(path) }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("appears to be Classic"))
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

    private fun bestMatchingFoxCountByCategory(
        rows: List<ClassicCourseGeneratorRow>,
        categories: List<String>
    ): Map<String, Int> =
        categories.mapNotNull { category ->
            rows
                .filter { category in it.matchingCategories }
                .maxOfOrNull { it.foxCount }
                ?.let { category to it }
        }.toMap()
}
