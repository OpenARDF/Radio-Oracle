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

package org.openardf.radiooracle.shared.files

import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.PunchStatus
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.shared.event.EventAliasPunch
import org.openardf.radiooracle.shared.event.EventCategory
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventCompetitor
import org.openardf.radiooracle.shared.event.EventCompetitorCategory
import org.openardf.radiooracle.shared.event.EventCompetitorData
import org.openardf.radiooracle.shared.event.EventControl
import org.openardf.radiooracle.shared.event.EventControlPoint
import org.openardf.radiooracle.shared.event.EventPunch
import org.openardf.radiooracle.shared.event.EventRace
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.EventReadoutData
import org.openardf.radiooracle.shared.event.EventResult
import kotlin.test.Test
import kotlin.test.assertEquals

class EventCsvExportsTest {
    @Test
    fun exportsPortableCategoryRows() {
        assertEquals(
            "M21;1;99;5000;100;1;;;;2;31,32\n",
            EventCsvExports.categories(raceData())
        )
    }

    @Test
    fun exportedCategoryRowsParseWithSharedImportContract() {
        val result = EventCsvImports.parseAndroidCategoryRows(EventCsvExports.categories(raceData()))

        assertEquals(emptyList(), result.invalidLines)
        val row = result.rows.single()
        assertEquals("M21", row.name)
        assertEquals("31 32", row.controlPointsText)
    }

    @Test
    fun exportedCategoryRowsQuoteNamesForRoundTripImport() {
        val baseRaceData = raceData()
        val raceData = baseRaceData.copy(
            categories = baseRaceData.categories.map { categoryData ->
                categoryData.copy(category = categoryData.category.copy(name = "M;21"))
            }
        )

        val exported = EventCsvExports.categories(raceData)
        val result = EventCsvImports.parseAndroidCategoryRows(exported)

        assertEquals("\"M;21\";1;99;5000;100;1;;;;2;31,32\n", exported)
        assertEquals(emptyList(), result.invalidLines)
        assertEquals("M;21", result.rows.single().name)
        assertEquals("31 32", result.rows.single().controlPointsText)
    }

    @Test
    fun lockedCategoryExportOmitsEncryptedIdealOrderColumn() {
        val raceData = raceData().withEncryptedIdealOrder("ro-ideal-v1:test")

        assertEquals(
            "M21;1;99;5000;100;1;;;;2;31,32\n",
            EventCsvExports.categories(raceData)
        )
    }

    @Test
    fun unlockedCategoryExportIncludesEncryptedIdealOrderColumn() {
        val raceData = raceData().withEncryptedIdealOrder("ro-ideal-v1:test")

        assertEquals(
            "M21;1;99;5000;100;1;;;;2;31,32;ro-ideal-v1:test\n",
            EventCsvExports.categories(raceData, includeEncryptedIdealOrder = true)
        )
    }

    @Test
    fun categoryImportAcceptsEncryptedIdealOrderColumn() {
        val result = EventCsvImports.parseAndroidCategoryRows(
            "M21;1;99;5000;100;1;;;;2;31,32;ro-ideal-v1:test\n"
        )

        assertEquals(emptyList(), result.invalidLines)
        assertEquals("ro-ideal-v1:test", result.rows.single().encryptedIdealOrder)
    }

    @Test
    fun exportsPortableCompetitorRows() {
        assertEquals(
            """
            si_number;start_number;first_name;last_name;category;gender;birth_year;club;index;start_time;si_rent;preferred_start_group;bib_number;call_sign
            123456;7;Test;Runner;M21;0;1985;OK Test;OK001;10:00;0;;;
            """.trimIndent() + "\n",
            EventCsvExports.competitors(raceData())
        )
    }

    @Test
    fun exportedCompetitorRowsPreserveIdentityFieldsForRoundTripImport() {
        val baseRaceData = raceData()
        val raceData = baseRaceData.copy(
            competitorData = baseRaceData.competitorData.map { competitorData ->
                val competitor = competitorData.competitorCategory.competitor
                competitorData.copy(
                    competitorCategory = competitorData.competitorCategory.copy(
                        competitor = competitor.copy(
                            index = "REG001",
                            bibNumber = "1007",
                            callSign = "RUN"
                        )
                    )
                )
            }
        )

        val result = EventCsvImports.parseAndroidCompetitorRows(EventCsvExports.competitors(raceData))

        assertEquals(emptyList(), result.invalidLines)
        val row = result.rows.single()
        assertEquals("REG001", row.index)
        assertEquals("1007", row.bibNumber)
        assertEquals("RUN", row.callSign)
    }

