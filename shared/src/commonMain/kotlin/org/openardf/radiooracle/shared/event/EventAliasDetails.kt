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

/** Shared alias row prepared for desktop and other race-admin surfaces. */
data class EventAliasDetails(
    val id: String,
    val siCode: Int,
    val siCodeText: String,
    val name: String
) {
    companion object {
        /** Builds alias display rows sorted by SI code and then display name. */
        fun from(raceData: EventRaceData): List<EventAliasDetails> =
            raceData.aliases
                .sortedWith(compareBy<EventAlias> { it.siCode }.thenBy { it.name })
                .map { alias ->
                    EventAliasDetails(
                        id = alias.id,
                        siCode = alias.siCode,
                        siCodeText = alias.siCode.toString(),
                        name = alias.name
                    )
                }
    }
}
