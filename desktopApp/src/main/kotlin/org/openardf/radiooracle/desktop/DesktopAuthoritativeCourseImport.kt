package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.event.*

/** The preview and final Apply share the same prepared data; no optimizer or numbering proposal is applied. */
internal data class DesktopCourseImportReview(
    val sourceName: String,
    val transaction: DesktopCourseImportTransaction,
    val importedProject: EventProjectFile,
    val categoryIds: Set<String>,
    val password: String?,
    val fetchElevations: Boolean = false,
    val notes: List<String> = emptyList()
)

internal object DesktopAuthoritativeCourseImport {
    fun prepare(project: EventProjectFile, categoryIds: Set<String>, password: String?): EventProjectFile {
        val storagePassword = project.courseDataPassword(password)
        var candidate = EventCourseDrafts.cancel(project)
        val categories = candidate.raceData.categories + candidate.raceData.courseMappings
        categories.filter { it.category.id in categoryIds }.forEach { data ->
            val imported = data.category.storedCourseInfo(storagePassword) ?: return@forEach
            val catalog = candidate.raceData.controls.associateBy { it.id }
            val priorBindings = imported.appliedBindings?.controls.orEmpty().associate { it.placementId to it.controlId }
            fun controlForPlacement(id: String) = catalog[priorBindings[id] ?: id]
            // Import order text may quote multiword names; stored placement labels are plain field labels.
            val info = imported.copy(
                controlPoints = imported.controlPoints.map { point -> controlForPlacement(point.controlId)?.let {
                    point.copy(label = it.publicLabel ?: it.label)
                } ?: point },
                courseObjects = imported.courseObjects.map { point -> controlForPlacement(point.id)?.let {
                    point.copy(label = it.publicLabel ?: it.label)
                } ?: point }
            )
            if (info.controlPoints.isEmpty() && info.courseObjects.none { it.type.controlRole() != null }) return@forEach
            // Importers already resolve each named point to an explicit race control. Keep that mapping,
            // the file's route, and all its mandatory vertices. Do not run the calculated-route applier.
            val placements = info.validatedPlacements()
            val placementIds = placements.filterValues { it.type.controlRole() != null }.keys
            val bindings = placementIds.associateWith { priorBindings[it] ?: it }
            val controlIds = bindings.values
            require(controlIds.all { id -> candidate.raceData.controls.any { it.id == id } }) {
                "Review the imported control identities for ${data.category.name}."
            }
            val order = (info.appliedBindings?.orderedPlacementIds ?: info.courseObjects.map { it.id }).toMutableList()
            val missing = info.controlPoints.map { it.controlId }.filter { it !in order }
            val finish = order.indexOfFirst { placements[it]?.type == ProtectedCourseObjectType.FINISH }
                .takeIf { it >= 0 } ?: order.size
            order.addAll(finish, missing)
            val bound = CourseDesignBindings.prepare(info, candidate.raceData.controls,
                bindings, order, "import-${info.sourceSha256.ifBlank { "course" }}")
            candidate = EventProjectEditor.replaceCategoryAssignedControls(candidate, data.category.id,
                bound.appliedBindings!!.controls.map { it.controlId }) { index ->
                data.controlPoints.getOrNull(index)?.id ?: "${data.category.id}-import-$index"
            }
            candidate = candidate.withStoredCourseInfo(data.category.id, bound, storagePassword)
                .withStoredIdealOrder(data.category.id, info.idealOrder, storagePassword)
        }
        val infos = (candidate.raceData.categories + candidate.raceData.courseMappings).mapNotNull { data ->
            data.category.storedCourseInfo(storagePassword)?.let { data.category.id to it }
        }.toMap()
        ResolvedCourseProjection.courseInfos(candidate.raceData, infos)
        return EventProjectFileJson.normalizedForStorage(candidate)
    }
}
