package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.domain.ProviderType
import org.openardf.radiooracle.shared.event.EventProjectEditor
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.files.LiveResultJsonExports
import org.openardf.radiooracle.shared.network.LiveResultRequestSpec
import org.openardf.radiooracle.shared.network.LiveResultRequests
import org.openardf.radiooracle.shared.results.EventResultSending
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

data class DesktopLiveResultSendResponse(
    val statusCode: Int,
    val body: String
) {
    val successful: Boolean
        get() = statusCode in 200..299
}

data class DesktopLiveResultSendResult(
    val projectFile: EventProjectFile,
    val sentCount: Int,
    val statusCode: Int,
    val responseBody: String
)

/** Desktop ROBIS sender for explicit manual live-result uploads. */
class DesktopRobisLiveResultSender(
    private val transport: (LiveResultRequestSpec, String) -> DesktopLiveResultSendResponse = ::jdkTransport
) {
    fun sendUnsent(projectFile: EventProjectFile, apiKey: String): DesktopLiveResultSendResult {
        val plan = EventResultSending.plan(projectFile.raceData)
        require(plan.hasCandidates) {
            "There are no unsent matched results to send."
        }

        val resultIds = plan.candidates.map { it.resultId }.toSet()
        val payload = LiveResultJsonExports.results(projectFile.raceData, resultIds)
        val request = LiveResultRequests.robis(ProviderType.ROBIS, apiKey)
        val response = transport(request, payload)
        require(response.successful) {
            "ROBIS send failed with HTTP ${response.statusCode}: ${response.body}"
        }

        return DesktopLiveResultSendResult(
            projectFile = EventProjectEditor.markReadoutsSent(projectFile, resultIds),
            sentCount = plan.candidateCount,
            statusCode = response.statusCode,
            responseBody = response.body
        )
    }
}

private fun jdkTransport(
    requestSpec: LiveResultRequestSpec,
    payload: String
): DesktopLiveResultSendResponse {
    val requestBuilder = HttpRequest.newBuilder(URI.create(requestSpec.url))
        .method(requestSpec.method, HttpRequest.BodyPublishers.ofString(payload))
        .header("Content-Type", requestSpec.contentType)

    requestSpec.headers.forEach { (name, value) ->
        requestBuilder.header(name, value)
    }

    val response = HttpClient.newHttpClient().send(
        requestBuilder.build(),
        HttpResponse.BodyHandlers.ofString()
    )
    return DesktopLiveResultSendResponse(
        statusCode = response.statusCode(),
        body = response.body()
    )
}
