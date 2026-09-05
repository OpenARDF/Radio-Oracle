package org.openardf.radiooracle.desktop

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.*
import java.nio.file.Files
import java.nio.file.Path

/** Opt-in local acceptance hook. Reads user archives; writes exports only under build/reports. */
class DesktopClassicRouteArchiveSmokeTest {
    @Test fun reproduceSavedTerrainReferencesAndExportRealSeries() = runBlocking {
        val input = System.getenv("RADIO_ORACLE_ROUTE_SMOKE_ARCHIVE")
        val baseline = System.getenv("RADIO_ORACLE_ROUTE_SMOKE_BASELINE")
        assumeTrue("Use just classic-route-smoke for local real-archive acceptance", !input.isNullOrBlank() && !baseline.isNullOrBlank())
        val path = Path.of(requireNotNull(input))
        val originalBytes = Files.readAllBytes(path)
        val archive = EventSeriesArchiveZipCodec.decode(originalBytes)
        val before = EventSeriesArchiveZipCodec.decode(Files.readAllBytes(Path.of(requireNotNull(baseline))))
        assertEquals(before.seriesFile, archive.seriesFile)
        val output = Path.of("build/reports/classic-route-live/verified-exports")
        Files.createDirectories(output)
        val surfaces = mutableMapOf<List<RouteElevationSource>, DesktopFrozenElevationSurface>()
        val notes = mutableListOf<String>()
        var readyCount = 0
        var idealCount = 0
        archive.seriesFile.sortedEvents().forEachIndexed { index, event ->
            val project = archive.member(event.seriesEventId)
            val original = before.member(event.seriesEventId)
            assertEquals("Analysis must not alter course design, scoring or readouts", original.raceData, project.raceData)
            if (project.raceData.race.raceType != RaceType.CLASSIC) {
                assertEquals(original, project)
                return@forEachIndexed
            }
            val metadata = requireNotNull(project.desktopRouteAnalysis)
            val ready = DesktopClassicRouteAnalysis.projection(project)
            assertTrue(ready.isNotEmpty())
            DesktopClassicRouteAnalysis.projection(original).forEach { (id, previous) ->
                assertEquals("Previously valid estimates must not change when unresolved punches are ignored", previous, ready[id])
            }
            readyCount += ready.size
            ready.values.forEach { length ->
                assertNotNull("Unprotected championship courses must recover their certified ideal order", length.idealRoute)
                assertTrue(length.text.matches(Regex("[0-9]+\\.[0-9]{2} km \\(.*\\)")))
                when (length.comparison) {
                    "Ideal order" -> { assertEquals(length.idealEffectiveMeters, length.effectiveMeters); idealCount++ }
                    "Alternative order" -> assertTrue(length.effectiveMeters >= length.idealEffectiveMeters)
                }
            }
            val sources = metadata.contexts.values.map { it.elevationSources }.distinct().single()
            val start = System.nanoTime()
            // Uses content-addressed retained sources only: no current-cache lookup or download.
            val surface = surfaces.getOrPut(sources) { DesktopVenueElevationCache.restoreRouteAnalysisSurface(sources) }
            val infos = (project.raceData.categories + project.raceData.courseMappings).mapNotNull {
                it.category.storedCourseInfo(null)?.let { info -> it.category.id to info }
            }.toMap()
            val recalculated = DesktopClassicRouteAnalysis.calculate(project.copy(desktopRouteAnalysis = null), infos, surface)
            assertEquals(ready, DesktopClassicRouteAnalysis.projection(project.copy(desktopRouteAnalysis = recalculated)))
            assertEquals(metadata.results.mapValues { it.value.unavailableReason }, recalculated.results.mapValues { it.value.unavailableReason })
            project.raceData.competitorData.forEach { data ->
                val readout = data.readoutData ?: return@forEach
                val snapshot = metadata.results[readout.result.id] ?: return@forEach
                if (snapshot.ignoredControlPunchIndexes.isEmpty()) return@forEach
                val ordered = readout.punches.filter { it.punch.punchType == org.openardf.radiooracle.shared.domain.SIRecordType.CONTROL }.sortedBy { it.punch.order }
                val omitted = snapshot.ignoredControlPunchIndexes.map(ordered::get)
                val withoutIgnored = project.copy(desktopRouteAnalysis = null, raceData = project.raceData.copy(competitorData = listOf(
                    data.copy(readoutData = readout.copy(punches = readout.punches.filterNot { it in omitted })))))
                val recomputed = DesktopClassicRouteAnalysis.calculate(withoutIgnored, infos, surface)
                assertEquals("Ignoring a punch must equal physically omitting it from the calculation input",
                    ready[readout.result.id], DesktopClassicRouteAnalysis.projection(withoutIgnored.copy(desktopRouteAnalysis = recomputed))[readout.result.id])
            }
            val stem = "race-${index + 1}"
            DesktopProjectFiles.write(output.resolve("$stem.rom.json"), project.copy(seriesLink = null))
            assertEquals(ready, DesktopClassicRouteAnalysis.projection(DesktopProjectFiles.read(output.resolve("$stem.rom.json"))))
            DesktopProjectFiles.exportResultsCsv(output.resolve("$stem.csv"), project)
            DesktopProjectFiles.exportSplitResultsCsv(output.resolve("$stem-splits.csv"), project)
            DesktopProjectFiles.exportResultsText(output.resolve("$stem.txt"), project)
            DesktopProjectFiles.exportResultsHtml(output.resolve("$stem.html"), project)
            DesktopProjectFiles.exportResultReportHtml(output.resolve("$stem-report.html"), project)
            DesktopProjectFiles.exportResultReportXml(output.resolve("$stem-report.xml"), project)
            DesktopProjectFiles.exportResultReportPdf(output.resolve("$stem-report.pdf"), project)
            DesktopProjectFiles.exportSplitResultsPdf(output.resolve("$stem-splits.pdf"), project)
            DesktopPublicResultSiteExports.export(output.resolve("website"), project, protectedCourseInfoByCategoryId = emptyMap())
            listOf("csv", "txt", "html").forEach { extension ->
                val text = Files.readString(output.resolve("$stem.$extension"))
                assertTrue(text.contains(ResultRouteLength.LABEL))
                assertFalse(text.contains("courseFingerprint"))
                assertFalse(text.contains("elevationSources"))
                if (extension != "csv") {
                    ready.values.forEach { assertTrue(text.contains(it.categoryHeadingSuffix)) }
                }
            }
            notes += "${project.raceData.race.name}: ${ready.size} ready; ${metadata.results.size - ready.size} unavailable; retained-source reproduction and all result export adapters verified (${(System.nanoTime() - start) / 1_000_000} ms)."
        }
        assertTrue(readyCount > 0)
        assertTrue("The acceptance series must include at least one ideal-order result", idealCount > 0)
        val site = DesktopPublicResultSiteExports.exportSeries(output.resolve("series-website"), archive.seriesFile.name,
            archive.seriesFile.sortedEvents().map { DesktopPublicResultSeriesRace(archive.member(it.seriesEventId)) }, archive.seriesFile.seriesId)
        val siteJson = Files.readString(site.publicResultsJson)
        assertFalse(siteJson.contains("courseFingerprint"))
        assertFalse(siteJson.contains("elevationSources"))
        // The series manifest links per-race payloads; the page fetches those payloads lazily.
        Json.parseToJsonElement(siteJson).jsonObject.getValue("races").jsonArray.forEachIndexed { index, entry ->
            val payload = Files.readString(site.eventDirectory.resolve(entry.jsonObject.getValue("dataUrl").jsonPrimitive.content).normalize())
            val project = archive.member(archive.seriesFile.sortedEvents()[index].seriesEventId)
            assertEquals(DesktopClassicRouteAnalysis.projection(project).size,
                Regex("\\\"estimatedEffectiveRouteLengthMeters\\\"").findAll(payload).count())
            assertFalse(payload.contains("courseFingerprint"))
            assertFalse(payload.contains("elevationSources"))
        }
        assertArrayEquals("Smoke checks must not modify the source archive", originalBytes, Files.readAllBytes(path))
        notes += "$readyCount saved estimates verified; $idealCount exact ideal-order matches. Non-Classic members and all race data unchanged. No current terrain-cache reads or network requests."
        Files.writeString(output.resolve("verification.txt"), notes.joinToString("\n", postfix = "\n"))
        Unit
    }
}
