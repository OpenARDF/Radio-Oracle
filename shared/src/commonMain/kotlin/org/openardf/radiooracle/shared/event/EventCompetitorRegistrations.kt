/* MIT License - Copyright (c) 2026 OpenARDF contributors */
package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.domain.RaceLevel

/** One registration may appear alongside several results in a race snapshot. */
fun List<EventCompetitorData>.registrations(): List<EventCompetitorData> =
    groupBy { it.competitorCategory.competitor.id }.values.map { rows ->
        rows.filter { it.readoutData != null }.maxByOrNull { it.readoutData!!.result.readoutDateTimeIso } ?: rows.first()
    }

/** Remove empty association rows when a registration still has another result. */
internal fun List<EventCompetitorData>.normalizedAssociations(): List<EventCompetitorData> =
    groupBy { it.competitorCategory.competitor.id }.values.flatMap { rows ->
        rows.filter { it.readoutData != null }.ifEmpty { listOf(rows.first()) }
    }

/** Result-only display names; the stored registration name and ID never change. */
fun EventRaceData.resultCompetitorData(): List<EventCompetitorData> {
    return if (race.raceLevel == RaceLevel.PRACTICE) competitorData.withPracticeResultNames() else competitorData
}

/** Apply labels only to transient result projections, never to saved registrations. */
fun List<EventCompetitorData>.withPracticeResultNames(): List<EventCompetitorData> {
    val ordinals = filter { it.readoutData != null }
        .groupBy { it.competitorCategory.competitor.id }.values.flatMap { rows ->
            rows.sortedBy { it.readoutData!!.result.readoutDateTimeIso }.mapIndexed { index, row ->
                row.readoutData!!.result.id to index + 1
            }
        }.toMap()
    return map { row ->
        val ordinal = ordinals[row.readoutData?.result?.id] ?: 1
        if (ordinal == 1) row else row.copy(competitorCategory = row.competitorCategory.copy(
            competitor = row.competitorCategory.competitor.let { it.copy(firstName = "${it.firstName} ($ordinal)") }
        ))
    }
}
