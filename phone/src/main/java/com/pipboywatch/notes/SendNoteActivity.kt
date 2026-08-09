package com.pipboywatch.notes

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.google.android.gms.wearable.Wearable

private const val NOTE_PATH = "/pipboy/note"
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
        Log.d(TAG, "onCreate text=$text")

        if (text.isNullOrBlank()) {
            toastAndFinish("Nothing to send")
            return
        }

        val nodeClient = Wearable.getNodeClient(this)
        val messageClient = Wearable.getMessageClient(this)

        nodeClient.connectedNodes
            .addOnSuccessListener { nodes ->
                Log.d(TAG, "connectedNodes success, count=${nodes.size} nodes=${nodes.map { it.displayName }}")
                if (nodes.isEmpty()) {
                    toastAndFinish("No Pip-Boy watch connected")
                    return@addOnSuccessListener
                }
                var pending = nodes.size
                var anySucceeded = false
                nodes.forEach { node ->
                    messageClient.sendMessage(node.id, NOTE_PATH, text.toByteArray(Charsets.UTF_8))
                        .addOnCompleteListener { result ->
                            Log.d(TAG, "sendMessage to ${node.id} isSuccessful=${result.isSuccessful} exception=${result.exception}")
                            pending--
                            if (result.isSuccessful) anySucceeded = true
                            if (pending == 0) {
                                toastAndFinish(if (anySucceeded) "Sent to Pip-Boy" else "Couldn't reach watch")
                            }
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.d(TAG, "connectedNodes failure", e)
                toastAndFinish("Couldn't reach watch")
            }
    }

    private fun toastAndFinish(message: String) {
        Log.d(TAG, "result: $message")
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        // Give the Toast a moment to actually attach its window before we
        // finish() and the process potentially gets reclaimed — finishing
        // immediately after show() can race the Toast system and silently
        // drop it ("Toast already killed"), observed during Phase 7 testing.
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 1500)
    }
}
