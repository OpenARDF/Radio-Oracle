package org.openardf.radiooracle.desktop

import org.junit.Assert.*
import org.junit.Test
import org.openardf.radiooracle.shared.event.*
import org.openardf.radiooracle.shared.files.*

class DesktopIofCourseAnalysisTest {
    @Test fun importsPositionsClassAssignmentsAndLegsIntoOneReviewedCourseWithoutChangingRace() {
        val original = project()
        val before = EventProjectFileJson.encode(original)
        val transaction = DesktopCourseImportTransaction.prepare(original)
        val parsed = IofXmlImports.validatedCourseData(xml(), IofXmlSchemaResource.loadBundledSchema(), original.raceData.race)
        assertEquals(listOf("W21", "M21"), parsed.parsedData.categories.map { it.category.name })
        val temporary = EventProjectEditor.importIofCourseData(transaction.baseProject, parsed.parsedData).projectFile
        val ids = temporary.raceData.courseMappings.map { it.category.id }.toSet()
        val result = DesktopIofCourseAnalysis.prepare(temporary, ids, null, elevationLookup = { 100.0 })
        assertEquals(before, EventProjectFileJson.encode(original)) // Reject is simply discarding result.
        assertEquals(1, result.reports.size)
        val report = result.reports.single()
        assertTrue(report.courseName.contains("W21"))
        assertTrue(report.courseName.contains("M21"))
        assertEquals(0, report.climbMeters)
        assertEquals(report.horizontalLengthMeters, report.effectiveLengthMeters)
        assertTrue(report.legWarnings.single().contains("keep-out"))
        val applied = EventProjectFileJson.decode(EventProjectFileJson.encode(transaction.applyTo(original) { result.project }))
        assertTrue(applied.raceData.categories.isEmpty())
        applied.raceData.courseMappings.forEach { data ->
            val info = data.category.courseInfo!!
            assertEquals(1500.0, info.suppliedLegLengths.single().lengthMeters, 0.0)
            assertEquals(report.horizontalLengthMeters, info.lengthMeters)
            assertNull(CourseDesignBindings.validationError(info))
            assertFalse(info.idealOrder.isBlank())
        }
    }

    @Test fun suppliedDistanceChangesOptimalOrderAndIsRetainedEvenWhenThatLegIsNotChosen() {
        fun analyze(input: String): DesktopIofCourseAnalysis.Result {
            val original = project()
            val imported = EventProjectEditor.importIofCourseData(original, IofXmlImports.courseData(input, original.raceData.race).parsedData).projectFile
            return DesktopIofCourseAnalysis.prepare(imported, imported.raceData.courseMappings.map { it.category.id }.toSet(), null,
                elevationLookup = { null })
        }
        val direct = analyze(xml().replace("<LegLength>1500</LegLength>", ""))
        val detour = analyze(xml())
        assertNotEquals(direct.reports.single().idealOrder, detour.reports.single().idealOrder)
        assertNull(detour.reports.single().climbMeters)
        assertNull(detour.reports.single().effectiveLengthMeters)
        assertTrue(detour.reports.single().notice!!.contains("horizontal length"))
        assertEquals(1500.0, detour.project.raceData.courseMappings.first().category.courseInfo!!.suppliedLegLengths.single().lengthMeters, 0.0)
    }

    @Test fun missingCoordinatesCanBeAcceptedWithoutInventingAnOrderOrClimb() {
        val original = project()
        val imported = EventProjectEditor.importIofCourseData(original,
            IofXmlImports.courseData(xml().replace(Regex("<Position[^>]*/>"), ""), original.raceData.race).parsedData).projectFile
        val result = DesktopIofCourseAnalysis.prepare(imported, imported.raceData.courseMappings.map { it.category.id }.toSet(), null)
        assertNull(result.reports.single().horizontalLengthMeters)
        assertNull(result.reports.single().climbMeters)
        assertTrue(result.reports.single().idealOrder.isEmpty())
        assertTrue(result.reports.single().legWarnings.single().contains("comparison unavailable"))
        val applied = DesktopCourseImportTransaction.prepare(original).applyTo(original) { result.project }
        assertEquals(2, applied.raceData.courseMappings.first().controlPoints.size)
    }

    @Test fun incompleteCoverageUsesOneHorizontalObjectiveForAllPermutations() {
        val original = project()
        val imported = EventProjectEditor.importIofCourseData(original, IofXmlImports.courseData(xml(), original.raceData.race).parsedData).projectFile
        val ids = imported.raceData.courseMappings.map { it.category.id }.toSet()
        val result = DesktopIofCourseAnalysis.prepare(imported, ids, null,
            elevationLookup = { point -> if (point.longitude > -78.998) null else 100.0 })
        assertTrue(result.reports.single().notice!!.contains("Route selected by horizontal length"))
    }

