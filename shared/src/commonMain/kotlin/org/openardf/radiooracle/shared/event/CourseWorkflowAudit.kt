package org.openardf.radiooracle.shared.event

import kotlinx.serialization.Serializable

@Serializable
data class CourseAuditCategory(val categoryId: String, val categoryName: String, val status: String,
                               val identityMode: String, val issues: List<String>)

@Serializable
data class CourseAuditReport(val raceName: String, val status: String, val categories: List<CourseAuditCategory>)

/** Read-only, coordinate-free diagnostics suitable for both platform adapters and automation. */
object CourseWorkflowAudit {
    fun audit(race: EventRaceData, unlocked: Map<String, ProtectedCourseInfo> = emptyMap()): CourseAuditReport {
        val reports = (race.categories + race.courseMappings).map { category -> auditCategory(race, category, unlocked) }
        return CourseAuditReport(race.race.name, when {
            reports.any { it.status == "failed" } -> "failed"
            reports.any { it.status == "blocked" } -> "blocked"
            reports.isEmpty() -> "blocked"
            else -> "passed"
        }, reports)
    }

    private fun auditCategory(race: EventRaceData, data: EventCategoryData,
                              unlocked: Map<String, ProtectedCourseInfo>): CourseAuditCategory {
        val category = data.category
        val info = unlocked[category.id] ?: category.courseInfo.takeIf { category.encryptedCourseInfo.isNullOrBlank() }
        if (info == null) return CourseAuditCategory(category.id, category.name, "blocked", "unavailable",
            listOf(if (!category.encryptedCourseInfo.isNullOrBlank()) "Race Password is required to audit course data." else "Course data is missing."))
        val issues = mutableListOf<String>()
        val ids = if (data.controlPoints.isNotEmpty()) data.controlPoints.map { it.controlId } else data.publicControlIds
        if (data.publicControlIds.isNotEmpty() && data.publicControlIds.toSet() != ids.toSet()) {
            issues += "Category control references disagree with public assignments."
        }
        if (ids.isEmpty()) issues += "No category controls are assigned."
        if (race.controls.map { it.id }.distinct().size != race.controls.size) issues += "Race catalog contains duplicate control IDs."
        val byId = race.controls.groupBy { it.id }
        ids.forEach { id ->
            val control = byId[id]?.singleOrNull()
            if (control == null) issues += "An assigned control is missing or ambiguous in the race catalog."
            else CourseControlResolver.resolve(control, listOf(info)).explanation?.let(issues::add)
        }
        if (info.route.size < 2) issues += "Course route is missing or stale."
        CourseDesignBindings.validationError(info)?.let(issues::add)
        info.appliedBindings?.let { bindings ->
            if (bindings.controls.map { it.controlId }.toSet() != ids.toSet()) issues += "Applied course bindings differ from category assignments."
        }
        return CourseAuditCategory(category.id, category.name, if (issues.isEmpty()) "passed" else "failed",
            if (info.appliedBindings != null) "applied" else "legacy", issues.distinct())
    }
}
