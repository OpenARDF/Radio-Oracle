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

package org.openardf.radiooracle.shared.domain

/** Stable result-status codes used in portable files and compact print displays. */
fun ResultStatus.toResultStatusCode(): String =
    when (this) {
        ResultStatus.OK -> "OK"
        ResultStatus.MISPUNCHED -> "MP"
        ResultStatus.NO_RANKING -> "NR"
        ResultStatus.DISQUALIFIED -> "DSQ"
        ResultStatus.DID_NOT_START -> "DNS"
        ResultStatus.DID_NOT_FINISH -> "DNF"
        ResultStatus.OVER_TIME_LIMIT -> "OVT"
        ResultStatus.UNOFFICIAL -> "UNF"
        ResultStatus.ERROR -> "ERR"
    }

fun resultStatusFromCode(code: String?, blankAsOk: Boolean = false): ResultStatus {
    val trimmed = code?.trim()
    if (trimmed == "" && blankAsOk) {
        return ResultStatus.OK
    }
    return when (trimmed) {
        "OK" -> ResultStatus.OK
        "MP", "Mispunched" -> ResultStatus.MISPUNCHED
        "NR", "No ranking" -> ResultStatus.NO_RANKING
        "DSQ", "Disqualified" -> ResultStatus.DISQUALIFIED
        "DNS", "Did not start" -> ResultStatus.DID_NOT_START
        "DNF", "Did not finish" -> ResultStatus.DID_NOT_FINISH
        "OVT", "Over time limit" -> ResultStatus.OVER_TIME_LIMIT
        "UNF", "Unofficial" -> ResultStatus.UNOFFICIAL
        "ERR", "Error" -> ResultStatus.ERROR
        else -> ResultStatus.NO_RANKING
    }
}
