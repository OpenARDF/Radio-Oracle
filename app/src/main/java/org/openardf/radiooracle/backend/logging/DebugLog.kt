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

/** App-private operational and SPORTident logs, exposed through user-initiated diagnostics export. */
object DebugLog {
    private const val LOG_DIR_NAME = "debug-logs"

    @Volatile
    private var rollingLog: RollingDebugLog? = null

    @Volatile private var sportIdentLog: RollingDebugLog? = null

    /** Configures logging under the app's private files directory. */
    @Synchronized
    fun initialize(context: Context) {
        if (rollingLog != null) return
        val directory = File(context.filesDir, LOG_DIR_NAME)
        rollingLog = RollingDebugLog(directory)
        sportIdentLog = RollingDebugLog(directory, maxFileBytes = 2L * 1024 * 1024,
            retainedFileCount = 4, fileName = "sportident.log")
        info("App", "Debug log initialized version=${org.openardf.radiooracle.BuildConfig.VERSION_NAME} " +
            "build=${org.openardf.radiooracle.BuildConfig.BUILD_DATE_UTC} Android=${android.os.Build.VERSION.RELEASE} model=${android.os.Build.MODEL}")
        sportIdent("SESSION version=${org.openardf.radiooracle.BuildConfig.VERSION_NAME} build=${org.openardf.radiooracle.BuildConfig.BUILD_DATE_UTC} " +
            "Android=${android.os.Build.VERSION.RELEASE} model=${android.os.Build.MODEL} diagnosticFormat=1 parser=preserve-incomplete-frames")
    }

    fun sportIdent(message: String) {
        try {
            sportIdentLog?.write("D", "SITrace", message)
        } catch (error: Exception) {
            warn("Diagnostics", "SPORTident trace write failed: ${error.javaClass.simpleName}")
        }
    }

    fun snapshot(): Map<String, ByteArray> =
        (rollingLog?.snapshot().orEmpty() + sportIdentLog?.snapshot().orEmpty())

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
