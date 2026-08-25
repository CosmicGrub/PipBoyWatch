package com.pipboywatch.sync

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.pipboywatch.shared.health.HealthConnectManager
import com.pipboywatch.shared.log.PipLog
import com.pipboywatch.shared.sync.STAT_REQUEST_PATH
import com.pipboywatch.shared.sync.STAT_RESPONSE_PATH
import com.pipboywatch.shared.sync.encodeStatSnapshot
import com.pipboywatch.shared.sync.isFromTrustedNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val TAG = "PipBoyStatSync"

/**
 * Answers the watch's STAT_REQUEST_PATH pings with a Health Connect
 * snapshot read on the phone (see HealthConnectManager for why the phone
 * is the reliable source). Fires automatically whenever the watch's STAT
 * tab is opened and its own on-watch Health Connect read comes up empty —
 * no phone-side interaction needed once permission has been granted once
 * via MainActivity.
 */
class StatRequestListenerService : WearableListenerService() {
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private val healthManager by lazy { HealthConnectManager(applicationContext) }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        PipLog.d(TAG, "onMessageReceived path=${messageEvent.path} from=${messageEvent.sourceNodeId}")
        if (messageEvent.path != STAT_REQUEST_PATH) return

        val sourceNodeId = messageEvent.sourceNodeId
        // "|" + the request's id, if any (see requestId in STAT_REQUEST_PATH
        // payload) — echoed back in the reply so a stale request can't be
        // mistaken for the answer to a newer one. Empty payload (legacy /
        // no id) just gets echoed back empty.
        val requestId = String(messageEvent.data, Charsets.UTF_8)
        serviceScope.launch {
            // The Wear Data Layer's AppKey routing already restricts
            // delivery to apps sharing this app's package+signature, but
            // that's weaker on a debug-signed build than a real release
            // key — this is a cheap extra check that the request actually
            // came from a currently-connected paired node before we hand
            // it real Health Connect data.
            if (!isFromTrustedNode(applicationContext, sourceNodeId)) {
                PipLog.w(TAG, "Ignoring stat request from untrusted node $sourceNodeId")
                return@launch
            }
            val body = when {
                !healthManager.isAvailable -> "UNAVAILABLE"
                !healthManager.hasAllPermissions() -> "NEEDS_PERMISSION"
                else -> encodeStatSnapshot(healthManager.readStatSnapshot())
            }
            val payload = "$requestId|$body"
            Wearable.getMessageClient(applicationContext)
                .sendMessage(sourceNodeId, STAT_RESPONSE_PATH, payload.toByteArray(Charsets.UTF_8))
                .addOnCompleteListener { result ->
                    PipLog.d(TAG, "reply isSuccessful=${result.isSuccessful} exception=${result.exception}")
                }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
