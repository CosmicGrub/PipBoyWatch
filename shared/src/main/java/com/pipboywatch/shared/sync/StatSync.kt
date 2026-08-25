package com.pipboywatch.shared.sync

import com.pipboywatch.shared.health.StatSnapshot
import com.pipboywatch.shared.health.WorkoutSummary
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

/**
 * Wire paths and encoding for the phone -> watch STAT relay. The watch
 * can't read Health Connect on this hardware (see HealthConnectManager),
 * so it asks the phone for a snapshot instead. Both the encoder (phone)
 * and decoder (watch) live here now — this used to be hand-duplicated
 * across the two modules with a comment warning to keep them in sync by
 * hand; that's exactly the risk a shared module removes.
 *
 * STAT_REQUEST_PATH payload: the request id as raw UTF-8 bytes (see
 * newRequestId()) — nothing else needed, the phone just echoes it back.
 *
 * STAT_RESPONSE_PATH payload: "requestId|body", where requestId is either
 * the id from the request being answered, or "" for an unsolicited push
 * (the phone's "Sync to Watch Now" button isn't replying to any request).
 * body: "OK|steps|activeMinutes|heartRateOrNeg1|sleepMinutes|streak0or1|workouts"
 * workouts: "title,startEpochMillis,durationMinutes" triples joined by ';'
 * Sentinels in place of "OK|...": "UNAVAILABLE", "NEEDS_PERMISSION"
 *
 * The id exists so a stale reply can't be mistaken for the answer to a
 * newer request — see StatDecodeReply and PhoneStatRelay's outstanding-id
 * tracking on the watch side.
 */
const val STAT_REQUEST_PATH = "/pipboy/stat/request"
const val STAT_RESPONSE_PATH = "/pipboy/stat"

/** Not a security token, just a collision-resistant-enough tag for
 * matching a reply to the request that triggered it within one process's
 * lifetime — timestamp+counter is plenty. Atomic because
 * PendingRequestTracker calls this once per channel per mint(), and
 * different channels' requests can legitimately be minted from different
 * threads at once — a plain var here would let two concurrent increments
 * race and collide on the same id. */
private val requestCounter = AtomicInteger(0)

fun newRequestId(): String {
    return "${System.currentTimeMillis()}-${requestCounter.incrementAndGet()}"
}

fun encodeStatSnapshot(snapshot: StatSnapshot): String {
    val workoutsPart = snapshot.recentWorkouts.joinToString(";") { w ->
        val safeTitle = w.title.replace('|', ' ').replace(';', ' ').replace(',', ' ').trim()
        "$safeTitle,${w.startTime.toEpochMilli()},${w.durationMinutes}"
    }
    return listOf(
        "OK",
        snapshot.steps.toString(),
        snapshot.activeMinutes.toString(),
        (snapshot.latestHeartRateBpm ?: -1L).toString(),
        snapshot.sleepMinutesLastNight.toString(),
        if (snapshot.hasStepStreak == true) "1" else "0",
        workoutsPart
    ).joinToString("|")
}

sealed interface StatDecodeResult {
    data class Success(val snapshot: StatSnapshot) : StatDecodeResult
    data object Unavailable : StatDecodeResult
    data object NeedsPermission : StatDecodeResult
}

/** A decoded STAT_RESPONSE_PATH message: which request it's answering
 * (empty for an unsolicited push) plus the decoded body. */
data class StatReply(val requestId: String, val result: StatDecodeResult)

/** Splits the "requestId|body" envelope and decodes the body. Only splits
 * on the FIRST '|' — the body itself is pipe-delimited, so a naive full
 * split would misalign every field after it. */
fun decodeStatReply(payload: String): StatReply {
    val requestId = payload.substringBefore('|', missingDelimiterValue = "")
    val body = payload.substringAfter('|', missingDelimiterValue = payload)
    return StatReply(requestId, decodeStatBody(body))
}

private fun decodeStatBody(body: String): StatDecodeResult {
    if (body == "UNAVAILABLE") return StatDecodeResult.Unavailable
    if (body == "NEEDS_PERMISSION") return StatDecodeResult.NeedsPermission

    val parts = body.split("|")
    if (parts.size < 7 || parts[0] != "OK") return StatDecodeResult.Unavailable

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

    return StatDecodeResult.Success(
        StatSnapshot(
            steps = steps,
            activeMinutes = activeMinutes,
            latestHeartRateBpm = heartRate,
            sleepMinutesLastNight = sleepMinutes,
            recentWorkouts = workouts,
            hasStepStreak = hasStreak
        )
    )
}
