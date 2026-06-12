package org.openardf.radiooracle.desktop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

class DesktopEventDefinitionSaveProtectionTest {
    @Test
    fun promptsForUnsavedEventDefinitionChangesOnLoadedFile() {
        assertTrue(
            DesktopEventDefinitionSaveProtection.shouldPromptBeforeSave(
                currentPath = Path.of("loaded.rom.json"),
                hasUnsavedEventDefinitionChanges = true,
                overwriteEventDefinitionChanges = false
            )
        )
    }

    @Test
    fun doesNotPromptForNewUnsavedEventFile() {
        assertFalse(
            DesktopEventDefinitionSaveProtection.shouldPromptBeforeSave(
                currentPath = null,
                hasUnsavedEventDefinitionChanges = true,
                overwriteEventDefinitionChanges = false
            )
        )
    }

    @Test
    fun doesNotPromptWithoutEventDefinitionChanges() {
        assertFalse(
            DesktopEventDefinitionSaveProtection.shouldPromptBeforeSave(
                currentPath = Path.of("loaded.rom.json"),
                hasUnsavedEventDefinitionChanges = false,
                overwriteEventDefinitionChanges = false
            )
        )
    }

    @Test
    fun doesNotPromptWhenOverwriteIsExplicit() {
        assertFalse(
            DesktopEventDefinitionSaveProtection.shouldPromptBeforeSave(
                currentPath = Path.of("loaded.rom.json"),
                hasUnsavedEventDefinitionChanges = true,
                overwriteEventDefinitionChanges = true
            )
        )
    }
}
