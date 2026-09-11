package org.openardf.radiooracle.desktop

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Keep new dialogs on the behavior-tested path instead of reintroducing one-off clipped bodies. */
class DesktopScrollArchitectureTest {
    @Test fun newDialogsAndScrollAreasMustUseTheSharedComponents() {
        val root = Path.of("src/main/kotlin/org/openardf/radiooracle/desktop")
        val rawDialogs = mutableMapOf<String, Int>()
        Files.walk(root).use { paths -> paths.filter { it.toString().endsWith(".kt") }.forEach { path ->
            val source = Files.readString(path)
            assertTrue("Use DesktopAlertDialog in $path", !Regex("\\bAlertDialog\\s*\\(").containsMatchIn(source))
            assertTrue("Use DesktopWorkspaceScroll in $path", path.fileName.toString() == "DesktopWorkspaceScroll.kt" ||
                !Regex("\\bverticalScroll\\s*\\(").containsMatchIn(source))
            val count = Regex("\\bDialog\\s*\\(").findAll(source).count()
            if (count > 0) rawDialogs[path.fileName.toString()] = count
        } }
        assertEquals("New custom dialogs need bounded body scrolling and UI coverage; prefer DesktopAlertDialog.",
            mapOf("DesktopAlertDialog.kt" to 1, "Main.kt" to 3,
                "DesktopCompetitorSpreadsheetImportDialog.kt" to 1, "DesktopCourseDesignUi.kt" to 1), rawDialogs)
    }
}
