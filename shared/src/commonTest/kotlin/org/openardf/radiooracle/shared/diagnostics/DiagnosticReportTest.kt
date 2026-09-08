package org.openardf.radiooracle.shared.diagnostics

import kotlin.test.*

class DiagnosticReportTest {
    @Test fun reportOnlyIncludesDiagnosticFilesAndBuildContext() {
        val data = "test".encodeToByteArray()
        val report = DiagnosticReport.entries(mapOf("debug.log" to data, "debug.log.2" to data,
            "sportident.log.3" to data, "event-database" to data, "settings.xml" to data,
            "../debug.log" to data, "README.txt" to data), "Android test", "1.0.test", "build-date", "export-date")
        assertEquals(setOf("README.txt", "debug.log", "debug.log.2", "sportident.log.3"), report.keys)
        val readme = report.getValue("README.txt").decodeToString()
        assertTrue(readme.contains("1.0.test"))
        assertTrue(readme.contains("Android test"))
        assertTrue(readme.contains("build-date"))
        assertTrue(readme.contains("export-date"))
        assertTrue(readme.contains("cardholder data"))
        assertContentEquals(data, report.getValue("sportident.log.3"))
    }

    @Test fun missingLogsAreReported() {
        assertTrue(DiagnosticReport.entries(emptyMap(), "test", "version", "build", "now")
            .getValue("README.txt").decodeToString().contains("No retained logs"))
    }
}
