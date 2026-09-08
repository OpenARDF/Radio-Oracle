package org.openardf.radiooracle.ui.readouts

import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.sportident.SIConstants

internal fun readoutSiNumberInputError(text: String): Int? {
    val value = text.trim()
    if (value.isEmpty()) return null
    val number = value.toIntOrNull()
    return if (number == null || !SIConstants.isSINumberValid(number)) {
        R.string.si_number_invalid_range
    } else null
}
