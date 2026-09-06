package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.event.*
import java.security.MessageDigest

/** The reviewed placement/station mapping is an input; this service never derives station codes from fox numbers. */
data class DesktopCourseRouteSelection(
    val courseInfo: ProtectedCourseInfo,
    val application: DesktopCourseCalculatedRouteApplication,
    val controlIdsByPlacementId: Map<String, String>
)

data class DesktopCourseBindingChange(val categoryName: String, val label: String, val siCode: Int, val controlId: String)

class DesktopPreparedCourseDesign internal constructor(
    internal val candidate: EventProjectFile,
    internal val expectedCandidateHash: String,
    val revision: String,
    val changes: List<DesktopCourseBindingChange>
)

/** Reuses Analyzer for each distinct route; the accepted numbering is frozen across the race. */
internal fun prepareAllCourseDesigns(
    project: EventProjectFile,
    accepted: DesktopCourseRouteSelection,
    reviewedBindingsByCategoryId: Map<String, Map<String, String>>,
    password: String?,
    elevationLookup: (CourseGeoPoint) -> Double? = { null },
    checkCancelled: () -> Unit = {}
): DesktopPreparedCourseDesign {
    EventCourseDrafts.requireCurrent(project)
    val source = EventCourseDrafts.candidate(project)
    val acceptedLabels = accepted.application.foxAssignments.associate { assignment ->
        requireNotNull(accepted.controlIdsByPlacementId[assignment.controlId]) {
            "Review the station binding for ${assignment.originalLabel}."
        } to assignment.calculatedLabel
    }
    val categories = source.raceData.categories + source.raceData.courseMappings
    val selections = categories.mapNotNull { category ->
        checkCancelled()
        if (category.category.id == accepted.application.categoryId) return@mapNotNull accepted
        val info = category.category.storedCourseInfo(password)
        if (info == null && category.controlPoints.isEmpty() && category.publicControlIds.isEmpty()) return@mapNotNull null
        requireNotNull(info) { "Course data is missing for ${category.category.name}. Complete this draft before applying it." }
        val reviewed = requireNotNull(reviewedBindingsByCategoryId[category.category.id]) {
            "Review the station bindings for ${category.category.name}."
        }
        // Analyzer's route optimizer minimizes geometry/effective length. Numbering affects its wait report,
        // so suppress further numbering proposals and carry the accepted labels into the complete change set.
        val application = requireNotNull(DesktopCourseAnalyzer.analyze(source, category.category.id, info, info.idealOrder,
            elevationLookup = elevationLookup, allowFoxRenumbering = false, prepareApplication = true).calculatedRouteApplication) {
            "A complete route could not be calculated for ${category.category.name}."
        }
        DesktopCourseRouteSelection(info, application.copy(foxAssignments = application.foxAssignments.map { assignment ->
            val id = requireNotNull(reviewed[assignment.controlId]) { "Review the station binding for ${assignment.originalLabel}." }
            assignment.copy(calculatedLabel = acceptedLabels[id] ?: source.raceData.controls.single { it.id == id }.let { it.publicLabel ?: it.label })
        }), reviewed)
    }
    checkCancelled()
    return prepareCourseDesign(project, selections, password)
}

