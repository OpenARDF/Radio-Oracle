package org.openardf.radiooracle.backend

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.preference.PreferenceManager
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.files.DataImportValidator
import org.openardf.radiooracle.backend.files.AndroidEventSeriesImport
import org.openardf.radiooracle.backend.files.EventSeriesExport
import org.openardf.radiooracle.backend.files.EventSeriesImport
import org.openardf.radiooracle.backend.files.FileProcessor
import org.openardf.radiooracle.backend.files.processors.JsonProcessor
import org.openardf.radiooracle.backend.files.wrappers.DataImportWrapper
import org.openardf.radiooracle.backend.helpers.ControlPointsHelper
import org.openardf.radiooracle.backend.helpers.TimeProcessor
import org.openardf.radiooracle.backend.logging.DebugLog
import org.openardf.radiooracle.backend.network.ProviderClient
import org.openardf.radiooracle.backend.prints.PrintAttemptResult
import org.openardf.radiooracle.backend.prints.PrintProcessor
import org.openardf.radiooracle.backend.results.ResultsProcessor
import org.openardf.radiooracle.backend.results.ResultsProcessor.updateResultsForCategory
import org.openardf.radiooracle.backend.results.ResultsProcessor.updateResultsForCompetitor
import org.openardf.radiooracle.backend.room.ARDFRepository
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
import org.openardf.radiooracle.backend.room.entity.embeddeds.RaceData
import org.openardf.radiooracle.backend.room.entity.embeddeds.ReadoutData
import org.openardf.radiooracle.backend.room.entity.embeddeds.ResultData
import org.openardf.radiooracle.backend.room.withFreshImportIds
import org.openardf.radiooracle.backend.sportident.EventSeriesReadoutMemberData
import org.openardf.radiooracle.backend.sportident.EventSeriesReadoutRoute
import org.openardf.radiooracle.backend.sportident.EventSeriesReadoutRouter
import org.openardf.radiooracle.backend.sportident.SIPort.CardData
import org.openardf.radiooracle.backend.sportident.SIReaderService
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
        DebugLog.info("Events", "Created event=${race.id} name=${race.name}")
    }

    suspend fun updateRace(race: Race) {
        ardfRepository.updateRace(race)
        updateResultsByRace(race.id)
        DebugLog.info("Events", "Updated event=${race.id} name=${race.name}")
    }

    suspend fun deleteRace(id: UUID) {
        val race = getRace(id)
        ardfRepository.deleteRace(id)
        DebugLog.info("Events", "Deleted event=$id name=${race?.name ?: "unknown"}")
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
        val statistics = StatisticsWrapper(competitors.size, 0, 0, 0)
        val race = getRace(raceId)

        for (cd in competitors) {
            val competitor = cd.competitorCategory.competitor
            val category = cd.competitorCategory.category

            if (cd.readoutData == null) {
                if (competitor.drawnRelativeStartTime != null) {

                    race?.let { race ->
                        //Count started
                        if (TimeProcessor.hasStarted(
                                race.startDateTime,
                                competitor.drawnRelativeStartTime!!,
                                LocalDateTime.now()
                            )
                        ) {
                            statistics.startedCompetitors++
                        }

                        val limit = category?.timeLimit ?: race.timeLimit
                        if (TimeProcessor.isInLimit(
                                race.startDateTime,
                                competitor.drawnRelativeStartTime!!,
                                limit, LocalDateTime.now()
                            )
                        ) {
                            statistics.inLimitCompetitors++
                        }
                    }
                }
            } else {
                statistics.startedCompetitors++
                statistics.finishedCompetitors++
            }

        }
        return statistics
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

    /** Recalculates stored results after scoring-rule changes that affect existing event data. */
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
    fun getEventSeries() = ardfRepository.getEventSeries()

    suspend fun getEventSeries(seriesId: String) =
        ardfRepository.getEventSeries(seriesId)

    suspend fun getEventSeriesForRace(raceId: UUID) =
        ardfRepository.getEventSeriesForRace(raceId)

    suspend fun getSeriesResultWrapperFlowForRace(raceId: UUID): Flow<List<ResultWrapper>>? {
        val seriesData = getEventSeriesForRace(raceId) ?: return null
        val members = seriesData.orderedMembers()
        if (members.size < 2) {
            return null
        }
        val memberFlows = members.map { member ->
            ResultsProcessor.getResultWrapperFlowByRace(member.localRaceId, this).map { wrappers ->
                wrappers.map { wrapper ->
                    wrapper.copy(displayLabel = seriesResultDisplayLabel(member.displayName, wrapper))
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
            "Event Series",
            "Saved Android event series id=${series.seriesId} members=${members.size}"
        )
    }

    fun prepareEventSeriesImport(
        manifestJson: String,
        eventFileJsonByPath: Map<String, String>
    ): AndroidEventSeriesImport =
        EventSeriesImport.prepare(manifestJson, eventFileJsonByPath)

    suspend fun saveEventSeriesImport(eventSeriesImport: AndroidEventSeriesImport) {
        ardfRepository.saveEventSeriesImport(eventSeriesImport)
        DebugLog.info(
            "Event Series",
            "Saved Android event series import id=${eventSeriesImport.series.seriesId} " +
                "members=${eventSeriesImport.memberImports.size}"
        )
    }

    suspend fun deleteEventSeries(seriesId: String) {
        ardfRepository.deleteEventSeries(seriesId)
        DebugLog.info("Event Series", "Deleted Android event series id=$seriesId")
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
        val seriesMembers = eventSeriesReadoutMembersForRace(currentRace.id)
        if (seriesMembers.size < 2) {
            return processCardData(cardData, currentRace)
        }

        return when (val route = EventSeriesReadoutRouter.route(cardData, seriesMembers)) {
            is EventSeriesReadoutRoute.Matched -> {
                DebugLog.info(
                    "Event Series",
                    "Card read routed si=${cardData.siNumber} " +
                        "series=${route.memberData.member.seriesId} " +
                        "event=${route.memberData.member.seriesEventId} " +
                        "reason=${route.reason}"
                )
                processCardData(cardData, route.memberData.raceData.race)
            }
            is EventSeriesReadoutRoute.Ambiguous -> {
                DebugLog.warn(
                    "Event Series",
                    "Card read ambiguous si=${cardData.siNumber} " +
                        "candidates=${route.candidates.joinToString { it.member.seriesEventId }} " +
                        "reason=${route.reason}"
                )
                false
            }
            EventSeriesReadoutRoute.NoMatch -> {
                DebugLog.warn(
                    "Event Series",
                    "Card read did not match any series event si=${cardData.siNumber}"
                )
                false
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
                    "Events",
                    "Prepared Event File import event=${importedRaceData.race.id} name=${importedRaceData.race.name} source=${importedRaceData.race.importSourceId ?: "none"}"
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
            "Events",
            "Prepared downloaded Event File import event=${importedRaceData.race.id} name=${importedRaceData.race.name} source=${importedRaceData.race.importSourceId ?: "none"}"
        )
        return importedRaceData
    }

    @Throws(Exception::class)
    suspend fun importEventSeriesPackage(uri: Uri): AndroidEventSeriesImport? {
        val context = getContext() ?: return null
        val eventSeriesImport = context.contentResolver.openInputStream(uri)?.use { input ->
            EventSeriesImport.prepareZipPackage(input)
        } ?: return null
        eventSeriesImport.races.forEach { raceData ->
            DataImportValidator.validateRaceDataImport(raceData, context)
        }
        DebugLog.info(
            "Event Series",
            "Prepared Event Series import id=${eventSeriesImport.series.seriesId} " +
                "members=${eventSeriesImport.memberImports.size}"
        )
        return eventSeriesImport
    }

    @Throws(Exception::class)
    suspend fun importEventSeriesPackage(bytes: ByteArray): AndroidEventSeriesImport? {
        val context = getContext() ?: return null
        val eventSeriesImport = EventSeriesImport.prepareZipPackage(ByteArrayInputStream(bytes))
        eventSeriesImport.races.forEach { raceData ->
            DataImportValidator.validateRaceDataImport(raceData, context)
        }
        DebugLog.info(
            "Event Series",
            "Prepared downloaded Event Series import id=${eventSeriesImport.series.seriesId} " +
                "members=${eventSeriesImport.memberImports.size}"
        )
        return eventSeriesImport
    }

    suspend fun exportRaceData(uri: Uri, raceId: UUID) {
        fileProcessor?.exportRaceData(uri, raceId)
        DebugLog.info("Events", "Exported event=$raceId")
    }

    suspend fun exportRaceOrSeriesData(uri: Uri, raceId: UUID) {
        val seriesPackageBytes = exportEventSeriesPackageBytesForRace(raceId)
        if (seriesPackageBytes == null) {
            exportRaceData(uri, raceId)
            return
        }
        val context = getContext() ?: return
        context.contentResolver.openOutputStream(uri)?.use { output ->
            output.write(seriesPackageBytes)
        }
        DebugLog.info("Event Series", "Exported Event Series package for event=$raceId")
    }

    suspend fun exportRaceDataBytes(raceId: UUID): ByteArray =
        ByteArrayOutputStream().use { outStream ->
            JsonProcessor.exportRaceData(outStream, this, raceId)
            outStream.toByteArray()
        }

    suspend fun exportRaceOrSeriesDataBytes(raceId: UUID): ByteArray =
        exportEventSeriesPackageBytesForRace(raceId) ?: exportRaceDataBytes(raceId)

    suspend fun exportEventSeriesPackageBytesForRace(raceId: UUID): ByteArray? {
        val seriesData = getEventSeriesForRace(raceId) ?: return null
        val members = seriesData.orderedMembers()
        if (members.size < 2) {
            return null
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
            "Events",
            "Saved imported event=${raceData.race.id} name=${raceData.race.name} source=${raceData.race.importSourceId ?: "none"}"
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

    suspend fun printFinishTicket(resultData: ResultData, race: Race): PrintAttemptResult =
        printProcessor.printFinishTicket(resultData, race)


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
