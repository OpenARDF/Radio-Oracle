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

import org.openardf.radiooracle.shared.event.EVENT_SERIES_ARCHIVE_CONTENT_TYPE
import org.openardf.radiooracle.shared.event.EventSeriesArchive
import org.openardf.radiooracle.shared.event.EventSeriesArchiveZipCodec
import org.openardf.radiooracle.shared.event.EventSeriesPackageContents
import java.nio.file.Files
import java.nio.file.Path

data class DesktopEventSeriesPackage(
    val fileName: String,
    val contentType: String = EVENT_SERIES_ARCHIVE_CONTENT_TYPE,
    val bytes: ByteArray
) {
    val byteCount: Long get() = bytes.size.toLong()
}

data class DesktopEventSeriesPackageImportResult(
    val manifestPath: Path,
    val eventFilePaths: List<Path>
)

object DesktopEventSeriesPackageFiles {
    fun packageForManifest(store: EventSeriesStore, manifestPath: Path): DesktopEventSeriesPackage {
        val seriesFile = store.read(manifestPath)
        val seriesFolder = requireNotNull(manifestPath.parent) {
            "Race Series manifest must have a parent folder."
        }
        val members = seriesFile.sortedEvents()
        require(members.isNotEmpty()) {
            "Race Series package requires at least one event."
        }

        val missing = members
            .map { it to seriesFolder.resolve(it.eventFilePath).normalize() }
            .filterNot { (_, path) -> store.exists(path) }
            .map { (event, _) -> event.eventFilePath }
        require(missing.isEmpty()) {
            "Cannot send Race Series because required Race Files are missing: ${missing.joinToString()}"
        }

        val archive = EventSeriesArchive(
            seriesFile = seriesFile,
            membersBySeriesEventId = members.associate { event ->
                val source = seriesFolder.resolve(event.eventFilePath).normalize()
                event.seriesEventId to store.readEvent(source)
            },
            manifestEntryPath = manifestPath.fileName.toString()
        )
        val content = archive.packageContent()

        return DesktopEventSeriesPackage(
            fileName = content.fileName,
            bytes = EventSeriesArchiveZipCodec.encode(archive)
        )
    }

    fun unpack(path: Path, targetRoot: Path): DesktopEventSeriesPackageImportResult {
        val archive = Files.newInputStream(path).use(EventSeriesArchiveZipCodec::read)
        val targetDirectory = uniqueDirectory(
            targetRoot,
            EventSeriesPackageContents.safePackageFileStem(path.fileName.toString())
        )
        Files.createDirectories(targetDirectory)

        val manifestPath = targetDirectory.resolve(Path.of(archive.manifestEntryPath)).normalize()
        require(manifestPath.startsWith(targetDirectory)) {
            "Race Series archive contains an unsafe manifest path."
        }
        DesktopEventSeriesFiles.write(manifestPath, archive.seriesFile)
        val manifestFolder = requireNotNull(manifestPath.parent)
        val eventFilePaths = archive.seriesFile.sortedEvents().map { event ->
            val eventPath = manifestFolder.resolve(event.eventFilePath).normalize()
            require(eventPath.startsWith(targetDirectory)) {
                "Race Series archive contains an unsafe Race File path: ${event.eventFilePath}"
            }
            DesktopProjectFiles.write(eventPath, archive.member(event.seriesEventId))
            eventPath
        }

        return DesktopEventSeriesPackageImportResult(
            manifestPath = manifestPath,
            eventFilePaths = eventFilePaths
        )
    }

    private fun uniqueDirectory(root: Path, stem: String): Path {
        val safeStem = EventSeriesPackageContents.safePackageFileStem(stem).ifBlank { "event-series" }
        var candidate = root.resolve(safeStem)
        var index = 2
        while (Files.exists(candidate)) {
            candidate = root.resolve("$safeStem $index")
            index += 1
        }
        return candidate
    }
}
