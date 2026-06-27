package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventProjectFileJson
import java.nio.file.Path

/** Storage boundary used by desktop Event File session logic. */
interface ProjectFileStore {
    /** Reads a shared Event File from a desktop path. */
    fun read(path: Path): EventProjectFile

    /** Writes a shared Event File to a desktop path. */
    fun write(path: Path, projectFile: EventProjectFile)
}

/** Tracks the desktop app's currently open Event File and save location. */
class DesktopProjectSession(private val store: ProjectFileStore) {
    /** Event File currently loaded into the desktop app, or null before a file is opened. */
    var currentProject: EventProjectFile? = null
        private set

    /** Filesystem path associated with the current Event File, or null for unsaved Event Files. */
    var currentPath: Path? = null
        private set

    /** True after local edits are applied and before those edits are written to disk. */
    var hasUnsavedChanges: Boolean = false
        private set

    /** Opens an Event File from disk and makes its path the default save destination. */
    fun open(path: Path): EventProjectFile {
        val projectFile = store.read(path)
        currentProject = projectFile
        currentPath = path
        hasUnsavedChanges = false
        return projectFile
    }

    /** Starts a new unsaved Event File that must later be saved to a chosen path. */
    fun newProject(projectFile: EventProjectFile): EventProjectFile {
        currentProject = projectFile
        currentPath = null
        hasUnsavedChanges = true
        return projectFile
    }

    /** Applies a shared Event File edit to the current Event File and marks it dirty. */
    fun updateCurrentProject(transform: (EventProjectFile) -> EventProjectFile): EventProjectFile {
        val projectFile = requireNotNull(currentProject) {
            "Cannot edit before an Event File is open."
        }
        val updatedProject = transform(projectFile)
        currentProject = updatedProject
        hasUnsavedChanges = hasUnsavedChanges || updatedProject != projectFile
        return updatedProject
    }

    /** Saves the current Event File to its existing path. */
    fun save() {
        val path = requireNotNull(currentPath) {
            "Cannot save before an Event File path is selected."
        }
        saveAs(path)
    }

    /** Saves the current Event File to a specific path and makes that path current. */
    fun saveAs(path: Path) {
        val projectFile = requireNotNull(currentProject) {
            "Cannot save before an Event File is open."
        }
        val savedProject = EventProjectFileJson.normalizedForStorage(projectFile)
        store.write(path, savedProject)
        currentProject = savedProject
        currentPath = path
        hasUnsavedChanges = false
    }

    /** Writes the current Event File to another path without changing the active save location or dirty state. */
    fun exportCopy(path: Path) {
        val projectFile = requireNotNull(currentProject) {
            "Cannot export before an Event File is open."
        }
        store.write(path, EventProjectFileJson.normalizedForStorage(projectFile))
    }

    /** Clears the active Event File, optionally discarding pending edits after user confirmation. */
    fun closeProject(discardUnsavedChanges: Boolean = false) {
        check(discardUnsavedChanges || !hasUnsavedChanges) {
            "Cannot close while there are unsaved changes."
        }
        currentProject = null
        currentPath = null
        hasUnsavedChanges = false
    }
}
