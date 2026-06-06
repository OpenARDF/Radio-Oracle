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
import kotlinx.coroutines.runBlocking

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
        assertEquals(listOf("cat-m21"), summary.matchedCategoryIds)
        assertEquals(listOf("M21"), summary.matchedCategoryNames)
        assertEquals(2, summary.matchedControlPointCount)
        assertEquals(1, summary.importedCategoryCount)
        assertEquals(0, summary.duplicateCategoryCount)
        assertTrue(summary.routeElevationPointCount > 0)
        assertEquals(0, category.lengthMeters)
        assertEquals(0, category.climbMeters)
        assertTrue(categoryData.controlPoints.isEmpty())
        assertNotNull(category.encryptedIdealOrder)
        assertNotNull(category.encryptedCourseInfo)
        assertEquals("1 2", DesktopProtectedCourseOrder.decrypt(category.encryptedIdealOrder!!, "course-key"))

        val protectedCourseInfo = DesktopProtectedCourseOrder.decryptCourseInfo(
            category.encryptedCourseInfo!!,
            "course-key"
        )
        assertEquals("1 2", protectedCourseInfo.idealOrder)
        assertTrue(protectedCourseInfo.lengthMeters!! > 100)
        assertTrue(protectedCourseInfo.climbMeters!! >= 12)
        assertEquals(kmlPath.fileName.toString(), protectedCourseInfo.sourceName)
        assertEquals(summary.sourceSha256, protectedCourseInfo.sourceSha256)
        assertEquals(64, protectedCourseInfo.sourceSha256.length)
        assertTrue(protectedCourseInfo.route.isNotEmpty())
        assertEquals(listOf("Start", "1", "2", "Finish"), protectedCourseInfo.courseObjects.map { it.label })
        assertTrue(protectedCourseInfo.courseObjects.all { it.elevationMeters != null })
    }

    @Test
    fun importsBeaconByVisibleLabelInsteadOfCrypticToken() {
        val kmlPath = Files.createTempFile("radio-oracle-course", ".kml")
        Files.writeString(kmlPath, sampleKmlWithBeacon())
        val project = EventProjectEditor.addCategory(
            EventProjectFactory.createEmptyProject("race", "Course Test", "2026-06-05T09:00"),
            categoryId = "cat-m21",
            name = "M21"
        )

        val (updated, summary) = DesktopCourseKmlImporter.importProtectedCourseInfo(
            path = kmlPath,
            projectFile = project,
            password = "course-key",
            elevationProvider = { null }
        )

        val category = updated.raceData.categories.single().category
        assertEquals(2, summary.matchedControlPointCount)
        assertEquals("1 M", DesktopProtectedCourseOrder.decrypt(category.encryptedIdealOrder!!, "course-key"))
    }

    @Test
    fun defaultImportIgnoresKmlElevationsUntilElevationFetchIsRequested() {
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
            password = "course-key"
        )

        val category = updated.raceData.categories.single().category
        val protectedCourseInfo = DesktopProtectedCourseOrder.decryptCourseInfo(
            category.encryptedCourseInfo!!,
            "course-key"
        )
        assertEquals(0, summary.routeElevationPointCount)
        assertTrue(protectedCourseInfo.route.all { it.elevationMeters == null })
        assertTrue(protectedCourseInfo.courseObjects.all { it.elevationMeters == null })
        assertEquals(null, protectedCourseInfo.climbMeters)
    }

    @Test
    fun skipsReloadingIdenticalImportedFileButReportsMissingElevations() {
        val kmlPath = Files.createTempFile("radio-oracle-course", ".kml")
        Files.writeString(kmlPath, sampleKml())
        val project = EventProjectEditor.addCategory(
            EventProjectFactory.createEmptyProject("race", "Course Test", "2026-06-05T09:00"),
            categoryId = "cat-m21",
            name = "M21"
        )
        val (imported, firstSummary) = DesktopCourseKmlImporter.importProtectedCourseInfo(
            path = kmlPath,
            projectFile = project,
            password = "course-key"
        )
        var unexpectedElevationRequestCount = 0

        val (duplicateProject, duplicateSummary) = DesktopCourseKmlImporter.importProtectedCourseInfo(
            path = kmlPath,
            projectFile = imported,
            password = "course-key",
            elevationProvider = {
                unexpectedElevationRequestCount++
                999.0
            }
        )

        assertEquals(imported, duplicateProject)
        assertEquals(firstSummary.sourceSha256, duplicateSummary.sourceSha256)
        assertEquals(1, duplicateSummary.matchedCategoryCount)
        assertEquals(0, duplicateSummary.importedCategoryCount)
        assertEquals(1, duplicateSummary.duplicateCategoryCount)
        assertTrue(duplicateSummary.isDuplicateOnly)
        assertTrue(duplicateSummary.hasDuplicateMissingElevations)
        assertTrue(duplicateSummary.duplicateMissingElevationPointCount > 0)
        assertEquals(0, unexpectedElevationRequestCount)
    }

    @Test
    fun reportsFullyDownloadedIdenticalFileAsDuplicateWithNoMissingWork() = runBlocking {
        val kmlPath = Files.createTempFile("radio-oracle-course", ".kml")
        Files.writeString(kmlPath, sampleKml())
        val project = EventProjectEditor.addCategory(
            EventProjectFactory.createEmptyProject("race", "Course Test", "2026-06-05T09:00"),
            categoryId = "cat-m21",
            name = "M21"
        )
        val (imported, summary) = DesktopCourseKmlImporter.importProtectedCourseInfo(
            path = kmlPath,
            projectFile = project,
            password = "course-key"
        )
        val (elevated, _) = DesktopCourseKmlImporter.fetchProtectedCourseElevations(
            projectFile = imported,
            categoryIds = summary.matchedCategoryIds,
            password = "course-key",
            elevationProvider = { 100.0 }
        )

        val (duplicateProject, duplicateSummary) = DesktopCourseKmlImporter.importProtectedCourseInfo(
            path = kmlPath,
            projectFile = elevated,
            password = "course-key"
        )

        assertEquals(elevated, duplicateProject)
        assertTrue(duplicateSummary.isDuplicateOnly)
        assertEquals(0, duplicateSummary.importedCategoryCount)
        assertEquals(1, duplicateSummary.duplicateCategoryCount)
        assertEquals(0, duplicateSummary.duplicateMissingElevationPointCount)
        assertEquals(false, duplicateSummary.hasDuplicateMissingElevations)
    }

    @Test
    fun treatsSameNamedFileWithDifferentHashAsNewImport() {
        val kmlPath = Files.createTempFile("radio-oracle-course", ".kml")
        Files.writeString(kmlPath, sampleKml())
        val project = EventProjectEditor.addCategory(
            EventProjectFactory.createEmptyProject("race", "Course Test", "2026-06-05T09:00"),
            categoryId = "cat-m21",
            name = "M21"
        )
        val (imported, firstSummary) = DesktopCourseKmlImporter.importProtectedCourseInfo(
            path = kmlPath,
            projectFile = project,
            password = "course-key"
        )
        Files.writeString(kmlPath, sampleKml().replace("-94.9990,39.0000,0", "-94.9995,39.0000,0"))

        val (updated, secondSummary) = DesktopCourseKmlImporter.importProtectedCourseInfo(
            path = kmlPath,
            projectFile = imported,
            password = "course-key"
        )
        val protectedCourseInfo = DesktopProtectedCourseOrder.decryptCourseInfo(
            updated.raceData.categories.single().category.encryptedCourseInfo!!,
            "course-key"
        )

        assertTrue(firstSummary.sourceSha256 != secondSummary.sourceSha256)
        assertEquals(1, secondSummary.importedCategoryCount)
        assertEquals(0, secondSummary.duplicateCategoryCount)
        assertEquals(secondSummary.sourceSha256, protectedCourseInfo.sourceSha256)
    }

    @Test
    fun fetchesProtectedRouteElevationsAfterImport() = runBlocking {
        val kmlPath = Files.createTempFile("radio-oracle-course", ".kml")
        Files.writeString(kmlPath, sampleKml())
        val project = EventProjectEditor.addCategory(
            EventProjectFactory.createEmptyProject("race", "Course Test", "2026-06-05T09:00"),
            categoryId = "cat-m21",
            name = "M21"
        )
        val (imported, summary) = DesktopCourseKmlImporter.importProtectedCourseInfo(
            path = kmlPath,
            projectFile = project,
            password = "course-key"
        )
        val importedCategory = imported.raceData.categories.single().category
        val importedCourseInfo = DesktopProtectedCourseOrder.decryptCourseInfo(
            importedCategory.encryptedCourseInfo!!,
            "course-key"
        )
        val progressUpdates = mutableListOf<DesktopRouteElevationProgress>()

        val (updated, elevationResult) = DesktopCourseKmlImporter.fetchProtectedCourseElevations(
            projectFile = imported,
            categoryIds = summary.matchedCategoryIds,
            password = "course-key",
            elevationProvider = { point ->
                when {
                    point.longitude < -94.9990 -> 100.0
                    point.longitude < -94.9980 -> 112.0
                    else -> 108.0
                }
            },
            onProgress = { progressUpdates += it }
        )

        val category = updated.raceData.categories.single().category
        val protectedCourseInfo = DesktopProtectedCourseOrder.decryptCourseInfo(
            category.encryptedCourseInfo!!,
            "course-key"
        )
        assertEquals(1, elevationResult.categoryCount)
        assertTrue(elevationResult.sampledPointCount > importedCourseInfo.sampledPointCount)
        assertEquals(elevationResult.sampledPointCount, elevationResult.elevatedPointCount)
        assertTrue(protectedCourseInfo.route.all { it.elevationMeters != null })
        assertTrue(protectedCourseInfo.controlPoints.all { it.elevationMeters != null })
        assertEquals(listOf("Start", "1", "2", "Finish"), protectedCourseInfo.courseObjects.map { it.label })
        assertTrue(protectedCourseInfo.courseObjects.all { it.elevationMeters != null })
        assertTrue(protectedCourseInfo.climbMeters!! >= 12)
        assertEquals(
            protectedCourseInfo.route.size + protectedCourseInfo.courseObjects.size,
            elevationResult.sampledPointCount
        )
        assertEquals(0, progressUpdates.first().completedPointCount)
        assertEquals(elevationResult.sampledPointCount, progressUpdates.last().completedPointCount)
    }

    @Test
    fun fetchesOnlyMissingElevationsAndPreservesExistingValues() = runBlocking {
        val kmlPath = Files.createTempFile("radio-oracle-course", ".kml")
        Files.writeString(kmlPath, sampleKml())
        val project = EventProjectEditor.addCategory(
            EventProjectFactory.createEmptyProject("race", "Course Test", "2026-06-05T09:00"),
            categoryId = "cat-m21",
            name = "M21"
        )
        val (imported, summary) = DesktopCourseKmlImporter.importProtectedCourseInfo(
            path = kmlPath,
            projectFile = project,
            password = "course-key"
        )

        val (elevated, firstResult) = DesktopCourseKmlImporter.fetchProtectedCourseElevations(
            projectFile = imported,
            categoryIds = summary.matchedCategoryIds,
            password = "course-key",
            elevationProvider = { point ->
                when {
                    point.longitude < -94.9990 -> 100.0
                    point.longitude < -94.9980 -> 112.0
                    else -> 108.0
                }
            }
        )
        var unexpectedFetchCount = 0
        val (refetched, secondResult) = DesktopCourseKmlImporter.fetchProtectedCourseElevations(
            projectFile = elevated,
            categoryIds = summary.matchedCategoryIds,
            password = "course-key",
            elevationProvider = {
                unexpectedFetchCount++
                -999.0
            }
        )

        val firstCourseInfo = DesktopProtectedCourseOrder.decryptCourseInfo(
            elevated.raceData.categories.single().category.encryptedCourseInfo!!,
            "course-key"
        )
        val secondCourseInfo = DesktopProtectedCourseOrder.decryptCourseInfo(
            refetched.raceData.categories.single().category.encryptedCourseInfo!!,
            "course-key"
        )
        assertTrue(firstResult.elevatedPointCount > 0)
        assertEquals(0, secondResult.sampledPointCount)
        assertEquals(0, secondResult.elevatedPointCount)
        assertEquals(0, unexpectedFetchCount)
        assertEquals(firstCourseInfo.route.map { it.elevationMeters }, secondCourseInfo.route.map { it.elevationMeters })
        assertEquals(
            firstCourseInfo.courseObjects.map { it.elevationMeters },
            secondCourseInfo.courseObjects.map { it.elevationMeters }
        )
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

    private fun sampleKmlWithBeacon(): String =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <kml xmlns="http://www.opengis.net/kml/2.2">
          <Document>
            <Placemark>
              <name>31</name>
              <Point><coordinates>-95.0000,39.0000,0</coordinates></Point>
            </Placemark>
            <Placemark>
              <name>M</name>
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
