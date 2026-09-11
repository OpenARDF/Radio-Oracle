package org.openardf.radiooracle.desktop

import org.junit.Assert.*
import org.junit.Test
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.event.*
import org.openardf.radiooracle.shared.files.IofXmlImports
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DesktopAuthoritativeCourseImportTest {
    @Test fun kmlAndKmzPreserveFieldNumberingBothCoursesAndMandatoryRouteVertices() {
        for (extension in listOf("kml", "kmz")) {
            val path = Files.createTempFile("authoritative-courses", ".$extension")
            try {
                if (extension == "kmz") ZipOutputStream(Files.newOutputStream(path)).use {
                    it.putNextEntry(ZipEntry("doc.kml")); it.write(kml().toByteArray()); it.closeEntry()
                } else Files.writeString(path, kml())
                val original = project()
                val transaction = DesktopCourseImportTransaction.prepare(original)
                val (preview, summary) = DesktopCourseKmlImporter.importProtectedCourseInfo(path, transaction.baseProject,
                    null, elevationProvider = { 100.0 })
                assertEquals(setOf("Full", "Short"), summary.matchedCategoryNames.toSet())
                val assigned = DesktopCourseKmlImporter.applyCategoryAssignmentUpdates(preview, summary.categoryAssignmentUpdates)
                val prepared = DesktopAuthoritativeCourseImport.prepare(assigned, summary.matchedCategoryIds.toSet(), null)
                // Building the report is read-only and must not optimize the imported route or numbering.
                val beforeReport = EventProjectFileJson.encode(prepared)
                val reports = DesktopCourseBriefReports.imported(prepared, summary.matchedCategoryIds.toSet(), null)
                assertEquals(beforeReport, EventProjectFileJson.encode(prepared))
                assertEquals(2, reports.size)
                assertTrue(reports.all { it.routeMap != null })
                val applied = EventProjectFileJson.decode(EventProjectFileJson.encode(transaction.applyTo(original) { prepared }))
                assertNull(applied.raceData.courseDraft)
                assertEquals(original.raceData.controls.map { Triple(it.id, it.label, it.siCode) },
                    applied.raceData.controls.map { Triple(it.id, it.label, it.siCode) })
                for (course in applied.raceData.categories) {
                    val info = course.category.courseInfo!!
                    val importedInfo = preview.raceData.categories.single { it.category.id == course.category.id }.category.courseInfo!!
                    assertEquals(importedInfo.route, info.route)
                    assertEquals(importedInfo.courseObjects, info.courseObjects)
                    assertTrue(info.courseObjects.any { it.type == ProtectedCourseObjectType.WAYPOINT })
                    info.controlPoints.forEach { point ->
                        val expected = points.getValue(point.label)
                        assertEquals(expected.first, point.longitude, 0.0000001)
                        assertEquals(expected.second, point.latitude, 0.0000001)
                    }
                    assertEquals(if (course.category.name == "Short") setOf("fox1", "fox3", "fox4", "beacon")
                        else setOf("fox1", "fox2", "fox3", "fox4", "fox5", "beacon"), course.publicControlIds.toSet())
                    assertNull(CourseDesignBindings.validationError(info))
                }
            } finally { Files.deleteIfExists(path) }
        }
    }

    @Test fun controlLocationsCanBeAppliedToExistingBoundCoursesWithoutAnalyzer() {
        val path = Files.createTempFile("control-location-import", ".kml")
        try {
            Files.writeString(path, kml())
            val (imported, summary) = DesktopCourseKmlImporter.importProtectedCourseInfo(path, project(), null,
                elevationProvider = { 100.0 })
            var original = DesktopAuthoritativeCourseImport.prepare(imported, summary.matchedCategoryIds.toSet(), null)
            // Applied designs may use placement IDs distinct from catalog control IDs.
            for (course in original.raceData.categories) {
                val info = course.category.courseInfo!!
                val ids = info.appliedBindings!!.controls.associate { it.placementId to "placement-${it.controlId}" }
                val distinct = info.copy(controlPoints = info.controlPoints.map { it.copy(controlId = ids[it.controlId] ?: it.controlId) },
                    courseObjects = info.courseObjects.map { it.copy(id = ids[it.id] ?: it.id) }, appliedBindings = null)
                val bound = CourseDesignBindings.prepare(distinct, original.raceData.controls,
                    info.appliedBindings!!.controls.associate { ids.getValue(it.placementId) to it.controlId },
                    info.appliedBindings!!.orderedPlacementIds.map { ids[it] ?: it }, "separate-placement-ids")
                original = original.withStoredCourseInfo(course.category.id, bound, null)
            }
            Files.writeString(path, """<kml xmlns="http://www.opengis.net/kml/2.2"><Document>
                <Placemark><name>4</name><Point><coordinates>-78.711,35.721,100</coordinates></Point></Placemark>
                </Document></kml>""")
            val (locations, update) = DesktopCourseKmlImporter.importProtectedCourseInfo(path, original, null,
                elevationProvider = { 100.0 }, requireRoutes = false)
            assertEquals(2, update.controlLocationAffectedCategoryCount)
            val prepared = DesktopAuthoritativeCourseImport.prepare(locations, setOf("full", "short"), null)
            val applied = DesktopCourseImportTransaction.prepare(original).applyTo(original) { prepared }
            for (course in applied.raceData.categories) {
                val info = course.category.courseInfo!!
                assertTrue(info.route.isEmpty())
                assertNull(CourseDesignBindings.validationError(info))
                val point = info.controlPoints.single { it.label == "4" }
                assertEquals(35.721, point.latitude, 0.0000001)
                assertEquals(-78.711, point.longitude, 0.0000001)
                assertEquals(original.raceData.categories.single { it.category.id == course.category.id }.publicControlIds.toSet(),
                    course.publicControlIds.toSet())
            }
        } finally { Files.deleteIfExists(path) }
    }

    @Test fun gpxAppliesWithoutAnAnalyzerDraft() {
        val path = java.nio.file.Path.of(requireNotNull(javaClass.getResource("/condes/course.gpx")).toURI())
        val original = EventProjectFactory.createEmptyProject("race", "GPX", "2026-09-11T09:00")
        val transaction = DesktopCourseImportTransaction.prepare(original)
        val (preview, summary) = DesktopCourseKmlImporter.importProtectedCourseInfo(path, transaction.baseProject, null,
            elevationProvider = { 100.0 }, createMissingCategories = true, createMissingControls = true)
        val prepared = DesktopAuthoritativeCourseImport.prepare(preview, summary.matchedCategoryIds.toSet(), null)
        val applied = transaction.applyTo(original) { prepared }
        assertNull(applied.raceData.courseDraft)
        assertTrue((applied.raceData.categories + applied.raceData.courseMappings).any { it.category.courseInfo?.appliedBindings != null })
        EventControlCatalog.requireCanonical(applied)
    }

    @Test fun xmlUsesExistingStationIdentitiesAndReplacesStaleCourseFacts() {
        val original = project()
        val parsed = IofXmlImports.courseData("""<CourseData iofVersion="3.0"><RaceCourseData><Course>
            <Name>Short</Name><Length>1234</Length><Climb>56</Climb>
            <CourseControl type="Control"><Control>134</Control></CourseControl>
            <CourseControl type="Control"><Control>131</Control></CourseControl>
            </Course></RaceCourseData></CourseData>""", original.raceData.race).parsedData
        val preview = EventProjectEditor.importIofCourseData(original, parsed).projectFile
        val transaction = DesktopCourseImportTransaction.prepare(original)
        val prepared = DesktopAuthoritativeCourseImport.prepare(preview, setOf("short"), null)
        assertEquals(listOf("fox4", "fox1"), prepared.raceData.categories.single { it.category.id == "short" }.publicControlIds)
        assertEquals(original.raceData.controls, prepared.raceData.controls)
        val report = DesktopCourseBriefReports.imported(prepared, setOf("short"), null).single()
        assertEquals(1234, report.horizontalLengthMeters)
        assertEquals(56, report.climbMeters)
        assertNull(report.routeMap)
        assertTrue(report.notice!!.contains("without geographic route data"))
        assertNull(transaction.applyTo(original) { prepared }.raceData.courseDraft)
    }

    companion object {
        val points = linkedMapOf("Start" to (-78.70 to 35.70), "4" to (-78.71 to 35.72),
            "1" to (-78.72 to 35.71), "2" to (-78.74 to 35.70), "5" to (-78.75 to 35.71),
            "3" to (-78.73 to 35.74), "B" to (-78.71 to 35.69), "Finish" to (-78.70 to 35.69))
        fun project(): EventProjectFile {
            var project = EventProjectFactory.createEmptyProject("race", "Two courses", "2026-09-11T09:00")
            project = project.copy(raceData = project.raceData.copy(controls = (1..5).map {
                EventControl("fox$it", "race", "$it", 130 + it, ControlPointType.CONTROL)
            } + EventControl("beacon", "race", "B", 136, ControlPointType.BEACON)))
            project = EventProjectEditor.addCategory(project, "full", "Full")
            project = EventProjectEditor.addCategory(project, "short", "Short")
            // Deliberately wrong pre-import assignments: the new file must replace them.
            return EventProjectEditor.replaceCategoryAssignedControls(project, "short", listOf("fox2")) { "old-$it" }
        }
        fun kml() = """<kml xmlns="http://www.opengis.net/kml/2.2"><Document>
            ${points.entries.joinToString("\n") { (name, p) -> "<Placemark><name>$name</name><Point><coordinates>${p.first},${p.second},100</coordinates></Point></Placemark>" }}
            ${listOf("Short" to listOf("Start", "1", "3", "4", "B", "Finish"),
                "Full" to listOf("Start", "4", "1", "2", "5", "3", "B", "Finish")).joinToString("\n") { (name, order) ->
                "<Placemark><name>$name</name><LineString><coordinates>" + order.mapIndexed { i, label ->
                    val p = points.getValue(label)
                    (if (i == 1) "-78.705,35.705,100 " else "") + "${p.first},${p.second},100"
                }.joinToString(" ") + "</coordinates></LineString></Placemark>"
            }}
            </Document></kml>""".trimIndent()
    }
}
