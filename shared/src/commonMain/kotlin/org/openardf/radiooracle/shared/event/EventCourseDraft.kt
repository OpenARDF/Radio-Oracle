package org.openardf.radiooracle.shared.event

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/** Only editable course state. Competitors and recorded activity belong to the applied race. */
@Serializable
data class EventCourseDesignState(
    val controls: List<EventControl>,
    val aliases: List<EventAlias>,
    val categories: List<EventCategoryData>,
    val courseMappings: List<EventCategoryData>,
    val analyzerSpeedCompensationFactor: Double? = null
)

@Serializable
data class EventCourseDraft(
    val version: Int = 1,
    /** Freshness token for the applied snapshot, not the design's semantic revision. */
    val baseSnapshotHash: String,
    val design: EventCourseDesignState
)

/** Keeps the existing race model authoritative for scoring/outputs while course tools edit a candidate. */
object EventCourseDrafts {
    private val json = Json { encodeDefaults = true }

    fun start(project: EventProjectFile): EventProjectFile = if (project.raceData.courseDraft != null) project else
        project.copy(raceData = project.raceData.copy(courseDraft = EventCourseDraft(
            baseSnapshotHash = snapshotHash(project), design = capture(project.raceData))))

    fun candidate(project: EventProjectFile): EventProjectFile {
        val draft = project.raceData.courseDraft ?: return project
        require(draft.version == 1) { "Unsupported course draft version ${draft.version}." }
        return install(project, draft.design).let { it.copy(raceData = it.raceData.copy(courseDraft = null)) }
    }

    fun edit(project: EventProjectFile, transform: (EventProjectFile) -> EventProjectFile): EventProjectFile {
        val started = start(project)
        requireCurrent(started)
        val before = candidate(started)
        val after = transform(before)
        require(before.raceData.competitorData == after.raceData.competitorData &&
            before.raceData.unmatchedReadoutData == after.raceData.unmatchedReadoutData) {
            "A course draft cannot change recorded activity or competitors."
        }
        return started.copy(raceData = started.raceData.copy(courseDraft = started.raceData.courseDraft!!.copy(design = capture(after.raceData))))
    }

    fun cancel(project: EventProjectFile): EventProjectFile = project.copy(raceData = project.raceData.copy(courseDraft = null))

    fun requireCurrent(project: EventProjectFile) {
        val draft = project.raceData.courseDraft ?: return
        require(draft.version == 1 && draft.baseSnapshotHash == snapshotHash(project)) {
            "Applied course data changed after this draft began. Reopen the current design before applying it."
        }
    }

    /** Called only after the normal application service has validated the complete candidate. */
    fun commit(project: EventProjectFile, validatedCandidate: EventProjectFile, expectedCandidateHash: String): EventProjectFile {
        require(project.raceData.race.id == validatedCandidate.raceData.race.id) { "The prepared design belongs to a different race." }
        require(!hasRecordedActivity(project.raceData)) {
            "This race has recorded activity. Start a new race copy without readouts before replacing its design."
        }
        requireCurrent(project)
        if (project.raceData.courseDraft == null && snapshotHash(project) == snapshotHash(validatedCandidate)) return project
        require(snapshotHash(candidate(project)) == expectedCandidateHash) {
            "The course draft changed after this calculation. Review the current draft before applying it."
        }
        return install(project, capture(validatedCandidate.raceData)).let { cancel(it).copy(desktopRouteAnalysis = null) }
    }

    fun hasRecordedActivity(race: EventRaceData): Boolean = race.unmatchedReadoutData.isNotEmpty() ||
        race.competitorData.any { it.readoutData != null }

    fun capture(race: EventRaceData) = EventCourseDesignState(
        controls = race.controls, aliases = race.aliases,
        categories = race.categories.map { it.copy(competitors = emptyList()) },
        courseMappings = race.courseMappings.map { it.copy(competitors = emptyList()) },
        analyzerSpeedCompensationFactor = race.race.courseAnalyzerSpeedCompensationFactor
    )

    /** Includes ciphertext to detect storage edits too. Applied semantic equality uses CourseDesignBindings. */
    fun snapshotHash(project: EventProjectFile): String {
        val race = project.raceData.race
        val state = capture(project.raceData)
        fun normalized(data: EventCategoryData) = data.copy(category = data.category.copy(id = "", raceId = ""),
            controlPoints = data.controlPoints.map { it.copy(id = "", categoryId = "") })
        val canonical = state.copy(controls = state.controls.map { it.copy(raceId = "") },
            aliases = state.aliases.filterNot { alias -> state.controls.any {
                it.siCode == alias.siCode && (it.publicLabel ?: it.label) == alias.name
            } }.map { it.copy(id = "", raceId = "") }.sortedBy { it.siCode },
            categories = state.categories.map(::normalized), courseMappings = state.courseMappings.map(::normalized))
        val text = json.encodeToString(canonical) + "|${race.raceType}|${race.raceBand}|${race.timeLimitSeconds}|${race.courseAnalyzerSpeedCompensationFactor}"
        return MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    /** Codec normalization may change representation; it must not revive a draft already known to be stale. */
    internal fun rebaseNormalizedDraft(original: EventProjectFile, normalized: EventProjectFile): EventProjectFile {
        val draft = original.raceData.courseDraft ?: return normalized
        if (draft.baseSnapshotHash != snapshotHash(original)) return normalized
        return normalized.copy(raceData = normalized.raceData.copy(courseDraft = normalized.raceData.courseDraft?.copy(
            baseSnapshotHash = snapshotHash(normalized))))
    }

    /** Protection changes cover both states; a previously stale draft must remain stale. */
    fun mapProtectedCategories(project: EventProjectFile, transform: (EventCategoryData) -> EventCategoryData): EventProjectFile {
        val race = project.raceData
        val draft = race.courseDraft
        val wasCurrent = draft != null && draft.baseSnapshotHash == snapshotHash(project)
        val updated = project.copy(raceData = race.copy(categories = race.categories.map(transform),
            courseMappings = race.courseMappings.map(transform), courseDraft = draft?.copy(design = draft.design.copy(
                categories = draft.design.categories.map(transform), courseMappings = draft.design.courseMappings.map(transform)))))
        return if (wasCurrent) updated.copy(raceData = updated.raceData.copy(courseDraft = updated.raceData.courseDraft!!.copy(
            baseSnapshotHash = snapshotHash(updated)))) else updated
    }

    fun protectedCategories(race: EventRaceData): List<EventCategoryData> = race.categories + race.courseMappings +
        race.courseDraft?.design?.let { it.categories + it.courseMappings }.orEmpty()

    private fun install(project: EventProjectFile, design: EventCourseDesignState): EventProjectFile {
        val existing = project.raceData.categories.associateBy { it.category.id }
        require(existing.values.none { it.competitors.isNotEmpty() && design.categories.none { proposed -> proposed.category.id == it.category.id } }) {
            "A course design cannot remove a category with registered competitors."
        }
        return project.copy(raceData = project.raceData.copy(controls = design.controls, aliases = design.aliases,
            categories = design.categories.map { it.copy(competitors = existing[it.category.id]?.competitors.orEmpty()) },
            courseMappings = design.courseMappings,
            race = project.raceData.race.copy(courseAnalyzerSpeedCompensationFactor = design.analyzerSpeedCompensationFactor
                ?: project.raceData.race.courseAnalyzerSpeedCompensationFactor)))
    }
}
