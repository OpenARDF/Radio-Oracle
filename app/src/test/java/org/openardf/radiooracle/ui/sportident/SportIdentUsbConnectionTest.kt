package org.openardf.radiooracle.ui.sportident

import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.Looper
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.openardf.radiooracle.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class SportIdentUsbConnectionTest {
    private val manager = mock<UsbManager>()
    private val devices = hashMapOf<String, UsbDevice>()
    private val permissions = mutableSetOf<String>()
    private val requests = mutableListOf<PendingIntent>()
    private val connected = mutableListOf<UsbDevice>()
    private val detached = mutableListOf<UsbDevice>()
    private lateinit var context: Context
    private lateinit var controller: SportIdentUsbConnection
    private lateinit var station: UsbDevice

    @Before fun setUp() {
        context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getSystemService(name: String): Any? =
                if (name == USB_SERVICE) manager else super.getSystemService(name)
        }
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(
            "${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        )
        whenever(manager.deviceList).thenAnswer { HashMap(devices) }
        whenever(manager.hasPermission(any<UsbDevice>())).thenAnswer {
            it.getArgument<UsbDevice>(0).deviceName in permissions
        }
        doAnswer { requests.add(it.getArgument(1)); null }
            .whenever(manager).requestPermission(any<UsbDevice>(), any())
        station = device("/dev/bus/usb/001/024", 4292, 32778)
        controller = createController()
    }

    @After fun tearDown() { if (::controller.isInitialized) controller.close() }

    @Test fun attachedStationRequestsAccessAndConnectsAfterApproval() {
        devices[station.deviceName] = station
        controller.refresh()
        assertEquals(R.string.si_usb_permission_missing, controller.disconnectedStatus)
        assertEquals(1, requests.size)
        assertTrue(connected.isEmpty())

        permissions.add(station.deviceName)
        requests.single().send()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(station), connected)
        assertEquals(R.string.si_waiting_for_station, controller.disconnectedStatus)
    }

    @Test fun denialAndActivityRecreationDoNotRepeatPromptButTapRetries() {
        devices[station.deviceName] = station
        controller.refresh()
        requests.single().send()
        shadowOf(Looper.getMainLooper()).idle()
        controller.refresh()
        assertEquals(1, requests.size)
        assertTrue(connected.isEmpty())

        val saved = Bundle()
        controller.saveState(saved)
        controller.close()
        controller = createController(saved)
        controller.refresh()
        assertEquals(1, requests.size)
        assertEquals(R.string.si_usb_permission_missing, controller.disconnectedStatus)

        controller.retryPermission()
        assertEquals(2, requests.size)
    }

    @Test fun detachWhilePermissionDialogIsOpenIgnoresLateApprovalAndReattachCanRequestAgain() {
        devices[station.deviceName] = station
        controller.refresh()
        devices.clear()
        broadcast(UsbManager.ACTION_USB_DEVICE_DETACHED)
        assertEquals(listOf(station), detached)
        assertEquals(R.string.si_disconnected, controller.disconnectedStatus)
        permissions.add(station.deviceName)
        requests.single().send()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(connected.isEmpty())

        permissions.clear()
        devices[station.deviceName] = station
        broadcast(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        assertEquals(2, requests.size)
    }

    @Test fun resumeFindsAlreadyPermittedStationAndUnrelatedUsbDevicesAreIgnored() {
        val other = device("/dev/bus/usb/001/025", 1234, 5678)
        devices[other.deviceName] = other
        controller.refresh()
        assertTrue(requests.isEmpty())
        assertTrue(connected.isEmpty())
        assertEquals(R.string.si_disconnected, controller.disconnectedStatus)

        controller.resumed = false
        devices[station.deviceName] = station
        broadcast(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        assertTrue(requests.isEmpty())
        permissions.add(station.deviceName)
        controller.resumed = true
        controller.refresh()
        assertEquals(listOf(station), connected)
        assertTrue(requests.isEmpty())
    }

    @Test fun permissionCallbackCannotGrantAccessByClaimingSuccess() {
        devices[station.deviceName] = station
        controller.refresh()
        requests.single().send(context, 0, Intent().putExtra(UsbManager.EXTRA_PERMISSION_GRANTED, true))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(connected.isEmpty())
        assertEquals(R.string.si_usb_permission_missing, controller.disconnectedStatus)
    }

    @Test fun approvalWhilePausedConnectsWhenReturningToApp() {
        devices[station.deviceName] = station
        controller.refresh()
        controller.resumed = false
        permissions.add(station.deviceName)
        requests.single().send()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(connected.isEmpty())

        controller.resumed = true
        controller.refresh()
        assertEquals(listOf(station), connected)
        assertEquals(1, requests.size)
    }

    private fun createController(saved: Bundle? = null) = SportIdentUsbConnection(
        context, saved, connected::add, detached::add, {}
    ).apply { resumed = true }

    private fun broadcast(action: String) {
        context.sendBroadcast(Intent(action).putExtra(UsbManager.EXTRA_DEVICE, station))
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun device(name: String, vendor: Int, product: Int): UsbDevice = mock<UsbDevice>().also {
        whenever(it.deviceName).thenReturn(name)
        whenever(it.deviceId).thenReturn(name.hashCode())
        whenever(it.vendorId).thenReturn(vendor)
        whenever(it.productId).thenReturn(product)
    }
}
