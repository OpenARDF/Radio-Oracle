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

import org.openardf.radiooracle.shared.domain.PunchStatus
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.shared.event.EventAliasPunch
import org.openardf.radiooracle.shared.event.EventAwardDisplayMode
import org.openardf.radiooracle.shared.event.EventAwardDetails
import org.openardf.radiooracle.shared.event.EventCompetitorData
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.EventResultDetails
import org.openardf.radiooracle.shared.event.PublicResultsPublicationStatus
import org.openardf.radiooracle.shared.publicresults.PublicResultsPublicationRules
import org.openardf.radiooracle.shared.time.DurationFormatter

/** A physical or synthetic endpoint used to identify a directed result leg. */
data class SplitResultPoint(
    val type: SplitResultPointType,
    val label: String,
    val siCode: Int? = null
)

enum class SplitResultPointType {
    START,
    CONTROL,
    FINISH
}

/** The comparable identity of a leg; direction is significant. */
data class SplitResultLegKey(
    val fromType: SplitResultPointType,
    val fromSiCode: Int?,
    val toType: SplitResultPointType,
    val toSiCode: Int?
)

data class SplitResultLeg(
    val sequence: Int,
    val from: SplitResultPoint,
    val control: SplitResultPoint,
    val legSeconds: Long,
    val cumulativeSeconds: Long,
    val punchStatus: PunchStatus,
    val legPlace: Int? = null
) {
    val key: SplitResultLegKey = SplitResultLegKey(
        fromType = from.type,
        fromSiCode = from.siCode,
        toType = control.type,
        toSiCode = control.siCode
    )
    val legTime: String = DurationFormatter.secondsToFormattedString(legSeconds, useMinutes = false)
    val cumulativeTime: String = DurationFormatter.secondsToFormattedString(cumulativeSeconds, useMinutes = false)
    val punchStatusText: String = punchStatus.splitReportLabel()
    val legPlaceText: String = legPlace?.toString().orEmpty()
}

data class SplitResultCompetitor(
    val resultId: String,
    val placeText: String,
    val name: String,
    val club: String,
    val personId: String,
    val bibNumber: String,
    val siNumber: String,
    val statusText: String,
    val pointsText: String,
    val runTimeText: String,
    val transmittersText: String,
    val splits: List<SplitResultLeg>
)

data class SplitResultCategory(
    val id: String,
    val name: String,
    val results: List<SplitResultCompetitor>
)

data class SplitResultReport(
    val raceName: String,
    val startDateTimeIso: String,
    val raceLevel: String,
    val publicationNotice: String?,
    val categories: List<SplitResultCategory>
) {
    val resultsById: Map<String, SplitResultCompetitor> = categories
        .flatMap(SplitResultCategory::results)
        .associateBy(SplitResultCompetitor::resultId)
}

/** Shared split-result model and Excel-friendly export used by desktop, Android, and public sites. */
object SplitResultExports {
    fun model(
        raceData: EventRaceData,
        awardDisplayMode: EventAwardDisplayMode = EventAwardDisplayMode.FIRST_TO_THIRD,
        publicationStatus: PublicResultsPublicationStatus? = null
    ): SplitResultReport {
        publicationStatus?.let { PublicResultsPublicationRules.requireReady(raceData, it) }
        val competitorsByResultId = raceData.competitorData
            .mapNotNull { competitorData ->
                competitorData.readoutData?.result?.id?.let { resultId -> resultId to competitorData }
            }
            .toMap()
        val controlsByCode = raceData.controls.associateBy { it.siCode }
        val useControlLabels = raceData.race.raceType != RaceType.ORIENTEERING
        val unrankedResults = EventResultDetails.from(raceData).mapNotNull { result ->
            val competitorData = competitorsByResultId[result.id] ?: return@mapNotNull null
            result to competitorData.toSplitResultCompetitor(
                result = result,
                controlsByCode = controlsByCode,
                useControlLabels = useControlLabels
            )
        }
        val categoryKeys = unrankedResults
            .map { (result, _) -> result.categoryId.orEmpty() to result.categoryName }
            .distinct()
        val categories = categoryKeys.map { (categoryId, categoryName) ->
            val results = unrankedResults
                .filter { (result, _) -> result.categoryId.orEmpty() == categoryId && result.categoryName == categoryName }
                .map { it.second }
            SplitResultCategory(
                id = categoryId,
                name = categoryName,
                results = results.withDirectedLegPlaces()
            )
        }
        val awards = EventAwardDetails.from(raceData, awardDisplayMode)
        return SplitResultReport(
            raceName = raceData.race.name,
            startDateTimeIso = raceData.race.startDateTimeIso,
            raceLevel = raceData.race.raceLevel.name,
            publicationNotice = if (publicationStatus == null) {
                awards.publicationNotice
            } else {
                PublicResultsPublicationRules.publicationNotice(publicationStatus)
            },
            categories = categories
        )
    }

