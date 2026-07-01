/*
 * MIT License
 *
 * Copyright (c) 2025 Pavel Kolský
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.openardf.radiooracle.desktop

import java.nio.file.Path

/** Deferred operation that first needs a save/discard/cancel decision for dirty Race File state. */
sealed interface PendingDirtyProjectAction {
    /** Create a new Race File after the user decides what to do with unsaved edits. */
    data object NewProject : PendingDirtyProjectAction

    /** Open the selected Race File after the user decides what to do with unsaved edits. */
    data class OpenProject(val path: Path) : PendingDirtyProjectAction

    /** Import an Android race backup after the user decides what to do with unsaved edits. */
    data class ImportAndroidRaceBackup(val path: Path) : PendingDirtyProjectAction

    /** Close the current Race File after the user decides what to do with unsaved edits. */
    data object CloseProject : PendingDirtyProjectAction

    /** Exit the application after the user decides what to do with unsaved edits. */
    data object ExitApplication : PendingDirtyProjectAction
}

/** Decides whether a Race File action can run immediately or needs the unsaved-edit prompt first. */
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
