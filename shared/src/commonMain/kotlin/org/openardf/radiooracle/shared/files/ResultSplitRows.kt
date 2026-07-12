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

import org.openardf.radiooracle.shared.domain.PunchStatus
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.shared.event.EventAliasPunch

/** One display-ready result split, including the final leg to the finish. */
data class ResultSplitRow(
    val label: String,
    val splitSeconds: Long,
    val punchStatus: PunchStatus
)

/** Shared split selection and labeling used by result presentation surfaces. */
object ResultSplitRows {
    fun from(
        punches: List<EventAliasPunch>,
        controlLabelsByCode: Map<Int, String> = emptyMap(),
        useControlLabels: Boolean = true
    ): List<ResultSplitRow> =
        punches.mapNotNull { aliasPunch ->
            val label = when (aliasPunch.punch.punchType) {
                SIRecordType.CONTROL -> if (useControlLabels) {
                    controlLabelsByCode[aliasPunch.punch.siCode]
                        ?: aliasPunch.alias?.name
                        ?: aliasPunch.punch.siCode.toString()
                } else {
                    aliasPunch.punch.siCode.toString()
                }
                SIRecordType.FINISH -> "Finish"
                SIRecordType.START, SIRecordType.CHECK -> null
            } ?: return@mapNotNull null
            ResultSplitRow(
                label = label,
                splitSeconds = aliasPunch.punch.splitSeconds,
                punchStatus = aliasPunch.punch.punchStatus
            )
        }
}
