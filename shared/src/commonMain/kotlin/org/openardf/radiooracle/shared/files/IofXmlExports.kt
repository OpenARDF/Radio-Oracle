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

package org.openardf.radiooracle.shared.files

import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.PunchStatus
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.shared.event.EventAssignedControlOrder
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventCompetitor
import org.openardf.radiooracle.shared.event.EventCompetitorData
import org.openardf.radiooracle.shared.event.EventControl
import org.openardf.radiooracle.shared.event.EventControlPoint
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.ProtectedCourseControlPoint
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.event.PublicResultsPublicationStatus
import org.openardf.radiooracle.shared.event.ProtectedCourseObjectPoint
import org.openardf.radiooracle.shared.event.ProtectedCourseObjectType
import org.openardf.radiooracle.shared.event.competitionCategories
import org.openardf.radiooracle.shared.event.effectiveLengthMeters
import org.openardf.radiooracle.shared.results.EventResultPlacement
import org.openardf.radiooracle.shared.results.IofResultStatus
import org.openardf.radiooracle.shared.publicresults.PublicResultsPublicationRules
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Shared IOF XML 3.0 export builders for desktop and Android flows. */
object IofXmlExports {
    private const val IOF_NAMESPACE = "http://www.orienteering.org/datastandard/3.0"

