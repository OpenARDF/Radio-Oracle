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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.serialization.SerializationException
import org.openardf.radiooracle.shared.event.EventProjectFileFormat
import org.openardf.radiooracle.shared.event.EventProjectFileJson
import org.openardf.radiooracle.shared.event.EventSeriesLink
import org.openardf.radiooracle.shared.event.PublicResultsPublication

class RaceBackupJsonImportsTest {
    @Test
    fun importsModernAndroidBackupWithoutReplacingIdsOrDroppingMetadata() {
        var nextId = 0
        val project = RaceBackupJsonImports.projectFile(androidRaceBackupJson()) { "id-${nextId++}" }.copy(
            seriesLink = EventSeriesLink("series-id", "member-id"),
            publicResultsPublication = PublicResultsPublication(
                url = "https://example.org/results/",
                publishedAtIso = "2026-09-05T18:00:00Z"
            )
        )
        val text = EventProjectFileJson.encode(project)

        val imported = RaceBackupJsonImports.projectFile(text) { error("Modern IDs must be retained") }

        assertEquals(EventProjectFileJson.decode(text), imported)
        assertEquals(project.raceData.race.id, imported.raceData.race.id)
        assertEquals(project.seriesLink, imported.seriesLink)
        assertEquals(project.publicResultsPublication, imported.publicResultsPublication)
    }

    @Test
    fun rejectsFutureModernSchemaWithoutFallingBackToLegacyImport() {
        var nextId = 0
        val project = RaceBackupJsonImports.projectFile(androidRaceBackupJson()) { "id-${nextId++}" }
        val futureVersion = EventProjectFileFormat.CURRENT_SCHEMA_VERSION + 1
        val text = EventProjectFileJson.encode(project).replace(
            "\"schemaVersion\": ${EventProjectFileFormat.CURRENT_SCHEMA_VERSION}",
            "\"schemaVersion\": $futureVersion"
        )

        val error = assertFailsWith<IllegalArgumentException> {
            RaceBackupJsonImports.projectFile(text) { error("Must not invoke the legacy importer") }
        }

        assertTrue(error.message.orEmpty().contains("Unsupported Radio-Oracle Race File schema version"))
    }

    @Test
    fun rejectsIncompleteModernEnvelopeEvenWhenLegacyRaceNameIsPresent() {
        assertFailsWith<SerializationException> {
            RaceBackupJsonImports.projectFile(
                """{"appName":"Radio-Oracle","race_name":"Incomplete modern file"}"""
            ) { error("Must not invoke the legacy importer") }
        }
    }

    @Test
    fun importsAndroidRaceBackupIntoSharedProjectFile() {
        var nextId = 0
        val projectFile = RaceBackupJsonImports.projectFile(androidRaceBackupJson()) {
            "id-${nextId++}"
        }
        val raceData = projectFile.raceData
        val category = raceData.categories.single()
        val competitorData = raceData.competitorData.single()
        val competitor = competitorData.competitorCategory.competitor
        val result = competitorData.readoutData!!.result
        val punch = competitorData.readoutData!!.punches.single().punch
        val unmatched = raceData.unmatchedReadoutData.single()

        assertEquals("Imported Race", raceData.race.name)
        assertEquals("2026-06-01T09:00:00", raceData.race.startDateTimeIso)
        assertEquals(RaceType.CLASSIC, raceData.race.raceType)
        assertEquals(RaceBand.M80, raceData.race.raceBand)
        assertEquals(RaceLevel.DISTRICT, raceData.race.raceLevel)
        assertEquals(6_000, raceData.race.timeLimitSeconds)

        assertEquals("M21", category.category.name)
        assertEquals(true, category.category.isMan)
        assertEquals(false, category.category.differentProperties)
        assertEquals(null, category.category.raceType)
        assertEquals(null, category.category.raceBand)
        assertEquals(null, category.category.timeLimitSeconds)
        assertEquals("31 90B", category.category.controlPointsString)
        assertEquals(ControlPointType.BEACON, category.controlPoints.last().type)
        assertEquals(1, category.competitors.size)

        assertEquals("Alice", competitor.firstName)
        assertEquals("Runner", competitor.lastName)
        assertEquals(1, competitor.startNumber)
        assertEquals(1, category.competitors.single().startNumber)
        assertEquals(600, competitor.drawnStartTimeSeconds)
        assertEquals("M21", competitorData.competitorCategory.category?.name)

        assertEquals(ResultStatus.MISPUNCHED, result.resultStatus)
        assertEquals(36_000, result.startTimeSeconds)
        assertEquals(37_200, result.finishTimeSeconds)
        assertEquals(1_200, result.runTimeSeconds)
        assertEquals(PunchStatus.UNKNOWN, punch.punchStatus)
        assertEquals(SIRecordType.CONTROL, punch.punchType)
        assertEquals(36_300, punch.siTimeSeconds)

        assertEquals(654321, unmatched.result.siNumber)
        assertEquals(ResultStatus.NO_RANKING, unmatched.result.resultStatus)
        assertEquals(SIRecordType.FINISH, unmatched.punches.single().punch.punchType)
    }

