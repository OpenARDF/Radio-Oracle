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

package org.openardf.radiooracle.backend.shared

import org.openardf.radiooracle.backend.room.entity.Alias
import org.openardf.radiooracle.backend.room.entity.Category
import org.openardf.radiooracle.backend.room.entity.Competitor
import org.openardf.radiooracle.backend.room.entity.ControlPoint
import org.openardf.radiooracle.backend.room.entity.Punch
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.room.entity.Result
import org.openardf.radiooracle.backend.room.entity.embeddeds.AliasPunch
import org.openardf.radiooracle.backend.room.entity.embeddeds.CategoryData
import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorCategory
import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorData
import org.openardf.radiooracle.backend.room.entity.embeddeds.RaceData
import org.openardf.radiooracle.backend.room.entity.embeddeds.ReadoutData
import org.openardf.radiooracle.backend.room.enums.RaceType
import org.openardf.radiooracle.shared.event.EventAlias
import org.openardf.radiooracle.shared.event.EventAliasPunch
import org.openardf.radiooracle.shared.event.EventAssignedControlOrder
import org.openardf.radiooracle.shared.event.EventCategory
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventCompetitor
import org.openardf.radiooracle.shared.event.EventCompetitorCategory
import org.openardf.radiooracle.shared.event.EventCompetitorData
import org.openardf.radiooracle.shared.event.EventControl
import org.openardf.radiooracle.shared.event.EventControlCatalog
import org.openardf.radiooracle.shared.event.EventControlPoint
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventPunch
import org.openardf.radiooracle.shared.event.EventRace
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.EventReadoutData
import org.openardf.radiooracle.shared.event.EventResult
import org.openardf.radiooracle.shared.course.ControlPointDefinition
import org.openardf.radiooracle.shared.course.ControlPointRules
import org.openardf.radiooracle.shared.event.StandardCategoryRules
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

/** Converts the Android Room race entity into the portable shared event model. */
fun Race.toEventRace(): EventRace =
    EventRace(
        id = id.toString(),
        name = name,
        apiKey = apiKey,
        startDateTimeIso = startDateTime.toString(),
        raceType = raceType,
        raceLevel = raceLevel,
        raceBand = raceBand,
        timeLimitSeconds = timeLimit.seconds
    )

/** Converts the Android Room category entity into the portable shared event model. */
fun Category.toEventCategory(): EventCategory =
    EventCategory(
        id = id.toString(),
        raceId = raceId.toString(),
        name = name,
        isMan = isMan,
        maxAge = maxAge,
        lengthMeters = length,
        climbMeters = climb,
        order = order,
        differentProperties = false,
        raceType = null,
        raceBand = null,
        timeLimitSeconds = null,
        controlPointsString = controlPointsString
    )

/** Converts the Android Room control-point entity into the portable shared event model. */
fun ControlPoint.toEventControlPoint(): EventControlPoint =
    EventControlPoint(
        id = id.toString(),
        categoryId = categoryId.toString(),
        siCode = siCode,
        type = type,
        order = order
    )

/** Converts the Android Room alias entity into the portable shared event model. */
fun Alias.toEventAlias(): EventAlias =
    EventAlias(
        id = id.toString(),
        raceId = raceId.toString(),
        siCode = siCode,
        name = name
    )

/** Converts the Android Room competitor entity into the portable shared event model. */
fun Competitor.toEventCompetitor(): EventCompetitor =
    EventCompetitor(
        id = id.toString(),
        raceId = raceId.toString(),
        categoryId = categoryId?.toString(),
        firstName = firstName,
        lastName = lastName,
        club = club,
        index = index,
        isMan = isMan,
        birthYear = birthYear,
        siNumber = siNumber,
        siRent = siRent,
        startNumber = startNumber.takeIf { it > 0 },
        drawnStartTimeSeconds = drawnRelativeStartTime?.seconds
    )

/** Converts the Android Room punch entity into the portable shared event model. */
fun Punch.toEventPunch(): EventPunch =
    EventPunch(
        id = id.toString(),
        raceId = raceId.toString(),
        resultId = resultId?.toString(),
        cardNumber = cardNumber,
        siCode = siCode,
        siTimeSeconds = siTime.getSeconds(),
        originalSiTimeSeconds = origSiTime.getSeconds(),
        punchType = punchType,
        order = order,
        punchStatus = punchStatus,
        splitSeconds = split.seconds
    )

/** Converts the Android Room result entity into the portable shared event model. */
fun Result.toEventResult(): EventResult =
    EventResult(
        id = id.toString(),
        raceId = raceId.toString(),
        competitorId = competitorId?.toString(),
        siNumber = siNumber,
        cardType = cardType,
        checkTimeSeconds = checkTime?.getSeconds(),
        startTimeSeconds = startTime?.getSeconds(),
        finishTimeSeconds = finishTime?.getSeconds(),
        readoutDateTimeIso = readoutTime.toString(),
        automaticStatus = automaticStatus,
        resultStatus = resultStatus,
        points = points,
        runTimeSeconds = runTime.seconds,
        modified = modified,
        sent = sent,
        cardName = cardName,
        place = place
    )

