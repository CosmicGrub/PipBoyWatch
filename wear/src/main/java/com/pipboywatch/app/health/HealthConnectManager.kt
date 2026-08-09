package com.pipboywatch.app.health

import android.content.Context
import android.util.Log
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

/** Permissions the STAT tab needs — read-only, nothing is ever written back. */
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
    val recentWorkouts: List<WorkoutSummary>
)

/** Thin wrapper around HealthConnectClient — read-only queries for the STAT tab. */
class HealthConnectManager(context: Context) {
    private val appContext = context.applicationContext

    // NOTE: on this Watch6 Classic (One UI Watch, Android 16), this reliably
    // returns false. Context.getSystemService("healthconnect") returns null
    // here even though `service list` on-device shows the raw Binder service
    // (android.health.connect.aidl.IHealthConnectService) registered — the
    // OEM framework isn't wiring it into SystemServiceRegistry. Confirmed by
    // walking the connect-client 1.1.0 bytecode: on SDK_INT >= 34 it always
    // routes through the platform system service with no app-level override,
    // so there's no supported client-side workaround. Verified the phone is
    // the more reliable Health Connect source going forward — see the
    // "Health Connect on-watch limitation" note in the design spec.
    val isAvailable: Boolean
        get() {
            val status = HealthConnectClient.getSdkStatus(appContext)
            Log.d("PipBoyHealth", "getSdkStatus=$status (SDK_AVAILABLE=${HealthConnectClient.SDK_AVAILABLE})")
            return status == HealthConnectClient.SDK_AVAILABLE
        }

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
            ReadRecordsRequest(
                HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )
        return response.records
            .flatMap { it.samples }
            .maxByOrNull { it.time }
            ?.beatsPerMinute
    }

    private suspend fun readTodayActiveMinutes(): Long {
        val c = client ?: return 0L
        val (start, end) = todayRange()
        val response = c.readRecords(
            ReadRecordsRequest(
                ExerciseSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )
        return response.records.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
    }

    private suspend fun readLastNightSleepMinutes(): Long {
        val c = client ?: return 0L
        val end = Instant.now()
        val start = end.minus(Duration.ofHours(24))
        val response = c.readRecords(
            ReadRecordsRequest(
                SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )
        return response.records.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
    }

    private suspend fun readRecentWorkouts(limit: Int = 5): List<WorkoutSummary> {
        val c = client ?: return emptyList()
        val end = Instant.now()
        val start = end.minus(Duration.ofDays(7))
        val response = c.readRecords(
            ReadRecordsRequest(
                ExerciseSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
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

    /** Used by the Perks "Step Streak" rule — true only if every one of the
     * last [days] days independently hit [thresholdSteps]. */
    suspend fun hasStepStreak(days: Int = 7, thresholdSteps: Long = 5000): Boolean {
        val c = client ?: return false
        val zone = ZoneId.systemDefault()
        for (dayOffset in 0 until days) {
            val day = LocalDate.now(zone).minusDays(dayOffset.toLong())
            val start = day.atStartOfDay(zone).toInstant()
            val end = day.plusDays(1).atStartOfDay(zone).toInstant()
            val response = c.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            val steps = response[StepsRecord.COUNT_TOTAL] ?: 0L
            if (steps < thresholdSteps) return false
        }
        return true
    }

    private fun todayRange(): Pair<Instant, Instant> {
        val zone = ZoneId.systemDefault()
        val startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant()
        return startOfDay to Instant.now()
    }
}
