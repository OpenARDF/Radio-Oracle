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

package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType

/** Creates shared project-file aggregates for new desktop and future non-Android projects. */
object EventProjectFactory {
    /**
     * Builds an empty Race File using the same conservative defaults as Android's new race model.
     *
     * The caller supplies IDs and time text so UI layers can choose platform-specific UUID and clock
     * sources while shared code owns the event defaults and aggregate shape.
     */
    fun createEmptyProject(
        raceId: String,
        raceName: String,
        startDateTimeIso: String
    ): EventProjectFile {
        val trimmedName = raceName.trim()
        require(raceId.isNotBlank()) {
            "Race ID cannot be blank."
        }
        require(trimmedName.isNotEmpty()) {
            "Race name cannot be blank."
        }
        require(startDateTimeIso.isNotBlank()) {
            "Race start date/time cannot be blank."
        }

        return EventProjectFile(
            raceData = EventRaceData(
                race = EventRace(
                    id = raceId,
                    name = trimmedName,
                    apiKey = "",
                    startDateTimeIso = startDateTimeIso,
                    raceType = RaceType.CLASSIC,
                    raceLevel = RaceLevel.PRACTICE,
                    raceBand = RaceBand.M80,
                    timeLimitSeconds = 7_200
                ),
                categories = emptyList(),
                aliases = emptyList(),
                competitorData = emptyList(),
                unmatchedReadoutData = emptyList(),
                /*
                 * New Race Files should expose missing course setup immediately. Preset controls
                 * can mask an incomplete Race File as valid-looking data, so organizers add or
                 * import the real controls before setup can be considered complete.
                 */
                controls = emptyList()
            )
        )
    }
}
