package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.event.EventProjectEditor
import org.openardf.radiooracle.shared.event.EventProjectFactory
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DesktopCourseKmlImportTest {
    @Test
    fun importsRouteDerivedCourseInfoIntoProtectedFields() {
        val kmlPath = Files.createTempFile("radio-oracle-course", ".kml")
        Files.writeString(kmlPath, sampleKml())
        val project = EventProjectEditor.addCategory(
            EventProjectFactory.createEmptyProject("race", "Course Test", "2026-06-05T09:00"),
            categoryId = "cat-m21",
            name = "M21"
        )

        val (updated, summary) = DesktopCourseKmlImporter.importProtectedCourseInfo(
            path = kmlPath,
            projectFile = project,
            password = "course-key",
            elevationProvider = { point ->
                when {
                    point.longitude < -94.9990 -> 100.0
                    point.longitude < -94.9980 -> 112.0
                    else -> 108.0
                }
            }
        )

        val categoryData = updated.raceData.categories.single()
        val category = categoryData.category
        assertEquals(1, summary.matchedCategoryCount)
        assertTrue(summary.sampledElevationCount > 0)
        assertEquals(0, category.lengthMeters)
        assertEquals(0, category.climbMeters)
        assertTrue(categoryData.controlPoints.isEmpty())
        assertNotNull(category.encryptedIdealOrder)
        assertNotNull(category.encryptedCourseInfo)
        assertEquals("31 32", DesktopProtectedCourseOrder.decrypt(category.encryptedIdealOrder!!, "course-key"))

        val protectedCourseInfo = DesktopProtectedCourseOrder.decryptCourseInfo(
            category.encryptedCourseInfo!!,
            "course-key"
        )
        assertEquals("31 32", protectedCourseInfo.idealOrder)
        assertTrue(protectedCourseInfo.lengthMeters!! > 100)
        assertTrue(protectedCourseInfo.climbMeters!! >= 12)
        assertEquals(kmlPath.fileName.toString(), protectedCourseInfo.sourceName)
        assertTrue(protectedCourseInfo.route.isNotEmpty())
    }

    @Test
    fun parsesKmlDocuments() {
        val kmlPath = Files.createTempFile("radio-oracle-course", ".kml").also {
            Files.writeString(it, sampleKml())
        }

        val parsed = DesktopCourseKmlImporter.parse(kmlPath)

        assertEquals(listOf("31", "32"), parsed.controls.map { it.name })
        assertEquals(listOf("M21"), parsed.routes.map { it.name })
    }

    @Test
    fun parsesKmzKmlDocuments() {
        val kmzPath = Files.createTempFile("radio-oracle-course", ".kmz")
        ZipOutputStream(Files.newOutputStream(kmzPath)).use { zip ->
            zip.putNextEntry(ZipEntry("doc.kml"))
            zip.write(sampleKml().toByteArray())
            zip.closeEntry()
        }

        val parsed = DesktopCourseKmlImporter.parse(kmzPath)

        assertEquals(listOf("31", "32"), parsed.controls.map { it.name })
        assertEquals(listOf("M21"), parsed.routes.map { it.name })
    }

    private fun sampleKml(): String =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <kml xmlns="http://www.opengis.net/kml/2.2">
          <Document>
            <Placemark>
              <name>31</name>
              <Point><coordinates>-95.0000,39.0000,0</coordinates></Point>
            </Placemark>
            <Placemark>
              <name>32</name>
              <Point><coordinates>-94.9980,39.0000,0</coordinates></Point>
            </Placemark>
            <Placemark>
              <name>M21</name>
              <LineString>
                <coordinates>
                  -95.0000,39.0000,0
                  -94.9990,39.0000,0
                  -94.9980,39.0000,0
                </coordinates>
              </LineString>
            </Placemark>
          </Document>
        </kml>
        """.trimIndent()
}
