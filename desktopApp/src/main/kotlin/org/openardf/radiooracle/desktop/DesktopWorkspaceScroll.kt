package org.openardf.radiooracle.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** One bounded vertical viewport, shared by the workspace and its visible scrollbar. */
@Composable
internal fun DesktopWorkspaceScroll(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    Box(modifier) {
        Column(
            Modifier.fillMaxSize().padding(end = 16.dp)
                .testTag("workspace-scroll")
                // Page keys navigate the workspace even while a single-line editor has focus.
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || event.isAltPressed || event.isCtrlPressed || event.isMetaPressed) false
                    else when (event.key) {
                        Key.PageDown, Key.PageUp -> {
                            val delta = if (event.key == Key.PageDown) scroll.viewportSize else -scroll.viewportSize
                            scope.launch { scroll.scrollTo((scroll.value + delta).coerceIn(0, scroll.maxValue)) }
                            true
                        }
                        else -> false
                    }
                }
                // Bubble after editors, so arrows/Home/End still edit text normally.
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || event.isAltPressed || event.isCtrlPressed || event.isMetaPressed) false
                    else {
                        val target = when (event.key) {
                            Key.DirectionDown -> scroll.value + 40
                            Key.DirectionUp -> scroll.value - 40
                            Key.MoveHome -> 0
                            Key.MoveEnd -> scroll.maxValue
                            else -> return@onKeyEvent false
                        }.coerceIn(0, scroll.maxValue)
                        scope.launch { scroll.scrollTo(target) }
                        true
                    }
                }
                .focusable()
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content
        )
        VerticalScrollbar(
            rememberScrollbarAdapter(scroll),
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().testTag("workspace-scrollbar")
        )
    }
}
