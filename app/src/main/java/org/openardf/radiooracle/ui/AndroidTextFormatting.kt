package org.openardf.radiooracle.ui

import android.annotation.SuppressLint
import android.widget.TextView

/**
 * Writes identifier and form-field numbers without grouping or locale-specific decorations.
 *
 * These values are parsed back as machine-readable integers or match SPORTident identifiers, so
 * sentence-oriented localization is intentionally not applied here.
 */
@SuppressLint("SetTextI18n")
fun TextView.setInvariantNumber(value: Number?) {
    text = value?.toString().orEmpty()
}
