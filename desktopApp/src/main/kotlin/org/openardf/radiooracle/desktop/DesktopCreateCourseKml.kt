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

import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.EventControl
import org.openardf.radiooracle.shared.event.EventProjectFile
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.PI

data class DesktopCreateCourseKmlResult(
    val outputPath: Path,
    val eventType: RaceType,
    val pointCount: Int,
    val reusedControlCount: Int
)

object DesktopCreateCourseKml {
    private const val M21_ROUTE_STYLE_ID = "courseRoute-cat-m21"
    private const val COURSE_ROUTE_STYLE_LINE_WIDTH = 4

    fun create(
        outputPath: Path,
        eventType: RaceType,
        center: DesktopKmlToolsPoint,
        projectFile: EventProjectFile?
    ): DesktopCreateCourseKmlResult {
        require(eventType in supportedEventTypes) {
            "Create Course supports Classic, Sprint, and Foxoring."
        }
        validatePoint(center, "Course location")
        val controls = projectFile
            ?.takeIf { it.raceData.race.raceType == eventType }
            ?.raceData
            ?.controls
            .orEmpty()
        val elements = courseElements(eventType, controls)
        val text = kmlText(eventType, center, elements)
        writeTextAtomically(outputPath, text)
        return DesktopCreateCourseKmlResult(
            outputPath = outputPath,
            eventType = eventType,
            pointCount = elements.size,
            reusedControlCount = elements.count { it.control != null }
        )
    }

    fun defaultFileName(eventName: String?, eventType: RaceType): String {
        val prefix = eventName
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { DesktopProjectFilePaths.defaultCsvFileName(it, "create ${eventType.createCourseTypeLabel()} course") }
            ?.removeSuffix(DesktopProjectFilePaths.CSV_EXTENSION)
            ?: "Create ${eventType.createCourseTypeLabel()} Course"
        return DesktopProjectFilePaths.withKmlExtension(Path.of(prefix)).fileName.toString()
    }

    private fun courseElements(eventType: RaceType, controls: List<EventControl>): List<CreateCourseElement> {
        val matcher = EventControlMatcher(controls)
        val orderedLabels = when (eventType) {
            RaceType.SPRINT -> buildList {
                add("Start")
                addAll((1..5).map { it.toString() })
                add("Spectator")
                addAll((1..5).map { "${it}F" })
                add("B")
                add("Finish")
            }
            RaceType.FOXORING -> listOf("Start") + (1..10).map { it.toString() } + listOf("B", "Finish")
            RaceType.CLASSIC -> listOf("Start") + (1..5).map { it.toString() } + listOf("B", "Finish")
            else -> emptyList()
        }
        val offsets = clusteredOffsetsMeters(orderedLabels.size)
        return orderedLabels.mapIndexed { index, label ->
            val kind = elementKind(label)
            CreateCourseElement(
                fallbackLabel = label,
                kind = kind,
                offsetEastMeters = offsets[index].first,
                offsetNorthMeters = offsets[index].second,
                control = matcher.match(label, kind)
            )
        }
    }

    private fun clusteredOffsetsMeters(count: Int): List<Pair<Double, Double>> =
        List(count) { index ->
            val angle = (index * 137.5) * PI / 180.0
            val radius = 55.0 + (index % 4) * 35.0 + (index / 4) * 8.0
            cos(angle) * radius to kotlin.math.sin(angle) * radius
        }

    private fun elementKind(label: String): CreateCourseElementKind =
        when {
            label == "Start" -> CreateCourseElementKind.Start
            label == "Finish" -> CreateCourseElementKind.Finish
            label == "B" -> CreateCourseElementKind.Beacon
            label == "Spectator" -> CreateCourseElementKind.Spectator
            else -> CreateCourseElementKind.Fox
        }

