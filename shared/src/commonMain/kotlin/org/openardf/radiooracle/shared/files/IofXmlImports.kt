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
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.openardf.radiooracle.shared.files

import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.*
import org.openardf.radiooracle.shared.course.ControlPointDefinition
import kotlin.math.roundToInt
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventControlPoint
import org.openardf.radiooracle.shared.event.EventRace
import org.openardf.radiooracle.shared.event.StandardCategoryRules

private const val IOF_VERSION = "3.0"

/** Severity for schema-valid IOF data that Radio-Oracle cannot fully represent yet. */
enum class IofXmlImportSeverity {
    WARNING,
    UNSUPPORTED
}

/** One import finding for supported or unsupported IOF XML content. */
data class IofXmlUnsupportedItem(
    val messageType: String,
    val location: String,
    val reason: String,
    val severity: IofXmlImportSeverity = IofXmlImportSeverity.WARNING
)

/** Generic IOF import result with parsed data and schema-valid content Radio-Oracle did not apply. */
data class IofXmlImportResult<T>(
    val parsedData: T,
    val unsupportedItems: List<IofXmlUnsupportedItem>
)

/** Shared preview payload for IOF CourseData imports. */
data class IofCourseDataPreview(
    val eventName: String?,
    val startDate: String?,
    val startTime: String?,
    val categories: List<EventCategoryData>,
    /** Entries backed by explicit XML ClassCourseAssignment elements become race categories. */
    val assignedCategoryIds: Set<String> = emptySet(),
    /** Ordinary, unnamed numeric controls carry no explicit ARDF role in IOF XML. */
    val unspecifiedRoleSiCodes: Set<Int> = emptySet(),
    /** Public names explicitly confirmed in the control-mapping review. */
    val reviewedControlNames: Map<Int, String> = emptyMap()
)

typealias IofCourseDataImportResult = IofXmlImportResult<IofCourseDataPreview>

/** IOF person identity fields common to start and result import previews. */
data class IofPersonPreview(
    val personId: String?,
    val personIdType: String?,
    val familyName: String,
    val givenName: String,
    val organisationName: String?
) {
    val displayName: String = listOf(givenName, familyName)
        .filter { it.isNotBlank() }
        .joinToString(" ")
}

/** One competitor start parsed from a valid IOF StartList. */
data class IofStartListEntryPreview(
    val className: String,
    val person: IofPersonPreview,
    val bibNumber: String?,
    val controlCard: Int?,
    val startTimeIso: String?,
    val relativeStartTimeSeconds: Long?
)

/** Shared preview payload for IOF StartList imports. */
data class IofStartListPreview(
    val eventName: String?,
    val startDate: String?,
    val startTime: String?,
    val entries: List<IofStartListEntryPreview>
)

typealias IofStartListImportResult = IofXmlImportResult<IofStartListPreview>

/** One split result parsed from an IOF ResultList row. */
data class IofResultSplitPreview(
    val controlCode: Int,
    val timeSeconds: Long?
)

/** One competitor result parsed from a valid IOF ResultList. */
data class IofResultListEntryPreview(
    val className: String,
    val person: IofPersonPreview,
    val bibNumber: String?,
    val controlCard: Int?,
    val startTimeIso: String?,
    val finishTimeIso: String?,
    val timeSeconds: Long?,
    val position: Int?,
    val status: String,
    val splitControls: List<Int>,
    val splitTimes: List<IofResultSplitPreview> = splitControls.map { IofResultSplitPreview(it, null) }
)

/** Shared preview payload for IOF ResultList imports. */
data class IofResultListPreview(
    val eventName: String?,
    val startDate: String?,
    val startTime: String?,
    val entries: List<IofResultListEntryPreview>
)

typealias IofResultListImportResult = IofXmlImportResult<IofResultListPreview>

/** Shared preview payload for IOF EntryList imports. */
data class IofEntryListPreview(
    val eventName: String?,
    val startDate: String?,
    val startTime: String?,
    val entries: List<CompetitorCsvImportRow>
)

typealias IofEntryListImportResult = IofXmlImportResult<IofEntryListPreview>

/** Thrown when IOF XML cannot be accepted as IOF 3.0 input. */
class IofXmlImportException(message: String) : IllegalArgumentException(message)

/** Shared IOF XML 3.0 import parser for the supported Radio-Oracle subset. */
object IofXmlImports {
    private val supportedRoots = setOf("CourseData", "StartList", "ResultList", "EntryList")

    /** Returns the IOF root name after enforcing XML well-formedness and iofVersion="3.0". */
    fun rootMessageType(xml: String): String {
        val root = parseXml(xml)
        val rootName = root.localName
        if (rootName !in supportedRoots) {
            throw IofXmlImportException("Unsupported IOF XML root element: $rootName")
        }
        val iofVersion = root.attribute("iofVersion")
        if (iofVersion != IOF_VERSION) {
            throw IofXmlImportException("Unsupported IOF XML version: ${iofVersion ?: "missing"}. Expected $IOF_VERSION.")
        }
        return rootName
    }

