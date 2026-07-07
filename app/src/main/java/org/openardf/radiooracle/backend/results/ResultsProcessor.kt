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

package org.openardf.radiooracle.backend.results

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.helpers.TimeProcessor
import org.openardf.radiooracle.backend.logging.DebugLog
import org.openardf.radiooracle.backend.room.entity.Category
import org.openardf.radiooracle.backend.room.entity.Competitor
import org.openardf.radiooracle.backend.room.entity.ControlPoint
import org.openardf.radiooracle.backend.room.entity.Punch
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.room.entity.Result
import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorData
import org.openardf.radiooracle.backend.room.entity.embeddeds.ControlPointAlias
import org.openardf.radiooracle.backend.room.enums.PunchStatus
import org.openardf.radiooracle.backend.room.enums.RaceType
import org.openardf.radiooracle.backend.room.enums.ResultStatus
import org.openardf.radiooracle.backend.room.enums.SIRecordType
import org.openardf.radiooracle.backend.shared.toEventCompetitorData
import org.openardf.radiooracle.backend.sounds.SoundProcessor
import org.openardf.radiooracle.backend.sportident.SIConstants
import org.openardf.radiooracle.backend.sportident.SIPort.CardData
import org.openardf.radiooracle.backend.sportident.SITime
import org.openardf.radiooracle.backend.wrappers.ResultWrapper
import org.openardf.radiooracle.backend.wrappers.StatisticsWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.openardf.radiooracle.shared.results.CourseEvaluator
import org.openardf.radiooracle.shared.results.EventResultPlacement
import org.openardf.radiooracle.shared.sound.SoundType
import org.openardf.radiooracle.shared.results.EvaluationControlPoint
import org.openardf.radiooracle.shared.results.EvaluationPunch
import org.openardf.radiooracle.shared.sportident.SportIdentReadoutTiming
import org.openardf.radiooracle.shared.sportident.SportIdentReadoutTimingRepair
import java.time.Duration
import java.time.LocalTime
import java.util.UUID


object ResultsProcessor {
    internal const val PRINT_AUTOMATIC_MANUALLY_VALUE = "manually"
    internal const val PRINT_AUTOMATIC_CATEGORY_MATCHED_VALUE = "category_matched"
    internal const val PRINT_AUTOMATIC_COMPETITOR_MATCHED_VALUE = "competitor_matched"
    internal const val PRINT_AUTOMATIC_ALWAYS_VALUE = "always"
    internal const val PRINT_AUTOMATIC_ALWAYS_LEGACY_VALUE = "allways"

    internal fun calculateReadoutRunTiming(
        startTime: SITime?,
        finishTime: SITime?,
        controlTimes: List<SITime> = emptyList()
    ) = SportIdentReadoutTiming.calculate(
        startSeconds = startTime?.getSeconds(),
        finishSeconds = finishTime?.getSeconds(),
        controlSeconds = controlTimes.map { it.getSeconds() }
    )

    private fun adjustTime(previous: SITime, current: SITime): SITime {
        if (current.isAtOrAfter(previous)) {
            return current
        }

        val cmp = SITime(current)
        cmp.addHalfDay()

        if (cmp.isAtOrAfter(previous)) {
            return cmp
        }

        current.addDay()
        return current
    }

