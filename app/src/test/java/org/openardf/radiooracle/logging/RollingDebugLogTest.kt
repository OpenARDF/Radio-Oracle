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

package org.openardf.radiooracle.logging

import org.openardf.radiooracle.backend.logging.RollingDebugLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Instant

class RollingDebugLogTest {

    @Test
    fun writeCreatesPrivateLogLine() {
        val directory = Files.createTempDirectory("rom-debug-log").toFile()
        val logger = RollingDebugLog(
            directory = directory,
            clock = { Instant.parse("2026-05-31T15:00:00Z") }
        )

        logger.write("I", "SI", "Reader connected")

        assertEquals(
            "2026-05-31T15:00:00Z I SI Reader connected\n",
            File(directory, "debug.log").readText()
        )
    }

    @Test
    fun writeSanitizesMultiLineMessages() {
        val directory = Files.createTempDirectory("rom-debug-log").toFile()
        val logger = RollingDebugLog(
            directory = directory,
            clock = { Instant.parse("2026-05-31T15:00:00Z") }
        )

        logger.write("W\n", "SI\tReader", "Line one\nLine two")

        val text = File(directory, "debug.log").readText()
        assertEquals("2026-05-31T15:00:00Z W SI Reader Line one Line two\n", text)
        assertFalse(text.dropLast(1).contains("\n"))
    }

    @Test
    fun writeRotatesAndRetainsConfiguredFileCount() {
        val directory = Files.createTempDirectory("rom-debug-log").toFile()
        val logger = RollingDebugLog(
            directory = directory,
            clock = { Instant.parse("2026-05-31T15:00:00Z") },
            maxFileBytes = 80,
            retainedFileCount = 3
        )

        repeat(5) { index ->
            logger.write("I", "Test", "message-$index-with-padding")
        }

        val files = logger.logFiles().map { it.name }.toSet()
        assertTrue("debug.log should exist", "debug.log" in files)
        assertTrue("first archive should exist", "debug.log.1" in files)
        assertTrue("second archive should exist", "debug.log.2" in files)
        assertFalse("only two archives should be retained", File(directory, "debug.log.3").exists())
    }
}
