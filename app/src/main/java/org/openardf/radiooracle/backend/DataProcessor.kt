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

package org.openardf.radiooracle.backend

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import androidx.preference.PreferenceManager
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.files.DataImportValidator
import org.openardf.radiooracle.backend.files.AndroidEventSeriesImport
import org.openardf.radiooracle.backend.files.DesktopFileTransferUpload
import org.openardf.radiooracle.backend.files.EventSeriesExport
import org.openardf.radiooracle.backend.files.EventSeriesImport
import org.openardf.radiooracle.backend.files.EventFileTransferUploads
import org.openardf.radiooracle.backend.files.FileProcessor
import org.openardf.radiooracle.backend.files.processors.JsonProcessor
import org.openardf.radiooracle.backend.files.wrappers.DataImportWrapper
import org.openardf.radiooracle.backend.helpers.ControlPointsHelper
import org.openardf.radiooracle.backend.logging.DebugLog
import org.openardf.radiooracle.backend.network.ProviderClient
import org.openardf.radiooracle.backend.prints.PrintAttemptResult
import org.openardf.radiooracle.backend.prints.PrintProcessor
import org.openardf.radiooracle.backend.results.ResultsProcessor
import org.openardf.radiooracle.backend.results.ResultsProcessor.updateResultsForCategory
import org.openardf.radiooracle.backend.results.ResultsProcessor.updateResultsForCompetitor
import org.openardf.radiooracle.backend.room.ARDFRepository
import org.openardf.radiooracle.backend.series.EventSeriesMemberships
import org.openardf.radiooracle.backend.room.entity.Alias
import org.openardf.radiooracle.backend.room.entity.Category
import org.openardf.radiooracle.backend.room.entity.Competitor
import org.openardf.radiooracle.backend.room.entity.ControlPoint
import org.openardf.radiooracle.backend.room.entity.EventSeries
import org.openardf.radiooracle.backend.room.entity.EventSeriesMember
import org.openardf.radiooracle.backend.room.entity.Punch
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.room.entity.Result
import org.openardf.radiooracle.backend.room.entity.ResultService
import org.openardf.radiooracle.backend.room.entity.embeddeds.CategoryData
import org.openardf.radiooracle.backend.room.entity.embeddeds.EventSeriesData
import org.openardf.radiooracle.backend.room.entity.embeddeds.RaceData
import org.openardf.radiooracle.backend.room.entity.embeddeds.ReadoutData
import org.openardf.radiooracle.backend.room.entity.embeddeds.ResultData
import org.openardf.radiooracle.backend.room.withFreshImportIds
import org.openardf.radiooracle.backend.sportident.EventSeriesReadoutMemberData
import org.openardf.radiooracle.backend.sportident.EventSeriesReadoutRoute
import org.openardf.radiooracle.backend.sportident.EventSeriesReadoutRouter
import org.openardf.radiooracle.backend.sportident.SIPort.CardData
import org.openardf.radiooracle.backend.sportident.SIReaderService
import org.openardf.radiooracle.backend.shared.toEventRaceData
import org.openardf.radiooracle.backend.wrappers.ResultWrapper
import org.openardf.radiooracle.backend.wrappers.StatisticsWrapper
import org.openardf.radiooracle.shared.device.SIReaderState
import org.openardf.radiooracle.shared.device.SIReaderStatus
import org.openardf.radiooracle.shared.domain.ProviderType
import org.openardf.radiooracle.shared.domain.PunchStatus
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.domain.ResultServiceStatus
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.StandardCategoryType
import org.openardf.radiooracle.shared.files.DataFormat
import org.openardf.radiooracle.shared.files.DataType
import org.openardf.radiooracle.shared.event.PracticeCompetitorCategoryAssignment
import org.openardf.radiooracle.shared.event.toDisplayLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.lang.ref.WeakReference
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID


/**
 * This is the main backend interface, processing and providing various sources of data
 */
class DataProcessor private constructor(context: Context) {

    private val ardfRepository = ARDFRepository.get()
    private var appContext: WeakReference<Context> = WeakReference(context)

    var currentState = MutableLiveData<AppState>()
    var fileProcessor: FileProcessor? = null
    var printProcessor = PrintProcessor(context, this)
    private val _raceSelectionRequests = MutableSharedFlow<UUID>(extraBufferCapacity = 1)
    val raceSelectionRequests: SharedFlow<UUID> = _raceSelectionRequests.asSharedFlow()

    companion object {
        private var INSTANCE: DataProcessor? = null
        fun initialize(context: Context) {
            if (INSTANCE == null) {
                INSTANCE = DataProcessor(context)
            }
        }

        fun get(): DataProcessor {
            return INSTANCE ?: throw IllegalStateException("DataProcessor must be initialized")
        }

        fun resetForTests() {
            INSTANCE = null
        }
    }

    init {
        currentState.postValue(
            AppState(null, SIReaderState(SIReaderStatus.DISCONNECTED))
        )
    }

    fun getContext(): Context? = appContext.get()

    fun getAppVersion(): String {
        val packageInfo =
            appContext.get()!!.packageManager.getPackageInfo(appContext.get()!!.packageName, 0)
        return packageInfo.versionName ?: "Unknown Version"
    }

    @SuppressLint("NullSafeMutableLiveData")
    fun updateReaderState(newSIState: SIReaderState) {
        val stateToUpdate = currentState.value

        if (stateToUpdate != null) {
            stateToUpdate.siReaderState = newSIState
            currentState.postValue(stateToUpdate)
        }
    }

    suspend fun setCurrentRace(raceId: UUID): Race? {
        val race = getRace(raceId)
        race?.let { race ->
            currentState.postValue(currentState.value?.let { AppState(race, it.siReaderState) })
            return race
        }
        return null
    }

