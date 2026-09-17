package org.openardf.radiooracle.desktop

import org.junit.Assert.*
import org.junit.Test
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.event.*
import org.openardf.radiooracle.shared.files.*

class DesktopIofCourseAnalysisTest {
    @Test fun optionalCondesRouteBendsStayOffByDefaultAndNeverBecomeScoredStationsWhenSelected() {
        val original = project()
        val input = xml().replace("<Control><Id>32</Id>", "<Control><Id>900</Id><Position lat=\"35.0001\" lng=\"-78.9985\"/></Control><Control><Id>32</Id>")
            .replace("<CourseControl><Control>32</Control>", "<CourseControl><Control>900</Control></CourseControl><CourseControl><Control>32</Control>")
            .replace("<LegLength>1500</LegLength>", "")
        val parsed = IofXmlImports.validatedCourseData(input, IofXmlSchemaResource.loadBundledSchema(), original.raceData.race).parsedData
        assertTrue(parsed.categories.all { it.controlPoints.any { cp -> cp.siCode == 900 } })
        val bends = parsed.withCondesRouteBends()
        assertTrue(bends.categories.all { it.controlPoints.none { cp -> cp.siCode == 900 } })
        val imported = EventProjectEditor.importIofCourseData(original, bends).projectFile
        val prepared = DesktopIofCourseAnalysis.prepare(imported, imported.raceData.categories.map { it.category.id }.toSet(), null, elevationLookup = { 100.0 }).project
        assertFalse(prepared.raceData.controls.any { it.siCode == 900 })
        val info = prepared.raceData.categories.first().category.courseInfo!!
        assertEquals(1, info.courseObjects.count { it.type == ProtectedCourseObjectType.WAYPOINT })
        assertTrue(info.route.any { it.latitude == 35.0001 && it.longitude == -78.9985 })
        assertFalse(info.idealOrder.contains("900"))
        assertEquals(1, DesktopMandatoryCourseLegs.from(info).size)
        val summary = DesktopCourseAnalyzer.analyze(prepared, prepared.raceData.categories.first().category.id, info, info.idealOrder,
            elevationLookup = { 100.0 }, allowFoxRenumbering = false)
        assertTrue(summary.kmlFolders.any { folder -> folder.courseObjects.any { it.type == DesktopCourseKmlExportPointType.WAYPOINT } })
        EventControlCatalog.requireCanonical(EventProjectFileJson.decode(EventProjectFileJson.encode(prepared)))
        val missing = IofXmlImports.courseData(input.replace("<Position lat=\"35.0001\" lng=\"-78.9985\"/>", ""), original.raceData.race).parsedData
        assertThrows(IllegalArgumentException::class.java) { missing.withCondesRouteBends() }
        val special = IofXmlImports.courseData(input.replace("<Id>900</Id>", "<Id>900</Id><Name>Beacon</Name>"), original.raceData.race).parsedData
        assertThrows(IllegalArgumentException::class.java) { special.withCondesRouteBends() }
        val repeated = IofXmlImports.courseData(input.replace("<CourseControl><Control>900</Control></CourseControl>",
            "<CourseControl><Control>900</Control></CourseControl><CourseControl><Control>900</Control></CourseControl>"), original.raceData.race).parsedData
        assertThrows(IllegalArgumentException::class.java) { repeated.withCondesRouteBends() }
    }

