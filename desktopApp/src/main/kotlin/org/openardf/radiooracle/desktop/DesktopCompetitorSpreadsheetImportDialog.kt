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

package org.openardf.radiooracle.desktop

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun DesktopCompetitorSpreadsheetImportDialog(
    draft: DesktopCompetitorSpreadsheetImportDraft,
    isPreparingReview: Boolean,
    onDraftChange: (DesktopCompetitorSpreadsheetImportDraft) -> Unit,
    onChooseDifferentFile: () -> Unit,
    onContinue: () -> Unit,
    onCancel: () -> Unit
) {
    Dialog(onDismissRequest = { if (!isPreparingReview) onCancel() }) {
        Surface(
            modifier = Modifier
                .width(1040.dp)
                .heightIn(max = 820.dp),
            color = MaterialTheme.colors.surface
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Import Competitor Spreadsheet",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                FileSelectionRow(
                    draft = draft,
                    enabled = !isPreparingReview,
                    onChooseDifferentFile = onChooseDifferentFile
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    WorkbookStructureSection(
                        draft = draft,
                        enabled = !isPreparingReview,
                        onDraftChange = onDraftChange
                    )
                    CompetitorColumnsSection(
                        draft = draft,
                        enabled = !isPreparingReview,
                        onDraftChange = onDraftChange
                    )
                    CompetitionColumnsSection(
                        draft = draft,
                        enabled = !isPreparingReview,
                        onDraftChange = onDraftChange
                    )
                    SpreadsheetPreview(draft)
                    MappingValidation(draft)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "This mapping will be remembered after the review is prepared.",
                        color = Color.DarkGray,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = onCancel,
                        enabled = !isPreparingReview
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onContinue,
                        enabled = !isPreparingReview && draft.canImport
                    ) {
                        Text(if (isPreparingReview) "Preparing..." else "Continue to Review")
                    }
                }
            }
        }
    }
}

@Composable
private fun FileSelectionRow(
    draft: DesktopCompetitorSpreadsheetImportDraft,
    enabled: Boolean,
    onChooseDifferentFile: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Workbook", fontWeight = FontWeight.Bold)
            Text(
                text = draft.path.toString(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.DarkGray,
                fontSize = 13.sp
            )
        }
        Button(onClick = onChooseDifferentFile, enabled = enabled) {
            Text("Choose Different File...")
        }
    }
}

@Composable
private fun WorkbookStructureSection(
    draft: DesktopCompetitorSpreadsheetImportDraft,
    enabled: Boolean,
    onDraftChange: (DesktopCompetitorSpreadsheetImportDraft) -> Unit
) {
    MappingSection(title = "Workbook layout") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LabeledChoice(
                label = "Worksheet",
                value = draft.selectedSheetName,
                choices = draft.worksheets.map { it.name },
                enabled = enabled,
                onChoice = { onDraftChange(draft.withSheet(it)) },
                modifier = Modifier.weight(1f)
            )
            val rowChoices = draft.selectedWorksheet.rows
                .take(MaxHeaderRowChoices)
                .mapIndexed { index, row -> headerRowChoice(index, row) }
            LabeledChoice(
                label = "Header row",
                value = rowChoices.getOrNull(draft.headerRowIndex)
                    ?: "Row ${draft.headerRowIndex + 1}",
                choices = rowChoices,
                enabled = enabled,
                onChoice = { choice ->
                    rowChoices.indexOf(choice).takeIf { it >= 0 }?.let { index ->
                        onDraftChange(draft.withHeaderRow(index))
                    }
                },
                modifier = Modifier.weight(2f)
            )
        }
    }
}

