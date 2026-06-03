package org.openardf.radiooracle.desktop

import java.nio.file.Path

/** Opens an optional startup Event File path for repeatable desktop smoke runs. */
fun openStartupProject(
    session: DesktopProjectSession,
    path: Path?
): String {
    if (path == null) {
        return "No Event File open."
    }

    return runCatching {
        session.open(path)
        "Opened ${path.fileName}"
    }.getOrElse { error ->
        "Open failed: ${error.message ?: error::class.simpleName}"
    }
}