/** Converts an Android alias-punch relation into the portable shared event model. */
fun AliasPunch.toEventAliasPunch(): EventAliasPunch =
    EventAliasPunch(
        punch = punch.toEventPunch(),
        alias = alias?.toEventAlias()
    )

/** Converts an Android readout aggregate into the portable shared event model. */
fun ReadoutData.toEventReadoutData(): EventReadoutData =
    EventReadoutData(
        result = result.toEventResult(),
        punches = punches.map { it.toEventAliasPunch() }
    )

/** Converts an Android category aggregate into the portable shared event model. */
fun CategoryData.toEventCategoryData(): EventCategoryData =
    EventCategoryData(
        category = category.toEventCategory(),
        controlPoints = controlPoints.map { it.toEventControlPoint() },
        competitors = competitors.map { it.toEventCompetitor() }
    )

/** Converts an Android competitor/category relation into the portable shared event model. */
fun CompetitorCategory.toEventCompetitorCategory(): EventCompetitorCategory =
    EventCompetitorCategory(
        competitor = competitor.toEventCompetitor(),
        category = category?.toEventCategory()
    )

/** Converts an Android competitor aggregate into the portable shared event model. */
fun CompetitorData.toEventCompetitorData(): EventCompetitorData =
    EventCompetitorData(
        competitorCategory = competitorCategory.toEventCompetitorCategory(),
        readoutData = readoutData?.toEventReadoutData()
    )

/** Converts a complete Android Room race aggregate into the portable shared event model. */
fun RaceData.toEventRaceData(): EventRaceData {
    val raceData = EventRaceData(
        race = race.toEventRace(),
        categories = categories.map { it.toEventCategoryData() },
        aliases = aliases.map { it.toEventAlias() },
        competitorData = competitorData.map { it.toEventCompetitorData() },
        unmatchedReadoutData = unmatchedReadoutData.map { it.toEventReadoutData() }
    )
    return EventControlCatalog.backfillControls(EventProjectFile(raceData = raceData)).raceData
}

/** Converts the portable shared race model back into an Android Room entity. */
fun EventRace.toRoomRace(): Race =
    toRoomRace(RoomIdMapper())

private fun EventRace.toRoomRace(idMapper: RoomIdMapper): Race =
    Race(
        id = idMapper.uuidFor(id),
        name = name,
        apiKey = apiKey,
        startDateTime = LocalDateTime.parse(startDateTimeIso),
        raceType = raceType,
        raceLevel = raceLevel,
        raceBand = raceBand,
        timeLimit = Duration.ofSeconds(timeLimitSeconds)
    )

/** Converts the portable shared category model back into an Android Room entity. */
fun EventCategory.toRoomCategory(): Category =
    toRoomCategory(RoomIdMapper())

private fun EventCategory.toRoomCategory(idMapper: RoomIdMapper): Category =
    Category(
        id = idMapper.uuidFor(id),
        raceId = idMapper.uuidFor(raceId),
        name = name,
        isMan = StandardCategoryRules.inferIsManFromName(name) ?: isMan,
        maxAge = maxAge,
        length = lengthMeters,
        climb = climbMeters,
        order = order,
        differentProperties = false,
        raceType = null,
        categoryBand = null,
        timeLimit = null,
        controlPointsString = controlPointsString
    )

/** Converts the portable shared control-point model back into an Android Room entity. */
fun EventControlPoint.toRoomControlPoint(): ControlPoint =
    toRoomControlPoint(RoomIdMapper(), emptyMap())

private fun EventControlPoint.toRoomControlPoint(
    idMapper: RoomIdMapper,
    controlsById: Map<String, EventControl>
): ControlPoint {
    val control = controlsById[controlId]
    return ControlPoint(
        id = idMapper.uuidFor(id),
        categoryId = idMapper.uuidFor(categoryId),
        siCode = control?.siCode ?: siCode,
        type = control?.type ?: type,
        order = order
    )
}

/** Converts the portable shared alias model back into an Android Room entity. */
fun EventAlias.toRoomAlias(): Alias =
    toRoomAlias(RoomIdMapper())

private fun EventAlias.toRoomAlias(idMapper: RoomIdMapper): Alias =
    Alias(
        id = idMapper.uuidFor(id),
        raceId = idMapper.uuidFor(raceId),
        siCode = siCode,
        name = name
    )