@Composable
private fun CompetitorColumnsSection(
    draft: DesktopCompetitorSpreadsheetImportDraft,
    enabled: Boolean,
    onDraftChange: (DesktopCompetitorSpreadsheetImportDraft) -> Unit
) {
    MappingSection(title = "Competitor columns") {
        DesktopSpreadsheetCompetitorField.entries.chunked(2).forEach { fields ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                fields.forEach { field ->
                    SpreadsheetColumnChoice(
                        label = field.displayLabel + if (field.required) " *" else "",
                        selected = draft.mapping.column(field),
                        options = draft.columnOptions,
                        required = field.required,
                        enabled = enabled,
                        onChoice = { reference ->
                            onDraftChange(draft.withCompetitorColumn(field, reference))
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (fields.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CompetitionColumnsSection(
    draft: DesktopCompetitorSpreadsheetImportDraft,
    enabled: Boolean,
    onDraftChange: (DesktopCompetitorSpreadsheetImportDraft) -> Unit
) {
    MappingSection(title = "Race / competition columns") {
        Text(
            text = "Each mapping produces the same competitor group that the previous spreadsheet importer mapped to a Race File.",
            color = Color.DarkGray,
            fontSize = 12.sp
        )
        draft.mapping.competitions.forEachIndexed { index, competition ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFD8D8D8)),
                color = Color(0xFFFAFAFA)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextField(
                            value = competition.competitionName,
                            onValueChange = { value ->
                                onDraftChange(
                                    draft.withCompetition(index, competition.copy(competitionName = value))
                                )
                            },
                            enabled = enabled,
                            singleLine = true,
                            label = { Text("Race / competition name *") },
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { onDraftChange(draft.removeCompetition(index)) },
                            enabled = enabled
                        ) {
                            Text("Remove")
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SpreadsheetColumnChoice(
                            label = "Category / class",
                            selected = competition.categoryColumn,
                            options = draft.columnOptions,
                            required = false,
                            enabled = enabled,
                            onChoice = { reference ->
                                onDraftChange(
                                    draft.withCompetition(index, competition.copy(categoryColumn = reference))
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                        SpreadsheetColumnChoice(
                            label = "Course",
                            selected = competition.courseColumn,
                            options = draft.columnOptions,
                            required = false,
                            enabled = enabled,
                            onChoice = { reference ->
                                onDraftChange(
                                    draft.withCompetition(index, competition.copy(courseColumn = reference))
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                        SpreadsheetColumnChoice(
                            label = "Start time",
                            selected = competition.startTimeColumn,
                            options = draft.columnOptions,
                            required = false,
                            enabled = enabled,
                            onChoice = { reference ->
                                onDraftChange(
                                    draft.withCompetition(index, competition.copy(startTimeColumn = reference))
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        Button(
            onClick = { onDraftChange(draft.addCompetition()) },
            enabled = enabled
        ) {
            Text("Add Race / Competition")
        }
    }
}

@Composable
private fun SpreadsheetPreview(draft: DesktopCompetitorSpreadsheetImportDraft) {
    MappingSection(title = "Selected sheet preview") {
        val previewRows = draft.selectedWorksheet.rows
            .drop(draft.headerRowIndex)
            .take(PreviewRowCount)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            previewRows.forEachIndexed { index, row ->
                Text(
                    text = row.joinToString("  |  "),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun MappingValidation(draft: DesktopCompetitorSpreadsheetImportDraft) {
    if (draft.validationErrors.isEmpty()) {
        Text(
            text = "Mapping is complete.",
            color = Color(0xFF237A36),
            fontWeight = FontWeight.Bold
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = "Complete the following before continuing:",
                color = Color(0xFF9F1D20),
                fontWeight = FontWeight.Bold
            )
            draft.validationErrors.forEach { error ->
                Text("• $error", color = Color(0xFF9F1D20), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun MappingSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        content()
    }
}

@Composable
private fun SpreadsheetColumnChoice(
    label: String,
    selected: DesktopSpreadsheetColumnRef?,
    options: List<DesktopSpreadsheetColumnOption>,
    required: Boolean,
    enabled: Boolean,
    onChoice: (DesktopSpreadsheetColumnRef?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedOption = options.firstOrNull { it.reference == selected }
    val buttonText = when {
        selected == null -> if (required) "Not mapped" else "Not imported"
        selectedOption != null -> selectedOption.displayLabel
        else -> "Missing: ${selected.heading}"
    }
    Column(modifier = modifier) {
        Text(label, fontSize = 12.sp, color = Color.DarkGray)
        Box(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { expanded = true },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    contentColor = if (selected != null && selectedOption == null) {
                        Color(0xFF9F1D20)
                    } else {
                        MaterialTheme.colors.onSurface
                    }
                )
            ) {
                Text(buttonText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    onClick = {
                        expanded = false
                        onChoice(null)
                    }
                ) {
                    Text(if (required) "Not mapped" else "Not imported")
                }
                options.forEach { option ->
                    DropdownMenuItem(
                        onClick = {
                            expanded = false
                            onChoice(option.reference)
                        }
                    ) {
                        Text(option.displayLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun LabeledChoice(
    label: String,
    value: String,
    choices: List<String>,
    enabled: Boolean,
    onChoice: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Text(label, fontSize = 12.sp, color = Color.DarkGray)
        Box(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { expanded = true },
                enabled = enabled && choices.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                choices.forEach { choice ->
                    DropdownMenuItem(
                        onClick = {
                            expanded = false
                            onChoice(choice)
                        }
                    ) {
                        Text(choice, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

private fun headerRowChoice(index: Int, row: List<String>): String {
    val preview = row
        .filter { it.isNotBlank() }
        .take(5)
        .joinToString(" | ")
        .take(100)
    return "Row ${index + 1}: $preview"
}

private const val MaxHeaderRowChoices = 100
private const val PreviewRowCount = 6
