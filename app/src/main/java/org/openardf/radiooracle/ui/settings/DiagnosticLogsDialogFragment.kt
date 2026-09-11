package org.openardf.radiooracle.ui.settings

import android.app.Dialog
import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.logging.DebugLog
import org.openardf.radiooracle.backend.logging.DiagnosticLogExport
import org.openardf.radiooracle.shared.diagnostics.DiagnosticReport

class DiagnosticLogsDialogFragment : DialogFragment() {
    private var busy = false
    private val saveZip = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) runAction {
            val context = requireContext().applicationContext
            withContext(Dispatchers.IO) {
                val file = DiagnosticLogExport.create(context)
                requireNotNull(context.contentResolver.openOutputStream(uri)).use { output ->
                    file.inputStream().use { it.copyTo(output) }
                }
            }
            Toast.makeText(context, R.string.diagnostic_logs_saved, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val padding = (20 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, 0, padding, 0)
            addView(TextView(context).apply { text = getString(R.string.diagnostic_logs_description) + "\n\n" + DiagnosticReport.PRIVACY_NOTICE })
            addView(Button(context).apply {
                setText(R.string.diagnostic_logs_share)
                setOnClickListener { shareLogs() }
            })
            addView(Button(context).apply {
                setText(R.string.diagnostic_logs_save)
                setOnClickListener { if (!busy) saveZip.launch("Radio-Oracle-logs.zip") }
            })
        }
        return MaterialAlertDialogBuilder(context).setTitle(R.string.diagnostic_logs_title)
            .setView(ScrollView(context).apply { isScrollbarFadingEnabled = false; addView(content) }).setNegativeButton(android.R.string.cancel, null).create()
    }

    private fun shareLogs() = runAction {
        val context = requireContext().applicationContext
        val file = withContext(Dispatchers.IO) { DiagnosticLogExport.create(context) }
        if (!isAdded) return@runAction
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.diagnostics", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_SUBJECT, "Radio-Oracle diagnostic logs")
            putExtra(Intent.EXTRA_TEXT, DiagnosticReport.SHARE_MESSAGE)
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("Radio-Oracle diagnostic logs", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.diagnostic_logs_share)))
    }

    private fun runAction(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        lifecycleScope.launch {
            try {
                action()
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                DebugLog.error("Diagnostics", "Log action failed: ${error.javaClass.simpleName}: ${error.message}")
                context?.let { Toast.makeText(it, R.string.diagnostic_logs_error, Toast.LENGTH_LONG).show() }
            } finally {
                busy = false
            }
        }
    }
}