    /** Returns the IOF root name after enforcing root/version checks and IOF 3.0 schema validity. */
    fun validatedRootMessageType(xml: String, iofSchema: String): String {
        val rootName = rootMessageType(xml)
        IofXmlValidator.requireValid(xml, iofSchema)
        return rootName
    }

    /** Parses the supported CourseData subset into Radio-Oracle category/course data. */
    fun courseData(
        xml: String,
        race: EventRace,
        idFactory: (String) -> String = { seed -> seed.stableIofId() }
    ): IofCourseDataImportResult {
        return parsedCourseData(xml, race, idFactory)
    }

    /** Uses the existing XML parser and course conversion after a complete mapping review. */
    fun courseDataWithControlMappings(
        xml: String,
        race: EventRace,
        mappings: List<IofCourseControlMapping>,
        useRouteBends: Boolean = false,
        idFactory: (String) -> String = { seed -> seed.stableIofId() }
    ): IofCourseDataImportResult {
        val sources = courseControlSources(xml)
        IofCourseControlMappings.requireValid(sources, mappings, useRouteBends)
        val resolved = mappings.map { if (IofCourseControlMappings.isRoutePoint(it.sourceId, useRouteBends)) it.copy(siCode = it.sourceId) else it }
        val parsed = parsedCourseData(xml, race, idFactory, resolved.associateBy { it.sourceId })
        return if (useRouteBends) parsed.copy(parsedData = parsed.parsedData.withCondesRouteBends()) else parsed
    }

    /** Inspection does not require an SI assignment, so aliases can reach the review UI. */
    fun validatedCourseControlSources(xml: String, iofSchema: String): List<IofCourseControlSource> {
        requireValidImportXml(xml, iofSchema, expectedRoot = "CourseData")
        return courseControlSources(xml)
    }

    fun courseControlSources(xml: String): List<IofCourseControlSource> {
        val root = parseXml(xml)
        requireRoot(root, "CourseData")
        val block = root.children("RaceCourseData").firstOrNull()
            ?: throw IofXmlImportException("CourseData does not contain RaceCourseData.")
        val definitions = block.children("Control")
        require(definitions.map { it.childText("Id") }.distinct().size == definitions.size) {
            "Duplicate control identifiers in IOF CourseData. Correct the XML before importing."
        }
        val byId = definitions.associateBy { it.childText("Id").orEmpty() }
        val visits = block.children("Course").flatMap { it.children("CourseControl") }
        require(visits.all { it.children("Control").size == 1 }) {
            "Alternative course controls need a single-course selection before importing."
        }
        return visits.groupBy { it.childText("Control").orEmpty().trim() }.map { (id, nodes) ->
            require(id.isNotBlank()) { "A course control identifier is missing." }
            val definition = byId[id]
            val types = nodes.map { it.attribute("type") ?: definition?.attribute("type") ?: "Control" }.distinct()
            require(types.size == 1) { "XML control $id has conflicting point types across courses." }
            val type = types.single()
            require(type in setOf("Start", "Finish", "Control")) { "Unsupported course-control type $type for $id." }
            IofCourseControlSource(id, type, definition?.children("Name")?.map { it.text.trim() }.orEmpty(),
                definition?.children("PunchingUnitId")?.map { it.text.trim() }.orEmpty())
        }
    }

