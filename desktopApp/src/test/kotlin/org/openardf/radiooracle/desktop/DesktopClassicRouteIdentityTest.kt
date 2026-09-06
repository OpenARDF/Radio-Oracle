package org.openardf.radiooracle.desktop

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.openardf.radiooracle.shared.domain.*
import org.openardf.radiooracle.shared.event.*
import org.openardf.radiooracle.shared.files.HtmlResultExports

class DesktopClassicRouteIdentityTest {
    private val flat = DesktopFrozenElevationSurface(listOf(RouteElevationSource("flat", "Identity test", 1.0))) { 100.0 }
    private val order = listOf(1, 4, 5, 2, 3)
    private val renamed = mapOf(1 to 5, 4 to 3, 5 to 1, 2 to 2, 3 to 4)

    @Test fun everyClassicSubsetUsesFieldIdentitiesForHeadingPunchesAndMissingPunches() = runBlocking {
        for (mask in 1..31) {
            val assigned = order.filter { mask and (1 shl (it - 1)) != 0 }
            for (visited in listOf(assigned, assigned.reversed(), assigned.drop(1), assigned + assigned.first())) {
                val canonical = calculated(fixture(assigned, visited, renumber = false))
                val legacy = calculated(fixture(assigned, visited, renumber = true))
                val explicit = calculated(fixture(assigned, visited, renumber = true, opaqueIds = true))
                val expected = DesktopClassicRouteAnalysis.projection(canonical).getValue("result")
                assertEquals(assigned.joinToString("-") { "Fox$it" }, expected.idealRoute)
                assertEquals("subset=$assigned, punches=$visited", expected, DesktopClassicRouteAnalysis.projection(legacy).getValue("result"))
                assertEquals(expected, DesktopClassicRouteAnalysis.projection(explicit).getValue("result"))
                assertEquals(visited.toSet() != assigned.toSet(), expected.missingAssignedPunches)
                if (visited == assigned) assertEquals(expected.idealEffectiveMeters, expected.effectiveMeters)
                assertEquals(legacy.desktopRouteAnalysis, EventProjectFileJson.decode(EventProjectFileJson.encode(legacy)).desktopRouteAnalysis)
            }
        }
    }

    @Test fun ordinaryImportsUseImportedLabelsRatherThanHistoricalIdNumbers() = runBlocking {
        val imported = fixture(order, order, renumber = true).mapInfo { it.copy(sourceName = "new-field-course.kml") }
        val first = imported.raceData.controls.single { it.label == "Fox5" }
        assertEquals(40.001, DesktopClassicRouteAnalysis.resolve(first, null, infos(imported).values.toList()).latitude, 0.0000001)
        assertNotEquals("Fox1-Fox4-Fox5-Fox2-Fox3", DesktopClassicRouteAnalysis.projection(calculated(imported)).getValue("result").idealRoute)
    }

    @Test fun ambiguousLegacyIdentitiesNeverFallBackToRenumberedLabels() = runBlocking {
        val ambiguous = fixture(order, order, renumber = true).mapInfo { info ->
            info.copy(controlPoints = info.controlPoints + info.controlPoints.first().copy(latitude = 41.0))
        }
        assertTrue(DesktopClassicRouteAnalysis.projection(calculated(ambiguous)).isEmpty())
        val opaque = fixture(order, order, renumber = true, opaqueIds = true).mapInfo { it.copy(resultControlLabelsById = emptyMap()) }
        val missing = calculated(opaque)
        assertTrue(DesktopClassicRouteAnalysis.projection(missing).isEmpty())
        assertTrue(missing.desktopRouteAnalysis!!.results.getValue("result").unavailableReason!!.contains("No location"))
    }

    @Test fun oldIdentityMethodIsStaleAndCannotLeakThroughResultsExports() = runBlocking {
        val good = calculated(fixture(order, order, renumber = true))
        val old = good.copy(desktopRouteAnalysis = good.desktopRouteAnalysis!!.let { metadata ->
            metadata.copy(contexts = metadata.contexts.mapValues { (_, c) -> c.copy(method = "classic-straight-25m-median50-prominence2-rounded-components-v1") })
        })
        assertTrue(DesktopClassicRouteAnalysis.projection(old).isEmpty())
        assertTrue(DesktopClassicRouteAnalysis.needsMethodRefresh(old))
        assertFalse(DesktopClassicRouteAnalysis.needsMethodRefresh(good))
        assertEquals("Stale", DesktopClassicRouteAnalysis.status(old, "result"))
        assertFalse(HtmlResultExports.results(old.raceData, routeLengths = DesktopClassicRouteAnalysis.projection(old)).contains(ResultRouteLength.LABEL))
        assertEquals(DesktopClassicRouteAnalysis.projection(good), DesktopClassicRouteAnalysis.projection(calculated(old)))
    }