    /**
     * Adjust the times for the SI_CARD5, because it operates on 12h mode instead of 24h
     */
    private fun card5TimeAdjust(
        result: Result,
        punches: List<Punch>,
        adjustStart: Boolean,
        zeroTimeBase: LocalTime
    ) {
        //Solve start and check
        if (result.startTime != null && adjustStart) {
            result.startTime = adjustTime(SITime(zeroTimeBase), result.startTime!!)
        }

        //Adjust the punches
        for (punch in punches.withIndex()) {

            val previousTime = if (punch.index == 0) {
                if (result.startTime != null) {
                    result.startTime!!
                } else {
                    SITime(zeroTimeBase)
                }
            } else {
                punches[punch.index - 1].siTime
            }

            val currentTime = punch.value.siTime
            currentTime.setDayOfWeek(previousTime.getDayOfWeek())
            currentTime.setWeek(previousTime.getWeek())
            punches[punch.index].siTime = adjustTime(previousTime, currentTime)
        }

        if (result.finishTime != null) {

            val preFinishTime = if (punches.isEmpty()) {
                if (result.startTime != null) {
                    result.startTime!!
                } else {
                    SITime(zeroTimeBase)
                }
            } else {
                punches.last().siTime
            }

            val finishTime = result.finishTime!!
            finishTime.setDayOfWeek(preFinishTime.getDayOfWeek())
            finishTime.setWeek(preFinishTime.getWeek())

            result.finishTime = adjustTime(preFinishTime, finishTime)
        }
    }

    private fun removeStartAndFinishPunch(result: Result, punches: ArrayList<Punch>) {
        if (punches.first().punchType == SIRecordType.START) {
            result.startTime = punches.first().siTime
            punches.removeAt(0)
        }
        if (punches.last().punchType == SIRecordType.FINISH) {
            result.finishTime = punches.last().siTime
            punches.removeAt(punches.lastIndex)
        }
    }

    /**
     * Processes the punches - converts PunchData to Punch entity
     */
    fun processCardPunches(
        cardData: CardData,
        raceId: UUID,
        result: Result,
        adjustStart: Boolean,
        zeroTimeBase: LocalTime
    ): ArrayList<Punch> {
        val punches = ArrayList<Punch>()

        var orderCounter = 1
        cardData.punchData.forEach { punchData ->
            val punch = Punch(
                UUID.randomUUID(),
                raceId,
                result.id,
                cardData.siNumber,
                punchData.siCode,
                punchData.siTime,
                punchData.siTime,
                SIRecordType.CONTROL,
                orderCounter,
                PunchStatus.UNKNOWN,
                Duration.ZERO
            )
            punches.add(punch)
            orderCounter++
        }

        if (cardData.cardType == SIConstants.SI_CARD5) {
            card5TimeAdjust(result, punches, adjustStart, zeroTimeBase)
        }
        return punches
    }

    fun calculateSplits(punches: ArrayList<Punch>) {
        //Calculate splits
        punches.forEachIndexed { index, punch ->
            if (index != 0) {
                punch.split = SITime.split(punches[index - 1].siTime, punch.siTime)
            }
        }
    }

    // Attempt to get the start time from the competitor's drawn start time
    // Returns true if start time was found and set, false otherwise
    suspend fun getStartTimeFromStartList(
        result: Result,
        race: Race,
        dataProcessor: DataProcessor
    ): Boolean {
        if (result.competitorId != null) {
            dataProcessor.getCompetitor(result.competitorId!!)?.drawnRelativeStartTime?.let { relativeStartTime ->
                val raceStart = race.startDateTime
                val startTime =
                    TimeProcessor.getAbsoluteDateTimeFromRelativeTime(raceStart, relativeStartTime)
                result.startTime =
                    SITime(startTime.toLocalTime(), SITime.dayOfWeekToSIIndex(startTime.dayOfWeek))
                return true
            }
        }
        return false
    }