    private fun parsedCourseData(
        xml: String,
        race: EventRace,
        idFactory: (String) -> String,
        mappings: Map<String, IofCourseControlMapping>? = null
    ): IofCourseDataImportResult {
        val root = parseXml(xml)
        requireRoot(root, "CourseData")
        val warnings = mutableListOf<IofXmlUnsupportedItem>()
        val event = root.child("Event")
        val startTime = event?.child("StartTime")
        val raceCourseDataElements = root.children("RaceCourseData")
        val raceCourseData = raceCourseDataElements.firstOrNull()
            ?: throw IofXmlImportException("CourseData does not contain RaceCourseData.")

        if (raceCourseDataElements.size > 1) {
            warnings += IofXmlUnsupportedItem(
                messageType = "CourseData",
                location = "/CourseData/RaceCourseData[2]",
                reason = "Multiple race course-data blocks are valid IOF data but only the first race is imported by Radio-Oracle.",
                severity = IofXmlImportSeverity.UNSUPPORTED
            )
        }
        raceCourseData.warnUnsupportedChildren(
            childName = "Map",
            location = "/CourseData/RaceCourseData/Map",
            reason = "Race maps are valid IOF data but are not imported into Radio-Oracle courses.",
            warnings = warnings
        )
        raceCourseData.warnUnsupportedChildren(
            childName = "PersonCourseAssignment",
            location = "/CourseData/RaceCourseData/PersonCourseAssignment",
            reason = "Person-course assignments are valid IOF data but are not applied by Radio-Oracle CourseData imports yet.",
            warnings = warnings,
            severity = IofXmlImportSeverity.UNSUPPORTED
        )
        raceCourseData.warnUnsupportedChildren(
            childName = "TeamCourseAssignment",
            location = "/CourseData/RaceCourseData/TeamCourseAssignment",
            reason = "Team and relay course assignments are valid IOF data but are not applied by Radio-Oracle CourseData imports yet.",
            warnings = warnings,
            severity = IofXmlImportSeverity.UNSUPPORTED
        )

        val definitions = raceCourseData.children("Control").associateBy { it.childText("Id").orEmpty() }
        val courseNodes = raceCourseData.children("Course")
        val courses = courseNodes.mapIndexed { index, course ->
            course.toCategoryData(
                race = race,
                index = index,
                idFactory = idFactory,
                warnings = warnings,
                definitions = definitions,
                mappings = mappings
            )
        }
        val (categories, assignedCategoryIds) = assignedCourses(raceCourseData, courseNodes, courses, idFactory, warnings)

        return IofXmlImportResult(
            parsedData = IofCourseDataPreview(
                eventName = event?.childText("Name"),
                startDate = startTime?.childText("Date"),
                startTime = startTime?.childText("Time"),
                categories = categories,
                assignedCategoryIds = assignedCategoryIds,
                unspecifiedRoleSiCodes = definitions.values.filter {
                    (it.attribute("type") ?: "Control") == "Control" &&
                        it.childText("Name").isNullOrBlank() &&
                        ControlRoleLabelRules.inferredSpecialRole(it.childText("Id").orEmpty()) == null
                }.mapNotNull { (it.childText("PunchingUnitId") ?: it.childText("Id"))?.toIntOrNull() }.toSet().takeIf { mappings == null }.orEmpty(),
                reviewedControlNames = mappings.orEmpty().values.filter { it.pointType.controlRole() != null }
                    .associate { it.siCode.trim().toInt() to it.publicName.trim() }
            ),
            unsupportedItems = warnings
        )
    }

    /** Validates CourseData against the IOF 3.0 schema before building a Radio-Oracle preview. */
    fun validatedCourseData(
        xml: String,
        iofSchema: String,
        race: EventRace,
        idFactory: (String) -> String = { seed -> seed.stableIofId() }
    ): IofCourseDataImportResult {
        requireValidImportXml(xml, iofSchema, expectedRoot = "CourseData")
        return courseData(xml, race, idFactory)
    }

    private fun XmlNode.warnUnsupportedChildren(
        childName: String,
        location: String,
        reason: String,
        warnings: MutableList<IofXmlUnsupportedItem>,
        severity: IofXmlImportSeverity = IofXmlImportSeverity.WARNING
    ) {
        val count = children(childName).size
        if (count == 0) return
        warnings += IofXmlUnsupportedItem(
            messageType = "CourseData",
            location = location,
            reason = "$reason ($count element${if (count == 1) "" else "s"} present.)",
            severity = severity
        )
    }

    /** Parses an IOF StartList into a preview that can be matched and applied by platform UI code. */
    fun startList(xml: String): IofStartListImportResult {
        val root = parseXml(xml)
        requireRoot(root, "StartList")
        val warnings = mutableListOf<IofXmlUnsupportedItem>()
        val event = root.child("Event")
        val eventStart = event?.child("StartTime")
        val eventStartDateTime = parseEventStartDateTime(eventStart)

        root.descendants("TeamStart").forEachIndexed { index, _ ->
            warnings += IofXmlUnsupportedItem(
                messageType = "StartList",
                location = "/StartList/TeamStart[${index + 1}]",
                reason = "Team and relay starts are valid IOF data but are not applied by Radio-Oracle start imports yet.",
                severity = IofXmlImportSeverity.UNSUPPORTED
            )
        }

        val entries = root.children("ClassStart").flatMapIndexed { classIndex, classStart ->
            val className = classStart.child("Class")?.childText("Name").orEmpty()
            if (classStart.children("Course").size > 1) {
                warnings += IofXmlUnsupportedItem(
                    messageType = "StartList",
                    location = "/StartList/ClassStart[${classIndex + 1}]/Course",
                    reason = "Multiple courses per class are valid IOF data but Radio-Oracle start imports match starts by class and competitor only."
                )
            }
            classStart.children("PersonStart").mapIndexed { personIndex, personStart ->
                val start = personStart.child("Start")
                val startTimeIso = start?.childText("StartTime")
                IofStartListEntryPreview(
                    className = className,
                    person = personStart.personPreview(
                        messageType = "StartList",
                        location = "/StartList/ClassStart[${classIndex + 1}]/PersonStart[${personIndex + 1}]"
                    ),
                    bibNumber = start?.childText("BibNumber"),
                    controlCard = start?.childText("ControlCard")?.toIntOrNull(),
                    startTimeIso = startTimeIso,
                    relativeStartTimeSeconds = relativeSeconds(eventStartDateTime, startTimeIso)
                )
            }
        }

        return IofXmlImportResult(
            parsedData = IofStartListPreview(
                eventName = event?.childText("Name"),
                startDate = eventStart?.childText("Date"),
                startTime = eventStart?.childText("Time"),
                entries = entries
            ),
            unsupportedItems = warnings
        )
    }

