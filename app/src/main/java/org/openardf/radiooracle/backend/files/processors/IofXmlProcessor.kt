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
import androidx.preference.PreferenceManager
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.files.constants.DataFormat
import org.openardf.radiooracle.backend.files.constants.DataType
import org.openardf.radiooracle.backend.files.wrappers.DataImportWrapper
import org.openardf.radiooracle.backend.room.entity.Category
import org.openardf.radiooracle.backend.room.entity.Competitor
import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorCategory
import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorData
import org.openardf.radiooracle.backend.results.ResultsProcessor
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.room.entity.embeddeds.CategoryData
import org.openardf.radiooracle.backend.room.entity.embeddeds.RaceData
import org.openardf.radiooracle.backend.shared.toEventCategoryData
import org.openardf.radiooracle.backend.shared.toEventCompetitorData
import org.openardf.radiooracle.backend.shared.toEventRace
import org.openardf.radiooracle.backend.shared.toEventRaceData
import org.openardf.radiooracle.backend.shared.toRoomReadoutData
import org.openardf.radiooracle.backend.shared.toRoomCategoryDataPreservingControlOrder
import org.openardf.radiooracle.backend.wrappers.ResultWrapper
import kotlinx.coroutines.flow.first
import org.openardf.radiooracle.shared.event.EventProjectEditor
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.StandardCategoryRules
import org.openardf.radiooracle.shared.files.CompetitorCsvImportRow
import org.openardf.radiooracle.shared.files.IofXmlCompetitorMatchIssue
import org.openardf.radiooracle.shared.files.IofXmlImportMatcher
import org.openardf.radiooracle.shared.files.IofXmlImports
import org.openardf.radiooracle.shared.files.IofXmlExports
import java.io.InputStream
import java.io.OutputStream
import java.time.Duration
import java.util.UUID
import kotlin.collections.emptyList

/** Import/export processor for IOF XML interoperability. */
object IofXmlProcessor : FormatProcessor {

    /** Imports IOF XML data types currently supported by the app. */
    override suspend fun importData(
        inStream: InputStream,
        dataType: DataType,
        race: Race,
        dataProcessor: DataProcessor
    ): DataImportWrapper =
        importDataInternal(
            inStream = inStream,
            dataType = dataType,
            race = race,
            dataProcessor = dataProcessor,
            iofSchema = null
        )

    /** Imports IOF XML after validating the input against the supplied IOF 3.0 schema text. */
    suspend fun importDataValidated(
        inStream: InputStream,
        dataType: DataType,
        race: Race,
        dataProcessor: DataProcessor,
        iofSchema: String
    ): DataImportWrapper =
        importDataInternal(
            inStream = inStream,
            dataType = dataType,
            race = race,
            dataProcessor = dataProcessor,
            iofSchema = iofSchema
        )

