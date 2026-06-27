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
        holder.siCode.setText(item.alias.siCode.toString())
        holder.name.setText(item.alias.name)

        // Add a warning to newly created wrapper via + button
        if (!item.isNameValid) {
            holder.name.error = holder.itemView.context.getString(R.string.general_required)
        }

        if (!item.isCodeValid) {
            holder.siCode.error = holder.itemView.context.getString(R.string.general_required)
        }

        holder.nameTextWatcher = holder.name.doOnTextChanged { cs: CharSequence?, _, _, _ ->
            val currentPosition = holder.currentPositionOrNull() ?: return@doOnTextChanged
            try {
                nameWatcher(currentPosition, cs.toString(), holder.name.context)
            } catch (e: IllegalArgumentException) {
                holder.name.error = e.message
            }
        }

        holder.codeTextWatcher = holder.siCode.doOnTextChanged { cs: CharSequence?, _, _, _ ->
            val currentPosition = holder.currentPositionOrNull() ?: return@doOnTextChanged
            try {
                codeWatcher(currentPosition, cs.toString(), holder.name.context)
            } catch (e: IllegalArgumentException) {
                holder.siCode.error = e.message
            }
        }

        holder.siCode.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                sortAliases()
                notifyDataSetChanged()
            }
        }

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

    private fun codeWatcher(position: Int, code: String, context: Context) {
        val result = AliasRules.validateCode(
            code = code,
            existingCodes = values.map { it.alias.siCode },
            position = position
        )

        if (result == AliasValidationResult.Valid) {
            values[position].isCodeValid = true
            values[position].alias.siCode = code.toInt()
        } else {
            values[position].isCodeValid = false
            throw IllegalArgumentException(result.toMessage(context))
        }
    }

    private fun nameWatcher(position: Int, name: String, context: Context) {
        val result = AliasRules.validateName(
            name = name,
            existingNames = values.map { it.alias.name },
            position = position
        )

        if (result == AliasValidationResult.Valid) {
            values[position].isNameValid = true
            values[position].alias.name = name
        } else {
            values[position].isNameValid = false
            throw IllegalArgumentException(result.toMessage(context))
        }
    }

    fun checkFields(): Boolean = values.all { a -> a.isNameValid && a.isCodeValid }

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
