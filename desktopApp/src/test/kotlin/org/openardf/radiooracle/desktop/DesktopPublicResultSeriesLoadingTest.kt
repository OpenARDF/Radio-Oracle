package org.openardf.radiooracle.desktop

import org.junit.Assert.*
import org.junit.Test
import org.openardf.radiooracle.shared.event.*
import java.nio.file.Files

class DesktopPublicResultSeriesLoadingTest {
    @Test fun missingMemberNeverProducesAPartialPublication() {
        val folder = Files.createTempDirectory("series-coverage-")
        val manifest = folder.resolve("series.radio-oracle.json")
        Files.writeString(manifest, EventSeriesFileJson.encode(EventSeriesFile(seriesId = "series", name = "Series", events = listOf(
            EventSeriesEvent("missing", "missing.json", 0, "Missing race")))))
        assertThrows(IllegalArgumentException::class.java) {
            loadPublicResultSeriesRaces(manifest, null, null, null, emptyMap(), EventAwardDisplayMode.FIRST_TO_THIRD)
        }
    }

    @Test fun courseInclusiveExportLoadsFrozenStoredDataAndResultsOnlySkipsItExplicitly() {
        val folder = Files.createTempDirectory("series-policy-")
        val manifest = folder.resolve("series.radio-oracle.json")
        val race = folder.resolve("race.json")
        var project = EventProjectEditor.addCategory(EventProjectFactory.createEmptyProject("race", "Race", "2026-09-06T09:00"), "m21", "M21")
        val info = ProtectedCourseInfo(sourceName = "current", route = listOf(ProtectedCourseRoutePoint(40.0, -75.0), ProtectedCourseRoutePoint(40.1, -75.0)))
        project = project.withStoredCourseInfo("m21", info, null)
        DesktopProjectFiles.write(race, project)
        Files.writeString(manifest, EventSeriesFileJson.encode(EventSeriesFile(seriesId = "series", name = "Series", events = listOf(EventSeriesEvent("race", "race.json", 0, "Race")))))
        val result = loadPublicResultSeriesRaces(manifest, race, project, null,
            mapOf("m21" to info.copy(sourceName = "stale")), EventAwardDisplayMode.FIRST_TO_THIRD, true).second.single()
        assertEquals(info, result.protectedCourseInfoByCategoryId.getValue("m21"))
        assertTrue(result.includeCourseDiagrams)
        val locked = project.withStoredCourseInfo("m21", info, "fixture-password")
        assertThrows(IllegalArgumentException::class.java) {
            loadPublicResultSeriesRaces(manifest, race, locked, null, emptyMap(), EventAwardDisplayMode.FIRST_TO_THIRD, true)
        }
        val onlyResults = loadPublicResultSeriesRaces(manifest, race, locked, null, emptyMap(), EventAwardDisplayMode.FIRST_TO_THIRD, false).second.single()
        assertFalse(onlyResults.includeCourseDiagrams)
        assertTrue(onlyResults.protectedCourseInfoByCategoryId.isEmpty())
    }
}
