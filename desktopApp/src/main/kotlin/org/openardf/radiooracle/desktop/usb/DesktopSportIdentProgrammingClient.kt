package org.openardf.radiooracle.desktop.usb

import java.io.BufferedReader
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameProgramming
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameWriteRequest
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameWriteResult
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerWritePhase
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerWriteProgress
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerWriteSequence

/** Optional local dependency; no SDK binaries or licensed values are bundled in the app. */
internal data class DesktopSportIdentSdkConfiguration(val commandPrefix: List<String>, val licenseFile: Path) {
    companion object {
        fun fromEnvironment(environment: Map<String, String> = System.getenv()): DesktopSportIdentSdkConfiguration? {
            val helper = environment["RADIO_ORACLE_SI_SDK_HELPER_DLL"]?.let(Path::of) ?: return null
            val license = environment["SPORTIDENT_SDK_LICENSE_FILE"]?.let(Path::of) ?: return null
            val dotnet = environment["RADIO_ORACLE_DOTNET"]?.let(Path::of) ?: return null
            if (!Files.isRegularFile(helper) || !Files.isRegularFile(license) || !Files.isExecutable(dotnet)) return null
            return DesktopSportIdentSdkConfiguration(listOf(dotnet.toString(), helper.toString()), license)
        }
    }
}

internal class DesktopSportIdentProgrammingException(val writeMayHaveOccurred: Boolean) : IllegalStateException(
    if (writeMayHaveOccurred) "The write could not be verified. Read the card again before trying another write."
    else "Name programming failed. Read the card again before trying another write."
)

/** Caller holds the app's SI mutex until this returns, including child-process cleanup. */
internal class DesktopSportIdentProgrammingClient(
    private val configuration: DesktopSportIdentSdkConfiguration,
    private val timeoutMillis: Long = 130_000,
    private val startProcess: (ProcessBuilder) -> Process = { it.start() }
) {
    suspend fun write(
        snapshot: DesktopSportIdentOwnerSnapshot,
        request: SportIdentOwnerNameWriteRequest,
        onPhase: (SportIdentOwnerWritePhase) -> Unit = {}
    ): SportIdentOwnerNameWriteResult {
        val sequence = SportIdentOwnerWriteSequence()
        var helperStarted = false
        try {
            require(request.cardNumber == snapshot.inspection.siNumber && request.stationNumber == snapshot.stationNumber)
            return execute(snapshot.portPath, request, sequence, onPhase) { helperStarted = true }
        } catch (timeout: TimeoutCancellationException) {
            throw DesktopSportIdentProgrammingException(helperStarted)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // Neither vendor stderr nor exception messages can reach the UI/logs.
            throw DesktopSportIdentProgrammingException(helperStarted)
        }
    }

    private suspend fun execute(
        portPath: String,
        request: SportIdentOwnerNameWriteRequest,
        sequence: SportIdentOwnerWriteSequence,
        onPhase: (SportIdentOwnerWritePhase) -> Unit,
        onStarted: () -> Unit
    ): SportIdentOwnerNameWriteResult = coroutineScope {
        var folder: Path? = null
        var requestFile: Path? = null
        var process: Process? = null
        var shutdownHook: Thread? = null
        try {
            withContext(Dispatchers.IO) {
                folder = Files.createTempDirectory("radio-oracle-si-owner-")
                requestFile = checkNotNull(folder).resolve("request.json")
            }
            val file = checkNotNull(requestFile)
            withContext(Dispatchers.IO) {
                Files.writeString(file, SportIdentOwnerNameProgramming.json.encodeToString(request))
                val builder = ProcessBuilder(configuration.commandPrefix + listOf(portPath,
                    request.stationNumber.toString(), request.cardNumber.toString(), "--write-request", file.toString()))
                builder.environment()["SPORTIDENT_SDK_LICENSE_FILE"] = configuration.licenseFile.toString()
                builder.environment()["DOTNET_CLI_TELEMETRY_OPTOUT"] = "1"
                process = startProcess(builder)
                onStarted()
            }
            val child = checkNotNull(process)
            val hook = Thread({ child.destroyForcibly() }, "SI-owner-helper-shutdown")
            shutdownHook = hook
            Runtime.getRuntime().addShutdownHook(hook)
            val lines = Channel<String>(8)
            val stdout = launch(Dispatchers.IO) {
                try {
                    child.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        while (true) lines.send(readBoundedLine(reader) ?: break)
                    }
                } finally { lines.close() }
            }
            val stderr = launch(Dispatchers.IO) {
                child.errorStream.use { input ->
                    val buffer = ByteArray(1024)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        check(total <= 131_072) { "Helper diagnostics exceeded the limit." }
                    }
                }
            }
            try {
                withTimeout(timeoutMillis) {
                    var result: SportIdentOwnerNameWriteResult? = null
                    for (line in lines) {
                        val packet = SportIdentOwnerNameProgramming.json.parseToJsonElement(line).jsonObject
                        if ("Event" in packet) {
                            check(result == null)
                            val progress = SportIdentOwnerNameProgramming.json.decodeFromString<SportIdentOwnerWriteProgress>(line)
                            sequence.advance(progress)
                            onPhase(progress.phase)
                        } else {
                            check(result == null)
                            result = SportIdentOwnerNameProgramming.json.decodeFromString<SportIdentOwnerNameWriteResult>(line)
                            sequence.verify(request, result)
                        }
                    }
                    while (child.isAlive) delay(50)
                    stdout.join()
                    stderr.join()
                    check(child.exitValue() == 0)
                    checkNotNull(result)
                }
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    terminate(child)
                    stdout.cancelAndJoin()
                    stderr.cancelAndJoin()
                }
            }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                process?.let(::terminate)
                shutdownHook?.let { hook ->
                    try { Runtime.getRuntime().removeShutdownHook(hook) } catch (_: IllegalStateException) { }
                }
                requestFile?.let(Files::deleteIfExists)
                folder?.let(Files::deleteIfExists)
            }
        }
    }

    private fun terminate(process: Process) {
        if (process.isAlive) {
            process.destroy()
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                check(process.waitFor(2, TimeUnit.SECONDS)) { "Helper could not be stopped." }
            }
        }
    }

    private fun readBoundedLine(reader: BufferedReader): String? {
        val text = StringBuilder()
        while (true) {
            val character = reader.read()
            if (character < 0) return text.toString().takeIf { it.isNotEmpty() }
            if (character == '\n'.code) return text.toString().removeSuffix("\r")
            check(text.length < 8192) { "Helper output exceeded the limit." }
            text.append(character.toChar())
        }
    }
}
