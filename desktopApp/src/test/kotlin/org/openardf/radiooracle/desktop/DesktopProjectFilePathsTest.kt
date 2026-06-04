package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
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
    fun keepsExistingLegacyProjectFileExtension() {
        val path = Path.of("event.rom.json")

        assertEquals(path, DesktopProjectFilePaths.withProjectExtension(path))
    }

    @Test
    fun recognizesCurrentAndLegacyEventFileNames() {
        assertTrue(DesktopProjectFilePaths.isProjectFileName("event.json"))
        assertTrue(DesktopProjectFilePaths.isProjectFileName("event.rom.json"))
        assertFalse(DesktopProjectFilePaths.isProjectFileName("event.csv"))
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