    /**
     * Transforms cardData into format for further processing
     */
    suspend fun processCardData(
        cardData: CardData,
        race: Race,
        context: Context,
        dataProcessor: DataProcessor
    ): Boolean {

        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        val preference =
            sharedPref.getString(
                context.getString(R.string.key_readout_duplicate),
                context.getString(R.string.preferences_readout_duplicate_ignore_value)
            ).normalizeDuplicatePreference(context)
        val existingResult = dataProcessor.getResultBySINumber(cardData.siNumber, race.id)
        val exist = (existingResult != null)
        var createNewReadout = false

        // Select action if the readout already exists
        if (exist) {
            when (preference) {

                // Create new readout
                context.getString(R.string.preferences_readout_duplicate_new_value) -> {
                    createNewReadout = true
                    DebugLog.info(
                        "SI",
                        "Duplicate card read will create a new unmatched readout " +
                            "id=${cardData.siNumber} race=${race.id} existingResult=${existingResult.id}"
                    )
                }

                // Replace duplicate
                context.getString(R.string.preferences_readout_duplicate_replace_value) -> {
                    DebugLog.info(
                        "SI",
                        "Duplicate card read will replace existing readout " +
                            "id=${cardData.siNumber} race=${race.id} existingResult=${existingResult.id}"
                    )
                    dataProcessor.deleteResult(existingResult.id)
                }

                // Warn about existing readout
                else -> {
                    val namedUnmatchedReadout =
                        existingResult.competitorId == null && !existingResult.cardName.isNullOrBlank()
                    val duplicateReason = if (namedUnmatchedReadout) {
                        "named-unmatched-readout-exists"
                    } else {
                        "readout-already-exists"
                    }
                    DebugLog.warn(
                        "SI",
                        "Duplicate card read ignored reason=$duplicateReason " +
                            "id=${cardData.siNumber} race=${race.id} existingResult=${existingResult.id}"
                    )
                    // Run on the main UI thread.
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(
                            context,
                            if (namedUnmatchedReadout) {
                                context.getString(
                                    R.string.readout_named_unmatched_si_exists,
                                    cardData.siNumber
                                )
                            } else {
                                context.getString(R.string.readout_si_exists, cardData.siNumber)
                            },
                            Toast.LENGTH_LONG
                        )
                            .show()
                    }
                    if (!namedUnmatchedReadout) {
                        isToMakeSound(context, SoundType.DUPLICATE)
                    }
                    return false
                }
            }
        }

        val competitor = if (!createNewReadout) {
            dataProcessor.getCompetitorBySINumber(cardData.siNumber, race.id)
        } else null

        val category = competitor?.categoryId?.let { dataProcessor.getCategory(it) }

        var drawnTime = false

        //Create the result
        val result =
            Result(
                UUID.randomUUID(),
                race.id,
                competitor?.id,
                if (!createNewReadout) cardData.siNumber else null,
                cardData.cardType,
                cardData.checkTime,
                cardData.startTime,
                cardData.finishTime,
                automaticStatus = false,
                resultStatus = ResultStatus.NO_RANKING,
                runTime = Duration.ZERO,
                modified = false,
                sent = false,
                cardName = cardData.cardName?.takeIf { it.isNotBlank() }
            )

        if (result.startTime == null) {
            drawnTime = getStartTimeFromStartList(result, race, dataProcessor)
        }

        //Process the punches
        val punches = processCardPunches(
            cardData,
            race.id,
            result, drawnTime,
            race.startDateTime.toLocalTime()
        )

        calculateResult(
            result,
            category,
            punches,
            null,
            race,
            dataProcessor
        )

        val shouldPrintFinishTicket = isToPrintFinishTicket(competitor, category, context)
        DebugLog.info(
            "Printer",
            "Automatic finish-ticket print decision result=${result.id} " +
                "si=${result.siNumber} competitorMatched=${competitor != null} " +
                "categoryMatched=${category != null} shouldPrint=$shouldPrintFinishTicket"
        )
        if (shouldPrintFinishTicket) {
            dataProcessor.getRace(result.raceId)?.let { race ->
                CoroutineScope(Dispatchers.IO).launch {
                    val printResult = dataProcessor.printFinishTicket(
                        dataProcessor.getResultData(result.id),
                        race
                    )
                    DebugLog.info(
                        "Printer",
                        "Automatic finish-ticket print result result=${result.id} outcome=$printResult"
                    )
                }
            }
        }

        // Prevent double sounds
        var sound = false

        // Warn about error / unknown with a sound
        if (result.resultStatus == ResultStatus.ERROR ||
            (result.competitorId == null && result.cardName.isNullOrBlank())
        ) {
            dataProcessor.getContext()?.let { isToMakeSound(it, SoundType.ERROR_UNKNOWN) }
            sound = true
        }

