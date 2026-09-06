package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.sportident.SportIdentCodes

fun String?.courseDescriptionSiCodeHint(): Int? =
    this
        ?.lineSequence()
        ?.map { line -> line.trim() }
        ?.mapNotNull { line ->
            Regex("""^SI\s*=\s*(\d+)\s*$""", RegexOption.IGNORE_CASE)
                .matchEntire(line)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?.takeIf(SportIdentCodes::isSICodeValid)
        }
        ?.firstOrNull()

/** Explicit applied station assignments supersede import-time SI hints. Other description lines survive. */
fun String?.withAppliedSiCode(siCode: Int): String =
    (this.orEmpty().lineSequence().filterNot { Regex("""^\s*SI\s*=.*$""", RegexOption.IGNORE_CASE).matches(it) }
        .filter(String::isNotBlank).toList() + "SI=$siCode").joinToString("\n")
