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
import org.openardf.radiooracle.backend.room.enums.ControlPointType
import org.openardf.radiooracle.shared.course.ControlPointDefinition
import org.openardf.radiooracle.shared.course.ControlPointRules
import java.io.Serializable
import java.util.UUID

/** Room entity for one ordered control point in a category course. */
@Entity(
    tableName = "control_point",
    indices = [Index("category_id")],
    foreignKeys = [ForeignKey(
        entity = Category::class,
        parentColumns = arrayOf("id"),
        childColumns = arrayOf("category_id"),
        onDelete = ForeignKey.CASCADE
    )]
)
data class ControlPoint(
    @PrimaryKey var id: UUID,
    @ColumnInfo(name = "category_id") var categoryId: UUID,
    @ColumnInfo(name = "si_code") var siCode: Int,
    @ColumnInfo(name = "type") var type: ControlPointType,
    @ColumnInfo(name = "order") var order: Int,
    @ColumnInfo(name = "portable_control_id") var portableControlId: String? = null
) : Serializable {

    /** Formats this single control point as the compact CSV token used by exports. */
    fun toCsvString(): String {
        return ControlPointRules.formatControlPoints(
            listOf(ControlPointDefinition(siCode, type, order))
        )
    }

    /** Default constructor used by tests and Room/tooling support. */
    constructor() : this(
        UUID.randomUUID(),
        UUID.randomUUID(),
        31,
        ControlPointType.CONTROL,
        0
    )

    /** Convenience constructor for tests that only need a control SI code. */
    constructor(siCode: Int) : this(
        UUID.randomUUID(),
        UUID.randomUUID(),
        siCode,
        ControlPointType.CONTROL,
        0
    )
}
