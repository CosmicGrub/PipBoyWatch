package com.pipboywatch.shared.log

import android.content.Context
import android.util.Log
import java.io.File

/** Kept small and closed rather than an open-ended severity scale — this
 * app only ever needs "normal", "should look into this", and "broke". */
enum class PipLogLevel { DEBUG, WARN, ERROR }

/**
 * Structured, rolling, on-device log — the entire "diagnostics" story for
 * an app with no backend and no crash-reporting SaaS (Crashlytics/Sentry
 * are explicitly out of scope; see the pitch's own "deliberately
 * excluded" list). This app's hardest bugs so far — the on-watch Health
 * Connect gap, the notification permission that never grants on this
 * Samsung device — were root-caused manually with both devices cabled to
 * adb at once. The next one might not be reproducible on demand.
 *
 * Both wear and phone are separate processes on separate physical
 * devices; each calls [init] once from its own Application.onCreate()
 * with a [deviceTag] ("wear" / "phone") identifying which side wrote a
 * given line, so logs from both halves of a phone<->watch bug can be
 * merged into one exported file (see DiagnosticsExporter) and correlated
 * by timestamp after the fact, without needing both devices at hand.
 *
 * Lines are "epochMillis|device|tag|level|message" — pipe-delimited to
 * stay trivially greppable, matching this project's existing wire-format
 * convention (see StatSync.kt) rather than inventing JSON for a file
 * whose only real consumer is a human reading it after the fact. Only
 * the first 4 fields are guaranteed delimiter-free; message is always the
 * remainder of the line (see [formatLine]), the same "split only where it
 * matters" approach as decodeStatReply.
 */
object PipLog {
    private const val MAX_FILE_BYTES = 256 * 1024L // 256 KB, then rotate
    private const val LOG_FILE_NAME = "pipboy.log"

    @Volatile private var logFile: File? = null
    @Volatile private var deviceTag: String = "unknown"
    private val writeLock = Any()

    fun init(context: Context, deviceTag: String) {
        val dir = File(context.applicationContext.filesDir, "logs").apply { mkdirs() }
        initWithFile(File(dir, LOG_FILE_NAME), deviceTag)
    }

    /** The actual seam [init] delegates to — every operation below this
     * point is plain java.io, nothing Android-specific, so tests exercise
     * the real rotation/write logic against a real (temp) file without
     * needing a Context or Robolectric. */
    internal fun initWithFile(file: File, deviceTag: String) {
        this.deviceTag = deviceTag
        logFile = file
    }

    fun d(tag: String, message: String) = write(PipLogLevel.DEBUG, tag, message, null)
    fun w(tag: String, message: String, throwable: Throwable? = null) = write(PipLogLevel.WARN, tag, message, throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) = write(PipLogLevel.ERROR, tag, message, throwable)

    private fun write(level: PipLogLevel, tag: String, message: String, throwable: Throwable?) {
        // Always mirror to logcat too — this supplements normal
        // development logging, it doesn't replace it.
        when (level) {
            PipLogLevel.DEBUG -> Log.d(tag, message, throwable)
            PipLogLevel.WARN -> Log.w(tag, message, throwable)
            PipLogLevel.ERROR -> Log.e(tag, message, throwable)
        }
        val file = logFile ?: return // init() never called — degrade to logcat-only rather than crash
        val fullMessage = if (throwable != null) {
            "$message | ${throwable.stackTraceToString()}"
        } else {
            message
        }
        val line = formatLine(System.currentTimeMillis(), deviceTag, tag, level, fullMessage)
        synchronized(writeLock) {
            try {
                rotateIfNeeded(file)
                file.appendText(line + "\n", Charsets.UTF_8)
            } catch (e: Exception) {
                // Logging must never be the thing that crashes the app —
                // already emitted to logcat above, degrade silently here.
            }
        }
    }

    private fun rotateIfNeeded(file: File) {
        if (!file.exists() || file.length() <= MAX_FILE_BYTES) return
        // Single-generation rotation is enough for a solo-dev diagnostics
        // file, not a production log-aggregation system — keep only the
        // most recent half of the budget, drop the rest.
        val tail = file.readText(Charsets.UTF_8).takeLast((MAX_FILE_BYTES / 2).toInt())
        file.writeText(tail, Charsets.UTF_8)
    }

    /** A newline in [message] would fake a second log line, so it's the
     * one character actually stripped — pipes are left alone since
     * message is always the last field (a parser reads the first 4
     * pipe-delimited fields, then everything after the 4th pipe is the
     * message verbatim, embedded pipes and all). */
    internal fun formatLine(epochMillis: Long, device: String, tag: String, level: PipLogLevel, message: String): String {
        // A single regex pass so a Windows-style "\r\n" collapses to one
        // space, not two independently-replaced characters.
        val safeMessage = message.replace(Regex("[\r\n]+"), " ")
        return "$epochMillis|$device|$tag|${level.name}|$safeMessage"
    }

    /** The current log file, or null if [init] was never called — used by
     * DiagnosticsExporter to find what to zip. */
    fun currentLogFile(): File? = logFile
}
