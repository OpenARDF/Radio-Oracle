package org.openardf.radiooracle.shared.event

/** Keeps accepted geometry attached to control IDs when catalog details or course membership change. */
object AppliedCourseEdits {
    fun refreshCatalog(info: ProtectedCourseInfo, controls: List<EventControl>): ProtectedCourseInfo {
        val binding = info.appliedBindings ?: return info
        require(CourseDesignBindings.validationError(info) == null) { CourseDesignBindings.validationError(info).orEmpty() }
        val byId = controls.associateBy { it.id }
        val byPlacement = binding.controls.associate { bound ->
            val control = requireNotNull(byId[bound.controlId]) { "An applied course control is missing." }
            require(bound.type == control.type) { "Reapply the course design before changing the role of ${control.label}." }
            bound.placementId to control
        }
        if (binding.controls.all { bound -> byPlacement.getValue(bound.placementId).let {
            bound.siCode == it.siCode && bound.label == (it.publicLabel ?: it.label)
        } }) return info
        val renamed = info.copy(
            idealOrder = ProtectedIdealOrderRules.formatControlIds(binding.orderedControlIds, controls),
            controlPoints = info.controlPoints.map { point -> point.copy(label = byPlacement.getValue(point.controlId).let { it.publicLabel ?: it.label }) },
            courseObjects = info.courseObjects.map { point -> byPlacement[point.id]?.let { point.copy(label = it.publicLabel ?: it.label) } ?: point },
            resultControlLabelsById = emptyMap()
        )
        return prepare(renamed, controls, binding.controls.associate { it.placementId to it.controlId }, binding.orderedPlacementIds)
    }

    /** Uses only explicit accepted IDs, never labels, aliases, station numbers, or inactive draft geometry. */
    fun reassign(race: EventRaceData, data: EventCategoryData): EventCategoryData {
        val info = data.category.courseInfo ?: return data
        val binding = info.appliedBindings ?: return data
        val assigned = data.controlPoints.map { it.controlId }.ifEmpty { data.publicControlIds }
        if (assigned.toSet() == binding.controls.map { it.controlId }.toSet()) return data
        if (assigned.isEmpty()) return data.copy(category = data.category.copy(
            courseInfo = null, idealOrder = null, encryptedIdealOrder = null, lengthMeters = 0, climbMeters = 0))
        val sources = (race.categories + race.courseMappings).mapNotNull {
            it.category.courseInfo?.takeIf { info -> it.category.encryptedCourseInfo == null && info.appliedBindings != null }
        }
        sources.forEach { require(CourseDesignBindings.validationError(it) == null) { CourseDesignBindings.validationError(it).orEmpty() } }
        val controls = race.controls.associateBy { it.id }
        val points = assigned.distinct().map { id ->
            val control = requireNotNull(controls[id]) { "An assigned control is missing." }
            val resolution = CourseControlResolver.resolve(control, sources)
            require(resolution.status == CourseResolutionStatus.RESOLVED) {
                "Cannot update ${data.category.name}: ${resolution.explanation} Import and apply this control's location first."
            }
            val candidates = sources.mapNotNull { source -> source.appliedBindings!!.controls.singleOrNull { it.controlId == id }
                ?.let { source.validatedPlacements().getValue(it.placementId) } }
            require(candidates.mapNotNull { it.speedFactor }.distinct().size <= 1) { "Course speed factors disagree for ${control.label}." }
            candidates.first().copy(id = id, label = control.publicLabel ?: control.label,
                elevationMeters = resolution.location!!.elevationMeters,
                speedFactor = candidates.mapNotNull { it.speedFactor }.firstOrNull())
        }
        val oldPoints = info.validatedPlacements()
        val byPlacement = binding.controls.associateBy { it.placementId }
        val retainedOrder = binding.orderedPlacementIds.mapNotNull { id ->
            byPlacement[id]?.controlId?.takeIf { it in assigned } ?: id.takeIf { it !in byPlacement }
        }.toMutableList()
        val added = assigned.distinct().filter { it !in retainedOrder }
        // Keep existing mandatory visits and their order; add new controls before the terminal beacon/finish.
        val terminal = retainedOrder.indexOfFirst { id -> controls[id]?.type == org.openardf.radiooracle.shared.domain.ControlPointType.BEACON ||
            oldPoints[id]?.type == ProtectedCourseObjectType.FINISH }.takeIf { it >= 0 } ?: retainedOrder.size
        retainedOrder.addAll(terminal, added)
        val objects = oldPoints.values.filter { it.type.controlRole() == null } + points
        require(objects.map { it.id }.distinct().size == objects.size) { "Course placement IDs conflict with assigned control IDs." }
        val updated = prepare(info.copy(
            // The old route and its metrics describe a different course. Analyzer calculates the new ideal route.
            route = emptyList(), lengthMeters = null, climbMeters = null, sampledPointCount = 0,
            idealOrder = "", controlPoints = points.map { ProtectedCourseControlPoint(it.id, it.label, it.latitude, it.longitude,
                it.type.controlRole()!!, it.elevationMeters, it.speedFactor, it.description) }, courseObjects = objects,
            resultControlLabelsById = emptyMap()
        ), race.controls, points.associate { it.id to it.id }, retainedOrder)
        return data.copy(category = data.category.copy(courseInfo = updated,
            idealOrder = null, encryptedIdealOrder = null, lengthMeters = 0, climbMeters = 0))
    }

    /** Explicit repair for a known current-format race; ordinary file loading never calls this. */
    fun reconcile(project: EventProjectFile): EventProjectFile {
        require((project.raceData.categories + project.raceData.courseMappings).none { it.category.encryptedCourseInfo != null }) {
            "Unlock and remove course protection before repairing saved course bindings."
        }
        fun refresh(data: EventCategoryData): EventCategoryData = data.copy(category = data.category.copy(
            courseInfo = data.category.courseInfo?.let { refreshCatalog(it, project.raceData.controls) }))
        val race = project.raceData.copy(categories = project.raceData.categories.map(::refresh), courseMappings = project.raceData.courseMappings.map(::refresh))
        return project.copy(raceData = race.copy(categories = race.categories.map { reassign(race, it) },
            courseMappings = race.courseMappings.map { reassign(race, it) }))
    }

    private fun prepare(info: ProtectedCourseInfo, controls: List<EventControl>, ids: Map<String, String>, order: List<String>): ProtectedCourseInfo {
        val prepared = CourseDesignBindings.prepare(info, controls, ids, order, "catalog-edit")
        return prepared.copy(appliedBindings = prepared.appliedBindings!!.copy(revision = prepared.appliedBindings.inputFingerprint))
    }
}
