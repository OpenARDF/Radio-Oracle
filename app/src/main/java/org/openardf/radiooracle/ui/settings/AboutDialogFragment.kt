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
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import org.openardf.radiooracle.BuildConfig
import org.openardf.radiooracle.R

/** Dialog showing app version and project links. */
class AboutDialogFragment : DialogFragment() {

    private val projectUrl: String by lazy { getString(R.string.about_app_project_url) }
    private val licenseUrl: String by lazy { getString(R.string.about_app_license_url) }

    /** Builds the about dialog from its custom layout. */
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view: View = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_about_app, null)

        val context = requireContext()
        val packageManager = context.packageManager
        val versionName = packageManager.getPackageInfo(context.packageName, 0).versionName

        view.findViewById<TextView>(R.id.tv_app_version).text =
            context.getString(R.string.about_app_version, versionName)
        view.findViewById<TextView>(R.id.tv_build_date).text =
            context.getString(R.string.about_app_build_date, BuildConfig.BUILD_DATE_UTC)
        view.findViewById<TextView>(R.id.tv_platform).setText(R.string.about_app_platform)
        view.findViewById<TextView>(R.id.tv_project).setLinkedRow(
            fullText = context.getString(R.string.about_app_project, projectUrl),
            linkText = projectUrl,
            url = projectUrl
        )
        view.findViewById<TextView>(R.id.tv_license).setLinkedRow(
            fullText = context.getString(
                R.string.about_app_license,
                context.getString(R.string.about_app_license_label)
            ),
            linkText = context.getString(R.string.about_app_license_label),
            url = licenseUrl
        )
        view.findViewById<TextView>(R.id.tv_updates).setLinkedRow(
            fullText = context.getString(
                R.string.about_app_updates,
                context.getString(R.string.about_app_check_updates)
            ),
            linkText = context.getString(R.string.about_app_check_updates),
            url = projectUrl
        )

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.about_app_menu_title)
            .setView(view)
            .setPositiveButton("OK", null)
            .create()
    }

    private fun TextView.setLinkedRow(fullText: String, linkText: String, url: String) {
        val start = fullText.indexOf(linkText)
        if (start < 0) {
            text = fullText
            return
        }
        val span = SpannableString(fullText)
        span.setSpan(
            object : ClickableSpan() {
                override fun onClick(widget: View) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }

                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.color = Color.rgb(55, 0, 179)
                    ds.isUnderlineText = true
                }
            },
            start,
            start + linkText.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        text = span
        movementMethod = LinkMovementMethod.getInstance()
        highlightColor = Color.TRANSPARENT
    }
}
