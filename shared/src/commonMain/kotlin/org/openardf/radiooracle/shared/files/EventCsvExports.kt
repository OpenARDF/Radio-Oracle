package org.openardf.radiooracle.shared.files

import org.openardf.radiooracle.shared.course.ControlPointDefinition
import org.openardf.radiooracle.shared.course.ControlPointRules
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.EventResultDetails
import org.openardf.radiooracle.shared.time.DurationFormatter

/** Shared semicolon-delimited CSV export builders for portable event projects. */
object EventCsvExports {
    fun categories(raceData: EventRaceData): String =
        raceData.categories
            .sortedWith(compareBy({ it.category.order }, { it.category.name }))
            .joinRows { categoryData ->
                val controlPoints = categoryData.controlPoints
                    .sortedBy { it.order }
                    .map {
                        ControlPointRules.formatControlPoints(
                            listOf(ControlPointDefinition(it.siCode, it.type, it.order))
                        )
                    }
                    .joinToString(",")
                "${EventCsvRows.categoryRow(categoryData.category)};${categoryData.controlPoints.size};$controlPoints"
            }

    fun competitors(raceData: EventRaceData): String =
        raceData.competitorData
            .sortedWith(compareBy({ it.competitorCategory.competitor.startNumber }, { it.competitorCategory.competitor.fullName() }))
            .joinRows { competitorData ->
                val competitorCategory = competitorData.competitorCategory
                EventCsvRows.competitorRow(
                    competitor = competitorCategory.competitor,
                    categoryName = raceData.categoryNameFor(competitorCategory.competitor.categoryId)
                )
            }

    fun competitorStarts(raceData: EventRaceData): String =
        raceData.competitorData
            .sortedWith(compareBy({ it.competitorCategory.competitor.startNumber }, { it.competitorCategory.competitor.fullName() }))
            .joinRows { competitorData ->
                val competitorCategory = competitorData.competitorCategory
                EventCsvRows.competitorStartRow(
                    competitor = competitorCategory.competitor,
                    categoryName = raceData.categoryNameFor(competitorCategory.competitor.categoryId),
                    startTimeText = competitorCategory.competitor.drawnStartTimeSeconds?.let {
                        DurationFormatter.secondsToFormattedString(it, useMinutes = true)
                    }
                )
            }

    fun readouts(raceData: EventRaceData): String =
        (raceData.competitorData.mapNotNull { it.readoutData } + raceData.unmatchedReadoutData)
            .sortedWith(compareBy({ it.result.siNumber ?: Int.MAX_VALUE }, { it.result.id }))
            .joinRows { readoutData ->
                EventCsvRows.readoutRow(
                    siNumber = readoutData.result.siNumber,
                    checkTimeText = readoutData.result.checkTimeSeconds?.asSiTimeText(),
                    startTimeText = readoutData.result.startTimeSeconds?.asSiTimeText(),
                    finishTimeText = readoutData.result.finishTimeSeconds?.asSiTimeText(),
                    controlPunches = readoutData.punches
                        .map { it.punch }
                        .filter { it.punchType == SIRecordType.CONTROL }
                        .sortedBy { it.order }
                        .map {
                            TimedPunchCsvField(
                                siCode = it.siCode,
                                timeText = it.siTimeSeconds.asSiTimeText()
                            )
                        }
                )
            }

    fun results(raceData: EventRaceData): String =
        EventResultDetails.from(raceData)
            .joinRows { result ->
                EventCsvRows.resultRow(
                    placeText = result.placeText,
                    competitorName = result.competitorName,
                    statusLabel = result.statusLabel,
                    pointsText = result.pointsText,
                    runTimeText = result.runTimeText
                )
            }

    private fun <T> List<T>.joinRows(row: (T) -> String): String =
        joinToString(separator = "\n", postfix = if (isEmpty()) "" else "\n", transform = row)

    private fun EventRaceData.categoryNameFor(categoryId: String?): String =
        categoryId?.let { id -> categories.firstOrNull { it.category.id == id }?.category?.name } ?: ""

    private fun Long.asSiTimeText(): String =
        DurationFormatter.secondsToFormattedString(this, useMinutes = false)
}
