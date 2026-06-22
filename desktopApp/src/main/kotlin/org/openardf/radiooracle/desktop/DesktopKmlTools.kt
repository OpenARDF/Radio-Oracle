package org.openardf.radiooracle.desktop

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

data class DesktopKmlToolsPoint(
    val latitude: Double,
    val longitude: Double
)

data class DesktopKmlMoveCourseResult(
    val sourcePath: Path,
    val outputPath: Path,
    val originalStart: DesktopKmlToolsPoint,
    val newStart: DesktopKmlToolsPoint,
    val translatedCoordinateCount: Int
)

object DesktopKmlTools {
    fun moveCourse(
        sourcePath: Path,
        newStart: DesktopKmlToolsPoint
    ): DesktopKmlMoveCourseResult {
        validatePoint(newStart, "New Start")
        val fileName = sourcePath.fileName.toString()
        require(fileName.endsWith(".kml", ignoreCase = true) || fileName.endsWith(".kmz", ignoreCase = true)) {
            "Choose a .kml or .kmz file."
        }
        val outputPath = newOutputPath(sourcePath)
        return if (fileName.endsWith(".kmz", ignoreCase = true)) {
            moveKmzCourse(sourcePath, outputPath, newStart)
        } else {
            val translated = translateKml(Files.readString(sourcePath), newStart)
            writeTextAtomically(outputPath, translated.kmlText)
            DesktopKmlMoveCourseResult(
                sourcePath = sourcePath,
                outputPath = outputPath,
                originalStart = translated.originalStart,
                newStart = newStart,
                translatedCoordinateCount = translated.translatedCoordinateCount
            )
        }
    }

