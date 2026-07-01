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

import org.openardf.radiooracle.shared.domain.ControlPointType

/** Shared public-label heuristics for controls whose label implies a non-scoring role. */
object ControlRoleLabelRules {
    fun inferredRole(publicLabel: String?): ControlPointType? {
        val label = publicLabel.normalizedRoleLabel()
        return when {
            label.isEmpty() -> null
            label in BEACON_LABELS || "BEACON" in label -> ControlPointType.BEACON
            label in SPECTATOR_LABELS || "SPECTATOR" in label || "SEPARATOR" in label -> ControlPointType.SEPARATOR
            label.isFoxLabel() -> ControlPointType.CONTROL
            else -> null
        }
    }

    fun foxNumber(publicLabel: String?): Int? {
        val label = publicLabel.normalizedRoleLabel()
        return morseFoxAliases[label]
            ?: FOX_NUMBER_LABEL_REGEX.matchEntire(label)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: SPRINT_FAST_FOX_LABEL_REGEX.matchEntire(label)?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }?.toIntOrNull()
    }

    fun foxAliasTokens(foxNumber: Int): List<String> =
        buildList {
            add("Fox $foxNumber")
            add("Fox$foxNumber")
            add("Fox-$foxNumber")
            add(foxNumber.toString())
            morseFoxAliases.entries
                .filter { it.value == foxNumber }
                .mapTo(this) { it.key }
        }.distinct()

    fun inferredSpecialRole(publicLabel: String?): ControlPointType? {
        return inferredRole(publicLabel).takeIf { it != ControlPointType.CONTROL }
    }

    fun mismatchWarning(publicLabel: String?, selectedRole: ControlPointType, inferredRole: ControlPointType): String {
        val label = publicLabel?.trim().orEmpty()
        val inferredLabel = roleLabel(inferredRole)
        val selectedLabel = roleLabel(selectedRole)
        return "Public label \"$label\" looks like $inferredLabel; set Role to $inferredLabel unless this station should be $selectedLabel."
    }

    fun roleLabel(type: ControlPointType): String =
        when (type) {
            ControlPointType.CONTROL -> "Fox"
            ControlPointType.SEPARATOR -> "Spectator"
            ControlPointType.BEACON -> "Beacon"
        }

    private fun String.isFoxLabel(): Boolean =
        this == "FOX" ||
            startsWith("FOX ") ||
            foxNumber(this) != null ||
            matches(SPRINT_FAST_FOX_LABEL_REGEX)

    private fun String?.normalizedRoleLabel(): String =
        orEmpty()
            .trim()
            .uppercase()
            .replace(Regex("[^A-Z0-9]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private val FOX_NUMBER_LABEL_REGEX = Regex("(?:FOX ?)?(\\d+)")
    private val SPRINT_FAST_FOX_LABEL_REGEX = Regex("(?:(\\d+)F|F(\\d+))")
    private val SPECTATOR_LABELS = setOf("S", "SP", "SPEC", "SPECTATOR", "SEP", "SEPARATOR")
    private val BEACON_LABELS = setOf("B", "BB", "M", "MO", "BEACON", "FINISH BEACON")
    private val morseFoxAliases = mapOf(
        "MOE" to 1,
        "MOI" to 2,
        "MOS" to 3,
        "MOH" to 4,
        "MO5" to 5
    )
}
