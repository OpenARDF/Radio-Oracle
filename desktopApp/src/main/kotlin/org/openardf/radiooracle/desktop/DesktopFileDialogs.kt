package org.openardf.radiooracle.desktop

import java.awt.FileDialog
import java.awt.Frame
import java.io.FilenameFilter
import java.nio.file.Files
import java.nio.file.Path
import java.util.prefs.Preferences
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.filechooser.FileNameExtensionFilter

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
    const val PDF_EXTENSION = ".pdf"
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

    /** Returns the editable Event File display name without current or legacy project extensions. */
    fun projectFileDisplayStem(fileName: String): String =
        fileName
            .removeSuffix(LEGACY_PROJECT_EXTENSION)
            .removeSuffix(PROJECT_EXTENSION)

    fun defaultAndroidEventJsonFileName(eventName: String): String =
        withAndroidRaceBackupJsonExtension(Path.of(defaultFileStem(eventName))).fileName.toString()

    fun defaultCsvFileName(eventName: String, suffix: String? = null): String {
        val sanitizedSuffix = suffix
            ?.let(::defaultFileStem)
            ?.takeUnless { it == "Event File" }
        val stem = listOfNotNull(defaultFileStem(eventName), sanitizedSuffix).joinToString(" ")
        return withCsvExtension(Path.of(stem)).fileName.toString()
    }

    fun defaultPdfFileName(eventName: String, suffix: String? = null): String {
        val sanitizedSuffix = suffix
            ?.let(::defaultFileStem)
            ?.takeUnless { it == "Event File" }
        val stem = listOfNotNull(defaultFileStem(eventName), sanitizedSuffix).joinToString(" ")
        return withPdfExtension(Path.of(stem)).fileName.toString()
    }

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

    fun withPdfExtension(path: Path): Path =
        if (path.fileName.toString().endsWith(PDF_EXTENSION)) {
            path
        } else {
            path.resolveSibling("${path.fileName}$PDF_EXTENSION")
        }

    fun withTxtExtension(path: Path): Path =
        if (path.fileName.toString().endsWith(TXT_EXTENSION)) {
            path
        } else {
            path.resolveSibling("${path.fileName}$TXT_EXTENSION")
        }
}

object DesktopFileOverwriteConfirmation {
    fun confirmedPath(
        path: Path,
        exists: (Path) -> Boolean,
        confirmOverwrite: (Path) -> Boolean
    ): Path? =
        if (!exists(path) || confirmOverwrite(path)) path else null
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
    fun chooseSaveProject(raceName: String? = null, suggestedFileName: String? = null): Path? =
        chooseEventFile(
            title = "Save Radio-Oracle Event File",
            mode = FileDialog.SAVE,
            defaultFileName = suggestedFileName
                ?.takeIf { it.isNotBlank() }
                ?.let(DesktopProjectFilePaths::defaultProjectFileName)
                ?: raceName?.let(DesktopProjectFilePaths::defaultProjectFileName)
        )
            ?.let(DesktopProjectFilePaths::withProjectExtension)

    /** Lets the user choose an export-copy location, returning null when cancelled. */
    fun chooseExportProject(): Path? =
        chooseEventFile("Export Radio-Oracle Event File Copy", FileDialog.SAVE)
            ?.let(DesktopProjectFilePaths::withProjectExtension)

    fun chooseExportCsv(title: String, eventName: String? = null, suffix: String? = null): Path? =
        chooseFile(
            title,
            FileDialog.SAVE,
            DesktopProjectFilePaths.CSV_EXTENSION,
            defaultFileName = eventName?.let { DesktopProjectFilePaths.defaultCsvFileName(it, suffix) }
        )
            ?.let(DesktopProjectFilePaths::withCsvExtension)

    fun chooseExportArdfJson(): Path? =
        chooseFile("Export ARDF JSON", FileDialog.SAVE, DesktopProjectFilePaths.ARDF_JSON_EXTENSION)
            ?.let(DesktopProjectFilePaths::withArdfJsonExtension)
            ?.let(::confirmOverwrite)

    fun chooseExportAndroidRaceBackupJson(eventName: String? = null): Path? =
        chooseFile(
            title = "Export Android Event File",
            mode = FileDialog.SAVE,
            extension = DesktopProjectFilePaths.ANDROID_RACE_BACKUP_JSON_EXTENSION,
            defaultFileName = eventName?.let(DesktopProjectFilePaths::defaultAndroidEventJsonFileName)
        )
            ?.let(DesktopProjectFilePaths::withAndroidRaceBackupJsonExtension)
            ?.let(::confirmOverwrite)

    fun chooseExportFinalResultsJson(): Path? =
        chooseFile("Export Final Results JSON", FileDialog.SAVE, DesktopProjectFilePaths.FINAL_RESULTS_JSON_EXTENSION)
            ?.let(DesktopProjectFilePaths::withFinalResultsJsonExtension)
            ?.let(::confirmOverwrite)

