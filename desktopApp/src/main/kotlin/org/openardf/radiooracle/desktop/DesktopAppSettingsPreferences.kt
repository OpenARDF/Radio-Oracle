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

import java.util.prefs.Preferences
import org.openardf.radiooracle.desktop.usb.DesktopSportIdentPortDiscoveryMode
import org.openardf.radiooracle.desktop.usb.DesktopSportIdentPortDiscoverySettings

interface DesktopAppSettingsStore : DesktopSportIdentPortDiscoverySettings {
    fun isUpdateCheckingEnabled(): Boolean
    fun setUpdateCheckingEnabled(enabled: Boolean)
    fun setSportIdentPortDiscoveryMode(mode: DesktopSportIdentPortDiscoveryMode)
    fun cloudflarePagesPublishSettings(): DesktopCloudflarePagesPublishSettings
    fun setCloudflarePagesPublishSettings(settings: DesktopCloudflarePagesPublishSettings)
    fun windowBounds(): DesktopWindowBounds?
    fun setWindowBounds(bounds: DesktopWindowBounds)
}

data class DesktopWindowBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
) {
    fun normalized(): DesktopWindowBounds? {
        if (x !in MIN_POSITION..MAX_POSITION || y !in MIN_POSITION..MAX_POSITION) {
            return null
        }
        if (width <= 0 || height <= 0) {
            return null
        }
        return copy(
            width = width.coerceAtLeast(MIN_WIDTH),
            height = height.coerceAtLeast(MIN_HEIGHT)
        )
    }

    companion object {
        const val DEFAULT_WIDTH = 1280
        const val DEFAULT_HEIGHT = 820
        const val MIN_WIDTH = 960
        const val MIN_HEIGHT = 640
        private const val MIN_POSITION = -20_000
        private const val MAX_POSITION = 20_000
    }
}

data class DesktopCloudflarePagesPublishSettings(
    val projectName: String = "openardf-results",
    val branch: String = "main",
    val accountId: String = "",
    val apiToken: String = ""
) {
    fun normalized(): DesktopCloudflarePagesPublishSettings =
        copy(
            projectName = projectName.trim().ifBlank { "openardf-results" },
            branch = branch.trim().ifBlank { "main" },
            accountId = accountId.trim(),
            apiToken = apiToken.trim()
        )

    fun request(directory: java.nio.file.Path): DesktopCloudflarePagesPublishRequest {
        val normalized = normalized()
        return DesktopCloudflarePagesPublishRequest(
            directory = directory,
            projectName = normalized.projectName,
            branch = normalized.branch,
            accountId = normalized.accountId,
            apiToken = normalized.apiToken
        )
    }
}

object DesktopAppSettingsPreferences : DesktopAppSettingsStore {
    private const val CHECK_FOR_UPDATES_KEY = "checkForRadioOracleUpdates"
    private const val SPORT_IDENT_PORT_DISCOVERY_MODE_KEY = "sportIdentPortDiscoveryMode"
    private const val SPORT_IDENT_REMEMBERED_FTDI_PORT_PATH_KEY = "sportIdentRememberedFtdiPortPath"
    private const val CLOUDFLARE_PROJECT_NAME_KEY = "cloudflarePagesProjectName"
    private const val CLOUDFLARE_BRANCH_KEY = "cloudflarePagesBranch"
    private const val CLOUDFLARE_ACCOUNT_ID_KEY = "cloudflarePagesAccountId"
    private const val CLOUDFLARE_API_TOKEN_KEY = "cloudflarePagesApiToken"
    private const val WINDOW_X_KEY = "windowX"
    private const val WINDOW_Y_KEY = "windowY"
    private const val WINDOW_WIDTH_KEY = "windowWidth"
    private const val WINDOW_HEIGHT_KEY = "windowHeight"
    private const val MISSING_WINDOW_VALUE = Int.MIN_VALUE
    private val preferences: Preferences =
        Preferences.userNodeForPackage(DesktopAppSettingsPreferences::class.java)

