package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.event.EventReadoutDuplicatePolicy
import org.openardf.radiooracle.shared.event.EventProjectEditor
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.defaultScored
import org.openardf.radiooracle.shared.sportident.SportIdentCardPunch
import org.openardf.radiooracle.shared.sportident.SportIdentCardReadout
import org.openardf.radiooracle.shared.sportident.SportIdentCodes
import org.openardf.radiooracle.shared.sportident.SportIdentProtocol
import org.openardf.radiooracle.shared.sportident.SportIdentTime
import java.time.LocalDateTime
import java.util.UUID

internal data class DesktopTestSportIdentDownloadInsertResult(
    val projectFile: EventProjectFile,
    val insertedCount: Int
)

/**
 * Creates ordinary downloaded SPORTident readouts for desktop UI testing.
 *
 * The generated readouts intentionally pass through EventProjectEditor.addDownloadedSportIdentReadout
 * so Results, Readouts, CSV exports, duplicate policy handling, and validation see the same project
 * data shape that real SI downloads create.
 */
internal object DesktopTestSportIdentDownloads {
    private const val DefaultMaxDownloads = 20
    private const val DefaultRaceStartSeconds = 9 * 60 * 60L
    private const val FirstGeneratedStartOffsetSeconds = 5 * 60L
    private const val StartSpacingSeconds = 2 * 60L
    private const val BaseRunTimeSeconds = 36 * 60L
    private const val RunTimeSpacingSeconds = 73L
    private const val PunchSpacingSeconds = 4 * 60L

    fun insert(
        projectFile: EventProjectFile,
        maxDownloads: Int = DefaultMaxDownloads,
        readoutDateTimeIso: String = LocalDateTime.now().withNano(0).toString()
    ): DesktopTestSportIdentDownloadInsertResult {
        var workingProjectFile = projectFile
        var insertedCount = 0
        val candidates = projectFile.raceData.competitorData
            .filter { data ->
                val competitor = data.competitorCategory.competitor
                val siNumber = competitor.siNumber
                data.readoutData == null &&
                    siNumber != null &&
                    !projectFile.raceData.containsReadoutForSiNumber(siNumber)
            }
            .sortedWith(
                compareBy(
                    { it.competitorCategory.competitor.drawnStartTimeSeconds ?: Long.MAX_VALUE },
                    { it.competitorCategory.competitor.startNumber },
                    { it.competitorCategory.competitor.lastName },
                    { it.competitorCategory.competitor.firstName }
                )
            )
            .take(maxDownloads.coerceAtLeast(0))

        candidates.forEachIndexed { index, data ->
            val competitor = data.competitorCategory.competitor
            val siNumber = competitor.siNumber ?: return@forEachIndexed
            val startSeconds = generatedStartSeconds(projectFile.raceData, competitor.drawnStartTimeSeconds, index)
            val finishSeconds = generatedFinishSeconds(startSeconds, index)
            val controlCodes = punchCodesFor(workingProjectFile.raceData, competitor.categoryId, index)
            val readout = SportIdentCardReadout(
                siNumber = siNumber,
                series = 6,
                checkTime = SportIdentTime((startSeconds - 60L).coerceAtLeast(0L)),
                startTime = SportIdentTime(startSeconds),
                finishTime = if (shouldIncludeFinishTime(index)) SportIdentTime(finishSeconds) else null,
                punches = controlCodes.mapIndexed { punchIndex, siCode ->
                    SportIdentCardPunch(
                        siCode = siCode,
                        siTime = SportIdentTime(startSeconds + PunchSpacingSeconds * (punchIndex + 1))
                    )
                }
            )

            workingProjectFile = EventProjectEditor.addDownloadedSportIdentReadout(
                projectFile = workingProjectFile,
                resultId = "test-readout-${UUID.randomUUID()}",
                cardType = SportIdentProtocol.SI_CARD6,
                readout = readout,
                readoutDateTimeIso = readoutDateTimeIso,
                duplicatePolicy = EventReadoutDuplicatePolicy.Reject
            ) { punchIndex, type ->
                "test-punch-${UUID.randomUUID()}-$punchIndex-${type.name}"
            }
            insertedCount += 1
        }

        return DesktopTestSportIdentDownloadInsertResult(workingProjectFile, insertedCount)
    }