    fun csv(
        raceData: EventRaceData,
        awardDisplayMode: EventAwardDisplayMode = EventAwardDisplayMode.FIRST_TO_THIRD,
        publicationStatus: PublicResultsPublicationStatus? = null
    ): String = csv(model(raceData, awardDisplayMode, publicationStatus))

    fun csv(report: SplitResultReport): String = buildString {
        appendCsvRow(
            "Race",
            "Start",
            "Category",
            "Place",
            "Bib",
            "Competitor",
            "Club",
            "Person ID",
            "SI",
            "Status",
            "Points",
            "Total Time",
            "Transmitters",
            "Split #",
            "From",
            "Control",
            "SI Code",
            "Punch Status",
            "Leg Time",
            "Leg Seconds",
            "Cumulative Time",
            "Cumulative Seconds",
            "Leg Place"
        )
        report.categories.forEach { category ->
            category.results.forEach { result ->
                if (result.splits.isEmpty()) {
                    appendResultCsvRow(report, category, result, null)
                } else {
                    result.splits.forEach { split ->
                        appendResultCsvRow(report, category, result, split)
                    }
                }
            }
        }
    }

    private fun EventCompetitorData.toSplitResultCompetitor(
        result: EventResultDetails,
        controlsByCode: Map<Int, org.openardf.radiooracle.shared.event.EventControl>,
        useControlLabels: Boolean
    ): SplitResultCompetitor {
        val competitor = competitorCategory.competitor
        val readout = requireNotNull(readoutData)
        return SplitResultCompetitor(
            resultId = result.id,
            placeText = result.placeText.ifBlank { result.statusLabel },
            name = result.competitorName,
            club = competitor.club,
            personId = competitor.index,
            bibNumber = competitor.bibNumber,
            siNumber = (readout.result.siNumber?.takeIf { it > 0 } ?: competitor.siNumber)?.toString().orEmpty(),
            statusText = result.statusLabel,
            pointsText = result.pointsText,
            runTimeText = result.runTimeText,
            transmittersText = result.punchCodesText,
            splits = readout.punches.toSplitResultLegs(controlsByCode, useControlLabels)
        )
    }

    private fun List<EventAliasPunch>.toSplitResultLegs(
        controlsByCode: Map<Int, org.openardf.radiooracle.shared.event.EventControl>,
        useControlLabels: Boolean
    ): List<SplitResultLeg> {
        var previous = SplitResultPoint(SplitResultPointType.START, "Start")
        var cumulativeSeconds = 0L
        val splits = mutableListOf<SplitResultLeg>()
        forEach { aliasPunch ->
            when (aliasPunch.punch.punchType) {
                SIRecordType.CHECK -> Unit
                SIRecordType.START -> previous = SplitResultPoint(SplitResultPointType.START, "Start")
                SIRecordType.CONTROL, SIRecordType.FINISH -> {
                    val destination = aliasPunch.destinationPoint(controlsByCode, useControlLabels)
                    cumulativeSeconds += aliasPunch.punch.splitSeconds
                    splits += SplitResultLeg(
                        sequence = splits.size + 1,
                        from = previous,
                        control = destination,
                        legSeconds = aliasPunch.punch.splitSeconds,
                        cumulativeSeconds = cumulativeSeconds,
                        punchStatus = aliasPunch.punch.punchStatus
                    )
                    previous = destination
                }
            }
        }
        return splits
    }