    /** Validates StartList against the IOF 3.0 schema before building a Radio-Oracle preview. */
    fun validatedStartList(xml: String, iofSchema: String): IofStartListImportResult {
        requireValidImportXml(xml, iofSchema, expectedRoot = "StartList")
        return startList(xml)
    }

    /** Parses an IOF ResultList into a preview that can be matched and applied by platform UI code. */
    fun resultList(xml: String): IofResultListImportResult {
        val root = parseXml(xml)
        requireRoot(root, "ResultList")
        val warnings = mutableListOf<IofXmlUnsupportedItem>()
        val event = root.child("Event")
        val eventStart = event?.child("StartTime")

        root.descendants("TeamResult").forEachIndexed { index, _ ->
            warnings += IofXmlUnsupportedItem(
                messageType = "ResultList",
                location = "/ResultList/TeamResult[${index + 1}]",
                reason = "Team and relay results are valid IOF data but are not applied by Radio-Oracle result imports yet.",
                severity = IofXmlImportSeverity.UNSUPPORTED
            )
        }

        val entries = root.children("ClassResult").flatMapIndexed { classIndex, classResult ->
            val className = classResult.child("Class")?.childText("Name").orEmpty()
            classResult.children("PersonResult").mapIndexed { personIndex, personResult ->
                val result = personResult.child("Result")
                val splitTimes = result?.children("SplitTime")
                    ?.mapNotNull { split ->
                        val controlCode = split.childText("ControlCode")?.toIntOrNull() ?: return@mapNotNull null
                        IofResultSplitPreview(
                            controlCode = controlCode,
                            timeSeconds = split.childText("Time")?.toLongOrNull()
                        )
                    }
                    ?: emptyList()
                IofResultListEntryPreview(
                    className = className,
                    person = personResult.personPreview(
                        messageType = "ResultList",
                        location = "/ResultList/ClassResult[${classIndex + 1}]/PersonResult[${personIndex + 1}]"
                    ),
                    bibNumber = result?.childText("BibNumber"),
                    controlCard = result?.childText("ControlCard")?.toIntOrNull(),
                    startTimeIso = result?.childText("StartTime"),
                    finishTimeIso = result?.childText("FinishTime"),
                    timeSeconds = result?.childText("Time")?.toLongOrNull(),
                    position = result?.childText("Position")?.toIntOrNull(),
                    status = result?.childText("Status") ?: "Active",
                    splitControls = splitTimes.map { it.controlCode },
                    splitTimes = splitTimes
                )
            }
        }

        return IofXmlImportResult(
            parsedData = IofResultListPreview(
                eventName = event?.childText("Name"),
                startDate = eventStart?.childText("Date"),
                startTime = eventStart?.childText("Time"),
                entries = entries
            ),
            unsupportedItems = warnings
        )
    }

    /** Validates ResultList against the IOF 3.0 schema before building a Radio-Oracle preview. */
    fun validatedResultList(xml: String, iofSchema: String): IofResultListImportResult {
        requireValidImportXml(xml, iofSchema, expectedRoot = "ResultList")
        return resultList(xml)
    }

    /** Parses an IOF EntryList into competitor registration rows. */
    fun entryList(xml: String): IofEntryListImportResult {
        val root = parseXml(xml)
        requireRoot(root, "EntryList")
        val warnings = mutableListOf<IofXmlUnsupportedItem>()
        val event = root.child("Event")
        val eventStart = event?.child("StartTime")

        root.children("TeamEntry").forEachIndexed { index, _ ->
            warnings += IofXmlUnsupportedItem(
                messageType = "EntryList",
                location = "/EntryList/TeamEntry[${index + 1}]",
                reason = "Team and relay entries are valid IOF data but are not imported as Radio-Oracle individual competitors yet.",
                severity = IofXmlImportSeverity.UNSUPPORTED
            )
        }

        val entries = root.children("PersonEntry").mapIndexed { index, personEntry ->
            personEntry.toCompetitorImportRow(index, warnings)
        }

        return IofXmlImportResult(
            parsedData = IofEntryListPreview(
                eventName = event?.childText("Name"),
                startDate = eventStart?.childText("Date"),
                startTime = eventStart?.childText("Time"),
                entries = entries
            ),
            unsupportedItems = warnings
        )
    }

