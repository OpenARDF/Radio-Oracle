package org.openardf.radiooracle.shared.printing

/** Converts the shared ESC/POS ticket markup into fixed-width plain text for desktop/system printers. */
object FinishTicketPlainTextFormatter {
    fun format(
        markedUpText: String,
        charactersPerLine: Int = DEFAULT_CHARACTERS_PER_LINE
    ): String {
        require(charactersPerLine > 0) { "charactersPerLine must be greater than zero." }
        return markedUpText
            .lineSequence()
            .map { it.formatLine(charactersPerLine) }
            .joinToString("\n")
            .trimEnd() + "\n"
    }

    private fun String.formatLine(charactersPerLine: Int): String {
        if (isBlank()) {
            return ""
        }
        val segments = parseSegments()
        if (segments.isEmpty()) {
            return cleanMarkup().truncate(charactersPerLine)
        }

        if (segments.all { it.alignment == Alignment.Center }) {
            return segments.joinToString("") { it.text }.center(charactersPerLine)
        }

        val left = segments
            .filter { it.alignment == Alignment.Left }
            .joinToString("") { it.text }
        val rightSegments = segments.filter { it.alignment == Alignment.Right }
        val center = segments
            .filter { it.alignment == Alignment.Center }
            .joinToString("") { it.text }

        if (rightSegments.isEmpty()) {
            return (left + center).truncate(charactersPerLine)
        }

        val right = rightSegments.joinToString(" ") { it.text }.truncate(charactersPerLine)
        val leftText = (left + center)
            .truncate((charactersPerLine - right.length - 1).coerceAtLeast(0))
        val padding = (charactersPerLine - leftText.length - right.length).coerceAtLeast(1)
        return leftText + " ".repeat(padding) + right
    }

    private fun String.parseSegments(): List<Segment> {
        val matches = segmentToken.findAll(this).toList()
        if (matches.isEmpty()) {
            return emptyList()
        }
        return matches.mapIndexedNotNull { index, match ->
            val start = match.range.last + 1
            val end = matches.getOrNull(index + 1)?.range?.first ?: length
            val text = substring(start, end).cleanMarkup()
            if (text.isEmpty()) {
                null
            } else {
                Segment(match.value.toAlignment(), text)
            }
        }
    }

    private fun String.toAlignment(): Alignment =
        when (this) {
            "[C]" -> Alignment.Center
            "[R]" -> Alignment.Right
            else -> Alignment.Left
        }

    private fun String.cleanMarkup(): String =
        replace(segmentToken, "").replace(htmlTag, "").trimEnd()

    private fun String.truncate(maxLength: Int): String =
        if (length > maxLength) take(maxLength) else this

    private fun String.center(width: Int): String {
        val text = truncate(width)
        val leftPadding = ((width - text.length) / 2).coerceAtLeast(0)
        return " ".repeat(leftPadding) + text
    }

    private enum class Alignment {
        Left,
        Center,
        Right
    }

    private data class Segment(
        val alignment: Alignment,
        val text: String
    )

    private val segmentToken = Regex("\\[(L|C|R)]")
    private val htmlTag = Regex("<[^>]+>")
    private const val DEFAULT_CHARACTERS_PER_LINE = 32
}
