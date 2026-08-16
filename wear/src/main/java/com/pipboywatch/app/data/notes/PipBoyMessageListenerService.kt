package com.pipboywatch.app.data.notes

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.pipboywatch.app.data.NoteEntity
import com.pipboywatch.app.data.PipBoyDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val TAG = "PipBoyNotes"

/**
 * Built in Phase 4, has nothing to receive from until Phase 7 builds the
 * phone-side Share-sheet sender. Listens on NOTE_PATH for a UTF-8 text
 * payload and stores it as a note, same table the on-watch "+ Add Note"
 * RemoteInput flow writes to.
 */
class PipBoyMessageListenerService : WearableListenerService() {
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "onMessageReceived path=${messageEvent.path} from=${messageEvent.sourceNodeId}")
        if (messageEvent.path != NOTE_PATH) return
        val text = String(messageEvent.data, Charsets.UTF_8).trim()
        if (text.isEmpty()) return

        val sourceNodeId = messageEvent.sourceNodeId
        serviceScope.launch {
            // Defense-in-depth on top of the Wear Data Layer's own
            // AppKey-based routing (package+signature) — confirms the note
            // actually came from a node we're currently paired with before
            // writing it into the on-watch database as if the user typed it.
            if (!isFromTrustedNode(sourceNodeId)) {
                Log.w(TAG, "Ignoring note from untrusted node $sourceNodeId")
                return@launch
            }
            PipBoyDatabase.getInstance(applicationContext).noteDao().insert(
                NoteEntity(text = text, receivedAt = System.currentTimeMillis(), source = "phone")
            )
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun isFromTrustedNode(sourceNodeId: String): Boolean {
        return try {
            Wearable.getNodeClient(applicationContext).connectedNodes.await().any { it.id == sourceNodeId }
        } catch (e: Exception) {
            Log.d(TAG, "isFromTrustedNode check failed", e)
            false
        }
    }

    companion object {
        const val NOTE_PATH = "/pipboy/note"
    }
}
