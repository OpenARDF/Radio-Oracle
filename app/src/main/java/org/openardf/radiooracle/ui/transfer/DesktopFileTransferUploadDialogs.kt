package org.openardf.radiooracle.ui.transfer

import android.app.AlertDialog
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.files.DesktopFileTransferUpload
import org.openardf.radiooracle.backend.files.DesktopFileTransferUploader

object DesktopFileTransferUploadDialogs {
    fun show(fragment: Fragment, upload: DesktopFileTransferUpload) {
        AlertDialog.Builder(fragment.requireContext())
            .setTitle(R.string.event_file_send_title)
            .setMessage(R.string.event_file_send_message)
            .setPositiveButton(R.string.event_file_send_scan_qr) { _, _ ->
                scanDesktopReceiveQr(fragment, upload)
            }
            .setNeutralButton(R.string.event_file_send_enter_url) { _, _ ->
                showManualDesktopReceiveUrlDialog(fragment, upload)
            }
            .setNegativeButton(R.string.general_cancel, null)
            .show()
    }

    private fun showManualDesktopReceiveUrlDialog(fragment: Fragment, upload: DesktopFileTransferUpload) {
        val input = EditText(fragment.requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            hint = fragment.getString(R.string.event_file_send_url_hint)
            setSingleLine(true)
        }

        AlertDialog.Builder(fragment.requireContext())
            .setTitle(R.string.event_file_send_title)
            .setMessage(R.string.event_file_send_message)
            .setView(input)
            .setPositiveButton(R.string.race_send_desktop) { _, _ ->
                sendToDesktopUrl(fragment, input.text.toString(), upload)
            }
            .setNegativeButton(R.string.general_cancel, null)
            .show()
    }

    private fun scanDesktopReceiveQr(fragment: Fragment, upload: DesktopFileTransferUpload) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        val scanner = GmsBarcodeScanning.getClient(fragment.requireActivity(), options)
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val url = barcode.rawValue
                if (url.isNullOrBlank()) {
                    displayAlert(fragment, "The QR code did not contain a desktop receive URL.")
                } else {
                    sendToDesktopUrl(fragment, url, upload)
                }
            }
            .addOnFailureListener { error ->
                displayAlert(fragment, error.message ?: "QR scan failed. Enter the receive URL manually.")
            }
    }

    private fun sendToDesktopUrl(fragment: Fragment, rawUrl: String, upload: DesktopFileTransferUpload) {
        val progressDialog = AlertDialog.Builder(fragment.requireContext())
            .setTitle(R.string.event_file_send_title)
            .setMessage(R.string.event_file_send_progress)
            .setCancelable(false)
            .create()
        progressDialog.show()

        fragment.viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    DesktopFileTransferUploader().upload(rawUrl, upload)
                }
                progressDialog.dismiss()
                Toast.makeText(
                    fragment.requireContext(),
                    fragment.getString(R.string.event_file_send_success, upload.fileName),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (error: Exception) {
                progressDialog.dismiss()
                displayAlert(fragment, error.message ?: "Desktop upload failed.")
            }
        }
    }

    private fun displayAlert(fragment: Fragment, message: String) {
        AlertDialog.Builder(fragment.requireContext())
            .setTitle(R.string.general_unknown_error)
            .setMessage(message)
            .setPositiveButton(R.string.general_ok, null)
            .show()
    }
}
