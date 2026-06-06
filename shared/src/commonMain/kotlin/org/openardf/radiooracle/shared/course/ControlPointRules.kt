package org.openardf.radiooracle.shared.course

import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.sportident.SportIdentCodes

/** Shared parser, formatter, and rule validator for category control-point sequences. */
object ControlPointRules {
    const val SPECTATOR_CONTROL_MARKER = '!'
    const val BEACON_CONTROL_MARKER = 'B'

    /** Parses a user-entered control string and validates it for the selected race type. */
    fun parseControlPoints(
        input: String,
        raceType: RaceType,
        tokenResolver: (String, Int) -> ControlPointDefinition? = { _, _ -> null }
    ): List<ControlPointDefinition> {
        if (input.isEmpty()) {
            return emptyList()
        }

        val controlPoints = tokenizeControlPoints(input).mapIndexed { index, token ->
            val order = index + 1
            tokenResolver(token, order) ?: parseControlPoint(order, token)
        }

        validateControlSequence(controlPoints, raceType)
        return controlPoints
    }

    /**
     * Parses category Assigned Controls as a neutral set rather than an ideal route.
     *
     * Radio-orienteering assigned controls are normalized before validation so the
     * stored/displayed list does not imply a preferred visiting order: foxes sort
     * numerically, beacons sort last, and sprint spectators sort between slow and
     * fast fox groups.
     */
    fun parseAssignedControlPoints(
        input: String,
        raceType: RaceType,
        tokenResolver: (String, Int) -> ControlPointDefinition? = { _, _ -> null }
    ): List<ControlPointDefinition> {
        if (input.isEmpty()) {
            return emptyList()
        }

        val controlPoints = tokenizeControlPoints(input).mapIndexed { index, token ->
            val order = index + 1
            tokenResolver(token, order) ?: parseControlPoint(order, token)
        }
        val normalizedControlPoints = normalizeAssignedControlPoints(controlPoints, raceType)

        validateControlSequence(normalizedControlPoints, raceType)
        return normalizedControlPoints
    }

    /** Returns Assigned Controls in neutral display order without changing the original definitions. */
    fun normalizeAssignedControlPoints(
        controlPoints: List<ControlPointDefinition>,
        raceType: RaceType
    ): List<ControlPointDefinition> =
        if (raceType == RaceType.ORIENTEERING) {
            controlPoints.sortedBy { it.order }
        } else {
            controlPoints.sortedWith(
                compareBy<ControlPointDefinition> { assignedControlSortGroup(it.siCode, it.type, raceType) }
                    .thenBy { it.siCode }
                    .thenBy { it.order }
            )
        }

    /** Sort group used by Assigned Controls displays and Event File public-control ordering. */
    fun assignedControlSortGroup(siCode: Int, type: ControlPointType, raceType: RaceType): Int =
        when (raceType) {
            RaceType.SPRINT -> when (type) {
                ControlPointType.CONTROL -> if (isSprintFastFox(siCode)) 2 else 0
                ControlPointType.SEPARATOR -> 1
                ControlPointType.BEACON -> 3
            }
            RaceType.CLASSIC,
            RaceType.SHORT,
            RaceType.FOXORING -> when (type) {
                ControlPointType.CONTROL -> 0
                ControlPointType.SEPARATOR -> 1
                ControlPointType.BEACON -> 2
            }
            RaceType.ORIENTEERING -> 0
        }

    /** Splits category course input on separators, preserving quoted labels that contain spaces. */
    fun tokenizeControlPoints(input: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        val hasExplicitDelimiter = input.any { it == ',' || it == ';' }

        fun flushToken() {
            current.toString().trim().takeIf { it.isNotEmpty() }?.let(tokens::add)
            current.clear()
        }

        input.forEach { char ->
            when {
                quote != null -> {
                    if (char == quote) {
                        quote = null
                    } else {
                        current.append(char)
                    }
                }
                char == '"' || char == '\'' -> quote = char
                char == ',' || char == ';' -> flushToken()
                char.isWhitespace() && !hasExplicitDelimiter -> flushToken()
                else -> current.append(char)
            }
        }
        flushToken()
        return tokens
    }

