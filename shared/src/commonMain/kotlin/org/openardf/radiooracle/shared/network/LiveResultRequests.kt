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

package org.openardf.radiooracle.shared.network

import org.openardf.radiooracle.shared.domain.ProviderType

data class LiveResultRequestSpec(
    val providerType: ProviderType,
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val contentType: String
)

/** Shared provider request metadata for platform-specific live-result HTTP clients. */
object LiveResultRequests {
    const val METHOD_PUT = "PUT"
    const val CONTENT_TYPE_JSON = "application/json; charset=utf-8"

    fun robis(providerType: ProviderType, apiKey: String): LiveResultRequestSpec {
        require(providerType == ProviderType.ROBIS || providerType == ProviderType.ROBIS_TEST) {
            "ROBIS request metadata requires ROBIS or ROBIS_TEST."
        }
        require(apiKey.isNotBlank()) {
            "ROBIS API key cannot be blank."
        }

        return LiveResultRequestSpec(
            providerType = providerType,
            method = METHOD_PUT,
            url = if (providerType == ProviderType.ROBIS_TEST) {
                NetworkEndpoints.ROBIS_PLAYGROUND_RESULTS_API_URL
            } else {
                NetworkEndpoints.ROBIS_RESULTS_API_URL
            },
            headers = mapOf(NetworkHeaders.ROBIS_API_HEADER to apiKey),
            contentType = CONTENT_TYPE_JSON
        )
    }
}
