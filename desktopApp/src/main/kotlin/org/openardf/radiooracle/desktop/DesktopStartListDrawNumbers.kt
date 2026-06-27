/*
 * MIT License
 *
 * Copyright (c) 2025 Pavel Kolský
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.StartDrawOptions
import java.nio.file.Path

object DesktopStartListDrawNumbers {
    fun assign(
        existingNumbers: Map<String, Int>,
        eventPath: Path?,
        projectFile: EventProjectFile,
        drawContextKey: String? = null
    ): DesktopStartListDrawNumbering {
        val orderPrefix = orderPrefix(eventPath, projectFile, drawContextKey)
        val assignmentSignature = startAssignmentSignature(projectFile)
        val solutionKey = "$orderPrefix|$assignmentSignature"
        val existingNumber = existingNumbers[solutionKey]
        val orderNumber = existingNumber
            ?: (existingNumbers
                .filterKeys { it.startsWith("$orderPrefix|") }
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

    fun orderProjectKey(
        eventPath: Path?,
        projectFile: EventProjectFile,
        orderNumber: Int,
        drawContextKey: String? = null
    ): String =
        "${orderPrefix(eventPath, projectFile, drawContextKey)}|order:$orderNumber"

    fun eventKey(eventPath: Path?, projectFile: EventProjectFile): String =
        eventPath?.toAbsolutePath()?.normalize()?.toString()
            ?: "unsaved:${projectFile.raceData.race.id}"

    fun knownOrderCount(
        existingNumbers: Map<String, Int>,
        eventPath: Path?,
        projectFile: EventProjectFile,
        drawContextKey: String? = null
    ): Int =
        existingNumbers
            .filterKeys { it.startsWith("${orderPrefix(eventPath, projectFile, drawContextKey)}|") }
            .values
            .maxOrNull()
            ?: 0

    fun drawContextKey(intervalText: String, options: StartDrawOptions): String =
        listOf(
            "interval=${intervalText.trim()}",
            "club=${options.clubHandling.name}",
            "starters=${options.startersPerStartTime}",
            "groups=${options.startGroupMode.name}",
            "firstFox=${options.idealFirstFoxByCategoryId.toSortedMap().entries.joinToString(";") { "${it.key}:${it.value}" }}"
        ).joinToString("|")

    private fun orderPrefix(eventPath: Path?, projectFile: EventProjectFile, drawContextKey: String?): String =
        if (drawContextKey.isNullOrBlank()) {
            eventKey(eventPath, projectFile)
        } else {
            "${eventKey(eventPath, projectFile)}|context:$drawContextKey"
        }

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
