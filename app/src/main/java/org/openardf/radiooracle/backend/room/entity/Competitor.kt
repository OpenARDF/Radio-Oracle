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
import org.openardf.radiooracle.backend.helpers.TimeProcessor
import org.openardf.radiooracle.backend.shared.toEventCompetitor
import org.openardf.radiooracle.shared.files.EventCsvRows
import java.io.Serializable
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

/** Room entity for a registered competitor in a race. */
@Entity(
    tableName = "competitor", indices = [
        Index(value = ["start_number", "race_id"]),
        Index("race_id"),
        Index("category_id")
    ],
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("category_id"),
            onDelete = ForeignKey.SET_NULL,

            ),
        ForeignKey(
            entity = Race::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("race_id"),
            onDelete = ForeignKey.CASCADE
        )]
)
data class Competitor(
    @PrimaryKey var id: UUID,
    @ColumnInfo(name = "race_id") var raceId: UUID,
    @ColumnInfo(name = "category_id") var categoryId: UUID? = null,
    @ColumnInfo(name = "first_name") var firstName: String,
    @ColumnInfo(name = "last_name") var lastName: String,
    @ColumnInfo(name = "club") var club: String,
    @ColumnInfo(name = "index") var index: String,
    @ColumnInfo(name = "is_man") var isMan: Boolean = false,
    @ColumnInfo(name = "birth_year") var birthYear: Int? = null,
    @ColumnInfo(name = "si_number") var siNumber: Int? = null,
    @ColumnInfo(name = "si_rent") var siRent: Boolean = false,
    @ColumnInfo(name = "start_number") var startNumber: Int,
    @ColumnInfo(name = "drawn_start_time") var drawnRelativeStartTime: Duration? = null,
) : Serializable {
    /** Returns the display name in the shared LASTNAME Firstname format. */
    fun getFullName(): String {
        return toEventCompetitor().fullName()
    }

    /** Returns the display name with the competitor's start number appended. */
    fun getNameWithStartNumber(): String {
        return toEventCompetitor().nameWithStartNumber()
    }

    /** Formats this competitor in the legacy competitor CSV export row shape. */
    fun toSimpleCsvString(categoryName: String): String {
        return EventCsvRows.competitorRow(toEventCompetitor(), categoryName)
    }

    /** Formats this competitor in the start-list CSV export row shape. */
    fun toStartCsvString(
        categoryName: String,
        raceStart: LocalDateTime
    ): String {

        val real =
            if (drawnRelativeStartTime != null) {
                TimeProcessor.hoursMinutesFormatter(raceStart + drawnRelativeStartTime)
            } else null

        return EventCsvRows.competitorStartRow(toEventCompetitor(), categoryName, real)
    }

    /** No-argument constructor used by serialization and tooling that require defaults. */
    constructor() : this(
        UUID.randomUUID(),
        UUID.randomUUID(),
        null,
        "Test",
        "Tester",
        "AC-Test",
        "ACT0001",
        true,
        2000,
        123456789,
        false,
        0,
        null
    )
}