    @Test
    fun exportsControlRows() {
        val raceData = raceData(
            controls = listOf(
                EventControl(
                    id = "control-31",
                    raceId = "race",
                    label = "31",
                    siCode = 31,
                    type = ControlPointType.CONTROL,
                    scored = true,
                    publicLabel = "F1",
                    notes = "first fox"
                )
            )
        )

        assertEquals(
            """
            si_code;role;fox;public_label;notes
            31;Fox;1;F1;first fox
            """.trimIndent() + "\n",
            EventCsvExports.controls(raceData)
        )
    }

    @Test
    fun exportedControlRowsParseWithSharedImportContract() {
        val raceData = raceData(
            controls = listOf(
                EventControl(
                    id = "control-31",
                    raceId = "race",
                    label = "31",
                    siCode = 31,
                    type = ControlPointType.CONTROL,
                    scored = true,
                    publicLabel = "F1",
                    notes = "first fox"
                ),
                EventControl(
                    id = "control-99",
                    raceId = "race",
                    label = "99B",
                    siCode = 99,
                    type = ControlPointType.BEACON,
                    scored = false,
                    publicLabel = "M",
                    notes = "finish beacon"
                )
            )
        )

        val result = EventCsvImports.parseControlRows(EventCsvExports.controls(raceData))

        assertEquals(emptyList(), result.invalidLines)
        assertEquals(2, result.rows.size)
        assertEquals(31, result.rows[0].siCode)
        assertEquals(ControlPointType.CONTROL, result.rows[0].type)
        assertEquals(true, result.rows[0].scored)
        assertEquals(99, result.rows[1].siCode)
        assertEquals(ControlPointType.BEACON, result.rows[1].type)
        assertEquals(false, result.rows[1].scored)
    }

    @Test
    fun exportedCompetitorRowsParseWithSharedImportContract() {
        val result = EventCsvImports.parseAndroidCompetitorRows(EventCsvExports.competitors(raceData()))

        assertEquals(emptyList(), result.invalidLines)
        val row = result.rows.single()
        assertEquals(123456, row.siNumber)
        assertEquals(7, row.startNumber)
        assertEquals("Test", row.firstName)
        assertEquals("Runner", row.lastName)
        assertEquals("M21", row.categoryName)
        assertEquals("OK Test", row.club)
        assertEquals("OK001", row.index)
        assertEquals("", row.bibNumber)
        assertEquals("", row.callSign)
        assertEquals("10:00", row.startTimeText)
    }

    @Test
    fun exportsPortableCompetitorStartRows() {
        assertEquals(
            "7;Runner;Test;M21;;10:00;OK001;;OK Test;123456\n",
            EventCsvExports.competitorStarts(raceData())
        )
    }

    @Test
    fun exportedCompetitorStartRowsParseWithSharedImportContract() {
        val result = EventCsvImports.parseAndroidCompetitorStartRows(EventCsvExports.competitorStarts(raceData()))

        assertEquals(emptyList(), result.invalidLines)
        assertEquals(
            listOf(CompetitorStartCsvImportRow(startNumber = 7, startTimeText = "10:00", siNumber = 123456)),
            result.rows
        )
    }

    @Test
    fun exportedCompetitorStartRowsPreserveBibNumberSeparatelyFromIndex() {
        val baseRaceData = raceData()
        val raceData = baseRaceData.copy(
            competitorData = baseRaceData.competitorData.map { competitorData ->
                val competitor = competitorData.competitorCategory.competitor
                competitorData.copy(
                    competitorCategory = competitorData.competitorCategory.copy(
                        competitor = competitor.copy(index = "REG001", bibNumber = "1007")
                    )
                )
            }
        )

        val result = EventCsvImports.parseAndroidCompetitorStartRows(EventCsvExports.competitorStarts(raceData))

        assertEquals(emptyList(), result.invalidLines)
        assertEquals(
            listOf(CompetitorStartCsvImportRow(startNumber = 7, startTimeText = "10:00", siNumber = 123456, bibNumber = "1007")),
            result.rows
        )
    }

    @Test
    fun exportedCompetitorStartVariantsParseWithSharedImportContract() {
        val raceData = startVariantRaceData()
        val exports = listOf(
            EventCsvExports.competitorStarts(raceData),
            EventCsvExports.competitorStartsByCategory(raceData),
            EventCsvExports.competitorStartsByMinute(raceData)
        )

        exports.forEach { csv ->
            val result = EventCsvImports.parseAndroidCompetitorStartRows(csv)

            assertEquals(emptyList(), result.invalidLines)
            assertEquals(4, result.rows.size)
            assertEquals(setOf(111111, 222222, 333333, 444444), result.rows.mapNotNull { it.siNumber }.toSet())
        }
    }

