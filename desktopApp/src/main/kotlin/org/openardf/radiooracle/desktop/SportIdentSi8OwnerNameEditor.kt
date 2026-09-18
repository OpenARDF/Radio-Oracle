package org.openardf.radiooracle.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.openardf.radiooracle.shared.sportident.SportIdentCardOwnerInspection
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameProblem
import org.openardf.radiooracle.shared.sportident.SportIdentSi8OwnerNamePlanner
import org.openardf.radiooracle.shared.sportident.SportIdentSi8OwnerNamePreview

@Composable
internal fun SportIdentSi8OwnerNameEditor(
    inspection: SportIdentCardOwnerInspection,
    enabled: Boolean = true,
    canProgram: Boolean = false,
    onWrite: (SportIdentSi8OwnerNamePreview) -> Unit = {}
) {
    var firstName by remember(inspection) { mutableStateOf(inspection.holder?.firstName.orEmpty()) }
    var lastName by remember(inspection) { mutableStateOf(inspection.holder?.lastName.orEmpty()) }
    val preview = SportIdentSi8OwnerNamePlanner.preview(inspection, firstName, lastName)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Names to write", fontWeight = FontWeight.Bold)
        Row(Modifier.widthIn(max = 640.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(firstName, { firstName = it }, modifier = Modifier.weight(1f),
                label = { Text("First name") }, singleLine = true, enabled = enabled)
            OutlinedTextField(lastName, { lastName = it }, modifier = Modifier.weight(1f),
                label = { Text("Last name") }, singleLine = true, enabled = enabled)
        }
        preview.problems.forEach { problem ->
            Text(when (problem) {
                SportIdentOwnerNameProblem.CARD_NOT_READY -> "Read an SI-Card8 completely before preparing a name."
                SportIdentOwnerNameProblem.UNSUPPORTED_CHARACTERS ->
                    "Use plain letters, numbers, spaces or punctuation. Accented letters, semicolons and control characters are not supported."
                SportIdentOwnerNameProblem.TOO_LONG -> "Shorten the names to fit the combined limit. Names are never truncated automatically."
            }, color = DesktopPalette.Error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (canProgram) {
                val changed = preview.firstName != inspection.holder?.firstName.orEmpty() ||
                    preview.lastName != inspection.holder?.lastName.orEmpty()
                Button(enabled = enabled && preview.problems.isEmpty() && changed,
                    onClick = { onWrite(preview) }) { Text("Write Names") }
            }
            Text("${preview.nameCharacterCount ?: "?"} / ${SportIdentSi8OwnerNamePlanner.MAX_NAME_CHARACTERS} characters total · plain letters, numbers and punctuation")
        }
        if (preview.problems.isEmpty() && preview.firstName.isEmpty() && preview.lastName.isEmpty()) {
            Text("Writing this draft removes both stored names.")
        } else if (preview.problems.isEmpty() && (firstName != preview.firstName || lastName != preview.lastName)) {
            Text("Will write: ${preview.firstName} ${preview.lastName}. Leading and trailing spaces are removed.")
        }
        if (!canProgram) Text("Writing names is unavailable on this desktop. You can read the card and edit a preview.")
    }
}