    /** Validates EntryList against the IOF 3.0 schema before building a Radio-Oracle preview. */
    fun validatedEntryList(xml: String, iofSchema: String): IofEntryListImportResult {
        requireValidImportXml(xml, iofSchema, expectedRoot = "EntryList")
        return entryList(xml)
    }

    private fun requireValidImportXml(xml: String, iofSchema: String, expectedRoot: String) {
        val root = parseXml(xml)
        requireRoot(root, expectedRoot)
        IofXmlValidator.requireValid(xml, iofSchema)
    }

    private fun assignedCourses(root: XmlNode, nodes: List<XmlNode>, courses: List<EventCategoryData>,
                                idFactory: (String) -> String, warnings: MutableList<IofXmlUnsupportedItem>): Pair<List<EventCategoryData>, Set<String>> {
        val assignments = root.children("ClassCourseAssignment")
        if (assignments.isEmpty()) return courses to emptySet()
        if (assignments.groupBy { it.childText("ClassName") }.any { it.value.size > 1 }) {
            warnings += IofXmlUnsupportedItem("CourseData", "/CourseData/RaceCourseData/ClassCourseAssignment",
                "Class-course assignments offer multiple courses for one class. Named courses are retained as separate mappings; class assignments need manual review.", IofXmlImportSeverity.UNSUPPORTED)
            return courses to emptySet()
        }
        val used = mutableSetOf<Int>()
        val assigned = assignments.map { assignment ->
            val name = assignment.childText("ClassName").orEmpty()
            val matches = nodes.indices.filter { index ->
                val courseName = assignment.childText("CourseName")
                val family = assignment.childText("CourseFamily")
                (courseName != null || family != null) &&
                    (courseName == null || nodes[index].childText("Name") == courseName) &&
                    (family == null || nodes[index].childText("CourseFamily") == family)
            }
            require(matches.size == 1) { "Class $name must identify one unambiguous course; forked or missing course assignments need review." }
            val index = matches.single()
            used += index
            val data = courses[index]
            val id = idFactory("iof-class-$name")
            data.copy(category = data.category.copy(id = id, name = name),
                controlPoints = data.controlPoints.map { it.copy(id = "$id-${it.id}", categoryId = id) })
        }
        return (assigned + courses.filterIndexed { index, _ -> index !in used }) to assigned.map { it.category.id }.toSet()
    }