    fun chooseExportLiveResultsJson(): Path? =
        chooseFile("Export Live Results JSON", FileDialog.SAVE, DesktopProjectFilePaths.LIVE_RESULTS_JSON_EXTENSION)
            ?.let(DesktopProjectFilePaths::withLiveResultsJsonExtension)
            ?.let(::confirmOverwrite)

    fun chooseExportIofXml(title: String): Path? =
        chooseFile(title, FileDialog.SAVE, DesktopProjectFilePaths.IOF_XML_EXTENSION)
            ?.let(DesktopProjectFilePaths::withIofXmlExtension)
            ?.let(::confirmOverwrite)

    fun chooseExportHtml(title: String): Path? =
        chooseFile(title, FileDialog.SAVE, DesktopProjectFilePaths.HTML_EXTENSION)
            ?.let(DesktopProjectFilePaths::withHtmlExtension)
            ?.let(::confirmOverwrite)

    fun chooseExportCourseAnalysisPdf(defaultFileName: String? = null): Path? =
        chooseFile(
            title = "Export Course Analysis PDF",
            mode = FileDialog.SAVE,
            extension = DesktopProjectFilePaths.PDF_EXTENSION,
            defaultFileName = defaultFileName
        )
            ?.let(DesktopProjectFilePaths::withPdfExtension)
            ?.let(::confirmOverwrite)

    fun chooseExportTxt(title: String): Path? =
        chooseFile(title, FileDialog.SAVE, DesktopProjectFilePaths.TXT_EXTENSION)
            ?.let(DesktopProjectFilePaths::withTxtExtension)
            ?.let(::confirmOverwrite)

    fun chooseImportCsv(title: String): Path? =
        chooseFile(title, FileDialog.LOAD, DesktopProjectFilePaths.CSV_EXTENSION)

    fun chooseImportCsvFiles(title: String): List<Path> =
        chooseFiles(title, DesktopProjectFilePaths.CSV_EXTENSION)

    fun chooseImportKmlKmz(): Path? {
        val dialog = FileDialog(null as Frame?, "Import Controls KML/KMZ", FileDialog.LOAD)
        dialog.filenameFilter = FilenameFilter { _, name ->
            name.endsWith(".kml", ignoreCase = true) || name.endsWith(".kmz", ignoreCase = true)
        }
        dialog.file = "*.kml;*.kmz"
        dialog.isVisible = true

        val directory = dialog.directory ?: return null
        val file = dialog.file ?: return null
        return Path.of(directory, file)
    }

    fun chooseImportGpx(): Path? {
        val dialog = FileDialog(null as Frame?, "Import Controls GPX", FileDialog.LOAD)
        dialog.filenameFilter = FilenameFilter { _, name ->
            name.endsWith(".gpx", ignoreCase = true)
        }
        dialog.file = "*.gpx"
        dialog.isVisible = true

        val directory = dialog.directory ?: return null
        val file = dialog.file ?: return null
        return Path.of(directory, file)
    }

    fun chooseExportControlsRouteKmlKmz(eventName: String? = null): DesktopControlsRouteKmlKmzExportTarget? {
        val directory = DesktopEventFileLocations.preparePreferredEventFileDirectory()
        val chooser = JFileChooser(directory.toFile())
        val kmlFilter = FileNameExtensionFilter("Encrypted ZIP containing KML (*.kml.zip)", "zip")
        val kmzFilter = FileNameExtensionFilter("Encrypted ZIP containing KMZ (*.kmz.zip)", "zip")
        chooser.dialogTitle = "Export Controls KML/KMZ"
        chooser.addChoosableFileFilter(kmlFilter)
        chooser.addChoosableFileFilter(kmzFilter)
        chooser.fileFilter = kmlFilter
        chooser.selectedFile = directory
            .resolve(defaultControlsRouteExportFileName(eventName, DesktopControlsRouteKmlKmzExportFormat.Kml))
            .toFile()

        if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) {
            return null
        }

