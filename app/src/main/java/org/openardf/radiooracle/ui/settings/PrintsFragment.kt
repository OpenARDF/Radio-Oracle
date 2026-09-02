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

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreference
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.sounds.SoundProcessor
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.logging.DebugLog
import org.openardf.radiooracle.backend.results.ResultsProcessor


class PrintsFragment : PreferenceFragmentCompat() {
    private lateinit var prefs: SharedPreferences
    private val dataProcessor = DataProcessor.get()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_prints, rootKey)
        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())

        setPreferences()
        val printsEnabled =
            prefs.getBoolean(requireContext().getString(R.string.key_prints_enabled), false)
        enableOrDisablePreferences(printsEnabled)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        //Add a back button to the toolbar
        view.findViewById<Toolbar>(R.id.settings_toolbar)?.let { toolbar ->
            toolbar.title = getString(R.string.global_settings)
            toolbar.subtitle = getString(R.string.general_print)
            val onToolbarColor = ContextCompat.getColor(requireContext(), R.color.white)
            toolbar.setTitleTextColor(onToolbarColor)
            toolbar.setSubtitleTextColor(onToolbarColor)
        }
    }

    private fun setPreferences() {
        val editor = prefs.edit()

        //Enable printing
        val enablePrintingPreference =
            findPreference<SwitchPreference>(requireContext().getString(R.string.key_prints_enabled))

        enablePrintingPreference?.setOnPreferenceChangeListener { _, enablePrints ->

            // If printing is disabled -> let the PrintProcessor know
            if (enablePrints as Boolean) {
                logInfo("Print settings enabled")
                // Request bluetooth permissions if needed
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ActivityCompat.requestPermissions(
                        requireActivity(),
                        arrayOf(
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_SCAN
                        ),
                        1
                    )
                }
            } else {
                logInfo("Print settings disabled")
                dataProcessor.disablePrinter()
            }

            editor.putBoolean(
                requireContext().getString(R.string.key_prints_enabled),
                enablePrints as Boolean
            )
            editor.apply()
            enableOrDisablePreferences(enablePrints)

            true
        }

        //Printer selection
        val printerSelectPreference =
            findPreference<ListPreference>(requireContext().getString(R.string.key_prints_selected_printer_address))

        printerSelectPreference?.setOnPreferenceClickListener {

            val bluetoothAvailable =
                requireContext().packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)

            if (bluetoothAvailable) {
                // Get the BluetoothManager
                val bluetoothAdapter =
                    ContextCompat.getSystemService(
                        requireContext(),
                        BluetoothManager::class.java
                    )?.adapter

                val pairedDevices = emptySet<BluetoothDevice>().toMutableSet()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ) {
                    // Request the BLUETOOTH_CONNECT permission
                    ActivityCompat.requestPermissions(
                        requireActivity(),
                        arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                        1
                    )
                }

                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S ||
                    ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    // Permission already granted, proceed with accessing paired devices
                    if (bluetoothAdapter != null && bluetoothAdapter.bondedDevices != null) {
                        pairedDevices.addAll(bluetoothAdapter.bondedDevices!!)
                    }
                } else {
                    logWarn("Bluetooth printer list unavailable because BLUETOOTH_CONNECT permission is not granted")
                }
                val deviceNames = pairedDevices.map { it.name }.toTypedArray()
                val deviceAddresses = pairedDevices.map { it.address }.toTypedArray()
                logInfo("Loaded ${pairedDevices.size} paired Bluetooth printer candidate(s)")

                printerSelectPreference.entries = deviceNames
                printerSelectPreference.entryValues = deviceAddresses
            }
            //Warning about missing bluetooth
            else {
                logWarn("Bluetooth is not supported on this device")
                SoundProcessor.makeErrorSound(requireContext())
                val toast = Toast.makeText(
                    requireContext(),
                    requireContext().getString(R.string.print_bluetooth_not_supported),
                    Toast.LENGTH_LONG
                )
                toast.show()
            }
            true
        }
        val currPrinterPref = prefs.getString(
            requireContext().getString(R.string.key_prints_selected_printer_name),
            ""
        )
        printerSelectPreference?.summary = requireContext().getString(
            R.string.preferences_prints_select_printer_hint,
            currPrinterPref
        )

        // Save both printer name and address
        printerSelectPreference?.setOnPreferenceChangeListener { preference, printer ->
            val listPref = preference as ListPreference
            val address = printer as String
            val index = listPref.entryValues.indexOf(address)
            val name = if (index >= 0) listPref.entries[index].toString() else ""

            printerSelectPreference.summary =
                requireContext().getString(R.string.preferences_prints_select_printer_hint, name)

            editor.putString(
                requireContext().getString(R.string.key_prints_selected_printer_name),
                name
            )
            editor.putString(
                requireContext().getString(R.string.key_prints_selected_printer_address),
                address
            )
            editor.apply()
            logInfo("Selected Bluetooth printer name=$name address=${address.maskBluetoothAddress()}")
            true
        }

        val automaticPrintPreference =
            findPreference<ListPreference>(requireContext().getString(R.string.key_prints_automatic_printout))

        automaticPrintPreference?.let { preference ->
            val automaticPrintKey = requireContext().getString(R.string.key_prints_automatic_printout)
            val currentAction = prefs.getString(
                automaticPrintKey,
                requireContext().getString(R.string.print_automatic_manually_value)
            )
            val normalizedAction =
                if (currentAction == ResultsProcessor.PRINT_AUTOMATIC_ALWAYS_LEGACY_VALUE) {
                    ResultsProcessor.PRINT_AUTOMATIC_ALWAYS_VALUE
                } else {
                    currentAction
                }

            if (normalizedAction != currentAction) {
                editor.putString(automaticPrintKey, normalizedAction)
                editor.apply()
            }

            preference.value = normalizedAction
            preference.updateSummaryForValue(normalizedAction)
        }

        automaticPrintPreference?.setOnPreferenceChangeListener { preference, action ->
            val selectedAction = action.toString()
            editor.putString(
                requireContext().getString(R.string.key_prints_automatic_printout),
                selectedAction
            )
            editor.apply()
            logInfo("Automatic print mode set to $selectedAction")

            (preference as ListPreference).updateSummaryForValue(selectedAction)
            true
        }

        val doublePrintPreference =
            findPreference<CheckBoxPreference>(requireContext().getString(R.string.key_prints_double_print))
        val doublePrintDelayPreference =
            findPreference<SeekBarPreference>(requireContext().getString(R.string.key_prints_double_print_delay))

        doublePrintPreference?.setOnPreferenceChangeListener { _, doublePrint ->
            editor.putBoolean(
                requireContext().getString(R.string.key_prints_double_print),
                doublePrint as Boolean
            )
            doublePrintDelayPreference?.isEnabled = doublePrint

            editor.apply()
            logInfo("Double print set to $doublePrint")
            true
        }

        // Enable / disable delay if double print is turned off
        doublePrintDelayPreference?.isEnabled = (doublePrintPreference?.isChecked == true)

        doublePrintDelayPreference?.setOnPreferenceChangeListener { _, doublePrint ->
            editor.putInt(
                requireContext().getString(R.string.key_prints_double_print_delay),
                doublePrint as Int
            )
            editor.apply()
            logInfo("Double print delay set to $doublePrint seconds")
            true
        }

        val removeDiacriticsPreference =
            findPreference<CheckBoxPreference>(requireContext().getString(R.string.key_prints_remove_diacritics))

        removeDiacriticsPreference?.setOnPreferenceChangeListener { _, removeDiacritics ->
            editor.putBoolean(
                requireContext().getString(R.string.key_prints_remove_diacritics),
                removeDiacritics as Boolean
            )
            editor.apply()
            logInfo("Remove diacritics set to $removeDiacritics")
            true
        }
    }

    private fun enableOrDisablePreferences(enable: Boolean) {
        val printerSelectPreference =
            findPreference<ListPreference>(requireContext().getString(R.string.key_prints_selected_printer_address))

        val automaticPrintPreference =
            findPreference<ListPreference>(requireContext().getString(R.string.key_prints_automatic_printout))

        printerSelectPreference?.isEnabled = enable
        automaticPrintPreference?.isEnabled = enable
    }

    private fun logInfo(message: String) {
        Log.i(LOG_TAG, message)
        DebugLog.info(LOG_TAG, message)
    }

    private fun logWarn(message: String) {
        Log.w(LOG_TAG, message)
        DebugLog.warn(LOG_TAG, message)
    }

    private fun ListPreference.updateSummaryForValue(value: String?) {
        val index = findIndexOfValue(value)
        val selectedLabel = if (index >= 0) entries[index].toString() else ""
        summary = requireContext().getString(
            R.string.preferences_prints_automatic_hint,
            selectedLabel
        )
    }

    private fun String.maskBluetoothAddress(): String =
        split(":").let { parts ->
            if (parts.size == 6) {
                "xx:xx:xx:${parts.takeLast(3).joinToString(":")}"
            } else {
                "<selected>"
            }
        }

    private companion object {
        const val LOG_TAG = "Printer"
    }
}
