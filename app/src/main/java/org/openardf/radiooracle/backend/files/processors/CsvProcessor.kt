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
import android.util.Log
import androidx.preference.PreferenceManager
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.files.constants.DataFormat
import org.openardf.radiooracle.backend.files.constants.DataType
import org.openardf.radiooracle.backend.files.wrappers.DataImportWrapper
import org.openardf.radiooracle.backend.helpers.ControlPointsHelper
import org.openardf.radiooracle.backend.helpers.TimeProcessor
import org.openardf.radiooracle.backend.results.ResultsProcessor
import org.openardf.radiooracle.backend.room.entity.Category
import org.openardf.radiooracle.backend.room.entity.Competitor
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.room.entity.embeddeds.CategoryData
import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorCategory
import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorData
import org.openardf.radiooracle.backend.room.entity.embeddeds.ResultData
import org.openardf.radiooracle.backend.room.enums.StandardCategoryType
import org.openardf.radiooracle.backend.wrappers.ResultWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.openardf.radiooracle.shared.files.CompetitorCsvImportRow
import org.openardf.radiooracle.shared.files.EventCsvFormat
import org.openardf.radiooracle.shared.files.EventCsvImports
import org.openardf.radiooracle.shared.event.StandardCategoryRules
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Duration
import java.util.UUID

/** Import/export processor for Radio-Oracle's semicolon-delimited CSV formats. */
object CsvProcessor : FormatProcessor {

    /** Imports the requested CSV data type into transient aggregates for validation and persistence. */
    override suspend fun importData(
        inStream: InputStream,
        dataType: DataType,
        race: Race,
        dataProcessor: DataProcessor
    ): DataImportWrapper {
        val context = dataProcessor.getContext()

        if (context != null) {
            return when (dataType) {
                DataType.CATEGORIES -> return importCategories(
                    inStream,
                    race,
                    dataProcessor,
                    context
                )

                DataType.COMPETITORS -> return importCompetitorData(
                    inStream,
                    race,
                    dataProcessor.getCategoryDataFlowForRace(race.id).first().toHashSet(),
                    dataProcessor,
                    context
                )

                DataType.COMPETITOR_STARTS -> return importCompetitorStarts(
                    inStream,
                    dataProcessor.getCompetitorDataFlowByRace(race.id).first().toHashSet(),
                    context
                )

                else -> DataImportWrapper(emptyList(), emptyList(), ArrayList())
            }
        }
        return DataImportWrapper(emptyList(), emptyList(), ArrayList())
    }

    /** Exports the requested data type in the app's legacy CSV shape. */
    override suspend fun exportData(
        outStream: OutputStream,
        dataType: DataType,
        format: DataFormat,
        dataProcessor: DataProcessor,
        race: Race
    ) {

        when (dataType) {
            DataType.CATEGORIES -> exportCategories(
                outStream,
                dataProcessor.getCategoryDataForRace(race.id)
            )

            DataType.COMPETITORS -> exportCompetitors(
                outStream,
                dataProcessor.getCompetitorDataFlowByRace(race.id).first()
            )

            DataType.COMPETITOR_STARTS ->
                exportStarts(
                    outStream,
                    dataProcessor.getCompetitorDataFlowByRace(race.id).first(),
                    race
                )

            DataType.RESULTS_FINAL, DataType.RESULTS_LIVE -> exportResults(
                outStream,
                ResultsProcessor.getResultWrapperFlowByRace(race.id, dataProcessor).first()
            )

            DataType.READOUT_DATA -> {
                exportReadoutData(
                    outStream,
                    dataProcessor.getResultDataFlowByRace(race.id).first()
                )
            }

        }
    }


    /** Imports category/course rows and reports invalid rows without aborting the whole import. */
    private fun importCategories(
        inStream: InputStream,
        race: Race,
        dataProcessor: DataProcessor,
        context: Context
    ): DataImportWrapper {
        val categories = ArrayList<CategoryData>()
        val parsedRows = EventCsvImports.parseAndroidCategoryRows(inStream.bufferedReader().readText())
        val invalidLines = parsedRows.invalidLines
            .mapTo(ArrayList()) { it.lineIndex to it.message }

        for ((index, row) in parsedRows.rows.withIndex()) {
            try {
                val category = Category(
                    UUID.randomUUID(),
                    race.id,
                    row.name,
                    row.isMan,
                    row.maxAge,
                    row.lengthMeters,
                    row.climbMeters,
                    0,
                    false,
                    null,
                    null,
                    null,
                    ""
                )

                val controlPoints = ControlPointsHelper.getControlPointsFromString(
                    row.controlPointsText,
                    category.id,
                    race.raceType,
                    context
                )
                category.controlPointsString = row.controlPointsText

                categories.add(
                    CategoryData(
                        category,
                        controlPoints,
                        emptyList()
                    )
                )
            } catch (e: Exception) {
                Log.w(
                    "CSV import",
                    "Failed to import category: ${row.name}\n" + e.stackTraceToString()
                )
                invalidLines.add(index to (e.message ?: ""))
            }
        }
        return DataImportWrapper(emptyList(), categories.toList(), invalidLines)
    }

