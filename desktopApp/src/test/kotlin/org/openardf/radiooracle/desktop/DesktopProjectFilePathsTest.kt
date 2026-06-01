package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Path

class DesktopProjectFilePathsTest {
    @Test
    fun keepsExistingProjectFileExtension() {
        val path = Path.of("event.rom.json")

        assertEquals(path, DesktopProjectFilePaths.withProjectExtension(path))
    }

    @Test
    fun appendsProjectFileExtensionWhenMissing() {
        assertEquals(
            Path.of("event.rom.json"),
            DesktopProjectFilePaths.withProjectExtension(Path.of("event"))
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
}
