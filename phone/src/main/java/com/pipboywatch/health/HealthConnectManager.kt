package com.pipboywatch.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Phone-side counterpart to wear's HealthConnectManager — same shape, same
 * read-only queries, but this one is expected to actually work: phone OEM
 * builds reliably wire up the real com.google.android.apps.healthdata
 * integration (unlike this project's Galaxy Watch, where the platform
 * system service isn't registered — see wear's HealthConnectManager for the
 * root cause). Samsung Health writes steps/heart-rate/sleep/exercise into
 * Health Connect on the phone, so a standard read here picks up
 * Samsung-Health-sourced data with no separate Samsung SDK needed.
 *
 * Duplicated here rather than shared because this project has no common
 * module between wear/ and phone/ — same call this codebase already made
 * for NOTE_PATH (see PipBoyMessageListenerService / SendNoteActivity).
 */
val HEALTH_PERMISSIONS: Set<String> = setOf(
    HealthPermission.getReadPermission(StepsRecord::class),
    HealthPermission.getReadPermission(HeartRateRecord::class),
    HealthPermission.getReadPermission(SleepSessionRecord::class),
    HealthPermission.getReadPermission(ExerciseSessionRecord::class)
)

data class WorkoutSummary(
    val title: String,
    val startTime: Instant,
    val durationMinutes: Long
)

data class StatSnapshot(
    val steps: Long,
    val activeMinutes: Long,
    val latestHeartRateBpm: Long?,
    val sleepMinutesLastNight: Long,
    val hasStepStreak: Boolean,
    val recentWorkouts: List<WorkoutSummary>
)

class HealthConnectManager(context: Context) {
    private val appContext = context.applicationContext

    val isAvailable: Boolean
        get() = HealthConnectClient.getSdkStatus(appContext) == HealthConnectClient.SDK_AVAILABLE

    private val client: HealthConnectClient? by lazy {
        if (isAvailable) HealthConnectClient.getOrCreate(appContext) else null
    }

    suspend fun hasAllPermissions(): Boolean {
        val c = client ?: return false
        val granted = c.permissionController.getGrantedPermissions()
        return granted.containsAll(HEALTH_PERMISSIONS)
    }

    suspend fun readStatSnapshot(): StatSnapshot {
        return StatSnapshot(
            steps = readTodaySteps(),
            activeMinutes = readTodayActiveMinutes(),
            latestHeartRateBpm = readLatestHeartRate(),
            sleepMinutesLastNight = readLastNightSleepMinutes(),
            hasStepStreak = hasStepStreak(),
            recentWorkouts = readRecentWorkouts()
        )
    }

    private suspend fun readTodaySteps(): Long {
        val c = client ?: return 0L
        val (start, end) = todayRange()
        val response = c.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )
        return response[StepsRecord.COUNT_TOTAL] ?: 0L
    }

    private suspend fun readLatestHeartRate(): Long? {
        val c = client ?: return null
        val (start, end) = todayRange()
        val response = c.readRecords(
            ReadRecordsRequest(HeartRateRecord::class, timeRangeFilter = TimeRangeFilter.between(start, end))
        )
        return response.records.flatMap { it.samples }.maxByOrNull { it.time }?.beatsPerMinute
    }

    private suspend fun readTodayActiveMinutes(): Long {
        val c = client ?: return 0L
        val (start, end) = todayRange()
        val response = c.readRecords(
            ReadRecordsRequest(ExerciseSessionRecord::class, timeRangeFilter = TimeRangeFilter.between(start, end))
        )
        return response.records.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
    }

    private suspend fun readLastNightSleepMinutes(): Long {
        val c = client ?: return 0L
        val end = Instant.now()
        val start = end.minus(Duration.ofHours(24))
        val response = c.readRecords(
            ReadRecordsRequest(SleepSessionRecord::class, timeRangeFilter = TimeRangeFilter.between(start, end))
        )
        return response.records.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
    }

    private suspend fun readRecentWorkouts(limit: Int = 5): List<WorkoutSummary> {
        val c = client ?: return emptyList()
        val end = Instant.now()
        val start = end.minus(Duration.ofDays(7))
        val response = c.readRecords(
            ReadRecordsRequest(ExerciseSessionRecord::class, timeRangeFilter = TimeRangeFilter.between(start, end))
        )
        return response.records
            .sortedByDescending { it.startTime }
            .take(limit)
            .map {
                WorkoutSummary(
                    title = it.title?.takeIf { t -> t.isNotBlank() } ?: it.exerciseType.toString(),
                    startTime = it.startTime,
                    durationMinutes = Duration.between(it.startTime, it.endTime).toMinutes()
                )
            }
    }

    private suspend fun hasStepStreak(days: Int = 7, thresholdSteps: Long = 5000): Boolean {
        val c = client ?: return false
        val zone = ZoneId.systemDefault()
        for (dayOffset in 0 until days) {
            val day = LocalDate.now(zone).minusDays(dayOffset.toLong())
            val start = day.atStartOfDay(zone).toInstant()
            val end = day.plusDays(1).atStartOfDay(zone).toInstant()
            val response = c.aggregate(
                AggregateRequest(metrics = setOf(StepsRecord.COUNT_TOTAL), timeRangeFilter = TimeRangeFilter.between(start, end))
            )
            if ((response[StepsRecord.COUNT_TOTAL] ?: 0L) < thresholdSteps) return false
        }
        return true
    }

    private fun todayRange(): Pair<Instant, Instant> {
        val zone = ZoneId.systemDefault()
        return LocalDate.now(zone).atStartOfDay(zone).toInstant() to Instant.now()
    }
}
