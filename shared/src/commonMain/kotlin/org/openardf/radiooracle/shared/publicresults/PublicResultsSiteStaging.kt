package org.openardf.radiooracle.shared.publicresults

import java.nio.charset.StandardCharsets
import java.nio.file.*
import java.security.MessageDigest
import java.util.UUID

/** One frozen staging and checked replacement implementation for desktop and Android publication. */
object PublicResultsSiteStaging {
    fun stageDirectory(directory: Path, retainExisting: Boolean = true): Candidate {
        val mirrorDirectory = directory.toAbsolutePath().normalize()
        val managedRoot = requireNotNull(mirrorDirectory.parent) { "A site directory is required." }
        Files.createDirectories(managedRoot)
        val baseline = directoryDigest(mirrorDirectory)
        val stagingDirectory = Files.createTempDirectory(managedRoot, "${mirrorDirectory.fileName}.staging-")
        return try {
            if (retainExisting && Files.isDirectory(mirrorDirectory)) copyRecursively(mirrorDirectory, stagingDirectory)
            require(directoryDigest(mirrorDirectory) == baseline) { "The existing results site changed while preparing this export. Retry from the current site." }
            Candidate(stagingDirectory, mirrorDirectory, managedRoot, baseline)
        } catch (error: Throwable) {
            deleteManagedDirectory(stagingDirectory, managedRoot)
            throw error
        }
    }

    class Candidate internal constructor(
        val stagingDirectory: Path,
        val mirrorDirectory: Path,
        private val managedRoot: Path,
        private val baselineDigest: String?
    ) {
        private var promoted = false

        fun promote(): Path {
            check(!promoted) { "The staged public-results site has already been promoted." }
            requireManagedChild(stagingDirectory, managedRoot)
            requireManagedChild(mirrorDirectory, managedRoot)
            require(directoryDigest(mirrorDirectory) == baselineDigest) { "The existing results site changed after generation began. Retry without overwriting those changes." }
            val backupDirectory = managedRoot.resolve(
                "${mirrorDirectory.fileName}.backup-${UUID.randomUUID()}"
            )
            var existingMirrorMoved = false
            try {
                if (Files.exists(mirrorDirectory)) {
                    moveDirectory(mirrorDirectory, backupDirectory)
                    existingMirrorMoved = true
                }
                moveDirectory(stagingDirectory, mirrorDirectory)
                promoted = true
            } catch (error: Throwable) {
                if (!Files.exists(mirrorDirectory) && existingMirrorMoved && Files.exists(backupDirectory)) {
                    runCatching { moveDirectory(backupDirectory, mirrorDirectory) }
                }
                throw error
            }
            if (existingMirrorMoved && Files.exists(backupDirectory)) {
                runCatching { deleteManagedDirectory(backupDirectory, managedRoot) }
            }
            return mirrorDirectory
        }

        fun discard() {
            if (!promoted && Files.exists(stagingDirectory)) {
                deleteManagedDirectory(stagingDirectory, managedRoot)
            }
        }
    }

    private fun directoryDigest(directory: Path): String? {
        if (!Files.exists(directory)) return null
        require(Files.isDirectory(directory) && !Files.isSymbolicLink(directory)) { "Expected a results directory." }
        val digest = MessageDigest.getInstance("SHA-256")
        Files.walk(directory).use { paths ->
            paths.sorted().forEach { path ->
                require(!Files.isSymbolicLink(path)) { "Results directories cannot contain symbolic links." }
                digest.update(directory.relativize(path).toString().toByteArray(StandardCharsets.UTF_8))
                digest.update(0.toByte())
                if (Files.isRegularFile(path)) Files.newInputStream(path).use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                }
                digest.update(0.toByte())
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun copyRecursively(source: Path, target: Path) {
        Files.walk(source).use { paths ->
            paths.forEach { sourcePath ->
                val relativePath = source.relativize(sourcePath)
                val targetPath = target.resolve(relativePath)
                if (Files.isDirectory(sourcePath)) {
                    Files.createDirectories(targetPath)
                } else {
                    targetPath.parent?.let(Files::createDirectories)
                    Files.copy(
                        sourcePath,
                        targetPath,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES
                    )
                }
            }
        }
    }

    private fun moveDirectory(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target)
        }
    }

    private fun deleteManagedDirectory(directory: Path, managedRoot: Path) {
        requireManagedChild(directory, managedRoot)
        if (Files.exists(directory)) {
            deleteRecursively(directory)
        }
    }

    private fun requireManagedChild(directory: Path, managedRoot: Path) {
        val normalizedRoot = managedRoot.toAbsolutePath().normalize()
        val normalizedDirectory = directory.toAbsolutePath().normalize()
        require(normalizedDirectory.parent == normalizedRoot && normalizedDirectory != normalizedRoot) {
            "Unsafe managed public-results directory: $normalizedDirectory"
        }
    }

    private fun deleteRecursively(directory: Path) {
        Files.walk(directory).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::delete)
        }
    }
}
