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

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.files.json.adapters.PunchJsonAdapter
import org.openardf.radiooracle.backend.files.json.temps.UnmatchedResultJson
import org.openardf.radiooracle.backend.helpers.TimeProcessor
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.room.entity.Result
import org.openardf.radiooracle.backend.room.entity.embeddeds.AliasPunch
import org.openardf.radiooracle.backend.room.entity.embeddeds.ReadoutData
import org.openardf.radiooracle.backend.room.enums.ResultStatus
import org.openardf.radiooracle.backend.sportident.SITime
import java.util.UUID

/** Moshi adapter for readouts that are not matched to a competitor. */
class UnmatchedResultJsonAdapter(val race: Race, val dataProcessor: DataProcessor) {
    val punchJsonAdapter = PunchJsonAdapter(race.id, dataProcessor)

    /** Serializes an unmatched readout with card number, timing, and split punches. */
    @ToJson
    fun toJson(readoutData: ReadoutData): UnmatchedResultJson {
        val result = readoutData.result

        return UnmatchedResultJson(
            check_time = result.checkTime?.toLocalDateTime(race.startDateTime),
            start_time = result.startTime!!.toLocalDateTime(race.startDateTime),
            finish_time = result.finishTime!!.toLocalDateTime(race.startDateTime),
            si_number = result.siNumber,
            run_time = TimeProcessor.durationToFormattedString(result.runTime, true),
            punches = readoutData.punches.map { ap -> punchJsonAdapter.toJson(ap) },
        )
    }

    /** Deserializes an unmatched readout and reconstructs absolute punch times from split durations. */
    @FromJson
    fun fromJson(json: UnmatchedResultJson): ReadoutData {
        val result = Result(
            id = UUID.randomUUID(),
            raceId = race.id,
            siNumber = json.si_number,
            cardType = 0,
            checkTime = json.check_time?.let { SITime(json.check_time, race.startDateTime) },
            startTime = SITime(json.start_time, race.startDateTime),
            finishTime = SITime(json.finish_time, race.startDateTime),
            automaticStatus = false,
            resultStatus = ResultStatus.NO_RANKING,
            runTime = TimeProcessor.minuteStringToDuration(json.run_time),
            modified = false,
            sent = false
        )

        val punches = ArrayList<AliasPunch>()
        val punchJsonAdapter = PunchJsonAdapter(race.id, dataProcessor)

        val prevTime = result.startTime!!

        json.punches.forEachIndexed { index, punchJson ->

            val punch = punchJsonAdapter.fromJson(punchJson)
            punch.order = index
            punch.resultId = result.id

            prevTime.addTime(punch.split)
            punch.siTime = prevTime

            punches.add(
                AliasPunch(punch, null)
            )
        }

        return ReadoutData(result, punches)
    }
}
