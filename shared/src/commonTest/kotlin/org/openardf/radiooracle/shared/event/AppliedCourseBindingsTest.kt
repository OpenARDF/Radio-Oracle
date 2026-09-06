package org.openardf.radiooracle.shared.event

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.publicresults.ProtectedCourseCipher
import kotlin.test.*

class AppliedCourseBindingsTest {
    private val controls = (1..2).map { EventControl("race-$it", "race", "Fox$it", 130 + it, ControlPointType.CONTROL) }
    private fun info() = ProtectedCourseInfo(sourceName = "input.kml", idealOrder = "Fox2 Fox1", controlPoints = listOf(
        ProtectedCourseControlPoint("old-fox1", "Fox2", 40.0, -75.0),
        ProtectedCourseControlPoint("old-fox2", "Fox1", 41.0, -76.0)))
    private fun applied() = CourseDesignBindings.prepare(info(), controls,
        mapOf("old-fox1" to "race-2", "old-fox2" to "race-1"), listOf("old-fox1", "old-fox2"), "revision-1")

    @Test fun explicitBindingsWinOverOldPlacementNamesAndRoundTrip() {
        val saved = applied()
        val decoded = Json.decodeFromString<ProtectedCourseInfo>(Json.encodeToString(saved))
        assertEquals(saved, decoded)
        assertNull(CourseDesignBindings.validationError(decoded))
        assertEquals(41.0, CourseControlResolver.resolve(controls.first(), listOf(decoded)).location?.latitude)
        assertEquals("applied-binding", CourseControlResolver.resolve(controls.first(), listOf(decoded)).provenance)
        val encrypted = ProtectedCourseCipher.encryptCourseInfo(saved, "fixture-password")
        assertEquals(saved, ProtectedCourseCipher.decryptCourseInfo(encrypted, "fixture-password"))
        assertFails { ProtectedCourseCipher.decryptCourseInfo(encrypted, "wrong") }
    }

    @Test fun movementAndCatalogEditsRequireNewApplication() {
        val saved = applied()
        val moved = saved.copy(controlPoints = saved.controlPoints.map { it.copy(latitude = it.latitude + 0.1) })
        assertNotNull(CourseDesignBindings.validationError(moved))
        assertEquals(CourseResolutionStatus.INVALID, CourseControlResolver.resolve(controls.first().copy(siCode = 999), listOf(saved)).status)
        assertEquals(CourseResolutionStatus.INVALID, CourseControlResolver.resolve(controls.first(), listOf(moved)).status)
    }

    @Test fun fingerprintIgnoresSourceNamesAndGeneratedIdsButIncludesBindingLocation() {
        val saved = applied()
        val renamed = saved.copy(sourceName = "renamed.kml", sourceSha256 = "new-file-bytes", sampledPointCount = 123,
            controlPoints = saved.controlPoints.map { it.copy(controlId = "new-${it.controlId}") },
            appliedBindings = saved.appliedBindings!!.copy(orderedPlacementIds = saved.appliedBindings.orderedPlacementIds.map { "new-$it" }, controls = saved.appliedBindings.controls.map { it.copy(placementId = "new-${it.placementId}") }))
        assertEquals(CourseDesignBindings.fingerprint(saved), CourseDesignBindings.fingerprint(renamed))
        val swapped = saved.copy(appliedBindings = saved.appliedBindings.copy(controls = saved.appliedBindings.controls.map {
            it.copy(placementId = if (it.placementId == "old-fox1") "old-fox2" else "old-fox1")
        }))
        assertNotEquals(CourseDesignBindings.fingerprint(saved), CourseDesignBindings.fingerprint(swapped))
    }

    @Test fun fullVisitOrderRetainsSpecialRolesAndRepeatedControls() {
        val points = listOf(
            ProtectedCourseObjectPoint("start", "Start", ProtectedCourseObjectType.START, 39.0, -75.0),
            ProtectedCourseObjectPoint("waypoint", "Crossing", ProtectedCourseObjectType.WAYPOINT, 40.5, -75.5),
            ProtectedCourseObjectPoint("finish", "Finish", ProtectedCourseObjectType.FINISH, 42.0, -75.0))
        val visits = listOf("start", "old-fox1", "waypoint", "old-fox2", "old-fox1", "finish")
        val source = info().copy(courseObjects = points)
        val saved = CourseDesignBindings.prepare(source, controls,
            mapOf("old-fox1" to "race-2", "old-fox2" to "race-1"), visits, "revision-1")
        assertEquals(listOf("race-2", "race-1", "race-2"), saved.appliedBindings!!.orderedControlIds)
        assertEquals(visits, saved.appliedBindings.orderedPlacementIds)
        assertNull(CourseDesignBindings.validationError(saved))
        assertFailsWith<IllegalArgumentException> { CourseDesignBindings.prepare(source, controls,
            mapOf("old-fox1" to "race-2", "old-fox2" to "race-1"), visits - "waypoint", "rev") }
        assertFailsWith<IllegalArgumentException> { CourseDesignBindings.prepare(source, controls,
            mapOf("old-fox1" to "race-2", "old-fox2" to "race-1"), visits.reversed(), "rev") }
    }

