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

package org.openardf.radiooracle.backend.files

import android.content.Context
import android.net.Uri
import android.util.Log
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.files.constants.DataFormat
import org.openardf.radiooracle.backend.files.constants.DataType
import org.openardf.radiooracle.backend.files.processors.CsvProcessor
import org.openardf.radiooracle.backend.files.processors.FormatProcessorFactory
import org.openardf.radiooracle.backend.files.processors.JsonProcessor
import org.openardf.radiooracle.backend.files.wrappers.DataImportWrapper
import org.openardf.radiooracle.backend.room.entity.Category
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.room.entity.embeddeds.RaceData
import org.openardf.radiooracle.backend.room.enums.StandardCategoryType
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.ref.WeakReference
import java.util.UUID

/** Android file import/export coordinator backed by the system content resolver. */
class FileProcessor(appContext: WeakReference<Context>) {
    private val dataProcessor = DataProcessor.get()
    private val contentResolver = appContext.get()?.contentResolver

    /** Opens a read stream for a user-selected document URI. */
    private fun openInputStream(uri: Uri): InputStream? {
        try {
            return contentResolver?.openInputStream(uri)
        } catch (exception: Exception) {
            Log.e("Failed to open file for read: ", exception.stackTrace.toString())
        }
        return null
    }

    /** Opens a write stream for a user-selected document URI. */
    private fun openOutputStream(uri: Uri): OutputStream? {
        try {
            return contentResolver?.openOutputStream(uri)
        } catch (exception: Exception) {
            Log.e("Failed to open file for write: ", exception.stackTrace.toString())
        }
        return null
    }

    /** Imports typed event data from the selected file using the requested format processor. */
    suspend fun importData(
        uri: Uri,
        type: DataType,
        format: DataFormat,
        race: Race,
        context: Context
    ): DataImportWrapper {
        val inStream = openInputStream(uri)
        if (inStream != null) {

            val proc = FormatProcessorFactory.getFormatProcessor(format)
            return proc.importData(inStream, type, race, dataProcessor)
        }
        throw RuntimeException(context.getString(R.string.data_import_file_error))
    }


    /** Creates built-in standard categories for a race. */
    suspend fun importStandardCategories(
        type: StandardCategoryType,
        race: Race
    ): List<Category> =
        CsvProcessor.importStandardCategories(type, race, dataProcessor)

    /** Exports typed event data to the selected file using the requested format processor. */
    suspend fun exportData(
        uri: Uri,
        type: DataType,
        format: DataFormat,
        race: Race,
    ) {
        val outStream = openOutputStream(uri)
        if (outStream != null) {
            val proc = FormatProcessorFactory.getFormatProcessor(format)

            proc.exportData(
                outStream,
                type,
                format,
                dataProcessor,
                race
            )

        } else {
            throw IOException(dataProcessor.getContext()?.getString(R.string.data_import_file_error))
        }
    }

    /** Imports a full race backup from JSON. */
    suspend fun importRaceData(uri: Uri, context: Context): RaceData {
        val inStream = openInputStream(uri)
        if (inStream != null) {
            return JsonProcessor.importRaceData(inStream, dataProcessor)
        }
        throw IOException(context.getString(R.string.data_import_file_error))
    }

    /** Exports a full race backup to JSON. */
    suspend fun exportRaceData(uri: Uri, raceId: UUID) {
        val outStream = openOutputStream(uri)
        if (outStream != null) {
            return JsonProcessor.exportRaceData(outStream, dataProcessor, raceId)
        }
        throw RuntimeException()
    }
}
