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

package org.openardf.radiooracle.ui.sportident

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.navigation.fragment.findNavController
import com.google.android.material.card.MaterialCardView
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.sportident.AndroidSportIdentOwnerRecoveryState
import org.openardf.radiooracle.backend.sportident.SIReaderService
import org.openardf.radiooracle.shared.device.SIReaderState
import org.openardf.radiooracle.shared.device.SIReaderStatus
import org.openardf.radiooracle.shared.sportident.SportIdentStationMode

/** Focused entry screen keeps unrelated SPORTident workflows out of one modal. */
class SportIdentToolsHomeFragment : SportIdentToolFragment(R.layout.fragment_sportident_tools) {
    private lateinit var connectionStatusView: TextView
    private lateinit var recoveryStatusView: TextView

    override fun onToolViewCreated(view: View, savedInstanceState: Bundle?) {
        configureToolbar(
            view.findViewById<Toolbar>(R.id.sportident_tools_toolbar),
            R.string.sportident_tools_title
        )
        connectionStatusView = view.findViewById(R.id.sportident_tools_connection_status)
        recoveryStatusView = view.findViewById(R.id.sportident_tools_recovery_status)

        view.findViewById<MaterialCardView>(R.id.sportident_tools_station).setOnClickListener {
            findNavController().navigate(
                SportIdentToolsHomeFragmentDirections.openSportIdentStation()
            )
        }
        view.findViewById<MaterialCardView>(R.id.sportident_tools_card).setOnClickListener {
            findNavController().navigate(
                SportIdentToolsHomeFragmentDirections.openSportIdentCard()
            )
        }
        view.findViewById<MaterialCardView>(R.id.sportident_tools_punch_history)
            .setOnClickListener {
                findNavController().navigate(
                    SportIdentToolsHomeFragmentDirections.openSportIdentPunchHistory()
                )
            }
    }

    override fun onSportIdentBinderChanged(binder: SIReaderService.LocalBinder?) {
        val recoveryState = runCatching { binder?.ownerWriteRecoveryState() }
            .getOrDefault(AndroidSportIdentOwnerRecoveryState.Unavailable)
        recoveryStatusView.visibility = when (recoveryState) {
            AndroidSportIdentOwnerRecoveryState.Empty, null -> View.GONE
            is AndroidSportIdentOwnerRecoveryState.Pending,
            AndroidSportIdentOwnerRecoveryState.Unavailable -> View.VISIBLE
        }
    }

    override fun onReaderStateChanged(readerState: SIReaderState) {
        connectionStatusView.text = when (readerState.status) {
            SIReaderStatus.CONNECTED -> {
                val station = readerState.stationId
                val mode = readerState.stationModeCode?.let(SportIdentStationMode::labelForModeCode)
                if (station != null && mode != null) {
                    getString(R.string.sportident_tools_connected, station, mode)
                } else {
                    getString(R.string.si_connected)
                }
            }

            SIReaderStatus.DISCONNECTED -> getString(R.string.sportident_time_sync_disconnected)
            else -> getString(R.string.sportident_time_sync_connecting)
        }
    }
}
