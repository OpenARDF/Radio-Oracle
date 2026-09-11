package org.openardf.radiooracle.desktop

import org.junit.Assert.*
import org.junit.Test
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.*

class DesktopCourseBriefReportTest {
    @Test fun activeAndImportReportsUseMagneticNorthWithoutCallerConfiguration() {
        val project = courseReportFixture()
        val reports = DesktopCourseBriefReports.build(project, emptyMap()) +
            DesktopCourseBriefReports.imported(project, project.raceData.categories.map { it.category.id }.toSet(), null)
        assertEquals(4, reports.size)
        reports.forEach { report ->
            val map = requireNotNull(report.routeMap)
            assertTrue("${report.courseName} must use magnetic north", map.magneticDeclinationDegrees?.isFinite() == true)
            assertTrue(map.northOrientationText().startsWith("Magnetic north"))
            val category = project.raceData.categories.single { it.category.id == report.categoryId }.category
            val trueNorth = DesktopCourseAnalyzer.analyze(project, category.id, category.courseInfo, category.idealOrder,
                controlIdentityMode = DesktopCourseControlIdentityMode.RESULT_CONTROLS, allowFoxRenumbering = false,
                magneticDeclinationProvider = { null })
            assertTrue("The graphic must actually rotate, not just change its orientation label",
                trueNorth.routeMaps.none { it.points == map.points })
        }
    }

    @Test fun stationEditsAndMembershipEditsKeepReportsAvailableAfterSavingAndReopening() {
        var project = boundCourseReportFixture()
        project = EventProjectEditor.updateControl(project, "fox-1", "Fox1", "135", ControlPointType.CONTROL,
            true, "Fox1", "")
        project = EventProjectEditor.updateCategoryControlPoints(project, "w40", "Fox2") { "new-$it" }
        project = EventProjectFileJson.decode(EventProjectFileJson.encode(project))
        val reports = DesktopCourseBriefReports.build(project, emptyMap())
        assertEquals(2, reports.size)
        assertTrue(reports.toString(), reports.all { it.routeMap != null && it.idealOrder.isNotEmpty() && it.estimatedIdealSeconds != null })
        assertEquals(setOf("Fox2"), reports.last().routeMap!!.points.filter { it.type == DesktopCourseRouteMapPointType.Control }.map { it.label }.toSet())
        val full = project.raceData.categories.first().category.courseInfo!!
        assertEquals(135, full.appliedBindings!!.controls.single { it.controlId == "fox-1" }.siCode)
        assertEquals(39.001, CourseControlResolver.resolve(project.raceData.controls.single { it.id == "fox-1" }, listOf(full)).location!!.latitude, 0.000001)
        assertEquals(listOf(32), DesktopCourseReportCsv.rows(project).last().siControlCodes)
    }

    @Test fun staleBindingsAreRejectedAndExplicitRepairUsesCurrentIdsWithoutChangingTheSource() {
        val original = boundCourseReportFixture()
        val stale = original.copy(raceData = original.raceData.copy(
            controls = original.raceData.controls.map { if (it.id == "fox-1") it.copy(siCode = 135) else it },
            categories = original.raceData.categories.map { data -> if (data.category.id != "w40") data else data.copy(
                controlPoints = listOf(EventControlPoint("new", "w40", 32, ControlPointType.CONTROL, 1, "fox-2")), publicControlIds = listOf("fox-2")) }
        ))
        assertTrue(DesktopCourseBriefReports.build(stale, emptyMap()).all { it.notice!!.contains("unavailable") })
        assertThrows(IllegalArgumentException::class.java) { EventProjectFileJson.encode(stale) }
        val repaired = AppliedCourseEdits.reconcile(stale)
        val reopened = EventProjectFileJson.decode(EventProjectFileJson.encode(repaired))
        val reports = DesktopCourseBriefReports.build(reopened, emptyMap())
        assertTrue(reports.toString(), reports.all { it.routeMap != null && it.estimatedIdealSeconds != null })
        assertEquals(listOf("fox-1"), stale.raceData.categories.last().category.courseInfo!!.appliedBindings!!.controls.map { it.controlId })
        assertEquals(listOf("fox-2"), repaired.raceData.categories.last().category.courseInfo!!.appliedBindings!!.controls.map { it.controlId })
    }

