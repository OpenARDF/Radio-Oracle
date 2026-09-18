package org.openardf.radiooracle.desktop.usb

import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.openardf.radiooracle.shared.sportident.*

class DesktopSportIdentProgrammingClientTest {
    private val snapshot = DesktopSportIdentOwnerSnapshot(SportIdentCardOwnerInspection(2450662,
        SportIdentCardFamily.SI8, SportIdentCardHolder("Mickey", "Mouse", null), SportIdentOwnerDataStatus.READ),
        "/test-port", 593927)
    private val request = SportIdentOwnerNameProgramming.prepare(snapshot.inspection, 593927, "Minnie", "Mouse", true)

    @Test fun verifiesTheResultOnlyAfterAllProgressAndZeroExit() = runBlocking {
        val fixture = fixture("success")
        val phases = mutableListOf<SportIdentOwnerWritePhase>()
        val result = fixture.client.write(snapshot, request) { phases += it }
        assertEquals("Minnie", result.firstName)
        assertEquals(SportIdentOwnerWritePhase.entries, phases)
        fixture.assertStoppedAndCleaned()
    }

    @Test fun nonzeroExitCannotBeHiddenByAValidResult() = runBlocking {
        val fixture = fixture("nonzero")
        val error = failure { fixture.client.write(snapshot, request) }
        assertTrue(error.writeMayHaveOccurred)
        assertFalse(error.message.orEmpty().contains("vendor diagnostic"))
        fixture.assertStoppedAndCleaned()
    }

    @Test fun malformedResultsAndPrematureSuccessCannotPass() = runBlocking {
        for (scenario in listOf("wrong-card", "no-phases", "oversized")) {
            val fixture = fixture(scenario)
            failure { fixture.client.write(snapshot, request) }
            fixture.assertStoppedAndCleaned()
        }
    }

    @Test fun timeoutStopsTheHelperAndDeletesItsRequestBeforeReturning() = runBlocking {
        val fixture = fixture("wait", timeout = 2_000)
        failure { fixture.client.write(snapshot, request) }
        fixture.assertStoppedAndCleaned()
    }

    @Test fun cancellationWhileWritingStopsTheHelperBeforeReleasingTheCaller() = runBlocking {
        val fixture = fixture("wait")
        val writing = CompletableDeferred<Unit>()
        val job = launch { fixture.client.write(snapshot, request) { if (it == SportIdentOwnerWritePhase.WRITING) writing.complete(Unit) } }
        writing.await()
        job.cancelAndJoin()
        fixture.assertStoppedAndCleaned()
    }

    @Test fun snapshotMismatchCannotStartAProcess() = runBlocking {
        val fixture = fixture("success")
        failure { fixture.client.write(snapshot.copy(stationNumber = 593928), request) }
        assertNull(fixture.child)
        fixture.close()
    }

    @Test fun cancellationDuringProcessStartupStillStopsTheChildAndDeletesItsRequest() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val fixture = fixture("wait", beforeStart = { entered.complete(Unit); release.await() })
        val job = launch { fixture.client.write(snapshot, request) }
        entered.await()
        job.cancel()
        release.countDown()
        job.join()
        fixture.assertStoppedAndCleaned()
    }

    private suspend fun failure(action: suspend () -> Unit): DesktopSportIdentProgrammingException {
        try { action(); fail("Expected programming refusal") } catch (error: DesktopSportIdentProgrammingException) { return error }
        error("Unreachable")
    }

    private fun fixture(scenario: String, timeout: Long = 10_000, beforeStart: () -> Unit = {}): Fixture {
        val paths = linkedSetOf<String>()
        var loader: ClassLoader? = javaClass.classLoader
        while (loader != null) {
            if (loader is URLClassLoader) loader.urLs.forEach { paths += Path.of(it.toURI()).toString() }
            loader = loader.parent
        }
        paths += System.getProperty("java.class.path").split(java.io.File.pathSeparator)
        val prefix = listOf(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp",
            paths.joinToString(java.io.File.pathSeparator), DesktopSportIdentHelperFixture::class.java.name, scenario)
        return Fixture(prefix, timeout, beforeStart)
    }

    private class Fixture(prefix: List<String>, timeout: Long, beforeStart: () -> Unit) {
        val license = Files.createTempFile("si-test-placeholder-", ".txt")
        var child: Process? = null
        var requestFile: Path? = null
        val client = DesktopSportIdentProgrammingClient(DesktopSportIdentSdkConfiguration(prefix, license), timeout) { builder ->
            requestFile = Path.of(builder.command().last())
            assertTrue(Files.isRegularFile(requestFile))
            beforeStart()
            builder.start().also { child = it }
        }
        fun assertStoppedAndCleaned() {
            assertFalse(checkNotNull(child).isAlive)
            assertFalse(Files.exists(checkNotNull(requestFile)))
            assertFalse(Files.exists(checkNotNull(requestFile).parent))
            close()
        }
        fun close() { Files.deleteIfExists(license) }
    }
}

/** Simulates only the local process protocol; it never loads an SDK or opens a serial port. */
object DesktopSportIdentHelperFixture {
    @JvmStatic fun main(args: Array<String>) {
        val scenario = args[0]
        check(Files.readString(Path.of(args.last())).contains("\"AcceptPossiblePunchLoss\":true"))
        if (scenario != "no-phases") {
            phase("WaitingForCard")
            phase("Writing")
            if (scenario == "wait") { Thread.sleep(300_000); return }
            phase("WaitingForReadBack")
        }
        System.err.println("vendor diagnostic text must not reach the caller")
        if (scenario == "oversized") { println("x".repeat(9000)); return }
        val card = if (scenario == "wrong-card") 2450663 else 2450662
        println("""{"SchemaVersion":1,"StationNumber":593927,"CardNumber":$card,"CardType":"SI-Card8","FirstName":"Minnie","LastName":"Mouse","ControlPunchCountBefore":11,"ControlPunchCountAfter":11,"PunchesPreserved":true,"FeedbackPreserved":true,"CharacterSetPreserved":true}""")
        if (scenario == "nonzero") kotlin.system.exitProcess(1)
    }
    private fun phase(value: String) {
        println("""{"SchemaVersion":1,"Event":"Phase","Phase":"$value"}""")
        System.out.flush()
    }
}
