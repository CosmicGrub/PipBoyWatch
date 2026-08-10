package com.pipboywatch.health

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/**
 * Shown when the user taps the privacy-policy link on Health Connect's
 * permission grant screen. Required by Health Connect for the app to be
 * eligible to request health permissions at all — mirrors wear's
 * PermissionsRationaleActivity, plain Android widgets here since the phone
 * module otherwise has no UI framework dependency (no Compose).
 */
class PermissionsRationaleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val padding = (16 * resources.displayMetrics.density).toInt()
        setContentView(
            TextView(this).apply {
                text = "Pip-Boy Companion reads steps, heart rate, sleep, and " +
                    "workout data from Health Connect to relay to your paired " +
                    "Pip-Boy watch, since the watch can't read Health Connect " +
                    "directly on this hardware. This data is sent only to your " +
                    "own paired watch over the Wear Data Layer and nowhere else."
                setPadding(padding, padding, padding, padding)
            }
        )
    }
}
