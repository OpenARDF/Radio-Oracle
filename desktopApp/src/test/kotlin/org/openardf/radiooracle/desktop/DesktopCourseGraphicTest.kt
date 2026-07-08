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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class DesktopCourseGraphicTest {
    @Test
    fun buildsMagneticNorthGraphicFromVisibleKmlObjects() {
        val path = Files.createTempDirectory("radio-oracle-graphic").resolve("Sprint Layout.kml")
        Files.writeString(path, sampleKml())

        val routeMap = DesktopCourseGraphic.routeMap(
            path = path,
            courseData = DesktopCourseFileReader.read(path),
            magneticDeclinationProvider = { DesktopMagneticDeclinationResult(90.0, usesExpiredCoefficients = false) }
        )

        assertEquals("Sprint Layout 2D Graphic", routeMap.title)
        assertEquals("Magnetic north (90.0° E declination)", routeMap.northOrientationText())
        assertEquals(listOf("Printed Start", "B", "Finish"), routeMap.points.map { it.label })
        assertFalse(routeMap.points.any { it.label == "Hidden Control" })
        assertFalse(routeMap.points.any { it.label == "Hidden Folder Point" })
        assertEquals(listOf("Printed Trail"), routeMap.lineStrings.map { it.label })
        assertEquals(listOf("Parking"), routeMap.polygons.map { it.label })
        assertTrue(routeMap.lineStrings.single().points.size >= 2)
        assertTrue(routeMap.polygons.single().points.size >= 4)
        val allX = routeMap.points.map { it.xFraction } +
            routeMap.lineStrings.flatMap { line -> line.points.map { it.xFraction } } +
            routeMap.polygons.flatMap { polygon -> polygon.points.map { it.xFraction } }
        val allY = routeMap.points.map { it.yFraction } +
            routeMap.lineStrings.flatMap { line -> line.points.map { it.yFraction } } +
            routeMap.polygons.flatMap { polygon -> polygon.points.map { it.yFraction } }
        assertTrue(allX.all { it in 0.05..0.95 })
        assertTrue(allY.all { it in 0.05..0.95 })
    }

    @Test
    fun exportsPngJpgAndPdfTogether() {
        val path = Files.createTempDirectory("radio-oracle-graphic-export").resolve("Sprint Layout.kml")
        Files.writeString(path, sampleKml())

        val result = DesktopCourseGraphic.generate(
            path = path,
            magneticDeclinationProvider = { DesktopMagneticDeclinationResult(12.3, usesExpiredCoefficients = false) }
        )

        assertEquals(3, result.visiblePointCount)
        assertEquals(1, result.visibleLineStringCount)
        assertEquals(1, result.visiblePolygonCount)
        assertEquals(2, result.hiddenObjectCount)
        listOf(result.outputPaths.pngPath, result.outputPaths.jpgPath, result.outputPaths.pdfPath).forEach { output ->
            assertTrue("${output.fileName} should exist", Files.size(output) > 0)
        }
        val pdfText = Files.readString(result.outputPaths.pdfPath, StandardCharsets.ISO_8859_1)
        assertTrue(pdfText.contains("[32.00 8.00] 0 d"))
        assertTrue(pdfText.contains("0.93 0.45 0.94 RG"))
        assertTrue(pdfText.contains("/GS1 gs"))
        assertTrue(pdfText.contains("MN"))
        assertTrue(pdfText.contains("Printed Start"))
        assertTrue(pdfText.contains("Printed Trail"))
        assertTrue(pdfText.contains("Parking"))
        val scaleBar = requireNotNull(DesktopCourseRouteMapStyle.scaleBar(result.routeMap.xRangeMeters, 684.0))
        assertTrue(pdfText.contains(scaleBar.label))
    }

    private fun sampleKml(): String =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <kml xmlns="http://www.opengis.net/kml/2.2">
          <Document>
            <Folder>
              <name>Visible</name>
              <Placemark>
                <name>Start</name>
                <description>Text="Printed Start"; Course object Start; type START</description>
                <Point><coordinates>-121.0000,45.0000,0</coordinates></Point>
              </Placemark>
              <Placemark>
                <name>Hidden Control</name>
                <visibility>0</visibility>
                <description>Text="Hidden Control"; Course object 1; type CONTROL</description>
                <Point><coordinates>-121.0010,45.0010,0</coordinates></Point>
              </Placemark>
              <Placemark>
                <name>B</name>
                <description>Course object B; type BEACON</description>
                <Point><coordinates>-121.0020,45.0020,0</coordinates></Point>
              </Placemark>
              <Placemark>
                <name>Finish</name>
                <description>Course object Finish; type FINISH</description>
                <Point><coordinates>-121.0030,45.0010,0</coordinates></Point>
              </Placemark>
              <Placemark>
                <name>Trail</name>
                <description>Text="Printed Trail"</description>
                <LineString><coordinates>-121.0000,45.0000,0 -121.0010,45.0005,0 -121.0020,45.0010,0</coordinates></LineString>
              </Placemark>
              <Placemark>
                <name>Parking</name>
                <Polygon>
                  <outerBoundaryIs>
                    <LinearRing>
                      <coordinates>
                        -121.0040,45.0000,0 -121.0040,45.0010,0 -121.0030,45.0010,0 -121.0030,45.0000,0 -121.0040,45.0000,0
                      </coordinates>
                    </LinearRing>
                  </outerBoundaryIs>
                </Polygon>
              </Placemark>
            </Folder>
            <Folder>
              <name>Hidden Folder</name>
              <visibility>0</visibility>
              <Placemark>
                <name>Hidden Folder Point</name>
                <description>Course object hidden; type WAYPOINT</description>
                <Point><coordinates>-121.0050,45.0050,0</coordinates></Point>
              </Placemark>
            </Folder>
          </Document>
        </kml>
        """.trimIndent()
}
