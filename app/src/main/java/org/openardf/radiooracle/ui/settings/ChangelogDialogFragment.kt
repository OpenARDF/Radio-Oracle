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
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import io.noties.markwon.Markwon
import org.openardf.radiooracle.R
import java.io.BufferedReader
import java.io.InputStreamReader

/** Dialog rendering the bundled changelog Markdown file. */
class ChangelogDialogFragment : DialogFragment() {

    /** Builds the changelog dialog and renders Markdown into its text view. */
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = LayoutInflater.from(requireContext())
        val view: View = inflater.inflate(R.layout.dialog_changelog, null)

        val changelogTextView: TextView = view.findViewById(R.id.changelog_text)
        val markdown = loadChangelogFromAssets()

        val markwon = Markwon.create(requireContext())
        markwon.setMarkdown(changelogTextView, markdown)

        return AlertDialog.Builder(requireContext())
            .setTitle("Changelog")
            .setView(view)
            .setPositiveButton("Close", null)
            .create()
    }

    /** Loads CHANGELOG.md from app assets, returning a fallback message on failure. */
    private fun loadChangelogFromAssets(): String {
        return try {
            requireContext().assets.open("CHANGELOG.md").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).readText()
            }
        } catch (e: Exception) {
            "Unable to load changelog."
        }
    }
}
