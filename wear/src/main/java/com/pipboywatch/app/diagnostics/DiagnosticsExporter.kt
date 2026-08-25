package com.pipboywatch.app.diagnostics

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.pipboywatch.shared.log.CrashHandler
import com.pipboywatch.shared.log.PipLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The entire crash-reporting story for a no-backend, no-telemetry-SaaS
 * app (Crashlytics/Sentry are explicitly out of scope by design — see
 * System 02's own pitch). Zips the rolling PipLog, the last CrashHandler
 * snapshot (if any), and a small metadata file, then hands the result off
 * via ACTION_SEND for the user to save or share themselves — this app
 * never phones anything home on its own.
 */
class DiagnosticsExporter(context: Context) {
    private val appContext = context.applicationContext

    /** Builds the zip and an ACTION_SEND [Intent] ready to hand to
     * startActivity — the caller decides when/how to launch it (e.g. from
     * a Composable's onClick), this class only builds the artifact. */
    fun buildExportIntent(): Intent {
        val zipFile = buildZip()
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.diagnostics", zipFile)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "PipBoyWatch diagnostics")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun buildZip(): File {
        val exportDir = File(appContext.getExternalFilesDir(null), "diagnostics").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())
        val zipFile = File(exportDir, "pipboy-diagnostics-$stamp.zip")

        ZipOutputStream(zipFile.outputStream()).use { zip ->
            PipLog.currentLogFile()?.takeIf { it.exists() }?.let { addFileToZip(zip, it, "pipboy.log") }
            CrashHandler.lastCrashFile(appContext).takeIf { it.exists() }?.let { addFileToZip(zip, it, "last_crash.txt") }
            addMetadataToZip(zip)
        }
        return zipFile
    }

    private fun addFileToZip(zip: ZipOutputStream, file: File, entryName: String) {
        zip.putNextEntry(ZipEntry(entryName))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun addMetadataToZip(zip: ZipOutputStream) {
        val packageInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        val metadata = buildString {
            appendLine("versionName=${packageInfo.versionName}")
            appendLine("versionCode=${packageInfo.longVersionCode}")
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("androidRelease=${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("storageFreeBytes=${appContext.filesDir.freeSpace}")
            appendLine("exportedAtEpochMillis=${System.currentTimeMillis()}")
        }
        zip.putNextEntry(ZipEntry("metadata.txt"))
        zip.write(metadata.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
}
