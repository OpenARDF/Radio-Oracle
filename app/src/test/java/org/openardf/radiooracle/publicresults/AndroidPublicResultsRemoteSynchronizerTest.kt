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

package org.openardf.radiooracle.publicresults

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.backend.publicresults.AndroidPublicResultsRemoteSynchronizer
import java.nio.file.Files

class AndroidPublicResultsRemoteSynchronizerTest {
    @Test
    fun synchronizesSeriesMembersDownloadsAndCourseGraphics() {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.requestUrl?.encodedPath) {
                    "/data/races.json" -> ok(
                        """{"races":[{"path":"series","name":"Series","start":"2026-07-27","generatedAt":"now","resultCount":1,"unofficialResults":false,"publicationId":"series:1"}]}"""
                    )
                    "/index.html" -> ok("<html>root</html>")
                    "/series/index.html" -> ok("<html>series</html>")
                    "/series/data/series-results.json" -> ok(
                        """
                        {
                          "races":[{
                            "dataUrl":"../race/data/public-results.json",
                            "courseGraphics":["../race/course-graphics/course-category.svg"]
                          }]
                        }
                        """.trimIndent()
                    )
                    "/race/index.html" -> ok("<html>race</html>")
                    "/race/data/event-summary.json" -> ok(
                        """{"courseGraphics":["course-graphics/course-category.svg"]}"""
                    )
                    "/race/data/public-results.json" -> ok("""{"categories":[]}""")
                    "/race/downloads/printable-results.html" -> ok("<html>print</html>")
                    "/race/course-graphics/course-category.svg" -> ok("<svg/>")
                    else -> MockResponse().setResponseCode(404)
                }
        }
        server.start()
        try {
            val target = Files.createTempDirectory("android-results-target").toFile()
            target.deleteRecursively()
            val cache = Files.createTempDirectory("android-results-cache").toFile()

            AndroidPublicResultsRemoteSynchronizer().synchronize(
                baseUrl = server.url("/").toString(),
                targetDirectory = target,
                cacheDirectory = cache
            )

            assertEquals("<html>root</html>", target.resolve("index.html").readText())
            assertTrue(target.resolve("series/data/series-results.json").isFile)
            assertTrue(target.resolve("race/data/public-results.json").isFile)
            assertTrue(target.resolve("race/downloads/printable-results.html").isFile)
            assertTrue(target.resolve("race/course-graphics/course-category.svg").isFile)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun rejectsMalformedRemoteCatalogWithoutReplacingLocalMirror() {
        val server = MockWebServer()
        server.enqueue(ok("""{"races":not-valid-json}"""))
        server.start()
        try {
            val target = Files.createTempDirectory("android-results-target").toFile()
            val marker = target.resolve("keep.txt").also { it.writeText("keep") }
            val cache = Files.createTempDirectory("android-results-cache").toFile()

            val failure = runCatching {
                AndroidPublicResultsRemoteSynchronizer().synchronize(
                    baseUrl = server.url("/").toString(),
                    targetDirectory = target,
                    cacheDirectory = cache
                )
            }.exceptionOrNull()

            assertTrue(failure is IllegalArgumentException)
            assertEquals("keep", marker.readText())
        } finally {
            server.shutdown()
        }
    }

    private fun ok(body: String): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(body)
}