        val selectedPath = chooser.selectedFile?.toPath() ?: return null
        val selectedName = selectedPath.fileName.toString()
        val selectedFormat = when {
            selectedName.endsWith(DesktopControlsRouteKmlKmzExportFormat.Kmz.zipFileSuffix, ignoreCase = true) ->
                DesktopControlsRouteKmlKmzExportFormat.Kmz
            selectedName.endsWith(DesktopControlsRouteKmlKmzExportFormat.Kml.zipFileSuffix, ignoreCase = true) ->
                DesktopControlsRouteKmlKmzExportFormat.Kml
            chooser.fileFilter == kmzFilter -> DesktopControlsRouteKmlKmzExportFormat.Kmz
            else -> DesktopControlsRouteKmlKmzExportFormat.Kml
        }
        val exportPath = withControlsRouteKmlKmzZipExtension(selectedPath, selectedFormat)
        return confirmOverwrite(exportPath)
            ?.also(DesktopEventFileLocations::rememberEventFileDirectory)
            ?.let { DesktopControlsRouteKmlKmzExportTarget(it, selectedFormat) }
    }

    fun chooseExportControlsRouteGpx(eventName: String? = null): DesktopControlsRouteKmlKmzExportTarget? {
        val directory = DesktopEventFileLocations.preparePreferredEventFileDirectory()
        val chooser = JFileChooser(directory.toFile())
        chooser.dialogTitle = "Export Controls GPX"
        chooser.fileFilter = FileNameExtensionFilter("Encrypted ZIP containing GPX (*.gpx.zip)", "zip")
        chooser.selectedFile = directory
            .resolve(defaultControlsRouteExportFileName(eventName, DesktopControlsRouteKmlKmzExportFormat.Gpx))
            .toFile()

        if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) {
            return null
        }

        val selectedPath = chooser.selectedFile?.toPath() ?: return null
        val exportPath = withControlsRouteKmlKmzZipExtension(selectedPath, DesktopControlsRouteKmlKmzExportFormat.Gpx)
        return confirmOverwrite(exportPath)
            ?.also(DesktopEventFileLocations::rememberEventFileDirectory)
            ?.let { DesktopControlsRouteKmlKmzExportTarget(it, DesktopControlsRouteKmlKmzExportFormat.Gpx) }
    }

    fun chooseElevationRaster(): Path? {
        val dialog = FileDialog(null as Frame?, "Select Elevation Source", FileDialog.LOAD)
        dialog.filenameFilter = FilenameFilter { _, name ->
            name.endsWith(".tif", ignoreCase = true) ||
                name.endsWith(".tiff", ignoreCase = true) ||
                name.endsWith(".zip", ignoreCase = true) ||
                name.endsWith(".las", ignoreCase = true) ||
                name.endsWith(".laz", ignoreCase = true)
        }
        dialog.file = "*.tif;*.tiff;*.zip;*.las;*.laz"
        dialog.isVisible = true

        val directory = dialog.directory ?: return null
        val file = dialog.file ?: return null
        return Path.of(directory, file)
    }

    fun chooseImportDemFiles(): List<Path> {
        val dialog = FileDialog(null as Frame?, "Import DEM File", FileDialog.LOAD)
        dialog.filenameFilter = FilenameFilter { _, name ->
            name.endsWith(".json", ignoreCase = true) || name.endsWith(".zip", ignoreCase = true)
        }
        dialog.file = "*.json;*.zip"
        dialog.isMultipleMode = true
        dialog.isVisible = true

        return dialog.files.orEmpty().map { it.toPath() }
    }

    fun chooseImportAndroidRaceBackupJson(): Path? =
        chooseFile(
            "Import Android Event File",
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

    private fun chooseFiles(title: String, extension: String): List<Path> {
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
        dialog.filenameFilter = FilenameFilter { _, name -> name.endsWith(extension) }
        dialog.file = "*$extension"
        dialog.isMultipleMode = true
        dialog.isVisible = true

        return dialog.files.orEmpty().map { it.toPath() }
    }

    private fun defaultControlsRouteExportFileName(
        eventName: String?,
        format: DesktopControlsRouteKmlKmzExportFormat
    ): String {
        val stem = eventName
            ?.let { DesktopProjectFilePaths.defaultCsvFileName(it, "controls routes") }
            ?.removeSuffix(DesktopProjectFilePaths.CSV_EXTENSION)
            ?: "controls routes"
        return "$stem${format.zipFileSuffix}"
    }

    private fun withControlsRouteKmlKmzZipExtension(
        path: Path,
        format: DesktopControlsRouteKmlKmzExportFormat
    ): Path {
        val fileName = path.fileName.toString()
        if (
            fileName.endsWith(DesktopControlsRouteKmlKmzExportFormat.Kml.zipFileSuffix, ignoreCase = true) ||
            fileName.endsWith(DesktopControlsRouteKmlKmzExportFormat.Kmz.zipFileSuffix, ignoreCase = true) ||
            fileName.endsWith(DesktopControlsRouteKmlKmzExportFormat.Gpx.zipFileSuffix, ignoreCase = true)
        ) {
            return path
        }
        val stem = fileName.removeSuffix(".zip")
        return path.resolveSibling("$stem${format.zipFileSuffix}")
    }

    private fun confirmOverwrite(path: Path): Path? =
        DesktopFileOverwriteConfirmation.confirmedPath(path, Files::exists) { selectedPath ->
            JOptionPane.showConfirmDialog(
                null,
                "${selectedPath.fileName} already exists. Replace it?",
                "Replace Existing File?",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            ) == JOptionPane.YES_OPTION
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
