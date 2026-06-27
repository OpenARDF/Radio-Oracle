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

import org.openardf.radiooracle.shared.event.EVENT_SERIES_PACKAGE_CONTENT_TYPE
import org.openardf.radiooracle.shared.event.EventSeriesPackageEntryKind
import org.openardf.radiooracle.shared.event.EventSeriesPackageContents
import org.openardf.radiooracle.shared.event.EventSeriesPackageEventFile
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class DesktopEventSeriesPackage(
    val fileName: String,
    val contentType: String = EVENT_SERIES_PACKAGE_CONTENT_TYPE,
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
            "Event Series manifest must have a parent folder."
        }
        val members = seriesFile.sortedEvents()
        require(members.isNotEmpty()) {
            "Event Series package requires at least one event."
        }

        val missing = members
            .map { it to seriesFolder.resolve(it.eventFilePath).normalize() }
            .filterNot { (_, path) -> store.exists(path) }
            .map { (event, _) -> event.eventFilePath }
        require(missing.isEmpty()) {
            "Cannot send Event Series because required Event Files are missing: ${missing.joinToString()}"
        }

        val content = EventSeriesPackageContents.build(
            seriesFile = seriesFile,
            eventFiles = members.map { event ->
                val source = seriesFolder.resolve(event.eventFilePath).normalize()
                EventSeriesPackageEventFile(event, store.readEvent(source))
            },
            manifestEntryPath = manifestPath.fileName.toString(),
            packageFileNameStem = seriesFile.name
        )
        val bytes = ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                content.entries.forEach { entry ->
                    zip.writeTextEntry(entry.path, entry.text)
                }
            }
            output.toByteArray()
        }

        return DesktopEventSeriesPackage(
            fileName = content.fileName,
            bytes = bytes
        )
    }

    fun unpack(path: Path, targetRoot: Path): DesktopEventSeriesPackageImportResult {
        val targetDirectory = uniqueDirectory(targetRoot, path.fileName.toString().removeSuffix(".zip"))
        Files.createDirectories(targetDirectory)

        var manifestPath: Path? = null
        val eventFilePaths = mutableListOf<Path>()
        ZipInputStream(Files.newInputStream(path).buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    val packageEntry = EventSeriesPackageContents.classifyEntryPath(entry.name)
                    if (packageEntry.kind != EventSeriesPackageEntryKind.IGNORED) {
                        val targetPath = targetDirectory.resolve(Path.of(packageEntry.path)).normalize()
                        require(targetPath.startsWith(targetDirectory)) {
                            "Event Series package contains an unsafe path: ${entry.name}"
                        }
                        targetPath.parent?.let(Files::createDirectories)
                        Files.write(targetPath, zip.readBytes())
                        when (packageEntry.kind) {
                            EventSeriesPackageEntryKind.MANIFEST -> {
                                require(manifestPath == null) {
                                    "Event Series package contains more than one manifest."
                                }
                                manifestPath = targetPath
                            }

                            EventSeriesPackageEntryKind.EVENT_FILE -> {
                                eventFilePaths.add(targetPath)
                            }

                            EventSeriesPackageEntryKind.IGNORED -> Unit
                        }
                    }
                }
                zip.closeEntry()
            }
        }

        return DesktopEventSeriesPackageImportResult(
            manifestPath = requireNotNull(manifestPath) {
                "Event Series package does not contain a series manifest."
            },
            eventFilePaths = eventFilePaths
        )
    }

    private fun ZipOutputStream.writeTextEntry(path: String, text: String) {
        writeBytesEntry(path, text.toByteArray(Charsets.UTF_8))
    }

    private fun ZipOutputStream.writeBytesEntry(path: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(path.replace('\\', '/')))
        write(bytes)
        closeEntry()
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
