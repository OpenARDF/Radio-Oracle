package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.sportident.SportIdentCodes
import org.w3c.dom.Element
import org.w3c.dom.Node

/** Explicit course-setting metadata; a course ordinal must never become a fox identity. */
internal data class DesktopCourseSettingPoint(
    val node: Node,
    val type: String,
    val siCode: Int?,
    val control: CourseControlPoint? = null
) {
    val endpointName: String? get() = endpointName(type)

    companion object {
        private const val NAMESPACE = "http://www.orienteering.org/schemas/course-setting"
        private val numberedControl = Regex("""^(\d+)\s*\((\d+)\)$""")

        fun endpointName(type: String?): String? = when (type) {
            "Start", "StartPoint" -> "Start"
            "Finish", "FinishPoint" -> "Finish"
            else -> null
        }

        fun numberedControlCode(name: String): Int? = numberedControl.matchEntire(name.trim())
            ?.groupValues?.get(2)?.toIntOrNull()?.takeIf(SportIdentCodes::isSICodeValid)

        fun fromKml(node: Node): DesktopCourseSettingPoint? {
            val extended = node.children().firstOrNull { it.localName == "ExtendedData" } ?: return null
            val values = extended.children().filter { it.localName == "Data" }.mapNotNull { data ->
                val key = (data as? Element)?.getAttribute("name").orEmpty()
                val prefix = key.substringBefore(':', "")
                if (prefix.isEmpty() || data.lookupNamespaceURI(prefix) != NAMESPACE) return@mapNotNull null
                val value = data.children().firstOrNull { it.localName == "value" }?.textContent?.trim()
                    ?: return@mapNotNull null
                key.substringAfter(':') to value
            }.toMap()
            val type = values["object-type"] ?: return null
            val code = values["control-code"]?.toIntOrNull()?.takeIf(SportIdentCodes::isSICodeValid)
                ?.takeIf { type == "Control" }
            return DesktopCourseSettingPoint(node, type, code)
        }

        fun routes(points: List<DesktopCourseSettingPoint>): List<CourseRoute> =
            points.groupBy { it.node.parentNode }.mapNotNull { (folder, group) ->
                val start = group.singleOrNull { it.type == "Start" }?.control ?: return@mapNotNull null
                val finish = group.singleOrNull { it.type == "Finish" }?.control ?: return@mapNotNull null
                val controls = group.filter { it.type == "Control" }
                if (controls.isEmpty() || group.size != controls.size + 2) return@mapNotNull null
                val ordered = controls.map { point ->
                    val match = numberedControl.matchEntire(point.control?.name.orEmpty()) ?: return@mapNotNull null
                    val ordinal = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                    if (point.siCode == null || numberedControlCode(point.control!!.name) != point.siCode) {
                        return@mapNotNull null
                    }
                    ordinal to point.control
                }.sortedBy { it.first }
                // Do not guess order for unnumbered, repeated, or incomplete coordinate sets.
                if (ordered.map { it.first } != (1..controls.size).toList()) return@mapNotNull null
                val courseFolder = if (folder.childName() == "Coordinates") folder.parentNode else folder
                val name = courseFolder.childName()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                CourseRoute(name, listOf(start.point) + ordered.map { it.second.point } + finish.point)
            }

        private fun Node.children(): List<Node> = (0 until childNodes.length).map(childNodes::item)
        private fun Node.childName(): String? = children().firstOrNull { it.localName == "name" }?.textContent?.trim()
    }
}
