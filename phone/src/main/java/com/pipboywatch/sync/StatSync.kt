package com.pipboywatch.sync

import com.pipboywatch.health.StatSnapshot

/**
 * Wire paths and encoding for the phone -> watch STAT relay. The watch
 * can't read Health Connect on this hardware (see wear's
 * HealthConnectManager), so it asks the phone for a snapshot instead.
 *
 * Kept as a plain pipe-delimited string, same convention as the existing
 * Notes relay (NOTE_PATH) elsewhere in this project — no shared module
 * between wear/ and phone/, so the matching decoder lives in wear's
 * PhoneStatRelay.kt; keep the two in sync by hand if this format changes.
 *
 * Format: "OK|steps|activeMinutes|heartRateOrNeg1|sleepMinutes|streak0or1|workouts"
 * workouts: "title,startEpochMillis,durationMinutes" triples joined by ';'
 * Sentinels in place of "OK|...": "UNAVAILABLE", "NEEDS_PERMISSION"
 */
const val STAT_REQUEST_PATH = "/pipboy/stat/request"
const val STAT_RESPONSE_PATH = "/pipboy/stat"

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
        if (snapshot.hasStepStreak) "1" else "0",
        workoutsPart
    ).joinToString("|")
}