internal fun prepareCourseDesign(project: EventProjectFile, selections: List<DesktopCourseRouteSelection>, password: String?): DesktopPreparedCourseDesign {
    EventCourseDrafts.requireCurrent(project)
    require(!EventCourseDrafts.hasRecordedActivity(project.raceData)) {
        "This race has recorded activity. Start a new race copy without readouts before replacing its design."
    }
    require(selections.isNotEmpty()) { "Select courses to apply." }
    val source = EventCourseDrafts.candidate(project)
    val token = EventCourseDrafts.snapshotHash(source)
    val storagePassword = source.courseDataPassword(password)
    val categories = source.raceData.categories + source.raceData.courseMappings
    require(categories.map { it.category.id }.distinct().size == categories.size) { "Course category IDs are ambiguous." }
    val byId = categories.associateBy { it.category.id }
    val selected = selections.associateBy { it.application.categoryId }
    require(selected.size == selections.size) { "A category was selected more than once." }
    selections.forEach { selection ->
        require(selection.application.sourceSnapshotHash == token) { "Course data changed after this calculation. Analyze the current draft before applying it." }
        val category = requireNotNull(byId[selection.application.categoryId]) { "Selected course category was not found." }
        require(category.category.storedCourseInfo(storagePassword) == selection.courseInfo) { "Loaded geometry changed for ${category.category.name}. Reload the current course." }
    }
    val missing = categories.filter { it.category.id !in selected &&
        (it.controlPoints.isNotEmpty() || it.publicControlIds.isNotEmpty() || it.category.storedCourseInfo(storagePassword) != null) }
    require(missing.isEmpty()) { "The design is incomplete. Include routes for: ${missing.joinToString { it.category.name }}." }
    val proposedLabels = selections.flatMap { selection -> selection.application.foxAssignments.map { assignment ->
        requireNotNull(selection.controlIdsByPlacementId[assignment.controlId]) { "Review the station binding for ${assignment.originalLabel}." } to assignment.calculatedLabel
    } }.groupBy({ it.first }, { it.second })
    require(proposedLabels.values.all { it.distinct().size == 1 }) { "Selected courses propose conflicting fox numbering for the same station." }
    val controls = source.raceData.controls.map { control -> proposedLabels[control.id]?.firstOrNull()?.let { label ->
        control.copy(label = label, publicLabel = label, latitude = null, longitude = null)
    } ?: control }
    var candidate = EventProjectEditor.replaceControlCatalog(source, controls)
    val byCode = candidate.raceData.controls.groupBy { it.siCode }
    candidate = candidate.copy(raceData = candidate.raceData.copy(aliases = candidate.raceData.aliases.map { alias ->
        val matches = byCode[alias.siCode].orEmpty()
        require(matches.map { it.publicLabel ?: it.label }.distinct().size <= 1) { "SI ${alias.siCode} has conflicting control labels." }
        matches.singleOrNull()?.let { alias.copy(name = it.publicLabel ?: it.label) } ?: alias
    }))
    val preparedInfos = selections.associate { selection ->
        val app = selection.application
        require(app.routePoints.size >= 2 && app.routeLengthMeters != null && app.climbMeters != null) {
            "Complete route geometry and metrics are required for ${byId.getValue(app.categoryId).category.name}."
        }
        val sourceInfo = selection.courseInfo.copy(
            controlPoints = selection.courseInfo.controlPoints.filter { it.controlId in app.orderedPlacementIds },
            courseObjects = app.courseObjects.ifEmpty { selection.courseInfo.courseObjects }.filter { it.id in app.orderedPlacementIds })
        val byPlacement = selection.controlIdsByPlacementId.mapValues { (_, id) -> candidate.raceData.controls.single { it.id == id } }
        val renamed = sourceInfo.copy(sourceName = "Course Analyzer applied design", sourceSha256 = "",
            idealOrder = app.idealOrderText, lengthMeters = app.routeLengthMeters, climbMeters = app.climbMeters,
            route = app.routePoints.map { ProtectedCourseRoutePoint(it.latitude, it.longitude, it.elevationMeters) }, sampledPointCount = app.routePoints.size,
            controlPoints = sourceInfo.controlPoints.map { point -> point.copy(label = byPlacement[point.controlId]?.let { it.publicLabel ?: it.label } ?: point.label) },
            courseObjects = sourceInfo.courseObjects.map { point -> point.copy(label = byPlacement[point.id]?.let { it.publicLabel ?: it.label } ?: point.label) },
            resultControlLabelsById = emptyMap(), appliedBindings = null)
        app.categoryId to CourseDesignBindings.prepare(renamed, candidate.raceData.controls, selection.controlIdsByPlacementId.filterKeys { it in app.orderedPlacementIds }, app.orderedPlacementIds, "pending")
    }
    preparedInfos.values.flatMap { it.appliedBindings!!.controls }.map { it.controlId }.distinct().forEach { id ->
        val control = candidate.raceData.controls.single { it.id == id }
        require(CourseControlResolver.resolve(control, preparedInfos.values.toList()).status == CourseResolutionStatus.RESOLVED) {
            "Selected courses disagree on the physical location of ${control.publicLabel ?: control.label}."
        }
    }
    val revisionSource = preparedInfos.entries.sortedWith(compareBy({ byId.getValue(it.key).category.name }, { it.value.appliedBindings!!.inputFingerprint })).joinToString("|") { (id, info) ->
        "${byId.getValue(id).category.name}:${info.appliedBindings!!.inputFingerprint}"
    } + "|${candidate.raceData.race.raceType}|${candidate.raceData.race.raceBand}|${candidate.raceData.race.timeLimitSeconds}|${candidate.raceData.race.courseAnalyzerSpeedCompensationFactor}"
    val revision = MessageDigest.getInstance("SHA-256").digest(revisionSource.toByteArray()).joinToString("") { "%02x".format(it) }
    preparedInfos.forEach { (id, info) ->
        val bindings = info.appliedBindings!!.copy(revision = revision)
        candidate = EventProjectEditor.replaceCategoryAssignedControls(candidate, id, bindings.controls.sortedWith(compareBy({
            org.openardf.radiooracle.shared.course.ControlPointRules.assignedControlSortGroup(it.siCode, it.type, candidate.raceData.race.raceType)
        }, { it.siCode })).map { it.controlId }) { index ->
            byId.getValue(id).controlPoints.getOrNull(index)?.id ?: "$id-applied-${index + 1}"
        }
        val resolved = ResolvedCourseProjection.courseInfo(candidate.raceData, id, info.copy(appliedBindings = bindings))
        candidate = candidate.withStoredCourseInfo(id, resolved, storagePassword).withStoredIdealOrder(id, resolved.idealOrder, storagePassword)
        candidate = EventProjectEditor.updateCategoryPhysicalStats(candidate, id, resolved.lengthMeters.toString(), resolved.climbMeters.toString())
    }
    val changes = preparedInfos.flatMap { (id, info) -> info.appliedBindings!!.controls.map {
        DesktopCourseBindingChange(byId.getValue(id).category.name, it.label, it.siCode, it.controlId)
    } }
    val completed = if (courseDesignSemanticHash(candidate, password) == courseDesignSemanticHash(project, password)) {
        EventCourseDrafts.cancel(project)
    } else candidate
    return DesktopPreparedCourseDesign(completed, token, revision, changes)
}

/** Compare protected content without treating fresh encryption or imported filenames as design changes. */
private fun courseDesignSemanticHash(project: EventProjectFile, password: String?): String {
    fun category(data: EventCategoryData): EventCategoryData {
        val value = data.category
        val info = value.storedCourseInfo(password)?.copy(sourceName = "", sourceSha256 = "", resultControlLabelsById = emptyMap())
        return data.copy(category = value.copy(encryptedCourseInfo = null, courseInfo = info,
            encryptedIdealOrder = null, idealOrder = value.storedIdealOrder(password)))
    }
    return EventCourseDrafts.snapshotHash(project.copy(raceData = project.raceData.copy(
        categories = project.raceData.categories.map(::category), courseMappings = project.raceData.courseMappings.map(::category))))
}
