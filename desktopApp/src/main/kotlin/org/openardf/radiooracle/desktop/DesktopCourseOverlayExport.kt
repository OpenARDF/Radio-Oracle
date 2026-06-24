package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.EventControl
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.ProtectedCourseControlPoint
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.event.ProtectedCourseObjectPoint
import org.openardf.radiooracle.shared.event.ProtectedCourseObjectType
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

data class DesktopCourseOverlayExportTarget(
    val baseMapPath: Path,
    val outputDirectory: Path,
    val startExclusionRadiusMeters: Int,
    val finishExclusionRadiusMeters: Int
)

data class DesktopCourseOverlayExportSummary(
    val competitorPath: Path,
    val masterPath: Path,
    val custodianPath: Path,
    val editableCompetitorPath: Path,
    val editableMasterPath: Path,
    val editableCustodianPath: Path,
    val exportedPointCount: Int,
    val exclusionCircleCount: Int,
    val finishCorridorCount: Int
)

object DesktopCourseOverlayExporter {
    fun defaultExclusionRadiusMeters(raceType: RaceType): Int =
        when (raceType) {
            RaceType.SPRINT -> 100
            RaceType.FOXORING -> 250
            RaceType.CLASSIC,
            RaceType.SHORT -> 750
            RaceType.ORIENTEERING -> 0
        }

    fun exportOverlays(
        target: DesktopCourseOverlayExportTarget,
        projectFile: EventProjectFile,
        password: String
    ): DesktopCourseOverlayExportSummary {
        require(target.startExclusionRadiusMeters >= 0) { "Start exclusion radius cannot be negative." }
        require(target.finishExclusionRadiusMeters >= 0) { "Finish exclusion radius cannot be negative." }
        val courseInfos = decryptProtectedCourseInfo(projectFile, password.trim())
        require(courseInfos.isNotEmpty()) {
            "No protected course locations are stored in this Event File."
        }
        val baseMap = OomBaseMap.read(target.baseMapPath)
        val overlayData = CourseOverlayData.from(projectFile, courseInfos.values, baseMap)
        require(overlayData.hasAnyPoint) {
            "No start, finish, beacon, spectator, or fox coordinates are available for overlay export."
        }

        Files.createDirectories(target.outputDirectory)
        val stem = projectFile.raceData.race.name.safeFileStem()
        val competitorPath = target.outputDirectory.resolve("$stem competitor overlay.xmap")
        val masterPath = target.outputDirectory.resolve("$stem master overlay.xmap")
        val custodianPath = target.outputDirectory.resolve("$stem custodian overlay.xmap")
        val editableCompetitorPath = target.outputDirectory.resolve("$stem competitor editable overlay.omap")
        val editableMasterPath = target.outputDirectory.resolve("$stem master editable overlay.omap")
        val editableCustodianPath = target.outputDirectory.resolve("$stem custodian editable overlay.omap")

        val competitorObjects = overlayObjects(
            data = overlayData,
            includeFoxes = false,
            includeLabelsForFoxes = false,
            includeExclusionCircles = true,
            includeFinishCorridor = true,
            startExclusionRadiusMeters = target.startExclusionRadiusMeters,
            finishExclusionRadiusMeters = target.finishExclusionRadiusMeters
        )
        val masterObjects = overlayObjects(
            data = overlayData,
            includeFoxes = true,
            includeLabelsForFoxes = true,
            includeExclusionCircles = true,
            includeFinishCorridor = true,
            startExclusionRadiusMeters = target.startExclusionRadiusMeters,
            finishExclusionRadiusMeters = target.finishExclusionRadiusMeters
        )
        val custodianObjects = overlayObjects(
            data = overlayData,
            includeFoxes = true,
            includeLabelsForFoxes = true,
            includeExclusionCircles = false,
            includeFinishCorridor = false,
            startExclusionRadiusMeters = 0,
            finishExclusionRadiusMeters = 0
        )

        Files.writeString(competitorPath, buildXmap(baseMap.georeferencingXml, "Competitor overlay", competitorObjects))
        Files.writeString(masterPath, buildXmap(baseMap.georeferencingXml, "Master overlay", masterObjects))
        Files.writeString(custodianPath, buildXmap(baseMap.georeferencingXml, "Custodian overlay", custodianObjects))
        Files.writeString(editableCompetitorPath, buildEditableOmap(baseMap.xml, "Radio-Oracle competitor overlay", competitorObjects))
        Files.writeString(editableMasterPath, buildEditableOmap(baseMap.xml, "Radio-Oracle master overlay", masterObjects))
        Files.writeString(editableCustodianPath, buildEditableOmap(baseMap.xml, "Radio-Oracle custodian overlay", custodianObjects))

        return DesktopCourseOverlayExportSummary(
            competitorPath = competitorPath,
            masterPath = masterPath,
            custodianPath = custodianPath,
            editableCompetitorPath = editableCompetitorPath,
            editableMasterPath = editableMasterPath,
            editableCustodianPath = editableCustodianPath,
            exportedPointCount = overlayData.pointCount,
            exclusionCircleCount = competitorObjects.count { it.kind == OverlayObjectKind.ExclusionCircle },
            finishCorridorCount = competitorObjects.count { it.kind == OverlayObjectKind.FinishCorridor }
        )
    }

