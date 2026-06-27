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

import org.openardf.radiooracle.backend.room.entity.Alias
import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorData
import org.openardf.radiooracle.backend.room.entity.embeddeds.ControlPointAlias
import org.openardf.radiooracle.backend.room.entity.embeddeds.ReadoutData
import org.openardf.radiooracle.backend.room.entity.embeddeds.ResultData

/** Applies race-scoped alias matches after Room's single-column relations have been loaded. */
object RaceScopedAliasResolver {
    fun resolveControlPoints(
        controlPoints: List<ControlPointAlias>,
        raceAliases: List<Alias>
    ): List<ControlPointAlias> {
        val aliasesByCode = raceAliases.associateBy { it.siCode }
        return controlPoints.onEach { controlPointAlias ->
            controlPointAlias.alias = aliasesByCode[controlPointAlias.controlPoint.siCode]
        }
    }

    fun resolveResultData(
        resultData: ResultData,
        raceAliases: List<Alias>
    ): ResultData {
        resolveReadoutData(ReadoutData(resultData.result, resultData.punches), raceAliases)
        return resultData
    }

    fun resolveResultData(
        resultData: List<ResultData>,
        raceAliases: List<Alias>
    ): List<ResultData> =
        resultData.onEach { resolveResultData(it, raceAliases) }

    fun resolveCompetitorData(
        competitorData: List<CompetitorData>,
        raceAliases: List<Alias>
    ): List<CompetitorData> =
        competitorData.onEach { data ->
            data.readoutData?.let { resolveReadoutData(it, raceAliases) }
        }

    private fun resolveReadoutData(
        readoutData: ReadoutData,
        raceAliases: List<Alias>
    ) {
        val aliasesByCode = raceAliases.associateBy { it.siCode }
        readoutData.punches.forEach { aliasPunch ->
            aliasPunch.alias = aliasesByCode[aliasPunch.punch.siCode]
        }
    }
}
