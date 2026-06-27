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

package org.openardf.radiooracle.backend.files.processors;

import android.content.Context
import org.openardf.radiooracle.shared.files.TemplateRenderer
import java.io.IOException

/** Android asset loader wrapper around the shared template renderer. */
object TemplateProcessor {

    /** Reads a template file from the app's bundled assets. */
    @Throws(IOException::class)
    fun loadTemplate(templateName: String, context: Context): String {
        val inputStream = context.assets.open(templateName)
        return inputStream.bufferedReader().use { it.readText() }
    }

    /** Replaces template placeholders with their resolved parameter values. */
    fun processTemplate(template: String, params: Map<String, String>): String {
        return TemplateRenderer.render(template, params)
    }
}
