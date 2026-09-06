package org.openardf.radiooracle.desktop

import kotlinx.serialization.json.*
import org.openardf.radiooracle.shared.event.*
import org.openardf.radiooracle.shared.files.IofXmlExports
import org.openardf.radiooracle.shared.publicresults.CourseDiagramSvg
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs

/** Thin automation adapters around the same candidate, Analyzer, Apply and export services as the UI. */
internal object DesktopCourseDesignCommands {
    fun preview(args: List<String>, out: PrintStream, err: PrintStream): Int {
        if (args.size != 2) { err.println("course-apply-preview requires a Race File/series and reviewed design JSON."); return 2 }
        return try {
            val designPath = Path.of(args[1]).toAbsolutePath()
            val design = Json.parseToJsonElement(Files.readString(designPath)).jsonObject
            val changes = readCourseWorkflowProjects(Path.of(args[0])).map { original ->
                val spec = design[original.raceData.race.id]?.jsonObject ?: design
                var project = original
                spec["kml"]?.jsonPrimitive?.content?.let { file ->
                    val path = designPath.parent.resolve(file).normalize()
                    project = EventCourseDrafts.edit(project) { candidate ->
                        DesktopCourseKmlImporter.importProtectedCourseInfo(path, candidate, null,
                            elevationProvider = DesktopVenueElevationCache::elevationMeters).first
                    }
                }
                val candidate = EventCourseDrafts.candidate(project)
                val infos = plaintextCourses(candidate)
                val id = spec.getValue("categoryId").jsonPrimitive.content
                val mappings = spec.getValue("bindingsByCategoryId").jsonObject.mapValues { (_, value) ->
                    value.jsonObject.mapValues { it.value.jsonPrimitive.content }
                }
                val info = infos.getValue(id)
                val app = requireNotNull(DesktopCourseAnalyzer.analyze(candidate, id, info, info.idealOrder,
                    prepareApplication = true).calculatedRouteApplication) { "The selected course cannot be calculated." }
                val prepared = DesktopCourseAnalysisApplier.prepareAll(project,
                    DesktopCourseRouteSelection(info, app, mappings.getValue(id)), mappings, null,
                    elevationLookup = DesktopVenueElevationCache::elevationMeters)
                buildJsonObject {
                    put("race", candidate.raceData.race.name); put("status", "passed")
                    put("courses", JsonArray(prepared.changes.groupBy { it.categoryName }.map { (name, controls) -> buildJsonObject {
                        put("name", name); put("stations", JsonArray(controls.map { control -> buildJsonObject {
                            put("label", control.label); put("siCode", control.siCode)
                        } }))
                    } }))
                }
            }
            out.println(JsonArray(changes).toString())
            0
        } catch (error: Exception) {
            err.println("Course Apply preview is incomplete (${error::class.simpleName}). Review all station bindings and unlocked course data in the app.")
            1
        }
    }

    fun exportVerify(args: List<String>, out: PrintStream, err: PrintStream): Int {
        if (args.size != 2) { err.println("course-export-verify requires a Race File/series and a new output directory."); return 2 }
        return try {
            val projects = readCourseWorkflowProjects(Path.of(args[0]))
            val ready = projects.map { project -> project to ResolvedCourseProjection.courseInfos(project.raceData, plaintextCourses(project)) }
            val root = Path.of(args[1]).toAbsolutePath().normalize()
            Files.createDirectory(root)
            var courseCount = 0
            ready.forEachIndexed { index, (project, infos) ->
                val folder = Files.createDirectory(root.resolve("race-${index + 1}"))
                for ((extension, format) in listOf("kml" to DesktopControlsRouteKmlKmzExportFormat.Kml, "gpx" to DesktopControlsRouteKmlKmzExportFormat.Gpx)) {
                    val path = folder.resolve("courses.$extension")
                    DesktopControlsRouteKmlKmzExporter.exportPlainFile(DesktopControlsRouteKmlKmzExportTarget(path, format), project)
                    val points = DesktopCourseFileReader.read(path).controls
                    infos.values.flatMap { it.validatedPlacements().values }.forEach { expected ->
                        require(points.any { point -> point.name == expected.label &&
                            abs(point.point.latitude - expected.latitude) < 0.000001 &&
                            abs(point.point.longitude - expected.longitude) < 0.000001 }) { "An exported placement is missing or moved." }
                    }
                }
                val xml = IofXmlExports.courseData(project.raceData, protectedCourseInfoByCategoryId = infos)
                val builder = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
                    isNamespaceAware = true
                    setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                }.newDocumentBuilder()
                require(builder.parse(java.io.ByteArrayInputStream(xml.toByteArray())).documentElement.localName == "CourseData")
                Files.writeString(folder.resolve("courses.xml"), xml)
                infos.entries.forEachIndexed { number, (_, info) ->
                    Files.writeString(folder.resolve("course-${number + 1}.svg"), CourseDiagramSvg.render("Applied course", info))
                    courseCount++
                }
            }
            out.println("Course export verification passed for ${ready.size} races and $courseCount courses (KML, GPX, IOF XML, SVG).")
            0
        } catch (error: Exception) {
            err.println("Course export verification failed (${error::class.simpleName}). Any generated output is incomplete; source files were not changed.")
            1
        }
    }

    private fun plaintextCourses(project: EventProjectFile): Map<String, ProtectedCourseInfo> {
        val categories = project.raceData.categories + project.raceData.courseMappings
        require(categories.none { !it.category.encryptedCourseInfo.isNullOrBlank() }) { "Unlock protected courses in the app; credentials are not accepted as command arguments." }
        return categories.mapNotNull { data -> data.category.courseInfo?.let { data.category.id to it } }.toMap().also {
            require(it.isNotEmpty()) { "Course data is missing." }
            require(categories.filter { it.controlPoints.isNotEmpty() }.all { category -> category.category.id in it }) { "Course coverage is incomplete." }
        }
    }
}

internal fun readCourseWorkflowProjects(path: Path): List<EventProjectFile> =
    if (path.fileName.toString().endsWith(".roseries", ignoreCase = true)) {
        val archive = EventSeriesArchiveZipCodec.decode(Files.readAllBytes(path))
        archive.seriesFile.sortedEvents().map { archive.member(it.seriesEventId) }
    } else listOf(DesktopProjectFiles.read(path))
