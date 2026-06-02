package org.openardf.radiooracle.shared.network

import org.openardf.radiooracle.shared.domain.ProviderType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LiveResultRequestsTest {
    @Test
    fun buildsRobisProductionRequestSpec() {
        val spec = LiveResultRequests.robis(ProviderType.ROBIS, apiKey = "secret")

        assertEquals(ProviderType.ROBIS, spec.providerType)
        assertEquals("PUT", spec.method)
        assertEquals(NetworkEndpoints.ROBIS_RESULTS_API_URL, spec.url)
        assertEquals(mapOf(NetworkHeaders.ROBIS_API_HEADER to "secret"), spec.headers)
        assertEquals("application/json; charset=utf-8", spec.contentType)
    }

    @Test
    fun buildsRobisPlaygroundRequestSpec() {
        val spec = LiveResultRequests.robis(ProviderType.ROBIS_TEST, apiKey = "secret")

        assertEquals(NetworkEndpoints.ROBIS_PLAYGROUND_RESULTS_API_URL, spec.url)
    }

    @Test
    fun rejectsNonRobisProviders() {
        assertFailsWith<IllegalArgumentException> {
            LiveResultRequests.robis(ProviderType.ORESULTS, apiKey = "secret")
        }
    }

    @Test
    fun rejectsBlankApiKey() {
        assertFailsWith<IllegalArgumentException> {
            LiveResultRequests.robis(ProviderType.ROBIS, apiKey = "")
        }
    }
}
