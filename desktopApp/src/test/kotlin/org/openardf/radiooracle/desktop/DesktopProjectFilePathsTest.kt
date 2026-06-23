package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class DesktopProjectFilePathsTest {
    @Test
    fun defaultsEventFilesToRadioOracleFolderUnderDocuments() {
        assertEquals(
            Path.of("/Users/example/Documents/Radio-Oracle"),
            DesktopEventFileLocations.defaultEventFileDirectory(Path.of("/Users/example"))
        )
    }

    @Test
    fun keepsExistingProjectFileExtension() {
        val path = Path.of("event.json")

        assertEquals(path, DesktopProjectFilePaths.withProjectExtension(path))
    }

    @Test
    fun keepsExistingProjectFileExtensionCaseInsensitively() {
        val path = Path.of("event.JSON")

        assertEquals(path, DesktopProjectFilePaths.withProjectExtension(path))
    }

    @Test
    fun keepsExistingLegacyProjectFileExtension() {
        val path = Path.of("event.rom.json")

        assertEquals(path, DesktopProjectFilePaths.withProjectExtension(path))
    }

    @Test
    fun recognizesCurrentAndLegacyEventFileNames() {
        assertTrue(DesktopProjectFilePaths.isProjectFileName("event.json"))
        assertTrue(DesktopProjectFilePaths.isProjectFileName("event.JSON"))
        assertTrue(DesktopProjectFilePaths.isProjectFileName("event.rom.json"))
        assertFalse(DesktopProjectFilePaths.isProjectFileName("event.csv"))
    }

    @Test
    fun recognizesOpenableDesktopAndAndroidEventFileNames() {
        assertTrue(DesktopProjectFilePaths.isOpenableEventFileName("event.json"))
        assertTrue(DesktopProjectFilePaths.isOpenableEventFileName("event.rom.json"))
        assertTrue(DesktopProjectFilePaths.isOpenableEventFileName("event.ardfjs"))
        assertTrue(DesktopProjectFilePaths.isOpenableEventFileName("event.ARDFJS"))
        assertTrue(DesktopProjectFilePaths.isOpenableEventFileName("series.radio-oracle.json"))
        assertTrue(DesktopProjectFilePaths.isOpenableEventFileName("Championship Week.series.radio-oracle.json"))
        assertFalse(DesktopProjectFilePaths.isOpenableEventFileName("event.csv"))
    }

    @Test
    fun openEventFileChooserAcceptsDesktopAndAndroidEventFiles() {
        val filter = DesktopEventFileChooserFilters.openableEventFiles()
        val directory = Files.createTempDirectory("radio-oracle-event-file-filter").toFile()

        assertTrue(filter.accept(File("event.json")))
        assertTrue(filter.accept(File("event.rom.json")))
        assertTrue(filter.accept(File("event.ardfjs")))
        assertTrue(filter.accept(File("series.radio-oracle.json")))
        assertTrue(filter.accept(File("Championship Week.series.radio-oracle.json")))
        assertTrue(filter.accept(directory))
        assertFalse(filter.accept(File("event.csv")))
    }

    @Test
    fun seriesMemberChooserAcceptsOnlyDesktopEventFiles() {
        val filter = DesktopEventFileChooserFilters.desktopEventFiles()
        val directory = Files.createTempDirectory("radio-oracle-desktop-event-file-filter").toFile()

        assertTrue(filter.accept(File("event.json")))
        assertTrue(filter.accept(File("event.rom.json")))
        assertTrue(filter.accept(directory))
        assertFalse(filter.accept(File("event.ardfjs")))
        assertFalse(filter.accept(File("series.radio-oracle.json")))
        assertFalse(filter.accept(File("Championship Week.series.radio-oracle.json")))
        assertFalse(filter.accept(File("event.csv")))
    }

    @Test
    fun appendsProjectFileExtensionWhenMissing() {
        assertEquals(
            Path.of("event.json"),
            DesktopProjectFilePaths.withProjectExtension(Path.of("event"))
        )
    }

    @Test
    fun buildsDefaultProjectFileNameFromEventName() {
        assertEquals(
            "Demo Event.json",
            DesktopProjectFilePaths.defaultProjectFileName("Demo Event")
        )
        assertEquals(
            "Demo Event.json",
            DesktopProjectFilePaths.defaultProjectFileName("Demo/Event")
        )
        assertEquals(
            "Event File.json",
            DesktopProjectFilePaths.defaultProjectFileName(" ")
        )
    }

    @Test
    fun removesProjectFileExtensionsForEditableDisplay() {
        assertEquals(
            "Demo Event",
            DesktopProjectFilePaths.projectFileDisplayStem("Demo Event.json")
        )
        assertEquals(
            "Demo Event",
            DesktopProjectFilePaths.projectFileDisplayStem("Demo Event.rom.json")
        )
        assertEquals(
            "Demo Event",
            DesktopProjectFilePaths.projectFileDisplayStem("Demo Event")
        )
    }

    @Test
    fun buildsDefaultAndroidEventJsonFileNameFromEventName() {
        assertEquals(
            "Demo Event.ardfjs",
            DesktopProjectFilePaths.defaultAndroidEventJsonFileName("Demo Event")
        )
        assertEquals(
            "Demo Event.ardfjs",
            DesktopProjectFilePaths.defaultAndroidEventJsonFileName("Demo/Event")
        )
        assertEquals(
            "Event File.ardfjs",
            DesktopProjectFilePaths.defaultAndroidEventJsonFileName(" ")
        )
    }

    @Test
    fun buildsDefaultCsvFileNameFromEventName() {
        assertEquals(
            "Demo Event.csv",
            DesktopProjectFilePaths.defaultCsvFileName("Demo Event")
        )
        assertEquals(
            "Demo Event.csv",
            DesktopProjectFilePaths.defaultCsvFileName("Demo/Event")
        )
        assertEquals(
            "Event File.csv",
            DesktopProjectFilePaths.defaultCsvFileName(" ")
        )
    }

    @Test
    fun buildsDefaultCsvFileNameFromEventNameAndSuffix() {
        assertEquals(
            "Demo Event categories.csv",
            DesktopProjectFilePaths.defaultCsvFileName("Demo Event", "categories")
        )
        assertEquals(
            "Demo Event starts by category.csv",
            DesktopProjectFilePaths.defaultCsvFileName("Demo/Event", "starts/by:category")
        )
        assertEquals(
            "Demo Event.csv",
            DesktopProjectFilePaths.defaultCsvFileName("Demo Event", " ")
        )
    }

    @Test
    fun keepsExistingCsvExtension() {
        val path = Path.of("event.csv")

        assertEquals(path, DesktopProjectFilePaths.withCsvExtension(path))
    }

    @Test
    fun appendsCsvExtensionWhenMissing() {
        assertEquals(
            Path.of("event.csv"),
            DesktopProjectFilePaths.withCsvExtension(Path.of("event"))
        )
    }

    @Test
    fun overwriteConfirmationKeepsNewFileWithoutPrompt() {
        val path = Path.of("event.csv")
        var prompted = false

        assertEquals(
            path,
            DesktopFileOverwriteConfirmation.confirmedPath(
                path = path,
                exists = { false },
                confirmOverwrite = {
                    prompted = true
                    false
                }
            )
        )
        assertFalse(prompted)
    }

    @Test
    fun overwriteConfirmationKeepsExistingFileWhenConfirmed() {
        val path = Path.of("event.csv")

        assertEquals(
            path,
            DesktopFileOverwriteConfirmation.confirmedPath(
                path = path,
                exists = { true },
                confirmOverwrite = { true }
            )
        )
    }

    @Test
    fun overwriteConfirmationCancelsExistingFileWhenRejected() {
        assertEquals(
            null,
            DesktopFileOverwriteConfirmation.confirmedPath(
                path = Path.of("event.csv"),
                exists = { true },
                confirmOverwrite = { false }
            )
        )
    }

    @Test
    fun extensionAdjustedOverwriteSkipsPromptWhenNativeDialogConfirmedSamePath() {
        val path = Path.of("event.csv")
        var prompted = false

        assertEquals(
            path,
            DesktopFileOverwriteConfirmation.confirmedExtensionAdjustedPath(
                selectedPath = path,
                finalPath = path,
                exists = { true },
                confirmOverwrite = {
                    prompted = true
                    false
                }
            )
        )
        assertFalse(prompted)
    }

    @Test
    fun extensionAdjustedOverwritePromptsWhenFinalPathDiffersAndExists() {
        val selectedPath = Path.of("event")
        val finalPath = Path.of("event.csv")
        var promptedPath: Path? = null

        assertEquals(
            finalPath,
            DesktopFileOverwriteConfirmation.confirmedExtensionAdjustedPath(
                selectedPath = selectedPath,
                finalPath = finalPath,
                exists = { it == finalPath },
                confirmOverwrite = {
                    promptedPath = it
                    true
                }
            )
        )
        assertEquals(finalPath, promptedPath)
    }

    @Test
    fun extensionAdjustedOverwriteCancelsWhenFinalPathDiffersAndRejected() {
        assertEquals(
            null,
            DesktopFileOverwriteConfirmation.confirmedExtensionAdjustedPath(
                selectedPath = Path.of("event"),
                finalPath = Path.of("event.csv"),
                exists = { true },
                confirmOverwrite = { false }
            )
        )
    }

    @Test
    fun keepsExistingArdfJsonExtension() {
        val path = Path.of("event.ardf.json")

        assertEquals(path, DesktopProjectFilePaths.withArdfJsonExtension(path))
    }

    @Test
    fun appendsArdfJsonExtensionWhenMissing() {
        assertEquals(
            Path.of("event.ardf.json"),
            DesktopProjectFilePaths.withArdfJsonExtension(Path.of("event"))
        )
    }

    @Test
    fun keepsExistingAndroidRaceBackupJsonExtension() {
        val path = Path.of("race.ardfjs")

        assertEquals(path, DesktopProjectFilePaths.withAndroidRaceBackupJsonExtension(path))
    }

    @Test
    fun keepsExistingAndroidRaceBackupJsonExtensionCaseInsensitively() {
        val path = Path.of("race.ARDFJS")

        assertEquals(path, DesktopProjectFilePaths.withAndroidRaceBackupJsonExtension(path))
    }

    @Test
    fun appendsAndroidRaceBackupJsonExtensionWhenMissing() {
        assertEquals(
            Path.of("race.ardfjs"),
            DesktopProjectFilePaths.withAndroidRaceBackupJsonExtension(Path.of("race"))
        )
    }

    @Test
    fun keepsExistingFinalResultsJsonExtension() {
        val path = Path.of("event.final-results.json")

        assertEquals(path, DesktopProjectFilePaths.withFinalResultsJsonExtension(path))
    }

    @Test
    fun appendsFinalResultsJsonExtensionWhenMissing() {
        assertEquals(
            Path.of("event.final-results.json"),
            DesktopProjectFilePaths.withFinalResultsJsonExtension(Path.of("event"))
        )
    }

    @Test
    fun keepsExistingLiveResultsJsonExtension() {
        val path = Path.of("event.live-results.json")

        assertEquals(path, DesktopProjectFilePaths.withLiveResultsJsonExtension(path))
    }

    @Test
    fun appendsLiveResultsJsonExtensionWhenMissing() {
        assertEquals(
            Path.of("event.live-results.json"),
            DesktopProjectFilePaths.withLiveResultsJsonExtension(Path.of("event"))
        )
    }

    @Test
    fun keepsExistingIofXmlExtension() {
        val path = Path.of("event.iof.xml")

        assertEquals(path, DesktopProjectFilePaths.withIofXmlExtension(path))
    }

    @Test
    fun appendsIofXmlExtensionWhenMissing() {
        assertEquals(
            Path.of("event.iof.xml"),
            DesktopProjectFilePaths.withIofXmlExtension(Path.of("event"))
        )
    }

    @Test
    fun keepsExistingHtmlExtension() {
        val path = Path.of("results.html")

        assertEquals(path, DesktopProjectFilePaths.withHtmlExtension(path))
    }

    @Test
    fun appendsHtmlExtensionWhenMissing() {
        assertEquals(
            Path.of("results.html"),
            DesktopProjectFilePaths.withHtmlExtension(Path.of("results"))
        )
    }

    @Test
    fun keepsExistingTxtExtension() {
        val path = Path.of("results.txt")

        assertEquals(path, DesktopProjectFilePaths.withTxtExtension(path))
    }

    @Test
    fun appendsTxtExtensionWhenMissing() {
        assertEquals(
            Path.of("results.txt"),
            DesktopProjectFilePaths.withTxtExtension(Path.of("results"))
        )
    }
}
