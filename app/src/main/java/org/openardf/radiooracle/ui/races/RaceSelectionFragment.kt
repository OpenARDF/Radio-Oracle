package org.openardf.radiooracle.ui.races

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.nambimobile.widgets.efab.FabOption
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.files.DesktopFileTransferUpload
import org.openardf.radiooracle.backend.files.EventFileTransferDownloader
import org.openardf.radiooracle.backend.files.EventFileTransferUploads
import org.openardf.radiooracle.backend.logging.DebugLog
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.room.entity.embeddeds.RaceData
import org.openardf.radiooracle.shared.event.EVENT_SERIES_PACKAGE_CONTENT_TYPE
import org.openardf.radiooracle.shared.event.EventFileTransferPayloads
import org.openardf.radiooracle.ui.SelectedRaceViewModel
import org.openardf.radiooracle.ui.serializableCompat
import org.openardf.radiooracle.ui.transfer.DesktopFileTransferUploadDialogs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID


/**
 * A fragment representing a list of Items.
 */
class RaceSelectionFragment : Fragment() {

    private lateinit var toolbar: Toolbar
    private lateinit var raceCreateOption: FabOption
    private lateinit var robisImportOption: FabOption
    private lateinit var fileImportOption: FabOption
    private lateinit var receiveDesktopOption: FabOption
    private lateinit var sendFileDesktopOption: FabOption
    private lateinit var recyclerView: RecyclerView
    private var selectedRaceId: UUID? = null
    private var exportData: Boolean = true

    private val raceViewModel: RaceViewModel by activityViewModels()
    private val selectedRaceViewModel: SelectedRaceViewModel by activityViewModels()
    private var raceData: RaceData? = null

    // Race export
    private val getResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            val value = it.data
            val uri = value?.data

