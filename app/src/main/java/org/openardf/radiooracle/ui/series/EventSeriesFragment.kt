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

package org.openardf.radiooracle.ui.series

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.shared.event.EVENT_SERIES_PACKAGE_CONTENT_TYPE
import org.openardf.radiooracle.shared.event.EventFileTransferPayloads
import org.openardf.radiooracle.ui.SelectedRaceViewModel
import org.openardf.radiooracle.ui.transfer.DesktopFileTransferUploadDialogs

class EventSeriesFragment : Fragment() {
    private val viewModel: EventSeriesViewModel by viewModels()
    private val selectedRaceViewModel: SelectedRaceViewModel by activityViewModels()
    private lateinit var toolbar: Toolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private var pendingExportSeries: EventSeriesListItem? = null

    private val importSeriesResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            importEventSeriesPackage(uri)
        }
    }

    private val exportSeriesResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val item = pendingExportSeries
        pendingExportSeries = null
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            item ?: return@registerForActivityResult
            exportSeriesToUri(item, uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        inflater.inflate(R.layout.fragment_event_series, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar = view.findViewById(R.id.event_series_toolbar)
        recyclerView = view.findViewById(R.id.event_series_recycler_view)
        emptyView = view.findViewById(R.id.event_series_empty)

        toolbar.setTitle(R.string.event_series_toolbar_title)
        toolbar.setTitleTextColor(ContextCompat.getColor(requireContext(), R.color.black))
        toolbar.setNavigationIcon(R.drawable.ic_back)
        toolbar.setNavigationOnClickListener { closeSeriesPage() }
        toolbar.inflateMenu(R.menu.fragment_menu_event_series)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.event_series_menu_import -> {
                    chooseSeriesImportSource()
                    true
                }

                else -> false
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.series.collect { series ->
                    recyclerView.adapter = EventSeriesRecyclerViewAdapter(
                        series,
                        requireContext(),
                        ::chooseRaceToAdd,
                        ::showEditSeriesDialog,
                        ::prepareSeriesForDesktopUpload,
                        ::chooseSeriesExportDestination,
                        ::confirmRemoveSeriesGrouping,
                        ::openSeriesMember
                    )
                    emptyView.visibility = if (series.isEmpty()) View.VISIBLE else View.GONE
                    recyclerView.visibility = if (series.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            closeSeriesPage()
        }
    }

    private fun closeSeriesPage() {
        findNavController().navigateUp()
    }

    private fun openSeriesMember(member: EventSeriesMemberListItem) {
        selectedRaceViewModel.setRace(member.localRaceId)
        findNavController().navigate(EventSeriesFragmentDirections.openRaceFromSeries())
    }

    private fun chooseSeriesImportSource() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "*/*"
        importSeriesResult.launch(intent)
    }

    private fun importEventSeriesPackage(uri: Uri) {
        val progressDialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.event_series_import_title)
            .setMessage(R.string.event_series_import_progress)
            .setCancelable(false)
            .create()
        progressDialog.show()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val eventSeriesImport = withContext(Dispatchers.IO) {
                    viewModel.importAndSaveEventSeriesPackage(uri)
                        ?: throw IllegalStateException(getString(R.string.event_series_import_invalid))
                }
                progressDialog.dismiss()
                Toast.makeText(
                    requireContext(),
                    getString(
                        R.string.event_series_import_success,
                        eventSeriesImport.series.name,
                        eventSeriesImport.memberImports.size
                    ),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (error: Exception) {
                progressDialog.dismiss()
                displayAlert(error.message ?: getString(R.string.race_import_failure))
            }
        }
    }

    private fun chooseRaceToAdd(item: EventSeriesListItem) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val races = withContext(Dispatchers.IO) {
                    viewModel.availableRacesForSeries(item.seriesId)
                }
                if (races.isEmpty()) {
                    AlertDialog.Builder(requireContext())
                        .setTitle(R.string.event_series_add_race)
                        .setMessage(R.string.event_series_add_race_empty)
                        .setPositiveButton(R.string.general_ok, null)
                        .show()
                    return@launch
                }

                val raceLabels = races.map(::raceAddLabel).toTypedArray()
                AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.event_series_add_race_title, item.name))
                    .setItems(raceLabels) { _, which ->
                        addRaceToSeries(item, races[which])
                    }
                    .setNegativeButton(R.string.general_cancel, null)
                    .show()
            } catch (error: Exception) {
                displayAlert(error.message ?: "Could not load races.")
            }
        }
    }

    private fun raceAddLabel(race: Race): String =
        "${race.startDateTime.toLocalDate()} - ${race.name}"

    private fun addRaceToSeries(item: EventSeriesListItem, race: Race) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    viewModel.addRaceToSeries(item.seriesId, race.id)
                }
                Toast.makeText(
                    requireContext(),
                    getString(R.string.event_series_add_to_existing_success, item.name),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (error: Exception) {
                displayAlert(error.message ?: "Could not add race to Race Series.")
            }
        }
    }

    private fun showEditSeriesDialog(item: EventSeriesListItem) {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            hint = getString(R.string.event_series_name_hint)
            setSingleLine(true)
            setText(item.name)
            selectAll()
        }
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.event_series_edit)
            .setView(input)
            .setPositiveButton(R.string.general_save, null)
            .setNegativeButton(R.string.general_cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val seriesName = input.text.toString().trim()
                if (seriesName.isBlank()) {
                    input.error = getString(R.string.event_series_name_required)
                } else {
                    dialog.dismiss()
                    renameSeries(item, seriesName)
                }
            }
        }
        dialog.show()
    }

    private fun renameSeries(item: EventSeriesListItem, seriesName: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    viewModel.renameSeries(item.seriesId, seriesName)
                }
                Toast.makeText(
                    requireContext(),
                    getString(R.string.event_series_edit_success),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (error: Exception) {
                displayAlert(error.message ?: "Could not edit Race Series.")
            }
        }
    }

    private fun chooseSeriesExportDestination(item: EventSeriesListItem) {
        pendingExportSeries = item
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = EVENT_SERIES_PACKAGE_CONTENT_TYPE
        intent.putExtra(Intent.EXTRA_TITLE, EventFileTransferPayloads.seriesPackageFileName(item.name))
        exportSeriesResult.launch(intent)
    }

    private fun exportSeriesToUri(item: EventSeriesListItem, uri: Uri) {
        val progressDialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.event_series_export)
            .setMessage(R.string.event_series_export_progress)
            .setCancelable(false)
            .create()
        progressDialog.show()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    viewModel.exportEventSeriesPackage(uri, item.seriesId)
                }
                progressDialog.dismiss()
                Toast.makeText(
                    requireContext(),
                    getString(R.string.event_series_export_success),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (error: Exception) {
                progressDialog.dismiss()
                displayAlert(error.message ?: "Could not export Race Series.")
            }
        }
    }

    private fun prepareSeriesForDesktopUpload(item: EventSeriesListItem) {
        val progressDialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.event_file_send_title)
            .setMessage(R.string.event_file_send_progress)
            .setCancelable(false)
            .create()
        progressDialog.show()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val upload = withContext(Dispatchers.IO) {
                    viewModel.desktopUploadForSeries(item.seriesId)
                }
                progressDialog.dismiss()
                DesktopFileTransferUploadDialogs.show(this@EventSeriesFragment, upload)
            } catch (error: Exception) {
                progressDialog.dismiss()
                displayAlert(error.message ?: "Could not prepare Race Series for desktop upload.")
            }
        }
    }

    private fun confirmRemoveSeriesGrouping(item: EventSeriesListItem) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.event_series_remove_grouping)
            .setMessage(getString(R.string.event_series_remove_grouping_confirmation, item.name))
            .setNegativeButton(R.string.general_cancel, null)
            .setPositiveButton(R.string.event_series_remove_grouping) { _, _ ->
                removeSeriesGrouping(item)
            }
            .show()
    }

    private fun removeSeriesGrouping(item: EventSeriesListItem) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    viewModel.removeSeriesGrouping(item.seriesId)
                }
                Toast.makeText(
                    requireContext(),
                    getString(R.string.event_series_remove_grouping_success),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (error: Exception) {
                displayAlert(error.message ?: "Could not remove Race Series grouping.")
            }
        }
    }

    private fun displayAlert(message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.general_unknown_error)
            .setMessage(message)
            .setPositiveButton(R.string.general_ok, null)
            .show()
    }
}
