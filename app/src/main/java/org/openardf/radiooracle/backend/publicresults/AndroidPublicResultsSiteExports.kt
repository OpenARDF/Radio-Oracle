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

package org.openardf.radiooracle.backend.publicresults

import org.openardf.radiooracle.shared.publicresults.PublicResultsRaceRenderRequest
import org.openardf.radiooracle.shared.publicresults.PublicResultsSiteCatalog
import org.openardf.radiooracle.shared.publicresults.PublicResultsSiteRenderer
import org.openardf.radiooracle.shared.publicresults.RenderedPublicResultsRace
import java.io.File

data class AndroidPublicResultsExport(
    val directory: File,
    val eventPath: String
)

/** Android internal-files adapter for the shared static-site renderer. */
object AndroidPublicResultsSiteExports {
    fun exportRace(
        directory: File,
        race: PublicResultsRaceRenderRequest,
        generatedAtIso: String,
        appVersion: String
    ): AndroidPublicResultsExport {
        directory.mkdirs()
        val rendered = PublicResultsSiteRenderer.renderRace(
            request = race,
            generatedAtIso = generatedAtIso,
            appVersion = appVersion
        )
        val existing = readCatalog(directory)
        val merge = PublicResultsSiteCatalog.merge(
            existing = existing,
            current = rendered.catalogEntry(generatedAtIso)
        )
        merge.replacedPaths
            .filterNot { it == rendered.path }
            .forEach { AndroidPublicResultsSiteMirror.deleteGeneratedSiteDirectory(directory, it) }
        writeRace(directory, rendered)
        writeRoot(directory, merge.entries)
        return AndroidPublicResultsExport(directory, rendered.path)
    }

    fun exportSeries(
        directory: File,
        seriesId: String,
        seriesName: String,
        races: List<PublicResultsRaceRenderRequest>,
        generatedAtIso: String,
        appVersion: String
    ): AndroidPublicResultsExport {
        require(races.isNotEmpty()) {
            "The Race Series does not contain any races to publish."
        }
        directory.mkdirs()
        val renderedRaces = races.map { race ->
            PublicResultsSiteRenderer.renderRace(
                request = race,
                generatedAtIso = generatedAtIso,
                appVersion = appVersion
            )
        }
        var catalog = readCatalog(directory)
        val memberPublicationIds = renderedRaces.mapTo(mutableSetOf()) { it.publicationId }
        val memberPaths = renderedRaces.mapTo(mutableSetOf()) { it.path }
        val replacedMembers = catalog.filter {
            it.publicationId in memberPublicationIds || it.path in memberPaths
        }
        catalog = catalog - replacedMembers.toSet()
        replacedMembers
            .map { it.path }
            .filterNot(memberPaths::contains)
            .forEach { AndroidPublicResultsSiteMirror.deleteGeneratedSiteDirectory(directory, it) }
        renderedRaces.forEach { writeRace(directory, it) }

        val renderedSeries = PublicResultsSiteRenderer.renderSeries(
            seriesId = seriesId,
            seriesName = seriesName,
            races = renderedRaces,
            generatedAtIso = generatedAtIso
        )
        val merge = PublicResultsSiteCatalog.merge(
            existing = catalog,
            current = renderedSeries.catalogEntry(generatedAtIso)
        )
        merge.replacedPaths
            .filterNot { it == renderedSeries.path }
            .forEach { AndroidPublicResultsSiteMirror.deleteGeneratedSiteDirectory(directory, it) }
        replaceGeneratedDirectory(directory, renderedSeries.path)
        writeFiles(directory.resolve(renderedSeries.path), renderedSeries.files)
        writeRoot(directory, merge.entries)
        return AndroidPublicResultsExport(directory, renderedSeries.path)
    }

    private fun writeRace(root: File, race: RenderedPublicResultsRace) {
        replaceGeneratedDirectory(root, race.path)
        writeFiles(root.resolve(race.path), race.files)
    }

    private fun replaceGeneratedDirectory(root: File, relativePath: String) {
        val target = root.resolve(relativePath)
        if (target.exists()) {
            AndroidPublicResultsSiteMirror.deleteGeneratedSiteDirectory(root, relativePath)
        }
        require(target.mkdirs()) {
            "Could not create public-results directory: $relativePath"
        }
    }

    private fun writeRoot(
        directory: File,
        entries: List<org.openardf.radiooracle.shared.publicresults.PublishedPublicResultsEntry>
    ) {
        writeFile(
            directory,
            "data/races.json",
            PublicResultsSiteCatalog.encode(entries).encodeToByteArray()
        )
        writeFile(
            directory,
            "index.html",
            PublicResultsSiteCatalog.rootIndexHtml(entries).encodeToByteArray()
        )
        writeFile(
            directory,
            "_headers",
            PublicResultsSiteRenderer.headersText().encodeToByteArray()
        )
    }

    private fun readCatalog(directory: File) =
        directory.resolve("data/races.json")
            .takeIf(File::isFile)
            ?.readText()
            .let(PublicResultsSiteCatalog::parse)

    private fun writeFiles(directory: File, files: Map<String, ByteArray>) {
        files.forEach { (path, bytes) -> writeFile(directory, path, bytes) }
    }

    private fun writeFile(root: File, relativePath: String, bytes: ByteArray) {
        val target = root.resolve(relativePath).canonicalFile
        require(target.path.startsWith(root.canonicalPath + File.separator)) {
            "Unsafe public-results path: $relativePath"
        }
        target.parentFile?.mkdirs()
        target.writeBytes(bytes)
    }
}
