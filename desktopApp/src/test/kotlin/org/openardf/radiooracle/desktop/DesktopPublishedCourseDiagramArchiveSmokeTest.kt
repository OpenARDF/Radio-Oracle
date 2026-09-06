package org.openardf.radiooracle.desktop

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.event.EventSeriesArchiveZipCodec
import java.nio.file.Files
import java.nio.file.Path

/** Optional read-only acceptance check against an unprotected championship archive. */
class DesktopPublishedCourseDiagramArchiveSmokeTest {
    @Test
    fun imported80mDiagramsContainEveryAssignedFox() {
        val archivePath = System.getenv("RADIO_ORACLE_ROUTE_SMOKE_ARCHIVE")
        assumeTrue("Set RADIO_ORACLE_ROUTE_SMOKE_ARCHIVE to render real course diagrams", !archivePath.isNullOrBlank())
        val path = Path.of(requireNotNull(archivePath))
        val original = Files.readAllBytes(path)
        val archive = EventSeriesArchiveZipCodec.decode(original)
        val project = archive.membersBySeriesEventId.values.single { it.raceData.race.name == "Classic 80m" }
        val controls = project.raceData.controls.associateBy { it.id }
        val output = Path.of("build", "reports", "published-course-diagrams")
        val names = setOf("M21", "M50", "W55", "M60")
        val categories = project.raceData.categories.filter { it.category.name in names }
        assertEquals(names, categories.map { it.category.name }.toSet())
        categories.forEach { category ->
            val info = requireNotNull(category.category.storedCourseInfo(null))
            val summary = DesktopCourseAnalyzer.analyze(
                project, category.category.id, info, info.idealOrder,
                magneticDeclinationProvider = DesktopMagneticDeclination::result,
                controlIdentityMode = DesktopCourseControlIdentityMode.RESULT_CONTROLS
            )
            val map = summary.routeMaps.first()
            val expected = category.controlPoints.map { controls.getValue(requireNotNull(it.controlId)) }
                .filter { it.type == ControlPointType.CONTROL }.map { it.publicLabel ?: it.label }.sorted()
            assertEquals(category.category.name, expected,
                map.points.filter { it.type == DesktopCourseRouteMapPointType.Control }.map { it.label }.sorted())
            DesktopCourseGraphic.writeWebPng(output.resolve("${category.category.name}.png"),
                map.copy(title = "${category.category.name} course"), simplifyRouteToStops = true)
        }
        assertArrayEquals("Diagram generation must not change the source archive", original, Files.readAllBytes(path))
    }
}
