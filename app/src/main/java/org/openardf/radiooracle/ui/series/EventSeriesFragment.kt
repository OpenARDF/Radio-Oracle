package org.openardf.radiooracle.ui.series

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
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
import org.openardf.radiooracle.shared.event.EVENT_SERIES_PACKAGE_CONTENT_TYPE
import org.openardf.radiooracle.shared.event.EventFileTransferPayloads
import org.openardf.radiooracle.ui.transfer.DesktopFileTransferUploadDialogs

class EventSeriesFragment : Fragment() {
    private val viewModel: EventSeriesViewModel by viewModels()
    private lateinit var toolbar: Toolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private var pendingExportSeries: EventSeriesListItem? = null

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
        toolbar.setNavigationIcon(R.drawable.ic_back)
        toolbar.setNavigationOnClickListener { closeSeriesPage() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.series.collect { series ->
                    recyclerView.adapter = EventSeriesRecyclerViewAdapter(
                        series,
                        requireContext(),
                        ::prepareSeriesForDesktopUpload,
                        ::chooseSeriesExportDestination,
                        ::confirmRemoveSeriesGrouping
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
                displayAlert(error.message ?: "Could not export Event Series.")
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
                displayAlert(error.message ?: "Could not prepare Event Series for desktop upload.")
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
                displayAlert(error.message ?: "Could not remove Event Series grouping.")
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
