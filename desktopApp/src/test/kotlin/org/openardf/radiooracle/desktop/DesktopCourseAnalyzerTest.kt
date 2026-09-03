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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class DesktopCourseAnalyzerTest {
    @Test
    fun analysisIsUnavailableWhenRouteGeometryCannotProduceRouteSections() {
        val routeOnlyInfo = protectedInfo(foxCount = 3).copy(
            idealOrder = "31 32 33 Beacon",
            controlPoints = emptyList(),
            courseObjects = emptyList()
        )

        val reason = DesktopCourseAnalyzer.analysisUnavailableReason(
            projectFile = projectFile(foxCount = 3, assignControls = false),
            categoryId = CATEGORY_ID,
            protectedCourseInfo = routeOnlyInfo,
            protectedIdealOrderText = null
        )

        assertEquals(
            "The selected category has route geometry, but no usable control order or located controls. Import route data with control assignments/locations before running analysis.",
            reason
        )
    }

    @Test
    fun analysisIsAvailableWhenImportedRouteSectionCanBeBuilt() {
        val protectedInfo = protectedInfo(foxCount = 3)

        val reason = DesktopCourseAnalyzer.analysisUnavailableReason(
            projectFile = projectFile(foxCount = 3),
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo,
            protectedIdealOrderText = "31 32 33 Beacon"
        )

        assertNull(reason)
    }

    @Test
    fun analysisIsAvailableForInactiveImportedCourseMapping() {
        val protectedInfo = protectedInfo(foxCount = 3)
        val projectFile = inactiveCourseMappingProjectFile(foxCount = 3)

        val reason = DesktopCourseAnalyzer.analysisUnavailableReason(
            projectFile = projectFile,
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo,
            protectedIdealOrderText = null
        )
        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = projectFile,
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo,
            protectedIdealOrderText = null
        )

        assertNull(reason)
        assertEquals("M21", summary.categoryName)
        assertEquals(listOf("S", "31", "32", "33", "B", "F"), summary.providedIdealOrder)
        assertNotNull(summary.providedRouteSection)
    }

    @Test
    fun analysisIsAvailableWhenOnlyCalculatedRouteSectionCanBeBuilt() {
        val protectedInfo = protectedInfo(foxCount = 3).copy(idealOrder = "")

        val reason = DesktopCourseAnalyzer.analysisUnavailableReason(
            projectFile = projectFile(foxCount = 3),
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo,
            protectedIdealOrderText = null
        )

        assertNull(reason)
    }

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
        assertEquals(listOf("S", "31", "32", "33", "34", "35", "B", "F"), requireNotNull(summary.calculatedRouteSection).routeOrder)
        assertEquals(summary.calculatedRouteSection?.secondaryRouteOrder, summary.calculatedIdealOrder)
        assertEquals(listOf("S", "35", "34", "33", "32", "31", "B", "F"), summary.providedIdealOrder)
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
        assertEquals(summary.providedRouteSection?.routeLengthMeters, summary.routeLengthMeters)
        assertEquals(summary.providedRouteSection?.climbMeters, summary.climbMeters)
        assertEquals(summary.providedRouteSection?.effectiveLengthMeters, summary.effectiveLengthMeters)
        assertEquals(null, summary.calculatedRouteApplication)
        assertNotNull(summary.providedRouteSection)
        assertNotNull(summary.calculatedRouteSection)
        assertEquals("Section 1: Saved route analysis", summary.providedRouteSection?.title)
        assertEquals("Section 2: Calculated ideal route", summary.calculatedRouteSection?.title)
        assertEquals(true, summary.calculatedRouteSection?.summaryOnly)
        assertEquals(
            listOf("Calculated ideal route matches saved route"),
            summary.calculatedRouteSection?.routeOrder
        )
        assertTrue(summary.calculatedRouteSection?.explanation.orEmpty().contains("matches the saved route"))
        assertTrue(summary.providedRouteSection?.explanation.orEmpty().contains("effective length"))
        assertTrue(summary.providedRouteSection?.explanation.orEmpty().contains("does not guarantee 3 m source terrain data"))
        assertTrue(summary.providedRouteSection?.explanation.orEmpty().contains("does not currently know map passability"))
        assertTrue(summary.providedRouteSection?.explanation.orEmpty().contains("category age/gender multiplier"))
        assertFalse(
            "Collapsed calculated sections still have a saved-route length available for export names.",
            DesktopCourseAnalysisExports.defaultPdfFileName(summary).contains("unknown length")
        )
        assertEquals("M21", summary.speedModel.categoryModelLabel)
        assertEquals(1.0, summary.speedModel.categorySpeedMultiplier, 0.001)
        assertEquals(1.0, summary.speedModel.compensationFactor, 0.001)
        assertEquals(summary.speedModel, summary.providedRouteSection?.speedModel)
        assertEquals(null, summary.calculatedRouteSection?.speedModel)
        assertTrue(summary.categorySpeedFactors.any { it.categoryCodes == listOf("W75") && it.multiplier == 0.47 })
        assertTrue(summary.categorySpeedFactors.any { it.categoryCodes == listOf("M19", "M40") && it.multiplier == 0.95 })
        assertTrue(summary.summaryExplanation.contains("race speed factor 1.00"))
        assertTrue(summary.summaryExplanation.contains("below 1.00 slows category estimates"))
        assertTrue(summary.summaryExplanation.contains("SS=#.## speed specifiers replace the race factor"))
        assertEquals(1, summary.profileComparison.size)
        assertEquals(listOf("31", "32", "33"), summary.profileComparison.first { it.title == "Saved route" }.markers.map { it.label })
        assertEquals(1, summary.routeMaps.size)
        assertEquals(listOf("Saved foxes and route"), summary.kmlFolders.map { it.title })
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
                it.value.endsWith("(required 9-12 km)") &&
                it.status == DesktopCourseMetricStatus.Warning
        })
        assertTrue(
            "Metrics were ${summary.metrics}",
            summary.metrics.any {
                it.label == "Climb percent of horizontal length" &&
                    it.value.contains("(limit 6.0%)") &&
                it.status == DesktopCourseMetricStatus.Good
            }
        )
        assertTrue(summary.goodnessMetrics.sharedMetrics.any { it.label == "Calculated route agrees with saved route order" })
        assertFalse(summary.goodnessMetrics.sharedMetrics.any { it.label.contains("shortest possible route") })
        val importedGoodnessMetrics = summary.goodnessMetrics.groups.single { it.title == "Saved" }.metrics
        val calculatedGoodnessMetrics = summary.goodnessMetrics.groups.single { it.title == "Calculated" }.metrics
        assertTrue(importedGoodnessMetrics.any {
            it.label == "Saved route is shortest possible route" &&
                it.status == DesktopCourseMetricStatus.Good
        })
        assertTrue(calculatedGoodnessMetrics.any {
            it.label == "Calculated route is shortest possible route" &&
                it.status == DesktopCourseMetricStatus.Good
        })
        assertFalse(calculatedGoodnessMetrics.any { it.label == "Calculated route agrees with saved route order" })
        assertTrue(importedGoodnessMetrics.any { it.label == "Climb percent of horizontal length" })
        assertFalse(importedGoodnessMetrics.any { it.label == "Effective length" })
        assertTrue(importedGoodnessMetrics.any { it.label == "Total ideal-route wait time" })
        assertEquals(
            importedGoodnessMetrics.map(::routeMetricPairingLabel),
            calculatedGoodnessMetrics.map(::routeMetricPairingLabel)
        )
        val reportText = DesktopCourseAnalysisExports.reportText(summary)
        assertEquals("Save Fox Renumbering Only", summary.courseRecommendation.actionLabel)
        assertTrue(reportText.contains("Saved checks and metrics\n"))
        assertTrue(reportText.contains("Calculated checks and metrics\n"))
        assertFalse(reportText.contains("\nSaved\n"))
        assertFalse(reportText.contains("\nCalculated\n"))
        assertTrue(reportText.contains("Order comparison: Saved and calculated routes match"))
        assertFalse(reportText.contains("Calculated ideal route (calculated fox numbering):"))
        assertFalse(reportText.contains("Calculated straight-line length:"))
        assertFalse(reportText.contains("Route length:"))
        assertTrue(reportText.contains("Effective length: "))
        assertTrue(reportText.contains("(required 9-12 km)"))
        assertTrue(reportText.contains("Course Recommendation"))
        assertTrue(summary.courseRecommendation.paragraph.contains("Radio-Oracle recommends Save Fox Renumbering Only"))
        assertTrue(summary.courseRecommendation.paragraph.contains("outside the 9-12 km rules range"))
        assertTrue(summary.courseRecommendation.paragraph.contains("honest representation of the course's overall difficulty"))
        assertTrue(summary.waitRows.any { it.waitSeconds > 30 })
        assertTrue(summary.courseRecommendation.paragraph.contains("current fox numbering exceed 30 seconds"))
        assertTrue(summary.courseRecommendation.paragraph.contains("suggested fox numbering"))
        assertTrue(reportText.contains("Radio-Oracle recommends"))
        assertTrue(reportText.contains("Assumed running speed equals"))
        assertTrue(reportText.contains("Speed model factors"))
        assertTrue(reportText.contains("Provisional built-in category assumptions"))
        assertTrue(reportText.contains("not a rules-derived or race-calibrated table"))
        assertTrue(reportText.contains("per-leg speed adjustment"))
        assertTrue(reportText.contains("race speed factor is adjustable"))
        assertTrue(reportText.contains("M19/M40: x0.95"))
        assertTrue(reportText.contains("W75: x0.47"))
        assertTrue(reportText.contains("Unmatched categories: x1.00"))
    }

    @Test
    fun activeClassicFoxArrivalDoesNotAddFindPunchAllowanceToIdealTime() {
        val route = listOf(
            ProtectedCourseRoutePoint(39.0, -95.0, 100.0),
            ProtectedCourseRoutePoint(39.0, -94.9999, 100.0),
            ProtectedCourseRoutePoint(39.0, -94.99, 100.0),
            ProtectedCourseRoutePoint(39.0, -94.98, 100.0),
            ProtectedCourseRoutePoint(39.0, -94.97, 100.0)
        )
        val protectedInfo = protectedInfo(foxCount = 2).copy(
            route = route,
            sampledPointCount = route.size,
            controlPoints = protectedInfo(foxCount = 2).controlPoints.map { control ->
                when (control.controlId) {
                    "control-1" -> control.copy(longitude = -94.9999, elevationMeters = 100.0)
                    "control-2" -> control.copy(longitude = -94.99, elevationMeters = 100.0)
                    "control-beacon" -> control.copy(longitude = -94.98, elevationMeters = 100.0)
                    else -> control
                }
            },
            courseObjects = protectedInfo(foxCount = 2).courseObjects.map { courseObject ->
                when (courseObject.id) {
                    "control-1" -> courseObject.copy(longitude = -94.9999, elevationMeters = 100.0)
                    "control-2" -> courseObject.copy(longitude = -94.99, elevationMeters = 100.0)
                    "control-beacon" -> courseObject.copy(longitude = -94.98, elevationMeters = 100.0)
                    "finish" -> courseObject.copy(longitude = -94.97, elevationMeters = 100.0)
                    else -> courseObject.copy(elevationMeters = 100.0)
                }
            }
        )

        val importedSummary = DesktopCourseAnalyzer.analyze(
            projectFile = projectFile(foxCount = 2),
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo,
            protectedIdealOrderText = "31 32 Beacon"
        )

        val importedArrival = importedSummary.waitRows.single { it.controlLabel == "31" }
        val importedLeg = importedSummary.providedLegRows.single { it.toLabel == "31" }
        assertEquals(0, importedArrival.waitSeconds)
        assertEquals(0, importedLeg.waitSeconds)
        assertEquals(0, importedLeg.findPunchSeconds)
        assertEquals(importedArrival.arrivalSeconds, importedLeg.cumulativeSeconds)

        val calculatedSummary = DesktopCourseAnalyzer.analyze(
            projectFile = projectFile(foxCount = 2),
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo,
            protectedIdealOrderText = "32 31 Beacon"
        )

        val calculatedActiveLeg = calculatedSummary.calculatedLegRows.first { it.waitSeconds == 0 }
        assertEquals(0, calculatedActiveLeg.findPunchSeconds)
        assertEquals(
            calculatedSummary.calculatedRouteSection?.waitRows?.first { it.waitSeconds == 0 }?.arrivalSeconds,
            calculatedActiveLeg.cumulativeSeconds
        )
    }

    @Test
    fun importedSpeedSpecifiersOverrideTheFollowingLegTiming() {
        val baseInfo = protectedInfo(foxCount = 3)
        val protectedInfo = baseInfo.copy(
            controlPoints = baseInfo.controlPoints.map { control ->
                if (control.controlId == "control-1") {
                    control.copy(speedFactor = 0.50)
                } else {
                    control
                }
            },
            courseObjects = baseInfo.courseObjects.map { courseObject ->
                when (courseObject.id) {
                    "start" -> courseObject.copy(speedFactor = 0.75)
                    "control-1" -> courseObject.copy(speedFactor = 0.50)
                    "finish" -> courseObject.copy(speedFactor = 0.25)
                    else -> courseObject
                }
            }
        )

        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = projectFile(foxCount = 3, raceType = RaceType.FOXORING),
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo,
            protectedIdealOrderText = protectedInfo.idealOrder
        )

        val firstLeg = summary.providedLegRows.single { it.fromLabel == "S" && it.toLabel == "31" }
        val secondLeg = summary.providedLegRows.single { it.fromLabel == "31" && it.toLabel == "32" }
        val thirdLeg = summary.providedLegRows.single { it.fromLabel == "32" && it.toLabel == "33" }
        val finalLeg = summary.providedLegRows.single { it.fromLabel == "B" && it.toLabel == "F" }
        assertEquals(0.75, firstLeg.speedFactorOverride ?: -1.0, 0.001)
        assertEquals(0.50, secondLeg.speedFactorOverride ?: -1.0, 0.001)
        assertNull(thirdLeg.speedFactorOverride)
        assertNull(finalLeg.speedFactorOverride)
        assertTrue(requireNotNull(secondLeg.splitSeconds) > requireNotNull(thirdLeg.splitSeconds))

        val reportText = DesktopCourseAnalysisExports.reportText(summary)
        assertTrue(reportText.contains("(speed x0.75)"))
        assertTrue(reportText.contains("(speed x0.50)"))
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
    fun importedRouteAppendsBeaconAndFinishForDisplayTimingAndEffectiveLength() {
        val baseInfo = protectedInfo(foxCount = 2)
        val protectedInfo = baseInfo.copy(
            lengthMeters = 1,
            climbMeters = 0,
            sampledPointCount = 3,
            route = baseInfo.route.take(3),
            courseObjects = baseInfo.courseObjects.map { courseObject ->
                if (courseObject.type == ProtectedCourseObjectType.FINISH) {
                    courseObject.copy(
                        longitude = -94.955,
                        elevationMeters = 150.0
                    )
                } else {
                    courseObject
                }
            }
        )

        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = projectFile(foxCount = 2),
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo,
            protectedIdealOrderText = "31 32"
        )

        assertEquals(listOf("S", "31", "32", "B", "F"), summary.providedIdealOrder)
        assertEquals(
            listOf("S -> 31", "31 -> 32", "32 -> B", "B -> F"),
            summary.providedLegRows.map { "${it.fromLabel} -> ${it.toLabel}" }
        )
        assertEquals(50, summary.climbMeters)
        assertTrue(requireNotNull(summary.routeLengthMeters) > 3_000)
        assertTrue(requireNotNull(summary.effectiveLengthMeters) > requireNotNull(summary.routeLengthMeters))
        assertEquals(summary.providedRouteSection?.routeLengthMeters, summary.routeLengthMeters)
        assertEquals(summary.providedRouteSection?.climbMeters, summary.climbMeters)
        assertEquals(summary.providedRouteSection?.effectiveLengthMeters, summary.effectiveLengthMeters)
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
        val calculatedFoxLabels = requireNotNull(summary.calculatedRouteSection)
            .secondaryRouteOrder
            .drop(1)
            .dropLast(1)
            .filterNot { it == "B" }
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
        assertTrue(sectionChecks.any { it.label == "Saved route fox count" && it.status == DesktopCourseMetricStatus.Warning })
        assertTrue(sectionChecks.any { it.label == "Saved route course length" && it.status == DesktopCourseMetricStatus.Warning })
        assertTrue(sectionChecks.any { it.label == "Classic start exclusion zone" && it.status == DesktopCourseMetricStatus.Good })
        assertTrue(sectionChecks.any { it.label == "Saved route fox count" && it.value == "3 foxes (required 5 for M21)" })
        assertTrue(sectionChecks.any { it.label == "Saved route course length" && it.value.contains("(required 9-12 km)") })
        assertTrue(sectionChecks.any { it.label == "Classic start exclusion zone" && it.value.contains("nearest transmitter") && it.value.contains("(required at least 750 m)") })
        assertTrue(sectionChecks.any { it.label == "Classic minimum transmitter spacing" && it.value.contains("closest pair") })

        val reportText = DesktopCourseAnalysisExports.reportText(summary)
        assertTrue(reportText.contains("Rules applied: USA Rules for Radio Orienteering, Effective Date: 1 Jan 2026"))
        assertEquals(1, Regex("Rules applied: USA Rules for Radio Orienteering, Effective Date: 1 Jan 2026").findAll(reportText).count())
        assertTrue(reportText.contains("Effective length: "))
        assertTrue(reportText.contains("(required 9-12 km)"))
        assertTrue(reportText.contains("RULE VIOLATION: Saved route fox count"))
        assertTrue(reportText.contains("RULE VIOLATION: Saved route course length"))
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
                it.label == "Saved route USA category name" &&
                it.value.contains("Using M21 rules for category \"M-21\"") &&
                it.status == DesktopCourseMetricStatus.Warning
        })
        assertTrue(sectionChecks.any {
                it.label == "Saved route fox count" &&
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
        assertEquals(listOf("S", "1", "2", "Spectator", "F1", "F2", "B", "F"), section.routeOrder)
        val importedCourseObjects = summary.kmlFolders
            .single { it.title == "Saved foxes and route" }
            .courseObjects
        assertTrue(importedCourseObjects.any { it.label == "Start" && it.type == DesktopCourseKmlExportPointType.START })
        assertTrue(importedCourseObjects.any { it.label == "Spectator" && it.type == DesktopCourseKmlExportPointType.SPECTATOR })
        assertTrue(importedCourseObjects.any { it.label == "Finish" && it.type == DesktopCourseKmlExportPointType.FINISH })
        assertTrue(section.explanation.contains("Sprint route calculated as separate first and fast loops"))
        assertTrue(section.ruleChecks.any { it.label == "Calculated route Sprint target time" })
        assertTrue(section.ruleChecks.any { it.label == "Sprint minimum transmitter spacing" && it.value.contains("closest pair F1-Spectator") })
        assertTrue(
            "Metrics were ${summary.goodnessMetrics.groups}",
            summary.goodnessMetrics.groups
                .flatMap { it.metrics }
                .any {
                    it.label == "Climb percent of horizontal length" &&
                        it.value.contains("(limit 6.0%)")
                }
        )
    }

    @Test
    fun calculatedSprintRouteIncludesMandatoryWaypointsAndUsesSpectatorTransition() {
        val baseInfo = sprintProtectedInfo()
        val gateAfterStart = ProtectedCourseObjectPoint(
            id = "waypoint-start-corridor",
            label = "End Corridor_Strt",
            type = ProtectedCourseObjectType.WAYPOINT,
            latitude = 39.0,
            longitude = -94.995,
            elevationMeters = 100.0
        )
        val gateAfterSpectator = ProtectedCourseObjectPoint(
            id = "waypoint-spectator-corridor",
            label = "End Corridor_S",
            type = ProtectedCourseObjectType.WAYPOINT,
            latitude = 39.0,
            longitude = -94.96025,
            elevationMeters = 100.0
        )
        val spectatorIndex = baseInfo.courseObjects.indexOfFirst { it.type == ProtectedCourseObjectType.SPECTATOR }
        val protectedInfo = baseInfo.copy(
            courseObjects = buildList {
                add(baseInfo.courseObjects.first())
                add(gateAfterStart)
                addAll(baseInfo.courseObjects.drop(1).take(spectatorIndex - 1))
                add(baseInfo.courseObjects[spectatorIndex])
                add(gateAfterSpectator)
                addAll(baseInfo.courseObjects.drop(spectatorIndex + 1))
            }
        )

        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = sprintProjectFile(),
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo,
            protectedIdealOrderText = "2 1 Spectator F2 F1 Beacon"
        )

        val section = requireNotNull(summary.calculatedRouteSection)
        assertEquals(
            listOf("S", "1", "2", "Spectator", "F1", "F2", "B", "F"),
            section.routeOrder
        )
        assertTrue(section.explanation.contains("using the assigned spectator"))
        val routeMap = requireNotNull(section.routeMap)
        assertEquals(
            listOf("S", "1", "2", "Spectator", "F1", "F2", "B", "F"),
            routeMap.routeLabels
        )
        assertTrue(routeMap.points.any { it.label == "End Corridor_Strt" && it.type == DesktopCourseRouteMapPointType.Waypoint })
        assertTrue(routeMap.points.any { it.label == "End Corridor_S" && it.type == DesktopCourseRouteMapPointType.Waypoint })

        val calculatedFolder = summary.kmlFolders.single { it.title == "Calculated foxes and route" }
        assertEquals(
            listOf("S", "End Corridor_Strt", "1", "2", "Spectator", "End Corridor_S", "F1", "F2", "B", "F"),
            calculatedFolder.routeStops.map { it.label }
        )
        assertEquals(
            listOf("End Corridor_Strt", "End Corridor_S"),
            calculatedFolder.courseObjects
                .filter { it.type == DesktopCourseKmlExportPointType.WAYPOINT }
                .map { it.label }
        )
        assertTrue(calculatedFolder.routeStops.indexOfFirst { it.label == "Spectator" } < calculatedFolder.routeStops.indexOfFirst { it.label == "B" })
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
            .single { it.label == "Saved route Sprint target time" }
            .value
        val calculatedTargetTime = requireNotNull(summary.calculatedRouteSection)
            .ruleChecks
            .single { it.label == "Calculated route Sprint target time" }
            .value
        assertEquals(storedTargetTime, calculatedTargetTime)
        assertFalse(storedTargetTime.startsWith("Unknown"))
    }

    @Test
    fun importedRouteUsesExplicitStartInsteadOfSpectatorEndpoint() {
        val protectedInfo = sprintProtectedInfo().copy(
            route = listOf(
                ProtectedCourseRoutePoint(39.0, -94.9605, 100.0),
                ProtectedCourseRoutePoint(39.0, -94.96, 100.0),
                ProtectedCourseRoutePoint(39.0, -94.95, 100.0),
                ProtectedCourseRoutePoint(39.0, -94.94, 100.0),
                ProtectedCourseRoutePoint(39.0, -94.93, 100.0)
            )
        )

        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = sprintProjectFile(),
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo,
            protectedIdealOrderText = "1 2 Spectator F1 F2 Beacon"
        )

        val importedRoute = summary.kmlFolders.single { it.title == "Saved foxes and route" }
        assertEquals(-95.0, importedRoute.routePoints.first().longitude, 0.000001)
        assertEquals(-94.93, importedRoute.routePoints.last().longitude, 0.000001)
        assertEquals("S", importedRoute.routeStops.first().label)
        assertEquals(-95.0, importedRoute.routeStops.first().point.longitude, 0.000001)
        assertEquals("Spectator", importedRoute.routeStops.first { it.label == "Spectator" }.label)
    }

    @Test
    fun importedRouteDoesNotUseSpectatorAsStartWhenLineStringStartsCorrectly() {
        val baseInfo = sprintProtectedInfo()
        val spectatorPoint = baseInfo.courseObjects.single { it.type == ProtectedCourseObjectType.SPECTATOR }
        val protectedInfo = baseInfo.copy(
            courseObjects = baseInfo.courseObjects.map { courseObject ->
                if (courseObject.type == ProtectedCourseObjectType.START) {
                    courseObject.copy(
                        latitude = spectatorPoint.latitude,
                        longitude = spectatorPoint.longitude,
                        elevationMeters = spectatorPoint.elevationMeters
                    )
                } else {
                    courseObject
                }
            }
        )

        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = sprintProjectFile(),
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo,
            protectedIdealOrderText = "1 2 Spectator F1 F2 Beacon"
        )

        val importedRoute = summary.kmlFolders.single { it.title == "Saved foxes and route" }
        assertEquals(-95.0, importedRoute.routePoints.first().longitude, 0.000001)
        assertEquals("S", importedRoute.routeStops.first().label)
        assertEquals(-95.0, importedRoute.routeStops.first().point.longitude, 0.000001)
        assertEquals(-94.9605, importedRoute.routeStops.first { it.label == "Spectator" }.point.longitude, 0.000001)
    }

    @Test
    fun pdfRouteDepictionUsesRoutePointIndexesWhenStartAndSpectatorShareLabel() {
        val projectFile = sprintProjectFile().copy(
            raceData = sprintProjectFile().raceData.copy(
                controls = sprintProjectFile().raceData.controls.map { control ->
                    if (control.type == ControlPointType.SEPARATOR) {
                        control.copy(label = "S", publicLabel = "S")
                    } else {
                        control
                    }
                }
            )
        )
        val baseInfo = sprintProtectedInfo()
        val protectedInfo = baseInfo.copy(
            controlPoints = baseInfo.controlPoints.map { controlPoint ->
                if (controlPoint.type == ControlPointType.SEPARATOR) {
                    controlPoint.copy(label = "S")
                } else {
                    controlPoint
                }
            },
            courseObjects = baseInfo.courseObjects.map { courseObject ->
                if (courseObject.type == ProtectedCourseObjectType.SPECTATOR) {
                    courseObject.copy(label = "S")
                } else {
                    courseObject
                }
            }
        )

        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = projectFile,
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo,
            protectedIdealOrderText = "1 2 S F1 F2 Beacon"
        )

        val routeMap = requireNotNull(summary.routeMaps.firstOrNull { it.title == "Saved route" })
        assertEquals("S", routeMap.points[routeMap.routePointIndexes.first()].label)
        assertEquals(DesktopCourseRouteMapPointType.Start, routeMap.points[routeMap.routePointIndexes.first()].type)
        assertTrue(routeMap.points.any { it.label == "S" && it.type == DesktopCourseRouteMapPointType.Spectator })

        val pdfPath = Files.createTempFile("course-analysis-duplicate-s-route-map", ".pdf")
        DesktopCourseAnalysisExports.exportPdf(pdfPath, summary)
        val pdfText = String(Files.readAllBytes(pdfPath))
        val routeLinePoints = routeMap.routePointIndexes.mapNotNull { routeMap.points.getOrNull(it) }
        val correctFirstLine = pdfRouteMapLineCommand(routeLinePoints[0], routeLinePoints[1])
        val spectatorPoint = routeMap.points.single { it.label == "S" && it.type == DesktopCourseRouteMapPointType.Spectator }
        val wrongFirstLine = pdfRouteMapLineCommand(spectatorPoint, routeLinePoints[1])
        assertTrue("PDF should draw first route leg from true Start: $correctFirstLine", pdfText.contains(correctFirstLine))
        assertFalse("PDF should not draw first route leg from spectator: $wrongFirstLine", pdfText.contains(wrongFirstLine))
    }

    @Test
    fun importedRouteEndingAtStartIsReversedBeforeAnalysis() {
        val protectedInfo = sprintProtectedInfo().copy(
            route = sprintProtectedInfo().route.reversed()
        )

        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = sprintProjectFile(),
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo,
            protectedIdealOrderText = "1 2 Spectator F1 F2 Beacon"
        )

        val importedRoute = summary.kmlFolders.single { it.title == "Saved foxes and route" }
        assertEquals(-95.0, importedRoute.routePoints.first().longitude, 0.000001)
        assertEquals(-94.93, importedRoute.routePoints.last().longitude, 0.000001)
        assertEquals("S", importedRoute.routeStops.first().label)
        assertEquals("F", importedRoute.routeStops.last().label)
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
        assertEquals(listOf("S", "1", "2", "B", "F2", "F1", "B", "F"), section.routeOrder)
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
            assertFalse("raceType=$raceType", summary.providedRouteSection?.includeWaitAnalysis ?: true)
            assertFalse("raceType=$raceType", summary.calculatedRouteSection?.includeWaitAnalysis ?: true)
            assertFalse(
                "raceType=$raceType metrics=${summary.goodnessMetrics}",
                summary.goodnessMetrics.groups
                    .flatMap { it.metrics }
                    .any { it.label.contains("wait", ignoreCase = true) }
            )
            val reportText = DesktopCourseAnalysisExports.reportText(summary)
            assertFalse("raceType=$raceType report=$reportText", reportText.contains("wait", ignoreCase = true))
            assertFalse("raceType=$raceType report=$reportText", reportText.contains("find/punch", ignoreCase = true))
            val pdfPath = Files.createTempFile("course-analysis-no-wait-$raceType", ".pdf")
            DesktopCourseAnalysisExports.exportPdf(pdfPath, summary)
            val pdfText = String(Files.readAllBytes(pdfPath))
            assertFalse("raceType=$raceType", pdfText.contains("wait", ignoreCase = true))
            assertFalse("raceType=$raceType", pdfText.contains("find/punch", ignoreCase = true))
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
                it.label == "Climb percent of horizontal length" &&
                    it.value.contains("(limit 6.0%)") &&
                    it.status == DesktopCourseMetricStatus.Warning
            }
        )
    }

    @Test
    fun savedRouteMapIncludesTheFullImportedRouteLineString() {
        val protectedInfo = protectedInfo(foxCount = 3).withIntermediateRoutePoints()

        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = projectFile(foxCount = 3),
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo,
            protectedIdealOrderText = protectedInfo.idealOrder
        )

        val routeMap = requireNotNull(summary.routeMaps.firstOrNull { it.title == "Saved route" })
        val routeLine = routeMap.lineStrings.single()
        assertEquals(protectedInfo.route.size, routeLine.points.size)
        assertFalse(routeLine.dashed)
    }

    @Test
    fun exportsCourseAnalysisReportTextAndPdf() {
        val projectFile = projectFile(foxCount = 3).withSameCourseCategory("cat-m50", "M50")
        val protectedInfo = protectedInfo(foxCount = 3).withIntermediateRoutePoints()
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
        assertTrue(reportText.contains("Race: Test Event"))
        assertTrue(reportText.contains("Race file: analysis-event.rom.json"))
        assertTrue(reportText.contains("Race format: Classic"))
        assertTrue(reportText.contains("Race type: Practice"))
        assertTrue(reportText.contains("Analyzed: Mon, Jun 15, 2026 9:30 AM"))
        assertTrue(reportText.indexOf("Analyzed:") < reportText.indexOf("Category:"))
        assertTrue(reportText.contains("Section 2: Calculated ideal route"))
        assertTrue(reportText.contains("Route order (saved fox numbering):"))
        assertTrue(reportText.contains("Route order (calculated fox numbering):"))
        assertTrue(reportText.contains("Saved checks and metrics\n"))
        assertTrue(reportText.contains("Calculated checks and metrics\n"))
        assertTrue(reportText.contains("Saved route:"))
        assertFalse(reportText.contains("Ideal route:"))
        assertTrue(reportText.contains("Course Recommendation"))
        assertTrue(reportText.contains("Radio-Oracle recommends"))
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
        assertTrue(pdfText.contains("Race: Test Event"))
        assertTrue(pdfText.contains("Race file: analysis-event.rom.json"))
        assertTrue(pdfText.contains("Analyzed: Mon, Jun 15, 2026 9:30 AM"))
        assertTrue(pdfText.contains("/Helvetica-Bold"))
        assertTrue(pdfText.contains("Course Recommendation"))
        assertTrue(pdfText.contains("with calculated fox numbering"))
        assertTrue(pdfText.contains("2D route"))
        assertTrue(pdfText.contains("depiction"))
        assertTrue(pdfText.contains("Elevation Profile Graphics"))
        assertTrue(pdfText.contains("2D Route Depiction Graphics"))
        assertPdfInfoCanRead(pdfPath)
        assertEquals(pdfPath, exportPaths.pdfPath)
        val expectedFileStem = "Classic - 3 Foxes - 3.86 km - M21,M50"
        assertEquals("$expectedFileStem.pdf", DesktopCourseAnalysisExports.defaultPdfFileName(summary))
        assertEquals(pdfPath.resolveSibling("$expectedFileStem.kml"), exportPaths.kmlPath)

        val multiPagePdfPath = Files.createTempFile("course-analysis-multipage", ".pdf")
        DesktopCourseAnalysisExports.exportPdf(
            multiPagePdfPath,
            summary.copy(missingElements = (1..120).map { "PDF validation filler line $it." })
        )
        assertPdfInfoCanRead(multiPagePdfPath)

        val kmlText = Files.readString(exportPaths.kmlPath)
        assertTrue(kmlText.contains("<name>Saved foxes and route</name>"))
        assertTrue(kmlText.contains("<name>Calculated foxes and route</name>"))
        assertTrue(kmlText.contains("<LineString>"))
        assertTrue(kmlText.contains("<Point>"))
        assertTrue(kmlText.contains("<href>http://maps.google.com/mapfiles/kml/shapes/donut.png</href>"))
        assertTrue(kmlText.contains("<href>http://maps.google.com/mapfiles/kml/shapes/triangle.png</href>"))
        assertTrue(kmlText.contains("<href>http://maps.google.com/mapfiles/kml/shapes/target.png</href>"))
        assertTrue(kmlText.contains("<color>ffef72ed</color>"))
        assertTrue(kmlText.contains("<LabelStyle><color>ffef72ed</color><colorMode>normal</colorMode></LabelStyle>"))
        assertTrue(kmlText.contains("<scale>1.2</scale>"))
        assertTrue(kmlText.contains("<styleUrl>#courseStartStyle</styleUrl>"))
        assertTrue(kmlText.contains("<styleUrl>#courseFinishStyle</styleUrl>"))
        assertTrue(kmlText.contains("<styleUrl>#courseControlDoughnutStyle</styleUrl>"))
        assertTrue(kmlText.contains("<name>Start</name>"))
        assertTrue(kmlText.contains("<name>Finish</name>"))
        assertTrue(kmlText.contains("<name>31</name>"))
        assertTrue(kmlText.contains("<name>B</name>"))
        assertTrue(kmlText.placemarkNamed("Start").contains("<styleUrl>#courseStartStyle</styleUrl>"))
        assertTrue(kmlText.placemarkNamed("Finish").contains("<styleUrl>#courseFinishStyle</styleUrl>"))
        assertTrue(kmlText.placemarkNamed("31").contains("<styleUrl>#courseControlDoughnutStyle</styleUrl>"))
        assertTrue(kmlText.placemarkNamed("B").contains("<styleUrl>#courseControlDoughnutStyle</styleUrl>"))
        val foxPlacemark = kmlText.placemarkNamed("31")
        assertTrue(foxPlacemark.contains("<description>SI=31</description>"))
        val beaconPlacemark = kmlText.placemarkNamed("B")
        assertTrue(beaconPlacemark.contains("<description>SI=99</description>"))
        assertEquals(
            List(summary.kmlFolders.size) { expectedFileStem },
            kmlLineStringPlacemarkNames(kmlText)
        )
        kmlLineStringPlacemarks(kmlText).forEach { placemark ->
            val description = Regex("""<description>([\s\S]*?)</description>""")
                .find(placemark)
                ?.groupValues
                ?.get(1)
                .orEmpty()
            assertTrue(description.contains("Categories: M21, M50"))
            assertTrue(description.contains(Regex("""Horizontal Length: \d+\.\d{2} km""")))
            assertTrue(description.contains(Regex("""Climb: \d+m \(\d+\.\d%\)""")))
            assertTrue(description.contains(Regex("""Effective Length: \d+\.\d{2} km""")))
        }
        val lineStringCoordinateLines = kmlLineStringCoordinateLines(kmlText)
        assertEquals("Expected one route LineString per exported course route", summary.kmlFolders.size, lineStringCoordinateLines.size)
        assertTrue(lineStringCoordinateLines.all { it.size > 2 })
        val lineStringCoordinates = lineStringCoordinateLines.flatten()
        assertFalse(
            "Intermediate saved route sample points should not be written into KML LineStrings",
            lineStringCoordinates.any { it.startsWith("-94.99500000,") || it.startsWith("-94.98500000,") }
        )
    }

    @Test
    fun importedRouteAnalysisForcesBeaconFromProtectedControlPointIntoRouteAndKmlExport() {
        val baseInfo = protectedInfo(foxCount = 3)
        val beaconPoint = baseInfo.controlPoints.single { it.type == ControlPointType.BEACON }
            .copy(latitude = 39.01, longitude = -94.96)
        val finishPoint = baseInfo.route.last().copy(latitude = 39.0, longitude = -94.95)
        val protectedInfo = baseInfo.copy(
            route = baseInfo.route.dropLast(1) + finishPoint,
            controlPoints = baseInfo.controlPoints.map { controlPoint ->
                if (controlPoint.type == ControlPointType.BEACON) beaconPoint else controlPoint
            },
            courseObjects = baseInfo.courseObjects
                .filterNot { it.type == ProtectedCourseObjectType.BEACON }
                .map { courseObject ->
                    if (courseObject.type == ProtectedCourseObjectType.FINISH) {
                        courseObject.copy(
                            latitude = finishPoint.latitude,
                            longitude = finishPoint.longitude,
                            elevationMeters = finishPoint.elevationMeters
                        )
                    } else {
                        courseObject
                    }
                }
        )

        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = projectFile(foxCount = 3),
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo,
            protectedIdealOrderText = protectedInfo.idealOrder
        )

        val importedFolder = summary.kmlFolders.single { it.title == "Saved foxes and route" }
        assertEquals(listOf("S", "31", "32", "33", "B", "F"), importedFolder.routeStops.map { it.label })
        assertEquals(39.01, importedFolder.routeStops.single { it.label == "B" }.point.latitude, 0.000001)
        assertEquals("B", importedFolder.courseObjects.single { it.label == "B" }.label)
        assertTrue(requireNotNull(summary.providedRouteSection).routeLengthMeters!! > protectedInfo.lengthMeters!!)

        val kmlPath = Files.createTempFile("course-analysis-beacon-route", ".kml")
        DesktopCourseAnalysisExports.exportKml(kmlPath, summary)
        val kmlText = Files.readString(kmlPath)
        assertTrue(kmlText.contains("<name>B</name>"))
        assertTrue(kmlLineStringCoordinateLines(kmlText).flatten().any { it.startsWith("-94.96000000,39.01000000") })
    }

    @Test
    fun importedRouteAnalysisSurvivesPublicLabelChangeAfterImport() {
        val protectedInfo = protectedInfo(foxCount = 3)
        val project = projectFile(foxCount = 3).withControlPublicLabel("control-beacon", "B")

        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = project,
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo,
            protectedIdealOrderText = protectedInfo.idealOrder
        )

        assertTrue(summary.missingElements.none { it.contains("Saved route order") })
        assertEquals(listOf("S", "31", "32", "33", "B", "F"), summary.providedIdealOrder)
        assertEquals("B", summary.kmlFolders.single { it.title == "Saved foxes and route" }.routeStops.single { it.label == "B" }.label)
    }

    @Test
    fun importedRouteAnalysisExportsMandatoryWaypointsWithCircleStyle() {
        val baseInfo = protectedInfo(foxCount = 3)
        val waypoint = ProtectedCourseObjectPoint(
            id = "waypoint-gate-a",
            label = "Gate A",
            type = ProtectedCourseObjectType.WAYPOINT,
            latitude = 39.0,
            longitude = -94.985,
            elevationMeters = 100.0
        )
        val protectedInfo = baseInfo.copy(
            courseObjects = baseInfo.courseObjects.take(2) + waypoint + baseInfo.courseObjects.drop(2)
        )

        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = projectFile(foxCount = 3),
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo,
            protectedIdealOrderText = protectedInfo.idealOrder
        )

        val importedFolder = summary.kmlFolders.single { it.title == "Saved foxes and route" }
        assertEquals("Gate A", importedFolder.courseObjects.single { it.type == DesktopCourseKmlExportPointType.WAYPOINT }.label)
        assertTrue(importedFolder.routeStops.map { it.label }.contains("Gate A"))
        val routeMap = requireNotNull(summary.providedRouteSection).routeMap!!
        assertTrue(routeMap.points.any { it.label == "Gate A" })
        assertEquals(listOf("S", "31", "32", "33", "B", "F"), routeMap.routeLabels)

        val kmlPath = Files.createTempFile("course-analysis-waypoint-route", ".kml")
        DesktopCourseAnalysisExports.exportKml(kmlPath, summary)
        val kmlText = Files.readString(kmlPath)
        assertTrue(kmlText.contains("http://maps.google.com/mapfiles/kml/shapes/placemark_circle.png"))
        assertTrue(kmlText.contains("<name>Gate A</name>"))
        assertTrue(kmlText.contains("<styleUrl>#courseWaypointCircleStyle</styleUrl>"))
        assertTrue(kmlLineStringCoordinateLines(kmlText).flatten().any { it.startsWith("-94.98500000,39.00000000") })
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
        assertTrue(summary.summaryExplanation.contains("may reduce saved-route wait time"))
        assertTrue(summary.summaryExplanation.contains("see Section 1 for the assignment details"))
        assertEquals("Save Calculated Route", summary.courseRecommendation.actionLabel)
        assertTrue(summary.courseRecommendation.paragraph.contains("Radio-Oracle recommends Save Calculated Route"))
        assertTrue(DesktopCourseAnalysisExports.reportText(summary).contains("Renumbered wait times"))
        val metricLabels = summary.metrics.map { it.label }
        assertEquals(
            metricLabels.indexOf("Total ideal-route wait time") + 1,
            metricLabels.indexOf("Total ideal-route wait time with renumbering")
        )
        assertEquals(
            metricLabels.indexOf("Challenge vs target winning time") + 1,
            metricLabels.indexOf("Saved route finish time with renumbering")
        )
        assertTrue(summary.metrics.first { it.label == "Total ideal-route wait time with renumbering" }.value.contains(":"))
        assertTrue(summary.metrics.first { it.label == "Saved route finish time with renumbering" }.value.contains(" / "))
    }

    @Test
    fun recommendsShorterCalculatedRouteWhenImportedGeometryIsLonger() {
        val baseInfo = protectedInfo(foxCount = 3)
        val longImportedRoute = listOf(
            ProtectedCourseRoutePoint(39.0, -95.0, 100.0),
            ProtectedCourseRoutePoint(39.0, -94.99, 100.0),
            ProtectedCourseRoutePoint(39.012, -94.99, 100.0),
            ProtectedCourseRoutePoint(39.0, -94.98, 100.0),
            ProtectedCourseRoutePoint(39.012, -94.98, 100.0),
            ProtectedCourseRoutePoint(39.0, -94.97, 100.0),
            ProtectedCourseRoutePoint(39.012, -94.97, 100.0),
            ProtectedCourseRoutePoint(39.0, -94.96, 100.0)
        )
        val protectedInfo = baseInfo.copy(
            lengthMeters = 9_000,
            climbMeters = 0,
            sampledPointCount = longImportedRoute.size,
            route = longImportedRoute,
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
                it.label == "Saved route course length" &&
                    it.status == DesktopCourseMetricStatus.Good
            }
        )
        assertTrue(
            summary.metrics.any {
                it.label == "Calculated route course length" &&
                    it.status == DesktopCourseMetricStatus.Warning
            }
        )
        val importedGoodnessMetrics = summary.goodnessMetrics.groups.single { it.title == "Saved" }.metrics
        val calculatedGoodnessMetrics = summary.goodnessMetrics.groups.single { it.title == "Calculated" }.metrics
        assertTrue(importedGoodnessMetrics.any {
            it.label == "Saved route is shortest possible route" &&
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
        assertEquals("Save Calculated Route", summary.courseRecommendation.actionLabel)
        assertTrue(summary.courseRecommendation.paragraph.contains("The saved route is"))
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
        val calculatedOrder = listOf("S") + renumbering.assignments.map { it.suggestedSlotLabel } + "B" + "F"
        assertEquals("Route order (saved fox numbering)", section.routeOrderLabel)
        assertEquals(listOf("S", "33", "32", "31", "B", "F"), section.routeOrder)
        assertEquals("Route order (calculated fox numbering)", section.secondaryRouteOrderLabel)
        assertEquals(calculatedOrder, section.secondaryRouteOrder)
        assertEquals(calculatedOrder, summary.calculatedIdealOrder)
        assertEquals(
            calculatedOrder.zipWithNext().map { (from, to) -> "$from -> $to" },
            summary.calculatedLegRows.map { "${it.fromLabel} -> ${it.toLabel}" }
        )
        assertEquals(renumbering.assignments.map { it.suggestedSlotLabel }, section.waitRows.map { it.controlLabel })
        assertEquals(
            renumbering.assignments.map { it.suggestedSlotLabel },
            summary.profileComparison.first { it.title == "Calculated route (calculated fox numbering)" }.markers.map { it.label }
        )
        val routeMap = requireNotNull(section.routeMap)
        assertEquals(calculatedOrder, routeMap.routeLabels)
        assertEquals(
            calculatedOrder,
            routeMap.routePointIndexes.map { routeMap.points[it].label }
        )
    }

    @Test
    fun appliesCalculatedRouteAndNumberingToSavedCourseData() {
        val projectFile = projectFile(
            foxCount = 3,
            publicLabels = listOf("Fox 3", "Fox 2", "Fox 1")
        )
        val sourceDescriptionByLabel = mapOf(
            "31" to "SI=135\nCustodian note for Fox 1",
            "32" to "SI=136\nCustodian note for Fox 2",
            "33" to "SI=137\nCustodian note for Fox 3"
        )
        val protectedInfo = protectedInfo(foxCount = 3).withControlDescriptions(sourceDescriptionByLabel)
        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = projectFile,
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo,
            protectedIdealOrderText = "'Fox 1' 'Fox 2' 'Fox 3' Beacon"
        )
        val application = requireNotNull(summary.calculatedRouteApplication)
        val descriptionByIdentity = sourceDescriptionByLabel.entries.associate { (label, description) ->
            label.courseDescriptionIdentityKey() to description
        }
        val calculatedFolder = summary.kmlFolders.single { it.title == "Calculated foxes and route" }
        application.foxAssignments.forEach { assignment ->
            val expectedDescription = descriptionByIdentity.getValue(assignment.calculatedLabel.courseDescriptionIdentityKey())
            val exportPoint = calculatedFolder.courseObjects.single { it.label == assignment.calculatedLabel }
            assertEquals(expectedDescription, exportPoint.description)
            assertEquals(expectedDescription.courseDescriptionSiCodeHint(), exportPoint.siCode)
        }
        val encryptedCourseInfo = DesktopProtectedCourseOrder.encryptCourseInfo(protectedInfo, "test-password")
        val encryptedIdealOrder = DesktopProtectedCourseOrder.encrypt(protectedInfo.idealOrder, "test-password")
        val projectWithSecondCategory = projectFile
            .withSameCourseCategory(categoryId = "category-m40", categoryName = "M40")
            .withAliasesAndUnmatchedControlReadout()
        val projectWithResults = projectWithSecondCategory.copy(
            raceData = projectWithSecondCategory.raceData.copy(
                categories = projectWithSecondCategory.raceData.categories.map { categoryData ->
                    categoryData.copy(
                        category = categoryData.category.copy(
                            encryptedIdealOrder = encryptedIdealOrder,
                            encryptedCourseInfo = encryptedCourseInfo
                        )
                    )
                }
            )
        )
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
        val secondCategory = updatedProject.raceData.categories.single { it.category.id == "category-m40" }.category
        val secondIdealOrder = DesktopProtectedCourseOrder.decrypt(requireNotNull(secondCategory.encryptedIdealOrder), "test-password")
        val secondCourseInfo = DesktopProtectedCourseOrder.decryptCourseInfo(requireNotNull(secondCategory.encryptedCourseInfo), "test-password")
        assertEquals(
            application.foxAssignments.map { it.calculatedLabel } + "Beacon",
            org.openardf.radiooracle.shared.course.ControlPointRules.tokenizeControlPoints(secondIdealOrder)
        )
        application.foxAssignments.forEach { assignment ->
            val expectedDescription = descriptionByIdentity.getValue(assignment.calculatedLabel.courseDescriptionIdentityKey())
            assertEquals(
                assignment.calculatedLabel,
                secondCourseInfo.controlPoints.single { it.controlId == assignment.controlId }.label
            )
            assertEquals(
                expectedDescription,
                secondCourseInfo.controlPoints.single { it.controlId == assignment.controlId }.description
            )
            assertEquals(
                assignment.calculatedLabel,
                secondCourseInfo.courseObjects.single { it.id == assignment.controlId }.label
            )
            assertEquals(
                expectedDescription,
                secondCourseInfo.courseObjects.single { it.id == assignment.controlId }.description
            )
        }
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
        assertTrue(updatedSummary.missingElements.none { it.contains("Saved route order") })
        val updatedRouteMap = requireNotNull(updatedSummary.providedRouteSection?.routeMap)
        val updatedRouteOrder = requireNotNull(updatedSummary.providedRouteSection).routeOrder
        assertEquals(updatedRouteOrder, updatedRouteMap.routeLabels)
        assertEquals(
            updatedRouteOrder,
            updatedRouteMap.routePointIndexes.map { updatedRouteMap.points[it].label }
        )
        val savedFolder = updatedSummary.kmlFolders.single { it.title == "Saved foxes and route" }
        application.foxAssignments.forEach { assignment ->
            val expectedDescription = descriptionByIdentity.getValue(assignment.calculatedLabel.courseDescriptionIdentityKey())
            val exportPoint = savedFolder.courseObjects.single { it.label == assignment.calculatedLabel }
            assertEquals(expectedDescription, exportPoint.description)
            assertEquals(expectedDescription.courseDescriptionSiCodeHint(), exportPoint.siCode)
        }
    }

    @Test
    fun appliesCalculatedIdealOrderToEveryCategoryWithTheSameAssignedCourse() {
        val password = "test-password"
        val baseProject = projectFile(
            foxCount = 3,
            publicLabels = listOf("Fox 3", "Fox 2", "Fox 1")
        )
        val sourceCourseInfo = protectedInfo(foxCount = 3)
        val application = requireNotNull(
            DesktopCourseAnalyzer.analyze(
                projectFile = baseProject,
                categoryId = CATEGORY_ID,
                protectedCourseInfo = sourceCourseInfo,
                protectedIdealOrderText = "'Fox 1' 'Fox 2' 'Fox 3' Beacon"
            ).calculatedRouteApplication
        )
        val sameCourseInfo = sourceCourseInfo.copy(
            idealOrder = "'Fox 3' 'Fox 1' 'Fox 2' Beacon",
            lengthMeters = 8_765,
            climbMeters = 432,
            route = sourceCourseInfo.route.reversed()
        )
        val differentCourseInfo = protectedInfo(foxCount = 2)
        val projectWithCategories = baseProject
            .withSameCourseCategory(categoryId = "category-m40", categoryName = "M40")
            .withSameCourseCategory(categoryId = "category-w65", categoryName = "W65")
        val projectToUpdate = projectWithCategories.copy(
            raceData = projectWithCategories.raceData.copy(
                categories = projectWithCategories.raceData.categories.map { categoryData ->
                    val (courseInfo, controlPoints) = when (categoryData.category.id) {
                        "category-m40" -> sameCourseInfo to categoryData.controlPoints.reversed()
                        "category-w65" -> differentCourseInfo to categoryData.controlPoints
                            .filterNot { it.controlId == "control-3" }
                        else -> sourceCourseInfo to categoryData.controlPoints
                    }
                    categoryData.copy(
                        category = categoryData.category.copy(
                            encryptedIdealOrder = DesktopProtectedCourseOrder.encrypt(courseInfo.idealOrder, password),
                            encryptedCourseInfo = DesktopProtectedCourseOrder.encryptCourseInfo(courseInfo, password)
                        ),
                        controlPoints = controlPoints
                    )
                }
            )
        )

        val result = DesktopCourseAnalysisApplier.applyCalculatedRoute(
            projectFile = projectToUpdate,
            courseInfo = sourceCourseInfo,
            application = application,
            password = password
        )

        assertEquals(2, result.affectedCategoryCount)
        listOf(CATEGORY_ID, "category-m40").forEach { categoryId ->
            val category = result.projectFile.raceData.categories.single { it.category.id == categoryId }.category
            assertEquals(
                application.idealOrderText,
                DesktopProtectedCourseOrder.decrypt(requireNotNull(category.encryptedIdealOrder), password)
            )
            assertEquals(
                application.idealOrderText,
                DesktopProtectedCourseOrder.decryptCourseInfo(
                    requireNotNull(category.encryptedCourseInfo),
                    password
                ).idealOrder
            )
        }
        val updatedSameCourseInfo = result.projectFile.raceData.categories
            .single { it.category.id == "category-m40" }
            .category.encryptedCourseInfo
            .let(::requireNotNull)
            .let { DesktopProtectedCourseOrder.decryptCourseInfo(it, password) }
        assertEquals(sameCourseInfo.lengthMeters, updatedSameCourseInfo.lengthMeters)
        assertEquals(sameCourseInfo.climbMeters, updatedSameCourseInfo.climbMeters)
        assertEquals(sameCourseInfo.route, updatedSameCourseInfo.route)

        val differentCategory = result.projectFile.raceData.categories
            .single { it.category.id == "category-w65" }
            .category
        val differentIdealOrder = DesktopProtectedCourseOrder.decrypt(
            requireNotNull(differentCategory.encryptedIdealOrder),
            password
        )
        assertFalse(differentIdealOrder == application.idealOrderText)
        assertEquals(
            differentCourseInfo.idealOrder,
            DesktopProtectedCourseOrder.decryptCourseInfo(
                requireNotNull(differentCategory.encryptedCourseInfo),
                password
            ).idealOrder
        )
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
            val expectedProtectedTokens = originalResolvedControlIds.map { controlId ->
                changedLabelsByControlId[controlId]
                    ?: projectFile.raceData.controls.single { it.id == controlId }.publicLabel
                    ?: projectFile.raceData.controls.single { it.id == controlId }.label
            }
            assertEquals(
                expectedProtectedTokens,
                org.openardf.radiooracle.shared.course.ControlPointRules.tokenizeControlPoints(decryptedIdealOrder)
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
        assertEquals("Horizontal length", summary.providedRouteSection?.comparisonLengthLabel)
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
    fun resolvesProtectedCoordinatesByMorseFoxAliasesWhenStoredControlIdsDiffer() {
        val projectFile = projectFile(
            foxCount = 3,
            publicLabels = listOf("MOE", "moi", "Mos"),
            siCodes = listOf(101, 102, 103)
        )
        val protectedInfo = protectedInfo(foxCount = 3).copy(
            controlPoints = protectedInfo(foxCount = 3).controlPoints.map { control ->
                if (control.type == ControlPointType.BEACON) {
                    control.copy(controlId = "stale-beacon", label = "M")
                } else {
                    control.copy(controlId = "stale-${control.label}", label = "Fox${control.label}")
                }
            },
            courseObjects = protectedInfo(foxCount = 3).courseObjects.map { courseObject ->
                when (courseObject.type) {
                    ProtectedCourseObjectType.CONTROL -> courseObject.copy(
                        id = "stale-object-${courseObject.label}",
                        label = "Fox${courseObject.label}"
                    )
                    ProtectedCourseObjectType.BEACON -> courseObject.copy(id = "stale-beacon", label = "M")
                    else -> courseObject
                }
            }
        )

        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = projectFile,
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo,
            protectedIdealOrderText = "MOE moi Mos Beacon"
        )

        assertTrue(summary.missingElements.none { it.contains("Location latitude/longitude is missing") })
        assertEquals(listOf("MOE", "moi", "Mos"), summary.waitRows.map { it.controlLabel })
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

        assertTrue(summary.missingElements.none { it.contains("Saved route order could not be resolved") })
        assertEquals(listOf("S", "Fox 1", "Fox 2", "B", "F"), summary.providedIdealOrder)
        assertTrue(summary.calculatedIdealOrder.none { it == "Fox 3" })
        assertEquals(listOf("Fox 1", "Fox 2"), summary.waitRows.map { it.controlLabel })
    }

    @Test
    fun routeMapShowsOnePublicLabelForControlsSharingCoordinates() {
        val baseProject = projectFile(
            foxCount = 2,
            publicLabels = listOf("Fox1", "Fox2"),
            siCodes = listOf(101, 102),
            assignControls = false
        )
        val baseProtectedInfo = protectedInfo(foxCount = 2)
        val staleControls = listOf(
            EventControl(
                id = "stale-31",
                raceId = RACE_ID,
                label = "31",
                siCode = 31,
                type = ControlPointType.CONTROL,
                latitude = baseProtectedInfo.controlPoints[0].latitude,
                longitude = baseProtectedInfo.controlPoints[0].longitude
            ),
            EventControl(
                id = "stale-32",
                raceId = RACE_ID,
                label = "32",
                siCode = 32,
                type = ControlPointType.CONTROL,
                latitude = baseProtectedInfo.controlPoints[1].latitude,
                longitude = baseProtectedInfo.controlPoints[1].longitude
            )
        )
        val projectFile = baseProject.copy(
            raceData = baseProject.raceData.copy(
                controls = baseProject.raceData.controls + staleControls
            )
        )
        val protectedInfo = baseProtectedInfo.copy(
            idealOrder = "Fox1 Fox2 Beacon",
            controlPoints = baseProtectedInfo.controlPoints.map { control ->
                when (control.controlId) {
                    "control-1" -> control.copy(label = "Fox1")
                    "control-2" -> control.copy(label = "Fox2")
                    else -> control
                }
            },
            courseObjects = baseProtectedInfo.courseObjects.map { courseObject ->
                when (courseObject.id) {
                    "control-1" -> courseObject.copy(label = "Fox1")
                    "control-2" -> courseObject.copy(label = "Fox2")
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

        val routeMapLabels = requireNotNull(summary.providedRouteSection?.routeMap).points
            .filter { it.type == DesktopCourseRouteMapPointType.Control }
            .map { it.label }
        assertEquals(1, routeMapLabels.count { it == "Fox1" })
        assertEquals(1, routeMapLabels.count { it == "Fox2" })
        assertFalse(routeMapLabels.any { it == "31" || it == "32" })
    }

    @Test
    fun foxoringRouteMapKeepsPairedSlowAndFastFoxLabelsAtDistinctLocations() {
        val baseProject = sprintProjectFile(includeSpectator = false).let { project ->
            project.copy(
                raceData = project.raceData.copy(
                    race = project.raceData.race.copy(raceType = RaceType.FOXORING),
                    controls = project.raceData.controls.map { control ->
                        when (control.id) {
                            "control-fast-1" -> control.copy(label = "1F", publicLabel = "1F")
                            "control-fast-2" -> control.copy(label = "2F", publicLabel = "2F")
                            else -> control
                        }
                    }
                )
            )
        }
        val controlsById = baseProject.raceData.controls.associateBy { it.id }
        val projectFile = baseProject.copy(
            raceData = baseProject.raceData.copy(
                categories = baseProject.raceData.categories.map { categoryData ->
                    categoryData.copy(
                        controlPoints = categoryData.controlPoints.map { controlPoint ->
                            val control = controlsById.getValue(controlPoint.controlId)
                            controlPoint.copy(siCode = control.siCode, type = control.type)
                        }
                    )
                }
            )
        )
        val baseInfo = sprintProtectedInfo(includeSpectator = false)
        val protectedInfo = baseInfo.copy(
            idealOrder = "1 1F 2 2F Beacon",
            controlPoints = baseInfo.controlPoints.map { control ->
                when (control.controlId) {
                    "control-slow-1", "control-slow-2" -> control.copy(controlId = "stale-${control.controlId}")
                    "control-fast-1" -> control.copy(label = "1F")
                    "control-fast-2" -> control.copy(label = "2F")
                    else -> control
                }
            },
            courseObjects = baseInfo.courseObjects.map { courseObject ->
                when (courseObject.id) {
                    "control-slow-1", "control-slow-2" -> courseObject.copy(id = "stale-${courseObject.id}")
                    "control-fast-1" -> courseObject.copy(label = "1F")
                    "control-fast-2" -> courseObject.copy(label = "2F")
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

        val routeMapLabels = requireNotNull(summary.providedRouteSection?.routeMap).points
            .filter { it.type == DesktopCourseRouteMapPointType.Control }
            .map { it.label }
        assertEquals(setOf("1", "1F", "2", "2F"), routeMapLabels.toSet())
        assertEquals(4, routeMapLabels.size)
    }

    @Test
    fun foxoringRouteMapKeepsCatalogFastLabelsWhenSavedAnalyzerLabelsLostTheF() {
        val baseProject = sprintProjectFile(includeSpectator = false)
        val projectFile = baseProject.copy(
            raceData = baseProject.raceData.copy(
                race = baseProject.raceData.race.copy(raceType = RaceType.FOXORING),
                controls = baseProject.raceData.controls.map { control ->
                    when (control.id) {
                        "control-fast-1" -> control.copy(label = "1F", publicLabel = "1F")
                        "control-fast-2" -> control.copy(label = "2F", publicLabel = "2F")
                        else -> control
                    }
                }
            )
        )
        val baseInfo = sprintProtectedInfo(includeSpectator = false)
        val includedIds = setOf("control-fast-1", "control-fast-2", "control-beacon")
        val protectedInfo = baseInfo.copy(
            idealOrder = "1 2 Beacon",
            sourceName = "Course Analyzer calculated route",
            controlPoints = baseInfo.controlPoints
                .filter { it.controlId in includedIds }
                .map { control ->
                    when (control.controlId) {
                        "control-fast-1" -> control.copy(label = "1")
                        "control-fast-2" -> control.copy(label = "2")
                        else -> control
                    }
                },
            courseObjects = baseInfo.courseObjects.mapNotNull { courseObject ->
                when (courseObject.id) {
                    "control-fast-1" -> courseObject.copy(label = "1")
                    "control-fast-2" -> courseObject.copy(label = "2")
                    "control-slow-1", "control-slow-2" -> null
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

        val routeMapLabels = requireNotNull(summary.providedRouteSection?.routeMap).points
            .filter { it.type == DesktopCourseRouteMapPointType.Control }
            .map { it.label }
        assertEquals(setOf("1F", "2F"), routeMapLabels.toSet())
        assertEquals(2, routeMapLabels.size)
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
        assertEquals(listOf("S", "31", "33", "35", "B", "F"), summary.providedIdealOrder)
        assertTrue(summary.calculatedIdealOrder.containsAll(listOf("31", "33", "35", "B")))
        assertFalse(summary.calculatedIdealOrder.any { it == "32" || it == "34" })
        assertTrue(routeMap.points.map { it.label }.containsAll(listOf("31", "32", "33", "34", "35", "B")))
        assertEquals(listOf("S", "31", "33", "35", "B", "F"), routeMap.routeLabels)
    }

    @Test
    fun analyzerIgnoresInvalidStoredProtectedCoordinates() {
        val baseProtectedInfo = protectedInfo(foxCount = 2)
        val protectedInfo = baseProtectedInfo.copy(
            route = listOf(
                baseProtectedInfo.route.first(),
                ProtectedCourseRoutePoint(latitude = Double.NaN, longitude = -94.99, elevationMeters = 120.0),
                baseProtectedInfo.route.last()
            ),
            controlPoints = baseProtectedInfo.controlPoints + ProtectedCourseControlPoint(
                controlId = "bad-control",
                label = "Bad",
                latitude = Double.NaN,
                longitude = -94.99,
                type = ControlPointType.CONTROL,
                elevationMeters = Double.POSITIVE_INFINITY,
                speedFactor = Double.NaN
            ),
            courseObjects = baseProtectedInfo.courseObjects + ProtectedCourseObjectPoint(
                id = "bad-object",
                label = "Bad",
                type = ProtectedCourseObjectType.WAYPOINT,
                latitude = 39.0,
                longitude = Double.POSITIVE_INFINITY,
                elevationMeters = Double.NaN,
                speedFactor = Double.NaN
            )
        )

        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = projectFile(foxCount = 2),
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo,
            protectedIdealOrderText = protectedInfo.idealOrder,
            magneticDeclinationProvider = { DesktopMagneticDeclinationResult(Double.NaN, usesExpiredCoefficients = false) }
        )

        assertTrue(summary.missingElements.any { it.contains("invalid latitude/longitude values") })
        assertNotNull(summary.providedRouteSection)
        assertTrue(summary.routeMaps.flatMap { it.points }.all { point ->
            point.xFraction.isFinite() && point.yFraction.isFinite()
        })
        assertTrue(summary.routeMaps.all { it.magneticDeclinationDegrees == null })
    }

    @Test
    fun routeMapAppliesEastDeclinationWithCorrectRotationSign() {
        val mtHoodDeclinationDegrees = 14.3
        val trueNorthSummary = DesktopCourseAnalyzer.analyze(
            projectFile = projectFile(foxCount = 3),
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo(foxCount = 3),
            protectedIdealOrderText = null
        )
        val magneticNorthSummary = DesktopCourseAnalyzer.analyze(
            projectFile = projectFile(foxCount = 3),
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo(foxCount = 3),
            protectedIdealOrderText = null,
            magneticDeclinationProvider = {
                DesktopMagneticDeclinationResult(mtHoodDeclinationDegrees, usesExpiredCoefficients = false)
            }
        )

        val trueNorthMap = requireNotNull(trueNorthSummary.routeMaps.single())
        val magneticNorthMap = requireNotNull(magneticNorthSummary.routeMaps.single())
        val trueStart = trueNorthMap.points.single { it.label == "S" && it.type == DesktopCourseRouteMapPointType.Start }
        val trueFinish = trueNorthMap.points.single { it.label == "F" }
        val magneticStart = magneticNorthMap.points.single { it.label == "S" && it.type == DesktopCourseRouteMapPointType.Start }
        val magneticFinish = magneticNorthMap.points.single { it.label == "F" }

        assertNull(trueNorthMap.magneticDeclinationDegrees)
        assertEquals(mtHoodDeclinationDegrees, magneticNorthMap.magneticDeclinationDegrees ?: 0.0, 0.001)
        assertTrue("True-north fixture should run from west to east.", trueFinish.xFraction > trueStart.xFraction)
        assertEquals("True-north fixture should have no north-south change.", trueStart.yFraction, trueFinish.yFraction, 0.001)
        assertTrue("East declination should preserve the route's west-to-east direction.", magneticFinish.xFraction > magneticStart.xFraction)
        assertTrue(
            "Positive east declination should rotate the eastward route toward the top of a magnetic-north map.",
            magneticFinish.yFraction < magneticStart.yFraction
        )

        val reportText = DesktopCourseAnalysisExports.reportText(magneticNorthSummary)
        assertTrue(reportText.contains("Orientation: Magnetic north (14.3° E declination)"))
    }

    @Test
    fun routeMapUsesExpiredMagneticDeclinationWithWarning() {
        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = projectFile(foxCount = 3),
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo(foxCount = 3),
            protectedIdealOrderText = null,
            magneticDeclinationProvider = { DesktopMagneticDeclinationResult(90.0, usesExpiredCoefficients = true) }
        )

        val routeMap = requireNotNull(summary.routeMaps.single())

        assertEquals(90.0, routeMap.magneticDeclinationDegrees ?: 0.0, 0.001)
        assertTrue(routeMap.magneticDeclinationUsesExpiredModel)
        assertTrue(summary.usesExpiredMagneticDeclinationModel)
        assertTrue(routeMap.northOrientationText().contains("expired WMM2025 coefficients"))

        val reportText = DesktopCourseAnalysisExports.reportText(summary)
        val unwrappedReportText = reportText.replace('\n', ' ')
        assertTrue(unwrappedReportText.contains("WMM2025 magnetic declination coefficients expired on December 31, 2029"))
        assertTrue(unwrappedReportText.contains("2D route depictions still use the expired model"))
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

    private fun EventProjectFile.withControlPublicLabel(controlId: String, publicLabel: String): EventProjectFile =
        copy(
            raceData = raceData.copy(
                controls = raceData.controls.map { control ->
                    if (control.id == controlId) {
                        control.copy(publicLabel = publicLabel)
                    } else {
                        control
                    }
                }
            )
        )

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

    private fun inactiveCourseMappingProjectFile(foxCount: Int): EventProjectFile {
        val projectFile = projectFile(foxCount = foxCount, assignControls = false)
        return projectFile.copy(
            raceData = projectFile.raceData.copy(
                categories = emptyList(),
                courseMappings = projectFile.raceData.categories
            )
        )
    }

    private fun EventProjectFile.withSameCourseCategory(categoryId: String, categoryName: String): EventProjectFile {
        val baseCategory = raceData.categories.first()
        val copiedCategory = baseCategory.category.copy(
            id = categoryId,
            name = categoryName,
            order = raceData.categories.maxOf { it.category.order } + 1
        )
        val copiedControlPoints = baseCategory.controlPoints.map { controlPoint ->
            controlPoint.copy(
                id = "${controlPoint.id}-$categoryId",
                categoryId = categoryId
            )
        }
        return copy(
            raceData = raceData.copy(
                categories = raceData.categories + EventCategoryData(
                    category = copiedCategory,
                    controlPoints = copiedControlPoints,
                    competitors = emptyList()
                )
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

    private fun ProtectedCourseInfo.withIntermediateRoutePoints(): ProtectedCourseInfo {
        val denseRoute = route.zipWithNext().flatMap { (from, to) ->
            listOf(
                from,
                ProtectedCourseRoutePoint(
                    latitude = (from.latitude + to.latitude) / 2.0,
                    longitude = (from.longitude + to.longitude) / 2.0,
                    elevationMeters = listOfNotNull(from.elevationMeters, to.elevationMeters)
                        .takeIf { it.size == 2 }
                        ?.average()
                )
            )
        } + route.takeLast(1)
        return copy(
            sampledPointCount = denseRoute.size,
            route = denseRoute
        )
    }

    private fun ProtectedCourseInfo.withControlDescriptions(descriptionByLabel: Map<String, String>): ProtectedCourseInfo =
        copy(
            controlPoints = controlPoints.map { controlPoint ->
                controlPoint.copy(description = descriptionByLabel[controlPoint.label])
            },
            courseObjects = courseObjects.map { courseObject ->
                courseObject.copy(description = descriptionByLabel[courseObject.label])
            }
        )

    private fun kmlLineStringCoordinateLines(kmlText: String): List<List<String>> =
        Regex("<LineString>[\\s\\S]*?<coordinates>([\\s\\S]*?)</coordinates>[\\s\\S]*?</LineString>")
            .findAll(kmlText)
            .map { match ->
                match.groupValues[1]
                    .lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toList()
            }
            .toList()

    private fun kmlLineStringPlacemarkNames(kmlText: String): List<String> =
        kmlLineStringPlacemarks(kmlText)
            .mapNotNull { placemark ->
                Regex("<name>([\\s\\S]*?)</name>")
                    .find(placemark)
                    ?.groupValues
                    ?.get(1)
            }
            .toList()

    private fun kmlLineStringPlacemarks(kmlText: String): List<String> =
        Regex("<Placemark>[\\s\\S]*?</Placemark>")
            .findAll(kmlText)
            .map { it.value }
            .filter { it.contains("<LineString>") }
            .toList()

    private fun String.placemarkNamed(name: String): String {
        val escapedName = Regex.escape(name)
        return Regex("<Placemark>[\\s\\S]*?</Placemark>")
            .findAll(this)
            .firstOrNull { Regex("<name>$escapedName</name>").containsMatchIn(it.value) }
            ?.value
            ?: error("Missing Placemark named $name")
    }

    private fun pdfRouteMapLineCommand(
        from: DesktopCourseRouteMapPoint,
        to: DesktopCourseRouteMapPoint,
        left: Double = 54.0,
        bottom: Double = 405.0,
        width: Double = 225.0,
        height: Double = 185.0
    ): String {
        fun x(point: DesktopCourseRouteMapPoint): Double = left + point.xFraction.coerceIn(0.0, 1.0) * width
        fun y(point: DesktopCourseRouteMapPoint): Double = bottom + (1.0 - point.yFraction.coerceIn(0.0, 1.0)) * height
        return "${pdfNumber(x(from))} ${pdfNumber(y(from))} m ${pdfNumber(x(to))} ${pdfNumber(y(to))} l S"
    }

    private fun pdfNumber(value: Double): String =
        String.format(Locale.US, "%.2f", value)

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
            .replace("Saved route is", "Route is")
            .replace("Calculated route is", "Route is")
            .replace("Saved route ", "Route ")
            .replace("Calculated route ", "Route ")

    private companion object {
        const val RACE_ID = "race"
        const val CATEGORY_ID = "category-m21"
    }
}
