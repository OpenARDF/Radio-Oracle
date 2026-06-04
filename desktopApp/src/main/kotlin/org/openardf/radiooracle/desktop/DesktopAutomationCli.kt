package org.openardf.radiooracle.desktop

import java.io.PrintStream
import java.nio.file.Path
import kotlin.system.exitProcess
import org.openardf.radiooracle.desktop.printing.DesktopPrinterDiagnostics
import org.openardf.radiooracle.desktop.printing.DesktopTicketPrinter
import org.openardf.radiooracle.desktop.usb.DesktopSerialPortProvider
import org.openardf.radiooracle.desktop.usb.DesktopSportIdentStationProbe
import org.openardf.radiooracle.desktop.usb.JSerialCommDesktopSerialPortProvider
import org.openardf.radiooracle.shared.event.EventValidationRules

fun main(args: Array<String>) {
    exitProcess(DesktopAutomationCli.run(args))
}

/**
 * Command-line hooks for automated desktop checks and local debugging.
 *
 * This is intentionally separate from the Compose UI. It gives CI and Codex a
 * stable app-control surface without adding a remote-control listener to the
 * beta desktop app.
 */
object DesktopAutomationCli {
    fun run(
        args: Array<String>,
        out: PrintStream = System.out,
        err: PrintStream = System.err,
        serialPortProvider: DesktopSerialPortProvider = JSerialCommDesktopSerialPortProvider,
        printerDiagnostics: (() -> DesktopPrinterDiagnostics)? = null
    ): Int {
        val command = args.firstOrNull()
        val commandArgs = args.drop(1)
        return when (command) {
            null,
            "help",
            "--help",
            "-h" -> {
                out.println(helpText())
                0
            }
            "version" -> {
                out.println(
                    jsonObject(
                        "command" to "version",
                        "baseVersion" to DesktopBuildInfo.baseVersion,
                        "buildSuffix" to DesktopBuildInfo.buildSuffix,
                        "displayVersion" to DesktopBuildInfo.displayVersion,
                        "windowTitle" to DesktopBuildInfo.windowTitle
                    )
                )
                0
            }
            "paths" -> {
                out.println(
                    jsonObject(
                        "command" to "paths",
                        "defaultEventFileDirectory" to DesktopEventFileLocations.defaultEventFileDirectory().toString(),
                        "preferredEventFileDirectory" to DesktopEventFileLocations.preferredEventFileDirectory().toString(),
                        "appDataDirectory" to DesktopAppDirectories.appDataDirectory().toString(),
                        "logDirectory" to DesktopAppDirectories.logDirectory().toString()
                    )
                )
                0
            }
            "logs" -> {
                DesktopDebugLog.initialize()
                out.println(
                    jsonObject(
                        "command" to "logs",
                        "logDirectory" to DesktopDebugLog.logDirectory().toString(),
                        "files" to DesktopDebugLog.logFiles().map { it.toString() }
                    )
                )
                0
            }
            "log-test" -> {
                DesktopDebugLog.initialize()
                DesktopDebugLog.info("Automation", commandArgs.joinToString(" ").ifBlank { "desktop automation log test" })
                out.println(
                    jsonObject(
                        "command" to "log-test",
                        "logDirectory" to DesktopDebugLog.logDirectory().toString(),
                        "files" to DesktopDebugLog.logFiles().map { it.toString() }
                    )
                )
                0
            }
            "open-event-file" -> openEventFile(commandArgs, out, err)
            "si-status" -> siStatus(commandArgs, out, err, serialPortProvider)
            "printer-status" -> printerStatus(commandArgs, out, err, printerDiagnostics)
            else -> {
                err.println("Unknown desktop automation command: $command")
                err.println(helpText())
                64
            }
        }
    }

    private fun openEventFile(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val pathText = args.firstOrNull()
        if (pathText.isNullOrBlank()) {
            err.println("open-event-file requires an Event File path.")
            return 64
        }
        return runCatching {
            val path = Path.of(pathText)
            val projectFile = DesktopProjectFiles.read(path)
            val validationErrors = EventValidationRules.validateRaceData(projectFile.raceData)
            out.println(
                jsonObject(
                    "command" to "open-event-file",
                    "path" to path.toAbsolutePath().normalize().toString(),
                    "raceName" to projectFile.raceData.race.name,
                    "categoryCount" to projectFile.raceData.categories.size,
                    "competitorCount" to projectFile.raceData.competitorData.size,
                    "unmatchedReadoutCount" to projectFile.raceData.unmatchedReadoutData.size,
                    "validationErrorCount" to validationErrors.size,
                    "validationErrors" to validationErrors
                )
            )
            0
        }.getOrElse { error ->
            err.println("Failed to open Event File: ${error.message ?: error::class.simpleName}")
            66
        }
    }

