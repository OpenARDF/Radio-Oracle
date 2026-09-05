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

package org.openardf.radiooracle.backend.helpers

import android.content.Context
import androidx.preference.PreferenceManager
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.room.entity.Alias
import org.openardf.radiooracle.backend.room.entity.ControlPoint
import org.openardf.radiooracle.backend.room.entity.Punch
import org.openardf.radiooracle.backend.room.entity.embeddeds.AliasPunch
import org.openardf.radiooracle.backend.room.entity.embeddeds.ControlPointAlias
import org.openardf.radiooracle.backend.room.enums.ControlPointType
import org.openardf.radiooracle.backend.room.enums.RaceType
import org.openardf.radiooracle.backend.room.enums.SIRecordType
import org.openardf.radiooracle.shared.course.ControlPointDefinition
import org.openardf.radiooracle.shared.course.ControlPointDisplayToken
import org.openardf.radiooracle.shared.course.ControlPointRules
import org.openardf.radiooracle.shared.course.ControlPointValidationError
import org.openardf.radiooracle.shared.course.ControlPointValidationException
import org.openardf.radiooracle.shared.event.ControlRoleLabelRules
import java.util.UUID

/**
 * @author Vojtech Kopal, Pavel Kolsky
 * Android adapter around shared control-point parsing, validation, and display formatting.
 */
object ControlPointsHelper {
    /**
     * Parses a course-control string into Room control points.
     *
     * Invalid sequences are rethrown with a localized message for direct UI display.
     */
    fun getControlPointsFromString(
        input: String,
        categoryId: UUID,
        raceType: RaceType,
        context: Context
    ): List<ControlPoint> {
        return try {
            ControlPointRules.parseControlPoints(input, raceType).map { definition ->
                definition.toControlPoint(categoryId)
            }
        } catch (exception: ControlPointValidationException) {
            throw IllegalArgumentException(exception.toLocalizedMessage(context))
        }
    }

    /** Parses display text where control aliases may be used instead of raw SI codes. */
    fun getControlPointsFromDisplayString(
        input: String,
        categoryId: UUID,
        raceType: RaceType,
        aliases: List<Alias>,
        context: Context
    ): List<ControlPoint> =
        getControlPointsFromString(
            replaceAliasTokens(input, aliases),
            categoryId,
            raceType,
            context
        )

    /** Formats Room control points back into the compact course-control string. */
    fun getStringFromControlPoints(controlPoints: List<ControlPoint>): String {
        return ControlPointRules.formatControlPoints(
            controlPoints.map { controlPoint ->
                ControlPointDefinition(controlPoint.siCode, controlPoint.type, controlPoint.order)
            }
        )
    }

    /** Converts a shared control-point definition into a Room entity for one category. */
    private fun ControlPointDefinition.toControlPoint(categoryId: UUID): ControlPoint {
        return ControlPoint(
            UUID.randomUUID(),
            categoryId,
            siCode,
            type,
            order
        )
    }

    /** Maps shared validation errors to Android string resources. */
    private fun ControlPointValidationException.toLocalizedMessage(context: Context): String {
        return when (error) {
            ControlPointValidationError.UNKNOWN_SPECIFIER ->
                context.getString(R.string.control_point_unknown_specifier, token)

            ControlPointValidationError.INVALID_RANGE ->
                context.getString(R.string.control_point_invalid_range, token)

            ControlPointValidationError.TWO_IN_ROW ->
                context.getString(R.string.control_point_two_in_row)

            ControlPointValidationError.ORIENTEERING_SPECIAL ->
                context.getString(R.string.control_point_orienteering_special)

            ControlPointValidationError.CLASSIC_DUPLICATE ->
                context.getString(R.string.control_point_classic_duplicate)

            ControlPointValidationError.NON_LAST_BEACON ->
                context.getString(R.string.control_point_non_last_beacon)

            ControlPointValidationError.CLASSIC_SPECTATOR_NOT_ALLOWED ->
                context.getString(R.string.control_point_classic_spectator_not_allowed)

            ControlPointValidationError.SPRINT_DUPLICATE ->
                context.getString(R.string.control_point_sprint_duplicate)

            ControlPointValidationError.ASSIGNED_DUPLICATE ->
                context.getString(R.string.control_point_assigned_duplicate)

            ControlPointValidationError.SPRINT_SPECIAL_REUSES_CONTROL ->
                context.getString(R.string.control_point_sprint_two_usages, siCode)
        }
    }

    /** Formats category control points for display, using aliases when the user setting enables them. */
    fun getStringFromControlPointAliases(
        controlPoints: List<ControlPointAlias>,
        context: Context
    ): String {
        return ControlPointRules.formatDisplayTokens(
            controlPoints.map { controlPointAlias ->
                ControlPointDisplayToken(
                    siCode = controlPointAlias.controlPoint.siCode,
                    aliasName = controlPointAlias.alias?.name
                )
            },
            shouldUseAliases(context)
        )
    }

    /** Formats category control points for editing, preserving special-control markers. */
    fun getEditableStringFromControlPointAliases(
        controlPoints: List<ControlPointAlias>,
        context: Context
    ): String =
        formatEditableControlPointAliases(controlPoints, shouldUseAliases(context))

