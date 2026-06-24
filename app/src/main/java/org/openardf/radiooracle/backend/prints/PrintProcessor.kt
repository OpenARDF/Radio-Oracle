package org.openardf.radiooracle.backend.prints

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.dantsu.escposprinter.EscPosCharsetEncoding
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.helpers.TimeProcessor
import org.openardf.radiooracle.backend.logging.DebugLog
import org.openardf.radiooracle.backend.room.entity.Competitor
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.room.entity.embeddeds.AliasPunch
import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorData
import org.openardf.radiooracle.backend.room.entity.embeddeds.ResultData
import org.openardf.radiooracle.backend.wrappers.ResultWrapper
import org.openardf.radiooracle.shared.domain.PunchStatus
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.shared.domain.toResultStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.text.Normalizer

enum class PrintAttemptResult {
    PRINTED,
    NEEDS_SETUP,
    FAILED
}


class PrintProcessor(context: Context, private val dataProcessor: DataProcessor) {
    private val appContext: WeakReference<Context> = WeakReference(context)
    private var printerReady: Boolean = false
    private var printer: EscPosPrinter? = null

    private fun preparePrinter(): PrinterSetupStatus {
        if (printerReady) {
            return PrinterSetupStatus.READY
        }

        val context = appContext.get() ?: return PrinterSetupStatus.FAILED
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        val enabled = sharedPref.getBoolean(
            context.getString(R.string.key_prints_enabled), false
        )
        if (!enabled) {
            logInfo("Printing disabled in preferences")
            return PrinterSetupStatus.NEEDS_SETUP
        }
        val address = sharedPref.getString(
            context.getString(R.string.key_prints_selected_printer_address), ""
        )
        if (address.isNullOrEmpty()) {
            logWarn("Printing enabled but no Bluetooth printer address is selected")
            return PrinterSetupStatus.NEEDS_SETUP
        }

        val name = sharedPref.getString(
            context.getString(R.string.key_prints_selected_printer_name), ""
        )
        logInfo("Initializing Bluetooth printer name=${name.orEmpty()} address=${address.maskBluetoothAddress()}")
        val bluetoothAdapter = (context.getSystemService(android.bluetooth.BluetoothManager::class.java))?.adapter
        if (bluetoothAdapter == null) {
            logWarn("Bluetooth adapter is unavailable")
            return PrinterSetupStatus.NEEDS_SETUP
        }
        if (!bluetoothAdapter.isEnabled) {
            logWarn("Bluetooth adapter is disabled")
            makeToast(
                appContext.get()?.getString(R.string.prints_bluetooth_disabled)
                    ?: "Bluetooth disabled"
            )
            return PrinterSetupStatus.NEEDS_SETUP
        }

        return try {
            printer =
                EscPosPrinter(
                    BluetoothConnection(bluetoothAdapter.getRemoteDevice(address)),
                    203,
                    58f,
                    32,
                    EscPosCharsetEncoding("windows-1250", 72)
                )   // TODO: fix charset encoding
            printerReady = true
            logInfo("Bluetooth printer initialized")
            PrinterSetupStatus.READY
        } catch (e: Exception) {
            logError("Bluetooth printer init failed: ${e.message ?: e::class.simpleName}")
            makeToast(context.getString(R.string.prints_error_init))
            printerReady = false
            PrinterSetupStatus.NEEDS_SETUP
        }
    }

    fun disablePrinter() {
        printerReady = false
        logInfo("Bluetooth printer disabled")
    }

    private suspend fun print(formatted: String): PrintAttemptResult {
        val context = appContext.get()!!

        val setupStatus = preparePrinter()
        if (setupStatus != PrinterSetupStatus.READY) {
            return when (setupStatus) {
                PrinterSetupStatus.NEEDS_SETUP -> PrintAttemptResult.NEEDS_SETUP
                PrinterSetupStatus.FAILED -> PrintAttemptResult.FAILED
                PrinterSetupStatus.READY -> PrintAttemptResult.PRINTED
            }
        }

        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        val preference =
            sharedPref.getBoolean(
                context.getString(R.string.key_prints_remove_diacritics),
                false
            )
        val version = "Radio-Oracle v${dataProcessor.getAppVersion()}"

        // Remove diacritics if the preference is set
        val textToPrint = if (preference) {
            removeDiacritics(formatted)
        } else {
            formatted
        }

        return try {
            logInfo("Submitting ESC/POS print job lines=${textToPrint.lines().size}")
            printer!!.printFormattedText(textToPrint + "\n\n[C]${version}", 100)
            logInfo("ESC/POS print job submitted")
            PrintAttemptResult.PRINTED
        } catch (e: Exception) {
            logError("ESC/POS print failed: ${e.message ?: e::class.simpleName}")
            makeToast(
                appContext.get()?.getString(R.string.prints_error, e.message)
                    ?: "Failed to print"
            )
            printerReady = false
            PrintAttemptResult.FAILED
        }
    }

