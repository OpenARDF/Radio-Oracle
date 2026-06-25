package org.openardf.radiooracle.shared.event

const val EVENT_FILE_TRANSFER_CONTENT_TYPE = "application/vnd.openardf.radiooracle.event+json; charset=utf-8"

object EventFileTransferPayloads {
    fun isSeriesPackage(fileName: String?, contentType: String?): Boolean =
        fileName?.trim()?.endsWith(".zip", ignoreCase = true) == true ||
            contentType?.lowercase()?.let { type ->
                type == EVENT_SERIES_PACKAGE_CONTENT_TYPE || type.contains("zip")
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
            EVENT_SERIES_PACKAGE_CONTENT_TYPE
        }

    fun singleEventFileName(eventName: String): String =
        "${safeEventFileStem(eventName)}.ardfjs"

    fun seriesPackageFileName(seriesName: String): String =
        "${EventSeriesPackageContents.safePackageFileStem(seriesName)}.zip"

    private fun safeEventFileStem(name: String): String =
        name
            .trim()
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ")
            .replace(Regex("\\s+"), " ")
            .ifBlank { "race" }
}
