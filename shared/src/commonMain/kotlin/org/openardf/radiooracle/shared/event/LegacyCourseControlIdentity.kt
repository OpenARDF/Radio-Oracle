package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.domain.ControlPointType

fun String.resultControlLabelKey(type: ControlPointType): String {
    if (type != ControlPointType.CONTROL) return type.name
    val compact = trim().lowercase().filter(Char::isLetterOrDigit)
    val number = ControlRoleLabelRules.foxNumber(this)?.let {
        when (it) { in 31..35 -> it - 30; in 41..45 -> it - 40; else -> it }
    } ?: return compact
    val fast = compact.endsWith("f") || (compact.startsWith("f") && compact.drop(1).all(Char::isDigit))
    return "${if (fast) "fast" else "standard"}:$number"
}

/** Capture before the first Analyzer rename, never reinterpret an already-renumbered legacy label. */
fun ProtectedCourseInfo.withResultControlLabels(controls: List<EventControl> = emptyList()): ProtectedCourseInfo {
    if (sourceName.startsWith("Course Analyzer", ignoreCase = true)) return this
    val labels = (controlPoints.map { it.controlId to it.label } + courseObjects.map { it.id to it.label })
        .groupBy({ it.first }, { it.second })
        .mapNotNull { (id, values) ->
            val field = controls.singleOrNull { it.id == id }?.let { it.publicLabel?.takeIf(String::isNotBlank) ?: it.label }
            (field ?: values.distinct().singleOrNull())?.let { id to it }
        }.toMap()
    return copy(resultControlLabelsById = labels + resultControlLabelsById)
}

/** Recovery for historical importer IDs only; newly applied courses use explicit bindings. */
fun String.stableResultControlIdentity(type: ControlPointType): String? {
    val match = historicalControlId.matchEntire(this) ?: return null
    if (match.groupValues[3] != type.name.lowercase()) return null
    return when (type) {
        ControlPointType.CONTROL -> {
            val token = match.groupValues[1]
            val n = ControlRoleLabelRules.foxNumber(token) ?: return null
            val compact = token.lowercase().filter(Char::isLetterOrDigit)
            val fast = compact.endsWith("f") || (compact.startsWith("f") && compact.drop(1).all(Char::isDigit))
            "control:${if (fast) "fast" else "standard"}:$n"
        }
        ControlPointType.BEACON -> "beacon"
        ControlPointType.SEPARATOR -> "spectator"
    }
}

private val historicalControlId = Regex("""^control-(.+)-(\d+)-(control|beacon|separator)$""")
