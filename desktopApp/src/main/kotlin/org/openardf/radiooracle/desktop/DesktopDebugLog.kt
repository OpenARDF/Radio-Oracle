package org.openardf.radiooracle.desktop

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant

object DesktopAppDirectories {
    private const val APP_FOLDER = "Radio-Oracle"

    fun appDataDirectory(
        osName: String = System.getProperty("os.name"),
        userHome: Path = Path.of(System.getProperty("user.home")),
        appData: String? = System.getenv("APPDATA"),
        xdgStateHome: String? = System.getenv("XDG_STATE_HOME")
    ): Path {
        val normalizedOsName = osName.lowercase()
        return when {
            normalizedOsName.contains("mac") ->
                userHome.resolve("Library").resolve("Application Support").resolve(APP_FOLDER)
            normalizedOsName.contains("win") && !appData.isNullOrBlank() ->
                Path.of(appData).resolve(APP_FOLDER)
            normalizedOsName.contains("win") ->
                userHome.resolve("AppData").resolve("Roaming").resolve(APP_FOLDER)
            !xdgStateHome.isNullOrBlank() ->
                Path.of(xdgStateHome).resolve(APP_FOLDER)
            else ->
                userHome.resolve(".local").resolve("state").resolve(APP_FOLDER)
        }
    }

    fun logDirectory(): Path =
        appDataDirectory().resolve("logs")
}

class DesktopRollingDebugLog(
    val directory: Path,
    private val clock: () -> Instant = { Instant.now() },
    private val maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
    private val retainedFileCount: Int = DEFAULT_RETAINED_FILE_COUNT,
    private val fileName: String = DEFAULT_FILE_NAME
) {
    init {
        require(maxFileBytes > 0) { "maxFileBytes must be positive" }
        require(retainedFileCount > 0) { "retainedFileCount must be positive" }
        require(fileName.isNotBlank()) { "fileName must not be blank" }
    }

    @Synchronized
    fun write(level: String, tag: String, message: String) {
        Files.createDirectories(directory)
        val line = "${clock()} ${sanitize(level)} ${sanitize(tag)} ${sanitize(message)}\n"
        val bytes = line.toByteArray(StandardCharsets.UTF_8)
        rotateIfNeeded(bytes.size.toLong())
        Files.write(activeFile(), bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }

    fun logFiles(): List<Path> =
        (listOf(activeFile()) + (1 until retainedFileCount).map { archiveFile(it) })
            .filter(Files::exists)

    fun sanitize(value: String): String =
        value
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .replace(Regex("[\\p{Cntrl}&&[^ ]]"), "?")
            .trim()

    private fun rotateIfNeeded(incomingBytes: Long) {
        val activeFile = activeFile()
        if (!Files.exists(activeFile) || Files.size(activeFile) + incomingBytes <= maxFileBytes) {
            return
        }

        Files.deleteIfExists(archiveFile(retainedFileCount - 1))
        for (index in retainedFileCount - 2 downTo 1) {
            val source = archiveFile(index)
            if (Files.exists(source)) {
                Files.move(source, archiveFile(index + 1))
            }
        }
        Files.move(activeFile, archiveFile(1))
    }

    private fun activeFile(): Path = directory.resolve(fileName)

    private fun archiveFile(index: Int): Path = directory.resolve("$fileName.$index")

    companion object {
        const val DEFAULT_FILE_NAME = "debug.log"
        const val DEFAULT_MAX_FILE_BYTES = 512L * 1024L
        const val DEFAULT_RETAINED_FILE_COUNT = 3
    }
}

object DesktopDebugLog {
    @Volatile
    private var rollingLog: DesktopRollingDebugLog? = null

    fun initialize(logDirectory: Path = DesktopAppDirectories.logDirectory()) {
        rollingLog = DesktopRollingDebugLog(logDirectory)
        info("App", "Desktop debug log initialized at $logDirectory")
    }

    fun logDirectory(): Path =
        rollingLog?.directory ?: DesktopAppDirectories.logDirectory()

    fun logFiles(): List<Path> =
        rollingLog?.logFiles() ?: emptyList()

    fun debug(tag: String, message: String) {
        write("D", tag, message)
    }

    fun info(tag: String, message: String) {
        write("I", tag, message)
    }

    fun warn(tag: String, message: String) {
        write("W", tag, message)
    }

    fun error(tag: String, message: String) {
        write("E", tag, message)
    }

    private fun write(level: String, tag: String, message: String) {
        runCatching {
            rollingLog?.write(level, tag, message)
        }.onFailure { error ->
            System.err.println("Failed to write Radio-Oracle desktop debug log: ${error.message}")
        }
    }
}
