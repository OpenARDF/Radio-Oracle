package org.openardf.radiooracle.backend.logging

import android.content.Context
import android.os.Build
import org.openardf.radiooracle.BuildConfig
import org.openardf.radiooracle.shared.diagnostics.DiagnosticReport
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DiagnosticLogExport {
    fun create(context: Context): File {
        DebugLog.initialize(context)
        DebugLog.info("Diagnostics", "User requested diagnostic log export")
        val entries = DiagnosticReport.entries(DebugLog.snapshot(),
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}); ${Build.MANUFACTURER} ${Build.MODEL}",
            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", BuildConfig.BUILD_DATE_UTC,
            Instant.now().toString())
        val directory = File(context.cacheDir, "diagnostic-exports").apply { mkdirs() }
        directory.listFiles()?.filter { it.isFile && it.lastModified() < System.currentTimeMillis() - 7L * 86_400_000 }
            ?.forEach { it.delete() }
        val file = File(directory, "Radio-Oracle-logs-${Instant.now().toString().replace(':', '-')}-${UUID.randomUUID().toString().take(8)}.zip")
        try {
            ZipOutputStream(file.outputStream().buffered()).use { zip ->
                entries.forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        } catch (error: Exception) {
            file.delete()
            throw error
        }
        return file
    }
}