    fun removeCurrentRace() {
        currentState.postValue(currentState.value?.let { AppState(null, it.siReaderState, null) })
    }

    //METHODS TO HANDLE RACES
    fun getRaces(): Flow<List<Race>> = ardfRepository.getRaces()

    suspend fun getRace(id: UUID) = ardfRepository.getRace(id)

    suspend fun createRace(race: Race) {
        ardfRepository.createRace(race)
        DebugLog.info("Races", "Created race=${race.id} name=${race.name}")
    }

    suspend fun updateRace(race: Race) {
        ardfRepository.updateRace(race)
        updateResultsByRace(race.id)
        DebugLog.info("Races", "Updated race=${race.id} name=${race.name}")
    }

    suspend fun deleteRace(id: UUID) {
        val race = getRace(id)
        ardfRepository.deleteRace(id)
        DebugLog.info("Races", "Deleted race=$id name=${race?.name ?: "unknown"}")
    }

    //CATEGORIES
    fun getCategoryDataFlowForRace(raceId: UUID) =
        ardfRepository.getCategoryDataFlowForRace(raceId)

    suspend fun getCategory(id: UUID): Category? = ardfRepository.getCategory(id)

    suspend fun getCategoriesForRace(raceId: UUID) = ardfRepository.getCategoriesForRace(raceId)

    suspend fun getCategoryData(id: UUID): CategoryData? {
        return ardfRepository.getCategoryData(id)
    }

    suspend fun getCategoryDataForRace(raceId: UUID): List<CategoryData> =
        ardfRepository.getCategoryDataForRace(raceId)


    suspend fun getCategoryByName(string: String, raceId: UUID): Category? =
        ardfRepository.getCategoryByName(string, raceId)


    suspend fun getStartTimeForCategory(categoryId: UUID): Duration? {
        val competitors = ardfRepository.getCompetitorsByCategory(categoryId)
            .sortedBy { it.drawnRelativeStartTime }

        return if (competitors.isNotEmpty()) {
            competitors.first().drawnRelativeStartTime
        } else null
    }

    suspend fun getHighestCategoryOrder(raceId: UUID) =
        ardfRepository.getHighestCategoryOrder(raceId)

    suspend fun createOrUpdateCategory(category: Category, controlPoints: List<ControlPoint>?) {
        // Update the control points string
        controlPoints?.let {
            category.controlPointsString = ControlPointsHelper.getStringFromControlPoints(it)
        }
        ardfRepository.createOrUpdateCategory(category, controlPoints)
        getRace(category.raceId)?.let { race -> updateResultsForCategory(category.id, race, this) }
    }

    /**
     * Creates a duplicate of the given category with a suffix "Copy" (or translated)
     * The control points are duplicated as well
     */
    suspend fun duplicateCategory(categoryData: CategoryData) {
        categoryData.category.name += "_" + (appContext.get()?.getString(R.string.general_copy)
            ?: "_Copy")
        categoryData.category.order = getHighestCategoryOrder(categoryData.category.raceId) + 1

        //Adjust the IDs
        categoryData.category.id = UUID.randomUUID()
        for (cp in categoryData.controlPoints) {
            cp.id = UUID.randomUUID()
            cp.categoryId = categoryData.category.id
        }

        createOrUpdateCategory(categoryData.category, categoryData.controlPoints)
    }

    suspend fun createStandardCategories(type: StandardCategoryType, raceId: UUID) {
        val race = getRace(raceId)
        race?.let { race ->
            val categories = fileProcessor?.importStandardCategories(type, race)
            if (categories != null) {
                for (cat in categories) {
                    ardfRepository.createCategory(cat)
                }
            }
        }
    }

    suspend fun deleteCategory(categoryId: UUID, raceId: UUID, deleteCompetitors: Boolean) {
        val retainedCompetitors = if (deleteCompetitors) {
            emptyList()
        } else {
            ardfRepository.getCompetitorsByCategory(categoryId)
        }

        if (deleteCompetitors) {
            ardfRepository.deleteCompetitorsByCategory(categoryId)
        } else {
            ardfRepository.clearCompetitorCategory(categoryId)
        }

        ardfRepository.deleteCategory(categoryId)
        ardfRepository.deleteControlPointsByCategory(categoryId)
        val race = getRace(raceId)
        race?.let {
            // Retained competitors are now uncategorized, so update them from the captured list.
            retainedCompetitors.forEach { competitor ->
                updateResultsForCompetitor(competitor.id, it, this)
            }
        }
        updateCategoryOrder(raceId)
    }

    //Updates category order after one is deleted - starts at 0
    private suspend fun updateCategoryOrder(raceId: UUID) {
        val categories = ardfRepository.getCategoriesForRace(raceId)
        for (c in categories.withIndex()) {
            c.value.order = c.index
            ardfRepository.createOrUpdateCategory(c.value, null)
        }
    }

    //CONTROL POINTS
    suspend fun getControlPointsByCategory(categoryId: UUID) =
        ardfRepository.getControlPointsByCategory(categoryId)

    //ALIASES
    suspend fun getAliasesByRace(raceId: UUID) = ardfRepository.getAliasesByRace(raceId)

    suspend fun getControlPointAliasesByCategory(categoryId: UUID) =
        ardfRepository.getControlPointAliasesByCategory(categoryId)

    suspend fun createOrUpdateAliases(aliases: List<Alias>, raceId: UUID) {
        ardfRepository.deleteAliasesByRace(raceId)
        for (alias in aliases) {
            ardfRepository.createOrUpdateAlias(alias)
        }
    }

    //COMPETITORS
    fun getCompetitorDataFlowByRace(raceId: UUID) =
        ardfRepository.getCompetitorDataFlowByRace(raceId)

    suspend fun getCompetitor(id: UUID) = ardfRepository.getCompetitor(id)

