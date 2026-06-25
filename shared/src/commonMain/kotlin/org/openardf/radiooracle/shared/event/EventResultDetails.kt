package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.shared.domain.toResultStatusCode
import org.openardf.radiooracle.shared.results.EventResultPlacement
import org.openardf.radiooracle.shared.time.DurationFormatter

/** Shared read-only result row for competitor result lists. */
data class EventResultDetails(
    val id: String,
    val categoryId: String? = null,
    val categoryName: String = "Uncategorized",
    val place: Int,
    val placeText: String,
    val competitorName: String,
    val resultStatus: ResultStatus,
    val automaticStatus: Boolean,
    val statusLabel: String,
    val pointsText: String,
    val runTimeText: String,
    val punchCodesText: String,
    val hasWarning: Boolean,
    private val categorySortOrder: Int = Int.MAX_VALUE
) {
    companion object {
        /** Builds display rows for competitors that currently have readout/result data. */
        fun from(raceData: EventRaceData, useAliases: Boolean = true): List<EventResultDetails> {
            val categoriesById = raceData.categories.associateBy { it.category.id }
            val controlLabelsByCode = raceData.controls.associateBy(
                keySelector = { it.siCode },
                valueTransform = { it.publicLabel?.takeIf(String::isNotBlank) ?: it.label }
            )
            return EventResultPlacement.assignPlacesByCategory(raceData.competitorData).mapNotNull { competitorData ->
                val readoutData = competitorData.readoutData ?: return@mapNotNull null
                val competitor = competitorData.competitorCategory.competitor
                val resultCategoryId = readoutData.result.categoryId ?: competitor.categoryId
                val category = resultCategoryId?.let { categoriesById[it]?.category }
                    ?: competitorData.competitorCategory.category
                fromReadout(
                    readoutData = readoutData,
                    categoryId = category?.id ?: resultCategoryId,
                    categoryName = category?.name ?: resultCategoryId?.let { "Unknown category" } ?: "Uncategorized",
                    categoryOrder = category?.order ?: Int.MAX_VALUE,
                    competitorName = competitor.fullName(),
                    raceType = raceData.race.raceType,
                    useAliases = useAliases,
                    controlLabelsByCode = controlLabelsByCode
                )
            }.sortedWith(
                compareBy<EventResultDetails> { it.categorySortOrder }
                    .thenBy { it.categoryName }
                    .thenBy { if (it.place > 0) it.place else Int.MAX_VALUE }
                    .thenBy { it.competitorName }
            )
        }

        private fun fromReadout(
            readoutData: EventReadoutData,
            categoryId: String?,
            categoryName: String,
            categoryOrder: Int,
            competitorName: String,
            raceType: RaceType,
            useAliases: Boolean,
            controlLabelsByCode: Map<Int, String>
        ): EventResultDetails {
            val result = readoutData.result
            val blocksScoreAndRunTime = readoutData.blocksScoreAndRunTimeDisplay()
            return EventResultDetails(
                id = result.id,
                categoryId = categoryId,
                categoryName = categoryName,
                categorySortOrder = categoryOrder,
                place = result.place,
                placeText = if (result.place > 0) result.place.toString() else "",
                competitorName = competitorName,
                resultStatus = result.resultStatus,
                automaticStatus = result.automaticStatus,
                statusLabel = result.resultStatus.toDisplayLabel(),
                pointsText = if (blocksScoreAndRunTime) "" else result.points.toString(),
                runTimeText = if (blocksScoreAndRunTime) {
                    readoutData.blockedRunTimeStatusCode()
                } else {
                    DurationFormatter.secondsToFormattedString(result.runTimeSeconds, useMinutes = false)
                },
                punchCodesText = readoutData.punches
                    .filter { it.punch.punchType == SIRecordType.CONTROL }
                    .joinToString(", ") { aliasPunch ->
                        if (raceType != RaceType.ORIENTEERING && useAliases) {
                            controlLabelsByCode[aliasPunch.punch.siCode]
                                ?: aliasPunch.alias?.name
                                ?: aliasPunch.punch.siCode.toString()
                        } else {
                            aliasPunch.punch.siCode.toString()
                        }
                    },
                hasWarning = readoutData.hasReadoutWarning()
            )
        }
    }
}
