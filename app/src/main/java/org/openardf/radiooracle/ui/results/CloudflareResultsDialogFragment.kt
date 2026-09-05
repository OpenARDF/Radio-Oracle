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

package org.openardf.radiooracle.ui.results

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.sounds.SoundProcessor
import org.openardf.radiooracle.backend.publicresults.AndroidCloudflarePagesPublishSettings
import org.openardf.radiooracle.backend.publicresults.AndroidCloudflarePagesSettingsStore
import org.openardf.radiooracle.backend.publicresults.AndroidPublicResultsPublishOutcome
import org.openardf.radiooracle.backend.publicresults.AndroidPublicResultsPublishingService
import org.openardf.radiooracle.backend.publicresults.AndroidPublicResultsRetentionMode
import org.openardf.radiooracle.backend.publicresults.AndroidPublicResultsTarget
import org.openardf.radiooracle.shared.event.PublicResultsPublicationStatus
import org.openardf.radiooracle.shared.publicresults.isCloudflarePagesSettingsRejection
import org.openardf.radiooracle.ui.SelectedRaceViewModel

internal data class CloudflareResultsActionAvailability(
    val publishEnabled: Boolean,
    val viewEnabled: Boolean
)

internal fun cloudflareResultsActionAvailability(
    hasTarget: Boolean,
    hasPublishedUrl: Boolean,
    settingsComplete: Boolean,
    settingsRejected: Boolean,
    publishing: Boolean
): CloudflareResultsActionAvailability {
    val websiteActionsEnabled =
        hasTarget && settingsComplete && !settingsRejected && !publishing
    return CloudflareResultsActionAvailability(
        publishEnabled = websiteActionsEnabled,
        viewEnabled = websiteActionsEnabled && hasPublishedUrl
    )
}

class CloudflareResultsDialogFragment : DialogFragment() {
    private val selectedRaceViewModel: SelectedRaceViewModel by activityViewModels()
    private lateinit var service: AndroidPublicResultsPublishingService
    private var target: AndroidPublicResultsTarget? = null

    private lateinit var targetView: TextView
    private lateinit var modeView: TextView
    private lateinit var statusView: TextView
    private lateinit var diagramControls: View
    private lateinit var includeDiagrams: SwitchMaterial
    private lateinit var officialResults: SwitchMaterial
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var passwordInput: TextInputEditText
    private lateinit var publishButton: Button
    private lateinit var urlView: TextView
    private lateinit var qrView: ImageView
    private lateinit var viewButton: Button
    private lateinit var settingsButton: Button
    private lateinit var closeButton: Button
    private lateinit var progress: ProgressBar
    private var publishing = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.dialog_cloudflare_results, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        service = AndroidPublicResultsPublishingService(requireContext())
        dialog?.setTitle(R.string.cloudflare_results_title)
        targetView = view.findViewById(R.id.cloudflare_target)
        modeView = view.findViewById(R.id.cloudflare_mode)
        statusView = view.findViewById(R.id.cloudflare_status)
        diagramControls = view.findViewById(R.id.cloudflare_diagram_controls)
        includeDiagrams = view.findViewById(R.id.cloudflare_include_diagrams)
        officialResults = view.findViewById(R.id.cloudflare_official_results)
        passwordLayout = view.findViewById(R.id.cloudflare_password_layout)
        passwordInput = view.findViewById(R.id.cloudflare_password)
        publishButton = view.findViewById(R.id.cloudflare_publish)
        urlView = view.findViewById(R.id.cloudflare_url)
        qrView = view.findViewById(R.id.cloudflare_qr)
        viewButton = view.findViewById(R.id.cloudflare_view)
        settingsButton = view.findViewById(R.id.cloudflare_settings)
        closeButton = view.findViewById(R.id.cloudflare_close)
        progress = view.findViewById(R.id.cloudflare_progress)

