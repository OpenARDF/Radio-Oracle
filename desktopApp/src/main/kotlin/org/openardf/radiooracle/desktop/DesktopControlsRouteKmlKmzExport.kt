package org.openardf.radiooracle.desktop

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.openardf.radiooracle.shared.event.EventControl
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.ProtectedCourseControlPoint
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.event.ProtectedCourseObjectPoint
import org.openardf.radiooracle.shared.event.ProtectedCourseRoutePoint
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class DesktopControlsRouteKmlKmzExportFormat(
    val contentExtension: String,
    val zipFileSuffix: String
) {
    Kml("kml", ".kml.zip"),
    Kmz("kmz", ".kmz.zip")
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
    fun exportEncryptedZip(
        target: DesktopControlsRouteKmlKmzExportTarget,
        projectFile: EventProjectFile,
        password: String
    ): DesktopControlsRouteKmlKmzExportSummary {
        val trimmedPassword = password.trim()
        require(trimmedPassword.isNotEmpty()) {
            "Event Password cannot be blank."
        }

        val protectedCourseInfoByCategoryId = decryptProtectedCourseInfo(projectFile, trimmedPassword)
        val kml = buildKml(projectFile, protectedCourseInfoByCategoryId)
        val entryBytes = when (target.format) {
            DesktopControlsRouteKmlKmzExportFormat.Kml -> kml.encodeToByteArray()
            DesktopControlsRouteKmlKmzExportFormat.Kmz -> buildKmz(kml)
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
            categoryCount = projectFile.raceData.categories.size,
            routeCount = protectedCourseInfoByCategoryId.values.count { it.route.isNotEmpty() },
            controlCatalogCount = projectFile.raceData.controls.size,
            courseControlPointCount = protectedCourseInfoByCategoryId.values.sumOf { it.controlPoints.size },
            outputFormat = target.format
        )
    }

    private fun decryptProtectedCourseInfo(
        projectFile: EventProjectFile,
        password: String
    ): Map<String, ProtectedCourseInfo> =
        projectFile.raceData.categories.mapNotNull { categoryData ->
            categoryData.category.encryptedCourseInfo
                ?.takeIf { it.isNotBlank() }
                ?.let { encryptedValue ->
                    categoryData.category.id to DesktopProtectedCourseOrder.decryptCourseInfo(encryptedValue, password)
                }
        }.toMap()

    private fun buildKml(
        projectFile: EventProjectFile,
        protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>
    ): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("""<kml xmlns="http://www.opengis.net/kml/2.2">""")
        appendLine("  <Document>")
        appendLine("    <name>${xml(projectFile.raceData.race.name)} controls and routes</name>")
        appendLine("    <open>1</open>")
        appendLine("    <Folder>")
        appendLine("      <name>Control catalog</name>")
        projectFile.raceData.controls.forEach { control ->
            appendControlCatalogPlacemark(control)
        }
        appendLine("    </Folder>")
        appendLine("    <Folder>")
        appendLine("      <name>Category courses</name>")
        projectFile.raceData.categories.forEach { categoryData ->
            val category = categoryData.category
            val courseInfo = protectedCourseInfoByCategoryId[category.id]
            appendLine("      <Folder>")
            appendLine("        <name>${xml(category.name)}</name>")
            appendLine("        <description>${xml(courseDescription(courseInfo))}</description>")
            if (courseInfo != null) {
                appendCourseRoutePlacemark(category.name, courseInfo)
                appendCourseControlPointPlacemarks(courseInfo.controlPoints)
                appendCourseObjectPlacemarks(courseInfo.courseObjects)
            }
            appendLine("      </Folder>")
        }
        appendLine("    </Folder>")
        appendLine("  </Document>")
        appendLine("</kml>")
    }

    private fun StringBuilder.appendControlCatalogPlacemark(control: EventControl) {
        appendLine("      <Placemark>")
        appendLine("        <name>${xml(control.publicLabel?.takeIf { it.isNotBlank() } ?: control.label)}</name>")
        appendLine("        <description>${xml(controlCatalogDescription(control))}</description>")
        appendExtendedData(
            indent = "        ",
            values = listOf(
                "id" to control.id,
                "siCode" to control.siCode.toString(),
                "type" to control.type.name,
                "scored" to control.scored.toString(),
                "publicLabel" to (control.publicLabel ?: ""),
                "notes" to (control.notes ?: "")
            )
        )
        val latitude = control.latitude
        val longitude = control.longitude
        if (latitude != null && longitude != null) {
            appendLine("        <Point><coordinates>${coordinates(longitude, latitude, null)}</coordinates></Point>")
        }
        appendLine("      </Placemark>")
    }

    private fun StringBuilder.appendCourseRoutePlacemark(categoryName: String, courseInfo: ProtectedCourseInfo) {
        if (courseInfo.route.isEmpty()) {
            return
        }
        appendLine("        <Placemark>")
        appendLine("          <name>${xml(categoryName)} route</name>")
        appendLine("          <description>${xml(courseDescription(courseInfo))}</description>")
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

    private fun StringBuilder.appendCourseControlPointPlacemarks(points: List<ProtectedCourseControlPoint>) {
        points.forEach { point ->
            appendLine("        <Placemark>")
            appendLine("          <name>${xml(point.label)}</name>")
            appendLine("          <description>${xml("Course control ${point.label}; type ${point.type}; id ${point.controlId}")}</description>")
            appendExtendedData(
                indent = "          ",
                values = listOf(
                    "controlId" to point.controlId,
                    "label" to point.label,
                    "type" to point.type.name,
                    "elevationMeters" to (point.elevationMeters?.let(::formatNumber) ?: "")
                )
            )
            appendLine("          <Point><coordinates>${coordinates(point.longitude, point.latitude, point.elevationMeters)}</coordinates></Point>")
            appendLine("        </Placemark>")
        }
    }

    private fun StringBuilder.appendCourseObjectPlacemarks(points: List<ProtectedCourseObjectPoint>) {
        points.forEach { point ->
            appendLine("        <Placemark>")
            appendLine("          <name>${xml(point.label)}</name>")
            appendLine("          <description>${xml("Course object ${point.label}; type ${point.type}; id ${point.id}")}</description>")
            appendExtendedData(
                indent = "          ",
                values = listOf(
                    "id" to point.id,
                    "label" to point.label,
                    "type" to point.type.name,
                    "elevationMeters" to (point.elevationMeters?.let(::formatNumber) ?: "")
                )
            )
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

    private fun buildKmz(kml: String): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("doc.kml"))
            zip.write(kml.encodeToByteArray())
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    private fun courseDescription(courseInfo: ProtectedCourseInfo?): String =
        if (courseInfo == null) {
            "No protected KML/KMZ course data is stored for this category."
        } else {
            buildList {
                if (courseInfo.idealOrder.isNotBlank()) {
                    add("Ideal order: ${courseInfo.idealOrder}")
                }
                courseInfo.lengthMeters?.let { add("Length: ${it} m") }
                courseInfo.climbMeters?.let { add("Climb: ${it} m") }
                if (courseInfo.sourceName.isNotBlank()) {
                    add("Source: ${courseInfo.sourceName}")
                }
                if (courseInfo.sourceSha256.isNotBlank()) {
                    add("Source SHA-256: ${courseInfo.sourceSha256}")
                }
            }.joinToString("; ").ifBlank { "Protected course data." }
        }

    private fun controlCatalogDescription(control: EventControl): String =
        buildList {
            add("SI ${control.siCode}")
            add("Type ${control.type}")
            add(if (control.scored) "Scored" else "Not scored")
            control.publicLabel?.takeIf { it.isNotBlank() }?.let { add("Public label: $it") }
            control.notes?.takeIf { it.isNotBlank() }?.let { add("Notes: $it") }
        }.joinToString("; ")

    private fun coordinates(point: ProtectedCourseRoutePoint): String =
        coordinates(point.longitude, point.latitude, point.elevationMeters)

    private fun coordinates(longitude: Double, latitude: Double, elevationMeters: Double?): String =
        listOfNotNull(
            formatNumber(longitude),
            formatNumber(latitude),
            elevationMeters?.let(::formatNumber)
        ).joinToString(",")

    private fun formatNumber(value: Double): String =
        String.format(Locale.US, "%.7f", value).trimEnd('0').trimEnd('.')

    private fun xml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
