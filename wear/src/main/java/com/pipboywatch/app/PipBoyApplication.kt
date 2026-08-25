package com.pipboywatch.app

import android.app.Application
import com.pipboywatch.shared.log.CrashHandler
import com.pipboywatch.shared.log.PipLog

/**
 * Installs the two pieces of this app's entire crash-reporting story
 * (System 02): PipLog for a rolling on-device log, CrashHandler for a
 * last-crash snapshot. Both are per-process, so this and
 * com.pipboywatch.PipBoyApplication (phone) each install their own — a
 * crash on the watch never reaches the phone's handler and vice versa.
 */
class PipBoyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PipLog.init(this, deviceTag = "wear")
        CrashHandler.install(this, deviceTag = "wear")
    }
}
