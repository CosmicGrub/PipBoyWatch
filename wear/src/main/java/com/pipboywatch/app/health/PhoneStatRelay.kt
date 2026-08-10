package com.pipboywatch.app.health

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

private const val TAG = "PipBoyStatSync"

/** Same paths/format as phone's StatSync.kt — no shared module between
 * wear/ and phone/, so keep these two in sync by hand if the format
 * changes (same convention this project already uses for NOTE_PATH). */
const val STAT_REQUEST_PATH = "/pipboy/stat/request"
const val STAT_RESPONSE_PATH = "/pipboy/stat"

sealed interface PhoneStatResult {
    data class Success(val snapshot: StatSnapshot) : PhoneStatResult
    data object Unavailable : PhoneStatResult
    data object NeedsPermission : PhoneStatResult
}

/**
 * Shared holder so StatMessageListenerService (receives the phone's reply)
 * and StatScreen (sends the request, observes the result) can talk without
 * IPC — both run in the same app process. Mirrors MediaSessionHolder's
 * pattern for RADIO.
 */
object PhoneStatRelay {
    private val _result = MutableStateFlow<PhoneStatResult?>(null)
    val result: StateFlow<PhoneStatResult?> = _result.asStateFlow()

    fun clear() {
        _result.value = null
    }

    fun onResponseReceived(payload: String) {
        Log.d(TAG, "onResponseReceived payload=$payload")
        _result.value = decodeStatSnapshot(payload)
    }

    /** Pings every connected phone node; the reply (if any) arrives later
     * via StatMessageListenerService -> onResponseReceived. */
    suspend fun requestFromPhone(context: Context) {
        try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            Log.d(TAG, "requestFromPhone nodes=${nodes.map { "${it.displayName}(${it.id}, nearby=${it.isNearby})" }}")
            if (nodes.isEmpty()) {
                _result.value = PhoneStatResult.Unavailable
                return
            }
            val messageClient = Wearable.getMessageClient(context)
            nodes.forEach { node ->
                messageClient.sendMessage(node.id, STAT_REQUEST_PATH, ByteArray(0))
                    .addOnCompleteListener { result ->
                        Log.d(TAG, "sendMessage to ${node.id} isSuccessful=${result.isSuccessful} exception=${result.exception}")
                    }
            }
        } catch (e: Exception) {
            Log.d(TAG, "requestFromPhone failed", e)
            _result.value = PhoneStatResult.Unavailable
        }
    }
}

private fun decodeStatSnapshot(payload: String): PhoneStatResult {
    if (payload == "UNAVAILABLE") return PhoneStatResult.Unavailable
    if (payload == "NEEDS_PERMISSION") return PhoneStatResult.NeedsPermission

    val parts = payload.split("|")
    if (parts.size < 7 || parts[0] != "OK") return PhoneStatResult.Unavailable

    val steps = parts[1].toLongOrNull() ?: 0L
    val activeMinutes = parts[2].toLongOrNull() ?: 0L
    val heartRate = parts[3].toLongOrNull()?.takeIf { it >= 0 }
    val sleepMinutes = parts[4].toLongOrNull() ?: 0L
    val hasStreak = parts[5] == "1"
    val workouts = parts[6]
        .takeIf { it.isNotBlank() }
        ?.split(";")
        ?.mapNotNull { entry ->
            val fields = entry.split(",")
            if (fields.size != 3) return@mapNotNull null
            val start = fields[1].toLongOrNull() ?: return@mapNotNull null
            val duration = fields[2].toLongOrNull() ?: return@mapNotNull null
            WorkoutSummary(title = fields[0], startTime = Instant.ofEpochMilli(start), durationMinutes = duration)
        }
        ?: emptyList()

    // hasStreak isn't part of StatSnapshot's shape (that struct is shared
    // with the on-watch HealthConnectManager, which has no streak field) —
    // carry it via the tiny side-channel below instead.
    PhoneStreakCache.update(hasStreak)

    return PhoneStatResult.Success(
        StatSnapshot(
            steps = steps,
            activeMinutes = activeMinutes,
            latestHeartRateBpm = heartRate,
            sleepMinutesLastNight = sleepMinutes,
            recentWorkouts = workouts
        )
    )
}

/** Perks' Step Streak check is a fallback consumer of the same relay
 * response — kept as a tiny separate cache rather than widening
 * StatSnapshot (which is also the on-watch HealthConnectManager's shape,
 * and that source has no equivalent "streak" field to carry it in). */
object PhoneStreakCache {
    private val _hasStreak = MutableStateFlow<Boolean?>(null)
    val hasStreak: StateFlow<Boolean?> = _hasStreak.asStateFlow()

    fun update(value: Boolean) {
        _hasStreak.value = value
    }
}
