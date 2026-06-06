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
