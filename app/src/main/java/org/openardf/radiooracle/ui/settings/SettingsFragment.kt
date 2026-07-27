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

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import org.openardf.radiooracle.R
import org.openardf.radiooracle.ui.MainActivity
import org.openardf.radiooracle.ui.ScreenOrientationPreference


class SettingsFragment : PreferenceFragmentCompat() {
    private lateinit var prefs: SharedPreferences

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<Toolbar>(R.id.settings_toolbar)?.let { toolbar ->
            toolbar.title = getString(R.string.global_settings)
            toolbar.subtitle = getString(R.string.general_main)
            val onToolbarColor = ContextCompat.getColor(requireContext(), R.color.white)
            toolbar.setTitleTextColor(onToolbarColor)
            toolbar.setSubtitleTextColor(onToolbarColor)
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        setPreferences()
    }

    private fun setPreferences() {
        val editor = prefs.edit()

        findPreference<CheckBoxPreference>(requireContext().getString(R.string.key_keep_screen_open))
            ?.setOnPreferenceChangeListener { _, keepOpen ->

                val enabled = keepOpen as Boolean

                editor.putBoolean(
                    requireContext().getString(R.string.key_keep_screen_open),
                    enabled
                )
                editor.apply()
                (requireActivity() as? MainActivity)?.applyKeepScreenOpenPreference(enabled)
                true
            }

        val langPref =
            findPreference<ListPreference>(requireContext().getString(R.string.key_app_language))

        langPref?.setOnPreferenceChangeListener { pref, code ->
            editor.putString(
                requireContext().getString(R.string.key_app_language),
                code.toString()
            )
            editor.apply()
            true
        }

        val screenOrientationPref =
            findPreference<ListPreference>(requireContext().getString(R.string.key_screen_orientation))

        screenOrientationPref?.setOnPreferenceChangeListener { _, value ->
            val orientation = value.toString()
            editor.putString(
                requireContext().getString(R.string.key_screen_orientation),
                orientation
            )
            editor.apply()
            requireActivity().requestedOrientation =
                ScreenOrientationPreference.requestedOrientation(orientation)
            true
        }

        //Time format
        val timeFormatPref =
            findPreference<ListPreference>(requireContext().getString(R.string.key_results_time_format))

        val currTimeFormatPref = prefs.getString(
            requireContext().getString(R.string.key_results_time_format),
            requireContext().getString(R.string.preferences_results_time_format_minutes)
        )

        timeFormatPref?.summary = requireContext().getString(
            R.string.preferences_results_time_format_hint,
            currTimeFormatPref
        )

        timeFormatPref?.setOnPreferenceChangeListener { _, timeFormat ->

            editor.putString(
                requireContext().getString(R.string.key_results_time_format),
                timeFormat.toString()
            )
            editor.apply()

            timeFormatPref.summary = requireContext().getString(
                R.string.preferences_results_time_format_hint,
                timeFormat
            )
            true
        }

        //Enable sounds
        findPreference<CheckBoxPreference>(
            requireContext().getString(
                R.string.key_readout_error_sounds
            )
        )
            ?.setOnPreferenceChangeListener { _, enableErrorSounds ->

                editor.putBoolean(
                    requireContext().getString(R.string.key_readout_error_sounds),
                    enableErrorSounds as Boolean
                )
                editor.apply()
                true
            }

        //Aliases
        findPreference<CheckBoxPreference>(requireContext().getString(R.string.key_results_use_aliases))
            ?.setOnPreferenceChangeListener { _, useAliases ->

                editor.putBoolean(
                    requireContext().getString(R.string.key_results_use_aliases),
                    useAliases as Boolean
                )
                editor.apply()
                true
            }

        // Result service
        val resultServicePref =
            findPreference<ListPreference>(requireContext().getString(R.string.key_result_service))

        resultServicePref?.setOnPreferenceChangeListener { _, service ->
            editor.putString(
                requireContext().getString(R.string.key_result_service),
                service.toString()
            )
            editor.apply()
            true
        }

        findPreference<CheckBoxPreference>(requireContext().getString(R.string.key_files_prefer_app_start_time))
            ?.setOnPreferenceChangeListener { _, keepOpen ->

                editor.putBoolean(
                    requireContext().getString(R.string.key_files_prefer_app_start_time),
                    keepOpen as Boolean
                )
                editor.apply()
                true
            }

        findPreference<androidx.preference.Preference>(
            requireContext().getString(
                R.string.key_prints
            )
        )
            ?.setOnPreferenceClickListener {
                findNavController().navigate(SettingsFragmentDirections.configurePrints())
                true
            }

        findPreference<androidx.preference.Preference>(
            requireContext().getString(R.string.key_cloudflare_settings)
        )?.setOnPreferenceClickListener {
            findNavController().navigate(SettingsFragmentDirections.configureCloudflare())
            true
        }

    }
}
