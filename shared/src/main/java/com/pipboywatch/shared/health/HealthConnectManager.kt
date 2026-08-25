package com.pipboywatch.shared.health

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
 * Shared between wear/ and phone/ — same read-only Health Connect queries
 * either side needs. On the watch (at least this Galaxy Watch6 Classic,
 * SM-R965U, One UI Watch/Android 16), isAvailable reliably returns false:
 * the raw Binder service is registered (`adb shell service list` shows
 * healthconnect: [android.health.connect.aidl.IHealthConnectService]) but
 * the OEM framework never wires it into SystemServiceRegistry, so
 * Context.getSystemService("healthconnect") returns null and
 * HealthConnectClient.getSdkStatus() never reaches SDK_AVAILABLE — not
 * fixable from app code. Phone OEM builds reliably wire up the real
 * integration, which is why the phone-relay path (see :wear's
 * PhoneStatRelay / :phone's StatRequestListenerService) exists at all.
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
    val recentWorkouts: List<WorkoutSummary>,
    // Null when not computed for this snapshot — the on-watch path doesn't
    // bother computing it just to display steps/HR/sleep (an extra 7-day
    // aggregate query not worth doing on every STAT load); PerksRepository
    // calls hasStepStreak() separately for that. The phone-relay path
    // always populates a real value here.
    val hasStepStreak: Boolean? = null
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

    /** Used by the Perks "Step Streak" rule and the phone-relay wire format
     * — true only if every one of the last [days] days independently hit
     * [thresholdSteps]. Excludes an in-progress "today" from the count
     * (dayOffset starts at 1, not 0) since today's step total isn't final
     * yet and counting it as a full day would inflate the streak. */
    suspend fun hasStepStreak(days: Int = 7, thresholdSteps: Long = 5000): Boolean {
        val c = client ?: return false
        val zone = ZoneId.systemDefault()
        for (dayOffset in 1..days) {
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