    @Test
    fun exportsCompetitorStartRowsByCategory() {
        assertEquals(
            """
            3;Gamma;Carol;M21;;12:00;OK003;;OK Test;333333
            4;NoTime;Dave;M21;;;OK004;;OK Test;444444
            2;Beta;Bob;W21;;11:00;OK002;;OK Test;222222
            1;Alpha;Alice;W21;;13:00;OK001;;OK Test;111111
            """.trimIndent() + "\n",
            EventCsvExports.competitorStartsByCategory(startVariantRaceData())
        )
    }

    @Test
    fun exportsCompetitorStartRowsByMinute() {
        assertEquals(
            """
            2;Beta;Bob;W21;;11:00;OK002;;OK Test;222222
            3;Gamma;Carol;M21;;12:00;OK003;;OK Test;333333
            1;Alpha;Alice;W21;;13:00;OK001;;OK Test;111111
            4;NoTime;Dave;M21;;;OK004;;OK Test;444444
            """.trimIndent() + "\n",
            EventCsvExports.competitorStartsByMinute(startVariantRaceData())
        )
    }

    @Test
    fun exportsRobisStartListRows() {
        assertEquals(
            """
            "";Gamma;Carol;M21;"";00:12:00;OK003;"";"CZE";333333
            "";NoTime;Dave;M21;"";;OK004;"";"CZE";444444
            "";Beta;Bob;W21;"";00:11:00;OK002;"";"CZE";222222
            "";Alpha;Alice;W21;"";00:13:00;OK001;"";"CZE";111111
            """.trimIndent() + "\n",
            EventCsvExports.robisStartList(startVariantRaceData())
        )
    }

    @Test
    fun exportsPortableReadoutRows() {
        assertEquals(
            "123456;00:01:40;00:10:00;00:20:00;2;31;00:12:00;32;00:15:00\n",
            EventCsvExports.readouts(raceData())
        )
    }

    @Test
    fun exportsPortableResultRows() {
        assertEquals(
            "1;RUNNER Test;OK;2;00:10:00\n",
            EventCsvExports.results(raceData())
        )
    }

    @Test
    fun exportsArdfEventStyleResultRows() {
        assertEquals(
            """
            Kategorie;Pořadí;Jméno;Index;Čas;TX;Status;Kontroly
            M21;1;RUNNER Test;OK001;00:10:00;2;OK;31 32
            """.trimIndent() + "\n",
            EventCsvExports.ardfEventResults(raceData())
        )
    }

    private fun raceData(controls: List<EventControl> = emptyList()): EventRaceData {
        val race = EventRace(
            id = "race",
            name = "CSV Race",
            apiKey = "",
            startDateTimeIso = "2026-06-01T10:00",
            raceType = RaceType.CLASSIC,
            raceLevel = RaceLevel.PRACTICE,
            raceBand = RaceBand.M80,
            timeLimitSeconds = 7_200
        )
        val category = EventCategory(
            id = "category",
            raceId = race.id,
            name = "M21",
            isMan = true,
            maxAge = null,
            lengthMeters = 5_000,
            climbMeters = 100,
            order = 1,
            differentProperties = false,
            raceType = null,
            raceBand = null,
            timeLimitSeconds = null,
            controlPointsString = "31 32"
        )
        val competitor = EventCompetitor(
            id = "competitor",
            raceId = race.id,
            categoryId = category.id,
            firstName = "Test",
            lastName = "Runner",
            club = "OK Test",
            index = "OK001",
            isMan = true,
            birthYear = 1985,
            siNumber = 123456,
            siRent = false,
            startNumber = 7,
            drawnStartTimeSeconds = 600
        )
        val readout = EventReadoutData(
            result = EventResult(
                id = "result",
                raceId = race.id,
                competitorId = competitor.id,
                siNumber = 123456,
                cardType = 0,
                checkTimeSeconds = 100,
                startTimeSeconds = 600,
                finishTimeSeconds = 1_200,
                readoutDateTimeIso = "2026-06-01T10:20",
                automaticStatus = true,
                resultStatus = ResultStatus.OK,
                points = 2,
                runTimeSeconds = 600,
                modified = false,
                sent = false,
                place = 1
            ),
            punches = listOf(
                punch("punch-1", race.id, "result", 31, 720, 1),
                punch("punch-2", race.id, "result", 32, 900, 2)
            )
        )

        return EventRaceData(
            race = race,
            categories = listOf(
                EventCategoryData(
                    category = category,
                    controlPoints = listOf(
                        EventControlPoint("control-1", category.id, 31, ControlPointType.CONTROL, 1),
                        EventControlPoint("control-2", category.id, 32, ControlPointType.CONTROL, 2)
                    ),
                    competitors = listOf(competitor)
                )
            ),
            aliases = emptyList(),
            competitorData = listOf(
                EventCompetitorData(
                    competitorCategory = EventCompetitorCategory(competitor, category),
                    readoutData = readout
                )
            ),
            unmatchedReadoutData = emptyList(),
            controls = controls
        )
    }