    private fun XmlNode.toCategoryData(
        race: EventRace, index: Int, idFactory: (String) -> String,
        warnings: MutableList<IofXmlUnsupportedItem>, definitions: Map<String, XmlNode>,
        mappings: Map<String, IofCourseControlMapping>? = null
    ): EventCategoryData {
        val courseName = childText("Name")?.takeIf { it.isNotBlank() }
            ?: throw IofXmlImportException("CourseData course name missing at /CourseData/RaceCourseData/Course[${index + 1}].")
        warnUnsupportedCoursePresentationData(index, warnings)
        val categoryId = idFactory("iof-course-category-$index-$courseName")
        val controls = mutableListOf<EventControlPoint>()
        val objects = mutableListOf<ProtectedCourseObjectPoint>()
        val legs = mutableListOf<ProtectedCourseLegLength>()
        var previousId: String? = null
        children("CourseControl").forEachIndexed { visit, node ->
            require(node.children("Control").size == 1) { "Course $courseName has alternative controls; select a single course before importing." }
            val code = node.childText("Control").orEmpty().trim()
            val definition = definitions[code]
            val mapping = mappings?.getValue(code)
            val type = when (mapping?.pointType) {
                ProtectedCourseObjectType.START -> "Start"
                ProtectedCourseObjectType.FINISH -> "Finish"
                else -> node.attribute("type") ?: definition?.attribute("type") ?: "Control"
            }.let { if (mapping?.pointType?.controlRole() != null) "Control" else it }
            val name = definition?.childText("Name").orEmpty()
            val role = mapping?.pointType?.controlRole() ?: ControlRoleLabelRules.inferredSpecialRole(name)
                ?: ControlRoleLabelRules.inferredSpecialRole(code) ?: ControlPointType.CONTROL
            val objectType = when (type) {
                "Start" -> ProtectedCourseObjectType.START
                "Finish" -> ProtectedCourseObjectType.FINISH
                "Control" -> when (role) {
                    ControlPointType.CONTROL -> ProtectedCourseObjectType.CONTROL
                    ControlPointType.BEACON -> ProtectedCourseObjectType.BEACON
                    ControlPointType.SEPARATOR -> ProtectedCourseObjectType.SPECTATOR
                }
                else -> throw IofXmlImportException("Unsupported course-control type $type in $courseName.")
            }
            val siCode = (mapping?.siCode?.trim() ?: definition?.childText("PunchingUnitId") ?: code).toIntOrNull()
            require(type != "Control" || siCode != null) { "Course $courseName: $code needs a numeric SI code or PunchingUnitId." }
            val control = if (type == "Control") EventControlCatalog.controlForDefinition(race.id,
                ControlPointDefinition(requireNotNull(siCode), role, visit + 1)) else null
            val id = control?.id ?: idFactory("iof-placement-$type-$code")
            if (control != null) controls += EventControlPoint(
                id = idFactory("iof-course-control-$categoryId-$visit"), categoryId = categoryId,
                siCode = control.siCode, type = role, order = controls.size + 1, controlId = id)
            node.childText("LegLength")?.let { text ->
                val length = text.toDoubleOrNull()
                require(length != null && length.isFinite() && length >= 0) { "Invalid leg length for $code in $courseName." }
                // A Start leg can describe the timed start to the start flag; preserve it separately.
                legs += ProtectedCourseLegLength(previousId ?: "iof-time-start", id, length)
            }
            previousId = id
            val position = definition?.child("Position")
            val lat = position?.attribute("lat")?.toDoubleOrNull()
            val lng = position?.attribute("lng")?.toDoubleOrNull()
            if (lat != null && lng != null && lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0) {
                val label = mapping?.publicName?.trim() ?: control?.label ?: if (type == "Start") "Start" else "Finish"
                objects += ProtectedCourseObjectPoint(id, label, objectType, lat, lng,
                    position.attribute("alt")?.toDoubleOrNull()?.takeIf { it.isFinite() })
            } else warnings += IofXmlUnsupportedItem("CourseData", "/Course/$courseName/$code",
                "Geographic coordinates are missing or invalid for $code; route analysis needs latitude and longitude.")
        }
        val info = ProtectedCourseInfo(sourceName = "IOF CourseData: $courseName",
            lengthMeters = childText("Length")?.toDoubleOrNull()?.roundToInt(),
            climbMeters = childText("Climb")?.toDoubleOrNull()?.roundToInt(),
            route = objects.map { ProtectedCourseRoutePoint(it.latitude, it.longitude, it.elevationMeters) },
            courseObjects = objects.distinctBy { it.id },
            controlPoints = objects.filter { it.type.controlRole() != null }.distinctBy { it.id }.map {
                ProtectedCourseControlPoint(it.id, it.label, it.latitude, it.longitude,
                    requireNotNull(it.type.controlRole()), it.elevationMeters)
            }, suppliedLegLengths = legs)
        return EventCategoryData(category = EventCategory(id = categoryId, raceId = race.id, name = courseName,
            isMan = StandardCategoryRules.inferIsManFromName(courseName) ?: true, maxAge = null,
            lengthMeters = info.lengthMeters ?: 0, climbMeters = info.climbMeters ?: 0, order = index,
            differentProperties = false, raceType = null, raceBand = null, timeLimitSeconds = null,
            controlPointsString = "", courseInfo = info), controlPoints = controls, competitors = emptyList())
    }

    private fun XmlNode.toCompetitorImportRow(
        index: Int,
        warnings: MutableList<IofXmlUnsupportedItem>
    ): CompetitorCsvImportRow {
        val person = child("Person")
            ?: throw IofXmlImportException("EntryList person missing at /EntryList/PersonEntry[${index + 1}].")
        val name = person.child("Name")
        val classElements = children("Class")
        val className = classElements.firstOrNull()?.childText("Name").orEmpty()
        if (classElements.size > 1) {
            warnings += IofXmlUnsupportedItem(
                messageType = "EntryList",
                location = "/EntryList/PersonEntry[${index + 1}]/Class",
                reason = "Multiple requested classes are valid IOF data but Radio-Oracle imports only the first class for a competitor."
            )
        }
        val controlCards = children("ControlCard")
        if (controlCards.size > 1) {
            warnings += IofXmlUnsupportedItem(
                messageType = "EntryList",
                location = "/EntryList/PersonEntry[${index + 1}]/ControlCard",
                reason = "Multiple control cards are valid IOF data but Radio-Oracle imports only the first card number."
            )
        }
        val controlCardText = controlCards.firstOrNull()?.text?.trim()
        val siNumber = controlCardText?.toIntOrNull()
        if (!controlCardText.isNullOrBlank() && siNumber == null) {
            warnings += IofXmlUnsupportedItem(
                messageType = "EntryList",
                location = "/EntryList/PersonEntry[${index + 1}]/ControlCard",
                reason = "Radio-Oracle competitor imports require numeric control-card numbers."
            )
        }
        warnUnsupportedEntryChildren(index, warnings)
        val sex = person.attribute("sex")
        val isMan = when (sex) {
            "M" -> true
            "F" -> false
            else -> StandardCategoryRules.inferIsManFromName(className) ?: true
        }
        return CompetitorCsvImportRow(
            siNumber = siNumber,
            startNumber = null,
            firstName = name?.childText("Given").orEmpty(),
            lastName = name?.childText("Family").orEmpty(),
            categoryName = className,
            isMan = isMan,
            birthYear = person.childText("BirthDate")?.take(4)?.toIntOrNull(),
            club = child("Organisation")?.childText("Name").orEmpty(),
            personId = person.child("Id")?.text?.trim().orEmpty(),
            startTimeText = null,
            siRent = controlCards.isEmpty(),
            preferredStartGroup = null,
            bibNumber = "",
            callSign = ""
        )
    }

