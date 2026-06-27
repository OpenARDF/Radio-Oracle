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

package org.openardf.radiooracle.backend.results

import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorData

/** Sorts competitors so readouts appear before missing results, then by result ranking. */
class ResultDataComparator : Comparator<CompetitorData> {
    /** Compares null-readout competitors after competitors with readout data. */
    override fun compare(o1: CompetitorData, o2: CompetitorData): Int {
        val readoutData1 = o1.readoutData
        val readoutData2 = o2.readoutData

        if (readoutData1 == null && readoutData2 == null) {
            return 0
        } else if (readoutData1 == null) {
            return 1
        } else if (readoutData2 == null) {
            return -1
        }

        return readoutData1.result.compareTo(readoutData2.result)
    }
}
