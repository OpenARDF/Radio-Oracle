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
import org.openardf.radiooracle.backend.sounds.SoundProcessor
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
        SoundProcessor.makeErrorSound(fragment.requireContext())
        AlertDialog.Builder(fragment.requireContext())
            .setTitle(R.string.general_unknown_error)
            .setMessage(message)
            .setPositiveButton(R.string.general_ok, null)
            .show()
    }
}
