package org.openardf.radiooracle.shared.files

import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventCompetitor
import org.openardf.radiooracle.shared.event.EventCompetitorData
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.event.effectiveLengthMeters
import org.openardf.radiooracle.shared.results.EventResultPlacement
import org.openardf.radiooracle.shared.results.IofResultStatus
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Shared IOF XML 3.0 export builders for desktop and non-Android flows. */
object IofXmlExports {
    private const val IOF_NAMESPACE = "http://www.orienteering.org/datastandard/3.0"

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
            raceData.categories
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
            raceData.categories
                .sortedWith(compareBy({ it.category.order }, { it.category.name }))
                .forEach { categoryData ->
                    appendClassResult(categoryData, placedByCategory[categoryData.category.id] ?: emptyList(), raceStart)
                }
            append("</ResultList>\n")
        }
    }

    private fun EventCompetitorData.resultCategoryId(): String? =
        readoutData?.result?.categoryId ?: competitorCategory.category?.id ?: competitorCategory.competitor.categoryId

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

    private fun StringBuilder.appendPersonStart(competitor: EventCompetitor, raceStart: LocalDateTime) {
        append("    <PersonStart>\n")
        appendPersonAndOrganisation(competitor, indent = "      ")
        append("      <Start>\n")
        competitor.bibNumber.takeIf { it.isNotBlank() }?.let { bibNumber ->
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
        append("      </Result>\n")
        append("    </PersonResult>\n")
    }

    private fun StringBuilder.appendPersonAndOrganisation(competitor: EventCompetitor, indent: String) {
        append(indent)
        append("<Person>\n")
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