    private fun generatedStartSeconds(
        raceData: EventRaceData,
        drawnStartTimeSeconds: Long?,
        index: Int
    ): Long {
        val raceStartSeconds = raceStartSecondsOfDay(raceData.race.startDateTimeIso)
        val generatedOffset = drawnStartTimeSeconds?.toLong()
            ?: (FirstGeneratedStartOffsetSeconds + index * StartSpacingSeconds)
        val candidate = (raceStartSeconds + generatedOffset) % SportIdentCodes.SECONDS_DAY
        return if (candidate + BaseRunTimeSeconds + index * RunTimeSpacingSeconds < SportIdentCodes.SECONDS_DAY) {
            candidate
        } else {
            DefaultRaceStartSeconds + index * StartSpacingSeconds
        }
    }

    private fun generatedFinishSeconds(startSeconds: Long, index: Int): Long =
        (startSeconds + BaseRunTimeSeconds + index * RunTimeSpacingSeconds)
            .coerceAtMost(SportIdentCodes.SECONDS_DAY - 1)

    private fun raceStartSecondsOfDay(startDateTimeIso: String): Long =
        runCatching {
            LocalDateTime.parse(startDateTimeIso).toLocalTime().toSecondOfDay().toLong()
        }.getOrDefault(DefaultRaceStartSeconds)

    @Suppress("DEPRECATION")
    private fun shouldIncludeFinishTime(index: Int): Boolean =
        index % 10 != 9

    @Suppress("DEPRECATION")
    private fun punchCodesFor(raceData: EventRaceData, categoryId: String?, index: Int): List<Int> {
        val categoryData = raceData.categories.firstOrNull { it.category.id == categoryId } ?: return emptyList()
        val controlsById = raceData.controls.associateBy { it.id }
        val courseControls = categoryData.controlPoints
            .sortedBy { it.order }
            .mapNotNull { controlPoint ->
                val control = controlsById[controlPoint.controlId]
                CourseTestControl(
                    siCode = control?.siCode ?: controlPoint.siCode,
                    type = control?.type ?: controlPoint.type,
                    scored = control?.scored ?: controlPoint.type.defaultScored()
                )
            }
        if (courseControls.isEmpty()) {
            return emptyList()
        }
        val scoredControls = courseControls.filter { it.effectiveScored }
        val requiredUnscoredControls = courseControls.filterNot { it.effectiveScored }
        val beacon = courseControls.lastOrNull { it.type == ControlPointType.BEACON }
        val shouldPunchBeacon = index % 6 != 5
        val selectedScoredControls = when (index % 8) {
            0, 1, 2 -> scoredControls
            3 -> scoredControls.take((scoredControls.size - 1).coerceAtLeast(0))
            4 -> scoredControls.filterIndexed { controlIndex, _ -> controlIndex % 2 == 0 }
            5 -> scoredControls.take(1)
            6 -> scoredControls.take((scoredControls.size * 2 / 3).coerceAtLeast(1))
            else -> scoredControls.drop(1).ifEmpty { scoredControls }
        }
        val requiredWithoutBeacon = requiredUnscoredControls.filterNot { it == beacon }
        return buildList {
            addAll(selectedScoredControls.map { it.siCode })
            if (index % 7 == 6 && selectedScoredControls.isNotEmpty()) {
                add(selectedScoredControls.first().siCode)
            }
            addAll(requiredWithoutBeacon.map { it.siCode })
            if (shouldPunchBeacon && beacon != null) {
                add(beacon.siCode)
            }
        }
    }

    private fun EventRaceData.containsReadoutForSiNumber(siNumber: Int): Boolean =
        competitorData.any { it.readoutData?.result?.siNumber == siNumber } ||
            unmatchedReadoutData.any { it.result.siNumber == siNumber }

    private data class CourseTestControl(
        val siCode: Int,
        val type: ControlPointType,
        val scored: Boolean
    ) {
        val effectiveScored: Boolean
            get() = type == ControlPointType.CONTROL || scored
    }
}
