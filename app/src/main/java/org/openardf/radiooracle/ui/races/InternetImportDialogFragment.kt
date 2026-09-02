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

package org.openardf.radiooracle.ui.races

import android.content.Context
import android.content.res.Resources
import android.graphics.Rect
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResultListener
import androidx.navigation.fragment.findNavController
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.sounds.SoundProcessor
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.room.entity.embeddeds.RaceData
import org.openardf.radiooracle.ui.serializableCompat
import kotlin.getValue

class InternetImportDialogFragment : DialogFragment() {
    private val dataProcessor = DataProcessor.get()
    private lateinit var raceViewModel: RaceViewModel

    private lateinit var typePicker: MaterialAutoCompleteTextView
    private lateinit var apiKeyEditText: TextInputEditText
    private lateinit var errorTextView: TextView
    private lateinit var okButton: Button
    private lateinit var cancelButton: Button

    private var raceData: RaceData? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_internet_import, container, false)
    }

    private fun DialogFragment.setWidthPercent(percentage: Int) {
        val percent = percentage.toFloat() / 100
        val dm = Resources.getSystem().displayMetrics
        val rect = dm.run { Rect(0, 0, widthPixels, heightPixels) }
        val percentWidth = rect.width() * percent
        dialog?.window?.setLayout(percentWidth.toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.add_dialog)
        setWidthPercent(95)

        val sl: RaceViewModel by activityViewModels()
        raceViewModel = sl

        dialog?.setTitle(R.string.race_import_internet)

        typePicker = view.findViewById(R.id.internet_import_dialog_type)
        apiKeyEditText = view.findViewById(R.id.internet_import_dialog_apikey)
        errorTextView = view.findViewById(R.id.internet_import_dialog_error)
        cancelButton = view.findViewById(R.id.internet_import_dialog_cancel)
        okButton = view.findViewById(R.id.robis_import_dialog_ok)

        setButtons()
        setFragmentListener()
    }

    private fun setFragmentListener() {
        setFragmentResultListener(RaceEditDialogFragment.REQUEST_RACE_MODIFICATION) { _, bundle ->
            val action: RaceEditDialogFragment.RaceEditActions =
                bundle.serializableCompat(RaceEditDialogFragment.BUNDLE_KEY_ACTIONS)!!
            val race: Race = bundle.serializableCompat(RaceEditDialogFragment.BUNDLE_KEY_RACE)!!
            if (action == RaceEditDialogFragment.RaceEditActions.IMPORT) {
                raceData?.let { raceData ->
                    raceData.race = race
                    raceViewModel.saveRaceData(raceData)
                    dialog?.dismiss()
                }
            }
        }
    }

    private fun isNetworkConnected(): Boolean {
        val cm =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        // Require both that the network advertises internet and that the system validated it.
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun setButtons() {
        typePicker.setText(requireContext().getString(R.string.provider_type_robis))

        okButton.setOnClickListener {

            val apiKey = apiKeyEditText.text.toString()
            if (apiKey.isNotBlank()) {
                if (isNetworkConnected()) {
                    val providerType =
                        dataProcessor.providerTypeFromString(typePicker.text.toString())

                    try {
                        raceData =
                            raceViewModel.fetchProviderRaceData(
                                providerType,
                                apiKey,
                                requireContext()
                            )
                        raceData?.race?.apiKey = apiKey         // Preset API key, because its not returned in response
                        findNavController().navigate(
                            InternetImportDialogFragmentDirections.importInternetRace(
                                RaceEditDialogFragment.RaceEditActions.IMPORT, -1, raceData!!.race
                            )
                        )

                    } catch (e: Exception) {
                        SoundProcessor.makeErrorSound(requireContext())
                        errorTextView.text = e.message
                    }

                } else {
                    SoundProcessor.makeErrorSound(requireContext())
                    errorTextView.text = getString(R.string.result_service_status_no_network)
                }
            } else {
                apiKeyEditText.error = getString(R.string.general_required)
                SoundProcessor.makeErrorSound(requireContext())
            }
        }

        cancelButton.setOnClickListener {
            dialog?.cancel()
        }
    }
}
