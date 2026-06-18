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

class RaceBackupJsonImportsTest {
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
        assertEquals(RaceBand.M2, category.category.raceBand)
        assertEquals(5_400, category.category.timeLimitSeconds)
        assertEquals("31 90B", category.category.controlPointsString)
        assertEquals(ControlPointType.BEACON, category.controlPoints.last().type)
        assertEquals(1, category.competitors.size)

        assertEquals("Alice", competitor.firstName)
        assertEquals("Runner", competitor.lastName)
        assertEquals(1, competitor.startNumber)
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
