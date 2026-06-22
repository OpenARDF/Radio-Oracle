package org.openardf.radiooracle.shared.event

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDateTime

/** Legacy manifest file name used inside a Radio-Oracle Event Series folder. */
const val EVENT_SERIES_FILE_NAME = "series.radio-oracle.json"

/** Suffix for user-named Event Series manifests, for example `Championship.series.radio-oracle.json`. */
const val EVENT_SERIES_NAMED_FILE_SUFFIX = ".series.radio-oracle.json"

/** Returns true for the legacy fixed manifest name and the newer user-named manifest convention. */
fun isEventSeriesFileName(fileName: String): Boolean =
    fileName == EVENT_SERIES_FILE_NAME ||
        fileName.endsWith(EVENT_SERIES_NAMED_FILE_SUFFIX, ignoreCase = true)

/** Portable manifest for a multi-event competition or championship. */
@Serializable
data class EventSeriesFile(
    val schemaVersion: Int = EventSeriesFileFormat.CURRENT_SCHEMA_VERSION,
    val appName: String = EventProjectFileFormat.APP_NAME,
    val seriesId: String,
    val name: String,
    val events: List<EventSeriesEvent>,
    val competitorMatchOverrides: List<EventSeriesCompetitorMatchOverride> = emptyList()
) {
    init {
        require(seriesId.isNotBlank()) {
            "Series id must not be blank."
        }
        require(name.isNotBlank()) {
            "Series name must not be blank."
        }
        validateMembership()
    }

    /** Returns true when this file schema can be read by the current shared code. */
    fun isSupportedSchema(): Boolean =
        EventSeriesFileFormat.isSupportedSchema(schemaVersion)

    fun sortedEvents(): List<EventSeriesEvent> {
        val eventsWithDates = events.map { event -> event to event.seriesStartDateTimeOrNull() }
        /*
         * Championship history is easier to read by event date, but only when every
         * manifest entry has a parseable date. Partial date sorting would move some
         * entries while leaving undated events ambiguous, so stored order remains the
         * predictable fallback.
         */
        return if (eventsWithDates.isNotEmpty() && eventsWithDates.all { it.second != null }) {
            eventsWithDates
                .sortedWith(compareBy({ it.second }, { it.first.order }, { it.first.displayName }, { it.first.seriesEventId }))
                .map { it.first }
        } else {
            events.sortedWith(compareBy({ it.order }, { it.displayName }, { it.seriesEventId }))
        }
    }

    fun usesDateTimeEventOrder(): Boolean =
        events.isNotEmpty() && events.all { it.seriesStartDateTimeOrNull() != null }

    private fun validateMembership() {
        val duplicateEventIds = events.groupBy { it.seriesEventId }.filterValues { it.size > 1 }.keys
        require(duplicateEventIds.isEmpty()) {
            "Series contains duplicate event ids: ${duplicateEventIds.joinToString()}"
        }
        val duplicateOrders = events.groupBy { it.order }.filterValues { it.size > 1 }.keys
        require(duplicateOrders.isEmpty()) {
            "Series contains duplicate event order values: ${duplicateOrders.joinToString()}"
        }
        val duplicatePaths = events.groupBy { it.eventFilePath }.filterValues { it.size > 1 }.keys
        require(duplicatePaths.isEmpty()) {
            "Series contains duplicate event file paths: ${duplicatePaths.joinToString()}"
        }
        val duplicateOverrides = competitorMatchOverrides
            .groupBy { it.competitorKey() }
            .filterValues { it.size > 1 }
            .keys
        require(duplicateOverrides.isEmpty()) {
            "Series contains duplicate competitor match overrides: ${duplicateOverrides.joinToString()}"
        }
        val eventIds = events.map { it.seriesEventId }.toSet()
        val overridesWithUnknownEvents = competitorMatchOverrides.filter {
            it.fromSeriesEventId !in eventIds || it.toSeriesEventId !in eventIds
        }
        require(overridesWithUnknownEvents.isEmpty()) {
            "Series contains competitor match overrides for unknown event ids: " +
                overridesWithUnknownEvents.joinToString { it.eventKey() }
        }
        val sameEventOverrides = competitorMatchOverrides.filter {
            it.fromSeriesEventId == it.toSeriesEventId
        }
        require(sameEventOverrides.isEmpty()) {
            "Series contains competitor match overrides within the same event: " +
                sameEventOverrides.joinToString { it.eventKey() }
        }
    }
}

/** One manifest-owned member event. Paths are relative to the series folder. */
@Serializable
data class EventSeriesEvent(
    val seriesEventId: String,
    val eventFilePath: String,
    val order: Int,
    val displayName: String,
    val startDateTimeIso: String = "",
    val formatLabel: String = ""
) {
    init {
        require(seriesEventId.isNotBlank()) {
            "Series event id must not be blank."
        }
        require(eventFilePath.isNotBlank()) {
            "Series event file path must not be blank."
        }
        require(order >= 0) {
            "Series event order must not be negative."
        }
        require(displayName.isNotBlank()) {
            "Series event display name must not be blank."
        }
        require(!eventFilePath.startsWith("/")) {
            "Series event file path must be relative."
        }
        require(eventFilePath.split('/').none { it == ".." }) {
            "Series event file path must not escape the series folder."
        }
    }
}

private fun EventSeriesEvent.seriesStartDateTimeOrNull(): LocalDateTime? =
    runCatching {
        LocalDateTime.parse(startDateTimeIso.trim().replace(' ', 'T'))
    }.getOrNull()

/** Operator-approved identity match for one competitor across two series events. */
@Serializable
data class EventSeriesCompetitorMatchOverride(
    val fromSeriesEventId: String,
    val fromCompetitorId: String,
    val toSeriesEventId: String,
    val toCompetitorId: String
) {
    init {
        require(fromSeriesEventId.isNotBlank()) {
            "Override source event id must not be blank."
        }
        require(fromCompetitorId.isNotBlank()) {
            "Override source competitor id must not be blank."
        }
        require(toSeriesEventId.isNotBlank()) {
            "Override target event id must not be blank."
        }
        require(toCompetitorId.isNotBlank()) {
            "Override target competitor id must not be blank."
        }
    }

    fun competitorKey(): String =
        listOf(fromSeriesEventId, fromCompetitorId, toSeriesEventId, toCompetitorId).joinToString("|")

    fun eventKey(): String =
        "$fromSeriesEventId->$toSeriesEventId"
}

/** JSON codec for portable Event Series manifests. */
object EventSeriesFileJson {
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun encode(seriesFile: EventSeriesFile): String =
        json.encodeToString(seriesFile)

    fun decode(text: String): EventSeriesFile {
        val seriesFile = json.decodeFromString<EventSeriesFile>(text)
        require(seriesFile.isSupportedSchema()) {
            "Unsupported Radio-Oracle Event Series schema version: ${seriesFile.schemaVersion}"
        }
        return seriesFile
    }
}

/** Schema metadata for portable Radio-Oracle Event Series manifests. */
object EventSeriesFileFormat {
    const val CURRENT_SCHEMA_VERSION = 1

    /** Returns true when the supplied schema version is within the supported range. */
    fun isSupportedSchema(schemaVersion: Int): Boolean =
        schemaVersion in 1..CURRENT_SCHEMA_VERSION
}
