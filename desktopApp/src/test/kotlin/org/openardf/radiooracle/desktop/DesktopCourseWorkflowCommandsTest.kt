package org.openardf.radiooracle.desktop

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.openardf.radiooracle.shared.event.*
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files

class DesktopCourseWorkflowCommandsTest {
    @Test fun auditReportsIncompleteGeometryWithoutChangingSource() {
        val source = Files.createTempFile("course-audit-", ".json")
        DesktopProjectFiles.write(source, DesktopClassicRouteAnalysisTest().fixture())
        val original = Files.readAllBytes(source)
        val output = ByteArrayOutputStream()
        val error = ByteArrayOutputStream()
        assertEquals(1, DesktopAutomationCli.run(arrayOf("course-audit", source.toString()), PrintStream(output), PrintStream(error)))
        assertEquals("", error.toString())
        assertEquals("failed", Json.parseToJsonElement(output.toString()).jsonArray.single().jsonObject.getValue("status").jsonPrimitive.content)
        assertFalse(output.toString().contains("40.001"))
        assertArrayEquals(original, Files.readAllBytes(source))
    }

    @Test fun expectedFailuresCannotMasqueradeAsAcceptanceSuccess() {
        val source = Files.createTempFile("workflow-report-", ".json")
        val output = PrintStream(ByteArrayOutputStream())
        for (status in listOf("passed", "failed", "blocked", "skipped")) {
            Files.writeString(source, """{"scenario":"fixture","steps":[{"status":"$status","expectedFailure":true}]}""")
            assertEquals(if (status == "passed") 0 else 1,
                DesktopAutomationCli.run(arrayOf("course-workflow-report", source.toString()), output, output))
        }
        Files.writeString(source, """{"scenario":"fixture","steps":[]}""")
        assertEquals(2, DesktopAutomationCli.run(arrayOf("course-workflow-report", source.toString()), output, output))
    }

    @Test fun auditDistinguishesLockedDataAndInvalidArguments() {
        val fixture = DesktopClassicRouteAnalysisTest().fixture()
        val locked = fixture.raceData.copy(categories = fixture.raceData.categories.map {
            it.copy(category = it.category.copy(courseInfo = null, encryptedCourseInfo = "opaque-encrypted-payload"))
        })
        assertEquals("blocked", CourseWorkflowAudit.audit(locked).status)
        val error = ByteArrayOutputStream()
        assertEquals(2, DesktopAutomationCli.run(arrayOf("course-audit"), PrintStream(ByteArrayOutputStream()), PrintStream(error)))
        assertTrue(error.toString().contains("requires"))
    }
}
