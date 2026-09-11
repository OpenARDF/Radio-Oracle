package org.openardf.radiooracle.ui

import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.openardf.radiooracle.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h240dp")
class DialogScrollLayoutTest {
    private val context get() = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_RadioOracle)

    @Test fun everyCustomDialogLayoutProvidesAReachableScrollViewport() {
        // Discover new layouts too: adding a form must not silently bypass the scrolling contract.
        Files.list(Path.of("src/main/res/layout")).use { files ->
            files.filter { it.fileName.toString().startsWith("dialog_") }.forEach { path ->
                val name = path.fileName.toString().removeSuffix(".xml")
                val resource = R.layout::class.java.getField(name).getInt(null)
                val root = inflate(resource)
                val scroll = descendants(root).filterIsInstance<ScrollView>().firstOrNull()
                assertNotNull("$name must scroll when content exceeds the window", scroll)
                measure(root)
                assertTrue("$name has no usable viewport", scroll!!.height > 0)
                assertTrue("$name viewport exceeds the screen", scroll.height <= root.height)
            }
        }
    }

    @Test fun longDeletionAndImportErrorsCanReachTheLastAction() {
        for ((layout, message, target) in listOf(
            Triple(R.layout.dialog_delete_category, R.id.delete_category_message, R.id.delete_category_checkbox),
            Triple(R.layout.dialog_delete_competitor, R.id.delete_competitor_message, R.id.delete_competitor_checkbox),
            Triple(R.layout.dialog_internet_import, R.id.internet_import_dialog_error, R.id.robis_import_dialog_ok),
            Triple(R.layout.dialog_share_results, R.id.results_error_view, R.id.results_file_cancel)
        )) {
            val root = inflate(layout) as ScrollView
            root.findViewById<TextView>(message).apply {
                text = "A long race or import explanation with details to review.\n".repeat(30)
                textSize = 24f
            }
            measure(root)
            assertTrue(root.canScrollVertically(1))
            root.scrollTo(0, root.getChildAt(0).height)
            assertVisibleWithin(root, root.findViewById(target))
            root.scrollTo(0, 0)
            assertEquals(0, root.scrollY)
        }
    }

    @Test fun readoutEditorScrollsLongWarningsAndAllPunchesWithSavePinned() {
        val root = inflate(R.layout.dialog_edit_readout) as ViewGroup
        root.findViewById<TextView>(R.id.readout_dialog_issue_explanation).apply {
            visibility = View.VISIBLE
            text = "Review this readout before saving.\n".repeat(20)
        }
        val recycler = root.findViewById<RecyclerView>(R.id.readout_dialog_punch_recycler_view)
        recycler.visibility = View.VISIBLE
        recycler.adapter = punches()
        measure(root)
        val scroll = root.findViewById<ScrollView>(R.id.readout_dialog_scroll)
        val save = root.findViewById<View>(R.id.readout_dialog_ok)
        val before = bounds(root, save)
        assertTrue(scroll.canScrollVertically(1))
        assertFalse(recycler.isNestedScrollingEnabled)
        scroll.scrollTo(0, scroll.getChildAt(0).height)
        assertVisibleWithin(root, save)
        assertVisibleWithin(root, root.findViewById(R.id.readout_dialog_cancel))
        assertEquals(before, bounds(root, save))
        val lastPunch = recycler.layoutManager!!.findViewByPosition(39)
        assertNotNull("The final punch must remain in the scrollable form", lastPunch)
        assertVisibleWithin(scroll, lastPunch!!)
    }

    @Test fun readoutDetailsCanScrollPastTheHeaderToTheLastPunch() {
        val root = inflate(R.layout.fragment_readout_detail) as ViewGroup
        val recycler = root.findViewById<RecyclerView>(R.id.readout_detail_punch_recycler_view)
        recycler.adapter = punches()
        measure(root)
        val scroll = root.findViewById<ScrollView>(R.id.readout_detail_scroll)
        val toolbar = root.findViewById<View>(R.id.readout_detail_toolbar)
        val before = bounds(root, toolbar)
        assertTrue(scroll.canScrollVertically(1))
        scroll.scrollTo(0, scroll.getChildAt(0).height)
        val last = recycler.layoutManager!!.findViewByPosition(39)
        assertNotNull(last)
        assertVisibleWithin(scroll, last!!)
        assertVisibleWithin(root, toolbar)
        assertEquals(before, bounds(root, toolbar))
    }

    private fun punches() = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = 40
        override fun onCreateViewHolder(parent: ViewGroup, type: Int) = object : RecyclerView.ViewHolder(
            TextView(parent.context).apply { layoutParams = ViewGroup.LayoutParams(-1, 64) }
        ) {}
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            (holder.itemView as TextView).text = "Control punch $position"
        }
    }

    private fun inflate(resource: Int) = LayoutInflater.from(context).inflate(resource, null)

    private fun measure(root: View) {
        root.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 360, 240)
    }

    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    private fun bounds(root: ViewGroup, view: View) = Rect(0, 0, view.width, view.height).also {
        root.offsetDescendantRectToMyCoords(view, it)
        // offsetDescendantRectToMyCoords includes intermediate scrolling, but not the root's own scroll.
        it.offset(-root.scrollX, -root.scrollY)
    }

    private fun assertVisibleWithin(root: ViewGroup, view: View) {
        val rect = bounds(root, view)
        assertTrue("Unreachable view ${view.id}: $rect in ${root.height}px", rect.top >= 0 && rect.bottom <= root.height)
    }
}
