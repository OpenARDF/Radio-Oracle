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

package org.openardf.radiooracle.backend.sportident

import org.openardf.radiooracle.backend.room.entity.EventSeriesMember
import org.openardf.radiooracle.backend.room.entity.embeddeds.RaceData
import org.openardf.radiooracle.backend.sportident.SIPort.CardData

/** One series member and its local Android race aggregate for readout routing. */
data class EventSeriesReadoutMemberData(
    val member: EventSeriesMember,
    val raceData: RaceData
)

/** Routing outcome for an SI card read while an Event Series is active. */
sealed class EventSeriesReadoutRoute {
    data class Matched(
        val memberData: EventSeriesReadoutMemberData,
        val reason: EventSeriesReadoutRouteReason
    ) : EventSeriesReadoutRoute()

    data class Ambiguous(
        val candidates: List<EventSeriesReadoutMemberData>,
        val reason: EventSeriesReadoutRouteReason
    ) : EventSeriesReadoutRoute()

    data object NoMatch : EventSeriesReadoutRoute()
}

enum class EventSeriesReadoutRouteReason {
    CONTROL_PUNCHES,
    CONTROL_PUNCHES_AND_SI_NUMBER,
    SI_NUMBER
}

/** Chooses the series member event that should receive an Android SI card readout. */
object EventSeriesReadoutRouter {
    fun route(
        cardData: CardData,
        members: List<EventSeriesReadoutMemberData>
    ): EventSeriesReadoutRoute {
        if (members.isEmpty()) {
            return EventSeriesReadoutRoute.NoMatch
        }

        val punchCodes = cardData.punchData.mapTo(mutableSetOf()) { it.siCode }
        if (punchCodes.isNotEmpty()) {
            val controlMatches = members.filter { memberData ->
                memberData.raceData.containsAllControlPunches(punchCodes)
            }
            return when (controlMatches.size) {
                0 -> EventSeriesReadoutRoute.NoMatch
                1 -> EventSeriesReadoutRoute.Matched(
                    memberData = controlMatches.single(),
                    reason = EventSeriesReadoutRouteReason.CONTROL_PUNCHES
                )
                else -> routeBySiNumber(
                    cardData = cardData,
                    candidates = controlMatches,
                    ambiguousReason = EventSeriesReadoutRouteReason.CONTROL_PUNCHES
                ) ?: EventSeriesReadoutRoute.Ambiguous(
                    candidates = controlMatches,
                    reason = EventSeriesReadoutRouteReason.CONTROL_PUNCHES
                )
            }
        }

        return routeBySiNumber(
            cardData = cardData,
            candidates = members,
            ambiguousReason = EventSeriesReadoutRouteReason.SI_NUMBER
        ) ?: EventSeriesReadoutRoute.NoMatch
    }

    private fun routeBySiNumber(
        cardData: CardData,
        candidates: List<EventSeriesReadoutMemberData>,
        ambiguousReason: EventSeriesReadoutRouteReason
    ): EventSeriesReadoutRoute? {
        val siMatches = candidates.filter { memberData ->
            memberData.raceData.competitorData.any { competitorData ->
                competitorData.competitorCategory.competitor.siNumber == cardData.siNumber
            }
        }
        return when (siMatches.size) {
            0 -> null
            1 -> EventSeriesReadoutRoute.Matched(
                memberData = siMatches.single(),
                reason = if (ambiguousReason == EventSeriesReadoutRouteReason.CONTROL_PUNCHES) {
                    EventSeriesReadoutRouteReason.CONTROL_PUNCHES_AND_SI_NUMBER
                } else {
                    EventSeriesReadoutRouteReason.SI_NUMBER
                }
            )
            else -> EventSeriesReadoutRoute.Ambiguous(
                candidates = siMatches,
                reason = ambiguousReason
            )
        }
    }

    private fun RaceData.containsAllControlPunches(punchCodes: Set<Int>): Boolean {
        val eventControlCodes = categories
            .flatMap { it.controlPoints }
            .mapTo(mutableSetOf()) { it.siCode }
        return eventControlCodes.isNotEmpty() && punchCodes.all { it in eventControlCodes }
    }
}
