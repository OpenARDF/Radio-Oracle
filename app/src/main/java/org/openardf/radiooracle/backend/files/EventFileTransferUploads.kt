package org.openardf.radiooracle.backend.files

import org.openardf.radiooracle.shared.event.EventFileTransferPayloads

object EventFileTransferUploads {
    fun forRaceOrSeries(
        raceName: String,
        seriesName: String?,
        bytes: ByteArray
    ): DesktopFileTransferUpload =
        DesktopFileTransferUpload(
            fileName = EventFileTransferPayloads.fileNameForRaceOrSeries(raceName, seriesName),
            contentType = EventFileTransferPayloads.contentTypeForRaceOrSeries(seriesName),
            bytes = bytes
        )
}