    suspend fun getCompetitorBySINumber(siNumber: Int, raceId: UUID): Competitor? =
        ardfRepository.getCompetitorBySINumber(siNumber, raceId)

    suspend fun getCompetitorsByCategory(categoryId: UUID): List<Competitor> =
        ardfRepository.getCompetitorsByCategory(categoryId)

    suspend fun getStatisticsByRace(raceId: UUID): StatisticsWrapper {
        val competitors = ardfRepository.getCompetitorDataFlowByRace(raceId).first()
        return ResultsProcessor.run { competitors.toReadoutStatistics() }
    }

    fun checkIfSINumberExists(siNumber: Int, raceId: UUID): Boolean {
        return runBlocking {
            return@runBlocking ardfRepository.checkIfSINumberExists(siNumber, raceId) > 0
        }
    }

    suspend fun getHighestStartNumberByRace(raceId: UUID) =
        ardfRepository.getHighestStartNumberByRace(raceId)

    suspend fun createOrUpdateCompetitor(
        competitor: Competitor,
    ) {
        ardfRepository.createCompetitor(competitor)
        getRace(competitor.raceId)?.let { race ->
            ResultsProcessor.updateResultsForCompetitor(competitor.id, race, this)
        }
    }

    suspend fun deleteCompetitor(id: UUID, deleteResult: Boolean) {
        if (deleteResult) {
            ardfRepository.deleteResultForCompetitor(id)
        }
        ardfRepository.deleteCompetitor(id)
    }

    suspend fun deleteAllCompetitorsByRace(raceId: UUID) {
        ardfRepository.deleteAllCompetitorsByRace(raceId)
    }

    //RESULTS_LIVE
    suspend fun getResult(id: UUID) = ardfRepository.getResult(id)

    suspend fun getResultData(resultId: UUID) = ardfRepository.getResultData(resultId)

    fun getResultDataFlowByRace(raceId: UUID) = ardfRepository.getResultDataFlowByRace(raceId)

    suspend fun getResultByCompetitor(competitorId: UUID) =
        ardfRepository.getResultByCompetitor(competitorId)

    suspend fun getResultBySINumber(siNumber: Int, raceId: UUID) =
        ardfRepository.getResultBySINumber(siNumber, raceId)

    suspend fun saveResultPunches(result: Result, punches: List<Punch>) {
        result.sent = false     // Mark as unsent
        ardfRepository.saveResultPunches(result, punches)
    }

    suspend fun createOrUpdateResult(result: Result) =
        ardfRepository.createOrUpdateResult(result)

    /**
     *     Recalculates all results in a race
     *     Since race edit could mean a change in start time 00, results for each competitor need to be recalculated
     */
    suspend fun updateResultsByRace(raceId: UUID) {
        getRace(raceId)?.let { race ->
            getCategoriesForRace(raceId).forEach { category ->
                updateResultsForCategory(category.id, race, this)

            }
            ardfRepository.getUnmatchedCompetitorsByRace(raceId)
                .forEach { comp -> updateResultsForCompetitor(comp.id, race, this) }
        }
    }

    /** Recalculates stored results after scoring-rule changes that affect existing race data. */
    suspend fun updateAllResults(reason: String) {
        val races = getRaces().first()
        races.forEach { race -> updateResultsByRace(race.id) }
        DebugLog.info("Results", "Recalculated stored results count=${races.size} reason=$reason")
    }

    suspend fun deleteResult(id: UUID) = ardfRepository.deleteResult(id)

    suspend fun deleteAllResultsByRace(raceId: UUID) {
        ardfRepository.deleteAllResultsByRace(raceId)
    }

    //EVENT SERIES
    fun getEventSeries(): Flow<List<EventSeriesData>> =
        ardfRepository.getEventSeries().map { seriesList ->
            seriesList.map { seriesData -> canonicalEventSeriesData(seriesData) }
        }

    suspend fun getEventSeries(seriesId: String) =
        ardfRepository.getEventSeries(seriesId)?.let { canonicalEventSeriesData(it) }

    suspend fun getEventSeriesForRace(raceId: UUID) =
        ardfRepository.getEventSeriesForRace(raceId)?.let { canonicalEventSeriesData(it) }

    private suspend fun canonicalEventSeriesData(seriesData: EventSeriesData): EventSeriesData =
        seriesData.copy(
            members = seriesData.members.map { member ->
                getRace(member.localRaceId)?.let { race ->
                    member.copy(
                        displayName = race.name,
                        startDateTimeIso = race.startDateTime.toString(),
                        formatLabel = race.raceType.toDisplayLabel()
                    )
                } ?: member
            }
        )

    suspend fun getSeriesResultWrapperFlowForRace(raceId: UUID): Flow<List<ResultWrapper>>? {
        val seriesData = getEventSeriesForRace(raceId) ?: return null
        val members = seriesData.orderedMembers()
        if (members.size < 2) {
            return null
        }
        val raceNameById = members.associate { member -> member.localRaceId to member.displayName }
        val memberFlows = members.map { member ->
            ResultsProcessor.getResultWrapperFlowByRace(member.localRaceId, this).map { wrappers ->
                wrappers.map { wrapper ->
                    val resultRaceId = wrapper.competitorData
                        .firstNotNullOfOrNull { it.readoutData?.result?.raceId }
                        ?: member.localRaceId
                    val raceName = raceNameById[resultRaceId]
                        ?: raceNameById.getValue(member.localRaceId)
                    wrapper.copy(displayLabel = seriesResultDisplayLabel(raceName, wrapper))
                }
            }
        }
        return combine(memberFlows) { groupedResults ->
            groupedResults.flatMap { it }
        }
    }

