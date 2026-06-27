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

package org.openardf.radiooracle.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.files.constants.DataFormat
import org.openardf.radiooracle.backend.files.constants.DataType
import org.openardf.radiooracle.backend.files.wrappers.DataImportWrapper
import org.openardf.radiooracle.backend.logging.DebugLog
import org.openardf.radiooracle.backend.results.ResultsProcessor
import org.openardf.radiooracle.backend.room.entity.Alias
import org.openardf.radiooracle.backend.room.entity.Category
import org.openardf.radiooracle.backend.room.entity.Competitor
import org.openardf.radiooracle.backend.room.entity.ControlPoint
import org.openardf.radiooracle.backend.room.entity.Punch
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.room.entity.Result
import org.openardf.radiooracle.backend.room.entity.embeddeds.CategoryData
import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorData
import org.openardf.radiooracle.backend.room.entity.embeddeds.EventSeriesData
import org.openardf.radiooracle.backend.room.entity.embeddeds.ResultData
import org.openardf.radiooracle.backend.room.entity.embeddeds.ResultServiceData
import org.openardf.radiooracle.backend.room.enums.ResultStatus
import org.openardf.radiooracle.backend.room.enums.StandardCategoryType
import org.openardf.radiooracle.backend.wrappers.ResultWrapper
import org.openardf.radiooracle.backend.wrappers.StatisticsWrapper
import org.openardf.radiooracle.ui.categories.CategoryDisplaySort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Locale
import java.util.UUID

/**
 * Represents the current selected race and its data - properties, categories, competitors and readouts
 */
class SelectedRaceViewModel : ViewModel() {
    private val dataProcessor = DataProcessor.get()
    private val _race = MutableLiveData<Race?>()

    val race: LiveData<Race?> get() = _race
    private val _categories: MutableStateFlow<List<CategoryData>> = MutableStateFlow(emptyList())
    val categories: StateFlow<List<CategoryData>> get() = _categories.asStateFlow()

    private val _competitorData: MutableStateFlow<List<CompetitorData>> =
        MutableStateFlow(emptyList())
    val competitorData: StateFlow<List<CompetitorData>>
        get() = _competitorData.asStateFlow()

    private val _readoutData: MutableStateFlow<List<ResultData>> = MutableStateFlow(emptyList())
    val readoutData: StateFlow<List<ResultData>> get() = _readoutData.asStateFlow()

    private val _resultWrappers: MutableStateFlow<List<ResultWrapper>> =
        MutableStateFlow(emptyList())
    val resultWrappers: StateFlow<List<ResultWrapper>> get() = _resultWrappers.asStateFlow()

    private val _eventSeries: MutableStateFlow<List<EventSeriesData>> = MutableStateFlow(emptyList())
    val eventSeries: StateFlow<List<EventSeriesData>> get() = _eventSeries.asStateFlow()

    private val _currentRaceSeries: MutableStateFlow<EventSeriesData?> = MutableStateFlow(null)
    val currentRaceSeries: StateFlow<EventSeriesData?> get() = _currentRaceSeries.asStateFlow()

    var resultService: LiveData<ResultServiceData> = MutableLiveData(null)

    // Jobs for running collectors - stored so we can cancel them when switching races
    private var categoryJob: Job? = null
    private var competitorJob: Job? = null
    private var readoutJob: Job? = null
    private var resultWrapperJob: Job? = null
    private var selectedRaceId: UUID? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            dataProcessor.raceSelectionRequests.collect { raceId ->
                if (raceId != selectedRaceId) {
                    setRace(raceId)
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            dataProcessor.getEventSeries().collect { series ->
                _eventSeries.value = series
                updateCurrentRaceSeries()
            }
        }
    }

    @Throws(IllegalStateException::class)
    fun getCurrentRace(): Race? {
        return race.value
    }

