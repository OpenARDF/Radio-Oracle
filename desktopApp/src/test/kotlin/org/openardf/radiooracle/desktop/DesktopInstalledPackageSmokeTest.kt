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

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document

class DesktopInstalledPackageSmokeTest {
    @Test
    fun ordinaryStartupArgumentsAreNotClaimed() {
        assertNull(DesktopInstalledPackageSmoke.runIfRequested(arrayOf("event.rom.json")))
    }

    @Test
    fun exportsRepresentativePortableFormats() {
        val directory = Files.createTempDirectory("radio-oracle-installed-package-smoke")
        val source = directory.resolve("source.rom.json")
        val output = directory.resolve("exports")
        Files.copy(
            Path.of("..", "samples", "desktop-smoke.rom.json"),
            source
        )
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()

        val exitCode = DesktopInstalledPackageSmoke.runIfRequested(
            arrayOf(DesktopInstalledPackageSmoke.COMMAND, source.toString(), output.toString()),
            PrintStream(stdout),
            PrintStream(stderr)
        )

        assertEquals(stderr.toString(), 0, exitCode)
        assertTrue(stdout.toString().contains("installed-package exports OK"))
        val versionEvidence = Files.readString(output.resolve("installed-package-evidence.txt"))
        assertTrue(versionEvidence.contains("packageVersion=${DesktopBuildInfo.baseVersion}"))
        assertTrue(versionEvidence.contains("displayVersion=${DesktopBuildInfo.displayVersion}"))
        assertEquals("Place,Competitor,Status,Points,Run time", Files.readAllLines(output.resolve("results.csv")).first())
        Json.parseToJsonElement(Files.readString(output.resolve("final-results.json")))
        val document: Document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(output.resolve("start-list.xml").toFile())
        assertEquals("StartList", document.documentElement.localName ?: document.documentElement.nodeName.substringAfter(':'))
    }

    @Test
    fun rejectsAnExistingOutputDirectoryToPreventStaleProof() {
        val directory = Files.createTempDirectory("radio-oracle-installed-package-smoke-existing")
        val source = directory.resolve("source.rom.json")
        val output = Files.createDirectory(directory.resolve("exports"))
        Files.copy(Path.of("..", "samples", "desktop-smoke.rom.json"), source)

        val exitCode = DesktopInstalledPackageSmoke.runIfRequested(
            arrayOf(DesktopInstalledPackageSmoke.COMMAND, source.toString(), output.toString()),
            PrintStream(ByteArrayOutputStream()),
            PrintStream(ByteArrayOutputStream())
        )

        assertEquals(1, exitCode)
    }
}
