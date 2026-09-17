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

import org.openardf.radiooracle.shared.event.resultCompetitorData
import org.openardf.radiooracle.shared.event.registrations
import org.openardf.radiooracle.shared.course.ControlPointDefinition
import org.openardf.radiooracle.shared.course.ControlPointRules
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.shared.event.EventCompetitor
import org.openardf.radiooracle.shared.event.EventCompetitorData
import org.openardf.radiooracle.shared.event.EventControl
import org.openardf.radiooracle.shared.event.EventControlDetails
import org.openardf.radiooracle.shared.event.EventAwardDetails
import org.openardf.radiooracle.shared.event.EventAwardDisplayMode
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventReadoutData
import org.openardf.radiooracle.shared.event.EventResultDetails
import org.openardf.radiooracle.shared.event.toDisplayLabel
import org.openardf.radiooracle.shared.time.DurationFormatter

/** Shared comma-delimited CSV export builders for portable Race Files. */
object EventCsvExports {
    fun categories(raceData: EventRaceData, includeEncryptedIdealOrder: Boolean = false): String =
        categories(raceData.categories, raceData.controls, includeEncryptedIdealOrder)

    fun categories(categories: List<EventCategoryData>, controls: List<EventControl> = emptyList(),
        includeEncryptedIdealOrder: Boolean = false): String =
        EventCsvFormat.Category.header(includeEncryptedIdealOrder) + "\n" + categories
            .sortedWith(compareBy({ it.category.order }, { it.category.name }))
            .joinRows { categoryData ->
                val controlsById = controls.associateBy { it.id }
                val exportControlPoints = if (categoryData.publicControlIds.isNotEmpty()) {
                    categoryData.publicControlIds.mapNotNull { controlId ->
                        controlsById[controlId]?.let { control ->
                            ControlPointDefinition(control.siCode, control.type, 0)
                        }
                    }
                } else {
                    categoryData.controlPoints
                    .map {
                        ControlPointDefinition(it.siCode, it.type, it.order)
                    }
                }
                val controlPoints = exportControlPoints
                    .sortedBy { it.siCode }
                    .map { ControlPointRules.formatControlPoints(listOf(it)) }
                    .joinToString(EventCsvFormat.CONTROL_POINT_DELIMITER.toString())
                val publicFields = "${EventCsvRows.categoryRow(categoryData.category)}${EventCsvFormat.DELIMITER}" +
                        "${exportControlPoints.size}${EventCsvFormat.DELIMITER}${CsvCodec.field(controlPoints)}"
                if (includeEncryptedIdealOrder) {
                    "$publicFields${EventCsvFormat.DELIMITER}${CsvCodec.field(categoryData.category.encryptedIdealOrder)}"
                } else {
                    publicFields
                }
            }

    fun competitors(raceData: EventRaceData): String =
        EventCsvFormat.Competitor.HEADER_ROW + "\n" + raceData.competitorData.registrations()
            .sortedWith(compareBy({ it.competitorCategory.competitor.startNumber ?: Int.MAX_VALUE }, { it.competitorCategory.competitor.fullName() }))
            .joinRows { competitorData ->
                val competitorCategory = competitorData.competitorCategory
                EventCsvRows.competitorRow(
                    competitor = competitorCategory.competitor,
                    categoryName = raceData.categoryNameFor(competitorCategory.competitor.categoryId)
                )
            }

    fun controls(raceData: EventRaceData): String =
        EventCsvFormat.Control.HEADER_ROW + "\n" + raceData.controls
            .sortedWith(compareBy<EventControl>({ it.siCode }, { it.type.name }, { it.label }))
            .joinRows { control ->
                listOf(
                    control.siCode,
                    EventControlDetails.typeLabel(control.type),
                    if (control.scored) 1 else 0,
                    control.publicLabel ?: "",
                    control.notes ?: ""
                ).joinToString(EventCsvFormat.DELIMITER.toString()) { it.toString().csvField() }
            }

    fun competitorStarts(raceData: EventRaceData): String =
        raceData.competitorData.registrations()
            .sortedWith(compareBy({ it.competitorCategory.competitor.startNumber ?: Int.MAX_VALUE }, { it.competitorCategory.competitor.fullName() }))
            .joinCompetitorStartRows(raceData)

    fun competitorStartsByCategory(raceData: EventRaceData): String =
        raceData.competitorData.registrations()
            .sortedWith(
                compareBy<EventCompetitorData>(
                    { raceData.categoryNameFor(it.competitorCategory.competitor.categoryId) },
                    { it.competitorCategory.competitor.startTimeSortKey() },
                    { it.competitorCategory.competitor.startNumber ?: Int.MAX_VALUE },
                    { it.competitorCategory.competitor.fullName() }
                )
            )
            .joinCompetitorStartRows(raceData)

    fun competitorStartsByMinute(raceData: EventRaceData): String =
        raceData.competitorData.registrations()
            .sortedWith(
                compareBy<EventCompetitorData>(
                    { it.competitorCategory.competitor.startTimeSortKey() },
                    { raceData.categoryNameFor(it.competitorCategory.competitor.categoryId) },
                    { it.competitorCategory.competitor.startNumber ?: Int.MAX_VALUE },
                    { it.competitorCategory.competitor.fullName() }
                )
            )
            .joinCompetitorStartRows(raceData)