    /**
     * Updates the current selected race and corresponding data
     * Return indicates if the race was correctly set and retrieved from DB
     */
    fun setRace(id: UUID) {
        selectedRaceId = id

        // Cancel any previous collectors to avoid collecting old race data
        categoryJob?.cancel()
        competitorJob?.cancel()
        readoutJob?.cancel()
        resultWrapperJob?.cancel()

        // Clear previously shown data immediately so UI doesn't show stale values
        _categories.value = emptyList()
        _competitorData.value = emptyList()
        _readoutData.value = emptyList()
        _resultWrappers.value = emptyList()
        _race.postValue(null)
        updateCurrentRaceSeries()

        // Use viewModelScope so the collectors are lifecycle-aware and can be cancelled when VM is cleared
        viewModelScope.launch(Dispatchers.IO) {
            val race = dataProcessor.setCurrentRace(id)

            if (race != null) {
                _race.postValue(race)

                // start collectors and keep job references so they can be cancelled on next setRace
                categoryJob = launch {
                    dataProcessor.getCategoryDataFlowForRace(id).collect {
                        _categories.value = CategoryDisplaySort.categoryData(it)
                    }
                }
                competitorJob = launch {
                    dataProcessor.getCompetitorDataFlowByRace(id).collect {
                        _competitorData.value = it
                    }
                }
                readoutJob = launch {
                    dataProcessor.getResultDataFlowByRace(id).collect {
                        _readoutData.value = it
                    }
                }

                resultWrapperJob = launch {
                    val seriesResultsFlow = dataProcessor.getSeriesResultWrapperFlowForRace(id)
                    val resultsFlow = seriesResultsFlow
                        ?: ResultsProcessor.getResultWrapperFlowByRace(id, dataProcessor)
                    resultsFlow.collect {
                        _resultWrappers.value = CategoryDisplaySort.resultWrappers(it)
                    }
                }
            }
        }

        // Start result service again (fire-and-forget)
        viewModelScope.launch(Dispatchers.IO) { dataProcessor.setResultServiceDisabledByRaceId(id) }
        resultService = dataProcessor.getResultServiceLiveDataWithCountByRaceId(id)

    }

    fun updateRace(race: Race) {
        viewModelScope.launch(Dispatchers.IO) {
            dataProcessor.updateRace(race)
            _race.postValue(race)
        }
    }

    fun removeReaderRace() {
        // Cancel collectors and clear state to ensure no stale data remains
        categoryJob?.cancel()
        competitorJob?.cancel()
        readoutJob?.cancel()
        resultWrapperJob?.cancel()

        _categories.value = emptyList()
        _competitorData.value = emptyList()
        _readoutData.value = emptyList()
        _resultWrappers.value = emptyList()
        _race.postValue(null)
        selectedRaceId = null
        updateCurrentRaceSeries()

        dataProcessor.removeCurrentRace()
    }

    private fun updateCurrentRaceSeries() {
        val raceId = selectedRaceId
        _currentRaceSeries.value = if (raceId == null) {
            null
        } else {
            _eventSeries.value.firstOrNull { seriesData ->
                seriesData.members.any { member -> member.localRaceId == raceId }
            }
        }
    }

    private fun currentRaceIdOrThrow(): UUID =
        selectedRaceId ?: throw IllegalStateException("No event is selected.")

    suspend fun createSeriesFromCurrentRace(seriesName: String): EventSeriesData {
        val seriesData = dataProcessor.createEventSeriesFromRace(currentRaceIdOrThrow(), seriesName)
        selectedRaceId?.let { setRace(it) }
        return seriesData
    }

    suspend fun addCurrentRaceToEventSeries(seriesId: String): EventSeriesData {
        val seriesData = dataProcessor.addRaceToEventSeries(currentRaceIdOrThrow(), seriesId)
        selectedRaceId?.let { setRace(it) }
        return seriesData
    }

