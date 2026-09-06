package org.openardf.radiooracle.ui

import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import de.codecrafters.tableview.SortableTableView
import de.codecrafters.tableview.toolkit.SimpleTableHeaderAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.openardf.radiooracle.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "xhdpi")
class FabListClearanceTest {
    private val screens = listOf(
        Triple(R.layout.fragment_race_selection, R.id.race_recycler_view, R.id.race_fab),
        Triple(R.layout.fragment_categories, R.id.category_recycler_view, R.id.category_btn_add),
        Triple(R.layout.fragment_competitors, de.codecrafters.tableview.R.id.table_data_view, R.id.competitor_btn_add),
        Triple(R.layout.fragment_readouts, R.id.readout_recycler_view, R.id.readout_btn_add)
    )

    @Test
    fun finalRowClearsButtonOnPhoneTabletAndLandscapeWithShortAndLongLists() {
        for (screen in screens) {
            for ((width, height) in listOf(360 to 640, 800 to 1000, 640 to 360)) {
                for (count in listOf(1, 50)) {
                    verifyFinalRow(screen, width, height, count)
                }
            }
        }
    }

    @Test
    fun clearanceTracksResizingAndLargerButtonsWithoutAccumulatingPadding() {
        for (screen in screens) {
            val (root, list, fab) = inflate(screen)
            layout(root, 360, 640)
            val original = clearance(list)
            layout(root, 800, 1000)
            assertEquals(original, clearance(list))
            (fab as FloatingActionButton).customSize = fab.height + 24
            layout(root, 800, 1000)
            assertEquals(original + 24, clearance(list))
            layout(root, 800, 1000)
            assertEquals(original + 24, clearance(list))
        }
    }

    @Test
    @Config(qualifiers = "ldrtl-xhdpi", fontScale = 1.5f)
    fun largeTextAndRightToLeftLayoutKeepFinalRowsAccessible() {
        for (screen in screens) verifyFinalRow(screen, 360, 640, 50)
    }

    private fun inflate(screen: Triple<Int, Int, Int>): Triple<ViewGroup, ViewGroup, View> {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_RadioOracle)
        val root = LayoutInflater.from(context).inflate(screen.first, null) as ViewGroup
        root.findViewById<SortableTableView<Any>>(R.id.competitor_fragment_table_view)?.let { table ->
            table.headerAdapter = SimpleTableHeaderAdapter(
                context, R.string.competitor_start_number_header, R.string.general_name,
                R.string.general_club, R.string.general_category, R.string.general_si_number,
                R.string.general_actions
            ).apply {
                setGravity(Gravity.CENTER)
                setTextSize(14)
            }
        }
        val list = root.findViewById<ViewGroup>(screen.second)
        val fab = root.findViewById<View>(screen.third)
        FabListClearance.bind(root, list, fab)
        return Triple(root, list, fab)
    }

    private fun verifyFinalRow(screen: Triple<Int, Int, Int>, width: Int, height: Int, count: Int) {
        val (root, list, fab) = inflate(screen)
        when (list) {
            is RecyclerView -> list.adapter = Rows(count)
            is ListView -> list.adapter = ArrayAdapter(
                root.context, android.R.layout.simple_list_item_1, (1..count).map { "Row $it" }
            )
        }
        layout(root, width, height)
        when (list) {
            is RecyclerView -> list.scrollBy(0, 100_000)
            is ListView -> repeat(count) { list.scrollListBy(height) }
        }
        layout(root, width, height)
        val description = "layout=${screen.first}, ${width}x$height, rows=$count"
        val listBounds = bounds(root, list)
        assertTrue("List must fit in screen: $description", listBounds.bottom <= root.height)
        assertTrue("List must have usable height: $description", list.height > list.paddingBottom)
        assertFalse(list.clipToPadding)
        if (list is ListView) assertEquals(description, count, list.lastVisiblePosition)
        val lastVisibleRow = if (list is ListView) {
            list.getChildAt(count - 1 - list.firstVisiblePosition)
        } else {
            (0 until list.childCount).map { list.getChildAt(it) }.maxBy { it.bottom }
        }
        val rowBounds = bounds(root, lastVisibleRow)
        val gap = root.resources.getDimensionPixelSize(R.dimen.fab_list_clearance_gap)
        assertTrue("Final row must clear button: $description, row=$rowBounds, list=$listBounds, fab=${bounds(root, fab)}, clearance=${clearance(list)}", rowBounds.bottom <= bounds(root, fab).top - gap)
        assertTrue("Final row must be fully visible: $description", rowBounds.top >= listBounds.top)
    }

    private fun clearance(list: ViewGroup): Int = if (list is ListView) {
        list.adapter.getView(list.count - 1, null, list).layoutParams.height
    } else list.paddingBottom

    private fun bounds(root: ViewGroup, view: View) = Rect(0, 0, view.width, view.height).also {
        root.offsetDescendantRectToMyCoords(view, it)
    }

    private fun layout(root: ViewGroup, width: Int, height: Int) {
        val density = root.resources.displayMetrics.density
        val widthPx = (width * density).toInt()
        val heightPx = (height * density).toInt()
        // Padding is calculated after the first layout and applied on the following traversal.
        repeat(3) {
            root.measure(
                View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
            )
            root.layout(0, 0, widthPx, heightPx)
        }
    }

    private class Rows(private val count: Int) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = count
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            object : RecyclerView.ViewHolder(TextView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, (64 * resources.displayMetrics.density).toInt()
                )
            }) {}

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            (holder.itemView as TextView).text = "Row $position"
        }
    }
}
