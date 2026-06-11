package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.CompetitorCsvImportDuplicatePolicy
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventCompetitor
import org.openardf.radiooracle.shared.event.EventCompetitorCategory
import org.openardf.radiooracle.shared.event.EventCompetitorData
import org.openardf.radiooracle.shared.event.EventControl
import org.openardf.radiooracle.shared.event.EventProjectEditor
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventRace
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.EventStartListDetails
import org.openardf.radiooracle.shared.event.StartDrawClubHandling
import org.openardf.radiooracle.shared.event.StartDrawOptions
import org.openardf.radiooracle.shared.event.StartDrawStartGroupMode
import org.openardf.radiooracle.shared.files.CompetitorCsvImportProfile
import org.openardf.radiooracle.shared.files.EventCsvImports
import java.nio.file.Files

/**
 * Broad generated coverage for event-data flows that are hard to exercise manually:
 * category imports, KML-derived assigned controls, competitor imports, start-list drawing, and
 * Race Ops readiness. The scenarios are intentionally compact but cover both valid data and common
 * operator mistakes.
 */
class DesktopEventDataGeneratedCoverageTest {
    @Test
    fun generatedCategoryCompetitorAndStartListScenariosDoNotBreak() {
        val executed = listOf(
            ::runCategoryCsvImportScenarios,
            ::runCategoryControlAssignmentScenarios,
            ::runKmlCategoryAssignmentPopulationScenarios,
            ::runCompetitorImportScenarios,
            ::runStartListScenarios
        ).sumOf { it() }

        assertTrue("Expected broad generated event-data coverage; ran $executed scenarios", executed >= 24)
    }

    private fun runCategoryCsvImportScenarios(): Int {
        var scenarios = 0
        val result = EventCsvImports.parseAndroidCategoryRows(
            """
            M21;1;99;9000;300;1;;;;6;31,32,33,34,35,99B
            W21;0;99;7000;200;1;;;;4;31,33,35,99B
            SPRINT-M21;1;99;3500;80;0;SPRINT;45;80m;6;31,32,46!,41,42,99B
            FOX-M21;1;99;8000;150;0;FOXORING;90;80m;11;31,32,33,34,35,36,37,38,39,40,99B
            bad-row
            """.trimIndent()
        )
        scenarios += 5
        assertEquals(4, result.rows.size)
        assertEquals(1, result.invalidLines.size)

        val imported = EventProjectEditor.importCategoryRows(
            projectFile = emptyProject(RaceType.CLASSIC),
            rows = result.rows,
            categoryIdFactory = idFactory("cat"),
            controlPointIdFactory = { categoryId, index -> "$categoryId-cp-$index" }
        )
        assertEquals(listOf("M21", "W21", "SPRINT-M21", "FOX-M21"), imported.raceData.categories.map { it.category.name })
        assertEquals(listOf(31, 32, 33, 34, 35, 99), imported.assignedSiCodes("M21"))
        assertEquals(RaceType.SPRINT, imported.category("SPRINT-M21").category.raceType)
        assertEquals(RaceType.FOXORING, imported.category("FOX-M21").category.raceType)
        assertTrue(imported.raceData.controls.any { it.siCode == 40 && it.type == ControlPointType.CONTROL })

        assertFails("duplicate category names should be rejected") {
            EventProjectEditor.importCategoryRows(
                projectFile = imported,
                rows = result.rows.take(1),
                categoryIdFactory = idFactory("dup-cat"),
                controlPointIdFactory = { categoryId, index -> "$categoryId-cp-$index" }
            )
        }
        scenarios++
        return scenarios
    }

