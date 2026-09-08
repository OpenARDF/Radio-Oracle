package org.openardf.radiooracle.ui.sportident

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.logging.DebugLog
import org.openardf.radiooracle.shared.sportident.SportIdentUsbDevice

/** Activity-owned USB discovery. Permission is checked again before every connection. */
internal class SportIdentUsbConnection(
    private val context: Context,
    savedState: Bundle?,
    private val connect: (UsbDevice) -> Unit,
    private val detach: (UsbDevice) -> Unit,
    private val statusChanged: () -> Unit
) {
    private val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val requestedDevices = savedState?.getStringArrayList(REQUESTED_DEVICES)?.toMutableSet()
        ?: mutableSetOf()
    var resumed = false
    var disconnectedStatus = R.string.si_disconnected
        private set

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    @Suppress("DEPRECATION")
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (device != null && SportIdentUsbDevice.matches(device.vendorId, device.productId)) {
                        requestedDevices.remove(device.deviceName)
                        detach(device)
                    }
                }
                ACTION_PERMISSION -> {
                    // The immutable PendingIntent carries our device name. Do not depend on
                    // system-added extras, or trust a broadcast as proof of USB permission.
                    val name = intent.getStringExtra(DEVICE_NAME)
                    val device = manager.deviceList[name]
                    DebugLog.info("USB", "SI USB permission result granted=${device?.let(manager::hasPermission) == true}")
                }
            }
            refresh()
        }
    }

    init {
        val filter = IntentFilter(ACTION_PERMISSION).apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun refresh() {
        val devices = manager.deviceList.values.filter {
            SportIdentUsbDevice.matches(it.vendorId, it.productId)
        }
        requestedDevices.retainAll(devices.map { it.deviceName }.toSet())
        val permitted = devices.filter(manager::hasPermission)
        val missingPermission = devices.firstOrNull { !manager.hasPermission(it) }
        DebugLog.debug("USB", "Scanning ${devices.size} attached SPORTident USB devices; permitted=${permitted.size}")
        disconnectedStatus = when {
            permitted.isNotEmpty() -> R.string.si_waiting_for_station
            missingPermission != null -> R.string.si_usb_permission_missing
            else -> R.string.si_disconnected
        }
        statusChanged()
        if (resumed) permitted.forEach(connect)
        if (resumed && permitted.isEmpty() && missingPermission != null &&
            requestedDevices.add(missingPermission.deviceName)
        ) {
            requestPermission(missingPermission)
        }
    }

    fun retryPermission() {
        requestedDevices.clear()
        refresh()
    }

    private fun requestPermission(device: UsbDevice) {
        val intent = Intent(ACTION_PERMISSION).setPackage(context.packageName)
            .putExtra(DEVICE_NAME, device.deviceName)
        val result = PendingIntent.getBroadcast(
            context, device.deviceId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        DebugLog.info("USB", "Requesting USB permission for SPORTident device ${device.vendorId}:${device.productId}")
        try {
            manager.requestPermission(device, result)
        } catch (error: RuntimeException) {
            // Keep the retry action available if Android cannot show its permission dialog.
            DebugLog.error("USB", "Could not request SI USB permission: ${error.message}")
        }
    }

    fun saveState(outState: Bundle) {
        outState.putStringArrayList(REQUESTED_DEVICES, ArrayList(requestedDevices))
    }

    fun close() {
        context.unregisterReceiver(receiver)
    }

    private companion object {
        const val ACTION_PERMISSION = "org.openardf.radiooracle.USB_PERMISSION"
        const val DEVICE_NAME = "si_usb_device_name"
        const val REQUESTED_DEVICES = "si_usb_permission_requested_devices"
    }
}
