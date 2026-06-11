package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.EventCategory
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventControl
import org.openardf.radiooracle.shared.event.EventControlPoint
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventRace
import org.openardf.radiooracle.shared.event.EventRaceData
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DesktopCourseKmlGeneratedCoverageTest {
    @Test
    fun importsAndAnalyzesGeneratedKmlCourseVariants() {
        val executedExamples = generatedCases().sumOf { testCase ->
            runGeneratedCase(testCase)
        } + runDuplicateImportScenario() + runControlsOnlyLocationUpdateScenario()

        assertTrue("Expected at least 30 generated KML/KMZ examples; ran $executedExamples", executedExamples >= 30)
    }

    private fun runGeneratedCase(testCase: GeneratedKmlCase): Int {
        val path = writeCourseFile(testCase)
        val importResult = runCatching {
            DesktopCourseKmlImporter.importProtectedCourseInfo(
                path = path,
                projectFile = testCase.project,
                password = PASSWORD,
                categoryOverrideId = testCase.categoryOverrideId,
                elevationProvider = ::syntheticElevation
            )
        }
        if (testCase.expectedImportFailure) {
            assertTrue("${testCase.name} should have failed import", importResult.isFailure)
            return 1
        }

        val (importedProject, summary) = importResult.getOrElse { throw AssertionError("${testCase.name} import failed", it) }
        testCase.expectedMatchedCategories?.let {
            assertEquals("${testCase.name} matched category count", it, summary.matchedCategoryCount)
        }
        testCase.expectedImportedCategories?.let {
            assertEquals("${testCase.name} imported category count", it, summary.importedCategoryCount)
        }
        testCase.expectedMatchedControls?.let {
            assertEquals("${testCase.name} matched control count", it, summary.matchedControlPointCount)
        }
        testCase.expectedAssignedControls?.let {
            assertEquals("${testCase.name} assigned control count", it, summary.assignedCategoryControlCount)
        }
        testCase.expectedRouteControlLabels.forEach { (categoryName, labels) ->
            val info = importedProject.protectedInfoFor(categoryName)
            assertEquals("${testCase.name} route controls for $categoryName", labels, info.controlPoints.map { it.label })
        }
        if (testCase.applyAssignments) {
            val appliedProject = DesktopCourseKmlImporter.applyCategoryAssignmentUpdates(importedProject, summary.categoryAssignmentUpdates)
            testCase.expectedAssignedSiCodes.forEach { (categoryName, siCodes) ->
                val category = appliedProject.raceData.categories.single { it.category.name == categoryName }
                assertEquals("${testCase.name} assigned SI order for $categoryName", siCodes, category.controlPoints.map { it.siCode })
            }
        }

        testCase.categoriesToAnalyze.forEach { categoryName ->
            val category = importedProject.raceData.categories.single { it.category.name == categoryName }
            val protectedInfo = category.category.encryptedCourseInfo
                ?.let { DesktopProtectedCourseOrder.decryptCourseInfo(it, PASSWORD) }
            val idealOrder = category.category.encryptedIdealOrder
                ?.let { DesktopProtectedCourseOrder.decrypt(it, PASSWORD) }
            val analysis = DesktopCourseAnalyzer.analyze(
                projectFile = importedProject,
                categoryId = category.category.id,
                protectedCourseInfo = protectedInfo,
                protectedIdealOrderText = idealOrder,
                elevationLookup = ::syntheticElevation
            )
            val reportText = DesktopCourseAnalysisExports.reportText(analysis)
            assertEquals(
                "${testCase.name} rules citation count",
                1,
                Regex("Rules applied: USA Rules for Radio Orienteering, Effective Date: 1 Jan 2026").findAll(reportText).count()
            )
            testCase.analysisChecks.forEach { check ->
                check(testCase.name, categoryName, analysis)
            }
        }
        testCase.verify(importedProject, summary)
        return 1
    }

    private fun runDuplicateImportScenario(): Int {
        val route = routeSpec("M21", "Start", "1", "2", "3", "B", "Finish")
        val testCase = case(
            name = "classic duplicate route import with missing elevations",
            raceType = RaceType.CLASSIC,
            box = NC_UMSTEAD,
            categories = listOf("M21"),
            controls = classicControls(3, includeBeacon = true),
            routes = listOf(route),
            categoriesToAnalyze = emptyList()
        )
        val path = writeCourseFile(testCase)
        val (firstProject, firstSummary) = DesktopCourseKmlImporter.importProtectedCourseInfo(
            path = path,
            projectFile = testCase.project,
            password = PASSWORD,
            elevationProvider = { null }
        )
        val (_, duplicateSummary) = DesktopCourseKmlImporter.importProtectedCourseInfo(
            path = path,
            projectFile = firstProject,
            password = PASSWORD,
            elevationProvider = { error("Duplicate imports with the same hash should not resample elevations") }
        )

        assertEquals(firstSummary.sourceSha256, duplicateSummary.sourceSha256)
        assertEquals(0, duplicateSummary.importedCategoryCount)
        assertEquals(1, duplicateSummary.duplicateCategoryCount)
        assertTrue(duplicateSummary.hasDuplicateMissingElevations)
        return 1
    }

    private fun runControlsOnlyLocationUpdateScenario(): Int {
        val initial = case(
            name = "classic route for controls-only update",
            raceType = RaceType.CLASSIC,
            box = NC_UMSTEAD,
            categories = listOf("M21"),
            controls = classicControls(3, includeBeacon = true),
            routes = listOf(routeSpec("M21", "Start", "1", "2", "3", "B", "Finish")),
            categoriesToAnalyze = emptyList()
        )
        val initialPath = writeCourseFile(initial)
        val (importedProject, _) = DesktopCourseKmlImporter.importProtectedCourseInfo(
            path = initialPath,
            projectFile = initial.project,
            password = PASSWORD,
            elevationProvider = ::syntheticElevation
        )
        val shiftedPoints = pointsFor(NC_UMSTEAD, classicControls(3, includeBeacon = true)).toMutableMap()
        shiftedPoints["2"] = shiftedPoints.getValue("2").copy(longitude = shiftedPoints.getValue("2").longitude + 0.001)
        val controlsOnly = GeneratedKmlCase(
            name = "controls-only changed location",
            project = importedProject,
            kmlText = kml(shiftedPoints, emptyList()),
            categoriesToAnalyze = emptyList(),
            expectedMatchedControls = 4
        )
        val controlsPath = writeCourseFile(controlsOnly)
        val (updatedProject, summary) = DesktopCourseKmlImporter.importProtectedCourseInfo(
            path = controlsPath,
            projectFile = importedProject,
            password = PASSWORD,
            elevationProvider = ::syntheticElevation
        )
        val updatedInfo = updatedProject.protectedInfoFor("M21")

        assertEquals(0, summary.routeCount)
        assertEquals(1, summary.changedControlLocationCount)
        assertEquals(1, summary.controlLocationAffectedCategoryCount)
        assertTrue(updatedInfo.route.isEmpty())
        assertEquals(null, updatedInfo.lengthMeters)
        assertEquals(-78.773530, updatedInfo.controlPoints.single { it.label == "2" }.longitude, 0.01)
        return 1
    }

    private fun generatedCases(): List<GeneratedKmlCase> = buildList {
        add(
            case(
                name = "nc classic three categories route-specific controls",
                raceType = RaceType.CLASSIC,
                box = NC_UMSTEAD,
                categories = listOf("M21", "M50", "W65"),
                controls = classicControls(5, includeBeacon = true),
                routes = listOf(
                    routeSpec("M21", "Start", "1", "2", "3", "4", "5", "B", "Finish"),
                    routeSpec("M50", "Start", "5", "1", "3", "B", "Finish"),
                    routeSpec("W65", "Start", "2", "4", "5", "B", "Finish")
                ),
                expectedRouteControlLabels = mapOf(
                    "M21" to listOf("1", "2", "3", "4", "5", "B"),
                    "M50" to listOf("5", "1", "3", "B"),
                    "W65" to listOf("2", "4", "5", "B")
                ),
                expectedAssignedSiCodes = mapOf(
                    "M50" to listOf(31, 33, 35, 99),
                    "W65" to listOf(32, 34, 35, 99)
                ),
                applyAssignments = true,
                categoriesToAnalyze = listOf("M50", "W65"),
                analysisChecks = listOf { caseName, categoryName, analysis ->
                    val calculated = analysis.calculatedIdealOrder.joinToString(" ")
                    if (categoryName == "M50") {
                        assertFalse("$caseName $categoryName should not include off-route Fox 2", calculated.contains("2"))
                        assertFalse("$caseName $categoryName should not include off-route Fox 4", calculated.contains("4"))
                    }
                    if (categoryName == "W65") {
                        assertFalse("$caseName $categoryName should not include off-route Fox 1", calculated.contains("1"))
                        assertFalse("$caseName $categoryName should not include off-route Fox 3", calculated.contains("3"))
                    }
                }
            )
        )
        add(
            case(
                name = "oregon classic compact category matching",
                raceType = RaceType.CLASSIC,
                box = OR_WHITE_RIVER,
                categories = listOf("M-21", "M 50"),
                controls = classicControls(5, includeBeacon = true),
                routes = listOf(
                    routeSpec("M21", "Start", "1", "3", "5", "2", "4", "B", "Finish"),
                    routeSpec("M50", "Start", "1", "5", "B", "Finish")
                ),
                expectedMatchedCategories = 2,
                expectedRouteControlLabels = mapOf("M 50" to listOf("1", "5", "B")),
                categoriesToAnalyze = listOf("M 50")
            )
        )
        add(classicSingle("oregon classic valid five controls", OR_SKYLINE, listOf("1", "2", "3", "4", "5", "B")))
        add(classicSingle("nc classic reversed stored route", NC_HORTON, listOf("5", "4", "3", "2", "1", "B")))
        add(classicSingle("nc classic too few controls", NC_UMSTEAD, listOf("1", "2", "B"), expectedControls = 3))
        add(classicSingle("oregon classic one fox", OR_WHITE_RIVER, listOf("1", "B"), expectedControls = 2))
        add(classicSingle("nc classic missing beacon", NC_HORTON, listOf("1", "2", "3"), includeBeacon = false, expectedControls = 3))
        add(classicSingle("oregon classic too many controls", OR_SKYLINE, (1..9).map { it.toString() } + "B", foxCount = 9, expectedControls = 10))
        add(classicSingle("nc classic extra off route controls", NC_UMSTEAD, listOf("1", "3", "5", "B"), foxCount = 7, expectedControls = 8))
        add(
            case(
                name = "single unmatched route uses selected override",
                raceType = RaceType.CLASSIC,
                box = OR_WHITE_RIVER,
                categories = listOf("W21"),
                controls = classicControls(4, includeBeacon = true),
                routes = listOf(routeSpec("Exported route", "Start", "1", "2", "4", "B", "Finish")),
                categoryOverrideId = "category-w21",
                expectedMatchedCategories = 1,
                expectedRouteControlLabels = mapOf("W21" to listOf("1", "2", "4", "B")),
                categoriesToAnalyze = listOf("W21")
            )
        )
        add(controlsOnlyCase("controls only matched points no stored course", NC_UMSTEAD))
        add(invalidCase("invalid no point placemarks", "<kml><Document><Placemark><name>M21</name><LineString><coordinates>-78.8,35.85,0 -78.79,35.86,0</coordinates></LineString></Placemark></Document></kml>"))
        add(invalidCase("invalid malformed xml", "<kml><Document><Placemark><name>1</name>"))
        add(singleCoordinateRouteCase())
        add(duplicatePointNameCase())
        add(ambiguousNumberNameCase())
        add(classicPublicLabelCase())
        add(classicSiCodeNameCase())
        add(classicSingle("kmz classic multi point import", NC_HORTON, listOf("1", "2", "3", "B"), extension = ".kmz", expectedControls = 4))
        add(classicSingle("nc classic deliberate spacing violation", NC_UMSTEAD, listOf("1", "2", "3", "4", "5", "B"), tightSpacing = true))
        add(sprintCase("sprint spectator present", NC_UMSTEAD, includeSpectator = true, includeBeacon = true, slowCount = 2, fastCount = 2))
        add(sprintCase("sprint beacon transition no spectator", OR_SKYLINE, includeSpectator = false, includeBeacon = true, slowCount = 2, fastCount = 2))
        add(sprintCase("sprint spectator but missing beacon", NC_HORTON, includeSpectator = true, includeBeacon = false, slowCount = 2, fastCount = 2))
        add(sprintCase("sprint five and five exact loop limit", OR_WHITE_RIVER, includeSpectator = true, includeBeacon = true, slowCount = 5, fastCount = 5))
        add(sprintCase("sprint slow loop heuristic fallback", NC_UMSTEAD, includeSpectator = true, includeBeacon = true, slowCount = 6, fastCount = 2))
        add(sprintCase("sprint fast controls only", OR_SKYLINE, includeSpectator = true, includeBeacon = true, slowCount = 0, fastCount = 3))
        add(foxoringCase("foxoring five controls exhaustive", NC_HORTON, 5, includeBeacon = true))
        add(foxoringCase("foxoring ten controls heuristic", OR_WHITE_RIVER, 10, includeBeacon = true))
        add(foxoringCase("foxoring twelve controls heuristic", NC_UMSTEAD, 12, includeBeacon = true))
        add(foxoringCase("foxoring no beacon", OR_SKYLINE, 7, includeBeacon = false))
        add(foxoringCase("foxoring one control warning", NC_HORTON, 1, includeBeacon = true))
        add(outsideElevationCoverageCase())
        add(classicSingle("category close match m dash 21", NC_UMSTEAD, listOf("1", "2", "3", "4", "5", "B"), categoryName = "M-21"))
        add(classicSingle("many point placemarks but short route", OR_WHITE_RIVER, listOf("1", "4", "7", "10", "B"), foxCount = 20, expectedControls = 21, expectedRouteLabels = null))
        add(classicSingle("nc route without start finish point placemarks", NC_HORTON, listOf("1", "2", "3", "B"), includeStartFinishPlacemarks = false, expectedControls = 4))
        add(classicSingle("oregon route with nonmatching extra placemark", OR_SKYLINE, listOf("1", "2", "B"), extraUnmatchedPoint = true, expectedControls = 3))
    }

    private fun classicSingle(
        name: String,
        box: Box,
        routeLabels: List<String>,
        foxCount: Int = routeLabels.count { it.toIntOrNull() != null },
        includeBeacon: Boolean = routeLabels.contains("B"),
        categoryName: String = "M21",
        extension: String = ".kml",
        expectedControls: Int? = null,
        expectedRouteLabels: List<String>? = routeLabels,
        tightSpacing: Boolean = false,
        includeStartFinishPlacemarks: Boolean = true,
        extraUnmatchedPoint: Boolean = false
    ): GeneratedKmlCase {
        val controls = classicControls(foxCount, includeBeacon = includeBeacon)
        val points = pointsFor(box, controls, tightSpacing = tightSpacing).toMutableMap()
        if (extraUnmatchedPoint) {
            points["Water Stop"] = box.point(0.44, 0.44)
        }
        val pointNames = if (includeStartFinishPlacemarks) points.keys.toList() else points.keys.filterNot { it == "Start" || it == "Finish" }
        return case(
            name = name,
            raceType = RaceType.CLASSIC,
            box = box,
            categories = listOf(categoryName),
            controls = controls,
            routes = listOf(routeSpec(categoryName, listOf("Start") + routeLabels + "Finish")),
            pointsOverride = points,
            pointPlacemarkNames = pointNames,
            extension = extension,
            expectedMatchedControls = expectedControls,
            expectedRouteControlLabels = expectedRouteLabels?.let { mapOf(categoryName to it) }.orEmpty(),
            categoriesToAnalyze = listOf(categoryName)
        )
    }

    private fun controlsOnlyCase(name: String, box: Box): GeneratedKmlCase =
        case(
            name = name,
            raceType = RaceType.CLASSIC,
            box = box,
            categories = listOf("M21"),
            controls = classicControls(3, includeBeacon = true),
            routes = emptyList(),
            expectedMatchedCategories = 0,
            expectedImportedCategories = 0,
            expectedMatchedControls = 4,
            categoriesToAnalyze = emptyList()
        )

    private fun invalidCase(name: String, kmlText: String): GeneratedKmlCase =
        GeneratedKmlCase(
            name = name,
            project = project(RaceType.CLASSIC, listOf("M21"), classicControls(3, includeBeacon = true)),
            kmlText = kmlText,
            expectedImportFailure = true,
            categoriesToAnalyze = emptyList()
        )

    private fun singleCoordinateRouteCase(): GeneratedKmlCase {
        val controls = classicControls(2, includeBeacon = true)
        val points = pointsFor(NC_UMSTEAD, controls)
        return GeneratedKmlCase(
            name = "route placemark with too few coordinates",
            project = project(RaceType.CLASSIC, listOf("M21"), controls),
            kmlText = kml(points, listOf(RouteSpec("M21", listOf("Start")))),
            expectedMatchedCategories = 0,
            expectedImportedCategories = 0,
            expectedMatchedControls = 3,
            categoriesToAnalyze = emptyList()
        )
    }

    private fun duplicatePointNameCase(): GeneratedKmlCase {
        val controls = classicControls(3, includeBeacon = true)
        val points = pointsFor(OR_SKYLINE, controls).toMutableMap()
        val duplicateOne = points.getValue("1").copy(latitude = points.getValue("1").latitude + 0.0002)
        val text = kml(points, listOf(routeSpec("M21", "Start", "1", "2", "3", "B", "Finish")))
            .replace(
                "  </Document>",
                pointPlacemark("1", duplicateOne).prependIndent("    ") + "\n  </Document>"
            )
        return GeneratedKmlCase(
            name = "duplicate point name remains nonfatal",
            project = project(RaceType.CLASSIC, listOf("M21"), controls),
            kmlText = text,
            expectedMatchedControls = 5,
            expectedRouteControlLabels = mapOf("M21" to listOf("1", "2", "3", "B")),
            categoriesToAnalyze = listOf("M21")
        )
    }

    private fun ambiguousNumberNameCase(): GeneratedKmlCase {
        val controls = listOf(
            ControlSpec("slow-1", "1", 31, ControlPointType.CONTROL, "1"),
            ControlSpec("fast-1", "F1", 41, ControlPointType.CONTROL, "F1")
        )
        val points = pointsFor(NC_HORTON, controls).toMutableMap()
        points["Fox 1"] = points.getValue("1")
        return GeneratedKmlCase(
            name = "ambiguous embedded number label",
            project = project(RaceType.SPRINT, listOf("M21"), controls),
            kmlText = kml(points, listOf(routeSpec("M21", "Start", "Fox 1", "Finish")), pointPlacemarkNames = listOf("Start", "Fox 1", "Finish")),
            expectedMatchedCategories = 1,
            expectedImportedCategories = 1,
            expectedMatchedControls = 0,
            categoriesToAnalyze = emptyList()
        )
    }

    private fun classicPublicLabelCase(): GeneratedKmlCase {
        val controls = classicControls(2, includeBeacon = true).map {
            when (it.publicLabel) {
                "1" -> it.copy(publicLabel = "Fox 1")
                "2" -> it.copy(publicLabel = "Fox 2")
                else -> it
            }
        }
        return case(
            name = "classic public label placemark names",
            raceType = RaceType.CLASSIC,
            box = NC_UMSTEAD,
            categories = listOf("M21"),
            controls = controls,
            routes = listOf(routeSpec("M21", "Start", "Fox 1", "Fox 2", "B", "Finish")),
            expectedMatchedControls = 3,
            expectedRouteControlLabels = mapOf("M21" to listOf("'Fox 1'", "'Fox 2'", "B")),
            categoriesToAnalyze = listOf("M21")
        )
    }

    private fun classicSiCodeNameCase(): GeneratedKmlCase =
        case(
            name = "classic SI code placemark names",
            raceType = RaceType.CLASSIC,
            box = OR_WHITE_RIVER,
            categories = listOf("M21"),
            controls = classicControls(2, includeBeacon = true),
            routes = listOf(routeSpec("M21", "Start", "31", "32", "99", "Finish")),
            pointNameAliases = mapOf("31" to "1", "32" to "2", "99" to "B"),
            pointPlacemarkNames = listOf("Start", "Finish", "31", "32", "99"),
            expectedMatchedControls = 3,
            expectedRouteControlLabels = mapOf("M21" to listOf("1", "2", "B")),
            categoriesToAnalyze = listOf("M21")
        )

    private fun sprintCase(
        name: String,
        box: Box,
        includeSpectator: Boolean,
        includeBeacon: Boolean,
        slowCount: Int,
        fastCount: Int
    ): GeneratedKmlCase {
        val controls = sprintControls(slowCount, fastCount, includeSpectator, includeBeacon)
        val route = buildList {
            add("Start")
            addAll((1..slowCount).map { it.toString() })
            if (includeSpectator) add("Spectator")
            if (!includeSpectator && includeBeacon) add("B")
            addAll((1..fastCount).map { "F$it" })
            if (includeBeacon) add("B")
            add("Finish")
        }
        return case(
            name = name,
            raceType = RaceType.SPRINT,
            box = box,
            categories = listOf("M21"),
            controls = controls,
            routes = listOf(routeSpec("M21", route)),
            expectedMatchedControls = controls.size,
            categoriesToAnalyze = listOf("M21")
        )
    }

    private fun foxoringCase(name: String, box: Box, foxCount: Int, includeBeacon: Boolean): GeneratedKmlCase {
        val controls = classicControls(foxCount, includeBeacon = includeBeacon)
        val route = listOf("Start") + (1..foxCount).map { it.toString() } + listOfNotNull("B".takeIf { includeBeacon }) + "Finish"
        return case(
            name = name,
            raceType = RaceType.FOXORING,
            box = box,
            categories = listOf("M21"),
            controls = controls,
            routes = listOf(routeSpec("M21", route)),
            expectedMatchedControls = controls.size,
            categoriesToAnalyze = listOf("M21")
        )
    }

    private fun outsideElevationCoverageCase(): GeneratedKmlCase =
        case(
            name = "outside elevation coverage falls back to horizontal distance",
            raceType = RaceType.CLASSIC,
            box = OUTSIDE_DEM,
            categories = listOf("M21"),
            controls = classicControls(3, includeBeacon = true),
            routes = listOf(routeSpec("M21", "Start", "1", "2", "3", "B", "Finish")),
            expectedMatchedControls = 4,
            categoriesToAnalyze = listOf("M21"),
            analysisChecks = listOf { caseName, categoryName, analysis ->
                assertTrue("$caseName $categoryName should report missing elevation data", analysis.hasMissingElevationData)
            }
        )

    private fun case(
        name: String,
        raceType: RaceType,
        box: Box,
        categories: List<String>,
        controls: List<ControlSpec>,
        routes: List<RouteSpec>,
        pointsOverride: Map<String, CourseGeoPoint>? = null,
        pointPlacemarkNames: List<String>? = null,
        pointNameAliases: Map<String, String> = emptyMap(),
        extension: String = ".kml",
        categoryOverrideId: String? = null,
        expectedMatchedCategories: Int? = routes.size.takeIf { it > 0 },
        expectedImportedCategories: Int? = routes.size.takeIf { it > 0 },
        expectedMatchedControls: Int? = null,
        expectedAssignedControls: Int? = null,
        expectedRouteControlLabels: Map<String, List<String>> = emptyMap(),
        expectedAssignedSiCodes: Map<String, List<Int>> = emptyMap(),
        applyAssignments: Boolean = false,
        categoriesToAnalyze: List<String> = categories,
        analysisChecks: List<(String, String, DesktopCourseAnalysisSummary) -> Unit> = emptyList(),
        verify: (EventProjectFile, DesktopCourseKmlImportSummary) -> Unit = { _, _ -> }
    ): GeneratedKmlCase {
        val points = (pointsOverride ?: pointsFor(box, controls))
            .let { base ->
                pointNameAliases.entries.fold(base.toMutableMap()) { current, (alias, target) ->
                    current[alias] = current.getValue(target)
                    current
                }
            }
        return GeneratedKmlCase(
            name = name,
            project = project(raceType, categories, controls),
            kmlText = kml(points, routes, pointPlacemarkNames ?: points.keys.toList()),
            extension = extension,
            categoryOverrideId = categoryOverrideId,
            expectedMatchedCategories = expectedMatchedCategories,
            expectedImportedCategories = expectedImportedCategories,
            expectedMatchedControls = expectedMatchedControls,
            expectedAssignedControls = expectedAssignedControls,
            expectedRouteControlLabels = expectedRouteControlLabels,
            expectedAssignedSiCodes = expectedAssignedSiCodes,
            applyAssignments = applyAssignments,
            categoriesToAnalyze = categoriesToAnalyze,
            analysisChecks = analysisChecks,
            verify = verify
        )
    }

    private fun project(raceType: RaceType, categories: List<String>, controls: List<ControlSpec>): EventProjectFile {
        val eventControls = controls.map { spec ->
            EventControl(
                id = spec.id,
                raceId = RACE_ID,
                label = spec.label,
                siCode = spec.siCode,
                type = spec.type,
                publicLabel = spec.publicLabel
            )
        }
        return EventProjectFile(
            raceData = EventRaceData(
                race = EventRace(
                    id = RACE_ID,
                    name = "Generated KML Coverage",
                    apiKey = "",
                    startDateTimeIso = "2026-06-10T09:00:00",
                    raceType = raceType,
                    raceLevel = RaceLevel.PRACTICE,
                    raceBand = RaceBand.M80,
                    timeLimitSeconds = 7_200
                ),
                categories = categories.mapIndexed { index, name ->
                    val categoryId = "category-${name.lowercase().replace(Regex("[^a-z0-9]+"), "")}"
                    EventCategoryData(
                        category = EventCategory(
                            id = categoryId,
                            raceId = RACE_ID,
                            name = name,
                            isMan = name.startsWith("M", ignoreCase = true),
                            maxAge = name.filter(Char::isDigit).toIntOrNull(),
                            lengthMeters = 0,
                            climbMeters = 0,
                            order = index + 1,
                            differentProperties = false,
                            raceType = null,
                            raceBand = null,
                            timeLimitSeconds = null,
                            controlPointsString = ""
                        ),
                        controlPoints = emptyList(),
                        competitors = emptyList()
                    )
                },
                aliases = emptyList(),
                competitorData = emptyList(),
                unmatchedReadoutData = emptyList(),
                controls = eventControls
            )
        )
    }

    private fun classicControls(foxCount: Int, includeBeacon: Boolean): List<ControlSpec> =
        (1..foxCount).map { number ->
            ControlSpec(
                id = "control-$number",
                label = number.toString(),
                siCode = 30 + number,
                type = ControlPointType.CONTROL,
                publicLabel = number.toString()
            )
        } + listOfNotNull(
            ControlSpec("control-beacon", "Beacon", 99, ControlPointType.BEACON, "B").takeIf { includeBeacon }
        )

    private fun sprintControls(
        slowCount: Int,
        fastCount: Int,
        includeSpectator: Boolean,
        includeBeacon: Boolean
    ): List<ControlSpec> =
        (1..slowCount).map { number ->
            ControlSpec("control-slow-$number", number.toString(), 30 + number, ControlPointType.CONTROL, number.toString())
        } +
            (1..fastCount).map { number ->
                ControlSpec("control-fast-$number", "F$number", 40 + number, ControlPointType.CONTROL, "F$number")
            } +
            listOfNotNull(
                ControlSpec("control-spectator", "Spectator", 46, ControlPointType.SEPARATOR, "Spectator").takeIf { includeSpectator },
                ControlSpec("control-beacon", "Beacon", 99, ControlPointType.BEACON, "B").takeIf { includeBeacon }
            )

    private fun pointsFor(
        box: Box,
        controls: List<ControlSpec>,
        tightSpacing: Boolean = false
    ): Map<String, CourseGeoPoint> {
        val fractions = if (tightSpacing) {
            listOf(
                "1" to (0.30 to 0.30),
                "2" to (0.302 to 0.302),
                "3" to (0.50 to 0.76),
                "4" to (0.65 to 0.30),
                "5" to (0.80 to 0.70),
                "B" to (0.86 to 0.48)
            )
        } else {
            listOf(
                "1" to (0.22 to 0.72),
                "2" to (0.35 to 0.20),
                "3" to (0.48 to 0.82),
                "4" to (0.62 to 0.28),
                "5" to (0.78 to 0.70),
                "6" to (0.18 to 0.45),
                "7" to (0.42 to 0.56),
                "8" to (0.68 to 0.58),
                "9" to (0.30 to 0.88),
                "10" to (0.58 to 0.12),
                "11" to (0.74 to 0.88),
                "12" to (0.12 to 0.24),
                "13" to (0.88 to 0.18),
                "14" to (0.16 to 0.80),
                "15" to (0.84 to 0.38),
                "16" to (0.28 to 0.42),
                "17" to (0.54 to 0.68),
                "18" to (0.70 to 0.14),
                "19" to (0.38 to 0.06),
                "20" to (0.92 to 0.72),
                "F1" to (0.58 to 0.62),
                "F2" to (0.72 to 0.22),
                "F3" to (0.84 to 0.58),
                "F4" to (0.64 to 0.86),
                "F5" to (0.90 to 0.32),
                "F6" to (0.76 to 0.80),
                "Spectator" to (0.52 to 0.52),
                "B" to (0.88 to 0.48)
            )
        }.toMap()
        return buildMap {
            put("Start", box.point(0.08, 0.10))
            put("Finish", box.point(0.94, 0.90))
            controls.forEach { spec ->
                val fractionKey = when {
                    spec.publicLabel in fractions -> spec.publicLabel
                    spec.type == ControlPointType.CONTROL -> spec.publicLabel.filter(Char::isDigit).takeIf { it.isNotBlank() }
                    else -> null
                }
                fractionKey?.let(fractions::get)?.let { (x, y) ->
                    put(spec.publicLabel, box.point(x, y))
                }
            }
        }
    }

    private fun kml(
        points: Map<String, CourseGeoPoint>,
        routes: List<RouteSpec>,
        pointPlacemarkNames: List<String> = points.keys.toList()
    ): String =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <kml xmlns="http://www.opengis.net/kml/2.2">
          <Document>
        ${pointPlacemarkNames.joinToString("\n") { pointPlacemark(it, points.getValue(it)) }}
        ${routes.joinToString("\n") { routePlacemark(it, points) }}
          </Document>
        </kml>
        """.trimIndent().trimStart()

    private fun pointPlacemark(name: String, point: CourseGeoPoint): String =
        """
            <Placemark>
              <name>${name.xml()}</name>
              <Point><coordinates>${point.longitude},${point.latitude},0</coordinates></Point>
            </Placemark>
        """.trimIndent()

    private fun routePlacemark(route: RouteSpec, points: Map<String, CourseGeoPoint>): String =
        """
            <Placemark>
              <name>${route.name.xml()}</name>
              <LineString>
                <coordinates>
        ${route.labels.joinToString("\n") { label ->
            val point = points.getValue(label)
            "          ${point.longitude},${point.latitude},0"
        }}
                </coordinates>
              </LineString>
            </Placemark>
        """.trimIndent()

    private fun writeCourseFile(testCase: GeneratedKmlCase): Path {
        val path = Files.createTempFile(testCase.name.lowercase().replace(Regex("[^a-z0-9]+"), "-"), testCase.extension)
        if (testCase.extension.equals(".kmz", ignoreCase = true)) {
            ZipOutputStream(Files.newOutputStream(path)).use { zip ->
                zip.putNextEntry(ZipEntry("doc.kml"))
                zip.write(testCase.kmlText.toByteArray())
                zip.closeEntry()
            }
        } else {
            Files.writeString(path, testCase.kmlText)
        }
        return path
    }

    private fun EventProjectFile.protectedInfoFor(categoryName: String): org.openardf.radiooracle.shared.event.ProtectedCourseInfo {
        val category = raceData.categories.single { it.category.name == categoryName }.category
        return DesktopProtectedCourseOrder.decryptCourseInfo(requireNotNull(category.encryptedCourseInfo), PASSWORD)
    }

    private fun syntheticElevation(point: CourseGeoPoint): Double? {
        val box = listOf(NC_UMSTEAD, NC_HORTON, OR_SKYLINE, OR_WHITE_RIVER).firstOrNull { it.contains(point) }
            ?: return null
        val x = (point.longitude - box.minLongitude) / (box.maxLongitude - box.minLongitude)
        val y = (point.latitude - box.minLatitude) / (box.maxLatitude - box.minLatitude)
        return box.baseElevation + x * 28.0 + y * 17.0
    }

    private fun routeSpec(name: String, vararg labels: String): RouteSpec =
        RouteSpec(name, labels.toList())

    private fun routeSpec(name: String, labels: List<String>): RouteSpec =
        RouteSpec(name, labels)

    private fun String.xml(): String =
        replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private data class GeneratedKmlCase(
        val name: String,
        val project: EventProjectFile,
        val kmlText: String,
        val extension: String = ".kml",
        val categoryOverrideId: String? = null,
        val expectedImportFailure: Boolean = false,
        val expectedMatchedCategories: Int? = null,
        val expectedImportedCategories: Int? = null,
        val expectedMatchedControls: Int? = null,
        val expectedAssignedControls: Int? = null,
        val expectedRouteControlLabels: Map<String, List<String>> = emptyMap(),
        val expectedAssignedSiCodes: Map<String, List<Int>> = emptyMap(),
        val applyAssignments: Boolean = false,
        val categoriesToAnalyze: List<String>,
        val analysisChecks: List<(String, String, DesktopCourseAnalysisSummary) -> Unit> = emptyList(),
        val verify: (EventProjectFile, DesktopCourseKmlImportSummary) -> Unit = { _, _ -> }
    )

    private data class ControlSpec(
        val id: String,
        val label: String,
        val siCode: Int,
        val type: ControlPointType,
        val publicLabel: String
    )

    private data class RouteSpec(
        val name: String,
        val labels: List<String>
    )

    private data class Box(
        val minLatitude: Double,
        val maxLatitude: Double,
        val minLongitude: Double,
        val maxLongitude: Double,
        val baseElevation: Double
    ) {
        fun point(xFraction: Double, yFraction: Double): CourseGeoPoint =
            CourseGeoPoint(
                latitude = minLatitude + (maxLatitude - minLatitude) * yFraction,
                longitude = minLongitude + (maxLongitude - minLongitude) * xFraction
            )

        fun contains(point: CourseGeoPoint): Boolean =
            point.latitude in minLatitude..maxLatitude && point.longitude in minLongitude..maxLongitude
    }

    private companion object {
        private const val PASSWORD = "generated-course-key"
        private const val RACE_ID = "generated-race"

        private val NC_UMSTEAD = Box(
            minLatitude = 35.818243444125045,
            maxLatitude = 35.903216555874955,
            minLongitude = -78.81388809730534,
            maxLongitude = -78.70358590269467,
            baseElevation = 120.0
        )
        private val NC_HORTON = Box(
            minLatitude = 36.11104544412505,
            maxLatitude = 36.16244555587495,
            minLongitude = -78.86945552795851,
            maxLongitude = -78.8191234720415,
            baseElevation = 150.0
        )
        private val OR_SKYLINE = Box(
            minLatitude = 45.158500444125046,
            maxLatitude = 45.179460555874954,
            minLongitude = -121.70017883620581,
            maxLongitude = -121.6487621637942,
            baseElevation = 1_000.0
        )
        private val OR_WHITE_RIVER = Box(
            minLatitude = 45.280053444125045,
            maxLatitude = 45.32070555587496,
            minLongitude = -121.69091858396932,
            maxLongitude = -121.63296841603068,
            baseElevation = 900.0
        )
        private val OUTSIDE_DEM = Box(
            minLatitude = 33.90,
            maxLatitude = 33.95,
            minLongitude = -112.10,
            maxLongitude = -112.02,
            baseElevation = 0.0
        )
    }
}
