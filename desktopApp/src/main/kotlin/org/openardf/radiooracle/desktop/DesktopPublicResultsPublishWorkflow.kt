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

package org.openardf.radiooracle.desktop

import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.PublicResultsPublication
import org.openardf.radiooracle.shared.publicresults.isCloudflarePagesSettingsRejection

internal data class DesktopPublicResultsSiteUiState(
    val directory: Path? = null,
    val eventPath: String? = null,
    val previewServer: DesktopPublicResultSitePreviewServer? = null,
    val previewUrl: String? = null,
    val publishedUrl: String? = null,
    val publishing: Boolean = false
)

internal data class DesktopPreparedPublicResultsPublish(
    val staging: DesktopPublicResultsSiteMirror.StagedPublish,
    val paths: DesktopPublicResultSiteExportPaths
)

internal data class DesktopCompletedPublicResultsPublish(
    val result: DesktopCloudflarePagesPublishResult,
    val mirrorDirectory: Path,
    val eventPath: String
)

internal data class DesktopPublicResultsPublishOutcome(
    val completed: DesktopCompletedPublicResultsPublish,
    val publicUrl: String,
    val publication: PublicResultsPublication,
    val updatedProject: EventProjectFile?,
    val persistenceError: Throwable?
) {
    val statusText: String
        get() = if (persistenceError == null) {
            "Published public results site to $publicUrl and saved its link."
        } else {
            "Published public results site to $publicUrl, but its link could not be saved: " +
                (persistenceError.message ?: persistenceError::class.simpleName)
        }
}

internal object DesktopPublicResultsRequestedAction {
    private var publishAfterUnlock = false

    fun rememberPublish(publish: Boolean) {
        publishAfterUnlock = publish
    }

    fun consumePublish(): Boolean = publishAfterUnlock.also { publishAfterUnlock = false }

    fun continueRequestedAction(generate: () -> Unit, publish: () -> Unit) {
        if (consumePublish()) publish() else generate()
    }
}

internal fun cloudflarePagesSettingsRejectionReason(error: Throwable): String? {
    if (!error.isCloudflarePagesSettingsRejection()) {
        return null
    }
    return "Cloudflare rejected the saved account, project, or API token. Update and save Cloudflare Settings."
}

internal fun cloudflarePagesSettingsDisabledReason(
    settings: DesktopCloudflarePagesPublishSettings,
    rejectionReason: String?
): String? =
    when {
        !settings.isComplete() -> "Save complete Cloudflare Settings before using public-results website actions."
        rejectionReason != null -> rejectionReason
        else -> null
    }

/** One-operation generation, upload, and managed-mirror promotion for desktop publishing. */
internal object DesktopPublicResultsPublishWorkflow {
    fun launch(
        scope: CoroutineScope,
        settings: DesktopCloudflarePagesPublishSettings,
        currentProject: EventProjectFile?,
        alreadyPublishing: Boolean,
        publisher: DesktopCloudflarePagesPublisher,
        manifestPath: Path?,
        projectSession: DesktopProjectSession,
        exportSite: (Path, EventProjectFile) -> DesktopPublicResultSiteExportPaths,
        updatePublishing: (Boolean) -> Unit,
        updateStatus: (String) -> Unit,
        onPublishFailed: (Throwable) -> Unit = {},
        onPublished: (DesktopPublicResultsPublishOutcome) -> Unit
    ) {
        if (currentProject == null) return
        if (!settings.isComplete()) {
            updateStatus("Save complete Cloudflare Settings before publishing.")
            return
        }
        if (alreadyPublishing) {
            updateStatus("Public results site publishing is already in progress.")
            return
        }
        updatePublishing(true)
        updateStatus("Generating and publishing the updated results website...")
        DesktopDebugLog.info("PublicResults", "Generating and publishing the updated results website...")
        scope.launch {
            try {
                runCatching {
                    execute(
                        settings = settings,
                        currentProject = currentProject,
                        publisher = publisher,
                        manifestPath = manifestPath,
                        projectSession = projectSession,
                        exportSite = exportSite
                    )
                }.onSuccess { outcome ->
                    onPublished(outcome)
                    updateStatus(outcome.statusText)
                    DesktopDebugLog.info("PublicResults", outcome.statusText)
                }.onFailure { error ->
                    onPublishFailed(error)
                    val status =
                        "Public results website publish failed; the previous site mirror was preserved: " +
                            (error.message ?: error::class.simpleName)
                    updateStatus(status)
                    DesktopDebugLog.error("PublicResults", status)
                }
            } finally {
                updatePublishing(false)
            }
        }
    }

    suspend fun execute(
        settings: DesktopCloudflarePagesPublishSettings,
        currentProject: EventProjectFile,
        publisher: DesktopCloudflarePagesPublisher,
        manifestPath: Path?,
        projectSession: DesktopProjectSession,
        exportSite: (Path, EventProjectFile) -> DesktopPublicResultSiteExportPaths
    ): DesktopPublicResultsPublishOutcome {
        val prepared = prepare(settings, currentProject, exportSite)
        val completed = withContext(Dispatchers.IO) {
            publish(prepared, settings, publisher)
        }
        val publicUrl = DesktopCloudflarePagesPublisher.publicResultsUrl(
            completed.result.url,
            completed.eventPath
        )
        val publication = DesktopPublicResultsPublicationSelection.publication(
            url = publicUrl,
            publishedAtIso = java.time.Instant.now().toString()
        )
        var updatedProject: EventProjectFile? = null
        val persistenceError = runCatching {
            updatedProject = persistPublicResultsPublication(
                manifestPath = manifestPath,
                projectSession = projectSession,
                publication = publication
            )
        }.exceptionOrNull()
        return DesktopPublicResultsPublishOutcome(
            completed = completed,
            publicUrl = publicUrl,
            publication = publication,
            updatedProject = updatedProject,
            persistenceError = persistenceError
        )
    }

    fun prepare(
        settings: DesktopCloudflarePagesPublishSettings,
        currentProject: EventProjectFile,
        exportSite: (Path, EventProjectFile) -> DesktopPublicResultSiteExportPaths
    ): DesktopPreparedPublicResultsPublish {
        val staging = DesktopPublicResultsSiteMirror.stageForPublish(settings)
        return try {
            DesktopPreparedPublicResultsPublish(
                staging = staging,
                paths = exportSite(staging.stagingDirectory, currentProject)
            )
        } catch (error: Throwable) {
            staging.discard()
            throw error
        }
    }

    fun publish(
        prepared: DesktopPreparedPublicResultsPublish,
        settings: DesktopCloudflarePagesPublishSettings,
        publisher: DesktopCloudflarePagesPublisher
    ): DesktopCompletedPublicResultsPublish = try {
        val result = publisher.publish(settings.request(prepared.staging.stagingDirectory))
        DesktopCompletedPublicResultsPublish(
            result = result,
            mirrorDirectory = prepared.staging.promote(),
            eventPath = prepared.paths.eventPath
        )
    } catch (error: Throwable) {
        prepared.staging.discard()
        throw error
    }
}
