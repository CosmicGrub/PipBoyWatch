package com.pipboywatch.app.data.health

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.pipboywatch.app.health.PhoneStatRelay
import com.pipboywatch.app.health.STAT_RESPONSE_PATH

private const val TAG = "PipBoyStatSync"

/** Receives the phone's reply to a STAT_REQUEST_PATH ping (see
 * PhoneStatRelay.requestFromPhone) and hands it to the same in-process
 * holder StatScreen observes. Sibling to PipBoyMessageListenerService,
 * same pattern. */
class StatMessageListenerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "onMessageReceived path=${messageEvent.path} from=${messageEvent.sourceNodeId}")
        if (messageEvent.path != STAT_RESPONSE_PATH) return
        PhoneStatRelay.onResponseReceived(String(messageEvent.data, Charsets.UTF_8))
    }
}