    suspend fun removeCurrentRaceFromEventSeries(): EventSeriesData? {
        val seriesData = dataProcessor.removeRaceFromEventSeries(currentRaceIdOrThrow())
        selectedRaceId?.let { setRace(it) }
        return seriesData
    }

    // get current locale
    fun getCurrentLocale(context: Context): Locale {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        val preference =
            sharedPref.getString(context.getString(R.string.key_app_language), "en") ?: "en"

        // Use forLanguageTag to avoid deprecated Locale constructor and handle nullability
        return Locale.forLanguageTag(preference)
    }

    //Category
    suspend fun getCategory(id: UUID) = dataProcessor.getCategory(id)

    fun getCategories(): List<Category> =
        CategoryDisplaySort.categories(categories.value.map { it.category })
    fun getCategoryByName(string: String, raceId: UUID): Category? {
        return runBlocking {
            return@runBlocking dataProcessor.getCategoryByName(string, raceId)
        }
    }

    fun getHighestCategoryOrder(raceId: UUID): Int {
        return runBlocking {
            return@runBlocking dataProcessor.getHighestCategoryOrder(raceId)
        }
    }

    fun createOrUpdateCategory(category: Category, controlPoints: List<ControlPoint>?) =
        CoroutineScope(Dispatchers.IO).launch {
            dataProcessor.createOrUpdateCategory(category, controlPoints)
        }

    fun duplicateCategory(categoryData: CategoryData) {
        CoroutineScope(Dispatchers.IO).launch {
            dataProcessor.duplicateCategory(categoryData)
        }
    }

    fun createStandardCategories(type: StandardCategoryType, raceId: UUID) {
        CoroutineScope(Dispatchers.IO).launch {
            dataProcessor.createStandardCategories(type, raceId)
        }
    }

    fun deleteCategory(categoryId: UUID, raceId: UUID, deleteCompetitors: Boolean) =
        CoroutineScope(Dispatchers.IO).launch {
            dataProcessor.deleteCategory(
                categoryId, raceId, deleteCompetitors
            )
        }


    fun getControlPointsByCategory(categoryId: UUID): ArrayList<ControlPoint> {
        return runBlocking {
            ArrayList(dataProcessor.getControlPointsByCategory(categoryId))
        }
    }

    //Alias
    fun getAliasesByRace(raceId: UUID) = runBlocking { dataProcessor.getAliasesByRace(raceId) }

    fun createOrUpdateAliases(aliases: List<Alias>, raceId: UUID) {
        CoroutineScope(Dispatchers.IO).launch {
            dataProcessor.createOrUpdateAliases(aliases, raceId)
        }
    }

    //Competitor
    fun getCompetitors(): List<Competitor> =
        competitorData.value.map { it.competitorCategory.competitor }

    fun getCompetitor(id: UUID) = runBlocking {
        return@runBlocking dataProcessor.getCompetitor(id)
    }

    fun createOrUpdateCompetitor(competitor: Competitor) = CoroutineScope(Dispatchers.IO).launch {
        dataProcessor.createOrUpdateCompetitor(competitor)
    }

    fun createCompetitorFromCardReadout(resultData: ResultData) = CoroutineScope(Dispatchers.IO).launch {
        val siNumber = resultData.result.siNumber ?: return@launch
        val cardName = resultData.result.cardName?.trim()?.takeIf { it.isNotBlank() } ?: return@launch
        val raceId = resultData.result.raceId
        if (dataProcessor.getCompetitorBySINumber(siNumber, raceId) != null) {
            DebugLog.info("Readouts", "Skipped card competitor creation because SI already exists si=$siNumber")
            return@launch
        }

        val nameParts = cardName.split(Regex("\\s+"), limit = 2)
        val competitor = Competitor(
            id = UUID.randomUUID(),
            raceId = raceId,
            categoryId = null,
            firstName = nameParts.getOrNull(1).orEmpty(),
            lastName = nameParts.firstOrNull().orEmpty(),
            club = "",
            index = "",
            isMan = false,
            birthYear = null,
            siNumber = siNumber,
            siRent = false,
            startNumber = dataProcessor.getHighestStartNumberByRace(raceId) + 1,
            drawnRelativeStartTime = null
        )
        dataProcessor.createOrUpdateCompetitor(competitor)
        DebugLog.info(
            "Readouts",
            "Created card competitor id=${competitor.id} si=$siNumber name=${competitor.getFullName()}"
        )
    }

