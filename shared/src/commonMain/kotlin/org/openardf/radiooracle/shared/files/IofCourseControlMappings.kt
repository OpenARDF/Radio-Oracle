package org.openardf.radiooracle.shared.files

import org.openardf.radiooracle.shared.event.*
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.sportident.SportIdentCodes

/** Source identifiers remain immutable; course references never depend on edited display names. */
data class IofCourseControlSource(
    val id: String,
    val xmlType: String,
    val names: List<String> = emptyList(),
    val punchingUnitIds: List<String> = emptyList()
)

data class IofCourseControlMapping(
    val sourceId: String,
    val publicName: String,
    val pointType: ProtectedCourseObjectType,
    val siCode: String,
    val notes: List<String> = emptyList()
)

/** Shared inference and validation for platform-specific import review interfaces. */
object IofCourseControlMappings {
    val pointTypes = listOf(ProtectedCourseObjectType.CONTROL, ProtectedCourseObjectType.BEACON,
        ProtectedCourseObjectType.SPECTATOR, ProtectedCourseObjectType.START, ProtectedCourseObjectType.FINISH)

    fun prefill(sources: List<IofCourseControlSource>, project: EventProjectFile): List<IofCourseControlMapping> =
        sources.map { source ->
            val notes = mutableListOf<String>()
            val explicitCode = source.punchingUnitIds.firstOrNull()
            val numericId = source.id.toIntOrNull()?.takeIf(SportIdentCodes::isSICodeValid)
            var code = explicitCode ?: numericId?.toString().orEmpty()
            val alias = source.names.firstOrNull { it.isNotBlank() }
                ?: source.id.takeIf { it.any(Char::isLetter) }
            val matches = project.raceData.controls.filter { control ->
                if (code.isNotBlank()) control.siCode == code.toIntOrNull()
                else alias != null && (EventControlCatalog.displayLabel(control).equals(alias, true) ||
                    control.label.equals(alias, true) || project.raceData.aliases.any {
                        it.siCode == control.siCode && it.name.equals(alias, true)
                    })
            }
            val existing = matches.singleOrNull()
            if (matches.size > 1) notes += "More than one existing control matches; choose the correct SI code."
            if (code.isBlank() && existing != null) code = existing.siCode.toString()
            val inferred = inferredPointType(alias) ?: source.id.takeIf { it.any(Char::isLetter) }?.let(::inferredPointType)
            val existingType = when (existing?.type) {
                ControlPointType.BEACON -> ProtectedCourseObjectType.BEACON
                ControlPointType.SEPARATOR -> ProtectedCourseObjectType.SPECTATOR
                ControlPointType.CONTROL -> ProtectedCourseObjectType.CONTROL
                null -> null
            }
            val type = when (source.xmlType) {
                "Start" -> ProtectedCourseObjectType.START
                "Finish" -> ProtectedCourseObjectType.FINISH
                else -> existingType ?: inferred ?: ProtectedCourseObjectType.CONTROL
            }
            if (inferred != null && inferred != type) notes += "The alias suggests ${typeLabel(inferred)}, but XML type or the existing control takes precedence. Review the role."
            if (source.xmlType == "Control" && type in listOf(ProtectedCourseObjectType.START, ProtectedCourseObjectType.FINISH))
                notes += "${typeLabel(type)} was inferred from the alias; confirm this is a course endpoint."
            if (explicitCode != null && numericId != null && explicitCode.toIntOrNull() != numericId)
                notes += "PunchingUnitId $explicitCode differs from XML Id ${source.id}; PunchingUnitId supplies the proposed SI code."
            if (source.punchingUnitIds.size > 1) notes += "Multiple PunchingUnitId values: ${source.punchingUnitIds.joinToString()}. Only one station code per control is supported; review the proposed code."
            if (source.names.size > 1) notes += "Multiple source names: ${source.names.joinToString()}; review the proposed name."
            val name = existing?.let(EventControlCatalog::displayLabel) ?: alias ?: when (type) {
                ProtectedCourseObjectType.START -> "Start"
                ProtectedCourseObjectType.FINISH -> "Finish"
                else -> code.ifBlank { source.id }
            }
            if (existing != null && alias != null && !name.equals(alias, true))
                notes += "Source name $alias differs from the existing public name $name; the existing name is retained for review."
            // Keep the existing explicit 900–999 option available; these are not valid station codes.
            if (source.id.toIntOrNull() in 900..999 && explicitCode == null && code.isBlank()) code = source.id
            IofCourseControlMapping(source.id, name, type, code, notes)
        }

    fun isRoutePoint(sourceId: String, useRouteBends: Boolean): Boolean =
        useRouteBends && sourceId.toIntOrNull() in 900..999

    fun errors(sources: List<IofCourseControlSource>, mappings: List<IofCourseControlMapping>, useRouteBends: Boolean): List<String> {
        val errors = mutableListOf<String>()
        if (mappings.map { it.sourceId }.toSet() != sources.map { it.id }.toSet() ||
            mappings.map { it.sourceId }.distinct().size != mappings.size) errors += "Review each XML point exactly once."
        mappings.forEach { row ->
            if (row.publicName.isBlank()) errors += "${row.sourceId}: enter an alias/public name."
            if (row.pointType !in pointTypes) errors += "${row.sourceId}: unsupported point role."
            if (isRoutePoint(row.sourceId, useRouteBends)) {
                if (row.pointType != ProtectedCourseObjectType.CONTROL) errors += "${row.sourceId}: a route point cannot be a Beacon, Spectator or endpoint."
            } else if (row.pointType.controlRole() != null && row.siCode.trim().toIntOrNull()?.let(SportIdentCodes::isSICodeValid) != true)
                errors += "${row.sourceId}: enter a valid SI station code (${SportIdentCodes.SI_MIN_CODE}–${SportIdentCodes.SI_MAX_CODE})."
        }
        mappings.filter { it.pointType.controlRole() != null && !isRoutePoint(it.sourceId, useRouteBends) }
            .groupBy { it.siCode.trim().toIntOrNull() }.filterKeys { it != null }.filterValues { it.size > 1 }
            .forEach { (code, rows) -> errors += "SI $code is assigned to multiple XML controls: ${rows.joinToString { it.sourceId }}." }
        return errors
    }

    fun requireValid(sources: List<IofCourseControlSource>, mappings: List<IofCourseControlMapping>, useRouteBends: Boolean) {
        val errors = errors(sources, mappings, useRouteBends)
        require(errors.isEmpty()) { errors.joinToString("\n") }
    }

    private fun inferredPointType(name: String?): ProtectedCourseObjectType? =
        when (ControlRoleLabelRules.inferredRole(name)) {
            ControlPointType.BEACON -> ProtectedCourseObjectType.BEACON
            ControlPointType.SEPARATOR -> ProtectedCourseObjectType.SPECTATOR
            ControlPointType.CONTROL -> ProtectedCourseObjectType.CONTROL
            null -> when (name?.trim()?.uppercase()) {
                "F", "FIN", "FINISH" -> ProtectedCourseObjectType.FINISH
                "START" -> ProtectedCourseObjectType.START
                else -> null
            }
        }

    fun typeLabel(type: ProtectedCourseObjectType): String = when (type) {
        ProtectedCourseObjectType.CONTROL -> "Fox"
        ProtectedCourseObjectType.BEACON -> "Beacon"
        ProtectedCourseObjectType.SPECTATOR -> "Spectator"
        ProtectedCourseObjectType.START -> "Start"
        ProtectedCourseObjectType.FINISH -> "Finish"
        else -> "Route point"
    }
}
