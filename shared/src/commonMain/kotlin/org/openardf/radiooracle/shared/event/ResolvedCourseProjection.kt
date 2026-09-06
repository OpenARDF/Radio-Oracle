package org.openardf.radiooracle.shared.event

/** One checked view of newly applied geometry for the existing format-specific writers. */
object ResolvedCourseProjection {
    fun courseInfos(race: EventRaceData, infos: Map<String, ProtectedCourseInfo>): Map<String, ProtectedCourseInfo> {
        val projected = infos.mapValues { (id, info) -> courseInfo(race, id, info) }
        val boundIds = projected.values.flatMap { it.appliedBindings?.controls.orEmpty() }.map { it.controlId }.toSet()
        boundIds.forEach { id ->
            val control = race.controls.single { it.id == id }
            val resolution = CourseControlResolver.resolve(control, projected.values.toList())
            require(resolution.status == CourseResolutionStatus.RESOLVED) {
                "Course locations disagree for ${control.publicLabel ?: control.label}. Review all affected courses before exporting."
            }
        }
        return projected
    }

    fun courseInfo(race: EventRaceData, categoryId: String, info: ProtectedCourseInfo): ProtectedCourseInfo {
        val bindings = info.appliedBindings ?: return info
        CourseDesignBindings.validationError(info)?.let { throw IllegalArgumentException(it) }
        val category = requireNotNull((race.categories + race.courseMappings).singleOrNull { it.category.id == categoryId }) {
            "Applied course category is missing or ambiguous."
        }
        val assigned = category.controlPoints.map { it.controlId }.ifEmpty { category.publicControlIds }
        require(assigned.toSet() == bindings.controls.map { it.controlId }.toSet()) { "Applied course bindings differ from category assignments." }
        bindings.controls.forEach { bound ->
            val control = requireNotNull(race.controls.singleOrNull { it.id == bound.controlId }) { "An applied race control is missing or ambiguous." }
            val resolved = CourseControlResolver.resolve(control, listOf(info))
            require(resolved.status == CourseResolutionStatus.RESOLVED) { resolved.explanation.orEmpty() }
        }
        val byPlacement = bindings.controls.associateBy { it.placementId }
        val projected = info.copy(
            idealOrder = ProtectedIdealOrderRules.formatControlIds(bindings.orderedControlIds, race.controls),
            controlPoints = info.controlPoints.map { point ->
                val bound = byPlacement.getValue(point.controlId)
                point.copy(controlId = bound.controlId, label = bound.label, description = point.description.withAppliedSiCode(bound.siCode))
            },
            courseObjects = info.courseObjects.map { point -> byPlacement[point.id]?.let { bound ->
                point.copy(id = bound.controlId, label = bound.label, description = point.description.withAppliedSiCode(bound.siCode))
            } ?: point },
            resultControlLabelsById = bindings.controls.associate { it.controlId to it.label },
            appliedBindings = bindings.copy(controls = bindings.controls.map { it.copy(placementId = it.controlId) },
                orderedPlacementIds = bindings.orderedPlacementIds.map { byPlacement[it]?.controlId ?: it })
        )
        require(CourseDesignBindings.validationError(projected) == null) { "The applied course projection contains conflicting object identities." }
        return projected
    }
}
