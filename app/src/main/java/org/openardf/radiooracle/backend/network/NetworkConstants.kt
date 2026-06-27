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

package org.openardf.radiooracle.backend.network

import okhttp3.MediaType.Companion.toMediaType
import org.openardf.radiooracle.shared.network.NetworkEndpoints
import org.openardf.radiooracle.shared.network.NetworkHeaders

/** Shared network endpoints, headers, and media types used by result-service clients. */
object NetworkConstants {

    // Result-service endpoint URLs.
    const val ROBIS_RACE_API_URL = NetworkEndpoints.ROBIS_RACE_API_URL
    const val ROBIS_PLAYGROUND_RACE_API_URL = NetworkEndpoints.ROBIS_PLAYGROUND_RACE_API_URL
    const val ROBIS_RESULTS_API_URL = NetworkEndpoints.ROBIS_RESULTS_API_URL
    const val ROBIS_PLAYGROUND_RESULTS_API_URL = NetworkEndpoints.ROBIS_PLAYGROUND_RESULTS_API_URL
    const val ORESULTS_RESULTS_API_URL = NetworkEndpoints.ORESULTS_RESULTS_API_URL
    const val OFEED_RESULTS_API_URL = NetworkEndpoints.OFEED_RESULTS_API_URL

    // Result-service authentication and metadata headers.
    const val ROBIS_API_HEADER = NetworkHeaders.ROBIS_API_HEADER
    const val ORESULTS_API_HEADER = NetworkHeaders.ORESULTS_API_HEADER
    const val OFEED_API_AUTH_HEADER = NetworkHeaders.OFEED_API_AUTH_HEADER
    const val OFEED_EVENT_ID = NetworkHeaders.OFEED_EVENT_ID

    val CONTENT_TYPE_JSON = "application/json; charset=utf-8".toMediaType()
    val CONTENT_TYPE_XML = "application/xml; charset=utf-8".toMediaType()
    val CONTENT_TYPE_GZIP = "application/gzip; charset=utf-8".toMediaType()

}
