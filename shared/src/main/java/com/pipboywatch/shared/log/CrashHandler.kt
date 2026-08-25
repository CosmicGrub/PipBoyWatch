package com.pipboywatch.shared.log

import android.content.Context
import java.io.File

private const val CRASH_FILE_NAME = "last_crash.txt"
private const val CRASH_TAG = "PipBoyCrash"

/**
 * Installs a Thread.UncaughtExceptionHandler that snapshots a crash to a
 * dedicated file before chaining to whatever handler was already
 * installed (Android's own default, which shows the "app has stopped"
 * dialog and kills the process). This must never SWALLOW the crash, only
 * observe it — [install]'s handler always calls the previous handler in
 * a `finally`, even if snapshotting itself throws.
 *
 * Wear and phone are separate processes on separate physical devices — a
 * crash in one never reaches the other's handler, so both call [install]
 * from their own Application.onCreate(), same as [PipLog.init].
 */
object CrashHandler {
    fun install(context: Context, deviceTag: String) {
        installWithFile(crashFile(context.applicationContext), deviceTag)
    }

    /** The actual seam [install] delegates to, and what tests exercise
     * directly — building and invoking the handler here doesn't require
     * triggering a real uncaught exception on a real thread. */
    internal fun installWithFile(crashFile: File, deviceTag: String) {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(
            buildHandler(crashFile, deviceTag, previousHandler)
        )
    }

    internal fun buildHandler(
        crashFile: File,
        deviceTag: String,
        previousHandler: Thread.UncaughtExceptionHandler?
    ) = Thread.UncaughtExceptionHandler { thread, throwable ->
        try {
            writeCrashSnapshot(crashFile, deviceTag, thread, throwable)
            PipLog.e(CRASH_TAG, "Uncaught exception on thread ${thread.name}", throwable)
        } catch (e: Exception) {
            // Never let the crash handler itself throw — that would
            // replace the real crash with a confusing secondary one, and
            // could prevent the finally block below from running.
        } finally {
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    internal fun writeCrashSnapshot(file: File, deviceTag: String, thread: Thread, throwable: Throwable) {
        file.parentFile?.mkdirs()
        val content = buildString {
            appendLine("epochMillis=${System.currentTimeMillis()}")
            appendLine("device=$deviceTag")
            appendLine("thread=${thread.name}")
            appendLine(throwable.stackTraceToString())
        }
        // Overwrite, not append — only the most recent crash matters for
        // a solo-dev diagnostics file; an accumulating crash log would
        // grow unbounded across every future crash instead.
        file.writeText(content, Charsets.UTF_8)
    }

    /** Null if the app has never crashed since this file's directory was
     * last cleared — DiagnosticsExporter checks existence before zipping. */
    fun lastCrashFile(context: Context): File = crashFile(context.applicationContext)

    private fun crashFile(context: Context): File =
        File(File(context.filesDir, "logs"), CRASH_FILE_NAME)
}