    private fun XmlNode.warnUnsupportedEntryChildren(
        index: Int,
        warnings: MutableList<IofXmlUnsupportedItem>
    ) {
        val unsupportedChildren = listOf(
            "RaceNumber" to "Multi-race EntryList race-number selections are not represented in Radio-Oracle competitor imports.",
            "AssignedFee" to "Entry fees and payment status are valid IOF data but are not represented in Radio-Oracle competitors.",
            "ServiceRequest" to "Entry service requests are valid IOF data but are not represented in Radio-Oracle competitors.",
            "StartTimeAllocationRequest" to "Start-time allocation preferences are valid IOF data but are not imported into Radio-Oracle start draw settings.",
            "Score" to "Entry ranking scores are valid IOF data but are not represented in Radio-Oracle competitors."
        )
        unsupportedChildren.forEach { (childName, reason) ->
            if (children(childName).isNotEmpty()) {
                warnings += IofXmlUnsupportedItem(
                    messageType = "EntryList",
                    location = "/EntryList/PersonEntry[${index + 1}]/$childName",
                    reason = reason
                )
            }
        }
    }

    private fun XmlNode.warnUnsupportedCoursePresentationData(
        index: Int,
        warnings: MutableList<IofXmlUnsupportedItem>
    ) {
        val unsupportedNames = listOf("MapText", "MapTextPosition")
        val presentNames = unsupportedNames.filter { descendants(it).isNotEmpty() }
        if (presentNames.isEmpty()) return
        warnings += IofXmlUnsupportedItem(
            messageType = "CourseData",
            location = "/CourseData/RaceCourseData/Course[${index + 1}]/CourseControl",
            reason = "Map presentation data are valid IOF data but are not represented in Radio-Oracle category imports: ${presentNames.joinToString()}."
        )
    }

    private fun requireRoot(root: XmlNode, expected: String) {
        val actual = root.localName
        if (actual != expected) {
            throw IofXmlImportException("Expected IOF $expected XML but found $actual.")
        }
        val iofVersion = root.attribute("iofVersion")
        if (iofVersion != IOF_VERSION) {
            throw IofXmlImportException("Unsupported IOF XML version: ${iofVersion ?: "missing"}. Expected $IOF_VERSION.")
        }
    }

    private fun XmlNode.personPreview(messageType: String, location: String): IofPersonPreview {
        val person = child("Person")
            ?: throw IofXmlImportException("$messageType person missing at $location.")
        val name = person.child("Name")
        val id = person.child("Id")
        return IofPersonPreview(
            personId = id?.text?.trim()?.takeIf { it.isNotBlank() },
            personIdType = id?.attribute("type"),
            familyName = name?.childText("Family").orEmpty(),
            givenName = name?.childText("Given").orEmpty(),
            organisationName = child("Organisation")?.childText("Name")
        )
    }

    private fun parseEventStartDateTime(startTime: XmlNode?): LocalDateTime? {
        val date = startTime?.childText("Date") ?: return null
        val time = startTime.childText("Time") ?: return null
        return parseIofDateTime("$date${if ('T' in time) "" else "T"}$time")
    }

    private fun relativeSeconds(eventStart: LocalDateTime?, startTimeIso: String?): Long? {
        val eventStartValue = eventStart ?: return null
        val startTimeValue = startTimeIso?.let(::parseIofDateTime) ?: return null
        return Duration.between(eventStartValue, startTimeValue).seconds
    }

    private fun parseIofDateTime(value: String): LocalDateTime? =
        runCatching { LocalDateTime.parse(value) }
            .getOrElse {
                runCatching { OffsetDateTime.parse(value).toLocalDateTime() }
                    .getOrNull()
            }
}