    private fun moveKmzCourse(
        sourcePath: Path,
        outputPath: Path,
        newStart: DesktopKmlToolsPoint
    ): DesktopKmlMoveCourseResult {
        var translatedResult: TranslatedKml? = null
        writeZipAtomically(outputPath) { output ->
            ZipInputStream(Files.newInputStream(sourcePath)).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    val nextEntry = ZipEntry(entry.name).also { copy ->
                        copy.comment = entry.comment
                        copy.time = entry.time
                    }
                    output.putNextEntry(nextEntry)
                    if (!entry.isDirectory && entry.name.endsWith(".kml", ignoreCase = true) && translatedResult == null) {
                        val translated = translateKml(input.readBytes().toString(StandardCharsets.UTF_8), newStart)
                        output.write(translated.kmlText.toByteArray(StandardCharsets.UTF_8))
                        translatedResult = translated
                    } else if (!entry.isDirectory) {
                        input.copyTo(output)
                    }
                    output.closeEntry()
                    input.closeEntry()
                }
            }
            if (translatedResult == null) {
                throw IllegalArgumentException("KMZ file did not contain a KML document.")
            }
        }
        val translated = requireNotNull(translatedResult)
        return DesktopKmlMoveCourseResult(
            sourcePath = sourcePath,
            outputPath = outputPath,
            originalStart = translated.originalStart,
            newStart = newStart,
            translatedCoordinateCount = translated.translatedCoordinateCount
        )
    }

    private fun translateKml(kmlText: String, newStart: DesktopKmlToolsPoint): TranslatedKml {
        val document = secureDocumentBuilderFactory()
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(kmlText.toByteArray(StandardCharsets.UTF_8)))
        val originalStart = findStartPoint(document)
            ?: throw IllegalArgumentException("KML/KMZ file did not contain a Point Placemark named Start.")
        val latitudeDelta = newStart.latitude - originalStart.latitude
        val longitudeDelta = newStart.longitude - originalStart.longitude
        val translatedCoordinateCount = translateCoordinatesElements(document, latitudeDelta, longitudeDelta) +
            translateGxCoordElements(document, latitudeDelta, longitudeDelta)
        require(translatedCoordinateCount > 0) {
            "KML/KMZ file did not contain coordinates to move."
        }
        return TranslatedKml(
            kmlText = documentToString(document),
            originalStart = originalStart,
            translatedCoordinateCount = translatedCoordinateCount
        )
    }

    private fun findStartPoint(document: Document): DesktopKmlToolsPoint? {
        val placemarks = document.getElementsByTagNameNS("*", "Placemark")
        repeat(placemarks.length) { index ->
            val placemark = placemarks.item(index) as? Element ?: return@repeat
            val name = placemark.directChildText("name")?.trim().orEmpty()
            if (!name.equals("Start", ignoreCase = true)) {
                return@repeat
            }
            val coordinates = placemark
                .firstDescendantElement("Point")
                ?.firstDescendantElement("coordinates")
                ?.textContent
                ?.let(::parseKmlCoordinates)
                ?.firstOrNull()
            if (coordinates != null) {
                return coordinates
            }
        }
        return null
    }

    private fun translateCoordinatesElements(
        document: Document,
        latitudeDelta: Double,
        longitudeDelta: Double
    ): Int {
        var count = 0
        val coordinates = document.getElementsByTagNameNS("*", "coordinates")
        repeat(coordinates.length) { index ->
            val element = coordinates.item(index) as? Element ?: return@repeat
            val parsed = translateKmlCoordinates(element.textContent.orEmpty(), latitudeDelta, longitudeDelta)
            if (parsed.count > 0) {
                element.textContent = parsed.text
                count += parsed.count
            }
        }
        return count
    }

    private fun translateGxCoordElements(
        document: Document,
        latitudeDelta: Double,
        longitudeDelta: Double
    ): Int {
        var count = 0
        val coordinates = document.getElementsByTagNameNS("*", "coord")
        repeat(coordinates.length) { index ->
            val element = coordinates.item(index) as? Element ?: return@repeat
            val parsed = translateGxCoord(element.textContent.orEmpty(), latitudeDelta, longitudeDelta)
            if (parsed != null) {
                element.textContent = parsed
                count++
            }
        }
        return count
    }

    private fun translateKmlCoordinates(
        value: String,
        latitudeDelta: Double,
        longitudeDelta: Double
    ): TranslatedCoordinates {
        val tokens = value.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val translated = tokens.mapNotNull { token ->
            val fields = token.split(',')
            val longitude = fields.getOrNull(0)?.toDoubleOrNull() ?: return@mapNotNull null
            val latitude = fields.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
            val nextPoint = DesktopKmlToolsPoint(
                latitude = latitude + latitudeDelta,
                longitude = longitude + longitudeDelta
            )
            validatePoint(nextPoint, "Translated coordinate")
            listOf(
                coordinateNumber(nextPoint.longitude),
                coordinateNumber(nextPoint.latitude)
            ).plus(fields.drop(2)).joinToString(",")
        }
        return TranslatedCoordinates(translated.joinToString(" "), translated.size)
    }

    private fun parseKmlCoordinates(value: String): List<DesktopKmlToolsPoint> =
        value
            .trim()
            .split(Regex("\\s+"))
            .mapNotNull { token ->
                val fields = token.split(',')
                val longitude = fields.getOrNull(0)?.toDoubleOrNull()
                val latitude = fields.getOrNull(1)?.toDoubleOrNull()
                if (latitude == null || longitude == null) {
                    null
                } else {
                    DesktopKmlToolsPoint(latitude = latitude, longitude = longitude)
                }
            }

    private fun translateGxCoord(
        value: String,
        latitudeDelta: Double,
        longitudeDelta: Double
    ): String? {
        val fields = value.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val longitude = fields.getOrNull(0)?.toDoubleOrNull() ?: return null
        val latitude = fields.getOrNull(1)?.toDoubleOrNull() ?: return null
        val nextPoint = DesktopKmlToolsPoint(
            latitude = latitude + latitudeDelta,
            longitude = longitude + longitudeDelta
        )
        validatePoint(nextPoint, "Translated gx:coord")
        return listOf(
            coordinateNumber(nextPoint.longitude),
            coordinateNumber(nextPoint.latitude)
        ).plus(fields.drop(2)).joinToString(" ")
    }

    private fun newOutputPath(sourcePath: Path): Path {
        val fileName = sourcePath.fileName.toString()
        val extensionIndex = fileName.lastIndexOf('.').takeIf { it > 0 } ?: fileName.length
        return sourcePath.resolveSibling("${fileName.substring(0, extensionIndex)}_NEW${fileName.substring(extensionIndex)}")
    }

    private fun validatePoint(point: DesktopKmlToolsPoint, label: String) {
        require(point.latitude in -90.0..90.0) { "$label latitude must be between -90 and 90." }
        require(point.longitude in -180.0..180.0) { "$label longitude must be between -180 and 180." }
    }

    private fun writeTextAtomically(outputPath: Path, text: String) {
        val tempPath = Files.createTempFile(outputPath.outputDirectory(), "${outputPath.fileName}.", ".tmp")
        try {
            Files.writeString(tempPath, text, StandardCharsets.UTF_8)
            Files.move(tempPath, outputPath, StandardCopyOption.REPLACE_EXISTING)
        } catch (error: Throwable) {
            Files.deleteIfExists(tempPath)
            throw error
        }
    }

    private fun writeZipAtomically(outputPath: Path, writeEntries: (ZipOutputStream) -> Unit) {
        val tempPath = Files.createTempFile(outputPath.outputDirectory(), "${outputPath.fileName}.", ".tmp")
        try {
            ZipOutputStream(Files.newOutputStream(tempPath)).use(writeEntries)
            Files.move(tempPath, outputPath, StandardCopyOption.REPLACE_EXISTING)
        } catch (error: Throwable) {
            Files.deleteIfExists(tempPath)
            throw error
        }
    }

    private fun coordinateNumber(value: Double): String =
        DecimalFormat("0.########", DecimalFormatSymbols(Locale.US)).format(value)

    private fun secureDocumentBuilderFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().also { factory ->
            factory.isNamespaceAware = true
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            factory.isXIncludeAware = false
            factory.isExpandEntityReferences = false
        }

    private fun documentToString(document: Document): String {
        val output = ByteArrayOutputStream()
        val factory = TransformerFactory.newInstance().also { transformerFactory ->
            transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            runCatching { transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "") }
            runCatching { transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "") }
        }
        factory.newTransformer().also { transformer ->
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            transformer.setOutputProperty(OutputKeys.INDENT, "yes")
        }.transform(DOMSource(document), StreamResult(output))
        return output.toString(StandardCharsets.UTF_8)
    }

    private fun Path.outputDirectory(): Path =
        parent ?: Path.of(".").toAbsolutePath()
}

private data class TranslatedKml(
    val kmlText: String,
    val originalStart: DesktopKmlToolsPoint,
    val translatedCoordinateCount: Int
)

private data class TranslatedCoordinates(
    val text: String,
    val count: Int
)

private fun Element.directChildText(localName: String): String? =
    childElements().firstOrNull { it.effectiveLocalName() == localName }?.textContent

private fun Element.firstDescendantElement(localName: String): Element? {
    childElements().forEach { child ->
        if (child.effectiveLocalName() == localName) {
            return child
        }
        child.firstDescendantElement(localName)?.let { return it }
    }
    return null
}

private fun Element.childElements(): List<Element> =
    (0 until childNodes.length)
        .mapNotNull { childNodes.item(it) as? Element }

private fun Node.effectiveLocalName(): String =
    localName ?: nodeName.substringAfter(':')
