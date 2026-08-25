package com.pipboywatch.app.data.health

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.pipboywatch.app.health.PhoneStatRelay
import com.pipboywatch.shared.sync.STAT_RESPONSE_PATH
import com.pipboywatch.shared.sync.isFromTrustedNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val TAG = "PipBoyStatSync"

/** Receives the phone's reply to a STAT_REQUEST_PATH ping (see
 * PhoneStatRelay.requestFromPhone) and hands it to the same in-process
 * holder StatScreen observes. Sibling to PipBoyMessageListenerService,
 * same pattern. */
class StatMessageListenerService : WearableListenerService() {
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "onMessageReceived path=${messageEvent.path} from=${messageEvent.sourceNodeId}")
        if (messageEvent.path != STAT_RESPONSE_PATH) return

        val sourceNodeId = messageEvent.sourceNodeId
        val payload = String(messageEvent.data, Charsets.UTF_8)
        serviceScope.launch {
            if (!isFromTrustedNode(applicationContext, sourceNodeId)) {
                Log.w(TAG, "Ignoring stat reply from untrusted node $sourceNodeId")
                return@launch
            }
            PhoneStatRelay.onResponseReceived(payload)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
