package org.openardf.radiooracle.shared.files

import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventCompetitor
import org.openardf.radiooracle.shared.event.EventRaceData
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Shared IOF XML 3.0 export builders for desktop and non-Android flows. */
object IofXmlExports {
    private const val IOF_NAMESPACE = "http://www.orienteering.org/datastandard/3.0"

    fun startList(raceData: EventRaceData, creator: String = "Radio-Oracle Desktop"): String {
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
                    appendClassStart(categoryData, raceData.competitorsFor(categoryData), raceStart)
                }
            append("</StartList>\n")
        }
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
        raceStart: LocalDateTime
    ) {
        append("  <ClassStart>\n")
        append("    <Class>\n")
        appendTextElement("Name", categoryData.category.name, indent = "      ")
        append("    </Class>\n")
        append("    <Course>\n")
        appendTextElement("Length", categoryData.category.lengthMeters.toString(), indent = "      ")
        appendTextElement("Climb", categoryData.category.climbMeters.toString(), indent = "      ")
        append("    </Course>\n")
        competitors
            .sortedWith(compareBy({ it.drawnStartTimeSeconds ?: 0L }, { it.startNumber }, { it.fullName() }))
            .forEach { competitor ->
                appendPersonStart(competitor, raceStart)
            }
        append("  </ClassStart>\n")
    }

    private fun StringBuilder.appendPersonStart(competitor: EventCompetitor, raceStart: LocalDateTime) {
        append("    <PersonStart>\n")
        append("      <Person>\n")
        if (competitor.index.isNotBlank()) {
            append("""        <Id type="CZE">${competitor.index.xmlEscaped()}</Id>""")
            append('\n')
        }
        append("        <Name>\n")
        appendTextElement("Family", competitor.lastName, indent = "          ")
        appendTextElement("Given", competitor.firstName, indent = "          ")
        append("        </Name>\n")
        append("      </Person>\n")
        if (competitor.club.isNotBlank()) {
            append("      <Organisation>\n")
            appendTextElement("Name", competitor.club, indent = "        ")
            append("      </Organisation>\n")
        }
        append("      <Start>\n")
        appendTextElement("BibNumber", competitor.startNumber.toString(), indent = "        ")
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
