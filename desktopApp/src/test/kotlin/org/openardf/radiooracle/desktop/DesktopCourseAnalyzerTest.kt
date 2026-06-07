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
import org.openardf.radiooracle.shared.event.ProtectedCourseControlPoint
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.event.ProtectedCourseObjectPoint
import org.openardf.radiooracle.shared.event.ProtectedCourseObjectType
import org.openardf.radiooracle.shared.event.ProtectedCourseRoutePoint
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.roundToInt

class DesktopCourseAnalyzerTest {
    @Test
    fun calculatesShortestRouteAndFlagsProvidedOrderMismatch() {
        val projectFile = projectFile(foxCount = 5)
        val protectedInfo = protectedInfo(foxCount = 5)

        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = projectFile,
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo,
            protectedIdealOrderText = "35 34 33 32 31 Beacon"
        )

        assertEquals(emptyList<String>(), summary.missingElements)
        assertEquals(120, summary.calculatedRouteCount)
        assertEquals(listOf("S", "31", "32", "33", "34", "35", "B"), requireNotNull(summary.calculatedRouteSection).routeOrder)
        assertEquals(summary.calculatedRouteSection?.secondaryRouteOrder, summary.calculatedIdealOrder)
        assertEquals(listOf("S", "35", "34", "33", "32", "31", "B"), summary.providedIdealOrder)
        assertFalse(summary.idealOrderMatches!!)
        assertTrue(summary.calculatedStraightLineMeters!! < summary.providedStraightLineMeters!!)
    }

    @Test
    fun calculatesProtectedEffectiveLengthIdealTimeAndClassicWaitRows() {
        val projectFile = projectFile(foxCount = 3)
        val protectedInfo = protectedInfo(foxCount = 3)

        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = projectFile,
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo,
            protectedIdealOrderText = "31 32 33 Beacon"
        )

        assertEquals(6, summary.calculatedRouteCount)
        assertEquals(true, summary.idealOrderMatches)
        assertEquals(4_000, summary.routeLengthMeters)
        assertEquals(100, summary.climbMeters)
        assertEquals(5_000, summary.effectiveLengthMeters)
        assertNotNull(summary.providedRouteSection)
        assertNotNull(summary.calculatedRouteSection)
        assertEquals("Section 1: Stored route analysis", summary.providedRouteSection?.title)
        assertEquals("Section 2: Calculated ideal route", summary.calculatedRouteSection?.title)
        assertTrue(summary.providedRouteSection?.explanation.orEmpty().contains("effective length"))
        assertTrue(summary.providedRouteSection?.explanation.orEmpty().contains("does not guarantee 3 m source terrain data"))
        assertTrue(summary.providedRouteSection?.explanation.orEmpty().contains("does not currently know map passability"))
        assertTrue(summary.providedRouteSection?.explanation.orEmpty().contains("4:38 min/km for Classic-style courses (3.6 m/s)"))
        assertTrue(summary.providedRouteSection?.explanation.orEmpty().contains("not yet adjusted by category age or gender"))
        assertTrue(summary.calculatedRouteSection?.explanation.orEmpty().contains("foxes and any spectator"))
        assertTrue(summary.calculatedRouteSection?.explanation.orEmpty().contains("USGS 3DEP source DEM resolution varies"))
        assertTrue(summary.calculatedRouteSection?.explanation.orEmpty().contains("true on-foot route and wait timing differ"))
        assertTrue(summary.calculatedRouteSection?.explanation.orEmpty().contains("movement time uses effective length for each leg"))
        assertEquals(2, summary.profileComparison.size)
        assertEquals(listOf("31", "32", "33"), summary.profileComparison.first { it.title == "Stored route" }.markers.map { it.label })
        val calculatedRouteOrder = requireNotNull(summary.calculatedRouteSection).secondaryRouteOrder
        val calculatedFoxLabels = calculatedRouteOrder.drop(1).dropLast(1)
        assertEquals(calculatedFoxLabels, summary.profileComparison.first { it.title == "Calculated route (calculated fox numbering)" }.markers.map { it.label })
        assertEquals(2, summary.routeMaps.size)
        assertEquals(false, summary.hasMissingElevationData)
        assertNotNull(summary.estimatedIdealSeconds)
        assertEquals(5, summary.elevationProfile.size)
        assertEquals(0, summary.elevationProfile.first().distanceMeters)
        assertEquals(100.0, summary.elevationProfile.first().elevationMeters, 0.001)
        assertEquals(listOf("S -> 31", "31 -> 32", "32 -> 33", "33 -> B", "B -> F"), summary.providedLegRows.map { "${it.fromLabel} -> ${it.toLabel}" })
        assertEquals(calculatedRouteOrder.zipWithNext().map { (from, to) -> "$from -> $to" } + "B -> F", summary.calculatedLegRows.map { "${it.fromLabel} -> ${it.toLabel}" })
        assertTrue(summary.providedLegRows.all { it.lengthMeters != null && it.splitSeconds != null && it.cumulativeSeconds != null })
        assertEquals(summary.estimatedIdealSeconds, summary.providedLegRows.last().cumulativeSeconds)
        assertEquals(listOf("31", "32", "33"), summary.waitRows.map { it.controlLabel })
        summary.providedLegRows.take(3).zip(summary.waitRows).forEach { (leg, wait) ->
            assertEquals(wait.arrivalSeconds + wait.waitSeconds + 30, leg.cumulativeSeconds)
            assertEquals(wait.waitSeconds, leg.waitSeconds)
            assertEquals(30, leg.findPunchSeconds)
        }
        summary.calculatedLegRows.take(3).zip(requireNotNull(summary.calculatedRouteSection).waitRows).forEach { (leg, wait) ->
            assertEquals(wait.arrivalSeconds + wait.waitSeconds + 30, leg.cumulativeSeconds)
            assertEquals(wait.waitSeconds, leg.waitSeconds)
            assertEquals(30, leg.findPunchSeconds)
        }
        assertEquals(
            ((requireNotNull(summary.calculatedLegRows.first().lengthMeters) + 100.0) / 3.6).roundToInt(),
            requireNotNull(summary.calculatedRouteSection).waitRows.first().arrivalSeconds
        )
        assertEquals(listOf(null, null), summary.providedLegRows.takeLast(2).map { it.waitSeconds })
        assertEquals(listOf(null, null), summary.providedLegRows.takeLast(2).map { it.findPunchSeconds })
        assertEquals(summary.calculatedRouteSection?.estimatedIdealSeconds, summary.calculatedLegRows.last().cumulativeSeconds)
        assertEquals(listOf(null, null), summary.calculatedLegRows.takeLast(2).map { it.waitSeconds })
        assertEquals(listOf(null, null), summary.calculatedLegRows.takeLast(2).map { it.findPunchSeconds })
        assertTrue(summary.metrics.any { it.label == "Effective length" && it.value == "5.00 km" })
        assertTrue(
            "Metrics were ${summary.metrics}",
            summary.metrics.any { it.label == "Classic shortest-route climb limit" && it.status == DesktopCourseMetricStatus.Good }
        )
    }

    @Test
    fun calculatedRouteSamplesElevationCacheBetweenCoursePoints() {
        val projectFile = projectFile(foxCount = 3)
        val protectedInfo = protectedInfo(foxCount = 3)

        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = projectFile,
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo,
            protectedIdealOrderText = "31 32 33 Beacon",
            elevationLookup = { point ->
                val progress = ((point.longitude + 95.0) / 0.04).coerceIn(0.0, 1.0)
                100.0 + progress * 40.0 + if (progress in 0.20..0.30) 80.0 else 0.0
            }
        )

        val calculatedProfile = summary.profileComparison.first { it.title == "Calculated route (calculated fox numbering)" }
        assertTrue(calculatedProfile.profile.size > protectedInfo.route.size)
        assertTrue(calculatedProfile.profile.any { it.elevationMeters > 170.0 })
        val calculatedFoxLabels = requireNotNull(summary.calculatedRouteSection).secondaryRouteOrder.drop(1).dropLast(1)
        assertEquals(calculatedFoxLabels, calculatedProfile.markers.map { it.label })
        assertTrue(calculatedProfile.markers.all { it.distanceMeters > 0 && it.elevationMeters > 0.0 })
    }

    @Test
    fun omitsClassicWaitAndFindPunchAllowanceForSprintAndFoxoring() {
        listOf(RaceType.SPRINT, RaceType.FOXORING).forEach { raceType ->
            val summary = DesktopCourseAnalyzer.analyze(
                projectFile = projectFile(foxCount = 3, raceType = raceType),
                categoryId = CATEGORY_ID,
                protectedCourseInfo = protectedInfo(foxCount = 3),
                protectedIdealOrderText = "31 32 33 Beacon"
            )

            assertEquals("raceType=$raceType", emptyList<DesktopCourseWaitRow>(), summary.waitRows)
            assertEquals("raceType=$raceType", emptyList<DesktopCourseWaitRow>(), summary.providedRouteSection?.waitRows)
            assertEquals("raceType=$raceType", emptyList<DesktopCourseWaitRow>(), summary.calculatedRouteSection?.waitRows)
            assertTrue("raceType=$raceType", summary.providedLegRows.all { it.waitSeconds == null && it.findPunchSeconds == null })
            assertTrue("raceType=$raceType", summary.calculatedLegRows.all { it.waitSeconds == null && it.findPunchSeconds == null })
        }
    }

    @Test
    fun flagsClassicShortestRouteClimbOverSixPercent() {
        val projectFile = projectFile(foxCount = 3)
        val protectedInfo = protectedInfo(foxCount = 3).copy(
            controlPoints = protectedInfo(foxCount = 3).controlPoints.mapIndexed { index, control ->
                control.copy(elevationMeters = 100.0 + index * 100.0)
            }
        )

        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = projectFile,
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo,
            protectedIdealOrderText = "31 32 33 Beacon"
        )

        assertTrue(
            "Metrics were ${summary.metrics}",
            summary.metrics.any { it.label == "Classic shortest-route climb limit" && it.status == DesktopCourseMetricStatus.Warning }
        )
    }

    @Test
    fun exportsCourseAnalysisReportTextAndPdf() {
        val projectFile = projectFile(foxCount = 3)
        val protectedInfo = protectedInfo(foxCount = 3)
        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = projectFile,
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo,
            protectedIdealOrderText = "31 32 33 Beacon"
        )
        val reportText = DesktopCourseAnalysisExports.reportText(summary)

        assertTrue(reportText.contains("Course Analyzer"))
        assertTrue(reportText.contains("Section 2: Calculated ideal route"))
        assertTrue(reportText.contains("Route order (stored fox numbering):"))
        assertTrue(reportText.contains("Route order (calculated fox numbering):"))
        assertTrue(reportText.contains("Calculated ideal route (calculated fox numbering):"))
        assertTrue(reportText.contains("Movement time:"))
        assertTrue(reportText.contains("(waits "))

        val pdfPath = Files.createTempFile("course-analysis", ".pdf")
        val exportPaths = DesktopCourseAnalysisExports.exportPdfAndKml(pdfPath, summary)
        val pdfBytes = Files.readAllBytes(pdfPath)
        assertTrue(String(pdfBytes.take(8).toByteArray()).startsWith("%PDF-1.4"))
        assertTrue(String(pdfBytes).contains("Course Analyzer"))
        assertTrue(String(pdfBytes).contains("Elevation Profile Graphics"))
        assertTrue(String(pdfBytes).contains("2D Route Depiction Graphics"))
        assertPdfInfoCanRead(pdfPath)
        assertEquals(pdfPath, exportPaths.pdfPath)
        assertEquals(pdfPath.resolveSibling("${pdfPath.fileName.toString().removeSuffix(".pdf")}.kml"), exportPaths.kmlPath)

        val multiPagePdfPath = Files.createTempFile("course-analysis-multipage", ".pdf")
        DesktopCourseAnalysisExports.exportPdf(
            multiPagePdfPath,
            summary.copy(missingElements = (1..120).map { "PDF validation filler line $it." })
        )
        assertPdfInfoCanRead(multiPagePdfPath)

        val kmlText = Files.readString(exportPaths.kmlPath)
        assertTrue(kmlText.contains("<name>Stored foxes and route</name>"))
        assertTrue(kmlText.contains("<name>Calculated foxes and route</name>"))
        assertTrue(kmlText.contains("<LineString>"))
        assertTrue(kmlText.contains("<Point>"))
        assertTrue(kmlText.contains("<name>31</name>"))
    }

    @Test
    fun checksWhetherRenumberingCanReduceClassicWaitTime() {
        val projectFile = projectFile(
            foxCount = 3,
            publicLabels = listOf("33", "32", "31")
        )
        val protectedInfo = protectedInfo(foxCount = 3)

        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = projectFile,
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo,
            protectedIdealOrderText = "31 32 33 Beacon"
        )

        val renumbering = requireNotNull(summary.waitRenumbering)
        val sectionRenumbering = requireNotNull(summary.providedRouteSection?.waitRenumbering)
        assertTrue(renumbering.improvesWait)
        assertTrue(renumbering.currentTotalWaitSeconds > renumbering.bestTotalWaitSeconds)
        assertEquals(renumbering.currentTotalWaitSeconds - renumbering.bestTotalWaitSeconds, sectionRenumbering.currentTotalWaitSeconds - sectionRenumbering.bestTotalWaitSeconds)
        assertEquals(listOf("33", "32", "31"), renumbering.assignments.map { it.controlLabel })
        assertEquals(listOf("33", "32", "31"), renumbering.assignments.map { it.currentSlotLabel })
        assertTrue(renumbering.assignments.map { it.suggestedSlotLabel } != renumbering.assignments.map { it.currentSlotLabel })
    }

    @Test
    fun calculatedSectionDisplaysOptimizedFoxNumberingAfterOriginalRouteOrder() {
        val projectFile = projectFile(
            foxCount = 3,
            publicLabels = listOf("33", "32", "31")
        )
        val protectedInfo = protectedInfo(foxCount = 3)

        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = projectFile,
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo,
            protectedIdealOrderText = "31 32 33 Beacon"
        )

        val section = requireNotNull(summary.calculatedRouteSection)
        val renumbering = requireNotNull(section.waitRenumbering)
        val calculatedOrder = listOf("S") + renumbering.assignments.map { it.suggestedSlotLabel } + "B"
        assertEquals("Route order (stored fox numbering)", section.routeOrderLabel)
        assertEquals(listOf("S", "33", "32", "31", "B"), section.routeOrder)
        assertEquals("Route order (calculated fox numbering)", section.secondaryRouteOrderLabel)
        assertEquals(calculatedOrder, section.secondaryRouteOrder)
        assertEquals(calculatedOrder, summary.calculatedIdealOrder)
        assertEquals(
            calculatedOrder.zipWithNext().map { (from, to) -> "$from -> $to" } + "B -> F",
            summary.calculatedLegRows.map { "${it.fromLabel} -> ${it.toLabel}" }
        )
        assertEquals(renumbering.assignments.map { it.suggestedSlotLabel }, section.waitRows.map { it.controlLabel })
        assertEquals(
            renumbering.assignments.map { it.suggestedSlotLabel },
            summary.profileComparison.first { it.title == "Calculated route (calculated fox numbering)" }.markers.map { it.label }
        )
        assertEquals(
            calculatedOrder + "F",
            requireNotNull(section.routeMap).routeLabels
        )
    }

    @Test
    fun appliesCalculatedRouteAndNumberingToSavedCourseData() {
        val projectFile = projectFile(
            foxCount = 3,
            publicLabels = listOf("Fox 3", "Fox 2", "Fox 1")
        )
        val protectedInfo = protectedInfo(foxCount = 3)
        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = projectFile,
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo,
            protectedIdealOrderText = "31 32 33 Beacon"
        )
        val application = requireNotNull(summary.calculatedRouteApplication)

        val (updatedProject, updatedCourseInfo) = DesktopCourseAnalysisApplier.applyCalculatedRoute(
            projectFile = projectFile,
            courseInfo = protectedInfo,
            application = application,
            password = "test-password"
        )

        val updatedCategory = updatedProject.raceData.categories.single { it.category.id == CATEGORY_ID }.category
        assertEquals(application.idealOrderText, DesktopProtectedCourseOrder.decrypt(requireNotNull(updatedCategory.encryptedIdealOrder), "test-password"))
        val decryptedCourseInfo = DesktopProtectedCourseOrder.decryptCourseInfo(requireNotNull(updatedCategory.encryptedCourseInfo), "test-password")
        assertTrue(application.idealOrderText.contains("'Fox "))
        assertEquals(
            application.foxAssignments.map { it.calculatedLabel } + "Beacon",
            org.openardf.radiooracle.shared.course.ControlPointRules.tokenizeControlPoints(application.idealOrderText)
        )
        assertEquals(application.idealOrderText, decryptedCourseInfo.idealOrder)
        assertEquals(application.routeLengthMeters, decryptedCourseInfo.lengthMeters)
        assertEquals(application.climbMeters, decryptedCourseInfo.climbMeters)
        assertEquals("Course Analyzer calculated route", decryptedCourseInfo.sourceName)
        assertEquals(application.routePoints.size, decryptedCourseInfo.sampledPointCount)
        assertEquals(application.routePoints.map { it.latitude to it.longitude }, decryptedCourseInfo.route.map { it.latitude to it.longitude })
        assertEquals(application.idealOrderText, updatedCourseInfo.idealOrder)
        assertEquals(application.routePoints.size, updatedCourseInfo.route.size)
        val publicLabelsByControlId = updatedProject.raceData.controls.associate { it.id to it.publicLabel }
        application.foxAssignments.forEach { assignment ->
            assertEquals(assignment.calculatedLabel, publicLabelsByControlId[assignment.controlId])
        }

        val updatedSummary = DesktopCourseAnalyzer.analyze(
            projectFile = updatedProject,
            categoryId = CATEGORY_ID,
            protectedCourseInfo = decryptedCourseInfo,
            protectedIdealOrderText = DesktopProtectedCourseOrder.decrypt(requireNotNull(updatedCategory.encryptedIdealOrder), "test-password")
        )
        assertTrue(updatedSummary.missingElements.none { it.contains("Protected ideal order") })
    }

    @Test
    fun updatesProtectedControlLocationAndInvalidatesStoredRouteGeometry() {
        val projectFile = projectFile(foxCount = 3)
        val protectedInfo = protectedInfo(foxCount = 3)

        val result = DesktopProtectedControlLocationUpdater.applyControlLocation(
            projectFile = projectFile,
            courseInfoByCategoryId = mapOf(CATEGORY_ID to protectedInfo),
            controlId = "control-2",
            latitudeText = "39.123456",
            longitudeText = "-95.654321",
            password = "test-password",
            elevationLookup = { 222.0 }
        )

        val updatedControl = result.projectFile.raceData.controls.single { it.id == "control-2" }
        assertEquals(null, updatedControl.latitude)
        assertEquals(null, updatedControl.longitude)
        assertEquals(listOf("M21"), result.affectedCategoryNames)
        val updatedCategory = result.projectFile.raceData.categories.single { it.category.id == CATEGORY_ID }.category
        val decryptedCourseInfo = DesktopProtectedCourseOrder.decryptCourseInfo(
            requireNotNull(updatedCategory.encryptedCourseInfo),
            "test-password"
        )
        val updatedProtectedControl = decryptedCourseInfo.controlPoints.single { it.controlId == "control-2" }
        assertEquals(39.123456, updatedProtectedControl.latitude, 0.000001)
        assertEquals(-95.654321, updatedProtectedControl.longitude, 0.000001)
        assertEquals(222.0, requireNotNull(updatedProtectedControl.elevationMeters), 0.001)
        val updatedCourseObject = decryptedCourseInfo.courseObjects.single { it.id == "control-2" }
        assertEquals(39.123456, updatedCourseObject.latitude, 0.000001)
        assertEquals(-95.654321, updatedCourseObject.longitude, 0.000001)
        assertEquals(null, decryptedCourseInfo.lengthMeters)
        assertEquals(null, decryptedCourseInfo.climbMeters)
        assertEquals(0, decryptedCourseInfo.sampledPointCount)
        assertEquals(emptyList<ProtectedCourseRoutePoint>(), decryptedCourseInfo.route)

        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = result.projectFile,
            categoryId = CATEGORY_ID,
            protectedCourseInfo = decryptedCourseInfo,
            protectedIdealOrderText = decryptedCourseInfo.idealOrder
        )
        assertEquals(null, summary.providedRouteSection)
        assertNotNull(summary.calculatedRouteSection)
    }

    @Test
    fun reportsMissingProtectedDataBeforePartialAnalysis() {
        val projectFile = projectFile(foxCount = 3)

        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = projectFile,
            categoryId = CATEGORY_ID,
            protectedCourseInfo = null,
            protectedIdealOrderText = null
        )

        assertTrue(summary.missingElements.any { it.contains("Protected route data") })
        assertTrue(summary.missingElements.any { it.contains("Protected route geometry") })
        assertEquals(null, summary.providedRouteSection)
        assertEquals(null, summary.calculatedRouteSection)
        assertEquals(0, summary.calculatedRouteCount)
        assertEquals(null, summary.estimatedIdealSeconds)
    }

    @Test
    fun reportsMissingElevationSamples() {
        val projectFile = projectFile(foxCount = 3)
        val protectedInfo = protectedInfo(foxCount = 3).copy(
            route = protectedInfo(foxCount = 3).route.mapIndexed { index, point ->
                if (index == 1) point.copy(elevationMeters = null) else point
            }
        )

        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = projectFile,
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo,
            protectedIdealOrderText = "31 32 33 Beacon"
        )

        assertTrue(summary.missingElements.any { it.contains("Route elevation samples") })
        assertEquals(true, summary.hasMissingElevationData)
        assertNotNull(summary.estimatedIdealSeconds)
        assertEquals("Horizontal route length", summary.providedRouteSection?.comparisonLengthLabel)
        assertEquals(emptyList<DesktopCourseElevationProfilePoint>(), summary.elevationProfile)
    }

    @Test
    fun reportsMissingProtectedControlElevations() {
        val projectFile = projectFile(foxCount = 3)
        val protectedInfo = protectedInfo(foxCount = 3).copy(
            controlPoints = protectedInfo(foxCount = 3).controlPoints.map {
                it.copy(elevationMeters = null)
            }
        )

        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = projectFile,
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo,
            protectedIdealOrderText = "31 32 33 Beacon"
        )

        assertTrue(summary.missingElements.any { it.contains("Protected control point elevations") })
        assertEquals(true, summary.hasMissingElevationData)
    }

    @Test
    fun missingControlLocationsDoNotRequestElevationRetrieval() {
        val projectFile = projectFile(foxCount = 3)
        val protectedInfo = protectedInfo(foxCount = 3).copy(
            controlPoints = emptyList(),
            courseObjects = emptyList()
        )

        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = projectFile,
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo,
            protectedIdealOrderText = "31 32 33 Beacon"
        )

        assertTrue(summary.missingElements.any { it.contains("Course object points are missing") })
        assertTrue(summary.missingElements.any { it.contains("Location latitude/longitude is missing") })
        assertEquals(false, summary.hasMissingElevationData)
    }

    @Test
    fun resolvesProtectedCoordinatesByVisibleLabelWhenStoredControlIdsDiffer() {
        val projectFile = projectFile(
            foxCount = 3,
            publicLabels = listOf("Fox 1", "Fox 2", "Fox 3")
        )
        val protectedInfo = protectedInfo(foxCount = 3).copy(
            controlPoints = protectedInfo(foxCount = 3).controlPoints.mapIndexed { index, control ->
                if (control.type == ControlPointType.BEACON) {
                    control.copy(controlId = "stale-beacon")
                } else {
                    control.copy(
                        controlId = "stale-${index + 1}",
                        label = (index + 1).toString()
                    )
                }
            },
            courseObjects = protectedInfo(foxCount = 3).courseObjects.mapIndexed { index, courseObject ->
                when (courseObject.type) {
                    ProtectedCourseObjectType.CONTROL -> courseObject.copy(
                        id = "stale-object-$index",
                        label = index.toString()
                    )
                    ProtectedCourseObjectType.BEACON -> courseObject.copy(id = "stale-beacon")
                    else -> courseObject
                }
            }
        )

        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = projectFile,
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo,
            protectedIdealOrderText = "31 32 33 Beacon"
        )

        assertTrue(summary.missingElements.none { it.contains("Location latitude/longitude is missing") })
        assertEquals(listOf("Fox 1", "Fox 2", "Fox 3"), summary.waitRows.map { it.controlLabel })
    }

    @Test
    fun resolvesProtectedCoordinatesBySlotEquivalentAndUniqueBeaconWhenStoredControlIdsDiffer() {
        val projectFile = projectFile(
            foxCount = 3,
            publicLabels = listOf("Fox 1", "Fox 2", "Fox 3"),
            siCodes = listOf(101, 102, 103)
        )
        val protectedInfo = protectedInfo(foxCount = 3).copy(
            controlPoints = protectedInfo(foxCount = 3).controlPoints.map { control ->
                if (control.type == ControlPointType.BEACON) {
                    control.copy(controlId = "stale-beacon", label = "M")
                } else {
                    control.copy(controlId = "stale-${control.label}")
                }
            },
            courseObjects = protectedInfo(foxCount = 3).courseObjects.map { courseObject ->
                when (courseObject.type) {
                    ProtectedCourseObjectType.CONTROL -> courseObject.copy(id = "stale-object-${courseObject.label}")
                    ProtectedCourseObjectType.BEACON -> courseObject.copy(id = "stale-beacon", label = "M")
                    else -> courseObject
                }
            }
        )

        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = projectFile,
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo,
            protectedIdealOrderText = "31 32 33 Beacon"
        )

        assertTrue(summary.missingElements.none { it.contains("Location latitude/longitude is missing") })
        assertEquals(listOf("Fox 1", "Fox 2", "Fox 3"), summary.waitRows.map { it.controlLabel })
    }

    private fun projectFile(
        foxCount: Int,
        publicLabels: List<String>? = null,
        siCodes: List<Int>? = null,
        raceType: RaceType = RaceType.CLASSIC
    ): EventProjectFile {
        val controls = (1..foxCount).map { number ->
            val siCode = siCodes?.getOrNull(number - 1) ?: (30 + number)
            EventControl(
                id = "control-$number",
                raceId = RACE_ID,
                label = (30 + number).toString(),
                siCode = siCode,
                type = ControlPointType.CONTROL,
                publicLabel = publicLabels?.getOrNull(number - 1) ?: (30 + number).toString()
            )
        } + EventControl(
            id = "control-beacon",
            raceId = RACE_ID,
            label = "Beacon",
            siCode = 99,
            type = ControlPointType.BEACON,
            publicLabel = "Beacon"
        )
        val category = EventCategory(
            id = CATEGORY_ID,
            raceId = RACE_ID,
            name = "M21",
            isMan = true,
            maxAge = 21,
            lengthMeters = 0,
            climbMeters = 0,
            order = 1,
            differentProperties = false,
            raceType = null,
            raceBand = null,
            timeLimitSeconds = null,
            controlPointsString = ""
        )
        val controlPoints = controls.mapIndexed { index, control ->
            EventControlPoint(
                id = "cp-${control.id}",
                categoryId = CATEGORY_ID,
                siCode = control.siCode,
                type = control.type,
                order = index + 1,
                controlId = control.id
            )
        }
        return EventProjectFile(
            raceData = EventRaceData(
                race = EventRace(
                    id = RACE_ID,
                    name = "Test Event",
                    apiKey = "",
                    startDateTimeIso = "2026-06-06T09:00:00",
                    raceType = raceType,
                    raceLevel = RaceLevel.PRACTICE,
                    raceBand = RaceBand.M80,
                    timeLimitSeconds = 7_200
                ),
                categories = listOf(
                    EventCategoryData(
                        category = category,
                        controlPoints = controlPoints,
                        competitors = emptyList()
                    )
                ),
                aliases = emptyList(),
                competitorData = emptyList(),
                unmatchedReadoutData = emptyList(),
                controls = controls
            )
        )
    }

    private fun protectedInfo(foxCount: Int): ProtectedCourseInfo {
        val routeLongitudes = (0..(foxCount + 1)).map { it * 0.01 }
        val route = routeLongitudes.mapIndexed { index, longitude ->
            ProtectedCourseRoutePoint(
                latitude = 39.0,
                longitude = -95.0 + longitude,
                elevationMeters = 100.0 + index * 10.0
            )
        }
        val controls = (1..foxCount).map { number ->
            ProtectedCourseControlPoint(
                controlId = "control-$number",
                label = (30 + number).toString(),
                latitude = 39.0,
                longitude = -95.0 + number * 0.01,
                type = ControlPointType.CONTROL,
                elevationMeters = 100.0 + number * 10.0
            )
        } + ProtectedCourseControlPoint(
            controlId = "control-beacon",
            label = "Beacon",
            latitude = 39.0,
            longitude = -95.0 + (foxCount + 1) * 0.01,
            type = ControlPointType.BEACON,
            elevationMeters = 100.0 + (foxCount + 1) * 10.0
        )
        val courseObjects = listOf(
            ProtectedCourseObjectPoint(
                id = "start",
                label = "Start",
                type = ProtectedCourseObjectType.START,
                latitude = route.first().latitude,
                longitude = route.first().longitude,
                elevationMeters = route.first().elevationMeters
            )
        ) + controls.map { control ->
            ProtectedCourseObjectPoint(
                id = control.controlId,
                label = control.label,
                type = if (control.type == ControlPointType.BEACON) {
                    ProtectedCourseObjectType.BEACON
                } else {
                    ProtectedCourseObjectType.CONTROL
                },
                latitude = control.latitude,
                longitude = control.longitude,
                elevationMeters = control.elevationMeters
            )
        } + ProtectedCourseObjectPoint(
            id = "finish",
            label = "Finish",
            type = ProtectedCourseObjectType.FINISH,
            latitude = route.last().latitude,
            longitude = route.last().longitude,
            elevationMeters = route.last().elevationMeters
        )
        return ProtectedCourseInfo(
            idealOrder = (1..foxCount).joinToString(" ") { (30 + it).toString() } + " Beacon",
            lengthMeters = 4_000,
            climbMeters = 100,
            sourceName = "test.kml",
            sampledPointCount = route.size,
            route = route,
            controlPoints = controls,
            courseObjects = courseObjects
        )
    }

    private fun assertPdfInfoCanRead(path: Path) {
        val process = try {
            ProcessBuilder("pdfinfo", path.toString())
                .redirectErrorStream(true)
                .start()
        } catch (_: Exception) {
            return
        }
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        assertEquals(output, 0, exitCode)
        assertTrue(output, output.contains("Pages:"))
    }

    private companion object {
        const val RACE_ID = "race"
        const val CATEGORY_ID = "category-m21"
    }
}
