package com.pipboywatch.shared.sync

import android.content.Context
import com.google.android.gms.wearable.Wearable
import com.pipboywatch.shared.log.PipLog
import kotlinx.coroutines.tasks.await

private const val TAG = "PipBoyTrustedNode"

/**
 * Defense-in-depth on top of the Wear Data Layer's own AppKey-based
 * routing (which already restricts delivery to apps sharing this app's
 * package+signature — weaker protection on a debug-signed build than a
 * real release key). Confirms a received message's sourceNodeId is a
 * currently-connected paired node before acting on its payload. Used by
 * every WearableListenerService in both modules — was triplicated by hand
 * across three files before this module existed.
 */
suspend fun isFromTrustedNode(context: Context, sourceNodeId: String): Boolean {
    return try {
        Wearable.getNodeClient(context).connectedNodes.await().any { it.id == sourceNodeId }
    } catch (e: Exception) {
        PipLog.w(TAG, "isFromTrustedNode check failed", e)
        false
    }
}
