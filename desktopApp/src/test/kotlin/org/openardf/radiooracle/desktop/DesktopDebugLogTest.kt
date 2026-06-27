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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

class DesktopDebugLogTest {
    @Test
    fun choosesPlatformAppDataDirectories() {
        assertEquals(
            Path.of("/Users/example/Library/Application Support/Radio-Oracle"),
            DesktopAppDirectories.appDataDirectory(
                osName = "Mac OS X",
                userHome = Path.of("/Users/example")
            )
        )
        assertEquals(
            Path.of("C:/Users/example/AppData/Roaming/Radio-Oracle"),
            DesktopAppDirectories.appDataDirectory(
                osName = "Windows 11",
                userHome = Path.of("C:/Users/example"),
                appData = "C:/Users/example/AppData/Roaming"
            )
        )
        assertEquals(
            Path.of("/home/example/.local/state/Radio-Oracle"),
            DesktopAppDirectories.appDataDirectory(
                osName = "Linux",
                userHome = Path.of("/home/example")
            )
        )
        assertEquals(
            Path.of("/state/Radio-Oracle"),
            DesktopAppDirectories.appDataDirectory(
                osName = "Linux",
                userHome = Path.of("/home/example"),
                xdgStateHome = "/state"
            )
        )
    }

    @Test
    fun writeCreatesPrivateLogLine() {
        val directory = Files.createTempDirectory("radio-oracle-desktop-log")
        val logger = DesktopRollingDebugLog(
            directory = directory,
            clock = { Instant.parse("2026-06-03T15:00:00Z") }
        )

        logger.write("I", "SI", "Reader connected")

        assertEquals(
            "2026-06-03T15:00:00Z I SI Reader connected\n",
            directory.resolve("debug.log").toFile().readText()
        )
    }

    @Test
    fun writeSanitizesMultiLineMessages() {
        val directory = Files.createTempDirectory("radio-oracle-desktop-log")
        val logger = DesktopRollingDebugLog(
            directory = directory,
            clock = { Instant.parse("2026-06-03T15:00:00Z") }
        )

        logger.write("W\n", "SI\tReader", "Line one\nLine two")

        val text = directory.resolve("debug.log").toFile().readText()
        assertEquals("2026-06-03T15:00:00Z W SI Reader Line one Line two\n", text)
        assertFalse(text.dropLast(1).contains("\n"))
    }

    @Test
    fun writeRotatesAndRetainsConfiguredFileCount() {
        val directory = Files.createTempDirectory("radio-oracle-desktop-log")
        val logger = DesktopRollingDebugLog(
            directory = directory,
            clock = { Instant.parse("2026-06-03T15:00:00Z") },
            maxFileBytes = 80,
            retainedFileCount = 3
        )

        repeat(5) { index ->
            logger.write("I", "Test", "message-$index-with-padding")
        }

        val files = logger.logFiles().map { it.fileName.toString() }.toSet()
        assertTrue("debug.log should exist", "debug.log" in files)
        assertTrue("first archive should exist", "debug.log.1" in files)
        assertTrue("second archive should exist", "debug.log.2" in files)
        assertFalse("only two archives should be retained", Files.exists(directory.resolve("debug.log.3")))
    }
}
