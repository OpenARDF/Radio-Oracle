package org.openardf.radiooracle.desktop

import org.junit.Test
import org.junit.Assert.*
import org.openardf.radiooracle.shared.event.*
import java.nio.file.Files

class DesktopCourseApplicationTest {
    private fun imported(): EventProjectFile {
        val folder = Files.createTempDirectory("course-application-")
        DesktopDebugLog.initialize(folder.resolve("logs"))
        val file = folder.resolve("draft.kml")
        Files.writeString(file, courseWorkflowKml())
        var project = EventProjectFactory.createEmptyProject("race", "Application fixture", "2026-09-06T09:00")
        project = project.copy(raceData = project.raceData.copy(controls = EventControlCatalog.classicPreset("race")))
        project = EventProjectEditor.addCategory(project, "m21", "M21")
        return DesktopCourseKmlImporter.importProtectedCourseInfo(file, project, null, elevationProvider = { 100.0 }).first
    }

    private fun selection(project: EventProjectFile): DesktopCourseRouteSelection {
        val info = project.raceData.categories.single().category.courseInfo!!
        val app = DesktopCourseAnalyzer.analyze(project, "m21", info, info.idealOrder).calculatedRouteApplication!!
        return DesktopCourseRouteSelection(info, app, info.controlPoints.filter { it.controlId in app.orderedPlacementIds }.associate { it.controlId to it.controlId })
    }

    @Test fun preparesAndAppliesNumberingLocationsStationsAndMetricsTogether() {
        val source = imported()
        val draft = EventCourseDrafts.start(source)
        val selected = selection(EventCourseDrafts.candidate(draft))
        val stationsBefore = source.raceData.controls.associate { it.id to it.siCode }
        val prepared = DesktopCourseAnalysisApplier.prepare(draft, listOf(selected), null)
        assertEquals(source.raceData.controls, draft.raceData.controls)
        val applied = DesktopCourseAnalysisApplier.commit(draft, prepared)
        assertSame(applied, DesktopCourseAnalysisApplier.commit(applied, prepared))
        val category = applied.raceData.categories.single().category
        val info = category.courseInfo!!
        val byId = applied.raceData.controls.associateBy { it.id }
        assertEquals(stationsBefore, applied.raceData.controls.associate { it.id to it.siCode })
        assertTrue(info.controlPoints.all { (byId[it.controlId]?.publicLabel ?: byId[it.controlId]?.label) == it.label })
        assertEquals(prepared.revision, info.appliedBindings!!.revision)
        assertEquals(category.lengthMeters, info.lengthMeters)
        assertEquals(category.climbMeters, info.climbMeters)
        assertEquals("passed", CourseWorkflowAudit.audit(applied.raceData).status)
        assertEquals(applied.raceData, EventProjectFileJson.decode(EventProjectFileJson.encode(applied)).raceData)
    }

