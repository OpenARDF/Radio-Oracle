package org.openardf.radiooracle.desktop

import java.awt.FileDialog
import java.awt.Frame
import java.io.FilenameFilter
import java.nio.file.Path

/** Project-file path helpers shared by desktop file dialogs and tests. */
object DesktopProjectFilePaths {
    const val PROJECT_EXTENSION = ".rom.json"
    const val CSV_EXTENSION = ".csv"

    /** Returns a path with the standard Radio-Oracle desktop project extension. */
    fun withProjectExtension(path: Path): Path =
        if (path.fileName.toString().endsWith(PROJECT_EXTENSION)) {
            path
        } else {
            path.resolveSibling("${path.fileName}$PROJECT_EXTENSION")
        }

    fun withCsvExtension(path: Path): Path =
        if (path.fileName.toString().endsWith(CSV_EXTENSION)) {
            path
        } else {
            path.resolveSibling("${path.fileName}$CSV_EXTENSION")
        }
}

/** AWT-backed file chooser for desktop `.rom.json` project files. */
object DesktopFileDialogs {
    /** Lets the user choose an existing project file, returning null when cancelled. */
    fun chooseOpenProject(): Path? =
        chooseFile("Open Radio-Oracle Project", FileDialog.LOAD, DesktopProjectFilePaths.PROJECT_EXTENSION)

    /** Lets the user choose a save location, returning null when cancelled. */
    fun chooseSaveProject(): Path? =
        chooseFile("Save Radio-Oracle Project", FileDialog.SAVE, DesktopProjectFilePaths.PROJECT_EXTENSION)
            ?.let(DesktopProjectFilePaths::withProjectExtension)

    /** Lets the user choose an export-copy location, returning null when cancelled. */
    fun chooseExportProject(): Path? =
        chooseFile("Export Radio-Oracle Project Copy", FileDialog.SAVE, DesktopProjectFilePaths.PROJECT_EXTENSION)
            ?.let(DesktopProjectFilePaths::withProjectExtension)

    fun chooseExportCsv(title: String): Path? =
        chooseFile(title, FileDialog.SAVE, DesktopProjectFilePaths.CSV_EXTENSION)
            ?.let(DesktopProjectFilePaths::withCsvExtension)

    private fun chooseFile(title: String, mode: Int, extension: String): Path? {
        val dialog = FileDialog(null as Frame?, title, mode)
        dialog.filenameFilter = FilenameFilter { _, name -> name.endsWith(extension) }
        dialog.file = "*$extension"
        dialog.isVisible = true

        val directory = dialog.directory ?: return null
        val file = dialog.file ?: return null
        return Path.of(directory, file)
    }
}