    suspend fun saveEventSeries(series: EventSeries, members: List<EventSeriesMember>) {
        ardfRepository.saveEventSeries(series, members)
        DebugLog.info(
            "Race Series",
            "Saved Android race series id=${series.seriesId} members=${members.size}"
        )
    }

    suspend fun createEventSeriesFromRace(raceId: UUID, seriesName: String): EventSeriesData {
        val race = getRace(raceId) ?: throw IllegalArgumentException("Race not found: $raceId")
        require(seriesName.isNotBlank()) {
            "Series name must not be blank."
        }
        require(getEventSeriesForRace(raceId) == null) {
            "Race is already part of a Race Series."
        }
        val series = EventSeries(seriesId = UUID.randomUUID().toString(), name = seriesName.trim())
        val member = EventSeriesMemberships.memberForRace(series.seriesId, race, eventOrder = 0)
        saveEventSeries(series, listOf(member))
        DebugLog.info("Race Series", "Created series id=${series.seriesId} race=$raceId")
        return getEventSeries(series.seriesId) ?: EventSeriesData(series, listOf(member))
    }

    suspend fun addRaceToEventSeries(raceId: UUID, seriesId: String): EventSeriesData {
        val race = getRace(raceId) ?: throw IllegalArgumentException("Race not found: $raceId")
        val seriesData = getEventSeries(seriesId) ?: throw IllegalArgumentException("Race Series not found: $seriesId")
        require(getEventSeriesForRace(raceId) == null) {
            "Race is already part of a Race Series."
        }
        saveEventSeries(seriesData.series, EventSeriesMemberships.appendRace(seriesData, race))
        DebugLog.info("Race Series", "Added race=$raceId to series=$seriesId")
        return getEventSeries(seriesId) ?: seriesData
    }

    suspend fun renameEventSeries(seriesId: String, seriesName: String): EventSeriesData {
        require(seriesName.isNotBlank()) {
            "Series name must not be blank."
        }
        val seriesData = getEventSeries(seriesId) ?: throw IllegalArgumentException("Race Series not found: $seriesId")
        val updatedSeries = seriesData.series.copy(name = seriesName.trim())
        saveEventSeries(updatedSeries, seriesData.members)
        DebugLog.info("Race Series", "Renamed series=$seriesId")
        return getEventSeries(seriesId) ?: seriesData.copy(series = updatedSeries)
    }

    suspend fun removeRaceFromEventSeries(raceId: UUID): EventSeriesData? {
        val seriesData = getEventSeriesForRace(raceId) ?: return null
        val remainingMembers = EventSeriesMemberships.removeRace(seriesData, raceId)
        if (remainingMembers.isEmpty()) {
            deleteEventSeries(seriesData.series.seriesId)
            DebugLog.info("Race Series", "Removed final race=$raceId from series=${seriesData.series.seriesId}")
            return null
        }
        saveEventSeries(seriesData.series, remainingMembers)
        DebugLog.info("Race Series", "Removed race=$raceId from series=${seriesData.series.seriesId}")
        return getEventSeries(seriesData.series.seriesId)
    }

    fun prepareEventSeriesImport(
        manifestJson: String,
        eventFileJsonByPath: Map<String, String>
    ): AndroidEventSeriesImport =
        EventSeriesImport.prepare(manifestJson, eventFileJsonByPath)

    suspend fun saveEventSeriesImport(eventSeriesImport: AndroidEventSeriesImport) {
        ardfRepository.saveEventSeriesImport(eventSeriesImport)
        DebugLog.info(
            "Race Series",
            "Saved Android race series import id=${eventSeriesImport.series.seriesId} " +
                "members=${eventSeriesImport.memberImports.size}"
        )
    }

    suspend fun deleteEventSeries(seriesId: String) {
        ardfRepository.deleteEventSeries(seriesId)
        DebugLog.info("Race Series", "Deleted Android race series id=$seriesId")
    }

    private fun seriesResultDisplayLabel(eventName: String, wrapper: ResultWrapper): String {
        val categoryName = wrapper.category?.name
            ?: getContext()?.getString(R.string.no_category)
            ?: "No category"
        return "$eventName - $categoryName"
    }

    // Return wherever the "mm:ss" format should be used
    fun useMinuteTimeFormat(): Boolean {
        val context = getContext()
        if (context != null) {
            val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
            val preference =
                sharedPref.getString(
                    context.getString(R.string.key_results_time_format),
                    context.getString(R.string.preferences_results_time_format_minutes)
                )
            return (preference == context.getString(R.string.preferences_results_time_format_minutes))
        }
        return true
    }

    //PUNCHES
    suspend fun getPunchesByResult(resultId: UUID) =
        ardfRepository.getPunchesByResult(resultId)

    private suspend fun createPunch(punch: Punch) = ardfRepository.createPunch(punch)

    suspend fun createPunches(punches: ArrayList<Punch>) {
        punches.forEach { punch -> createPunch(punch) }
    }

    suspend fun processCardData(cardData: CardData, race: Race) =
        appContext.get()?.let { ResultsProcessor.processCardData(cardData, race, it, this) }

