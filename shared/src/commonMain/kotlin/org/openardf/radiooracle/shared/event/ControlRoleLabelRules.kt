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
            matches(FOX_NUMBER_LABEL_REGEX) ||
            matches(SPRINT_FAST_FOX_LABEL_REGEX)

    private fun String?.normalizedRoleLabel(): String =
        orEmpty().trim().uppercase().replace(Regex("\\s+"), " ")

    private val FOX_NUMBER_LABEL_REGEX = Regex("(?:FOX ?)?\\d+")
    private val SPRINT_FAST_FOX_LABEL_REGEX = Regex("(?:\\d+F|F\\d+)")
    private val SPECTATOR_LABELS = setOf("S", "SP", "SPEC", "SPECTATOR", "SEP", "SEPARATOR")
    private val BEACON_LABELS = setOf("B", "BB", "M", "MO", "BEACON", "FINISH BEACON")
}
