/*
 * MIT License
 *
 * Copyright (c) 2025 Pavel Kolský
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.openardf.radiooracle.desktop

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventControl
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.ProtectedCourseControlPoint
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.event.ProtectedCourseObjectPoint
import org.openardf.radiooracle.shared.event.ProtectedCourseObjectType
import org.openardf.radiooracle.shared.event.ProtectedCourseRoutePoint
import org.openardf.radiooracle.shared.event.StandardCategoryRules
import org.openardf.radiooracle.shared.event.effectiveLengthMeters
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val COURSE_ROUTE_STYLE_LINE_WIDTH = 4
private const val COURSE_OBJECT_COLOCATION_TOLERANCE_METERS = 5.0

private val COURSE_CONTROL_DONUT_STYLE_ID = DesktopCourseKmlStyle.DonutStyleId
private val COURSE_START_STYLE_ID = DesktopCourseKmlStyle.StartStyleId
private val COURSE_FINISH_STYLE_ID = DesktopCourseKmlStyle.FinishStyleId
private val COURSE_WAYPOINT_STYLE_ID = DesktopCourseKmlStyle.WaypointStyleId

enum class DesktopControlsRouteKmlKmzExportFormat(
    val contentExtension: String,
    val zipFileSuffix: String
) {
    Kml("kml", ".kml.zip"),
    Kmz("kmz", ".kmz.zip"),
    Gpx("gpx", ".gpx.zip")
}

data class DesktopControlsRouteKmlKmzExportTarget(
    val path: Path,
    val format: DesktopControlsRouteKmlKmzExportFormat
)

data class DesktopControlsRouteKmlKmzExportSummary(
    val categoryCount: Int,
    val routeCount: Int,
    val controlCatalogCount: Int,
    val courseControlPointCount: Int,
    val outputFormat: DesktopControlsRouteKmlKmzExportFormat
)

object DesktopControlsRouteKmlKmzExporter {
    private fun exportCategoryData(projectFile: EventProjectFile): List<EventCategoryData> {
        val activeNames = projectFile.raceData.categories.mapTo(mutableSetOf()) { categoryData ->
            StandardCategoryRules.normalizedCategoryName(categoryData.category.name).uppercase()
        }
        return projectFile.raceData.categories + projectFile.raceData.courseMappings.filterNot { mapping ->
            StandardCategoryRules.normalizedCategoryName(mapping.category.name).uppercase() in activeNames
        }
    }

    fun exportEncryptedZip(
        target: DesktopControlsRouteKmlKmzExportTarget,
        projectFile: EventProjectFile,
        password: String
    ): DesktopControlsRouteKmlKmzExportSummary {
        val trimmedPassword = password.trim()
        require(trimmedPassword.isNotEmpty()) {
            "Race Password cannot be blank."
        }

        val protectedCourseInfoByCategoryId = decryptProtectedCourseInfo(projectFile, trimmedPassword)
        val exportedCourseObjects = courseExportObjects(protectedCourseInfoByCategoryId.values)
        val controlCatalogControls = if (protectedCourseInfoByCategoryId.isEmpty()) {
            controlCatalogControls(projectFile, exportedCourseObjects)
        } else {
            emptyList()
        }
        val entryBytes = when (target.format) {
            DesktopControlsRouteKmlKmzExportFormat.Kml -> buildKml(
                projectFile,
                protectedCourseInfoByCategoryId,
                exportedCourseObjects,
                controlCatalogControls
            ).encodeToByteArray()
            DesktopControlsRouteKmlKmzExportFormat.Kmz -> buildKmz(
                buildKml(projectFile, protectedCourseInfoByCategoryId, exportedCourseObjects, controlCatalogControls)
            )
            DesktopControlsRouteKmlKmzExportFormat.Gpx -> buildGpx(
                projectFile,
                protectedCourseInfoByCategoryId,
                exportedCourseObjects,
                controlCatalogControls
            ).encodeToByteArray()
        }
        val entryName = "controls-routes.${target.format.contentExtension}"

        target.path.parent?.let(Files::createDirectories)
        Files.deleteIfExists(target.path)
        val zipFile = ZipFile(target.path.toFile(), trimmedPassword.toCharArray())
        val zipParameters = ZipParameters().apply {
            fileNameInZip = entryName
            compressionMethod = CompressionMethod.DEFLATE
            isEncryptFiles = true
            encryptionMethod = EncryptionMethod.AES
            aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
        }
        zipFile.addStream(ByteArrayInputStream(entryBytes), zipParameters)

        return DesktopControlsRouteKmlKmzExportSummary(
            categoryCount = exportCategoryData(projectFile).size,
            routeCount = protectedCourseInfoByCategoryId.values.count { it.route.isNotEmpty() },
            controlCatalogCount = controlCatalogControls.size,
            courseControlPointCount = exportedCourseObjects.courseObjects.size + exportedCourseObjects.controlPoints.size,
            outputFormat = target.format
        )
    }

    private fun decryptProtectedCourseInfo(
        projectFile: EventProjectFile,
        password: String
    ): Map<String, ProtectedCourseInfo> =
        exportCategoryData(projectFile).mapNotNull { categoryData ->
            categoryData.category.encryptedCourseInfo
                ?.takeIf { it.isNotBlank() }
                ?.let { encryptedValue ->
                    categoryData.category.id to DesktopProtectedCourseOrder.decryptCourseInfo(encryptedValue, password)
                        .withFiniteCourseGeometry()
                }
        }.toMap()

    private fun buildKml(
        projectFile: EventProjectFile,
        protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>,
        exportedCourseObjects: CourseExportObjects,
        controlCatalogControls: List<EventControl>
    ): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("""<kml xmlns="http://www.opengis.net/kml/2.2">""")
        appendLine("  <Document>")
        appendLine("    <name>${xml(projectFile.raceData.race.name)} controls and routes</name>")
        appendLine("    <open>1</open>")
        append(DesktopCourseKmlStyle.pointStyleDefinitions(includeWaypoint = true))
        exportCategoryData(projectFile).forEachIndexed { categoryIndex, categoryData ->
            val routeStyleId = courseRouteStyleId(categoryData.category.id)
            appendLine("    <Style id=\"$routeStyleId\">")
            appendLine("      <LineStyle>")
            appendLine("        <color>${categoryLineColor(categoryIndex)}</color>")
            appendLine("        <width>$COURSE_ROUTE_STYLE_LINE_WIDTH</width>")
            appendLine("      </LineStyle>")
            appendLine("    </Style>")
        }
        val controlLookup = ControlExportLookup(projectFile.raceData.controls)
        if (controlCatalogControls.isNotEmpty()) {
            appendLine("    <Folder>")
            appendLine("      <name>Control catalog</name>")
            appendControlCatalogPlacemarks(controlCatalogControls)
            appendLine("    </Folder>")
        }
        appendLine("    <Folder>")
        appendLine("      <name>Courses</name>")
        exportCategoryData(projectFile).forEach { categoryData ->
            val category = categoryData.category
            val courseInfo = protectedCourseInfoByCategoryId[category.id]
            if (courseInfo != null) {
                appendCourseRoutePlacemark(
                    categoryName = category.name,
                    categoryId = category.id,
                    courseInfo = courseInfo
                )
            }
        }
        appendCourseObjectPlacemarks(
            exportedCourseObjects.courseObjects,
            exportedCourseObjects.analyzerSavedControlIds,
            controlLookup
        )
        appendCourseControlPointPlacemarks(
            exportedCourseObjects.controlPoints,
            exportedCourseObjects.analyzerSavedControlIds,
            controlLookup
        )
        appendLine("    </Folder>")
        appendLine("  </Document>")
        appendLine("</kml>")
    }

    private fun StringBuilder.appendControlCatalogPlacemarks(controls: List<EventControl>) {
        controls.forEach { control ->
            val latitude = control.latitude ?: return@forEach
            val longitude = control.longitude ?: return@forEach
            val label = control.displayCourseLabel()
            appendLine("        <Placemark>")
            appendLine("          <name>${xml(label)}</name>")
            appendLine("          <description>${xml(coursePointDescription(control, null, "Control catalog $label; type ${control.type}; id ${control.id}"))}</description>")
            appendExtendedData(
                indent = "          ",
                values = listOf(
                    "controlId" to control.id,
                    "label" to label,
                    "type" to control.type.name,
                    "siCode" to control.siCode.toString()
                )
            )
            controlPointStyle(control.type)?.let { styleId ->
                appendLine("          <styleUrl>#$styleId</styleUrl>")
            }
            appendLine("          <Point><coordinates>${coordinates(longitude, latitude, null)}</coordinates></Point>")
            appendLine("        </Placemark>")
        }
    }

    private fun StringBuilder.appendCourseRoutePlacemark(
        categoryName: String,
        categoryId: String,
        courseInfo: ProtectedCourseInfo
    ) {
        if (courseInfo.route.isEmpty()) {
            return
        }
        appendLine("        <Placemark>")
        appendLine("          <name>${xml(categoryName)} route</name>")
        appendLine("          <description>${xml(courseDescription(courseInfo))}</description>")
        appendLine("          <styleUrl>#${courseRouteStyleId(categoryId)}</styleUrl>")
        appendLine("          <LineString>")
        appendLine("            <tessellate>1</tessellate>")
        appendLine("            <coordinates>")
        courseInfo.route.forEach { point ->
            appendLine("              ${coordinates(point)}")
        }
        appendLine("            </coordinates>")
        appendLine("          </LineString>")
        appendLine("        </Placemark>")
    }

    private fun StringBuilder.appendCourseControlPointPlacemarks(
        points: List<ProtectedCourseControlPoint>,
        analyzerSavedControlIds: Set<String>,
        controlLookup: ControlExportLookup
    ) {
        points.forEach { point ->
            val control = controlLookup.resolve(point.controlId, point.label, point.type)
            val label = if (point.controlId in analyzerSavedControlIds) {
                point.label.takeIf { it.isNotBlank() } ?: control?.displayCourseLabel().orEmpty()
            } else {
                control?.displayCourseLabel() ?: point.label
            }
            val siCode = point.description.courseDescriptionSiCodeHint() ?: control?.siCode
            appendLine("        <Placemark>")
            appendLine("          <name>${xml(label)}</name>")
            appendLine("          <description>${xml(coursePointDescription(control, point.description, "Course control $label; type ${point.type}; id ${point.controlId}"))}</description>")
            appendExtendedData(
                indent = "          ",
                values = listOf(
                    "controlId" to point.controlId,
                    "label" to label,
                    "sourceLabel" to point.label,
                    "type" to point.type.name,
                    "siCode" to (siCode?.toString() ?: ""),
                    "elevationMeters" to (point.elevationMeters?.let(::formatNumber) ?: "")
                )
            )
            controlPointStyle(point.type)?.let { styleId ->
                appendLine("          <styleUrl>#$styleId</styleUrl>")
            }
            appendLine("          <Point><coordinates>${coordinates(point.longitude, point.latitude, point.elevationMeters)}</coordinates></Point>")
            appendLine("        </Placemark>")
        }
    }

    private fun StringBuilder.appendCourseObjectPlacemarks(
        points: List<ProtectedCourseObjectPoint>,
        analyzerSavedControlIds: Set<String>,
        controlLookup: ControlExportLookup
    ) {
        points.forEach { point ->
            val control = controlLookup.resolve(point.id, point.label, point.type.controlPointType())
            val label = if (point.id in analyzerSavedControlIds) {
                point.label.takeIf { it.isNotBlank() } ?: control?.displayCourseLabel().orEmpty()
            } else {
                control?.displayCourseLabel() ?: point.label
            }
            val siCode = point.description.courseDescriptionSiCodeHint() ?: control?.siCode
            appendLine("        <Placemark>")
            appendLine("          <name>${xml(label)}</name>")
            appendLine("          <description>${xml(coursePointDescription(control, point.description, "Course object $label; type ${point.type}; id ${point.id}"))}</description>")
            appendExtendedData(
                indent = "          ",
                values = listOf(
                    "id" to point.id,
                    "label" to label,
                    "sourceLabel" to point.label,
                    "type" to point.type.name,
                    "siCode" to (siCode?.toString() ?: ""),
                    "elevationMeters" to (point.elevationMeters?.let(::formatNumber) ?: "")
                )
            )
            courseObjectStyle(point.type)?.let { styleId ->
                appendLine("          <styleUrl>#$styleId</styleUrl>")
            }
            appendLine("          <Point><coordinates>${coordinates(point.longitude, point.latitude, point.elevationMeters)}</coordinates></Point>")
            appendLine("        </Placemark>")
        }
    }

    private fun StringBuilder.appendExtendedData(indent: String, values: List<Pair<String, String>>) {
        appendLine("${indent}<ExtendedData>")
        values.forEach { (name, value) ->
            appendLine("${indent}  <Data name=\"${xml(name)}\"><value>${xml(value)}</value></Data>")
        }
        appendLine("${indent}</ExtendedData>")
    }

    private fun coursePointDescription(
        control: EventControl?,
        sourceDescription: String?,
        details: String,
        includeControlSiFallback: Boolean = true
    ): String =
        buildList {
            sourceDescription?.takeIf { it.isNotBlank() }?.let(::add)
            if (includeControlSiFallback && sourceDescription.courseDescriptionSiCodeHint() == null) {
                control?.siCode?.let { add("SI=$it") }
            }
            add(details)
        }.joinToString("\n")

    private fun buildKmz(kml: String): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("doc.kml"))
            zip.write(kml.encodeToByteArray())
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    private fun buildGpx(
        projectFile: EventProjectFile,
        protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>,
        exportedCourseObjects: CourseExportObjects,
        controlCatalogControls: List<EventControl>
    ): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("""<gpx version="1.1" creator="Radio-Oracle ${xml(DesktopBuildInfo.displayVersion)}" xmlns="http://www.topografix.com/GPX/1/1">""")
        appendLine("  <metadata>")
        appendLine("    <name>${xml(projectFile.raceData.race.name)} controls and routes</name>")
        appendLine("    <desc>Radio-Oracle protected controls and category routes. OCAD-specific GPX extensions are not yet documented; standard GPX waypoints and routes are used.</desc>")
        appendLine("  </metadata>")
        val controlsById = projectFile.raceData.controls.associateBy { it.id }
        appendGpxControlCatalogWaypoints(controlCatalogControls)
        appendGpxCourseObjectWaypoints(
            exportedCourseObjects.courseObjects,
            exportedCourseObjects.analyzerSavedControlIds,
            controlsById
        )
        appendGpxCourseControlWaypoints(
            exportedCourseObjects.controlPoints,
            exportedCourseObjects.analyzerSavedControlIds,
            controlsById
        )
        exportCategoryData(projectFile).forEach { categoryData ->
            val category = categoryData.category
            val courseInfo = protectedCourseInfoByCategoryId[category.id] ?: return@forEach
            appendLine("  <rte>")
            appendLine("    <name>${xml(category.name)}</name>")
            val description = courseDescription(courseInfo)
            if (description.isNotBlank()) {
                appendLine("    <desc>${xml(description)}</desc>")
            }
            appendLine("    <type>Radio-Oracle category route</type>")
            courseInfo.route.forEach { point ->
                appendGpxWaypoint(
                    indent = "    ",
                    tagName = "rtept",
                    latitude = point.latitude,
                    longitude = point.longitude,
                    elevationMeters = point.elevationMeters,
                    name = null,
                    description = null,
                    type = null
                )
            }
            appendLine("  </rte>")
        }
        appendLine("</gpx>")
    }

    private fun StringBuilder.appendGpxControlCatalogWaypoints(controls: List<EventControl>) {
        controls.forEach { control ->
            val latitude = control.latitude ?: return@forEach
            val longitude = control.longitude ?: return@forEach
            val label = control.displayCourseLabel()
            appendGpxWaypoint(
                indent = "  ",
                tagName = "wpt",
                latitude = latitude,
                longitude = longitude,
                elevationMeters = null,
                name = label,
                description = "Control catalog $label; type ${control.type}; id ${control.id}",
                type = control.type.name
            )
        }
    }

    private fun StringBuilder.appendGpxCourseObjectWaypoints(
        points: List<ProtectedCourseObjectPoint>,
        analyzerSavedControlIds: Set<String>,
        controlsById: Map<String, EventControl>
    ) {
        points.forEach { point ->
            val control = controlsById[point.id]
            val label = if (point.id in analyzerSavedControlIds) {
                point.label.takeIf { it.isNotBlank() } ?: control?.displayCourseLabel().orEmpty()
            } else {
                control?.displayCourseLabel() ?: point.label
            }
            appendGpxWaypoint(
                indent = "  ",
                tagName = "wpt",
                latitude = point.latitude,
                longitude = point.longitude,
                elevationMeters = point.elevationMeters,
                name = label,
                description = coursePointDescription(
                    control,
                    point.description,
                    "Course object $label; type ${point.type}; id ${point.id}",
                    includeControlSiFallback = false
                ),
                type = point.type.name
            )
        }
    }

    private fun StringBuilder.appendGpxCourseControlWaypoints(
        points: List<ProtectedCourseControlPoint>,
        analyzerSavedControlIds: Set<String>,
        controlsById: Map<String, EventControl>
    ) {
        points.forEach { point ->
            val control = controlsById[point.controlId]
            val label = if (point.controlId in analyzerSavedControlIds) {
                point.label.takeIf { it.isNotBlank() } ?: control?.displayCourseLabel().orEmpty()
            } else {
                control?.displayCourseLabel() ?: point.label
            }
            appendGpxWaypoint(
                indent = "  ",
                tagName = "wpt",
                latitude = point.latitude,
                longitude = point.longitude,
                elevationMeters = point.elevationMeters,
                name = label,
                description = coursePointDescription(
                    control,
                    point.description,
                    "Course control $label; type ${point.type}; id ${point.controlId}",
                    includeControlSiFallback = false
                ),
                type = point.type.name
            )
        }
    }

    private fun StringBuilder.appendGpxWaypoint(
        indent: String,
        tagName: String,
        latitude: Double,
        longitude: Double,
        elevationMeters: Double?,
        name: String?,
        description: String?,
        type: String?
    ) {
        appendLine("$indent<$tagName lat=\"${formatNumber(latitude)}\" lon=\"${formatNumber(longitude)}\">")
        elevationMeters?.let { appendLine("$indent  <ele>${formatNumber(it)}</ele>") }
        name?.takeIf { it.isNotBlank() }?.let { appendLine("$indent  <name>${xml(it)}</name>") }
        description?.takeIf { it.isNotBlank() }?.let { appendLine("$indent  <desc>${xml(it)}</desc>") }
        type?.takeIf { it.isNotBlank() }?.let { appendLine("$indent  <type>${xml(it)}</type>") }
        appendLine("$indent</$tagName>")
    }

    private fun courseDescription(courseInfo: ProtectedCourseInfo?): String =
        if (courseInfo == null) {
            "No protected controls/route course data is stored for this category."
        } else {
            buildList {
                if (courseInfo.idealOrder.isNotBlank()) {
                    add("Ideal order: ${courseInfo.idealOrder}")
                }
                courseInfo.lengthMeters?.let { add("Length: ${it} m") }
                courseInfo.effectiveLengthMeters()?.let { add("Effective length: ${it} m") }
                courseInfo.climbMeters?.let { add("Climb: ${it} m") }
                if (courseInfo.sourceName.isNotBlank()) {
                    add("Source: ${courseInfo.sourceName}")
                }
                if (courseInfo.sourceSha256.isNotBlank()) {
                    add("Source SHA-256: ${courseInfo.sourceSha256}")
                }
            }.joinToString("; ").ifBlank { "Protected course data." }
        }

    private fun courseRouteStyleId(categoryId: String): String = "courseRoute-${categoryId}"

    private fun EventControl.displayCourseLabel(): String =
        publicLabel?.takeIf { it.isNotBlank() } ?: label

    private fun courseExportObjects(courseInfos: Collection<ProtectedCourseInfo>): CourseExportObjects {
        // KML/KMZ/GPX exports keep routes and course objects in one shared surface. Starts, finishes,
        // spectators, beacons, and foxes are shared event objects, so dedupe them by object identity
        // even when category route endpoints differ slightly. For route-derived objects such as
        // mandatory waypoints, use a meter-based colocation check so tiny coordinate differences do
        // not create duplicate exported waypoints.
        val courseObjects = courseInfos.flatMap { it.courseObjects }.dedupeForExport()
        val courseObjectControlIds = courseObjects.mapTo(mutableSetOf()) { it.id }
        val controlPoints = courseInfos
            .flatMap { it.controlPoints }
            .filterNot { it.controlId in courseObjectControlIds }
            .distinctBy { "${it.controlId}:${it.type}" }
        val analyzerSavedControlIds = courseInfos
            .filter { it.sourceName.startsWith("Course Analyzer", ignoreCase = true) }
            .flatMap { courseInfo ->
                courseInfo.courseObjects.map { it.id } + courseInfo.controlPoints.map { it.controlId }
            }
            .toSet()
        return CourseExportObjects(
            courseObjects = courseObjects,
            controlPoints = controlPoints,
            analyzerSavedControlIds = analyzerSavedControlIds
        )
    }

    private fun controlCatalogControls(
        projectFile: EventProjectFile,
        exportedCourseObjects: CourseExportObjects
    ): List<EventControl> {
        val protectedControlIds = exportedCourseObjects.courseObjects.mapTo(mutableSetOf()) { it.id }
        exportedCourseObjects.controlPoints.mapTo(protectedControlIds) { it.controlId }
        return projectFile.raceData.controls.filter { control ->
            val latitude = control.latitude
            val longitude = control.longitude
            control.id !in protectedControlIds &&
                latitude != null &&
                longitude != null &&
                latitude.isValidLatitude() &&
                longitude.isValidLongitude()
        }
    }

    private fun List<ProtectedCourseObjectPoint>.dedupeForExport(): List<ProtectedCourseObjectPoint> =
        fold(emptyList()) { exported, candidate ->
            if (exported.any { it.sameExportObjectAs(candidate) }) {
                exported
            } else {
                exported + candidate
            }
        }

    private fun ProtectedCourseObjectPoint.sameExportObjectAs(other: ProtectedCourseObjectPoint): Boolean {
        if (id == other.id && type == other.type) {
            return true
        }
        return type == other.type &&
            label.trim().equals(other.label.trim(), ignoreCase = true) &&
            CourseGeoPoint(latitude, longitude).distanceMetersTo(CourseGeoPoint(other.latitude, other.longitude)) <=
            COURSE_OBJECT_COLOCATION_TOLERANCE_METERS
    }

    private fun controlPointStyle(type: ControlPointType): String? = when (type) {
        ControlPointType.CONTROL,
        ControlPointType.BEACON,
        ControlPointType.SEPARATOR -> COURSE_CONTROL_DONUT_STYLE_ID
    }

    private fun courseObjectStyle(type: ProtectedCourseObjectType): String? = when (type) {
        ProtectedCourseObjectType.START -> COURSE_START_STYLE_ID
        ProtectedCourseObjectType.FINISH -> COURSE_FINISH_STYLE_ID
        ProtectedCourseObjectType.CONTROL,
        ProtectedCourseObjectType.BEACON,
        ProtectedCourseObjectType.SPECTATOR -> COURSE_CONTROL_DONUT_STYLE_ID
        ProtectedCourseObjectType.WAYPOINT -> COURSE_WAYPOINT_STYLE_ID
    }

    private fun ProtectedCourseObjectType.controlPointType(): ControlPointType? = when (this) {
        ProtectedCourseObjectType.CONTROL -> ControlPointType.CONTROL
        ProtectedCourseObjectType.BEACON -> ControlPointType.BEACON
        ProtectedCourseObjectType.SPECTATOR -> ControlPointType.SEPARATOR
        ProtectedCourseObjectType.START,
        ProtectedCourseObjectType.FINISH,
        ProtectedCourseObjectType.WAYPOINT -> null
    }

    private fun categoryLineColor(categoryIndex: Int): String {
        var hue = (categoryIndex * 137.5) % 360.0
        if (hue in 45.0..75.0) {
            hue += 38.0
        }
        val saturation = 0.75
        val value = 0.85
        val c = value * saturation
        val x = c * (1.0 - kotlin.math.abs((hue / 60.0) % 2.0 - 1.0))
        val m = value - c
        val (red, green, blue) = when {
            hue < 60.0 -> Triple(c + m, x + m, m)
            hue < 120.0 -> Triple(x + m, c + m, m)
            hue < 180.0 -> Triple(m, c + m, x + m)
            hue < 240.0 -> Triple(m, x + m, c + m)
            hue < 300.0 -> Triple(x + m, m, c + m)
            else -> Triple(c + m, m, x + m)
        }
        return String.format(
            Locale.US,
            "ff%02x%02x%02x",
            (blue * 255).toInt(),
            (green * 255).toInt(),
            (red * 255).toInt()
        )
    }

    private fun coordinates(point: ProtectedCourseRoutePoint): String =
        coordinates(point.longitude, point.latitude, point.elevationMeters)

    private fun coordinates(longitude: Double, latitude: Double, elevationMeters: Double?): String =
        DesktopExportPrimitives.compactKmlCoordinate(longitude, latitude, elevationMeters)

    private fun formatNumber(value: Double): String =
        DesktopExportPrimitives.compactDecimal(value)

    private fun xml(value: String): String =
        DesktopExportPrimitives.xmlText(value)
}

private data class CourseExportObjects(
    val courseObjects: List<ProtectedCourseObjectPoint>,
    val controlPoints: List<ProtectedCourseControlPoint>,
    val analyzerSavedControlIds: Set<String>
)

private class ControlExportLookup(controls: List<EventControl>) {
    private val controlsById = controls.associateBy { it.id }
    private val controlsByRoleAndLabel = controls
        .flatMap { control ->
            listOf(control.exportDisplayCourseLabel(), control.label)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinctBy { it.normalizedExportLabel() }
                .map { label -> (control.type to label.normalizedExportLabel()) to control }
        }
        .groupBy({ it.first }, { it.second })
        .mapNotNull { (key, matchingControls) ->
            matchingControls.distinctBy { it.id }.singleOrNull()?.let { key to it }
        }
        .toMap()

    fun resolve(controlId: String, label: String, type: ControlPointType?): EventControl? =
        controlsById[controlId]
            ?: type?.let { controlType ->
                controlsByRoleAndLabel[controlType to label.normalizedExportLabel()]
            }
}

private fun String.normalizedExportLabel(): String =
    trim().lowercase()

private fun EventControl.exportDisplayCourseLabel(): String =
    publicLabel?.takeIf { it.isNotBlank() } ?: label
