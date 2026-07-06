/*
 * MIT License
 *
 * Copyright (c) 2025 Pavel Kolský
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.openardf.radiooracle.ui.categories

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
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
import org.openardf.radiooracle.backend.room.entity.embeddeds.ControlPointAlias
import org.openardf.radiooracle.backend.room.entity.embeddeds.CategoryData
import org.openardf.radiooracle.shared.course.ControlPointDisplayToken
import org.openardf.radiooracle.shared.course.ControlPointRules
import org.openardf.radiooracle.shared.domain.ControlPointType
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
        holder.type.text = dataProcessor.raceTypeToString(currentRaceType())

        holder.band.text = dataProcessor.raceBandToString(currentRaceBand())
        holder.gender.text = categoryGenderAgeText(item)
        holder.siCodes.text = getDisplayControlPoints(item, currentRaceType())

        holder.maxAge.visibility = View.GONE

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

    private fun getDisplayControlPoints(item: CategoryData, raceType: RaceType): CharSequence {
        if (raceType == RaceType.ORIENTEERING) {
            return item.category.controlPointsString
        }

        return runBlocking {
            coloredControlPointAliases(dataProcessor.getControlPointAliasesByCategory(item.category.id))
        }
    }

    private fun coloredControlPointAliases(controlPoints: List<ControlPointAlias>): CharSequence {
        val useAliases = ControlPointsHelper.shouldUseAliases(context)
        val builder = SpannableStringBuilder()
        controlPoints.forEachIndexed { index, controlPointAlias ->
            if (index > 0) {
                builder.append(" ")
            }
            val start = builder.length
            builder.append(controlPointAlias.displayToken(useAliases))
            builder.setSpan(
                BackgroundColorSpan(controlPointAlias.controlPoint.type.roleBackgroundColor()),
                start,
                builder.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return builder
    }

    private fun ControlPointAlias.displayToken(useAliases: Boolean): String =
        ControlPointRules.formatDisplayTokens(
            listOf(
                ControlPointDisplayToken(
                    siCode = controlPoint.siCode,
                    aliasName = alias?.name
                )
            ),
            useAliases
        )

    private fun ControlPointType.roleBackgroundColor(): Int {
        val colorRes = when (this) {
            ControlPointType.CONTROL -> R.color.control_role_fox_background
            ControlPointType.SEPARATOR, ControlPointType.BEACON -> R.color.control_role_special_background
        }
        return ContextCompat.getColor(context, colorRes)
    }

    private fun currentRaceType(): RaceType =
        selectedRaceViewModel.getCurrentRace()?.raceType ?: RaceType.CLASSIC

    private fun currentRaceBand(): RaceBand =
        selectedRaceViewModel.getCurrentRace()?.raceBand ?: RaceBand.M80

    private fun categoryGenderAgeText(item: CategoryData): String {
        val gender = dataProcessor.genderToString(item.category.isMan)
        val age = displayCategoryAge(item)
        return if (age == null) {
            gender
        } else {
            "$gender $age"
        }
    }

    private fun displayCategoryAge(item: CategoryData): Int? {
        val nameAge = categoryNameAge(item.category.name)
        val maxAge = item.category.maxAge
        return when {
            maxAge == null -> nameAge
            maxAge >= OPEN_ENDED_CATEGORY_MAX_AGE && nameAge != null -> nameAge
            else -> maxAge
        }
    }

    private fun categoryNameAge(name: String): Int? =
        CATEGORY_NAME_AGE_PATTERN.find(name.trim())?.groupValues?.get(1)?.toIntOrNull()

    private fun categoryBackgroundColor(isMan: Boolean): Int {
        val colorRes = if (isMan) {
            R.color.category_men_background
        } else {
            R.color.category_women_background
        }
        return ContextCompat.getColor(context, colorRes)
    }

    private companion object {
        const val OPEN_ENDED_CATEGORY_MAX_AGE = 200
        val CATEGORY_NAME_AGE_PATTERN = Regex("^[A-Za-z]\\s*-?\\s*(\\d{1,3})")
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
