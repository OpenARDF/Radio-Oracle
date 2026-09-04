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

package org.openardf.radiooracle.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import com.google.android.material.navigation.NavigationBarView
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.AppState
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.files.FileProcessor
import org.openardf.radiooracle.backend.logging.DebugLog
import org.openardf.radiooracle.backend.sounds.SoundProcessor
import org.openardf.radiooracle.backend.room.ARDFRepository
import org.openardf.radiooracle.backend.sportident.SIConstants
import org.openardf.radiooracle.databinding.ActivityMainBinding
import org.openardf.radiooracle.shared.device.SIReadoutReadinessRules
import org.openardf.radiooracle.shared.device.SIReaderStatus
import org.openardf.radiooracle.shared.event.EventFileTransferPayloads
import org.openardf.radiooracle.shared.sportident.SportIdentStationMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.util.Locale


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var siStatusTextView: TextView
    private lateinit var dataProcessor: DataProcessor
    private var lastSiStationModeWarningKey: String? = null
    private var lastSiReaderStatus: SIReaderStatus? = null
    private var keepScreenOpen = false
    private var consumedSeriesArchiveUri: String? = null

    companion object {
        private const val KEY_RESULTS_SCORING_REVISION = "results_scoring_revision"
        private const val RESULTS_SCORING_REVISION = 5
    }

    private var usbDetachReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {

            if (UsbManager.ACTION_USB_DEVICE_DETACHED == intent.action) {
                val device: UsbDevice? = intent.parcelableExtraCompat(UsbManager.EXTRA_DEVICE)
                device?.apply {
                    dataProcessor.detachDevice(device)
                }
            }
        }
    }

    override fun attachBaseContext(newBase: Context?) {

        val languageCode: String = if (newBase != null) {
            PreferenceManager.getDefaultSharedPreferences(newBase).getString("app_language", "en")
                ?: "en"
        } else {
            "en"
        }

        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)

        val config = Configuration()
        config.setLocale(locale)

        val context = newBase?.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    //Apply preferences based on Shared preferences values
    private fun setPreferences() {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(baseContext)
        applyKeepScreenOpenPreference(
            sharedPref.getBoolean(getString(R.string.key_keep_screen_open), false)
        )

        val screenOrientation = sharedPref.getString(
            getString(R.string.key_screen_orientation),
            getString(R.string.preferences_screen_orientation_portrait_value)
        )
        requestedOrientation = ScreenOrientationPreference.requestedOrientation(screenOrientation)
    }

    internal fun applyKeepScreenOpenPreference(enabled: Boolean) {
        keepScreenOpen = enabled
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            holdCurrentScreenBrightness()
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            releaseScreenBrightnessOverride()
        }
    }

    override fun onResume() {
        super.onResume()
        if (keepScreenOpen) {
            holdCurrentScreenBrightness()
        }
    }

    override fun onPause() {
        releaseScreenBrightnessOverride()
        super.onPause()
    }

    private fun holdCurrentScreenBrightness() {
        val systemBrightness = Settings.System.getInt(
            contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            -1
        ).takeIf { it >= 0 }
        val brightness = ScreenBrightnessOverride.fromSystemSetting(systemBrightness) ?: return

        window.attributes = window.attributes.apply {
            screenBrightness = brightness
        }
    }

    private fun releaseScreenBrightnessOverride() {
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPreferences()

        //Initialize singletons
        ARDFRepository.initialize(applicationContext)
        DataProcessor.initialize(applicationContext)
        DebugLog.initialize(applicationContext)
        DebugLog.info("App", "MainActivity created")
        dataProcessor = DataProcessor.get()
        dataProcessor.fileProcessor = FileProcessor(WeakReference(this))
        refreshStoredResultsForCurrentScoringRules()


        val filter = IntentFilter()
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        registerReceiver(usbDetachReceiver, filter)

        // Set the usb device
        detectSIReader()

        if (intent != null) {
            val device: UsbDevice? = intent.parcelableExtraCompat(UsbManager.EXTRA_DEVICE)
            if (device != null) {
                dataProcessor.connectDevice(device)
            }
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: NavigationBarView = findViewById(R.id.nav_view)
        siStatusTextView = binding.siStatusView

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        navView.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.navigation_categories, R.id.navigation_competitors, R.id.navigation_readouts,
                R.id.navigation_results, R.id.categoryEditDialogFragment, R.id.competitorEditDialogFragment,
                R.id.readoutEditDialogFragment, R.id.resultsShareDialogFragment
                    -> {
                    navView.visibility = View.VISIBLE
                    siStatusTextView.visibility = View.VISIBLE
                }

                R.id.raceSelectionFragment, R.id.readoutDetailFragment -> {
                    navView.visibility = View.GONE
                    siStatusTextView.visibility = View.VISIBLE
                }

                else -> {
                    navView.visibility = View.GONE
                    siStatusTextView.visibility = View.GONE
                }
            }
        }

        //Set the notification channel
        setNotificationChannel()

        //Set the observer for the SI text view
        setStationObserver()
        handleOpenedSeriesArchive(intent)
    }

    private fun refreshStoredResultsForCurrentScoringRules() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        if (sharedPreferences.getInt(KEY_RESULTS_SCORING_REVISION, 0) >= RESULTS_SCORING_REVISION) {
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                dataProcessor.updateAllResults("scoring-revision-$RESULTS_SCORING_REVISION")
                sharedPreferences.edit()
                    .putInt(KEY_RESULTS_SCORING_REVISION, RESULTS_SCORING_REVISION)
                    .apply()
            }.onFailure { error ->
                DebugLog.error(
                    "Results",
                    "Stored result recalculation failed reason=scoring-revision-$RESULTS_SCORING_REVISION error=${error.message}"
                )
            }
        }
    }

    private fun detectSIReader() {
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val deviceList = usbManager.deviceList
        DebugLog.debug("USB", "Scanning ${deviceList.size} attached USB devices")
        for (device in deviceList.values) {
            if (usbManager.hasPermission(device)) {
                DebugLog.info("USB", "Connecting already-permitted device ${device.vendorId}:${device.productId}")
                dataProcessor.connectDevice(device)
            } else {
                DebugLog.debug("USB", "Attached device lacks permission ${device.vendorId}:${device.productId}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbDetachReceiver)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        if (intent != null) {
            setIntent(intent)
            val device: UsbDevice? = intent.parcelableExtraCompat(UsbManager.EXTRA_DEVICE)
            if (device != null) {
                DebugLog.info("USB", "USB attach intent for device ${device.vendorId}:${device.productId}")
                dataProcessor.connectDevice(device)
            }
            handleOpenedSeriesArchive(intent)
        }
    }

    private fun handleOpenedSeriesArchive(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        val displayName = runCatching {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment
        val contentType = runCatching { contentResolver.getType(uri) }.getOrNull()
        if (!EventFileTransferPayloads.isSeriesPackage(displayName, contentType)) return
        if (consumedSeriesArchiveUri == uri.toString()) return
        consumedSeriesArchiveUri = uri.toString()

        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val imported = dataProcessor.importEventSeriesPackage(uri)
                        ?: throw IllegalArgumentException(getString(R.string.event_series_import_invalid))
                    dataProcessor.saveEventSeriesImport(imported)
                    imported
                }
            }.onSuccess { imported ->
                Toast.makeText(
                    this@MainActivity,
                    getString(
                        R.string.event_series_import_success,
                        imported.series.name,
                        imported.memberImports.size
                    ),
                    Toast.LENGTH_LONG
                ).show()
            }.onFailure { error ->
                SoundProcessor.makeErrorSound(this@MainActivity)
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(R.string.event_series_import_title)
                    .setMessage(error.message ?: getString(R.string.event_series_import_invalid))
                    .setPositiveButton(R.string.general_ok, null)
                    .show()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == SIConstants.NOTIFICATION_PERMISSION_CODE &&
            permissions.contains(android.Manifest.permission.POST_NOTIFICATIONS) &&
            grantResults.isNotEmpty() &&
            grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            setNotificationChannel()
        }
    }

    private fun requestNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                return true
            } else {
                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    SIConstants.NOTIFICATION_PERMISSION_CODE
                )
                return false
            }
        }
        return true
    }

    private fun setNotificationChannel() {
        if (requestNotificationPermission()) {
            val channel = NotificationChannel(
                SIConstants.NOTIFICATION_CHANNEL_ID,
                SIConstants.NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            )

            val notificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun setStationObserver() {
        val siObserver = Observer<AppState> { newState ->
            val enteredErrorState = newState.siReaderState.status == SIReaderStatus.ERROR &&
                lastSiReaderStatus != SIReaderStatus.ERROR
            showSiStationModeWarningIfNeeded(newState)
            when (newState.siReaderState.status) {
                SIReaderStatus.CONNECTED -> {
                    val stationModeCode = newState.siReaderState.stationModeCode
                    val stationModeLabel = stationModeCode?.let(SportIdentStationMode::labelForModeCode)
                    val hasReadoutModeWarning =
                        stationModeCode != null && !SportIdentStationMode.isReadoutModeCode(stationModeCode)

                    //Check if race is set
                    if (newState.currentRace != null) {

                        if (hasReadoutModeWarning && newState.siReaderState.stationId != null && stationModeLabel != null) {
                            siStatusTextView.text =
                                getString(
                                    R.string.si_station_wrong_mode_status,
                                    newState.siReaderState.stationId,
                                    stationModeLabel
                                )
                        } else if (newState.siReaderState.stationId != null) {
                            siStatusTextView.text =
                                getString(R.string.si_connected, newState.siReaderState.stationId)
                        } else {
                            siStatusTextView.text = getString(R.string.si_connected)
                        }
                    }
                    //Race not selected - warn user
                    else {
                        val readiness = SIReadoutReadinessRules.evaluate(
                            readerState = newState.siReaderState,
                            hasSelectedRace = false
                        )
                        if (hasReadoutModeWarning && newState.siReaderState.stationId != null && stationModeLabel != null) {
                            siStatusTextView.text =
                                getString(
                                    R.string.si_station_wrong_mode_status,
                                    newState.siReaderState.stationId,
                                    stationModeLabel
                                )
                        } else if (newState.siReaderState.stationId != null) {
                            siStatusTextView.text =
                                getString(
                                    R.string.si_connected_but_no_race,
                                    newState.siReaderState.stationId!!
                                )
                        } else {
                            siStatusTextView.text =
                                getString(R.string.si_connected_but_no_race_no_station)
                        }
                        DebugLog.warn("SI", "Readout not ready: ${readiness.reason} ${readiness.message}")
                    }
                }

                SIReaderStatus.DISCONNECTED -> {
                    lastSiStationModeWarningKey = null
                    siStatusTextView.setText(R.string.si_disconnected)
                }

                SIReaderStatus.READING -> {
                    if (newState.siReaderState.stationId != null &&
                        newState.siReaderState.cardId != null
                    ) {
                        siStatusTextView.text =
                            getString(
                                R.string.si_reading,
                                newState.siReaderState.stationId!!,
                                newState.siReaderState.cardId!!
                            )
                    } else {
                        siStatusTextView.text = getString(R.string.si_reading)
                    }
                }

                SIReaderStatus.ERROR -> {
                    if (enteredErrorState) SoundProcessor.makeErrorSound(this@MainActivity)
                    if (newState.siReaderState.stationId != null && newState.siReaderState.cardId != null) {
                        siStatusTextView.text =
                            getString(
                                R.string.si_card_error,
                                newState.siReaderState.stationId!!,
                                newState.siReaderState.cardId!!
                            )
                    } else {
                        siStatusTextView.text = getString(R.string.si_card_error)
                    }
                }

                SIReaderStatus.CARD_READ -> {
                    if (newState.siReaderState.stationId != null && newState.siReaderState.cardId != null) {
                        siStatusTextView.text =
                            getString(
                                R.string.si_card_read,
                                newState.siReaderState.stationId!!,
                                newState.siReaderState.cardId!!
                            )
                    } else {
                        siStatusTextView.text = getString(R.string.si_card_read)
                    }
                }
            }
            lastSiReaderStatus = newState.siReaderState.status
        }
        DataProcessor.get().currentState.observe(this, siObserver)
    }

    private fun showSiStationModeWarningIfNeeded(newState: AppState) {
        if (newState.siReaderState.status == SIReaderStatus.DISCONNECTED) {
            return
        }
        val stationModeCode = newState.siReaderState.stationModeCode ?: return
        if (SportIdentStationMode.isReadoutModeCode(stationModeCode)) {
            return
        }
        val stationId = newState.siReaderState.stationId ?: return
        val stationModeLabel = SportIdentStationMode.labelForModeCode(stationModeCode)
        val warningKey = "$stationId:$stationModeCode"
        if (warningKey == lastSiStationModeWarningKey) {
            return
        }
        lastSiStationModeWarningKey = warningKey

        AlertDialog.Builder(this)
            .setTitle(R.string.si_station_wrong_mode_title)
            .setMessage(getString(R.string.si_station_wrong_mode_message, stationId, stationModeLabel))
            .setPositiveButton(R.string.general_ok, null)
            .show()
    }
}

private inline fun <reified T : Parcelable> Intent.parcelableExtraCompat(name: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(name, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(name) as? T
    }
