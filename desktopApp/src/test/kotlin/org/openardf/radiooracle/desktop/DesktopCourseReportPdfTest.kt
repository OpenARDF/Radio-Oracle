package org.openardf.radiooracle.desktop

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopCourseReportPdfTest {
    @Test fun exportsEveryVisibleReportFieldLegTableAndGraphic() {
        val project = courseReportFixtureWithMandatoryBend()
        val reports = DesktopCourseBriefReports.build(project, emptyMap())
        val output = Path.of("build/reports/course-report/course-report-pdf-test.pdf")

        DesktopCourseReportPdf.exportPdf(output, project, reports)

        val bytes = Files.readAllBytes(output)
        val raw = String(bytes, Charsets.ISO_8859_1)
        assertTrue(raw.startsWith("%PDF-1.4"))
        listOf(
            "Course Report",
            "Race: Course report",
            "Horizontal length:",
            "Total climb:",
            "Effective length:",
            "Ideal order:",
            "Estimated ideal time:",
            "Ideal route legs",
            "Ideal-order leg",
            "S -> Fox1",
            "2D route depiction",
            "Elevation profile",
            "Page 1 of"
        ).forEach { expected -> assertTrue("Missing PDF text: $expected", raw.contains(expected)) }
        assertTrue("Mandatory bend labels must not appear in the PDF table", !raw.contains("Mandatory bend"))
        assertEquals(
            "Course report course report.pdf",
            DesktopCourseReportPdf.defaultFileName(project)
        )
    }
}
