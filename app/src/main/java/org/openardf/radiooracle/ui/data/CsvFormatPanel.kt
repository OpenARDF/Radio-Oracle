package org.openardf.radiooracle.ui.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isGone
import androidx.core.view.isVisible
import org.openardf.radiooracle.R
import org.openardf.radiooracle.shared.files.CsvFormatGuide

/** Android view adapter for the same guide metadata used by Compose Desktop. */
class CsvFormatPanel @JvmOverloads constructor(context: Context, attrs: android.util.AttributeSet? = null) : LinearLayout(context, attrs) {
    init {
        orientation = VERTICAL
        val padding = (12 * resources.displayMetrics.density).toInt()
        setPadding(padding, padding, padding, padding)
    }

    fun showGuide(guide: CsvFormatGuide?, saveTemplate: ((CsvFormatGuide) -> Unit)? = null) {
        removeAllViews()
        isVisible = guide != null
        if (guide == null) return
        label(context.getString(R.string.csv_format_title, guide.title), bold = true)
        label(guide.organization)
        label(guide.orderRule)
        label(
            context.getString(
                if (guide.includesHeader) R.string.csv_header
                else R.string.csv_column_order_no_header
            ),
            bold = true
        )
        label(guide.headerRow, code = true)
        label(context.getString(R.string.csv_example_row), bold = true)
        label(guide.exampleRow, code = true)
        addView(Button(context).apply {
            setText(
                if (guide.includesHeader) R.string.csv_copy_header
                else R.string.csv_copy_column_order
            )
            setOnClickListener {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(
                        ClipData.newPlainText(
                            context.getString(R.string.csv_clipboard_header_label),
                            guide.headerRow
                        )
                    )
            }
        })
        if (guide.importable && saveTemplate != null) addView(Button(context).apply {
            setText(R.string.csv_save_template)
            setOnClickListener { saveTemplate(guide) }
        })
        val details = LinearLayout(context).apply { orientation = VERTICAL; isGone = true }
        guide.columns.forEachIndexed { index, column ->
            details.addView(TextView(context).apply {
                val requirement = if (guide.importable && column in guide.requiredColumns) {
                    context.getString(R.string.csv_required_suffix)
                } else {
                    ""
                }
                text = context.getString(
                    R.string.csv_column_detail,
                    index + 1,
                    column,
                    requirement,
                    guide.description(column)
                )
                setTextIsSelectable(true)
            })
        }
        guide.notes.forEach { note -> details.addView(TextView(context).apply { text = note }) }
        if (guide.alternatives.isNotEmpty()) details.addView(TextView(context).apply {
            setText(R.string.csv_other_accepted_layouts)
        })
        guide.alternatives.forEach { alternative ->
            details.addView(CsvFormatPanel(context).apply { showGuide(alternative, saveTemplate) })
        }
        addView(Button(context).apply {
            setText(R.string.csv_show_field_details)
            setOnClickListener {
                details.isGone = details.isVisible
                setText(
                    if (details.isVisible) R.string.csv_hide_field_details
                    else R.string.csv_show_field_details
                )
            }
        })
        addView(details)
    }

    private fun label(value: String, bold: Boolean = false, code: Boolean = false) {
        addView(TextView(context).apply {
            text = value
            setTextIsSelectable(true)
            if (code) typeface = Typeface.MONOSPACE
            else if (bold) setTypeface(typeface, Typeface.BOLD)
            val padding = (4 * resources.displayMetrics.density).toInt()
            setPadding(0, padding, 0, padding)
        })
    }
}
