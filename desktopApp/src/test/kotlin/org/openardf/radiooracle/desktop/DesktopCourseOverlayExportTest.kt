package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.EventControl
import org.openardf.radiooracle.shared.event.EventProjectEditor
import org.openardf.radiooracle.shared.event.EventProjectFactory
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.ProtectedCourseControlPoint
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.event.ProtectedCourseObjectPoint
import org.openardf.radiooracle.shared.event.ProtectedCourseObjectType
import org.openardf.radiooracle.shared.event.ProtectedCourseRoutePoint
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory

class DesktopCourseOverlayExportTest {
    @Test
    fun exportsThreeOomXmapOverlaysWithAudienceSpecificContent() {
        val outputDirectory = Files.createTempDirectory("radio-oracle-course-overlays")
        val baseMap = Files.createTempFile("radio-oracle-base-map", ".xmap")
        Files.writeString(baseMap, sampleBaseMap())
        val project = sampleProject("course-key")

        val summary = DesktopCourseOverlayExporter.exportOverlays(
            target = DesktopCourseOverlayExportTarget(
                baseMapPath = baseMap,
                outputDirectory = outputDirectory,
                startExclusionRadiusMeters = 750,
                finishExclusionRadiusMeters = 400
            ),
            projectFile = project,
            password = "course-key"
        )

        assertTrue(Files.exists(summary.competitorPath))
        assertTrue(Files.exists(summary.masterPath))
        assertTrue(Files.exists(summary.custodianPath))
        assertTrue(Files.exists(summary.editableCompetitorPath))
        assertTrue(Files.exists(summary.editableMasterPath))
        assertTrue(Files.exists(summary.editableCustodianPath))
        listOf(
            summary.competitorPath,
            summary.masterPath,
            summary.custodianPath,
            summary.editableCompetitorPath,
            summary.editableMasterPath,
            summary.editableCustodianPath
        ).forEach(::assertWellFormedXml)
        assertEquals(6, summary.exportedPointCount)
        assertEquals(2, summary.exclusionCircleCount)
        assertEquals(1, summary.finishCorridorCount)

        val competitor = Files.readString(summary.competitorPath)
        assertTrue(competitor.contains("""<barrier version="6" required="0.6.0">"""))
        assertTrue(competitor.contains("</barrier>"))
        assertTrue(competitor.contains("""<color priority="0" name="Radio-Oracle Purple" c="0.2" m="1" y="0" k="0""""))
        assertTrue(competitor.contains("""code="701" name="Radio-Oracle Start""""))
        assertTrue(competitor.contains("""code="702" name="Radio-Oracle Control point""""))
        assertTrue(competitor.contains("""code="705" name="Radio-Oracle Marked route""""))
        assertTrue(competitor.contains("""code="706" name="Radio-Oracle Finish""""))
        assertTrue(competitor.contains("""code="RO.1" name="Radio-Oracle ARDF exclusion circle""""))
        assertEquals(1, competitor.objectCountForSymbol(1))
        assertEquals(2, competitor.objectCountForSymbol(2))
        assertEquals(1, competitor.objectCountForSymbol(5))
        assertEquals(1, competitor.objectCountForSymbol(6))
        assertEquals(2, competitor.objectCountForSymbol(209))
        assertEquals(listOf("B", "Spectator"), competitor.textObjectValues())
        assertTrue(competitor.coordsBlocks().all { it.trimEnd().endsWith(";") })

        val master = Files.readString(summary.masterPath)
        assertEquals(4, master.objectCountForSymbol(2))
        assertEquals(listOf("B", "Spectator", "1", "2"), master.textObjectValues())
        assertTrue(master.coordsBlocks().all { it.trimEnd().endsWith(";") })

        val custodian = Files.readString(summary.custodianPath)
        assertEquals(0, custodian.objectCountForSymbol(5))
        assertEquals(0, custodian.objectCountForSymbol(209))
        assertEquals(4, custodian.objectCountForSymbol(2))
        assertEquals(1, custodian.objectCountForSymbol(1))
        assertEquals(1, custodian.objectCountForSymbol(6))
        assertEquals(listOf("B", "Spectator", "1", "2"), custodian.textObjectValues())
        assertTrue(custodian.coordsBlocks().all { it.trimEnd().endsWith(";") })

        val editableMaster = Files.readString(summary.editableMasterPath)
        assertTrue(editableMaster.contains("""<part name="Radio-Oracle master overlay">""") || editableMaster.contains("""<part name="Radio-Oracle master overlay"><objects"""))
        assertTrue(editableMaster.contains("""<objects count=""""))
        assertTrue(editableMaster.contains("""name="Radio-Oracle Control point""""))
        assertTrue(editableMaster.contains("""current="1""""))
    }

    @Test
    fun rejectsBaseMapWithoutProjectedUtmGeoreferencing() {
        val outputDirectory = Files.createTempDirectory("radio-oracle-course-overlays")
        val baseMap = Files.createTempFile("radio-oracle-local-base-map", ".xmap")
        Files.writeString(
            baseMap,
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <map xmlns="http://openorienteering.org/apps/mapper/xml/v2" version="9">
            <georeferencing scale="15000"><projected_crs id="Local"/></georeferencing>
            </map>
            """.trimIndent()
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            DesktopCourseOverlayExporter.exportOverlays(
                target = DesktopCourseOverlayExportTarget(
                    baseMapPath = baseMap,
                    outputDirectory = outputDirectory,
                    startExclusionRadiusMeters = 750,
                    finishExclusionRadiusMeters = 400
                ),
                projectFile = sampleProject("course-key"),
                password = "course-key"
            )
        }

        assertTrue(error.message.orEmpty().contains("map reference point"))
    }

    @Test
    fun defaultExclusionRadiusUsesEventRuleFamily() {
        assertEquals(750, DesktopCourseOverlayExporter.defaultExclusionRadiusMeters(RaceType.CLASSIC))
        assertEquals(750, DesktopCourseOverlayExporter.defaultExclusionRadiusMeters(RaceType.SHORT))
        assertEquals(100, DesktopCourseOverlayExporter.defaultExclusionRadiusMeters(RaceType.SPRINT))
        assertEquals(250, DesktopCourseOverlayExporter.defaultExclusionRadiusMeters(RaceType.FOXORING))
        assertEquals(0, DesktopCourseOverlayExporter.defaultExclusionRadiusMeters(RaceType.ORIENTEERING))
    }

    private fun sampleBaseMap(): String =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <map xmlns="http://openorienteering.org/apps/mapper/xml/v2" version="9">
        <georeferencing scale="15000" auxiliary_scale_factor="1" grivation="0"><ref_point x="10" y="35"/><projected_crs id="UTM"><spec language="PROJ.4">+proj=utm +datum=WGS84 +zone=32</spec><parameter>32 N</parameter><ref_point x="696686.398978" y="5347699.134904"/></projected_crs><geographic_crs id="Geographic coordinates"><spec language="PROJ.4">+proj=latlong +datum=WGS84</spec><ref_point_deg lat="48.25195669" lon="11.64973926"/></geographic_crs></georeferencing>
        <colors count="1"><color priority="0" name="Black" c="0" m="0" y="0" k="1" opacity="1"><cmyk method="custom"/><rgb method="cmyk" r="0" g="0" b="0"/></color></colors>
        <barrier version="6" required="0.6.0">
        <symbols count="1" id="Test"><symbol type="1" id="1" code="0" name="Existing"><point_symbol inner_radius="100" inner_color="0" outer_width="0" outer_color="-1" elements="0"/></symbol></symbols>
        <parts count="1" current="0"><part name="default part"><objects count="0"></objects></part></parts>
        </barrier>
        </map>
        """.trimIndent()

    private fun sampleProject(password: String): EventProjectFile =
        EventProjectEditor.addCategory(
            EventProjectFactory.createEmptyProject("race", "Overlay Test", "2026-06-05T09:00").copy(
                raceData = EventProjectFactory.createEmptyProject(
                    "race",
                    "Overlay Test",
                    "2026-06-05T09:00"
                ).raceData.copy(
                    race = EventProjectFactory.createEmptyProject(
                        "race",
                        "Overlay Test",
                        "2026-06-05T09:00"
                    ).raceData.race.copy(raceType = RaceType.CLASSIC),
                    controls = listOf(
                        EventControl(
                            id = "control-31",
                            raceId = "race",
                            label = "Fox 1",
                            siCode = 31,
                            type = ControlPointType.CONTROL,
                            publicLabel = "1",
                            latitude = 48.2522,
                            longitude = 11.6502
                        ),
                        EventControl(
                            id = "control-32",
                            raceId = "race",
                            label = "Fox 2",
                            siCode = 32,
                            type = ControlPointType.CONTROL,
                            publicLabel = "2",
                            latitude = 48.2524,
                            longitude = 11.6512
                        ),
                        EventControl(
                            id = "control-spectator",
                            raceId = "race",
                            label = "Spectator",
                            siCode = 46,
                            type = ControlPointType.SEPARATOR,
                            publicLabel = "Spectator",
                            latitude = 48.2526,
                            longitude = 11.6516
                        ),
                        EventControl(
                            id = "control-beacon",
                            raceId = "race",
                            label = "Beacon",
                            siCode = 99,
                            type = ControlPointType.BEACON,
                            publicLabel = "B",
                            latitude = 48.2528,
                            longitude = 11.6520
                        )
                    )
                )
            ),
            categoryId = "cat-m21",
            name = "M21"
        ).let { projectFile ->
            EventProjectEditor.updateCategoryEncryptedCourseInfo(
                projectFile,
                "cat-m21",
                DesktopProtectedCourseOrder.encryptCourseInfo(sampleCourseInfo(), password)
            )
        }

    private fun sampleCourseInfo(): ProtectedCourseInfo =
        ProtectedCourseInfo(
            idealOrder = "1 2",
            lengthMeters = 1200,
            climbMeters = 25,
            sourceName = "sample.kml",
            sourceSha256 = "abc123",
            sampledPointCount = 6,
            route = listOf(
                ProtectedCourseRoutePoint(latitude = 48.25195669, longitude = 11.64973926, elevationMeters = 500.0),
                ProtectedCourseRoutePoint(latitude = 48.2522, longitude = 11.6502, elevationMeters = 501.0),
                ProtectedCourseRoutePoint(latitude = 48.2526, longitude = 11.6516, elevationMeters = 502.0),
                ProtectedCourseRoutePoint(latitude = 48.2528, longitude = 11.6520, elevationMeters = 503.0),
                ProtectedCourseRoutePoint(latitude = 48.2530, longitude = 11.6524, elevationMeters = 504.0)
            ),
            controlPoints = listOf(
                ProtectedCourseControlPoint("control-31", "1", 48.2522, 11.6502, ControlPointType.CONTROL, 501.0),
                ProtectedCourseControlPoint("control-32", "2", 48.2524, 11.6512, ControlPointType.CONTROL, 502.0),
                ProtectedCourseControlPoint("control-beacon", "Beacon", 48.2528, 11.6520, ControlPointType.BEACON, 503.0)
            ),
            courseObjects = listOf(
                ProtectedCourseObjectPoint("start", "Start", ProtectedCourseObjectType.START, 48.25195669, 11.64973926, 500.0),
                ProtectedCourseObjectPoint("control-spectator", "Spectator", ProtectedCourseObjectType.SPECTATOR, 48.2526, 11.6516, 502.0),
                ProtectedCourseObjectPoint("control-beacon", "Beacon", ProtectedCourseObjectType.BEACON, 48.2528, 11.6520, 503.0),
                ProtectedCourseObjectPoint("finish", "Finish", ProtectedCourseObjectType.FINISH, 48.2530, 11.6524, 504.0)
            )
        )

    private fun String.objectCountForSymbol(symbolId: Int): Int =
        Regex("""<object\b[^>]*\bsymbol="$symbolId"""").findAll(this).count()

    private fun String.textObjectValues(): List<String> =
        Regex("""<object\b[^>]*\bsymbol="3"[\s\S]*?<text>([\s\S]*?)</text></object>""")
            .findAll(this)
            .map { it.groupValues[1] }
            .toList()

    private fun String.coordsBlocks(): List<String> =
        Regex("""<coords\b[^>]*>([^<]*)</coords>""")
            .findAll(this)
            .map { it.groupValues[1] }
            .toList()

    private fun assertWellFormedXml(path: Path) {
        DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(path.toFile())
    }
}
