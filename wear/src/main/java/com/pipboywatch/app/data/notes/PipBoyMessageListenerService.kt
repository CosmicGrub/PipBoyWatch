package com.pipboywatch.app.data.notes

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.pipboywatch.app.notes.NoteRepository
import com.pipboywatch.shared.log.PipLog
import com.pipboywatch.shared.sync.NOTE_PATH
import com.pipboywatch.shared.sync.decodeNote
import com.pipboywatch.shared.sync.isFromTrustedNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val TAG = "PipBoyNotes"

/**
 * Built in Phase 4, has nothing to receive from until Phase 7 builds the
 * phone-side Share-sheet sender. Listens on NOTE_PATH (now declared once,
 * in :shared's SyncChannelRegistry, alongside every other channel's paths
 * — see System 06) for a UTF-8 text payload and stores it as a note, same
 * table the on-watch "+ Add Note" RemoteInput flow writes to — via
 * NoteRepository, same as the on-watch flow, rather than hitting the DAO
 * directly.
 */
class PipBoyMessageListenerService : WearableListenerService() {
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private val noteRepository by lazy { NoteRepository(applicationContext) }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        PipLog.d(TAG, "onMessageReceived path=${messageEvent.path} from=${messageEvent.sourceNodeId}")
        if (messageEvent.path != NOTE_PATH) return
        val text = decodeNote(messageEvent.data)
        if (text.isEmpty()) return

        val sourceNodeId = messageEvent.sourceNodeId
        serviceScope.launch {
            // Defense-in-depth on top of the Wear Data Layer's own
            // AppKey-based routing (package+signature) — confirms the note
            // actually came from a node we're currently paired with before
            // writing it into the on-watch database as if the user typed it.
            if (!isFromTrustedNode(applicationContext, sourceNodeId)) {
                PipLog.w(TAG, "Ignoring note from untrusted node $sourceNodeId")
                return@launch
            }
            noteRepository.addFromPhone(text)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