    private fun kmlText(
        eventType: RaceType,
        center: DesktopKmlToolsPoint,
        elements: List<CreateCourseElement>
    ): String {
        val points = elements.map { element ->
            element to element.pointNear(center)
        }
        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<kml xmlns="http://www.opengis.net/kml/2.2">""")
            appendLine("  <Document>")
            appendLine("    <name>Radio-Oracle ${xml(eventType.createCourseTypeLabel())} course starter</name>")
            appendLine("    <open>1</open>")
            append(DesktopCourseKmlStyle.pointStyleDefinitions(includeWaypoint = false))
            appendLine("    <Style id=\"$M21_ROUTE_STYLE_ID\">")
            appendLine("      <LineStyle>")
            appendLine("        <color>ff0066cc</color>")
            appendLine("        <width>$COURSE_ROUTE_STYLE_LINE_WIDTH</width>")
            appendLine("      </LineStyle>")
            appendLine("    </Style>")
            appendLine("    <Folder>")
            appendLine("      <name>Instructions</name>")
            appendLine("      <Placemark>")
            appendLine("        <name>How to use this starter course</name>")
            appendLine("        <description>${xml(instructions(eventType))}</description>")
            appendLine("      </Placemark>")
            appendLine("    </Folder>")
            appendLine("    <Folder>")
            appendLine("      <name>Courses</name>")
            appendM21Route(points)
            points.forEach { (element, point) ->
                appendPointPlacemark(element, point)
            }
            appendLine("    </Folder>")
            appendLine("  </Document>")
            appendLine("</kml>")
        }
    }

    private fun StringBuilder.appendM21Route(points: List<Pair<CreateCourseElement, DesktopKmlToolsPoint>>) {
        appendLine("      <Placemark>")
        appendLine("        <name>M21 route</name>")
        appendLine("        <description>${xml("Example M21 route. Move these vertices or redraw this LineString after placing the real course elements.")}</description>")
        appendLine("        <styleUrl>#$M21_ROUTE_STYLE_ID</styleUrl>")
        appendLine("        <LineString>")
        appendLine("          <tessellate>1</tessellate>")
        appendLine("          <coordinates>")
        points.forEach { (_, point) ->
            appendLine("            ${coordinate(point)}")
        }
        appendLine("          </coordinates>")
        appendLine("        </LineString>")
        appendLine("      </Placemark>")
    }

    private fun StringBuilder.appendPointPlacemark(
        element: CreateCourseElement,
        point: DesktopKmlToolsPoint
    ) {
        val label = element.displayLabel()
        appendLine("      <Placemark>")
        appendLine("        <name>${xml(label)}</name>")
        appendLine("        <description>${xml(element.description())}</description>")
        appendLine("        <ExtendedData>")
        appendLine("          <Data name=\"courseElement\"><value>${xml(element.fallbackLabel)}</value></Data>")
        appendLine("          <Data name=\"type\"><value>${xml(element.kind.name)}</value></Data>")
        element.control?.let { control ->
            appendLine("          <Data name=\"controlId\"><value>${xml(control.id)}</value></Data>")
            appendLine("          <Data name=\"siCode\"><value>${control.siCode}</value></Data>")
            appendLine("          <Data name=\"publicLabel\"><value>${xml(control.publicLabel.orEmpty())}</value></Data>")
            appendLine("          <Data name=\"notes\"><value>${xml(control.notes.orEmpty())}</value></Data>")
        }
        appendLine("        </ExtendedData>")
        appendLine("        <styleUrl>#${element.styleId()}</styleUrl>")
        appendLine("        <Point><coordinates>${coordinate(point)}</coordinates></Point>")
        appendLine("      </Placemark>")
    }

    private fun CreateCourseElement.pointNear(center: DesktopKmlToolsPoint): DesktopKmlToolsPoint {
        val latitude = center.latitude + offsetNorthMeters / 111_320.0
        val longitudeMeters = max(1_000.0, 111_320.0 * cos(center.latitude * PI / 180.0))
        val longitude = center.longitude + offsetEastMeters / longitudeMeters
        val point = DesktopKmlToolsPoint(latitude = latitude, longitude = longitude)
        validatePoint(point, displayLabel())
        return point
    }

    private fun CreateCourseElement.displayLabel(): String =
        control?.publicLabel?.takeIf { it.isNotBlank() } ?: control?.label ?: fallbackLabel

    private fun CreateCourseElement.description(): String =
        buildList {
            control?.let {
                add("SI=${it.siCode}")
                it.publicLabel?.takeIf(String::isNotBlank)?.let { publicLabel -> add("Public label: $publicLabel") }
                it.notes?.takeIf(String::isNotBlank)?.let { notes -> add("Notes: $notes") }
            }
            add("Course element: $fallbackLabel")
            add("Move this point to its real course location.")
        }.joinToString("\n")

    private fun CreateCourseElement.styleId(): String =
        when (kind) {
            CreateCourseElementKind.Start -> DesktopCourseKmlStyle.StartStyleId
            CreateCourseElementKind.Finish -> DesktopCourseKmlStyle.FinishStyleId
            CreateCourseElementKind.Fox,
            CreateCourseElementKind.Beacon,
            CreateCourseElementKind.Spectator -> DesktopCourseKmlStyle.DonutStyleId
        }

    private fun instructions(eventType: RaceType): String =
        """
        This KML is a Radio-Oracle starter file for a ${eventType.createCourseTypeLabel()} race. It contains the standard course elements for the selected race type, clustered near the requested location so they do not overlap. Spectator is included for Sprint only.

        Use Google Earth, OCAD, or another KML editor to move each point to the real course design. Keep the point names recognizable so Radio-Oracle can match them when importing course data.

        The M21 route is only an example LineString connecting Start through the foxes, then Beacon, then Finish. Replace or edit it for the real M21 course, and create additional category route LineStrings for every desired category route.
        """.trimIndent()

    private fun coordinate(point: DesktopKmlToolsPoint): String =
        DesktopExportPrimitives.compactKmlCoordinate(point.longitude, point.latitude, 0.0)

    private fun validatePoint(point: DesktopKmlToolsPoint, label: String) {
        require(point.latitude in -90.0..90.0) { "$label latitude must be between -90 and 90." }
        require(point.longitude in -180.0..180.0) { "$label longitude must be between -180 and 180." }
    }

    private fun writeTextAtomically(outputPath: Path, text: String) {
        outputPath.parent?.let(Files::createDirectories)
        val tempPath = Files.createTempFile(outputPath.parent ?: Path.of("."), "${outputPath.fileName}.", ".tmp")
        try {
            Files.writeString(tempPath, text, StandardCharsets.UTF_8)
            Files.move(tempPath, outputPath, StandardCopyOption.REPLACE_EXISTING)
        } catch (error: Throwable) {
            Files.deleteIfExists(tempPath)
            throw error
        }
    }

    private fun xml(value: String): String =
        DesktopExportPrimitives.xmlText(value)

    val supportedEventTypes: List<RaceType> = listOf(RaceType.CLASSIC, RaceType.SPRINT, RaceType.FOXORING)
}

private class EventControlMatcher(controls: List<EventControl>) {
    private val startNames = setOf("START", "S")
    private val finishNames = setOf("FINISH", "F")
    private val spectatorNames = setOf("SPECTATOR", "SPEC", "SP")
    private val usedControlIds = mutableSetOf<String>()
    private val controlsByNormalizedName = controls.flatMap { control ->
        listOfNotNull(control.label, control.publicLabel)
            .filter { it.isNotBlank() }
            .map { normalizeLabel(it) to control }
    }.toMap()
    private val foxControls = controls
        .filter { it.type == ControlPointType.CONTROL && !it.hasReservedCourseObjectName() }
        .sortedWith(compareBy<EventControl> { it.siCode }.thenBy { it.label })

    fun match(label: String, kind: CreateCourseElementKind): EventControl? {
        val exact = exactMatch(label)?.takeIf { it.kindMatches(kind) }
        if (exact != null) {
            usedControlIds += exact.id
            return exact
        }
        val fallback = when (kind) {
            CreateCourseElementKind.Fox -> foxControls.firstOrNull { it.id !in usedControlIds }
            CreateCourseElementKind.Beacon -> firstUnused { it.type == ControlPointType.BEACON }
            CreateCourseElementKind.Spectator -> firstUnused {
                it.type == ControlPointType.SEPARATOR ||
                    normalizeLabel(it.label) in spectatorNames ||
                    normalizeLabel(it.publicLabel.orEmpty()) in spectatorNames
            }
            CreateCourseElementKind.Start -> firstUnused {
                it.type == ControlPointType.CONTROL &&
                    (normalizeLabel(it.label) in startNames || normalizeLabel(it.publicLabel.orEmpty()) in startNames)
            }
            CreateCourseElementKind.Finish -> firstUnused {
                it.type == ControlPointType.CONTROL &&
                    (normalizeLabel(it.label) in finishNames || normalizeLabel(it.publicLabel.orEmpty()) in finishNames)
            }
        }
        fallback?.let { usedControlIds += it.id }
        return fallback
    }

    private fun exactMatch(label: String): EventControl? =
        controlsByNormalizedName[normalizeLabel(label)]?.takeIf { it.id !in usedControlIds }

    private fun firstUnused(predicate: (EventControl) -> Boolean): EventControl? =
        controlsByNormalizedName.values.distinctBy { it.id }.firstOrNull { it.id !in usedControlIds && predicate(it) }

    private fun EventControl.kindMatches(kind: CreateCourseElementKind): Boolean =
        when (kind) {
            CreateCourseElementKind.Fox -> type == ControlPointType.CONTROL
            CreateCourseElementKind.Beacon -> type == ControlPointType.BEACON
            CreateCourseElementKind.Spectator -> type == ControlPointType.SEPARATOR || normalizeLabel(label) in spectatorNames
            CreateCourseElementKind.Start -> type == ControlPointType.CONTROL &&
                (normalizeLabel(label) in startNames || normalizeLabel(publicLabel.orEmpty()) in startNames)
            CreateCourseElementKind.Finish -> type == ControlPointType.CONTROL &&
                (normalizeLabel(label) in finishNames || normalizeLabel(publicLabel.orEmpty()) in finishNames)
        }

    private fun normalizeLabel(value: String): String =
        value.trim().uppercase().replace(Regex("""\s+"""), "")

    private fun EventControl.hasReservedCourseObjectName(): Boolean {
        val labels = listOf(label, publicLabel.orEmpty()).map(::normalizeLabel)
        return labels.any { it in startNames || it in finishNames || it in spectatorNames }
    }

}

private data class CreateCourseElement(
    val fallbackLabel: String,
    val kind: CreateCourseElementKind,
    val offsetEastMeters: Double,
    val offsetNorthMeters: Double,
    val control: EventControl?
)

private enum class CreateCourseElementKind {
    Start,
    Fox,
    Beacon,
    Finish,
    Spectator
}

internal fun RaceType.createCourseTypeLabel(): String =
    when (this) {
        RaceType.CLASSIC -> "Classic"
        RaceType.SPRINT -> "Sprint"
        RaceType.FOXORING -> "Foxoring"
        else -> name.lowercase().replaceFirstChar { it.titlecase() }
    }