    suspend fun processCardDataForCurrentRaceOrSeries(cardData: CardData, currentRace: Race): Boolean? {
        if (currentRace.raceLevel == RaceLevel.PRACTICE && cardData.isBlankPracticeStart()) {
            ensurePracticeCompetitorForCard(cardData, currentRace, startedInForest = true)
            DebugLog.info("SI", "Practice card placed in forest si=${cardData.siNumber} race=${currentRace.id}")
            return true
        }
        val seriesMembers = eventSeriesReadoutMembersForRace(currentRace.id)
        if (seriesMembers.size < 2) {
            return processCardData(cardData, currentRace)
        }

        return when (val route = EventSeriesReadoutRouter.route(cardData, seriesMembers)) {
            is EventSeriesReadoutRoute.Matched -> {
                val routedRace = route.memberData.raceData.race
                DebugLog.info(
                    "Race Series",
                    "Card read routed si=${cardData.siNumber} " +
                        "series=${route.memberData.member.seriesId} " +
                        "seriesRace=${route.memberData.member.seriesEventId} " +
                        "reason=${route.reason}"
                )
                val stored = processCardData(cardData, routedRace)
                if (stored == true && routedRace.id != currentRace.id) {
                    setCurrentRace(routedRace.id)
                    _raceSelectionRequests.tryEmit(routedRace.id)
                }
                stored
            }
            is EventSeriesReadoutRoute.Ambiguous -> {
                DebugLog.warn(
                    "Race Series",
                    "Card read ignored reason=ambiguous-series-route si=${cardData.siNumber} " +
                        "candidates=${route.candidates.joinToString { it.member.seriesEventId }} " +
                        "reason=${route.reason}"
                )
                showSiReadoutToast(R.string.si_card_ambiguous_series_route, cardData.siNumber)
                false
            }
            EventSeriesReadoutRoute.NoMatch -> {
                DebugLog.warn(
                    "Race Series",
                    "Card read ignored reason=no-series-route si=${cardData.siNumber}"
                )
                showSiReadoutToast(R.string.si_card_no_series_route, cardData.siNumber)
                false
            }
        }
    }

    suspend fun ensurePracticeCompetitorForCard(
        cardData: CardData,
        sourceRace: Race,
        startedInForest: Boolean = false
    ): Competitor? {
        if (sourceRace.raceLevel != RaceLevel.PRACTICE) {
            return getCompetitorBySINumber(cardData.siNumber, sourceRace.id)
        }
        val sourceRaceData = getRaceData(sourceRace.id).toEventRaceData()
        val sourceCategory = if (startedInForest) {
            PracticeCompetitorCategoryAssignment.longestCourseCategory(sourceRaceData)
        } else {
            PracticeCompetitorCategoryAssignment.mostLikelyCategory(
                sourceRaceData,
                cardData.punchData.map { it.siCode }
            )
        }
        val categoryName = sourceCategory?.category?.name
        val seriesMembers = getEventSeriesForRace(sourceRace.id)
            ?.orderedMembers()
            ?.takeIf { it.size >= 2 }
        val raceIds = seriesMembers?.map { it.localRaceId } ?: listOf(sourceRace.id)
        val cardName = cardData.cardName?.trim().orEmpty()
        val nameParts = cardName.split(Regex("\\s+"), limit = 2).filter { it.isNotBlank() }

        raceIds.forEach { raceId ->
            val race = getRace(raceId) ?: return@forEach
            if (race.raceLevel != RaceLevel.PRACTICE) {
                return@forEach
            }
            val existing = getCompetitorBySINumber(cardData.siNumber, raceId)
            val existingResult = existing?.let { getResultByCompetitor(it.id) }
            val startTime = if (startedInForest) {
                Duration.ofSeconds(
                    Duration.between(race.startDateTime, LocalDateTime.now()).seconds.coerceAtLeast(0L)
                )
            } else {
                null
            }
            val eventRaceData = getRaceData(raceId).toEventRaceData()
            val categoryData = categoryName?.let {
                PracticeCompetitorCategoryAssignment.categoryNamedLike(eventRaceData, it)
            } ?: PracticeCompetitorCategoryAssignment.longestCourseCategory(eventRaceData)
            if (existing != null) {
                val updatedStart = when {
                    existingResult != null -> existing.drawnRelativeStartTime
                    startedInForest -> existing.drawnRelativeStartTime ?: startTime
                    else -> null
                }
                val updatedCategoryId = existing.categoryId ?: categoryData?.category?.id?.let(UUID::fromString)
                if (updatedStart != existing.drawnRelativeStartTime || updatedCategoryId != existing.categoryId) {
                    createOrUpdateCompetitor(
                        existing.copy(
                            categoryId = updatedCategoryId,
                            isMan = if (existing.categoryId == null) categoryData?.category?.isMan ?: existing.isMan else existing.isMan,
                            drawnRelativeStartTime = updatedStart
                        )
                    )
                }
                return@forEach
            }

            createOrUpdateCompetitor(
                Competitor(
                    id = UUID.randomUUID(),
                    raceId = raceId,
                    categoryId = categoryData?.category?.id?.let(UUID::fromString),
                    firstName = nameParts.getOrNull(1).orEmpty().ifEmpty { "SI ${cardData.siNumber}" },
                    lastName = nameParts.firstOrNull().orEmpty().ifEmpty { "Practice" },
                    club = "",
                    index = "",
                    isMan = categoryData?.category?.isMan ?: false,
                    birthYear = null,
                    siNumber = cardData.siNumber,
                    siRent = false,
                    startNumber = getHighestStartNumberByRace(raceId) + 1,
                    drawnRelativeStartTime = startTime
                )
            )
        }
        return getCompetitorBySINumber(cardData.siNumber, sourceRace.id)
    }

    private fun CardData.isBlankPracticeStart(): Boolean =
        punchData.isEmpty() && startTime == null && finishTime == null