    private fun runCategoryControlAssignmentScenarios(): Int {
        var scenarios = 0
        var project = emptyProject(RaceType.CLASSIC)
            .withControls(classicControls())
            .withCategory("M21")
        val categoryId = project.category("M21").category.id

        project = EventProjectEditor.updateCategoryControlPoints(project, categoryId, "1 3 5 B") { index -> "m21-cp-$index" }
        scenarios++
        assertEquals(listOf("control-1", "control-3", "control-5", "control-b"), project.category("M21").controlPoints.map { it.controlId })
        assertEquals("31 33 35 99B", project.assignedControlText("M21"))

        val cleared = EventProjectEditor.updateCategoryControlPoints(project, categoryId, " ") { index -> "clear-cp-$index" }
        scenarios++
        assertTrue(cleared.category("M21").controlPoints.isEmpty())
        assertEquals("", cleared.assignedControlText("M21"))

        val withUndefined = EventProjectEditor.updateCategoryControlPoints(cleared, categoryId, "77 99B") { index -> "undef-cp-$index" }
        scenarios++
        assertTrue(withUndefined.raceData.controls.any { it.siCode == 77 && it.type == ControlPointType.CONTROL })
        assertEquals(listOf(77, 99), withUndefined.assignedSiCodes("M21"))

        assertFails("duplicate assigned foxes should be rejected") {
            EventProjectEditor.updateCategoryControlPoints(project, categoryId, "31 31") { index -> "bad-cp-$index" }
        }
        scenarios++
        return scenarios
    }

