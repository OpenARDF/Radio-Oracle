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

package org.openardf.radiooracle.backend.sportident

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Observer
import com.felhr.usbserial.UsbSerialDevice
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.AppState
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.logging.DebugLog
import org.openardf.radiooracle.backend.sportident.SIConstants.SI_PRODUCT_ID
import org.openardf.radiooracle.backend.sportident.SIConstants.SI_VENDOR_ID
import org.openardf.radiooracle.shared.device.SIReaderState
import org.openardf.radiooracle.shared.device.SIReaderStatus
import org.openardf.radiooracle.shared.sportident.SportIdentStationBackupSnapshot
import kotlinx.coroutines.Job


class SIReaderService :
    Service() {
    private var dataProcessor = DataProcessor.get()
    private var device: UsbDevice? = null
    private var connection: UsbDeviceConnection? = null
    private var serialDevice: UsbSerialDevice? = null
    private var siPort: SIPort? = null
    private var siJob: Job? = null
    private var observer: Observer<AppState>? = null
    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    inner class LocalBinder : Binder() {
        suspend fun inspectTimeSyncStation(): AndroidSportIdentTimeSyncInspection =
            requirePort().inspectTimeSyncStation()

        suspend fun syncTime(
            writeEnabled: Boolean,
            putStationToSleepAfterSync: Boolean,
            expectedStationSerialNumber: Int
        ): AndroidSportIdentTimeSyncResult =
            requirePort().syncTime(
                writeEnabled,
                putStationToSleepAfterSync,
                expectedStationSerialNumber
            )

        suspend fun readStationBackup(
            onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }
        ): SportIdentStationBackupSnapshot =
            requirePort().readStationBackup(onProgress)

        private fun requirePort(): SIPort =
            siPort ?: error("Connect a SPORTident download station before using station tools.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val usbDevice: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(USB_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(USB_DEVICE)
        }

        if (usbDevice != null) {
            when (intent?.action) {
                ReaderServiceActions.START.toString() -> startService(usbDevice, this)
                ReaderServiceActions.STOP.toString() -> stopService(usbDevice)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun startService(newDevice: UsbDevice, context: Context) {
        if (newDevice.vendorId == SI_VENDOR_ID && newDevice.productId == SI_PRODUCT_ID) {
            if (
                SIReaderServiceStartRules.shouldReuseActiveReader(
                    activeDeviceId = device?.deviceId,
                    requestedDeviceId = newDevice.deviceId,
                    hasPort = siPort != null,
                    jobActive = siJob?.isActive == true
                )
            ) {
                DebugLog.info(
                    "SI",
                    "Reader service already active for SPORTident device " +
                        "${newDevice.vendorId}:${newDevice.productId}; reusing connection"
                )
                return
            }

            if (device != null || siPort != null || serialDevice != null || connection != null) {
                DebugLog.warn("SI", "Replacing stale SPORTident reader connection")
                releaseReaderResources(updateReaderState = true)
            }
            DebugLog.info("SI", "Reader service accepted SPORTident device ${newDevice.vendorId}:${newDevice.productId}")
            device = newDevice
            startSIDevice()

            // Ensure notification channel exists (required on O+)
            createNotificationChannel(context)

            val notification =
                NotificationCompat.Builder(context, SIConstants.NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_sportident)
                    .setContentTitle(getString(R.string.si_ready))
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .build()

            startForeground(NOTIFICATION_ID, notification)
            setNotificationObserver()
        } else {
            DebugLog.warn("SI", "Ignoring unsupported USB device ${newDevice.vendorId}:${newDevice.productId}")
        }
    }

    private fun stopService(removedDevice: UsbDevice) {
        if (
            removedDevice.vendorId == SI_VENDOR_ID &&
            removedDevice.productId == SI_PRODUCT_ID &&
            device?.deviceId == removedDevice.deviceId
        ) {
            DebugLog.info("SI", "Reader service stopping for SPORTident device ${removedDevice.vendorId}:${removedDevice.productId}")
            releaseReaderResources(updateReaderState = true)
            stopForeground(STOP_FOREGROUND_REMOVE)
            DebugLog.info("SI", "Reader service stopped")
        }
    }

    override fun onDestroy() {
        releaseReaderResources(updateReaderState = true)
        super.onDestroy()
    }

    private fun releaseReaderResources(updateReaderState: Boolean) {
        siJob?.cancel()
        siJob = null

        observer?.let(dataProcessor.currentState::removeObserver)
        observer = null

        runCatching { serialDevice?.close() }
        serialDevice = null
        siPort = null

        runCatching { connection?.close() }
        connection = null
        device = null

        if (updateReaderState) {
            dataProcessor.updateReaderState(
                SIReaderState(
                    SIReaderStatus.DISCONNECTED,
                    null,
                    null,
                    null
                )
            )
        }
    }

    private fun startSIDevice() {
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        connection = usbManager.openDevice(device)
        serialDevice = UsbSerialDevice.createUsbSerialDevice(device, connection)
        DebugLog.info("SI", "USB serial device created")
        siPort = SIPort(serialDevice!!)

        //Start the work on the SI reader
        siJob = siPort!!.workJob()
        siJob!!.start()
        DebugLog.info("SI", "SI reader work job started")
    }

    private fun createNotificationChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Use a readable name for the channel; reuse existing string resource
        val channel = NotificationChannel(
            SIConstants.NOTIFICATION_CHANNEL_ID,
            getString(R.string.si_ready),
            NotificationManager.IMPORTANCE_LOW
        )
        nm.createNotificationChannel(channel)
    }

    private fun setNotificationObserver() {

        observer = Observer { newState ->

            val lastCardString =
                if (newState.siReaderState.lastCard != null) {
                    newState.siReaderState.lastCard.toString()
                } else {
                    getString(R.string.no_cards_yet)
                }
            val notification = NotificationCompat.Builder(this, SIConstants.NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_sportident)
                .setContentTitle(getString(R.string.si_ready))
                .setContentText(getString(R.string.si_last_card, lastCardString))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()

            // Update the existing foreground notification instead of posting a new one
            startForeground(NOTIFICATION_ID, notification)
        }

        dataProcessor.currentState.observeForever(observer!!)
    }

    enum class ReaderServiceActions {
        START,
        STOP
    }

    companion object {
        const val USB_DEVICE = "USB_DEVICE"
        private const val NOTIFICATION_ID = 1
    }
}

internal object SIReaderServiceStartRules {
    fun shouldReuseActiveReader(
        activeDeviceId: Int?,
        requestedDeviceId: Int,
        hasPort: Boolean,
        jobActive: Boolean
    ): Boolean =
        activeDeviceId == requestedDeviceId && hasPort && jobActive
}
