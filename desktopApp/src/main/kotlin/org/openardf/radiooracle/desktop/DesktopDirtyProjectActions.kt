package org.openardf.radiooracle.desktop

import java.nio.file.Path

/** Deferred operation that first needs a save/discard/cancel decision for dirty Event File state. */
sealed interface PendingDirtyProjectAction {
    /** Create a new Event File after the user decides what to do with unsaved edits. */
    data object NewProject : PendingDirtyProjectAction

    /** Open the selected Event File after the user decides what to do with unsaved edits. */
    data class OpenProject(val path: Path) : PendingDirtyProjectAction

    /** Import an Android race backup after the user decides what to do with unsaved edits. */
    data class ImportAndroidRaceBackup(val path: Path) : PendingDirtyProjectAction

    /** Close the current Event File after the user decides what to do with unsaved edits. */
    data object CloseProject : PendingDirtyProjectAction

    /** Exit the application after the user decides what to do with unsaved edits. */
    data object ExitApplication : PendingDirtyProjectAction
}

/** Decides whether an Event File action can run immediately or needs the unsaved-edit prompt first. */
object DesktopDirtyProjectActions {
    /** Returns the action as pending only when there are unsaved edits to protect. */
    fun pendingActionOrNull(
        hasUnsavedChanges: Boolean,
        action: PendingDirtyProjectAction
    ): PendingDirtyProjectAction? =
        if (hasUnsavedChanges) action else null

    /** Close actions discard only when the user chose Discard instead of Save. */
    fun shouldDiscardForClose(saveFirst: Boolean): Boolean = !saveFirst
}
