package org.openardf.radiooracle.ui

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import com.google.android.material.textfield.TextInputEditText
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.helpers.TimeProcessor
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ManualTimeEntryLayoutsTest {
    private fun editor(layout: Int, field: Int): TextInputEditText {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_RadioOracle)
        return LayoutInflater.from(context).inflate(layout, null).findViewById(field)
    }

    @Test
    fun raceClockKeyboardDoesNotTruncateSeconds() {
        val field = editor(R.layout.dialog_edit_race, R.id.race_dialog_start_time)
        field.onCreateInputConnection(EditorInfo())!!.commitText("123456", 1)
        assertEquals("123456", field.text.toString())
        assertEquals(LocalTime.of(12, 34, 56), TimeProcessor.parseClockInput(field.text.toString()))
        field.setText("12:34:56")
        assertEquals("12:34:56", field.text.toString())
    }

    @Test
    fun competitorKeyboardSupportsCompactLongMinuteOffsets() {
        val field = editor(R.layout.dialog_edit_competitor, R.id.competitor_dialog_start_time)
        field.onCreateInputConnection(EditorInfo())!!.commitText("12345", 1)
        assertEquals("12345", field.text.toString())
        assertEquals(7425L, TimeProcessor.parseMinuteInput(field.text.toString()).seconds)
        field.setText("123:45")
        assertEquals(7425L, TimeProcessor.parseMinuteInput(field.text.toString()).seconds)
    }
}
