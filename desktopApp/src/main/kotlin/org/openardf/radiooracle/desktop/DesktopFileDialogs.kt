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

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter
import java.nio.file.Files
import java.nio.file.Path
import java.util.prefs.Preferences
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.filechooser.FileFilter
import org.openardf.radiooracle.shared.event.EVENT_SERIES_FILE_NAME
import org.openardf.radiooracle.shared.event.EVENT_SERIES_NAMED_FILE_SUFFIX
import org.openardf.radiooracle.shared.event.EVENT_SERIES_ARCHIVE_FILE_SUFFIX
import org.openardf.radiooracle.shared.event.isEventSeriesArchiveFileName
import org.openardf.radiooracle.shared.event.isEventSeriesFileName

/** Race File path helpers shared by desktop file dialogs and tests. */
object DesktopProjectFilePaths {
    const val PROJECT_EXTENSION = ".json"
    private const val LEGACY_PROJECT_EXTENSION = ".rom.json"
    const val ANDROID_RACE_BACKUP_JSON_EXTENSION = ".ardfjs"
    const val ARDF_JSON_EXTENSION = ".ardf.json"
    const val FINAL_RESULTS_JSON_EXTENSION = ".final-results.json"
    const val LIVE_RESULTS_JSON_EXTENSION = ".live-results.json"
    const val IOF_XML_EXTENSION = ".iof.xml"
    const val XML_EXTENSION = ".xml"
    const val CSV_EXTENSION = ".csv"
    const val HTML_EXTENSION = ".html"
    const val PDF_EXTENSION = ".pdf"
    const val KML_EXTENSION = ".kml"
    const val TXT_EXTENSION = ".txt"
    const val ZIP_EXTENSION = ".zip"
    const val SERIES_ARCHIVE_EXTENSION = EVENT_SERIES_ARCHIVE_FILE_SUFFIX

    /** Returns a path with the standard Radio-Oracle desktop Race File extension. */
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
            .ifBlank { "Race File" }

    fun defaultProjectFileName(eventName: String): String =
        withProjectExtension(Path.of(defaultFileStem(eventName))).fileName.toString()

    /** Returns the editable Race File display name without current or legacy project extensions. */
    fun projectFileDisplayStem(fileName: String): String =
        fileName
            .removeSuffix(LEGACY_PROJECT_EXTENSION)
            .removeSuffix(PROJECT_EXTENSION)

    fun defaultAndroidEventJsonFileName(eventName: String): String =
        withAndroidRaceBackupJsonExtension(Path.of(defaultFileStem(eventName))).fileName.toString()

    fun defaultCsvFileName(eventName: String, suffix: String? = null): String {
        val sanitizedSuffix = suffix
            ?.let(::defaultFileStem)
            ?.takeUnless { it == "Race File" }
        val stem = listOfNotNull(defaultFileStem(eventName), sanitizedSuffix).joinToString(" ")
        return withCsvExtension(Path.of(stem)).fileName.toString()
    }

    fun defaultPdfFileName(eventName: String, suffix: String? = null): String {
        val sanitizedSuffix = suffix
            ?.let(::defaultFileStem)
            ?.takeUnless { it == "Race File" }
        val stem = listOfNotNull(defaultFileStem(eventName), sanitizedSuffix).joinToString(" ")
        return withPdfExtension(Path.of(stem)).fileName.toString()
    }

    fun isProjectFileName(fileName: String): Boolean =
        fileName.endsWith(PROJECT_EXTENSION, ignoreCase = true) ||
            fileName.endsWith(LEGACY_PROJECT_EXTENSION, ignoreCase = true)

    fun isEventSeriesManifestName(fileName: String): Boolean =
        isEventSeriesFileName(fileName)

    fun isEventSeriesArchiveName(fileName: String): Boolean =
        isEventSeriesArchiveFileName(fileName)

    fun isAndroidRaceBackupJsonFileName(fileName: String): Boolean =
        fileName.endsWith(ANDROID_RACE_BACKUP_JSON_EXTENSION, ignoreCase = true)

    fun isOpenableEventFileName(fileName: String): Boolean =
        isProjectFileName(fileName) ||
            isAndroidRaceBackupJsonFileName(fileName) ||
            isEventSeriesManifestName(fileName) ||
            isEventSeriesArchiveName(fileName)

    fun withSeriesArchiveExtension(path: Path): Path =
        if (isEventSeriesArchiveName(path.fileName.toString())) {
            path
        } else {
            path.resolveSibling("${path.fileName}$SERIES_ARCHIVE_EXTENSION")
        }

