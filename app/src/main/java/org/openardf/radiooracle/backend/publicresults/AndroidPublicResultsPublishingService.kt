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

package org.openardf.radiooracle.backend.publicresults

import android.content.Context
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.room.entity.embeddeds.EventSeriesData
import org.openardf.radiooracle.backend.room.entity.embeddeds.RaceData
import org.openardf.radiooracle.backend.shared.toEventRaceData
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventResultDetails
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.publicresults.CloudflarePagesPublisher
import org.openardf.radiooracle.shared.publicresults.PublicResultsRaceRenderRequest
import java.time.Instant
import java.util.UUID

data class AndroidPublicResultsTarget(
    val name: String,
    val isSeries: Boolean,
    val raceCount: Int,
    val savedUrl: String?,
    val publishedAtIso: String?,
    val needsRacePasswordForDiagrams: Boolean
)

data class AndroidPublicResultsPublishOutcome(
    val name: String,
    val url: String,
    val publishedAtIso: String,
    val output: String,
    val persistenceWarning: String? = null
)

/** Coordinates Room data, shared rendering, Cloudflare upload, and publication persistence. */
class AndroidPublicResultsPublishingService(
    context: Context,
    private val dataProcessor: DataProcessor = DataProcessor.get(),
    private val publisher: AndroidCloudflarePagesPublisher =
        AndroidCloudflarePagesPublisher()
) {
    private val appContext = context.applicationContext

    suspend fun target(raceId: UUID): AndroidPublicResultsTarget {
        val series = dataProcessor.getEventSeriesForRace(raceId)
        return if (series == null) {
            val raceData = dataProcessor.getRaceData(raceId)
            AndroidPublicResultsTarget(
                name = raceData.race.name,
                isSeries = false,
                raceCount = 1,
                savedUrl = raceData.race.publicResultsUrl,
                publishedAtIso = raceData.race.publicResultsPublishedAtIso,
                needsRacePasswordForDiagrams = raceData.needsPasswordForCourseDiagrams()
            )
        } else {
            val races = series.orderedMembers().map { dataProcessor.getRaceData(it.localRaceId) }
            AndroidPublicResultsTarget(
                name = series.series.name,
                isSeries = true,
                raceCount = races.size,
                savedUrl = series.series.publicResultsUrl,
                publishedAtIso = series.series.publicResultsPublishedAtIso,
                needsRacePasswordForDiagrams = races.any { it.needsPasswordForCourseDiagrams() }
            )
        }
    }

    suspend fun publish(
        raceId: UUID,
        settings: AndroidCloudflarePagesPublishSettings,
        includeCourseDiagrams: Boolean,
        racePassword: String?
    ): AndroidPublicResultsPublishOutcome {
        require(settings.isComplete()) {
            "Complete Cloudflare Settings before publishing."
        }
        val series = dataProcessor.getEventSeriesForRace(raceId)
        val generatedAt = Instant.now().toString()
        val root = AndroidPublicResultsSiteMirror.prepare(appContext, settings)
        val export = if (series == null) {
            val raceData = dataProcessor.getRaceData(raceId)
            AndroidPublicResultsSiteExports.exportRace(
                directory = root,
                race = raceData.renderRequest(includeCourseDiagrams, racePassword),
                generatedAtIso = generatedAt,
                appVersion = dataProcessor.getAppVersion()
            )
        } else {
            val races = series.orderedMembers().map { member ->
                dataProcessor.getRaceData(member.localRaceId)
                    .renderRequest(includeCourseDiagrams, racePassword)
            }
            AndroidPublicResultsSiteExports.exportSeries(
                directory = root,
                seriesId = series.series.seriesId,
                seriesName = series.series.name,
                races = races,
                generatedAtIso = generatedAt,
                appVersion = dataProcessor.getAppVersion()
            )
        }
        val result = publisher.publish(export.directory, settings)
        val url = CloudflarePagesPublisher.publicResultsUrl(result.url, export.eventPath)
        val persistenceWarning = runCatching {
            persistPublication(raceId, series, url, generatedAt)
        }.exceptionOrNull()?.let { it.message ?: it::class.simpleName.orEmpty() }
        return AndroidPublicResultsPublishOutcome(
            name = series?.series?.name ?: dataProcessor.getRace(raceId)?.name.orEmpty(),
            url = url,
            publishedAtIso = generatedAt,
            output = result.output,
            persistenceWarning = persistenceWarning
        )
    }

    private suspend fun persistPublication(
        raceId: UUID,
        series: EventSeriesData?,
        url: String,
        publishedAtIso: String
    ) {
        if (series != null) {
            dataProcessor.saveEventSeries(
                series = series.series.copy(
                    publicResultsUrl = url,
                    publicResultsPublishedAtIso = publishedAtIso
                ),
                members = series.members
            )
        } else {
            val race = requireNotNull(dataProcessor.getRace(raceId)) {
                "The published race is no longer available."
            }
            dataProcessor.updateRace(
                race.copy(
                    publicResultsUrl = url,
                    publicResultsPublishedAtIso = publishedAtIso
                )
            )
        }
    }

    private fun RaceData.renderRequest(
        includeCourseDiagrams: Boolean,
        racePassword: String?
    ): PublicResultsRaceRenderRequest =
        PublicResultsRaceRenderRequest(
            projectFile = EventProjectFile(raceData = toEventRaceData()),
            protectedCourseInfoByCategoryId = if (includeCourseDiagrams) {
                unlockedCourseInfo(racePassword)
            } else {
                emptyMap()
            }
        )

    private fun RaceData.unlockedCourseInfo(password: String?): Map<String, ProtectedCourseInfo> {
        if (!needsPasswordForCourseDiagrams()) {
            return emptyMap()
        }
        val value = password?.trim().orEmpty()
        require(value.isNotEmpty()) {
            "Race Password is required to include 2D course diagrams."
        }
        return categories.mapNotNull { categoryData ->
            categoryData.category.encryptedCourseInfo
                ?.takeIf(String::isNotBlank)
                ?.let { encrypted ->
                    categoryData.category.id.toString() to
                        AndroidProtectedCourseInfo.decryptCourseInfo(encrypted, value)
                }
        }.toMap()
    }

    private fun RaceData.needsPasswordForCourseDiagrams(): Boolean {
        val categoryIdsWithResults = EventResultDetails.from(toEventRaceData())
            .mapNotNull(EventResultDetails::categoryId)
            .toSet()
        return categories.any { categoryData ->
            categoryData.category.id.toString() in categoryIdsWithResults &&
                !categoryData.category.encryptedCourseInfo.isNullOrBlank()
        }
    }
}
