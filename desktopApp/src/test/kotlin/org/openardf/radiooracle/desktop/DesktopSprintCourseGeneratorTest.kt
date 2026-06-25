package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class DesktopSprintCourseGeneratorTest {
    @Test
    fun generatesSprintCoursesFromOneThroughFiveFoxesPerLoop() {
        val path = Files.createTempFile("sprint-course-points", ".kml")
        Files.writeString(path, sprintCoursePointsKml(includeSpectator = true))

        val result = DesktopSprintCourseGenerator.generate(path, elevationLookup = { null })

        assertEquals("Sprint Course Generator", result.generatorTitle)
        assertEquals("Sprint", result.formatLabel)
        assertEquals("Start, 5 slow foxes, spectator, 5 fast foxes, Beacon, Finish", result.pointSummary)
        assertEquals(10, result.foxes.size)
        assertEquals(1, result.additionalCourseObjects.size)
        assertEquals((2..10).toList(), result.groups.map { it.foxCount })
        assertEquals(25, result.groups.single { it.foxCount == 2 }.rows.size)
        assertEquals(100, result.groups.single { it.foxCount == 3 }.rows.size)
        assertEquals(210, result.groups.single { it.foxCount == 6 }.rows.size)
        assertEquals(1, result.groups.single { it.foxCount == 10 }.rows.size)
        assertTrue(result.rows.all { row ->
            val labels = row.orderLabels
            labels.first() == "S" &&
                "SP" in labels &&
                labels.takeLast(2) == listOf("B", "F") &&
                labels.indexOf("SP") > 1 &&
                labels.indexOf("SP") < labels.lastIndex - 1
        })
        assertTrue(result.rows.any { row ->
            row.foxCount == 6 &&
                row.orderLabels.count { it.isFastFoxLabel() } == 1 &&
                "M70" in row.matchingCategories
        })
        assertTrue(result.rows.any { row -> row.foxCount == 10 && "M21" in row.matchingCategories })
        assertTrue(DesktopSprintCourseGenerator.reportText(result).contains("TEN-FOX COURSES"))
    }

    @Test
    fun recommendsSprintCourseSetsAndExportsPdfAndKml() {
        val path = Files.createTempFile("sprint-course-points-recommendations", ".kml")
        Files.writeString(path, sprintCoursePointsKml(includeSpectator = true))

        val result = DesktopSprintCourseGenerator.generate(path, elevationLookup = { null })
        val reportText = DesktopSprintCourseGenerator.reportText(result)
        val pdfPath = Files.createTempFile("sprint-course-generator", ".pdf")
        val exports = DesktopSprintCourseGenerator.exportPdfAndKml(pdfPath, result)
        val pdfText = Files.readString(exports.pdfPath)
        val kmlText = Files.readString(exports.kmlPath)

        val firstSet = result.recommendedCourseSets.firstOrNull()
            ?: error("Expected at least one recommended Sprint course set.")
        assertTrue(firstSet.courseCount in 3..4)
        assertTrue(firstSet.coveredCategories.contains("M21"))
        assertTrue(firstSet.coveredCategories.contains("W75"))
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
        assertTrue(reportText.contains("RECOMMENDED SPRINT COURSE SETS"))
        assertTrue(reportText.contains("Set #1"))
        assertTrue(pdfText.contains("Sprint Course Generator"))
        assertTrue(pdfText.contains("Points: Start, 5 slow foxes, spectator, 5 fast foxes, Beacon, Finish"))
        assertTrue(pdfText.contains("Recommended Sprint course sets"))
        assertTrue(kmlText.contains("<name>Course Objects</name>"))
        assertEquals(1, kmlText.countOccurrences("<name>Start</name>"))
        assertEquals(1, kmlText.countOccurrences("<name>Spectator</name>"))
        assertEquals(1, kmlText.countOccurrences("<name>Beacon</name>"))
        assertTrue(kmlText.contains("<name>Recommended Set #1</name>"))
        assertEquals(
            result.rows.count { it.hasCategoryMatch } + result.recommendedCourseSets.sumOf { it.rows.size },
            kmlText.countOccurrences("<LineString>")
        )
        assertFalse(kmlText.contains("No category match"))
    }

    @Test
    fun usesBeaconAsSprintLoopTransitionWhenSpectatorIsAbsent() {
        val path = Files.createTempFile("sprint-course-points-no-spectator", ".kml")
        Files.writeString(path, sprintCoursePointsKml(includeSpectator = false))

        val result = DesktopSprintCourseGenerator.generate(path, elevationLookup = { null })
        val row = result.groups.single { it.foxCount == 2 }.rows.first()

        assertEquals("Start, 5 slow foxes, no spectator, 5 fast foxes, Beacon, Finish", result.pointSummary)
        assertTrue(row.orderLabels.count { it == "B" } == 2)
        assertTrue(row.orderLabels.indexOf("B") > 1)
        assertEquals(listOf("B", "F"), row.orderLabels.takeLast(2))
    }

    @Test
    fun ignoresSprintHelperPointsAndRecognizesSpSpectator() {
        val path = Files.createTempFile("sprint-course-points-with-helpers", ".kml")
        Files.writeString(
            path,
            sprintCoursePointsKml(
                includeSpectator = true,
                spectatorName = "Sp",
                includeCorridorHelpers = true
            )
        )

        val result = DesktopSprintCourseGenerator.generate(path, elevationLookup = { null })

        assertEquals("Start, 5 slow foxes, spectator, 5 fast foxes, Beacon, Finish", result.pointSummary)
        assertEquals(10, result.foxes.size)
        assertEquals(listOf("Sp"), result.additionalCourseObjects.map { it.label })
        assertEquals((2..10).toList(), result.groups.map { it.foxCount })
    }

    @Test
    fun rejectsClassicCoursePointsWhenSprintGeneratorSelected() {
        val path = Files.createTempFile("Classic Practice", ".kml")
        Files.writeString(path, classicCoursePointsKml())

        val error = runCatching { DesktopSprintCourseGenerator.generate(path) }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("appears to be Classic"))
    }

    private fun sprintCoursePointsKml(
        includeSpectator: Boolean,
        spectatorName: String = "Spectator",
        includeCorridorHelpers: Boolean = false
    ): String {
        val centerLatitude = 39.000
        val centerLongitude = -95.000
        val metersPerLongitudeDegree = 86_200.0
        fun longitude(distanceMeters: Int): Double = centerLongitude + distanceMeters / metersPerLongitudeDegree
        val slowFoxes = (1..5).joinToString("\n") { index ->
            pointPlacemark(index.toString(), longitude(300 + index * 120), centerLatitude, 100.0, "SI=${230 + index}")
        }
        val fastFoxes = (1..5).joinToString("\n") { index ->
            pointPlacemark("${index}F", longitude(1_400 + index * 120), centerLatitude, 100.0, "SI=${240 + index}")
        }
        val spectator = if (includeSpectator) {
            pointPlacemark(spectatorName, longitude(1_100), centerLatitude, 100.0, "SI=246")
        } else {
            ""
        }
        val helperPoints = if (includeCorridorHelpers) {
            """
                ${pointPlacemark("End Corridor_Strt", longitude(2_260), centerLatitude, 100.0, "SI=0")}
                ${pointPlacemark("End Corridor_S", longitude(2_300), centerLatitude, 100.0, "SI=0")}
            """.trimIndent()
        } else {
            ""
        }
        return """
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Document>
                ${pointPlacemark("Start", centerLongitude, centerLatitude, 100.0, "SI=200")}
                $slowFoxes
                $spectator
                $fastFoxes
                ${pointPlacemark("Beacon", longitude(2_200), centerLatitude, 100.0, "SI=299")}
                $helperPoints
                ${pointPlacemark("Finish", longitude(2_350), centerLatitude, 100.0, "SI=201")}
              </Document>
            </kml>
        """.trimIndent()
    }

    private fun classicCoursePointsKml(): String {
        val foxes = (1..5).joinToString("\n") { index ->
            pointPlacemark("FOX$index", -94.990 + index * 0.004, 39.000, 100.0, "SI=${220 + index}")
        }
        return """
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Document>
                ${pointPlacemark("Start", -95.000, 39.000, 100.0, "SI=200")}
                $foxes
                ${pointPlacemark("Beacon", -94.940, 39.000, 100.0, "SI=299")}
                ${pointPlacemark("Finish", -94.930, 39.000, 100.0, "SI=201")}
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

    private fun String.isFastFoxLabel(): Boolean =
        matches(Regex("""(?:[1-5]F|F[1-5])"""))

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