    fun withCsvExtension(path: Path): Path =
        if (path.fileName.toString().endsWith(CSV_EXTENSION)) {
            path
        } else {
            path.resolveSibling("${path.fileName}$CSV_EXTENSION")
        }

    fun withAndroidRaceBackupJsonExtension(path: Path): Path =
        if (path.fileName.toString().endsWith(ANDROID_RACE_BACKUP_JSON_EXTENSION, ignoreCase = true)) {
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

    fun withXmlExtension(path: Path): Path =
        if (path.fileName.toString().endsWith(XML_EXTENSION)) {
            path
        } else {
            path.resolveSibling("${path.fileName}$XML_EXTENSION")
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

    fun withKmlExtension(path: Path): Path =
        if (path.fileName.toString().endsWith(KML_EXTENSION, ignoreCase = true)) {
            path
        } else {
            path.resolveSibling("${path.fileName}$KML_EXTENSION")
        }

    fun withTxtExtension(path: Path): Path =
        if (path.fileName.toString().endsWith(TXT_EXTENSION)) {
            path
        } else {
            path.resolveSibling("${path.fileName}$TXT_EXTENSION")
        }

    fun withZipExtension(path: Path): Path =
        if (path.fileName.toString().endsWith(ZIP_EXTENSION, ignoreCase = true)) {
            path
        } else {
            path.resolveSibling("${path.fileName}$ZIP_EXTENSION")
        }
}

object DesktopFileOverwriteConfirmation {
    fun confirmedPath(
        path: Path,
        exists: (Path) -> Boolean,
        confirmOverwrite: (Path) -> Boolean
    ): Path? =
        if (!exists(path) || confirmOverwrite(path)) path else null

    fun confirmedExtensionAdjustedPath(
        selectedPath: Path,
        finalPath: Path,
        exists: (Path) -> Boolean,
        confirmOverwrite: (Path) -> Boolean
    ): Path? =
        if (selectedPath.toAbsolutePath().normalize() == finalPath.toAbsolutePath().normalize()) {
            finalPath
        } else {
            confirmedPath(finalPath, exists, confirmOverwrite)
        }
}

/** File filters for selectable Race Files; directories stay visible so users can browse normally. */
object DesktopEventFileChooserFilters {
    fun openableEventFiles(): FileFilter =
        EventFileFilter(
            description = "Radio-Oracle Race and Series Files (*.json, *.rom.json, *.ardfjs, *.roseries)",
            acceptsFileName = DesktopProjectFilePaths::isOpenableEventFileName
        )

    fun desktopEventFiles(): FileFilter =
        EventFileFilter(
            description = "Radio-Oracle Desktop Race Files (*.json, *.rom.json)",
            acceptsFileName = { name ->
                DesktopProjectFilePaths.isProjectFileName(name) &&
                    !DesktopProjectFilePaths.isEventSeriesManifestName(name)
            }
        )

    private class EventFileFilter(
        private val description: String,
        private val acceptsFileName: (String) -> Boolean
    ) : FileFilter() {
        override fun accept(file: File): Boolean =
            file.isDirectory || acceptsFileName(file.name)

        override fun getDescription(): String =
            description
    }
}

/** Remembers and prepares the desktop directory used for user-visible Race Files. */
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

/** AWT-backed file chooser for desktop Race Files. */
object DesktopFileDialogs {
    /** Lets the user choose an existing Race File, returning null when cancelled. */
    fun chooseOpenProject(): Path? =
        chooseLoadEventFile("Open Radio-Oracle Race File", DesktopEventFileChooserFilters.openableEventFiles())

    /** Lets the user choose an existing desktop Race File to add to a Race Series. */
    fun chooseEventSeriesMemberEventFile(): Path? =
        chooseLoadEventFile("Add Race File to Race Series", DesktopEventFileChooserFilters.desktopEventFiles())

    /** Lets the user choose an existing Race Series manifest, returning null when cancelled. */
    fun chooseOpenEventSeries(): Path? =
        chooseSeriesFile("Open Radio-Oracle Race Series")

    /** Lets the user choose a destination for a new live `.roseries` container. */
    fun chooseSaveEventSeries(defaultFileName: String): Path? =
        chooseSaveFile(
            title = "Save Radio-Oracle Series File",
            extension = DesktopProjectFilePaths.SERIES_ARCHIVE_EXTENSION,
            defaultFileName = defaultFileName
        ) { DesktopProjectFilePaths.withSeriesArchiveExtension(it) }

    /** Lets the user choose a save location, returning null when cancelled. */
    fun chooseSaveProject(raceName: String? = null, suggestedFileName: String? = null): Path? =
        chooseSaveEventFile(
            title = "Save Radio-Oracle Race File",
            defaultFileName = suggestedFileName
                ?.takeIf { it.isNotBlank() }
                ?.let(DesktopProjectFilePaths::defaultProjectFileName)
                ?: raceName?.let(DesktopProjectFilePaths::defaultProjectFileName)
        )

    /** Lets the user choose an export-copy location, returning null when cancelled. */
    fun chooseExportProject(): Path? =
        chooseSaveEventFile("Export Radio-Oracle Race File Copy")

    fun chooseExportCsv(title: String, eventName: String? = null, suffix: String? = null): Path? =
        chooseSaveFile(
            title = title,
            extension = DesktopProjectFilePaths.CSV_EXTENSION,
            defaultFileName = eventName?.let { DesktopProjectFilePaths.defaultCsvFileName(it, suffix) }
        ) { DesktopProjectFilePaths.withCsvExtension(it) }

    fun chooseExportArdfJson(): Path? =
        chooseSaveFile("Export ARDF JSON", DesktopProjectFilePaths.ARDF_JSON_EXTENSION) {
            DesktopProjectFilePaths.withArdfJsonExtension(it)
        }

    fun chooseExportAndroidRaceBackupJson(eventName: String? = null): Path? =
        chooseSaveFile(
            title = "Save Android Race File",
            extension = DesktopProjectFilePaths.ANDROID_RACE_BACKUP_JSON_EXTENSION,
            defaultFileName = eventName?.let(DesktopProjectFilePaths::defaultAndroidEventJsonFileName)
        ) { DesktopProjectFilePaths.withAndroidRaceBackupJsonExtension(it) }

    fun chooseExportAndroidEventSeriesPackage(defaultFileName: String): Path? =
        chooseSaveFile(
            title = "Save Radio-Oracle Series File",
            extension = DesktopProjectFilePaths.SERIES_ARCHIVE_EXTENSION,
            defaultFileName = defaultFileName
        ) { DesktopProjectFilePaths.withSeriesArchiveExtension(it) }

    fun chooseExportFinalResultsJson(): Path? =
        chooseSaveFile("Export Final Results JSON", DesktopProjectFilePaths.FINAL_RESULTS_JSON_EXTENSION) {
            DesktopProjectFilePaths.withFinalResultsJsonExtension(it)
        }

    fun chooseExportLiveResultsJson(): Path? =
        chooseSaveFile("Export Live Results JSON", DesktopProjectFilePaths.LIVE_RESULTS_JSON_EXTENSION) {
            DesktopProjectFilePaths.withLiveResultsJsonExtension(it)
        }

    fun chooseExportIofXml(title: String): Path? =
        chooseSaveFile(title, DesktopProjectFilePaths.IOF_XML_EXTENSION) {
            DesktopProjectFilePaths.withIofXmlExtension(it)
        }

    fun chooseExportXml(title: String): Path? =
        chooseSaveFile(title, DesktopProjectFilePaths.XML_EXTENSION) {
            DesktopProjectFilePaths.withXmlExtension(it)
        }

    fun chooseExportHtml(title: String): Path? =
        chooseSaveFile(title, DesktopProjectFilePaths.HTML_EXTENSION) {
            DesktopProjectFilePaths.withHtmlExtension(it)
        }

    fun confirmReplacePublicResultsSite(retainedEntryCount: Int): Boolean =
        JOptionPane.showConfirmDialog(
            null,
            buildString {
                append("Replace ")
                append(retainedEntryCount)
                append(" retained public result ")
                append(if (retainedEntryCount == 1) "entry" else "entries")
                append("?\n\n")
                append("The next Cloudflare publish will contain only the current race or series.")
            },
            "Replace Previous Public Results",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        ) == JOptionPane.YES_OPTION

    fun chooseExportEventSeriesDirectory(): Path? {
        val directory = DesktopEventFileLocations.preparePreferredEventFileDirectory()
        val chooser = JFileChooser(directory.toFile())
        chooser.dialogTitle = "Export Race Series"
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        chooser.approveButtonText = "Export Here"
        if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) {
            return null
        }
        return chooser.selectedFile?.toPath()?.also(DesktopEventFileLocations::rememberEventFileDirectory)
    }

    fun chooseExportCourseAnalysisPdf(defaultFileName: String? = null): Path? =
        chooseSaveFile(
            title = "Export Course Analysis PDF",
            extension = DesktopProjectFilePaths.PDF_EXTENSION,
            defaultFileName = defaultFileName
        ) { DesktopProjectFilePaths.withPdfExtension(it) }

    fun chooseExportPrintableStartListPdf(defaultFileName: String? = null): Path? =
        chooseSaveFile(
            title = "Export Printable Start List PDF",
            extension = DesktopProjectFilePaths.PDF_EXTENSION,
            defaultFileName = defaultFileName
        ) { DesktopProjectFilePaths.withPdfExtension(it) }

    fun chooseExportResultReportPdf(defaultFileName: String? = null): Path? =
        chooseSaveFile(
            title = "Export Results Report PDF",
            extension = DesktopProjectFilePaths.PDF_EXTENSION,
            defaultFileName = defaultFileName
        ) { DesktopProjectFilePaths.withPdfExtension(it) }

    fun chooseExportClassicCourseGeneratorPdf(defaultFileName: String? = null): Path? =
        chooseExportCourseGeneratorPdf(
            title = "Export Classic Route Generator PDF",
            defaultFileName = defaultFileName
        )

    fun chooseExportCourseGeneratorPdf(title: String, defaultFileName: String? = null): Path? =
        chooseSaveFile(
            title = title,
            extension = DesktopProjectFilePaths.PDF_EXTENSION,
            defaultFileName = defaultFileName
        ) { DesktopProjectFilePaths.withPdfExtension(it) }

    fun chooseExportCreateCourseKml(defaultFileName: String? = null): Path? =
        chooseSaveFile(
            title = "Create Course KML",
            extension = DesktopProjectFilePaths.KML_EXTENSION,
            defaultFileName = defaultFileName
        ) { DesktopProjectFilePaths.withKmlExtension(it) }

    fun chooseExportTxt(title: String): Path? =
        chooseSaveFile(title, DesktopProjectFilePaths.TXT_EXTENSION) {
            DesktopProjectFilePaths.withTxtExtension(it)
        }

    fun chooseImportCsv(title: String): Path? =
        chooseFile(title, FileDialog.LOAD, DesktopProjectFilePaths.CSV_EXTENSION)

    fun chooseImportCsvFiles(title: String): List<Path> =
        chooseFiles(title, DesktopProjectFilePaths.CSV_EXTENSION)

    fun chooseImportCompetitorSpreadsheet(): Path? {
        val chooser = JFileChooser(DesktopCompetitorSpreadsheetImportPreferences.preferredDirectory().toFile())
        chooser.dialogTitle = "Import Competitor Spreadsheet"
        chooser.fileSelectionMode = JFileChooser.FILES_ONLY
        chooser.fileFilter = FileNameExtensionFilter("Excel workbook (*.xlsx)", "xlsx")
        chooser.isAcceptAllFileFilterUsed = false
        chooser.approveButtonText = "Select"
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
            return null
        }
        return chooser.selectedFile
            ?.toPath()
            ?.also(DesktopCompetitorSpreadsheetImportPreferences::rememberFile)
    }

