package org.openardf.radiooracle.desktop

import java.awt.FileDialog
import java.awt.Frame
import java.io.FilenameFilter
import java.nio.file.Files
import java.nio.file.Path
import java.util.prefs.Preferences

/** Event File path helpers shared by desktop file dialogs and tests. */
object DesktopProjectFilePaths {
    const val PROJECT_EXTENSION = ".json"
    private const val LEGACY_PROJECT_EXTENSION = ".rom.json"
    const val ANDROID_RACE_BACKUP_JSON_EXTENSION = ".ardfjs"
    const val ARDF_JSON_EXTENSION = ".ardf.json"
    const val FINAL_RESULTS_JSON_EXTENSION = ".final-results.json"
    const val LIVE_RESULTS_JSON_EXTENSION = ".live-results.json"
    const val IOF_XML_EXTENSION = ".iof.xml"
    const val CSV_EXTENSION = ".csv"
    const val HTML_EXTENSION = ".html"
    const val TXT_EXTENSION = ".txt"

    /** Returns a path with the standard Radio-Oracle desktop Event File extension. */
    fun withProjectExtension(path: Path): Path =
        if (isProjectFileName(path.fileName.toString())) {
            path
        } else {
            path.resolveSibling("${path.fileName}$PROJECT_EXTENSION")
        }

    private fun defaultFileStem(eventName: String): String =
        eventName
            .trim()
            .map { character ->
                if (character.isISOControl() || character in """\/:*?"<>|""") ' ' else character
            }
            .joinToString("")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "Event File" }

    fun defaultProjectFileName(eventName: String): String =
        withProjectExtension(Path.of(defaultFileStem(eventName))).fileName.toString()

    fun defaultAndroidEventJsonFileName(eventName: String): String =
        withAndroidRaceBackupJsonExtension(Path.of(defaultFileStem(eventName))).fileName.toString()

    fun isProjectFileName(fileName: String): Boolean =
        fileName.endsWith(PROJECT_EXTENSION) || fileName.endsWith(LEGACY_PROJECT_EXTENSION)

    fun withCsvExtension(path: Path): Path =
        if (path.fileName.toString().endsWith(CSV_EXTENSION)) {
            path
        } else {
            path.resolveSibling("${path.fileName}$CSV_EXTENSION")
        }

    fun withAndroidRaceBackupJsonExtension(path: Path): Path =
        if (path.fileName.toString().endsWith(ANDROID_RACE_BACKUP_JSON_EXTENSION)) {
            path
        } else {
            path.resolveSibling("${path.fileName}$ANDROID_RACE_BACKUP_JSON_EXTENSION")
        }

    fun withArdfJsonExtension(path: Path): Path =
        if (path.fileName.toString().endsWith(ARDF_JSON_EXTENSION)) {
            path
        } else {
            path.resolveSibling("${path.fileName}$ARDF_JSON_EXTENSION")
        }

    fun withFinalResultsJsonExtension(path: Path): Path =
        if (path.fileName.toString().endsWith(FINAL_RESULTS_JSON_EXTENSION)) {
            path
        } else {
            path.resolveSibling("${path.fileName}$FINAL_RESULTS_JSON_EXTENSION")
        }

    fun withLiveResultsJsonExtension(path: Path): Path =
        if (path.fileName.toString().endsWith(LIVE_RESULTS_JSON_EXTENSION)) {
            path
        } else {
            path.resolveSibling("${path.fileName}$LIVE_RESULTS_JSON_EXTENSION")
        }

    fun withIofXmlExtension(path: Path): Path =
        if (path.fileName.toString().endsWith(IOF_XML_EXTENSION)) {
            path
        } else {
            path.resolveSibling("${path.fileName}$IOF_XML_EXTENSION")
        }

    fun withHtmlExtension(path: Path): Path =
        if (path.fileName.toString().endsWith(HTML_EXTENSION)) {
            path
        } else {
            path.resolveSibling("${path.fileName}$HTML_EXTENSION")
        }

    fun withTxtExtension(path: Path): Path =
        if (path.fileName.toString().endsWith(TXT_EXTENSION)) {
            path
        } else {
            path.resolveSibling("${path.fileName}$TXT_EXTENSION")
        }
}

/** Remembers and prepares the desktop directory used for user-visible Event Files. */
object DesktopEventFileLocations {
    private const val APP_DOCUMENTS_FOLDER = "Radio-Oracle"
    private const val LAST_EVENT_FILE_DIRECTORY_KEY = "lastEventFileDirectory"
    private val preferences: Preferences =
        Preferences.userNodeForPackage(DesktopEventFileLocations::class.java)

    fun defaultEventFileDirectory(userHome: Path = Path.of(System.getProperty("user.home"))): Path =
        userHome.resolve("Documents").resolve(APP_DOCUMENTS_FOLDER)

    fun preferredEventFileDirectory(): Path {
        val remembered = preferences.get(LAST_EVENT_FILE_DIRECTORY_KEY, null)
            ?.takeIf { it.isNotBlank() }
            ?.let(Path::of)
        return remembered ?: defaultEventFileDirectory()
    }

    fun preparePreferredEventFileDirectory(): Path {
        val directory = preferredEventFileDirectory()
        Files.createDirectories(directory)
        return directory
    }

