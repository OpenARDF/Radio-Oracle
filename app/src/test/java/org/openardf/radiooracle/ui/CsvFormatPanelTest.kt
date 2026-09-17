package org.openardf.radiooracle.ui

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.openardf.radiooracle.shared.files.CsvFormatGuide
import org.openardf.radiooracle.shared.files.CsvFormatGuides
import org.openardf.radiooracle.ui.data.CsvFormatPanel

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CsvFormatPanelTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test fun changingFormatsClearsOldHeadersAndOnlyImportsOfferTemplates() {
        val panel = CsvFormatPanel(context)
        val guide = CsvFormatGuides.competitors(true)
        var template: CsvFormatGuide? = null
        panel.showGuide(guide) { template = it }
        assertEquals(View.VISIBLE, panel.visibility)
        assertTrue(texts(panel).contains(guide.headerRow))
        assertTrue(texts(panel).contains(guide.exampleRow))
        children(panel).filterIsInstance<Button>().single { it.text == "Save template…" }.performClick()
        assertEquals(guide, template)
        panel.showGuide(CsvFormatGuides.readouts(2))
        assertFalse(texts(panel).contains(guide.headerRow))
        assertFalse(texts(panel).contains("Save template…"))
        panel.showGuide(null)
        assertEquals(View.GONE, panel.visibility)
        assertEquals(0, panel.childCount)
    }

    @Test fun fieldDetailsCanExpandAndHeaderCanBeCopied() {
        val panel = CsvFormatPanel(context)
        val guide = CsvFormatGuides.controls(true)
        panel.showGuide(guide)
        children(panel).filterIsInstance<Button>().single { it.text == "Show field details" }.performClick()
        assertTrue(texts(panel).any { it.contains("si_code (required)") })
        children(panel).filterIsInstance<Button>().single { it.text == "Copy header" }.performClick()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        assertEquals(guide.headerRow, clipboard.primaryClip!!.getItemAt(0).text.toString())
    }

    private fun children(view: android.view.ViewGroup): List<View> = (0 until view.childCount).map { view.getChildAt(it) }
        .flatMap { listOf(it) + if (it is android.view.ViewGroup) children(it) else emptyList() }
    private fun texts(view: android.view.ViewGroup) = children(view).filterIsInstance<TextView>().map { it.text.toString() }
}
