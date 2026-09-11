package org.openardf.radiooracle.desktop

import java.awt.Dimension
import javax.swing.JScrollPane
import javax.swing.JTextArea

/** Keep variable-length native notices inside the viewport while JOptionPane retains its actions. */
internal fun desktopScrollableMessage(message: String, preferredHeight: Int = 320): JScrollPane =
    JScrollPane(JTextArea(message).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        caretPosition = 0
    }).apply {
        preferredSize = Dimension(620, preferredHeight)
    }
