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

package org.openardf.radiooracle.backend.files.wrappers

import org.openardf.radiooracle.backend.files.constants.DataType
import org.openardf.radiooracle.backend.room.entity.embeddeds.CategoryData
import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorCategory
import org.openardf.radiooracle.backend.room.entity.embeddeds.ReadoutData

/** Result of an import pass, including accepted rows and row-level validation failures. */
data class DataImportWrapper(
    var competitorCategories: List<CompetitorCategory>,
    var categories: List<CategoryData>,
    var invalidLines: ArrayList<Pair<Int, String>>, // Row index plus validation failure reason.
    var readoutData: List<ReadoutData> = emptyList(),
    var iofWarnings: List<String> = emptyList()
) {
    /** Returns the number of accepted rows relevant to the requested import data type. */
    fun getCount(dataType: DataType): Int {
        return when (dataType) {
            DataType.CATEGORIES -> categories.size
            DataType.COMPETITORS, DataType.COMPETITOR_STARTS -> competitorCategories.size
            DataType.RESULTS_LIVE -> readoutData.size
            else -> 0
        }
    }
}