    override fun isUpdateCheckingEnabled(): Boolean =
        preferences.getBoolean(CHECK_FOR_UPDATES_KEY, true)

    override fun setUpdateCheckingEnabled(enabled: Boolean) {
        preferences.putBoolean(CHECK_FOR_UPDATES_KEY, enabled)
    }

    override fun sportIdentPortDiscoveryMode(): DesktopSportIdentPortDiscoveryMode =
        runCatching {
            DesktopSportIdentPortDiscoveryMode.valueOf(
                preferences.get(
                    SPORT_IDENT_PORT_DISCOVERY_MODE_KEY,
                    DesktopSportIdentPortDiscoveryMode.SPORTIDENT_USB_ONLY.name
                )
            )
        }.getOrDefault(DesktopSportIdentPortDiscoveryMode.SPORTIDENT_USB_ONLY)

    override fun setSportIdentPortDiscoveryMode(mode: DesktopSportIdentPortDiscoveryMode) {
        preferences.put(SPORT_IDENT_PORT_DISCOVERY_MODE_KEY, mode.name)
    }

    override fun rememberedSportIdentFtdiPortPath(): String? =
        preferences.get(SPORT_IDENT_REMEMBERED_FTDI_PORT_PATH_KEY, "")
            .takeIf { it.isNotBlank() }

    override fun rememberSportIdentFtdiPortPath(portPath: String) {
        preferences.put(SPORT_IDENT_REMEMBERED_FTDI_PORT_PATH_KEY, portPath)
    }

    override fun cloudflarePagesPublishSettings(): DesktopCloudflarePagesPublishSettings =
        DesktopCloudflarePagesPublishSettings(
            projectName = preferences.get(CLOUDFLARE_PROJECT_NAME_KEY, "openardf-results"),
            branch = preferences.get(CLOUDFLARE_BRANCH_KEY, "main"),
            accountId = preferences.get(CLOUDFLARE_ACCOUNT_ID_KEY, ""),
            apiToken = preferences.get(CLOUDFLARE_API_TOKEN_KEY, "")
        ).normalized()

    override fun setCloudflarePagesPublishSettings(settings: DesktopCloudflarePagesPublishSettings) {
        val normalized = settings.normalized()
        preferences.put(CLOUDFLARE_PROJECT_NAME_KEY, normalized.projectName)
        preferences.put(CLOUDFLARE_BRANCH_KEY, normalized.branch)
        preferences.put(CLOUDFLARE_ACCOUNT_ID_KEY, normalized.accountId)
        preferences.put(CLOUDFLARE_API_TOKEN_KEY, normalized.apiToken)
    }

    override fun windowBounds(): DesktopWindowBounds? {
        val x = preferences.getInt(WINDOW_X_KEY, MISSING_WINDOW_VALUE)
        val y = preferences.getInt(WINDOW_Y_KEY, MISSING_WINDOW_VALUE)
        val width = preferences.getInt(WINDOW_WIDTH_KEY, MISSING_WINDOW_VALUE)
        val height = preferences.getInt(WINDOW_HEIGHT_KEY, MISSING_WINDOW_VALUE)
        if (listOf(x, y, width, height).any { it == MISSING_WINDOW_VALUE }) {
            return null
        }
        return DesktopWindowBounds(x = x, y = y, width = width, height = height).normalized()
    }

    override fun setWindowBounds(bounds: DesktopWindowBounds) {
        val normalized = bounds.normalized() ?: return
        preferences.putInt(WINDOW_X_KEY, normalized.x)
        preferences.putInt(WINDOW_Y_KEY, normalized.y)
        preferences.putInt(WINDOW_WIDTH_KEY, normalized.width)
        preferences.putInt(WINDOW_HEIGHT_KEY, normalized.height)
    }
}