    @Test fun fieldLabelsAreCapturedOnceAndRoundTripWithoutGuessingLegacyProvenance() {
        val plain = fixture(order, order, renumber = false, opaqueIds = true).raceData.categories.single().category.courseInfo!!
            .copy(resultControlLabelsById = emptyMap())
        val captured = plain.withResultControlLabels()
        assertEquals("Fox1", captured.resultControlLabelsById.getValue("opaque-1"))
        val renamedInfo = captured.copy(sourceName = "Course Analyzer fox renumbering", controlPoints = captured.controlPoints.map { it.copy(label = "Changed") })
        assertEquals(captured.resultControlLabelsById, renamedInfo.withResultControlLabels().resultControlLabelsById)
        assertTrue(plain.copy(sourceName = "Course Analyzer fox renumbering").withResultControlLabels().resultControlLabelsById.isEmpty())
        assertEquals("Fox 1".resultControlLabelKey(ControlPointType.CONTROL), "31".resultControlLabelKey(ControlPointType.CONTROL))
        assertNotEquals("1F".resultControlLabelKey(ControlPointType.CONTROL), "1".resultControlLabelKey(ControlPointType.CONTROL))
        val fieldControl = EventControl("opaque-1", "race", "Field label", 221, ControlPointType.CONTROL)
        assertEquals("Field label", plain.withResultControlLabels(listOf(fieldControl)).resultControlLabelsById.getValue("opaque-1"))
    }

    private fun EventProjectFile.mapInfo(transform: (ProtectedCourseInfo) -> ProtectedCourseInfo) = copy(raceData = raceData.copy(
        categories = raceData.categories.map { it.copy(category = it.category.copy(courseInfo = transform(it.category.courseInfo!!))) }
    ))
    private fun infos(p: EventProjectFile) = p.raceData.categories.associate { it.category.id to it.category.courseInfo!! }
    private suspend fun calculated(p: EventProjectFile) = p.copy(desktopRouteAnalysis = DesktopClassicRouteAnalysis.calculate(p, infos(p), flat))

    private fun fixture(assigned: List<Int>, visited: List<Int>, renumber: Boolean, opaqueIds: Boolean = false): EventProjectFile {
        val base = DesktopClassicRouteAnalysisTest().fixture(visited.map { 220 + it } + 136)
        val controls = (1..5).map { EventControl("control-fox$it-${220 + it}-control", "race", "Fox$it", 220 + it, ControlPointType.CONTROL) } +
            EventControl("control-b-136-beacon", "race", "B", 136, ControlPointType.BEACON)
        fun id(n: Int) = if (opaqueIds) "opaque-$n" else "control-$n-${30 + n}-control"
        val points = order.mapIndexed { i, n -> ProtectedCourseControlPoint(id(n), "Fox${if (renumber) renamed.getValue(n) else n}", 40.001 + i * 0.001, -75.0) } +
            ProtectedCourseControlPoint("control-b-136-beacon", "B", 40.006, -75.0, ControlPointType.BEACON)
        val info = base.raceData.categories.single().category.courseInfo!!.copy(
            sourceName = if (renumber) "Course Analyzer fox renumbering" else "original.kml",
            controlPoints = points,
            courseObjects = listOf(ProtectedCourseObjectPoint("start", "Start", ProtectedCourseObjectType.START, 40.0, -75.0),
                ProtectedCourseObjectPoint("finish", "Finish", ProtectedCourseObjectType.FINISH, 40.007, -75.0)),
            resultControlLabelsById = if (opaqueIds) (1..5).associate { id(it) to "Fox$it" } else emptyMap()
        )
        val category = base.raceData.categories.single().let { c -> c.copy(category = c.category.copy(courseInfo = info),
            controlPoints = controls.filter { it.type == ControlPointType.BEACON || it.siCode - 220 in assigned }.mapIndexed { i, control ->
                EventControlPoint("cp$i", c.category.id, control.siCode, control.type, i, control.id)
            }) }
        return base.copy(raceData = base.raceData.copy(controls = controls, categories = listOf(category)))
    }
}
