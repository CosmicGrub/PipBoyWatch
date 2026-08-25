package com.pipboywatch.app.health

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import com.pipboywatch.shared.sync.STAT_REQUEST_PATH
import com.pipboywatch.shared.sync.StatDecodeResult
import com.pipboywatch.shared.sync.decodeStatReply
import com.pipboywatch.shared.sync.newRequestId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

private const val TAG = "PipBoyStatSync"

/**
 * Shared holder so StatMessageListenerService (receives the phone's reply)
 * and StatScreen (sends the request, observes the result) can talk without
 * IPC — both run in the same app process. Mirrors MediaSessionHolder's
 * pattern for RADIO. Wire format/paths live in :shared now (see
 * com.pipboywatch.shared.sync.StatSync) — this object is purely the
 * wear-side request/response state holder, not the protocol itself.
 *
 * Tracks the outstanding request's id so a stale reply — e.g. from a
 * request the user retried past, or from a prior STAT screen visit whose
 * reply arrived late — can't be mistaken for the answer to a fresher
 * request. An empty-id reply (the phone's "Sync to Watch Now" button,
 * which isn't answering any specific request) is always accepted.
 */
object PhoneStatRelay {
    private val _result = MutableStateFlow<StatDecodeResult?>(null)
    val result: StateFlow<StatDecodeResult?> = _result.asStateFlow()

    @Volatile private var outstandingRequestId: String? = null

    fun clear() {
        _result.value = null
        outstandingRequestId = null
    }

    fun onResponseReceived(payload: String) {
        val reply = decodeStatReply(payload)
        Log.d(TAG, "onResponseReceived requestId=${reply.requestId} outstanding=$outstandingRequestId")
        if (reply.requestId.isNotEmpty() && reply.requestId != outstandingRequestId) {
            Log.d(TAG, "Dropping stale stat reply for ${reply.requestId}")
            return
        }
        _result.value = reply.result
    }

    /** Pings every connected phone node; the reply (if any) arrives later
     * via StatMessageListenerService -> onResponseReceived. */
    suspend fun requestFromPhone(context: Context) {
        val requestId = newRequestId()
        outstandingRequestId = requestId
        try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            Log.d(TAG, "requestFromPhone id=$requestId nodes=${nodes.map { "${it.displayName}(${it.id}, nearby=${it.isNearby})" }}")
            if (nodes.isEmpty()) {
                _result.value = StatDecodeResult.Unavailable
                return
            }
            val messageClient = Wearable.getMessageClient(context)
            nodes.forEach { node ->
                messageClient.sendMessage(node.id, STAT_REQUEST_PATH, requestId.toByteArray(Charsets.UTF_8))
                    .addOnCompleteListener { result ->
                        Log.d(TAG, "sendMessage to ${node.id} isSuccessful=${result.isSuccessful} exception=${result.exception}")
                    }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "requestFromPhone failed", e)
            _result.value = StatDecodeResult.Unavailable
        }
    }
}
