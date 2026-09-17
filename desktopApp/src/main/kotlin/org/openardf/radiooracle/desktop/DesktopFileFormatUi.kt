package org.openardf.radiooracle.desktop

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openardf.radiooracle.shared.files.KmlFormatGuide

@Composable
internal fun DesktopFileFormatBox(tag: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().border(1.dp, DesktopPalette.LightGrey).padding(12.dp).testTag(tag),
        verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
}

@Composable
internal fun DesktopFileFormatPanels(guides: List<DesktopFileFormatGuide>) {
    guides.forEach { guide ->
        when (guide) {
            is DesktopFileFormatGuide.Csv -> DesktopCsvFormatPanel(guide.guide, ::saveDesktopCsvTemplate)
            is DesktopFileFormatGuide.Kml -> DesktopKmlFormatPanel(guide.guide)
        }
    }
}

@Composable
internal fun DesktopKmlFormatPanel(guide: KmlFormatGuide) {
    var details by remember(guide.id) { mutableStateOf(false) }
    DesktopFileFormatBox("kml-format-${guide.id}") {
        Text("KML/KMZ format", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(guide.introduction, fontSize = 12.sp)
        Text(guide.structure, fontSize = 12.sp)
        Text(guide.actionNote, fontSize = 12.sp)
        Text(guide.routeRule, fontSize = 12.sp)
        TextButton(onClick = { details = !details }) {
            Text(if (details) "Hide format details" else "Show format details")
        }
        if (details) guide.details.forEach { (property, explanation) ->
            Text("$property — $explanation", fontSize = 12.sp)
        }
    }
}
