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

package org.openardf.radiooracle.backend.wrappers

import org.openardf.radiooracle.backend.room.entity.Punch
import org.openardf.radiooracle.backend.room.entity.Result
import org.openardf.radiooracle.backend.room.entity.embeddeds.AliasPunch
import org.openardf.radiooracle.backend.room.enums.PunchStatus
import org.openardf.radiooracle.backend.room.enums.SIRecordType
import org.openardf.radiooracle.backend.sportident.SITime
import java.time.Duration
import java.time.LocalTime
import java.util.UUID

/** UI edit wrapper that tracks punch field validation state. */
data class PunchEditItemWrapper(
    var punch: Punch,
    var isCodeValid: Boolean,
    var isTimeValid: Boolean,
    var isDayValid: Boolean,
    var isWeekValid: Boolean,
    var aliasName: String? = null,
) {
    fun displayCodeText(): String =
        aliasName?.takeIf { it.isNotBlank() } ?: punch.siCode.takeIf { it != 0 }?.toString().orEmpty()

    fun matchesDisplayCodeText(text: String): Boolean =
        text == displayCodeText()

    companion object {
        /** Wraps alias-punch aggregates with optimistic valid flags for the edit UI. */
        fun getWrappers(punches: ArrayList<AliasPunch>): ArrayList<PunchEditItemWrapper> {
            return ArrayList(punches.map { ap ->
                PunchEditItemWrapper(
                    ap.punch,
                    isCodeValid = true,
                    isTimeValid = true,
                    isDayValid = true,
                    isWeekValid = true,
                    aliasName = ap.alias?.name
                )
            })
        }

        /** Extracts punch entities from edit wrappers for persistence. */
        fun getPunches(punchEditItemWrappers: ArrayList<PunchEditItemWrapper>): ArrayList<Punch> {
            val punches = ArrayList<Punch>()
            punchEditItemWrappers.forEach { wrapper ->
                punches.add(wrapper.punch)
            }
            return punches
        }

        /** Builds an editable start or finish punch from a result, or an empty placeholder if missing. */
        fun getStartOrFinishWrapper(
            start: Boolean,
            result: Result?,
            raceId: UUID
        ): PunchEditItemWrapper {

            if (start) {
                return if (result?.startTime != null) {
                    PunchEditItemWrapper(
                        Punch(
                            UUID.randomUUID(),
                            raceId,
                            null,
                            null,
                            0,
                            result.startTime!!,
                            result.startTime!!,
                            SIRecordType.START,
                            0,
                            PunchStatus.VALID,
                            Duration.ZERO
                        ), true, true, true, true
                    )
                } else {
                    PunchEditItemWrapper(
                        Punch(
                            UUID.randomUUID(),
                            raceId,
                            null,
                            null,
                            0,
                            SITime(LocalTime.MIN),
                            SITime(LocalTime.MIN),
                            SIRecordType.START,
                            0,
                            PunchStatus.VALID,
                            Duration.ZERO
                        ), true, true, true, true
                    )

                }
            }

            else {
                return if (result?.finishTime != null) {
                    PunchEditItemWrapper(
                        Punch(
                            UUID.randomUUID(),
                            raceId,
                            null,
                            null,
                            0,
                            result.finishTime!!,
                            result.finishTime!!,
                            SIRecordType.FINISH,
                            0,
                            PunchStatus.VALID,
                            Duration.ZERO
                        ), isCodeValid = true, isTimeValid = true, isDayValid = true, isWeekValid = true
                    )
                } else {
                    PunchEditItemWrapper(
                        Punch(
                            UUID.randomUUID(),
                            raceId,
                            null,
                            null,
                            0,
                            SITime(LocalTime.MIN),
                            SITime(LocalTime.MIN),
                            SIRecordType.FINISH,
                            0,
                            PunchStatus.VALID,
                            Duration.ZERO
                        ), isCodeValid = true, isTimeValid = true, isDayValid = true, isWeekValid = true
                    )

                }
            }
        }
    }
}
