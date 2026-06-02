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