    private fun EventRaceData.withEncryptedIdealOrder(value: String): EventRaceData =
        copy(
            categories = categories.mapIndexed { index, categoryData ->
                if (index == 0) {
                    categoryData.copy(category = categoryData.category.copy(encryptedIdealOrder = value))
                } else {
                    categoryData
                }
            }
        )

    private fun startVariantRaceData(): EventRaceData {
        val race = EventRace(
            id = "race",
            name = "CSV Race",
            apiKey = "",
            startDateTimeIso = "2026-06-01T10:00",
            raceType = RaceType.CLASSIC,
            raceLevel = RaceLevel.PRACTICE,
            raceBand = RaceBand.M80,
            timeLimitSeconds = 7_200
        )
        val m21 = EventCategory(
            id = "m21",
            raceId = race.id,
            name = "M21",
            isMan = true,
            maxAge = null,
            lengthMeters = 5_000,
            climbMeters = 100,
            order = 1,
            differentProperties = false,
            raceType = null,
            raceBand = null,
            timeLimitSeconds = null,
            controlPointsString = ""
        )
        val w21 = m21.copy(id = "w21", name = "W21", isMan = false)
        val competitors = listOf(
            variantCompetitor(race.id, w21.id, "Alice", "Alpha", "OK001", 111111, 1, 780),
            variantCompetitor(race.id, w21.id, "Bob", "Beta", "OK002", 222222, 2, 660),
            variantCompetitor(race.id, m21.id, "Carol", "Gamma", "OK003", 333333, 3, 720),
            variantCompetitor(race.id, m21.id, "Dave", "NoTime", "OK004", 444444, 4, null)
        )
        return EventRaceData(
            race = race,
            categories = listOf(
                EventCategoryData(m21, controlPoints = emptyList(), competitors = competitors.filter { it.categoryId == m21.id }),
                EventCategoryData(w21, controlPoints = emptyList(), competitors = competitors.filter { it.categoryId == w21.id })
            ),
            aliases = emptyList(),
            competitorData = competitors.map { competitor ->
                EventCompetitorData(
                    competitorCategory = EventCompetitorCategory(
                        competitor = competitor,
                        category = if (competitor.categoryId == m21.id) m21 else w21
                    ),
                    readoutData = null
                )
            },
            unmatchedReadoutData = emptyList()
        )
    }

    private fun variantCompetitor(
        raceId: String,
        categoryId: String,
        firstName: String,
        lastName: String,
        index: String,
        siNumber: Int,
        startNumber: Int,
        drawnStartTimeSeconds: Long?
    ): EventCompetitor =
        EventCompetitor(
            id = index,
            raceId = raceId,
            categoryId = categoryId,
            firstName = firstName,
            lastName = lastName,
            club = "OK Test",
            index = index,
            isMan = categoryId == "m21",
            birthYear = 1985,
            siNumber = siNumber,
            siRent = false,
            startNumber = startNumber,
            drawnStartTimeSeconds = drawnStartTimeSeconds
        )

    private fun punch(
        id: String,
        raceId: String,
        resultId: String,
        siCode: Int,
        siTimeSeconds: Long,
        order: Int
    ): EventAliasPunch =
        EventAliasPunch(
            punch = EventPunch(
                id = id,
                raceId = raceId,
                resultId = resultId,
                cardNumber = 123456,
                siCode = siCode,
                siTimeSeconds = siTimeSeconds,
                originalSiTimeSeconds = siTimeSeconds,
                punchType = SIRecordType.CONTROL,
                order = order,
                punchStatus = PunchStatus.UNKNOWN,
                splitSeconds = 0
            ),
            alias = null
        )
}
