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
import org.openardf.radiooracle.shared.event.EventAlias
import org.openardf.radiooracle.shared.event.EventAliasPunch
import org.openardf.radiooracle.shared.event.EventPunch
import kotlin.test.Test
import kotlin.test.assertEquals

class ResultSplitRowsTest {
    @Test
    fun includesControlsAndFinishButNotStartOrCheck() {
        val rows = ResultSplitRows.from(
            punches = listOf(
                punch(SIRecordType.START, 0, 0),
                punch(SIRecordType.CONTROL, 31, 600, alias = EventAlias("alias", "race", 31, "F1")),
                punch(SIRecordType.CHECK, 0, 0),
                punch(SIRecordType.FINISH, 0, 75)
            )
        )

        assertEquals(listOf("F1", "Finish"), rows.map { it.label })
        assertEquals(listOf(600L, 75L), rows.map { it.splitSeconds })
    }

    @Test
    fun canKeepRawControlCodesWhileStillLabelingFinish() {
        val rows = ResultSplitRows.from(
            punches = listOf(
                punch(SIRecordType.CONTROL, 31, 600, alias = EventAlias("alias", "race", 31, "F1")),
                punch(SIRecordType.FINISH, 0, 75)
            ),
            controlLabelsByCode = mapOf(31 to "Fox 1"),
            useControlLabels = false
        )

        assertEquals(listOf("31", "Finish"), rows.map { it.label })
    }

    private fun punch(
        type: SIRecordType,
        code: Int,
        splitSeconds: Long,
        alias: EventAlias? = null
    ): EventAliasPunch =
        EventAliasPunch(
            punch = EventPunch(
                id = "${type.name}-$code",
                raceId = "race",
                resultId = "result",
                cardNumber = 123456,
                siCode = code,
                siTimeSeconds = 36_000 + splitSeconds,
                originalSiTimeSeconds = 36_000 + splitSeconds,
                punchType = type,
                order = 0,
                punchStatus = PunchStatus.VALID,
                splitSeconds = splitSeconds
            ),
            alias = alias
        )
}