    @Test fun conflictingRepresentationsCannotBeApplied() {
        val source = info().copy(courseObjects = listOf(ProtectedCourseObjectPoint(
            "old-fox1", "Fox2", ProtectedCourseObjectType.CONTROL, 50.0, -75.0)))
        assertFailsWith<IllegalArgumentException> { CourseDesignBindings.prepare(source, controls,
            mapOf("old-fox1" to "race-2", "old-fox2" to "race-1"), listOf("old-fox1", "old-fox2"), "rev") }
    }

    @Test fun raceFilesAndMixedProtectionSeriesKeepBindingsAndRequireCurrentSchema() {
        var project = EventProjectFactory.createEmptyProject("race", "Bindings fixture", "2026-09-06T09:00")
        project = EventProjectEditor.addCategory(project.copy(raceData = project.raceData.copy(controls = controls)), "m21", "M21")
        project = EventProjectEditor.updateCategoryCourseInfo(project, "m21", applied())
        val text = EventProjectFileJson.encode(project.copy(schemaVersion = 6))
        val plain = EventProjectFileJson.decode(text)
        assertEquals(EventProjectFileFormat.CURRENT_SCHEMA_VERSION, plain.schemaVersion)
        assertEquals(applied(), plain.raceData.categories.single().category.courseInfo)
        assertFalse(EventProjectFileFormat.isSupportedSchema(999))
        assertFailsWith<IllegalArgumentException> {
            EventProjectFileJson.decode(text.replace("\"schemaVersion\": 7", "\"schemaVersion\": 999"))
        }
        val encrypted = EventProjectEditor.updateCategoryEncryptedCourseInfo(project, "m21",
            ProtectedCourseCipher.encryptCourseInfo(applied(), "fixture-password"))
        val archive = EventSeriesArchive(EventSeriesFile(seriesId = "series", name = "Fixture", events = listOf(
            EventSeriesEvent("plain", "plain.json", 0, "Plain"), EventSeriesEvent("encrypted", "encrypted.json", 1, "Encrypted"))),
            mapOf("plain" to plain, "encrypted" to encrypted))
        val decoded = EventSeriesArchiveZipCodec.decode(EventSeriesArchiveZipCodec.encode(archive))
        assertEquals(applied(), decoded.member("plain").raceData.categories.single().category.courseInfo)
        assertEquals(applied(), ProtectedCourseCipher.decryptCourseInfo(
            decoded.member("encrypted").raceData.categories.single().category.encryptedCourseInfo!!, "fixture-password"))
    }

    @Test fun acceptedLabelsAndStationCodesMustBeValidBeforeApplication() {
        assertFailsWith<IllegalArgumentException> { CourseDesignBindings.prepare(info(), controls.map { it.copy(label = "Wrong") },
            mapOf("old-fox1" to "race-2", "old-fox2" to "race-1"), listOf("old-fox1", "old-fox2"), "rev") }
        assertFailsWith<IllegalArgumentException> { CourseDesignBindings.prepare(info(), controls.map { it.copy(siCode = -1) },
            mapOf("old-fox1" to "race-2", "old-fox2" to "race-1"), listOf("old-fox1", "old-fox2"), "rev") }
    }

    @Test fun invalidBindingCannotFallBackToLegacyGuessing() {
        val saved = applied()
        val unknown = saved.copy(appliedBindings = saved.appliedBindings!!.copy(version = 999))
        assertEquals(CourseResolutionStatus.INVALID, CourseControlResolver.resolve(controls.first(), listOf(unknown, info())).status)
        assertFailsWith<IllegalArgumentException> { CourseDesignBindings.prepare(info(), controls,
            mapOf("old-fox1" to "race-1", "old-fox2" to "race-1"), listOf("old-fox1"), "rev") }
    }
}