        // Inform about rented card
        if (competitor?.siRent == true && !sound) {
            isToMakeSound(context, SoundType.RENT)
        }

        return true
    }

    private fun String?.normalizeDuplicatePreference(context: Context): String =
        when (this) {
            context.getString(R.string.preferences_readout_duplicate_new_value) ->
                context.getString(R.string.preferences_readout_duplicate_new_value)
            context.getString(R.string.preferences_readout_duplicate_replace_value) ->
                context.getString(R.string.preferences_readout_duplicate_replace_value)
            else -> context.getString(R.string.preferences_readout_duplicate_ignore_value)
        }

    // Returns if the ticket should be printed based on the current settings
    private fun isToPrintFinishTicket(
        competitor: Competitor?,
        category: Category?,
        context: Context,
    ): Boolean {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        val preference = sharedPref.getString(
            context.getString(R.string.key_prints_automatic_printout),
            PRINT_AUTOMATIC_MANUALLY_VALUE
        )
        return shouldPrintFinishTicketForPreference(
            preference = preference,
            competitorMatched = competitor != null,
            categoryMatched = category != null
        )
    }

    internal fun shouldPrintFinishTicketForPreference(
        preference: String?,
        competitorMatched: Boolean,
        categoryMatched: Boolean
    ): Boolean =
        when (preference) {
            PRINT_AUTOMATIC_ALWAYS_VALUE,
            PRINT_AUTOMATIC_ALWAYS_LEGACY_VALUE -> true
            PRINT_AUTOMATIC_COMPETITOR_MATCHED_VALUE -> competitorMatched
            PRINT_AUTOMATIC_CATEGORY_MATCHED_VALUE -> competitorMatched && categoryMatched
            else -> false
        }

    private fun isToMakeSound(
        context: Context,
        soundType: SoundType
    ) {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        val enabled =
            sharedPref.getBoolean(
                context.getString(R.string.key_readout_error_sounds),
                true
            )

        if (enabled) {
            SoundProcessor.makeSound(context, soundType)
        }
    }

    suspend fun processManualPunchData(
        result: Result,
        punches: ArrayList<Punch>,
        manualStatus: ResultStatus?,
        race: Race,
        dataProcessor: DataProcessor,
        modified: Boolean
    ) {
        var competitor: Competitor? = null

        if (result.competitorId != null) {
            competitor = dataProcessor.getCompetitor(result.competitorId!!)
        } else if (result.siNumber != null && result.siNumber != 0) {
            competitor = dataProcessor.getCompetitorBySINumber(result.siNumber!!, result.raceId)

            if (competitor != null) {
                result.competitorId = competitor.id
            }
        }

        val category = if (competitor?.categoryId != null) {
            dataProcessor.getCategory(competitor.categoryId!!)
        } else {
            null
        }

        //  Mark the result punches were modified and need to be sent again
        if (modified) {
            result.modified = true
        }
        result.sent = false

        punches.forEachIndexed { order, punch ->
            punch.resultId = result.id
            punch.order = order
        }

        //Modify the start and finish times
        removeStartAndFinishPunch(result, punches)
        DebugLog.info(
            "Results",
            "Manual readout edit requested result=${result.id} si=${result.siNumber} " +
                "manualStatus=${manualStatus?.name ?: "automatic"} modified=$modified " +
                "start=${result.startTime?.getSeconds()} finish=${result.finishTime?.getSeconds()} " +
                "controls=${punches.count { it.punchType == SIRecordType.CONTROL }}"
        )

        calculateResult(
            result,
            category,
            punches,
            manualStatus,
            race,
            dataProcessor,
            repairEditedDayWeek = modified
        )
        DebugLog.info(
            "Results",
            "Manual readout edit saved result=${result.id} status=${result.resultStatus.name} " +
                "automatic=${result.automaticStatus} runTime=${result.runTime.seconds} " +
                "points=${result.points} start=${result.startTime?.getSeconds()} finish=${result.finishTime?.getSeconds()}"
        )
    }

    /* Main method for calculation
     Manual status marks adjustments done by hand (e.g. disqualification)
    */
    suspend fun calculateResult(
        result: Result,
        category: Category?,
        punches: ArrayList<Punch>,
        manualStatus: ResultStatus?,
        race: Race,
        dataProcessor: DataProcessor,
        repairEditedDayWeek: Boolean = false
    ) {
        // If no start time is found in the SI card or edit, try to get it from the competitor.
        if (result.startTime == null) {
            getStartTimeFromStartList(result, race, dataProcessor)
        }

        if (category != null) {
            evaluatePunches(punches, category, result, race, dataProcessor)
        } else {
            clearEvaluation(punches, result)
        }
        // Only editor saves should infer stale SI day/week fields. Fresh SI readouts must keep raw timing
        // so Android and desktop report rollover-looking finish times the same way.
        if (repairEditedDayWeek) {
            repairStaleDayWeekFields(result, punches)
        }
        val runTiming = calculateReadoutRunTiming(
            result.startTime,
            result.finishTime,
            punches.map { it.siTime }
        )

        // Add back start and finish
        if (result.startTime != null) {

            punches.add(
                0,
                Punch(
                    UUID.randomUUID(),
                    result.raceId,
                    result.id,
                    result.siNumber,
                    0,
                    result.startTime!!,
                    result.startTime!!,
                    SIRecordType.START,
                    0,
                    PunchStatus.VALID,
                    Duration.ZERO
                )
            )
        }

        //Add finish punch
        if (result.finishTime != null) {
            punches.add(
                Punch(
                    UUID.randomUUID(),
                    result.raceId,
                    result.id,
                    result.siNumber,
                    0,
                    result.finishTime!!,
                    result.finishTime!!,
                    SIRecordType.FINISH,
                    punches.size,
                    PunchStatus.VALID,
                    Duration.ZERO
                )
            )
        }

        calculateSplits(punches)

        // Result time calculation
        if (!runTiming.blocksResult) {
            result.runTime = Duration.ofSeconds(runTiming.runTimeSeconds)

            // Check time limit
            val timeLimit = race.timeLimit

            if (result.runTime > timeLimit) {
                result.resultStatus = ResultStatus.OVER_TIME_LIMIT
            }

        } else {
            result.runTime = Duration.ZERO
            result.resultStatus = ResultStatus.ERROR
            DebugLog.warn(
                "SI",
                "Invalid readout timing result=${result.id} si=${result.siNumber} " +
                    "status=${runTiming.status} start=${result.startTime} finish=${result.finishTime}"
            )
        }

        // Set the result status based on user preference
        if (manualStatus != null && !runTiming.blocksResult) {
            result.automaticStatus = false
            result.resultStatus = manualStatus
        } else {
            result.automaticStatus = true
        }

        dataProcessor.saveResultPunches(result, punches)
    }

    private fun repairStaleDayWeekFields(result: Result, punches: ArrayList<Punch>) {
        val originalControlSeconds = punches.map { it.siTime.getSeconds() }
        val originalFinishSeconds = result.finishTime?.getSeconds()
        val repaired = SportIdentReadoutTimingRepair.normalizeEditedTimes(
            startSeconds = result.startTime?.getSeconds(),
            controlSeconds = originalControlSeconds,
            finishSeconds = originalFinishSeconds
        )
        if (!repaired.changedFrom(originalControlSeconds, originalFinishSeconds)) {
            return
        }

        repaired.controlSeconds.forEachIndexed { index, seconds ->
            punches[index].siTime = SITime(seconds)
        }
        result.finishTime = repaired.finishSeconds?.let(::SITime)
        DebugLog.info(
            "Results",
            "Repaired stale readout day/week result=${result.id} si=${result.siNumber} " +
                "start=${result.startTime?.getSeconds()} finish=$originalFinishSeconds->${result.finishTime?.getSeconds()} " +
                "controls=${originalControlSeconds.zip(repaired.controlSeconds).count { it.first != it.second }}"
        )
    }

    private suspend fun evaluatePunches(
        punches: ArrayList<Punch>,
        category: Category, result: Result,
        race: Race,
        dataProcessor: DataProcessor
    ) {

        var controlPoints: List<ControlPoint> = ArrayList()
        try {
            controlPoints = dataProcessor.getControlPointsByCategory(category.id)
        } catch (e: Exception) {
            Log.d("ResultsProcess", e.message.toString())
        }
        var controlPointAliases: List<ControlPointAlias> = emptyList()
        try {
            controlPointAliases = dataProcessor.getControlPointAliasesByCategory(category.id)
        } catch (e: Exception) {
            Log.d("ResultsProcess", e.message.toString())
        }
        result.points = 0

        when (race.raceType) {
            RaceType.CLASSIC, RaceType.SHORT, RaceType.FOXORING -> evaluateClassics(
                punches,
                controlPoints,
                result,
                controlPointAliases
            )

            RaceType.SPRINT -> evaluateSprint(
                punches,
                controlPoints,
                result,
                controlPointAliases
            )

            RaceType.ORIENTEERING -> evaluateOrienteering(
                punches,
                controlPoints,
                result,
                controlPointAliases
            )
        }
    }

    /**
     * Updates the already read out data in case of a change in category / competitor
     */
    suspend fun updateResultsForCategory(
        categoryId: UUID,
        race: Race,
        dataProcessor: DataProcessor
    ) {
        val competitors = dataProcessor.getCompetitorsByCategory(categoryId)

        competitors.forEach { competitor ->
            updateResultsForCompetitor(competitor.id, race, dataProcessor)
        }
    }

    suspend fun updateResultsForCompetitor(
        competitorId: UUID,
        race: Race,
        dataProcessor: DataProcessor
    ) {
        var result = dataProcessor.getResultByCompetitor(competitorId)
        val competitor = dataProcessor.getCompetitor(competitorId)

        //Try to get result by SI instead and update competitor ID
        if (result == null && competitor?.siNumber != null) {
            val siResult = dataProcessor.getResultBySINumber(
                competitor.siNumber!!, competitor.raceId
            )
            if (siResult != null) {
                result = siResult
                result.competitorId = competitorId
            }
        }

        //If result is found, recalculate it
        if (result != null) {
            val punches = ArrayList(dataProcessor.getPunchesByResult(result.id))
            val category = competitor?.categoryId?.let { dataProcessor.getCategory(it) }

            if (category == null) {
                clearEvaluation(punches, result)
            }

            removeStartAndFinishPunch(
                result,
                punches
            )  // Remove start and finish punches before calculation

            // In case the manual status was previously set, keep it
            val manualStatus = if (!result.automaticStatus) {
                result.resultStatus
            } else null

            calculateResult(result, category, punches, manualStatus, race, dataProcessor)
        }
    }

    suspend fun getCompetitorPlace(
        competitorId: UUID,
        raceId: UUID,
        dataProcessor: DataProcessor
    ): Int? {
        val results = dataProcessor.getCompetitorDataFlowByRace(raceId)
        val sorted = results.first().groupByCategoryAndSortByPlace()

        // Find the competitor in the sorted results
        return sorted.values.flatten()
            .find { it.competitorCategory.competitor.id == competitorId }?.readoutData?.result?.place

    }

    fun List<CompetitorData>.sortByPlace(): List<CompetitorData> {
        val originalByCompetitorId = associateBy { it.competitorCategory.competitor.id.toString() }
        return EventResultPlacement.sortByPlace(map { it.toEventCompetitorData() })
            .mapNotNull { sharedCompetitorData ->
                val original = originalByCompetitorId[sharedCompetitorData.competitorCategory.competitor.id]
                sharedCompetitorData.readoutData?.let { sharedReadoutData ->
                    original?.readoutData?.result?.place = sharedReadoutData.result.place
                }
                original
            }
    }


    fun List<CompetitorData>.toResultWrappers(): List<ResultWrapper> {
        // Transform each ReadoutData item into a ResultWrapper
        val res = this.filter { it.readoutData != null }.groupByCategoryAndSortByPlace()

        return res.map { result ->
            ResultWrapper(
                category = result.key,
                competitorData = result.value.toMutableList(),
                finished = result.value.size
            )
        }.sortedBy { it.category?.order }
    }

    fun List<CompetitorData>.toReadoutStatistics(): StatisticsWrapper {
        val readoutCount = count { it.readoutData != null }
        return StatisticsWrapper(
            competitors = size,
            startedCompetitors = readoutCount,
            inLimitCompetitors = 0,
            finishedCompetitors = readoutCount
        )
    }

    fun List<CompetitorData>.groupByCategoryAndSortByPlace(): Map<Category?, List<CompetitorData>> {
        val grouped = this.groupBy { it.competitorCategory.category }.toMutableMap()
        grouped.forEach { cg ->
            grouped[cg.key] = cg.value.sortByPlace()
        }
        return grouped
    }

    fun getResultWrapperFlowByRace(
        raceId: UUID,
        dataProcessor: DataProcessor
    ): Flow<List<ResultWrapper>> {
        return dataProcessor.getCompetitorDataFlowByRace(raceId).map { resultDataList ->
            resultDataList.toResultWrappers()
        }
    }

    suspend fun getCompetitorDataByRace(
        raceId: UUID,
        dataProcessor: DataProcessor
    ): List<CompetitorData> {
        val grouped = dataProcessor.getCompetitorDataFlowByRace(raceId).first()
            .groupByCategoryAndSortByPlace()
        return grouped.values.flatten().toList()
    }

    /**
     * Resets all the punches to unknown, e. g. when the category has been deleted
     */
    fun clearEvaluation(punches: ArrayList<Punch>, result: Result) {
        result.points = 0
        punches.forEach { punch ->
            punch.punchStatus = PunchStatus.UNKNOWN
        }
        result.resultStatus = ResultStatus.NO_RANKING
    }

    /**
     * Process the classics race
     */
    fun evaluateClassics(
        punches: ArrayList<Punch>,
        controlPoints: List<ControlPoint>,
        result: Result,
        controlPointAliases: List<ControlPointAlias> = emptyList()
    ) {
        applyCourseEvaluation(RaceType.CLASSIC, punches, controlPoints, result, controlPointAliases)
    }

    /**
     * Process the sprint race
     */
    fun evaluateSprint(
        punches: ArrayList<Punch>,
        controlPoints: List<ControlPoint>,
        result: Result,
        controlPointAliases: List<ControlPointAlias> = emptyList()
    ) {
        applyCourseEvaluation(RaceType.SPRINT, punches, controlPoints, result, controlPointAliases)
    }

    /**
     * Process the orienteering race
     */
    fun evaluateOrienteering(
        punches: ArrayList<Punch>,
        controlPoints: List<ControlPoint>,
        result: Result,
        controlPointAliases: List<ControlPointAlias> = emptyList()
    ) {
        applyCourseEvaluation(RaceType.ORIENTEERING, punches, controlPoints, result, controlPointAliases)
    }

    private fun applyCourseEvaluation(
        raceType: RaceType,
        punches: ArrayList<Punch>,
        controlPoints: List<ControlPoint>,
        result: Result,
        controlPointAliases: List<ControlPointAlias> = emptyList()
    ) {
        val aliasesByControlPointId = controlPointAliases.associateBy { it.controlPoint.id }
        val evaluation = CourseEvaluator.evaluate(
            raceType,
            punches.map { EvaluationPunch(it.siCode, it.punchType) },
            controlPoints.map { controlPoint ->
                EvaluationControlPoint(
                    siCode = controlPoint.siCode,
                    type = controlPoint.type,
                    label = aliasesByControlPointId[controlPoint.id]?.alias?.name
                )
            }
        )
        result.points = evaluation.points
        result.resultStatus = evaluation.resultStatus
        evaluation.punchStatuses.forEachIndexed { index, status ->
            punches[index].punchStatus = status
        }
    }
}
