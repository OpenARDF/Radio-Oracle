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

/** Opens an optional startup Race File path for repeatable desktop smoke runs. */
fun openStartupProject(
    session: DesktopProjectSession,
    path: Path?,
    onOpened: (Path) -> Unit = {}
): String {
    if (path == null) {
        return "No Race File open."
    }

    return runCatching {
        session.open(path)
        onOpened(path)
        "Opened ${path.fileName}"
    }.getOrElse { error ->
        "Open failed: ${error.message ?: error::class.simpleName}"
    }
}
