package org.openardf.radiooracle.ui.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
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
        visibility = if (guide == null) View.GONE else View.VISIBLE
        if (guide == null) return
        label("CSV format — ${guide.title}", bold = true)
        label(guide.organization)
        label(guide.orderRule)
        label(if (guide.includesHeader) "Header" else "Column order (no header in the file)", bold = true)
        label(guide.headerRow, code = true)
        label("Example row", bold = true)
        label(guide.exampleRow, code = true)
        addView(Button(context).apply {
            text = if (guide.includesHeader) "Copy header" else "Copy column order"
            setOnClickListener {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("CSV header", guide.headerRow))
            }
        })
        if (guide.importable && saveTemplate != null) addView(Button(context).apply {
            text = "Save template…"
            setOnClickListener { saveTemplate(guide) }
        })
        val details = LinearLayout(context).apply { orientation = VERTICAL; visibility = View.GONE }
        guide.columns.forEachIndexed { index, column ->
            details.addView(TextView(context).apply {
                val requirement = if (guide.importable && column in guide.requiredColumns) " (required)" else ""
                text = "${index + 1}. $column$requirement — ${guide.description(column)}"
                setTextIsSelectable(true)
            })
        }
        guide.notes.forEach { note -> details.addView(TextView(context).apply { text = note }) }
        if (guide.alternatives.isNotEmpty()) details.addView(TextView(context).apply { text = "Other accepted layouts" })
        guide.alternatives.forEach { alternative ->
            details.addView(CsvFormatPanel(context).apply { showGuide(alternative, saveTemplate) })
        }
        addView(Button(context).apply {
            text = "Show field details"
            setOnClickListener {
                details.visibility = if (details.visibility == View.GONE) View.VISIBLE else View.GONE
                text = if (details.visibility == View.VISIBLE) "Hide field details" else "Show field details"
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
