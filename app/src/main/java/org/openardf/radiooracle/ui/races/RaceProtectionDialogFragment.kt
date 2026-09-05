package org.openardf.radiooracle.ui.races

import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.room.ARDFRepository
import org.openardf.radiooracle.backend.room.AndroidCourseProtectionState
import java.util.UUID

/** Explicit encryption actions; plaintext and empty races never display an unlock prompt. */
class RaceProtectionDialogFragment : DialogFragment() {
    private val protection get() = ARDFRepository.get().courseProtection
    private val raceId get() = UUID.fromString(requireArguments().getString("raceId"))
    private val wholeSeries get() = requireArguments().getBoolean("wholeSeries")
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private lateinit var action: Button
    private var state: AndroidCourseProtectionState? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, padding / 2)
        }
        status = TextView(context).apply { setText(R.string.race_protection_checking) }
        action = Button(context).apply { visibility = View.GONE }
        content.addView(status)
        content.addView(action)
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(if (wholeSeries) R.string.series_password_protection else R.string.race_password_protection)
            .setView(ScrollView(context).apply { addView(content) })
            .setNegativeButton(R.string.general_close, null)
            .create()
        lifecycleScope.launch {
            try {
                state = protection.state(raceId, wholeSeries)
                renderState()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                status.text = error.message ?: getString(R.string.race_protection_failed)
            }
        }
        return dialog
    }

    private fun renderState() {
        val current = state ?: return
        status.setText(when {
            current.encrypted -> R.string.race_protection_encrypted
            current.hasCourseData -> R.string.race_protection_plaintext
            else -> R.string.race_protection_empty
        })
        action.visibility = if (current.hasCourseData) View.VISIBLE else View.GONE
        action.setText(if (current.encrypted) R.string.race_remove_encryption else R.string.race_enable_encryption)
        action.setOnClickListener { showPasswordForm(current.encrypted) }
    }

    private fun showPasswordForm(removing: Boolean) {
        action.visibility = View.GONE
        val form = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        content.addView(form)
        if (removing) form.addView(TextView(requireContext()).apply { setText(R.string.race_remove_encryption_warning) })
        fun passwordField(label: Int): TextInputEditText {
            val layout = TextInputLayout(requireContext()).apply {
                setHint(label)
                endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
            }
            val input = TextInputEditText(layout.context).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                isSingleLine = true
                isSaveEnabled = false
            }
            layout.addView(input)
            form.addView(layout)
            return input
        }
        val password = passwordField(if (removing) R.string.race_current_password else R.string.cloudflare_results_password)
        val confirmation = if (removing) null else passwordField(R.string.race_confirm_password)
        val submit = Button(requireContext()).apply {
            setText(if (removing) R.string.race_remove_encryption else R.string.race_enable_encryption)
        }
        form.addView(submit)
        val cancel = Button(requireContext()).apply { setText(R.string.general_cancel) }
        form.addView(cancel)
        cancel.setOnClickListener { content.removeView(form); renderState() }
        submit.setOnClickListener {
            val value = password.text?.toString().orEmpty().trim()
            if (value.isBlank()) {
                password.error = getString(R.string.general_required)
                return@setOnClickListener
            }
            if (confirmation != null && value != confirmation.text?.toString().orEmpty().trim()) {
                confirmation.error = getString(R.string.race_password_mismatch)
                return@setOnClickListener
            }
            isCancelable = false
            (dialog as? androidx.appcompat.app.AlertDialog)?.getButton(Dialog.BUTTON_NEGATIVE)?.isEnabled = false
            submit.isEnabled = false
            cancel.isEnabled = false
            password.isEnabled = false
            confirmation?.isEnabled = false
            status.setText(R.string.race_protection_updating)
            lifecycleScope.launch {
                try {
                    protection.update(raceId, wholeSeries, value, encrypt = !removing)
                    password.text?.clear()
                    confirmation?.text?.clear()
                    state = protection.state(raceId, wholeSeries)
                    content.removeView(form)
                    renderState()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    status.text = error.message ?: getString(R.string.race_protection_failed)
                    submit.isEnabled = true
                    cancel.isEnabled = true
                    password.isEnabled = true
                    confirmation?.isEnabled = true
                } finally {
                    isCancelable = true
                    (dialog as? androidx.appcompat.app.AlertDialog)?.getButton(Dialog.BUTTON_NEGATIVE)?.isEnabled = true
                }
            }
        }
    }

    companion object {
        fun newInstance(raceId: UUID, wholeSeries: Boolean = false) = RaceProtectionDialogFragment().apply {
            arguments = Bundle().apply {
                putString("raceId", raceId.toString())
                putBoolean("wholeSeries", wholeSeries)
            }
        }
    }
}