    private fun siStatus(
        args: List<String>,
        out: PrintStream,
        err: PrintStream,
        serialPortProvider: DesktopSerialPortProvider
    ): Int {
        val requireStation = "--require" in args
        val requestedPort = optionValue(args, "--port")
        val ports = serialPortProvider.listPorts()
        val port = runCatching {
            if (requestedPort.isNullOrBlank()) {
                ports.firstOrNull { it.info.matchesSportIdent() }
            } else {
                serialPortProvider.getPort(requestedPort)
            }
        }.getOrElse { error ->
            err.println("SI serial port selection failed: ${error.message ?: error::class.simpleName}")
            return if (requireStation) 69 else 0
        }
        if (port == null) {
            out.println(
                jsonObject(
                    "command" to "si-status",
                    "severity" to "DISCONNECTED",
                    "status" to "SI station disconnected",
                    "detectedPorts" to ports.map { it.info.describe() }
                )
            )
            return if (requireStation) 69 else 0
        }

        return runCatching {
            val connection = DesktopSportIdentStationProbe().connect(port)
            val stationInfo = connection.stationInfo
            val severity = if (stationInfo.isReadoutMode == false) "WARNING" else "CONNECTED"
            out.println(
                jsonObject(
                    "command" to "si-status",
                    "severity" to severity,
                    "status" to "SI station ${stationInfo.serialNumber} connected",
                    "port" to port.info.describe(),
                    "baudRate" to connection.baudRate,
                    "serialNumber" to stationInfo.serialNumber,
                    "stationMode" to (stationInfo.stationModeLabel ?: "unknown"),
                    "stationModeCode" to stationInfo.stationModeCode,
                    "isReadoutMode" to stationInfo.isReadoutMode
                )
            )
            0
        }.getOrElse { error ->
            err.println("SI station probe failed: ${error.message ?: error::class.simpleName}")
            if (requireStation) 69 else 0
        }
    }

    private fun printerStatus(
        args: List<String>,
        out: PrintStream,
        err: PrintStream,
        printerDiagnostics: (() -> DesktopPrinterDiagnostics)?
    ): Int {
        val requirePrinter = "--require" in args
        return runCatching {
            val diagnostics = printerDiagnostics?.invoke()
                ?: DesktopPrinterDiagnostics.from(DesktopTicketPrinter().listPrinters())
            out.println(
                jsonObject(
                    "command" to "printer-status",
                    "selectedPrinterName" to diagnostics.selectedPrinterName,
                    "detectedPrinterNames" to diagnostics.detectedPrinterNames,
                    "readinessText" to diagnostics.readinessText
                )
            )
            if (requirePrinter && diagnostics.selectedPrinterName == null) 69 else 0
        }.getOrElse { error ->
            err.println("Printer status failed: ${error.message ?: error::class.simpleName}")
            if (requirePrinter) 69 else 0
        }
    }

    private fun optionValue(args: List<String>, name: String): String? {
        val index = args.indexOf(name)
        return if (index >= 0) args.getOrNull(index + 1) else null
    }

    private fun helpText(): String = """
        Radio-Oracle desktop automation

        Commands:
          version                         Print desktop build metadata as JSON.
          paths                           Print Event File, app-data, and log paths as JSON.
          logs                            Initialize logging and print current log files as JSON.
          log-test [message]              Write a desktop automation log entry.
          open-event-file <path>          Decode and validate an Event File.
          si-status [--require] [--port]  Probe attached SI station state.
          printer-status [--require]      Inspect desktop printer selection state.
    """.trimIndent()

    private fun jsonObject(vararg pairs: Pair<String, Any?>): String =
        pairs.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "${jsonString(key)}:${jsonValue(value)}"
        }

    private fun jsonValue(value: Any?): String =
        when (value) {
            null -> "null"
            is Boolean -> value.toString()
            is Number -> value.toString()
            is Iterable<*> -> value.joinToString(prefix = "[", postfix = "]") { jsonValue(it) }
            else -> jsonString(value.toString())
        }

    private fun jsonString(value: String): String =
        buildString {
            append('"')
            value.forEach { character ->
                when (character) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(character)
                }
            }
            append('"')
        }
}