    private suspend fun importDataInternal(
        inStream: InputStream,
        dataType: DataType,
        race: Race,
        dataProcessor: DataProcessor,
        iofSchema: String?
    ): DataImportWrapper {
        val context = dataProcessor.getContext()

        if (context != null) {
            val schema = iofSchema ?: context.assets.open(IOF_SCHEMA_ASSET_PATH)
                .bufferedReader()
                .use { it.readText() }
            return when (dataType) {
                DataType.CATEGORIES -> importCategories(
                    inStream,
                    race,
                    context,
                    schema
                )

                DataType.COMPETITOR_STARTS -> importStartList(
                    inStream,
                    race,
                    dataProcessor,
                    context,
                    schema
                )

                DataType.RESULTS_LIVE -> importResultList(
                    inStream,
                    race,
                    dataProcessor,
                    schema
                )

                DataType.COMPETITORS -> importEntryList(
                    inStream,
                    race,
                    dataProcessor,
                    schema
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
        val xml = inStream.readBytes().toString(Charsets.UTF_8)
        val parsed = IofXmlImports.entryList(xml)
        return entryRowsToImportWrapper(
            rows = parsed.parsedData.entries,
            race = race,
            categories = categories,
            nextCategoryOrder = (categories.maxOfOrNull { it.category.order } ?: -1) + 1,
            nextStartNumber = 1,
            iofWarnings = parsed.unsupportedItems.map { "${it.location}: ${it.reason}" }
        )
    }

    /** Imports IOF XML course/category data into category aggregates. */
    fun importCategories(
        inStream: InputStream,
        race: Race,
        context: Context,
        iofSchema: String? = null
    ): DataImportWrapper {
        val xml = inStream.readBytes().toString(Charsets.UTF_8)
        val sharedRace = race.toEventRace()
        val result = iofSchema?.let { schema ->
            IofXmlImports.validatedCourseData(xml, schema, sharedRace)
        } ?: IofXmlImports.courseData(xml, sharedRace)
        val cats = result.parsedData.categories.map { it.toRoomCategoryDataPreservingControlOrder() }
        return DataImportWrapper(
            competitorCategories = emptyList(),
            categories = cats,
            invalidLines = arrayListOf(),
            iofWarnings = result.unsupportedItems.map { "${it.location}: ${it.reason}" }
        )
    }

    /** Imports an IOF EntryList into Android competitor aggregates. */
    private suspend fun importEntryList(
        inStream: InputStream,
        race: Race,
        dataProcessor: DataProcessor,
        iofSchema: String?
    ): DataImportWrapper {
        val xml = inStream.readBytes().toString(Charsets.UTF_8)
        val parsed = iofSchema?.let { schema ->
            IofXmlImports.validatedEntryList(xml, schema)
        } ?: IofXmlImports.entryList(xml)
        return entryRowsToImportWrapper(
            rows = parsed.parsedData.entries,
            race = race,
            categories = dataProcessor.getCategoryDataForRace(race.id).toHashSet(),
            nextCategoryOrder = dataProcessor.getHighestCategoryOrder(race.id) + 1,
            nextStartNumber = dataProcessor.getHighestStartNumberByRace(race.id) + 1,
            iofWarnings = parsed.unsupportedItems.map { "${it.location}: ${it.reason}" }
        )
    }

    private fun entryRowsToImportWrapper(
        rows: List<CompetitorCsvImportRow>,
        race: Race,
        categories: HashSet<CategoryData>,
        nextCategoryOrder: Int,
        nextStartNumber: Int,
        iofWarnings: List<String>
    ): DataImportWrapper {
        val competitors = ArrayList<CompetitorCategory>()
        val invalidLines = arrayListOf<Pair<Int, String>>()
        var currCategoryOrder = nextCategoryOrder
        var currStartNumber = nextStartNumber

        for ((index, row) in rows.withIndex()) {
            try {
                val category = findOrCreateEntryListCategory(row, race, categories, currCategoryOrder)
                if (category != null && categories.none { it.category.id == category.category.id }) {
                    currCategoryOrder++
                    categories.add(category)
                }

                val competitor = Competitor(
                    UUID.randomUUID(),
                    race.id,
                    category?.category?.id,
                    row.firstName,
                    row.lastName,
                    row.club,
                    row.personId,
                    row.isMan,
                    row.birthYear,
                    row.siNumber,
                    row.siRent,
                    currStartNumber++,
                    null,
                    row.bibNumber
                )
                competitors += CompetitorCategory(competitor, category?.category)
            } catch (ex: Exception) {
                invalidLines += (index + 1) to (ex.message ?: "Invalid IOF EntryList row.")
            }
        }

        return DataImportWrapper(
            competitorCategories = competitors,
            categories = categories.toList(),
            invalidLines = invalidLines,
            iofWarnings = iofWarnings
        )
    }

    private fun findOrCreateEntryListCategory(
        row: CompetitorCsvImportRow,
        race: Race,
        categories: HashSet<CategoryData>,
        order: Int
    ): CategoryData? {
        if (row.categoryName.isBlank()) return null
        categories.find { it.category.name == row.categoryName }?.let { return it }
        return CategoryData(
            Category(
                UUID.randomUUID(),
                race.id,
                row.categoryName,
                StandardCategoryRules.inferIsManFromName(row.categoryName) ?: row.isMan,
                null,
                0,
                0,
                order,
                false,
                null,
                null,
                null,
                ""
            ),
            emptyList(),
            emptyList()
        )
    }

    /** Imports an IOF StartList by matching rows to existing competitors and previewing start updates. */
    private suspend fun importStartList(
        inStream: InputStream,
        race: Race,
        dataProcessor: DataProcessor,
        context: Context,
        iofSchema: String?
    ): DataImportWrapper {
        val xml = inStream.readBytes().toString(Charsets.UTF_8)
        val parsed = iofSchema?.let { schema ->
            IofXmlImports.validatedStartList(xml, schema)
        } ?: IofXmlImports.startList(xml)
        val raceData = dataProcessor.getRaceData(race.id)
        val sharedRaceData = raceData.toEventRaceData()
        val matched = IofXmlImportMatcher.matchStartList(parsed.parsedData, sharedRaceData)
        val competitorsById = raceData.competitorData
            .associateBy { it.competitorCategory.competitor.id.toString() }
        val invalidLines = arrayListOf<Pair<Int, String>>()
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val preferAppStartTime =
            sharedPreferences.getBoolean(
                context.getString(R.string.key_files_prefer_app_start_time),
                false
            )

        matched.entries.forEachIndexed { index, matchedEntry ->
            val fatalIssues = matchedEntry.match.issues.filterNot {
                it == IofXmlCompetitorMatchIssue.MISSING_CONTROL_CARD
            }
            val relativeStartSeconds = matchedEntry.entry.relativeStartTimeSeconds
            val competitorData = matchedEntry.match.competitorId?.let(competitorsById::get)
            when {
                fatalIssues.isNotEmpty() -> invalidLines += (index + 1) to fatalIssues.joinToString { it.androidMessage() }
                relativeStartSeconds == null -> invalidLines += (index + 1) to "Start time missing or invalid."
                competitorData == null -> invalidLines += (index + 1) to IofXmlCompetitorMatchIssue.UNKNOWN_COMPETITOR.androidMessage()
                !preferAppStartTime -> {
                    competitorData.competitorCategory.competitor.drawnRelativeStartTime =
                        Duration.ofSeconds(relativeStartSeconds)
                    matchedEntry.entry.controlCard?.let { controlCard ->
                        competitorData.competitorCategory.competitor.siNumber = controlCard
                    }
                }
            }
        }

        val warnings = parsed.unsupportedItems.map { "${it.location}: ${it.reason}" } +
            matched.entries.flatMapIndexed { index, matchedEntry ->
                if (matchedEntry.match.issues.contains(IofXmlCompetitorMatchIssue.MISSING_CONTROL_CARD)) {
                    listOf("StartList row ${index + 1}: ${IofXmlCompetitorMatchIssue.MISSING_CONTROL_CARD.androidMessage()}")
                } else {
                    emptyList()
                }
            }

        return DataImportWrapper(
            competitorCategories = raceData.competitorData.map { it.competitorCategory },
            categories = emptyList(),
            invalidLines = invalidLines,
            iofWarnings = warnings
        )
    }

    /** Imports an IOF ResultList by matching person results to existing competitors. */
    private suspend fun importResultList(
        inStream: InputStream,
        race: Race,
        dataProcessor: DataProcessor,
        iofSchema: String?
    ): DataImportWrapper {
        val xml = inStream.readBytes().toString(Charsets.UTF_8)
        val parsed = iofSchema?.let { schema ->
            IofXmlImports.validatedResultList(xml, schema)
        } ?: IofXmlImports.resultList(xml)
        val raceData = dataProcessor.getRaceData(race.id)
        val outcome = EventProjectEditor.importIofResultList(
            projectFile = EventProjectFile(raceData = raceData.toEventRaceData()),
            preview = parsed.parsedData,
            resultIdFactory = { "iof-result-${UUID.randomUUID()}" },
            punchIdFactory = { resultId, index, type -> "$resultId-punch-$index-${type.name}" }
        )
        return DataImportWrapper(
            competitorCategories = emptyList(),
            categories = emptyList(),
            invalidLines = arrayListOf(),
            readoutData = outcome.importedReadouts.map { it.toRoomReadoutData() },
            iofWarnings = parsed.unsupportedItems.map { "${it.location}: ${it.reason}" } + outcome.warnings
        )
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
        try {
            outStream.write(
                IofXmlExports.startList(
                    raceData = raceDataForStartList(race, data),
                    creator = "Radio-Oracle ${dataProcessor.getAppVersion()}"
                ).toByteArray(Charsets.UTF_8)
            )
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
        try {
            outStream.write(
                IofXmlExports.resultList(
                    raceData = raceDataForResults(race, results),
                    creator = "Radio-Oracle ${dataProcessor.getAppVersion()}"
                ).toByteArray(Charsets.UTF_8)
            )
        } catch (ex: Exception) {
            throw RuntimeException("Failed to export IOF XML: ${ex.message}", ex)
        }
    }

    private fun raceDataForStartList(race: Race, categories: List<CategoryData>) =
        RaceData(
            race = race,
            categories = categories,
            aliases = emptyList(),
            competitorData = categories.flatMap { categoryData ->
                categoryData.competitors.map { competitor ->
                    CompetitorData(
                        competitorCategory = CompetitorCategory(competitor, categoryData.category),
                        readoutData = null
                    )
                }
            },
            unmatchedReadoutData = emptyList()
        ).toEventRaceData()

    private fun raceDataForResults(race: Race, results: List<ResultWrapper>) =
        org.openardf.radiooracle.shared.event.EventRaceData(
            race = race.toEventRace(),
            categories = results.mapNotNull { wrapper ->
                wrapper.category?.let { category ->
                    CategoryData(
                        category = category,
                        controlPoints = emptyList(),
                        competitors = wrapper.competitorData.map { it.competitorCategory.competitor }
                    ).toEventCategoryData()
                }
            },
            aliases = emptyList(),
            competitorData = results.flatMap { wrapper ->
                wrapper.competitorData.map { competitorData ->
                    val category = competitorData.competitorCategory.category ?: wrapper.category
                    competitorData.copy(
                        competitorCategory = CompetitorCategory(
                            competitor = competitorData.competitorCategory.competitor,
                            category = category ?: Category("")
                        )
                    ).toEventCompetitorData()
                }
            },
            unmatchedReadoutData = emptyList()
        )

    private fun IofXmlCompetitorMatchIssue.androidMessage(): String =
        when (this) {
            IofXmlCompetitorMatchIssue.UNKNOWN_CLASS -> "Class not found in this event."
            IofXmlCompetitorMatchIssue.MISSING_CONTROL_CARD -> "Control card missing; matched by another identifier."
            IofXmlCompetitorMatchIssue.UNKNOWN_COMPETITOR -> "Competitor not found in this event."
            IofXmlCompetitorMatchIssue.DUPLICATE_MATCH -> "Multiple competitors match this IOF row."
        }

    private const val IOF_SCHEMA_ASSET_PATH = "iof/IOF.xsd"
}
