package org.openardf.radiooracle.desktop.usb

import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import kotlinx.serialization.decodeFromString
import org.openardf.radiooracle.desktop.DesktopSportIdentOwnerRecoveryState
import org.openardf.radiooracle.desktop.DesktopSportIdentOwnerRecoveryStore
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameProgramming
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameRecovery
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameWriteRequest
import org.openardf.radiooracle.shared.sportident.SportIdentCommandResult
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    exitProcess(DesktopSportIdentNativeOwnerWrite.run(args))
}

/** Explicit experimental CLI for one native owner-write attempt; never retries. */
internal object DesktopSportIdentNativeOwnerWrite {
    private const val usage = "Usage: --execute-native-write <exact-owner-write-request-json> " +
        "[--stop-after-first-reply|--stop-after-second-reply|--stop-after-third-reply|" +
        "--allow-seven-word-trial|--allow-seven-word-paced-trial|--allow-six-word-paced-trial|" +
        "--allow-three-word-paced-trial]"
    private const val pacedTrialPauseMillis = 200L

    fun run(
        args: Array<String>,
        out: PrintStream = System.out,
        err: PrintStream = System.err,
        store: DesktopSportIdentOwnerRecoveryStore = DesktopSportIdentOwnerRecoveryStore(),
        execute: ((SportIdentOwnerNameWriteRequest) -> DesktopSportIdentOwnerWriteOutcome)? = null
    ): Int {
        if (args.contentEquals(arrayOf("--help"))) {
            out.println(usage)
            return 0
        }
        val stopAfterWord = when (args.getOrNull(2)) {
            "--stop-after-first-reply" -> 1
            "--stop-after-second-reply" -> 2
            "--stop-after-third-reply" -> 3
            else -> null
        }
        val experimentalWordCount = when (args.getOrNull(2)) {
            "--allow-seven-word-trial", "--allow-seven-word-paced-trial" -> 7
            "--allow-six-word-paced-trial" -> 6
            "--allow-three-word-paced-trial" -> 3
            else -> null
        }
        val pacedTrial = args.getOrNull(2) in setOf(
            "--allow-seven-word-paced-trial", "--allow-six-word-paced-trial",
            "--allow-three-word-paced-trial")
        if (args.getOrNull(0) != "--execute-native-write" ||
            (args.size != 2 && (args.size != 3 ||
                (stopAfterWord == null && experimentalWordCount == null)))) {
            err.println(usage)
            return 1
        }
        return try {
            val file = Path.of(args[1])
            require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && Files.size(file) <= 4096) {
                "Owner-write request must be a regular file of at most 4096 bytes."
            }
            val request = SportIdentOwnerNameProgramming.json.decodeFromString<SportIdentOwnerNameWriteRequest>(
                Files.readString(file))
            SportIdentOwnerNameRecovery.validate(request)
            out.println("Experimental native SI-Card8 write: station=${request.stationNumber} " +
                "card=${request.cardNumber} '${request.expectedFirstName} ${request.expectedLastName}' " +
                "to '${request.firstName} ${request.lastName}'. One attempt; no automatic retry." +
                if (stopAfterWord != null) " Stop deliberately after acknowledged word $stopAfterWord."
                else if (pacedTrial)
                    " Opt-in $experimentalWordCount-word trial with a 200 ms pause between words."
                else if (experimentalWordCount != null) " Opt-in $experimentalWordCount-word trial." else "")
            val outcome = (execute ?: { nativeExecute(it, store, out, stopAfterWord,
                experimentalWordCount, if (pacedTrial) pacedTrialPauseMillis else 0L) })(request)
            if (outcome.verified && store.load() == DesktopSportIdentOwnerRecoveryState.Empty) {
                out.println("Native write verified against a fresh two-block card read; recovery record cleared.")
                0
            } else {
                err.println("Native write not verified: ${outcome.stopReason ?: outcome.stage}; " +
                    "recovery=${describeRecovery(store.load())}. Do not retry without a fresh card read.")
                2
            }
        } catch (error: Exception) {
            err.println("Native write failed: ${error.message}; recovery=${describeRecovery(store.load())}. " +
                "Do not retry an uncertain attempt without a fresh card read.")
            1
        }
    }

    private fun describeRecovery(state: DesktopSportIdentOwnerRecoveryState): String = when (state) {
        DesktopSportIdentOwnerRecoveryState.Empty -> "empty"
        DesktopSportIdentOwnerRecoveryState.Unavailable -> "unavailable"
        is DesktopSportIdentOwnerRecoveryState.Pending ->
            "pending card=${state.request.cardNumber} attemptedWords=${state.nativeAttempt?.attemptedWords ?: "unknown"}"
    }

    private fun nativeExecute(request: SportIdentOwnerNameWriteRequest,
        store: DesktopSportIdentOwnerRecoveryStore, out: PrintStream,
        stopAfterWord: Int?, experimentalWordCount: Int?,
        pauseAfterReplyMillis: Long): DesktopSportIdentOwnerWriteOutcome {
        val wordResults = mutableListOf<String>()
        val preflight = DesktopSportIdentOwnerWritePreflight(readCard = { port ->
            out.println("Station ${request.stationNumber} ready. Insert SI-Card8 ${request.cardNumber}; keep it seated.")
            DesktopSportIdentCardBlockReader(onProgress = { out.println(it) })
                .readFirstSupportedCardAfterInsertOnOpenPort(port)
        })
        val readback = DesktopSportIdentOwnerReadbackVerifier(
            awaitTargetRemoval = { port, card ->
                out.println("Expected owner-word replies observed. Remove SI-Card8 $card now for independent verification.")
                DesktopSportIdentCardEventMonitor().waitForRemoveEventOnOpenPort(
                    port, card, System.currentTimeMillis() + DesktopSportIdentCardEventMonitor.defaultMaxWaitMs
                ) != null
            },
            readAfterReinsertion = { port ->
                out.println("Removal observed. Reinsert SI-Card8 ${request.cardNumber} and keep it seated until the read finishes.")
                DesktopSportIdentCardBlockReader(onProgress = { out.println(it) })
                    .readFirstSupportedCardAfterInsertOnOpenPort(port)
            }
        )
        val transaction = DesktopSportIdentOwnerWriteTransaction(
            preflight, readback, store,
            stopAfterAcknowledgedWord = stopAfterWord,
            experimentalWordCount = experimentalWordCount,
            onBeforeWordExchange = {
                out.println("Target card rechecked and recovery baseline saved. " +
                    if (stopAfterWord != null) "Writing $stopAfterWord owner word(s), then stopping; keep the card seated."
                    else "Writing owner words; keep the card seated.")
            },
            makeWordTransport = { port ->
                DesktopSportIdentOwnerWordTransport(port, onWordResult = { word, result ->
                    wordResults += "Owner word $word reply: ${describe(result)}"
                }, onWordTiming = { timing ->
                    wordResults += "Owner word ${timing.wordNumber} timing: reply-to-write=" +
                        (timing.replyToWriteMicros?.let { "$it us" } ?: "first word") +
                        ", write-to-reply=${timing.writeToReplyMicros} us"
                }, pauseAfterReplyMillis = pauseAfterReplyMillis)
            }
        )
        try {
            return transaction.execute(request)
        } finally {
            wordResults.forEach(out::println)
        }
    }

    private fun describe(result: SportIdentCommandResult): String = when (result) {
        SportIdentCommandResult.NoReply -> "no complete reply"
        SportIdentCommandResult.NegativeAcknowledgement -> "negative acknowledgement"
        is SportIdentCommandResult.Reply -> result.frame.raw.joinToString(" ") {
            "%02x".format(it.toInt() and 0xff)
        }
    }
}
