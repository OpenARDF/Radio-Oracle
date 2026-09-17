package org.openardf.radiooracle.shared.files

import kotlin.test.*
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.event.*

class IofCourseControlMappingsTest {
    private fun project() = EventProjectFactory.createEmptyProject("race", "Mapping", "2026-09-17T09:00")
    private fun xml(id: String = "B", type: String = "Control", extra: String = "") = """
        <CourseData xmlns="http://www.orienteering.org/datastandard/3.0" iofVersion="3.0">
          <Event><Name>Mapping test</Name></Event>
          <RaceCourseData>
            <Control type="Start"><Id>St</Id><Position lat="35" lng="-78"/></Control>
            <Control type="$type"><Id>$id</Id>$extra<Position lat="35.01" lng="-78.01"/></Control>
            <Control type="Finish"><Id>End</Id><Position lat="35.02" lng="-78.02"/></Control>
            <Course><Name>M21</Name><CourseControl type="Start"><Control>St</Control></CourseControl>
              <CourseControl type="$type"><Control>$id</Control><LegLength>100</LegLength></CourseControl>
              <CourseControl type="Finish"><Control>End</Control><LegLength>200</LegLength></CourseControl></Course>
            <Course><Name>W21</Name><CourseControl type="Start"><Control>St</Control></CourseControl>
              <CourseControl type="$type"><Control>$id</Control></CourseControl>
              <CourseControl type="Finish"><Control>End</Control></CourseControl></Course>
          </RaceCourseData>
        </CourseData>
    """.trimIndent()

    @Test fun aliasesReachSchemaValidatedReviewAndOneMappingFeedsEveryCourse() {
        val original = project()
        val sources = IofXmlImports.validatedCourseControlSources(xml(), IofXmlSchemaResource.loadBundledSchema())
        val initial = IofCourseControlMappings.prefill(sources, original)
        assertEquals(3, initial.size)
        assertEquals("B", initial[1].publicName)
        assertEquals(ProtectedCourseObjectType.BEACON, initial[1].pointType)
        assertEquals("", initial[1].siCode)
        assertTrue(IofCourseControlMappings.errors(sources, initial, false).isNotEmpty())
        val rows = initial.map { if (it.sourceId == "B") it.copy(siCode = "79", publicName = "Beacon") else it }
        val parsed = IofXmlImports.courseDataWithControlMappings(xml(), original.raceData.race, rows).parsedData
        val candidate = EventProjectEditor.importIofCourseData(original, parsed).projectFile
        assertTrue(original.raceData.controls.isEmpty())
        val beacon = candidate.raceData.controls.single()
        assertEquals(79, beacon.siCode)
        assertEquals("Beacon", EventControlCatalog.displayLabel(beacon))
        assertEquals(ControlPointType.BEACON, beacon.type)
        assertTrue(candidate.raceData.courseMappings.all { it.controlPoints.single().controlId == beacon.id })
        assertTrue(candidate.raceData.courseMappings.all { it.category.courseInfo!!.courseObjects[1].label == "Beacon" })
        assertEquals(100.0, candidate.raceData.courseMappings.first().category.courseInfo!!.suppliedLegLengths.first().lengthMeters)
        val reopened = EventProjectFileJson.decode(EventProjectFileJson.encode(candidate))
        assertEquals(candidate.raceData.controls, reopened.raceData.controls)
    }

    @Test fun explicitPunchingCodeWinsAndExistingRolesAndNamesArePreserved() {
        val original = project().let { it.copy(raceData = it.raceData.copy(controls = listOf(
            EventControl("beacon", "race", "M", 79, ControlPointType.BEACON, publicLabel = "Beacon")
        ))) }
        val sources = IofXmlImports.courseControlSources(xml("31", extra = "<PunchingUnitId>79</PunchingUnitId>"))
        val rows = IofCourseControlMappings.prefill(sources, original)
        assertEquals("79", rows[1].siCode)
        assertEquals("Beacon", rows[1].publicName)
        assertEquals(ProtectedCourseObjectType.BEACON, rows[1].pointType)
        assertTrue(rows[1].notes.any { "differs" in it })
        val candidate = EventProjectEditor.importIofCourseData(original,
            IofXmlImports.courseDataWithControlMappings(xml("31", extra = "<PunchingUnitId>79</PunchingUnitId>"), original.raceData.race, rows).parsedData).projectFile
        assertEquals("beacon", candidate.raceData.controls.single().id)
        assertEquals(ControlPointType.BEACON, candidate.raceData.controls.single().type)
    }

    @Test fun alphabeticIdsNeverSupplyTheirEmbeddedDigitsAsStationCodes() {
        val sources = IofXmlImports.courseControlSources(xml("F1"))
        val row = IofCourseControlMappings.prefill(sources, project())[1]
        assertEquals("F1", row.publicName)
        assertEquals("", row.siCode)
        assertEquals(ProtectedCourseObjectType.CONTROL, row.pointType)
    }

