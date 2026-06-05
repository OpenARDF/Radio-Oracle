package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.course.ControlPointValidationError
import org.openardf.radiooracle.shared.course.ControlPointValidationException
import org.openardf.radiooracle.shared.sportident.SportIdentCodes

/** Desktop text for shared control-point validation failures, aligned with Android meanings. */
object DesktopControlPointValidationText {
    fun messageFor(exception: ControlPointValidationException): String =
        messageFor(exception.error, exception.token, exception.siCode)

    fun messageFor(error: ControlPointValidationError, token: String? = null, siCode: Int? = null): String =
        when (error) {
            ControlPointValidationError.UNKNOWN_SPECIFIER ->
                "Unknown special control specifier: ${token ?: ""}"

            ControlPointValidationError.INVALID_RANGE ->
                "Invalid SI range: ${token ?: ""}. The code must be between ${SportIdentCodes.SI_MIN_CODE} and ${SportIdentCodes.SI_MAX_CODE}."

            ControlPointValidationError.TWO_IN_ROW ->
                "Two identical control points cannot be in a row."

            ControlPointValidationError.ORIENTEERING_SPECIAL ->
                "Orienteering cannot contain special control points."

            ControlPointValidationError.CLASSIC_DUPLICATE ->
                "Duplicate control points are not allowed in a classic race."

            ControlPointValidationError.NON_LAST_BEACON ->
                "Beacon must be the last control point."

            ControlPointValidationError.CLASSIC_SPECTATOR_NOT_ALLOWED ->
                "Spectator control points are not allowed in a classic race."

            ControlPointValidationError.SPRINT_DUPLICATE ->
                "Duplicate controls are not allowed in a single lap of a sprint race."

            ControlPointValidationError.SPRINT_SPECIAL_REUSES_CONTROL ->
                "Control point with SI code [${siCode ?: "unknown"}] used as a separator / beacon and as a control."
        }
}
