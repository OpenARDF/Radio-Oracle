package org.openardf.radiooracle.ui.categories

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.helpers.ControlPointsHelper
import org.openardf.radiooracle.backend.room.entity.embeddeds.CategoryData
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.ui.SelectedRaceViewModel
import kotlinx.coroutines.runBlocking

class CategoryRecyclerViewAdapter(
    private var values: List<CategoryData>,
    private val onMoreClicked: (action: Int, position: Int, categoryData: CategoryData) -> Unit,
    private val context: Context,
    private val selectedRaceViewModel: SelectedRaceViewModel
) :
    RecyclerView.Adapter<CategoryRecyclerViewAdapter.CategoryViewHolder>() {

    private val dataProcessor = DataProcessor.get()
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val adapterLayout = LayoutInflater.from(parent.context)
            .inflate(R.layout.recycler_item_category, parent, false)

        return CategoryViewHolder(adapterLayout)
    }

    override fun getItemCount(): Int {
        return values.size
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val item = values[position]
        holder.itemView.setBackgroundColor(categoryBackgroundColor(item.category.isMan))
        holder.title.text = item.category.name
        holder.numCompeititors.text =
            "(${item.competitors.size} ${
                context.getString(R.string.general_competitors).lowercase()
            })"
        holder.type.text = dataProcessor.raceTypeToString(
            item.category.raceType ?: (selectedRaceViewModel.getCurrentRace()?.raceType
                ?: RaceType.CLASSIC)
        )

        holder.band.text = dataProcessor.raceBandToString(
            item.category.categoryBand ?: (selectedRaceViewModel.getCurrentRace()?.raceBand
                ?: RaceBand.M80)
        )
        holder.gender.text = dataProcessor.genderToString(item.category.isMan)
        holder.siCodes.text = getDisplayControlPoints(item, item.category.raceType ?: currentRaceType())

        holder.maxAge.text = item.category.maxAge?.toString().orEmpty()

        holder.itemView.setOnClickListener {
            onMoreClicked(0, position, item)
        }

        holder.itemView.setOnLongClickListener {
            showContextMenu(holder.moreBtn, position, item)
            true
        }

        holder.moreBtn.setOnClickListener {
            showContextMenu(holder.moreBtn, position, item)
        }

        holder.upBtn.visibility = View.GONE
        holder.downBtn.visibility = View.GONE
    }

    private fun showContextMenu(anchor: View, position: Int, item: CategoryData) {
        val popupMenu = PopupMenu(context, anchor)
        popupMenu.inflate(R.menu.context_menu_category)

        popupMenu.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menu_item_edit_category -> {
                    onMoreClicked(0, position, item)
                    true
                }

                R.id.menu_item_duplicate_category -> {
                    onMoreClicked(1, position, item)
                    true
                }

                R.id.menu_item_delete_category -> {
                    onMoreClicked(2, position, item)
                    true
                }

                else -> {
                    false
                }
            }
        }
        popupMenu.show()
    }

    private fun getDisplayControlPoints(item: CategoryData, raceType: RaceType): String {
        if (raceType == RaceType.ORIENTEERING) {
            return item.category.controlPointsString
        }

        return runBlocking {
            ControlPointsHelper.getStringFromControlPointAliases(
                dataProcessor.getControlPointAliasesByCategory(item.category.id),
                context
            )
        }
    }

    private fun currentRaceType(): RaceType =
        selectedRaceViewModel.getCurrentRace()?.raceType ?: RaceType.CLASSIC

    private fun categoryBackgroundColor(isMan: Boolean): Int {
        val colorRes = if (isMan) {
            R.color.category_men_background
        } else {
            R.color.category_women_background
        }
        return ContextCompat.getColor(context, colorRes)
    }

    inner class CategoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var title: TextView = view.findViewById(R.id.category_item_title)
        var type: TextView = view.findViewById(R.id.category_item_type)
        var band: TextView = view.findViewById(R.id.category_item_band)
        var numCompeititors: TextView = view.findViewById(R.id.category_item_competitor_number)
        var gender: TextView = view.findViewById(R.id.category_item_gender)
        var maxAge: TextView = view.findViewById(R.id.category_item_max_age)
        var siCodes: TextView = view.findViewById(R.id.category_item_codes)
        var upBtn: ImageButton = view.findViewById(R.id.category_item_up_btn)
        var moreBtn: ImageButton = view.findViewById(R.id.category_item_more_btn)
        var downBtn: ImageButton = view.findViewById(R.id.category_item_down_btn)
    }
}
