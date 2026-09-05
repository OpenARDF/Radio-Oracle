package org.openardf.radiooracle.desktop

import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/** Fail closed if atomic replacement is unavailable; never truncate an existing Race File. */
internal fun writeDesktopTextAtomically(path: Path, text: String) {
    val target = path.toAbsolutePath().normalize()
    Files.createDirectories(target.parent)
    val temporary = Files.createTempFile(target.parent, ".radio-oracle-", ".tmp")
    try {
        Files.writeString(temporary, text)
        FileChannel.open(temporary, StandardOpenOption.WRITE).use { it.force(true) }
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } finally {
        Files.deleteIfExists(temporary)
    }
}
