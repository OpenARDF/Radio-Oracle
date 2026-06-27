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

import org.junit.Assert.assertEquals
import org.junit.Test
import org.openardf.radiooracle.shared.course.ControlPointValidationError
import org.openardf.radiooracle.shared.course.ControlPointValidationException

class DesktopControlPointValidationTextTest {
    @Test
    fun mapsControlPointValidationFailuresToUserText() {
        assertEquals(
            "Unknown special control specifier: X",
            DesktopControlPointValidationText.messageFor(
                ControlPointValidationException(ControlPointValidationError.UNKNOWN_SPECIFIER, token = "X")
            )
        )
        assertEquals(
            "Invalid SI range: 512. The code must be between 1 and 511.",
            DesktopControlPointValidationText.messageFor(
                ControlPointValidationException(ControlPointValidationError.INVALID_RANGE, token = "512")
            )
        )
        assertEquals(
            "Beacon must be the last control point.",
            DesktopControlPointValidationText.messageFor(
                ControlPointValidationException(ControlPointValidationError.NON_LAST_BEACON)
            )
        )
        assertEquals(
            "Control point with SI code [40] used as a spectator / beacon and as a control.",
            DesktopControlPointValidationText.messageFor(
                ControlPointValidationException(
                    ControlPointValidationError.SPRINT_SPECIAL_REUSES_CONTROL,
                    siCode = 40
                )
            )
        )
        assertEquals(
            "Assigned Controls cannot include the same control more than once.",
            DesktopControlPointValidationText.messageFor(
                ControlPointValidationException(ControlPointValidationError.ASSIGNED_DUPLICATE, siCode = 31)
            )
        )
    }
}
