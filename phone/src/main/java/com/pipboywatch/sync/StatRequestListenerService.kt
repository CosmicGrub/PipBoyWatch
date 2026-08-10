package com.pipboywatch.sync

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.pipboywatch.health.HealthConnectManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
        Log.d(TAG, "onMessageReceived path=${messageEvent.path} from=${messageEvent.sourceNodeId}")
        if (messageEvent.path != STAT_REQUEST_PATH) return

        val sourceNodeId = messageEvent.sourceNodeId
        serviceScope.launch {
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
}
