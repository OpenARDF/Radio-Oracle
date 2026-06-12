package org.openardf.radiooracle.desktop

import java.nio.file.Path

/** Save-time guard for edits that redefine an already loaded Event File. */
object DesktopEventDefinitionSaveProtection {
    const val statusText =
        "Event File definition changed; choose Save As New Event File or explicitly overwrite the current file."

    fun shouldPromptBeforeSave(
        currentPath: Path?,
        hasUnsavedEventDefinitionChanges: Boolean,
        overwriteEventDefinitionChanges: Boolean
    ): Boolean =
        currentPath != null &&
            hasUnsavedEventDefinitionChanges &&
            !overwriteEventDefinitionChanges
}