    @Test fun reimportPreservesAnExistingBeaconRoleEverywhereWithoutChangingTheSource() {
        val original = project()
        val input = xml().replace("32", "79")
        val parsed = IofXmlImports.validatedCourseData(input, IofXmlSchemaResource.loadBundledSchema(), original.raceData.race).parsedData
        val imported = EventProjectEditor.importIofCourseData(original, parsed).projectFile
        val beacon = imported.raceData.controls.single { it.siCode == 79 }
        val corrected = EventProjectEditor.updateControl(imported, beacon.id, "", "79", ControlPointType.BEACON, false, "B", "")
        val source = EventProjectFileJson.decode(EventProjectFileJson.encode(corrected))
        val before = EventProjectFileJson.encode(source)
        val transaction = DesktopCourseImportTransaction.prepare(source)
        val candidate = EventProjectEditor.importIofCourseData(transaction.baseProject, parsed).projectFile
        val prepared = DesktopIofCourseAnalysis.prepare(candidate, candidate.raceData.categories.map { it.category.id }.toSet(), null,
            elevationLookup = { 100.0 }).project
        assertEquals(before, EventProjectFileJson.encode(source))
        val result = EventProjectFileJson.decode(EventProjectFileJson.encode(transaction.applyTo(source) { prepared }))
        assertEquals(source.raceData.controls, result.raceData.controls)
        result.raceData.categories.forEach { data ->
            val info = data.category.courseInfo!!
            assertEquals(ControlPointType.BEACON, data.controlPoints.single { it.siCode == 79 }.type)
            assertEquals(ControlPointType.BEACON, info.controlPoints.single { it.controlId == beacon.id }.type)
            assertEquals(ProtectedCourseObjectType.BEACON, info.courseObjects.single { it.id == beacon.id }.type)
            assertEquals(parsed.categories.first().category.courseInfo!!.courseObjects.associate { it.id to (it.latitude to it.longitude) },
                info.courseObjects.associate { it.id to (it.latitude to it.longitude) })
            assertNull(CourseDesignBindings.validationError(info))
        }
        assertEquals(result, EventProjectEditor.importIofCourseData(result, parsed).projectFile.let { reimport ->
            DesktopIofCourseAnalysis.prepare(reimport, reimport.raceData.categories.map { it.category.id }.toSet(), null,
                elevationLookup = { 100.0 }).project
        })
        val explicitConflict = IofXmlImports.courseData(input.replace("<Id>79</Id>", "<Id>79</Id><Name>Spectator</Name>"), source.raceData.race).parsedData
        val error = assertThrows(IllegalArgumentException::class.java) { EventProjectEditor.importIofCourseData(source, explicitConflict) }
        assertTrue(error.message.orEmpty().contains("SI 79"))
        assertEquals(before, EventProjectFileJson.encode(source))
    }

    @Test fun importedFoxCanBeCorrectedToBeaconAndAnalyzedAfterSavingAndReopening() {
        val original = project()
        val input = xml().replace("32", "79").replace("<LegLength>1500</LegLength>", "")
        val imported = EventProjectEditor.importIofCourseData(original,
            IofXmlImports.courseData(input, original.raceData.race).parsedData).projectFile
        val prepared = DesktopIofCourseAnalysis.prepare(imported,
            imported.raceData.categories.map { it.category.id }.toSet(), null, elevationLookup = { 100.0 }).project
        val stored = prepared.raceData.categories.last()
        var updated = prepared.copy(raceData = prepared.raceData.copy(
            categories = prepared.raceData.categories.dropLast(1), courseMappings = listOf(stored)))
        val beacon = updated.raceData.controls.single { it.siCode == 79 }
        val fox = updated.raceData.controls.single { it.siCode == 31 }
        updated = EventProjectEditor.updateControl(updated, fox.id, fox.label, "31", ControlPointType.CONTROL, true, "Fox 1", "")
        updated = EventProjectEditor.updateControl(updated, beacon.id, "", "79", ControlPointType.BEACON, false, "", "")
        updated = EventProjectEditor.updateControl(updated, beacon.id, "", "79", ControlPointType.BEACON, false, "Beacon", "")
        updated = EventProjectFileJson.decode(EventProjectFileJson.encode(updated))
        (updated.raceData.categories + updated.raceData.courseMappings).forEach { data ->
            val info = data.category.courseInfo!!
            assertEquals(ControlPointType.BEACON, data.controlPoints.single { it.controlId == beacon.id }.type)
            assertEquals(ProtectedCourseObjectType.BEACON, info.courseObjects.single { it.id == beacon.id }.type)
            assertNull(CourseDesignBindings.validationError(info))
            assertEquals(0, data.category.lengthMeters)
            assertEquals(0, data.category.climbMeters)
            assertNull(data.category.idealOrder)
            val before = prepared.raceData.categories.single { it.category.id == data.category.id }.category.courseInfo!!
            assertEquals(before.courseObjects.map { it.id to (it.latitude to it.longitude) },
                info.courseObjects.map { it.id to (it.latitude to it.longitude) })
            val summary = DesktopCourseAnalyzer.analyze(updated, data.category.id, info, data.category.idealOrder,
                elevationLookup = { 100.0 }, controlIdentityMode = DesktopCourseControlIdentityMode.RESULT_CONTROLS,
                allowFoxRenumbering = false, magneticDeclinationProvider = { null })
            val route = requireNotNull(summary.calculatedRouteSection)
            assertNotNull(route.estimatedIdealSeconds)
            assertEquals(listOf("S", "Fox 1", "B", "F"), route.routeOrder)
            assertTrue(summary.missingElements.toString(), summary.missingElements.none {
                it.contains("Route geometry") || it.contains("Start coordinates") || it.contains("Finish coordinates")
            })
        }
        val beforeReport = EventProjectFileJson.encode(updated)
        val report = DesktopCourseBriefReports.build(updated, emptyMap()).single()
        assertNotNull(report.routeMap)
        assertEquals(listOf("S", "Fox 1", "B", "F"), report.idealOrder)
        assertNotNull(report.estimatedIdealSeconds)
        assertFalse(report.notice.orEmpty().contains("Run Course Analyzer"))
        assertEquals(beforeReport, EventProjectFileJson.encode(updated))
    }

