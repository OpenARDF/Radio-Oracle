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

import android.content.Context
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.files.processors.JsonProcessor
import org.openardf.radiooracle.backend.network.NetworkConstants.ROBIS_API_HEADER
import org.openardf.radiooracle.backend.network.NetworkConstants.ROBIS_PLAYGROUND_RACE_API_URL
import org.openardf.radiooracle.backend.network.NetworkConstants.ROBIS_RACE_API_URL
import org.openardf.radiooracle.backend.room.entity.embeddeds.RaceData
import org.openardf.radiooracle.backend.room.enums.ProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Client for downloading race data from online event providers. */
object ProviderClient {

    /** Fetches a full race payload from the selected provider and parses it into race data. */
    suspend fun fetchRaceData(
        apiKey: String,
        providerType: ProviderType,
        dataProcessor: DataProcessor,
        context: Context
    ): RaceData {

        val httpClient = OkHttpClient.Builder().build()
        val url = if (providerType == ProviderType.ROBIS)
            ROBIS_RACE_API_URL
        else ROBIS_PLAYGROUND_RACE_API_URL

        // Current provider downloads use the ROBIS API-key header.
        val request: Request = Request.Builder()
            .url(url)
            .addHeader(ROBIS_API_HEADER, apiKey)
            .build()


        // Execute the blocking network call and JSON parsing on Dispatchers.IO.
        val raceData = withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().use { response ->
                val bodyString = response.body.string()

                when (response.code) {
                    in 200..299 -> {
                        JsonProcessor.importRaceData(bodyString, dataProcessor)
                    }

                    403 -> {
                        throw IllegalArgumentException(context.getString(R.string.result_service_invalid_api_key))
                    }

                    else -> {
                        throw Exception("${context.getString(R.string.general_unknown_error)} ${response.code}")
                    }
                }
            }
        }

        return raceData
    }

    const val LOG_TAG = "ROBIS_CLIENT"
}
