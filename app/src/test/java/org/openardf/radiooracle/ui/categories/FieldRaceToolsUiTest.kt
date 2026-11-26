package org.openardf.radiooracle.ui.categories

import android.app.AlertDialog
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.descendants
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.room.ARDFRepository
import org.openardf.radiooracle.backend.room.entity.*
import org.openardf.radiooracle.backend.room.entity.embeddeds.*
import org.openardf.radiooracle.ui.SelectedRaceViewModel
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w320dp-h480dp", fontScale = 1.6f)
class FieldRaceToolsUiTest {
    private suspend fun awaitUi(predicate: () -> Boolean) = withTimeout(10_000) {
        while (true) {
            shadowOf(Looper.getMainLooper()).idle()
            if (predicate()) break
            delay(10)
        }
    }

    @Test fun reviewAndSwitchFromTheCategoriesToolbar() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        DataProcessor.resetForTests()
        ARDFRepository.resetForTests()
        context.deleteDatabase("event-database")
        ARDFRepository.initialize(context)
        DataProcessor.initialize(context)
        val processor = DataProcessor.get()
        val first = Race().copy(name = "Morning practice")
        val second = Race().copy(name = "Afternoon practice")
        val cat = Category("Short").copy(raceId = first.id, idealOrder = "1")
        val point = ControlPoint(31).copy(categoryId = cat.id)
        processor.saveRaceData(RaceData(first, listOf(CategoryData(cat, listOf(point), emptyList())),
            emptyList(), emptyList(), emptyList()))
        ARDFRepository.get().courseProtection.update(first.id, false, "test-password", true)
        processor.createRace(second)
        val series = processor.createEventSeriesFromRace(first.id, "Training")
        processor.addRaceToEventSeries(second.id, series.series.seriesId)

        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        controller.get().setTheme(R.style.Theme_RadioOracle)
        val activity = controller.setup().get()
        try {
            val fragment = CategoryFragment()
            activity.supportFragmentManager.beginTransaction().add(android.R.id.content, fragment).commitNow()
            val model = ViewModelProvider(activity)[SelectedRaceViewModel::class.java]
            model.setRace(first.id)
            val view = fragment.requireView()
            val toolbar = view.findViewById<Toolbar>(R.id.category_toolbar)
            val review = view.findViewById<Button>(R.id.category_review_mismatches)
            awaitUi { toolbar.title == first.name && toolbar.menu.findItem(R.id.series_switch_race).isVisible }
            assertFalse(review.isEnabled)

            toolbar.menu.performIdentifierAction(R.id.category_menu_station, 0)
            awaitUi { ShadowAlertDialog.getLatestAlertDialog()?.isShowing == true }
            val controls = ShadowAlertDialog.getLatestAlertDialog().listView
            controls.performItemClick(controls.getChildAt(0), 0, 0)
            val edit = ShadowAlertDialog.getLatestAlertDialog()
            val scroll = (edit.window!!.decorView as ViewGroup).descendants.filterIsInstance<ScrollView>()
                .first { it.descendants.filterIsInstance<EditText>().any() }
            val fields = scroll.descendants.filterIsInstance<EditText>().toList()
            fields.first().setText("49")
            scroll.fullScroll(View.FOCUS_DOWN)
            fields.last().requestFocus()
            fields.last().setText("test-password")
            edit.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            awaitUi { ShadowAlertDialog.getLatestAlertDialog() !== edit }
            val confirm = ShadowAlertDialog.getLatestAlertDialog()
            assertEquals(31, processor.getControlPointsByCategory(cat.id).single().siCode)
            confirm.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            withTimeout(5_000) {
                while (processor.getControlPointsByCategory(cat.id).single().siCode != 49) {
                    shadowOf(Looper.getMainLooper()).idle()
                    delay(10)
                }
            }

            processor.createOrUpdateCompetitor(Competitor().copy(raceId = first.id, categoryId = null))
            awaitUi { review.isEnabled }
            review.performClick()
            val options = ShadowAlertDialog.getLatestAlertDialog()
            assertTrue(options.listView.adapter.getItem(0).toString().contains("course data"))
            assertFalse(options.listView.isItemChecked(0))
            assertFalse(options.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)

            // A card-routed selection uses the same model as manual switching. An old review must close.
            model.setRace(second.id)
            awaitUi { toolbar.title == second.name && !options.isShowing }
            assertNotNull(processor.getCategory(cat.id))
            toolbar.menu.performIdentifierAction(R.id.series_switch_race, 0)
            val popup = shadowOf(context).latestPopupWindow
            val content = popup.contentView
            val list = if (content is ListView) content else (content as ViewGroup).descendants.filterIsInstance<ListView>().first()
            val index = (0 until list.count).single { list.getItemAtPosition(it).toString() == first.name }
            list.performItemClick(list.getChildAt(index), index, list.getItemIdAtPosition(index))
            awaitUi { toolbar.title == first.name && review.isEnabled }

            review.performClick()
            val removal = ShadowAlertDialog.getLatestAlertDialog()
            removal.listView.performItemClick(removal.listView.getChildAt(0), 0, 0)
            removal.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            awaitUi { review.visibility == View.GONE }
            assertNull(processor.getCategory(cat.id))
        } finally {
            controller.pause().stop().destroy()
            ARDFRepository.resetForTests()
        }
    }
}
