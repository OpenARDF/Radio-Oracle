package org.openardf.radiooracle.desktop

import java.util.prefs.Preferences

interface DesktopAppSettingsStore {
    fun isUpdateCheckingEnabled(): Boolean
    fun setUpdateCheckingEnabled(enabled: Boolean)
}

object DesktopAppSettingsPreferences : DesktopAppSettingsStore {
    private const val CHECK_FOR_UPDATES_KEY = "checkForRadioOracleUpdates"
    private val preferences: Preferences =
        Preferences.userNodeForPackage(DesktopAppSettingsPreferences::class.java)

    override fun isUpdateCheckingEnabled(): Boolean =
        preferences.getBoolean(CHECK_FOR_UPDATES_KEY, true)

    override fun setUpdateCheckingEnabled(enabled: Boolean) {
        preferences.putBoolean(CHECK_FOR_UPDATES_KEY, enabled)
    }
}
