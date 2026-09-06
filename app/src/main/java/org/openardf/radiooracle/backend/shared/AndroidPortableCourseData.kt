package org.openardf.radiooracle.backend.shared

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.openardf.radiooracle.shared.event.*

private val portableCourseJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }

internal fun encodePortableCourseData(race: EventRaceData): String {
    return portableCourseJson.encodeToString(EventPortableCourseCatalog(controls = race.controls.map { it.copy(latitude = null, longitude = null) }, courseMappings = race.courseMappings, courseDraft = race.courseDraft))
}

/** Merge native edits through explicit references, then reuse the legacy adapter only for new/unreferenced rows. */
internal fun restorePortableCourseData(native: EventRaceData, stored: String?, categoryIds: Map<String, String>): EventRaceData {
    if (stored == null) {
        val legacy = native.copy(categories = native.categories.map { category ->
            category.copy(controlPoints = category.controlPoints.map { it.copy(controlId = "") })
        })
        return EventControlCatalog.backfillControls(EventProjectFile(raceData = legacy)).raceData
    }
    val saved = portableCourseJson.decodeFromString<EventPortableCourseCatalog>(stored)
    require(saved.version == 1) { "Unsupported Android course catalog version ${saved.version}." }
    require(saved.controls.map { it.id }.distinct().size == saved.controls.size) { "Ambiguous saved course catalog." }
    val byId = saved.controls.associateBy { it.id }
    val references = native.categories.flatMap { it.controlPoints }.filter { it.controlId.isNotBlank() }.groupBy { it.controlId }
    require(references.keys.all { it in byId }) { "A category references a missing race control. Reopen the original Race File." }
    val aliases = native.aliases.groupBy { it.siCode }
    val controls = saved.controls.map { control ->
        val definitions = references[control.id].orEmpty().map { it.siCode to it.type }.distinct()
        require(definitions.size <= 1) { "Categories disagree about the station assignment for ${control.publicLabel ?: control.label}." }
        val (code, type) = definitions.singleOrNull() ?: (control.siCode to control.type)
        val labels = aliases[code].orEmpty().map { it.name }.distinct()
        require(labels.size <= 1) { "Conflicting labels for SI $code." }
        val label = labels.singleOrNull()
        control.copy(raceId = native.race.id, siCode = code, type = type,
            publicLabel = if (label != null && label != (control.publicLabel ?: control.label)) label else control.publicLabel,
            latitude = null, longitude = null)
    }
    val categories = native.categories.map { category ->
        val points = category.controlPoints.map { point ->
            if (point.controlId.isNotBlank()) point else {
                val candidates = controls.filter { it.siCode == point.siCode && it.type == point.type }
                require(candidates.size <= 1) { "The station assignment matches more than one race control." }
                point.copy(controlId = candidates.singleOrNull()?.id.orEmpty())
            }
        }
        category.copy(controlPoints = points)
    }
    val draft = saved.courseDraft?.let { draft ->
        draft.copy(design = draft.design.copy(controls = draft.design.controls.map { it.copy(raceId = native.race.id) },
            aliases = draft.design.aliases.map { it.copy(raceId = native.race.id) },
            categories = draft.design.categories.map { category ->
                val id = categoryIds[category.category.id] ?: category.category.id
                category.copy(category = category.category.copy(id = id, raceId = native.race.id),
                    controlPoints = category.controlPoints.map { it.copy(categoryId = id) })
            }))
    }
    val restored = native.copy(controls = controls, categories = categories, courseDraft = draft, courseMappings = saved.courseMappings.map { mapping ->
        mapping.copy(category = mapping.category.copy(raceId = native.race.id))
    })
    return EventControlCatalog.backfillControls(EventProjectFile(raceData = restored)).raceData
}
