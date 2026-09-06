package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.domain.ControlPointType
import kotlin.test.*

class CourseControlResolverTest {
    private val controls = (1..5).map { EventControl("race-$it", "race", "Fox$it", 130 + it, ControlPointType.CONTROL) }

    @Test
    fun everyNumberingPermutationAndSubsetKeepsRecordedFieldLocations() {
        var checked = 0
        permutations((1..5).toList()).forEach { labels ->
            val info = ProtectedCourseInfo(sourceName = "Course Analyzer calculated route",
                controlPoints = labels.mapIndexed { index, number ->
                    ProtectedCourseControlPoint("placement-$index", "Fox$number", 40.0 + index * 0.001, -75.0)
                }, resultControlLabelsById = labels.indices.associate { "placement-$it" to "Fox${it + 1}" })
            for (mask in 1..31) {
                controls.forEachIndexed { index, control ->
                    if (mask and (1 shl index) != 0) {
                        val resolved = CourseControlResolver.resolve(control, listOf(info))
                        assertEquals(CourseResolutionStatus.RESOLVED, resolved.status)
                        assertEquals(40.0 + index * 0.001, resolved.location?.latitude)
                    }
                }
                checked++
            }
        }
        assertEquals(3720, checked)
    }

    @Test
    fun explicitBindingsPreserveEveryNumberingPermutationAndCategorySubset() {
        var checked = 0
        permutations((1..5).toList()).forEach { numbering ->
            for (mask in 1..31) {
                val placements = numbering.indices.filter { mask and (1 shl it) != 0 }
                val info = ProtectedCourseInfo(controlPoints = placements.map { index ->
                    ProtectedCourseControlPoint("opaque-placement-$index", "Fox${numbering[index]}", 40.0 + index * 0.001, -75.0)
                })
                val applied = CourseDesignBindings.prepare(info, controls,
                    placements.associate { "opaque-placement-$it" to "race-${numbering[it]}" },
                    placements.reversed().map { "opaque-placement-$it" }, "fixture-revision")
                placements.forEach { index ->
                    val resolution = CourseControlResolver.resolve(controls[numbering[index] - 1], listOf(applied))
                    assertEquals(CourseResolutionStatus.RESOLVED, resolution.status)
                    assertEquals(40.0 + index * 0.001, resolution.location?.latitude)
                }
                checked++
            }
        }
        assertEquals(3720, checked)
    }

    @Test
    fun importedIdCollisionIsReportedWithoutChoosingAnIdentity() {
        val oldControl = controls[2].copy(id = "control-3-33-control")
        val info = ProtectedCourseInfo(sourceName = "import.kml", controlPoints = listOf(
            ProtectedCourseControlPoint(oldControl.id, "Fox5", 40.0, -75.0)))
        assertEquals(CourseResolutionStatus.AMBIGUOUS, CourseControlResolver.resolve(oldControl, listOf(info)).status)
        assertEquals(40.0, CourseControlResolver.resolve(controls[4], listOf(info)).location?.latitude)
    }

    @Test
    fun importedLabelsSupersedeHistoricalNumbersButAnalyzerLabelsDoNot() {
        val fox5 = controls.last()
        val point = ProtectedCourseControlPoint("control-3-33-control", "Fox5", 41.0, -76.0)
        val imported = ProtectedCourseInfo(sourceName = "course.kml", controlPoints = listOf(point))
        assertEquals(41.0, CourseControlResolver.resolve(fox5, listOf(imported)).location?.latitude)
        assertEquals(CourseResolutionStatus.MISSING,
            CourseControlResolver.resolve(fox5, listOf(imported.copy(sourceName = "Course Analyzer saved route"))).status)
    }

    @Test
    fun conflictingExactLocationsNeverFallThroughToLabels() {
        val point = ProtectedCourseControlPoint(controls.first().id, "Fox1", 40.0, -75.0)
        val info = ProtectedCourseInfo(controlPoints = listOf(point, point.copy(latitude = 41.0)))
        assertEquals(CourseResolutionStatus.AMBIGUOUS, CourseControlResolver.resolve(controls.first(), listOf(info)).status)
        assertEquals(CourseResolutionStatus.INVALID, CourseControlResolver.resolve(controls.first(),
            listOf(info.copy(controlPoints = listOf(point.copy(latitude = Double.NaN))))).status)
    }

    @Test
    fun exactIdAndRecordedIdentityMustAgree() {
        val point = ProtectedCourseControlPoint(controls.first().id, "Fox5", 40.0, -75.0)
        val source = ProtectedCourseInfo(sourceName = "Course Analyzer", controlPoints = listOf(point),
            resultControlLabelsById = mapOf(point.controlId to "Fox2"))
        assertEquals(CourseResolutionStatus.AMBIGUOUS, CourseControlResolver.resolve(controls.first(), listOf(source)).status)
        val competing = source.copy(controlPoints = listOf(point, point.copy(controlId = "another", latitude = 41.0)),
            resultControlLabelsById = mapOf(point.controlId to "Fox1", "another" to "Fox1"))
        assertEquals(CourseResolutionStatus.AMBIGUOUS, CourseControlResolver.resolve(controls.first(), listOf(competing)).status)
    }

    @Test
    fun duplicateRepresentationsAndMissingElevationAreCompatible() {
        val point = ProtectedCourseControlPoint(controls.first().id, "Fox1", 40.0, -75.0)
        val info = ProtectedCourseInfo(controlPoints = listOf(point), courseObjects = listOf(
            ProtectedCourseObjectPoint(point.controlId, point.label, ProtectedCourseObjectType.CONTROL,
                point.latitude, point.longitude, 123.0)))
        assertEquals(123.0, CourseControlResolver.resolve(controls.first(), listOf(info)).location?.elevationMeters)
    }

    @Test
    fun sprintAndFoxoringPairsAreNotMerged() {
        assertNotEquals("1".resultControlLabelKey(ControlPointType.CONTROL), "1F".resultControlLabelKey(ControlPointType.CONTROL))
        assertEquals("F1".resultControlLabelKey(ControlPointType.CONTROL), "1F".resultControlLabelKey(ControlPointType.CONTROL))
        assertNotEquals("control-1-31-control".stableResultControlIdentity(ControlPointType.CONTROL),
            "control-1f-41-control".stableResultControlIdentity(ControlPointType.CONTROL))
    }

    private fun permutations(values: List<Int>): List<List<Int>> = if (values.isEmpty()) listOf(emptyList())
        else values.flatMap { first -> permutations(values - first).map { listOf(first) + it } }
}