    @Test fun membershipEditsRejectUnknownLocationsAndConflictsWithoutChangingTheRace() {
        val source = boundCourseReportFixture()
        val added = source.copy(raceData = source.raceData.copy(controls = source.raceData.controls +
            EventControl("fox-3", "race", "Fox3", 33, ControlPointType.CONTROL, publicLabel = "Fox3")))
        assertThrows(IllegalArgumentException::class.java) {
            EventProjectEditor.updateCategoryControlPoints(added, "w40", "Fox3") { "new-$it" }
        }
        val other = source.raceData.courseMappings.single()
        val info = other.category.courseInfo!!
        val moved = info.copy(controlPoints = info.controlPoints.map { it.copy(latitude = it.latitude + 0.01) },
            courseObjects = info.courseObjects.map { it.copy(latitude = it.latitude + 0.01) })
        val rebound = CourseDesignBindings.prepare(moved, source.raceData.controls,
            info.appliedBindings!!.controls.associate { it.placementId to it.controlId }, info.appliedBindings!!.orderedPlacementIds, "moved")
        val conflicting = source.copy(raceData = source.raceData.copy(courseMappings = listOf(other.copy(category = other.category.copy(courseInfo = rebound)))))
        assertThrows(IllegalArgumentException::class.java) {
            EventProjectEditor.updateCategoryControlPoints(conflicting, "w40", "Fox2") { "new-$it" }
        }
        assertEquals(listOf("fox-1"), source.raceData.categories.last().controlPoints.map { it.controlId })
    }

    @Test fun reportsEveryActiveCourseAndExcludesInactiveImports() {
        val project = courseReportFixture()
        val reports = DesktopCourseBriefReports.build(project, emptyMap())
        assertEquals(listOf("M21", "W40"), reports.map { it.courseName })
        assertEquals(listOf(2, 1), reports.map { it.routeMap!!.points.count { p -> p.type == DesktopCourseRouteMapPointType.Control } })
        assertTrue(reports.all { it.horizontalLengthMeters!! > 0 && it.estimatedIdealSeconds!! > 0 })
        assertTrue(DesktopCourseBriefReports.build(project.copy(raceData = project.raceData.copy(categories = emptyList())), emptyMap()).isEmpty())
    }

    @Test fun anIncompleteCourseDoesNotPreventReportingTheOtherActiveCourse() {
        val project = courseReportFixture()
        val reports = DesktopCourseBriefReports.build(project, mapOf("m21" to ProtectedCourseInfo()))
        assertEquals(2, reports.size)
        assertNull(reports.first().routeMap)
        assertNotNull(reports.first().notice)
        assertNotNull(reports.last().routeMap)
    }

    @Test fun metricsOrderAndGraphicComeFromTheSameAnalyzerSectionWithoutRenumbering() {
        val project = courseReportFixture()
        val category = project.raceData.categories.first().category
        val analysis = DesktopCourseAnalyzer.analyze(project, category.id, category.courseInfo, category.idealOrder,
            controlIdentityMode = DesktopCourseControlIdentityMode.RESULT_CONTROLS, allowFoxRenumbering = false)
        val section = analysis.calculatedRouteSection?.takeUnless { it.summaryOnly } ?: analysis.providedRouteSection!!
        val report = DesktopCourseBriefReports.build(project, emptyMap()).first()
        assertEquals(section.routeLengthMeters, report.horizontalLengthMeters)
        assertEquals(section.climbMeters, report.climbMeters)
        assertEquals(section.effectiveLengthMeters, report.effectiveLengthMeters)
        assertEquals(section.estimatedIdealSeconds, report.estimatedIdealSeconds)
        assertEquals(section.routeOrder, report.idealOrder)
        assertEquals(section.routeMap!!.copy(title = "Ideal order"), report.routeMap)
        assertEquals(setOf("Fox1", "Fox2"), report.routeMap!!.points.filter { it.type == DesktopCourseRouteMapPointType.Control }.map { it.label }.toSet())
        assertEquals(report.idealOrder, report.routeMap.routeLabels)
    }

    @Test fun lockedAndMissingCoursesRemainListedAndUnlockRefreshesTheReport() {
        val base = courseReportFixture()
        val info = base.raceData.categories.first().category.courseInfo!!
        val project = base.copy(raceData = base.raceData.copy(categories = base.raceData.categories.mapIndexed { i, data ->
            data.copy(category = data.category.copy(courseInfo = null, idealOrder = null,
                encryptedCourseInfo = if (i == 0) "locked-test-payload" else null))
        }))
        val locked = DesktopCourseBriefReports.build(project, emptyMap())
        assertEquals(2, locked.size)
        assertTrue(locked.first().isLocked)
        assertTrue(locked.all { it.routeMap == null && it.idealOrder.isEmpty() && it.estimatedIdealSeconds == null })
        val unlocked = DesktopCourseBriefReports.build(project, mapOf("m21" to info))
        assertFalse(unlocked.first().isLocked)
        assertNotNull(unlocked.first().routeMap)
    }