private fun EventControl.toRoomAlias(idMapper: RoomIdMapper): Alias =
    Alias(
        id = idMapper.uuidFor("alias-from-$id"),
        raceId = idMapper.uuidFor(raceId),
        siCode = siCode,
        name = publicLabel?.takeIf { it.isNotBlank() } ?: label
    )

/** Converts the portable shared competitor model back into an Android Room entity. */
fun EventCompetitor.toRoomCompetitor(): Competitor =
    toRoomCompetitor(RoomIdMapper())

private fun EventCompetitor.toRoomCompetitor(idMapper: RoomIdMapper): Competitor =
    Competitor(
        id = idMapper.uuidFor(id),
        raceId = idMapper.uuidFor(raceId),
        categoryId = categoryId?.let { idMapper.uuidFor(it) },
        firstName = firstName,
        lastName = lastName,
        club = club,
        index = index,
        isMan = isMan,
        birthYear = birthYear,
        siNumber = siNumber,
        siRent = siRent,
        startNumber = startNumber ?: 0,
        drawnRelativeStartTime = drawnStartTimeSeconds?.let(Duration::ofSeconds)
    )

/** Converts the portable shared punch model back into an Android Room entity. */
fun EventPunch.toRoomPunch(): Punch =
    toRoomPunch(RoomIdMapper())

private fun EventPunch.toRoomPunch(idMapper: RoomIdMapper): Punch =
    Punch(
        id = idMapper.uuidFor(id),
        raceId = idMapper.uuidFor(raceId),
        resultId = resultId?.let { idMapper.uuidFor(it) },
        cardNumber = cardNumber,
        siCode = siCode,
        siTime = org.openardf.radiooracle.backend.sportident.SITime(siTimeSeconds),
        origSiTime = org.openardf.radiooracle.backend.sportident.SITime(originalSiTimeSeconds),
        punchType = punchType,
        order = order,
        punchStatus = punchStatus,
        split = Duration.ofSeconds(splitSeconds)
    )

/** Converts the portable shared result model back into an Android Room entity. */
fun EventResult.toRoomResult(): Result =
    toRoomResult(RoomIdMapper())

private fun EventResult.toRoomResult(idMapper: RoomIdMapper): Result =
    Result(
        id = idMapper.uuidFor(id),
        raceId = idMapper.uuidFor(raceId),
        competitorId = competitorId?.let { idMapper.uuidFor(it) },
        siNumber = siNumber,
        cardType = cardType,
        checkTime = checkTimeSeconds?.let { org.openardf.radiooracle.backend.sportident.SITime(it) },
        startTime = startTimeSeconds?.let { org.openardf.radiooracle.backend.sportident.SITime(it) },
        finishTime = finishTimeSeconds?.let { org.openardf.radiooracle.backend.sportident.SITime(it) },
        readoutTime = LocalDateTime.parse(readoutDateTimeIso),
        automaticStatus = automaticStatus,
        resultStatus = resultStatus,
        points = points,
        runTime = Duration.ofSeconds(runTimeSeconds),
        modified = modified,
        sent = sent,
        cardName = cardName
    ).also { it.place = place }

/** Converts the portable shared alias-punch model back into an Android relation object. */
fun EventAliasPunch.toRoomAliasPunch(): AliasPunch =
    toRoomAliasPunch(RoomIdMapper())

private fun EventAliasPunch.toRoomAliasPunch(idMapper: RoomIdMapper): AliasPunch =
    AliasPunch(
        punch = punch.toRoomPunch(idMapper),
        alias = alias?.toRoomAlias(idMapper)
    )

/** Converts the portable shared readout model back into an Android aggregate. */
fun EventReadoutData.toRoomReadoutData(): ReadoutData =
    toRoomReadoutData(RoomIdMapper())

private fun EventReadoutData.toRoomReadoutData(idMapper: RoomIdMapper): ReadoutData =
    ReadoutData(
        result = result.toRoomResult(idMapper),
        punches = punches.map { it.toRoomAliasPunch(idMapper) }
    )

/** Converts the portable shared category aggregate back into an Android aggregate. */
fun EventCategoryData.toRoomCategoryData(): CategoryData =
    toRoomCategoryData(RoomIdMapper(), emptyMap(), null)

/** Converts an imported IOF course into Android data without applying ARDF control-order rules. */
fun EventCategoryData.toRoomCategoryDataPreservingControlOrder(): CategoryData {
    val idMapper = RoomIdMapper()
    val controlPoints = controlPoints
        .sortedBy { it.order }
        .mapIndexed { index, controlPoint ->
            controlPoint.toRoomControlPoint(idMapper, emptyMap()).also { roomControlPoint ->
                roomControlPoint.order = index + 1
            }
        }
    val category = category.toRoomCategory(idMapper).also { roomCategory ->
        roomCategory.controlPointsString = ControlPointRules.formatControlPoints(
            controlPoints.map { ControlPointDefinition(it.siCode, it.type, it.order) }
        )
    }
    return CategoryData(
        category = category,
        controlPoints = controlPoints,
        competitors = competitors.map { it.toRoomCompetitor(idMapper) }
    )
}

