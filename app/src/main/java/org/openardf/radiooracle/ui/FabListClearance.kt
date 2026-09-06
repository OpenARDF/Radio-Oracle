package org.openardf.radiooracle.ui

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.ListView
import org.openardf.radiooracle.R

/** Lets the final row scroll above an overlaid add button, including in nested table lists. */
object FabListClearance {
    fun bind(root: ViewGroup, list: ViewGroup, fab: View) {
        val originalBottomPadding = list.paddingBottom
        val gap = root.resources.getDimensionPixelSize(R.dimen.fab_list_clearance_gap)
        val listBounds = Rect()
        val fabBounds = Rect()
        list.clipToPadding = false
        // ListView ignores unclipped bottom padding at the end of a drag. A disabled footer
        // supplies actual scrollable space while leaving the table header and row data intact.
        val footer = if (list is ListView) View(list.context).apply {
            layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            list.addFooterView(this, null, false)
        } else null

        fun updatePadding() {
            if (list.height == 0 || fab.height == 0) return
            listBounds.set(0, 0, list.width, list.height)
            fabBounds.set(0, 0, fab.width, fab.height)
            root.offsetDescendantRectToMyCoords(list, listBounds)
            root.offsetDescendantRectToMyCoords(fab, fabBounds)
            val bottomPadding = maxOf(originalBottomPadding, listBounds.bottom - fabBounds.top + gap)
            if (footer != null) {
                val footerHeight = bottomPadding
                if (footer.layoutParams.height != footerHeight) {
                    footer.layoutParams = footer.layoutParams.apply { height = footerHeight }
                }
            } else if (list.paddingBottom != bottomPadding) {
                list.setPadding(list.paddingLeft, list.paddingTop, list.paddingRight, bottomPadding)
            }
        }

        // Recompute after screen resizing, header changes, or a different measured FAB size.
        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updatePadding() }
        root.addOnLayoutChangeListener(listener)
        list.addOnLayoutChangeListener(listener)
        fab.addOnLayoutChangeListener(listener)
        updatePadding()
    }
}
