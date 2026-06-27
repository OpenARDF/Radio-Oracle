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

package org.openardf.radiooracle.shared.results

import org.openardf.radiooracle.shared.domain.ResultStatus

/** Maps internal result statuses to IOF XML status strings. */
object IofResultStatus {
    /** Returns the IOF status value for a shared result status. */
    fun fromResultStatus(resultStatus: ResultStatus): String {
        return when (resultStatus) {
            ResultStatus.OK -> "OK"
            ResultStatus.MISPUNCHED -> "MissingPunch"
            ResultStatus.NO_RANKING -> "MissingPunch"
            ResultStatus.DISQUALIFIED -> "Disqualified"
            ResultStatus.DID_NOT_START -> "DidNotStart"
            ResultStatus.DID_NOT_FINISH -> "DidNotFinish"
            ResultStatus.OVER_TIME_LIMIT -> "OverTime"
            ResultStatus.UNOFFICIAL -> "NotCompeting"
            ResultStatus.ERROR -> "Cancelled"
        }
    }
}