    fun robisStartList(raceData: EventRaceData): String =
        raceData.competitorData.registrations()
            .sortedWith(
                compareBy<EventCompetitorData>(
                    { raceData.categoryNameFor(it.competitorCategory.competitor.categoryId) },
                    { it.competitorCategory.competitor.startTimeSortKey() },
                    { it.competitorCategory.competitor.startNumber ?: Int.MAX_VALUE },
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
        readouts(raceData.competitorData.mapNotNull { it.readoutData } + raceData.unmatchedReadoutData)

    fun readouts(data: List<EventReadoutData>, formatTime: (Long) -> String = { it.asSiTimeText() }): String {
        val readouts = data.sortedWith(compareBy({ it.result.siNumber ?: Int.MAX_VALUE }, { it.result.id }))
        val maxPunches = readouts.maxOfOrNull { data -> data.punches.count { it.punch.punchType == SIRecordType.CONTROL } } ?: 0
        return EventCsvRows.readoutHeader(maxPunches) + "\n" + readouts.joinRows { readoutData ->
            EventCsvRows.readoutRow(
                siNumber = readoutData.result.siNumber,
                checkTimeText = readoutData.result.checkTimeSeconds?.let(formatTime),
                startTimeText = readoutData.result.startTimeSeconds?.let(formatTime),
                finishTimeText = readoutData.result.finishTimeSeconds?.let(formatTime),
                controlPunches = readoutData.punches.map { it.punch }
                    .filter { it.punchType == SIRecordType.CONTROL }.sortedBy { it.order }
                    .map { TimedPunchCsvField(it.siCode, formatTime(it.siTimeSeconds)) },
                punchColumnCount = maxPunches
            )
        }
    }

    fun results(
        raceData: EventRaceData,
        awardDisplayMode: EventAwardDisplayMode = EventAwardDisplayMode.FIRST_TO_THIRD,
        routeLengths: Map<String, org.openardf.radiooracle.shared.event.ResultRouteLength> = emptyMap()
    ): String {
        val awards = EventAwardDetails.from(raceData, awardDisplayMode)
        val usaAwardByResultId = awards.categories
            .flatMap { it.usaAwards }
            .associate { it.resultId to it.awardText }
        val region2AwardByResultId = awards.categories
            .flatMap { it.region2Awards }
            .associate { it.resultId to it.awardText }
        val header = CsvCodec.row(listOf("Place", "Competitor", "Status", "Points", "Run time") +
            (if (awards.hasAwards) listOf("USA award", "Region 2 award") else emptyList()) +
            (if (routeLengths.isNotEmpty()) listOf("Estimated effective route length (m)", "Analysis ideal effective length (m)", "Route comparison") else emptyList())) + "\n"
        return header + EventResultDetails.from(raceData)
            .joinRows { result ->
                EventCsvRows.resultRow(
                    placeText = result.placeText,
                    competitorName = result.competitorName,
                    statusLabel = result.statusLabel,
                    pointsText = result.pointsText,
                    runTimeText = result.runTimeText,
                    usaAwardText = if (awards.hasAwards) usaAwardByResultId[result.id].orEmpty() else null,
                    region2AwardText = if (awards.hasAwards) region2AwardByResultId[result.id].orEmpty() else null
                ) + if (routeLengths.isEmpty()) "" else {
                    val length = routeLengths[result.id]
                    "," + CsvCodec.row(listOf(length?.effectiveMeters, length?.idealEffectiveMeters, length?.comparison))
                }
            }
    }

    fun ardfEventResults(raceData: EventRaceData): String {
        val competitorDataByResultId = raceData.resultCompetitorData().mapNotNull { competitorData ->
            competitorData.readoutData?.result?.id?.let { resultId -> resultId to competitorData }
        }.toMap()
        return "Kategorie;Pořadí;Jméno;Person ID;Čas;TX;Status;Kontroly\n" +
            EventResultDetails.from(raceData)
                .mapNotNull { resultDetails ->
                    val competitorData = competitorDataByResultId[resultDetails.id] ?: return@mapNotNull null
                    val readoutData = requireNotNull(competitorData.readoutData)
                    val result = readoutData.result
                    val competitor = competitorData.competitorCategory.competitor
                    val controlOrder = readoutData.punches
                        .map { it.punch }
                        .filter { it.punchType == SIRecordType.CONTROL }
                        .sortedBy { it.order }
                        .joinToString(" ") { it.siCode.toString() }
                    EventCsvRows.ardfEventResultRow(
                        categoryName = resultDetails.categoryName,
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
    }

    private fun <T> List<T>.joinRows(row: (T) -> String): String =
        joinToString(separator = "\n", postfix = if (isEmpty()) "" else "\n", transform = row)

    private fun List<EventCompetitorData>.joinCompetitorStartRows(raceData: EventRaceData): String =
        EventCsvFormat.CompetitorStart.HEADER_ROW + "\n" + joinRows { competitorData ->
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

    private fun String.csvField(): String = CsvCodec.field(this)
}
