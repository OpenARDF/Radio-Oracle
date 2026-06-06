package org.openardf.radiooracle.desktop

import java.nio.file.Path
import java.util.prefs.Preferences

interface DesktopLastEventFileStore {
    fun lastEventFile(): Path?
    fun rememberEventFile(path: Path)
}

object DesktopLastEventFilePreferences : DesktopLastEventFileStore {
    private const val LAST_EVENT_FILE_KEY = "lastEventFile"
    private val preferences: Preferences =
        Preferences.userNodeForPackage(DesktopLastEventFilePreferences::class.java)

    override fun lastEventFile(): Path? =
        runCatching {
            preferences.get(LAST_EVENT_FILE_KEY, null)
                ?.takeIf { it.isNotBlank() }
                ?.let(Path::of)
        }.getOrNull()

    override fun rememberEventFile(path: Path) {
        runCatching {
            preferences.put(LAST_EVENT_FILE_KEY, path.toAbsolutePath().toString())
        }
    }
}

fun startupProjectPath(
    commandLinePath: Path?,
    lastEventFileStore: DesktopLastEventFileStore = DesktopLastEventFilePreferences
): Path? =
    commandLinePath ?: lastEventFileStore.lastEventFile()

/** Opens an optional startup Event File path for repeatable desktop smoke runs. */
fun openStartupProject(
    session: DesktopProjectSession,
    path: Path?,
    onOpened: (Path) -> Unit = {}
): String {
    if (path == null) {
        return "No Event File open."
    }

    return runCatching {
        session.open(path)
        onOpened(path)
        "Opened ${path.fileName}"
    }.getOrElse { error ->
        "Open failed: ${error.message ?: error::class.simpleName}"
    }
}