    fun courseData(
        raceData: EventRaceData,
        creator: String = "Radio-Oracle Desktop",
        protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>? = null
    ): String {
        val raceStart = parseRaceStart(raceData.race.startDateTimeIso)
        val controlsById = raceData.controls.associateBy { it.id }
        val categoryCourses = raceData.categories
            .sortedWith(compareBy({ it.category.order }, { it.category.name }))
            .map { categoryData ->
                categoryData.toIofCourse(
                    raceData = raceData,
                    controlsById = controlsById,
                    protectedCourseInfo = protectedCourseInfoByCategoryId?.get(categoryData.category.id)
                )
            }
        val controlDefinitions = categoryCourses.controlDefinitions(
            controls = raceData.controls,
            protectedCourseInfoByCategoryId = protectedCourseInfoByCategoryId.orEmpty()
        )
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""")
            append('\n')
            append("""<CourseData xmlns="$IOF_NAMESPACE" iofVersion="3.0" creator="${creator.xmlEscaped()}">""")
            append('\n')
            appendEvent(raceData, raceStart)
            append("  <RaceCourseData>\n")
            controlDefinitions.forEach { control -> appendControlDefinition(control) }
            categoryCourses.forEach { course -> appendCourse(course) }
            categoryCourses.forEach { course -> appendClassCourseAssignment(course) }
            append("  </RaceCourseData>\n")
            append("</CourseData>\n")
        }
    }

    fun entryList(raceData: EventRaceData, creator: String = "Radio-Oracle Desktop"): String {
        val raceStart = parseRaceStart(raceData.race.startDateTimeIso)
        val categoriesById = raceData.categories.associateBy { it.category.id }
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""")
            append('\n')
            append("""<EntryList xmlns="$IOF_NAMESPACE" iofVersion="3.0" creator="${creator.xmlEscaped()}">""")
            append('\n')
            appendEvent(raceData, raceStart)
            raceData.competitorData
                .map { it.competitorCategory.competitor }
                .sortedWith(compareBy<EventCompetitor>({ categoriesById[it.categoryId]?.category?.order ?: Int.MAX_VALUE }, { it.startNumber ?: Int.MAX_VALUE }, { it.fullName() }))
                .forEach { competitor ->
                    appendPersonEntry(competitor, categoriesById[competitor.categoryId])
                }
            append("</EntryList>\n")
        }
    }

    fun startList(
        raceData: EventRaceData,
        creator: String = "Radio-Oracle Desktop",
        protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>? = null
    ): String {
        val raceStart = parseRaceStart(raceData.race.startDateTimeIso)
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""")
            append('\n')
            append("""<StartList xmlns="$IOF_NAMESPACE" iofVersion="3.0" creator="${creator.xmlEscaped()}">""")
            append('\n')
            appendEvent(raceData, raceStart)
            raceData.competitionCategories(includeResultCategoryIds = false)
                .sortedWith(compareBy({ it.category.order }, { it.category.name }))
                .forEach { categoryData ->
                    appendClassStart(
                        categoryData,
                        raceData.competitorsFor(categoryData),
                        raceStart,
                        protectedCourseInfoByCategoryId
                    )
                }
            append("</StartList>\n")
        }
    }

    fun resultList(
        raceData: EventRaceData,
        creator: String = "Radio-Oracle Desktop",
        publicationStatus: PublicResultsPublicationStatus? = null
    ): String {
        publicationStatus?.let { PublicResultsPublicationRules.requireReady(raceData, it) }
        val raceStart = parseRaceStart(raceData.race.startDateTimeIso)
        val placedByCategory = raceData.competitorData
            .groupBy { it.resultCategoryId() }
            .mapValues { (_, categoryCompetitors) -> EventResultPlacement.sortByPlace(categoryCompetitors) }
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""")
            append('\n')
            val resultListStatus = if (publicationStatus != PublicResultsPublicationStatus.PRELIMINARY) {
                "Complete"
            } else {
                "Snapshot"
            }
            append("""<ResultList xmlns="$IOF_NAMESPACE" iofVersion="3.0" creator="${creator.xmlEscaped()}" status="$resultListStatus">""")
            append('\n')
            appendEvent(raceData, raceStart)
            raceData.competitionCategories()
                .sortedWith(compareBy({ it.category.order }, { it.category.name }))
                .forEach { categoryData ->
                    appendClassResult(
                        categoryData = categoryData,
                        competitorData = placedByCategory[categoryData.category.id] ?: emptyList(),
                        raceStart = raceStart,
                        raceData = raceData
                    )
                }
            append("</ResultList>\n")
        }
    }

    private fun EventCompetitorData.resultCategoryId(): String? =
        readoutData?.result?.categoryId ?: competitorCategory.category?.id ?: competitorCategory.competitor.categoryId

    private fun StringBuilder.appendCourse(course: IofCategoryCourse) {
        append("    <Course>\n")
        appendTextElement("Name", course.name, indent = "      ")
        if (course.lengthMeters > 0) {
            appendTextElement("Length", course.lengthMeters.toString(), indent = "      ")
        }
        if (course.climbMeters > 0) {
            appendTextElement("Climb", course.climbMeters.toString(), indent = "      ")
        }
        appendCourseControl(code = "S", type = "Start")
        course.controls.forEach { control ->
            appendCourseControl(
                code = control.code,
                type = "Control",
                randomOrder = control.randomOrder
            )
        }
        appendCourseControl(code = "F", type = "Finish")
        append("    </Course>\n")
    }

    private fun StringBuilder.appendCourseControl(code: String, type: String, randomOrder: Boolean = false) {
        append("""      <CourseControl type="$type"""")
        if (randomOrder) {
            append(""" randomOrder="true"""")
        }
        append('>')
        append('\n')
        appendTextElement("Control", code, indent = "        ")
        append("      </CourseControl>\n")
    }

    private fun StringBuilder.appendControlDefinition(control: IofControlDefinition) {
        append("""    <Control type="${control.iofType}">""")
        append('\n')
        appendTextElement("Id", control.code, indent = "      ")
        control.name.takeIf { it.isNotBlank() }?.let { name ->
            appendTextElement("Name", name, indent = "      ")
        }
        control.position?.let { position ->
            append("""      <Position lng="${position.longitude}" lat="${position.latitude}"""")
            position.elevationMeters?.let { elevation ->
                append(" alt=\"")
                append(elevation)
                append('"')
            }
            append("/>\n")
        }
        append("    </Control>\n")
    }

    private fun StringBuilder.appendClassCourseAssignment(course: IofCategoryCourse) {
        append("    <ClassCourseAssignment>\n")
        appendTextElement("ClassId", course.categoryId, indent = "      ")
        appendTextElement("ClassName", course.name, indent = "      ")
        appendTextElement("CourseName", course.name, indent = "      ")
        append("    </ClassCourseAssignment>\n")
    }

    @Suppress("DEPRECATION")
    private fun EventCategoryData.toIofCourse(
        raceData: EventRaceData,
        controlsById: Map<String, EventControl>,
        protectedCourseInfo: ProtectedCourseInfo?
    ): IofCategoryCourse {
        val assignedPoints = when {
            controlPoints.isNotEmpty() -> controlPoints
            publicControlIds.isNotEmpty() -> publicControlIds.mapIndexedNotNull { index, controlId ->
                val control = controlsById[controlId] ?: return@mapIndexedNotNull null
                EventControlPoint(
                    id = "public-$controlId",
                    categoryId = category.id,
                    siCode = control.siCode,
                    type = control.type,
                    order = index + 1,
                    controlId = control.id
                )
            }
            else -> emptyList()
        }
        val raceType = category.effectiveRaceType(raceData.race)
        val controlsByLegacyDefinition = raceData.controls
            .groupBy { it.siCode to it.type }
            .mapNotNull { (definition, controls) -> controls.singleOrNull()?.let { definition to it } }
            .toMap()
        val controls = EventAssignedControlOrder.sort(assignedPoints, controlsById, raceType)
            .mapNotNull { point ->
                val control = controlsById[point.controlId]
                    ?: controlsByLegacyDefinition[point.siCode to point.type]
                val siCode = control?.siCode ?: point.siCode
                if (siCode <= 0) {
                    return@mapNotNull null
                }
                val type = control?.type ?: point.type
                IofAssignedControl(
                    code = siCode.toString(),
                    name = control?.displayCourseLabel().orEmpty().ifBlank { siCode.toString() },
                    type = type,
                    controlId = control?.id ?: point.controlId,
                    randomOrder = raceType != RaceType.ORIENTEERING && type == ControlPointType.CONTROL
                )
            }
        return IofCategoryCourse(
            categoryId = category.id,
            name = category.name,
            lengthMeters = protectedCourseInfo?.lengthMeters ?: category.lengthMeters,
            climbMeters = protectedCourseInfo?.climbMeters ?: category.climbMeters,
            controls = controls
        )
    }

    private fun List<IofCategoryCourse>.controlDefinitions(
        controls: List<EventControl>,
        protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>
    ): List<IofControlDefinition> {
        val controlsById = controls.associateBy { it.id }
        val protectedInfosInCourseOrder = mapNotNull { course ->
            protectedCourseInfoByCategoryId[course.categoryId]
        }
        val definitions = linkedMapOf<String, IofControlDefinition>()
        definitions["S"] = IofControlDefinition(
            code = "S",
            name = "Start",
            iofType = "Start",
            position = protectedInfosInCourseOrder.firstNotNullOfOrNull { it.startPosition() }
        )
        forEach { course ->
            val categoryInfo = protectedCourseInfoByCategoryId[course.categoryId]
            course.controls.forEach controlLoop@ { assignedControl ->
                if (assignedControl.code in definitions) {
                    return@controlLoop
                }
                val control = controlsById[assignedControl.controlId]
                definitions[assignedControl.code] = IofControlDefinition(
                    code = assignedControl.code,
                    name = assignedControl.name,
                    iofType = "Control",
                    position = assignedControl.positionFrom(
                        control = control,
                        categoryInfo = categoryInfo,
                        allCourseInfos = protectedInfosInCourseOrder
                    )
                )
            }
        }
        definitions["F"] = IofControlDefinition(
            code = "F",
            name = "Finish",
            iofType = "Finish",
            position = protectedInfosInCourseOrder.firstNotNullOfOrNull { it.finishPosition() }
        )
        return definitions.values.toList()
    }

    private fun IofAssignedControl.positionFrom(
        control: EventControl?,
        categoryInfo: ProtectedCourseInfo?,
        allCourseInfos: List<ProtectedCourseInfo>
    ): IofGeoPosition? {
        val matchingInfos = buildList {
            categoryInfo?.let(::add)
            allCourseInfos.filterTo(this) { it !== categoryInfo }
        }
        matchingInfos.forEach { courseInfo ->
            courseInfo.courseObjects
                .firstOrNull { point -> point.id == controlId && point.type.matches(type) }
                ?.toPosition()
                ?.let { return it }
            courseInfo.controlPoints
                .firstOrNull { point -> point.controlId == controlId && point.type == type }
                ?.toPosition()
                ?.let { return it }
        }
        val normalizedLabels = listOfNotNull(
            name,
            control?.displayCourseLabel(),
            control?.label
        )
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()
        matchingInfos.forEach { courseInfo ->
            val candidates = courseInfo.courseObjects
                .filter { point ->
                    point.type.matches(type) && point.label.trim().lowercase() in normalizedLabels
                }
                .mapNotNull { point -> point.toPosition() } +
                courseInfo.controlPoints
                    .filter { point ->
                        point.type == type && point.label.trim().lowercase() in normalizedLabels
                    }
                    .mapNotNull { point -> point.toPosition() }
            candidates.firstOrNull()?.let { return it }
        }
        return IofGeoPosition.of(control?.latitude, control?.longitude, elevationMeters = null)
    }

    private fun ProtectedCourseInfo.startPosition(): IofGeoPosition? =
        courseObjects.firstNotNullOfOrNull { point ->
            point.takeIf { it.type == ProtectedCourseObjectType.START }?.toPosition()
        } ?: route.firstNotNullOfOrNull { point ->
            IofGeoPosition.of(point.latitude, point.longitude, point.elevationMeters)
        }

    private fun ProtectedCourseInfo.finishPosition(): IofGeoPosition? =
        courseObjects.firstNotNullOfOrNull { point ->
            point.takeIf { it.type == ProtectedCourseObjectType.FINISH }?.toPosition()
        } ?: route.asReversed().firstNotNullOfOrNull { point ->
            IofGeoPosition.of(point.latitude, point.longitude, point.elevationMeters)
        }

    private fun ProtectedCourseObjectPoint.toPosition(): IofGeoPosition? =
        IofGeoPosition.of(latitude, longitude, elevationMeters)

    private fun ProtectedCourseControlPoint.toPosition(): IofGeoPosition? =
        IofGeoPosition.of(latitude, longitude, elevationMeters)

    private fun ProtectedCourseObjectType.matches(type: ControlPointType): Boolean = when (this) {
        ProtectedCourseObjectType.CONTROL -> type == ControlPointType.CONTROL
        ProtectedCourseObjectType.BEACON -> type == ControlPointType.BEACON
        ProtectedCourseObjectType.SPECTATOR -> type == ControlPointType.SEPARATOR
        ProtectedCourseObjectType.START,
        ProtectedCourseObjectType.FINISH,
        ProtectedCourseObjectType.WAYPOINT -> false
    }

    private fun EventControl.displayCourseLabel(): String =
        publicLabel?.takeIf { it.isNotBlank() } ?: label

    private fun StringBuilder.appendEvent(raceData: EventRaceData, raceStart: LocalDateTime) {
        append("  <Event>\n")
        appendTextElement("Name", raceData.race.name, indent = "    ")
        append("    <StartTime>\n")
        appendTextElement("Date", raceStart.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE), indent = "      ")
        appendTextElement("Time", raceStart.toLocalTime().format(DateTimeFormatter.ISO_LOCAL_TIME), indent = "      ")
        append("    </StartTime>\n")
        append("  </Event>\n")
    }

    private fun StringBuilder.appendClassStart(
        categoryData: EventCategoryData,
        competitors: List<EventCompetitor>,
        raceStart: LocalDateTime,
        protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>?
    ) {
        val protectedCourseInfo = protectedCourseInfoByCategoryId?.get(categoryData.category.id)
        val protectedEffectiveLength = protectedCourseInfo?.effectiveLengthMeters()
        append("  <ClassStart>\n")
        append("    <Class>\n")
        appendTextElement("Name", categoryData.category.name, indent = "      ")
        append("    </Class>\n")
        if (protectedCourseInfoByCategoryId == null || protectedEffectiveLength != null) {
            append("    <Course>\n")
            if (protectedCourseInfoByCategoryId == null) {
                appendTextElement("Length", categoryData.category.lengthMeters.toString(), indent = "      ")
                appendTextElement("Climb", categoryData.category.climbMeters.toString(), indent = "      ")
            } else {
                appendTextElement("Length", protectedEffectiveLength.toString(), indent = "      ")
                protectedCourseInfo?.climbMeters?.let { climb ->
                    appendTextElement("Climb", climb.toString(), indent = "      ")
                }
            }
            append("    </Course>\n")
        }
        competitors
            .sortedWith(compareBy({ it.drawnStartTimeSeconds ?: 0L }, { it.startNumber }, { it.fullName() }))
            .forEach { competitor ->
                appendPersonStart(competitor, raceStart)
            }
        append("  </ClassStart>\n")
    }

    private fun StringBuilder.appendPersonEntry(competitor: EventCompetitor, categoryData: EventCategoryData?) {
        append("  <PersonEntry>\n")
        appendPersonAndOrganisation(competitor, indent = "    ", includeSex = true)
        competitor.siNumber?.let { siNumber ->
            appendTextElement("ControlCard", siNumber.toString(), indent = "    ")
        }
        categoryData?.let { data ->
            append("    <Class>\n")
            appendTextElement("Name", data.category.name, indent = "      ")
            append("    </Class>\n")
        }
        append("  </PersonEntry>\n")
    }

    private fun StringBuilder.appendPersonStart(competitor: EventCompetitor, raceStart: LocalDateTime) {
        append("    <PersonStart>\n")
        appendPersonAndOrganisation(competitor, indent = "      ")
        append("      <Start>\n")
        competitor.bibNumber.takeIf { it.isIofBibNumber() }?.let { bibNumber ->
            appendTextElement("BibNumber", bibNumber, indent = "        ")
        }
        appendTextElement(
            "StartTime",
            raceStart.plusSeconds(competitor.drawnStartTimeSeconds ?: 0L).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            indent = "        "
        )
        competitor.siNumber?.let { siNumber ->
            appendTextElement("ControlCard", siNumber.toString(), indent = "        ")
        }
        append("      </Start>\n")
        append("    </PersonStart>\n")
    }

    private fun StringBuilder.appendClassResult(
        categoryData: EventCategoryData,
        competitorData: List<EventCompetitorData>,
        raceStart: LocalDateTime,
        raceData: EventRaceData
    ) {
        append("  <ClassResult>\n")
        append("""    <Class sex="${if (categoryData.category.isMan) "M" else "F"}">""")
        append('\n')
        appendTextElement("Name", categoryData.category.name, indent = "      ")
        append("    </Class>\n")
        val assignedControlCodes = categoryData.toIofCourse(
            raceData = raceData,
            controlsById = raceData.controls.associateBy { it.id },
            protectedCourseInfo = null
        ).controls.map { it.code }
        competitorData.forEach { appendPersonResult(it, raceStart, assignedControlCodes) }
        append("  </ClassResult>\n")
    }

    private fun StringBuilder.appendPersonResult(
        competitorData: EventCompetitorData,
        raceStart: LocalDateTime,
        assignedControlCodes: List<String>
    ) {
        append("    <PersonResult>\n")
        val competitor = competitorData.competitorCategory.competitor
        appendPersonAndOrganisation(competitor, indent = "      ")
        append("      <Result>\n")
        competitor.bibNumber.takeIf { it.isIofBibNumber() }?.let { bibNumber ->
            appendTextElement("BibNumber", bibNumber, indent = "        ")
        }
        val readoutData = competitorData.readoutData
        if (readoutData == null) {
            appendTextElement("Status", "Active", indent = "        ")
        } else {
            val result = readoutData.result
            result.startTimeSeconds?.let { seconds ->
                appendTextElement("StartTime", seconds.toRaceDateTime(raceStart), indent = "        ")
            }
            result.finishTimeSeconds?.let { seconds ->
                appendTextElement("FinishTime", seconds.toRaceDateTime(raceStart), indent = "        ")
            }
            appendTextElement("Time", result.runTimeSeconds.toString(), indent = "        ")
            if (result.place > 0 && result.resultStatus == ResultStatus.OK) {
                appendTextElement("Position", result.place.toString(), indent = "        ")
            }
            appendTextElement("Status", IofResultStatus.fromResultStatus(result.resultStatus), indent = "        ")
        }
        appendResultSplitTimes(readoutData, assignedControlCodes)
        (readoutData?.result?.siNumber?.takeIf { it > 0 } ?: competitor.siNumber)?.let { siNumber ->
            appendTextElement("ControlCard", siNumber.toString(), indent = "        ")
        }
        append("      </Result>\n")
        append("    </PersonResult>\n")
    }

    private fun StringBuilder.appendResultSplitTimes(
        readoutData: org.openardf.radiooracle.shared.event.EventReadoutData?,
        assignedControlCodes: List<String>
    ) {
        val assigned = assignedControlCodes.toSet()
        val successfullyVisited = mutableSetOf<String>()
        var cumulativeSplitSeconds = 0L
        readoutData?.punches
            ?.filter { it.punch.punchType == SIRecordType.CONTROL }
            ?.forEach { aliasPunch ->
                cumulativeSplitSeconds += aliasPunch.punch.splitSeconds
                val controlCode = aliasPunch.punch.siCode.toString()
                val isFirstValidAssignedPunch =
                    assigned.isEmpty() ||
                        (controlCode in assigned &&
                            aliasPunch.punch.punchStatus == PunchStatus.VALID &&
                            successfullyVisited.add(controlCode))
                append("        <SplitTime")
                if (!isFirstValidAssignedPunch) {
                    append(" status=\"Additional\"")
                }
                append(">\n")
                appendTextElement("ControlCode", controlCode, indent = "          ")
                appendTextElement("Time", cumulativeSplitSeconds.toString(), indent = "          ")
                append("        </SplitTime>\n")
            }
        assignedControlCodes
            .filterNot(successfullyVisited::contains)
            .forEach { controlCode ->
                append("        <SplitTime status=\"Missing\">\n")
                appendTextElement("ControlCode", controlCode, indent = "          ")
                append("        </SplitTime>\n")
            }
    }

    private fun StringBuilder.appendPersonAndOrganisation(
        competitor: EventCompetitor,
        indent: String,
        includeSex: Boolean = false
    ) {
        append(indent)
        if (includeSex) {
            append("""<Person sex="${if (competitor.isMan) "M" else "F"}">""")
            append('\n')
        } else {
            append("<Person>\n")
        }
        if (competitor.index.isNotBlank()) {
            append(indent)
            append("""  <Id type="CZE">${competitor.index.xmlEscaped()}</Id>""")
            append('\n')
        }
        append(indent)
        append("  <Name>\n")
        appendTextElement("Family", competitor.lastName, indent = "$indent    ")
        appendTextElement("Given", competitor.firstName, indent = "$indent    ")
        append(indent)
        append("  </Name>\n")
        append(indent)
        append("</Person>\n")
        if (competitor.club.isNotBlank()) {
            append(indent)
            append("<Organisation>\n")
            appendTextElement("Name", competitor.club, indent = "$indent  ")
            append(indent)
            append("</Organisation>\n")
        }
    }

    private fun StringBuilder.appendTextElement(name: String, value: String, indent: String) {
        append(indent)
        append('<')
        append(name)
        append('>')
        append(value.xmlEscaped())
        append("</")
        append(name)
        append(">\n")
    }

    private fun EventRaceData.competitorsFor(categoryData: EventCategoryData): List<EventCompetitor> =
        competitorData
            .map { it.competitorCategory.competitor }
            .filter { it.categoryId == categoryData.category.id }

    private fun parseRaceStart(value: String): LocalDateTime =
        LocalDateTime.parse(value.trim().replace(' ', 'T'))

    private fun Long.toRaceDateTime(raceStart: LocalDateTime): String {
        val secondsInDay = 24 * 60 * 60
        val normalized = ((this % secondsInDay) + secondsInDay) % secondsInDay
        return raceStart.toLocalDate()
            .atStartOfDay()
            .plusSeconds(normalized)
            .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    }

    private fun String.isIofBibNumber(): Boolean =
        isNotBlank() && all { it.isDigit() }

    private fun String.xmlEscaped(): String =
        buildString {
            this@xmlEscaped.forEach { char ->
                when (char) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&apos;")
                    else -> append(char)
                }
            }
        }
}

private data class IofCategoryCourse(
    val categoryId: String,
    val name: String,
    val lengthMeters: Int,
    val climbMeters: Int,
    val controls: List<IofAssignedControl>
)

private data class IofAssignedControl(
    val code: String,
    val name: String,
    val type: ControlPointType,
    val controlId: String,
    val randomOrder: Boolean
)

private data class IofControlDefinition(
    val code: String,
    val name: String,
    val iofType: String,
    val position: IofGeoPosition?
)

private data class IofGeoPosition(
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double?
) {
    companion object {
        fun of(latitude: Double?, longitude: Double?, elevationMeters: Double?): IofGeoPosition? {
            val validLatitude = latitude?.takeIf { it.isFinite() && it in -90.0..90.0 } ?: return null
            val validLongitude = longitude?.takeIf { it.isFinite() && it in -180.0..180.0 } ?: return null
            return IofGeoPosition(
                latitude = validLatitude,
                longitude = validLongitude,
                elevationMeters = elevationMeters?.takeIf(Double::isFinite)
            )
        }
    }
}
