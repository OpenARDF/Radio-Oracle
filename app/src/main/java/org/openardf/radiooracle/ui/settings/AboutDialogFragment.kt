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

package org.openardf.radiooracle.ui.settings

import android.app.Dialog
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import org.openardf.radiooracle.R

/** Dialog showing app version and project links. */
class AboutDialogFragment : DialogFragment() {

    /** Builds the about dialog from its custom layout. */
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view: View = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_about_app, null)

        val appVersionTextView: TextView = view.findViewById(R.id.tv_version)
        val appLinkTextView: TextView = view.findViewById(R.id.tv_link)

        val context = requireContext()
        val packageManager = context.packageManager
        val versionName = packageManager.getPackageInfo(context.packageName, 0).versionName

        appVersionTextView.text = context.getString(R.string.about_app_version, versionName)
        appLinkTextView.movementMethod = LinkMovementMethod.getInstance()

        return AlertDialog.Builder(requireContext())
            .setTitle("About")
            .setView(view)
            .setPositiveButton("OK", null)
            .create()
    }
}
