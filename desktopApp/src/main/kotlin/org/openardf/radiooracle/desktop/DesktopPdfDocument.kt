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

package org.openardf.radiooracle.desktop

import java.nio.charset.StandardCharsets
import java.util.Locale

/** Shared minimal PDF container for desktop exports that draw their own page content streams. */
object DesktopPdfDocument {
    const val LetterWidth = 612.0
    const val LetterHeight = 792.0

    fun bytes(
        pageContents: List<String>,
        pageWidth: Double = LetterWidth,
        pageHeight: Double = LetterHeight
    ): ByteArray {
        val safePageContents = pageContents.ifEmpty { listOf("") }
        val objects = mutableListOf<String>()
        objects += "<< /Type /Catalog /Pages 2 0 R >>"
        objects += "<< /Type /Pages /Kids ${safePageContents.indices.joinToString(separator = " ", prefix = "[", postfix = "]") { "${4 + it * 2} 0 R" }} /Count ${safePageContents.size} >>"
        objects += "<< /F1 << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> /F2 << /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >> >>"
        safePageContents.forEachIndexed { index, content ->
            val pageObjectId = 4 + index * 2
            val contentObjectId = pageObjectId + 1
            objects += "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${number(pageWidth)} ${number(pageHeight)}] /Resources << /Font 3 0 R >> /Contents $contentObjectId 0 R >>"
            val length = content.toByteArray(StandardCharsets.ISO_8859_1).size
            objects += "<< /Length $length >>\nstream\n$content\nendstream"
        }

        // Keep streams uncompressed; tests and support diagnostics can inspect labels directly.
        val output = StringBuilder("%PDF-1.4\n")
        val offsets = mutableListOf<Int>()
        objects.forEachIndexed { index, obj ->
            offsets += output.toString().toByteArray(StandardCharsets.ISO_8859_1).size
            output.append("${index + 1} 0 obj\n")
            output.append(obj)
            output.append("\nendobj\n")
        }
        val xrefOffset = output.toString().toByteArray(StandardCharsets.ISO_8859_1).size
        output.append("xref\n")
        output.append("0 ${objects.size + 1}\n")
        output.append("0000000000 65535 f \n")
        offsets.forEach { output.append(it.toString().padStart(10, '0')).append(" 00000 n \n") }
        output.append("trailer\n")
        output.append("<< /Size ${objects.size + 1} /Root 1 0 R >>\n")
        output.append("startxref\n")
        output.append(xrefOffset)
        output.append("\n%%EOF\n")
        return output.toString().toByteArray(StandardCharsets.ISO_8859_1)
    }

    fun number(value: Double): String =
        String.format(Locale.US, "%.2f", value)
}
