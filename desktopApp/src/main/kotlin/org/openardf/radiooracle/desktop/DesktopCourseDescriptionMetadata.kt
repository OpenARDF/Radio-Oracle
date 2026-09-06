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

import org.openardf.radiooracle.shared.event.ControlRoleLabelRules

internal fun String.courseDescriptionIdentityKey(): String {
    val normalized = trim().lowercase().replace(Regex("[^a-z0-9]+"), "")
    val foxNumber = ControlRoleLabelRules.foxNumber(this)?.let { number ->
        when (number) {
            in 1..5 -> number
            in 31..35 -> number - 30
            in 41..45 -> number - 40
            else -> null
        }
    }
    return foxNumber?.let { "fox:$it" } ?: normalized
}

internal fun Iterable<Pair<String, String?>>.unambiguousCourseDescriptionsByIdentity(): Map<String, String> =
    mapNotNull { (label, description) ->
        description?.takeIf { it.isNotBlank() }?.let { label.courseDescriptionIdentityKey() to it }
    }
        .groupBy({ it.first }, { it.second })
        .mapNotNull { (identity, descriptions) ->
            descriptions.distinct().singleOrNull()?.let { identity to it }
        }
        .toMap()
