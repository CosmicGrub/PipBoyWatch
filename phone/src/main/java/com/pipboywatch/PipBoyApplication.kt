package com.pipboywatch

import android.app.Application
import com.pipboywatch.shared.log.CrashHandler
import com.pipboywatch.shared.log.PipLog

/**
 * Installs this app's half of System 02's crash-reporting story — see
 * com.pipboywatch.app.PipBoyApplication (wear) for the wear-side
 * counterpart and the full rationale. Note this file lives in the
 * com.pipboywatch root package, not com.pipboywatch.notes (this module's
 * `namespace`) — same "fully-qualified names for anything outside the
 * namespace package" gotcha AndroidManifest.xml's own comment already
 * documents for MainActivity.
 */
class PipBoyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PipLog.init(this, deviceTag = "phone")
        CrashHandler.install(this, deviceTag = "phone")
    }
}