    private fun showSiReadoutToast(messageResId: Int, vararg formatArgs: Any) {
        appContext.get()?.let { context ->
            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(context, context.getString(messageResId, *formatArgs), Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun eventSeriesReadoutMembersForRace(raceId: UUID): List<EventSeriesReadoutMemberData> {
        val seriesData = getEventSeriesForRace(raceId) ?: return emptyList()
        return seriesData.orderedMembers().mapNotNull { member ->
            getRace(member.localRaceId) ?: return@mapNotNull null
            EventSeriesReadoutMemberData(
                member = member,
                raceData = getRaceData(member.localRaceId)
            )
        }
    }

    suspend fun setAllResultsUnsent(raceId: UUID) =
        ardfRepository.setAllResultsUnsent(raceId)

    //RESULT SERVICE
    fun getResultServiceByRaceId(raceId: UUID) =
        ardfRepository.getResultServiceByRaceId(raceId)

    fun getResultServiceLiveDataWithCountByRaceId(raceId: UUID) =
        ardfRepository.getResultServiceLiveDataWithCountByRaceId(raceId)

    suspend fun createOrUpdateResultService(resultService: ResultService) =
        ardfRepository.createOrUpdateResultService(resultService)

    suspend fun setResultServiceDisabledByRaceId(raceId: UUID) {
        val service = getResultServiceByRaceId(raceId)
        if (service != null) {
            service.enabled = false
            service.init = false
            service.status = ResultServiceStatus.DISABLED
            service.errorText = ""
            ardfRepository.createOrUpdateResultService(service)
        }
    }

    fun setResultServiceJob(job: Job) {
        currentState.postValue(currentState.value?.let {
            AppState(
                it.currentRace,
                it.siReaderState,
                job
            )
        })
        currentState.value?.resultServiceJob?.start()
    }

    fun removeResultServiceJob() {
        currentState.value?.resultServiceJob?.cancel()
        currentState.postValue(currentState.value?.let {
            AppState(
                it.currentRace,
                it.siReaderState,
                null
            )
        })
    }

    //DATA IMPORT/EXPORT
    suspend fun importData(
        uri: Uri,
        dataType: DataType,
        dataFormat: DataFormat,
        raceId: UUID
    ): DataImportWrapper {
        val race = getRace(raceId)
        val context = getContext()

        if (context != null && race != null) {
            race.let { race ->
                val data =
                    fileProcessor?.importData(uri, dataType, dataFormat, race, context)

                DataImportValidator.validateDataImport(data!!, raceId, dataType, this, context)
                return data
            }
        }
        return DataImportWrapper(emptyList(), emptyList(), arrayListOf())
    }


    suspend fun exportData(
        uri: Uri,
        dataType: DataType,
        dataFormat: DataFormat,
        race: Race
    ) =
        fileProcessor?.exportData(
            uri,
            dataType,
            dataFormat,
            race
        )

    //-----------------------RACE DATA-----------------------

    suspend fun getRaceData(raceId: UUID): RaceData {
        val race = getRace(raceId)
        val categories = getCategoryDataForRace(raceId)
        val aliases = getAliasesByRace(raceId)
        val competitorData =
            ResultsProcessor.getCompetitorDataByRace(raceId, this)
        val unknownReadoutData =
            getResultDataFlowByRace(raceId).first().filter { it.competitorCategory == null }
                .map { fil -> ReadoutData(fil.result, fil.punches) }

        return race?.let { RaceData(it, categories, aliases, competitorData, unknownReadoutData) }
            ?: RaceData(Race(), categories, aliases, competitorData, unknownReadoutData)
    }

    @Throws(Exception::class)
    suspend fun importRaceData(uri: Uri): RaceData? {
        val context = getContext()
        return if (context != null) {
            fileProcessor?.importRaceData(uri, context)?.let { raceData ->
                DataImportValidator.validateRaceDataImport(raceData, context)
                val importedRaceData = raceData.withFreshImportIds()
                DebugLog.info(
                    "Races",
                    "Prepared Race File import race=${importedRaceData.race.id} name=${importedRaceData.race.name} source=${importedRaceData.race.importSourceId ?: "none"}"
                )
                return importedRaceData
            }
        } else null
    }

    @Throws(Exception::class)
    suspend fun importRaceData(jsonString: String): RaceData? {
        val context = getContext() ?: return null
        val raceData = JsonProcessor.importRaceData(jsonString, this)
        DataImportValidator.validateRaceDataImport(raceData, context)
        val importedRaceData = raceData.withFreshImportIds()
        DebugLog.info(
            "Races",
            "Prepared downloaded Race File import race=${importedRaceData.race.id} name=${importedRaceData.race.name} source=${importedRaceData.race.importSourceId ?: "none"}"
        )
        return importedRaceData
    }

    @Throws(Exception::class)
    suspend fun importEventSeriesPackage(uri: Uri): AndroidEventSeriesImport? {
        val context = getContext() ?: return null
        val eventSeriesImport = context.contentResolver.openInputStream(uri)?.use { input ->
            EventSeriesImport.prepareZipPackage(input)
        } ?: return null
        return validateEventSeriesImport(context, eventSeriesImport, "Prepared Race Series import")
    }

    @Throws(Exception::class)
    suspend fun importEventSeriesPackage(bytes: ByteArray): AndroidEventSeriesImport? {
        val context = getContext() ?: return null
        val eventSeriesImport = EventSeriesImport.prepareZipPackage(ByteArrayInputStream(bytes))
        return validateEventSeriesImport(context, eventSeriesImport, "Prepared downloaded Race Series import")
    }

    private fun validateEventSeriesImport(
        context: Context,
        eventSeriesImport: AndroidEventSeriesImport,
        logMessagePrefix: String
    ): AndroidEventSeriesImport {
        eventSeriesImport.races.forEach { raceData ->
            DataImportValidator.validateRaceDataImport(raceData, context)
        }
        DebugLog.info(
            "Race Series",
            "$logMessagePrefix id=${eventSeriesImport.series.seriesId} " +
                "members=${eventSeriesImport.memberImports.size}"
        )
        return eventSeriesImport
    }

    suspend fun exportRaceData(uri: Uri, raceId: UUID) {
        fileProcessor?.exportRaceData(uri, raceId)
        DebugLog.info("Races", "Exported race=$raceId")
    }

    suspend fun exportRaceOrSeriesData(uri: Uri, raceId: UUID) {
        val seriesPackageBytes = exportEventSeriesPackageBytesForRace(raceId)
        if (seriesPackageBytes == null) {
            exportRaceData(uri, raceId)
            return
        }
        writeEventSeriesPackageToUri(uri, seriesPackageBytes)
        DebugLog.info("Race Series", "Exported Race Series package for race=$raceId")
    }

    suspend fun exportEventSeriesPackage(uri: Uri, seriesId: String) {
        val seriesPackageBytes = exportEventSeriesPackageBytes(seriesId)
        writeEventSeriesPackageToUri(uri, seriesPackageBytes)
        DebugLog.info("Race Series", "Exported Race Series package id=$seriesId")
    }

    private fun writeEventSeriesPackageToUri(uri: Uri, bytes: ByteArray) {
        val context = getContext() ?: throw IllegalStateException("Could not access Android context.")
        context.contentResolver.openOutputStream(uri)?.use { output ->
            output.write(bytes)
        } ?: throw IllegalStateException("Could not open Race Series export destination.")
    }

    suspend fun exportRaceDataBytes(raceId: UUID): ByteArray =
        ByteArrayOutputStream().use { outStream ->
            JsonProcessor.exportRaceData(outStream, this, raceId)
            outStream.toByteArray()
        }

    suspend fun exportRaceOrSeriesDataBytes(raceId: UUID): ByteArray =
        exportEventSeriesPackageBytesForRace(raceId) ?: exportRaceDataBytes(raceId)

    suspend fun desktopUploadForRaceOrSeries(raceId: UUID): DesktopFileTransferUpload {
        val race = getRace(raceId) ?: throw IllegalArgumentException("Race not found: $raceId")
        val seriesName = getEventSeriesForRace(raceId)
            ?.series
            ?.name
        return EventFileTransferUploads.forRaceOrSeries(
            raceName = race.name,
            seriesName = seriesName,
            bytes = exportRaceOrSeriesDataBytes(raceId)
        )
    }

    suspend fun desktopUploadForSeries(seriesId: String): DesktopFileTransferUpload {
        val seriesData = getEventSeries(seriesId) ?: throw IllegalArgumentException("Race Series not found: $seriesId")
        val firstMember = seriesData.orderedMembers().firstOrNull()
        return EventFileTransferUploads.forRaceOrSeries(
            raceName = firstMember?.displayName ?: seriesData.series.name,
            seriesName = seriesData.series.name,
            bytes = exportEventSeriesPackageBytes(seriesId)
        )
    }

    suspend fun exportEventSeriesPackageBytes(seriesId: String): ByteArray {
        val seriesData = getEventSeries(seriesId) ?: throw IllegalArgumentException("Race Series not found: $seriesId")
        return exportEventSeriesPackageBytes(seriesData)
    }

    suspend fun exportEventSeriesPackageBytesForRace(raceId: UUID): ByteArray? {
        val seriesData = getEventSeriesForRace(raceId) ?: return null
        return exportEventSeriesPackageBytes(seriesData)
    }

    private suspend fun exportEventSeriesPackageBytes(seriesData: EventSeriesData): ByteArray {
        val members = seriesData.orderedMembers()
        if (members.isEmpty()) {
            throw IllegalArgumentException("Race Series export requires at least one race.")
        }
        return EventSeriesExport.packageBytes(
            seriesData = seriesData,
            raceDataById = members.associate { member ->
                member.localRaceId to getRaceData(member.localRaceId)
            }
        )
    }

    suspend fun saveRaceData(raceData: RaceData) {
        ardfRepository.saveRaceData(raceData)
        DebugLog.info(
            "Races",
            "Saved imported race=${raceData.race.id} name=${raceData.race.name} source=${raceData.race.importSourceId ?: "none"}"
        )
    }

    suspend fun saveDataImportWrapper(
        data: DataImportWrapper
    ) {
        //Upsert categories
        for (catData in data.categories) {
            createOrUpdateCategory(
                catData.category,
                catData.controlPoints
            )
        }
        //Create competitors - TODO: ADD duplicates check
        for (compData in data.competitorCategories) {
            createOrUpdateCompetitor(compData.competitor)
        }
    }

    suspend fun fetchProviderRaceData(
        providerType: ProviderType,
        apiKey: String,
        context: Context
    ): RaceData =
        ProviderClient.fetchRaceData(apiKey, providerType, this, context)

    //SportIdent manipulation
    fun connectDevice(usbDevice: UsbDevice) {
        val context = getContext();
        context?.let { context ->
            DebugLog.info("SI", "Starting reader service for USB ${usbDevice.vendorId}:${usbDevice.productId}")
            Intent(context, SIReaderService::class.java).also {
                it.action = SIReaderService.ReaderServiceActions.START.toString()
                it.putExtra(SIReaderService.USB_DEVICE, usbDevice)
                appContext.get()?.startService(it)
            }
        }
    }

    fun detachDevice(usbDevice: UsbDevice) {
        val context = getContext();
        context?.let { context ->
            DebugLog.info("SI", "Stopping reader service for USB ${usbDevice.vendorId}:${usbDevice.productId}")
            Intent(context, SIReaderService::class.java).also {
                it.action = SIReaderService.ReaderServiceActions.STOP.toString()
                it.putExtra(SIReaderService.USB_DEVICE, usbDevice)
                context.startService(it)
            }
        }
    }

    fun getLastReadCard(): Int? = currentState.value?.siReaderState?.lastCard

    //PRINTING
    fun disablePrinter() {
        printProcessor.disablePrinter()
    }

    suspend fun printFinishTicket(resultData: ResultData): PrintAttemptResult =
        printProcessor.printFinishTicket(resultData)


    suspend fun printResults(results: List<ResultWrapper>, race: Race): PrintAttemptResult =
        printProcessor.printResults(results, race)

    //============================= GENERAL HELPER METHODS =========================================

    //Enums manipulation
    fun raceTypeToString(raceType: RaceType): String {
        val raceTypeStrings = appContext.get()?.resources?.getStringArray(R.array.race_types_array)
        return raceTypeStrings?.getOrNull(raceType.value) ?: ""
    }

    fun raceTypeStringToEnum(string: String): RaceType {
        val raceTypeStrings = appContext.get()?.resources?.getStringArray(R.array.race_types_array)
        val idx = raceTypeStrings?.indexOf(string) ?: -1
        return if (idx >= 0) RaceType.getByValue(idx) else RaceType.entries.first()
    }

    fun raceLevelToString(raceLevel: RaceLevel): String {
        val raceLevelStrings =
            appContext.get()?.resources?.getStringArray(R.array.race_levels_array)
        return raceLevelStrings?.getOrNull(raceLevel.value) ?: ""
    }

    fun raceLevelStringToEnum(string: String): RaceLevel {
        val raceLevelStrings =
            appContext.get()?.resources?.getStringArray(R.array.race_levels_array)
        val idx = raceLevelStrings?.indexOf(string) ?: -1
        return if (idx >= 0) RaceLevel.getByValue(idx) else RaceLevel.entries.first()
    }

    fun raceBandToString(raceBand: RaceBand): String {
        val raceBandStrings = appContext.get()?.resources?.getStringArray(R.array.race_bands_array)
        return raceBandStrings?.getOrNull(raceBand.value) ?: ""
    }

    fun raceBandStringToEnum(string: String): RaceBand {
        val raceBandStrings = appContext.get()?.resources?.getStringArray(R.array.race_bands_array)
        val idx = raceBandStrings?.indexOf(string) ?: -1
        return if (idx >= 0) RaceBand.getByValue(idx) else RaceBand.entries.first()
    }

    fun resultStatusToString(resultStatus: ResultStatus): String {
        val raceStatusStrings =
            appContext.get()?.resources?.getStringArray(R.array.result_status_array)
        return raceStatusStrings?.getOrNull(resultStatus.value) ?: ""
    }

    fun resultStatusStringToEnum(string: String): ResultStatus {
        val raceStatusStrings =
            appContext.get()?.resources?.getStringArray(R.array.result_status_array)
        val idx = raceStatusStrings?.indexOf(string) ?: -1
        return if (idx >= 0) ResultStatus.getByValue(idx) else ResultStatus.entries.first()
    }

    fun resultStatusToShortString(resultStatus: ResultStatus): String {
        val raceStatusStrings =
            appContext.get()?.resources?.getStringArray(R.array.result_status_short_array)
        return raceStatusStrings?.getOrNull(resultStatus.value) ?: ""
    }

    fun resultStatusShortStringToEnum(string: String): ResultStatus {
        val raceStatusStrings =
            appContext.get()?.resources?.getStringArray(R.array.result_status_short_array)
        val idx = raceStatusStrings?.indexOf(string) ?: -1
        return if (idx >= 0) ResultStatus.getByValue(idx) else ResultStatus.entries.first()
    }

    fun genderToString(isMan: Boolean?): String {
        val ctx = appContext.get()
        return when (isMan) {
            false -> ctx?.resources?.getString(R.string.general_gender_woman) ?: "Women"
            true -> ctx?.resources?.getString(R.string.general_gender_man) ?: "Men"
            null -> "Men"
        }
    }

    fun providerTypeFromString(string: String): ProviderType {
        val providerStrings =
            appContext.get()?.resources?.getStringArray(R.array.result_service_types)
        val idx = providerStrings?.indexOf(string) ?: -1
        return if (idx >= 0) ProviderType.getByValue(idx) else ProviderType.entries.first()
    }

    fun providerTypeToString(providerType: ProviderType): String {
        val providerTypes =
            appContext.get()?.resources?.getStringArray(R.array.result_service_types)
        return providerTypes?.getOrNull(providerType.value) ?: ""
    }

    fun resultServiceStatusToString(status: ResultServiceStatus): CharSequence {
        val resultServiceStatus =
            appContext.get()?.resources?.getStringArray(R.array.result_service_status)
        return resultServiceStatus?.getOrNull(status.value) ?: ""
    }

    fun punchStatusToShortString(punchStatus: PunchStatus): String {
        val arr = appContext.get()?.resources?.getStringArray(R.array.punch_status_array_short)
        return arr?.getOrNull(punchStatus.ordinal) ?: ""
    }

    fun shortStringToPunchStatus(string: String): PunchStatus {
        val punchStatusStrings =
            appContext.get()?.resources?.getStringArray(R.array.punch_status_array_short)
        val idx = punchStatusStrings?.indexOf(string) ?: -1
        return if (idx >= 0) PunchStatus.getByValue(idx) else PunchStatus.entries.first()
    }

    /**
     * @return false for woman, true for man
     */
    fun genderFromString(string: String): Boolean {
        val genderStrings = appContext.get()?.resources?.getStringArray(R.array.genders)
        return when (genderStrings?.indexOf(string)) {
            0 -> false
            1 -> true
            else -> true
        }
    }

    fun dataFormatFromString(string: String): DataFormat {
        val dataStrings = appContext.get()?.resources?.getStringArray(R.array.data_formats)!!
        val index = dataStrings.indexOf(string).or(0)
        return DataFormat.getByValue(index)!!
    }

    fun dataTypeFromString(string: String): DataType {
        val dataStrings = appContext.get()?.resources?.getStringArray(R.array.data_types)!!
        val index = dataStrings.indexOf(string).or(0)
        return DataType.getByValue(index)!!
    }
}
