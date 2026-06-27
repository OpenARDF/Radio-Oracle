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

package org.openardf.radiooracle.backend.room

import org.openardf.radiooracle.backend.room.entity.embeddeds.AliasPunch
import org.openardf.radiooracle.backend.room.entity.embeddeds.CategoryData
import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorCategory
import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorData
import org.openardf.radiooracle.backend.room.entity.embeddeds.RaceData
import org.openardf.radiooracle.backend.room.entity.embeddeds.ReadoutData
import java.util.UUID

/** Returns an imported race aggregate with fresh Room primary keys and preserved relationships. */
fun RaceData.withFreshImportIds(): RaceData {
    val idMap = mutableMapOf<UUID, UUID>()

    fun mapped(id: UUID): UUID =
        idMap.getOrPut(id) { UUID.randomUUID() }

    val newRaceId = mapped(race.id)
    val newRace = race.copy(id = newRaceId)

    fun CategoryData.withMappedIds(): CategoryData {
        val newCategoryId = mapped(category.id)
        return copy(
            category = category.copy(
                id = newCategoryId,
                raceId = newRaceId
            ),
            controlPoints = controlPoints.map { controlPoint ->
                controlPoint.copy(
                    id = mapped(controlPoint.id),
                    categoryId = newCategoryId
                )
            },
            competitors = competitors.map { competitor ->
                competitor.copy(
                    id = mapped(competitor.id),
                    raceId = newRaceId,
                    categoryId = competitor.categoryId?.let(::mapped)
                )
            }
        )
    }

    fun CompetitorCategory.withMappedIds(): CompetitorCategory =
        copy(
            competitor = competitor.copy(
                id = mapped(competitor.id),
                raceId = newRaceId,
                categoryId = competitor.categoryId?.let(::mapped)
            ),
            category = category?.let { category ->
                category.copy(
                    id = mapped(category.id),
                    raceId = newRaceId
                )
            }
        )

    fun ReadoutData.withMappedIds(): ReadoutData {
        val newResultId = mapped(result.id)
        return copy(
            result = result.copy(
                id = newResultId,
                raceId = newRaceId,
                competitorId = result.competitorId?.let(::mapped)
            ),
            punches = punches.map { aliasPunch ->
                val alias = aliasPunch.alias
                AliasPunch(
                    punch = aliasPunch.punch.copy(
                        id = mapped(aliasPunch.punch.id),
                        raceId = newRaceId,
                        resultId = aliasPunch.punch.resultId?.let(::mapped)
                    ),
                    alias = alias?.copy(
                        id = mapped(alias.id),
                        raceId = newRaceId
                    )
                )
            }
        )
    }

    return copy(
        race = newRace,
        categories = categories.map { it.withMappedIds() },
        aliases = aliases.map { alias ->
            alias.copy(
                id = mapped(alias.id),
                raceId = newRaceId
            )
        },
        competitorData = competitorData.map { competitorData ->
            CompetitorData(
                competitorCategory = competitorData.competitorCategory.withMappedIds(),
                readoutData = competitorData.readoutData?.withMappedIds()
            )
        },
        unmatchedReadoutData = unmatchedReadoutData.map { it.withMappedIds() }
    )
}
