package org.openardf.radiooracle.shared.sportident

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Transport-neutral request; it contains names and identity, never card-memory bytes. */
@Serializable
data class SportIdentOwnerNameWriteRequest(
    @SerialName("SchemaVersion") val schemaVersion: Int,
    @SerialName("StationNumber") val stationNumber: Int,
    @SerialName("CardNumber") val cardNumber: Int,
    @SerialName("ExpectedFirstName") val expectedFirstName: String,
    @SerialName("ExpectedLastName") val expectedLastName: String,
    @SerialName("FirstName") val firstName: String,
    @SerialName("LastName") val lastName: String,
    @SerialName("AcceptPossiblePunchLoss") val acceptPossiblePunchLoss: Boolean
)

@Serializable
data class SportIdentOwnerNameWriteResult(
    @SerialName("SchemaVersion") val schemaVersion: Int,
    @SerialName("StationNumber") val stationNumber: Int,
    @SerialName("CardNumber") val cardNumber: Int,
    @SerialName("CardType") val cardType: String,
    @SerialName("FirstName") val firstName: String,
    @SerialName("LastName") val lastName: String,
    @SerialName("ControlPunchCountBefore") val controlPunchCountBefore: Int,
    @SerialName("ControlPunchCountAfter") val controlPunchCountAfter: Int,
    @SerialName("PunchesPreserved") val punchesPreserved: Boolean,
    @SerialName("FeedbackPreserved") val feedbackPreserved: Boolean,
    @SerialName("CharacterSetPreserved") val characterSetPreserved: Boolean
)

@Serializable
enum class SportIdentOwnerWritePhase {
    @SerialName("WaitingForCard") WAITING_FOR_CARD,
    @SerialName("Writing") WRITING,
    @SerialName("WaitingForReadBack") WAITING_FOR_READ_BACK
}

@Serializable
data class SportIdentOwnerWriteProgress(
    @SerialName("SchemaVersion") val schemaVersion: Int,
    @SerialName("Event") val event: String,
    @SerialName("Phase") val phase: SportIdentOwnerWritePhase
)

/** The same preparation and read-back rules can be used by desktop and Android transports. */
object SportIdentOwnerNameProgramming {
    val json = Json { encodeDefaults = true }

    fun prepare(
        inspection: SportIdentCardOwnerInspection,
        stationNumber: Int,
        firstName: String,
        lastName: String,
        acceptPossiblePunchLoss: Boolean
    ): SportIdentOwnerNameWriteRequest {
        val preview = SportIdentSi8OwnerNamePlanner.preview(inspection, firstName, lastName)
        require(preview.problems.isEmpty() && inspection.siNumber > 0 && stationNumber > 0) {
            "Read a complete SI-Card8 and prepare valid names before writing."
        }
        require(acceptPossiblePunchLoss) { "Accept possible punch loss before writing." }
        val oldFirst = inspection.holder?.firstName.orEmpty()
        val oldLast = inspection.holder?.lastName.orEmpty()
        require(preview.firstName != oldFirst || preview.lastName != oldLast) { "The names are unchanged." }
        return SportIdentOwnerNameWriteRequest(1, stationNumber, inspection.siNumber, oldFirst, oldLast,
            preview.firstName, preview.lastName, true)
    }

    fun verify(request: SportIdentOwnerNameWriteRequest, result: SportIdentOwnerNameWriteResult) {
        require(request.schemaVersion == 1 && result.schemaVersion == 1) { "Unsupported verification version." }
        require(result.stationNumber == request.stationNumber && result.cardNumber == request.cardNumber &&
            result.cardType == SportIdentCardFamily.SI8.label) { "Read-back identity does not match the target card." }
        require(result.firstName == request.firstName && result.lastName == request.lastName) {
            "The stored names do not match the requested names."
        }
        require(result.controlPunchCountBefore >= 0 && result.controlPunchCountAfter == result.controlPunchCountBefore &&
            result.punchesPreserved && result.feedbackPreserved && result.characterSetPreserved) {
            "Read-back did not confirm preservation of the compared card data."
        }
    }
}

/** Reject missing/repeated/reordered progress and results received before independent read-back. */
class SportIdentOwnerWriteSequence {
    var phase: SportIdentOwnerWritePhase? = null
        private set

    fun advance(progress: SportIdentOwnerWriteProgress) {
        require(progress.schemaVersion == 1 && progress.event == "Phase" &&
            progress.phase.ordinal == (phase?.ordinal ?: -1) + 1) { "Unexpected programming progress." }
        phase = progress.phase
    }

    fun verify(request: SportIdentOwnerNameWriteRequest, result: SportIdentOwnerNameWriteResult) {
        require(phase == SportIdentOwnerWritePhase.WAITING_FOR_READ_BACK) { "Independent read-back did not finish." }
        SportIdentOwnerNameProgramming.verify(request, result)
    }
}
