package org.openardf.radiooracle.ui.races

import android.app.Activity
import android.os.Looper
import android.text.method.PasswordTransformationMethod
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.openardf.radiooracle.R
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class RaceProtectionKeyboardTest {
    @Test fun passwordAddedAfterDialogOpensCanReceiveKeyboardInput() {
        val controller = Robolectric.buildActivity(Activity::class.java)
        val activity = controller.get().apply { setTheme(R.style.Theme_RadioOracle) }
        controller.setup()
        val content = LinearLayout(activity).apply { addView(TextView(activity)) }
        val dialog = MaterialAlertDialogBuilder(activity).setView(content).create()
        try {
            dialog.show()
            // Reproduce AlertDialog's automatic keyboard-blocking flag when opened without editors.
            assertTrue(dialog.window!!.attributes.flags and WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM != 0)
            val password = racePasswordInput(activity)
            content.addView(password)
            dialog.enableRacePasswordInput(password)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0, dialog.window!!.attributes.flags and WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
            assertTrue(password.hasFocus())
            assertTrue(password.transformationMethod is PasswordTransformationMethod)
            assertFalse(password.isSaveEnabled)
            assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
                dialog.window!!.attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST)
        } finally {
            dialog.dismiss()
            controller.pause().stop().destroy()
        }
    }
}
