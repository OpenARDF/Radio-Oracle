package org.openardf.radiooracle.shared.sportident

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SportIdentOwnerNameRecoveryTest {
    private val original = SportIdentCardOwnerInspection(2450662, SportIdentCardFamily.SI8,
        SportIdentCardHolder("Mickey", "Mouse", null), SportIdentOwnerDataStatus.READ)
    private val request = SportIdentOwnerNameProgramming.prepare(original, 593927, "Minnie", "Mouse", true)

    @Test fun distinguishesOriginalRequestedAndUnexpectedNamesWithoutClaimingPunchVerification() {
        assertEquals(SportIdentOwnerNameRecoveryAssessment.ORIGINAL_NAMES, SportIdentOwnerNameRecovery.assess(request, original))
        assertEquals(SportIdentOwnerNameRecoveryAssessment.REQUESTED_NAMES,
            SportIdentOwnerNameRecovery.assess(request, original.copy(holder = SportIdentCardHolder("Minnie", "Mouse", null))))
        assertEquals(SportIdentOwnerNameRecoveryAssessment.DIFFERENT_NAMES,
            SportIdentOwnerNameRecovery.assess(request, original.copy(holder = SportIdentCardHolder("Mortimer", "Mouse", null))))
    }

    @Test fun namesOnAnotherCardOrAnIncompleteReadCannotResolveRecovery() {
        assertEquals(SportIdentOwnerNameRecoveryAssessment.NOT_TARGET_CARD,
            SportIdentOwnerNameRecovery.assess(request, original.copy(siNumber = 2450663)))
        assertEquals(SportIdentOwnerNameRecoveryAssessment.NOT_TARGET_CARD,
            SportIdentOwnerNameRecovery.assess(request, original.copy(family = SportIdentCardFamily.SI9)))
        for (status in SportIdentOwnerDataStatus.entries.filter { it != SportIdentOwnerDataStatus.READ }) {
            assertEquals(SportIdentOwnerNameRecoveryAssessment.UNREADABLE,
                SportIdentOwnerNameRecovery.assess(request, original.copy(status = status)))
        }
        assertEquals(SportIdentOwnerNameRecoveryAssessment.UNREADABLE,
            SportIdentOwnerNameRecovery.assess(request, original.copy(holder = null)))
    }

    @Test fun malformedSavedIntentionsCannotBeUsedForRecovery() {
        for (bad in listOf(request.copy(schemaVersion = 2), request.copy(acceptPossiblePunchLoss = false),
                request.copy(cardNumber = 0), request.copy(stationNumber = 0), request.copy(firstName = "Minnie "),
                request.copy(firstName = "Mickey"), request.copy(firstName = "東京"))) {
            assertFailsWith<IllegalArgumentException> { SportIdentOwnerNameRecovery.assess(bad, original) }
        }
    }
}
