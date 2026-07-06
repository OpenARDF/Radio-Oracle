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

package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.files.CompetitorStartCsvImportRow
import org.openardf.radiooracle.shared.importing.ImportValidationRules
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
    BIB_NUMBER,
    CALL_SIGN,
    OVERRIDE
}

data class EventSeriesCompetitorMatchReport(
    val matches: List<EventSeriesCompetitorMatch>,
    val issues: List<EventSeriesValidationIssue>
)

data class EventSeriesCompetitorIdentity(
    val key: String,
    val label: String
)

/** Shared event-series rules that do not depend on desktop filesystem APIs. */
object EventSeriesSupport {
    fun competitorIdentities(competitor: EventCompetitor): List<EventSeriesCompetitorIdentity> =
        buildList {
            competitor.seriesSiKey()?.let { add(EventSeriesCompetitorIdentity("si:$it", "SI $it")) }
            competitor.seriesBibKey()?.let { add(EventSeriesCompetitorIdentity("bib:${it.uppercase()}", "Bib $it")) }
            competitor.seriesCallSignKey()?.let { add(EventSeriesCompetitorIdentity("call:$it", "Call $it")) }
        }

    fun primaryCompetitorIdentity(competitor: EventCompetitor): EventSeriesCompetitorIdentity? =
        competitorIdentities(competitor).firstOrNull()

