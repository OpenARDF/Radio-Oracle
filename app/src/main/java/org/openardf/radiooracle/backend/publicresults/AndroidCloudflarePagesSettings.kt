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

package org.openardf.radiooracle.backend.publicresults

import android.content.Context
import androidx.preference.PreferenceManager
import java.security.MessageDigest

enum class AndroidPublicResultsRetentionMode {
    RETAIN_PREVIOUS,
    REPLACE_PREVIOUS
}

data class AndroidCloudflarePagesPublishSettings(
    val projectName: String = DEFAULT_PROJECT,
    val branch: String = DEFAULT_BRANCH,
    val accountId: String = "",
    val apiToken: String = "",
    val retentionMode: AndroidPublicResultsRetentionMode =
        AndroidPublicResultsRetentionMode.RETAIN_PREVIOUS
) {
    fun normalized(): AndroidCloudflarePagesPublishSettings =
        copy(
            projectName = projectName.trim().ifBlank { DEFAULT_PROJECT },
            branch = branch.trim().ifBlank { DEFAULT_BRANCH },
            accountId = accountId.trim(),
            apiToken = apiToken.trim()
        )

    fun isComplete(): Boolean {
        val value = normalized()
        return value.projectName.isNotBlank() &&
            value.branch.isNotBlank() &&
            value.accountId.isNotBlank() &&
            value.apiToken.isNotBlank()
    }

    fun publicSiteBaseUrl(): String =
        "https://${normalized().projectName}.pages.dev"

    companion object {
        const val DEFAULT_PROJECT = "openardf-results"
        const val DEFAULT_BRANCH = "main"
    }
}

object AndroidCloudflarePagesSettingsStore {
    const val PROJECT_NAME_KEY = "cloudflarePagesProjectName"
    const val BRANCH_KEY = "cloudflarePagesBranch"
    const val ACCOUNT_ID_KEY = "cloudflarePagesAccountId"
    const val API_TOKEN_KEY = "cloudflarePagesApiToken"
    const val RETENTION_MODE_KEY = "cloudflarePagesRetentionMode"
    private const val REJECTION_FINGERPRINT_KEY = "cloudflarePagesRejectedSettingsFingerprint"

    fun read(context: Context): AndroidCloudflarePagesPublishSettings {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        return AndroidCloudflarePagesPublishSettings(
            projectName = preferences.getString(
                PROJECT_NAME_KEY,
                AndroidCloudflarePagesPublishSettings.DEFAULT_PROJECT
            ).orEmpty(),
            branch = preferences.getString(
                BRANCH_KEY,
                AndroidCloudflarePagesPublishSettings.DEFAULT_BRANCH
            ).orEmpty(),
            accountId = preferences.getString(ACCOUNT_ID_KEY, "").orEmpty(),
            apiToken = preferences.getString(API_TOKEN_KEY, "").orEmpty(),
            retentionMode = runCatching {
                AndroidPublicResultsRetentionMode.valueOf(
                    preferences.getString(
                        RETENTION_MODE_KEY,
                        AndroidPublicResultsRetentionMode.RETAIN_PREVIOUS.name
                    ).orEmpty()
                )
            }.getOrDefault(AndroidPublicResultsRetentionMode.RETAIN_PREVIOUS)
        ).normalized()
    }

    fun write(context: Context, settings: AndroidCloudflarePagesPublishSettings) {
        val value = settings.normalized()
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            .edit()
            .putString(PROJECT_NAME_KEY, value.projectName)
            .putString(BRANCH_KEY, value.branch)
            .putString(ACCOUNT_ID_KEY, value.accountId)
            .putString(API_TOKEN_KEY, value.apiToken)
            .putString(RETENTION_MODE_KEY, value.retentionMode.name)
            .apply()
    }

    fun isRejected(
        context: Context,
        settings: AndroidCloudflarePagesPublishSettings = read(context)
    ): Boolean {
        val rejectedFingerprint = PreferenceManager
            .getDefaultSharedPreferences(context.applicationContext)
            .getString(REJECTION_FINGERPRINT_KEY, null)
        return rejectedFingerprint != null && rejectedFingerprint == settings.rejectionFingerprint()
    }

    fun recordRejection(context: Context, settings: AndroidCloudflarePagesPublishSettings) {
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            .edit()
            .putString(REJECTION_FINGERPRINT_KEY, settings.rejectionFingerprint())
            .apply()
    }

    fun clearRejection(context: Context) {
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            .edit()
            .remove(REJECTION_FINGERPRINT_KEY)
            .apply()
    }

    private fun AndroidCloudflarePagesPublishSettings.rejectionFingerprint(): String {
        val value = normalized()
        val input = listOf(
            value.projectName,
            value.branch,
            value.accountId,
            value.apiToken
        ).joinToString("\u0000")
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}
