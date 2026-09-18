package org.openardf.radiooracle.shared.sportident

/** A saved intention is never permission to replay a write. */
enum class SportIdentOwnerNameRecoveryAssessment {
    NOT_TARGET_CARD,
    UNREADABLE,
    REQUESTED_NAMES,
    ORIGINAL_NAMES,
    DIFFERENT_NAMES
}

object SportIdentOwnerNameRecovery {
    fun validate(request: SportIdentOwnerNameWriteRequest) {
        val original = SportIdentCardOwnerInspection(request.cardNumber, SportIdentCardFamily.SI8,
            SportIdentCardHolder(request.expectedFirstName, request.expectedLastName, null), SportIdentOwnerDataStatus.READ)
        require(SportIdentOwnerNameProgramming.prepare(original, request.stationNumber,
            request.firstName, request.lastName, request.acceptPossiblePunchLoss) == request)
    }

    /** This checks names only. A later native read cannot prove preservation of earlier punch values. */
    fun assess(request: SportIdentOwnerNameWriteRequest, inspection: SportIdentCardOwnerInspection): SportIdentOwnerNameRecoveryAssessment {
        validate(request)
        if (inspection.siNumber != request.cardNumber || inspection.family != SportIdentCardFamily.SI8) {
            return SportIdentOwnerNameRecoveryAssessment.NOT_TARGET_CARD
        }
        if (inspection.status != SportIdentOwnerDataStatus.READ || inspection.holder == null) {
            return SportIdentOwnerNameRecoveryAssessment.UNREADABLE
        }
        val first = inspection.holder.firstName.orEmpty()
        val last = inspection.holder.lastName.orEmpty()
        return when {
            first == request.firstName && last == request.lastName -> SportIdentOwnerNameRecoveryAssessment.REQUESTED_NAMES
            first == request.expectedFirstName && last == request.expectedLastName -> SportIdentOwnerNameRecoveryAssessment.ORIGINAL_NAMES
            else -> SportIdentOwnerNameRecoveryAssessment.DIFFERENT_NAMES
        }
    }
}
