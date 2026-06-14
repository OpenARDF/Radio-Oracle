package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopAppUpdateSupportTest {
    @Test
    fun reportsJdeployUpdateAvailableFromInjectedProperty() {
        val status = DesktopAppUpdateSupport.status("1.0.10") { key ->
            when (key) {
                "jdeploy.updatesAvailable" -> "true"
                "jdeploy.app.version" -> "1.0.10"
                "jdeploy.app.source" -> "https://github.com/OpenARDF/Radio-Oracle"
                else -> null
            }
        }

        assertTrue(status.launchedByJdeploy)
        assertEquals(true, status.jdeployUpdatesAvailable)
        assertTrue(DesktopAppUpdateSupport.shouldShowAutomaticNotice(status))
        assertTrue(DesktopAppUpdateSupport.dialogMessage(status).contains("updated version"))
        assertTrue(DesktopAppUpdateSupport.dialogMessage(status).contains(DesktopAppUpdateSupport.updatePageUrl))
    }

    @Test
    fun suppressesAutomaticNoticeWhenJdeployReportsNoUpdate() {
        val status = DesktopAppUpdateSupport.status("1.0.10") { key ->
            when (key) {
                "jdeploy.updatesAvailable" -> "false"
                "jdeploy.app.version" -> "1.0.10"
                else -> null
            }
        }

        assertTrue(status.launchedByJdeploy)
        assertEquals(false, status.jdeployUpdatesAvailable)
        assertFalse(DesktopAppUpdateSupport.shouldShowAutomaticNotice(status))
    }

    @Test
    fun treatsNonJdeployLaunchAsUnknownUpdateStatus() {
        val status = DesktopAppUpdateSupport.status("1.0.10") { null }

        assertFalse(status.launchedByJdeploy)
        assertNull(status.jdeployUpdatesAvailable)
        assertFalse(DesktopAppUpdateSupport.shouldShowAutomaticNotice(status))
        assertTrue(DesktopAppUpdateSupport.dialogMessage(status).contains("not launched by jDeploy"))
    }
}
