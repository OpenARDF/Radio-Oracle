package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.EventControl
import org.openardf.radiooracle.shared.event.EventProjectFactory
import org.openardf.radiooracle.shared.event.EventProjectFile
import java.nio.file.Files

class DesktopCreateCourseKmlTest {
    @Test
    fun createsClassicStarterKmlWithoutSpectatorAndReusesMatchingEventControls() {
        val output = Files.createTempFile("radio-oracle-create-classic-course", ".kml")

        val result = DesktopCreateCourseKml.create(
            outputPath = output,
            eventType = RaceType.CLASSIC,
            center = DesktopKmlToolsPoint(latitude = 39.0, longitude = -95.0),
            projectFile = sampleProject(RaceType.CLASSIC)
        )

        assertEquals(8, result.pointCount)
        assertEquals(8, result.reusedControlCount)
        val kml = Files.readString(output)
        assertTrue(kml.contains("<Style id=\"${DesktopCourseKmlStyle.DonutStyleId}\">"))
        assertTrue(kml.contains("<Style id=\"${DesktopCourseKmlStyle.StartStyleId}\">"))
        assertTrue(kml.contains("<Style id=\"${DesktopCourseKmlStyle.FinishStyleId}\">"))
        assertTrue(kml.placemarkNamed("Start").contains("<styleUrl>#${DesktopCourseKmlStyle.StartStyleId}</styleUrl>"))
        assertTrue(kml.placemarkNamed("Finish").contains("<styleUrl>#${DesktopCourseKmlStyle.FinishStyleId}</styleUrl>"))
        assertTrue(kml.placemarkNamed("1").contains("SI=31"))
        assertTrue(kml.placemarkNamed("1").contains("Public label: 1"))
        assertTrue(kml.placemarkNamed("1").contains("Notes: Fox one note"))
        assertTrue(kml.placemarkNamed("B").contains("SI=99"))
        assertFalse(kml.contains("<name>Spectator</name>"))
        assertTrue(kml.placemarkNamed("M21 route").contains(kml.placemarkNamed("Start").pointCoordinate()))
        assertEquals(kml.pointCoordinates().size, kml.pointCoordinates().toSet().size)
    }

    @Test
    fun createsSprintStarterKmlWithSpectator() {
        val output = Files.createTempFile("radio-oracle-create-sprint-course", ".kml")

        val result = DesktopCreateCourseKml.create(
            outputPath = output,
            eventType = RaceType.SPRINT,
            center = DesktopKmlToolsPoint(latitude = 39.0, longitude = -95.0),
            projectFile = sampleProject(RaceType.SPRINT)
        )

        assertEquals(14, result.pointCount)
        val kml = Files.readString(output)
        assertTrue(kml.placemarkNamed("Spectator").contains("SI=46"))
        assertTrue(kml.placemarkNamed("Spectator").contains("<styleUrl>#${DesktopCourseKmlStyle.DonutStyleId}</styleUrl>"))
        assertTrue(kml.placemarkNamed("1F").contains("SI=41"))
        val routePlacemark = kml.placemarkNamed("M21 route")
        assertTrue(routePlacemark.contains(kml.placemarkNamed("Spectator").pointCoordinate()))
        assertTrue(
            routePlacemark.indexOf(kml.placemarkNamed("5").pointCoordinate()) <
                routePlacemark.indexOf(kml.placemarkNamed("Spectator").pointCoordinate())
        )
        assertTrue(
            routePlacemark.indexOf(kml.placemarkNamed("Spectator").pointCoordinate()) <
                routePlacemark.indexOf(kml.placemarkNamed("1F").pointCoordinate())
        )
    }

    @Test
    fun createsFoxoringStarterKmlWithoutSpectator() {
        val output = Files.createTempFile("radio-oracle-create-foxoring-course", ".kml")

        val result = DesktopCreateCourseKml.create(
            outputPath = output,
            eventType = RaceType.FOXORING,
            center = DesktopKmlToolsPoint(latitude = 39.0, longitude = -95.0),
            projectFile = sampleProject(RaceType.CLASSIC)
        )

        assertEquals(13, result.pointCount)
        assertEquals(0, result.reusedControlCount)
        val kml = Files.readString(output)
        assertFalse(kml.contains("<name>Spectator</name>"))
        assertTrue(kml.contains("Spectator is included for Sprint only."))
    }

    private fun sampleProject(raceType: RaceType): EventProjectFile =
        EventProjectFactory.createEmptyProject("race", "Course Test", "2026-06-05T09:00").let { project ->
            project.copy(
                raceData = project.raceData.copy(
                    race = project.raceData.race.copy(raceType = raceType),
                    controls = listOf(
                        EventControl("start", "race", "Start", 201, ControlPointType.CONTROL, publicLabel = "Start"),
                        EventControl("fox-1", "race", "Fox 1", 31, ControlPointType.CONTROL, publicLabel = "1", notes = "Fox one note"),
                        EventControl("fox-2", "race", "Fox 2", 32, ControlPointType.CONTROL, publicLabel = "2"),
                        EventControl("fox-3", "race", "Fox 3", 33, ControlPointType.CONTROL, publicLabel = "3"),
                        EventControl("fox-4", "race", "Fox 4", 34, ControlPointType.CONTROL, publicLabel = "4"),
                        EventControl("fox-5", "race", "Fox 5", 35, ControlPointType.CONTROL, publicLabel = "5"),
                        EventControl("fast-1", "race", "Fast 1", 41, ControlPointType.CONTROL, publicLabel = "1F"),
                        EventControl("fast-2", "race", "Fast 2", 42, ControlPointType.CONTROL, publicLabel = "2F"),
                        EventControl("fast-3", "race", "Fast 3", 43, ControlPointType.CONTROL, publicLabel = "3F"),
                        EventControl("fast-4", "race", "Fast 4", 44, ControlPointType.CONTROL, publicLabel = "4F"),
                        EventControl("fast-5", "race", "Fast 5", 45, ControlPointType.CONTROL, publicLabel = "5F"),
                        EventControl("spectator", "race", "Spectator", 46, ControlPointType.SEPARATOR, publicLabel = "Spectator"),
                        EventControl("beacon", "race", "Beacon", 99, ControlPointType.BEACON, publicLabel = "B"),
                        EventControl("finish", "race", "Finish", 202, ControlPointType.CONTROL, publicLabel = "Finish")
                    )
                )
            )
        }

    private fun String.placemarkNamed(name: String): String {
        val regex = Regex("<Placemark>\\s*<name>${Regex.escape(name)}</name>[\\s\\S]*?</Placemark>")
        return regex.find(this)?.value ?: error("Missing Placemark named $name")
    }

    private fun String.pointCoordinate(): String =
        Regex("<Point><coordinates>([^<]+)</coordinates></Point>")
            .find(this)
            ?.groupValues
            ?.get(1)
            ?: error("Missing point coordinate")

    private fun String.pointCoordinates(): List<String> =
        Regex("<Point><coordinates>([^<]+)</coordinates></Point>")
            .findAll(this)
            .map { it.groupValues[1] }
            .toList()
}
