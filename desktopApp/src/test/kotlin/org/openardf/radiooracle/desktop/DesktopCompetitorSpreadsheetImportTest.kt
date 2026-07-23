/*
 * MIT License
 *
 * Copyright (c) 2025 Pavel Kolský
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.openardf.radiooracle.desktop

import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.EventProjectFactory

class DesktopCompetitorSpreadsheetImportTest {
    @Test
    fun readsNamedWorksheetAndAppliesRememberedColumnMapping() {
        val path = Files.createTempFile("radio-oracle-competitors-", ".xlsx")
        Files.write(path, sampleWorkbookBytes())
        val mapping = mapping(
            competitions = listOf(
                DesktopSpreadsheetCompetitionColumnMapping(
                    competitionName = "Sprint",
                    categoryColumn = ref("Sprint Category"),
                    courseColumn = ref("Sprint Course"),
                    startTimeColumn = ref("Sprint Start")
                )
            )
        )
        val profile = DesktopCompetitorSpreadsheetImportProfile(
            sheetName = "Registrations",
            headerRowIndex = 1,
            mapping = mapping
        )

        val draft = DesktopCompetitorSpreadsheetImportDraft.load(path, profile)

        assertEquals(listOf("Summary", "Registrations"), draft.worksheets.map { it.name })
        assertEquals("Registrations", draft.selectedSheetName)
        assertEquals(1, draft.headerRowIndex)
        assertTrue(draft.validationErrors.toString(), draft.canImport)

        val registration = draft.toRegistrationImport().registration
        assertEquals("Sprint", registration.competitions.single().name)
        val competitor = registration.competitions.single().competitors.single()
        assertEquals("Gheorghe", competitor.firstName)
        assertEquals("Fala", competitor.lastName)
        assertEquals("BOK", competitor.club)
        assertEquals(8400555, competitor.siNumber)
        assertEquals(31, competitor.startNumber)
        assertEquals("101", competitor.bibNumber)
        assertEquals("K4FAL", competitor.callSign)
        assertEquals(1991, competitor.birthYear)
        assertEquals("person-1", competitor.personId)
        assertEquals(true, competitor.isMan)
        assertTrue(competitor.siRent)
        assertEquals("fala@example.test", competitor.email)
        assertEquals("555-0101", competitor.cellPhone)
        assertEquals(true, competitor.usaChampEligible)
        assertEquals(false, competitor.region2ChampEligible)
        assertEquals("M-21", competitor.categoryName)
        assertEquals("Sprint A", competitor.courseName)
        assertEquals("05:00", competitor.startTimeText)
    }

    @Test
    fun rememberedHeadingMappingSurvivesColumnReordering() {
        val originalHeaders = allHeaders()
        val reorderedHeaders = originalHeaders.reversed()
        val mapping = mapping(
            competitions = listOf(
                DesktopSpreadsheetCompetitionColumnMapping(
                    competitionName = "Sprint",
                    categoryColumn = ref("Sprint Category"),
                    courseColumn = ref("Sprint Course"),
                    startTimeColumn = ref("Sprint Start")
                )
            )
        )
        val valuesByHeader = originalHeaders.zip(competitorValues()).toMap()
        val reorderedValues = reorderedHeaders.map { valuesByHeader.getValue(it) }

        val registration = DesktopEventRegSpreadsheetParser.parseMappedRows(
            rows = listOf(reorderedHeaders, reorderedValues),
            headerRowIndex = 0,
            eventName = "Reordered",
            mapping = mapping
        )

        val competitor = registration.competitions.single().competitors.single()
        assertEquals("Gheorghe", competitor.firstName)
        assertEquals("Fala", competitor.lastName)
        assertEquals(8400555, competitor.siNumber)
        assertEquals("M-21", competitor.categoryName)
    }

    @Test
    fun extractsBirthYearFromDisplayedDatesAndExcelDateSerials() {
        val excelBirthDate = LocalDate.of(1967, 8, 19)
        val excelSerial = ChronoUnit.DAYS.between(
            LocalDate.of(1899, 12, 30),
            excelBirthDate
        )
        val rows = listOf(
            listOf("Given", "Family", "Born", "Sprint Category"),
            listOf("ISO", "Date", "1991-01-12", "M-21"),
            listOf("US", "Date", "04/15/1978", "M-40"),
            listOf("Written", "Date", "July 4, 1959", "M-60"),
            listOf("Excel", "Date", "$excelSerial.75", "M-50"),
            listOf("Plain", "Year", "2001", "M-21")
        )
        val mapping = DesktopSpreadsheetCompetitorColumnMapping(
            competitorColumns = mapOf(
                DesktopSpreadsheetCompetitorField.FIRST_NAME to ref("Given"),
                DesktopSpreadsheetCompetitorField.LAST_NAME to ref("Family"),
                DesktopSpreadsheetCompetitorField.BIRTH_YEAR to ref("Born")
            ),
            competitions = listOf(
                DesktopSpreadsheetCompetitionColumnMapping(
                    competitionName = "Sprint",
                    categoryColumn = ref("Sprint Category")
                )
            )
        )

        val competitors = DesktopEventRegSpreadsheetParser.parseMappedRows(
            rows = rows,
            headerRowIndex = 0,
            eventName = "Birth Dates",
            mapping = mapping
        ).competitions.single().competitors

        assertEquals(listOf(1991, 1978, 1959, 1967, 2001), competitors.map { it.birthYear })
    }

    @Test
    fun mappedRegistrationUsesExistingRacePlanAndReviewEngine() {
        val rows = listOf(
            listOf("Given", "Family", "Sprint Group", "Fox Group", "Chip"),
            listOf("Gheorghe", "Fala", "M-21", "NC", "8400555"),
            listOf("Gerald", "Boyd", "NC", "M-60", "247347")
        )
        val mapping = DesktopSpreadsheetCompetitorColumnMapping(
            competitorColumns = mapOf(
                DesktopSpreadsheetCompetitorField.FIRST_NAME to ref("Given"),
                DesktopSpreadsheetCompetitorField.LAST_NAME to ref("Family"),
                DesktopSpreadsheetCompetitorField.SI_NUMBER to ref("Chip")
            ),
            competitions = listOf(
                DesktopSpreadsheetCompetitionColumnMapping(
                    competitionName = "Sprint",
                    categoryColumn = ref("Sprint Group")
                ),
                DesktopSpreadsheetCompetitionColumnMapping(
                    competitionName = "FoxO",
                    categoryColumn = ref("Fox Group")
                )
            )
        )
        val registrationImport = DesktopSpreadsheetRegistrationImport(
            sourceUrl = "/tmp/competitors.xlsx",
            registration = DesktopEventRegSpreadsheetParser.parseMappedRows(
                rows = rows,
                headerRowIndex = 0,
                eventName = "Mapped Registration",
                mapping = mapping
            )
        )
        val project = EventProjectFactory.createEmptyProject(
            raceId = "sprint-race",
            raceName = "Sprint",
            startDateTimeIso = "2026-07-23T09:00"
        ).let { original ->
            original.copy(
                raceData = original.raceData.copy(
                    race = original.raceData.race.copy(
                        raceType = RaceType.SPRINT,
                        raceBand = RaceBand.NONE
                    )
                )
            )
        }
        val target = DesktopSpreadsheetCompetitorImportTarget(
            targetId = "sprint-race",
            displayName = "Sprint",
            path = null,
            projectFile = project
        )

        val plan = DesktopSpreadsheetCompetitorImporter.buildPlan(
            registrationImport = registrationImport,
            targets = listOf(target)
        )

        assertEquals(listOf("Sprint", "FoxO"), plan.mappings.map { it.competitionName })
        assertEquals(listOf("Fala"), plan.mappings.first().rows.map { it.lastName })
        assertTrue(plan.mappings.first().selectedByDefault)
        assertFalse(plan.mappings.last().selectedByDefault)
    }

    @Test
    fun profileCodecPreservesEveryMapping() {
        val profile = DesktopCompetitorSpreadsheetImportProfile(
            sheetName = "Registrations",
            headerRowIndex = 3,
            mapping = mapping(
                competitions = listOf(
                    DesktopSpreadsheetCompetitionColumnMapping(
                        competitionName = "Sprint",
                        categoryColumn = ref("Sprint Category", occurrence = 1),
                        courseColumn = ref("Sprint Course"),
                        startTimeColumn = ref("Sprint Start")
                    )
                )
            )
        )

        val decoded = DesktopCompetitorSpreadsheetProfileCodec.decode(
            DesktopCompetitorSpreadsheetProfileCodec.encode(profile)
        )

        assertEquals(profile, decoded)
    }

    @Test
    fun missingRememberedHeadingMustBeCorrectedBeforeImport() {
        val profile = DesktopCompetitorSpreadsheetImportProfile(
            sheetName = "Registrations",
            headerRowIndex = 0,
            mapping = DesktopSpreadsheetCompetitorColumnMapping(
                competitorColumns = mapOf(
                    DesktopSpreadsheetCompetitorField.FIRST_NAME to ref("Given"),
                    DesktopSpreadsheetCompetitorField.LAST_NAME to ref("Missing Family")
                ),
                competitions = listOf(
                    DesktopSpreadsheetCompetitionColumnMapping(
                        competitionName = "Sprint",
                        categoryColumn = ref("Class")
                    )
                )
            )
        )
        val draft = DesktopCompetitorSpreadsheetImportDraft(
            path = java.nio.file.Path.of("/tmp/competitors.xlsx"),
            worksheets = listOf(
                DesktopXlsxWorksheet(
                    "Registrations",
                    listOf(
                        listOf("Given", "Family", "Class"),
                        listOf("Gheorghe", "Fala", "M-21")
                    )
                )
            ),
            selectedSheetName = "Registrations",
            headerRowIndex = 0,
            mapping = profile.mapping,
            rememberedProfile = profile
        )

        assertFalse(draft.canImport)
        assertTrue(draft.validationErrors.any { it.contains("Missing Family") })
    }

    private fun mapping(
        competitions: List<DesktopSpreadsheetCompetitionColumnMapping>
    ): DesktopSpreadsheetCompetitorColumnMapping =
        DesktopSpreadsheetCompetitorColumnMapping(
            competitorColumns = mapOf(
                DesktopSpreadsheetCompetitorField.FIRST_NAME to ref("Given"),
                DesktopSpreadsheetCompetitorField.LAST_NAME to ref("Family"),
                DesktopSpreadsheetCompetitorField.CLUB to ref("Organization"),
                DesktopSpreadsheetCompetitorField.SI_NUMBER to ref("Chip"),
                DesktopSpreadsheetCompetitorField.START_NUMBER to ref("Start Number"),
                DesktopSpreadsheetCompetitorField.BIB_NUMBER to ref("Bib"),
                DesktopSpreadsheetCompetitorField.CALL_SIGN to ref("Radio Call"),
                DesktopSpreadsheetCompetitorField.BIRTH_YEAR to ref("Born"),
                DesktopSpreadsheetCompetitorField.PERSON_ID to ref("Identity"),
                DesktopSpreadsheetCompetitorField.SEX to ref("Gender"),
                DesktopSpreadsheetCompetitorField.SI_RENT to ref("Rental"),
                DesktopSpreadsheetCompetitorField.EMAIL to ref("Mail"),
                DesktopSpreadsheetCompetitorField.CELL_PHONE to ref("Mobile"),
                DesktopSpreadsheetCompetitorField.USA_CHAMP_ELIGIBLE to ref("US Eligible"),
                DesktopSpreadsheetCompetitorField.REGION2_CHAMP_ELIGIBLE to ref("R2 Eligible")
            ),
            competitions = competitions
        )

    private fun ref(heading: String, occurrence: Int = 0): DesktopSpreadsheetColumnRef =
        DesktopSpreadsheetColumnRef(heading, occurrence)

    private fun allHeaders(): List<String> =
        listOf(
            "Given",
            "Family",
            "Organization",
            "Chip",
            "Start Number",
            "Bib",
            "Radio Call",
            "Born",
            "Identity",
            "Gender",
            "Rental",
            "Mail",
            "Mobile",
            "US Eligible",
            "R2 Eligible",
            "Sprint Category",
            "Sprint Course",
            "Sprint Start"
        )

    private fun competitorValues(): List<String> =
        listOf(
            "Gheorghe",
            "Fala",
            "BOK",
            "8400555",
            "31",
            "101",
            "K4FAL",
            "1991-01-01",
            "person-1",
            "M",
            "Y",
            "fala@example.test",
            "555-0101",
            "Y",
            "N",
            "M-21",
            "Sprint A",
            "05:00"
        )

    private fun sampleWorkbookBytes(): ByteArray {
        val output = ByteArrayOutputStream()
        val workbookCompetitorValues = competitorValues().toMutableList().apply {
            this[allHeaders().indexOf("Born")] = ChronoUnit.DAYS.between(
                LocalDate.of(1899, 12, 30),
                LocalDate.of(1991, 1, 1)
            ).toString()
        }
        ZipOutputStream(output).use { zip ->
            zip.writeEntry(
                "xl/workbook.xml",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                    xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets>
                    <sheet name="Summary" sheetId="1" r:id="rId1"/>
                    <sheet name="Registrations" sheetId="2" r:id="rId2"/>
                  </sheets>
                </workbook>
                """.trimIndent()
            )
            zip.writeEntry(
                "xl/_rels/workbook.xml.rels",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="worksheet" Target="worksheets/sheet1.xml"/>
                  <Relationship Id="rId2" Type="worksheet" Target="worksheets/sheet2.xml"/>
                </Relationships>
                """.trimIndent()
            )
            zip.writeEntry(
                "xl/worksheets/sheet1.xml",
                worksheetXml(
                    listOf(
                        listOf("Report", "Count"),
                        listOf("Registrations", "1")
                    )
                )
            )
            zip.writeEntry(
                "xl/worksheets/sheet2.xml",
                worksheetXml(
                    listOf(
                        listOf("Radio Championships Registration"),
                        allHeaders(),
                        workbookCompetitorValues
                    ),
                    numericCells = setOf(2 to allHeaders().indexOf("Born"))
                )
            )
        }
        return output.toByteArray()
    }

    private fun worksheetXml(
        rows: List<List<String>>,
        numericCells: Set<Pair<Int, Int>> = emptySet()
    ): String =
        buildString {
            append(
                """<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>"""
            )
            rows.forEachIndexed { rowIndex, row ->
                append("""<row r="${rowIndex + 1}">""")
                row.forEachIndexed { columnIndex, value ->
                    val reference = "${columnLetters(columnIndex)}${rowIndex + 1}"
                    if (rowIndex to columnIndex in numericCells) {
                        append("""<c r="$reference"><v>$value</v></c>""")
                    } else {
                        append(
                            """<c r="$reference" t="inlineStr"><is><t>${xmlEscape(value)}</t></is></c>"""
                        )
                    }
                }
                append("</row>")
            }
            append("</sheetData></worksheet>")
        }

    private fun ZipOutputStream.writeEntry(name: String, contents: String) {
        putNextEntry(ZipEntry(name))
        write(contents.toByteArray())
        closeEntry()
    }

    private fun columnLetters(index: Int): String {
        var number = index + 1
        val result = StringBuilder()
        while (number > 0) {
            result.append(('A'.code + ((number - 1) % 26)).toChar())
            number = (number - 1) / 26
        }
        return result.reverse().toString()
    }

    private fun xmlEscape(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
