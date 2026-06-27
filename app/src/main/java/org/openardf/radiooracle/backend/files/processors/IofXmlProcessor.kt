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

package org.openardf.radiooracle.backend.files.processors

import android.content.Context
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.files.constants.DataFormat
import org.openardf.radiooracle.backend.files.constants.DataType
import org.openardf.radiooracle.backend.files.wrappers.DataImportWrapper
import org.openardf.radiooracle.backend.files.xml.XmlHelper
import org.openardf.radiooracle.backend.results.ResultsProcessor
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.room.entity.embeddeds.CategoryData
import org.openardf.radiooracle.backend.wrappers.ResultWrapper
import kotlinx.coroutines.flow.first
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import kotlin.collections.emptyList

/** Import/export processor for IOF XML interoperability. */
object IofXmlProcessor : FormatProcessor {

    /** Imports IOF XML data types currently supported by the app. */
    override suspend fun importData(
        inStream: InputStream,
        dataType: DataType,
        race: Race,
        dataProcessor: DataProcessor
    ): DataImportWrapper {
        val context = dataProcessor.getContext()

        if (context != null) {
            return when (dataType) {
                DataType.CATEGORIES -> importCategories(
                    inStream,
                    race,
                    context
                )

                else -> {
                    TODO()
                }
            }
        }
        return DataImportWrapper(emptyList(), emptyList(), ArrayList())
    }

    /** Exports IOF XML data types currently supported by the app. */
    override suspend fun exportData(
        outStream: OutputStream,
        dataType: DataType,
        format: DataFormat,
        dataProcessor: DataProcessor,
        race: Race
    ) {
        when (dataType) {
            DataType.COMPETITORS -> TODO()
            DataType.RESULTS_LIVE -> exportResults(
                outStream,
                race, ResultsProcessor.getResultWrapperFlowByRace(race.id, dataProcessor).first()
                    .filter { it.category != null },
                dataProcessor
            )

            else -> TODO()
        }
    }

    fun importCompetitorData(
        inStream: InputStream,
        race: Race,
        categories: HashSet<CategoryData>
    ): DataImportWrapper {
        // Competitor import is not implemented yet; return an empty wrapper for callers that probe it.
        return DataImportWrapper(emptyList(), emptyList(), arrayListOf())
    }

    /** Imports IOF XML course/category data into category aggregates. */
    fun importCategories(
        inStream: InputStream,
        race: Race,
        context: Context
    ): DataImportWrapper {

        val cats = XmlHelper.parseCategories(inStream, race, context)
        return DataImportWrapper(emptyList(), cats, arrayListOf())
    }

    /** Placeholder for future IOF XML category export support. */
    fun exportCategories(
        outStream: OutputStream,
        race: Race,
        dataProcessor: DataProcessor
    ) {
    }

    /** Exports an IOF XML start list for the supplied category data. */
    suspend fun exportStartList(
        outStream: OutputStream,
        race: Race,
        data: List<CategoryData>,
        dataProcessor: DataProcessor
    ) {
        var writer: OutputStreamWriter? = null
        try {
            val (serializer, w) = XmlHelper.createSerializer(outStream)
            writer = w

            XmlHelper.writeRootTag(serializer, race, "StartList", dataProcessor)

            for (res in data) {
                XmlHelper.writeCategoryStartList(serializer, res, race.startDateTime)
            }

            serializer.endTag(null, "StartList")
            XmlHelper.finishSerializer(serializer, writer)
        } catch (ex: Exception) {
            throw RuntimeException("Failed to export IOF XML startlist: ${ex.message}", ex)
        }
    }

    /** Exports an IOF XML result list for categorized live results. */
    suspend fun exportResults(
        outStream: OutputStream,
        race: Race,
        results: List<ResultWrapper>,
        dataProcessor: DataProcessor
    ) {
        var writer: OutputStreamWriter? = null
        try {
            val (serializer, w) = XmlHelper.createSerializer(outStream)
            writer = w

            XmlHelper.writeRootTag(serializer, race, "ResultList", dataProcessor)

            for (res in results) {
                XmlHelper.writeCategoryResult(serializer, res, race.startDateTime)
            }

            serializer.endTag(null, "ResultList")
            XmlHelper.finishSerializer(serializer, writer)
        } catch (ex: Exception) {
            throw RuntimeException("Failed to export IOF XML: ${ex.message}", ex)
        }
    }
}
