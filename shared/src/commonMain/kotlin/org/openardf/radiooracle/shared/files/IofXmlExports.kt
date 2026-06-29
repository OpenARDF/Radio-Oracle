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
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventCompetitor
import org.openardf.radiooracle.shared.event.EventCompetitorData
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.event.competitionCategories
import org.openardf.radiooracle.shared.event.effectiveLengthMeters
import org.openardf.radiooracle.shared.results.EventResultPlacement
import org.openardf.radiooracle.shared.results.IofResultStatus
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Shared IOF XML 3.0 export builders for desktop and Android flows. */
object IofXmlExports {
    private const val IOF_NAMESPACE = "http://www.orienteering.org/datastandard/3.0"

    fun courseData(raceData: EventRaceData, creator: String = "Radio-Oracle Desktop"): String {
        val raceStart = parseRaceStart(raceData.race.startDateTimeIso)
        val controlsById = raceData.controls.associateBy { it.id }
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""")
            append('\n')
            append("""<CourseData xmlns="$IOF_NAMESPACE" iofVersion="3.0" creator="${creator.xmlEscaped()}">""")
            append('\n')
            appendEvent(raceData, raceStart)
            append("  <RaceCourseData>\n")
            raceData.categories
                .sortedWith(compareBy({ it.category.order }, { it.category.name }))
                .forEach { categoryData ->
                    appendCourse(categoryData, controlsById)
                }
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

    fun resultList(raceData: EventRaceData, creator: String = "Radio-Oracle Desktop"): String {
        val raceStart = parseRaceStart(raceData.race.startDateTimeIso)
        val placedByCategory = raceData.competitorData
            .groupBy { it.resultCategoryId() }
            .mapValues { (_, categoryCompetitors) -> EventResultPlacement.sortByPlace(categoryCompetitors) }
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""")
            append('\n')
            append("""<ResultList xmlns="$IOF_NAMESPACE" iofVersion="3.0" creator="${creator.xmlEscaped()}" status="Complete">""")
            append('\n')
            appendEvent(raceData, raceStart)
            raceData.competitionCategories()
                .sortedWith(compareBy({ it.category.order }, { it.category.name }))
                .forEach { categoryData ->
                    appendClassResult(categoryData, placedByCategory[categoryData.category.id] ?: emptyList(), raceStart)
                }
            append("</ResultList>\n")
        }
    }

    private fun EventCompetitorData.resultCategoryId(): String? =
        readoutData?.result?.categoryId ?: competitorCategory.category?.id ?: competitorCategory.competitor.categoryId

    private fun StringBuilder.appendCourse(
        categoryData: EventCategoryData,
        controlsById: Map<String, org.openardf.radiooracle.shared.event.EventControl>
    ) {
        append("    <Course>\n")
        appendTextElement("Name", categoryData.category.name, indent = "      ")
        if (categoryData.category.lengthMeters > 0) {
            appendTextElement("Length", categoryData.category.lengthMeters.toString(), indent = "      ")
        }
        if (categoryData.category.climbMeters > 0) {
            appendTextElement("Climb", categoryData.category.climbMeters.toString(), indent = "      ")
        }
        appendCourseControl(code = "S", type = "Start")
        categoryData.courseControlCodes(controlsById).forEach { controlCode ->
            appendCourseControl(code = controlCode, type = "Control")
        }
        appendCourseControl(code = "F", type = "Finish")
        append("    </Course>\n")
    }

    private fun StringBuilder.appendCourseControl(code: String, type: String) {
        append("""      <CourseControl type="$type">""")
        append('\n')
        appendTextElement("Control", code, indent = "        ")
        append("      </CourseControl>\n")
    }

    private fun EventCategoryData.courseControlCodes(
        controlsById: Map<String, org.openardf.radiooracle.shared.event.EventControl>
    ): List<String> {
        val points = if (controlPoints.isNotEmpty()) {
            controlPoints.sortedBy { it.order }.mapNotNull { point ->
                val control = controlsById[point.controlId]
                val siCode = control?.siCode ?: point.siCode
                val type = control?.type ?: point.type
                siCode.takeIf { type == ControlPointType.CONTROL && it > 0 }?.toString()
            }
        } else {
            publicControlIds.mapNotNull { controlId ->
                val control = controlsById[controlId] ?: return@mapNotNull null
                control.siCode.takeIf { control.type == ControlPointType.CONTROL && it > 0 }?.toString()
            }
        }
        return points
    }

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
        raceStart: LocalDateTime
    ) {
        append("  <ClassResult>\n")
        append("""    <Class sex="${if (categoryData.category.isMan) "M" else "F"}">""")
        append('\n')
        appendTextElement("Name", categoryData.category.name, indent = "      ")
        append("    </Class>\n")
        competitorData.forEach { appendPersonResult(it, raceStart) }
        append("  </ClassResult>\n")
    }

    private fun StringBuilder.appendPersonResult(competitorData: EventCompetitorData, raceStart: LocalDateTime) {
        append("    <PersonResult>\n")
        appendPersonAndOrganisation(competitorData.competitorCategory.competitor, indent = "      ")
        append("      <Result>\n")
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
        var cumulativeSplitSeconds = 0L
        readoutData?.punches
            ?.filter { it.punch.punchType == SIRecordType.CONTROL }
            ?.forEach { aliasPunch ->
                cumulativeSplitSeconds += aliasPunch.punch.splitSeconds
                append("        <SplitTime>\n")
                appendTextElement("ControlCode", aliasPunch.punch.siCode.toString(), indent = "          ")
                appendTextElement("Time", cumulativeSplitSeconds.toString(), indent = "          ")
                append("        </SplitTime>\n")
            }
        (readoutData?.result?.siNumber?.takeIf { it > 0 } ?: competitorData.competitorCategory.competitor.siNumber)?.let { siNumber ->
            appendTextElement("ControlCard", siNumber.toString(), indent = "        ")
        }
        append("      </Result>\n")
        append("    </PersonResult>\n")
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