    private fun decryptProtectedCourseInfo(
        projectFile: EventProjectFile,
        password: String
    ): Map<String, ProtectedCourseInfo> {
        require(password.isNotBlank()) { "Event Password cannot be blank." }
        return projectFile.raceData.categories.mapNotNull { categoryData ->
            categoryData.category.encryptedCourseInfo
                ?.takeIf { it.isNotBlank() }
                ?.let { encryptedValue ->
                    categoryData.category.id to DesktopProtectedCourseOrder.decryptCourseInfo(encryptedValue, password)
                }
        }.toMap()
    }

    private fun overlayObjects(
        data: CourseOverlayData,
        includeFoxes: Boolean,
        includeLabelsForFoxes: Boolean,
        includeExclusionCircles: Boolean,
        includeFinishCorridor: Boolean,
        startExclusionRadiusMeters: Int,
        finishExclusionRadiusMeters: Int
    ): List<OverlayObject> = buildList {
        data.start?.let { add(OverlayObject.point(OomSymbol.Start, it.mapPoint)) }
        data.beacon?.let {
            add(OverlayObject.point(OomSymbol.Control, it.mapPoint))
            add(OverlayObject.text(it.mapPoint.labelPoint(), it.label))
        }
        data.spectator?.let {
            add(OverlayObject.point(OomSymbol.Control, it.mapPoint))
            add(OverlayObject.text(it.mapPoint.labelPoint(), it.label))
        }
        if (includeFoxes) {
            data.foxes.forEach { fox ->
                add(OverlayObject.point(OomSymbol.Control, fox.mapPoint))
                if (includeLabelsForFoxes) {
                    add(OverlayObject.text(fox.mapPoint.labelPoint(), fox.label))
                }
            }
        }
        data.finish?.let { add(OverlayObject.point(OomSymbol.Finish, it.mapPoint)) }
        if (includeFinishCorridor && data.beacon != null && data.finish != null) {
            add(
                OverlayObject.line(
                    OomSymbol.MarkedRoute,
                    listOf(data.beacon.mapPoint, data.finish.mapPoint),
                    OverlayObjectKind.FinishCorridor
                )
            )
        }
        if (includeExclusionCircles) {
            if (startExclusionRadiusMeters > 0) {
                data.start?.let { start ->
                    add(OverlayObject.line(OomSymbol.ExclusionCircle, data.baseMap.circle(start.mapPoint, startExclusionRadiusMeters), OverlayObjectKind.ExclusionCircle))
                }
            }
            if (finishExclusionRadiusMeters > 0) {
                data.finish?.let { finish ->
                    add(OverlayObject.line(OomSymbol.ExclusionCircle, data.baseMap.circle(finish.mapPoint, finishExclusionRadiusMeters), OverlayObjectKind.ExclusionCircle))
                }
            }
        }
    }

