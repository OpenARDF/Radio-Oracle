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

package org.openardf.radiooracle.shared.event

const val EVENT_FILE_TRANSFER_CONTENT_TYPE = "application/vnd.openardf.radiooracle.event+json; charset=utf-8"

object EventFileTransferPayloads {
    fun isSeriesPackage(fileName: String?, contentType: String?): Boolean =
        fileName?.trim()?.let { name ->
            isEventSeriesArchiveFileName(name) || name.endsWith(".zip", ignoreCase = true)
        } == true ||
            contentType?.lowercase()?.let { type ->
                type == EVENT_SERIES_ARCHIVE_CONTENT_TYPE ||
                    type == LEGACY_EVENT_SERIES_ARCHIVE_CONTENT_TYPE ||
                    type.contains("zip")
            } == true

    fun fileNameForRaceOrSeries(raceName: String, seriesName: String?): String =
        if (seriesName == null) {
            singleEventFileName(raceName)
        } else {
            seriesPackageFileName(seriesName)
        }

    fun contentTypeForRaceOrSeries(seriesName: String?): String =
        if (seriesName == null) {
            EVENT_FILE_TRANSFER_CONTENT_TYPE
        } else {
            EVENT_SERIES_ARCHIVE_CONTENT_TYPE
        }

    fun singleEventFileName(eventName: String): String =
        "${safeEventFileStem(eventName)}.json"

    fun seriesPackageFileName(seriesName: String): String =
        "${EventSeriesPackageContents.safePackageFileStem(seriesName)}$EVENT_SERIES_ARCHIVE_FILE_SUFFIX"

    private fun safeEventFileStem(name: String): String =
        name
            .trim()
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ")
            .replace(Regex("\\s+"), " ")
            .ifBlank { "race" }
}
