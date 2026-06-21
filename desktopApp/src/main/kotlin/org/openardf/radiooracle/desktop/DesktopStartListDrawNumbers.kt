package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.event.EventProjectFile
import java.nio.file.Path

object DesktopStartListDrawNumbers {
    fun assign(
        existingNumbers: Map<String, Int>,
        eventPath: Path?,
        projectFile: EventProjectFile
    ): DesktopStartListDrawNumbering {
        val eventKey = eventKey(eventPath, projectFile)
        val assignmentSignature = startAssignmentSignature(projectFile)
        val solutionKey = "$eventKey|$assignmentSignature"
        val existingNumber = existingNumbers[solutionKey]
        val orderNumber = existingNumber
            ?: (existingNumbers
                .filterKeys { it.startsWith("$eventKey|") }
                .values
                .maxOrNull()
                ?: 0) + 1
        return DesktopStartListDrawNumbering(
            orderNumber = orderNumber,
            repeatedOrder = existingNumber != null,
            assignmentSignature = assignmentSignature,
            orderNumbers = if (existingNumber == null) {
                existingNumbers + (solutionKey to orderNumber)
            } else {
                existingNumbers
            }
        )
    }

    fun orderProjectKey(eventPath: Path?, projectFile: EventProjectFile, orderNumber: Int): String =
        "${eventKey(eventPath, projectFile)}|order:$orderNumber"

    private fun eventKey(eventPath: Path?, projectFile: EventProjectFile): String =
        eventPath?.toAbsolutePath()?.normalize()?.toString()
            ?: "unsaved:${projectFile.raceData.race.id}"

    fun startAssignmentSignature(projectFile: EventProjectFile): String =
        projectFile.raceData.competitorData
            .map { data ->
                val competitor = data.competitorCategory.competitor
                "${competitor.id}:${competitor.drawnStartTimeSeconds ?: "none"}"
            }
            .sorted()
            .joinToString(",")
}

data class DesktopStartListDrawNumbering(
    val orderNumber: Int,
    val repeatedOrder: Boolean,
    val assignmentSignature: String,
    val orderNumbers: Map<String, Int>
)