    @Test fun appliedKmlGpxIofAndDiagramUseTheReviewedStationsAndPhysicalPositions() {
        val original = imported()
        val selected = selection(original)
        val expected = mapOf(31 to ("Fox5" to 40.010), 32 to ("Fox4" to 40.006),
            33 to ("Fox3" to 40.004), 34 to ("Fox2" to 40.008), 35 to ("Fox1" to 40.002), 99 to ("M" to 40.012))
        val infoWithOldHints = selected.courseInfo.copy(controlPoints = selected.courseInfo.controlPoints.map { it.copy(description = "Survey note\nSI=999") },
            courseObjects = selected.courseInfo.courseObjects.map { it.copy(description = "Survey note\nSI=999") })
        val source = original.withStoredCourseInfo("m21", infoWithOldHints, null)
        val byId = source.raceData.controls.associateBy { it.id }
        val app = selected.application.copy(sourceSnapshotHash = EventCourseDrafts.snapshotHash(source),
            foxAssignments = selected.application.foxAssignments.map { it.copy(calculatedLabel = expected.getValue(byId.getValue(it.controlId).siCode).first) })
        val prepared = DesktopCourseAnalysisApplier.prepare(source,
            listOf(selected.copy(courseInfo = infoWithOldHints, application = app)), null)
        val applied = DesktopCourseAnalysisApplier.commit(source, prepared)
        val info = applied.raceData.categories.single().category.courseInfo!!
        val folder = Files.createTempDirectory("course-output-oracle-")
        fun parse(text: String) = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(java.io.ByteArrayInputStream(text.toByteArray()))
        val kml = folder.resolve("course.kml")
        DesktopControlsRouteKmlKmzExporter.exportPlainFile(DesktopControlsRouteKmlKmzExportTarget(kml, DesktopControlsRouteKmlKmzExportFormat.Kml), applied)
        val points = parse(Files.readString(kml)).getElementsByTagNameNS("*", "Placemark")
        val actualKml = (0 until points.length).map { points.item(it) as org.w3c.dom.Element }
            .filter { it.getElementsByTagNameNS("*", "Point").length > 0 }
            .mapNotNull { element ->
                val data = element.getElementsByTagNameNS("*", "Data")
                val fields = (0 until data.length).map { data.item(it) as org.w3c.dom.Element }.associate { it.getAttribute("name") to it.textContent.trim() }
                val code = fields["siCode"]?.toIntOrNull()?.takeIf { it in expected } ?: return@mapNotNull null
                code to (element.getElementsByTagNameNS("*", "name").item(0).textContent to
                    element.getElementsByTagNameNS("*", "coordinates").item(0).textContent.trim().split(',')[1].toDouble())
            }.toMap()
        assertEquals(expected, actualKml)
        val gpx = folder.resolve("course.gpx")
        DesktopControlsRouteKmlKmzExporter.exportPlainFile(DesktopControlsRouteKmlKmzExportTarget(gpx, DesktopControlsRouteKmlKmzExportFormat.Gpx), applied)
        val waypoints = parse(Files.readString(gpx)).getElementsByTagNameNS("*", "wpt")
        val positionsByLabel = (0 until waypoints.length).map { waypoints.item(it) as org.w3c.dom.Element }
            .associate { it.getElementsByTagNameNS("*", "name").item(0).textContent to it.getAttribute("lat").toDouble() }
        expected.values.forEach { (label, latitude) -> assertEquals(latitude, positionsByLabel.getValue(label), 0.0000001) }
        val xml = org.openardf.radiooracle.shared.files.IofXmlExports.courseData(applied.raceData, protectedCourseInfoByCategoryId = mapOf("m21" to info))
        val definitions = parse(xml).getElementsByTagNameNS("*", "Control")
        val iofPositions = (0 until definitions.length).map { definitions.item(it) as org.w3c.dom.Element }.mapNotNull { control ->
            val id = control.getElementsByTagNameNS("*", "Id").item(0)?.textContent?.toIntOrNull() ?: return@mapNotNull null
            val position = control.getElementsByTagNameNS("*", "Position").item(0) as? org.w3c.dom.Element ?: return@mapNotNull null
            id to position.getAttribute("lat").toDouble()
        }.toMap()
        expected.forEach { (code, pair) -> assertEquals(pair.second, iofPositions.getValue(code), 0.0000001) }
        val svg = org.openardf.radiooracle.shared.publicresults.CourseDiagramSvg.render("Applied course", info)
        expected.values.forEach { (label, _) -> assertTrue("Diagram missing $label", svg.contains(label)) }
    }

    @Test fun preparingAllCoursesFreezesNumberingAndRecomputesEachRoute() {
        var source = imported()
        val primary = source.raceData.categories.single()
        source = source.copy(raceData = source.raceData.copy(courseMappings = listOf(primary.copy(
            category = primary.category.copy(id = "w21", name = "W21"),
            controlPoints = primary.controlPoints.map { it.copy(id = "w-${it.id}", categoryId = "w21") }))))
        val selected = selection(source)
        val prepared = DesktopCourseAnalysisApplier.prepareAll(source, selected,
            mapOf("w21" to selected.controlIdsByPlacementId), null, elevationLookup = { 100.0 })
        val applied = DesktopCourseAnalysisApplier.commit(source, prepared)
        val courses = applied.raceData.categories + applied.raceData.courseMappings
        assertEquals(2, courses.size)
        courses.forEach {
            assertEquals(prepared.revision, it.category.courseInfo!!.appliedBindings!!.revision)
            assertEquals(applied.raceData.categories.single().category.courseInfo!!.controlPoints, it.category.courseInfo!!.controlPoints)
            assertTrue(it.category.courseInfo!!.route.size >= 2)
        }
        assertEquals("passed", CourseWorkflowAudit.audit(applied.raceData).status)
    }