    /** Formats parsed control definitions back into the compact user-facing course string. */
    fun formatControlPoints(controlPoints: List<ControlPointDefinition>): String {
        return controlPoints.joinToString(" ") { controlPoint ->
            val marker = when (controlPoint.type) {
                ControlPointType.BEACON -> BEACON_CONTROL_MARKER.toString()
                ControlPointType.SEPARATOR -> SPECTATOR_CONTROL_MARKER.toString()
                ControlPointType.CONTROL -> ""
            }
            "${controlPoint.siCode}$marker"
        }
    }

    /** Formats control display tokens, preserving positions for skipped tokens. */
    fun formatDisplayTokens(tokens: List<ControlPointDisplayToken>, useAlias: Boolean): String {
        val builder = StringBuilder()

        for ((index, token) in tokens.withIndex()) {
            if (token.include) {
                builder.append(
                    if (useAlias && token.aliasName != null) {
                        token.aliasName
                    } else {
                        token.siCode.toString()
                    }
                )
            }

            if (index < tokens.size - 1) {
                builder.append(" ")
            }
        }

        return builder.toString()
    }

    /** Formats display tokens for text fields that must be parseable when edited again. */
    fun formatEditableDisplayTokens(tokens: List<ControlPointDisplayToken>, useAlias: Boolean): String {
        val builder = StringBuilder()

        for ((index, token) in tokens.withIndex()) {
            if (token.include) {
                builder.append(
                    if (useAlias && token.aliasName != null) {
                        quoteTokenIfNeeded(token.aliasName)
                    } else {
                        token.siCode.toString()
                    }
                )
            }

            if (index < tokens.size - 1) {
                builder.append(" ")
            }
        }

        return builder.toString()
    }

    /** Formats only included display tokens with a trailing space after each included token. */
    fun formatIncludedDisplayTokensWithTrailingSpaces(
        tokens: List<ControlPointDisplayToken>,
        useAlias: Boolean
    ): String {
        val builder = StringBuilder()

        for (token in tokens) {
            if (token.include) {
                builder.append(
                    if (useAlias && token.aliasName != null) {
                        token.aliasName
                    } else {
                        token.siCode.toString()
                    }
                )
                builder.append(" ")
            }
        }

        return builder.toString()
    }

    private fun quoteTokenIfNeeded(token: String): String {
        val trimmedToken = token.trim()
        val needsQuoting = trimmedToken.any { it.isWhitespace() || it == ',' || it == ';' }
        if (!needsQuoting) {
            return trimmedToken
        }
        return when {
            '\'' !in trimmedToken -> "'$trimmedToken'"
            '"' !in trimmedToken -> "\"$trimmedToken\""
            else -> trimmedToken
        }
    }

    private fun isSprintFastFox(siCode: Int): Boolean =
        siCode >= 40

    private fun parseControlPoint(order: Int, token: String): ControlPointDefinition {
        val controlPointType =
            when (val lastCharacter = token.last()) {
                SPECTATOR_CONTROL_MARKER -> ControlPointType.SEPARATOR
                BEACON_CONTROL_MARKER -> ControlPointType.BEACON
                else -> if (lastCharacter.isDigit()) ControlPointType.CONTROL
                else throw ControlPointValidationException(
                    ControlPointValidationError.UNKNOWN_SPECIFIER,
                    token = lastCharacter.toString()
                )
            }

        val siCode =
            if (controlPointType == ControlPointType.CONTROL) {
                token.toIntOrNull()
            } else {
                token.dropLast(1).toIntOrNull()
            } ?: throw ControlPointValidationException(
                ControlPointValidationError.UNKNOWN_SPECIFIER,
                token = token
            )

        if (!SportIdentCodes.isSICodeValid(siCode)) {
            throw ControlPointValidationException(
                ControlPointValidationError.INVALID_RANGE,
                token = token
            )
        }

        return ControlPointDefinition(siCode, controlPointType, order)
    }

    private fun validateControlSequence(controlPoints: List<ControlPointDefinition>, raceType: RaceType) {
        when (raceType) {
            RaceType.ORIENTEERING -> validateOrienteeringControlSequence(controlPoints)
            RaceType.CLASSIC, RaceType.SHORT, RaceType.FOXORING -> validateClassicsControlSequence(controlPoints)
            RaceType.SPRINT -> validateSprintControlSequence(controlPoints)
        }
    }

