package org.openardf.radiooracle.ui

internal object ScreenBrightnessOverride {
    private const val MAX_SYSTEM_BRIGHTNESS = 255f

    fun fromSystemSetting(systemBrightness: Int?): Float? {
        return systemBrightness
            ?.coerceIn(1, MAX_SYSTEM_BRIGHTNESS.toInt())
            ?.div(MAX_SYSTEM_BRIGHTNESS)
    }
}
