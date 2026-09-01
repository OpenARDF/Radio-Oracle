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

package org.openardf.radiooracle.ui.settings

import android.os.Bundle
import android.text.InputType
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.publicresults.AndroidCloudflarePagesSettingsStore

class CloudflareSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_cloudflare, rootKey)
        listOf(
            AndroidCloudflarePagesSettingsStore.PROJECT_NAME_KEY,
            AndroidCloudflarePagesSettingsStore.BRANCH_KEY,
            AndroidCloudflarePagesSettingsStore.ACCOUNT_ID_KEY,
            AndroidCloudflarePagesSettingsStore.API_TOKEN_KEY
        ).forEach { key ->
            findPreference<Preference>(key)?.setOnPreferenceChangeListener { _, _ ->
                AndroidCloudflarePagesSettingsStore.clearRejection(requireContext())
                true
            }
        }
        val token = findPreference<EditTextPreference>(
            AndroidCloudflarePagesSettingsStore.API_TOKEN_KEY
        )
        token?.setOnBindEditTextListener { editText ->
            editText.inputType =
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        token?.summaryProvider = androidx.preference.Preference.SummaryProvider<EditTextPreference> {
            if (it.text.isNullOrBlank()) {
                getString(R.string.cloudflare_api_token_missing)
            } else {
                getString(R.string.cloudflare_api_token_saved)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<Toolbar>(R.id.settings_toolbar)?.let { toolbar ->
            toolbar.title = getString(R.string.global_settings)
            toolbar.subtitle = getString(R.string.preferences_cloudflare)
            val color = ContextCompat.getColor(requireContext(), R.color.white)
            toolbar.setTitleTextColor(color)
            toolbar.setSubtitleTextColor(color)
        }
    }
}
