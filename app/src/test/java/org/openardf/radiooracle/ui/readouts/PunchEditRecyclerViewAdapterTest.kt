package org.openardf.radiooracle.ui.readouts

import android.app.Activity
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.room.entity.Alias
import org.openardf.radiooracle.backend.room.entity.Punch
import org.openardf.radiooracle.backend.room.enums.SIRecordType
import org.openardf.radiooracle.backend.sportident.SITime
import org.openardf.radiooracle.backend.wrappers.PunchEditItemWrapper
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PunchEditRecyclerViewAdapterTest {
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: PunchEditRecyclerViewAdapter

    @Before
    fun setup() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setTheme(R.style.Theme_RadioOracle)
        recycler = RecyclerView(activity).apply {
            layoutManager = LinearLayoutManager(activity)
            itemAnimator = null
        }
        activity.setContentView(recycler)
        adapter = PunchEditRecyclerViewAdapter(
            arrayListOf(row(SIRecordType.START, 36000), row(SIRecordType.FINISH, 40000)),
            listOf(Alias(31, "1"), Alias(42, "Fox 2"), Alias(136, "B"))
        ) { adapter.refreshSemanticTimeErrors() }
        recycler.adapter = adapter
        layout()
    }

    private fun row(type: SIRecordType, seconds: Long) = PunchEditItemWrapper(
        Punch(0, SITime(seconds), type, 0), true, true, true, true
    )

    private fun layout() {
        shadowOf(Looper.getMainLooper()).idle()
        recycler.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1800, View.MeasureSpec.EXACTLY))
        recycler.layout(0, 0, 1080, 1800)
    }

    private fun holder(position: Int) =
        recycler.findViewHolderForAdapterPosition(position) as PunchEditRecyclerViewAdapter.PunchViewHolder

    private fun addAfter(position: Int) {
        holder(position).addBtn.performClick()
        layout()
    }

    @Test
    fun newRowsAreBlankAndRequireBothControlAndTime() {
        holder(0).weekday.setText("3")
        holder(0).week.setText("2")
        addAfter(0)
        assertEquals("", holder(1).code.text.toString())
        assertEquals("", holder(1).time.text.toString())
        assertEquals("3", holder(1).weekday.text.toString())
        assertEquals("2", holder(1).week.text.toString())
        assertFalse(adapter.isValid())
        holder(1).code.setText("?")
        holder(1).code.setText("")
        assertFalse(adapter.values[1].isCodeValid)
        assertEquals(listOf(0, 1, 2), adapter.values.map { it.punch.order })
    }

    @Test
    fun timeFirstKeepsEditingUntilTheStationIsEntered() {
        addAfter(0)
        val punch = holder(1)
        punch.time.requestFocus()
        punch.time.setText("")
        val input = punch.time.onCreateInputConnection(EditorInfo())!!
        "101234".forEach { input.commitText(it.toString(), 1) }
        punch.time.onEditorAction(EditorInfo.IME_ACTION_NEXT)
        assertTrue(punch.code.hasFocus())
        assertEquals(3, adapter.itemCount)
        assertEquals("101234", punch.time.text.toString())
        punch.code.setText("fox2")
        assertEquals(42, adapter.values[1].punch.siCode)
        assertEquals(36754L, adapter.values[1].punch.siTime.getSeconds())
        assertTrue(adapter.isValid())
    }

    @Test
    fun numericAliasesAndRawStationNumbersBothWork() {
        addAfter(0)
        holder(1).code.setText("1")
        assertEquals(31, adapter.values[1].punch.siCode)
        holder(1).code.setText("132")
        assertEquals(132, adapter.values[1].punch.siCode)
        holder(1).code.setText("B")
        assertEquals(136, adapter.values[1].punch.siCode)
    }

    @Test
    fun unfinishedTextStaysWithItsRowAcrossInsertDeleteAndRecycling() {
        addAfter(0)
        val original = adapter.values[1]
        holder(1).code.setText("Fo")
        holder(1).time.setText("10:1")
        addAfter(0)
        assertSame(original, adapter.values[2])
        assertEquals("Fo", holder(2).code.text.toString())
        assertEquals("10:1", holder(2).time.text.toString())
        holder(1).deleteBtn.performClick()
        layout()
        assertSame(original, adapter.values[1])
        assertEquals("10:1", holder(1).time.text.toString())
        val reused = holder(1)
        adapter.onViewRecycled(reused)
        adapter.onBindViewHolder(reused, 0)
        reused.time.setText("095959")
        assertEquals("10:1", original.timeDraft)
        assertEquals(35999L, adapter.values[0].punch.siTime.getSeconds())
    }

    @Test
    fun changingTimingWarningsDoesNotRebindTheTimeBeingTyped() {
        addAfter(0)
        holder(1).code.setText("1")
        val input = holder(1).time
        input.requestFocus()
        input.setText("09:59")
        input.setSelection(2)
        adapter.refreshSemanticTimeErrors()
        layout()
        assertEquals("09:59", input.text.toString())
        assertEquals(2, input.selectionStart)
        assertTrue(input.hasFocus())
        input.setText("10:12:34")
        layout()
        assertEquals("10:12:34", input.text.toString())
        assertNull(input.error)
    }
}
