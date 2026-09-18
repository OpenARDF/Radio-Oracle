package org.openardf.radiooracle.shared.sportident

enum class SportIdentOwnerNameProblem {
    CARD_NOT_READY, UNSUPPORTED_CHARACTERS, TOO_LONG
}

data class SportIdentSi8OwnerNamePreview(
    val siNumber: Int,
    val firstName: String,
    val lastName: String,
    val nameCharacterCount: Int?,
    val problems: Set<SportIdentOwnerNameProblem>,
    // Parser-compatible preview text, not a write payload or card-memory layout.
    val encodedOwnerText: ByteArray?
)

/** Prepares owner text only, never a card write command or a replacement memory block. */
object SportIdentSi8OwnerNamePlanner {
    // Verified using the supplied vendor library's documented personal-data validation API.
    // Its count excludes separators; this does not establish the write payload's byte layout.
    const val MAX_NAME_CHARACTERS = 23

    fun preview(
        inspection: SportIdentCardOwnerInspection,
        firstName: String,
        lastName: String
    ): SportIdentSi8OwnerNamePreview {
        val first = firstName.trim(' ')
        val last = lastName.trim(' ')
        val problems = mutableSetOf<SportIdentOwnerNameProblem>()
        if (inspection.family != SportIdentCardFamily.SI8 || inspection.status != SportIdentOwnerDataStatus.READ) {
            problems += SportIdentOwnerNameProblem.CARD_NOT_READY
        }
        val supported = (first + last).all { it in ' '..'~' && it != ';' }
        if (!supported) problems += SportIdentOwnerNameProblem.UNSUPPORTED_CHARACTERS
        val text = "$first;$last;"
        val characterCount = if (supported) first.length + last.length else null
        if (characterCount != null && characterCount > MAX_NAME_CHARACTERS) {
            problems += SportIdentOwnerNameProblem.TOO_LONG
        }
        return SportIdentSi8OwnerNamePreview(
            inspection.siNumber, first, last, characterCount, problems,
            if (problems.isEmpty()) ByteArray(text.length) { text[it].code.toByte() } else null
        )
    }
}
