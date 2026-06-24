package org.openardf.radiooracle.desktop

object DesktopCoursePointLabelClassifier {
    fun isCourseEndpointName(name: String): Boolean =
        isCourseStartName(name) || isCourseFinishName(name)

    fun isCourseStartName(name: String): Boolean {
        val normalized = categoryMatchText(name)
        return Regex("""\bstart\b""").containsMatchIn(normalized) ||
            leadingLettersToken(name) == "start"
    }

    fun isCourseFinishName(name: String): Boolean {
        val normalized = categoryMatchText(name)
        return Regex("""\bfinish\b""").containsMatchIn(normalized) ||
            leadingLettersToken(name) == "finish"
    }

    fun isEndpointStartName(name: String): Boolean =
        isCourseStartName(name) || leadingLettersToken(name) in endpointStartTokens

    fun isEndpointFinishName(name: String): Boolean =
        isCourseFinishName(name) || leadingLettersToken(name) in endpointFinishTokens

    fun isSpectatorLabel(name: String): Boolean =
        normalizedRoleLabel(name) in spectatorLabels ||
            "SPECTATOR" in normalizedRoleLabel(name) ||
            "SEPARATOR" in normalizedRoleLabel(name)

    fun isBeaconLabel(name: String): Boolean =
        normalizedRoleLabel(name) in beaconLabels ||
            "BEACON" in normalizedRoleLabel(name)

    fun isSpectatorPrefixOnlyName(name: String): Boolean {
        val letters = name.lowercase().filter { it in 'a'..'z' }
        return letters.isNotEmpty() && "spectator".startsWith(letters)
    }

    fun sprintSlowFoxNumber(name: String): Int? {
        val normalized = name.trim().uppercase()
        if (normalized.endsWith("F") || normalized.startsWith("F") || normalized.contains("FAST")) {
            return null
        }
        return normalized.sprintLabelNumber()?.takeIf { it in 1..5 }
    }

    fun sprintFastFoxNumber(name: String): Int? {
        val compactName = compactCourseName(name).uppercase()
        val exactMatch = Regex("""^(?:F([1-5])|([1-5])F)$""").matchEntire(compactName)
        if (exactMatch != null) {
            return exactMatch.groupValues.drop(1).firstOrNull { it.isNotBlank() }?.toIntOrNull()
        }
        val normalized = name.trim().uppercase()
        return normalized.takeIf { it.contains("FAST") }?.sprintLabelNumber()?.takeIf { it in 1..5 }
    }

    private fun normalizedCourseName(name: String): String =
        name.trim().lowercase().replace(Regex("\\s+"), " ")

    private fun compactCourseName(name: String): String =
        normalizedCourseName(name).replace(" ", "")

    private fun categoryMatchText(name: String): String =
        normalizedCourseName(name)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    private fun leadingLettersToken(name: String): String =
        name.trim()
            .takeWhile { it.isLetter() }
            .lowercase()

    private fun normalizedRoleLabel(name: String): String =
        name.trim().uppercase().replace(Regex("\\s+"), " ")

    private fun String.sprintLabelNumber(): Int? =
        Regex("""\b([1-5])\b""").find(this)?.groupValues?.get(1)?.toIntOrNull()

    private val endpointStartTokens = setOf("s", "st", "sta", "star", "start")
    private val endpointFinishTokens = setOf("f", "fi", "fin", "fini", "finis", "finish")
    private val spectatorLabels = setOf("S", "SP", "SPEC", "SPECTATOR", "SEP", "SEPARATOR")
    private val beaconLabels = setOf("B", "BB", "M", "MO", "BEACON", "FINISH BEACON")
}
