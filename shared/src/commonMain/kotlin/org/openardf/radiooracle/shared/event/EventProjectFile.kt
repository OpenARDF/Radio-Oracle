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

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** File envelope for future shared Race File import/export. */
@Serializable
data class EventProjectFile(
    val schemaVersion: Int = EventProjectFileFormat.CURRENT_SCHEMA_VERSION,
    val appName: String = EventProjectFileFormat.APP_NAME,
    /** Independently versioned automatic-result scoring rules applied to this file. */
    val resultsScoringRevision: Int = EventResultScoringFormat.CURRENT_REVISION,
    val raceData: EventRaceData,
    val seriesLink: EventSeriesLink? = null,
    val publicResultsPublication: PublicResultsPublication? = null,
    /** Additive, independently versioned desktop metadata; old readers may omit it. */
    val desktopRouteAnalysis: StoredClassicRouteAnalysis? = null
) {
    /** Returns true when this file schema can be read by the current shared code. */
    fun isSupportedSchema(): Boolean =
        EventProjectFileFormat.isSupportedSchema(schemaVersion)
}

/** Optional backlink from a Race File to its authoritative series manifest entry. */
@Serializable
data class EventSeriesLink(
    val seriesId: String,
    val seriesEventId: String
) {
    init {
        require(seriesId.isNotBlank()) {
            "Series id must not be blank."
        }
        require(seriesEventId.isNotBlank()) {
            "Series race id must not be blank."
        }
    }
}

/** JSON codec for portable `.rom.json` Race Files. */
object EventProjectFileJson {
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    /** Returns the in-memory form that will be persisted when this Race File is written. */
    fun normalizedForStorage(projectFile: EventProjectFile): EventProjectFile =
        reconcileStandardCategoryGenders(
            clearPublicControlLocations(clearLegacyCategoryRaceSettings(projectFile))
        ).copy(
            schemaVersion = EventProjectFileFormat.CURRENT_SCHEMA_VERSION,
            resultsScoringRevision = EventResultScoringFormat.CURRENT_REVISION,
            desktopRouteAnalysis = projectFile.desktopRouteAnalysis?.let { metadata ->
                if (metadata.version != 1) metadata else {
                    val resultIds = projectFile.raceData.competitorData.mapNotNull { it.readoutData?.result?.id }.toSet()
                    val retained = metadata.results.filterKeys { it in resultIds }
                    val contextIds = retained.values.map { it.contextId }.toSet()
                    metadata.copy(results = retained, contexts = metadata.contexts.filterKeys { it in contextIds })
                }
            }
        ).let { EventCourseDrafts.rebaseNormalizedDraft(projectFile, it) }

    /** Encodes a Race File using the stable, shared desktop-beta JSON format. */
    fun encode(projectFile: EventProjectFile): String =
        json.encodeToString(normalizedForStorage(projectFile))

    /**
     * Decodes a Race File and rejects schema versions this build does not support.
     *
     * Unknown fields are tolerated for additive metadata inside a supported schema
     * version, but schema upgrades must still increment `schemaVersion`.
     */
    fun decode(text: String): EventProjectFile {
        val rootJson = json.parseToJsonElement(text).jsonObject
        val storedResultsScoringRevision = rootJson["resultsScoringRevision"]
            ?.jsonPrimitive
            ?.intOrNull
            ?: 0
        val raceDataJson = rootJson["raceData"]
            ?.jsonObject
        val hasControlsField = raceDataJson
            ?.containsKey("controls") == true
        val hasPublicControlIdsField = raceDataJson
            ?.get("categories")
            ?.toString()
            ?.contains("publicControlIds") == true
        val needsControlBackfill = !hasControlsField || !hasPublicControlIdsField || !text.contains("\"controlId\"")
        val needsControlScoringMigration = !text.contains("\"scored\"")
        val projectFile = json.decodeFromString<EventProjectFile>(text)
        require(projectFile.isSupportedSchema()) {
            "Unsupported Radio-Oracle Race File schema version: ${projectFile.schemaVersion}"
        }
        val backfilledProjectFile = if (needsControlBackfill) {
            EventControlCatalog.backfillControls(projectFile)
        } else {
            projectFile
        }
        val migratedProjectFile = if (needsControlScoringMigration) {
            EventControlCatalog.migrateLegacyControlScoring(backfilledProjectFile)
        } else {
            backfilledProjectFile
        }
        val normalizedProjectFile = EventStartNumbers.assignFromDrawnStartTimes(
            reconcileStandardCategoryGenders(clearPublicControlLocations(migratedProjectFile))
        )
        return if (storedResultsScoringRevision < EventResultScoringFormat.CURRENT_REVISION) {
            EventProjectEditor.repairLegacyZeroFoxResults(normalizedProjectFile)
                .projectFile
                .copy(resultsScoringRevision = EventResultScoringFormat.CURRENT_REVISION)
        } else {
            normalizedProjectFile
        }
    }

    private fun clearPublicControlLocations(projectFile: EventProjectFile): EventProjectFile {
        if ((projectFile.raceData.controls + projectFile.raceData.courseDraft?.design?.controls.orEmpty()).none { it.latitude != null || it.longitude != null }) {
            return projectFile
        }
        return projectFile.copy(
            raceData = projectFile.raceData.copy(
                controls = projectFile.raceData.controls.map { control ->
                    control.copy(latitude = null, longitude = null)
                },
                courseDraft = projectFile.raceData.courseDraft?.let { draft -> draft.copy(design = draft.design.copy(
                    controls = draft.design.controls.map { it.copy(latitude = null, longitude = null) })) }
            )
        )
    }

    private fun clearLegacyCategoryRaceSettings(projectFile: EventProjectFile): EventProjectFile =
        EventCourseDrafts.mapProtectedCategories(projectFile) { data ->
            if (data.category.hasLegacyRaceSettings()) data.copy(category = data.category.withEventLevelRaceSettings()) else data
        }

    private fun reconcileStandardCategoryGenders(projectFile: EventProjectFile): EventProjectFile =
        EventCourseDrafts.mapProtectedCategories(projectFile, ::reconcileStandardCategoryGender)

    private fun reconcileStandardCategoryGender(categoryData: EventCategoryData): EventCategoryData {
        val reconciledIsMan = StandardCategoryRules.reconcileIsManWithName(
            categoryData.category.name,
            categoryData.category.isMan
        )
        if (reconciledIsMan == categoryData.category.isMan) {
            return categoryData
        }
        return categoryData.copy(category = categoryData.category.copy(isMan = reconciledIsMan))
    }

    private fun EventCategory.hasLegacyRaceSettings(): Boolean =
        differentProperties || raceType != null || raceBand != null || timeLimitSeconds != null

    private fun EventCategory.withEventLevelRaceSettings(): EventCategory =
        copy(
            differentProperties = false,
            raceType = null,
            raceBand = null,
            timeLimitSeconds = null
        )
}

/** Schema metadata for portable Radio-Oracle Race Files. */
object EventProjectFileFormat {
    const val APP_NAME = "Radio-Oracle"
    const val CURRENT_SCHEMA_VERSION = 7

    /** Returns true when the supplied schema version is within the supported range. */
    fun isSupportedSchema(schemaVersion: Int): Boolean =
        schemaVersion in 1..CURRENT_SCHEMA_VERSION
}

/** Revision metadata for scoring migrations that do not otherwise change the Race File schema. */
object EventResultScoringFormat {
    const val CURRENT_REVISION = 6
}
