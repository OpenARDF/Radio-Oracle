package org.openardf.radiooracle.backend.files

import org.openardf.radiooracle.shared.event.EVENT_FILE_TRANSFER_CONTENT_TYPE
import org.openardf.radiooracle.shared.event.EVENT_SERIES_PACKAGE_CONTENT_TYPE
import org.openardf.radiooracle.shared.event.EventSeriesPackageContents

object EventFileTransferUploads {
    fun forRaceOrSeries(
        raceName: String,
        seriesName: String?,
        bytes: ByteArray
    ): DesktopFileTransferUpload =
        DesktopFileTransferUpload(
            fileName = fileNameForRaceOrSeries(raceName, seriesName),
            contentType = contentTypeForRaceOrSeries(seriesName),
            bytes = bytes
        )

    fun fileNameForRaceOrSeries(raceName: String, seriesName: String?): String =
        if (seriesName == null) {
            "${safeEventFileStem(raceName)}.ardfjs"
        } else {
            "${EventSeriesPackageContents.safePackageFileStem(seriesName)}.zip"
        }

    fun contentTypeForRaceOrSeries(seriesName: String?): String =
        if (seriesName == null) {
            EVENT_FILE_TRANSFER_CONTENT_TYPE
        } else {
            EVENT_SERIES_PACKAGE_CONTENT_TYPE
        }

    private fun safeEventFileStem(name: String): String =
        name
            .trim()
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ")
            .replace(Regex("\\s+"), " ")
            .ifBlank { "race" }
}