    @Test
    fun importsLegacyBlankAndroidResultStatusAsOk() {
        var nextId = 0
        val projectFile = RaceBackupJsonImports.projectFile(
            androidRaceBackupJson().replace("\"result_status\": \"MP\"", "\"result_status\": \"\"")
        ) {
            "id-${nextId++}"
        }

        val result = projectFile.raceData.competitorData.single().readoutData!!.result
        val unmatched = projectFile.raceData.unmatchedReadoutData.single().result

        assertEquals(ResultStatus.OK, result.resultStatus)
        assertEquals(ResultStatus.NO_RANKING, unmatched.resultStatus)
    }

    private fun androidRaceBackupJson(): String =
        """
        {
          "race_name": "Imported Race",
          "race_start": "2026-06-01T09:00:00",
          "race_type": "CLASSIC",
          "race_band": "M80",
          "race_level": "DISTRICT",
          "race_time_limit": 100,
          "race_api_key": "api-key",
          "categories": [
            {
              "category_name": "M21",
              "category_gender": false,
              "category_max_age": 99,
              "category_length": 4500,
              "category_climb": 180,
              "category_control_points": [
                { "si_code": 31, "control_type": "CONTROL" },
                { "si_code": 90, "control_type": "BEACON" }
              ],
              "category_different_properties": true,
              "category_race_band": "M2",
              "category_time_limit": "90:00"
            }
          ],
          "aliases": [
            { "alias_si_code": 31, "alias_name": "Fox" }
          ],
          "competitors": [
            {
              "first_name": "Alice",
              "last_name": "Runner",
              "competitor_club": "OC",
              "competitor_category": "M21",
              "competitor_index": "IDX1",
              "competitor_gender": false,
              "birth_year": 1980,
              "si_number": 123456,
              "si_rent": false,
              "start_number": null,
              "competitor_start_time": "10:00",
              "result": {
                "check_time": "2026-06-01T09:55:00",
                "start_time": "2026-06-01T10:00:00",
                "finish_time": "2026-06-01T10:20:00",
                "run_time": "20:00",
                "place": 2,
                "readoutTime": "2026-06-01T10:21:00",
                "modified": false,
                "punch_count": 1,
                "result_status": "MP",
                "automatic_status": true,
                "punches": [
                  {
                    "code": "Fox",
                    "si_code": 31,
                    "control_type": "CONTROL",
                    "punch_status": "AP",
                    "split_time": "05:00"
                  }
                ]
              }
            }
          ],
          "unmatched_results": [
            {
              "si_number": 654321,
              "start_time": "2026-06-01T10:00:00",
              "finish_time": "2026-06-01T10:25:00",
              "run_time": "25:00",
              "punches": [
                {
                  "code": "0",
                  "si_code": 0,
                  "control_type": "FINISH",
                  "punch_status": "OK",
                  "split_time": "25:00"
                }
              ]
            }
          ]
        }
        """.trimIndent()
}
