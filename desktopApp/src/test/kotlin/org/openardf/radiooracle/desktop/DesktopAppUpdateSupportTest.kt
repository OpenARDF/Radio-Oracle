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
        assertTrue(DesktopAppUpdateSupport.dialogMessage(status).contains("not started by the desktop installer"))
        assertTrue(DesktopAppUpdateSupport.dialogMessage(status).contains("open the update page"))
    }

    @Test
    fun explainsMissingInstallerUpdateStatusWithoutImplementationJargon() {
        val status = DesktopAppUpdateSupport.status("1.0.10") { key ->
            when (key) {
                "jdeploy.app.version" -> "1.0.10"
                else -> null
            }
        }
        val message = DesktopAppUpdateSupport.dialogMessage(status)

        assertTrue(status.launchedByJdeploy)
        assertNull(status.jdeployUpdatesAvailable)
        assertTrue(message.contains("could not determine update availability"))
        assertFalse(message.contains("launched by jDeploy"))
        assertFalse(message.contains("status was not reported"))
    }

    @Test
    fun disabledDialogMessageExplainsSettingAndProvidesUpdatePage() {
        val message = DesktopAppUpdateSupport.disabledDialogMessage()

        assertTrue(message.contains("disabled in App Settings"))
        assertTrue(message.contains(DesktopAppUpdateSupport.updatePageUrl))
    }
}