    fun rememberEventFileDirectory(path: Path) {
        val directory = if (Files.isDirectory(path)) path else path.parent
        directory?.let {
            preferences.put(LAST_EVENT_FILE_DIRECTORY_KEY, it.toAbsolutePath().toString())
        }
    }
}

/** AWT-backed file chooser for desktop Event Files. */
object DesktopFileDialogs {
    /** Lets the user choose an existing Event File, returning null when cancelled. */
    fun chooseOpenProject(): Path? =
        chooseEventFile("Open Radio-Oracle Event File", FileDialog.LOAD)

    /** Lets the user choose a save location, returning null when cancelled. */
    fun chooseSaveProject(raceName: String? = null): Path? =
        chooseEventFile(
            title = "Save Radio-Oracle Event File",
            mode = FileDialog.SAVE,
            defaultFileName = raceName?.let(DesktopProjectFilePaths::defaultProjectFileName)
        )
            ?.let(DesktopProjectFilePaths::withProjectExtension)

    /** Lets the user choose an export-copy location, returning null when cancelled. */
    fun chooseExportProject(): Path? =
        chooseEventFile("Export Radio-Oracle Event File Copy", FileDialog.SAVE)
            ?.let(DesktopProjectFilePaths::withProjectExtension)

    fun chooseExportCsv(title: String): Path? =
        chooseFile(title, FileDialog.SAVE, DesktopProjectFilePaths.CSV_EXTENSION)
            ?.let(DesktopProjectFilePaths::withCsvExtension)

    fun chooseExportArdfJson(): Path? =
        chooseFile("Export ARDF JSON", FileDialog.SAVE, DesktopProjectFilePaths.ARDF_JSON_EXTENSION)
            ?.let(DesktopProjectFilePaths::withArdfJsonExtension)

    fun chooseExportAndroidRaceBackupJson(eventName: String? = null): Path? =
        chooseFile(
            title = "Export Android Event JSON",
            mode = FileDialog.SAVE,
            extension = DesktopProjectFilePaths.ANDROID_RACE_BACKUP_JSON_EXTENSION,
            defaultFileName = eventName?.let(DesktopProjectFilePaths::defaultAndroidEventJsonFileName)
        )?.let(DesktopProjectFilePaths::withAndroidRaceBackupJsonExtension)

    fun chooseExportFinalResultsJson(): Path? =
        chooseFile("Export Final Results JSON", FileDialog.SAVE, DesktopProjectFilePaths.FINAL_RESULTS_JSON_EXTENSION)
            ?.let(DesktopProjectFilePaths::withFinalResultsJsonExtension)

    fun chooseExportLiveResultsJson(): Path? =
        chooseFile("Export Live Results JSON", FileDialog.SAVE, DesktopProjectFilePaths.LIVE_RESULTS_JSON_EXTENSION)
            ?.let(DesktopProjectFilePaths::withLiveResultsJsonExtension)

    fun chooseExportIofXml(title: String): Path? =
        chooseFile(title, FileDialog.SAVE, DesktopProjectFilePaths.IOF_XML_EXTENSION)
            ?.let(DesktopProjectFilePaths::withIofXmlExtension)

    fun chooseExportHtml(title: String): Path? =
        chooseFile(title, FileDialog.SAVE, DesktopProjectFilePaths.HTML_EXTENSION)
            ?.let(DesktopProjectFilePaths::withHtmlExtension)

    fun chooseExportTxt(title: String): Path? =
        chooseFile(title, FileDialog.SAVE, DesktopProjectFilePaths.TXT_EXTENSION)
            ?.let(DesktopProjectFilePaths::withTxtExtension)

    fun chooseImportCsv(title: String): Path? =
        chooseFile(title, FileDialog.LOAD, DesktopProjectFilePaths.CSV_EXTENSION)

    fun chooseImportAndroidRaceBackupJson(): Path? =
        chooseFile(
            "Import Android Event JSON",
            FileDialog.LOAD,
            DesktopProjectFilePaths.ANDROID_RACE_BACKUP_JSON_EXTENSION
        )

    private fun chooseFile(title: String, mode: Int, extension: String, defaultFileName: String? = null): Path? {
        val dialog = FileDialog(null as Frame?, title, mode)
        dialog.filenameFilter = FilenameFilter { _, name -> name.endsWith(extension) }
        dialog.file = defaultFileName ?: "*$extension"
        dialog.isVisible = true

        val directory = dialog.directory ?: return null
        val file = dialog.file ?: return null
        return Path.of(directory, file)
    }

    private fun chooseEventFile(title: String, mode: Int, defaultFileName: String? = null): Path? {
        val directory = DesktopEventFileLocations.preparePreferredEventFileDirectory()
        val dialog = FileDialog(null as Frame?, title, mode)
        dialog.filenameFilter = FilenameFilter { _, name -> DesktopProjectFilePaths.isProjectFileName(name) }
        dialog.directory = directory.toString()
        dialog.file = defaultFileName ?: "*${DesktopProjectFilePaths.PROJECT_EXTENSION}"
        dialog.isVisible = true

        val selectedDirectory = dialog.directory ?: return null
        val file = dialog.file ?: return null
        return Path.of(selectedDirectory, file).also(DesktopEventFileLocations::rememberEventFileDirectory)
    }
}
