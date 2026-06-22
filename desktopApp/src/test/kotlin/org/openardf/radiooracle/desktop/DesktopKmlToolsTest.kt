package org.openardf.radiooracle.desktop

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class DesktopKmlToolsTest {
    @Test
    fun moveCourseWritesTranslatedKmlNextToSource() {
        val source = Files.createTempFile("radio-oracle-move-course", ".kml")
        Files.writeString(source, sampleKml())

        val result = DesktopKmlTools.moveCourse(
            sourcePath = source,
            newStart = DesktopKmlToolsPoint(latitude = 39.01, longitude = -94.99)
        )

        assertEquals(source.resolveSibling("${source.fileName.toString().removeSuffix(".kml")}_NEW.kml"), result.outputPath)
        assertEquals(DesktopKmlToolsPoint(latitude = 39.0, longitude = -95.0), result.originalStart)
        assertEquals(4, result.translatedCoordinateCount)

        val output = Files.readString(result.outputPath)
        assertTrue(output.contains("-94.99,39.01,0"))
        assertTrue(output.contains("-94.989,39.01,5"))
        assertTrue(output.contains("-94.988,39.012"))
        assertTrue(output.contains("-94.987,39.013,10"))
    }

    @Test
    fun moveCourseUpdatesFirstKmlInsideKmzAndPreservesOtherEntries() {
        val source = Files.createTempFile("radio-oracle-move-course", ".kmz")
        ZipOutputStream(Files.newOutputStream(source)).use { zip ->
            zip.putNextEntry(ZipEntry("doc.kml"))
            zip.write(sampleKml().toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("files/icon.txt"))
            zip.write(byteArrayOf(1, 2, 3))
            zip.closeEntry()
        }

        val result = DesktopKmlTools.moveCourse(
            sourcePath = source,
            newStart = DesktopKmlToolsPoint(latitude = 38.5, longitude = -95.5)
        )

        assertEquals(source.resolveSibling("${source.fileName.toString().removeSuffix(".kmz")}_NEW.kmz"), result.outputPath)
        val entries = readKmzEntries(result.outputPath)
        assertTrue(entries.getValue("doc.kml").toString(StandardCharsets.UTF_8).contains("-95.5,38.5,0"))
        assertTrue(entries.getValue("doc.kml").toString(StandardCharsets.UTF_8).contains("-95.499,38.5,5"))
        assertArrayEquals(byteArrayOf(1, 2, 3), entries.getValue("files/icon.txt"))
    }

    @Test
    fun moveCourseRejectsKmlWithoutStartPoint() {
        val source = Files.createTempFile("radio-oracle-no-start", ".kml")
        Files.writeString(
            source,
            """
                <kml xmlns="http://www.opengis.net/kml/2.2">
                  <Document>
                    <Placemark><name>31</name><Point><coordinates>-95,39,0</coordinates></Point></Placemark>
                  </Document>
                </kml>
            """.trimIndent()
        )

        val error = runCatching {
            DesktopKmlTools.moveCourse(
                sourcePath = source,
                newStart = DesktopKmlToolsPoint(latitude = 39.01, longitude = -94.99)
            )
        }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("Point Placemark named Start"))
    }

    private fun sampleKml(): String =
        """
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Document>
                <Placemark>
                  <name>Start</name>
                  <Point><coordinates>-95.000000,39.000000,0</coordinates></Point>
                </Placemark>
                <Placemark>
                  <name>Route</name>
                  <LineString>
                    <coordinates>-94.999000,39.000000,5 -94.998000,39.002000</coordinates>
                  </LineString>
                </Placemark>
                <Placemark>
                  <name>Finish</name>
                  <Point><coordinates>-94.997000,39.003000,10</coordinates></Point>
                </Placemark>
              </Document>
            </kml>
        """.trimIndent()

    private fun readKmzEntries(path: java.nio.file.Path): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(Files.newInputStream(path)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    entries[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
            }
        }
        return entries
    }
}
