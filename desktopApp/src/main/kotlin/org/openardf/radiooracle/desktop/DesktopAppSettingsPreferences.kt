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
