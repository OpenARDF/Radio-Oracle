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
import org.openardf.radiooracle.backend.room.enums.ResultServiceStatus
import org.openardf.radiooracle.backend.room.enums.ProviderType
import java.io.Serializable
import java.time.Duration
import java.time.LocalTime
import java.util.UUID

/** Room entity for one configured live-result publishing service. */
@Entity(
    tableName = "result_service",
    indices = [Index("race_id")],
    foreignKeys = [ForeignKey(
        entity = Race::class,
        parentColumns = arrayOf("id"),
        childColumns = arrayOf("race_id"),
        onDelete = ForeignKey.CASCADE
    )]
)
data class ResultService(
    @PrimaryKey var id: UUID,
    @ColumnInfo(name = "service_type") var serviceType: ProviderType,
    @ColumnInfo(name = "race_id") var raceId: UUID,
    @ColumnInfo(name = "url") var url: String,
    @ColumnInfo(name = "api_key") var apiKey: String,
    @ColumnInfo(name = "interval") var interval: Duration,
    @ColumnInfo(name = "enabled") var enabled: Boolean,
    @ColumnInfo(name = "init") var init: Boolean,
    @ColumnInfo(name = "status") var status: ResultServiceStatus,
    @ColumnInfo(name = "error_text") var errorText: String,
    @ColumnInfo(name = "sent") var sent: Int = 0,
    @ColumnInfo(name = "sent_at") var sentAt: LocalTime,
) : Serializable {
    /** Creates a disabled default ROBIS service configuration for a race. */
    constructor(raceId: UUID) : this(
        UUID.randomUUID(),
        ProviderType.ROBIS,
        raceId,
        "",
        "",
        Duration.ofSeconds(10),
        false,
        false,
        ResultServiceStatus.RUNNING,
        "",
        sentAt = LocalTime.now()
    )
}
