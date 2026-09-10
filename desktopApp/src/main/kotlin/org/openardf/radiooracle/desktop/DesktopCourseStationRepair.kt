package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.event.*

/** Recover an applied design using a known pre-renumbering catalog, without optimizing its routes again. */
internal fun repairCourseStationPairs(
    project: EventProjectFile, referenceControls: List<EventControl>, password: String?
): EventProjectFile {
    require(project.raceData.courseDraft == null) { "Apply or discard the pending draft before repairing the applied design." }
    require(!EventCourseDrafts.hasRecordedActivity(project.raceData)) { "A race with recorded activity cannot have its station assignments replaced." }
    val reference = referenceControls.associateBy { it.id }
    require(reference.size == referenceControls.size && reference.keys == project.raceData.controls.map { it.id }.toSet()) {
        "The reference must contain exactly the same station IDs as the affected race."
    }
    val controls = project.raceData.controls.map { control ->
        val before = reference.getValue(control.id)
        require(before.siCode == control.siCode && before.type == control.type) { "The reference station code or role differs for ${control.label}." }
        control.copy(label = before.label, publicLabel = before.publicLabel)
    }
    val categories = project.raceData.categories + project.raceData.courseMappings
    val infos = categories.mapNotNull { category -> category.category.storedCourseInfo(password)?.let { category.category.id to it } }.toMap()
    // Validate the old projection before changing its catalog. Recovery must not conceal unrelated damage.
    ResolvedCourseProjection.courseInfos(project.raceData, infos)
    val draft = EventCourseDrafts.edit(project) { current ->
        var candidate = EventProjectEditor.replaceControlCatalog(current, controls)
        val labelsByCode = controls.associate { it.siCode to (it.publicLabel ?: it.label) }
        candidate = candidate.copy(raceData = candidate.raceData.copy(aliases = candidate.raceData.aliases.map {
            it.copy(name = labelsByCode[it.siCode] ?: it.name)
        }))
        infos.forEach { (id, info) -> candidate = candidate.withStoredCourseInfo(id, info.copy(appliedBindings = null), password) }
        candidate
    }
    val candidate = EventCourseDrafts.candidate(draft)
    val selections = infos.map { (id, oldInfo) ->
        val bindings = requireNotNull(oldInfo.appliedBindings) { "Recovery requires a complete previously applied design." }
        val info = (candidate.raceData.categories + candidate.raceData.courseMappings).single { it.category.id == id }.category.storedCourseInfo(password)!!
        val points = info.validatedPlacements()
        DesktopCourseRouteSelection(info, DesktopCourseCalculatedRouteApplication(
            categoryId = id, idealOrderText = info.idealOrder,
            routePoints = info.route.map { CourseGeoPoint(it.latitude, it.longitude, it.elevationMeters) },
            routeLengthMeters = info.lengthMeters, climbMeters = info.climbMeters,
            foxAssignments = bindings.controls.filter { it.type == ControlPointType.CONTROL }.map {
                val label = points.getValue(it.placementId).label
                // Recovery cannot fall back to the old, incorrect station when a label is missing.
                requireNotNull(CourseStationAssignments.foxForLabel(controls, label)) { "The reference has no station for Fox $label." }
                DesktopCourseCalculatedFoxAssignment(it.placementId, label, label)
            }, orderedPlacementIds = bindings.orderedPlacementIds, courseObjects = info.courseObjects,
            sourceSnapshotHash = EventCourseDrafts.snapshotHash(candidate)
        ), bindings.controls.associate { it.placementId to it.controlId })
    }
    return DesktopCourseAnalysisApplier.commit(draft, DesktopCourseAnalysisApplier.prepare(draft, selections, password))
}
