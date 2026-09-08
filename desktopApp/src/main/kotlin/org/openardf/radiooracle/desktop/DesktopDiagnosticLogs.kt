package org.openardf.radiooracle.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openardf.radiooracle.shared.diagnostics.DiagnosticReport
import java.awt.Desktop
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object DesktopDiagnosticLogs {
    fun export(target: Path) {
        DesktopDebugLog.info("Diagnostics", "User requested diagnostic log export")
        val entries = DiagnosticReport.entries(DesktopDebugLog.snapshot(),
            "${System.getProperty("os.name")} ${System.getProperty("os.version")} ${System.getProperty("os.arch")}; Java ${System.getProperty("java.version")}",
            DesktopBuildInfo.displayVersion, DesktopBuildInfo.buildDateUtc, Instant.now().toString())
        val temporary = Files.createTempFile(target.toAbsolutePath().parent, ".radio-oracle-logs-", ".zip")
        try {
            ZipOutputStream(Files.newOutputStream(temporary).buffered()).use { zip ->
                entries.forEach { (name, data) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(data)
                    zip.closeEntry()
                }
            }
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}

@Composable
internal fun DesktopDiagnosticLogsDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Diagnostic Logs") },
        text = {
            Column(Modifier.width(520.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Export a diagnostic ZIP and attach it to your support message. Include what happened, the approximate time, and the SI card number. Export soon after a problem, before older logs rotate out.")
                Text(DiagnosticReport.PRIVACY_NOTICE)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(enabled = !busy, onClick = {
                        val target = DesktopFileDialogs.chooseExportDiagnosticLogs() ?: return@Button
                        busy = true
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { DesktopDiagnosticLogs.export(target) }
                                status = "Saved $target. Attach this ZIP to your support message."
                            } catch (error: Exception) {
                                status = "Could not export logs: ${error.message}"
                                DesktopDebugLog.error("Diagnostics", "Export failed: ${error.javaClass.simpleName}")
                            } finally {
                                busy = false
                            }
                        }
                    }) { Text(if (busy) "Exporting…" else "Export ZIP…") }
                    OutlinedButton(enabled = !busy, onClick = {
                        runCatching {
                            val directory = DesktopDebugLog.logDirectory()
                            Files.createDirectories(directory)
                            Desktop.getDesktop().open(directory.toFile())
                        }.onFailure { status = "Log folder: ${DesktopDebugLog.logDirectory()} (${it.message})" }
                    }) { Text("Open Log Folder") }
                }
                if (status.isNotEmpty()) Text(status)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Close") } }
    )
}