    private fun runKmlCategoryAssignmentPopulationScenarios(): Int {
        var scenarios = 0
        val project = emptyProject(RaceType.CLASSIC)
            .withControls(classicControls())
            .withCategory("M21")
            .withCategory("M50")
            .withCategory("W65")
        val kmlPath = Files.createTempFile("radio-oracle-category-assignment", ".kml")
        // Keep skipped controls well outside each shorter route's 50 m matching corridor. That
        // makes failures point to assignment logic instead of accidental collinear geometry.
        Files.writeString(
            kmlPath,
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2"><Document>
              ${point("1", -78.800, 35.820)}
              ${point("2", -78.790, 35.845)}
              ${point("3", -78.770, 35.822)}
              ${point("4", -78.760, 35.850)}
              ${point("5", -78.740, 35.825)}
              ${point("B", -78.725, 35.845)}
              ${route("M21", listOf(-78.800 to 35.820, -78.790 to 35.845, -78.770 to 35.822, -78.760 to 35.850, -78.740 to 35.825, -78.725 to 35.845))}
              ${route("M50", listOf(-78.800 to 35.820, -78.770 to 35.822, -78.740 to 35.825, -78.725 to 35.845))}
              ${route("W65", listOf(-78.790 to 35.845, -78.760 to 35.850, -78.725 to 35.845))}
            </Document></kml>
            """.trimIndent().trimStart()
        )

        val (imported, summary) = DesktopCourseKmlImporter.importProtectedCourseInfo(
            path = kmlPath,
            projectFile = project,
            password = PASSWORD,
            elevationProvider = { 100.0 }
        )
        scenarios++
        assertEquals(3, summary.importedCategoryCount)
        assertEquals(13, summary.assignedCategoryControlCount)
        assertTrue(project.raceData.categories.all { it.controlPoints.isEmpty() })
        assertTrue(imported.raceData.categories.all { it.controlPoints.isEmpty() })

        val applied = DesktopCourseKmlImporter.applyCategoryAssignmentUpdates(imported, summary.categoryAssignmentUpdates)
        scenarios++
        assertEquals(listOf(31, 32, 33, 34, 35, 99), applied.assignedSiCodes("M21"))
        assertEquals(listOf(31, 33, 35, 99), applied.assignedSiCodes("M50"))
        assertEquals(listOf(32, 34, 99), applied.assignedSiCodes("W65"))
        assertEquals("31 33 35 99B", applied.assignedControlText("M50"))

        val m50Info = DesktopProtectedCourseOrder.decryptCourseInfo(
            requireNotNull(applied.category("M50").category.encryptedCourseInfo),
            PASSWORD
        )
        assertEquals(listOf("1", "3", "5", "B"), m50Info.controlPoints.map { it.label })
        scenarios++
        return scenarios
    }

    private fun runCompetitorImportScenarios(): Int {
        var scenarios = 0
        val categoryProject = emptyProject(RaceType.CLASSIC)
            .withControls(classicControls())
            .withCategory("M21")
            .withCategory("W21")
            .withCategory("M50")

        val csv = """
            ${org.openardf.radiooracle.shared.files.EventCsvFormat.Competitor.HEADER_ROW}
            123456;101;Alice;Runner;W21;1;1985;OKC;REG001;;0;1;B101;ALR
            123457;102;Bob;Climber;M21;0;1979;OKC;REG002;12:00;0;2;B102;BOB
            ;103;Casey;NoCard;M50;0;1965;TUL;REG003;;1;3;B103;CAS
            123458;;Dana;Newcat;W50;1;1970;NWA;REG004;;0;;B104;DAN
            bad
        """.trimIndent()
        val parsed = EventCsvImports.parseAndroidCompetitorRows(csv)
        scenarios += 5
        assertEquals(4, parsed.rows.size)
        assertEquals(1, parsed.invalidLines.size)
        val outcome = EventProjectEditor.importCompetitorRowsWithOutcome(
            projectFile = categoryProject,
            rows = parsed.rows,
            competitorIdFactory = idFactory("comp"),
            categoryIdFactory = idFactory("placeholder")
        )
        val imported = outcome.projectFile
        assertEquals(4, outcome.importedCount)
        assertEquals(1, outcome.warnings.count { it.contains("created placeholder category 'W50'") })
        assertEquals(4, imported.raceData.competitorData.size)
        assertEquals(104, imported.raceData.competitorData.single { it.competitorCategory.competitor.lastName == "Newcat" }.competitorCategory.competitor.startNumber)

        val ardf = EventCsvImports.parseAndroidCompetitorRows(
            """
            Jméno;Příjmení;Registrace;SI;Kategorie
            Eva;Forester;REG005;123459;W21
            Finn;Map;REG006;;M21
            """.trimIndent()
        )
        scenarios++
        assertEquals(CompetitorCsvImportProfile.ARDF_EVENT_REGISTRATION, EventCsvImports.detectCompetitorProfile("Jméno;Příjmení;Registrace;SI;Kategorie"))
        val withArdf = EventProjectEditor.importCompetitorRows(
            imported,
            ardf.rows,
            competitorIdFactory = idFactory("ardf-comp"),
            categoryIdFactory = idFactory("ardf-cat")
        )
        assertEquals(6, withArdf.raceData.competitorData.size)

        assertFails("duplicate SI numbers should be rejected") {
            EventProjectEditor.importCompetitorRows(
                withArdf,
                listOf(parsed.rows.first().copy(startNumber = 201, index = "REG999", bibNumber = "B999", callSign = "ZZZ")),
                competitorIdFactory = idFactory("dup-comp"),
                categoryIdFactory = idFactory("dup-cat")
            )
        }
        scenarios++

        val updatedOutcome = EventProjectEditor.importCompetitorRowsWithOutcome(
            projectFile = withArdf,
            rows = listOf(parsed.rows.first().copy(firstName = "Alicia", club = "NEW")),
            competitorIdFactory = idFactory("update-comp"),
            categoryIdFactory = idFactory("update-cat"),
            duplicatePolicy = CompetitorCsvImportDuplicatePolicy.UPDATE_EXISTING_BY_IMPORT_KEY
        )
        scenarios++
        assertEquals(1, updatedOutcome.updatedCount)
        assertEquals("Alicia", updatedOutcome.projectFile.raceData.competitorData.single { it.competitorCategory.competitor.index == "REG001" }.competitorCategory.competitor.firstName)

        val skippedOutcome = EventProjectEditor.importCompetitorRowsWithOutcome(
            projectFile = withArdf,
            rows = listOf(parsed.rows.first().copy(firstName = "Skipped")),
            competitorIdFactory = idFactory("skip-comp"),
            categoryIdFactory = idFactory("skip-cat"),
            duplicatePolicy = CompetitorCsvImportDuplicatePolicy.SKIP_EXISTING_BY_IMPORT_KEY
        )
        scenarios++
        assertEquals(1, skippedOutcome.skippedCount)

        val startRows = EventCsvImports.parseAndroidCompetitorStartRows("101;15:00;123456\n102;17:00;123457\n999;19:00;123999")
        val withStarts = EventProjectEditor.importCompetitorStartRows(withArdf, startRows.rows)
        scenarios++
        assertEquals(900L, withStarts.raceData.competitorData.single { it.competitorCategory.competitor.startNumber == 101 }.competitorCategory.competitor.drawnStartTimeSeconds)
        assertEquals(null, withStarts.raceData.competitorData.single { it.competitorCategory.competitor.startNumber == 103 }.competitorCategory.competitor.drawnStartTimeSeconds)

        return scenarios
    }

    private fun runStartListScenarios(): Int {
        var scenarios = 0
        val project = startListProject()
        val before = DesktopNavigationReadiness.from(project)
        scenarios++
        assertFalse(DesktopNavigation.isWorkflowEnabled(DesktopWorkflow.RaceOps, before))
        assertEquals(12, before.unscheduledCompetitorCount)

        val drawn = EventProjectEditor.drawStartList(project, "02:00")
        val details = EventStartListDetails.from(drawn.raceData)
        val readiness = DesktopNavigationReadiness.from(drawn)
        scenarios++
        assertEquals(12, details.scheduledCount)
        assertEquals(0, details.unscheduledCount)
        assertTrue(DesktopNavigation.isWorkflowEnabled(DesktopWorkflow.RaceOps, readiness))
        assertEquals(null, DesktopNavigation.disabledWorkflowReason(DesktopWorkflow.RaceOps, readiness))

        val twoAtATime = EventProjectEditor.drawStartList(
            project,
            "01:00",
            StartDrawOptions(startersPerStartTime = 2, clubHandling = StartDrawClubHandling.IGNORE, seed = "two-at-time")
        )
        scenarios++
        assertTrue(EventStartListDetails.from(twoAtATime.raceData).rows.groupBy { it.startTimeText }.values.any { it.size == 2 })

        val preferred = EventProjectEditor.drawStartList(
            project.withPreferredStartGroups(),
            "01:00",
            StartDrawOptions(startGroupMode = StartDrawStartGroupMode.PREFERRED_THIRDS, seed = "preferred")
        )
        scenarios++
        assertEquals(12, EventStartListDetails.from(preferred.raceData).scheduledCount)

        val firstSeed = EventProjectEditor.drawStartList(project, "02:00", StartDrawOptions(seed = "repeat")).startOrder()
        val secondSeed = EventProjectEditor.drawStartList(project, "02:00", StartDrawOptions(seed = "repeat")).startOrder()
        val differentSeed = EventProjectEditor.drawStartList(project, "02:00", StartDrawOptions(seed = "different")).startOrder()
        scenarios++
        assertEquals(firstSeed, secondSeed)
        assertNotEquals(firstSeed, differentSeed)

        assertFails("zero interval should be rejected") {
            EventProjectEditor.drawStartList(project, "00:00")
        }
        assertFails("invalid starters per start time should be rejected") {
            StartDrawOptions(startersPerStartTime = 0)
        }
        scenarios += 2
        return scenarios
    }

    private fun startListProject(): EventProjectFile {
        var project = emptyProject(RaceType.CLASSIC)
            .withControls(classicControls())
            .withCategory("M21")
            .withCategory("W21")
            .withCategory("M50")
        val categories = project.raceData.categories.map { it.category }
        val competitors = (1..12).map { index ->
            val category = categories[(index - 1) % categories.size]
            EventCompetitorData(
                competitorCategory = EventCompetitorCategory(
                    competitor = EventCompetitor(
                        id = "start-comp-$index",
                        raceId = RACE_ID,
                        categoryId = category.id,
                        firstName = "Runner$index",
                        lastName = "Test$index",
                        club = if (index % 2 == 0) "OKC" else "TUL",
                        index = "IDX$index",
                        isMan = index % 2 == 0,
                        birthYear = 1980 + index,
                        siNumber = 200000 + index,
                        siRent = false,
                        startNumber = index,
                        drawnStartTimeSeconds = null,
                        preferredStartGroup = null,
                        bibNumber = "B$index",
                        callSign = "C$index"
                    ),
                    category = category
                ),
                readoutData = null
            )
        }
        project = project.copy(raceData = project.raceData.copy(competitorData = competitors))
        return project.copy(
            raceData = project.raceData.copy(
                categories = project.raceData.categories.map { categoryData ->
                    categoryData.copy(
                        competitors = competitors
                            .map { it.competitorCategory.competitor }
                            .filter { it.categoryId == categoryData.category.id }
                    )
                }
            )
        )
    }

    private fun EventProjectFile.withPreferredStartGroups(): EventProjectFile =
        copy(
            raceData = raceData.copy(
                competitorData = raceData.competitorData.mapIndexed { index, data ->
                    val competitor = data.competitorCategory.competitor
                    data.copy(
                        competitorCategory = data.competitorCategory.copy(
                            competitor = competitor.copy(preferredStartGroup = index % 3 + 1)
                        )
                    )
                }
            )
        )

    private fun EventProjectFile.startOrder(): List<Int> =
        raceData.competitorData
            .sortedWith(
                compareBy<EventCompetitorData> {
                    it.competitorCategory.competitor.drawnStartTimeSeconds ?: Long.MAX_VALUE
                }.thenBy { it.competitorCategory.competitor.startNumber }
            )
            .map { it.competitorCategory.competitor.startNumber }

    private fun emptyProject(raceType: RaceType): EventProjectFile =
        EventProjectFile(
            raceData = EventRaceData(
                race = EventRace(
                    id = RACE_ID,
                    name = "Generated Event Data",
                    apiKey = "",
                    startDateTimeIso = "2026-06-10T09:00:00",
                    raceType = raceType,
                    raceLevel = RaceLevel.PRACTICE,
                    raceBand = RaceBand.M80,
                    timeLimitSeconds = 7_200
                ),
                categories = emptyList(),
                aliases = emptyList(),
                competitorData = emptyList(),
                unmatchedReadoutData = emptyList(),
                controls = emptyList()
            )
        )

    private fun EventProjectFile.withCategory(name: String): EventProjectFile =
        EventProjectEditor.addCategory(this, "category-${name.lowercase().replace(Regex("[^a-z0-9]+"), "")}", name)

    private fun EventProjectFile.withControls(controls: List<EventControl>): EventProjectFile =
        copy(raceData = raceData.copy(controls = controls))

    private fun EventProjectFile.category(name: String): EventCategoryData =
        raceData.categories.single { it.category.name == name }

    private fun EventProjectFile.assignedSiCodes(categoryName: String): List<Int> {
        val controlsById = raceData.controls.associateBy { it.id }
        return category(categoryName).controlPoints.map { controlPoint ->
            // Resolve through controlId, matching the current event model, so the test catches
            // stale legacy SI-code-only assignment behavior.
            requireNotNull(controlsById[controlPoint.controlId]) {
                "Assigned control ${controlPoint.controlId} is missing from controls."
            }.siCode
        }
    }

    private fun EventProjectFile.assignedControlText(categoryName: String): String =
        assignedSiCodes(categoryName).joinToString(" ") { siCode ->
            val control = raceData.controls.single { it.siCode == siCode }
            if (control.type == ControlPointType.BEACON) "${siCode}B" else siCode.toString()
        }

    private fun classicControls(): List<EventControl> =
        (1..5).map { number ->
            EventControl("control-$number", RACE_ID, number.toString(), 30 + number, ControlPointType.CONTROL, publicLabel = number.toString())
        } + EventControl("control-b", RACE_ID, "B", 99, ControlPointType.BEACON, publicLabel = "B")

    private fun point(name: String, longitude: Double, latitude: Double): String =
        """<Placemark><name>$name</name><Point><coordinates>$longitude,$latitude,0</coordinates></Point></Placemark>"""

    private fun route(name: String, points: List<Pair<Double, Double>>): String =
        """
        <Placemark><name>$name</name><LineString><coordinates>
        ${points.joinToString("\n") { (longitude, latitude) -> "$longitude,$latitude,0" }}
        </coordinates></LineString></Placemark>
        """.trimIndent()

    private fun idFactory(prefix: String): () -> String {
        var next = 0
        return {
            next += 1
            "$prefix-$next"
        }
    }

    private fun assertFails(label: String, block: () -> Unit) {
        val result = runCatching(block)
        assertTrue(label, result.isFailure)
        assertNotNull(label, result.exceptionOrNull())
    }

    private companion object {
        const val RACE_ID = "generated-race"
        const val PASSWORD = "generated-event-data-key"
    }
}
