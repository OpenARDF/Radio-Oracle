package org.openardf.radiooracle.ui.categories

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import com.google.android.material.textfield.TextInputEditText
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.helpers.ControlPointsHelper
import org.openardf.radiooracle.backend.room.entity.Alias
import org.openardf.radiooracle.backend.room.enums.RaceType
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class CategoryControlPointsInputTest {
    private val context
        get() = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_RadioOracle)

    private fun editor(): TextInputEditText = LayoutInflater.from(context)
        .inflate(R.layout.dialog_edit_category, null)
        .findViewById(R.id.category_dialog_control_points)

    @Test
    fun keyboardCanEnterAliasesAndSpecialControlsThatParseToSiCodes() {
        val editor = editor()
        val input = editor.onCreateInputConnection(EditorInfo())!!
        "Fox 2 S1! B".forEach { input.commitText(it.toString(), 1) }

        assertEquals("Fox 2 S1! B", editor.text.toString())
        val raceId = UUID.randomUUID()
        val controls = ControlPointsHelper.getControlPointsFromDisplayString(
            editor.text.toString(),
            UUID.randomUUID(),
            RaceType.SPRINT,
            listOf(
                Alias(UUID.randomUUID(), raceId, 41, "Fox 2"),
                Alias(UUID.randomUUID(), raceId, 90, "S1"),
                Alias(UUID.randomUUID(), raceId, 99, "B")
            ),
            context
        )
        assertEquals("41 90! 99B", ControlPointsHelper.getStringFromControlPoints(controls))
    }

    @Test
    fun existingAliasCanBeReplacedWithoutLosingLetters() {
        val editor = editor()
        editor.setText("F1 F2 B")
        editor.setSelection(3, 5)
        editor.onCreateInputConnection(EditorInfo())!!.commitText("F3", 1)

        assertEquals("F1 F3 B", editor.text.toString())
    }

    @Test
    fun keyboardStillAcceptsRawSiCodesAndMarkers() {
        val editor = editor()
        editor.onCreateInputConnection(EditorInfo())!!.commitText("41 90! 99B", 1)

        assertEquals("41 90! 99B", editor.text.toString())
    }
}
