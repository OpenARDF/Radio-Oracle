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

package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.event.EventCompetitor
import org.openardf.radiooracle.shared.event.EventCompetitorCategory
import org.openardf.radiooracle.shared.event.EventCompetitorData
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventReadoutCourseMatcher
import org.openardf.radiooracle.shared.event.EventSeriesEvent
import org.openardf.radiooracle.shared.event.PracticeCompetitorCategoryAssignment
import org.openardf.radiooracle.shared.sportident.SportIdentCardReadout
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime

data class DesktopSeriesSportIdentReadoutMatch(
    val event: EventSeriesEvent,
    val eventPath: Path,
    val projectFile: EventProjectFile
)

data class DesktopSeriesPracticeInForestUpdate(
    val updatedEventPaths: Set<Path>,
    val updatedCompetitorCount: Int
)

object DesktopSeriesSportIdentReadoutRouter {
    fun isBlankPracticeStartReadout(readout: SportIdentCardReadout): Boolean =
        readout.punches.isEmpty() &&
            readout.startTime == null &&
            readout.finishTime == null

    fun matchingEventForReadout(
        store: EventSeriesStore,
        manifestPath: Path,
        readout: SportIdentCardReadout
    ): DesktopSeriesSportIdentReadoutMatch? {
        val punchCodes = readout.punches
            .map { it.siCode }
            .toSet()
            .takeIf { it.isNotEmpty() }
            ?: return null
        val matches = loadSeriesMembers(store, manifestPath).mapNotNull { member ->
            EventReadoutCourseMatcher.bestMatch(
                raceData = member.projectFile.raceData,
                siNumber = readout.siNumber,
                controlPunchCodes = readout.punches.map { it.siCode }
            )?.takeIf { it.quality.matchedPunchCount > 0 }
                ?.let { match -> member to match }
        }
        val bestQuality = matches.maxOfOrNull { it.second.quality } ?: return null
        return matches.filter { it.second.quality == bestQuality }.singleOrNull()?.first
    }

    fun startPracticeCompetitorInForestAcrossSeries(
        store: EventSeriesStore,
        manifestPath: Path,
        readout: SportIdentCardReadout,
        readoutDateTime: LocalDateTime
    ): DesktopSeriesPracticeInForestUpdate {
        val members = loadSeriesMembers(store, manifestPath)
            .filter { it.projectFile.raceData.race.raceLevel == RaceLevel.PRACTICE }
        return writeChangedMembers(store, members) { projectFile ->
            startPracticeCompetitorInForest(projectFile, readout, readoutDateTime)
        }
    }

    fun clearPracticeCompetitorInForestAcrossSeries(
        store: EventSeriesStore,
        manifestPath: Path,
        siNumber: Int
    ): DesktopSeriesPracticeInForestUpdate {
        val members = loadSeriesMembers(store, manifestPath)
            .filter { it.projectFile.raceData.race.raceLevel == RaceLevel.PRACTICE }
        return writeChangedMembers(store, members) { projectFile ->
            clearPracticeCompetitorInForest(projectFile, siNumber)
        }
    }

    fun addPracticeCompetitorAcrossSeries(
        store: EventSeriesStore,
        manifestPath: Path,
        readout: SportIdentCardReadout,
        categoryName: String
    ): DesktopSeriesPracticeInForestUpdate {
        val members = loadSeriesMembers(store, manifestPath)
            .filter { it.projectFile.raceData.race.raceLevel == RaceLevel.PRACTICE }
        return writeChangedMembers(store, members) { projectFile ->
            addPracticeCompetitor(projectFile, readout, categoryName)
        }
    }

    fun startPracticeCompetitorInForest(
        projectFile: EventProjectFile,
        readout: SportIdentCardReadout,
        readoutDateTime: LocalDateTime
    ): EventProjectFile {
        if (projectFile.raceData.race.raceLevel != RaceLevel.PRACTICE) {
            return projectFile
        }
        val startSeconds = practiceStartSeconds(projectFile, readoutDateTime)
        val existingIndex = projectFile.raceData.competitorData.indexOfFirst {
            it.competitorCategory.competitor.siNumber == readout.siNumber
        }
        val updatedCompetitorData = if (existingIndex >= 0) {
            projectFile.raceData.competitorData.mapIndexed { index, data ->
                if (index != existingIndex || data.readoutData != null) {
                    data
                } else {
                    data.copy(
                        competitorCategory = data.competitorCategory.copy(
                            competitor = data.competitorCategory.competitor.copy(
                                drawnStartTimeSeconds = startSeconds
                            )
                        )
                    )
                }
            }
        } else {
            val categoryData = PracticeCompetitorCategoryAssignment.longestCourseCategory(projectFile.raceData)
            projectFile.raceData.competitorData + EventCompetitorData(
                competitorCategory = EventCompetitorCategory(
                    competitor = practiceCompetitorForReadout(
                        projectFile,
                        readout,
                        startSeconds,
                        categoryData?.category?.id
                    ),
                    category = categoryData?.category
                ),
                readoutData = null
            )
        }
        return if (updatedCompetitorData == projectFile.raceData.competitorData) {
            projectFile
        } else {
            projectFile.copy(
                raceData = projectFile.raceData.copy(competitorData = updatedCompetitorData)
            )
        }
    }