    fun competitorIdentityLabelComparator(): Comparator<String> =
        compareBy(
            {
                when {
                    it.startsWith("SI ") -> 0
                    it.startsWith("Bib ") -> 1
                    it.startsWith("Call ") -> 2
                    else -> 3
                }
            },
            { it }
        )

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
                    message = "Race File '${event.displayName}' does not have a series backlink.",
                    seriesEventId = event.seriesEventId
                )
                link.seriesId != seriesFile.seriesId -> issues += EventSeriesValidationIssue(
                    severity = EventSeriesIssueSeverity.ERROR,
                    message = "Race File '${event.displayName}' links to a different series.",
                    seriesEventId = event.seriesEventId
                )
                link.seriesEventId != event.seriesEventId -> issues += EventSeriesValidationIssue(
                    severity = EventSeriesIssueSeverity.ERROR,
                    message = "Race File '${event.displayName}' links to a different series race.",
                    seriesEventId = event.seriesEventId
                )
            }
        }
        // A copied or buggy Race File can carry the same underlying race id as another file.
        // Series membership can still be unique through seriesEventId/path, but validation should
        // flag the duplicate because other import, diagnostic, and UI code may still assume race ids
        // distinguish Race Files.
        linkedEvents
            .groupBy { it.projectFile.raceData.race.id }
            .filter { (raceId, races) -> raceId.isNotBlank() && races.size > 1 }
            .values
            .forEach { duplicateEvents ->
                duplicateEvents.forEach { linked ->
                    val otherEventNames = duplicateEvents
                        .filterNot { it.event.seriesEventId == linked.event.seriesEventId }
                        .map { it.event.displayName }
                        .distinct()
                        .joinToString(", ")
                    issues += EventSeriesValidationIssue(
                        severity = EventSeriesIssueSeverity.WARNING,
                        message = "Race File '${linked.event.displayName}' has a duplicate race ID shared with: $otherEventNames.",
                        seriesEventId = linked.event.seriesEventId
                    )
                }
            }

        val orderedLinkedEvents = seriesFile.sortedEvents().mapNotNull { event -> linkedByEventId[event.seriesEventId] }
        val expectedRaceLevel = orderedLinkedEvents.firstOrNull()?.projectFile?.raceData?.race?.raceLevel
        if (expectedRaceLevel != null) {
            orderedLinkedEvents
                .filter { it.projectFile.raceData.race.raceLevel != expectedRaceLevel }
                .forEach { linked ->
                    issues += EventSeriesValidationIssue(
                        severity = EventSeriesIssueSeverity.ERROR,
                        message = "Race File '${linked.event.displayName}' has race level " +
                            "${linked.projectFile.raceData.race.raceLevel.toDisplayLabel()}; " +
                            "series member races must all use ${expectedRaceLevel.toDisplayLabel()}.",
                        seriesEventId = linked.event.seriesEventId
                    )
                }
        }

        return issues
    }

    /**
     * Returns generated-start histories from every manifest-listed series race except the
     * event being redrawn. Series fairness is a whole-series problem once other draws exist;
     * calendar dates and manifest order are intentionally not used to exclude later races.
     */
    fun otherSeriesStartRowsForCurrentEvent(
        seriesFile: EventSeriesFile,
        linkedEvents: List<EventSeriesLinkedEvent>,
        currentSeriesEventId: String
    ): List<List<CompetitorStartCsvImportRow>> {
        if (seriesFile.events.none { it.seriesEventId == currentSeriesEventId }) {
            return emptyList()
        }
        val linkedByEventId = linkedEvents.associateBy { it.event.seriesEventId }
        return seriesFile.sortedEvents()
            .filterNot { it.seriesEventId == currentSeriesEventId }
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
            previousStartLists = otherSeriesStartRowsForCurrentEvent(seriesFile, linkedEvents, currentSeriesEventId)
        )

    fun matchCompetitors(
        seriesFile: EventSeriesFile,
        fromEvent: EventSeriesLinkedEvent,
        toEvent: EventSeriesLinkedEvent
    ): EventSeriesCompetitorMatchReport {
        val issues = mutableListOf<EventSeriesValidationIssue>()
        val matches = mutableListOf<EventSeriesCompetitorMatch>()
        val usedTargets = mutableSetOf<String>()

        /*
         * Series-level identity must use persistent competitor identifiers. The legacy
         * startNumber field is event-local ordering data in this workflow, so it is
         * deliberately excluded from automatic same-person matching.
         */
        var targetBySi: Map<String, EventCompetitor> = emptyMap()
        var targetByBib: Map<String, EventCompetitor> = emptyMap()
        var targetByCallSign: Map<String, EventCompetitor> = emptyMap()
        listOf(fromEvent, toEvent).forEach { event ->
            val eventBySi = uniqueCompetitorsByKey(event, { it.seriesSiKey() }, "SI number", issues)
            val eventByBib = uniqueCompetitorsByKey(event, { it.seriesBibKey() }, "bib number", issues)
            val eventByCallSign = uniqueCompetitorsByKey(event, { it.seriesCallSignKey() }, "call sign", issues)
            if (event.event.seriesEventId == toEvent.event.seriesEventId) {
                targetBySi = eventBySi
                targetByBib = eventByBib
                targetByCallSign = eventByCallSign
            }
        }

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

                val bibTarget = competitor.seriesBibKey()?.let { targetByBib[it] }
                if (bibTarget != null && usedTargets.add(bibTarget.id)) {
                    matches += EventSeriesCompetitorMatch(
                        fromSeriesEventId = fromEvent.event.seriesEventId,
                        fromCompetitorId = competitor.id,
                        toSeriesEventId = toEvent.event.seriesEventId,
                        toCompetitorId = bibTarget.id,
                        method = EventSeriesCompetitorMatchMethod.BIB_NUMBER
                    )
                    return@forEach
                }

                val callSignTarget = competitor.seriesCallSignKey()?.let { targetByCallSign[it] }
                if (callSignTarget != null && usedTargets.add(callSignTarget.id)) {
                    matches += EventSeriesCompetitorMatch(
                        fromSeriesEventId = fromEvent.event.seriesEventId,
                        fromCompetitorId = competitor.id,
                        toSeriesEventId = toEvent.event.seriesEventId,
                        toCompetitorId = callSignTarget.id,
                        method = EventSeriesCompetitorMatchMethod.CALL_SIGN
                    )
                }
            }

        return EventSeriesCompetitorMatchReport(matches = matches, issues = issues)
    }

    fun startRowsFromEventFile(projectFile: EventProjectFile): List<CompetitorStartCsvImportRow> =
        EventStartNumbers.assignFromDrawnStartTimes(projectFile).raceData.competitorData
            .map { it.competitorCategory.competitor }
            .filter { it.drawnStartTimeSeconds != null }
            .sortedWith(compareBy({ it.drawnStartTimeSeconds ?: Long.MAX_VALUE }, { it.fullName() }))
            .map { competitor ->
                CompetitorStartCsvImportRow(
                    startNumber = requireNotNull(competitor.startNumber),
                    startTimeText = DurationFormatter.secondsToFormattedString(
                        totalSeconds = requireNotNull(competitor.drawnStartTimeSeconds),
                        useMinutes = true
                    ),
                    siNumber = competitor.siNumber,
                    bibNumber = competitor.bibNumber,
                    callSign = competitor.callSign
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
                message = "Race '${event.event.displayName}' has duplicate $issueLabel $duplicateKey.",
                seriesEventId = event.event.seriesEventId
            )
        }

        return grouped
            .filterValues { it.size == 1 }
            .mapValues { it.value.single() }
    }

    private fun EventCompetitor.seriesSiKey(): String? =
        siNumber?.takeIf { it > 0 }?.toString()

    private fun EventCompetitor.seriesBibKey(): String? =
        bibNumber.trim().takeIf { it.isNotEmpty() }

    private fun EventCompetitor.seriesCallSignKey(): String? =
        ImportValidationRules.normalizedUniqueCallSign(callSign)
}
