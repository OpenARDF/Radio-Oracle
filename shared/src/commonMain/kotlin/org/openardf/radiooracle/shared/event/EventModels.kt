package org.openardf.radiooracle.shared.event

import kotlinx.serialization.Serializable
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.PunchStatus
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.SIRecordType

/** Portable race metadata used by shared services and non-Android clients. */
@Serializable
data class EventRace(
    val id: String,
    val name: String,
    val apiKey: String,
    val startDateTimeIso: String,
    val raceType: RaceType,
    val raceLevel: RaceLevel,
    val raceBand: RaceBand,
    val timeLimitSeconds: Long,
    val courseAnalyzerSpeedCompensationFactor: Double = 1.0
)

/** Portable category definition, including optional category-level race overrides. */
@Serializable
data class EventCategory(
    val id: String,
    val raceId: String,
    val name: String,
    val isMan: Boolean,
    val maxAge: Int?,
    val lengthMeters: Int,
    val climbMeters: Int,
    val order: Int,
    val differentProperties: Boolean,
    val raceType: RaceType?,
    val raceBand: RaceBand?,
    val timeLimitSeconds: Long?,
    @Deprecated("Use EventCategoryData.controlPoints plus EventRaceData.controls.")
    val controlPointsString: String,
    val encryptedIdealOrder: String? = null,
    val encryptedCourseInfo: String? = null
) {
    /** Returns the race type that should be used for this category. */
    fun effectiveRaceType(race: EventRace): RaceType =
        if (differentProperties) raceType ?: race.raceType else race.raceType

    /** Returns the frequency band that should be used for this category. */
    fun effectiveRaceBand(race: EventRace): RaceBand =
        if (differentProperties) raceBand ?: race.raceBand else race.raceBand

    /** Returns the time limit that should be used for this category, expressed in seconds. */
    fun effectiveTimeLimitSeconds(race: EventRace): Long =
        if (differentProperties) timeLimitSeconds ?: race.timeLimitSeconds else race.timeLimitSeconds
}

/**
 * Password-protected route-derived course data.
 *
 * KML/KMZ files may live outside the Event File, but their derived length, climb,
 * ideal order, and route geometry are sensitive before competition day. The
 * desktop app therefore encrypts this payload before storing it in
 * EventCategory.encryptedCourseInfo instead of copying those values into public
 * category length/climb/control fields.
 */
@Serializable
data class ProtectedCourseInfo(
    val idealOrder: String = "",
    val lengthMeters: Int? = null,
    val climbMeters: Int? = null,
    val sourceName: String = "",
    val sourceSha256: String = "",
    val sampledPointCount: Int = 0,
    val route: List<ProtectedCourseRoutePoint> = emptyList(),
    val controlPoints: List<ProtectedCourseControlPoint> = emptyList(),
    val courseObjects: List<ProtectedCourseObjectPoint> = emptyList()
)

@Serializable
data class ProtectedCourseRoutePoint(
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double? = null
)

@Serializable
data class ProtectedCourseControlPoint(
    val controlId: String,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val type: ControlPointType = ControlPointType.CONTROL,
    val elevationMeters: Double? = null
)

@Serializable
data class ProtectedCourseObjectPoint(
    val id: String,
    val label: String,
    val type: ProtectedCourseObjectType,
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double? = null
)

@Serializable
enum class ProtectedCourseObjectType {
    START,
    FINISH,
    CONTROL,
    BEACON,
    SPECTATOR
}

fun ProtectedCourseInfo.effectiveLengthMeters(): Int? {
    val length = lengthMeters ?: return null
    val climb = climbMeters ?: return null
    return length + 10 * climb
}

/** Portable control-point definition for a category course. */
@Serializable
data class EventControlPoint(
    val id: String,
    val categoryId: String,
    @Deprecated("Use controlId and resolve through EventRaceData.controls.")
    val siCode: Int,
    @Deprecated("Use controlId and resolve through EventRaceData.controls.")
    val type: ControlPointType,
    val order: Int,
    val controlId: String = ""
)

/** Race-level logical control backed by a physical SportIdent code. */
@Serializable
data class EventControl(
    val id: String,
    val raceId: String,
    val label: String,
    val siCode: Int,
    val type: ControlPointType,
    val scored: Boolean = type.defaultScored(),
    @Deprecated("Use scored; legacy mandatory had inverted and previously unenforced semantics.")
    val mandatory: Boolean = false,
    val publicLabel: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val notes: String? = null
)

/** Default radio-o scoring role for a logical control. */
fun ControlPointType.defaultScored(): Boolean =
    this == ControlPointType.CONTROL

/** Portable display alias for a SportIdent control code. */
@Serializable
data class EventAlias(
    val id: String,
    val raceId: String,
    val siCode: Int,
    val name: String
)

