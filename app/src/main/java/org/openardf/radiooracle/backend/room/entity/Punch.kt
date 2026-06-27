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

package org.openardf.radiooracle.backend.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.openardf.radiooracle.backend.room.enums.PunchStatus
import org.openardf.radiooracle.backend.room.enums.SIRecordType
import org.openardf.radiooracle.backend.sportident.SITime
import org.openardf.radiooracle.shared.files.EventCsvRows
import java.io.Serializable
import java.time.Duration
import java.util.UUID

/** Room entity for one punch read from a SportIdent card. */
@Entity(
    tableName = "punch",
    indices = [Index("result_id")],
    foreignKeys = [ForeignKey(
        entity = Result::class,
        parentColumns = arrayOf("id"),
        childColumns = arrayOf("result_id"),
        onDelete = ForeignKey.CASCADE
    )]
)
data class Punch(
    @PrimaryKey var id: UUID,
    @ColumnInfo(name = "race_id") var raceId: UUID,
    @ColumnInfo(name = "result_id") var resultId: UUID?,
    @ColumnInfo(name = "card_number") var cardNumber: Int? = null,
    @ColumnInfo(name = "si_code") var siCode: Int,
    @ColumnInfo(name = "si_time") var siTime: SITime,
    @ColumnInfo(name = "orig_si_time") var origSiTime: SITime,
    @ColumnInfo(name = "punch_type") var punchType: SIRecordType,
    @ColumnInfo(name = "order") var order: Int,
    @ColumnInfo(name = "punch_status") var punchStatus: PunchStatus,
    @ColumnInfo(name = "split") var split: Duration
) : Serializable {
    /** Formats this punch in the legacy readout CSV row shape. */
    fun toCsvString(): String {
        return EventCsvRows.punchRow(cardNumber, siCode, siTime.toString())
    }

    /** Default constructor used by debug views, tests, and tooling that require defaults. */
    constructor() : this(
        id = UUID.randomUUID(),
        raceId = UUID.randomUUID(),
        resultId = null,
        cardNumber = null,
        siCode = 0,
        siTime = SITime(),
        origSiTime = SITime(),
        punchType = SIRecordType.CONTROL,
        order = 0,
        punchStatus = PunchStatus.UNKNOWN,
        split = Duration.ZERO
    )

    /** Convenience constructor for building parsed SportIdent punch records. */
    constructor(siCode: Int, siTime: SITime, punchType: SIRecordType, order: Int) : this(
        id = UUID.randomUUID(),
        raceId = UUID.randomUUID(),
        resultId = null,
        cardNumber = null,
        siCode = siCode,
        siTime = siTime,
        origSiTime = siTime,
        punchType = punchType,
        order = order,
        punchStatus = PunchStatus.UNKNOWN,
        split = Duration.ZERO
    )
}
