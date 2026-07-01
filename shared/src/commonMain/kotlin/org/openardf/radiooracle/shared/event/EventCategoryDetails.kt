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

import org.openardf.radiooracle.shared.course.ControlPointDisplayToken
import org.openardf.radiooracle.shared.course.ControlPointDefinition
import org.openardf.radiooracle.shared.course.ControlPointRules
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.time.DurationFormatter

/** Shared read-only category row prepared for desktop and other race-admin surfaces. */
data class EventCategoryDetails(
    val id: String,
    val name: String,
    val isMan: Boolean,
    val lengthMetersText: String,
    val climbMetersText: String,
    val raceTypeLabel: String,
    val raceBandLabel: String,
    val timeLimitText: String,
    val controlPointsText: String,
    val assignedCompetitorCount: Int
) {
    companion object {
        /** Builds display rows sorted the same way category administration presents them. */
        fun from(raceData: EventRaceData, useAliases: Boolean = true): List<EventCategoryDetails> {
            val assignedCompetitorCountByCategoryId = raceData.competitorData
                .mapNotNull { competitorData ->
                    competitorData.competitorCategory.category?.id
                        ?: competitorData.competitorCategory.competitor.categoryId
                }
                .groupingBy { it }
                .eachCount()
            return raceData.categories
                .sortedWith(EventCategorySort.byDisplayName)
                .map { categoryData ->
                    val category = categoryData.category
                    val raceType = category.effectiveRaceType(raceData.race)
                    EventCategoryDetails(
                        id = category.id,
                        name = category.name,
                        isMan = category.isMan,
                        lengthMetersText = category.lengthMeters.toString(),
                        climbMetersText = category.climbMeters.toString(),
                        raceTypeLabel = raceType.toDisplayLabel(),
                        raceBandLabel = category.effectiveRaceBand(raceData.race).toDisplayLabel(),
                        timeLimitText = DurationFormatter.secondsToFormattedString(
                            category.effectiveTimeLimitSeconds(raceData.race),
                            useMinutes = true
                        ),
                        controlPointsText = categoryData.displayControlPoints(raceData, raceType, useAliases),
                        assignedCompetitorCount = assignedCompetitorCountByCategoryId[category.id] ?: 0
                    )
                }
        }

        private fun EventCategoryData.displayControlPoints(
            raceData: EventRaceData,
            raceType: RaceType,
            useAliases: Boolean
        ): String {
            val aliasesByCode = raceData.aliases.associateBy { it.siCode }
            val controlsById = raceData.controls.associateBy { it.id }
            val publicControlPoints = if (controlPoints.isNotEmpty()) {
                controlPoints
            } else if (publicControlIds.isNotEmpty()) {
                publicControlIds.mapIndexedNotNull { index, controlId ->
                    val control = controlsById[controlId] ?: return@mapIndexedNotNull null
                    EventControlPoint(
                        id = "public-$controlId",
                        categoryId = category.id,
                        controlId = control.id,
                        siCode = control.siCode,
                        type = control.type,
                        order = index + 1
                    )
                }
            } else {
                emptyList()
            }
            val sortedControlPoints = if (raceType == RaceType.ORIENTEERING) {
                publicControlPoints.sortedBy { it.order }
            } else {
                EventAssignedControlOrder.sort(publicControlPoints, controlsById, raceType)
            }
            if (!useAliases || raceType == RaceType.ORIENTEERING) {
                return ControlPointRules.formatControlPoints(
                    sortedControlPoints.map {
                        ControlPointDefinition(it.siCode, it.type, it.order)
                    }
                )
            }
            return ControlPointRules.formatEditableDisplayTokens(
                sortedControlPoints.map { controlPoint ->
                    ControlPointDisplayToken(
                        siCode = controlPoint.siCode,
                        aliasName = controlsById[controlPoint.controlId]?.publicLabel
                            ?: aliasesByCode[controlPoint.siCode]?.name
                            ?: controlsById[controlPoint.controlId]?.label
                    )
                },
                useAlias = useAliases
            )
        }
    }
}
