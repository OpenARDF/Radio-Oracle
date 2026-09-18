package org.openardf.radiooracle.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.OutlinedTextField
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

@Composable
internal fun SportIdentSi8OwnerNameEditor(inspection: SportIdentCardOwnerInspection) {
    var firstName by remember(inspection) { mutableStateOf(inspection.holder?.firstName.orEmpty()) }
    var lastName by remember(inspection) { mutableStateOf(inspection.holder?.lastName.orEmpty()) }
    val preview = SportIdentSi8OwnerNamePlanner.preview(inspection, firstName, lastName)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Prepare SI-Card8 Owner Name", fontWeight = FontWeight.Bold)
        Text("Edit the names to preview a replacement. Nothing is written to the card.")
        OutlinedTextField(firstName, { firstName = it }, label = { Text("First name") }, singleLine = true)
        OutlinedTextField(lastName, { lastName = it }, label = { Text("Last name") }, singleLine = true)
        Text("First and last names can contain ${SportIdentSi8OwnerNamePlanner.MAX_NAME_CHARACTERS} characters in total.")
        preview.problems.forEach { problem ->
            Text(when (problem) {
                SportIdentOwnerNameProblem.CARD_NOT_READY -> "Read an SI-Card8 completely before preparing a name."
                SportIdentOwnerNameProblem.UNSUPPORTED_CHARACTERS ->
                    "Use basic Latin letters, numbers, spaces or punctuation. Semicolons, accented letters and control characters are not supported in this preview."
                SportIdentOwnerNameProblem.TOO_LONG -> "Shorten the names to fit the combined limit. Names are never truncated automatically."
            }, color = DesktopPalette.Error)
        }
        preview.nameCharacterCount?.let {
            Text("Name characters: $it / ${SportIdentSi8OwnerNamePlanner.MAX_NAME_CHARACTERS}")
        }
        if (preview.encodedOwnerText != null) {
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Replacement preview for SI-Card ${preview.siNumber}", fontWeight = FontWeight.Bold)
                    Text("First name: ${preview.firstName.ifEmpty { "Not stored" }}")
                    Text("Last name: ${preview.lastName.ifEmpty { "Not stored" }}")
                    if (preview.firstName.isEmpty() && preview.lastName.isEmpty()) {
                        Text("This draft removes both stored names.")
                    }
                    if (firstName != preview.firstName || lastName != preview.lastName) {
                        Text("Leading and trailing spaces are removed in this preview.")
                    }
                }
            }
        }
    }
}
