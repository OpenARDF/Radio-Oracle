package org.openardf.radiooracle.desktop

import kotlinx.coroutines.runBlocking
import java.io.PrintStream
import java.nio.file.Path

/** Copy-only CLI adapter; never replaces the input archive or prints protected geometry. */
internal fun classicRouteLengths(args: List<String>, out: PrintStream, err: PrintStream): Int {
    if (args.size != 2) {
        err.println("classic-route-lengths requires an input .rom.json and a separate output .rom.json; optional password: RADIO_ORACLE_ROUTE_PASSWORD environment variable.")
        return 2
    }
    return try {
        val source = Path.of(args[0]).toAbsolutePath().normalize()
        val target = Path.of(args[1]).toAbsolutePath().normalize()
        require(source != target) { "Choose a separate output Race File." }
        val project = DesktopProjectFiles.read(source)
        val password = System.getenv("RADIO_ORACLE_ROUTE_PASSWORD")
        val infos = (project.raceData.categories + project.raceData.courseMappings).mapNotNull {
            it.category.storedCourseInfo(password)?.let { info -> it.category.id to info }
        }.toMap()
        val surface = DesktopVenueElevationCache.freezeForRouteAnalysis(bounds = DesktopClassicRouteAnalysis.terrainBounds(infos.values))
        val result = runBlocking { DesktopClassicRouteAnalysis.calculate(project, infos, surface) }
        DesktopProjectFiles.write(target, project.copy(desktopRouteAnalysis = result, seriesLink = null))
        out.println("Classic route analysis saved: ${result.results.values.count { it.length != null }} ready; ${result.results.values.count { it.length == null }} unavailable.")
        0
    } catch (e: Exception) {
        err.println("Classic route analysis failed: ${e.message}")
        1
    }
}