    private fun removeDiacritics(text: String): String {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
    }

    private fun makeToast(message: String) {
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(
                appContext.get(), message, Toast.LENGTH_LONG
            ).show()
        }
    }

    suspend fun printFinishTicket(resultData: ResultData, race: Race): PrintAttemptResult {
        val context = appContext.get()!!
        val competitor = resultData.competitorCategory?.competitor
        val category = resultData.competitorCategory?.category?.name ?: "?"
        val punches = getPunchesFormatted(resultData.punches, race.raceType)
        val siNumber = "SI: ${resultData.result.siNumber ?: "?"}"
        val bibNumber = competitor?.index?.takeIf { it.isNotBlank() }

        val score = "[R]Score: ${resultData.result.points}"
        val status =
            "[R]${context.getString(R.string.general_status)}: ${resultData.result.resultStatus.toResultStatusCode()}"

        val runTime = "${context.getString(R.string.general_run_time)}: " +
                TimeProcessor.durationToFormattedString(
                    resultData.result.runTime,
                    dataProcessor.useMinuteTimeFormat()
                )

        val formatted = "[C]<b>${race.name}</b>\n" +
                "[L]\n" +
                "[L]${getMaxCompetitorName(resultData)}\n" +
                "[L]$siNumber\n" +
                (bibNumber?.let { "[L]Bib: $it\n" } ?: "") +
                "[L]${context.getString(R.string.general_category)}: $category\n\n" +
                punches + "\n\n" +
                "[R]<b>$runTime</b>\n" +
                "$score\n" +
                "$status\n"

        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        val doublePrint =
            sharedPref.getBoolean(
                context.getString(R.string.key_prints_double_print),
                false
            )
        val doublePrintDelay =
            sharedPref.getInt(
                context.getString(R.string.key_prints_double_print_delay),
                2
            )

        logInfo(
            "Printing finish ticket result=${resultData.result.id} si=${resultData.result.siNumber} " +
                "doublePrint=$doublePrint delaySeconds=$doublePrintDelay"
        )
        val firstPrint = print(formatted)
        if (firstPrint != PrintAttemptResult.PRINTED) {
            return firstPrint
        }
        if (doublePrint) {
            delay(doublePrintDelay * 1000L)
            return print(formatted)
        }
        return PrintAttemptResult.PRINTED
    }

    private fun getMaxCompetitorName(resultData: ResultData): String {
        val maxLength = getCharactersPerLine()
        val fullName = resultData.competitorCategory?.competitor?.getFullName()
            ?: resultData.result.cardName
            ?: "?"
        return if (fullName.length > maxLength) {
            fullName.take(maxLength)
        } else {
            fullName
        }
    }

    private fun getPunchesFormatted(punches: List<AliasPunch>, raceType: RaceType): String {
        return punches.joinToString("\n") { p -> getAliasPunchFormatted(p, raceType) }
    }

    private fun getAliasPunchFormatted(aliasPunch: AliasPunch, raceType: RaceType): String {
        when (aliasPunch.punch.punchType) {
            SIRecordType.START -> {
                return "[L]${
                    formatTimeRow(
                        appContext.get()?.getString(R.string.general_start) ?: "Start",
                        aliasPunch.punch.siTime.getTimeString(),
                        null
                    )
                }"
            }

            SIRecordType.FINISH -> {
                return "[L]${
                    formatTimeRow(
                        appContext.get()?.getString(R.string.general_finish) ?: "Finish",
                        aliasPunch.punch.siTime.getTimeString(),
                            TimeProcessor.durationToFormattedString(
                                aliasPunch.punch.split, dataProcessor.useMinuteTimeFormat()
                            )
                    )
                }"
            }

            SIRecordType.CONTROL -> {
                return "[L]${
                    formatTimeRow(
                        formatCodeString(aliasPunch, raceType),
                        aliasPunch.punch.siTime.getTimeString(),
                            TimeProcessor.durationToFormattedString(
                                aliasPunch.punch.split, dataProcessor.useMinuteTimeFormat()
                            )
                    )
                }"
            }

            else -> {
                return ""
            }
        }
    }

    private fun formatCodeString(aliasPunch: AliasPunch, raceType: RaceType): String {
        val context = appContext.get()!!

        val symbol = when (aliasPunch.punch.punchStatus) {
            PunchStatus.VALID -> context.getString(R.string.punch_status_valid)
            PunchStatus.INVALID -> context.getString(R.string.punch_status_invalid)
            PunchStatus.DUPLICATE -> context.getString(R.string.punch_status_duplicate)
            PunchStatus.UNKNOWN -> context.getString(R.string.punch_status_unknown)
        }
        val code = if (raceType == RaceType.ORIENTEERING || shouldUseAliases()) {
            "${aliasPunch.punch.order} (${aliasPunch.alias?.name ?: aliasPunch.punch.siCode})"
        } else {
            aliasPunch.punch.siCode.toString()
        }
        return "$code$symbol"
    }

    private fun formatTimeRow(label: String, time: String, split: String?): String {
        val charactersPerLine = getCharactersPerLine()
        val timeWidth = 8
        val splitWidth = split?.length?.coerceAtLeast(8) ?: 8
        val labelWidth = charactersPerLine - timeWidth - splitWidth - 2
        if (labelWidth < 1) {
            return listOfNotNull(label, time, split).joinToString(" ").take(charactersPerLine)
        }
        if (split == null) {
            return label.take(labelWidth).padEnd(labelWidth) +
                    " " + time.takeLast(timeWidth).padStart(timeWidth)
        }

        return label.take(labelWidth).padEnd(labelWidth) +
                " " + time.takeLast(timeWidth).padStart(timeWidth) +
                " " + split.padStart(splitWidth)
    }

    private fun shouldUseAliases(): Boolean {
        val context = appContext.get() ?: return true
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPref.getBoolean(context.getString(R.string.key_results_use_aliases), true)
    }

    suspend fun printResults(results: List<ResultWrapper>, race: Race): PrintAttemptResult {
        val formatted = formatResultsHeader(race) + getResultsFormatted(results)
        return print(formatted)
    }

    private fun formatResultsHeader(race: Race): String {
        return "[C]<font size='big'>${race.name}</font>\n" + "[C]${
            TimeProcessor.formatLocalDate(
                race.startDateTime.toLocalDate()
            )
        }" + "\n\n"
    }

    private fun getResultsFormatted(results: List<ResultWrapper>): String {
        val sb = StringBuilder()

        results.forEachIndexed { index, result ->
            if (result.category != null) {
                sb.append(formatCategoryHeader(result))

                // Format each competitor's result
                result.competitorData.forEach { competitorData ->
                    if (competitorData.readoutData?.result != null) {

                        // Format the single result
                        sb.append(formatSingleResult(competitorData))
                        sb.append("\n")
                    }
                }
                if (index < results.size - 1) {
                    sb.append("\n")
                }
            }
        }
        return sb.toString()
    }

    private fun formatCategoryHeader(resultWrapper: ResultWrapper): String {
        val catHead = "<b>${resultWrapper.displayLabel ?: resultWrapper.category?.name}</b>"
        return "${catHead}\n" + formatHorizontalLine()
    }

    private fun formatSingleResult(competitorData: CompetitorData): String {
        val result = competitorData.readoutData?.result!!
        var place = result.place.toString()
        var runTime = TimeProcessor.durationToFormattedString(
            result.runTime,
            dataProcessor.useMinuteTimeFormat()
        )
        val name = getMaxName(competitorData.competitorCategory.competitor.getFullName())

        if (result.resultStatus != ResultStatus.OK) {
            place = "-"
            runTime = dataProcessor.resultStatusToShortString(result.resultStatus)
        }

        return "[L]$place[L]$name[R]$runTime"
    }

    private fun formatHorizontalLine(): String {
        val lineLength = getCharactersPerLine()
        return "[C]${"-".repeat(lineLength)}\n"
    }

    private fun getCharactersPerLine(): Int {
        return if (printer != null) {
            printer!!.printerNbrCharactersPerLine
        } else {
            32 // Default width for ESC/POS printers
        }
    }

    private fun getMaxName(name: String): String {
        val maxLength = getCharactersPerLine()
        return if (name.length > maxLength) {
            name.substring(0, maxLength)
        } else {
            name
        }
    }

    private fun logInfo(message: String) {
        Log.i(LOG_TAG, message)
        DebugLog.info(LOG_TAG, message)
    }

    private fun logWarn(message: String) {
        Log.w(LOG_TAG, message)
        DebugLog.warn(LOG_TAG, message)
    }

    private fun logError(message: String) {
        Log.e(LOG_TAG, message)
        DebugLog.error(LOG_TAG, message)
    }

    private fun String.maskBluetoothAddress(): String =
        split(":").let { parts ->
            if (parts.size == 6) {
                "xx:xx:xx:${parts.takeLast(3).joinToString(":")}"
            } else {
                "<selected>"
            }
        }

    private companion object {
        const val LOG_TAG = "Printer"
    }

    private enum class PrinterSetupStatus {
        READY,
        NEEDS_SETUP,
        FAILED
    }
}