    fun clearPracticeCompetitorInForest(projectFile: EventProjectFile, siNumber: Int): EventProjectFile {
        if (projectFile.raceData.race.raceLevel != RaceLevel.PRACTICE) {
            return projectFile
        }
        val updatedCompetitorData = projectFile.raceData.competitorData.map { data ->
            val competitor = data.competitorCategory.competitor
            if (competitor.siNumber == siNumber && data.readoutData == null && competitor.drawnStartTimeSeconds != null) {
                data.copy(
                    competitorCategory = data.competitorCategory.copy(
                        competitor = competitor.copy(drawnStartTimeSeconds = null)
                    )
                )
            } else {
                data
            }
        }
        return if (updatedCompetitorData == projectFile.raceData.competitorData) {
            projectFile
        } else {
            projectFile.copy(
                raceData = projectFile.raceData.copy(competitorData = updatedCompetitorData)
            )
        }
    }

    fun addPracticeCompetitor(
        projectFile: EventProjectFile,
        readout: SportIdentCardReadout,
        categoryName: String
    ): EventProjectFile {
        if (projectFile.raceData.race.raceLevel != RaceLevel.PRACTICE) {
            return projectFile
        }
        val existingIndex = projectFile.raceData.competitorData.indexOfFirst {
            it.competitorCategory.competitor.siNumber == readout.siNumber
        }
        val categoryData = PracticeCompetitorCategoryAssignment.categoryNamedLike(
            projectFile.raceData,
            categoryName
        ) ?: PracticeCompetitorCategoryAssignment.longestCourseCategory(projectFile.raceData)
        if (existingIndex >= 0) {
            val existingData = projectFile.raceData.competitorData[existingIndex]
            if (existingData.competitorCategory.competitor.categoryId != null || categoryData == null) {
                return projectFile
            }
            return projectFile.copy(
                raceData = projectFile.raceData.copy(
                    competitorData = projectFile.raceData.competitorData.mapIndexed { index, data ->
                        if (index != existingIndex) data else data.copy(
                            competitorCategory = data.competitorCategory.copy(
                                competitor = data.competitorCategory.competitor.copy(
                                    categoryId = categoryData.category.id,
                                    isMan = categoryData.category.isMan
                                ),
                                category = categoryData.category
                            )
                        )
                    }
                )
            )
        }
        val competitor = practiceCompetitorForReadout(
            projectFile = projectFile,
            readout = readout,
            startSeconds = null,
            categoryId = categoryData?.category?.id
        )
        return projectFile.copy(
            raceData = projectFile.raceData.copy(
                competitorData = projectFile.raceData.competitorData + EventCompetitorData(
                    competitorCategory = EventCompetitorCategory(competitor, categoryData?.category),
                    readoutData = null
                )
            )
        )
    }

    private fun writeChangedMembers(
        store: EventSeriesStore,
        members: List<DesktopSeriesSportIdentReadoutMatch>,
        transform: (EventProjectFile) -> EventProjectFile
    ): DesktopSeriesPracticeInForestUpdate {
        val updatedPaths = mutableSetOf<Path>()
        var updatedCompetitors = 0
        members.forEach { member ->
            val updatedProject = transform(member.projectFile)
            if (updatedProject != member.projectFile) {
                store.writeEvent(member.eventPath, updatedProject)
                updatedPaths.add(member.eventPath.toAbsolutePath().normalize())
                updatedCompetitors += 1
            }
        }
        return DesktopSeriesPracticeInForestUpdate(updatedPaths, updatedCompetitors)
    }

    private fun loadSeriesMembers(store: EventSeriesStore, manifestPath: Path): List<DesktopSeriesSportIdentReadoutMatch> {
        val seriesFile = store.read(manifestPath)
        if (seriesFile.events.size < 2) {
            return emptyList()
        }
        val seriesFolder = requireNotNull(manifestPath.parent) {
            "Race Series manifest must have a parent folder."
        }
        return seriesFile.sortedEvents().mapNotNull { event ->
            val eventPath = seriesFolder.resolve(event.eventFilePath).normalize()
            if (!store.exists(eventPath)) {
                return@mapNotNull null
            }
            DesktopSeriesSportIdentReadoutMatch(event, eventPath, store.readEvent(eventPath))
        }
    }

    private fun practiceCompetitorForReadout(
        projectFile: EventProjectFile,
        readout: SportIdentCardReadout,
        startSeconds: Long?,
        categoryId: String?
    ): EventCompetitor {
        val holder = readout.cardHolder
        val firstName = holder?.firstName?.trim().orEmpty()
        val lastName = holder?.lastName?.trim().orEmpty()
        return EventCompetitor(
            id = uniquePracticeCompetitorId(projectFile, readout.siNumber),
            raceId = projectFile.raceData.race.id,
            categoryId = categoryId,
            firstName = firstName.ifEmpty { "SI ${readout.siNumber}" },
            lastName = lastName.ifEmpty { "Practice" },
            club = holder?.club?.trim().orEmpty(),
            index = "",
            isMan = true,
            birthYear = null,
            siNumber = readout.siNumber,
            siRent = false,
            drawnStartTimeSeconds = startSeconds
        )
    }

    private fun uniquePracticeCompetitorId(projectFile: EventProjectFile, siNumber: Int): String {
        val existingIds = projectFile.raceData.competitorData
            .mapTo(mutableSetOf()) { it.competitorCategory.competitor.id }
        val baseId = "practice-competitor-si-$siNumber"
        if (baseId !in existingIds) {
            return baseId
        }
        var suffix = 2
        while ("$baseId-$suffix" in existingIds) {
            suffix += 1
        }
        return "$baseId-$suffix"
    }

    private fun practiceStartSeconds(projectFile: EventProjectFile, readoutDateTime: LocalDateTime): Long =
        runCatching {
            Duration.between(LocalDateTime.parse(projectFile.raceData.race.startDateTimeIso), readoutDateTime)
                .seconds
                .coerceAtLeast(0L)
        }.getOrDefault(0L)
}
