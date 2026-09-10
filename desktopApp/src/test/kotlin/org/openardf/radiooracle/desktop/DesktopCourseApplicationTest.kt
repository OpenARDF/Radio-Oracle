package org.openardf.radiooracle.desktop

import org.junit.Test
import org.junit.Assert.*
import org.openardf.radiooracle.shared.event.*
import org.openardf.radiooracle.shared.sportident.*
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

    @Test fun renumberingKeepsFoxStationPairsInControlsResultsAndExports() {
        val imported = imported()
        val original = imported.copy(raceData = imported.raceData.copy(controls = imported.raceData.controls.map {
            if (it.siCode in 31..35) it.copy(label = "Fox${it.siCode - 30}", publicLabel = "Fox${it.siCode - 30}", siCode = it.siCode + 100) else it
        }))
        val selected = selection(original)
        // Independent oracle: numbering moves the configured Fox/SI pair to a new location.
        val expected = mapOf(131 to ("Fox1" to 40.002), 132 to ("Fox2" to 40.008),
            133 to ("Fox3" to 40.004), 134 to ("Fox4" to 40.006), 135 to ("Fox5" to 40.010), 99 to ("M" to 40.012))
        val labelsAtOldStation = mapOf(131 to "Fox5", 132 to "Fox4", 133 to "Fox3", 134 to "Fox2", 135 to "Fox1")
        val infoWithOldHints = selected.courseInfo.copy(controlPoints = selected.courseInfo.controlPoints.map { it.copy(description = "Survey note\nSI=999") },
            courseObjects = selected.courseInfo.courseObjects.map { it.copy(description = "Survey note\nSI=999") })
        val source = original.withStoredCourseInfo("m21", infoWithOldHints, null)
        val byId = source.raceData.controls.associateBy { it.id }
        val app = selected.application.copy(sourceSnapshotHash = EventCourseDrafts.snapshotHash(source),
            foxAssignments = selected.application.foxAssignments.map { it.copy(calculatedLabel = labelsAtOldStation.getValue(byId.getValue(it.controlId).siCode)) })
        val prepared = DesktopCourseAnalysisApplier.prepare(source,
            listOf(selected.copy(courseInfo = infoWithOldHints, application = app)), null)
        val applied = DesktopCourseAnalysisApplier.commit(source, prepared)
        val info = applied.raceData.categories.single().category.courseInfo!!
        assertEquals(original.raceData.controls, applied.raceData.controls)
        assertEquals(expected, info.appliedBindings!!.controls.associate { bound -> bound.siCode to
            (bound.label to info.controlPoints.single { it.controlId == bound.controlId }.latitude) })
        assertEquals(app.routePoints.map { it.latitude to it.longitude }, info.route.map { it.latitude to it.longitude })
        assertTrue(info.controlPoints.all { it.description?.contains("SI=999") == false })
        val roundTrip = EventProjectFileJson.decode(EventProjectFileJson.encode(applied))
        assertEquals(applied.raceData, roundTrip.raceData)
        val analysis = DesktopCourseAnalyzer.analyze(roundTrip, "m21", info, info.idealOrder, allowFoxRenumbering = false)
        analysis.kmlFolders.flatMap { it.courseObjects }.filter { it.siCode in 131..135 }.forEach {
            assertEquals(expected.getValue(it.siCode!!), it.label to it.point.latitude)
        }
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
        val preview = courseStationPreviewFolders(source, mapOf("m21" to infoWithOldHints),
            selected.controlIdsByPlacementId.mapKeys { "m21" to it.key }, app).single()
        assertEquals(expected.mapValues { it.value.second }, preview.courseObjects.filter { it.siCode in expected }
            .associate { it.siCode!! to it.point.latitude })
        assertStationResultsAndPublication(applied, info, folder)
    }

    private fun assertStationResultsAndPublication(applied: EventProjectFile, info: ProtectedCourseInfo, folder: java.nio.file.Path) {
        var results = EventProjectEditor.assignCompetitorCategory(EventProjectEditor.addCompetitor(applied,
            "runner", "Runner", "Fixture", "1", "100001"), "runner", "m21")
        val readout = SportIdentCardReadout(100001, 2, null, SportIdentTime(32400L), SportIdentTime(34200L),
            listOf(131, 132, 133, 134, 135, 99).mapIndexed { index, code -> SportIdentCardPunch(code, SportIdentTime(32460L + index * 60)) })
        results = EventProjectEditor.addDownloadedSportIdentReadout(results, "readout", 5, readout, "2026-09-06T09:50") { index, type -> "punch-$index-$type" }
        val row = EventResultDetails.from(results.raceData).single()
        assertEquals("Fox1, Fox2, Fox3, Fox4, Fox5, M", row.punchCodesText)
        assertEquals("5", row.pointsText)
        val race = DesktopPublicResultSeriesRace(results, mapOf("m21" to info))
        val graphic = publicResultCoursesForGraphics(race).single().courseInfo
        assertEquals(info.controlPoints, graphic.controlPoints)
        assertEquals(info.route, graphic.route)
        val root = folder.resolve("cloudflare")
        val site = DesktopPublicResultSiteExports.exportSeries(root, "Station regression", listOf(race))
        assertTrue(Files.readString(site.publicResultsJson).contains("course-graphics/"))
        assertEquals(1L, Files.walk(root).use { files -> files.filter { it.toString().endsWith(".png") }.count() })
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

    @Test fun applyingFromAShortCourseKeepsDraftNumberingForFoxesOnlyOnTheFullCourse() {
        val imported = imported()
        val original = imported.copy(raceData = imported.raceData.copy(controls = imported.raceData.controls.map {
            if (it.siCode in 31..35) it.copy(publicLabel = (it.siCode - 30).toString()) else it
        }))
        val full = original.raceData.categories.single()
        val labelsByCode = mapOf(31 to "4", 32 to "3", 33 to "5", 34 to "2", 35 to "1")
        val labels = original.raceData.controls.filter { it.siCode in labelsByCode }
            .associate { it.id to labelsByCode.getValue(it.siCode) }
        val shortIds = original.raceData.controls.filter { it.siCode in setOf(31, 32, 35, 99) }.map { it.id }.toSet()
        fun info(short: Boolean): ProtectedCourseInfo {
            val source = full.category.courseInfo!!
            val points = source.controlPoints.filter { !short || it.controlId in shortIds }
                .map { it.copy(label = labels[it.controlId] ?: it.label) }
            return source.copy(sourceName = "Course Analyzer fox renumbering",
                controlPoints = points,
                courseObjects = source.courseObjects.filter { !short || it.type.controlRole() == null || it.id in shortIds }
                    .map { it.copy(label = labels[it.id] ?: it.label) },
                idealOrder = if (short) "1 5 2 M" else "1 5 4 3 2 M")
        }
        fun order(info: ProtectedCourseInfo) = info.controlPoints.joinToString(" ") { ProtectedIdealOrderRules.quoteToken(it.label) }
        val short = full.copy(category = full.category.copy(id = "short", name = "Short", courseInfo = info(true), idealOrder = order(info(true))),
            controlPoints = full.controlPoints.filter { it.controlId in shortIds }.map { it.copy(id = "short-${it.id}", categoryId = "short") })
        // Include an inactive mapping as well: applying from either category must produce the same catalog.
        val draft = EventCourseDrafts.edit(original) { it.copy(raceData = it.raceData.copy(
            categories = listOf(short), courseMappings = listOf(full.copy(category = full.category.copy(
                name = "Full", courseInfo = info(false), idealOrder = order(info(false))))))) }
        val source = EventCourseDrafts.candidate(draft)
        val categories = source.raceData.categories + source.raceData.courseMappings
        val mappings = categories.associate { category -> category.category.id to
            category.category.courseInfo!!.controlPoints.associate { it.controlId to it.controlId } }
        val before = EventProjectFileJson.encode(draft)
        for (category in categories) {
            val course = category.category.courseInfo!!
            val analysis = DesktopCourseAnalyzer.analyze(source, category.category.id, course, category.category.storedIdealOrder(null),
                prepareApplication = true)
            assertNull("Accepted numbering must not be proposed again", analysis.waitRenumbering)
            val application = analysis.calculatedRouteApplication!!
            val prepared = DesktopCourseAnalysisApplier.prepareAll(draft,
                DesktopCourseRouteSelection(course, application, mappings.getValue(category.category.id)), mappings, null,
                elevationLookup = { 100.0 })
            val applied = DesktopCourseAnalysisApplier.commit(draft, prepared)
            assertEquals(original.raceData.controls, applied.raceData.controls)
            assertEquals(original.raceData.controls.associate { it.id to it.siCode }, applied.raceData.controls.associate { it.id to it.siCode })
            (applied.raceData.categories + applied.raceData.courseMappings).forEach { output ->
                val input = categories.single { it.category.id == output.category.id }.category.courseInfo!!
                val result = output.category.courseInfo!!
                assertEquals(input.controlPoints.associate { it.label to (it.latitude to it.longitude) },
                    result.controlPoints.associate { it.label to (it.latitude to it.longitude) })
                assertEquals(if (output.category.id == "short") setOf(31, 33, 34, 99) else setOf(31, 32, 33, 34, 35, 99),
                    result.appliedBindings!!.controls.map { it.siCode }.toSet())
                result.appliedBindings!!.controls.filter { it.siCode in labelsByCode }.forEach {
                    assertEquals((it.siCode - 30).toString(), it.label)
                }
            }
            assertEquals("passed", CourseWorkflowAudit.audit(applied.raceData).status)
        }
        assertEquals(before, EventProjectFileJson.encode(draft))
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
            assertEquals(source.raceData.controls, applied.raceData.controls)
            val sourcePositions = selected.courseInfo.controlPoints.associate { it.controlId to it.latitude }
            selected.application.foxAssignments.forEach { assignment ->
                val target = source.raceData.controls.single { (it.publicLabel ?: it.label) == assignment.calculatedLabel }
                assertEquals(sourcePositions.getValue(assignment.controlId), stations.getValue(target.siCode), 0.0000001)
            }
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
        // Course symbols can share IDs even when category route endpoints differ slightly.
        val category = applied.raceData.categories.single()
        val originalInfo = category.category.courseInfo!!
        val shifted = originalInfo.copy(
            route = originalInfo.route.mapIndexed { index, point ->
                if (index == 0 || index == originalInfo.route.lastIndex) point.copy(longitude = point.longitude + 0.00001) else point
            }, courseObjects = originalInfo.courseObjects.map { point ->
                if (point.type in setOf(ProtectedCourseObjectType.START, ProtectedCourseObjectType.FINISH)) point.copy(longitude = point.longitude + 0.00001) else point
            })
        val secondInfo = CourseDesignBindings.prepare(shifted, applied.raceData.controls,
            originalInfo.appliedBindings!!.controls.associate { it.placementId to it.controlId },
            originalInfo.appliedBindings!!.orderedPlacementIds, "second-course")
        val second = category.copy(category = category.category.copy(id = "w21", name = "W21", courseInfo = secondInfo),
            controlPoints = category.controlPoints.map { it.copy(id = "w-${it.id}", categoryId = "w21") })
        DesktopProjectFiles.write(race, applied.copy(raceData = applied.raceData.copy(courseMappings = listOf(second))))
        val appliedBytes = Files.readAllBytes(race)
        assertEquals(text.toString(), 0, DesktopAutomationCli.run(arrayOf("course-export-verify", race.toString(), folder.resolve("output").toString()), stream, stream))
        assertArrayEquals(appliedBytes, Files.readAllBytes(race))
        assertTrue(Files.exists(folder.resolve("output/race-1/courses.xml")))
    }

    @Test fun recoveryPreservesMandatoryCornersAndCustomStationPairsInPlaintextAndEncryptedRaces() {
        val imported = imported()
        val codes = listOf(201, 87, 311, 49, 173)
        val reference = imported.raceData.controls.map { control ->
            if (control.siCode in 31..35) control.copy(label = "Fox${control.siCode - 30}",
                publicLabel = (control.siCode - 30).toString(), siCode = codes[control.siCode - 31]) else control
        }
        val labels = reference.filter { it.siCode in codes }.mapIndexed { index, control -> control.id to (5 - index).toString() }.toMap()
        val wrongCatalog = reference.map { control -> labels[control.id]?.let { control.copy(label = it, publicLabel = it) } ?: control }
        val source = imported.copy(raceData = imported.raceData.copy(controls = wrongCatalog))
        val selected = selection(source)
        val corner = ProtectedCourseObjectPoint("mandatory-corner", "Required corner", ProtectedCourseObjectType.WAYPOINT, 40.001, -75.002, 100.0)
        val route = selected.application.routePoints.map { ProtectedCourseRoutePoint(it.latitude, it.longitude, it.elevationMeters) }.toMutableList()
        route.add(1, ProtectedCourseRoutePoint(corner.latitude, corner.longitude, corner.elevationMeters))
        val raw = selected.courseInfo.copy(sourceName = "Course Analyzer applied design (accepted fox numbering)",
            route = route, lengthMeters = 6543, climbMeters = 123,
            controlPoints = selected.courseInfo.controlPoints.map { it.copy(label = labels[it.controlId] ?: it.label) },
            courseObjects = selected.application.courseObjects.map { it.copy(label = labels[it.id] ?: it.label) } + corner)
        val order = selected.application.orderedPlacementIds.toMutableList().apply { add(1, corner.id) }
        val oldInfo = CourseDesignBindings.prepare(raw, wrongCatalog, selected.controlIdsByPlacementId, order, "bad-legacy-design")
        val damaged = EventProjectEditor.replaceCategoryAssignedControls(source, "m21", oldInfo.appliedBindings!!.controls.map { it.controlId }) { "assigned-$it" }
            .withStoredCourseInfo("m21", oldInfo, null).withStoredIdealOrder("m21", oldInfo.idealOrder, null)
        val expectedCodes = mapOf("1" to 201, "2" to 87, "3" to 311, "4" to 49, "5" to 173, "M" to 99)
        for (password in listOf(null, "regression-password")) {
            val input = password?.let { org.openardf.radiooracle.shared.publicresults.ProtectedCourseCipher.protectProjectCourseData(damaged, it) } ?: damaged
            val before = EventProjectFileJson.encode(input)
            val repaired = repairCourseStationPairs(input, reference, password)
            val output = repaired.raceData.categories.single().category.storedCourseInfo(password)!!
            assertEquals(reference, repaired.raceData.controls)
            assertEquals(expectedCodes, output.appliedBindings!!.controls.associate { it.label to it.siCode })
            assertEquals(oldInfo.controlPoints.associate { it.label to (it.latitude to it.longitude) },
                output.controlPoints.associate { it.label to (it.latitude to it.longitude) })
            assertEquals(oldInfo.route, output.route)
            assertEquals(oldInfo.effectiveLengthMeters(), output.effectiveLengthMeters())
            assertEquals(corner, output.courseObjects.single { it.id == corner.id })
            assertEquals(corner.id, output.appliedBindings!!.orderedPlacementIds[1])
            val roundTrip = EventProjectFileJson.decode(EventProjectFileJson.encode(repaired))
            assertEquals(output, roundTrip.raceData.categories.single().category.storedCourseInfo(password))
            assertEquals(before, EventProjectFileJson.encode(input))
            assertThrows(IllegalArgumentException::class.java) { repairCourseStationPairs(input, reference.dropLast(1), password) }
        }
    }

    @Test fun gpxStationHintsSurviveWaypointsRoutesAndTracks() {
        val path = Files.createTempFile("gpx-station-hints-", ".gpx")
        Files.writeString(path, """<gpx xmlns="http://www.topografix.com/GPX/1/1" version="1.1">
            <wpt lat="40" lon="-75"><name>Fox1</name><desc>Survey note
SI=131</desc></wpt>
            <rte><name>Route</name><rtept lat="40.01" lon="-75"><name>Fox2</name><desc>SI=132</desc></rtept><rtept lat="40.02" lon="-75"/></rte>
            <trk><name>Track</name><trkseg><trkpt lat="40.03" lon="-75"><name>Fox3</name><desc>SI=133</desc></trkpt><trkpt lat="40.04" lon="-75"/></trkseg></trk>
        </gpx>""")
        val data = DesktopCourseFileReader.read(path)
        assertEquals(mapOf("Fox1" to 131, "Fox2" to 132, "Fox3" to 133), data.controls.associate { it.name to it.siCodeHint })
        assertEquals("Survey note\nSI=131", data.controls.single { it.name == "Fox1" }.description)
    }

    @Test fun foxStationLookupKeepsSprintGroupsDistinctAndRejectsAmbiguousOrMissingPairs() {
        val controls = EventControlCatalog.sprintPreset("race").mapIndexed { index, control -> control.copy(siCode = 200 + index) }
        assertEquals(controls.single { it.label == "1" }, CourseStationAssignments.foxForLabel(controls, "Fox-1"))
        assertEquals(controls.single { it.label == "F1" }, CourseStationAssignments.foxForLabel(controls, "1F"))
        assertNull(CourseStationAssignments.foxForLabel(controls, "201"))
        val duplicate = controls.first().copy(id = "duplicate", label = "Fox1", siCode = 301)
        assertThrows(IllegalArgumentException::class.java) { CourseStationAssignments.foxForLabel(controls + duplicate, "1") }
        val source = imported()
        val selected = selection(source)
        val before = EventProjectFileJson.encode(source)
        val missing = selected.copy(application = selected.application.copy(foxAssignments = selected.application.foxAssignments.map {
            it.copy(calculatedLabel = "Unconfigured fox")
        }))
        assertThrows(IllegalArgumentException::class.java) { DesktopCourseAnalysisApplier.prepare(source, listOf(missing), null) }
        assertEquals(before, EventProjectFileJson.encode(source))
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
