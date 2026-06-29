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

package org.openardf.radiooracle.backend.commands

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.preference.PreferenceManager
import org.openardf.radiooracle.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.files.DesktopFileTransferUploader
import org.openardf.radiooracle.backend.files.DataImportValidator
import org.openardf.radiooracle.backend.files.constants.DataType
import org.openardf.radiooracle.backend.files.processors.IofXmlProcessor
import org.openardf.radiooracle.backend.logging.DebugLog
import org.openardf.radiooracle.backend.room.ARDFRepository
import org.openardf.radiooracle.backend.room.entity.Category
import org.openardf.radiooracle.backend.room.entity.Competitor
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.room.entity.embeddeds.CategoryData
import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorCategory
import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorData
import org.openardf.radiooracle.backend.room.entity.embeddeds.RaceData
import org.openardf.radiooracle.backend.room.entity.embeddeds.ResultData
import org.openardf.radiooracle.backend.room.enums.RaceBand
import org.openardf.radiooracle.backend.room.enums.RaceLevel
import org.openardf.radiooracle.backend.room.enums.RaceType
import org.openardf.radiooracle.backend.results.ResultsProcessor
import org.openardf.radiooracle.shared.event.EventSeriesPackageFingerprints
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * Debug-only command surface for exercising important app operations from adb.
 *
 * Release builds keep this receiver unexported through the manifest placeholder,
 * and the receiver also refuses commands unless the installed app is debuggable.
 */
class AppCommandReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!context.isDebuggableApp()) {
            Log.w(TAG, "Ignoring command because this build is not debuggable")
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleCommand(context.applicationContext, intent)
            } catch (error: Exception) {
                DebugLog.error(TAG, "Command failed action=${intent.action}: ${error.message}")
                Log.e(TAG, "Command failed action=${intent.action}", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleCommand(context: Context, intent: Intent) {
        val dataProcessor = initializedDataProcessor(context)
        when (intent.action) {
            ACTION_LIST_EVENTS -> listEvents(dataProcessor)
            ACTION_LIST_SERIES -> listSeries(dataProcessor)
            ACTION_DELETE_EVENT -> deleteEvent(dataProcessor, intent)
            ACTION_SELECT_EVENT -> selectEvent(dataProcessor, intent)
            ACTION_CREATE_SERIES_FROM_EVENTS -> createSeriesFromEvents(dataProcessor, intent)
            ACTION_SEND_EVENT_OR_SERIES_TO_DESKTOP -> sendEventOrSeriesToDesktop(dataProcessor, intent)
            ACTION_SEND_SERIES_TO_DESKTOP -> sendSeriesToDesktop(dataProcessor, intent)
            ACTION_LOG_SERIES_PACKAGE_FINGERPRINT -> logSeriesPackageFingerprint(dataProcessor, intent)
            ACTION_RUN_IOF_XML_SMOKE -> runIofXmlSmoke(context, dataProcessor, intent)
            ACTION_PRINT_STATUS -> printStatus(context)
            ACTION_PRINT_FINISH_TICKET -> printFinishTicket(dataProcessor, intent)
            ACTION_PRINT_LATEST_FINISH_TICKET -> printLatestFinishTicket(dataProcessor, intent)
            else -> {
                DebugLog.warn(TAG, "Unknown command action=${intent.action}")
                Log.w(TAG, "Unknown command action=${intent.action}")
            }
        }
    }

    private fun initializedDataProcessor(context: Context): DataProcessor {
        DebugLog.initialize(context)
        ARDFRepository.initialize(context)
        DataProcessor.initialize(context)
        return DataProcessor.get()
    }

    private suspend fun runIofXmlSmoke(context: Context, dataProcessor: DataProcessor, intent: Intent) {
        val externalRoot = context.getExternalFilesDir(null) ?: context.filesDir
        val fixtureDir = context.commandFile(
            intent.getStringExtra(EXTRA_FIXTURE_DIR)
                ?: File(externalRoot, "iof-smoke/input").absolutePath
        )
        val outputDir = context.commandFile(
            intent.getStringExtra(EXTRA_OUTPUT_DIR)
                ?: File(externalRoot, "iof-smoke/output").absolutePath
        ).also { it.mkdirs() }
        val keepEvent = intent.getBooleanExtra(EXTRA_KEEP_EVENT, true)
        val summary = mutableListOf<String>()
        val raceData = iofSmokeRaceData()
        val race = raceData.race

        fun fixtureFile(name: String): File =
            File(fixtureDir, name).also { file ->
                require(file.isFile) { "IOF smoke fixture not found: ${file.absolutePath}" }
            }

        fun appendImportWarnings(label: String, warnings: List<String>) {
            warnings.forEach { warning ->
                summary += "$label warning=$warning"
            }
        }

        try {
            dataProcessor.saveRaceData(raceData)
            dataProcessor.setCurrentRace(race.id)
            summary += "event id=${race.id} name=${race.name}"

            val courseImport = IofXmlProcessor.importData(
                fixtureFile("course.xml").inputStream(),
                DataType.CATEGORIES,
                race,
                dataProcessor
            )
            DataImportValidator.validateDataImport(courseImport, race.id, DataType.CATEGORIES, dataProcessor, context)
            dataProcessor.saveDataImportWrapper(courseImport)
            summary += "courseData importedCategories=${courseImport.categories.size}"
            appendImportWarnings("courseData", courseImport.iofWarnings)
            val resultCategory = dataProcessor.getCategoryByName("A", race.id)
                ?: error("IOF smoke CourseData did not import category A.")
            dataProcessor.createOrUpdateCompetitor(iofSmokeResultCompetitor(race.id, resultCategory.id))
            summary += "courseData resultCompetitorCategory=${resultCategory.name}"

            val startImport = IofXmlProcessor.importData(
                fixtureFile("start.xml").inputStream(),
                DataType.COMPETITOR_STARTS,
                race,
                dataProcessor
            )
            require(startImport.invalidLines.isEmpty()) {
                startImport.invalidLines.joinToString(prefix = "StartList import failed: ") { "${it.first}:${it.second}" }
            }
            DataImportValidator.validateDataImport(startImport, race.id, DataType.COMPETITOR_STARTS, dataProcessor, context)
            startImport.competitorCategories.forEach { dataProcessor.createOrUpdateCompetitor(it.competitor) }
            summary += "startList updatedCompetitors=${startImport.competitorCategories.size}"
            appendImportWarnings("startList", startImport.iofWarnings)

            val resultImport = IofXmlProcessor.importData(
                fixtureFile("results.xml").inputStream(),
                DataType.RESULTS_LIVE,
                race,
                dataProcessor
            )
            require(resultImport.invalidLines.isEmpty()) {
                resultImport.invalidLines.joinToString(prefix = "ResultList import failed: ") { "${it.first}:${it.second}" }
            }
            DataImportValidator.validateDataImport(resultImport, race.id, DataType.RESULTS_LIVE, dataProcessor, context)
            resultImport.readoutData.forEach { readout ->
                dataProcessor.saveResultPunches(readout.result, readout.punches.map { it.punch })
            }
            dataProcessor.updateResultsByRace(race.id)
            summary += "resultList importedReadouts=${resultImport.readoutData.size}"
            appendImportWarnings("resultList", resultImport.iofWarnings)

            val startListOutput = File(outputDir, "exported-start-list.xml")
            ByteArrayOutputStream().use { output ->
                IofXmlProcessor.exportStartList(output, race, dataProcessor.getCategoryDataForRace(race.id), dataProcessor)
                startListOutput.writeBytes(output.toByteArray())
            }

            val resultListOutput = File(outputDir, "exported-result-list.xml")
            val resultWrappers = ResultsProcessor.getResultWrapperFlowByRace(race.id, dataProcessor)
                .first()
                .filter { it.category != null }
            ByteArrayOutputStream().use { output ->
                IofXmlProcessor.exportResults(output, race, resultWrappers, dataProcessor)
                resultListOutput.writeBytes(output.toByteArray())
            }

            summary += "exports startList=${startListOutput.absolutePath}"
            summary += "exports resultList=${resultListOutput.absolutePath}"
            summary += "manualPickerFiles expectedUnder=/sdcard/Download/RadioOracleIofSmoke"
            summary += "status=ok"
        } finally {
            if (!keepEvent) {
                dataProcessor.deleteRace(race.id)
                summary += "event deleted=${race.id}"
            }
            val summaryFile = File(outputDir, "smoke-summary.txt")
            summaryFile.writeText(summary.joinToString(separator = "\n", postfix = "\n"))
            summary.forEach { line ->
                DebugLog.info(TAG, "IOF smoke $line")
                Log.i(TAG, "IOF smoke $line")
            }
        }
    }

    private fun iofSmokeRaceData(): RaceData {
        val raceId = UUID.randomUUID()
        val m21CategoryId = UUID.randomUUID()
        val race = Race(
            id = raceId,
            name = "IOF XML Smoke ${System.currentTimeMillis()}",
            apiKey = "",
            startDateTime = LocalDateTime.of(2023, 6, 15, 9, 30),
            raceType = RaceType.CLASSIC,
            raceLevel = RaceLevel.PRACTICE,
            raceBand = RaceBand.M80,
            timeLimit = Duration.ZERO
        )
        val m21 = Category(
            id = m21CategoryId,
            raceId = raceId,
            name = "M21",
            isMan = true,
            maxAge = null,
            length = 5_200,
            climb = 120,
            order = 1,
            controlPointsString = ""
        )
        val competitors = listOf(
            Competitor(
                id = UUID.randomUUID(),
                raceId = raceId,
                categoryId = m21CategoryId,
                firstName = "Jan",
                lastName = "Novak",
                club = "Club A",
                index = "IDX1",
                isMan = true,
                birthYear = 1990,
                siNumber = null,
                siRent = false,
                startNumber = 1,
                drawnRelativeStartTime = null
            ),
            Competitor(
                id = UUID.randomUUID(),
                raceId = raceId,
                categoryId = m21CategoryId,
                firstName = "Petr",
                lastName = "Svoboda",
                club = "Club B",
                index = "IDX2",
                isMan = true,
                birthYear = 1992,
                siNumber = null,
                siRent = false,
                startNumber = 2,
                drawnRelativeStartTime = null
            )
        )
        return RaceData(
            race = race,
            categories = listOf(
                CategoryData(m21, emptyList(), competitors.filter { it.categoryId == m21CategoryId })
            ),
            aliases = emptyList(),
            competitorData = competitors.map { competitor ->
                CompetitorData(CompetitorCategory(competitor, m21), readoutData = null)
            },
            unmatchedReadoutData = emptyList()
        )
    }

    private fun iofSmokeResultCompetitor(raceId: UUID, categoryId: UUID): Competitor =
        Competitor(
            id = UUID.randomUUID(),
            raceId = raceId,
            categoryId = categoryId,
            firstName = "Test",
            lastName = "Tester",
            club = "AC-Test",
            index = "ACT0001",
            isMan = true,
            birthYear = 2000,
            siNumber = 123456789,
            siRent = false,
            startNumber = 3,
            drawnRelativeStartTime = null
        )

    private fun Context.commandFile(path: String): File =
        File(path).let { file ->
            if (file.isAbsolute) file else File(filesDir, path)
        }

    private suspend fun listEvents(dataProcessor: DataProcessor) {
        val races = dataProcessor.getRaces().first().sortedBy { it.startDateTime }
        DebugLog.info(TAG, "Command listed events count=${races.size}")
        Log.i(TAG, "events count=${races.size}")
        races.forEach { race ->
            val message = "event id=${race.id} name=${race.name} source=${race.importSourceId ?: "local"}"
            DebugLog.info(TAG, message)
            Log.i(TAG, message)
        }
    }

    private suspend fun listSeries(dataProcessor: DataProcessor) {
        val seriesList = dataProcessor.getEventSeries().first().sortedBy { it.series.name }
        DebugLog.info(TAG, "Command listed series count=${seriesList.size}")
        Log.i(TAG, "series count=${seriesList.size}")
        seriesList.forEach { seriesData ->
            val members = seriesData.orderedMembers()
            val message = "series id=${seriesData.series.seriesId} name=${seriesData.series.name} members=${members.size}"
            DebugLog.info(TAG, message)
            Log.i(TAG, message)
            members.forEach { member ->
                val race = dataProcessor.getRace(member.localRaceId)
                val memberMessage = "series-member series=${seriesData.series.seriesId} " +
                    "event=${member.seriesEventId} localRace=${member.localRaceId} " +
                    "name=${race?.name ?: member.displayName}"
                DebugLog.info(TAG, memberMessage)
                Log.i(TAG, memberMessage)
            }
        }
    }

    private suspend fun deleteEvent(dataProcessor: DataProcessor, intent: Intent) {
        val eventId = intent.uuidExtra() ?: return missingEventId()
        val race = dataProcessor.getRace(eventId)
        if (race == null) {
            DebugLog.warn(TAG, "Command delete ignored missing event=$eventId")
            Log.w(TAG, "event not found id=$eventId")
            return
        }

        dataProcessor.deleteRace(eventId)
        DebugLog.info(TAG, "Command deleted event=$eventId name=${race.name}")
        Log.i(TAG, "deleted event id=$eventId name=${race.name}")
    }

    private suspend fun selectEvent(dataProcessor: DataProcessor, intent: Intent) {
        val eventId = intent.uuidExtra() ?: return missingEventId()
        val race = dataProcessor.setCurrentRace(eventId)
        if (race == null) {
            DebugLog.warn(TAG, "Command select ignored missing event=$eventId")
            Log.w(TAG, "event not found id=$eventId")
            return
        }

        DebugLog.info(TAG, "Command selected event=$eventId name=${race.name}")
        Log.i(TAG, "selected event id=$eventId name=${race.name}")
    }

    private suspend fun createSeriesFromEvents(dataProcessor: DataProcessor, intent: Intent) {
        val seriesName = intent.getStringExtra(EXTRA_SERIES_NAME)?.trim()?.takeIf { it.isNotBlank() }
            ?: return missingSeriesName()
        val eventIds = AppCommandEventIds.parse(intent.getStringExtra(EXTRA_EVENT_IDS))
            ?: return missingEventIds()
        val races = eventIds.map { eventId ->
            dataProcessor.getRace(eventId) ?: run {
                DebugLog.warn(TAG, "Command create series ignored missing event=$eventId")
                Log.w(TAG, "event not found id=$eventId")
                return
            }
        }

        var seriesData = dataProcessor.createEventSeriesFromRace(races.first().id, seriesName)
        races.drop(1).forEach { race ->
            seriesData = dataProcessor.addRaceToEventSeries(race.id, seriesData.series.seriesId)
        }

        val message = "created series id=${seriesData.series.seriesId} name=${seriesData.series.name} " +
            "members=${seriesData.members.size}"
        DebugLog.info(TAG, "Command $message")
        Log.i(TAG, message)
    }

    private suspend fun sendEventOrSeriesToDesktop(dataProcessor: DataProcessor, intent: Intent) {
        val eventId = intent.uuidExtra() ?: return missingEventId()
        val receiveUrl = intent.desktopReceiveUrl() ?: return
        val upload = dataProcessor.desktopUploadForRaceOrSeries(eventId)
        DesktopFileTransferUploader().upload(receiveUrl, upload)
        val message = "sent event-or-series id=$eventId file=${upload.fileName} " +
            "contentType=${upload.contentType} bytes=${upload.bytes.size}"
        DebugLog.info(TAG, "Command $message")
        Log.i(TAG, message)
    }

    private suspend fun sendSeriesToDesktop(dataProcessor: DataProcessor, intent: Intent) {
        val seriesId = intent.getStringExtra(EXTRA_SERIES_ID)?.takeIf { it.isNotBlank() } ?: return missingSeriesId()
        val receiveUrl = intent.desktopReceiveUrl() ?: return
        val upload = dataProcessor.desktopUploadForSeries(seriesId)
        DesktopFileTransferUploader().upload(receiveUrl, upload)
        val message = "sent series id=$seriesId file=${upload.fileName} " +
            "contentType=${upload.contentType} bytes=${upload.bytes.size}"
        DebugLog.info(TAG, "Command $message")
        Log.i(TAG, message)
    }

    private suspend fun logSeriesPackageFingerprint(dataProcessor: DataProcessor, intent: Intent) {
        val seriesId = intent.getStringExtra(EXTRA_SERIES_ID)?.takeIf { it.isNotBlank() }
        val eventId = intent.uuidExtra(EXTRA_EVENT_ID)
        val source: String
        val bytes: ByteArray
        if (seriesId != null) {
            source = "series:$seriesId"
            bytes = dataProcessor.exportEventSeriesPackageBytes(seriesId)
        } else if (eventId != null) {
            source = "event:$eventId"
            bytes = dataProcessor.exportEventSeriesPackageBytesForRace(eventId)
                ?: return eventWithoutSeries(eventId)
        } else {
            return missingSeriesOrEventId()
        }

        val fingerprint = EventSeriesPackageFingerprints.fromTextEntries(unzipTextEntries(bytes))
        EventSeriesCommandFingerprintLog.lines(source, bytes.size, fingerprint).forEach { line ->
            DebugLog.info(TAG, "Command $line")
            Log.i(TAG, line)
        }
    }

    private suspend fun printStatus(context: Context) {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        val enabled = sharedPref.getBoolean(context.getString(R.string.key_prints_enabled), false)
        val automaticMode = sharedPref.getString(context.getString(R.string.key_prints_automatic_printout), "")
        val printerName = sharedPref.getString(context.getString(R.string.key_prints_selected_printer_name), "")
        val printerAddress = sharedPref.getString(context.getString(R.string.key_prints_selected_printer_address), "")
        val doublePrint = sharedPref.getBoolean(context.getString(R.string.key_prints_double_print), false)
        val doublePrintDelay = sharedPref.getInt(context.getString(R.string.key_prints_double_print_delay), 0)
        val removeDiacritics = sharedPref.getBoolean(context.getString(R.string.key_prints_remove_diacritics), false)
        val message = "print status enabled=$enabled automatic=$automaticMode printer=$printerName " +
            "address=${printerAddress.orEmpty().maskBluetoothAddress()} doublePrint=$doublePrint " +
            "doublePrintDelay=$doublePrintDelay removeDiacritics=$removeDiacritics"

        DebugLog.info(TAG, message)
        Log.i(TAG, message)
    }

    private suspend fun printFinishTicket(dataProcessor: DataProcessor, intent: Intent) {
        val resultId = intent.uuidExtra(EXTRA_RESULT_ID) ?: return missingResultId()
        val resultData = dataProcessor.getResultData(resultId)
        val race = dataProcessor.getRace(resultData.result.raceId)
        if (race == null) {
            DebugLog.warn(TAG, "Command print ignored missing race=${resultData.result.raceId} result=$resultId")
            Log.w(TAG, "race not found id=${resultData.result.raceId} result=$resultId")
            return
        }
        printFinishTicket(dataProcessor, race, resultData, "result id=$resultId")
    }

    private suspend fun printLatestFinishTicket(dataProcessor: DataProcessor, intent: Intent) {
        val race = intent.uuidExtra(EXTRA_EVENT_ID)?.let { eventId ->
            dataProcessor.getRace(eventId).also {
                if (it == null) {
                    DebugLog.warn(TAG, "Command print latest ignored missing event=$eventId")
                    Log.w(TAG, "event not found id=$eventId")
                }
            }
        } ?: latestRace(dataProcessor)

        if (race == null) {
            DebugLog.warn(TAG, "Command print latest ignored because no events exist")
            Log.w(TAG, "no events available")
            return
        }

        val resultData = dataProcessor.getResultDataFlowByRace(race.id)
            .first()
            .maxByOrNull { it.result.readoutTime }
        if (resultData == null) {
            DebugLog.warn(TAG, "Command print latest ignored because event has no readouts event=${race.id}")
            Log.w(TAG, "event has no readouts id=${race.id}")
            return
        }
        printFinishTicket(dataProcessor, race, resultData, "latest event=${race.id}")
    }

    private suspend fun latestRace(dataProcessor: DataProcessor): Race? =
        dataProcessor.getRaces().first().maxByOrNull { it.startDateTime }

    private suspend fun printFinishTicket(
        dataProcessor: DataProcessor,
        race: Race,
        resultData: ResultData,
        source: String
    ) {
        DebugLog.info(
            TAG,
            "Command printing finish ticket source=$source result=${resultData.result.id} " +
                "si=${resultData.result.siNumber} race=${race.id}"
        )
        Log.i(TAG, "printing finish ticket source=$source result=${resultData.result.id} race=${race.id}")
        val printResult = dataProcessor.printFinishTicket(resultData, race)
        DebugLog.info(TAG, "Command print finish ticket outcome=$printResult result=${resultData.result.id}")
        Log.i(TAG, "print finish ticket outcome=$printResult result=${resultData.result.id}")
    }

    private fun Intent.uuidExtra(name: String): UUID? =
        getStringExtra(name)?.let { rawValue ->
            runCatching { UUID.fromString(rawValue) }.getOrNull()
        }

    private fun Intent.uuidExtra(): UUID? =
        uuidExtra(EXTRA_EVENT_ID)

    private fun Intent.desktopReceiveUrl(): String? =
        getStringExtra(EXTRA_DESKTOP_RECEIVE_URL)?.takeIf { it.isNotBlank() } ?: run {
            DebugLog.warn(TAG, "Command send ignored missing $EXTRA_DESKTOP_RECEIVE_URL")
            Log.w(TAG, "missing $EXTRA_DESKTOP_RECEIVE_URL")
            null
        }

    private fun missingEventId() {
        DebugLog.warn(TAG, "Command missing or invalid $EXTRA_EVENT_ID")
        Log.w(TAG, "missing or invalid $EXTRA_EVENT_ID")
    }

    private fun missingSeriesId() {
        DebugLog.warn(TAG, "Command missing or invalid $EXTRA_SERIES_ID")
        Log.w(TAG, "missing or invalid $EXTRA_SERIES_ID")
    }

    private fun missingSeriesOrEventId() {
        DebugLog.warn(TAG, "Command missing $EXTRA_SERIES_ID or valid $EXTRA_EVENT_ID")
        Log.w(TAG, "missing $EXTRA_SERIES_ID or valid $EXTRA_EVENT_ID")
    }

    private fun missingSeriesName() {
        DebugLog.warn(TAG, "Command missing $EXTRA_SERIES_NAME")
        Log.w(TAG, "missing $EXTRA_SERIES_NAME")
    }

    private fun missingEventIds() {
        DebugLog.warn(TAG, "Command missing or invalid $EXTRA_EVENT_IDS")
        Log.w(TAG, "missing or invalid $EXTRA_EVENT_IDS")
    }

    private fun eventWithoutSeries(eventId: UUID) {
        DebugLog.warn(TAG, "Command fingerprint ignored because event is not in a series event=$eventId")
        Log.w(TAG, "event is not in a series id=$eventId")
    }

    private fun missingResultId() {
        DebugLog.warn(TAG, "Command missing or invalid $EXTRA_RESULT_ID")
        Log.w(TAG, "missing or invalid $EXTRA_RESULT_ID")
    }

    private fun unzipTextEntries(bytes: ByteArray): Map<String, String> =
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            buildMap {
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory) {
                        put(entry.name, zip.readBytes().toString(Charsets.UTF_8))
                    }
                    zip.closeEntry()
                }
            }
        }

    private fun String.maskBluetoothAddress(): String =
        split(":").let { parts ->
            if (parts.size == 6) {
                "xx:xx:xx:${parts.takeLast(3).joinToString(":")}"
            } else if (isBlank()) {
                "<none>"
            } else {
                "<selected>"
            }
        }

    private fun Context.isDebuggableApp(): Boolean =
        (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    companion object {
        private const val TAG = "AppCommand"
        const val ACTION_LIST_EVENTS = "org.openardf.radiooracle.command.LIST_EVENTS"
        const val ACTION_LIST_SERIES = "org.openardf.radiooracle.command.LIST_SERIES"
        const val ACTION_DELETE_EVENT = "org.openardf.radiooracle.command.DELETE_EVENT"
        const val ACTION_SELECT_EVENT = "org.openardf.radiooracle.command.SELECT_EVENT"
        const val ACTION_CREATE_SERIES_FROM_EVENTS =
            "org.openardf.radiooracle.command.CREATE_SERIES_FROM_EVENTS"
        const val ACTION_SEND_EVENT_OR_SERIES_TO_DESKTOP =
            "org.openardf.radiooracle.command.SEND_EVENT_OR_SERIES_TO_DESKTOP"
        const val ACTION_SEND_SERIES_TO_DESKTOP = "org.openardf.radiooracle.command.SEND_SERIES_TO_DESKTOP"
        const val ACTION_LOG_SERIES_PACKAGE_FINGERPRINT =
            "org.openardf.radiooracle.command.LOG_SERIES_PACKAGE_FINGERPRINT"
        const val ACTION_RUN_IOF_XML_SMOKE = "org.openardf.radiooracle.command.RUN_IOF_XML_SMOKE"
        const val ACTION_PRINT_STATUS = "org.openardf.radiooracle.command.PRINT_STATUS"
        const val ACTION_PRINT_FINISH_TICKET = "org.openardf.radiooracle.command.PRINT_FINISH_TICKET"
        const val ACTION_PRINT_LATEST_FINISH_TICKET = "org.openardf.radiooracle.command.PRINT_LATEST_FINISH_TICKET"
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_EVENT_IDS = "event_ids"
        const val EXTRA_SERIES_ID = "series_id"
        const val EXTRA_SERIES_NAME = "series_name"
        const val EXTRA_RESULT_ID = "result_id"
        const val EXTRA_DESKTOP_RECEIVE_URL = "desktop_receive_url"
        const val EXTRA_FIXTURE_DIR = "fixture_dir"
        const val EXTRA_OUTPUT_DIR = "output_dir"
        const val EXTRA_KEEP_EVENT = "keep_event"
    }
}
