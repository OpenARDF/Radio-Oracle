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
