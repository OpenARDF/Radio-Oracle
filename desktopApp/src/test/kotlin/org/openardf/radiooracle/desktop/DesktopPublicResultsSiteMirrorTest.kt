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

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopPublicResultsSiteMirrorTest {
    private val settings = DesktopCloudflarePagesPublishSettings(
        projectName = "openardf-results",
        branch = "main",
        accountId = "account",
        apiToken = "token"
    )

    @Test
    fun usesStableProjectAndBranchSpecificManagedDirectories() {
        val appData = Files.createTempDirectory("radio-oracle-app-data")

        val first = DesktopPublicResultsSiteMirror.directory(settings, appData)
        val second = DesktopPublicResultsSiteMirror.directory(settings, appData)
        val otherBranch = DesktopPublicResultsSiteMirror.directory(
            settings.copy(branch = "preview"),
            appData
        )

        assertEquals(first, second)
        assertEquals(appData.resolve("public-results-sites"), first.parent)
        assertNotEquals(first, otherBranch)
        assertFalse(first.toString().contains("account"))
    }

    @Test
    fun retainModePreservesExistingMirrorContents() {
        val appData = Files.createTempDirectory("radio-oracle-app-data")
        val mirror = DesktopPublicResultsSiteMirror.directory(settings, appData)
        Files.createDirectories(mirror)
        val marker = mirror.resolve("keep.txt")
        Files.writeString(marker, "keep")

        val prepared = DesktopPublicResultsSiteMirror.prepare(settings, appData)

        assertEquals(mirror, prepared)
        assertTrue(Files.exists(marker))
    }

    @Test
    fun replaceModeCanBeCanceledBeforeResettingExistingEntries() {
        val appData = Files.createTempDirectory("radio-oracle-app-data")
        val replacementSettings = settings.copy(
            retentionMode = DesktopPublicResultsRetentionMode.REPLACE_PREVIOUS
        )
        val mirror = DesktopPublicResultsSiteMirror.directory(replacementSettings, appData)
        writeRacesJson(mirror, 2)
        val marker = mirror.resolve("keep.txt")
        Files.writeString(marker, "keep")
        var confirmationCount = 0

        val prepared = DesktopPublicResultsSiteMirror.prepare(
            replacementSettings,
            appData
        ) { count ->
            confirmationCount = count
            false
        }

        assertEquals(null, prepared)
        assertEquals(2, confirmationCount)
        assertTrue(Files.exists(marker))
    }

    @Test
    fun replaceModeResetsManagedMirrorAfterConfirmation() {
        val appData = Files.createTempDirectory("radio-oracle-app-data")
        val replacementSettings = settings.copy(
            retentionMode = DesktopPublicResultsRetentionMode.REPLACE_PREVIOUS
        )
        val mirror = DesktopPublicResultsSiteMirror.directory(replacementSettings, appData)
        writeRacesJson(mirror, 2)
        val marker = mirror.resolve("remove.txt")
        Files.writeString(marker, "remove")

        val prepared = DesktopPublicResultsSiteMirror.prepare(
            replacementSettings,
            appData
        ) { count -> count == 2 }

        assertEquals(mirror, prepared)
        assertTrue(Files.isDirectory(mirror))
        assertFalse(Files.exists(marker))
        assertEquals(0, DesktopPublicResultsSiteMirror.publishedEntryCount(mirror))
    }

    @Test
    fun replaceModeRequiresConfirmationWhenRetainedCatalogIsUnreadable() {
        val appData = Files.createTempDirectory("radio-oracle-app-data")
        val replacementSettings = settings.copy(
            retentionMode = DesktopPublicResultsRetentionMode.REPLACE_PREVIOUS
        )
        val mirror = DesktopPublicResultsSiteMirror.directory(replacementSettings, appData)
        val dataDirectory = mirror.resolve("data")
        Files.createDirectories(dataDirectory)
        Files.writeString(dataDirectory.resolve("races.json"), "not-json")
        val marker = mirror.resolve("keep.txt")
        Files.writeString(marker, "keep")
        var confirmationCount = 0

        val prepared = DesktopPublicResultsSiteMirror.prepare(
            replacementSettings,
            appData
        ) { count ->
            confirmationCount = count
            false
        }

        assertEquals(null, prepared)
        assertEquals(1, confirmationCount)
        assertTrue(Files.exists(marker))
    }

    private fun writeRacesJson(mirror: java.nio.file.Path, count: Int) {
        val dataDirectory = mirror.resolve("data")
        Files.createDirectories(dataDirectory)
        val races = (1..count).joinToString(",") { index -> """{"path":"race-$index"}""" }
        Files.writeString(dataDirectory.resolve("races.json"), """{"races":[$races]}""")
    }
}
