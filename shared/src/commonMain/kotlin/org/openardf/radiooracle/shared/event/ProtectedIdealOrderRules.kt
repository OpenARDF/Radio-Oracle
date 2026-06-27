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

package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.course.ControlPointRules
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.sportident.SportIdentCodes

/** Parsing and resolution rules for protected ideal course-order text. */
object ProtectedIdealOrderRules {
    private val tokenSeparators = Regex("""[\s,;]+""")
    private val numericToken = Regex("""\d+""")

    fun firstControlCode(idealOrderText: String, controls: List<EventControl>): Int? =
        resolveControls(idealOrderText, controls).firstOrNull()?.siCode

    fun validate(idealOrderText: String, controls: List<EventControl>) {
        resolveControls(idealOrderText, controls)
    }

    fun validateAssignedToCategory(idealOrderText: String, assignedControls: List<EventControl>) {
        val resolvedControls = resolveControls(
            idealOrderText = idealOrderText,
            controls = assignedControls,
            allowUncatalogedNumericControls = false,
            missingControlMessage = {
                "Protected ideal order control was not assigned to this category: $it"
            }
        )
        val duplicateControl = resolvedControls
            .groupingBy { it.id }
            .eachCount()
            .firstNotNullOfOrNull { (controlId, count) ->
                assignedControls.firstOrNull { it.id == controlId }?.takeIf { count > 1 }
            }
        require(duplicateControl == null) {
            val label = duplicateControl?.publicLabel?.takeIf { it.isNotBlank() } ?: duplicateControl?.label.orEmpty()
            "Protected ideal order control appears more than once: $label"
        }
    }

    fun resolveControlIds(idealOrderText: String, controls: List<EventControl>): List<String> =
        resolveControls(idealOrderText, controls).map { it.id }

    fun resolveControlCodes(idealOrderText: String, controls: List<EventControl>): List<Int> =
        resolveControls(idealOrderText, controls).map { it.siCode }

    @Deprecated("Use global controls instead of aliases.")
    fun firstControlCodeFromAliases(idealOrderText: String, aliases: List<EventAlias>): Int? =
        resolveControlCodesFromAliases(idealOrderText, aliases).firstOrNull()

    @Deprecated("Use global controls instead of aliases.")
    fun validateFromAliases(idealOrderText: String, aliases: List<EventAlias>) {
        resolveControlCodesFromAliases(idealOrderText, aliases)
    }

    @Deprecated("Use global controls instead of aliases.")
    fun resolveControlCodesFromAliases(idealOrderText: String, aliases: List<EventAlias>): List<Int> {
        val aliasesByName = aliases.associateBy { it.name }
        return idealOrderText
            .split(tokenSeparators)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map { token -> token.resolveControlCode(aliasesByName) }
    }

    private fun resolveControls(
        idealOrderText: String,
        controls: List<EventControl>,
        allowUncatalogedNumericControls: Boolean = true,
        missingControlMessage: (String) -> String = {
            "Protected ideal order control was not found: $it"
        }
    ): List<EventControl> {
        val controlsByStoredLabel = controls.associateBy { it.label }
        val controlsByPublicLabel = controls
            .mapNotNull { control -> control.publicLabel?.takeIf { it.isNotBlank() }?.let { it to control } }
            .groupBy { it.first }
            .mapNotNull { (label, matches) ->
                matches.map { it.second.id }.distinct().singleOrNull()?.let { label to matches.first().second }
            }
            .toMap()
        val controlsByEquivalentToken = controls
            .flatMap { control -> control.idealOrderEquivalentTokens().map { token -> token to control } }
            .groupBy { it.first }
            .mapNotNull { (token, matches) ->
                matches.map { it.second.id }.distinct().singleOrNull()?.let { token to matches.first().second }
            }
            .toMap()
        return ControlPointRules.tokenizeControlPoints(idealOrderText)
            .map { token ->
                token.resolveControl(
                    controls = controls,
                    controlsByStoredLabel = controlsByStoredLabel,
                    controlsByPublicLabel = controlsByPublicLabel,
                    controlsByEquivalentToken = controlsByEquivalentToken,
                    allowUncatalogedNumericControls = allowUncatalogedNumericControls,
                    missingControlMessage = missingControlMessage
                )
            }
    }

    private fun String.resolveControl(
        controls: List<EventControl>,
        controlsByStoredLabel: Map<String, EventControl>,
        controlsByPublicLabel: Map<String, EventControl>,
        controlsByEquivalentToken: Map<String, EventControl>,
        allowUncatalogedNumericControls: Boolean,
        missingControlMessage: (String) -> String
    ): EventControl {
        controlsByStoredLabel[this]?.let { return it }
        if (matches(numericToken)) {
            val controlCode = toIntOrNull()
            require(controlCode != null && controlCode > 0) {
                "Protected ideal order control code must be numeric and positive: $this"
            }
            controls.firstOrNull { it.siCode == controlCode }?.let { return it }
            controlsByPublicLabel[this]?.let { return it }
            expandedIdealOrderTokens().firstNotNullOfOrNull { controlsByEquivalentToken[it] }?.let { return it }
            if (allowUncatalogedNumericControls && controlCode <= SportIdentCodes.SI_MAX_CODE) {
                return EventControl(
                    id = EventControlCatalog.stableId(this, controlCode, ControlPointType.CONTROL),
                    raceId = "",
                    label = this,
                    siCode = controlCode,
                    type = ControlPointType.CONTROL
                )
            }
        }
        controlsByPublicLabel[this]?.let { return it }
        expandedIdealOrderTokens().firstNotNullOfOrNull { controlsByEquivalentToken[it] }?.let { return it }
        throw IllegalArgumentException(missingControlMessage(this))
    }

    private fun EventControl.idealOrderEquivalentTokens(): List<String> =
        buildList {
            add(label)
            publicLabel?.takeIf { it.isNotBlank() }?.let(::add)
            add(siCode.toString())
            publicLabel
                ?.filter(Char::isDigit)
                ?.takeIf { it.isNotBlank() }
                ?.let(::add)
            label
                .filter(Char::isDigit)
                .takeIf { it.isNotBlank() }
                ?.let(::add)
        }
            .flatMap { it.expandedIdealOrderTokens() }
            .distinct()

    private fun String.expandedIdealOrderTokens(): List<String> {
        val normalized = normalizedIdealOrderToken() ?: return emptyList()
        val digits = normalized.filter(Char::isDigit).takeIf { it.isNotBlank() }
        return buildList {
            add(normalized)
            digits?.toIntOrNull()?.let { number ->
                add(number.toString())
                when (number) {
                    in 1..5 -> {
                        add((30 + number).toString())
                        add((40 + number).toString())
                    }
                    in 31..35 -> add((number - 30).toString())
                    in 41..45 -> add((number - 40).toString())
                }
            }
        }
            .mapNotNull { it.normalizedIdealOrderToken() }
            .distinct()
    }

    private fun String.normalizedIdealOrderToken(): String? {
        val trimmed = trim()
            .removeSurrounding("'")
            .removeSurrounding("\"")
            .trim()
        return trimmed
            .takeIf { it.isNotBlank() }
            ?.lowercase()
            ?.replace(Regex("\\s+"), " ")
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