    @Test fun schemaSupportedNamesAndMultipleStationHintsReachReviewWithoutBeingLost() {
        val original = project()
        val input = xml("31", extra = "<PunchingUnitId>79</PunchingUnitId><PunchingUnitId>80</PunchingUnitId><Name>Beacon</Name>")
        val sources = IofXmlImports.validatedCourseControlSources(input, IofXmlSchemaResource.loadBundledSchema())
        val rows = IofCourseControlMappings.prefill(sources, original)
        assertEquals("Beacon", rows[1].publicName)
        assertEquals("79", rows[1].siCode)
        assertEquals(ProtectedCourseObjectType.BEACON, rows[1].pointType)
        assertTrue(rows[1].notes.any { "79, 80" in it })
        val reviewed = rows.map { if (it.sourceId == "31") it.copy(siCode = "80") else it }
        val candidate = EventProjectEditor.importIofCourseData(original,
            IofXmlImports.courseDataWithControlMappings(input, original.raceData.race, reviewed).parsedData).projectFile
        assertEquals(80, candidate.raceData.controls.single().siCode)
        assertEquals("Beacon", EventControlCatalog.displayLabel(candidate.raceData.controls.single()))
    }

    @Test fun xmlEndpointTypesTakePrecedenceOverAmbiguousAliases() {
        val row = IofCourseControlMappings.prefill(IofXmlImports.courseControlSources(xml("S", "Start")), project())[1]
        assertEquals(ProtectedCourseObjectType.START, row.pointType)
        assertTrue(row.notes.any { "Spectator" in it })
        for (alias in listOf("S", "Sp")) assertEquals(ProtectedCourseObjectType.SPECTATOR,
            IofCourseControlMappings.prefill(IofXmlImports.courseControlSources(xml(alias)), project())[1].pointType)
        for (alias in listOf("F", "Fin")) assertEquals(ProtectedCourseObjectType.FINISH,
            IofCourseControlMappings.prefill(IofXmlImports.courseControlSources(xml(alias)), project())[1].pointType)
    }

    @Test fun missingOutOfRangeDuplicateAndIncompleteMappingsCannotBeConverted() {
        val original = project()
        val sources = IofXmlImports.courseControlSources(xml("31"))
        val rows = IofCourseControlMappings.prefill(sources, original)
        assertTrue(IofCourseControlMappings.errors(sources, rows, false).isEmpty())
        for (code in listOf("", "0", "512", "900", "F1")) assertFailsWith<IllegalArgumentException> {
            IofXmlImports.courseDataWithControlMappings(xml("31"), original.raceData.race,
                rows.map { if (it.sourceId == "31") it.copy(siCode = code) else it })
        }
        assertFailsWith<IllegalArgumentException> {
            IofXmlImports.courseDataWithControlMappings(xml("31"), original.raceData.race, rows.drop(1))
        }
        val duplicate = rows.map { it.copy(pointType = ProtectedCourseObjectType.CONTROL, siCode = "31") }
        assertTrue(IofCourseControlMappings.errors(sources, duplicate, false).any { "multiple XML controls" in it })
        assertTrue(original.raceData.controls.isEmpty())
    }

    @Test fun routePointOptionPreservesSourceIdsBendsAndSuppliedLegLengths() {
        val original = project()
        val rows = IofCourseControlMappings.prefill(IofXmlImports.courseControlSources(xml("900")), original)
        assertFailsWith<IllegalArgumentException> { IofXmlImports.courseDataWithControlMappings(xml("900"), original.raceData.race, rows) }
        val parsed = IofXmlImports.courseDataWithControlMappings(xml("900"), original.raceData.race,
            rows.map { if (it.sourceId == "900") it.copy(siCode = "") else it }, true).parsedData
        assertTrue(parsed.categories.all { it.controlPoints.isEmpty() })
        val info = parsed.categories.first().category.courseInfo!!
        assertEquals(ProtectedCourseObjectType.WAYPOINT, info.courseObjects[1].type)
        assertEquals(info.courseObjects[1].id, info.suppliedLegLengths.first().toId)
        assertEquals(3, info.route.size)
    }

    @Test fun schemaInvalidAndDuplicateSourceIdentifiersAreRejectedBeforeReview() {
        assertFailsWith<IofXmlImportException> {
            IofXmlImports.validatedCourseControlSources(xml().replace("iofVersion=\"3.0\"", "iofVersion=\"2.0\""), IofXmlSchemaResource.loadBundledSchema())
        }
        assertFailsWith<IllegalArgumentException> { IofXmlImports.courseControlSources(xml("End")) }
    }
}
