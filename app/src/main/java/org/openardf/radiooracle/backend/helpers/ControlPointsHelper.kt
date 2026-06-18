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
        "$base$marker"
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
        return input.trim().split("\\s+".toRegex()).joinToString(" ") { token ->
            aliasesByName[token]?.let { alias ->
                if (token.equals(ControlPointRules.BEACON_CONTROL_MARKER.toString(), ignoreCase = true)) {
                    "${alias.siCode}${ControlPointRules.BEACON_CONTROL_MARKER}"
                } else {
                    alias.siCode.toString()
                }
            }
                ?: replaceMarkedAliasToken(token, aliasesByName)
        }
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
        aliasesByName: Map<String, Alias>
    ): String {
        val marker = token.lastOrNull()
        if (marker != ControlPointRules.SPECTATOR_CONTROL_MARKER &&
            marker != ControlPointRules.BEACON_CONTROL_MARKER
        ) {
            return token
        }
        val base = token.dropLast(1)
        return aliasesByName[base]?.let { alias -> "${alias.siCode}$marker" } ?: token
    }

    const val SPECTATOR_CONTROL_MARKER = ControlPointRules.SPECTATOR_CONTROL_MARKER
    const val BEACON_CONTROL_MARKER = ControlPointRules.BEACON_CONTROL_MARKER
}