private data class XmlNode(
    val name: String,
    val attributes: Map<String, String>,
    val children: List<XmlNode>,
    val text: String
) {
    val localName: String = name.substringAfter(':')

    fun attribute(name: String): String? = attributes[name] ?: attributes.entries
        .firstOrNull { it.key.substringAfter(':') == name }
        ?.value

    fun child(name: String): XmlNode? = children.firstOrNull { it.localName == name }

    fun children(name: String): List<XmlNode> = children.filter { it.localName == name }

    fun childText(name: String): String? = child(name)?.text?.trim()

    fun descendants(name: String): List<XmlNode> =
        children.flatMap { child ->
            buildList {
                if (child.localName == name) {
                    add(child)
                }
                addAll(child.descendants(name))
            }
        }
}

private fun parseXml(xml: String): XmlNode =
    XmlParser(xml).parse()

private class XmlParser(private val xml: String) {
    private var index = 0

    fun parse(): XmlNode {
        if (index < xml.length && xml[index] == '\uFEFF') {
            index++
        }
        skipWhitespace()
        if (peek("<?xml")) {
            skipUntil("?>")
        }
        skipWhitespaceAndComments()
        val root = parseElement()
        skipWhitespaceAndComments()
        if (index < xml.length) {
            throw IofXmlImportException("Unexpected trailing XML content.")
        }
        return root
    }

    private fun parseElement(): XmlNode {
        expect('<')
        if (peek("!--")) {
            skipUntil("-->")
            skipWhitespaceAndComments()
            return parseElement()
        }
        val name = readName()
        val attributes = mutableMapOf<String, String>()
        while (true) {
            skipWhitespace()
            when {
                peek("/>") -> {
                    index += 2
                    return XmlNode(name, attributes, emptyList(), "")
                }
                peek(">") -> {
                    index++
                    break
                }
                else -> {
                    val attrName = readName()
                    skipWhitespace()
                    expect('=')
                    skipWhitespace()
                    val quote = current()
                    if (quote != '"' && quote != '\'') {
                        throw IofXmlImportException("XML attribute $attrName is missing quotes.")
                    }
                    index++
                    val valueStart = index
                    while (index < xml.length && xml[index] != quote) {
                        index++
                    }
                    if (index >= xml.length) {
                        throw IofXmlImportException("Unterminated XML attribute $attrName.")
                    }
                    attributes[attrName] = xml.substring(valueStart, index).decodeXmlEntities()
                    index++
                }
            }
        }

        val children = mutableListOf<XmlNode>()
        val text = StringBuilder()
        while (index < xml.length) {
            when {
                peek("</") -> {
                    index += 2
                    val endName = readName()
                    if (endName.substringAfter(':') != name.substringAfter(':')) {
                        throw IofXmlImportException("Mismatched XML end tag: expected $name but found $endName.")
                    }
                    skipWhitespace()
                    expect('>')
                    return XmlNode(name, attributes, children, text.toString().decodeXmlEntities())
                }
                peek("<!--") -> skipUntil("-->")
                peek("<![CDATA[") -> {
                    index += "<![CDATA[".length
                    val end = xml.indexOf("]]>", index)
                    if (end < 0) {
                        throw IofXmlImportException("Unterminated XML CDATA section.")
                    }
                    text.append(xml.substring(index, end))
                    index = end + "]]>".length
                }
                current() == '<' -> children += parseElement()
                else -> {
                    text.append(current())
                    index++
                }
            }
        }
        throw IofXmlImportException("Unterminated XML element: $name.")
    }

    private fun skipWhitespaceAndComments() {
        while (true) {
            skipWhitespace()
            if (peek("<!--")) {
                skipUntil("-->")
            } else {
                return
            }
        }
    }

    private fun skipWhitespace() {
        while (index < xml.length && xml[index].isWhitespace()) {
            index++
        }
    }

    private fun readName(): String {
        val start = index
        while (index < xml.length) {
            val c = xml[index]
            if (c.isLetterOrDigit() || c == '_' || c == '-' || c == ':' || c == '.') {
                index++
            } else {
                break
            }
        }
        if (start == index) {
            throw IofXmlImportException("Expected XML name at character $index.")
        }
        return xml.substring(start, index)
    }

    private fun skipUntil(marker: String) {
        val end = xml.indexOf(marker, index)
        if (end < 0) {
            throw IofXmlImportException("Unterminated XML section.")
        }
        index = end + marker.length
    }

    private fun expect(char: Char) {
        if (index >= xml.length || xml[index] != char) {
            throw IofXmlImportException("Expected '$char' at character $index.")
        }
        index++
    }

    private fun current(): Char {
        if (index >= xml.length) {
            throw IofXmlImportException("Unexpected end of XML.")
        }
        return xml[index]
    }

    private fun peek(value: String): Boolean =
        xml.startsWith(value, index)
}

private fun String.decodeXmlEntities(): String =
    replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")

private fun String.stableIofId(): String =
    lowercase()
        .map { char -> if (char.isLetterOrDigit()) char else '-' }
        .joinToString("")
        .replace(Regex("-+"), "-")
        .trim('-')
        .ifBlank { "iof" }