    /** Creates missing built-in categories for the selected standard category set. */
    suspend fun importStandardCategories(
        type: StandardCategoryType,
        race: Race,
        dataProcessor: DataProcessor
    ): List<Category> {
        val context = dataProcessor.getContext()
        if (context == null) {
            return emptyList()
        }

        val definitions = StandardCategoryRules.definitionsFor(type)
        val categories = ArrayList<Category>()

        for ((index, definition) in definitions.withIndex()) {
            if (dataProcessor.getCategoryByName(definition.name, race.id) == null) {
                val cat = Category(
                    UUID.randomUUID(),
                    race.id,
                    definition.name,
                    definition.isMan,
                    definition.maxAge,
                    0,
                    0,
                    index,
                    false,
                    null,
                    null,
                    null,
                    ""
                )
                categories.add(cat)
            }
        }

        return categories.toList()
    }

    /** Imports competitor rows, creating category placeholders for category names not yet in the race. */
    private suspend fun importCompetitorData(
        inStream: InputStream,
        race: Race,
        categories: HashSet<CategoryData>,

        dataProcessor: DataProcessor,
        context: Context
    ): DataImportWrapper {

        val competitors = ArrayList<CompetitorCategory>()
        var currOrder =
            dataProcessor.getHighestCategoryOrder(race.id) + 1 // Preserve category order when new categories are created.
        var currStartNum = dataProcessor.getHighestStartNumberByRace(race.id) + 1
        val parsedRows = EventCsvImports.parseAndroidCompetitorRows(inStream.bufferedReader().readText())
        val invalidLines = parsedRows.invalidLines
            .mapTo(ArrayList()) { it.lineIndex to it.message }
        val startNumberByDrawnStartTime = parsedRows.rows
            .mapNotNull { row -> row.startTimeText?.let { runCatching { TimeProcessor.minuteStringToDuration(it) }.getOrNull() } }
            .distinct()
            .sorted()
            .withIndex()
            .associate { (index, startTime) -> startTime to index + 1 }

        for ((index, row) in parsedRows.rows.withIndex()) {
            try {
                val category = findOrCreateCategory(row, race, categories, currOrder)
                if (category != null && categories.none { it.category.id == category.category.id }) {
                    currOrder++
                    categories.add(category)
                }

                val categoryId = category?.category?.id
                val drawnRelativeStartTime: Duration? =
                    row.startTimeText?.let { TimeProcessor.minuteStringToDuration(it) }
                /*
                 * A start number is a start-time slot, not a competitor identity.
                 * When import rows include start times, simultaneous starters share
                 * the number derived from that unique time.
                 */
                val startNumber = drawnRelativeStartTime?.let(startNumberByDrawnStartTime::get)
                    ?: row.startNumber
                    ?: currStartNum++
                if (startNumber >= currStartNum) {
                    currStartNum = startNumber + 1
                }

                val competitor = Competitor(
                    UUID.randomUUID(),
                    race.id,
                    categoryId,
                    row.firstName,
                    row.lastName,
                    row.club,
                    row.personId,
                    row.isMan,
                    row.birthYear,
                    row.siNumber,
                    row.siRent,
                    startNumber,
                    drawnRelativeStartTime,
                    row.bibNumber
                )
                competitors.add(CompetitorCategory(competitor, category?.category))
            } catch (e: Exception) {
                Log.w(
                    "CSV import",
                    "Failed to import competitor \n\" " + e.stackTraceToString()
                )

                invalidLines.add(Pair(index, e.message ?: ""))
            }
        }
        return DataImportWrapper(competitors, categories.toList(), invalidLines)
    }

    private fun findOrCreateCategory(
        row: CompetitorCsvImportRow,
        race: Race,
        categories: HashSet<CategoryData>,
        order: Int
    ): CategoryData? {
        if (row.categoryName.isEmpty()) {
            return null
        }
        categories.find { it.category.name == row.categoryName }?.let { return it }
        return CategoryData(
            Category(
                UUID.randomUUID(),
                race.id,
                row.categoryName,
                false,
                null,
                0,
                0,
                order,
                false,
                null,
                null,
                null,
                ""
            ), emptyList(), emptyList()
        )
    }

