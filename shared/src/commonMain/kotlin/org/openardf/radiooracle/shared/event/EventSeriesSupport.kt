package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.files.CompetitorStartCsvImportRow
import org.openardf.radiooracle.shared.time.DurationFormatter

data class EventSeriesLinkedEvent(
    val event: EventSeriesEvent,
    val projectFile: EventProjectFile
)

data class EventSeriesValidationIssue(
    val severity: EventSeriesIssueSeverity,
    val message: String,
    val seriesEventId: String? = null
)

enum class EventSeriesIssueSeverity {
    WARNING,
    ERROR
}

data class EventSeriesCompetitorMatch(
    val fromSeriesEventId: String,
    val fromCompetitorId: String,
    val toSeriesEventId: String,
    val toCompetitorId: String,
    val method: EventSeriesCompetitorMatchMethod
)

enum class EventSeriesCompetitorMatchMethod {
    SI_NUMBER,
    START_NUMBER,
    OVERRIDE
}

data class EventSeriesCompetitorMatchReport(
    val matches: List<EventSeriesCompetitorMatch>,
    val issues: List<EventSeriesValidationIssue>
)

/** Shared event-series rules that do not depend on desktop filesystem APIs. */
object EventSeriesSupport {
    fun validateLinkedEvents(
        seriesFile: EventSeriesFile,
        linkedEvents: List<EventSeriesLinkedEvent>
    ): List<EventSeriesValidationIssue> {
        val linkedByEventId = linkedEvents.associateBy { it.event.seriesEventId }
        val issues = mutableListOf<EventSeriesValidationIssue>()

        seriesFile.events.forEach { event ->
            val linked = linkedByEventId[event.seriesEventId]
            if (linked == null) {
                return@forEach
            }
            val link = linked.projectFile.seriesLink
            when {
                link == null -> issues += EventSeriesValidationIssue(
                    severity = EventSeriesIssueSeverity.WARNING,
                    message = "Event File '${event.displayName}' does not have a series backlink.",
                    seriesEventId = event.seriesEventId
                )
                link.seriesId != seriesFile.seriesId -> issues += EventSeriesValidationIssue(
                    severity = EventSeriesIssueSeverity.ERROR,
                    message = "Event File '${event.displayName}' links to a different series.",
                    seriesEventId = event.seriesEventId
                )
                link.seriesEventId != event.seriesEventId -> issues += EventSeriesValidationIssue(
                    severity = EventSeriesIssueSeverity.ERROR,
                    message = "Event File '${event.displayName}' links to a different series event.",
                    seriesEventId = event.seriesEventId
                )
            }
        }

        return issues
    }

    fun priorStartRowsForCurrentEvent(
        seriesFile: EventSeriesFile,
        linkedEvents: List<EventSeriesLinkedEvent>,
        currentSeriesEventId: String
    ): List<List<CompetitorStartCsvImportRow>> {
        val currentOrder = seriesFile.events
            .firstOrNull { it.seriesEventId == currentSeriesEventId }
            ?.order
            ?: return emptyList()
        val linkedByEventId = linkedEvents.associateBy { it.event.seriesEventId }
        return seriesFile.sortedEvents()
            .filter { it.order < currentOrder }
            .mapNotNull { event ->
                linkedByEventId[event.seriesEventId]?.projectFile?.let(::startRowsFromEventFile)
            }
            .filter { it.isNotEmpty() }
    }

    fun drawStartListWithSeriesBalancedStartGroups(
        seriesFile: EventSeriesFile,
        linkedEvents: List<EventSeriesLinkedEvent>,
        currentSeriesEventId: String,
        currentProjectFile: EventProjectFile,
        intervalText: String,
        options: StartDrawOptions
    ): EventProjectFile =
        EventProjectEditor.drawStartListWithBalancedStartGroups(
            projectFile = currentProjectFile,
            intervalText = intervalText,
            options = options,
            previousStartLists = priorStartRowsForCurrentEvent(seriesFile, linkedEvents, currentSeriesEventId)
        )

