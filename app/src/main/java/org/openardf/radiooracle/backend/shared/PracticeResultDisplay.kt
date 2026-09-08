/* MIT License - Copyright (c) 2026 OpenARDF contributors */
package org.openardf.radiooracle.backend.shared

import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorData
import org.openardf.radiooracle.backend.room.entity.embeddeds.ResultData
import org.openardf.radiooracle.backend.room.entity.embeddeds.ReadoutData
import org.openardf.radiooracle.shared.event.withPracticeResultNames

/** Transient UI projections. Canonical registration names are kept in Room. */
fun List<CompetitorData>.withPracticeDisplayNames(): List<CompetitorData> {
    val labels = map { it.toEventCompetitorData() }.withPracticeResultNames()
    return zip(labels) { original, display ->
        original.copy(competitorCategory = original.competitorCategory.copy(
            competitor = original.competitorCategory.competitor.copy(firstName = display.competitorCategory.competitor.firstName)
        ))
    }
}

fun List<ResultData>.withPracticeReadoutNames(): List<ResultData> {
    val displays = mapNotNull { row -> row.competitorCategory?.let {
        CompetitorData(it, ReadoutData(row.result, row.punches))
    } }.withPracticeDisplayNames().associateBy { it.readoutData!!.result.id }
    return map { row -> row.copy(competitorCategory = displays[row.result.id]?.competitorCategory ?: row.competitorCategory) }
}
