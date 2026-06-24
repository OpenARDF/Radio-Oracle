package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import kotlin.math.roundToInt

class DesktopClassicCourseGeneratorTest {
    @Test
    fun generatesIdealClassicCourseCombinationsFromPointPlacemarksOnly() {
        val path = Files.createTempFile("classic-course-points", ".kml")
        Files.writeString(path, coursePointsKml(includeLineString = true))

        val result = DesktopClassicCourseGenerator.generate(path, elevationLookup = { null })

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

        val result = DesktopClassicCourseGenerator.generate(path, elevationLookup = { null })

        assertEquals(0, result.elevationResolvedPointCount)
        assertEquals(8, result.missingElevationPointCount)
        assertTrue(result.rows.all { it.matchingCategories.isEmpty() })
        assertTrue(DesktopClassicCourseGenerator.reportText(result).contains("point elevations missing"))
    }

    @Test
    fun fillsMissingPointElevationsBeforeGeneratingCourses() {
        val path = Files.createTempFile("classic-course-points-cache-elev", ".kml")
        Files.writeString(path, coursePointsKml(includeElevations = false))

        val result = DesktopClassicCourseGenerator.generate(path) { point ->
            ((point.longitude + 95.0) * 1_000.0).roundToInt().toDouble()
        }

        assertEquals(8, result.elevationResolvedPointCount)
        assertEquals(0, result.missingElevationPointCount)
        assertTrue(result.start.point.elevationMeters != null)
        assertTrue(result.finish.point.elevationMeters != null)
        assertTrue(result.foxes.all { it.point.elevationMeters != null })
        assertTrue(result.rows.all { it.climbMeters != null })
        assertTrue(result.rows.any { it.hasCategoryMatch })
        assertTrue(DesktopClassicCourseGenerator.reportText(result).contains("filled 8 missing point elevations"))
    }

    @Test
    fun samplesElevationsAlongGeneratedCourseLegsForClimbAndEffectiveLength() {
        val path = Files.createTempFile("classic-course-points-route-elev", ".kml")
        Files.writeString(path, coursePointsKml(flatElevations = true))

        val result = DesktopClassicCourseGenerator.generate(path) { point ->
            val routeProgressMeters = (point.longitude + 95.0) * 10_000.0
            when {
                routeProgressMeters in 75.0..125.0 -> 150.0
                routeProgressMeters in 175.0..225.0 -> 160.0
                else -> 100.0
            }
        }

        assertEquals(0, result.missingElevationPointCount)
        assertTrue(result.rows.all { it.routePoints.size > it.coursePoints.size })
        assertTrue(result.rows.all { it.climbMeters != null })
        assertTrue(result.rows.any { requireNotNull(it.climbMeters) > 0.0 })
        assertTrue(result.rows.all { it.effectiveLengthMeters >= it.horizontalLengthMeters + 10.0 * requireNotNull(it.climbMeters) })
    }

    @Test
    fun reportsClassicCourseRequirementWarningsWithoutFilteringResults() {
        val path = Files.createTempFile("classic-course-points-rule-warnings", ".kml")
        Files.writeString(path, coursePointsKmlWithRuleViolations())

        val result = DesktopClassicCourseGenerator.generate(path, elevationLookup = { null })
        val reportText = DesktopClassicCourseGenerator.reportText(result)
        val pdfPath = Files.createTempFile("classic-course-generator-warnings", ".pdf")
        DesktopClassicCourseGenerator.exportPdf(pdfPath, result)
        val pdfText = Files.readString(pdfPath)

        assertEquals(2, result.requirementWarnings.size)
        assertTrue(result.requirementWarnings.any { it.label == "Classic start exclusion zone" })
        assertTrue(result.requirementWarnings.any { it.label == "Classic minimum transmitter spacing" })
        assertTrue(reportText.contains("Course requirement warnings"))
        assertTrue(reportText.contains("required at least 750 m"))
        assertTrue(reportText.contains("required at least 400 m"))
        assertTrue(pdfText.contains("Course requirement warnings"))
        assertTrue(pdfText.contains("Classic start exclusion zone:"))
        assertTrue(pdfText.contains("required at least 750 m"))
        assertTrue(pdfText.contains("Classic minimum transmitter spacing:"))
        assertTrue(pdfText.contains("required at least 400 m"))
        assertEquals(10, result.groups.single { it.foxCount == 3 }.rows.size)
        assertEquals(5, result.groups.single { it.foxCount == 4 }.rows.size)
        assertEquals(1, result.groups.single { it.foxCount == 5 }.rows.size)
        assertTrue(result.rows.any { it.hasCategoryMatch })
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
        Files.writeString(path, coursePointsKml(includeSiDescriptions = true))
        val result = DesktopClassicCourseGenerator.generate(path, elevationLookup = { null })
        val pdfPath = Files.createTempFile("classic-course-generator", ".pdf")

        val exports = DesktopClassicCourseGenerator.exportPdfAndKml(pdfPath, result)

        val bytes = Files.readAllBytes(exports.pdfPath)
        assertTrue(String(bytes.take(8).toByteArray()).startsWith("%PDF-1.4"))
        val text = String(bytes)
        assertTrue(text.contains("Classic Course Generator"))
        assertTrue(text.contains("Points: Start, 5 foxes, beacon, Finish"))
        assertTrue(text.contains("Elevation: complete point elevations available."))
        assertTrue(text.contains("THREE-FOX COURSES"))
        assertTrue(text.contains("IDEAL EL : Course Order"))
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
        assertTrue(kmlText.placemarkNamed("FOX1").contains("<description>SI=221</description>"))
        assertTrue(kmlText.placemarkNamed("Beacon").contains("<description>SI=299</description>"))
        val routeStyles = kmlText.routeLineStyleBlocks()
        assertEquals(result.rows.count { it.hasCategoryMatch }, routeStyles.size)
        assertTrue(routeStyles.all { it.contains("<width>3</width>") })
        val routeColors = routeStyles.mapNotNull { Regex("""<color>([^<]+)</color>""").find(it)?.groupValues?.get(1) }
        assertEquals(routeStyles.size, routeColors.size)
        assertEquals(routeColors.size, routeColors.toSet().size)
        assertFalse(routeColors.any { it in setOf("ffffffff", "ff000000", "ff00ff00") })
        val routePlacemarks = kmlText.routePlacemarkBlocks()
        assertEquals(result.rows.count { it.hasCategoryMatch }, routePlacemarks.size)
        routePlacemarks.zip(result.rows.filter { it.hasCategoryMatch }).forEach { (routePlacemark, row) ->
            assertEquals(row.coursePoints.size, routePlacemark.lineStringCoordinateLines().size)
            val description = Regex("""<description>(.*?)</description>""", RegexOption.DOT_MATCHES_ALL)
                .find(routePlacemark)
                ?.groupValues
                ?.get(1)
                .orEmpty()
            assertTrue(description.contains("Matching Categories: "))
            assertTrue(description.contains(Regex("""Horizontal Length: \d+\.\d{2} km""")))
            assertTrue(description.contains(Regex("""Climb: \d+ m""")))
            assertTrue(description.contains(Regex("""Effective Length: \d+\.\d{2} km""")))
        }
        assertEquals(1, kmlText.countOccurrences("<name>Start</name>"))
        assertEquals(1, kmlText.countOccurrences("<name>Finish</name>"))
        assertEquals(1, kmlText.countOccurrences("<name>Beacon</name>"))
        assertEquals(1, kmlText.countOccurrences("<name>FOX1</name>"))
        assertFalse(kmlText.contains("No category match"))
    }

