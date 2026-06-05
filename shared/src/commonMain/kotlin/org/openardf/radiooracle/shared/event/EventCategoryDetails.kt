package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.course.ControlPointDisplayToken
import org.openardf.radiooracle.shared.course.ControlPointDefinition
import org.openardf.radiooracle.shared.course.ControlPointRules
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.time.DurationFormatter

/** Shared read-only category row prepared for desktop and other event-admin surfaces. */
data class EventCategoryDetails(
    val id: String,
    val name: String,
    val lengthMetersText: String,
    val climbMetersText: String,
    val raceTypeLabel: String,
    val raceBandLabel: String,
    val timeLimitText: String,
    val controlPointsText: String
) {
    companion object {
        /** Builds display rows sorted the same way category administration presents them. */
        fun from(raceData: EventRaceData, useAliases: Boolean = true): List<EventCategoryDetails> =
            raceData.categories
                .sortedWith(EventCategorySort.byDisplayName)
                .map { categoryData ->
                    val category = categoryData.category
                    val raceType = category.effectiveRaceType(raceData.race)
                    EventCategoryDetails(
                        id = category.id,
                        name = category.name,
                        lengthMetersText = category.lengthMeters.toString(),
                        climbMetersText = category.climbMeters.toString(),
                        raceTypeLabel = raceType.toDisplayLabel(),
                        raceBandLabel = category.effectiveRaceBand(raceData.race).toDisplayLabel(),
                        timeLimitText = DurationFormatter.secondsToFormattedString(
                            category.effectiveTimeLimitSeconds(raceData.race),
                            useMinutes = true
                        ),
                        controlPointsText = categoryData.displayControlPoints(raceData, raceType, useAliases)
                    )
                }

        private fun EventCategoryData.displayControlPoints(
            raceData: EventRaceData,
            raceType: RaceType,
            useAliases: Boolean
        ): String {
            val aliasesByCode = raceData.aliases.associateBy { it.siCode }
            val sortedControlPoints = if (useAliases && raceType != RaceType.ORIENTEERING) {
                controlPoints.sortedWith(compareBy({ aliasesByCode[it.siCode]?.name ?: it.siCode.toString() }, { it.siCode }))
            } else {
                controlPoints.sortedBy { it.siCode }
            }
            if (!useAliases || raceType == RaceType.ORIENTEERING) {
                return ControlPointRules.formatControlPoints(
                    sortedControlPoints.map {
                        ControlPointDefinition(it.siCode, it.type, it.order)
                    }
                )
            }
            return ControlPointRules.formatDisplayTokens(
                sortedControlPoints.map { controlPoint ->
                    ControlPointDisplayToken(
                        siCode = controlPoint.siCode,
                        aliasName = aliasesByCode[controlPoint.siCode]?.name
                    )
                },
                useAlias = useAliases
            )
        }
    }
}
