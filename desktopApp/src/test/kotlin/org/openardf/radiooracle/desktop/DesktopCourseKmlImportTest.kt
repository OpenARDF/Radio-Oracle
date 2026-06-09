package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.domain.ControlPointType
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
        assertEquals(0, summary.changedControlLocationCount)
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
        assertTrue(updated.raceData.controls.all { it.latitude == null && it.longitude == null })
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
    fun reportsLikelyControlLabelConversionsBeforeImportIsKept() {
        val kmlPath = Files.createTempFile("radio-oracle-course", ".kml")
        Files.writeString(kmlPath, sampleKmlWithCompactFoxLabels())
        val baseProject = EventProjectFactory.createEmptyProject("race", "Course Test", "2026-06-05T09:00")
            .withControlPublicLabel(siCode = 31, publicLabel = "Fox 1")
            .withControlPublicLabel(siCode = 32, publicLabel = "Fox 2")
        val project = EventProjectEditor.addCategory(
            baseProject,
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
        assertEquals(
            listOf(
                DesktopCourseKmlLabelConversion("Transmitter 1", "Fox 1"),
                DesktopCourseKmlLabelConversion("FOX2", "Fox 2")
            ),
            summary.labelConversions
        )
        assertEquals(true, summary.hasLabelConversions)
        assertEquals("'Fox 1' 'Fox 2'", DesktopProtectedCourseOrder.decrypt(category.encryptedIdealOrder!!, "course-key"))
        assertEquals("1", updated.raceData.controls.single { it.siCode == 31 }.label)
        assertEquals("Fox 1", updated.raceData.controls.single { it.siCode == 31 }.publicLabel)
        assertEquals("2", updated.raceData.controls.single { it.siCode == 32 }.label)
        assertEquals("Fox 2", updated.raceData.controls.single { it.siCode == 32 }.publicLabel)
    }

    @Test
    fun doesNotGuessNumberBasedControlMatchWhenAmbiguous() {
        val kmlPath = Files.createTempFile("radio-oracle-course", ".kml")
        Files.writeString(kmlPath, sampleKmlWithAmbiguousControlNumber())
        val baseProject = EventProjectEditor.addControl(
            EventProjectFactory.createEmptyProject("race", "Course Test", "2026-06-05T09:00"),
            controlId = "control-fast-1",
            label = "F1",
            siCode = "41",
            type = ControlPointType.CONTROL
        )
        val project = EventProjectEditor.addCategory(
            baseProject,
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
        assertEquals(0, summary.matchedControlPointCount)
        assertEquals(emptyList<DesktopCourseKmlLabelConversion>(), summary.labelConversions)
        assertEquals(null, category.encryptedIdealOrder)
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
    fun controlsOnlyKmlUpdatesChangedStoredControlLocations() {
        val routeKmlPath = Files.createTempFile("radio-oracle-course", ".kml")
        Files.writeString(routeKmlPath, sampleKml())
        val project = EventProjectEditor.addCategory(
            EventProjectFactory.createEmptyProject("race", "Course Test", "2026-06-05T09:00"),
            categoryId = "cat-m21",
            name = "M21"
        )
        val (imported, _) = DesktopCourseKmlImporter.importProtectedCourseInfo(
            path = routeKmlPath,
            projectFile = project,
            password = "course-key",
            elevationProvider = { 100.0 }
        )
        val controlsOnlyPath = Files.createTempFile("radio-oracle-controls", ".kml")
        Files.writeString(controlsOnlyPath, controlsOnlyKml(longitude31 = -95.0100, longitude32 = -94.9980))

        val (updated, summary) = DesktopCourseKmlImporter.importProtectedCourseInfo(
            path = controlsOnlyPath,
            projectFile = imported,
            password = "course-key",
            elevationProvider = { 222.0 }
        )

        assertEquals(0, summary.routeCount)
        assertEquals(2, summary.matchedControlPointCount)
        assertEquals(1, summary.changedControlLocationCount)
        assertEquals(1, summary.controlLocationAffectedCategoryCount)
        assertEquals(0, summary.importedCategoryCount)
        val publicControl = updated.raceData.controls.single { it.siCode == 31 }
        assertEquals(null, publicControl.latitude)
        assertEquals(null, publicControl.longitude)
        val protectedCourseInfo = DesktopProtectedCourseOrder.decryptCourseInfo(
            updated.raceData.categories.single().category.encryptedCourseInfo!!,
            "course-key"
        )
        val protectedControl = protectedCourseInfo.controlPoints.single { it.longitude == -95.0100 }
        assertEquals(-95.0100, protectedControl.longitude, 0.000001)
        assertEquals(222.0, requireNotNull(protectedControl.elevationMeters), 0.001)
        assertTrue(protectedCourseInfo.route.isEmpty())
        assertEquals(null, protectedCourseInfo.lengthMeters)
        assertEquals(null, protectedCourseInfo.climbMeters)
    }

    @Test
    fun controlsOnlyKmlIgnoresUnchangedStoredControlLocations() {
        val routeKmlPath = Files.createTempFile("radio-oracle-course", ".kml")
        Files.writeString(routeKmlPath, sampleKml())
        val project = EventProjectEditor.addCategory(
            EventProjectFactory.createEmptyProject("race", "Course Test", "2026-06-05T09:00"),
            categoryId = "cat-m21",
            name = "M21"
        )
        val (imported, _) = DesktopCourseKmlImporter.importProtectedCourseInfo(
            path = routeKmlPath,
            projectFile = project,
            password = "course-key"
        )
        val controlsOnlyPath = Files.createTempFile("radio-oracle-controls", ".kml")
        Files.writeString(controlsOnlyPath, controlsOnlyKml(longitude31 = -95.0000, longitude32 = -94.9980))

        val (updated, summary) = DesktopCourseKmlImporter.importProtectedCourseInfo(
            path = controlsOnlyPath,
            projectFile = imported,
            password = "course-key",
            elevationProvider = { error("Unchanged point placemarks should not fetch elevation") }
        )

        assertEquals(imported, updated)
        assertEquals(0, summary.routeCount)
        assertEquals(2, summary.matchedControlPointCount)
        assertEquals(0, summary.changedControlLocationCount)
        assertTrue(summary.isControlLocationNoOp)
    }

    @Test
    fun reprocessesIdenticalImportedFileWhenStoredProtectedLocationsAreIncomplete() = runBlocking {
        val kmlPath = Files.createTempFile("radio-oracle-course", ".kml")
        Files.writeString(kmlPath, sampleKml())
        val project = EventProjectEditor.addCategory(
            EventProjectFactory.createEmptyProject("race", "Course Test", "2026-06-05T09:00"),
            categoryId = "cat-m21",
            name = "M21"
        )
        val password = "course-key"
        val (imported, firstSummary) = DesktopCourseKmlImporter.importProtectedCourseInfo(
            path = kmlPath,
            projectFile = project,
            password = password
        )
        val (elevated, _) = DesktopCourseKmlImporter.fetchProtectedCourseElevations(
            projectFile = imported,
            categoryIds = firstSummary.matchedCategoryIds,
            password = password,
            elevationProvider = { 100.0 }
        )
        val elevatedCourseInfo = DesktopProtectedCourseOrder.decryptCourseInfo(
            elevated.raceData.categories.single().category.encryptedCourseInfo!!,
            password
        )
        val legacyCourseInfo = elevatedCourseInfo.copy(
            controlPoints = emptyList(),
            courseObjects = emptyList()
        )
        val legacyProject = EventProjectEditor.updateCategoryEncryptedCourseInfo(
            elevated,
            "cat-m21",
            DesktopProtectedCourseOrder.encryptCourseInfo(legacyCourseInfo, password)
        )

        val (updated, summary) = DesktopCourseKmlImporter.importProtectedCourseInfo(
            path = kmlPath,
            projectFile = legacyProject,
            password = password
        )
        val protectedCourseInfo = DesktopProtectedCourseOrder.decryptCourseInfo(
            updated.raceData.categories.single().category.encryptedCourseInfo!!,
            password
        )

        assertEquals(firstSummary.sourceSha256, summary.sourceSha256)
        assertEquals(1, summary.importedCategoryCount)
        assertEquals(0, summary.duplicateCategoryCount)
        assertEquals(false, summary.isDuplicateOnly)
        assertEquals(2, protectedCourseInfo.controlPoints.size)
        assertEquals(listOf("Start", "1", "2", "Finish"), protectedCourseInfo.courseObjects.map { it.label })
        assertEquals(elevatedCourseInfo.route.size, protectedCourseInfo.route.size)
        assertTrue(protectedCourseInfo.route.all { it.elevationMeters == 100.0 })
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
        assertTrue(elevationResult.elevatedPointCount < elevationResult.sampledPointCount)
        assertEquals(elevationResult.sampledPointCount, elevationResult.resolvedPointCount)
        assertTrue(protectedCourseInfo.route.all { it.elevationMeters != null })
        assertTrue(protectedCourseInfo.controlPoints.all { it.elevationMeters != null })
        assertEquals(listOf("Start", "1", "2", "Finish"), protectedCourseInfo.courseObjects.map { it.label })
        assertTrue(protectedCourseInfo.courseObjects.all { it.elevationMeters != null })
        assertTrue(protectedCourseInfo.climbMeters!! >= 12)
        assertEquals(
            protectedCourseInfo.route.size + protectedCourseInfo.courseObjects.size,
            elevationResult.elevatedPointCount
        )
        assertEquals(0, progressUpdates.first().completedPointCount)
        assertEquals(elevationResult.sampledPointCount, progressUpdates.last().completedPointCount)
    }

    @Test
    fun fetchesMissingProtectedControlElevationsWhenRouteAndObjectsAlreadyHaveElevations() = runBlocking {
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
            password = "course-key",
            elevationProvider = { 100.0 }
        )
        val importedCategory = imported.raceData.categories.single().category
        val importedCourseInfo = DesktopProtectedCourseOrder.decryptCourseInfo(
            importedCategory.encryptedCourseInfo!!,
            "course-key"
        )
        val missingControlCourseInfo = importedCourseInfo.copy(
            controlPoints = importedCourseInfo.controlPoints.map { it.copy(elevationMeters = null) }
        )
        val projectWithMissingControlElevations = EventProjectEditor.updateCategoryEncryptedCourseInfo(
            imported,
            "cat-m21",
            DesktopProtectedCourseOrder.encryptCourseInfo(missingControlCourseInfo, "course-key")
        )
        var requestCount = 0

        val (updated, elevationResult) = DesktopCourseKmlImporter.fetchProtectedCourseElevations(
            projectFile = projectWithMissingControlElevations,
            categoryIds = summary.matchedCategoryIds,
            password = "course-key",
            elevationProvider = {
                requestCount++
                123.0
            }
        )
        val protectedCourseInfo = DesktopProtectedCourseOrder.decryptCourseInfo(
            updated.raceData.categories.single().category.encryptedCourseInfo!!,
            "course-key"
        )

        assertEquals(missingControlCourseInfo.controlPoints.size, elevationResult.sampledPointCount)
        assertEquals(0, elevationResult.elevatedPointCount)
        assertEquals(missingControlCourseInfo.controlPoints.size, elevationResult.resolvedPointCount)
        assertEquals(0, requestCount)
        assertTrue(protectedCourseInfo.route.all { it.elevationMeters == 100.0 })
        assertTrue(protectedCourseInfo.courseObjects.all { it.elevationMeters == 100.0 })
        assertTrue(protectedCourseInfo.controlPoints.all { it.elevationMeters == 100.0 })
    }

    @Test
    fun fetchProtectedCourseElevationsUsesLocalCacheBeforeNetwork() = runBlocking {
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
        var networkRequestCount = 0

        val (updated, elevationResult) = DesktopCourseKmlImporter.fetchProtectedCourseElevations(
            projectFile = imported,
            categoryIds = summary.matchedCategoryIds,
            password = "course-key",
            elevationProvider = {
                networkRequestCount++
                999.0
            },
            localElevationProvider = { 123.0 }
        )
        val protectedCourseInfo = DesktopProtectedCourseOrder.decryptCourseInfo(
            updated.raceData.categories.single().category.encryptedCourseInfo!!,
            "course-key"
        )

        assertEquals(0, networkRequestCount)
        assertTrue(elevationResult.cachedPointCount > 0)
        assertEquals(elevationResult.sampledPointCount, elevationResult.resolvedPointCount)
        assertEquals(0, elevationResult.elevatedPointCount)
        assertTrue(protectedCourseInfo.route.all { it.elevationMeters == 123.0 })
        assertTrue(protectedCourseInfo.courseObjects.all { it.elevationMeters == 123.0 })
        assertTrue(protectedCourseInfo.controlPoints.all { it.elevationMeters == 123.0 })
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

    private fun sampleKmlWithCompactFoxLabels(): String =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <kml xmlns="http://www.opengis.net/kml/2.2">
          <Document>
            <Placemark>
              <name>Transmitter 1</name>
              <Point><coordinates>-95.0000,39.0000,0</coordinates></Point>
            </Placemark>
            <Placemark>
              <name>FOX2</name>
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

    private fun sampleKmlWithAmbiguousControlNumber(): String =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <kml xmlns="http://www.opengis.net/kml/2.2">
          <Document>
            <Placemark>
              <name>Transmitter 1</name>
              <Point><coordinates>-95.0000,39.0000,0</coordinates></Point>
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

    private fun controlsOnlyKml(longitude31: Double, longitude32: Double): String =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <kml xmlns="http://www.opengis.net/kml/2.2">
          <Document>
            <Placemark>
              <name>31</name>
              <Point><coordinates>$longitude31,39.0000,0</coordinates></Point>
            </Placemark>
            <Placemark>
              <name>32</name>
              <Point><coordinates>$longitude32,39.0000,0</coordinates></Point>
            </Placemark>
          </Document>
        </kml>
        """.trimIndent()

    private fun org.openardf.radiooracle.shared.event.EventProjectFile.withControlPublicLabel(
        siCode: Int,
        publicLabel: String
    ): org.openardf.radiooracle.shared.event.EventProjectFile {
        val control = raceData.controls.single { it.siCode == siCode }
        return EventProjectEditor.updateControl(
            projectFile = this,
            controlId = control.id,
            label = control.label,
            siCode = control.siCode.toString(),
            type = control.type,
            scored = control.scored,
            publicLabel = publicLabel,
            notes = control.notes.orEmpty()
        )
    }
}
