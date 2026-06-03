package org.openardf.radiooracle.shared.event

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** File envelope for future shared Event File import/export. */
@Serializable
data class EventProjectFile(
    val schemaVersion: Int = EventProjectFileFormat.CURRENT_SCHEMA_VERSION,
    val appName: String = EventProjectFileFormat.APP_NAME,
    val raceData: EventRaceData
) {
    /** Returns true when this file schema can be read by the current shared code. */
    fun isSupportedSchema(): Boolean =
        EventProjectFileFormat.isSupportedSchema(schemaVersion)
}

/** JSON codec for portable `.rom.json` Event Files. */
object EventProjectFileJson {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    /** Encodes an Event File using the stable, shared desktop-beta JSON format. */
    fun encode(projectFile: EventProjectFile): String =
        json.encodeToString(projectFile)

    /**
     * Decodes an Event File and rejects schema versions this build does not support.
     *
     * Unknown fields are tolerated for additive metadata inside a supported schema
     * version, but schema upgrades must still increment `schemaVersion`.
     */
    fun decode(text: String): EventProjectFile {
        val projectFile = json.decodeFromString<EventProjectFile>(text)
        require(projectFile.isSupportedSchema()) {
            "Unsupported Radio-Oracle Event File schema version: ${projectFile.schemaVersion}"
        }
        return projectFile
    }
}

/** Schema metadata for portable Radio-Oracle Event Files. */
object EventProjectFileFormat {
    const val APP_NAME = "Radio-Oracle"
    const val CURRENT_SCHEMA_VERSION = 1

    /** Returns true when the supplied schema version is within the supported range. */
    fun isSupportedSchema(schemaVersion: Int): Boolean =
        schemaVersion in 1..CURRENT_SCHEMA_VERSION
}
