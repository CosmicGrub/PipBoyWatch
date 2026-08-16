package com.pipboywatch.sync

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.pipboywatch.health.HealthConnectManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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
        Log.d(TAG, "onMessageReceived path=${messageEvent.path} from=${messageEvent.sourceNodeId}")
        if (messageEvent.path != STAT_REQUEST_PATH) return

        val sourceNodeId = messageEvent.sourceNodeId
        serviceScope.launch {
            // The Wear Data Layer's AppKey routing already restricts
            // delivery to apps sharing this app's package+signature, but
            // that's weaker on a debug-signed build than a real release
            // key — this is a cheap extra check that the request actually
            // came from a currently-connected paired node before we hand
            // it real Health Connect data.
            if (!isFromTrustedNode(sourceNodeId)) {
                Log.w(TAG, "Ignoring stat request from untrusted node $sourceNodeId")
                return@launch
            }
            val payload = when {
                !healthManager.isAvailable -> "UNAVAILABLE"
                !healthManager.hasAllPermissions() -> "NEEDS_PERMISSION"
                else -> encodeStatSnapshot(healthManager.readStatSnapshot())
            }
            Wearable.getMessageClient(applicationContext)
                .sendMessage(sourceNodeId, STAT_RESPONSE_PATH, payload.toByteArray(Charsets.UTF_8))
                .addOnCompleteListener { result ->
                    Log.d(TAG, "reply isSuccessful=${result.isSuccessful} exception=${result.exception}")
                }
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
}
