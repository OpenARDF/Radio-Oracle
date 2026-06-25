package org.openardf.radiooracle.shared.event

const val EVENT_FILE_TRANSFER_CONTENT_TYPE = "application/vnd.openardf.radiooracle.event+json; charset=utf-8"

object EventFileTransferPayloads {
    fun isSeriesPackage(fileName: String?, contentType: String?): Boolean =
        fileName?.trim()?.endsWith(".zip", ignoreCase = true) == true ||
            contentType?.lowercase()?.let { type ->
                type == EVENT_SERIES_PACKAGE_CONTENT_TYPE || type.contains("zip")
            } == true
}
