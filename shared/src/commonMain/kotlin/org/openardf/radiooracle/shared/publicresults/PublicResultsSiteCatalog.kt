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

package org.openardf.radiooracle.shared.publicresults

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.openardf.radiooracle.shared.event.PublicResultsPublicationStatus

@Serializable
data class PublishedPublicResultsEntry(
    val path: String,
    val name: String,
    val start: String,
    val generatedAt: String,
    val resultCount: Int,
    val unofficialResults: Boolean,
    val publicationStatus: PublicResultsPublicationStatus? = null,
    val publicationId: String? = null
) {
    fun effectivePublicationLabel(): String =
        publicationStatus?.displayLabel ?: PublicResultsSiteCatalog.publicResultsLabel(unofficialResults)
}

@Serializable
private data class PublishedPublicResultsCatalog(
    val races: List<PublishedPublicResultsEntry>
)

data class PublicResultsCatalogMerge(
    val entries: List<PublishedPublicResultsEntry>,
    val replacedPaths: Set<String>
)

/**
 * Shared naming, retention, catalog, and landing-page rules for desktop and Android.
 */
object PublicResultsSiteCatalog {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun parse(text: String?): List<PublishedPublicResultsEntry> {
        if (text.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<PublishedPublicResultsCatalog>(text).races
                .filter(::isSafeEntry)
        }.getOrDefault(emptyList())
    }

    fun parseStrict(text: String): List<PublishedPublicResultsEntry>? =
        runCatching {
            json.decodeFromString<PublishedPublicResultsCatalog>(text).races
                .takeIf { entries -> entries.all(::isSafeEntry) }
        }.getOrNull()

    fun encode(entries: List<PublishedPublicResultsEntry>): String =
        json.encodeToString(PublishedPublicResultsCatalog(entries)) + "\n"

    fun merge(
        existing: List<PublishedPublicResultsEntry>,
        current: PublishedPublicResultsEntry
    ): PublicResultsCatalogMerge {
        require(isSafeEntry(current)) {
            "Public results entry contains an unsafe event path: ${current.path}"
        }
        val replaced = existing.filter { event ->
            event.path == current.path ||
                (
                    current.publicationId != null &&
                        event.publicationId == current.publicationId
                    )
        }
        val entries = (existing - replaced.toSet() + current)
            .sortedWith(compareByDescending<PublishedPublicResultsEntry> { it.start }.thenBy { it.name })
        return PublicResultsCatalogMerge(
            entries = entries,
            replacedPaths = replaced.map(PublishedPublicResultsEntry::path).toSet()
        )
    }

    fun rootIndexHtml(entries: List<PublishedPublicResultsEntry>): String {
        val eventLinks = if (entries.isEmpty()) {
            """<p class="empty-state">No public result races have been generated yet.</p>"""
        } else {
            entries.joinToString(separator = "\n") { event ->
                val publicationSummary = if (event.resultCount == 0) {
                    "${event.effectivePublicationLabel()} Coming Soon | Scheduled ${html(event.start)}"
                } else {
                    "${event.resultCount} ${event.effectivePublicationLabel().lowercase()} | " +
                        "Published ${html(event.generatedAt)}"
                }
                """
                <a class="event-link" href="${html(event.path)}/">
                  <span>
                    <strong>${html(event.name)}</strong>
                    <small>$publicationSummary</small>
                  </span>
                </a>
                """.trimIndent()
            }
        }
        return """
        <!doctype html>
        <html lang="en">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>OpenARDF Results</title>
          <style>
            :root{--bg:#f4f6f8;--panel:#fff;--text:#111827;--muted:#5f6b7a;--line:#d8dee8;--accent:#1769aa}
            *{box-sizing:border-box}
            body{margin:0;background:var(--bg);color:var(--text);font-family:Inter,ui-sans-serif,system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif}
            header{padding:36px min(6vw,72px) 28px;background:#fff;border-bottom:1px solid var(--line)}
            main{display:grid;gap:12px;width:min(900px,calc(100% - 32px));margin:24px auto 40px}
            h1{margin:0 0 8px;font-size:34px;line-height:1.1}.summary,.empty-state,small{color:var(--muted)}
            .event-link{display:flex;align-items:center;min-height:72px;padding:16px;border:1px solid var(--line);border-radius:8px;background:var(--panel);color:var(--text);text-decoration:none}
            .event-link:hover{border-color:var(--accent)}.event-link strong,.event-link small{display:block}.event-link small{margin-top:4px}
          </style>
        </head>
        <body>
          <header>
            <h1>OpenARDF Results</h1>
            <p class="summary">Published Radio-Oracle race results.</p>
          </header>
          <main>
            $eventLinks
          </main>
        </body>
        </html>
        """.trimIndent() + "\n"
    }

    fun eventPath(eventName: String, startDateTimeIso: String, generatedDate: String): String {
        val date = startDateTimeIso.take(10)
            .takeIf { it.matches(Regex("""\d{4}-\d{2}-\d{2}""")) }
            ?: generatedDate.take(10)
        return "$date-${safePathSegment(eventName, "event")}"
    }

    fun seriesPath(seriesName: String, firstStartDateTimeIso: String, generatedDate: String): String {
        val date = firstStartDateTimeIso.take(10)
            .takeIf { it.matches(Regex("""\d{4}-\d{2}-\d{2}""")) }
            ?: generatedDate.take(10)
        return "$date-${safePathSegment(seriesName, "series")}-series"
    }

    fun publicResultsLabel(unofficialResults: Boolean): String =
        if (unofficialResults) {
            "Unofficial Results"
        } else {
            "Results"
        }

    fun comingSoonResultsLabel(unofficialResults: Boolean): String =
        "${publicResultsLabel(unofficialResults)} Coming Soon"

    fun safePathSegment(value: String, fallback: String): String =
        value.lowercase()
            .map { if (it.isLetterOrDigit()) it else '-' }
            .joinToString("")
            .replace(Regex("-+"), "-")
            .trim('-')
            .ifBlank { fallback }
            .take(64)
            .trim('-')

    private fun isSafeEntry(entry: PublishedPublicResultsEntry): Boolean {
        val path = entry.path.trim()
        return path.isNotEmpty() &&
            '/' !in path &&
            '\\' !in path &&
            path != "." &&
            path != ".."
    }

    private fun html(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
}