    /** Formats editable category controls while avoiding duplicate conventional marker aliases. */
    internal fun formatEditableControlPointAliases(
        controlPoints: List<ControlPointAlias>,
        useAlias: Boolean
    ): String = controlPoints.joinToString(" ") { controlPointAlias ->
        val controlPoint = controlPointAlias.controlPoint
        val base = if (useAlias && controlPointAlias.alias?.name != null) {
            controlPointAlias.alias!!.name
        } else {
            controlPoint.siCode.toString()
        }
        val marker = controlPoint.editMarkerFor(base, useAlias)
        ControlPointRules.formatEditableDisplayTokens(
            listOf(ControlPointDisplayToken(controlPoint.siCode, "$base$marker")),
            useAlias = true
        )
    }

    /** Formats raw readout punches without alias substitution. */
    fun getStringFromPunches(punches: List<Punch>): String {
        return ControlPointRules.formatIncludedDisplayTokensWithTrailingSpaces(
            punches.map { punch ->
                ControlPointDisplayToken(
                    siCode = punch.siCode,
                    include = punch.punchType == SIRecordType.CONTROL
                )
            },
            useAlias = false
        )
    }

    /** Formats readout punches for display, using aliases when the user setting enables them. */
    fun getStringFromAliasPunches(punches: List<AliasPunch>, context: Context): String {
        return ControlPointRules.formatDisplayTokens(
            punches.map { aliasPunch ->
                ControlPointDisplayToken(
                    siCode = aliasPunch.punch.siCode,
                    aliasName = aliasPunch.alias?.name,
                    include = aliasPunch.punch.punchType == SIRecordType.CONTROL
                )
            },
            shouldUseAliases(context)
        )
    }

    fun shouldUseAliases(context: Context): Boolean {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPref.getBoolean(context.getString(R.string.key_results_use_aliases), true)
    }

    private fun replaceAliasTokens(input: String, aliases: List<Alias>): String {
        if (input.isBlank()) {
            return input
        }
        val aliasesByName = aliases.associateBy { it.name }
        val aliasesByEquivalentName = aliases.groupBy { it.name.aliasMatchKey() }
            .mapNotNull { (name, matches) ->
                matches.first().takeIf { matches.map(Alias::siCode).distinct().size == 1 }
                    ?.let { name to it }
            }.toMap()
        fun findAlias(name: String): Alias? = aliasesByName[name]
            ?: aliasesByEquivalentName[name.aliasMatchKey()].takeIf { name.any(Char::isLetter) }
        val tokens = ControlPointRules.tokenizeControlPoints(input)
        val resolved = mutableListOf<String>()
        var index = 0
        while (index < tokens.size) {
            // Prefer a complete known name (for example Fox 2) over its individual words.
            // Shared tokenization also supports quoted names and comma-separated controls.
            val match = (tokens.size downTo index + 1).firstNotNullOfOrNull { end ->
                val candidate = tokens.subList(index, end).joinToString(" ")
                resolveAliasToken(candidate, ::findAlias)?.let { it to end }
            }
            if (match == null) {
                resolved += tokens[index++]
            } else {
                resolved += match.first
                index = match.second
            }
        }
        return resolved.joinToString(" ")
    }

    private fun String.aliasMatchKey(): String =
        ControlRoleLabelRules.foxNumber(this)?.let { "fox:$it" }
            ?: "label:${filterNot(Char::isWhitespace).lowercase()}"

    private fun resolveAliasToken(token: String, findAlias: (String) -> Alias?): String? {
        findAlias(token)?.let { alias ->
            return if (token.equals(ControlPointRules.BEACON_CONTROL_MARKER.toString(), ignoreCase = true)) {
                "${alias.siCode}${ControlPointRules.BEACON_CONTROL_MARKER}"
            } else {
                alias.siCode.toString()
            }
        }
        return replaceMarkedAliasToken(token, findAlias)
    }

    private fun ControlPoint.editMarkerFor(base: String, useAlias: Boolean): String =
        when (type) {
            ControlPointType.BEACON ->
                if (useAlias && base.equals(ControlPointRules.BEACON_CONTROL_MARKER.toString(), ignoreCase = true)) {
                    ""
                } else {
                    ControlPointRules.BEACON_CONTROL_MARKER.toString()
                }
            ControlPointType.SEPARATOR -> ControlPointRules.SPECTATOR_CONTROL_MARKER.toString()
            ControlPointType.CONTROL -> ""
        }

    private fun replaceMarkedAliasToken(
        token: String,
        findAlias: (String) -> Alias?
    ): String? {
        val marker = token.lastOrNull()
        if (marker != ControlPointRules.SPECTATOR_CONTROL_MARKER &&
            marker != ControlPointRules.BEACON_CONTROL_MARKER
        ) {
            return null
        }
        val base = token.dropLast(1)
        // A marker must be attached to its control. In "Fox 2 B", B is a separate
        // control and must not be absorbed while matching a multiword fox name.
        if (base.lastOrNull()?.isWhitespace() == true) {
            return null
        }
        return findAlias(base)?.let { alias -> "${alias.siCode}$marker" }
    }

    const val SPECTATOR_CONTROL_MARKER = ControlPointRules.SPECTATOR_CONTROL_MARKER
    const val BEACON_CONTROL_MARKER = ControlPointRules.BEACON_CONTROL_MARKER
}