    private fun validateOrienteeringControlSequence(controlPoints: List<ControlPointDefinition>) {
        for (i in 1..<controlPoints.size) {
            val controlPoint = controlPoints[i]
            val previousControlPoint = controlPoints[i - 1]

            if (controlPoint.type != ControlPointType.CONTROL) {
                throw ControlPointValidationException(ControlPointValidationError.ORIENTEERING_SPECIAL)
            }

            if (controlPoint.siCode == previousControlPoint.siCode) {
                throw ControlPointValidationException(ControlPointValidationError.TWO_IN_ROW)
            }
        }
    }

    private fun validateClassicsControlSequence(controlPoints: List<ControlPointDefinition>) {
        if (controlPoints.isEmpty()) {
            return
        }

        val previousCodes = HashSet<Int>()
        for (i in controlPoints.indices) {
            val controlPoint = controlPoints[i]

            if (controlPoint.type == ControlPointType.SEPARATOR) {
                throw ControlPointValidationException(ControlPointValidationError.CLASSIC_SPECTATOR_NOT_ALLOWED)
            }

            if (previousCodes.contains(controlPoint.siCode)) {
                throw ControlPointValidationException(ControlPointValidationError.CLASSIC_DUPLICATE)
            }

            if (controlPoint.type == ControlPointType.BEACON && i != controlPoints.size - 1) {
                throw ControlPointValidationException(ControlPointValidationError.NON_LAST_BEACON)
            }
            previousCodes.add(controlPoint.siCode)
        }
    }

    private fun validateSprintControlSequence(controlPoints: List<ControlPointDefinition>) {
        if (controlPoints.isEmpty()) {
            return
        }

        val previousCodesInLap = HashSet<Int>()
        val previousCodesGlobal = HashSet<Int>()
        previousCodesInLap.add(controlPoints.first().siCode)
        previousCodesGlobal.add(controlPoints.first().siCode)

        for (i in 1..<controlPoints.size) {
            val controlPoint = controlPoints[i]
            val previousControlPoint = controlPoints[i - 1]
            val siCode = controlPoint.siCode

            if (siCode == previousControlPoint.siCode) {
                throw ControlPointValidationException(ControlPointValidationError.TWO_IN_ROW)
            }

            if (previousCodesInLap.contains(siCode)) {
                throw ControlPointValidationException(ControlPointValidationError.SPRINT_DUPLICATE)
            }

            when (controlPoint.type) {
                ControlPointType.CONTROL -> {
                    previousCodesInLap.add(siCode)
                    previousCodesGlobal.add(siCode)
                }

                ControlPointType.SEPARATOR -> {
                    if (previousCodesGlobal.contains(siCode)) {
                        throw ControlPointValidationException(
                            ControlPointValidationError.SPRINT_SPECIAL_REUSES_CONTROL,
                            siCode = siCode
                        )
                    }
                    previousCodesInLap.clear()
                }

                ControlPointType.BEACON -> {
                    if (previousCodesGlobal.contains(siCode) || i != controlPoints.size - 1) {
                        throw ControlPointValidationException(ControlPointValidationError.NON_LAST_BEACON)
                    }
                }
            }
        }
    }
}

/** Display-ready control token that can use either a raw SI code or an alias name. */
data class ControlPointDisplayToken(
    val siCode: Int,
    val aliasName: String? = null,
    val include: Boolean = true
)

/** Structured control-point validation failure for callers that provide localized text. */
class ControlPointValidationException(
    val error: ControlPointValidationError,
    val token: String? = null,
    val siCode: Int? = null
) : IllegalArgumentException()

/** Machine-readable reasons why a control-point sequence can be invalid. */
enum class ControlPointValidationError {
    UNKNOWN_SPECIFIER,
    INVALID_RANGE,
    TWO_IN_ROW,
    ORIENTEERING_SPECIAL,
    CLASSIC_DUPLICATE,
    NON_LAST_BEACON,
    CLASSIC_SPECTATOR_NOT_ALLOWED,
    SPRINT_DUPLICATE,
    SPRINT_SPECIAL_REUSES_CONTROL
}
