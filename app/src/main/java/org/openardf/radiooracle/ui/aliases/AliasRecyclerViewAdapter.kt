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

package org.openardf.radiooracle.ui.aliases

import android.content.Context
import android.text.InputFilter
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.RecyclerView
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.room.entity.Alias
import org.openardf.radiooracle.backend.wrappers.AliasEditItemWrapper
import org.openardf.radiooracle.shared.alias.AliasRules
import org.openardf.radiooracle.shared.alias.AliasValidationResult
import java.util.UUID

class AliasRecyclerViewAdapter(
    var values: ArrayList<AliasEditItemWrapper>,
    val raceId: UUID
) :
    RecyclerView.Adapter<AliasRecyclerViewAdapter.AliasViewHolder>() {
    private val boundHolders = mutableSetOf<AliasViewHolder>()

    init {
        sortAliases()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AliasViewHolder {
        val adapterLayout = LayoutInflater.from(parent.context)
            .inflate(R.layout.recycler_item_alias, parent, false)

        return AliasViewHolder(adapterLayout)
    }

    override fun getItemCount(): Int = values.size

    override fun onBindViewHolder(holder: AliasViewHolder, position: Int) {
        val item = values[position]
        holder.nameTextWatcher?.let { holder.name.removeTextChangedListener(it) }
        holder.codeTextWatcher?.let { holder.siCode.removeTextChangedListener(it) }
        holder.boundItem = item
        boundHolders += holder
        holder.siCode.setText(item.codeDraft)
        // Imported aliases may be longer than the limits for newly authored aliases.
        holder.name.filters = arrayOf(InputFilter.LengthFilter(maxOf(AliasRules.MAX_NAME_LENGTH, item.originalName.length)))
        holder.name.setText(item.nameDraft)
        refreshValidation()

        holder.nameTextWatcher = holder.name.doOnTextChanged { cs, _, _, _ ->
            item.nameDraft = cs.toString()
            refreshValidation()
        }
        holder.codeTextWatcher = holder.siCode.doOnTextChanged { cs, _, _, _ ->
            item.codeDraft = cs.toString()
            refreshValidation()
        }
        // Sorting on focus loss moves rows underneath an in-progress edit. Sort on open/save.
        holder.siCode.onFocusChangeListener = null

        holder.addBtn.setOnClickListener {
            val currentPosition = holder.currentPositionOrNull() ?: return@setOnClickListener
            addAlias(currentPosition)
        }

        holder.deleteBtn.setOnClickListener {
            //Remove focus to prevent crash
            holder.name.clearFocus()
            holder.siCode.clearFocus()
            val currentPosition = holder.currentPositionOrNull() ?: return@setOnClickListener
            deleteAlias(currentPosition)
        }
    }

    override fun onViewRecycled(holder: AliasViewHolder) {
        holder.nameTextWatcher?.let(holder.name::removeTextChangedListener)
        holder.codeTextWatcher?.let(holder.siCode::removeTextChangedListener)
        holder.boundItem = null
        boundHolders.remove(holder)
        super.onViewRecycled(holder)
    }

    private fun refreshValidation() {
        val codes = values.map { it.codeDraft.toIntOrNull() ?: 0 }
        val names = values.map { it.nameDraft }
        values.forEachIndexed { index, item ->
            val codeResult = AliasRules.validateCode(item.codeDraft, codes, index)
            val nameResult = if (item.nameDraft.isNotBlank() && item.nameDraft == item.originalName) {
                // Preserve existing/imported and standard numeric aliases; still check duplicates.
                if (names.withIndex().any { it.index != index && it.value == item.nameDraft }) {
                    AliasValidationResult.Duplicate
                } else AliasValidationResult.Valid
            } else AliasRules.validateName(item.nameDraft, names, index)
            item.isCodeValid = codeResult == AliasValidationResult.Valid
            item.isNameValid = nameResult == AliasValidationResult.Valid
            if (item.isCodeValid) item.alias.siCode = item.codeDraft.toInt()
            if (item.isNameValid) item.alias.name = item.nameDraft
            boundHolders.filter { it.boundItem === item }.forEach { holder ->
                holder.siCode.error = codeResult.takeUnless { item.isCodeValid }?.toMessage(holder.itemView.context)
                holder.name.error = nameResult.takeUnless { item.isNameValid }?.toMessage(holder.itemView.context)
            }
        }
    }

    fun checkFields(): Boolean {
        refreshValidation()
        return values.all { it.isNameValid && it.isCodeValid }
    }

    fun getSortedAliases(): List<Alias> {
        sortAliases()
        return AliasEditItemWrapper.getAliases(values)
    }

    private fun AliasValidationResult.toMessage(context: Context): String {
        return when (this) {
            AliasValidationResult.Valid -> ""
            AliasValidationResult.Required -> context.getString(R.string.general_required)
            AliasValidationResult.Invalid -> context.getString(R.string.general_invalid)
            AliasValidationResult.Duplicate -> context.getString(R.string.general_duplicate)
        }
    }

    fun addAlias(position: Int) {
        val aliasWrapper = AliasEditItemWrapper(
            Alias(
                UUID.randomUUID(),
                raceId,
                0,
                ""
            ),
            isCodeValid = false, isNameValid = false
        )

        if (position == values.size - 1) {
            values.add(aliasWrapper)
        } else {
            values.add(position + 1, aliasWrapper)
        }
        notifyItemInserted(position + 1)
    }


    private fun deleteAlias(position: Int) {
        if (position in 0 until values.size) {
            values.removeAt(position)
            notifyItemRemoved(position)
            refreshValidation()
        }
    }

    fun addStandardAliases(international: Boolean) {
        val standard = ArrayList<AliasEditItemWrapper>()

        standard.add(AliasEditItemWrapper(Alias(UUID.randomUUID(), raceId, 31, "1"), true, true))
        standard.add(AliasEditItemWrapper(Alias(UUID.randomUUID(), raceId, 32, "2"), true, true))
        standard.add(AliasEditItemWrapper(Alias(UUID.randomUUID(), raceId, 33, "3"), true, true))
        standard.add(AliasEditItemWrapper(Alias(UUID.randomUUID(), raceId, 34, "4"), true, true))
        standard.add(AliasEditItemWrapper(Alias(UUID.randomUUID(), raceId, 35, "5"), true, true))
        standard.add(AliasEditItemWrapper(Alias(UUID.randomUUID(), raceId, 36, "S"), true, true))
        standard.add(
            AliasEditItemWrapper(
                Alias(
                    UUID.randomUUID(),
                    raceId,
                    41,
                    if (international) "F1" else "R1"
                ), true, true
            )
        )
        standard.add(
            AliasEditItemWrapper(
                Alias(
                    UUID.randomUUID(),
                    raceId,
                    42,
                    if (international) "F2" else "R2"
                ), true, true
            )
        )
        standard.add(
            AliasEditItemWrapper(
                Alias(
                    UUID.randomUUID(),
                    raceId,
                    43,
                    if (international) "F3" else "R3"
                ), true, true
            )
        )
        standard.add(
            AliasEditItemWrapper(
                Alias(
                    UUID.randomUUID(),
                    raceId,
                    44,
                    if (international) "F4" else "R4"
                ), true, true
            )
        )
        standard.add(
            AliasEditItemWrapper(
                Alias(
                    UUID.randomUUID(),
                    raceId,
                    45,
                    if (international) "F5" else "R5"
                ), true, true
            )
        )

        values = standard
        sortAliases()
        notifyDataSetChanged()
    }

    private fun sortAliases() {
        values.sortWith(
            compareBy<AliasEditItemWrapper> {
                if (it.isCodeValid && it.alias.siCode > 0) it.alias.siCode else Int.MAX_VALUE
            }.thenBy { it.alias.name }
        )
    }

    class AliasViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var boundItem: AliasEditItemWrapper? = null
        var siCode: EditText = view.findViewById(R.id.alias_item_code)
        var name: EditText = view.findViewById(R.id.alias_item_name)
        var addBtn: ImageButton = view.findViewById(R.id.alias_item_add_btn)
        var deleteBtn: ImageButton =
            view.findViewById(R.id.alias_item_delete_btn)
        var nameTextWatcher: TextWatcher? = null
        var codeTextWatcher: TextWatcher? = null

        fun currentPositionOrNull(): Int? =
            bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }
    }
}
