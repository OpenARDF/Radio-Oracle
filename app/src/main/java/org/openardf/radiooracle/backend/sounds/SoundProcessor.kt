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

package org.openardf.radiooracle.backend.sounds

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.openardf.radiooracle.R
import org.openardf.radiooracle.shared.sound.SoundType

/** Plays short user-feedback sounds for readout outcomes. */
object SoundProcessor {
    /** Plays the sound associated with the supplied readout type. */
    fun makeSound(context: Context, type: SoundType) {
        if (type == SoundType.ERROR_UNKNOWN) {
            makeErrorSound(context)
            return
        }
        val sound = when (type) {
            SoundType.ERROR_UNKNOWN -> error("Handled above")
            SoundType.DUPLICATE -> R.raw.si_duplicate
            SoundType.RENT -> R.raw.si_rent
        }

        val mediaPlayer = MediaPlayer.create(context, sound)
        mediaPlayer.setOnCompletionListener(MediaPlayer::release)
        mediaPlayer.setOnErrorListener { player, _, _ ->
            player.release()
            true
        }
        mediaPlayer.start()
    }

    /** Plays one short, restrained cue for a user-visible error. */
    @Synchronized
    fun makeErrorSound(context: Context) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastErrorSoundAtMs < ERROR_SOUND_DEBOUNCE_MS) return
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION) == 0) return
        lastErrorSoundAtMs = now
        runCatching {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, ERROR_TONE_VOLUME_PERCENT).also { tone ->
                tone.startTone(ToneGenerator.TONE_PROP_NACK, ERROR_TONE_DURATION_MS)
                Handler(Looper.getMainLooper()).postDelayed(
                    { tone.release() },
                    ERROR_TONE_DURATION_MS + ERROR_TONE_RELEASE_DELAY_MS
                )
            }
        }
    }

    private var lastErrorSoundAtMs = 0L
    private const val ERROR_SOUND_DEBOUNCE_MS = 750L
    private const val ERROR_TONE_VOLUME_PERCENT = 35
    private const val ERROR_TONE_DURATION_MS = 180
    private const val ERROR_TONE_RELEASE_DELAY_MS = 100L
}