private fun EventCategoryData.toRoomCategoryData(
    idMapper: RoomIdMapper,
    controlsById: Map<String, EventControl>,
    race: EventRace?
): CategoryData {
    val raceType = race?.raceType ?: RaceType.CLASSIC
    val controlPoints = androidImportControlPoints(controlsById, raceType)
        .mapIndexed { index, controlPoint ->
            controlPoint.toRoomControlPoint(idMapper, controlsById).also { roomControlPoint ->
                roomControlPoint.order = index + 1
            }
        }
    val category = category.toRoomCategory(idMapper).also { roomCategory ->
        if (roomCategory.controlPointsString.isBlank() || raceType != RaceType.ORIENTEERING) {
            roomCategory.controlPointsString = ControlPointRules.formatControlPoints(
                controlPoints.map { ControlPointDefinition(it.siCode, it.type, it.order) }
            )
        }
    }
    return CategoryData(
        category = category,
        controlPoints = controlPoints,
        competitors = competitors.map { it.toRoomCompetitor(idMapper) }
    )
}

private fun EventCategoryData.androidImportControlPoints(
    controlsById: Map<String, EventControl>,
    raceType: RaceType
): List<EventControlPoint> {
    val pointsByControlId = controlPoints
        .filter { it.controlId.isNotBlank() }
        .associateBy { it.controlId }
    val publicPoints = publicControlIds.mapIndexedNotNull { index, controlId ->
        pointsByControlId[controlId]
            ?: controlsById[controlId]?.let { control ->
                EventControlPoint(
                    id = "${category.id}-android-public-control-$index",
                    categoryId = category.id,
                    siCode = control.siCode,
                    type = control.type,
                    order = index + 1,
                    controlId = control.id
                )
            }
    }
    val source = publicPoints.ifEmpty { controlPoints }
    return EventAssignedControlOrder.sort(source, controlsById, raceType)
}

/** Converts the portable shared competitor/category model back into an Android relation object. */
fun EventCompetitorCategory.toRoomCompetitorCategory(): CompetitorCategory =
    toRoomCompetitorCategory(RoomIdMapper())

private fun EventCompetitorCategory.toRoomCompetitorCategory(idMapper: RoomIdMapper): CompetitorCategory =
    CompetitorCategory(
        competitor = competitor.toRoomCompetitor(idMapper),
        category = category?.toRoomCategory(idMapper)
    )

/** Converts the portable shared competitor aggregate back into an Android aggregate. */
fun EventCompetitorData.toRoomCompetitorData(): CompetitorData =
    toRoomCompetitorData(RoomIdMapper())

private fun EventCompetitorData.toRoomCompetitorData(idMapper: RoomIdMapper): CompetitorData =
    CompetitorData(
        competitorCategory = competitorCategory.toRoomCompetitorCategory(idMapper),
        readoutData = readoutData?.toRoomReadoutData(idMapper)
    )

/** Converts a complete portable shared race aggregate back into Android Room aggregates. */
fun EventRaceData.toRoomRaceData(): RaceData {
    val idMapper = RoomIdMapper()
    val controlsById = controls.associateBy { it.id }
    return RaceData(
        race = race.toRoomRace(idMapper),
        categories = categories.map { it.toRoomCategoryData(idMapper, controlsById, race) },
        aliases = androidCompatibleAliases(idMapper),
        competitorData = competitorData.map { it.toRoomCompetitorData(idMapper) },
        unmatchedReadoutData = unmatchedReadoutData.map { it.toRoomReadoutData(idMapper) }
    )
}

private fun EventRaceData.androidCompatibleAliases(idMapper: RoomIdMapper): List<Alias> {
    val existingAliases = aliases.map { it.toRoomAlias(idMapper) }
    val existingCodes = existingAliases.map { it.siCode }.toSet()
    val controlAliases = controls
        .filter { control -> control.label.isNotBlank() || !control.publicLabel.isNullOrBlank() }
        .map { it.toRoomAlias(idMapper) }
        .filterNot { it.siCode in existingCodes }
        .distinctBy { it.siCode }
    return (existingAliases + controlAliases)
        .distinctBy { it.siCode }
}

private class RoomIdMapper {
    private val ids = mutableMapOf<String, UUID>()

    fun uuidFor(id: String): UUID =
        ids.getOrPut(id) {
            runCatching { UUID.fromString(id) }
                .getOrElse { UUID.nameUUIDFromBytes(id.toByteArray(Charsets.UTF_8)) }
        }
}
