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

interface DesktopAppSettingsStore {
    fun isUpdateCheckingEnabled(): Boolean
    fun setUpdateCheckingEnabled(enabled: Boolean)
    fun cloudflarePagesPublishSettings(): DesktopCloudflarePagesPublishSettings
    fun setCloudflarePagesPublishSettings(settings: DesktopCloudflarePagesPublishSettings)
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
    private const val CLOUDFLARE_PROJECT_NAME_KEY = "cloudflarePagesProjectName"
    private const val CLOUDFLARE_BRANCH_KEY = "cloudflarePagesBranch"
    private const val CLOUDFLARE_ACCOUNT_ID_KEY = "cloudflarePagesAccountId"
    private const val CLOUDFLARE_API_TOKEN_KEY = "cloudflarePagesApiToken"
    private val preferences: Preferences =
        Preferences.userNodeForPackage(DesktopAppSettingsPreferences::class.java)

    override fun isUpdateCheckingEnabled(): Boolean =
        preferences.getBoolean(CHECK_FOR_UPDATES_KEY, true)

    override fun setUpdateCheckingEnabled(enabled: Boolean) {
        preferences.putBoolean(CHECK_FOR_UPDATES_KEY, enabled)
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
}
