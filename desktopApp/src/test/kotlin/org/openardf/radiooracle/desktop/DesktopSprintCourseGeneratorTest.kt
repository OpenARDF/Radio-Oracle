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
import org.openardf.radiooracle.shared.event.EventCourseRuleCatalog
import java.nio.file.Files

class DesktopSprintCourseGeneratorTest {
    @Test
    fun diverseSetSelectionAddsNewFirstFoxesBeforeReusingCombinations() {
        val selected = selectDiverseFirstFoxCombinationIndices(
            firstFoxesByCandidate = listOf(
                listOf("A", "B", "C", "D"),
                listOf("A", "B", "C", "E"),
                listOf("E", "F", "G", "H"),
                listOf("I", "J", "K", "L")
            ),
            limit = 3
        )

        assertEquals(listOf(0, 2, 3), selected)
    }

    @Test
    fun diverseSetSelectionSpreadsFirstFoxOverlapAcrossEarlierSets() {
        val selected = selectDiverseFirstFoxCombinationIndices(
            firstFoxesByCandidate = listOf(
                listOf("A", "B", "C", "D"),
                listOf("A", "E", "I", "J"),
                listOf("A", "B", "I", "J"),
                listOf("E", "F", "G", "H")
            ),
            limit = 3
        )

        assertEquals(listOf(0, 3, 1), selected)
    }

    @Test
    fun diverseSetSelectionDoesNotCrossARequiredQualityTier() {
        val selected = selectDiverseFirstFoxCombinationIndices(
            firstFoxesByCandidate = listOf(
                listOf("A", "B", "C"),
                listOf("D", "E", "F"),
                listOf("A", "B", "D"),
                listOf("G", "H", "I")
            ),
            limit = 3,
            priorityTierByCandidate = listOf(0, 1, 0, 1)
        )

        assertEquals(listOf(0, 2, 3), selected)
    }

    @Test
    fun sprintTargetTimeUsesSharedCategorySpeedModel() {
        val m21TargetSeconds = DesktopCourseSpeedFactors.estimatedSprintSeconds(
            comparisonLengthMeters = 3_780.0,
            categoryKey = "M21"
        )
        val w75TargetSeconds = DesktopCourseSpeedFactors.estimatedSprintSeconds(
            comparisonLengthMeters = 1_776.6,
            categoryKey = "W75"
        )

        assertEquals(900.0, m21TargetSeconds, 0.001)
        assertEquals(900.0, w75TargetSeconds, 0.001)
        assertTrue(DesktopCourseSpeedFactors.isWithinSprintTargetTime(m21TargetSeconds))
        assertFalse(DesktopCourseSpeedFactors.isWithinSprintTargetTime(600.0))
    }

