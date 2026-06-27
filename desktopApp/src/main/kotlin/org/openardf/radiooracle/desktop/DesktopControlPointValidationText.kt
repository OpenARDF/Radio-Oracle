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
                "Duplicate controls are not allowed in a sprint race."

            ControlPointValidationError.ASSIGNED_DUPLICATE ->
                "Assigned Controls cannot include the same control more than once."

            ControlPointValidationError.SPRINT_SPECIAL_REUSES_CONTROL ->
                "Control point with SI code [${siCode ?: "unknown"}] used as a spectator / beacon and as a control."
        }
}
