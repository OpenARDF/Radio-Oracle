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
        assertEquals(listOf("Printed Start", "B", "Finish", ""), routeMap.points.map { it.label })
        assertEquals(
            listOf(
                DesktopCourseRouteMapPointType.Start,
                DesktopCourseRouteMapPointType.Beacon,
                DesktopCourseRouteMapPointType.Finish,
                DesktopCourseRouteMapPointType.Waypoint
            ),
            routeMap.points.map { it.type }
        )
        assertFalse(routeMap.points.any { it.label == "Hidden Control" })
        assertFalse(routeMap.points.any { it.label == "Hidden Folder Point" })
        assertEquals(listOf("Printed Trail", "Black Trail", "Thin Black Trail"), routeMap.lineStrings.map { it.label })
        val printedTrail = routeMap.lineStrings.first { it.label == "Printed Trail" }
        assertEquals(null, printedTrail.strokeColorArgb)
        assertTrue(printedTrail.dashed)
        val blackTrail = routeMap.lineStrings.first { it.label == "Black Trail" }
        assertEquals(0xFF000000L, blackTrail.strokeColorArgb)
        assertEquals(10f, blackTrail.strokeWidthPixels)
        assertFalse(blackTrail.dashed)
        val thinBlackTrail = routeMap.lineStrings.first { it.label == "Thin Black Trail" }
        assertEquals(0xFF000000L, thinBlackTrail.strokeColorArgb)
        assertEquals(3f, thinBlackTrail.strokeWidthPixels)
        assertTrue(thinBlackTrail.dashed)
        assertEquals(listOf("Parking"), routeMap.polygons.map { it.label })
        assertTrue(routeMap.lineStrings.all { it.points.size >= 2 })
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

        assertEquals(4, result.visiblePointCount)
        assertEquals(3, result.visibleLineStringCount)
        assertEquals(1, result.visiblePolygonCount)
        assertEquals(2, result.hiddenObjectCount)
        listOf(result.outputPaths.pngPath, result.outputPaths.jpgPath, result.outputPaths.pdfPath).forEach { output ->
            assertTrue("${output.fileName} should exist", Files.size(output) > 0)
        }
        val pdfText = Files.readString(result.outputPaths.pdfPath, StandardCharsets.ISO_8859_1)
        assertTrue(pdfText.contains("[10.00 5.00] 0 d"))
        assertTrue(pdfText.contains("[20.00 10.00] 0 d"))
        assertTrue(pdfText.contains("[] 0 d"))
        assertTrue(pdfText.contains("0.93 0.45 0.94 RG"))
        assertTrue(pdfText.contains("0.00 0.00 0.00 RG"))
        assertTrue(pdfText.contains("10.00 w"))
        assertTrue(pdfText.contains("3.00 w"))
        assertTrue(pdfText.contains("/GS1 gs"))
        assertTrue(pdfText.contains("/Resources 3 0 R"))
        assertTrue(pdfText.contains("/Font << /F1 << /Type /Font"))
        assertTrue(pdfText.contains(">> /ExtGState << /GS1"))
        assertFalse(pdfText.contains("/Resources << /Font 3 0 R >>"))
        assertTrue(pdfText.contains("MN"))
        assertTrue(pdfText.contains("Printed Start"))
        assertTrue(pdfText.contains("Printed Trail"))
        assertTrue(pdfText.contains("Parking"))
        assertFalse(pdfText.contains("Suppressed Label"))
        val scaleBar = requireNotNull(DesktopCourseRouteMapStyle.scaleBar(result.routeMap.xRangeMeters, 684.0))
        assertTrue(pdfText.contains(scaleBar.label))
    }

    private fun sampleKml(): String =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <kml xmlns="http://www.opengis.net/kml/2.2">
          <Document>
            <Style id="trianglePointStyle">
              <IconStyle>
                <Icon><href>http://maps.google.com/mapfiles/kml/shapes/triangle.png</href></Icon>
              </IconStyle>
            </Style>
            <Style id="donutPointStyle">
              <IconStyle>
                <Icon><href>http://maps.google.com/mapfiles/kml/shapes/donut.png</href></Icon>
              </IconStyle>
            </Style>
            <Style id="targetPointStyle">
              <IconStyle>
                <Icon><href>http://maps.google.com/mapfiles/kml/shapes/target.png</href></Icon>
              </IconStyle>
            </Style>
            <Style id="blackTrailStyle">
              <LineStyle>
                <color>ff000000</color>
                <width>10</width>
              </LineStyle>
            </Style>
            <Style id="thinBlackTrailStyle">
              <LineStyle>
                <color>ff000000</color>
                <width>3</width>
              </LineStyle>
            </Style>
            <StyleMap id="blackTrailStyleMap">
              <Pair>
                <key>normal</key>
                <styleUrl>#blackTrailStyle</styleUrl>
              </Pair>
            </StyleMap>
            <Folder>
              <name>Visible</name>
              <Placemark>
                <name>Start</name>
                <styleUrl>#trianglePointStyle</styleUrl>
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
                <styleUrl>#donutPointStyle</styleUrl>
                <description>Course object Spectator; type SPECTATOR</description>
                <Point><coordinates>-121.0020,45.0020,0</coordinates></Point>
              </Placemark>
              <Placemark>
                <name>Finish</name>
                <styleUrl>#targetPointStyle</styleUrl>
                <description>Course object Finish; type FINISH</description>
                <Point><coordinates>-121.0030,45.0010,0</coordinates></Point>
              </Placemark>
              <Placemark>
                <name>Suppressed Label</name>
                <description>Text=""; Course object no printed label</description>
                <Point><coordinates>-121.0035,45.0015,0</coordinates></Point>
              </Placemark>
              <Placemark>
                <name>Trail</name>
                <description>Text="Printed Trail"</description>
                <LineString><coordinates>-121.0000,45.0000,0 -121.0010,45.0005,0 -121.0020,45.0010,0</coordinates></LineString>
              </Placemark>
              <Placemark>
                <name>Black Trail</name>
                <styleUrl>#blackTrailStyleMap</styleUrl>
                <LineString><coordinates>-121.0020,45.0010,0 -121.0030,45.0015,0</coordinates></LineString>
              </Placemark>
              <Placemark>
                <name>Thin Black Trail</name>
                <styleUrl>#thinBlackTrailStyle</styleUrl>
                <LineString><coordinates>-121.0015,45.0002,0 -121.0035,45.0007,0</coordinates></LineString>
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
