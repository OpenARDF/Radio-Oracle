package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.course.ControlPointRules
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.sportident.SportIdentCodes

/** Parsing and resolution rules for protected ideal course-order text. */
object ProtectedIdealOrderRules {
    private val tokenSeparators = Regex("""[\s,;]+""")
    private val numericToken = Regex("""\d+""")

    fun firstControlCode(idealOrderText: String, controls: List<EventControl>): Int? =
        resolveControls(idealOrderText, controls).firstOrNull()?.siCode

    fun validate(idealOrderText: String, controls: List<EventControl>) {
        resolveControls(idealOrderText, controls)
    }

    fun validateAssignedToCategory(idealOrderText: String, assignedControls: List<EventControl>) {
        val resolvedControls = resolveControls(
            idealOrderText = idealOrderText,
            controls = assignedControls,
            allowUncatalogedNumericControls = false,
            missingControlMessage = {
                "Protected ideal order control was not assigned to this category: $it"
            }
        )
        val duplicateControl = resolvedControls
            .groupingBy { it.id }
            .eachCount()
            .firstNotNullOfOrNull { (controlId, count) ->
                assignedControls.firstOrNull { it.id == controlId }?.takeIf { count > 1 }
            }
        require(duplicateControl == null) {
            val label = duplicateControl?.publicLabel?.takeIf { it.isNotBlank() } ?: duplicateControl?.label.orEmpty()
            "Protected ideal order control appears more than once: $label"
        }
    }

    fun resolveControlIds(idealOrderText: String, controls: List<EventControl>): List<String> =
        resolveControls(idealOrderText, controls).map { it.id }

    fun resolveControlCodes(idealOrderText: String, controls: List<EventControl>): List<Int> =
        resolveControls(idealOrderText, controls).map { it.siCode }

    @Deprecated("Use global controls instead of aliases.")
    fun firstControlCodeFromAliases(idealOrderText: String, aliases: List<EventAlias>): Int? =
        resolveControlCodesFromAliases(idealOrderText, aliases).firstOrNull()

    @Deprecated("Use global controls instead of aliases.")
    fun validateFromAliases(idealOrderText: String, aliases: List<EventAlias>) {
        resolveControlCodesFromAliases(idealOrderText, aliases)
    }

    @Deprecated("Use global controls instead of aliases.")
    fun resolveControlCodesFromAliases(idealOrderText: String, aliases: List<EventAlias>): List<Int> {
        val aliasesByName = aliases.associateBy { it.name }
        return idealOrderText
            .split(tokenSeparators)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map { token -> token.resolveControlCode(aliasesByName) }
    }

    private fun resolveControls(
        idealOrderText: String,
        controls: List<EventControl>,
        allowUncatalogedNumericControls: Boolean = true,
        missingControlMessage: (String) -> String = {
            "Protected ideal order control was not found: $it"
        }
    ): List<EventControl> {
        val controlsByLabel = controls.flatMap { control ->
            listOfNotNull(control.label to control, control.publicLabel?.takeIf { it.isNotBlank() }?.let { it to control })
        }.toMap()
        return ControlPointRules.tokenizeControlPoints(idealOrderText)
            .map { token ->
                token.resolveControl(
                    controls = controls,
                    controlsByLabel = controlsByLabel,
                    allowUncatalogedNumericControls = allowUncatalogedNumericControls,
                    missingControlMessage = missingControlMessage
                )
            }
    }

    private fun String.resolveControl(
        controls: List<EventControl>,
        controlsByLabel: Map<String, EventControl>,
        allowUncatalogedNumericControls: Boolean,
        missingControlMessage: (String) -> String
    ): EventControl {
        if (matches(numericToken)) {
            val controlCode = toIntOrNull()
            require(controlCode != null && controlCode > 0) {
                "Protected ideal order control code must be numeric and positive: $this"
            }
            controls.firstOrNull { it.siCode == controlCode }?.let { return it }
            if (allowUncatalogedNumericControls && controlCode <= SportIdentCodes.SI_MAX_CODE) {
                return EventControl(
                    id = EventControlCatalog.stableId(this, controlCode, ControlPointType.CONTROL),
                    raceId = "",
                    label = this,
                    siCode = controlCode,
                    type = ControlPointType.CONTROL
                )
            }
        }
        return controlsByLabel[this]
            ?: throw IllegalArgumentException(missingControlMessage(this))
    }

    private fun String.resolveControlCode(aliasesByName: Map<String, EventAlias>): Int {
        if (matches(numericToken)) {
            val controlCode = toIntOrNull()
            require(controlCode != null && controlCode > 0) {
                "Protected ideal order control code must be numeric and positive: $this"
            }
            if (controlCode <= SportIdentCodes.SI_MAX_CODE) {
                return controlCode
            }
        }
        return aliasesByName[this]?.siCode
            ?: throw IllegalArgumentException("Protected ideal order alias was not found: $this")
    }
}
