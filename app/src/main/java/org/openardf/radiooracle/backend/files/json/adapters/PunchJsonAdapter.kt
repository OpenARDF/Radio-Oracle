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

package org.openardf.radiooracle.backend.files.json.adapters

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.files.json.temps.PunchJson
import org.openardf.radiooracle.backend.helpers.TimeProcessor
import org.openardf.radiooracle.backend.room.entity.Punch
import org.openardf.radiooracle.backend.room.entity.embeddeds.AliasPunch
import org.openardf.radiooracle.backend.room.enums.SIRecordType
import org.openardf.radiooracle.backend.sportident.SITime
import java.util.UUID

/** Moshi adapter for converting punch records to JSON control/split rows. */
class PunchJsonAdapter(val raceId: UUID, val dataProcessor: DataProcessor) {

    /** Serializes a punch using an alias display name when one exists. */
    @ToJson
    fun toJson(aliasPunch: AliasPunch): PunchJson {
        val punch = aliasPunch.punch
        return PunchJson(
            code = aliasPunch.alias?.name ?: punch.siCode.toString(),
            si_code = punch.siCode,
            control_type = punch.punchType.name,
            punch_status = dataProcessor.punchStatusToShortString(punch.punchStatus),
            split_time = TimeProcessor.durationToFormattedString(punch.split, true)
        )
    }

    /** Deserializes a JSON punch and leaves absolute SI time reconstruction to the result adapter. */
    @FromJson
    fun fromJson(punchJson: PunchJson): Punch {
        val punchType = SIRecordType.valueOf(punchJson.control_type)
        return Punch(
            id = UUID.randomUUID(),
            raceId = raceId,
            resultId = UUID.randomUUID(),
            cardNumber = 0,
            siCode = if (punchType == SIRecordType.CONTROL) {
                if (punchJson.si_code != null) {
                    punchJson.si_code!!
                } else punchJson.code.toInt()
            } else 0, // START and FINISH punches do not carry a control SI code.

            siTime = SITime(),
            origSiTime = SITime(),
            punchType = punchType,
            order = 0,
            punchStatus = dataProcessor
                .shortStringToPunchStatus(punchJson.punch_status),
            split = TimeProcessor.minuteStringToDuration(punchJson.split_time),
        )
    }
}