/** Portable competitor record independent of Android Room persistence. */
@Serializable
data class EventCompetitor(
    val id: String,
    val raceId: String,
    val categoryId: String?,
    val firstName: String,
    val lastName: String,
    val club: String,
    val index: String,
    val isMan: Boolean,
    val birthYear: Int?,
    val siNumber: Int?,
    val siRent: Boolean,
    val startNumber: Int? = null,
    val drawnStartTimeSeconds: Long?,
    val preferredStartGroup: Int? = null,
    val bibNumber: String = index,
    val callSign: String = ""
) {
    init {
        require(preferredStartGroup == null || preferredStartGroup in 1..3) {
            "Preferred start group must be 1, 2, or 3."
        }
    }

    /** Formats the competitor name in the app's existing LASTNAME Firstname style. */
    fun fullName(): String = "${lastName.uppercase()} $firstName"

    /** Formats the competitor name with the assigned start number appended when starts have been assigned. */
    fun nameWithStartNumber(): String =
        startNumber?.let { "${fullName()} ($it)" } ?: fullName()
}

/** Portable raw punch record, with SportIdent times represented as absolute seconds. */
@Serializable
data class EventPunch(
    val id: String,
    val raceId: String,
    val resultId: String?,
    val cardNumber: Int?,
    val siCode: Int,
    val siTimeSeconds: Long,
    val originalSiTimeSeconds: Long,
    val punchType: SIRecordType,
    val order: Int,
    val punchStatus: PunchStatus,
    val splitSeconds: Long
)

/** Portable result/readout summary for a competitor or unmatched SI card. */
@Serializable
data class EventResult(
    val id: String,
    val raceId: String,
    val competitorId: String?,
    val siNumber: Int?,
    val cardType: Byte,
    val checkTimeSeconds: Long?,
    val startTimeSeconds: Long?,
    val finishTimeSeconds: Long?,
    val readoutDateTimeIso: String,
    val automaticStatus: Boolean,
    val resultStatus: ResultStatus,
    val points: Int,
    val runTimeSeconds: Long,
    val modified: Boolean,
    val sent: Boolean,
    val cardName: String? = null,
    val place: Int = 0,
    val categoryId: String? = null
)

/** Portable punch plus optional alias resolved for display. */
@Serializable
data class EventAliasPunch(
    val punch: EventPunch,
    val alias: EventAlias?
)

/** Portable readout data: result summary plus all recorded punches. */
@Serializable
data class EventReadoutData(
    val result: EventResult,
    val punches: List<EventAliasPunch>
)

/** Portable category aggregate containing course and category competitors. */
@Serializable
data class EventCategoryData(
    val category: EventCategory,
    val controlPoints: List<EventControlPoint>,
    val competitors: List<EventCompetitor>,
    val publicControlIds: List<String> = emptyList()
)

/** Portable competitor plus optional category aggregate used by result lists. */
@Serializable
data class EventCompetitorCategory(
    val competitor: EventCompetitor,
    val category: EventCategory?
)

/** Portable competitor aggregate with optional readout data. */
@Serializable
data class EventCompetitorData(
    val competitorCategory: EventCompetitorCategory,
    val readoutData: EventReadoutData?
)

/** Portable complete event aggregate used for project import/export and desktop workflows. */
@Serializable
data class EventRaceData(
    val race: EventRace,
    val categories: List<EventCategoryData>,
    val aliases: List<EventAlias>,
    val competitorData: List<EventCompetitorData>,
    val unmatchedReadoutData: List<EventReadoutData>,
    val controls: List<EventControl> = emptyList(),
    val startDrawSettings: StartDrawSettings? = null
)

/**
 * Returns category ids that are associated with competitors for operational
 * race surfaces. Course-only categories, such as categories created from KML/KMZ
 * before competitors are imported, are intentionally omitted.
 */
fun EventRaceData.associatedCategoryIds(includeResultCategoryIds: Boolean = true): Set<String> =
    competitorData
        .flatMap { data ->
            buildList {
                data.competitorCategory.category?.id?.let(::add)
                data.competitorCategory.competitor.categoryId?.let(::add)
                if (includeResultCategoryIds) {
                    data.readoutData?.result?.categoryId?.let(::add)
                }
            }
        }
        .toSet()

/**
 * Returns categories that should participate in race operations, start/result
 * exports, and live displays. Setup/course editors should continue to use the
 * raw category list so imported course-only categories remain editable.
 */
fun EventRaceData.competitionCategories(includeResultCategoryIds: Boolean = true): List<EventCategoryData> {
    val categoryIds = associatedCategoryIds(includeResultCategoryIds)
    return categories.filter { it.category.id in categoryIds }
}
