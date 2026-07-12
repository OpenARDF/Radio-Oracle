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

package org.openardf.radiooracle.desktop.printing

import java.awt.image.BufferedImage
import java.awt.print.PageFormat
import java.awt.print.Paper
import java.awt.print.Printable
import javax.print.DocFlavor
import org.junit.Assert.assertEquals
import org.junit.Test

class JavaxDesktopPrinterBackendTest {
    @Test
    fun preservesPlainTextTransportWhenPrinterSupportsIt() {
        val mode = selectDesktopPrintMode { flavor ->
            flavor == DocFlavor.INPUT_STREAM.TEXT_PLAIN_UTF_8 ||
                flavor == DocFlavor.SERVICE_FORMATTED.PRINTABLE
        }

        assertEquals(DesktopPrintMode.PLAIN_TEXT, mode)
    }

    @Test
    fun usesNativePrintableTransportWhenPlainTextIsUnavailable() {
        val mode = selectDesktopPrintMode { flavor ->
            flavor == DocFlavor.SERVICE_FORMATTED.PRINTABLE
        }

        assertEquals(DesktopPrintMode.PRINTABLE, mode)
    }

    @Test
    fun rejectsPrinterThatSupportsNeitherTicketTransport() {
        assertEquals(null, selectDesktopPrintMode { false })
    }

    @Test
    fun printableNormalizesLineEndingsAndPaginatesWithoutDroppingLines() {
        val printable = DesktopPlainTextPrintable("One\r\nTwo\rThree")

        assertEquals(listOf("One", "Two", "Three"), printable.lines)
        assertEquals(listOf("One", "Two"), printable.linesForPage(pageIndex = 0, linesPerPage = 2))
        assertEquals(listOf("Three"), printable.linesForPage(pageIndex = 1, linesPerPage = 2))
        assertEquals(emptyList<String>(), printable.linesForPage(pageIndex = 2, linesPerPage = 2))
    }

    @Test
    fun printableRendersThroughGraphicsPath() {
        val image = BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        val pageFormat = PageFormat().apply {
            paper = Paper().apply {
                setSize(300.0, 300.0)
                setImageableArea(10.0, 10.0, 280.0, 280.0)
            }
        }

        try {
            val printable = DesktopPlainTextPrintable("Radio-Oracle\nFinish 00:42:17")
            assertEquals(Printable.PAGE_EXISTS, printable.print(graphics, pageFormat, 0))
            assertEquals(Printable.NO_SUCH_PAGE, printable.print(graphics, pageFormat, 1))
        } finally {
            graphics.dispose()
        }
    }
}
