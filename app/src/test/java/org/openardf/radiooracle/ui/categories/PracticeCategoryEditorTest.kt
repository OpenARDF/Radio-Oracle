package org.openardf.radiooracle.ui.categories

import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.room.ARDFRepository
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.room.entity.gender
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.event.CategoryGender
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PracticeCategoryEditorTest {
    @Test
    fun practiceDialogInfersStandardNamesAndSavesCustomCourseAsOpen() {
        val context = RuntimeEnvironment.getApplication()
        DataProcessor.resetForTests()
        ARDFRepository.resetForTests()
        ARDFRepository.initialize(context)
        DataProcessor.initialize(context)
        val processor = DataProcessor.get()
        val race = Race().copy(raceLevel = RaceLevel.PRACTICE)
        runBlocking { processor.createRace(race) }
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        controller.get().setTheme(R.style.Theme_RadioOracle)
        val activity = controller.setup().get()
        try {
            val editor = CategoryEditDialogFragment().apply {
                arguments = CategoryEditDialogFragmentArgs.Builder(true, 0, null, "", race).build().toBundle()
            }
            editor.showNow(activity.supportFragmentManager, "category")
            val view = editor.requireView()
            val name = view.findViewById<EditText>(R.id.category_dialog_name)
            val picker = view.findViewById<MaterialAutoCompleteTextView>(R.id.category_gender)
            name.setText("W21")
            assertEquals(context.getString(R.string.general_gender_woman), picker.text.toString())
            name.setText("M50")
            assertEquals(context.getString(R.string.general_gender_man), picker.text.toString())
            name.setText("Long")
            assertEquals(context.getString(R.string.category_gender_open), picker.text.toString())
            view.findViewById<android.view.View>(R.id.category_dialog_ok).performClick()

            // The real Save handler writes on the IO dispatcher. Wait for Room to publish it.
            val saved = runBlocking { withTimeout(5000) {
                processor.getCategoryDataFlowForRace(race.id).first { it.isNotEmpty() }.single().category
            } }
            assertEquals("Long", saved.name)
            assertEquals(CategoryGender.OPEN, saved.gender)
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
