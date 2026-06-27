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

package org.openardf.radiooracle.backend.logging

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Hidden app-private debug log facade.
 *
 * The file log complements logcat for field diagnostics. It is deliberately not
 * exposed in normal UI and should contain only operational breadcrumbs that are
 * useful when reproducing device, import/export, or service problems.
 */
object DebugLog {
    private const val LOG_DIR_NAME = "debug-logs"

    @Volatile
    private var rollingLog: RollingDebugLog? = null

    /** Configures logging under the app's private files directory. */
    fun initialize(context: Context) {
        rollingLog = RollingDebugLog(File(context.filesDir, LOG_DIR_NAME))
        info("App", "Debug log initialized")
    }

    /** Writes a debug-level breadcrumb to the hidden file log. */
    fun debug(tag: String, message: String) {
        write("D", tag, message)
    }

    /** Writes an info-level breadcrumb to the hidden file log. */
    fun info(tag: String, message: String) {
        write("I", tag, message)
    }

    /** Writes a warning-level breadcrumb to the hidden file log. */
    fun warn(tag: String, message: String) {
        write("W", tag, message)
    }

    /** Writes an error-level breadcrumb to the hidden file log. */
    fun error(tag: String, message: String) {
        write("E", tag, message)
    }

    private fun write(level: String, tag: String, message: String) {
        try {
            rollingLog?.write(level, tag, message)
        } catch (exception: Exception) {
            Log.w("DebugLog", "Failed to write debug log: ${exception.message}")
        }
        try {
            val logMessage = "$tag $message"
            when (level) {
                "E" -> Log.e("RadioOracle", logMessage)
                "W" -> Log.w("RadioOracle", logMessage)
                "I" -> Log.i("RadioOracle", logMessage)
                else -> Log.d("RadioOracle", logMessage)
            }
        } catch (_: RuntimeException) {
            // android.util.Log is not available in local JVM unit tests.
        }
    }
}
