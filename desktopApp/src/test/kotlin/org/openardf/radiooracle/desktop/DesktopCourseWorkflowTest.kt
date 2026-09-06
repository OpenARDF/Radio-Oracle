package org.openardf.radiooracle.desktop

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.openardf.radiooracle.shared.event.*
import org.openardf.radiooracle.shared.sportident.*
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.shared.domain.ResultStatus
import java.nio.file.Files
import java.nio.file.Path

/** Production operations, isolated synthetic inputs, and evidence at each persistence boundary. */
class DesktopCourseWorkflowTest {
    @Test
    fun importAnalyzeApplyReopenAndMoveCourse() {
        val folder = Files.createTempDirectory("course-workflow-")
        DesktopDebugLog.initialize(folder.resolve("logs"))
        val source = folder.resolve("draft.kml")
        val seed = EventProjectFactory.createEmptyProject("race", "Workflow fixture", "2026-09-06T09:00")
        var project = EventProjectEditor.addCategory(seed.copy(raceData = seed.raceData.copy(
            controls = EventControlCatalog.classicPreset("race")
        )), "m21", "M21")
        val evidence = mutableListOf<JsonObject>()
        fun checkpoint(step: String, value: EventProjectFile): EventProjectFile {
            val path = folder.resolve("$step.json")
            DesktopProjectFiles.write(path, value)
            val reopened = DesktopProjectFiles.read(path)
            assertEquals(value.raceData, reopened.raceData)
            evidence += buildJsonObject {
                put("step", step); put("status", "passed")
                put("controls", reopened.raceData.controls.size)
                put("categories", reopened.raceData.categories.size)
            }
            return reopened
        }
        Files.writeString(source, courseWorkflowKml())
        val imported = DesktopCourseKmlImporter.importProtectedCourseInfo(source, project, null, elevationProvider = { 100.0 })
        project = checkpoint("import", EventCourseDrafts.edit(project) { imported.first })
        assertEquals(1, imported.second.importedCategoryCount)
        repeat(3) { iteration ->
            val candidate = EventCourseDrafts.candidate(project)
            val exported = folder.resolve("iteration-${iteration + 1}.kml")
            DesktopControlsRouteKmlKmzExporter.exportPlainFile(
                DesktopControlsRouteKmlKmzExportTarget(exported, DesktopControlsRouteKmlKmzExportFormat.Kml), candidate)
            val next = DesktopCourseKmlImporter.importProtectedCourseInfo(exported, candidate, null, elevationProvider = { 100.0 }).first
            project = checkpoint("iteration-${iteration + 1}", EventCourseDrafts.edit(project) { next })
            assertNull(project.raceData.categories.single().category.courseInfo)
        }
        val candidate = EventCourseDrafts.candidate(project)
        val info = requireNotNull(candidate.raceData.categories.single().category.courseInfo)
        val analysis = DesktopCourseAnalyzer.analyze(candidate, "m21", info, info.idealOrder, prepareApplication = true)
        val application = requireNotNull(analysis.calculatedRouteApplication)
        val selection = DesktopCourseRouteSelection(info, application,
            info.controlPoints.filter { it.controlId in application.orderedPlacementIds }.associate { it.controlId to it.controlId })
        val prepared = DesktopCourseAnalysisApplier.prepare(project, listOf(selection), null)
        project = checkpoint("apply", DesktopCourseAnalysisApplier.commit(project, prepared))
        assertSame(project, DesktopCourseAnalysisApplier.commit(project, prepared))
        val saved = requireNotNull(project.raceData.categories.single().category.courseInfo)
        val resultMap = DesktopCourseAnalyzer.analyze(project, "m21", saved, saved.idealOrder,
            controlIdentityMode = DesktopCourseControlIdentityMode.RESULT_CONTROLS).routeMaps.first()
        val designLabels = saved.controlPoints.associate { it.controlId to it.label }
        val catalogLabels = project.raceData.controls.associate { it.id to (it.publicLabel ?: it.label) }
        assertTrue("Final Apply numbering must agree with the catalog",
            designLabels.all { (id, label) -> catalogLabels[id] == label })
        evidence += buildJsonObject {
            put("step", "numbering-agreement")
            put("status", if (designLabels.all { (id, label) -> catalogLabels[id] == label }) "passed" else "failed")
            put("applicationPath", "prepared-service")
            put("reason", "Analyzer design labels and result-control labels must agree after final Apply")
            put("diagramLabels", JsonArray(resultMap.points.map { JsonPrimitive(it.label) }))
        }
        Files.writeString(source, courseWorkflowKml(moved = true, includeRoute = false))
        val moved = DesktopCourseKmlImporter.importProtectedCourseInfo(source, EventCourseDrafts.candidate(project), null,
            elevationProvider = { 100.0 }, requireRoutes = false)
        assertTrue(moved.second.staleCourseMappingCategoryIds.contains("m21"))
        val movedInfo = requireNotNull(moved.first.raceData.categories.single().category.courseInfo)
        assertTrue("Movement cannot retain the previous route", movedInfo.route.isEmpty())
        assertEquals("", movedInfo.idealOrder)
        assertEquals("", moved.first.raceData.categories.single().category.storedIdealOrder(null))
        assertEquals(0, moved.first.raceData.categories.single().category.lengthMeters)
        val movedDraft = checkpoint("move", EventCourseDrafts.edit(project) { moved.first })
        assertEquals(saved, movedDraft.raceData.categories.single().category.courseInfo)
        assertEquals(project.raceData, EventCourseDrafts.cancel(movedDraft).raceData)
        Files.writeString(source, courseWorkflowKml(moved = true))
        val newImport = DesktopCourseKmlImporter.importProtectedCourseInfo(source, EventCourseDrafts.candidate(movedDraft), null, elevationProvider = { 100.0 }).first
        val draftD = checkpoint("redesign", EventCourseDrafts.edit(movedDraft) { newImport })
        val designD = EventCourseDrafts.candidate(draftD)
        val infoD = designD.raceData.categories.single().category.courseInfo!!
        val appD = DesktopCourseAnalyzer.analyze(designD, "m21", infoD, infoD.idealOrder, prepareApplication = true).calculatedRouteApplication!!
        val preparedD = DesktopCourseAnalysisApplier.prepare(draftD, listOf(DesktopCourseRouteSelection(infoD, appD,
            infoD.controlPoints.associate { it.controlId to it.controlId })), null)
        project = checkpoint("apply-redesign", DesktopCourseAnalysisApplier.commit(draftD, preparedD))
        val finalInfo = project.raceData.categories.single().category.courseInfo!!
        assertTrue(finalInfo.controlPoints.all { it.latitude > 40.1 })
        assertFalse(finalInfo.appliedBindings!!.revision == saved.appliedBindings!!.revision)
        val streams = listOf(listOf(35, 33, 32, 34, 31, 99), listOf(31, 32, 33, 34, 35, 35, 999, 99), listOf(35, 33, 32, 31, 99))
        streams.forEachIndexed { index, codes ->
            val competitorId = "competitor-$index"
            val si = 100001 + index
            project = EventProjectEditor.assignCompetitorCategory(EventProjectEditor.addCompetitor(project, competitorId,
                "Runner", "Fixture$index", "${index + 1}", si.toString()), competitorId, "m21")
            val readout = SportIdentCardReadout(si, 2, null, SportIdentTime(32400L), SportIdentTime(34200L),
                codes.mapIndexed { i, code -> SportIdentCardPunch(code, SportIdentTime(32460L + i * 60)) })
            project = EventProjectEditor.addDownloadedSportIdentReadout(project, "result-$index", 5, readout, "2026-09-06T09:50") { i, type -> "punch-$index-$i-$type" }
            val recorded = project.raceData.competitorData.single { it.competitorCategory.competitor.id == competitorId }.readoutData!!
            assertEquals(if (index == 2) 4 else 5, recorded.result.points)
            assertEquals(ResultStatus.OK, recorded.result.resultStatus)
            assertEquals(codes, recorded.punches.filter { it.punch.punchType == SIRecordType.CONTROL }.map { it.punch.siCode })
        }
        project = checkpoint("sportident-readouts", project)
        assertThrows(IllegalArgumentException::class.java) { DesktopCourseAnalysisApplier.commit(project, preparedD) }
        val historical = EventProjectFileJson.encode(project)
        val copy = EventProjectFactory.copyForCourseRedesign(project, "revised", "Revised fixture", "2026-09-07T09:00")
        assertFalse(EventCourseDrafts.hasRecordedActivity(copy.raceData))
        assertEquals(historical, EventProjectFileJson.encode(project))
        checkpoint("revised-copy", copy)
        val siteRoot = folder.resolve("site")
        val site = DesktopPublicResultSiteExports.exportSeries(siteRoot, "Workflow Series", listOf(DesktopPublicResultSeriesRace(project, mapOf("m21" to finalInfo))))
        val diagrams = Files.walk(siteRoot).use { files -> files.filter { it.toString().endsWith(".png") }.toList() }
        assertEquals(1, diagrams.size)
        assertTrue(Files.readString(site.publicResultsJson).contains("course-graphics/"))
        val withoutResults = project.copy(raceData = project.raceData.copy(competitorData = emptyList(),
            categories = project.raceData.categories.map { it.copy(competitors = emptyList()) }))
        val replacement = DesktopPublicResultSiteExports.exportSeries(siteRoot, "Workflow Series", listOf(DesktopPublicResultSeriesRace(withoutResults, mapOf("m21" to finalInfo))))
        assertTrue(diagrams.none(Files::exists))
        assertFalse(Files.readString(replacement.publicResultsJson).contains("course-graphics/"))
        evidence += buildJsonObject { put("step", "local-publication-replacement"); put("status", "passed"); put("obsoleteDiagramsRemoved", diagrams.size) }
        val output = Path.of("build/reports/course-workflow")
        Files.createDirectories(output)
        val series = EventSeriesFile(seriesId = "workflow-series", name = "Workflow Series", events = listOf(
            EventSeriesEvent("classic", "classic.json", 0, "Classic 80m")))
        Files.write(output.resolve("transfer-input.roseries"), EventSeriesArchiveZipCodec.encode(EventSeriesArchive(series, mapOf("classic" to project))))

        Files.writeString(output.resolve("baseline.json"), Json { prettyPrint = true }.encodeToString(
            JsonObject.serializer(), buildJsonObject { put("scenario", "import-apply-redesign-readouts-local-publication"); put("steps", JsonArray(evidence)) }
        ))
    }

}

internal fun courseWorkflowKml(moved: Boolean = false, includeRoute: Boolean = true): String {
        val positions = listOf(5, 3, 2, 4, 1)
        val points = listOf("Start" to 0.0) + positions.mapIndexed { i, p -> (31 + i).toString() to p * 0.002 } +
            listOf("M" to 0.012, "Finish" to 0.013)
        fun coordinate(n: Double) = "-75.0,${40.0 + n + if (moved) 0.1 else 0.0},100"
        return """<kml xmlns="http://www.opengis.net/kml/2.2"><Document>""" +
            points.joinToString("") { (label, n) -> "<Placemark><name>$label</name><Point><coordinates>${coordinate(n)}</coordinates></Point></Placemark>" } +
            (if (includeRoute) "<Placemark><name>M21</name><LineString><coordinates>${points.joinToString(" ") { coordinate(it.second) }}</coordinates></LineString></Placemark>" else "") +
            "</Document></kml>"
    }
