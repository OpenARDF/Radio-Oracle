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

/** Provider endpoint URLs shared by Android and future desktop result-service code. */
object NetworkEndpoints {
    const val ROBIS_RACE_API_URL = "https://rob-is.cz/api/?type=json&name=race"
    const val ROBIS_PLAYGROUND_RACE_API_URL = "https://playground.rob-is.cz/api/?type=json&name=race"
    const val ROBIS_RESULTS_API_URL = "https://rob-is.cz/api/results/?name=json"
    const val ROBIS_PLAYGROUND_RESULTS_API_URL = "https://playground.rob-is.cz/api/results/?name=json"
    const val ORESULTS_RESULTS_API_URL = "https://api.oresults.eu"
    const val OFEED_RESULTS_API_URL = "https://api.orienteerfeed.com/rest/v1/upload/iof"
}

/** Provider HTTP header names shared by platform-specific network clients. */
object NetworkHeaders {
    const val ROBIS_API_HEADER = "Race-Api-Key"
    const val ORESULTS_API_HEADER = "apiKey"
    const val OFEED_API_AUTH_HEADER = "Authorization"
    const val OFEED_EVENT_ID = "eventId"
}