    private fun EventAliasPunch.destinationPoint(
        controlsByCode: Map<Int, org.openardf.radiooracle.shared.event.EventControl>,
        useControlLabels: Boolean
    ): SplitResultPoint =
        if (punch.punchType == SIRecordType.FINISH) {
            SplitResultPoint(SplitResultPointType.FINISH, "Finish")
        } else {
            val label = if (useControlLabels) {
                controlsByCode[punch.siCode]?.let { control ->
                    control.publicLabel?.takeIf(String::isNotBlank) ?: control.label
                } ?: alias?.name ?: punch.siCode.toString()
            } else {
                punch.siCode.toString()
            }
            SplitResultPoint(SplitResultPointType.CONTROL, label, punch.siCode)
        }

    private fun List<SplitResultCompetitor>.withDirectedLegPlaces(): List<SplitResultCompetitor> {
        data class SplitReference(val resultId: String, val splitIndex: Int)

        val placesByReference = flatMap { result ->
            result.splits.mapIndexedNotNull { splitIndex, split ->
                split.takeIf { it.isRankable() }?.let {
                    Triple(it.key, SplitReference(result.resultId, splitIndex), it.legSeconds)
                }
            }
        }
            .groupBy { it.first }
            .values
            .flatMap { comparableLegs ->
                var previousSeconds: Long? = null
                var place = 0
                comparableLegs
                    .sortedBy { it.third }
                    .mapIndexed { position, (_, reference, seconds) ->
                        if (previousSeconds != seconds) {
                            place = position + 1
                            previousSeconds = seconds
                        }
                        reference to place
                    }
            }
            .toMap()

        return map { result ->
            result.copy(
                splits = result.splits.mapIndexed { splitIndex, split ->
                    split.copy(legPlace = placesByReference[SplitReference(result.resultId, splitIndex)])
                }
            )
        }
    }

    private fun SplitResultLeg.isRankable(): Boolean =
        punchStatus != PunchStatus.INVALID && punchStatus != PunchStatus.DUPLICATE

    private fun StringBuilder.appendResultCsvRow(
        report: SplitResultReport,
        category: SplitResultCategory,
        result: SplitResultCompetitor,
        split: SplitResultLeg?
    ) {
        appendCsvRow(
            report.raceName,
            report.startDateTimeIso,
            category.name,
            result.placeText,
            result.bibNumber,
            result.name,
            result.club,
            result.personId,
            result.siNumber,
            result.statusText,
            result.pointsText,
            result.runTimeText,
            result.transmittersText,
            split?.sequence,
            split?.from?.label,
            split?.control?.label,
            split?.control?.siCode,
            split?.punchStatusText,
            split?.legTime,
            split?.legSeconds,
            split?.cumulativeTime,
            split?.cumulativeSeconds,
            split?.legPlace
        )
    }

    private fun StringBuilder.appendCsvRow(vararg fields: Any?) {
        append(fields.joinToString(EventCsvFormat.DELIMITER.toString()) { (it ?: "").toString().csvField() })
        append('\n')
    }

    private fun String.csvField(): String =
        if (any { it == EventCsvFormat.DELIMITER || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + replace("\"", "\"\"") + "\""
        } else {
            this
        }
}

private fun PunchStatus.splitReportLabel(): String =
    when (this) {
        PunchStatus.VALID -> "OK"
        PunchStatus.INVALID -> "MP"
        PunchStatus.DUPLICATE -> "DP"
        PunchStatus.UNKNOWN -> "AP"
    }
