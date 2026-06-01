package org.openardf.radiooracle.backend.files.processors

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.github.doyaaaaaken.kotlincsv.client.CsvReader
import com.github.doyaaaaaken.kotlincsv.dsl.context.CsvReaderContext
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.files.constants.DataFormat
import org.openardf.radiooracle.backend.files.constants.DataType
import org.openardf.radiooracle.backend.files.constants.FileConstants
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
import org.openardf.radiooracle.backend.sportident.SIConstants
import org.openardf.radiooracle.backend.wrappers.ResultWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
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


    /** Creates a CSV reader configured for the app's semicolon-delimited files. */
    private fun getReader(): CsvReader {
        val context = CsvReaderContext()
        context.delimiter = EventCsvFormat.DELIMITER
        return CsvReader(context)
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
                    !row.followsRacePresets,
                    if (row.followsRacePresets) race.raceType else row.raceType,
                    if (row.followsRacePresets) race.raceBand else row.raceBand,
                    if (row.followsRacePresets) race.timeLimit else row.timeLimitMinutes?.let(Duration::ofMinutes),
                    ""
                )

                val controlPoints = ControlPointsHelper.getControlPointsFromString(
                    row.controlPointsText,
                    category.id,
                    category.raceType ?: race.raceType,
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

        val csvReader = getReader().readAll(inStream)
        val competitors = ArrayList<CompetitorCategory>()
        var currOrder =
            dataProcessor.getHighestCategoryOrder(race.id) + 1 // Preserve category order when new categories are created.
        var currStartNum = dataProcessor.getHighestStartNumberByRace(race.id) + 1
        val invalidLines = ArrayList<Pair<Int, String>>()

        for (csvRow in csvReader.withIndex()) {
            try {
                val row = csvRow.value
                var category: CategoryData? = null

                // Reuse existing categories by name, or create lightweight placeholders for new names.
                if (row[4].isNotEmpty()) {
                    val catName = row[4].trim()
                    val origCat = categories.find { it.category.name == catName }
                    if (origCat != null) {
                        category = origCat
                    } else {
                        category = CategoryData(
                            Category(
                                UUID.randomUUID(),
                                race.id,
                                row[4].trim(),
                                false,
                                null,
                                0,
                                0,
                                currOrder,
                                false,
                                null,
                                null,
                                null,
                                ""
                            ), emptyList(), emptyList()
                        )
                        currOrder++
                        categories.add(category)
                    }
                }

                val categoryId = category?.category?.id
                var startNumber = currStartNum

                if (row[1].isNotEmpty()) {
                    startNumber = row[1].trim().toInt()
                } else {
                    currStartNum++
                }
                val firstName = row[2].trim()
                val lastName = row[3].trim()
                val isMan = row[5].trim().toIntOrNull() == 0
                val birthYear = if (row.size > 6) row[6].trim().toIntOrNull() else null
                val club = if (row.size > 7) row[7].trim() else ""
                val index = if (row.size > 8) row[8].trim() else ""
                val siNumber = row[0].trim().toIntOrNull()

                // Validate SI numbers here because invalid numbers cannot be fixed during persistence.
                if (siNumber != null && !SIConstants.isSINumberValid(siNumber)) {
                    throw IllegalArgumentException(
                        context.getString(
                            R.string.data_import_competitor_invalid_si,
                            csvRow.index
                        )
                    )
                }

                val drawnRelativeStartTime: Duration? =
                    if (row.size > 9 && row[9].isNotEmpty()) {
                        TimeProcessor.minuteStringToDuration(row[9].trim())
                    } else null

                // Competitor names are required by the UI and export paths.
                if (firstName.isEmpty() || lastName.isEmpty()) {
                    throw IllegalArgumentException(
                        context.getString(
                            R.string.data_import_competitor_blank_name,
                            csvRow.index
                        )
                    )
                }

                val siRent = if (row.size > 10) {
                    row[10].trim().toInt() == 1
                } else false

                val competitor = Competitor(
                    UUID.randomUUID(),
                    race.id,
                    categoryId,
                    firstName,
                    lastName,
                    club,
                    index,
                    isMan,
                    birthYear,
                    siNumber,
                    siRent,
                    startNumber,
                    drawnRelativeStartTime
                )
                if (category != null) {
                    competitors.add(CompetitorCategory(competitor, category.category))
                }
            } catch (e: Exception) {
                Log.w(
                    "CSV import",
                    "Failed to import competitor \n\" " + e.stackTraceToString()
                )

                invalidLines.add(Pair(csvRow.index, e.message ?: ""))
            }
        }
        return DataImportWrapper(competitors, categories.toList(), invalidLines)
    }

    /** Imports start-list rows and updates matched competitors by start number. */
    private fun importCompetitorStarts(
        inStream: InputStream,
        competitors: HashSet<CompetitorData>,

        context: Context
    ): DataImportWrapper {
        val csvReader = getReader().readAll(inStream)
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        val preferAppStartTime =
            sharedPref.getBoolean(
                context.getString(R.string.key_files_prefer_app_start_time),
                false
            )
        val invalidLines = ArrayList<Pair<Int, String>>()

        for (csvRow in csvReader.withIndex()) {
            val row = csvRow.value
            if (row.size == FileConstants.OCM_START_CSV_COLUMNS) {
                try {
                    val startNumber = row[0].trim().toInt()
                    val relativeTime = TimeProcessor.minuteStringToDuration(row[1].trim())
                    val siNumber = row[2].trim().toIntOrNull()

                    // Validate SI numbers before attaching them to existing competitors.
                    if (siNumber != null && !SIConstants.isSINumberValid(siNumber)) {
                        throw IllegalArgumentException(
                            context.getString(
                                R.string.data_import_competitor_invalid_si,
                                csvRow.index
                            )
                        )
                    }

                    val match =
                        competitors.find { it.competitorCategory.competitor.startNumber == startNumber }

                    if (match != null && !preferAppStartTime) {
                        match.competitorCategory.competitor.drawnRelativeStartTime =
                            relativeTime

                        if (siNumber != null) {
                            match.competitorCategory.competitor.siNumber = siNumber
                        }
                    }
                } catch (e: Exception) {
                    Log.e(
                        "CSV import",
                        "Failed to import competitor start: \n" + e.stackTraceToString()
                    )
                    invalidLines.add(Pair(csvRow.index, e.message ?: ""))
                }
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