    private fun buildXmap(georeferencingXml: String, partName: String, objects: List<OverlayObject>): String =
        buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<map xmlns="http://openorienteering.org/apps/mapper/xml/v2" version="9">""")
            appendLine("<notes>Generated by Radio-Oracle Course Overlay Export.</notes>")
            appendLine(georeferencingXml)
            appendLine(courseDesignColorsXml(StandaloneCourseOverlayColorIds))
            appendLine("""<barrier version="6" required="0.6.0">""")
            appendLine("""<symbols count="6" id="Radio_Oracle_Course_Overlay_ISOM_2017_2_15000">""")
            appendLine(courseDesignSymbolsXml(StandaloneCourseOverlaySymbolIds, StandaloneCourseOverlayColorIds))
            appendLine("</symbols>")
            appendLine("""<parts count="1" current="0">""")
            appendLine("""<part name="${xml(partName)}"><objects count="${objects.size}">""")
            objects.forEach { appendLine(it.toXml(StandaloneCourseOverlaySymbolIds)) }
            appendLine("</objects></part>")
            appendLine("</parts>")
            appendLine("""<templates count="0" first_front_template="0"><defaults use_meters_per_pixel="true" meters_per_pixel="100" dpi="0" scale="0"/></templates>""")
            appendLine("""<view><grid color="#c0c0c0" display="0" alignment="0" additional_rotation="0" unit="0" h_spacing="3" v_spacing="3" h_offset="0" v_offset="0" snapping_enabled="true"/><map_view zoom="1" position_x="0" position_y="0" grid="true"><map opacity="1" visible="true"/><templates count="0"/></map_view></view>""")
            appendLine("""<print scale="15000" resolution="600" templates_visible="true" mode="vector"><page_format paper_size="A4" orientation="portrait" h_overlap="5" v_overlap="5"><dimensions width="210" height="297"/><page_rect left="5" top="5" width="200" height="287"/></page_format><print_area left="5" top="5" width="200" height="287" center_area="true" single_page="true"/></print>""")
            appendLine("</barrier>")
            appendLine("</map>")
        }

    private fun buildEditableOmap(baseMapXml: String, partName: String, objects: List<OverlayObject>): String {
        val colors = baseMapXml.courseOverlayColorIds()
        val symbolStart = Regex("""<symbol\b[^>]*\bid="(\d+)""")
            .findAll(baseMapXml)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .maxOrNull()
            ?.plus(1)
            ?: 1
        val symbols = CourseOverlaySymbolIds(
            start = symbolStart,
            control = symbolStart + 1,
            controlNumber = symbolStart + 2,
            markedRoute = symbolStart + 3,
            finish = symbolStart + 4,
            exclusionCircle = symbolStart + 5
        )
        val overlayPart = buildString {
            appendLine("""<part name="${xml(partName)}"><objects count="${objects.size}">""")
            objects.forEach { appendLine(it.toXml(symbols)) }
            appendLine("</objects></part>")
        }
        return baseMapXml
            .replaceFirstCountAttribute("symbols", increment = 6)
            .replaceFirst("</symbols>", courseDesignSymbolsXml(symbols, colors) + "\n</symbols>")
            .replaceFirstPartsCountAndCurrent(increment = 1)
            .replaceFirst("</parts>", overlayPart + "</parts>")
    }

    private fun String.courseOverlayColorIds(): CourseOverlayColorIds {
        fun colorPriorityNamed(pattern: Regex): Int? =
            Regex("""<color\b[^>]*\bpriority="(\d+)"[^>]*\bname="([^"]+)"""")
                .findAll(this)
                .firstOrNull { pattern.containsMatchIn(it.groupValues[2]) }
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()

        val purple = colorPriorityNamed(Regex("""Purple""", RegexOption.IGNORE_CASE)) ?: 0
        val white = colorPriorityNamed(Regex("""White""", RegexOption.IGNORE_CASE)) ?: purple
        val black = colorPriorityNamed(Regex("""Black""", RegexOption.IGNORE_CASE)) ?: purple
        return CourseOverlayColorIds(
            purple = purple,
            blackForControlDescriptions = black,
            whiteAboveFraming = white,
            blackForFraming = black,
            whiteBelowFraming = white,
            black = black
        )
    }

    private fun String.replaceFirstCountAttribute(element: String, increment: Int): String =
        Regex("""<$element\b([^>]*)\bcount="(\d+)"([^>]*)>""")
            .find(this)
            ?.let { match ->
                val newCount = match.groupValues[2].toInt() + increment
                replaceRange(match.range, "<$element${match.groupValues[1]}count=\"$newCount\"${match.groupValues[3]}>")
            }
            ?: this

    private fun String.replaceFirstPartsCountAndCurrent(increment: Int): String =
        Regex("""<parts\b([^>]*)\bcount="(\d+)"([^>]*)>""")
            .find(this)
            ?.let { match ->
                val originalCount = match.groupValues[2].toInt()
                val newCount = originalCount + increment
                val attributes = (match.groupValues[1] + match.groupValues[3])
                    .replace(Regex("""\s*\bcurrent="[^"]*""""), "")
                replaceRange(match.range, """<parts${attributes} count="$newCount" current="$originalCount">""")
            }
            ?: this

    private fun String.safeFileStem(): String =
        trim()
            .map { if (it.isISOControl() || it in """\/:*?"<>|""") ' ' else it }
            .joinToString("")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "Course" }

    private fun xml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}

private data class CourseOverlayData(
    val baseMap: OomBaseMap,
    val start: OverlayPoint?,
    val finish: OverlayPoint?,
    val beacon: OverlayPoint?,
    val spectator: OverlayPoint?,
    val foxes: List<OverlayPoint>
) {
    val hasAnyPoint: Boolean = start != null || finish != null || beacon != null || spectator != null || foxes.isNotEmpty()
    val pointCount: Int = listOfNotNull(start, finish, beacon, spectator).size + foxes.size

    companion object {
        fun from(
            projectFile: EventProjectFile,
            courseInfos: Collection<ProtectedCourseInfo>,
            baseMap: OomBaseMap
        ): CourseOverlayData {
            val controlsById = projectFile.raceData.controls.associateBy { it.id }
            val courseObjects = courseInfos.flatMap { it.courseObjects }.dedupeCourseObjects()
            val courseObjectControlIds = courseObjects.mapTo(mutableSetOf()) { it.id }
            val controlPoints = courseInfos
                .flatMap { it.controlPoints }
                .filterNot { it.controlId in courseObjectControlIds }
                .distinctBy { "${it.controlId}:${it.type}" }

            fun objectPoint(type: ProtectedCourseObjectType): OverlayPoint? =
                courseObjects.firstOrNull { it.type == type }?.toOverlayPoint(baseMap, controlsById)

            val objectControlPoints = courseObjects
                .filter { it.type == ProtectedCourseObjectType.CONTROL }
                .map { it.toOverlayPoint(baseMap, controlsById) }
            val protectedControlPoints = controlPoints
                .filter { it.type == ControlPointType.CONTROL }
                .map { it.toOverlayPoint(baseMap, controlsById) }
            val foxes = (objectControlPoints + protectedControlPoints).distinctBy { it.id }
                .sortedWith(compareBy<OverlayPoint> { it.label.toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it.label })

            return CourseOverlayData(
                baseMap = baseMap,
                start = objectPoint(ProtectedCourseObjectType.START),
                finish = objectPoint(ProtectedCourseObjectType.FINISH),
                beacon = objectPoint(ProtectedCourseObjectType.BEACON)
                    ?: controlPoints.firstOrNull { it.type == ControlPointType.BEACON }?.toOverlayPoint(baseMap, controlsById),
                spectator = objectPoint(ProtectedCourseObjectType.SPECTATOR)
                    ?: controlPoints.firstOrNull { it.type == ControlPointType.SEPARATOR }?.toOverlayPoint(baseMap, controlsById),
                foxes = foxes
            )
        }

        private fun List<ProtectedCourseObjectPoint>.dedupeCourseObjects(): List<ProtectedCourseObjectPoint> =
            fold(emptyList()) { exported, candidate ->
                if (exported.any { it.sameOverlayObjectAs(candidate) }) exported else exported + candidate
            }

        private fun ProtectedCourseObjectPoint.sameOverlayObjectAs(other: ProtectedCourseObjectPoint): Boolean {
            if (id == other.id && type == other.type) {
                return true
            }
            return type == other.type &&
                label.trim().equals(other.label.trim(), ignoreCase = true) &&
                CourseGeoPoint(latitude, longitude).distanceMetersTo(CourseGeoPoint(other.latitude, other.longitude)) <= 5.0
        }

        private fun ProtectedCourseObjectPoint.toOverlayPoint(
            baseMap: OomBaseMap,
            controlsById: Map<String, EventControl>
        ): OverlayPoint =
            OverlayPoint(
                id = id,
                label = controlsById[id]?.displayCourseLabel() ?: label,
                mapPoint = baseMap.mapPoint(latitude, longitude)
            )

        private fun ProtectedCourseControlPoint.toOverlayPoint(
            baseMap: OomBaseMap,
            controlsById: Map<String, EventControl>
        ): OverlayPoint =
            OverlayPoint(
                id = controlId,
                label = controlsById[controlId]?.displayCourseLabel() ?: label,
                mapPoint = baseMap.mapPoint(latitude, longitude)
            )

        private fun EventControl.displayCourseLabel(): String =
            publicLabel?.takeIf { it.isNotBlank() } ?: label
    }
}

private data class OverlayPoint(
    val id: String,
    val label: String,
    val mapPoint: OomMapPoint
)

private data class OomBaseMap(
    val xml: String,
    val georeferencingXml: String,
    val scale: Double,
    val combinedScaleFactor: Double,
    val declinationDegrees: Double,
    val grivationDegrees: Double,
    val mapReferencePoint: OomMapPoint,
    val projectedReferencePoint: ProjectedPoint,
    val utmZone: Int,
    val utmNorth: Boolean
) {
    fun mapPoint(latitude: Double, longitude: Double): OomMapPoint {
        val projected = UTM.project(latitude, longitude, utmZone, utmNorth)
        val eastDelta = projected.x - projectedReferencePoint.x
        val northDelta = projected.y - projectedReferencePoint.y
        val rotation = Math.toRadians(-declinationDegrees)
        val mapEastMeters = eastDelta * cos(rotation) - northDelta * sin(rotation)
        val mapNorthMeters = eastDelta * sin(rotation) + northDelta * cos(rotation)
        val unitsPerMeter = 1_000_000.0 / (scale * combinedScaleFactor)
        return OomMapPoint(
            x = (mapReferencePoint.x + mapEastMeters * unitsPerMeter).roundToInt(),
            y = (mapReferencePoint.y - mapNorthMeters * unitsPerMeter).roundToInt()
        )
    }

    fun circle(center: OomMapPoint, radiusMeters: Int): List<OomMapPoint> {
        val radiusUnits = radiusMeters * 1_000_000.0 / (scale * combinedScaleFactor)
        val points = (0 until 96).map { index ->
            val angle = 2.0 * PI * index / 96.0
            OomMapPoint(
                x = (center.x + radiusUnits * cos(angle)).roundToInt(),
                y = (center.y + radiusUnits * sin(angle)).roundToInt()
            )
        }
        return points + points.first()
    }

    companion object {
        fun read(path: Path): OomBaseMap {
            val text = Files.readString(path)
            val georeferencingXml = Regex("""<georeferencing\b[\s\S]*?</georeferencing>""")
                .find(text)
                ?.value
                ?: throw IllegalArgumentException("Base OOM map does not contain georeferencing metadata.")
            val scale = georeferencingXml.attributeDouble("scale")
                ?: throw IllegalArgumentException("Base OOM map georeferencing does not specify a scale.")
            val mapRef = Regex("<georeferencing\\b[^>]*>[\\s\\S]*?<ref_point\\s+x=\"([^\"]+)\"\\s+y=\"([^\"]+)\"")
                .find(georeferencingXml)
                ?.let { OomMapPoint((it.groupValues[1].toDouble() * 1000.0).roundToInt(), (it.groupValues[2].toDouble() * 1000.0).roundToInt()) }
                ?: throw IllegalArgumentException("Base OOM map must have a map reference point.")
            val projectedBlock = Regex("""<projected_crs\b[\s\S]*?</projected_crs>""")
                .find(georeferencingXml)
                ?.value
                ?: throw IllegalArgumentException("Base OOM map must use a projected CRS.")
            val projectedRef = Regex("<ref_point\\s+x=\"([^\"]+)\"\\s+y=\"([^\"]+)\"")
                .find(projectedBlock)
                ?.let { ProjectedPoint(it.groupValues[1].toDouble(), it.groupValues[2].toDouble()) }
                ?: throw IllegalArgumentException("Base OOM map projected CRS must have a reference point.")
            val proj4 = Regex("""<spec\s+language="PROJ\.4">([^<]+)</spec>""")
                .find(projectedBlock)
                ?.groupValues
                ?.get(1)
                ?: throw IllegalArgumentException("Base OOM map projected CRS must include a PROJ.4 spec.")
            val zone = Regex("""(?:^|\s)\+zone=(\d+)""")
                .find(proj4)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
                ?: throw IllegalArgumentException("Only WGS84 UTM base OOM maps are supported for course overlay export.")
            require(proj4.contains("+proj=utm") && proj4.contains("+datum=WGS84")) {
                "Only WGS84 UTM base OOM maps are supported for course overlay export."
            }
            val parameter = Regex("""<parameter>([^<]+)</parameter>""")
                .find(projectedBlock)
                ?.groupValues
                ?.get(1)
                .orEmpty()
            val isSouth = proj4.contains("+south") || parameter.uppercase(Locale.US).contains(" S")
            return OomBaseMap(
                xml = text,
                georeferencingXml = georeferencingXml,
                scale = scale,
                combinedScaleFactor = georeferencingXml.attributeDouble("grid_scale_factor") ?: 1.0,
                declinationDegrees = georeferencingXml.attributeDouble("declination") ?: georeferencingXml.attributeDouble("grivation") ?: 0.0,
                grivationDegrees = georeferencingXml.attributeDouble("grivation") ?: 0.0,
                mapReferencePoint = mapRef,
                projectedReferencePoint = projectedRef,
                utmZone = zone,
                utmNorth = !isSouth
            )
        }

        private fun String.attributeDouble(name: String): Double? =
            Regex("\\b${Regex.escape(name)}=\"([^\"]+)\"")
                .find(this)
                ?.groupValues
                ?.get(1)
                ?.toDoubleOrNull()
    }
}

private data class OomMapPoint(val x: Int, val y: Int) {
    fun labelPoint(): OomMapPoint = OomMapPoint(x + 4200, y - 4200)
}

private data class ProjectedPoint(val x: Double, val y: Double)

private object UTM {
    fun project(latitude: Double, longitude: Double, zone: Int, north: Boolean): ProjectedPoint {
        require(latitude in -80.0..84.0) { "UTM projection supports latitudes from 80 S to 84 N." }
        val a = 6378137.0
        val f = 1.0 / 298.257223563
        val k0 = 0.9996
        val e2 = f * (2.0 - f)
        val ep2 = e2 / (1.0 - e2)
        val lat = Math.toRadians(latitude)
        val lon = Math.toRadians(longitude)
        val lonOrigin = Math.toRadians((zone - 1) * 6 - 180 + 3.0)
        val n = a / sqrt(1.0 - e2 * sin(lat).pow(2.0))
        val t = tan(lat).pow(2.0)
        val c = ep2 * cos(lat).pow(2.0)
        val aa = cos(lat) * (lon - lonOrigin)
        val m = a * (
            (1.0 - e2 / 4.0 - 3.0 * e2.pow(2.0) / 64.0 - 5.0 * e2.pow(3.0) / 256.0) * lat -
                (3.0 * e2 / 8.0 + 3.0 * e2.pow(2.0) / 32.0 + 45.0 * e2.pow(3.0) / 1024.0) * sin(2.0 * lat) +
                (15.0 * e2.pow(2.0) / 256.0 + 45.0 * e2.pow(3.0) / 1024.0) * sin(4.0 * lat) -
                (35.0 * e2.pow(3.0) / 3072.0) * sin(6.0 * lat)
            )
        val easting = k0 * n * (
            aa + (1.0 - t + c) * aa.pow(3.0) / 6.0 +
                (5.0 - 18.0 * t + t.pow(2.0) + 72.0 * c - 58.0 * ep2) * aa.pow(5.0) / 120.0
            ) + 500_000.0
        var northing = k0 * (
            m + n * tan(lat) * (
                aa.pow(2.0) / 2.0 +
                    (5.0 - t + 9.0 * c + 4.0 * c.pow(2.0)) * aa.pow(4.0) / 24.0 +
                    (61.0 - 58.0 * t + t.pow(2.0) + 600.0 * c - 330.0 * ep2) * aa.pow(6.0) / 720.0
                )
            )
        if (!north && latitude < 0.0) {
            northing += 10_000_000.0
        }
        return ProjectedPoint(easting, northing)
    }
}

private enum class OomSymbol(val id: Int) {
    Start(1),
    Control(2),
    ControlNumber(3),
    MarkedRoute(5),
    Finish(6),
    ExclusionCircle(209)
}

private data class CourseOverlaySymbolIds(
    val start: Int,
    val control: Int,
    val controlNumber: Int,
    val markedRoute: Int,
    val finish: Int,
    val exclusionCircle: Int
) {
    fun idFor(symbol: OomSymbol): Int =
        when (symbol) {
            OomSymbol.Start -> start
            OomSymbol.Control -> control
            OomSymbol.ControlNumber -> controlNumber
            OomSymbol.MarkedRoute -> markedRoute
            OomSymbol.Finish -> finish
            OomSymbol.ExclusionCircle -> exclusionCircle
        }
}

private val StandaloneCourseOverlaySymbolIds = CourseOverlaySymbolIds(
    start = 1,
    control = 2,
    controlNumber = 3,
    markedRoute = 5,
    finish = 6,
    exclusionCircle = 209
)

private data class CourseOverlayColorIds(
    val purple: Int,
    val blackForControlDescriptions: Int,
    val whiteAboveFraming: Int,
    val blackForFraming: Int,
    val whiteBelowFraming: Int,
    val black: Int
)

private val StandaloneCourseOverlayColorIds = CourseOverlayColorIds(
    purple = 0,
    blackForControlDescriptions = 1,
    whiteAboveFraming = 2,
    blackForFraming = 3,
    whiteBelowFraming = 4,
    black = 5
)

private enum class OverlayObjectKind {
    Point,
    Label,
    FinishCorridor,
    ExclusionCircle
}

private data class OverlayObject(
    val symbol: OomSymbol,
    val type: Int,
    val points: List<OomMapPoint>,
    val text: String?,
    val kind: OverlayObjectKind
) {
    fun toXml(symbolIds: CourseOverlaySymbolIds): String {
        val attributes = if (type == 4) {
            """ h_align="1" v_align="2""""
        } else {
            ""
        }
        val textXml = text?.let { "<text>${xml(it)}</text>" }.orEmpty()
        return """<object type="$type" symbol="${symbolIds.idFor(symbol)}"$attributes><coords count="${points.size}">${coordsText()}</coords>$textXml</object>"""
    }

    private fun coordsText(): String =
        points.mapIndexed { index, point ->
            val closeFlag = if (kind == OverlayObjectKind.ExclusionCircle && index == points.lastIndex) " 18" else ""
            "${point.x} ${point.y}$closeFlag"
        }.joinToString(separator = ";", postfix = ";")

    private fun xml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    companion object {
        fun point(symbol: OomSymbol, point: OomMapPoint): OverlayObject =
            OverlayObject(symbol, type = 0, points = listOf(point), text = null, kind = OverlayObjectKind.Point)

        fun text(point: OomMapPoint, text: String): OverlayObject =
            OverlayObject(OomSymbol.ControlNumber, type = 4, points = listOf(point), text = text, kind = OverlayObjectKind.Label)

        fun line(symbol: OomSymbol, points: List<OomMapPoint>, kind: OverlayObjectKind): OverlayObject =
            OverlayObject(symbol, type = 1, points = points, text = null, kind = kind)
    }
}

private fun courseDesignColorsXml(colors: CourseOverlayColorIds): String =
    """
    <colors count="6">
    <color priority="${colors.purple}" name="Radio-Oracle Purple" c="0.2" m="1" y="0" k="0" opacity="1"><spotcolors><namedcolor>PURPLE</namedcolor></spotcolors><cmyk method="custom"/><rgb method="cmyk" r="0.8" g="0" b="1"/></color>
    <color priority="${colors.blackForControlDescriptions}" name="Radio-Oracle Black for control descriptions" c="0" m="0" y="0" k="1" opacity="1"><spotcolors knockout="true"><component factor="1" spotcolor="${colors.black}"/></spotcolors><cmyk method="spotcolor"/><rgb method="spotcolor" r="0" g="0" b="0"/></color>
    <color priority="${colors.whiteAboveFraming}" name="Radio-Oracle White above framing" c="0" m="0" y="0" k="0" opacity="1"><spotcolors knockout="true"><component factor="0" spotcolor="${colors.black}"/></spotcolors><cmyk method="spotcolor"/><rgb method="spotcolor" r="1" g="1" b="1"/></color>
    <color priority="${colors.blackForFraming}" name="Radio-Oracle Black for framing" c="0" m="0" y="0" k="1" opacity="1"><spotcolors knockout="true"><component factor="1" spotcolor="${colors.black}"/></spotcolors><cmyk method="spotcolor"/><rgb method="spotcolor" r="0" g="0" b="0"/></color>
    <color priority="${colors.whiteBelowFraming}" name="Radio-Oracle White below framing" c="0" m="0" y="0" k="0" opacity="1"><spotcolors knockout="true"><component factor="0" spotcolor="${colors.black}"/></spotcolors><cmyk method="spotcolor"/><rgb method="spotcolor" r="1" g="1" b="1"/></color>
    <color priority="${colors.black}" name="Radio-Oracle Black" c="0" m="0" y="0" k="1" opacity="1"><spotcolors knockout="true"><namedcolor>BLACK</namedcolor></spotcolors><cmyk method="custom"/><rgb method="cmyk" r="0" g="0" b="0"/></color>
    </colors>
    """.trimIndent()

private fun courseDesignSymbolsXml(symbols: CourseOverlaySymbolIds, colors: CourseOverlayColorIds): String =
    """
    <symbol type="1" id="${symbols.start}" code="701" name="Radio-Oracle Start"><description>The start or map issue point (if not at the start) is shown by an equilateral triangle which points in the direction of the first control. The centre of the triangle shows the precise position of the start point.</description><point_symbol rotatable="true" inner_radius="1000" inner_color="-1" outer_width="0" outer_color="-1" elements="1"><element><symbol type="2" code=""><line_symbol color="${colors.purple}" line_width="350" minimum_length="0" join_style="1" cap_style="0" start_offset="0" end_offset="0" segment_length="4000" end_length="0" show_at_least_one_symbol="true" minimum_mid_symbol_count="0" minimum_mid_symbol_count_when_closed="0" dash_length="4000" break_length="1000" dashes_in_group="1" in_group_break_length="500" mid_symbols_per_spot="1" mid_symbol_distance="0"/></symbol><object type="1"><coords count="4">-1557 2697;3114 0;-1557 -2697;-1557 2697 18;</coords><pattern rotation="0"><coord x="0" y="0"/></pattern></object></element></point_symbol></symbol>
    <symbol type="1" id="${symbols.control}" code="702" name="Radio-Oracle Control point"><description>The control points are shown with circles. The centre of the circle shows the precise position of the feature. Sections of circles should be omitted to leave important detail showing.</description><point_symbol inner_radius="2825" inner_color="-1" outer_width="350" outer_color="${colors.purple}" elements="0"/></symbol>
    <symbol type="8" id="${symbols.controlNumber}" code="703" name="Radio-Oracle Control number"><description>The number or name of the control is placed close to the control point circle in such a way that it does not obscure important detail.</description><text_symbol icon_text="5"><font family="Arial" size="5500"/><text color="${colors.purple}" line_spacing="1" paragraph_spacing="0" character_spacing="0" kerning="true"/><framing color="${colors.whiteAboveFraming}" mode="1" line_half_width="100" shadow_x_offset="200" shadow_y_offset="200"/></text_symbol></symbol>
    <symbol type="2" id="${symbols.markedRoute}" code="705" name="Radio-Oracle Marked route"><description>A marked route is shown on the map with a dashed line.</description><line_symbol color="${colors.purple}" line_width="350" minimum_length="0" join_style="1" cap_style="0" start_offset="0" end_offset="0" dashed="true" segment_length="4000" end_length="0" show_at_least_one_symbol="true" minimum_mid_symbol_count="0" minimum_mid_symbol_count_when_closed="0" dash_length="2000" break_length="500" dashes_in_group="1" in_group_break_length="500" mid_symbols_per_spot="1" mid_symbol_distance="0"/></symbol>
    <symbol type="1" id="${symbols.finish}" code="706" name="Radio-Oracle Finish"><description>The finish is shown by two concentric circles.</description><point_symbol inner_radius="2325" inner_color="-1" outer_width="350" outer_color="${colors.purple}" elements="1"><element><symbol type="1" code=""><point_symbol inner_radius="3325" inner_color="-1" outer_width="350" outer_color="${colors.purple}" elements="0"/></symbol><object type="0"><coords count="1">0 0;</coords></object></element></point_symbol></symbol>
    <symbol type="2" id="${symbols.exclusionCircle}" code="RO.1" name="Radio-Oracle ARDF exclusion circle"><description>Radio-Oracle ARDF exclusion-zone circle using the ISOM course-object purple and official course-symbol line width.</description><line_symbol color="${colors.purple}" line_width="350" minimum_length="0" join_style="1" cap_style="0" start_offset="0" end_offset="0" segment_length="4000" end_length="0" show_at_least_one_symbol="true" minimum_mid_symbol_count="0" minimum_mid_symbol_count_when_closed="0" dash_length="4000" break_length="1000" dashes_in_group="1" in_group_break_length="500" mid_symbols_per_spot="1" mid_symbol_distance="0"/></symbol>
    """.trimIndent()
