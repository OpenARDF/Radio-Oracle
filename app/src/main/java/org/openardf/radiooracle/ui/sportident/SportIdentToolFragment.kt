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

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.sportident.SIReaderService
import org.openardf.radiooracle.shared.device.SIReaderState
import org.openardf.radiooracle.shared.device.SIReaderStatus

/**
 * Android-only service/lifecycle boundary shared by the focused SPORTident screens.
 * Hardware transactions remain in [SIReaderService]; screens receive only its binder.
 */
abstract class SportIdentToolFragment(
    @LayoutRes layoutResource: Int
) : Fragment(layoutResource) {
    protected var sportIdentBinder: SIReaderService.LocalBinder? = null
        private set

    /** Keep the in-app back stack stable while one screen owns the station mutex. */
    protected open val navigationBlocked: Boolean = false

    private var isBound = false
    private val dataProcessor by lazy { DataProcessor.get() }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            sportIdentBinder = service as? SIReaderService.LocalBinder
            onSportIdentBinderChanged(sportIdentBinder)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            sportIdentBinder = null
            onSportIdentBinderChanged(null)
        }
    }

    final override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        onToolViewCreated(view, savedInstanceState)
        dataProcessor.currentState.observe(viewLifecycleOwner) { state ->
            onReaderStateChanged(state.siReaderState)
            if (state.siReaderState.status == SIReaderStatus.DISCONNECTED) {
                sportIdentBinder = null
                onSportIdentBinderChanged(null)
            } else if (!isBound && lifecycle.currentState.isAtLeast(
                    androidx.lifecycle.Lifecycle.State.STARTED
                )
            ) {
                bindReaderService()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        bindReaderService()
    }

    override fun onStop() {
        if (isBound) {
            requireContext().unbindService(serviceConnection)
            isBound = false
        }
        sportIdentBinder = null
        super.onStop()
    }

    protected abstract fun onToolViewCreated(view: View, savedInstanceState: Bundle?)

    protected open fun onSportIdentBinderChanged(binder: SIReaderService.LocalBinder?) = Unit

    protected open fun onReaderStateChanged(readerState: SIReaderState) = Unit

    protected fun configureToolbar(toolbar: Toolbar, @StringRes title: Int) {
        toolbar.setTitle(title)
        toolbar.setNavigationIcon(R.drawable.ic_back)
        toolbar.setNavigationOnClickListener { navigateBack() }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            navigateBack()
        }
    }

    private fun navigateBack() {
        if (navigationBlocked) {
            Toast.makeText(
                requireContext(),
                R.string.sportident_operation_in_progress,
                Toast.LENGTH_LONG
            ).show()
        } else {
            findNavController().navigateUp()
        }
    }

    private fun bindReaderService() {
        if (isBound) return
        val readerState = dataProcessor.currentState.value?.siReaderState
        if (readerState == null || readerState.status == SIReaderStatus.DISCONNECTED) {
            onSportIdentBinderChanged(null)
            return
        }
        isBound = requireContext().bindService(
            Intent(requireContext(), SIReaderService::class.java),
            serviceConnection,
            0
        )
        if (!isBound) onSportIdentBinderChanged(null)
    }
}
