package org.openardf.radiooracle.desktop

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openardf.radiooracle.shared.files.CsvFormatGuide

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DesktopCsvFormatPanel(guide: CsvFormatGuide, onSaveTemplate: ((CsvFormatGuide) -> String?)? = null) {
    var details by remember(guide.id) { mutableStateOf(false) }
    var templateStatus by remember(guide.id) { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    Column(Modifier.fillMaxWidth().border(1.dp, DesktopPalette.LightGrey).padding(12.dp)
        .testTag("csv-format-${guide.id}-${if (guide.importable) "import" else "export"}"),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("CSV format — ${guide.title}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(guide.organization, fontSize = 12.sp)
        Text(guide.orderRule, fontSize = 12.sp)
        Text(if (guide.includesHeader) "Header" else "Column order (no header in the file)", fontWeight = FontWeight.Bold)
        SelectionContainer { Text(guide.headerRow, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
        Text("Example row", fontWeight = FontWeight.Bold)
        SelectionContainer { Text(guide.exampleRow, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { clipboard.setText(AnnotatedString(guide.headerRow)) }) {
                Text(if (guide.includesHeader) "Copy header" else "Copy column order")
            }
            if (guide.importable && onSaveTemplate != null) TextButton(onClick = {
                templateStatus = runCatching { onSaveTemplate(guide) }.getOrElse { "Template save failed: ${it.message}" }
            }) {
                Text("Save template…")
            }
            TextButton(onClick = { details = !details }) { Text(if (details) "Hide field details" else "Show field details") }
        }
        templateStatus?.let { Text(it, fontSize = 12.sp) }
        if (details) {
            guide.columns.forEachIndexed { index, column ->
                val requirement = if (!guide.importable) "" else if (column in guide.requiredColumns) " (required)" else ""
                Text("${index + 1}. $column$requirement — ${guide.description(column)}", fontSize = 12.sp)
            }
            guide.notes.forEach { Text(it, fontSize = 12.sp) }
            if (guide.alternatives.isNotEmpty()) Text("Other accepted layouts", fontWeight = FontWeight.Bold)
            guide.alternatives.forEach { DesktopCsvFormatPanel(it, onSaveTemplate) }
        }
    }
}

internal fun saveDesktopCsvTemplate(guide: CsvFormatGuide): String? =
    DesktopFileDialogs.chooseExportCsv("Save ${guide.title} template", guide.title, "template")?.let {
        java.nio.file.Files.writeString(it, guide.template)
        "Saved ${it.fileName} — header only; add your data rows before importing."
    }
