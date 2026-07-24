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
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventProjectFileJson
import org.openardf.radiooracle.shared.event.EventSeriesArchive
import org.openardf.radiooracle.shared.event.EventSeriesArchiveMemberRemoval
import org.openardf.radiooracle.shared.event.EventSeriesArchiveZipCodec
import org.openardf.radiooracle.shared.event.EventSeriesEvent
import org.openardf.radiooracle.shared.event.EventSeriesFile
import org.openardf.radiooracle.shared.event.EventSeriesFileJson
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.UUID

/**
 * One live desktop view of a `.roseries` file.
 *
 * The archive is authoritative. A private materialized directory supplies ordinary Paths to
 * existing desktop workflows; every supported write updates the aggregate and atomically replaces
 * the archive before the materialized view is refreshed.
 */
class DesktopEventSeriesArchiveWorkspace private constructor(
    initialContainerPath: Path,
    initialArchive: EventSeriesArchive,
    private val workingRoot: Path,
    private var committedDigest: String?
) {
    var containerPath: Path = initialContainerPath.toAbsolutePath().normalize()
        private set

    var archive: EventSeriesArchive = initialArchive
        private set

    private var materializedPaths: Set<Path> = emptySet()

    val manifestPath: Path
        get() = workingRoot.resolve(archive.manifestEntryPath).normalize()

    val memberPaths: List<Path>
        get() = archive.seriesFile.sortedEvents().map(::memberPath)

    init {
        materialize()
    }

    fun owns(path: Path): Boolean =
        path.toAbsolutePath().normalize().startsWith(workingRoot)

    fun isManifestPath(path: Path): Boolean =
        path.toAbsolutePath().normalize() == manifestPath.toAbsolutePath().normalize()

    fun memberPath(event: EventSeriesEvent): Path =
        requireNotNull(manifestPath.parent).resolve(event.eventFilePath).normalize()

    fun seriesEventIdForPath(path: Path): String? {
        val normalizedPath = path.toAbsolutePath().normalize()
        return archive.seriesFile.events.firstOrNull { event ->
            memberPath(event).toAbsolutePath().normalize() == normalizedPath
        }?.seriesEventId
    }

    fun readSeries(): EventSeriesFile = archive.seriesFile

    fun readMember(path: Path): EventProjectFile =
        archive.member(
            requireNotNull(seriesEventIdForPath(path)) {
                "Race File is not a member of ${containerPath.fileName}: ${path.fileName}"
            }
        )

    @Synchronized
    fun writeSeries(seriesFile: EventSeriesFile) {
        replaceArchive(
            EventSeriesArchive(
                seriesFile = seriesFile,
                membersBySeriesEventId = archive.membersBySeriesEventId,
                manifestEntryPath = archive.manifestEntryPath
            )
        )
    }

    @Synchronized
    fun writeMember(path: Path, projectFile: EventProjectFile) {
        val seriesEventId = requireNotNull(seriesEventIdForPath(path)) {
            "Race File is not a member of ${containerPath.fileName}: ${path.fileName}"
        }
        replaceArchive(archive.updateMember(seriesEventId, projectFile))
    }

    @Synchronized
    fun addMember(event: EventSeriesEvent, projectFile: EventProjectFile) {
        replaceArchive(archive.addMember(event, projectFile))
    }

    @Synchronized
    fun removeMember(seriesEventId: String, standalonePath: Path): EventSeriesArchiveMemberRemoval {
        val normalizedStandalonePath = standalonePath.toAbsolutePath().normalize()
        require(normalizedStandalonePath != containerPath) {
            "Choose a separate Race File destination; the Radio-Oracle Series File cannot be overwritten."
        }
        require(!owns(normalizedStandalonePath)) {
            "Choose a Race File destination outside Radio-Oracle's temporary series workspace."
        }
        val removal = archive.removeMember(seriesEventId)
        writeStandaloneFirst(normalizedStandalonePath, removal.detachedProjectFile)
        val remainingArchive = removal.remainingArchive
        if (remainingArchive == null) {
            verifyContainerUnchanged()
            Files.deleteIfExists(containerPath)
            committedDigest = null
        } else {
            replaceArchive(remainingArchive)
        }
        return removal
    }

    @Synchronized
    fun replaceArchive(updatedArchive: EventSeriesArchive) {
        verifyContainerUnchanged()
        val normalizedArchive = updatedArchive.normalizedForStorage()
        val bytes = EventSeriesArchiveZipCodec.encode(normalizedArchive)
        writeAtomically(containerPath, bytes)
        committedDigest = bytes.sha256()
        archive = normalizedArchive
        materialize()
    }

    @Synchronized
    fun renameContainer(targetPath: Path): Path {
        verifyContainerUnchanged()
        val normalizedTarget = targetPath.toAbsolutePath().normalize()
        require(normalizedTarget != containerPath) {
            "Race Series file already has that name."
        }
        require(!Files.exists(normalizedTarget)) {
            "Race Series file already exists at ${normalizedTarget.fileName}."
        }
        normalizedTarget.parent?.let(Files::createDirectories)
        moveAtomically(containerPath, normalizedTarget)
        containerPath = normalizedTarget
        committedDigest = Files.readAllBytes(containerPath).sha256()
        return containerPath
    }

    fun close() {
        runCatching {
            Files.walk(workingRoot).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }

    private fun verifyContainerUnchanged() {
        val expectedDigest = committedDigest
        if (expectedDigest == null) {
            require(!Files.exists(containerPath)) {
                "Race Series file appeared on disk while it was open: ${containerPath.fileName}"
            }
            return
        }
        require(Files.exists(containerPath)) {
            "Race Series file was removed while it was open: ${containerPath.fileName}"
        }
        val currentDigest = Files.readAllBytes(containerPath).sha256()
        require(currentDigest == expectedDigest) {
            "Race Series file changed outside Radio-Oracle. Reopen it before saving."
        }
    }

    private fun materialize() {
        Files.createDirectories(workingRoot)
        val currentPaths = buildSet {
            add(manifestPath.toAbsolutePath().normalize())
            archive.seriesFile.events.forEach { event ->
                add(memberPath(event).toAbsolutePath().normalize())
            }
        }
        (materializedPaths - currentPaths).forEach(Files::deleteIfExists)
        manifestPath.parent?.let(Files::createDirectories)
        Files.writeString(
            manifestPath,
            EventSeriesFileJson.encode(archive.seriesFile),
            StandardCharsets.UTF_8
        )
        archive.seriesFile.events.forEach { event ->
            val path = memberPath(event)
            path.parent?.let(Files::createDirectories)
            Files.writeString(
                path,
                EventProjectFileJson.encode(archive.member(event.seriesEventId)),
                StandardCharsets.UTF_8
            )
        }
        materializedPaths = currentPaths
    }

    private fun writeStandaloneFirst(path: Path, projectFile: EventProjectFile) {
        path.parent?.let(Files::createDirectories)
        val bytes = EventProjectFileJson.encode(projectFile).toByteArray(StandardCharsets.UTF_8)
        writeAtomically(path, bytes)
        EventProjectFileJson.decode(Files.readString(path, StandardCharsets.UTF_8))
    }

    companion object {
        fun open(path: Path): DesktopEventSeriesArchiveWorkspace {
            val normalizedPath = path.toAbsolutePath().normalize()
            val bytes = Files.readAllBytes(normalizedPath)
            return DesktopEventSeriesArchiveWorkspace(
                initialContainerPath = normalizedPath,
                initialArchive = EventSeriesArchiveZipCodec.decode(bytes),
                workingRoot = Files.createTempDirectory("radio-oracle-roseries-"),
                committedDigest = bytes.sha256()
            )
        }

        fun create(
            path: Path,
            archive: EventSeriesArchive,
            replaceExisting: Boolean = false
        ): DesktopEventSeriesArchiveWorkspace {
            val normalizedPath = path.toAbsolutePath().normalize()
            require(replaceExisting || !Files.exists(normalizedPath)) {
                "Race Series file already exists at ${normalizedPath.fileName}."
            }
            val existingDigest = if (Files.exists(normalizedPath)) {
                Files.readAllBytes(normalizedPath).sha256()
            } else {
                null
            }
            val workspace = DesktopEventSeriesArchiveWorkspace(
                initialContainerPath = normalizedPath,
                initialArchive = archive.normalizedForStorage(),
                workingRoot = Files.createTempDirectory("radio-oracle-roseries-"),
                committedDigest = existingDigest
            )
            workspace.replaceArchive(workspace.archive)
            return workspace
        }

        private fun writeAtomically(path: Path, bytes: ByteArray) {
            val parent = requireNotNull(path.parent) {
                "Race Series file must have a parent folder."
            }
            Files.createDirectories(parent)
            val temporaryPath = parent.resolve(".${path.fileName}.tmp-${UUID.randomUUID()}")
            try {
                FileChannel.open(
                    temporaryPath,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
                ).use { channel ->
                    val buffer = java.nio.ByteBuffer.wrap(bytes)
                    while (buffer.hasRemaining()) {
                        channel.write(buffer)
                    }
                    channel.force(true)
                }
                moveAtomically(temporaryPath, path)
            } finally {
                Files.deleteIfExists(temporaryPath)
            }
        }

        private fun moveAtomically(source: Path, target: Path) {
            try {
                Files.move(
                    source,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
            }
        }

        private fun ByteArray.sha256(): String =
            MessageDigest.getInstance("SHA-256")
                .digest(this)
                .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}

/** Registry that lets existing Path-oriented desktop workflows reach an open archive workspace. */
object DesktopEventSeriesArchiveWorkspaces {
    private val workspaces = linkedSetOf<DesktopEventSeriesArchiveWorkspace>()

    @Synchronized
    fun open(path: Path): DesktopEventSeriesArchiveWorkspace {
        val normalizedPath = path.toAbsolutePath().normalize()
        workspaces.firstOrNull { it.containerPath == normalizedPath }?.let { return it }
        return DesktopEventSeriesArchiveWorkspace.open(normalizedPath).also(workspaces::add)
    }

    @Synchronized
    fun create(
        path: Path,
        archive: EventSeriesArchive,
        replaceExisting: Boolean = false
    ): DesktopEventSeriesArchiveWorkspace {
        val normalizedPath = path.toAbsolutePath().normalize()
        require(workspaces.none { it.containerPath == normalizedPath }) {
            "The Radio-Oracle Series File is already open: $normalizedPath"
        }
        return DesktopEventSeriesArchiveWorkspace.create(
            normalizedPath,
            archive,
            replaceExisting
        ).also(workspaces::add)
    }

    @Synchronized
    fun workspaceFor(path: Path): DesktopEventSeriesArchiveWorkspace? =
        workspaces.firstOrNull { it.owns(path) }

    @Synchronized
    fun workspaceForContainer(path: Path): DesktopEventSeriesArchiveWorkspace? {
        val normalizedPath = path.toAbsolutePath().normalize()
        return workspaces.firstOrNull { it.containerPath == normalizedPath }
    }

    @Synchronized
    fun containerPathFor(path: Path): Path? =
        workspaceFor(path)?.containerPath

    @Synchronized
    fun close(workspace: DesktopEventSeriesArchiveWorkspace) {
        if (workspaces.remove(workspace)) {
            workspace.close()
        }
    }

    @Synchronized
    fun closeAll() {
        workspaces.forEach(DesktopEventSeriesArchiveWorkspace::close)
        workspaces.clear()
    }
}