    @Test fun usesTheCalculatedIdealRouteWhenTheStoredRouteTakesADetour() {
        val base = courseReportFixture()
        val data = base.raceData.categories.first()
        val info = data.category.courseInfo!!
        val reversed = info.copy(idealOrder = "Fox2 Fox1",
            route = listOf(info.route.first(), info.route[2], info.route[1], info.route.last()))
        val project = base.copy(raceData = base.raceData.copy(categories = listOf(
            data.copy(category = data.category.copy(courseInfo = reversed))
        )))
        val analysis = DesktopCourseAnalyzer.analyze(project, data.category.id, reversed, null,
            controlIdentityMode = DesktopCourseControlIdentityMode.RESULT_CONTROLS, allowFoxRenumbering = false)
        val calculated = analysis.calculatedRouteSection!!
        assertFalse(calculated.summaryOnly)
        val report = DesktopCourseBriefReports.build(project, emptyMap()).single()
        assertEquals(calculated.routeOrder, report.idealOrder)
        assertNotEquals(analysis.providedRouteSection!!.routeOrder, report.idealOrder)
        assertEquals(calculated.effectiveLengthMeters, report.effectiveLengthMeters)
        assertEquals(calculated.routeMap!!.copy(title = "Ideal order"), report.routeMap)
        assertEquals(report.idealOrder, report.routeMap!!.routeLabels)
    }

    @Test fun reportingDoesNotChangeTheLegacyCsvOrRaceData() {
        val project = courseReportFixture()
        val before = DesktopCourseReportCsv.generate(project)
        DesktopCourseBriefReports.build(project, emptyMap())
        assertEquals(before, DesktopCourseReportCsv.generate(project))
        assertEquals("Course,km,m,C1,C2", before.lineSequence().first())
        assertEquals(listOf("31", "32"), DesktopCourseReportCsv.rows(project).first().siControlCodes.map(Int::toString))
        assertEquals("Fox1 Fox2", project.raceData.categories.first().category.courseInfo!!.idealOrder)
    }

    @Test fun incompleteElevationIsNotReportedAsZeroClimb() {
        val project = courseReportFixture()
        val infos = project.raceData.categories.associate { data -> data.category.id to data.category.courseInfo!!.let { info ->
            info.copy(route = info.route.map { it.copy(elevationMeters = null) },
                controlPoints = info.controlPoints.map { it.copy(elevationMeters = null) },
                courseObjects = info.courseObjects.map { it.copy(elevationMeters = null) })
        } }
        val report = DesktopCourseBriefReports.build(project, infos).first()
        assertNull(report.climbMeters)
        assertNull(report.effectiveLengthMeters)
        assertNotNull(report.horizontalLengthMeters)
        assertTrue(report.notice!!.contains("Elevation"))
    }
}

internal fun boundCourseReportFixture(): EventProjectFile {
    val source = courseReportFixture()
    fun bind(data: EventCategoryData): EventCategoryData {
        val info = data.category.courseInfo!!
        return data.copy(category = data.category.copy(courseInfo = CourseDesignBindings.prepare(info, source.raceData.controls,
            info.controlPoints.associate { it.controlId to it.controlId }, info.courseObjects.map { it.id }, "fixture")))
    }
    return source.copy(raceData = source.raceData.copy(categories = source.raceData.categories.map(::bind),
        courseMappings = source.raceData.courseMappings.map(::bind)))
}

internal fun courseReportFixture(): EventProjectFile {
    var project = EventProjectFactory.createEmptyProject("race", "Course report", "2026-09-11T09:00")
    project = project.copy(raceData = project.raceData.copy(race = project.raceData.race.copy(raceType = RaceType.FOXORING)))
    project = EventProjectEditor.addCategory(project, "m21", "M21")
    project = EventProjectEditor.addCategory(project, "w40", "W40")
    val controls = (1..2).map { EventControl("fox-$it", "race", "Fox$it", 30 + it, ControlPointType.CONTROL, publicLabel = "Fox$it") }
    val categories = project.raceData.categories.mapIndexed { index, data ->
        val points = controls.take(2 - index).mapIndexed { i, control ->
            ProtectedCourseControlPoint(control.id, control.label, 39.0 + (i + 1) * 0.001, -95.001,
                elevationMeters = 110.0 + i * 10)
        }
        val objects = listOf(ProtectedCourseObjectPoint("start", "Start", ProtectedCourseObjectType.START, 39.0, -95.0, 100.0)) +
            points.map { ProtectedCourseObjectPoint(it.controlId, it.label, ProtectedCourseObjectType.CONTROL, it.latitude, it.longitude, it.elevationMeters) } +
            ProtectedCourseObjectPoint("finish", "Finish", ProtectedCourseObjectType.FINISH, 39.003, -95.003, 130.0)
        val info = ProtectedCourseInfo(idealOrder = points.joinToString(" ") { it.label }, controlPoints = points,
            courseObjects = objects, route = objects.map { ProtectedCourseRoutePoint(it.latitude, it.longitude, it.elevationMeters) })
        data.copy(category = data.category.copy(courseInfo = info, lengthMeters = 500 - index * 100, climbMeters = 30),
            controlPoints = points.mapIndexed { i, point -> EventControlPoint("${data.category.id}-$i", data.category.id,
                31 + i, ControlPointType.CONTROL, i, point.controlId) })
    }
    return project.copy(raceData = project.raceData.copy(controls = controls, categories = categories,
        courseMappings = listOf(categories.first().copy(category = categories.first().category.copy(id = "inactive", name = "M70")))))
}
