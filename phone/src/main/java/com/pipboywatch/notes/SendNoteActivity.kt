package com.pipboywatch.notes

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.google.android.gms.wearable.Wearable
import com.pipboywatch.shared.log.PipLog
import com.pipboywatch.shared.sync.NOTE_PATH
import com.pipboywatch.shared.sync.encodeNote

private const val TAG = "PipBoyNotes"

/**
 * Minimal Share-sheet target — appears as "Pip-Boy Notes" wherever Android
 * offers a Share action for plain text. Relays the shared text to every
 * connected watch node over the Wear Data Layer, then finishes; no UI of
 * its own beyond a confirmation Toast.
 */
class SendNoteActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text = if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
        } else {
            null
        }
        // Log the note's length, not its content — this is free-text the
        // user shared, and logcat is readable by anything with ADB/shell
        // access even on a release build.
        if (BuildConfig.DEBUG) PipLog.d(TAG, "onCreate textLength=${text?.length}")

        if (text.isNullOrBlank()) {
            toastAndFinish("Nothing to send")
            return
        }

        val nodeClient = Wearable.getNodeClient(this)
        val messageClient = Wearable.getMessageClient(this)

        nodeClient.connectedNodes
            .addOnSuccessListener { nodes ->
                PipLog.d(TAG, "connectedNodes success, count=${nodes.size} nodes=${nodes.map { it.displayName }}")
                if (nodes.isEmpty()) {
                    toastAndFinish("No Pip-Boy watch connected")
                    return@addOnSuccessListener
                }
                var pending = nodes.size
                var anySucceeded = false
                nodes.forEach { node ->
                    messageClient.sendMessage(node.id, NOTE_PATH, encodeNote(text))
                        .addOnCompleteListener { result ->
                            PipLog.d(TAG, "sendMessage to ${node.id} isSuccessful=${result.isSuccessful} exception=${result.exception}")
                            pending--
                            if (result.isSuccessful) anySucceeded = true
                            if (pending == 0) {
                                toastAndFinish(if (anySucceeded) "Sent to Pip-Boy" else "Couldn't reach watch")
                            }
                        }
                }
            }
            .addOnFailureListener { e ->
                PipLog.w(TAG, "connectedNodes failure", e)
                toastAndFinish("Couldn't reach watch")
            }
    }

    private fun toastAndFinish(message: String) {
        PipLog.d(TAG, "result: $message")
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        // Give the Toast a moment to actually attach its window before we
        // finish() and the process potentially gets reclaimed — finishing
        // immediately after show() can race the Toast system and silently
        // drop it ("Toast already killed"), observed during Phase 7 testing.
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 1500)
    }
}
