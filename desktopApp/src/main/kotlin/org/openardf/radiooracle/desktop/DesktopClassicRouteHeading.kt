package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.event.*

/** Recovers only a certified order from accessible geometry; never optimizes or loads terrain on the UI thread. */
internal object DesktopClassicRouteHeading {
    private val orders = object : LinkedHashMap<String, String?>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String?>?) = size > 128
    }

    @Synchronized
    fun order(project: EventProjectFile, reference: ClassicRouteReference): String? {
        val category = project.raceData.categories.singleOrNull { it.category.id == reference.categoryId } ?: return null
        // Check protection before consulting the cache. This is a public projection, not an unlock operation.
        if (!category.category.encryptedCourseInfo.isNullOrBlank() || !category.category.encryptedIdealOrder.isNullOrBlank()) return null
        val info = category.category.courseInfo ?: return null
        val key = reference.courseFingerprint + reference.categoryId + reference.idealOrderFingerprint
        if (orders.containsKey(key)) return orders[key]
        val order = try {
            val all = (project.raceData.categories + project.raceData.courseMappings)
                .filter { it.category.encryptedCourseInfo.isNullOrBlank() }.mapNotNull { it.category.courseInfo }
            fun endpoint(type: ProtectedCourseObjectType): CourseGeoPoint {
                val point = info.courseObjects.filter { it.type == type }.distinctBy { it.latitude to it.longitude }.single()
                return CourseGeoPoint(point.latitude, point.longitude)
            }
            val start = endpoint(ProtectedCourseObjectType.START)
            val finish = endpoint(ProtectedCourseObjectType.FINISH)
            val assigned = category.controlPoints.map { point ->
                project.raceData.controls.firstOrNull { it.id == point.controlId }
                    ?: project.raceData.controls.single { it.siCode == point.siCode && it.type == point.type }
            }.distinctBy { it.id }
            val beacon = assigned.single { it.type == ControlPointType.BEACON }
            val controls = assigned.filter { it.type != ControlPointType.BEACON }
            require(controls.size in 1..8 && controls.all { it.type == ControlPointType.CONTROL || it.type == ControlPointType.SEPARATOR })
            val points = assigned.associate { it.id to DesktopClassicRouteAnalysis.resolve(it, info, all) }
            require(points.values.distinct().size == assigned.size) { "Co-located controls do not identify a unique labeled order." }
            // The reference fingerprint includes Start, terminal Beacon and Finish. Headings show the variable fox order.
            fun find(prefix: List<EventControl>, remaining: List<EventControl>): String? {
                if (remaining.isEmpty()) {
                    val stops = listOf(start) + prefix.map { points.getValue(it.id) } + points.getValue(beacon.id) + finish
                    if (DesktopClassicRouteAnalysis.sha256(DesktopClassicRouteAnalysis.METHOD + stops.toString()) != reference.idealOrderFingerprint) return null
                    return prefix.joinToString("-") { it.publicLabel?.takeIf(String::isNotBlank) ?: it.label }
                }
                for (control in remaining) find(prefix + control, remaining - control)?.let { return it }
                return null
            }
            find(emptyList(), controls)
        } catch (_: IllegalArgumentException) { null }
          catch (_: NoSuchElementException) { null }
        orders[key] = order
        return order
    }
}
