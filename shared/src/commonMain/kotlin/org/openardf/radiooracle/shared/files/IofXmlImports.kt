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
import org.openardf.radiooracle.shared.event.EventCategory
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventControlPoint
import org.openardf.radiooracle.shared.event.EventRace

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
    val categories: List<EventCategoryData>
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
        val root = parseXml(xml)
        requireRoot(root, "CourseData")
        val warnings = mutableListOf<IofXmlUnsupportedItem>()
        val event = root.child("Event")
        val startTime = event?.child("StartTime")
        val raceCourseData = root.child("RaceCourseData")
            ?: throw IofXmlImportException("CourseData does not contain RaceCourseData.")

        raceCourseData.children("ClassCourseAssignment").forEachIndexed { index, _ ->
            warnings += IofXmlUnsupportedItem(
                messageType = "CourseData",
                location = "/CourseData/RaceCourseData/ClassCourseAssignment[${index + 1}]",
                reason = "Class-course assignments are not applied yet; courses are imported as editable Radio-Oracle categories."
            )
        }

        val categories = raceCourseData.children("Course").mapIndexed { index, course ->
            course.toCategoryData(
                race = race,
                index = index,
                idFactory = idFactory,
                warnings = warnings
            )
        }

        return IofXmlImportResult(
            parsedData = IofCourseDataPreview(
                eventName = event?.childText("Name"),
                startDate = startTime?.childText("Date"),
                startTime = startTime?.childText("Time"),
                categories = categories
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

    private fun requireValidImportXml(xml: String, iofSchema: String, expectedRoot: String) {
        val root = parseXml(xml)
        requireRoot(root, expectedRoot)
        IofXmlValidator.requireValid(xml, iofSchema)
    }

    private fun XmlNode.toCategoryData(
        race: EventRace,
        index: Int,
        idFactory: (String) -> String,
        warnings: MutableList<IofXmlUnsupportedItem>
    ): EventCategoryData {
        val courseName = childText("Name")?.takeIf { it.isNotBlank() }
            ?: throw IofXmlImportException("CourseData course name missing at /CourseData/RaceCourseData/Course[${index + 1}].")
        if (child("CourseFamily") != null) {
            warnings += IofXmlUnsupportedItem(
                messageType = "CourseData",
                location = "/CourseData/RaceCourseData/Course[${index + 1}]/CourseFamily",
                reason = "Course family is valid IOF data but is not represented in Radio-Oracle categories."
            )
        }
        descendants("MapPosition").forEach { _ ->
            warnings += IofXmlUnsupportedItem(
                messageType = "CourseData",
                location = "/CourseData/RaceCourseData/Course[${index + 1}]",
                reason = "Map positions are valid IOF data but are not represented in Radio-Oracle category imports."
            )
        }
        val categoryId = idFactory("iof-course-category-$index-$courseName")
        val controlPoints = mutableListOf<EventControlPoint>()
        children("CourseControl").forEachIndexed { controlIndex, courseControl ->
            val type = courseControl.attribute("type") ?: "Control"
            val controlCode = courseControl.childText("Control")?.trim().orEmpty()
            when (type) {
                "Control" -> {
                    val siCode = controlCode.toIntOrNull()
                    if (siCode != null) {
                        controlPoints += EventControlPoint(
                            id = idFactory("iof-course-control-$categoryId-$controlIndex-$siCode"),
                            categoryId = categoryId,
                            siCode = siCode,
                            type = ControlPointType.CONTROL,
                            order = controlPoints.size + 1
                        )
                    } else {
                        warnings += IofXmlUnsupportedItem(
                            messageType = "CourseData",
                            location = "/CourseData/RaceCourseData/Course[${index + 1}]/CourseControl[${controlIndex + 1}]",
                            reason = "Radio-Oracle category imports require numeric SI control codes."
                        )
                    }
                }
                "Start", "Finish" -> {
                    warnings += IofXmlUnsupportedItem(
                        messageType = "CourseData",
                        location = "/CourseData/RaceCourseData/Course[${index + 1}]/CourseControl[${controlIndex + 1}]",
                        reason = "$type controls are valid IOF data but are not part of Radio-Oracle assigned transmitter order."
                    )
                }
                else -> {
                    warnings += IofXmlUnsupportedItem(
                        messageType = "CourseData",
                        location = "/CourseData/RaceCourseData/Course[${index + 1}]/CourseControl[${controlIndex + 1}]",
                        reason = "Unsupported IOF course-control type: $type.",
                        severity = IofXmlImportSeverity.UNSUPPORTED
                    )
                }
            }
        }
        return EventCategoryData(
            category = EventCategory(
                id = categoryId,
                raceId = race.id,
                name = courseName,
                isMan = true,
                maxAge = null,
                lengthMeters = childText("Length")?.trim()?.toIntOrNull() ?: 0,
                climbMeters = childText("Climb")?.trim()?.toIntOrNull() ?: 0,
                order = index,
                differentProperties = false,
                raceType = null,
                raceBand = null,
                timeLimitSeconds = null,
                controlPointsString = ""
            ),
            controlPoints = controlPoints,
            competitors = emptyList()
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
