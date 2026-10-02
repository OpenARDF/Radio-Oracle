package org.openardf.radiooracle.sportident

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.openardf.radiooracle.R

/** Device-level structure checks keep each SPORTident workflow on a focused screen. */
@RunWith(AndroidJUnit4::class)
class SportIdentToolsLayoutInstrumentedTest {
    @Test
    fun toolsHomeOffersThreeFocusedDestinations() {
        val tools = inflate(R.layout.fragment_sportident_tools)
        listOf(
            R.id.sportident_tools_station,
            R.id.sportident_tools_card,
            R.id.sportident_tools_punch_history
        ).forEach { id -> assertNotNull(tools.findViewById<View>(id)) }
    }

    @Test
    fun stationScreenContainsOnlyStationMaintenanceActions() {
        val station = inflate(R.layout.fragment_sportident_station)
        listOf(
            R.id.sportident_time_sync_inspect,
            R.id.sportident_time_sync_run,
            R.id.sportident_station_sleep
        ).forEach { id -> assertNotNull(station.findViewById<View>(id)) }
        assertEquals(null, station.findViewById<View>(R.id.sportident_card_write))
    }

    @Test
    fun cardScreenContainsOnlyCardOwnerActions() {
        val card = inflate(R.layout.fragment_sportident_card)
        listOf(
            R.id.sportident_card_presence_dot,
            R.id.sportident_card_presence_status,
            R.id.sportident_card_action,
            R.id.sportident_card_write,
            R.id.sportident_card_accept_recovery
        ).forEach { id -> assertNotNull(card.findViewById<View>(id)) }
        assertEquals(null, card.findViewById<View>(R.id.sportident_backup_read))
    }

    @Test
    fun punchHistoryScreenContainsOnlyBackupActions() {
        val history = inflate(R.layout.fragment_sportident_punch_history)
        listOf(
            R.id.sportident_backup_read,
            R.id.sportident_backup_show
        ).forEach { id -> assertNotNull(history.findViewById<View>(id)) }
        assertEquals(null, history.findViewById<View>(R.id.sportident_time_sync_run))
    }

    private fun inflate(layout: Int): View {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var root: View? = null
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(
                instrumentation.targetContext,
                R.style.Theme_RadioOracle
            )
            root = LayoutInflater.from(context).inflate(
                layout,
                FrameLayout(context),
                false
            )
        }
        return requireNotNull(root).also {
            assertEquals(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                it.layoutParams.height
            )
        }
    }
}
