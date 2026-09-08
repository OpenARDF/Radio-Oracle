package org.openardf.radiooracle.logging

import org.robolectric.RuntimeEnvironment
import org.junit.Assert.*
import androidx.core.content.FileProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.openardf.radiooracle.backend.logging.DebugLog
import org.openardf.radiooracle.backend.logging.DiagnosticLogExport
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DiagnosticLogExportTest {
    @Test fun shareFileIsSnapshotAndContainsOnlyLogsAndReport() {
        val context = RuntimeEnvironment.getApplication()
        DebugLog.initialize(context)
        DebugLog.info("Test", "before-export")
        DebugLog.sportIdent("packet-test")
        val file = DiagnosticLogExport.create(context)
        DebugLog.info("Test", "after-export")
        assertEquals("diagnostic-exports", file.parentFile?.name)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.diagnostics", file)
        context.contentResolver.openInputStream(uri)!!.use { input ->
            assertArrayEquals(file.readBytes(), input.readBytes())
        }
        try {
            FileProvider.getUriForFile(context, "${context.packageName}.diagnostics", java.io.File(context.filesDir, "event-database"))
            fail("Private app files must not be exposed by the diagnostics provider")
        } catch (_: IllegalArgumentException) {
            // Only cache/diagnostic-exports is shareable.
        }
        ZipFile(file).use { zip ->
            assertEquals(setOf("README.txt", "debug.log", "sportident.log"),
                zip.entries().asSequence().map { it.name }.toSet())
            val text = zip.getInputStream(zip.getEntry("debug.log")).reader().readText()
            assertTrue(text.contains("before-export"))
            assertFalse(text.contains("after-export"))
            assertTrue(zip.getInputStream(zip.getEntry("sportident.log")).reader().readText().contains("packet-test"))
        }
    }
}