    /** Imports start-list rows and updates matched competitors by SI number, bib number, or start number. */
    private fun importCompetitorStarts(
        inStream: InputStream,
        competitors: HashSet<CompetitorData>,

        context: Context
    ): DataImportWrapper {
        val parsedRows = EventCsvImports.parseAndroidCompetitorStartRows(inStream.bufferedReader().readText())
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        val preferAppStartTime =
            sharedPref.getBoolean(
                context.getString(R.string.key_files_prefer_app_start_time),
                false
            )
        val invalidLines = parsedRows.invalidLines
            .mapTo(ArrayList()) { it.lineIndex to it.message }

        for ((index, row) in parsedRows.rows.withIndex()) {
            try {
                val relativeTime = TimeProcessor.minuteStringToDuration(row.startTimeText)
                val bibNumber = row.bibNumber.trim()
                val match = row.siNumber?.let { siNumber ->
                    competitors.find { it.competitorCategory.competitor.siNumber == siNumber }
                } ?: row.personId.trim().takeIf { it.isNotEmpty() }?.let { personId ->
                    competitors.singleOrNull {
                        it.competitorCategory.competitor.index.trim() == personId
                    }
                } ?: bibNumber.takeIf { it.isNotEmpty() }?.let { importedBibNumber ->
                    competitors.singleOrNull {
                        it.competitorCategory.competitor.bibNumber == importedBibNumber
                    }
                } ?: competitors.find { it.competitorCategory.competitor.startNumber == row.startNumber }

                if (match != null) {
                    val competitor = match.competitorCategory.competitor
                    if (!preferAppStartTime) {
                        competitor.drawnRelativeStartTime = relativeTime
                    }

                    if (row.siNumber != null) {
                        competitor.siNumber = row.siNumber
                    }
                    if (bibNumber.isNotEmpty()) {
                        require(
                            competitors.none {
                                it != match && it.competitorCategory.competitor.bibNumber == bibNumber
                            }
                        ) {
                            "Bib number must be unique."
                        }
                        competitor.bibNumber = bibNumber
                    }
                    row.corridor?.let { competitor.corridor = it }
                }
            } catch (e: Exception) {
                Log.e(
                    "CSV import",
                    "Failed to import competitor start: \n" + e.stackTraceToString()
                )
                invalidLines.add(Pair(index, e.message ?: ""))
            }
        }

        return DataImportWrapper(
            competitors.map { it.competitorCategory },
            emptyList(),
            invalidLines
        )
    }


    // TODO: Finish lower-priority CSV export variants that are currently only partially implemented.

    /** Exports categories with the compact control-point list used by legacy CSV consumers. */
    @Throws(IOException::class)
    suspend fun exportCategories(outStream: OutputStream, categories: List<CategoryData>) {

        withContext(Dispatchers.IO) {
            val writer = outStream.bufferedWriter()
            for (data in categories) {

                writer.write(data.category.toCSVString())
                writer.write(";")
                writer.write(data.controlPoints.size.toString())
                writer.write(";")

                // Control points are stored as a comma-separated list inside the final CSV column.
                for (cp in data.controlPoints.withIndex()) {
                    writer.write(cp.value.toCsvString())

                    if (cp.index < data.controlPoints.size - 1) {
                        writer.write(",")
                    }
                }
                writer.newLine()
            }
            writer.flush()
        }
    }

    /** Exports registered competitors with category names for event administration. */
    @Throws(IOException::class)
    suspend fun exportCompetitors(
        outStream: OutputStream,
        competitorData: List<CompetitorData>
    ) {
        val writer = outStream.bufferedWriter()
        withContext(Dispatchers.IO) {
            writer.write(EventCsvFormat.Competitor.HEADER_ROW)
            writer.newLine()

            for (com in competitorData) {
                writer.write(
                    com.competitorCategory.competitor.toSimpleCsvString(
                        com.competitorCategory.category?.name ?: ""
                    )
                )
                writer.newLine()
            }
            writer.flush()
        }
    }

    /** Exports the competitor start list with start times relative to the race start. */
    @Throws(IOException::class)
    suspend fun exportStarts(
        outStream: OutputStream,
        competitorData: List<CompetitorData>,
        race: Race
    ) {
        val writer = outStream.bufferedWriter()
        withContext(Dispatchers.IO) {
            for (com in competitorData) {
                val category = com.competitorCategory.category
                writer.write(
                    com.competitorCategory.competitor.toStartCsvString(
                        category?.name ?: "",
                        race.startDateTime
                    )
                )
                writer.newLine()
            }
            writer.flush()
        }
    }

    /** Exports raw readout rows for downstream processing or troubleshooting. */
    @Throws(IOException::class)
    suspend fun exportReadoutData(outStream: OutputStream, readoutData: List<ResultData>) {
        val writer = outStream.bufferedWriter()
        withContext(Dispatchers.IO) {
            for (rd in readoutData) {
                writer.write(rd.toReadoutCSVString())
                writer.newLine()
            }
            writer.flush()
        }
    }

    /** Placeholder for final result CSV export, which is currently not implemented. */
    @Throws(IOException::class)
    suspend fun exportResults(outStream: OutputStream, results: List<ResultWrapper>) {
        val writer = outStream.bufferedWriter()
        withContext(Dispatchers.IO) {
            for (res in results) {

                writer.newLine()
            }
            writer.flush()
        }
    }
}
