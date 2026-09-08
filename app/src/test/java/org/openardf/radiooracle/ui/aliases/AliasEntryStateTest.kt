package org.openardf.radiooracle.ui.aliases

import android.app.Activity
import android.os.Looper
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.room.entity.Alias
import org.openardf.radiooracle.backend.wrappers.AliasEditItemWrapper
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AliasEntryStateTest {
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: AliasRecyclerViewAdapter

    @Before
    fun setup() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setTheme(R.style.Theme_RadioOracle)
        recycler = RecyclerView(activity).apply {
            layoutManager = LinearLayoutManager(activity)
            itemAnimator = null
        }
        activity.setContentView(recycler)
        adapter = AliasRecyclerViewAdapter(arrayListOf(
            AliasEditItemWrapper(Alias(31, "F1"), true, true),
            AliasEditItemWrapper(Alias(32, "F2"), true, true)
        ), UUID.randomUUID())
        recycler.adapter = adapter
        layout()
    }

    private fun layout() {
        shadowOf(Looper.getMainLooper()).idle()
        recycler.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1800, View.MeasureSpec.EXACTLY))
        recycler.layout(0, 0, 1080, 1800)
    }

    private fun holder(position: Int) = recycler.findViewHolderForAdapterPosition(position) as AliasRecyclerViewAdapter.AliasViewHolder

    @Test
    fun invalidDraftSurvivesFocusChangeAndRebinding() {
        val original = adapter.values[0]
        val row = holder(0)
        row.siCode.requestFocus()
        row.siCode.setText("3")
        row.name.setText("")
        holder(1).name.requestFocus()
        layout()
        assertSame(original, adapter.values[0])
        adapter.onBindViewHolder(row, 0)
        assertEquals("3", row.siCode.text.toString())
        assertEquals("", row.name.text.toString())
        assertFalse(adapter.checkFields())
        row.siCode.setText("33")
        row.name.setText("F3")
        assertTrue(adapter.checkFields())
        assertNull(row.siCode.error)
        assertNull(row.name.error)
    }

    @Test
    fun duplicateErrorsClearOnBothRowsWhenConflictIsCorrected() {
        holder(1).siCode.setText("31")
        assertFalse(adapter.checkFields())
        assertNotNull(holder(0).siCode.error)
        holder(1).siCode.setText("33")
        assertTrue(adapter.checkFields())
        assertNull(holder(0).siCode.error)
        assertNull(holder(1).siCode.error)
        holder(1).name.setText("F1")
        assertFalse(adapter.checkFields())
        holder(1).name.setText("F3")
        assertTrue(adapter.checkFields())
        assertNull(holder(0).name.error)
    }

    @Test
    fun existingNumericAndMultiwordAliasesAreNotRejectedOrTruncated() {
        adapter.values[0] = AliasEditItemWrapper(Alias(31, "1"), true, true)
        adapter.values[1] = AliasEditItemWrapper(Alias(32, "Spectator control"), true, true)
        adapter.notifyDataSetChanged()
        layout()
        assertEquals("1", holder(0).name.text.toString())
        assertEquals("Spectator control", holder(1).name.text.toString())
        holder(1).siCode.setText("33")
        assertTrue(adapter.checkFields())
        assertEquals("Spectator control", adapter.values[1].alias.name)
    }

    @Test
    fun recycledViewDoesNotWriteBackIntoItsPreviousRow() {
        val original = adapter.values[0]
        val row = holder(0)
        adapter.onViewRecycled(row)
        adapter.onBindViewHolder(row, 1)
        row.name.setText("F3")
        assertEquals("F1", original.nameDraft)
        assertEquals("F3", adapter.values[1].nameDraft)
    }
}