    @Test
    fun generatesSprintCoursesFromOneThroughFiveFoxesPerLoop() {
        val path = Files.createTempFile("sprint-course-points", ".kml")
        Files.writeString(path, sprintCoursePointsKml(includeSpectator = true))

        val result = DesktopSprintCourseGenerator.generate(path, elevationLookup = { null })

        assertEquals("Sprint Route Generator", result.generatorTitle)
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
        assertTrue(result.rows.all { row ->
            row.sprintSlowFoxCount != null &&
                row.sprintFastFoxCount != null &&
                row.foxCount == row.sprintSlowFoxCount + row.sprintFastFoxCount
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
        assertEquals(
            "Balanced Sprint routes should win when target-time coverage and unique first foxes allow them.",
            0,
            firstSet.unbalancedSprintCourseCount
        )
        assertEquals(0, firstSet.totalSprintLoopFoxCountDifference)
        val recommendedFoxCountByCategory = bestMatchingFoxCountByCategory(firstSet.rows, firstSet.coveredCategories)
        assertEquals(firstSet.coveredCategories.toSet(), recommendedFoxCountByCategory.keys)
        assertEquals(recommendedFoxCountByCategory.values.minOrNull(), firstSet.categoryFoxMinimum)
        assertEquals(recommendedFoxCountByCategory.values.sum(), firstSet.categoryFoxTotal)
        recommendedFoxCountByCategory.forEach { (category, foxCount) ->
            val requirement = requireNotNull(EventCourseRuleCatalog.sprintRequirements[category])
            assertTrue(
                "$category assigned $foxCount foxes outside ${requirement.controlRangeText()}",
                foxCount in requirement.minControls..requirement.maxControls
            )
        }
        val recommendationQualityComparator =
            compareByDescending<ClassicCourseGeneratorRecommendedSet> { it.categoryFoxMinimum }
                .thenByDescending { it.categoryFoxTotal }
                .thenBy { it.unbalancedSprintCourseCount }
                .thenBy { it.totalSprintLoopFoxCountDifference }
                .thenByDescending { it.sprintTargetTimeCategoryCount }
                .thenByDescending { it.uniqueFirstFoxCount }
                .thenByDescending { it.rows.size }
        assertEquals(
            firstSet,
            result.recommendedCourseSets.minWith(recommendationQualityComparator)
        )
        assertTrue(
            result.recommendedCourseSets.none { alternative ->
                alternative.sprintTargetTimeCategoryCount == firstSet.sprintTargetTimeCategoryCount &&
                    alternative.uniqueFirstFoxCount == firstSet.uniqueFirstFoxCount &&
                    alternative.unbalancedSprintCourseCount < firstSet.unbalancedSprintCourseCount
            }
        )
        result.recommendedCourseSets.forEach { recommendedSet ->
            val assignedFoxCountByCategory = bestMatchingFoxCountByCategory(
                recommendedSet.rows,
                recommendedSet.coveredCategories
            )
            assertEquals(recommendedSet.coveredCategories.toSet(), assignedFoxCountByCategory.keys)
            assignedFoxCountByCategory.forEach { (category, foxCount) ->
                val requirement = requireNotNull(EventCourseRuleCatalog.sprintRequirements[category])
                assertTrue(
                    "Set ${recommendedSet.index}: $category assigned $foxCount foxes outside " +
                        requirement.controlRangeText(),
                    foxCount in requirement.minControls..requirement.maxControls
                )
            }
        }
        val firstFoxCombinations = result.recommendedCourseSets.map { recommendedSet ->
            recommendedSet.rows.mapNotNull { it.orderLabels.getOrNull(1) }.sorted()
        }
        assertTrue(
            "Recommended Sprint sets should diversify first-ideal-fox combinations within the required fox-count and balance tier.",
            firstFoxCombinations.distinct().size > 1
        )
        assertTrue(reportText.contains("RECOMMENDED SPRINT COURSE SETS"))
        assertTrue(reportText.contains("Set #1"))
        assertTrue(pdfText.contains("Sprint Route Generator"))
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
    fun prioritizesRequiredFoxCountsThenBalancedLoopsAheadOfTargetTime() {
        val path = Files.createTempFile("sprint-course-points-balance-priority", ".kml")
        Files.writeString(path, sprintBalancePriorityCoursePointsKml())

        val result = DesktopSprintCourseGenerator.generate(path, elevationLookup = { null })

        assertTrue(result.recommendedCourseSets.isNotEmpty())
        result.recommendedCourseSets.forEach { recommendedSet ->
            val assignedFoxCountByCategory = bestMatchingFoxCountByCategory(
                recommendedSet.rows,
                recommendedSet.coveredCategories
            )
            assertEquals(recommendedSet.coveredCategories.toSet(), assignedFoxCountByCategory.keys)
            assignedFoxCountByCategory.forEach { (category, foxCount) ->
                assertEquals(
                    "$category should receive its full Sprint fox assignment before route balance is considered.",
                    EventCourseRuleCatalog.sprintRequirements.getValue(category).maxControls,
                    foxCount
                )
            }
            recommendedSet.rows
                .filter { row -> row.foxCount < 10 && row.foxCount % 2 == 0 }
                .forEach { row ->
                    assertEquals(
                        "Even Sprint assignments should use equal slow and fast fox counts before target-time proximity breaks ties: ${row.orderLabels}",
                        0,
                        row.sprintLoopFoxCountDifference
                    )
                }
        }
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

    private fun sprintBalancePriorityCoursePointsKml(): String =
        """
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Document>
                ${pointPlacemark("Start", -121.6707874501688, 45.29997622140402, 1277.38239289, "SI=200")}
                ${pointPlacemark("3", -121.6704418259752, 45.29886556539267, 1264.96169061, "SI=163")}
                ${pointPlacemark("5", -121.6692957048742, 45.29770713955187, 1256.99654462, "SI=165")}
                ${pointPlacemark("1", -121.6706891576796, 45.29790481644139, 1250.18099412, "SI=161")}
                ${pointPlacemark("2", -121.6724807053287, 45.29728701767213, 1248.86481787, "SI=162")}
                ${pointPlacemark("4", -121.6710638383796, 45.29695757452851, 1262.09927079, "SI=164")}
                ${pointPlacemark("S", -121.6718678618151, 45.29851629288117, 1268.09916729, "SI=137")}
                ${pointPlacemark("3F", -121.67286354, 45.30121818, 1284.78709992, "SI=173")}
                ${pointPlacemark("1F", -121.6741548479692, 45.2988254426979, 1268.88012747, "SI=171")}
                ${pointPlacemark("4F", -121.6741437945765, 45.29989796968182, 1268.75703204, "SI=174")}
                ${pointPlacemark("5F", -121.6737207394431, 45.29789384949425, 1258.98093943, "SI=175")}
                ${pointPlacemark("2F", -121.6728527000563, 45.29926387442363, 1247.47549897, "SI=172")}
                ${pointPlacemark("B", -121.6724676120949, 45.30020673173927, 1255.48802327, "SI=136")}
                ${pointPlacemark("Finish", -121.6721378738688, 45.30037170669513, 1257.7052447, "SI=201")}
              </Document>
            </kml>
        """.trimIndent()

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
