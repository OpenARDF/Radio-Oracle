package org.openardf.radiooracle.shared.sportident

enum class SportIdentOwnerNameProblem {
    CARD_NOT_READY, UNSUPPORTED_CHARACTERS, TOO_LONG
}

data class SportIdentSi8OwnerNamePreview(
    val siNumber: Int,
    val firstName: String,
    val lastName: String,
    val byteCount: Int?,
    val problems: Set<SportIdentOwnerNameProblem>,
    val encodedOwnerText: ByteArray?
)

/** Prepares owner text only, never a card write command or a replacement memory block. */
object SportIdentSi8OwnerNamePlanner {
    // Conservative draft policy from the legacy layout reference, pending write-protocol verification.
    // The read parser's larger owner region must not be treated as a proven writable capacity.
    const val DRAFT_BYTE_LIMIT = 24

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
        val byteCount = if (supported) text.length else null
        if (byteCount != null && byteCount > DRAFT_BYTE_LIMIT) problems += SportIdentOwnerNameProblem.TOO_LONG
        return SportIdentSi8OwnerNamePreview(
            inspection.siNumber, first, last, byteCount, problems,
            if (problems.isEmpty()) ByteArray(text.length) { text[it].code.toByte() } else null
        )
    }
}
