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

package org.openardf.radiooracle.backend.files.processors

import ResultJsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.files.constants.DataFormat
import org.openardf.radiooracle.backend.files.constants.DataType
import org.openardf.radiooracle.backend.files.json.adapters.FinalResultJsonAdapter
import org.openardf.radiooracle.backend.files.json.adapters.LocalDateTimeAdapter
import org.openardf.radiooracle.backend.files.json.adapters.RaceDataJsonAdapter
import org.openardf.radiooracle.backend.files.json.temps.ResultCompetitorJson
import org.openardf.radiooracle.backend.files.json.temps.RobisResponseJson
import org.openardf.radiooracle.backend.files.wrappers.DataImportWrapper
import org.openardf.radiooracle.backend.results.ResultsProcessor
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.room.entity.embeddeds.CategoryData
import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorData
import org.openardf.radiooracle.backend.room.entity.embeddeds.RaceData
import org.openardf.radiooracle.backend.shared.toRoomRaceData
import org.openardf.radiooracle.shared.event.EventProjectFileJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID

/** Import/export processor for JSON race backups and live/final result feeds. */
@OptIn(ExperimentalStdlibApi::class)
object JsonProcessor : FormatProcessor {

    /** JSON imports through this format contract are not implemented except for full race backups. */
    override suspend fun importData(
        inStream: InputStream,
        dataType: DataType,
        race: Race,
        dataProcessor: DataProcessor
    ): DataImportWrapper {
        when (dataType) {
            DataType.CATEGORIES -> TODO()
            DataType.COMPETITORS -> TODO()
            else -> TODO()
        }
    }

    /** Exports supported JSON result feeds; full race backups are handled by [exportRaceData]. */
    override suspend fun exportData(
        outStream: OutputStream,
        dataType: DataType,
        format: DataFormat,
        dataProcessor: DataProcessor,
        race: Race
    ) {
        when (dataType) {
            DataType.CATEGORIES -> TODO()
            DataType.COMPETITORS -> TODO()
            DataType.RESULTS_LIVE -> exportLiveResults(
                outStream,
                race,
                dataProcessor
            )

            DataType.RESULTS_FINAL -> exportFinalResults(
                outStream,
                race,
                dataProcessor
            )

            else -> TODO()
        }
    }

    /** Placeholder for future competitor-only JSON import support. */
    fun importCompetitorData(
        inStream: InputStream,
        race: Race,
        categories: HashSet<CategoryData>
    ): DataImportWrapper {
        TODO()
    }

    /** Placeholder for future category-only JSON import support. */
    suspend fun importCategories() {

    }

    /** Placeholder for future competitor JSON import support. */
    suspend fun importCompetitors() {

    }

    /** Imports a full race backup from a UTF-8 JSON stream. */
    fun importRaceData(inStream: InputStream, dataProcessor: DataProcessor): RaceData {
        val jsonString = inStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        return importRaceData(jsonString, dataProcessor)
    }

    /** Imports a full race backup from a JSON string using the race-data Moshi adapter. */
    fun importRaceData(jsonString: String, dataProcessor: DataProcessor): RaceData {
        val fingerprint = jsonString.sha256Hex()
        if (jsonString.contains("\"appName\"") && jsonString.contains("\"raceData\"")) {
            val eventRaceData = EventProjectFileJson.decode(jsonString).raceData
            return eventRaceData.toRoomRaceData().withImportIdentity(
                sourceId = "event-file:${eventRaceData.race.id}",
                fingerprint = fingerprint
            )
        }

        val moshi: Moshi = Moshi.Builder()
            .add(RaceDataJsonAdapter(dataProcessor))
            .add(LocalDateTimeAdapter())
            .add(KotlinJsonAdapterFactory())
            .build()
        val adapter = moshi.adapter<RaceData>()

        val raceData = adapter.nonNull().fromJson(jsonString)!!
        return raceData.withImportIdentity(
            sourceId = "android-race:${raceData.race.id}",
            fingerprint = fingerprint
        )
    }

    /** Parses the ROBIS response shape returned by the live-result service. */
    fun parseRobisResponse(response: String): RobisResponseJson? {
        val moshi: Moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(RobisResponseJson::class.java)

        return adapter.fromJson(response)
    }

    /** Exports complete final results, including categories, aliases, and competitor readouts. */
    suspend fun exportFinalResults(
        outStream: OutputStream,
        race: Race,
        dataProcessor: DataProcessor
    ) {
        withContext(Dispatchers.IO) {
            val moshi: Moshi = Moshi.Builder()
                .add(ResultJsonAdapter(race, dataProcessor))
                .add(FinalResultJsonAdapter(dataProcessor))
                .add(LocalDateTimeAdapter())
                .add(KotlinJsonAdapterFactory())
                .build()

            val adapter = moshi.adapter<RaceData>()
            val raceData: RaceData = dataProcessor.getRaceData(race.id)

            val json = adapter.toJson(raceData)
            outStream.write(json.toByteArray(Charsets.UTF_8))
            outStream.flush()
        }
    }

    /** Exports live results as a flat list of competitors with matched readouts. */
    suspend fun exportLiveResults(
        outStream: OutputStream,
        race: Race,
        dataProcessor: DataProcessor
    ) {
        withContext(Dispatchers.IO) {
            val moshi: Moshi = Moshi.Builder()
                .add(ResultJsonAdapter(race, dataProcessor))
                .add(LocalDateTimeAdapter())
                .add(KotlinJsonAdapterFactory())
                .build()

            val type =
                Types.newParameterizedType(List::class.java, ResultCompetitorJson::class.java)
            val adapter = moshi.adapter<List<ResultCompetitorJson>>(type)

            val results = ResultsProcessor.getCompetitorDataByRace(race.id, dataProcessor)
            val exportList = results.mapNotNull { rd ->
                // Live result feeds only include competitors with a readout and assigned category.
                val result = rd.readoutData ?: return@mapNotNull null

                val compCat = rd.competitorCategory
                val competitor = compCat.competitor
                val category = compCat.category ?: return@mapNotNull null

                ResultCompetitorJson(
                    competitor_index = competitor.index,
                    si_number = competitor.siNumber ?: 0,
                    last_name = competitor.lastName,
                    first_name = competitor.firstName,
                    competitor_category = category.name,
                    result = ResultJsonAdapter(race, dataProcessor).toJson(rd)
                )
            }

            val json = adapter.toJson(exportList)
            outStream.write(json.toByteArray(Charsets.UTF_8))
            outStream.flush()
        }
    }


    /** Exports a complete race backup suitable for later import into a fresh race. */
    suspend fun exportRaceData(
        outStream: OutputStream,
        dataProcessor: DataProcessor,
        raceId: UUID
    ) {
        withContext(Dispatchers.IO) {
            val moshi: Moshi = Moshi.Builder()
                .add(RaceDataJsonAdapter(dataProcessor))
                .add(LocalDateTimeAdapter())
                .add(KotlinJsonAdapterFactory())
                .build()
            val raceData: RaceData = dataProcessor.getRaceData(raceId)
            val adapter = moshi.adapter<RaceData>()

            val json = adapter.toJson(raceData)

            outStream.write(json.toByteArray(Charsets.UTF_8))

            outStream.flush()
        }
    }
}

private fun RaceData.withImportIdentity(sourceId: String, fingerprint: String): RaceData {
    race.importSourceId = sourceId
    race.importFingerprint = fingerprint
    return this
}

private fun String.sha256Hex(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
