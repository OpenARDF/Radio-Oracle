package org.openardf.radiooracle.shared.publicresults

import org.openardf.radiooracle.shared.event.*

data class PublicResultsCourse(val categoryId: String, val categoryName: String, val courseInfo: ProtectedCourseInfo)

/** Required course coverage for an intentionally course-inclusive result export. */
object PublicResultsCourseSelection {
    fun resolve(race: EventRaceData, unlocked: Map<String, ProtectedCourseInfo>,
                results: List<EventResultDetails> = EventResultDetails.from(race)): List<PublicResultsCourse> {
        val resolvedInfos = ResolvedCourseProjection.courseInfos(race, unlocked.filterKeys { id -> results.any { it.categoryId == id } })
        return results.mapNotNull { result -> result.categoryId?.let { it to result.categoryName } }
            .distinctBy { it.first }.map { (id, name) ->
                val info = requireNotNull(resolvedInfos[id]) {
                    "Course data for $name is missing or locked. Load the course or choose results-only publishing."
                }
                val resolved = info
                require(resolved.route.size >= 2 || resolved.controlPoints.size >= 2 || resolved.courseObjects.size >= 2) {
                    "Course geometry for $name is incomplete. Repair the course or choose results-only publishing."
                }
                PublicResultsCourse(id, name, resolved)
            }
    }
}