    @Test fun movedDraftRemainsAnalyzableAndDoesNotChangeAppliedOutputs() {
        val source = imported()
        val prepared = DesktopCourseAnalysisApplier.prepare(source, listOf(selection(source)), null)
        val applied = DesktopCourseAnalysisApplier.commit(source, prepared)
        val control = applied.raceData.controls.first { it.siCode == 31 }
        val draft = EventCourseDrafts.edit(applied) { candidate ->
            DesktopProtectedControlLocationUpdater.applyControlLocation(candidate, emptyMap(), control.id,
                "40.011", "-75.0", null, elevationLookup = { 100.0 }).projectFile
        }
        assertEquals(applied.raceData.categories, draft.raceData.categories)
        val candidate = EventCourseDrafts.candidate(draft)
        val info = candidate.raceData.categories.single().category.courseInfo!!
        assertTrue(info.route.isEmpty())
        val infos = effectiveCourseAnalysisCourseInfoByCategoryId(candidate, mapOf("m21" to info), emptyMap())
        assertEquals(listOf("m21"), courseAnalysisRouteCategories(candidate, infos).map { it.category.id })
        assertNull(DesktopCourseAnalyzer.analysisUnavailableReason(candidate, "m21", info, null))
        val analysis = DesktopCourseAnalyzer.analyze(candidate, "m21", info, null, prepareApplication = true)
        assertNotNull(analysis.calculatedRouteApplication)
    }

    @Test fun freshEncryptedReanalysisOfTheSameDesignPreservesStoredCiphertext() {
        val password = "fixture-password"
        val original = imported()
        val applied = DesktopCourseAnalysisApplier.commit(original,
            DesktopCourseAnalysisApplier.prepare(original, listOf(selection(original)), null))
        val protected = org.openardf.radiooracle.shared.publicresults.ProtectedCourseCipher.protectProjectCourseData(applied, password)
        val info = protected.raceData.categories.single().category.storedCourseInfo(password)!!
        val application = DesktopCourseAnalyzer.analyze(protected, "m21", info, info.idealOrder,
            allowFoxRenumbering = false, prepareApplication = true).calculatedRouteApplication!!
        val selection = DesktopCourseRouteSelection(info, application, info.appliedBindings!!.controls.associate { it.placementId to it.controlId })
        val prepared = DesktopCourseAnalysisApplier.prepare(protected, listOf(selection), password)
        val repeated = DesktopCourseAnalysisApplier.commit(protected, prepared)
        assertEquals(EventProjectFileJson.encode(protected), EventProjectFileJson.encode(repeated))
    }

    @Test fun sprintApplicationKeepsSlowFastSpectatorAndBeaconDistinct() {
        val fixture = DesktopCourseAnalyzerTest()
        val id = "category-m21"
        val info = fixture.sprintProtectedInfo()
        val source = fixture.sprintProjectFile().withStoredCourseInfo(id, info, null)
        val app = DesktopCourseAnalyzer.analyze(source, id, info, info.idealOrder, prepareApplication = true).calculatedRouteApplication!!
        val mapping = info.controlPoints.associate { it.controlId to it.controlId }
        val prepared = DesktopCourseAnalysisApplier.prepare(source, listOf(DesktopCourseRouteSelection(info, app, mapping)), null)
        val applied = DesktopCourseAnalysisApplier.commit(source, prepared)
        val output = applied.raceData.categories.single().category.courseInfo!!
        val actual = output.controlPoints.associate { it.label to it.longitude }
        assertEquals(mapOf("1" to -94.99, "2" to -94.98, "F1" to -94.96, "F2" to -94.95,
            "Spectator" to -94.9605, "Beacon" to -94.94), actual)
        val visits = output.appliedBindings!!.orderedControlIds
        assertTrue(visits.indexOf("control-slow-1") < visits.indexOf("control-spectator"))
        assertTrue(visits.indexOf("control-fast-1") > visits.indexOf("control-spectator"))
        assertEquals("passed", CourseWorkflowAudit.audit(applied.raceData).status)
        assertEquals(applied.raceData, EventProjectFileJson.decode(EventProjectFileJson.encode(applied)).raceData)
    }

