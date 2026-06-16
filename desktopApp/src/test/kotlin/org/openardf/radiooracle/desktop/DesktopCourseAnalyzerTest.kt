package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.PunchStatus
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.shared.event.EventAlias
import org.openardf.radiooracle.shared.event.EventAliasPunch
import org.openardf.radiooracle.shared.event.EventCategory
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventControl
import org.openardf.radiooracle.shared.event.EventControlPoint
import org.openardf.radiooracle.shared.event.EventPunch
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventRace
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.EventReadoutData
import org.openardf.radiooracle.shared.event.EventResult
import org.openardf.radiooracle.shared.event.ProtectedCourseControlPoint
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.event.ProtectedCourseObjectPoint
import org.openardf.radiooracle.shared.event.ProtectedCourseObjectType
import org.openardf.radiooracle.shared.event.ProtectedCourseRoutePoint
import org.openardf.radiooracle.shared.event.ProtectedIdealOrderRules
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

        assertEquals(true, summary.hasMissingCalculatedRouteElevationData)
        assertTrue(summary.missingElements.any { it.contains("Calculated route elevation samples are missing from the local elevation cache") })
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
            protectedIdealOrderText = "31 32 33 Beacon",
            elevationCacheNotes = { points ->
                assertTrue(points.isNotEmpty())
                listOf("Elevation cache: Test Venue - USGS 3DEP, 3 m grid (test.roelev.json)")
            }
        )

        assertEquals(6, summary.calculatedRouteCount)
        assertEquals(true, summary.idealOrderMatches)
        assertEquals(4_000, summary.routeLengthMeters)
        assertEquals(100, summary.climbMeters)
        assertEquals(5_000, summary.effectiveLengthMeters)
        assertEquals(null, summary.calculatedRouteApplication)
        assertNotNull(summary.providedRouteSection)
        assertNotNull(summary.calculatedRouteSection)
        assertEquals("Section 1: Imported route analysis", summary.providedRouteSection?.title)
        assertEquals("Section 2: Calculated ideal route", summary.calculatedRouteSection?.title)
        assertEquals(true, summary.calculatedRouteSection?.summaryOnly)
        assertEquals(
            listOf("Calculated ideal route matches imported route"),
            summary.calculatedRouteSection?.routeOrder
        )
        assertTrue(summary.calculatedRouteSection?.explanation.orEmpty().contains("matches the imported route"))
        assertTrue(summary.providedRouteSection?.explanation.orEmpty().contains("effective length"))
        assertTrue(summary.providedRouteSection?.explanation.orEmpty().contains("does not guarantee 3 m source terrain data"))
        assertTrue(summary.providedRouteSection?.explanation.orEmpty().contains("does not currently know map passability"))
        assertTrue(summary.providedRouteSection?.explanation.orEmpty().contains("category age/gender multiplier"))
        assertEquals("M21", summary.speedModel.categoryModelLabel)
        assertEquals(1.0, summary.speedModel.categorySpeedMultiplier, 0.001)
        assertEquals(1.0, summary.speedModel.compensationFactor, 0.001)
        assertEquals(summary.speedModel, summary.providedRouteSection?.speedModel)
        assertEquals(null, summary.calculatedRouteSection?.speedModel)
        assertTrue(summary.categorySpeedFactors.any { it.categoryCodes == listOf("W75") && it.multiplier == 0.47 })
        assertTrue(summary.categorySpeedFactors.any { it.categoryCodes == listOf("M19", "M40") && it.multiplier == 0.95 })
        assertTrue(summary.summaryExplanation.contains("event speed factor 1.00"))
        assertTrue(summary.summaryExplanation.contains("below 1.00 slows all category estimates"))
        assertEquals(1, summary.profileComparison.size)
        assertEquals(listOf("31", "32", "33"), summary.profileComparison.first { it.title == "Imported route" }.markers.map { it.label })
        assertEquals(1, summary.routeMaps.size)
        assertEquals(listOf("Imported foxes and route"), summary.kmlFolders.map { it.title })
        assertEquals(
            listOf("Elevation cache: Test Venue - USGS 3DEP, 3 m grid (test.roelev.json)"),
            summary.elevationCacheNotes
        )
        assertEquals(false, summary.hasMissingElevationData)
        assertNotNull(summary.estimatedIdealSeconds)
        assertEquals(5, summary.elevationProfile.size)
        assertEquals(0, summary.elevationProfile.first().distanceMeters)
        assertEquals(100.0, summary.elevationProfile.first().elevationMeters, 0.001)
        assertEquals(listOf("S -> 31", "31 -> 32", "32 -> 33", "33 -> B", "B -> F"), summary.providedLegRows.map { "${it.fromLabel} -> ${it.toLabel}" })
        assertEquals(emptyList<DesktopCourseLegRow>(), summary.calculatedLegRows)
        assertTrue(summary.providedLegRows.all { it.lengthMeters != null && it.splitSeconds != null && it.cumulativeSeconds != null })
        assertEquals(summary.estimatedIdealSeconds, summary.providedLegRows.last().cumulativeSeconds)
        assertEquals(listOf("31", "32", "33"), summary.waitRows.map { it.controlLabel })
        summary.providedLegRows.take(3).zip(summary.waitRows).forEach { (leg, wait) ->
            assertEquals(wait.arrivalSeconds + wait.waitSeconds + 30, leg.cumulativeSeconds)
            assertEquals(wait.waitSeconds, leg.waitSeconds)
            assertEquals(30, leg.findPunchSeconds)
        }
        assertEquals(emptyList<DesktopCourseWaitRow>(), requireNotNull(summary.calculatedRouteSection).waitRows)
        assertEquals(listOf(null, null), summary.providedLegRows.takeLast(2).map { it.waitSeconds })
        assertEquals(listOf(null, null), summary.providedLegRows.takeLast(2).map { it.findPunchSeconds })
        assertTrue(summary.metrics.any {
            it.label == "Effective length" &&
                it.value == "5.00 km (required 9-12 km)" &&
                it.status == DesktopCourseMetricStatus.Warning
        })
        assertTrue(
            "Metrics were ${summary.metrics}",
            summary.metrics.any {
                it.label == "Climb percent of route length" &&
                    it.value.contains("(limit 6.0%)") &&
                it.status == DesktopCourseMetricStatus.Good
            }
        )
        assertTrue(summary.goodnessMetrics.sharedMetrics.any { it.label == "Calculated route agrees with imported route order" })
        assertFalse(summary.goodnessMetrics.sharedMetrics.any { it.label.contains("shortest possible route") })
        val importedGoodnessMetrics = summary.goodnessMetrics.groups.single { it.title == "Imported" }.metrics
        val calculatedGoodnessMetrics = summary.goodnessMetrics.groups.single { it.title == "Calculated" }.metrics
        assertTrue(importedGoodnessMetrics.any {
            it.label == "Imported route is shortest possible route" &&
                it.status == DesktopCourseMetricStatus.Good
        })
        assertTrue(calculatedGoodnessMetrics.any {
            it.label == "Calculated route is shortest possible route" &&
                it.status == DesktopCourseMetricStatus.Good
        })
        assertFalse(calculatedGoodnessMetrics.any { it.label == "Calculated route agrees with imported route order" })
        assertTrue(importedGoodnessMetrics.any { it.label == "Climb percent of route length" })
        assertTrue(importedGoodnessMetrics.any { it.label == "Effective length" })
        assertTrue(importedGoodnessMetrics.any { it.label == "Total ideal-route wait time" })
        assertEquals(
            importedGoodnessMetrics.map(::routeMetricPairingLabel),
            calculatedGoodnessMetrics.map(::routeMetricPairingLabel)
        )
        val reportText = DesktopCourseAnalysisExports.reportText(summary)
        assertEquals("Apply Fox Renumbering Only", summary.courseRecommendation.actionLabel)
        assertTrue(reportText.contains("Imported\n"))
        assertTrue(reportText.contains("Calculated\n"))
        assertTrue(reportText.contains("Order comparison: Imported and calculated routes match"))
        assertFalse(reportText.contains("Calculated ideal route (calculated fox numbering):"))
        assertFalse(reportText.contains("Calculated straight-line length:"))
        assertTrue(reportText.contains("Effective length: 5.00 km (required 9-12 km)"))
        assertTrue(reportText.contains("Course Recommendation"))
        assertTrue(summary.courseRecommendation.paragraph.contains("Radio-Oracle recommends Apply Fox Renumbering Only"))
        assertTrue(summary.courseRecommendation.paragraph.contains("outside the 9-12 km rules range"))
        assertTrue(summary.courseRecommendation.paragraph.contains("honest representation of the course's overall difficulty"))
        assertTrue(summary.waitRows.any { it.waitSeconds > 30 })
        assertTrue(summary.courseRecommendation.paragraph.contains("current fox numbering exceed 30 seconds"))
        assertTrue(summary.courseRecommendation.paragraph.contains("suggested fox numbering"))
        assertTrue(reportText.contains("Radio-Oracle recommends"))
        assertTrue(reportText.contains("Assumed running speed equals"))
        assertTrue(reportText.contains("Speed model factors"))
        assertTrue(reportText.contains("Provisional built-in category assumptions"))
        assertTrue(reportText.contains("not a rules-derived or event-calibrated table"))
        assertTrue(reportText.contains("per-leg speed adjustment"))
        assertTrue(reportText.contains("event speed factor is adjustable"))
        assertTrue(reportText.contains("M19/M40: x0.95"))
        assertTrue(reportText.contains("W75: x0.47"))
        assertTrue(reportText.contains("Unmatched categories: x1.00"))
    }

    @Test
    fun estimatedTimeUsesCategorySpeedAndEventCompensationFactor() {
        val m21Summary = DesktopCourseAnalyzer.analyze(
            projectFile = projectFile(foxCount = 3, categoryName = "M21"),
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo(foxCount = 3),
            protectedIdealOrderText = "31 32 33 Beacon"
        )
        val w75Summary = DesktopCourseAnalyzer.analyze(
            projectFile = projectFile(foxCount = 3, categoryName = "W75"),
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo(foxCount = 3),
            protectedIdealOrderText = "31 32 33 Beacon"
        )
        val slowW75Project = projectFile(foxCount = 3, categoryName = "W75").let { project ->
            project.copy(
                raceData = project.raceData.copy(
                    race = project.raceData.race.copy(courseAnalyzerSpeedCompensationFactor = 0.80)
                )
            )
        }
        val slowW75Summary = DesktopCourseAnalyzer.analyze(
            projectFile = slowW75Project,
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo(foxCount = 3),
            protectedIdealOrderText = "31 32 33 Beacon"
        )

        assertTrue(requireNotNull(w75Summary.estimatedIdealSeconds) > requireNotNull(m21Summary.estimatedIdealSeconds))
        assertTrue(requireNotNull(slowW75Summary.estimatedIdealSeconds) > requireNotNull(w75Summary.estimatedIdealSeconds))
        assertEquals("W75", w75Summary.speedModel.categoryModelLabel)
        assertEquals(0.47, w75Summary.speedModel.categorySpeedMultiplier, 0.001)
        assertEquals(0.80, slowW75Summary.speedModel.compensationFactor, 0.001)
    }

    @Test
    fun calculatedRouteSamplesElevationCacheBetweenCoursePoints() {
        val projectFile = projectFile(foxCount = 3)
        val protectedInfo = protectedInfo(foxCount = 3)

        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = projectFile,
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo,
            protectedIdealOrderText = "33 32 31 Beacon",
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
    fun reportsCalculatedRouteMissingLocalElevationCacheSamples() {
        val projectFile = projectFile(foxCount = 3)
        val protectedInfo = protectedInfo(foxCount = 3)

        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = projectFile,
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo,
            protectedIdealOrderText = "33 32 31 Beacon"
        )

        assertEquals(false, summary.hasMissingElevationData)
        assertEquals(true, summary.hasMissingCalculatedRouteElevationData)
        assertTrue(summary.calculatedRouteMissingElevationPointCount > 0)
        assertNotNull(summary.calculatedRouteElevationBoundingBox)
        assertTrue(summary.missingElements.any { it.contains("Calculated route elevation samples are missing from the local elevation cache") })
        assertNotNull(summary.calculatedRouteSection)
    }

    @Test
    fun flagsUsaRulesViolationsInSectionChecksAndExportText() {
        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = projectFile(foxCount = 3),
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo(foxCount = 3),
            protectedIdealOrderText = "31 32 33 Beacon"
        )

        val sectionChecks = requireNotNull(summary.providedRouteSection).ruleChecks
        assertEquals("USA Rules for Radio Orienteering, Effective Date: 1 Jan 2026", summary.rulesDocumentLabel)
        assertTrue(sectionChecks.any { it.label == "Imported route fox count" && it.status == DesktopCourseMetricStatus.Warning })
        assertTrue(sectionChecks.any { it.label == "Imported route course length" && it.status == DesktopCourseMetricStatus.Warning })
        assertTrue(sectionChecks.any { it.label == "Classic start exclusion zone" && it.status == DesktopCourseMetricStatus.Good })
        assertTrue(sectionChecks.any { it.label == "Imported route fox count" && it.value == "3 foxes (required 5 for M21)" })
        assertTrue(sectionChecks.any { it.label == "Imported route course length" && it.value.contains("(required 9-12 km)") })
        assertTrue(sectionChecks.any { it.label == "Classic start exclusion zone" && it.value.contains("nearest transmitter") && it.value.contains("(required at least 750 m)") })
        assertTrue(sectionChecks.any { it.label == "Classic minimum transmitter spacing" && it.value.contains("closest pair") })

        val reportText = DesktopCourseAnalysisExports.reportText(summary)
        assertTrue(reportText.contains("Rules applied: USA Rules for Radio Orienteering, Effective Date: 1 Jan 2026"))
        assertEquals(1, Regex("Rules applied: USA Rules for Radio Orienteering, Effective Date: 1 Jan 2026").findAll(reportText).count())
        assertTrue(reportText.contains("Effective length: 5.00 km (required 9-12 km)"))
        assertTrue(reportText.contains("RULE VIOLATION: Imported route fox count"))
        assertTrue(reportText.contains("RULE VIOLATION: Imported route course length"))
    }

    @Test
    fun acceptsCloseUsaRulesCategoryNameMatchWithWarning() {
        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = projectFile(foxCount = 5, categoryName = "M-21"),
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo(foxCount = 5),
            protectedIdealOrderText = "31 32 33 34 35 Beacon"
        )

        val sectionChecks = requireNotNull(summary.providedRouteSection).ruleChecks
        assertTrue(sectionChecks.any {
                it.label == "Imported route USA category name" &&
                it.value.contains("Using M21 rules for category \"M-21\"") &&
                it.status == DesktopCourseMetricStatus.Warning
        })
        assertTrue(sectionChecks.any {
                it.label == "Imported route fox count" &&
                it.value == "5 foxes (required 5 for M21)" &&
                it.status == DesktopCourseMetricStatus.Good
        })
        assertTrue(summary.metrics.any {
            it.label == "Effective length" &&
                it.value.contains("(required 9-12 km)")
        })
    }

    @Test
    fun calculatesSprintRouteAsSeparateFirstAndFastLoops() {
        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = sprintProjectFile(),
            categoryId = CATEGORY_ID,
            protectedCourseInfo = sprintProtectedInfo(),
            protectedIdealOrderText = "2 1 Spectator F2 F1 Beacon"
        )

        val section = requireNotNull(summary.calculatedRouteSection)
        assertEquals(4, summary.calculatedRouteCount)
        assertEquals(listOf("S", "1", "2", "Spectator", "F1", "F2", "B"), section.routeOrder)
        assertTrue(section.explanation.contains("Sprint route calculated as separate first and fast loops"))
        assertTrue(section.ruleChecks.any { it.label == "Calculated route Sprint target time" })
        assertTrue(section.ruleChecks.any { it.label == "Sprint minimum transmitter spacing" && it.value.contains("closest pair F1-Spectator") })
    }

    @Test
    fun matchingSprintCalculatedRouteUsesStoredRouteTimingForTargetTimeChecks() {
        val protectedInfo = sprintProtectedInfo().copy(
            route = listOf(
                ProtectedCourseRoutePoint(39.0, -95.0, 100.0),
                ProtectedCourseRoutePoint(39.0, -94.99, 100.0),
                ProtectedCourseRoutePoint(39.015, -94.985, 100.0),
                ProtectedCourseRoutePoint(39.0, -94.98, 100.0),
                ProtectedCourseRoutePoint(39.015, -94.97025, 100.0),
                ProtectedCourseRoutePoint(39.0, -94.9605, 100.0),
                ProtectedCourseRoutePoint(39.015, -94.96025, 100.0),
                ProtectedCourseRoutePoint(39.0, -94.96, 100.0),
                ProtectedCourseRoutePoint(39.015, -94.955, 100.0),
                ProtectedCourseRoutePoint(39.0, -94.95, 100.0),
                ProtectedCourseRoutePoint(39.015, -94.945, 100.0),
                ProtectedCourseRoutePoint(39.0, -94.94, 100.0),
                ProtectedCourseRoutePoint(39.015, -94.935, 100.0),
                ProtectedCourseRoutePoint(39.0, -94.93, 100.0)
            )
        )

        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = sprintProjectFile(),
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo,
            protectedIdealOrderText = "1 2 Spectator F1 F2 Beacon"
        )

        assertEquals(true, summary.idealOrderMatches)
        assertEquals(emptyList<DesktopCourseLegRow>(), summary.calculatedLegRows)
        val storedTargetTime = requireNotNull(summary.providedRouteSection)
            .ruleChecks
            .single { it.label == "Imported route Sprint target time" }
            .value
        val calculatedTargetTime = requireNotNull(summary.calculatedRouteSection)
            .ruleChecks
            .single { it.label == "Calculated route Sprint target time" }
            .value
        assertEquals(storedTargetTime, calculatedTargetTime)
        assertFalse(storedTargetTime.startsWith("Unknown"))
    }

    @Test
    fun calculatesSprintRouteUsingBeaconTransitionWhenNoSpectatorIsAssigned() {
        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = sprintProjectFile(includeSpectator = false),
            categoryId = CATEGORY_ID,
            protectedCourseInfo = sprintProtectedInfo(includeSpectator = false),
            protectedIdealOrderText = "2 1 Beacon F2 F1 Beacon"
        )

        val section = requireNotNull(summary.calculatedRouteSection)
        assertEquals(4, summary.calculatedRouteCount)
        assertEquals(listOf("S", "1", "2", "B", "F2", "F1", "B"), section.routeOrder)
        assertTrue(section.explanation.contains("using the beacon as the slow-to-fast transition"))
    }

    @Test
    fun doesNotCalculateSprintRouteWithSpectatorButNoBeacon() {
        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = sprintProjectFile(includeBeacon = false),
            categoryId = CATEGORY_ID,
            protectedCourseInfo = sprintProtectedInfo(includeBeacon = false),
            protectedIdealOrderText = "2 1 Spectator F2 F1"
        )

        assertEquals(null, summary.calculatedRouteSection)
        assertEquals(0, summary.calculatedRouteCount)
        assertTrue(summary.missingElements.any { it.contains("a spectator cannot replace the beacon") })
    }

    @Test
    fun usesHybridFoxoringRouteSearchAboveSixFoxes() {
        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = projectFile(foxCount = 7, raceType = RaceType.FOXORING),
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo(foxCount = 7),
            protectedIdealOrderText = "37 36 35 34 33 32 31 Beacon"
        )

        assertTrue(summary.calculatedRouteCount > 1)
        assertTrue(requireNotNull(summary.calculatedRouteSection).explanation.contains("non-exhaustive hybrid search"))
        assertTrue(summary.calculatedRouteSection?.explanation.orEmpty().contains("rolling 5-control exhaustive-window"))
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
            assertFalse(
                "raceType=$raceType missing=${summary.missingElements}",
                summary.missingElements.any { it.contains("Transmit-slot wait analysis") }
            )
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
            protectedIdealOrderText = "33 32 31 Beacon"
        )

        assertTrue(
            "Metrics were ${summary.metrics}",
            summary.metrics.any {
                it.label == "Climb percent of route length" &&
                    it.value.contains("(limit 6.0%)") &&
                    it.status == DesktopCourseMetricStatus.Warning
            }
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
            protectedIdealOrderText = "33 32 31 Beacon",
            eventFileName = "analysis-event.rom.json",
            analysisPerformedAtText = "Mon, Jun 15, 2026 9:30 AM"
        )
        val reportText = DesktopCourseAnalysisExports.reportText(summary)

        assertTrue(reportText.contains("Course Analyzer"))
        assertTrue(reportText.contains("Event: Test Event"))
        assertTrue(reportText.contains("Event file: analysis-event.rom.json"))
        assertTrue(reportText.contains("Event format: Classic"))
        assertTrue(reportText.contains("Event type: Practice"))
        assertTrue(reportText.contains("Analyzed: Mon, Jun 15, 2026 9:30 AM"))
        assertTrue(reportText.indexOf("Analyzed:") < reportText.indexOf("Category:"))
        assertTrue(reportText.contains("Section 2: Calculated ideal route"))
        assertTrue(reportText.contains("Route order (imported fox numbering):"))
        assertTrue(reportText.contains("Route order (calculated fox numbering):"))
        assertTrue(reportText.contains("Imported\n"))
        assertTrue(reportText.contains("Calculated\n"))
        assertTrue(reportText.contains("Imported route:"))
        assertTrue(reportText.contains("Ideal route:"))
        assertTrue(reportText.contains("Course Recommendation"))
        assertTrue(reportText.contains("Radio-Oracle recommends"))
        assertTrue(reportText.contains("The imported route is"))
        assertTrue(reportText.contains("should therefore be used as the course's effective length for M21"))
        assertTrue(reportText.contains("with calculated fox numbering"))
        assertTrue(reportText.contains("2D route"))
        assertTrue(reportText.contains("depiction"))
        assertTrue(reportText.contains("Movement time:"))
        assertTrue(reportText.contains("(waits "))

        val pdfPath = Files.createTempFile("course-analysis", ".pdf")
        val exportPaths = DesktopCourseAnalysisExports.exportPdfAndKml(pdfPath, summary)
        val pdfBytes = Files.readAllBytes(pdfPath)
        assertTrue(String(pdfBytes.take(8).toByteArray()).startsWith("%PDF-1.4"))
        val pdfText = String(pdfBytes)
        assertTrue(pdfText.contains("Course Analyzer"))
        assertTrue(pdfText.contains("Event: Test Event"))
        assertTrue(pdfText.contains("Event file: analysis-event.rom.json"))
        assertTrue(pdfText.contains("Analyzed: Mon, Jun 15, 2026 9:30 AM"))
        assertTrue(pdfText.contains("/Helvetica-Bold"))
        assertTrue(pdfText.contains("Course Recommendation"))
        assertTrue(pdfText.contains("The imported route is"))
        assertTrue(pdfText.contains("should therefore be used as the course's effective length for M21"))
        assertTrue(pdfText.contains("with calculated fox numbering"))
        assertTrue(pdfText.contains("2D route"))
        assertTrue(pdfText.contains("depiction"))
        assertTrue(pdfText.contains("Elevation Profile Graphics"))
        assertTrue(pdfText.contains("2D Route Depiction Graphics"))
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
        assertTrue(kmlText.contains("<name>Imported foxes and route</name>"))
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
            protectedIdealOrderText = "33 32 31 Beacon"
        )

        val renumbering = requireNotNull(summary.waitRenumbering)
        val sectionRenumbering = requireNotNull(summary.providedRouteSection?.waitRenumbering)
        assertTrue(renumbering.improvesWait)
        assertTrue(renumbering.currentTotalWaitSeconds > renumbering.bestTotalWaitSeconds)
        assertEquals(renumbering.currentTotalWaitSeconds - renumbering.bestTotalWaitSeconds, sectionRenumbering.currentTotalWaitSeconds - sectionRenumbering.bestTotalWaitSeconds)
        assertEquals(listOf("31", "32", "33"), renumbering.assignments.map { it.controlLabel })
        assertEquals(listOf("31", "32", "33"), renumbering.assignments.map { it.currentSlotLabel })
        assertTrue(renumbering.assignments.map { it.suggestedSlotLabel } != renumbering.assignments.map { it.currentSlotLabel })
        assertEquals(renumbering.bestTotalWaitSeconds, renumbering.suggestedWaitRows.sumOf { it.waitSeconds })
        val suggestedSlotByControlLabel = renumbering.assignments.associate { it.controlLabel to it.suggestedSlotLabel }
        assertEquals(
            renumbering.suggestedWaitRows.map { suggestedSlotByControlLabel[it.controlLabel] },
            renumbering.suggestedWaitRows.map { it.slotLabel }
        )
        assertTrue(summary.summaryExplanation.contains("may reduce imported-route wait time"))
        assertTrue(summary.summaryExplanation.contains("see Section 1 for the assignment details"))
        assertEquals("Apply Calculated Route", summary.courseRecommendation.actionLabel)
        assertTrue(summary.courseRecommendation.paragraph.contains("Radio-Oracle recommends Apply Calculated Route"))
        assertTrue(DesktopCourseAnalysisExports.reportText(summary).contains("Renumbered wait times"))
        val metricLabels = summary.metrics.map { it.label }
        assertEquals(
            metricLabels.indexOf("Total ideal-route wait time") + 1,
            metricLabels.indexOf("Total ideal-route wait time with renumbering")
        )
        assertEquals(
            metricLabels.indexOf("Challenge vs target winning time") + 1,
            metricLabels.indexOf("Imported route finish time with renumbering")
        )
        assertTrue(summary.metrics.first { it.label == "Total ideal-route wait time with renumbering" }.value.contains(":"))
        assertTrue(summary.metrics.first { it.label == "Imported route finish time with renumbering" }.value.contains(" / "))
    }

    @Test
    fun recommendsShorterCalculatedRouteEvenWhenStoredRouteBetterFitsCategoryLength() {
        val baseInfo = protectedInfo(foxCount = 3)
        val protectedInfo = baseInfo.copy(
            lengthMeters = 9_000,
            climbMeters = 0,
            route = baseInfo.route.map { it.copy(elevationMeters = 100.0) },
            controlPoints = baseInfo.controlPoints.map { it.copy(elevationMeters = 100.0) },
            courseObjects = baseInfo.courseObjects.map { it.copy(elevationMeters = 100.0) }
        )

        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = projectFile(foxCount = 3),
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo,
            protectedIdealOrderText = "33 32 31 Beacon"
        )

        val storedLength = requireNotNull(summary.providedRouteSection?.comparisonLengthMeters)
        val calculatedLength = requireNotNull(summary.calculatedRouteSection?.comparisonLengthMeters)
        assertTrue(calculatedLength < storedLength)
        assertTrue(
            summary.metrics.any {
                it.label == "Imported route course length" &&
                    it.status == DesktopCourseMetricStatus.Good
            }
        )
        assertTrue(
            summary.metrics.any {
                it.label == "Calculated route course length" &&
                    it.status == DesktopCourseMetricStatus.Warning
            }
        )
        val importedGoodnessMetrics = summary.goodnessMetrics.groups.single { it.title == "Imported" }.metrics
        val calculatedGoodnessMetrics = summary.goodnessMetrics.groups.single { it.title == "Calculated" }.metrics
        assertTrue(importedGoodnessMetrics.any {
            it.label == "Imported route is shortest possible route" &&
                it.status == DesktopCourseMetricStatus.Warning &&
                it.value.contains("No:")
        })
        assertTrue(calculatedGoodnessMetrics.any {
            it.label == "Calculated route is shortest possible route" &&
                it.status == DesktopCourseMetricStatus.Good &&
                it.value.contains("Yes:")
        })
        assertEquals(
            importedGoodnessMetrics.map(::routeMetricPairingLabel),
            calculatedGoodnessMetrics.map(::routeMetricPairingLabel)
        )
        assertEquals("Apply Calculated Route", summary.courseRecommendation.actionLabel)
        assertTrue(summary.courseRecommendation.paragraph.contains("The imported route is"))
        assertTrue(summary.courseRecommendation.paragraph.contains("longer than the ideal route"))
        assertTrue(summary.courseRecommendation.paragraph.contains("should therefore be used as the course's effective length for M21"))
        assertTrue(summary.courseRecommendation.paragraph.contains("ideal route order is"))
        assertTrue(summary.courseRecommendation.paragraph.contains("with calculated fox numbering as shown in the 2D route depiction graphic below"))
        assertTrue(summary.courseRecommendation.paragraph.contains("outside the 9-12 km rules range"))
        assertTrue(summary.courseRecommendation.paragraph.contains("honest representation of the course's overall difficulty"))
        assertTrue(summary.courseRecommendation.paragraph.contains("redesign the course by moving the start, finish, or foxes"))
        assertTrue(requireNotNull(summary.calculatedRouteSection).waitRows.any { it.waitSeconds > 30 })
        assertTrue(summary.courseRecommendation.paragraph.contains("calculated fox numbering"))
        assertTrue(summary.courseRecommendation.paragraph.contains("reduce wait time at the affected foxes"))
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
            protectedIdealOrderText = "33 32 31 Beacon"
        )

        val section = requireNotNull(summary.calculatedRouteSection)
        val renumbering = requireNotNull(section.waitRenumbering)
        val calculatedOrder = listOf("S") + renumbering.assignments.map { it.suggestedSlotLabel } + "B"
        assertEquals("Route order (imported fox numbering)", section.routeOrderLabel)
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
            protectedIdealOrderText = "'Fox 1' 'Fox 2' 'Fox 3' Beacon"
        )
        val application = requireNotNull(summary.calculatedRouteApplication)
        val projectWithResults = projectFile.withAliasesAndUnmatchedControlReadout()
        val originalPublicLabelsByControlId = projectWithResults.raceData.controls.associate { it.id to it.publicLabel }
        val originalAliasesBySiCode = projectWithResults.raceData.aliases.associate { it.siCode to it.name }
        val originalPunch = projectWithResults.raceData.unmatchedReadoutData.single().punches.single()

        val (updatedProject, updatedCourseInfo) = DesktopCourseAnalysisApplier.applyCalculatedRoute(
            projectFile = projectWithResults,
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
        assertEquals(originalPublicLabelsByControlId, publicLabelsByControlId)
        assertEquals(originalAliasesBySiCode, updatedProject.raceData.aliases.associate { it.siCode to it.name })
        val updatedPunch = updatedProject.raceData.unmatchedReadoutData.single().punches.single()
        assertEquals(originalPunch.punch.siCode, updatedPunch.punch.siCode)
        assertEquals(originalPunch.alias?.name, updatedPunch.alias?.name)

        val updatedSummary = DesktopCourseAnalyzer.analyze(
            projectFile = updatedProject,
            categoryId = CATEGORY_ID,
            protectedCourseInfo = decryptedCourseInfo,
            protectedIdealOrderText = DesktopProtectedCourseOrder.decrypt(requireNotNull(updatedCategory.encryptedIdealOrder), "test-password")
        )
        assertTrue(updatedSummary.missingElements.none { it.contains("Imported route order") })
    }

    @Test
    fun appliesFoxRenumberingOnlyAcrossProtectedCategoriesAndIdealOrders() {
        val password = "test-password"
        val projectFile = projectFile(
            foxCount = 3,
            publicLabels = listOf("33", "32", "31")
        ).withAliasesAndUnmatchedControlReadout()
        val protectedInfo = protectedInfo(foxCount = 3)
        val storedIdealOrderText = "33 32 31 Beacon"
        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = projectFile,
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo,
            protectedIdealOrderText = storedIdealOrderText
        )
        val renumbering = requireNotNull(summary.waitRenumbering)
        val encryptedCourseInfo = DesktopProtectedCourseOrder.encryptCourseInfo(protectedInfo, password)
        val encryptedIdealOrder = DesktopProtectedCourseOrder.encrypt(storedIdealOrderText, password)
        val primaryCategoryData = projectFile.raceData.categories.single().let { categoryData ->
            categoryData.copy(
                category = categoryData.category.copy(
                    encryptedIdealOrder = encryptedIdealOrder,
                    encryptedCourseInfo = encryptedCourseInfo
                )
            )
        }
        val secondCategoryId = "category-m40"
        val secondCategoryData = primaryCategoryData.copy(
            category = primaryCategoryData.category.copy(
                id = secondCategoryId,
                name = "M40"
            ),
            controlPoints = primaryCategoryData.controlPoints.map { controlPoint ->
                controlPoint.copy(
                    id = "${controlPoint.id}-m40",
                    categoryId = secondCategoryId
                )
            }
        )
        val encryptedProject = projectFile.copy(
            raceData = projectFile.raceData.copy(
                categories = listOf(primaryCategoryData, secondCategoryData)
            )
        )

        val result = DesktopCourseAnalysisApplier.applyFoxRenumberingOnly(
            projectFile = encryptedProject,
            renumbering = renumbering,
            password = password
        )

        val changedLabelsByControlId = renumbering.assignments
            .filter { it.suggestedSlotLabel != it.currentSlotLabel }
            .associate { it.controlId to it.suggestedSlotLabel }
        val originalPublicLabelsByControlId = encryptedProject.raceData.controls.associate { it.id to it.publicLabel }
        val originalAliasesBySiCode = encryptedProject.raceData.aliases.associate { it.siCode to it.name }
        val originalPunch = encryptedProject.raceData.unmatchedReadoutData.single().punches.single()
        assertEquals(changedLabelsByControlId.size, result.changedControlCount)
        assertEquals(2, result.affectedCategoryCount)
        val updatedPublicLabelsByControlId = result.projectFile.raceData.controls.associate { it.id to it.publicLabel }
        assertEquals(originalPublicLabelsByControlId, updatedPublicLabelsByControlId)
        assertEquals(originalAliasesBySiCode, result.projectFile.raceData.aliases.associate { it.siCode to it.name })
        val updatedPunch = result.projectFile.raceData.unmatchedReadoutData.single().punches.single()
        assertEquals(originalPunch.punch.siCode, updatedPunch.punch.siCode)
        assertEquals(originalPunch.alias?.name, updatedPunch.alias?.name)

        val originalResolvedControlIds = ProtectedIdealOrderRules.resolveControlIds(
            storedIdealOrderText,
            projectFile.raceData.controls
        )
        result.projectFile.raceData.categories.forEach { categoryData ->
            val decryptedIdealOrder = DesktopProtectedCourseOrder.decrypt(
                requireNotNull(categoryData.category.encryptedIdealOrder),
                password
            )
            assertEquals(
                originalResolvedControlIds,
                ProtectedIdealOrderRules.resolveControlIds(decryptedIdealOrder, result.projectFile.raceData.controls)
            )
            val decryptedCourseInfo = DesktopProtectedCourseOrder.decryptCourseInfo(
                requireNotNull(categoryData.category.encryptedCourseInfo),
                password
            )
            changedLabelsByControlId.forEach { (controlId, expectedLabel) ->
                assertEquals(expectedLabel, decryptedCourseInfo.controlPoints.single { it.controlId == controlId }.label)
                assertEquals(expectedLabel, decryptedCourseInfo.courseObjects.single { it.id == controlId }.label)
            }
        }
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

        assertTrue(summary.missingElements.any { it.contains("Route data is locked") })
        assertTrue(summary.missingElements.any { it.contains("Route geometry") })
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
    fun reportsMissingControlLocationElevations() {
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

        assertTrue(summary.missingElements.any { it.contains("Control location elevations") })
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

    @Test
    fun resolvesImportedProtectedIdealOrderWhenCategoryHasNoAssignedControls() {
        val projectFile = projectFile(
            foxCount = 3,
            publicLabels = listOf("Fox 1", "Fox 2", "Fox 3"),
            siCodes = listOf(31, 32, 33),
            assignControls = false
        )
        val baseProtectedInfo = protectedInfo(foxCount = 3)
        val protectedInfo = baseProtectedInfo.copy(
            idealOrder = "Fox1 Fox2 Beacon",
            controlPoints = baseProtectedInfo.controlPoints.map { control ->
                when (control.controlId) {
                    "control-1" -> control.copy(label = "Fox1")
                    "control-2" -> control.copy(label = "Fox2")
                    "control-3" -> control.copy(label = "Fox3")
                    else -> control
                }
            },
            courseObjects = baseProtectedInfo.courseObjects.map { courseObject ->
                when (courseObject.id) {
                    "control-1" -> courseObject.copy(label = "Fox1")
                    "control-2" -> courseObject.copy(label = "Fox2")
                    "control-3" -> courseObject.copy(label = "Fox3")
                    else -> courseObject
                }
            }
        )

        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = projectFile,
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo,
            protectedIdealOrderText = protectedInfo.idealOrder
        )

        assertTrue(summary.missingElements.none { it.contains("Imported route order could not be resolved") })
        assertEquals(listOf("S", "Fox 1", "Fox 2", "B"), summary.providedIdealOrder)
        assertTrue(summary.calculatedIdealOrder.none { it == "Fox 3" })
        assertEquals(listOf("Fox 1", "Fox 2"), summary.waitRows.map { it.controlLabel })
    }

    @Test
    fun calculatedRouteUsesControlsFromStoredRouteInsteadOfBroaderCategoryAssignments() {
        val baseProtectedInfo = protectedInfo(foxCount = 5)
        val routeControlIds = setOf("control-1", "control-3", "control-5", "control-beacon")
        val protectedInfo = baseProtectedInfo.copy(
            idealOrder = "31 33 35 Beacon",
            controlPoints = baseProtectedInfo.controlPoints.filter { it.controlId in routeControlIds }
        )

        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = projectFile(foxCount = 5, categoryName = "M50"),
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo,
            protectedIdealOrderText = protectedInfo.idealOrder
        )

        val routeMap = requireNotNull(summary.routeMaps.single())
        assertEquals(6, summary.calculatedRouteCount)
        assertEquals(listOf("S", "31", "33", "35", "B"), summary.providedIdealOrder)
        assertTrue(summary.calculatedIdealOrder.containsAll(listOf("31", "33", "35", "B")))
        assertFalse(summary.calculatedIdealOrder.any { it == "32" || it == "34" })
        assertTrue(routeMap.points.map { it.label }.containsAll(listOf("31", "32", "33", "34", "35", "B")))
        assertEquals(listOf("S", "31", "33", "35", "B", "F"), routeMap.routeLabels)
    }

    private fun EventProjectFile.withAliasesAndUnmatchedControlReadout(): EventProjectFile {
        val controlAliases = raceData.controls
            .filter { it.type == ControlPointType.CONTROL }
            .map { control ->
                EventAlias(
                    id = "alias-${control.id}",
                    raceId = control.raceId,
                    siCode = control.siCode,
                    name = control.publicLabel ?: control.label
                )
            }
        val firstControl = raceData.controls.first { it.type == ControlPointType.CONTROL }
        val firstAlias = controlAliases.single { it.siCode == firstControl.siCode }
        return copy(
            raceData = raceData.copy(
                aliases = controlAliases,
                unmatchedReadoutData = listOf(
                    EventReadoutData(
                        result = EventResult(
                            id = "result-unmatched",
                            raceId = RACE_ID,
                            competitorId = null,
                            siNumber = 123456,
                            cardType = 0,
                            checkTimeSeconds = null,
                            startTimeSeconds = 0,
                            finishTimeSeconds = 1_200,
                            readoutDateTimeIso = "2026-06-06T09:20:00",
                            automaticStatus = true,
                            resultStatus = ResultStatus.OK,
                            points = 1,
                            runTimeSeconds = 1_200,
                            modified = false,
                            sent = false
                        ),
                        punches = listOf(
                            EventAliasPunch(
                                punch = EventPunch(
                                    id = "punch-unmatched-control",
                                    raceId = RACE_ID,
                                    resultId = "result-unmatched",
                                    cardNumber = 123456,
                                    siCode = firstControl.siCode,
                                    siTimeSeconds = 600,
                                    originalSiTimeSeconds = 600,
                                    punchType = SIRecordType.CONTROL,
                                    order = 1,
                                    punchStatus = PunchStatus.VALID,
                                    splitSeconds = 600
                                ),
                                alias = firstAlias
                            )
                        )
                    )
                )
            )
        )
    }

    private fun projectFile(
        foxCount: Int,
        publicLabels: List<String>? = null,
        siCodes: List<Int>? = null,
        raceType: RaceType = RaceType.CLASSIC,
        categoryName: String = "M21",
        assignControls: Boolean = true
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
            name = categoryName,
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
        val controlPoints = if (assignControls) {
            controls.mapIndexed { index, control ->
                EventControlPoint(
                    id = "cp-${control.id}",
                    categoryId = CATEGORY_ID,
                    siCode = control.siCode,
                    type = control.type,
                    order = index + 1,
                    controlId = control.id
                )
            }
        } else {
            emptyList()
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

    private fun sprintProjectFile(includeSpectator: Boolean = true, includeBeacon: Boolean = true): EventProjectFile {
        val controls = listOfNotNull(
            EventControl("control-slow-1", RACE_ID, "1", 31, ControlPointType.CONTROL, publicLabel = "1"),
            EventControl("control-slow-2", RACE_ID, "2", 32, ControlPointType.CONTROL, publicLabel = "2"),
            EventControl("control-fast-1", RACE_ID, "F1", 41, ControlPointType.CONTROL, publicLabel = "F1"),
            EventControl("control-fast-2", RACE_ID, "F2", 42, ControlPointType.CONTROL, publicLabel = "F2"),
            EventControl("control-spectator", RACE_ID, "Spectator", 46, ControlPointType.SEPARATOR, publicLabel = "Spectator").takeIf { includeSpectator },
            EventControl("control-beacon", RACE_ID, "Beacon", 99, ControlPointType.BEACON, publicLabel = "Beacon").takeIf { includeBeacon }
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
        return EventProjectFile(
            raceData = EventRaceData(
                race = EventRace(
                    id = RACE_ID,
                    name = "Sprint Test Event",
                    apiKey = "",
                    startDateTimeIso = "2026-06-06T09:00:00",
                    raceType = RaceType.SPRINT,
                    raceLevel = RaceLevel.PRACTICE,
                    raceBand = RaceBand.M80,
                    timeLimitSeconds = 3_600
                ),
                categories = listOf(
                    EventCategoryData(
                        category = category,
                        controlPoints = controls.mapIndexed { index, control ->
                            EventControlPoint(
                                id = "cp-${control.id}",
                                categoryId = CATEGORY_ID,
                                siCode = control.siCode,
                                type = control.type,
                                order = index + 1,
                                controlId = control.id
                            )
                        },
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

    private fun sprintProtectedInfo(includeSpectator: Boolean = true, includeBeacon: Boolean = true): ProtectedCourseInfo {
        val route = (0..7).map { index ->
            ProtectedCourseRoutePoint(
                latitude = 39.0,
                longitude = -95.0 + index * 0.01,
                elevationMeters = 100.0
            )
        }
        val controls = listOfNotNull(
            ProtectedCourseControlPoint("control-slow-1", "1", 39.0, -94.99, ControlPointType.CONTROL, 100.0),
            ProtectedCourseControlPoint("control-slow-2", "2", 39.0, -94.98, ControlPointType.CONTROL, 100.0),
            ProtectedCourseControlPoint("control-spectator", "Spectator", 39.0, -94.9605, ControlPointType.SEPARATOR, 100.0).takeIf { includeSpectator },
            ProtectedCourseControlPoint("control-fast-1", "F1", 39.0, -94.96, ControlPointType.CONTROL, 100.0),
            ProtectedCourseControlPoint("control-fast-2", "F2", 39.0, -94.95, ControlPointType.CONTROL, 100.0),
            ProtectedCourseControlPoint("control-beacon", "Beacon", 39.0, -94.94, ControlPointType.BEACON, 100.0).takeIf { includeBeacon }
        )
        return ProtectedCourseInfo(
            idealOrder = when {
                includeSpectator && includeBeacon -> "1 2 Spectator F1 F2 Beacon"
                includeSpectator -> "1 2 Spectator F1 F2"
                else -> "1 2 Beacon F1 F2 Beacon"
            },
            lengthMeters = 7_000,
            climbMeters = 0,
            sourceName = "sprint-test.kml",
            sampledPointCount = route.size,
            route = route,
            controlPoints = controls,
            courseObjects = listOf(
                ProtectedCourseObjectPoint("start", "Start", ProtectedCourseObjectType.START, 39.0, -95.0, 100.0)
            ) + controls.map { control ->
                ProtectedCourseObjectPoint(
                    id = control.controlId,
                    label = control.label,
                    type = when (control.type) {
                        ControlPointType.CONTROL -> ProtectedCourseObjectType.CONTROL
                        ControlPointType.BEACON -> ProtectedCourseObjectType.BEACON
                        ControlPointType.SEPARATOR -> ProtectedCourseObjectType.SPECTATOR
                    },
                    latitude = control.latitude,
                    longitude = control.longitude,
                    elevationMeters = control.elevationMeters
                )
            } + ProtectedCourseObjectPoint("finish", "Finish", ProtectedCourseObjectType.FINISH, 39.0, -94.93, 100.0)
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

    private fun routeMetricPairingLabel(metric: DesktopCourseGoodnessMetric): String =
        metric.label
            .replace("Imported route is", "Route is")
            .replace("Calculated route is", "Route is")
            .replace("Imported route ", "Route ")
            .replace("Calculated route ", "Route ")

    private companion object {
        const val RACE_ID = "race"
        const val CATEGORY_ID = "category-m21"
    }
}
