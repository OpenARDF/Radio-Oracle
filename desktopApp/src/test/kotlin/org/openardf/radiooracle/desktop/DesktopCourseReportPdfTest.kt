package org.openardf.radiooracle.desktop

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopCourseReportPdfTest {
    @Test fun exportsEveryVisibleReportFieldLegTableAndGraphic() {
        val project = courseReportFixtureWithMandatoryBend()
        val report = DesktopCourseBriefReports.build(project, emptyMap()).single()
        val fullLegs = listOf("S" to "1", "1" to "2", "2" to "5", "5" to "3", "3" to "4", "4" to "B", "B" to "F")
            .mapIndexed { index, (from, to) -> DesktopCourseBriefLeg(from, to, 430 + index * 70) }
        val shortLegs = listOf("S" to "1", "1" to "2", "2" to "4", "4" to "B", "B" to "F")
            .mapIndexed { index, (from, to) -> DesktopCourseBriefLeg(from, to, 430 + index * 70) }
        val reports = listOf(
            report.copy(
                courseName = "Full",
                idealOrder = listOf("S", "1", "2", "5", "3", "4", "B", "F"),
                idealRouteLegs = fullLegs
            ),
            report.copy(
                categoryId = "short",
                courseName = "Short",
                idealOrder = listOf("S", "1", "2", "4", "B", "F"),
                idealRouteLegs = shortLegs
            )
        )
        val output = Path.of("build/reports/course-report/course-report-pdf-test.pdf")

        DesktopCourseReportPdf.exportPdf(output, project, reports)

        val bytes = Files.readAllBytes(output)
        val raw = String(bytes, Charsets.ISO_8859_1)
        assertTrue(raw.startsWith("%PDF-1.4"))
        listOf(
            "Course Report",
            "Race: Course report",
            "Course: Full",
            "Course: Short",
            "Horizontal length:",
            "Total climb:",
            "Effective length:",
            "Ideal order:",
            "Estimated ideal time:",
            "Ideal route legs",
            "Ideal-order leg",
            "S -> 1",
            "2D route depiction",
            "Elevation profile",
            "Page 1 of 1"
        ).forEach { expected -> assertTrue("Missing PDF text: $expected", raw.contains(expected)) }
        assertEquals(1, Regex("/Type /Page\\b").findAll(raw).count())
        assertTrue("Leg distances must use meters", raw.contains("430 m"))
        assertTrue("Leg distances must not use kilometers", !raw.contains("0.43 km"))
        assertTrue("Mandatory bend labels must not appear in the PDF table", !raw.contains("Mandatory bend"))
        assertEquals(
            "Course report course report.pdf",
            DesktopCourseReportPdf.defaultFileName(project)
        )
    }
}