    @Test fun classicTwoMetersAndFoxoringPreserveAcceptedIdentityThroughApply() {
        for (type in listOf(org.openardf.radiooracle.shared.domain.RaceType.CLASSIC, org.openardf.radiooracle.shared.domain.RaceType.FOXORING)) {
            val imported = imported()
            val source = imported.copy(raceData = imported.raceData.copy(race = imported.raceData.race.copy(
                raceType = type, raceBand = org.openardf.radiooracle.shared.domain.RaceBand.M2)))
            val selected = selection(source)
            val prepared = DesktopCourseAnalysisApplier.prepare(source, listOf(selected), null)
            val applied = DesktopCourseAnalysisApplier.commit(source, prepared)
            assertEquals("passed", CourseWorkflowAudit.audit(applied.raceData).status)
            val info = applied.raceData.categories.single().category.courseInfo!!
            val stations = info.appliedBindings!!.controls.associate { it.siCode to info.controlPoints.single { p -> p.controlId == it.controlId }.latitude }
            assertEquals(mapOf(31 to 40.010, 32 to 40.006, 33 to 40.004, 34 to 40.008, 35 to 40.002, 99 to 40.012), stations)
        }
    }

    @Test fun automationPreviewAndExportUseTheProductionServicesWithoutEditingSources() {
        val source = imported()
        val selected = selection(source)
        val folder = Files.createTempDirectory("course-cli-")
        val race = folder.resolve("race.json")
        DesktopProjectFiles.write(race, source)
        val sourceBytes = Files.readAllBytes(race)
        val design = folder.resolve("design.json")
        Files.writeString(design, kotlinx.serialization.json.buildJsonObject {
            put("categoryId", kotlinx.serialization.json.JsonPrimitive("m21"))
            put("bindingsByCategoryId", kotlinx.serialization.json.buildJsonObject {
                put("m21", kotlinx.serialization.json.JsonObject(selected.controlIdsByPlacementId.mapValues { kotlinx.serialization.json.JsonPrimitive(it.value) }))
            })
        }.toString())
        val text = java.io.ByteArrayOutputStream()
        val stream = java.io.PrintStream(text)
        assertEquals(text.toString(), 0, DesktopAutomationCli.run(arrayOf("course-apply-preview", race.toString(), design.toString()), stream, stream))
        assertArrayEquals(sourceBytes, Files.readAllBytes(race))
        val applied = DesktopCourseAnalysisApplier.commit(source, DesktopCourseAnalysisApplier.prepare(source, listOf(selected), null))
        DesktopProjectFiles.write(race, applied)
        val appliedBytes = Files.readAllBytes(race)
        assertEquals(text.toString(), 0, DesktopAutomationCli.run(arrayOf("course-export-verify", race.toString(), folder.resolve("output").toString()), stream, stream))
        assertArrayEquals(appliedBytes, Files.readAllBytes(race))
        assertTrue(Files.exists(folder.resolve("output/race-1/courses.xml")))
    }

    @Test fun incompleteSelectionsAndLateCalculationsDoNotMutateTheDraft() {
        val source = imported()
        val selected = selection(source)
        val changed = EventProjectEditor.updateCategoryPhysicalStats(source, "m21", "999", "99")
        assertThrows(IllegalArgumentException::class.java) { DesktopCourseAnalysisApplier.prepare(changed, listOf(selected), null) }
        val draft = EventCourseDrafts.start(source)
        val prepared = DesktopCourseAnalysisApplier.prepare(draft, listOf(selected), null)
        val newer = EventCourseDrafts.edit(draft) { EventProjectEditor.updateCategoryPhysicalStats(it, "m21", "999", "99") }
        assertThrows(IllegalArgumentException::class.java) { DesktopCourseAnalysisApplier.commit(newer, prepared) }
        assertNull(source.raceData.categories.single().category.courseInfo!!.appliedBindings)
    }
}