    fun deleteCompetitor(competitorId: UUID, deleteResult: Boolean) =
        CoroutineScope(Dispatchers.IO).launch {
            dataProcessor.deleteCompetitor(
                competitorId, deleteResult
            )
        }

    fun deleteAllCompetitorsByRace() = CoroutineScope(Dispatchers.IO).launch {
        race.value?.let {
            dataProcessor.deleteAllCompetitorsByRace(
                it.id
            )
        }
    }

    suspend fun getStatistics(raceId: UUID): StatisticsWrapper =
        dataProcessor.getStatisticsByRace(raceId)

    /**
     * Checks if the SI number is unique
     */
    fun checkIfSINumberExists(siNumber: Int): Boolean {
        if (race.value != null) {
            return dataProcessor.checkIfSINumberExists(siNumber, race.value!!.id)
        }
        return true
    }

    fun getLastReadCard() = dataProcessor.getLastReadCard()

    suspend fun processManualPunchData(
        result: Result, punches: ArrayList<Punch>, manualStatus: ResultStatus?, modified: Boolean
    ) {
        getCurrentRace()?.let { race ->
            ResultsProcessor.processManualPunchData(
                result,
                punches,
                manualStatus, race,
                DataProcessor.get(),
                modified
            )
        }
    }

    fun getResultData(id: UUID): ResultData {
        return runBlocking {
            return@runBlocking dataProcessor.getResultData(id)
        }
    }

    fun getResultBySINumber(siNumber: Int, raceId: UUID) = runBlocking {
        return@runBlocking dataProcessor.getResultBySINumber(siNumber, raceId)
    }

    fun getResultByCompetitor(competitorId: UUID) = runBlocking {
        return@runBlocking dataProcessor.getResultByCompetitor(competitorId)
    }

    fun updateResultsByRace(raceId: UUID) = CoroutineScope(Dispatchers.IO).launch {
        dataProcessor.updateResultsByRace(raceId)
    }

    fun deleteResult(id: UUID) {
        CoroutineScope(Dispatchers.IO).launch {
            dataProcessor.deleteResult(id)
        }
    }

    fun deleteAllResultsByRace() = CoroutineScope(Dispatchers.IO).launch {
        race.value?.let {
            dataProcessor.deleteAllResultsByRace(
                it.id
            )
        }
    }

    //RESULT SERVICE

    fun disableResultService() {
        CoroutineScope(Dispatchers.IO).launch {
            dataProcessor.removeResultServiceJob()
            race.value?.let { dataProcessor.setResultServiceDisabledByRaceId(it.id) }
        }
    }

    fun setAllResultsUnsent(raceId: UUID) {
        CoroutineScope(Dispatchers.IO).launch {
            dataProcessor.setAllResultsUnsent(raceId)
        }
    }

    //DATA IMPORT/EXPORT
    suspend fun importData(
        uri: Uri, dataType: DataType, dataFormat: DataFormat, raceId: UUID
    ): DataImportWrapper {
        return dataProcessor.importData(
            uri, dataType, dataFormat, raceId
        )
    }

    fun exportData(
        uri: Uri, dataType: DataType, dataFormat: DataFormat, race: Race
    ) = runBlocking {
        dataProcessor.exportData(
            uri, dataType, dataFormat, race
        )
    }

    fun saveDataImportWrapper(
        dataImportWrapper: DataImportWrapper
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            dataProcessor.saveDataImportWrapper(dataImportWrapper)
        }
    }
}