    @Test fun importsPositionsClassAssignmentsAndLegsIntoOneReviewedCourseWithoutChangingRace() {
        val original = project()
        val before = EventProjectFileJson.encode(original)
        val transaction = DesktopCourseImportTransaction.prepare(original)
        val parsed = IofXmlImports.validatedCourseData(xml(), IofXmlSchemaResource.loadBundledSchema(), original.raceData.race)
        assertEquals(listOf("W21", "M21"), parsed.parsedData.categories.map { it.category.name })
        val temporary = EventProjectEditor.importIofCourseData(transaction.baseProject, parsed.parsedData).projectFile
        val ids = temporary.raceData.categories.map { it.category.id }.toSet()
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
        assertEquals(setOf("W21", "M21"), applied.raceData.categories.map { it.category.name }.toSet())
        assertTrue(applied.raceData.courseMappings.isEmpty())
        applied.raceData.categories.forEach { data ->
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
            return DesktopIofCourseAnalysis.prepare(imported, imported.raceData.categories.map { it.category.id }.toSet(), null,
                elevationLookup = { null })
        }
        val direct = analyze(xml().replace("<LegLength>1500</LegLength>", ""))
        val detour = analyze(xml())
        assertNotEquals(direct.reports.single().idealOrder, detour.reports.single().idealOrder)
        assertNull(detour.reports.single().climbMeters)
        assertNull(detour.reports.single().effectiveLengthMeters)
        assertTrue(detour.reports.single().notice!!.contains("horizontal length"))
        assertEquals(1500.0, detour.project.raceData.categories.first().category.courseInfo!!.suppliedLegLengths.single().lengthMeters, 0.0)
    }

    @Test fun missingCoordinatesCanBeAcceptedWithoutInventingAnOrderOrClimb() {
        val original = project()
        val imported = EventProjectEditor.importIofCourseData(original,
            IofXmlImports.courseData(xml().replace(Regex("<Position[^>]*/>"), ""), original.raceData.race).parsedData).projectFile
        val result = DesktopIofCourseAnalysis.prepare(imported, imported.raceData.categories.map { it.category.id }.toSet(), null)
        assertNull(result.reports.single().horizontalLengthMeters)
        assertNull(result.reports.single().climbMeters)
        assertTrue(result.reports.single().idealOrder.isEmpty())
        assertTrue(result.reports.single().legWarnings.single().contains("comparison unavailable"))
        val applied = DesktopCourseImportTransaction.prepare(original).applyTo(original) { result.project }
        assertEquals(2, applied.raceData.categories.first().controlPoints.size)
    }

