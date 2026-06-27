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

package org.openardf.radiooracle.backend.room.entity.embeddeds

import androidx.room.Embedded
import androidx.room.Relation
import org.openardf.radiooracle.backend.room.entity.Competitor
import org.openardf.radiooracle.backend.room.entity.Punch
import org.openardf.radiooracle.backend.room.entity.Result
import org.openardf.radiooracle.backend.room.enums.SIRecordType
import org.openardf.radiooracle.shared.files.EventCsvRows
import org.openardf.radiooracle.shared.files.TimedPunchCsvField
import java.io.Serializable

/** Room aggregate for a readout, its punches, and the matched competitor/category when available. */
data class ResultData(
    @Embedded var result: Result,

    @Relation(
        entityColumn = "result_id",
        parentColumn = "id",
        entity = Punch::class
    )
    var punches: List<AliasPunch>,
    @Relation(
        parentColumn = "competitor_id",
        entityColumn = "id",
        entity = Competitor::class
    ) var competitorCategory: CompetitorCategory?

) : Serializable {
    /** Returns only the punch entities from the alias-punch relation list. */
    fun getPunchList(): List<Punch> {
        return punches.map { p -> p.punch }
    }

    /** Formats this readout in the legacy readout CSV export shape. */
    fun toReadoutCSVString(): String {
        val controlPunches = punches
            .filter { punch -> punch.punch.punchType == SIRecordType.CONTROL }
            .map { punch ->
                TimedPunchCsvField(
                    siCode = punch.punch.siCode,
                    timeText = punch.punch.siTime.getTimeString()
                )
            }

        return EventCsvRows.readoutRow(
            siNumber = result.siNumber,
            checkTimeText = result.checkTime?.getTimeString(),
            startTimeText = result.startTime?.getTimeString(),
            finishTimeText = result.finishTime?.getTimeString(),
            controlPunches = controlPunches
        )
    }
}
