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

import kotlinx.serialization.Serializable

/** Organizer-selected publication state for generated result websites and downloads. */
@Serializable
enum class PublicResultsPublicationStatus {
    PRELIMINARY,
    OFFICIAL;

    val displayLabel: String
        get() = when (this) {
            PRELIMINARY -> "Preliminary Results"
            OFFICIAL -> "Official Results"
        }
}

/** Portable public-results location retained with a Race File or Race Series. */
@Serializable
data class PublicResultsPublication(
    val url: String,
    val publishedAtIso: String,
    val status: PublicResultsPublicationStatus = PublicResultsPublicationStatus.PRELIMINARY
) {
    init {
        require(url.isNotBlank()) {
            "Public results URL must not be blank."
        }
        require(publishedAtIso.isNotBlank()) {
            "Public results publication time must not be blank."
        }
    }
}