    fun matchCompetitors(
        seriesFile: EventSeriesFile,
        fromEvent: EventSeriesLinkedEvent,
        toEvent: EventSeriesLinkedEvent
    ): EventSeriesCompetitorMatchReport {
        val issues = mutableListOf<EventSeriesValidationIssue>()
        val matches = mutableListOf<EventSeriesCompetitorMatch>()
        val usedTargets = mutableSetOf<String>()

        val targetBySi = uniqueCompetitorsByKey(
            toEvent,
            key = { it.siNumber?.takeIf { value -> value > 0 }?.toString() },
            issueLabel = "SI number",
            issues = issues
        )
        val targetByStart = uniqueCompetitorsByKey(
            toEvent,
            key = { competitor -> if (competitor.siNumber == null) competitor.startNumber.toString() else null },
            issueLabel = "start number",
            issues = issues
        )

        seriesFile.competitorMatchOverrides
            .filter { override ->
                override.fromSeriesEventId == fromEvent.event.seriesEventId &&
                    override.toSeriesEventId == toEvent.event.seriesEventId
            }
            .forEach { override ->
                matches += EventSeriesCompetitorMatch(
                    fromSeriesEventId = override.fromSeriesEventId,
                    fromCompetitorId = override.fromCompetitorId,
                    toSeriesEventId = override.toSeriesEventId,
                    toCompetitorId = override.toCompetitorId,
                    method = EventSeriesCompetitorMatchMethod.OVERRIDE
                )
                usedTargets += override.toCompetitorId
            }

        fromEvent.projectFile.raceData.competitorData
            .map { it.competitorCategory.competitor }
            .forEach { competitor ->
                if (matches.any { it.fromCompetitorId == competitor.id }) {
                    return@forEach
                }
                val siKey = competitor.siNumber?.takeIf { it > 0 }?.toString()
                val siTarget = siKey?.let { targetBySi[it] }
                if (siTarget != null && usedTargets.add(siTarget.id)) {
                    matches += EventSeriesCompetitorMatch(
                        fromSeriesEventId = fromEvent.event.seriesEventId,
                        fromCompetitorId = competitor.id,
                        toSeriesEventId = toEvent.event.seriesEventId,
                        toCompetitorId = siTarget.id,
                        method = EventSeriesCompetitorMatchMethod.SI_NUMBER
                    )
                    return@forEach
                }

                if (competitor.siNumber == null) {
                    val startTarget = targetByStart[competitor.startNumber.toString()]
                    if (startTarget != null && usedTargets.add(startTarget.id)) {
                        matches += EventSeriesCompetitorMatch(
                            fromSeriesEventId = fromEvent.event.seriesEventId,
                            fromCompetitorId = competitor.id,
                            toSeriesEventId = toEvent.event.seriesEventId,
                            toCompetitorId = startTarget.id,
                            method = EventSeriesCompetitorMatchMethod.START_NUMBER
                        )
                    }
                }
            }

        return EventSeriesCompetitorMatchReport(matches = matches, issues = issues)
    }

    fun startRowsFromEventFile(projectFile: EventProjectFile): List<CompetitorStartCsvImportRow> =
        projectFile.raceData.competitorData
            .map { it.competitorCategory.competitor }
            .filter { it.drawnStartTimeSeconds != null }
            .sortedWith(compareBy({ it.startNumber }, { it.fullName() }))
            .map { competitor ->
                CompetitorStartCsvImportRow(
                    startNumber = competitor.startNumber,
                    startTimeText = DurationFormatter.secondsToFormattedString(
                        totalSeconds = requireNotNull(competitor.drawnStartTimeSeconds),
                        useMinutes = true
                    ),
                    siNumber = competitor.siNumber
                )
            }

    private fun uniqueCompetitorsByKey(
        event: EventSeriesLinkedEvent,
        key: (EventCompetitor) -> String?,
        issueLabel: String,
        issues: MutableList<EventSeriesValidationIssue>
    ): Map<String, EventCompetitor> {
        val grouped = event.projectFile.raceData.competitorData
            .map { it.competitorCategory.competitor }
            .mapNotNull { competitor -> key(competitor)?.let { it to competitor } }
            .groupBy({ it.first }, { it.second })

        grouped.filterValues { it.size > 1 }.keys.forEach { duplicateKey ->
            issues += EventSeriesValidationIssue(
                severity = EventSeriesIssueSeverity.WARNING,
                message = "Event '${event.event.displayName}' has duplicate $issueLabel $duplicateKey.",
                seriesEventId = event.event.seriesEventId
            )
        }

        return grouped
            .filterValues { it.size == 1 }
            .mapValues { it.value.single() }
    }
}
