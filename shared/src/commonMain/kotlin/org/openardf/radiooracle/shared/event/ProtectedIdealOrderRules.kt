package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.sportident.SportIdentCodes

/** Parsing and resolution rules for protected ideal course-order text. */
object ProtectedIdealOrderRules {
    private val tokenSeparators = Regex("""[\s,;]+""")
    private val numericToken = Regex("""\d+""")

    fun firstControlCode(idealOrderText: String, aliases: List<EventAlias>): Int? =
        resolveControlCodes(idealOrderText, aliases).firstOrNull()

    fun validate(idealOrderText: String, aliases: List<EventAlias>) {
        resolveControlCodes(idealOrderText, aliases)
    }

    fun resolveControlCodes(idealOrderText: String, aliases: List<EventAlias>): List<Int> {
        val aliasesByName = aliases.associateBy { it.name }
        return idealOrderText
            .split(tokenSeparators)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map { token -> token.resolveControlCode(aliasesByName) }
    }

    private fun String.resolveControlCode(aliasesByName: Map<String, EventAlias>): Int {
        if (matches(numericToken)) {
            val controlCode = toIntOrNull()
            require(controlCode != null && controlCode > 0) {
                "Protected ideal order control code must be numeric and positive: $this"
            }
            if (controlCode <= SportIdentCodes.SI_MAX_CODE) {
                return controlCode
            }
        }
        return aliasesByName[this]?.siCode
            ?: throw IllegalArgumentException("Protected ideal order alias was not found: $this")
    }
}
