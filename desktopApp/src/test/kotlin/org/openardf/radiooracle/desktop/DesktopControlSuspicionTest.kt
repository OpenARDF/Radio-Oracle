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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.EventCategory
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventControlDetails
import org.openardf.radiooracle.shared.event.EventControlPoint

class DesktopControlSuspicionTest {
    @Test
    fun flagsUnusedMissingAndDuplicatePublicLabels() {
        val fox1 = control("fox1", 31, "Fox 1")
        val fox2 = control("fox2", 32, "")
        val fox3 = control("fox3", 33, "Fox 1")
        val beacon = control("beacon", 50, "B", ControlPointType.BEACON)
        val category = category(
            controlPoint("fox1", 31),
            controlPoint("beacon", 50, ControlPointType.BEACON)
        )

        val reasons = controlSuspicionReasonsByControlId(
            controls = listOf(fox1, fox2, fox3, beacon),
            categories = listOf(category)
        )

        assertEquals(
            listOf("Public label \"Fox 1\" is also used by another control."),
            reasons.getValue("fox1")
        )
        assertEquals(
            listOf(
                "Control is not assigned to any category.",
                "Control has no Public label."
            ),
            reasons.getValue("fox2")
        )
        assertEquals(
            listOf(
                "Control is not assigned to any category.",
                "Public label \"Fox 1\" is also used by another control."
            ),
            reasons.getValue("fox3")
        )
        assertTrue(reasons.getValue("beacon").isEmpty())
    }

    @Test
    fun resolvesLegacyAssignmentsBySiCodeAndRole() {
        val fox1 = control("fox1", 31, "Fox 1")
        val category = category(
            EventControlPoint(
                id = "legacy-fox1",
                categoryId = "cat",
                siCode = 31,
                type = ControlPointType.CONTROL,
                order = 1,
                controlId = ""
            )
        )

        val reasons = controlSuspicionReasonsByControlId(
            controls = listOf(fox1),
            categories = listOf(category)
        )

        assertTrue(reasons.getValue("fox1").isEmpty())
    }

    @Test
    fun treatsPublicControlIdsAsCategoryUse() {
        val fox1 = control("fox1", 31, "Fox 1")
        val category = category().copy(publicControlIds = listOf("fox1"))

        val reasons = controlSuspicionReasonsByControlId(
            controls = listOf(fox1),
            categories = listOf(category)
        )

        assertTrue(reasons.getValue("fox1").isEmpty())
    }

    @Test
    fun doesNotFlagUnusedControlsBeforeCategoriesExist() {
        val fox1 = control("fox1", 31, "Fox 1")

        val reasons = controlSuspicionReasonsByControlId(
            controls = listOf(fox1),
            categories = emptyList()
        )

        assertTrue(reasons.getValue("fox1").isEmpty())
    }

    private fun control(
        id: String,
        siCode: Int,
        publicLabel: String,
        type: ControlPointType = ControlPointType.CONTROL
    ): EventControlDetails =
        EventControlDetails(
            id = id,
            label = publicLabel.ifBlank { "Control $siCode" },
            siCode = siCode,
            siCodeText = siCode.toString(),
            type = type,
            typeLabel = type.name,
            scored = type == ControlPointType.CONTROL,
            publicLabel = publicLabel,
            notes = ""
        )

    private fun category(vararg controlPoints: EventControlPoint): EventCategoryData =
        EventCategoryData(
            category = EventCategory(
                id = "cat",
                raceId = "race",
                name = "M21",
                isMan = true,
                maxAge = null,
                lengthMeters = 0,
                climbMeters = 0,
                order = 1,
                differentProperties = false,
                raceType = RaceType.CLASSIC,
                raceBand = RaceBand.M2,
                timeLimitSeconds = null,
                controlPointsString = ""
            ),
            controlPoints = controlPoints.toList(),
            competitors = emptyList()
        )

    private fun controlPoint(
        controlId: String,
        siCode: Int,
        type: ControlPointType = ControlPointType.CONTROL
    ): EventControlPoint =
        EventControlPoint(
            id = "cp-$controlId",
            categoryId = "cat",
            siCode = siCode,
            type = type,
            order = 1,
            controlId = controlId
        )
}
