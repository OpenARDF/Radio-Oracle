package org.openardf.radiooracle.ui.series

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import org.openardf.radiooracle.R

class EventSeriesFragment : Fragment() {
    private val viewModel: EventSeriesViewModel by viewModels()
    private lateinit var toolbar: Toolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView

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
                    recyclerView.adapter = EventSeriesRecyclerViewAdapter(series, requireContext())
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
}