    @Test fun elevationCanChangeTheWinnerFromTheHorizontallyShortestRoute() {
        val original = project()
        val input = xml().replace("<LegLength>1500</LegLength>", "")
            .replace("lat=\"35.0\" lng=\"-78.999\"", "lat=\"35.001\" lng=\"-78.999\"")
        val imported = EventProjectEditor.importIofCourseData(original, IofXmlImports.courseData(input, original.raceData.race).parsedData).projectFile
        val ids = imported.raceData.courseMappings.map { it.category.id }.toSet()
        val horizontal = DesktopIofCourseAnalysis.prepare(imported, ids, null, elevationLookup = { null })
        val effective = DesktopIofCourseAnalysis.prepare(imported, ids, null,
            elevationLookup = { if (it.longitude < -78.9995 && it.latitude > 35.0001) 200.0 else 0.0 })
        assertNotEquals(horizontal.reports.single().idealOrder, effective.reports.single().idealOrder)
        assertTrue(effective.reports.single().horizontalLengthMeters!! > horizontal.reports.single().horizontalLengthMeters!!)
        assertTrue(effective.reports.single().notice!!.contains("Route selected by effective length"))
    }

    @Test fun invalidLegLengthsAreRejected() {
        for (value in listOf("-1", "NaN", "INF")) {
            assertThrows(IllegalArgumentException::class.java) { IofXmlImports.courseData(xml().replace("1500", value), project().raceData.race) }
        }
    }

    @Test fun encryptedAcceptanceProtectsNewGeometryAndRetainsLegLengths() {
        val original = project()
        val imported = EventProjectEditor.importIofCourseData(original, IofXmlImports.courseData(xml(), original.raceData.race).parsedData).projectFile
        val result = DesktopIofCourseAnalysis.prepare(imported, imported.raceData.courseMappings.map { it.category.id }.toSet(), "fixture-password", elevationLookup = { 100.0 })
        val data = result.project.raceData.courseMappings.first().category
        assertNull(data.courseInfo)
        assertNotNull(data.encryptedCourseInfo)
        assertEquals(1500.0, data.storedCourseInfo("fixture-password")!!.suppliedLegLengths.single().lengthMeters, 0.0)
    }

    @Test fun downloadedCandidateElevationsAreUsedAndSaved() = kotlinx.coroutines.runBlocking {
        val original = project()
        val imported = EventProjectEditor.importIofCourseData(original, IofXmlImports.courseData(xml(), original.raceData.race).parsedData).projectFile
        val ids = imported.raceData.courseMappings.map { it.category.id }.toSet()
        var requests = 0
        val lookup = DesktopIofCourseAnalysis.fetchCandidateElevations(imported, ids, null,
            provider = { points -> requests++; points.map { 100.0 + (it.longitude + 79) * 10000 } }, local = { null })
        assertEquals(1, requests)
        val result = DesktopIofCourseAnalysis.prepare(imported, ids, null, elevationLookup = lookup)
        assertTrue(result.reports.single().notice!!.contains("Route selected by effective length"))
        assertTrue(result.reports.single().climbMeters!! > 0)
        assertTrue(result.project.raceData.courseMappings.first().category.courseInfo!!.route.all { it.elevationMeters != null })
    }

    private fun project() = EventProjectFactory.createEmptyProject("race", "Import test", "2026-09-14T09:00")

    internal fun xml() = """<CourseData xmlns="http://www.orienteering.org/datastandard/3.0" iofVersion="3.0"><Event><Name>Import test</Name></Event><RaceCourseData>
        <Control type="Start"><Id>S</Id><Position lat="35.0" lng="-79.0"/></Control>
        <Control><Id>31</Id><Position lat="35.0" lng="-78.999"/></Control>
        <Control><Id>32</Id><Position lat="35.0" lng="-78.998"/></Control>
        <Control type="Finish"><Id>F</Id><Position lat="35.0" lng="-78.997"/></Control>
        <Course><Name>Shared course</Name>
            <CourseControl type="Start"><Control>S</Control></CourseControl>
            <CourseControl><Control>31</Control><LegLength>1500</LegLength></CourseControl>
            <CourseControl><Control>32</Control></CourseControl>
            <CourseControl type="Finish"><Control>F</Control></CourseControl>
        </Course>
        <ClassCourseAssignment><ClassName>W21</ClassName><CourseName>Shared course</CourseName></ClassCourseAssignment>
        <ClassCourseAssignment><ClassName>M21</ClassName><CourseName>Shared course</CourseName></ClassCourseAssignment>
        </RaceCourseData></CourseData>"""
}