        includeDiagrams.setOnCheckedChangeListener { _, checked ->
            passwordLayout.visibility = if (
                checked && target?.needsRacePasswordForDiagrams == true
            ) View.VISIBLE else View.GONE
        }
        publishButton.setOnClickListener { confirmAndPublish() }
        viewButton.setOnClickListener { openSavedUrl() }
        settingsButton.setOnClickListener {
            findNavController().navigate(
                CloudflareResultsDialogFragmentDirections.openCloudflareSettings()
            )
        }
        closeButton.setOnClickListener { dismiss() }
        updateActionAvailability()
        loadTarget()
    }

    override fun onResume() {
        super.onResume()
        if (target != null) render()
    }

    private fun loadTarget() {
        val race = selectedRaceViewModel.getCurrentRace()
        if (race == null) {
            statusView.text = getString(R.string.cloudflare_results_not_published)
            updateActionAvailability()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val settings = AndroidCloudflarePagesSettingsStore.read(requireContext())
            runCatching {
                withContext(Dispatchers.IO) {
                    service.target(race.id, settings.publicSiteBaseUrl())
                }
            }.onSuccess {
                target = it
                render()
            }.onFailure {
                SoundProcessor.makeErrorSound(requireContext())
                statusView.text = getString(
                    R.string.cloudflare_results_publish_failed,
                    it.message ?: it::class.simpleName
                )
                updateActionAvailability()
            }
        }
    }

    private fun render() {
        val value = target ?: return
        val settings = AndroidCloudflarePagesSettingsStore.read(requireContext())
        targetView.text = if (value.isSeries) {
            getString(R.string.cloudflare_results_target_series, value.name, value.raceCount)
        } else {
            getString(R.string.cloudflare_results_target_race, value.name)
        }
        modeView.text = if (
            settings.retentionMode == AndroidPublicResultsRetentionMode.RETAIN_PREVIOUS
        ) {
            getString(R.string.cloudflare_results_retention_retain)
        } else {
            getString(R.string.cloudflare_results_retention_replace)
        }
        diagramControls.visibility =
            if (value.hasCourseDiagrams) View.VISIBLE else View.GONE
        passwordLayout.visibility = if (
            includeDiagrams.isChecked && value.needsRacePasswordForDiagrams
        ) View.VISIBLE else View.GONE
        renderUrl(value.savedUrl)
        val settingsRejected = AndroidCloudflarePagesSettingsStore.isRejected(
            requireContext(),
            settings
        )
        val settingsMessage = when {
            !settings.isComplete() -> getString(R.string.cloudflare_results_settings_incomplete)
            settingsRejected -> getString(R.string.cloudflare_results_settings_rejected)
            else -> null
        }
        if (settingsMessage != null) {
            statusView.text = settingsMessage
        } else if (
            statusView.text == getString(R.string.cloudflare_results_settings_incomplete) ||
            statusView.text == getString(R.string.cloudflare_results_settings_rejected)
        ) {
            statusView.text = ""
        }
        updateActionAvailability(settings, settingsRejected)
    }

    private fun renderUrl(url: String?) {
        if (url.isNullOrBlank()) {
            urlView.text = getString(R.string.cloudflare_results_not_published)
            qrView.visibility = View.GONE
            return
        }
        urlView.text = url
        qrView.setImageBitmap(qrCode(url))
        qrView.visibility = View.VISIBLE
    }

    private fun confirmAndPublish() {
        val settings = AndroidCloudflarePagesSettingsStore.read(requireContext())
        if (!settings.isComplete()) {
            statusView.text = getString(R.string.cloudflare_results_settings_incomplete)
            return
        }
        if (AndroidCloudflarePagesSettingsStore.isRejected(requireContext(), settings)) {
            statusView.text = getString(R.string.cloudflare_results_settings_rejected)
            updateActionAvailability(settings, settingsRejected = true)
            return
        }
        if (settings.retentionMode == AndroidPublicResultsRetentionMode.REPLACE_PREVIOUS) {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.cloudflare_results_replace_title)
                .setMessage(R.string.cloudflare_results_replace_message)
                .setPositiveButton(R.string.cloudflare_results_replace_confirm) { _, _ ->
                    publish()
                }
                .setNegativeButton(R.string.general_cancel, null)
                .show()
        } else {
            publish()
        }
    }

    private fun publish() {
        val race = selectedRaceViewModel.getCurrentRace() ?: return
        val settings = AndroidCloudflarePagesSettingsStore.read(requireContext())
        setPublishing(true)
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    service.publish(
                        raceId = race.id,
                        settings = settings,
                        includeCourseDiagrams =
                            target?.hasCourseDiagrams == true && includeDiagrams.isChecked,
                        racePassword = passwordInput.text?.toString(),
                        publicationStatus = if (officialResults.isChecked) {
                            PublicResultsPublicationStatus.OFFICIAL
                        } else {
                            PublicResultsPublicationStatus.PRELIMINARY
                        }
                    )
                }
            }.onSuccess(::showPublished)
                .onFailure {
                    SoundProcessor.makeErrorSound(requireContext())
                    if (it.isCloudflarePagesSettingsRejection()) {
                        AndroidCloudflarePagesSettingsStore.recordRejection(
                            requireContext(),
                            settings
                        )
                        statusView.text = getString(R.string.cloudflare_results_settings_rejected)
                    } else {
                        statusView.text = getString(
                            R.string.cloudflare_results_publish_failed,
                            it.message ?: it::class.simpleName
                        )
                    }
                }
            setPublishing(false)
        }
    }

    private fun showPublished(outcome: AndroidPublicResultsPublishOutcome) {
        target = target?.copy(
            savedUrl = outcome.url,
            publishedAtIso = outcome.publishedAtIso
        )
        renderUrl(outcome.url)
        statusView.text = outcome.persistenceWarning?.let {
            getString(R.string.cloudflare_results_publish_warning, it)
        } ?: getString(R.string.cloudflare_results_publish_success, outcome.name)
    }

    private fun setPublishing(publishing: Boolean) {
        this.publishing = publishing
        progress.visibility = if (publishing) View.VISIBLE else View.GONE
        publishButton.text = getString(
            if (publishing) R.string.cloudflare_results_publishing
            else R.string.cloudflare_results_publish
        )
        settingsButton.isEnabled = !publishing
        closeButton.isEnabled = !publishing
        includeDiagrams.isEnabled = !publishing
        officialResults.isEnabled = !publishing
        passwordInput.isEnabled = !publishing
        updateActionAvailability()
    }

    private fun updateActionAvailability(
        settings: AndroidCloudflarePagesPublishSettings =
            AndroidCloudflarePagesSettingsStore.read(requireContext()),
        settingsRejected: Boolean = AndroidCloudflarePagesSettingsStore.isRejected(
            requireContext(),
            settings
        )
    ) {
        val availability = cloudflareResultsActionAvailability(
            hasTarget = target != null,
            hasPublishedUrl = target?.canViewPublicResults == true,
            settingsComplete = settings.isComplete(),
            settingsRejected = settingsRejected,
            publishing = publishing
        )
        publishButton.isEnabled = availability.publishEnabled
        viewButton.isEnabled = availability.viewEnabled
    }

    private fun openSavedUrl() {
        val url = target?.savedUrl?.takeIf(String::isNotBlank) ?: return
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun qrCode(value: String): Bitmap {
        val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 720, 720)
        val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                bitmap.setPixel(x, y, if (matrix[x, y]) 0xff000000.toInt() else 0xffffffff.toInt())
            }
        }
        return bitmap
    }
}