    fun chooseImportIofXml(title: String): Path? =
        chooseFile(title, FileDialog.LOAD, DesktopProjectFilePaths.XML_EXTENSION)

    fun chooseImportKmlKmz(): Path? {
        val dialog = FileDialog(null as Frame?, "Import Controls KML/KMZ", FileDialog.LOAD)
        dialog.filenameFilter = extensionFilenameFilter(".kml", ".kmz")
        dialog.file = "*.kml;*.kmz"
        dialog.isVisible = true

        val directory = dialog.directory ?: return null
        val file = dialog.file ?: return null
        return Path.of(directory, file)
    }

    fun chooseKmlToolsFile(): Path? {
        val dialog = FileDialog(null as Frame?, "Choose KML/KMZ File", FileDialog.LOAD)
        dialog.filenameFilter = extensionFilenameFilter(".kml", ".kmz")
        dialog.file = "*.kml;*.kmz"
        dialog.isVisible = true

        val directory = dialog.directory ?: return null
        val file = dialog.file ?: return null
        return Path.of(directory, file)
    }

    fun chooseImportGpx(): Path? {
        val dialog = FileDialog(null as Frame?, "Import Controls GPX", FileDialog.LOAD)
        dialog.filenameFilter = extensionFilenameFilter(".gpx")
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

    fun chooseExportCourseOverlays(
        eventName: String? = null,
        defaultStartRadiusMeters: Int,
        defaultFinishRadiusMeters: Int
    ): DesktopCourseOverlayExportTarget? {
        val directory = DesktopEventFileLocations.preparePreferredEventFileDirectory()
        val baseMapChooser = JFileChooser(directory.toFile())
        baseMapChooser.dialogTitle = "Choose Base OOM Map"
        baseMapChooser.fileFilter = FileNameExtensionFilter("OpenOrienteering Mapper maps (*.omap, *.xmap)", "omap", "xmap")
        if (baseMapChooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
            return null
        }
        val baseMapPath = baseMapChooser.selectedFile?.toPath() ?: return null

        val outputChooser = JFileChooser(baseMapPath.parent?.toFile() ?: directory.toFile())
        outputChooser.dialogTitle = "Export Course Overlay Files"
        outputChooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        outputChooser.approveButtonText = "Export Here"
        if (outputChooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) {
            return null
        }
        val outputDirectory = outputChooser.selectedFile?.toPath() ?: return null

        val startRadiusField = javax.swing.JTextField(defaultStartRadiusMeters.toString(), 8)
        val finishRadiusField = javax.swing.JTextField(defaultFinishRadiusMeters.toString(), 8)
        val panel = javax.swing.JPanel(java.awt.GridLayout(0, 2, 8, 8)).apply {
            add(javax.swing.JLabel("Start exclusion radius (m)"))
            add(startRadiusField)
            add(javax.swing.JLabel("Finish exclusion radius (m)"))
            add(finishRadiusField)
        }
        val title = eventName?.takeIf { it.isNotBlank() }?.let { "Course Overlay Export - $it" }
            ?: "Course Overlay Export"
        val choice = JOptionPane.showConfirmDialog(
            null,
            panel,
            title,
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE
        )
        if (choice != JOptionPane.OK_OPTION) {
            return null
        }
        val startRadius = startRadiusField.text.trim().toIntOrNull()
        val finishRadius = finishRadiusField.text.trim().toIntOrNull()
        if (startRadius == null || startRadius < 0 || finishRadius == null || finishRadius < 0) {
            JOptionPane.showMessageDialog(
                null,
                "Exclusion radii must be whole numbers greater than or equal to 0.",
                "Course Overlay Export",
                JOptionPane.ERROR_MESSAGE
            )
            return null
        }
        DesktopEventFileLocations.rememberEventFileDirectory(outputDirectory)
        return DesktopCourseOverlayExportTarget(
            baseMapPath = baseMapPath,
            outputDirectory = outputDirectory,
            startExclusionRadiusMeters = startRadius,
            finishExclusionRadiusMeters = finishRadius
        )
    }

    fun chooseElevationRaster(): List<Path> {
        val dialog = FileDialog(null as Frame?, "Select Elevation Source", FileDialog.LOAD)
        dialog.filenameFilter = extensionFilenameFilter(".tif", ".tiff", ".zip", ".las", ".laz")
        dialog.file = "*.tif;*.tiff;*.zip;*.las;*.laz"
        dialog.isMultipleMode = true
        dialog.isVisible = true

        val selectedFiles = dialog.files.orEmpty().map { it.toPath() }
        if (selectedFiles.isNotEmpty()) {
            return selectedFiles
        }
        val directory = dialog.directory ?: return emptyList()
        val file = dialog.file ?: return emptyList()
        return listOf(Path.of(directory, file))
    }

    fun chooseImportDemFiles(): List<Path> {
        val dialog = FileDialog(null as Frame?, "Import DEM File", FileDialog.LOAD)
        dialog.filenameFilter = extensionFilenameFilter(".json", ".zip")
        dialog.file = "*.json;*.zip"
        dialog.isMultipleMode = true
        dialog.isVisible = true

        return dialog.files.orEmpty().map { it.toPath() }
    }

    private fun chooseFile(title: String, mode: Int, extension: String, defaultFileName: String? = null): Path? {
        val dialog = FileDialog(null as Frame?, title, mode)
        dialog.filenameFilter = extensionFilenameFilter(extension)
        dialog.file = defaultFileName ?: "*$extension"
        dialog.isVisible = true

        val directory = dialog.directory ?: return null
        val file = dialog.file ?: return null
        return Path.of(directory, file)
    }

    private fun chooseSaveFile(
        title: String,
        extension: String,
        defaultFileName: String? = null,
        withExtension: (Path) -> Path
    ): Path? {
        val selectedPath = chooseFile(title, FileDialog.SAVE, extension, defaultFileName) ?: return null
        val finalPath = withExtension(selectedPath)
        return confirmExtensionAdjustedOverwrite(selectedPath, finalPath)
    }

    private fun chooseFiles(title: String, extension: String): List<Path> {
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
        dialog.filenameFilter = extensionFilenameFilter(extension)
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

    private fun confirmExtensionAdjustedOverwrite(selectedPath: Path, finalPath: Path): Path? =
        DesktopFileOverwriteConfirmation.confirmedExtensionAdjustedPath(selectedPath, finalPath, Files::exists) { path ->
            JOptionPane.showConfirmDialog(
                null,
                "${path.fileName} already exists. Replace it?",
                "Replace Existing File?",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            ) == JOptionPane.YES_OPTION
        }

    private fun chooseSaveEventFile(title: String, defaultFileName: String? = null): Path? {
        val selectedPath = chooseEventFile(title, FileDialog.SAVE, defaultFileName) ?: return null
        val finalPath = DesktopProjectFilePaths.withProjectExtension(selectedPath)
        return confirmExtensionAdjustedOverwrite(selectedPath, finalPath)
    }

    private fun chooseEventFile(title: String, mode: Int, defaultFileName: String? = null): Path? {
        val directory = DesktopEventFileLocations.preparePreferredEventFileDirectory()
        val dialog = FileDialog(null as Frame?, title, mode)
        dialog.filenameFilter = navigableFilenameFilter { name -> DesktopProjectFilePaths.isOpenableEventFileName(name) }
        dialog.directory = directory.toString()
        dialog.file = defaultFileName ?: listOf(
            "*${DesktopProjectFilePaths.PROJECT_EXTENSION}",
            "*${DesktopProjectFilePaths.ANDROID_RACE_BACKUP_JSON_EXTENSION}"
        ).joinToString(";")
        dialog.isVisible = true

        val selectedDirectory = dialog.directory ?: return null
        val file = dialog.file ?: return null
        return Path.of(selectedDirectory, file).also(DesktopEventFileLocations::rememberEventFileDirectory)
    }

    private fun chooseLoadEventFile(title: String, fileFilter: FileFilter): Path? {
        val directory = DesktopEventFileLocations.preparePreferredEventFileDirectory()
        val chooser = JFileChooser(directory.toFile())
        chooser.dialogTitle = title
        chooser.fileSelectionMode = JFileChooser.FILES_ONLY
        chooser.fileFilter = fileFilter
        chooser.isAcceptAllFileFilterUsed = false
        chooser.approveButtonText = "Open"
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
            return null
        }
        return chooser.selectedFile?.toPath()?.also(DesktopEventFileLocations::rememberEventFileDirectory)
    }

    private fun chooseSeriesFile(title: String): Path? {
        val directory = DesktopEventFileLocations.preparePreferredEventFileDirectory()
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
        dialog.filenameFilter = navigableFilenameFilter { name ->
            DesktopProjectFilePaths.isEventSeriesManifestName(name) ||
                DesktopProjectFilePaths.isEventSeriesArchiveName(name)
        }
        dialog.directory = directory.toString()
        dialog.file = listOf(
            "*$EVENT_SERIES_ARCHIVE_FILE_SUFFIX",
            "*$EVENT_SERIES_NAMED_FILE_SUFFIX",
            EVENT_SERIES_FILE_NAME
        ).joinToString(";")
        dialog.isVisible = true

        val selectedDirectory = dialog.directory ?: return null
        val file = dialog.file ?: return null
        return Path.of(selectedDirectory, file).also(DesktopEventFileLocations::rememberEventFileDirectory)
    }

    private fun extensionFilenameFilter(vararg extensions: String): FilenameFilter =
        navigableFilenameFilter { name ->
            extensions.any { extension -> name.endsWith(extension, ignoreCase = true) }
        }

    private fun navigableFilenameFilter(fileNameIsAccepted: (String) -> Boolean): FilenameFilter =
        FilenameFilter { directory, name ->
            // macOS FileDialog may apply FilenameFilter to folders as well as files. Always allow
            // directories through so users can navigate to Downloads, Desktop, external drives, etc.
            File(directory, name).isDirectory || fileNameIsAccepted(name)
        }
}
