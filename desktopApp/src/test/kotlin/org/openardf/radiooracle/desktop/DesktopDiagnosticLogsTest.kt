package org.openardf.radiooracle.desktop

import org.junit.Test
import org.junit.Assert.*
import java.nio.file.Files
import java.util.zip.ZipFile

class DesktopDiagnosticLogsTest {
    @Test fun exportedArchiveIsIndependentOfActiveLogsAndExcludesOtherFiles() {
        val directory = Files.createTempDirectory("radio-oracle-diagnostic-test")
        try {
            DesktopDebugLog.initialize(directory)
            DesktopDebugLog.info("Test", "before-export")
            DesktopDebugLog.sportIdent("test-packet")
            Files.writeString(directory.resolve("settings.xml"), "must-not-be-exported")
            val output = directory.resolve("report.zip")
            DesktopDiagnosticLogs.export(output)
            DesktopDebugLog.info("Test", "after-export")
            ZipFile(output.toFile()).use { zip ->
                val names = zip.entries().asSequence().map { it.name }.toSet()
                assertEquals(setOf("README.txt", "debug.log", "sportident.log"), names)
                val text = zip.getInputStream(zip.getEntry("debug.log")).reader().readText()
                assertTrue(text.contains("before-export"))
                assertFalse(text.contains("after-export"))
                assertTrue(zip.getInputStream(zip.getEntry("sportident.log")).reader().readText().contains("test-packet"))
            }
        } finally { directory.toFile().deleteRecursively() }
    }
}
