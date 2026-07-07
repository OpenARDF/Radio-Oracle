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

data class DesktopAppUpdateStatus(
    val currentVersion: String,
    val launchedByJdeploy: Boolean,
    val jdeployUpdatesAvailable: Boolean?,
    val jdeployAppVersion: String?,
    val jdeployAppSource: String?
)

object DesktopAppUpdateSupport {
    const val updatePageUrl = "https://www.jdeploy.com/gh/OpenARDF/Radio-Oracle"

    private const val jdeployUpdatesAvailableProperty = "jdeploy.updatesAvailable"
    private const val jdeployAppVersionProperty = "jdeploy.app.version"
    private const val jdeployAppSourceProperty = "jdeploy.app.source"

    fun status(
        currentVersion: String,
        propertyValue: (String) -> String? = System::getProperty
    ): DesktopAppUpdateStatus {
        val updatesAvailableRaw = propertyValue(jdeployUpdatesAvailableProperty)
        val appVersion = propertyValue(jdeployAppVersionProperty)?.trim()?.takeIf(String::isNotBlank)
        val appSource = propertyValue(jdeployAppSourceProperty)?.trim()?.takeIf(String::isNotBlank)
        return DesktopAppUpdateStatus(
            currentVersion = currentVersion,
            launchedByJdeploy = updatesAvailableRaw != null || appVersion != null || appSource != null,
            jdeployUpdatesAvailable = parseFlexibleBoolean(updatesAvailableRaw),
            jdeployAppVersion = appVersion,
            jdeployAppSource = appSource
        )
    }

    fun shouldShowAutomaticNotice(status: DesktopAppUpdateStatus): Boolean =
        status.jdeployUpdatesAvailable == true

    fun disabledDialogMessage(): String =
        "Radio-Oracle update checks are disabled in App Settings.\n\n" +
            "Enable update checks to show jDeploy update status from app startup, or open the update page directly:\n" +
            updatePageUrl

    fun dialogMessage(status: DesktopAppUpdateStatus): String =
        buildString {
            when (status.jdeployUpdatesAvailable) {
                true -> append("An updated version of Radio-Oracle is available.")
                false -> append("jDeploy did not report a Radio-Oracle update at launch.")
                null -> {
                    if (status.launchedByJdeploy) {
                        append("Radio-Oracle was launched by jDeploy, but update status was not reported.")
                    } else {
                        append("Radio-Oracle was not launched by jDeploy, so jDeploy update status is not available in this run.")
                    }
                }
            }
            append("\n\nCurrent version: ${status.currentVersion}")
            if (!status.jdeployAppVersion.isNullOrBlank() && status.jdeployAppVersion != status.currentVersion) {
                append("\njDeploy package version: ${status.jdeployAppVersion}")
            }
            if (!status.jdeployAppSource.isNullOrBlank()) {
                append("\njDeploy source: ${status.jdeployAppSource}")
            }
            append("\n\nUpdate page:\n$updatePageUrl")
            append("\n\njDeploy-installed copies normally check for updates automatically each time they launch.")
        }

    private fun parseFlexibleBoolean(value: String?): Boolean? =
        when (value?.trim()?.lowercase()) {
            "true", "1", "yes" -> true
            "false", "0", "no" -> false
            else -> null
        }
}