    private fun coursePointsKml(
        includeLineString: Boolean = false,
        includeElevations: Boolean = true,
        extraPlacemark: String = "",
        includeSiDescriptions: Boolean = false,
        flatElevations: Boolean = false
    ): String {
        fun elevation(value: Double): Double? = (if (flatElevations) 100.0 else value).takeIf { includeElevations }
        fun description(siCode: Int): String? = "SI=$siCode".takeIf { includeSiDescriptions }
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
                ${pointPlacemark("Start", -95.000, 39.000, elevation(100.0), description(200))}
                ${pointPlacemark("FOX1", -94.990, 39.000, elevation(100.0), description(221))}
                ${pointPlacemark("FOX2", -94.980, 39.000, elevation(105.0), description(222))}
                ${pointPlacemark("FOX3", -94.970, 39.000, elevation(105.0), description(223))}
                ${pointPlacemark("FOX4", -94.960, 39.000, elevation(110.0), description(224))}
                ${pointPlacemark("FOX5", -94.950, 39.000, elevation(110.0), description(225))}
                ${pointPlacemark("Beacon", -94.940, 39.000, elevation(110.0), description(299))}
                ${pointPlacemark("Finish", -94.930, 39.000, elevation(110.0), description(201))}
                $extraPlacemark
                $lineString
              </Document>
            </kml>
        """.trimIndent()
    }

    private fun coursePointsKmlWithRuleViolations(): String =
        """
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Document>
                ${pointPlacemark("Start", -95.0000, 39.0000, 100.0)}
                ${pointPlacemark("FOX1", -94.9990, 39.0000, 100.0)}
                ${pointPlacemark("FOX2", -94.9985, 39.0000, 105.0)}
                ${pointPlacemark("FOX3", -94.9700, 39.0000, 105.0)}
                ${pointPlacemark("FOX4", -94.9600, 39.0000, 110.0)}
                ${pointPlacemark("FOX5", -94.9500, 39.0000, 110.0)}
                ${pointPlacemark("Beacon", -94.9400, 39.0000, 110.0)}
                ${pointPlacemark("Finish", -94.9300, 39.0000, 110.0)}
              </Document>
            </kml>
        """.trimIndent()

    private fun pointPlacemark(name: String, longitude: Double, latitude: Double, elevation: Double?, description: String? = null): String {
        val coordinateText = if (elevation == null) {
            "$longitude,$latitude"
        } else {
            "$longitude,$latitude,$elevation"
        }
        val descriptionText = description?.let { "<description>$it</description>" }.orEmpty()
        return """
            <Placemark>
              <name>$name</name>
              $descriptionText
              <Point><coordinates>$coordinateText</coordinates></Point>
            </Placemark>
        """.trimIndent()
    }

    private fun String.placemarkNamed(name: String): String {
        val escapedName = Regex.escape(name)
        return Regex("<Placemark>[\\s\\S]*?</Placemark>")
            .findAll(this)
            .firstOrNull { Regex("<name>$escapedName</name>").containsMatchIn(it.value) }
            ?.value
            ?: error("Missing Placemark named $name")
    }

    private fun String.countOccurrences(needle: String): Int =
        split(needle).size - 1

    private fun String.routeLineStyleBlocks(): List<String> =
        Regex("""<Style id="classicCourseCandidateRoute-\d+">.*?</Style>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(this)
            .map { it.value }
            .toList()

    private fun String.routePlacemarkBlocks(): List<String> =
        Regex("""<Placemark>.*?</Placemark>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(this)
            .filter { it.value.contains("<LineString>") }
            .map { it.value }
            .toList()

    private fun String.lineStringCoordinateLines(): List<String> =
        Regex("""<coordinates>(.*?)</coordinates>""", RegexOption.DOT_MATCHES_ALL)
            .find(this)
            ?.groupValues
            ?.get(1)
            ?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toList()
            ?: emptyList()
}