    @Test fun incompleteCoverageUsesOneHorizontalObjectiveForAllPermutations() {
        val original = project()
        val imported = EventProjectEditor.importIofCourseData(original, IofXmlImports.courseData(xml(), original.raceData.race).parsedData).projectFile
        val ids = imported.raceData.categories.map { it.category.id }.toSet()
        val result = DesktopIofCourseAnalysis.prepare(imported, ids, null,
            elevationLookup = { point -> if (point.longitude > -78.998) null else 100.0 })
        assertTrue(result.reports.single().notice!!.contains("Route selected by horizontal length"))
    }

    @Test fun elevationCanChangeTheWinnerFromTheHorizontallyShortestRoute() {
        val original = project()
        val input = xml().replace("<LegLength>1500</LegLength>", "")
            .replace("lat=\"35.0\" lng=\"-78.999\"", "lat=\"35.001\" lng=\"-78.999\"")
        val imported = EventProjectEditor.importIofCourseData(original, IofXmlImports.courseData(input, original.raceData.race).parsedData).projectFile
        val ids = imported.raceData.categories.map { it.category.id }.toSet()
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
        val result = DesktopIofCourseAnalysis.prepare(imported, imported.raceData.categories.map { it.category.id }.toSet(), "fixture-password", elevationLookup = { 100.0 })
        val data = result.project.raceData.categories.first().category
        assertNull(data.courseInfo)
        assertNotNull(data.encryptedCourseInfo)
        assertEquals(1500.0, data.storedCourseInfo("fixture-password")!!.suppliedLegLengths.single().lengthMeters, 0.0)
    }

    @Test fun downloadedCandidateElevationsAreUsedAndSaved() = kotlinx.coroutines.runBlocking {
        val original = project()
        val imported = EventProjectEditor.importIofCourseData(original, IofXmlImports.courseData(xml(), original.raceData.race).parsedData).projectFile
        val ids = imported.raceData.categories.map { it.category.id }.toSet()
        var requests = 0
        val lookup = DesktopIofCourseAnalysis.fetchCandidateElevations(imported, ids, null,
            provider = { points -> requests++; points.map { 100.0 + (it.longitude + 79) * 10000 } }, local = { null })
        assertEquals(1, requests)
        val result = DesktopIofCourseAnalysis.prepare(imported, ids, null, elevationLookup = lookup)
        assertTrue(result.reports.single().notice!!.contains("Route selected by effective length"))
        assertTrue(result.reports.single().climbMeters!! > 0)
        assertTrue(result.project.raceData.categories.first().category.courseInfo!!.route.all { it.elevationMeters != null })
    }

    @Test fun straightLineToleranceIncludesThreeMetersInBothDirectionsAndRetainsXmlValues() {
        val original = project()
        val parsed = IofXmlImports.courseData(xml(), original.raceData.race).parsedData
        val info = parsed.categories.first().category.courseInfo!!
        val leg = info.suppliedLegLengths.single()
        val from = info.courseObjects.single { it.id == leg.fromId }
        val to = info.courseObjects.single { it.id == leg.toId }
        val direct = CourseGeoPoint(from.latitude, from.longitude).distanceMetersTo(CourseGeoPoint(to.latitude, to.longitude))
        for (difference in listOf(-3.01, -3.0, -2.0, 0.0, 2.0, 3.0, 3.01)) {
            val changed = info.copy(suppliedLegLengths = listOf(leg.copy(lengthMeters = direct + difference)))
            assertEquals("difference=$difference", kotlin.math.abs(difference) > 3.0, DesktopIofCourseAnalysis.legWarnings(changed).isNotEmpty())
            assertEquals(direct + difference, changed.suppliedLegLengths.single().lengthMeters, 0.0)
        }
        val closeXml = xml().replace("1500", (direct + 2.0).toString())
        val imported = EventProjectEditor.importIofCourseData(original, IofXmlImports.courseData(closeXml, original.raceData.race).parsedData).projectFile
        val result = DesktopIofCourseAnalysis.prepare(imported, imported.raceData.categories.map { it.category.id }.toSet(), null, elevationLookup = { 100.0 })
        assertTrue(result.reports.single().legWarnings.isEmpty())
        assertFalse(result.reports.single().notice!!.contains("unknown detours"))
        assertEquals(direct + 2.0, result.project.raceData.categories.first().category.courseInfo!!.suppliedLegLengths.single().lengthMeters, 0.0)
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
