package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.event.EVENT_SERIES_PACKAGE_CONTENT_TYPE
import org.openardf.radiooracle.shared.event.EventSeriesPackageContents
import org.openardf.radiooracle.shared.event.EventSeriesPackageEventFile
import org.openardf.radiooracle.shared.event.isEventSeriesFileName
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
        require(members.size >= 2) {
            "Event Series package requires at least two events."
        }

        val missing = members
            .map { it to seriesFolder.resolve(it.eventFilePath).normalize() }
            .filterNot { (_, path) -> store.exists(path) }
            .map { (event, _) -> event.eventFilePath }
        require(missing.isEmpty()) {
            "Cannot send Event Series because required Event Files are missing: ${missing.joinToString()}"
        }

        val bytes = ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                val content = EventSeriesPackageContents.build(
                    seriesFile = seriesFile,
                    eventFiles = members.map { event ->
                        val source = seriesFolder.resolve(event.eventFilePath).normalize()
                        EventSeriesPackageEventFile(event, store.readEvent(source))
                    },
                    manifestEntryPath = manifestPath.fileName.toString(),
                    packageFileNameStem = seriesFile.name
                )
                content.entries.forEach { entry ->
                    zip.writeTextEntry(entry.path, entry.text)
                }
            }
            output.toByteArray()
        }

        return DesktopEventSeriesPackage(
            fileName = EventSeriesPackageContents.safePackageFileStem(seriesFile.name) + ".zip",
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
                    val safeRelativePath = safeZipRelativePath(entry.name)
                    val targetPath = targetDirectory.resolve(safeRelativePath).normalize()
                    require(targetPath.startsWith(targetDirectory)) {
                        "Event Series package contains an unsafe path: ${entry.name}"
                    }
                    targetPath.parent?.let(Files::createDirectories)
                    Files.write(targetPath, zip.readBytes())
                    if (isEventSeriesFileName(targetPath.fileName.toString())) {
                        require(manifestPath == null) {
                            "Event Series package contains more than one manifest."
                        }
                        manifestPath = targetPath
                    } else if (DesktopProjectFilePaths.isProjectFileName(targetPath.fileName.toString())) {
                        eventFilePaths.add(targetPath)
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

    private fun safeZipRelativePath(path: String): Path {
        val normalized = Path.of(path.replace('\\', '/')).normalize()
        require(!normalized.isAbsolute && normalized.none { it.toString() == ".." }) {
            "Event Series package contains an unsafe path: $path"
        }
        return normalized
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
