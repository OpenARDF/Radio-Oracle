package org.openardf.radiooracle.desktop

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.openardf.radiooracle.shared.event.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest

internal fun courseFileDigest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/** Legacy external-member series need durable rollback, since separate file replacements are not atomic together. */
internal object DesktopCourseSeriesTransaction {
    private val json = Json { encodeDefaults = true }
    private const val marker = "Radio-Oracle course transaction v1"
    private fun directory(manifest: Path) = manifest.toAbsolutePath().normalize().let { it.resolveSibling(".${it.fileName}.course-transaction") }

    fun write(manifest: Path, updates: Map<Path, EventProjectFile>, expected: Map<Path, String>, beforeWrite: (Int) -> Unit = {}) {
        recover(manifest)
        require(updates.isNotEmpty() && updates.keys == expected.keys) { "Course write coverage is incomplete." }
        val normalized = updates.mapKeys { it.key.toAbsolutePath().normalize() }
        require(normalized.size == updates.size) { "A Race File occurs more than once in this transaction." }
        require(normalized.keys.all { it in memberPaths(manifest) }) { "Selected course files do not belong to this series." }
        val texts = normalized.mapValues { EventProjectFileJson.encode(it.value).also(EventProjectFileJson::decode) }
        updates.keys.forEach { path -> require(courseFileDigest(Files.readAllBytes(path)) == expected.getValue(path)) {
            "A Race File changed before the course update. Reload the series and retry."
        } }
        val folder = directory(manifest)
        Files.createDirectory(folder)
        runCatching { Files.setPosixFilePermissions(folder, PosixFilePermissions.fromString("rwx------")) }
        try {
            writeDesktopTextAtomically(folder.resolve("owner"), marker)
            val entries = texts.entries.mapIndexed { index, (path, replacement) ->
                val original = Files.readString(path)
                val originalHash = courseFileDigest(original.toByteArray())
                val key = updates.keys.single { it.toAbsolutePath().normalize() == path }
                require(originalHash == expected.getValue(key)) { "A Race File changed during staging." }
                writeDesktopTextAtomically(folder.resolve("$index.before"), original)
                writeDesktopTextAtomically(folder.resolve("$index.after"), replacement)
                CourseTransactionMember(path.toString(), originalHash, courseFileDigest(replacement.toByteArray()))
            }
            val journal = CourseTransactionJournal(members = entries)
            writeDesktopTextAtomically(folder.resolve("journal.json"), json.encodeToString(journal.toJson()))
            entries.forEachIndexed { index, entry ->
                beforeWrite(index)
                val path = Path.of(entry.path)
                require(courseFileDigest(Files.readAllBytes(path)) == entry.originalDigest) { "A Race File changed during the course update." }
                writeDesktopTextAtomically(path, Files.readString(folder.resolve("$index.after")))
            }
            entries.forEach { require(courseFileDigest(Files.readAllBytes(Path.of(it.path))) == it.replacementDigest) { "Course write verification failed." } }
            writeDesktopTextAtomically(folder.resolve("journal.json"), json.encodeToString(journal.copy(committed = true).toJson()))
        } catch (failure: Exception) {
            try { recover(manifest) } catch (recovery: Exception) { failure.addSuppressed(recovery) }
            throw failure
        }
        recover(manifest)
    }

    /** Called when reopening a legacy series, and before another batch. Unknown external edits are preserved. */
    fun recover(manifest: Path) {
        val folder = directory(manifest)
        if (!Files.exists(folder)) return
        require(!Files.isSymbolicLink(folder)) { "Unrecognized course recovery folder; no files were changed." }
        // A crash can occur immediately after mkdir or after deleting the final owner marker.
        if (Files.list(folder).use { !it.findAny().isPresent }) { Files.delete(folder); return }
        require(Files.readString(folder.resolve("owner")) == marker) { "Unrecognized course recovery folder; no files were changed." }
        val journalPath = folder.resolve("journal.json")
        if (Files.exists(journalPath)) {
            val journal = courseJournal(json.parseToJsonElement(Files.readString(journalPath)).jsonObject)
            require(journal.version == 1 && journal.members.map { it.path }.distinct().size == journal.members.size) { "Unsupported course recovery journal." }
            val allowed = memberPaths(manifest)
            require(journal.members.all { Path.of(it.path).toAbsolutePath().normalize() in allowed }) { "The series membership changed; retain the course recovery folder for review." }
            val disk = journal.members.map { courseFileDigest(Files.readAllBytes(Path.of(it.path))) }
            require(journal.members.indices.all { disk[it] in setOf(journal.members[it].originalDigest, journal.members[it].replacementDigest) }) {
                "A Race File changed outside the interrupted course update. The recovery folder was retained; no external changes were overwritten."
            }
            if (journal.committed) require(journal.members.indices.all { disk[it] == journal.members[it].replacementDigest }) { "Committed course files changed; recovery journal retained for review." }
            if (!journal.committed) {
                journal.members.forEachIndexed { index, entry ->
                    val before = Files.readString(folder.resolve("$index.before"))
                    require(courseFileDigest(before.toByteArray()) == entry.originalDigest) { "Course recovery backup is damaged." }
                }
                journal.members.forEachIndexed { index, entry ->
                    if (disk[index] != entry.originalDigest) {
                        require(courseFileDigest(Files.readAllBytes(Path.of(entry.path))) == disk[index]) { "A Race File changed during recovery; the recovery folder was retained." }
                        writeDesktopTextAtomically(Path.of(entry.path), Files.readString(folder.resolve("$index.before")))
                    }
                }
            }
        }
        val cleanup = Files.list(folder).use { it.toList() }
        require(cleanup.all { path -> !Files.isSymbolicLink(path) &&
            (path.fileName.toString() in setOf("owner", "journal.json") || path.fileName.toString().matches(Regex("[0-9]+\\.(before|after)"))) }) {
            "Unrecognized recovery file; recovery folder retained."
        }
        // Recovery has completed. Remove its journal before backups, keeping ownership until last.
        Files.deleteIfExists(journalPath)
        cleanup.filter { it.fileName.toString() !in setOf("owner", "journal.json") }.forEach(Files::delete)
        Files.delete(folder.resolve("owner"))
        Files.delete(folder)
    }

    private fun memberPaths(manifest: Path): Set<Path> {
        val path = manifest.toAbsolutePath().normalize()
        return EventSeriesFileJson.decode(Files.readString(path)).events.map { path.parent.resolve(it.eventFilePath).normalize() }.toSet()
    }
}

private data class CourseTransactionJournal(val version: Int = 1, val committed: Boolean = false, val members: List<CourseTransactionMember>)
private data class CourseTransactionMember(val path: String, val originalDigest: String, val replacementDigest: String)

private fun CourseTransactionJournal.toJson(): JsonObject = buildJsonObject {
    put("version", version)
    put("committed", committed)
    put("members", JsonArray(members.map { member -> buildJsonObject {
        put("path", member.path); put("originalDigest", member.originalDigest); put("replacementDigest", member.replacementDigest)
    } }))
}

private fun courseJournal(value: JsonObject) = CourseTransactionJournal(
    version = value.getValue("version").jsonPrimitive.int,
    committed = value.getValue("committed").jsonPrimitive.boolean,
    members = value.getValue("members").jsonArray.map { item -> item.jsonObject.let {
        CourseTransactionMember(it.getValue("path").jsonPrimitive.content,
            it.getValue("originalDigest").jsonPrimitive.content, it.getValue("replacementDigest").jsonPrimitive.content)
    } }
)
