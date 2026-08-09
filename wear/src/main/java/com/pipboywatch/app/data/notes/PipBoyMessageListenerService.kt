package com.pipboywatch.app.data.notes

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.pipboywatch.app.data.NoteEntity
import com.pipboywatch.app.data.PipBoyDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

        serviceScope.launch {
            PipBoyDatabase.getInstance(applicationContext).noteDao().insert(
                NoteEntity(text = text, receivedAt = System.currentTimeMillis(), source = "phone")
            )
        }
    }

    companion object {
        const val NOTE_PATH = "/pipboy/note"
    }
}
