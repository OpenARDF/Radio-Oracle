package org.openardf.radiooracle.shared.publicresults

import kotlin.test.*

class PublicResultsArtifactVerificationTest {
    private fun site() = CloudflarePagesSite(listOf(
        CloudflarePagesAsset("index.html", "<a href=\"race/\">Race</a>".toByteArray(), "text/html"),
        CloudflarePagesAsset("data/races.json", "{\"races\":[]}".toByteArray(), "application/json"),
        CloudflarePagesAsset("race/index.html", "<a href=\"../\">Home</a><img src=\"diagram.png?generation=2\">".toByteArray(), "text/html"),
        CloudflarePagesAsset("race/diagram.png", byteArrayOf(0, 1, -1, 42), "image/png")
    ))

    @Test fun binaryInventoryDetectsStaleCorruptAndMissingDownloads() {
        val site = site()
        val files = site.assets.associate { it.relativePath to it.content }
        val manifest = PublicResultsArtifactVerification.manifest(site)
        assertTrue(PublicResultsArtifactVerification.verify(manifest) { files.getValue(it) }.isEmpty())
        assertEquals(listOf("race/diagram.png"), PublicResultsArtifactVerification.verify(manifest) {
            if (it.endsWith("png")) byteArrayOf(0, 1, -1, 43) else files.getValue(it)
        })
        assertEquals(listOf("race/diagram.png"), PublicResultsArtifactVerification.verify(manifest) {
            if (it.endsWith("png")) error("404") else files.getValue(it)
        })
    }

    @Test fun missingLinkedDiagramAndLateAssetMutationPreventUpload() {
        val site = site()
        assertFailsWith<IllegalArgumentException> { PublicResultsArtifactVerification.manifest(site.copy(assets = site.assets.dropLast(1))) }
        site.assets.last().content[0] = 3
        assertFailsWith<IllegalArgumentException> { PublicResultsArtifactVerification.manifest(site) }
    }
    @Test fun acceptedDeploymentWaitsForFreshPublicBytesAndReportsPersistentMismatch() {
        for (eventuallyReady in listOf(true, false)) {
            val site = site()
            val files = site.assets.associate { it.relativePath to it.content }
            var publicRequests = 0
            fun api(result: String) = CloudflarePagesHttpResponse(200, """{"success":true,"result":$result}""")
            val publisher = CloudflarePagesPublisher(CloudflarePagesHttpTransport { request ->
                val uri = java.net.URI(request.url)
                if (uri.host == "fixture.pages.dev") {
                    assertFalse(request.headers.containsKey("Authorization"))
                    assertTrue(uri.query.startsWith("roverify="))
                    publicRequests++
                    val current = eventuallyReady && publicRequests > files.size
                    val bytes = if (current) files.getValue(uri.path.removePrefix("/")) else "previous deployment".toByteArray()
                    CloudflarePagesHttpResponse(200, "", bytes)
                } else when {
                    uri.path.endsWith("upload-token") -> api("""{"jwt":"fixture-upload"}""")
                    uri.path.endsWith("check-missing") -> api("[]")
                    uri.path.endsWith("upsert-hashes") -> api("null")
                    uri.path.endsWith("deployments") -> api("""{"id":"fixture-deployment","url":"https://immutable.fixture.pages.dev"}""")
                    else -> error("Unexpected publish request")
                }
            }, verificationAttempts = 2, pauseBeforeVerificationRetry = {})
            val request = CloudflarePagesPublishRequest("fixture", "main", "account", "fixture-token")
            if (eventuallyReady) assertTrue(publisher.publish(request, site).output.contains("Fresh public downloads verified"))
            else assertTrue(assertFailsWith<IllegalArgumentException> { publisher.publish(request, site) }.message!!.contains("accepted deployment fixture-deployment"))
            assertEquals(files.size * 2, publicRequests)
        }
    }

}
