package org.openardf.radiooracle.shared.files

import org.openardf.radiooracle.shared.course.ControlPointDefinition
import org.openardf.radiooracle.shared.course.ControlPointRules
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.shared.event.EventCompetitor
import org.openardf.radiooracle.shared.event.EventCompetitorData
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.EventResultDetails
import org.openardf.radiooracle.shared.event.toDisplayLabel
import org.openardf.radiooracle.shared.time.DurationFormatter

/** Shared semicolon-delimited CSV export builders for portable Event Files. */
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
                    .joinToString(EventCsvFormat.CONTROL_POINT_DELIMITER.toString())
                "${EventCsvRows.categoryRow(categoryData.category)}${EventCsvFormat.DELIMITER}" +
                        "${categoryData.controlPoints.size}${EventCsvFormat.DELIMITER}$controlPoints"
            }

    fun competitors(raceData: EventRaceData): String =
        EventCsvFormat.Competitor.HEADER_ROW + "\n" + raceData.competitorData
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
            .joinCompetitorStartRows(raceData)

    fun competitorStartsByCategory(raceData: EventRaceData): String =
        raceData.competitorData
            .sortedWith(
                compareBy<EventCompetitorData>(
                    { raceData.categoryNameFor(it.competitorCategory.competitor.categoryId) },
                    { it.competitorCategory.competitor.startTimeSortKey() },
                    { it.competitorCategory.competitor.startNumber },
                    { it.competitorCategory.competitor.fullName() }
                )
            )
            .joinCompetitorStartRows(raceData)

    fun competitorStartsByMinute(raceData: EventRaceData): String =
        raceData.competitorData
            .sortedWith(
                compareBy<EventCompetitorData>(
                    { it.competitorCategory.competitor.startTimeSortKey() },
                    { raceData.categoryNameFor(it.competitorCategory.competitor.categoryId) },
                    { it.competitorCategory.competitor.startNumber },
                    { it.competitorCategory.competitor.fullName() }
                )
            )
            .joinCompetitorStartRows(raceData)

    fun robisStartList(raceData: EventRaceData): String =
        raceData.competitorData
            .sortedWith(
                compareBy<EventCompetitorData>(
                    { raceData.categoryNameFor(it.competitorCategory.competitor.categoryId) },
                    { it.competitorCategory.competitor.startTimeSortKey() },
                    { it.competitorCategory.competitor.startNumber },
                    { it.competitorCategory.competitor.fullName() }
                )
            )
            .joinRows { competitorData ->
                val competitor = competitorData.competitorCategory.competitor
                EventCsvRows.robisStartListRow(
                    competitor = competitor,
                    categoryName = raceData.categoryNameFor(competitor.categoryId),
                    startTimeText = competitor.drawnStartTimeSeconds?.let {
                        DurationFormatter.secondsToFormattedString(it, useMinutes = false)
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

    fun ardfEventResults(raceData: EventRaceData): String =
        "Kategorie;Pořadí;Jméno;Index;Čas;TX;Status;Kontroly\n" +
            raceData.competitorData
                .sortedWith(
                    compareBy<EventCompetitorData>(
                        { raceData.categoryNameFor(it.competitorCategory.competitor.categoryId) },
                        { it.readoutData?.result?.place?.takeIf { place -> place > 0 } ?: Int.MAX_VALUE },
                        { it.competitorCategory.competitor.fullName() }
                    )
                )
                .mapNotNull { competitorData ->
                    val readoutData = competitorData.readoutData ?: return@mapNotNull null
                    val result = readoutData.result
                    val competitor = competitorData.competitorCategory.competitor
                    val categoryName = raceData.categoryNameFor(competitor.categoryId)
                    val controlOrder = readoutData.punches
                        .map { it.punch }
                        .filter { it.punchType == SIRecordType.CONTROL }
                        .sortedBy { it.order }
                        .joinToString(" ") { it.siCode.toString() }
                    EventCsvRows.ardfEventResultRow(
                        categoryName = categoryName,
                        placeText = if (result.place > 0) result.place.toString() else "",
                        competitorName = competitor.fullName(),
                        index = competitor.index,
                        runTimeText = DurationFormatter.secondsToFormattedString(result.runTimeSeconds, useMinutes = false),
                        pointsText = result.points.toString(),
                        statusLabel = result.resultStatus.toDisplayLabel(),
                        controlOrderText = controlOrder
                    )
                }
                .joinToString(separator = "\n", postfix = "\n")

    private fun <T> List<T>.joinRows(row: (T) -> String): String =
        joinToString(separator = "\n", postfix = if (isEmpty()) "" else "\n", transform = row)

    private fun List<EventCompetitorData>.joinCompetitorStartRows(raceData: EventRaceData): String =
        joinRows { competitorData ->
            val competitorCategory = competitorData.competitorCategory
            EventCsvRows.competitorStartRow(
                competitor = competitorCategory.competitor,
                categoryName = raceData.categoryNameFor(competitorCategory.competitor.categoryId),
                startTimeText = competitorCategory.competitor.drawnStartTimeSeconds?.let {
                    DurationFormatter.secondsToFormattedString(it, useMinutes = true)
                }
            )
        }

    private fun EventRaceData.categoryNameFor(categoryId: String?): String =
        categoryId?.let { id -> categories.firstOrNull { it.category.id == id }?.category?.name } ?: ""

    private fun EventCompetitor.startTimeSortKey(): Long =
        drawnStartTimeSeconds ?: Long.MAX_VALUE

    private fun Long.asSiTimeText(): String =
        DurationFormatter.secondsToFormattedString(this, useMinutes = false)
}