            if (uri != null) {
                exportImportRaceData(uri)
            }
        }
    }

    private val getDesktopUploadFileResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            val uri = it.data?.data ?: return@registerForActivityResult
            preparePickedFileForDesktopUpload(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_race_selection, container, false)
        recyclerView = view.findViewById(R.id.race_recycler_view)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar = view.findViewById(R.id.race_toolbar)
        toolbar.setTitle(R.string.race_toolbar_title)
        val onToolbarColor = ContextCompat.getColor(requireContext(), R.color.white)
        toolbar.setTitleTextColor(onToolbarColor)
        toolbar.inflateMenu(R.menu.fragment_menu_race)
        toolbar.overflowIcon = toolbar.overflowIcon?.let { icon ->
            DrawableCompat.wrap(icon.mutate()).apply {
                DrawableCompat.setTint(this, onToolbarColor)
            }
        }
        tintMenuIcons(onToolbarColor)

        //FAB options
        raceCreateOption = view.findViewById(R.id.race_fab_create)
        robisImportOption = view.findViewById(R.id.race_fab_robis)
        fileImportOption = view.findViewById(R.id.race_fab_file)
        receiveDesktopOption = view.findViewById(R.id.race_fab_receive_desktop)
        sendFileDesktopOption = view.findViewById(R.id.race_fab_send_file_desktop)

        raceCreateOption.setOnClickListener {
            findNavController().navigate(
                RaceSelectionFragmentDirections.raceCreateOfModify(
                    RaceEditDialogFragment.RaceEditActions.CREATE,
                    -1,
                    null
                )
            )
        }

        fileImportOption.setOnClickListener {
            exportData = false
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "*/*"
            getResult.launch(intent)
        }
        robisImportOption.setOnClickListener {
            findNavController().navigate(RaceSelectionFragmentDirections.importRobis())
        }
        receiveDesktopOption.setOnClickListener {
            showReceiveFromDesktopDialog()
        }
        sendFileDesktopOption.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "*/*"
            getDesktopUploadFileResult.launch(intent)
        }

        setMenuListener()
        setRecyclerAdapter()
        setSwipeActions()
        setFragmentListener()
        setSeriesMenuVisibility()
        setBackButton()
    }

    private fun setMenuListener() {
        toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.race_menu_event_series -> {
                    findNavController().navigate(RaceSelectionFragmentDirections.openEventSeries())
                    true
                }

                R.id.race_menu_global_settings -> {
                    // Navigate to settings screen.
                    findNavController().navigate(RaceSelectionFragmentDirections.openSettings())
                    true
                }

                else -> false
            }
        }
    }

    private fun tintMenuIcons(color: Int) {
        for (index in 0 until toolbar.menu.size()) {
            val item = toolbar.menu.getItem(index)
            item.icon = item.icon?.let { icon ->
                DrawableCompat.wrap(icon.mutate()).apply {
                    DrawableCompat.setTint(this, color)
                }
            }
        }
    }

    private fun setSeriesMenuVisibility() {
        toolbar.menu.findItem(R.id.race_menu_event_series)?.isVisible = false
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                raceViewModel.showStoredSeriesActions.collect { showSeries ->
                    toolbar.menu.findItem(R.id.race_menu_event_series)?.isVisible = showSeries
                }
            }
        }
    }

    private fun setBackButton() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            val builder = AlertDialog.Builder(context)
            builder.setTitle(getString(R.string.general_exit_title))
            val message = getString(R.string.general_exit_confirmation)
            builder.setMessage(message)

            builder.setPositiveButton(R.string.general_ok) { _, _ ->
                requireActivity().finishAffinity();
            }

            builder.setNegativeButton(R.string.general_cancel) { dialog, _ ->
                dialog.cancel()
            }
            builder.show()
        }
    }

    private fun setFragmentListener() {
        setFragmentResultListener(RaceEditDialogFragment.REQUEST_RACE_MODIFICATION) { _, bundle ->
            val action: RaceEditDialogFragment.RaceEditActions =
                bundle.serializableCompat(RaceEditDialogFragment.BUNDLE_KEY_ACTIONS)!!

            val position = bundle.getInt(RaceEditDialogFragment.BUNDLE_KEY_POSITION)

            val race: Race = bundle.serializableCompat(RaceEditDialogFragment.BUNDLE_KEY_RACE)!!

            //create new race
            when (action) {
                RaceEditDialogFragment.RaceEditActions.CREATE -> {
                    raceViewModel.createRace(race)
                }
                //Edit an existing race
                RaceEditDialogFragment.RaceEditActions.EDIT -> {
                    raceViewModel.updateRace(race)
                    recyclerView.adapter?.notifyItemChanged(position)
                }

                else -> {
                    raceData?.let { raceData ->
                        raceData.race = race
                        raceViewModel.saveRaceData(raceData)
                    }
                }
            }
        }
    }

    private fun recyclerViewContextMenuActions(action: Int, position: Int, race: Race) {
        when (action) {
            0 -> findNavController().navigate(
                RaceSelectionFragmentDirections.raceCreateOfModify(
                    RaceEditDialogFragment.RaceEditActions.EDIT, position, race
                )
            )

            1 -> exportRace(race.id)
            2 -> prepareRaceForDesktopUpload(race)
            3 -> confirmRaceDeletion(race)
        }
    }

    private fun displayAlert(message: String, titleRes: Int = R.string.race_import_failure) {
        val alertDialog = AlertDialog.Builder(requireContext()).create()
        alertDialog.setTitle(getString(titleRes))
        alertDialog.setMessage(message)
        alertDialog.setButton(
            AlertDialog.BUTTON_POSITIVE, getString(R.string.general_ok)
        ) { dialog, which -> dialog.dismiss() }
        alertDialog.show()
    }

    private fun exportImportRaceData(uri: Uri) {
        if (exportData && selectedRaceId != null) {
            try {
                raceViewModel.exportRaceOrSeriesData(uri, selectedRaceId!!)
            } catch (error: Exception) {
                displayAlert(
                    error.message ?: getString(R.string.race_export_failure),
                    R.string.race_export_failure
                )
                return
            }

            // Inform user about successful export
            Toast.makeText(
                requireContext(),
                requireContext().getText(R.string.race_export_success),
                Toast.LENGTH_SHORT
            ).show()

        } else {
            if (isEventSeriesPackageUri(uri)) {
                importEventSeriesPackage(uri)
            } else {
                importSingleEventFile(uri)
            }
        }
    }

    private fun importSingleEventFile(uri: Uri) {
        try {
            raceData = raceViewModel.importRaceData(uri)
            findNavController().navigate(
                RaceSelectionFragmentDirections.raceCreateOfModify(
                    RaceEditDialogFragment.RaceEditActions.IMPORT, -1, raceData!!.race
                )
            )
        } catch (e: Exception) {
            displayAlert(e.message.toString())
        }
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
                    raceViewModel.importAndSaveEventSeriesPackage(uri)
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

    private fun isEventSeriesPackageUri(uri: Uri): Boolean {
        val type = requireContext().contentResolver.getType(uri).orEmpty()
        val name = displayNameForUri(uri)
        return EventFileTransferPayloads.isSeriesPackage(name, type)
    }

    private fun showReceiveFromDesktopDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.event_file_receive_title)
            .setMessage(R.string.event_file_receive_message)
            .setPositiveButton(R.string.event_file_receive_scan_qr) { _, _ ->
                scanDesktopTransferQr()
            }
            .setNeutralButton(R.string.event_file_receive_enter_url) { _, _ ->
                showManualDesktopTransferUrlDialog()
            }
            .setNegativeButton(R.string.general_cancel, null)
            .show()
    }

    private fun showManualDesktopTransferUrlDialog() {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            hint = getString(R.string.event_file_receive_url_hint)
            setSingleLine(true)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.event_file_receive_title)
            .setMessage(R.string.event_file_receive_message)
            .setView(input)
            .setPositiveButton(R.string.race_import) { _, _ ->
                importEventFileFromDesktopUrl(input.text.toString())
            }
            .setNegativeButton(R.string.general_cancel, null)
            .show()
    }

    private fun scanDesktopTransferQr() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        val scanner = GmsBarcodeScanning.getClient(requireActivity(), options)
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val url = barcode.rawValue
                if (url.isNullOrBlank()) {
                    displayAlert("The QR code did not contain a desktop transfer URL.")
                } else {
                    importEventFileFromDesktopUrl(url)
                }
            }
            .addOnFailureListener { error ->
                displayAlert(error.message ?: "QR scan failed. Enter the transfer URL manually.")
            }
    }

    private fun importEventFileFromDesktopUrl(rawUrl: String) {
        val progressDialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.event_file_receive_title)
            .setMessage(R.string.event_file_receive_progress)
            .setCancelable(false)
            .create()
        progressDialog.show()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val download = withContext(Dispatchers.IO) {
                    EventFileTransferDownloader().download(rawUrl)
                }
                if (download.isZip) {
                    val eventSeriesImport = withContext(Dispatchers.IO) {
                        raceViewModel.importAndSaveEventSeriesPackage(download.bytes)
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
                    return@launch
                }

                raceData = raceViewModel.importRaceData(download.text())
                    ?: throw IllegalStateException("Invalid Event File.")
                progressDialog.dismiss()
                findNavController().navigate(
                    RaceSelectionFragmentDirections.raceCreateOfModify(
                        RaceEditDialogFragment.RaceEditActions.IMPORT, -1, raceData!!.race
                    )
                )
                Toast.makeText(
                    requireContext(),
                    getString(R.string.event_file_receive_success, raceData!!.race.name),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (error: Exception) {
                progressDialog.dismiss()
                displayAlert(error.message ?: getString(R.string.race_import_failure))
            }
        }
    }

    private fun prepareRaceForDesktopUpload(race: Race) {
        val progressDialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.event_file_send_title)
            .setMessage(R.string.event_file_send_progress)
            .setCancelable(false)
            .create()
        progressDialog.show()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val upload = withContext(Dispatchers.IO) {
                    raceViewModel.desktopUploadForRaceOrSeries(race.id)
                }
                progressDialog.dismiss()
                DesktopFileTransferUploadDialogs.show(this@RaceSelectionFragment, upload)
            } catch (error: Exception) {
                progressDialog.dismiss()
                displayAlert(error.message ?: "Could not prepare Event File for desktop upload.")
            }
        }
    }

    private fun preparePickedFileForDesktopUpload(uri: Uri) {
        val progressDialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.event_file_send_title)
            .setMessage(R.string.event_file_send_progress)
            .setCancelable(false)
            .create()
        progressDialog.show()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val upload = withContext(Dispatchers.IO) {
                    desktopUploadFromUri(uri)
                }
                progressDialog.dismiss()
                DesktopFileTransferUploadDialogs.show(this@RaceSelectionFragment, upload)
            } catch (error: Exception) {
                progressDialog.dismiss()
                displayAlert(error.message ?: "Could not read the selected file.")
            }
        }
    }

    private fun desktopUploadFromUri(uri: Uri): DesktopFileTransferUpload {
        val resolver = requireContext().contentResolver
        val fileName = displayNameForUri(uri).ifBlank { "android-upload.bin" }
        val bytes = resolver.openInputStream(uri)?.use { input -> input.readBytes() }
            ?: throw IllegalStateException("Could not open the selected file.")
        return DesktopFileTransferUpload(
            fileName = fileName,
            contentType = resolver.getType(uri) ?: "application/octet-stream",
            bytes = bytes
        )
    }

    private fun displayNameForUri(uri: Uri): String =
        requireContext().contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                } else {
                    null
                }
            }
            ?: uri.lastPathSegment
            ?: ""

    /**
     * Displays alert dialog to confirm the deletion of the race
     */
    private fun confirmRaceDeletion(race: Race) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle(getString(R.string.race_delete))
        val message = getString(R.string.race_delete_confirmation) + " " + race.name
        builder.setMessage(message)

        builder.setPositiveButton(R.string.general_ok) { dialog, _ ->
            raceViewModel.deleteRace(race.id)
            dialog.dismiss()
        }

        builder.setNegativeButton(R.string.general_cancel) { dialog, _ ->
            dialog.cancel()
        }
        builder.show()
    }

    private fun confirmRaceDeletionFromSwipe(race: Race, position: Int) {
        confirmRaceDeletion(race)
        recyclerView.adapter?.notifyItemChanged(position)
    }

    private fun exportRace(raceId: UUID) {
        selectedRaceId = raceId
        exportData = true

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        val seriesName = raceViewModel.seriesNameForRace(raceId)
        if (seriesName == null) {
            intent.type = "text/json"
            intent.putExtra(Intent.EXTRA_TITLE, "race.ardfjs")
        } else {
            intent.type = EVENT_SERIES_PACKAGE_CONTENT_TYPE
            intent.putExtra(Intent.EXTRA_TITLE, EventFileTransferPayloads.seriesPackageFileName(seriesName))
        }
        getResult.launch(intent)
    }

    private fun setRecyclerAdapter() {

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                raceViewModel.races.collect { races ->
                    recyclerView.adapter =
                        RaceRecyclerViewAdapter(
                            races, { raceId ->

                                // Pass the race id into view Model
                                selectedRaceViewModel.setRace(raceId)

                                findNavController().navigate(
                                    RaceSelectionFragmentDirections.openRace()
                                )
                            },
                            //Context menu action setup
                            { action, position, race ->
                                recyclerViewContextMenuActions(
                                    action,
                                    position,
                                    race
                                )
                            }, requireContext()
                        )
                }
            }
        }
    }

    private fun setSwipeActions() {
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            private val backgroundPaint = Paint().apply { color = Color.rgb(190, 40, 40) }
            private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textAlign = Paint.Align.RIGHT
                textSize = resources.getDimensionPixelSize(R.dimen.race_swipe_delete_text_size).toFloat()
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) {
                    DebugLog.warn("Events", "Swipe delete ignored because row position was stale")
                    return
                }
                val adapter = recyclerView.adapter as? RaceRecyclerViewAdapter
                val race = adapter?.raceAt(position)
                if (race == null) {
                    recyclerView.adapter?.notifyItemChanged(position)
                    DebugLog.warn("Events", "Swipe delete ignored because row position was unavailable")
                    return
                }

                DebugLog.info("Events", "Swipe delete requested event=${race.id} name=${race.name}")
                confirmRaceDeletionFromSwipe(race, position)
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                if (dX < 0) {
                    c.drawRect(
                        itemView.right + dX,
                        itemView.top.toFloat(),
                        itemView.right.toFloat(),
                        itemView.bottom.toFloat(),
                        backgroundPaint
                    )
                    val baseline = itemView.top + (itemView.height - textPaint.descent() - textPaint.ascent()) / 2
                    c.drawText(
                        getString(R.string.race_delete),
                        itemView.right - resources.getDimensionPixelSize(R.dimen.race_swipe_delete_padding).toFloat(),
                        baseline,
                        textPaint
                    )
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }

        ItemTouchHelper(swipeCallback).attachToRecyclerView(recyclerView)
    }
}
