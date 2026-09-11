package org.openardf.radiooracle.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/** All alert bodies can grow; reserve the title and actions before measuring the scroll viewport. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DesktopAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier.widthIn(min = 320.dp, max = 560.dp).heightIn(max = 720.dp)
                .testTag("desktop-alert"),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                title?.let { ProvideTextStyle(MaterialTheme.typography.subtitle1, it) }
                text?.let { body ->
                    DesktopWorkspaceScroll(Modifier.weight(1f, fill = false).fillMaxWidth()) {
                        ProvideTextStyle(MaterialTheme.typography.body2, body)
                    }
                }
                FlowRow(
                    Modifier.fillMaxWidth().testTag("desktop-alert-actions"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}
