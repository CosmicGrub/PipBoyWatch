package com.pipboywatch.app.health

import android.content.Context
import com.google.android.gms.wearable.Wearable
import com.pipboywatch.shared.log.PipLog
import com.pipboywatch.shared.sync.PendingRequestTracker
import com.pipboywatch.shared.sync.STAT_REQUEST_PATH
import com.pipboywatch.shared.sync.StatDecodeResult
import com.pipboywatch.shared.sync.decodeStatReply
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val TAG = "PipBoyStatSync"
private const val CHANNEL = "stat"

/** How long to wait for the phone's reply before treating the request as
 * abandoned. This transport has already been observed to silently drop
 * messages outright (see PhoneStatRelay's own commit history) — without
 * this, that failure mode left the STAT screen showing "SYNCING" forever
 * instead of ever settling on Unavailable. */
private const val REPLY_TIMEOUT_MILLIS = 20_000L

/**
 * Shared holder so StatMessageListenerService (receives the phone's reply)
 * and StatScreen (sends the request, observes the result) can talk without
 * IPC — both run in the same app process. Mirrors MediaSessionHolder's
 * pattern for RADIO. Wire format/paths live in :shared now (see
 * com.pipboywatch.shared.sync.StatSync) — this object is purely the
 * wear-side request/response state holder, not the protocol itself.
 *
 * Request correlation itself — rejecting a stale reply from a request the
 * user retried past, or from a prior STAT screen visit whose reply
 * arrived late, while always accepting an empty-id unsolicited push —
 * is delegated to PendingRequestTracker rather than owned here; this
 * object is now just STAT's one-channel wiring around it (see
 * shared/.../sync/PendingRequestTracker.kt for why that got pulled out).
 */
object PhoneStatRelay {
    private val tracker = PendingRequestTracker<StatDecodeResult>()
    val result: StateFlow<StatDecodeResult?> = tracker.observe(CHANNEL)

    // onResponseReceived runs on whatever background thread the Wearable
    // Data Layer delivers messages on, not necessarily the same thread
    // requestFromPhone's coroutine runs its cancellation on — @Volatile
    // for cross-thread visibility of the reference itself (Job.cancel()
    // is already safe to call from any thread on its own).
    @Volatile private var timeoutJob: Job? = null

    fun clear() {
        timeoutJob?.cancel()
        tracker.clear(CHANNEL)
    }

    fun onResponseReceived(payload: String) {
        val reply = decodeStatReply(payload)
        PipLog.d(TAG, "onResponseReceived requestId=${reply.requestId}")
        // Only cancel the pending timeout if this reply was actually
        // accepted — a stale/mismatched reply (complete() returning
        // false) shouldn't cancel the timeout still watching the real
        // outstanding request.
        if (tracker.complete(CHANNEL, reply.requestId, reply.result)) {
            timeoutJob?.cancel()
        }
    }

    /** Pings every connected phone node; the reply (if any) arrives later
     * via StatMessageListenerService -> onResponseReceived. If nothing
     * answers within REPLY_TIMEOUT_MILLIS, settles on Unavailable instead
     * of leaving observers waiting indefinitely. */
    suspend fun requestFromPhone(context: Context) {
        val requestId = tracker.mint(CHANNEL, REPLY_TIMEOUT_MILLIS)
        timeoutJob?.cancel()
        timeoutJob = CoroutineScope(Dispatchers.Default).launch {
            delay(REPLY_TIMEOUT_MILLIS)
            if (tracker.expireStale(System.currentTimeMillis()).contains(CHANNEL)) {
                PipLog.d(TAG, "requestFromPhone id=$requestId timed out waiting for a reply")
                tracker.complete(CHANNEL, "", StatDecodeResult.Unavailable)
            }
        }
        try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            PipLog.d(TAG, "requestFromPhone id=$requestId nodes=${nodes.map { "${it.displayName}(${it.id}, nearby=${it.isNearby})" }}")
            if (nodes.isEmpty()) {
                timeoutJob?.cancel()
                tracker.complete(CHANNEL, requestId, StatDecodeResult.Unavailable)
                return
            }
            val messageClient = Wearable.getMessageClient(context)
            nodes.forEach { node ->
                messageClient.sendMessage(node.id, STAT_REQUEST_PATH, requestId.toByteArray(Charsets.UTF_8))
                    .addOnCompleteListener { result ->
                        PipLog.d(TAG, "sendMessage to ${node.id} isSuccessful=${result.isSuccessful} exception=${result.exception}")
                    }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PipLog.w(TAG, "requestFromPhone failed", e)
            timeoutJob?.cancel()
            tracker.complete(CHANNEL, requestId, StatDecodeResult.Unavailable)
        }
    }
}
