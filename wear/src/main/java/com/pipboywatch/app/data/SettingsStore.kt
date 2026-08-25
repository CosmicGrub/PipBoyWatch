package com.pipboywatch.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.settingsDataStore by preferencesDataStore(name = "pipboy_settings")
private val LAST_INV_RESET_EPOCH_DAY = longPreferencesKey("last_inv_reset_epoch_day")

private val RUN_CHECKPOINT_ACTIVE = booleanPreferencesKey("run_checkpoint_active")
private val RUN_CHECKPOINT_START_TIME = longPreferencesKey("run_checkpoint_start_time")
private val RUN_CHECKPOINT_DISTANCE_METERS = doublePreferencesKey("run_checkpoint_distance_meters")
private val RUN_CHECKPOINT_ELEVATION_GAIN_METERS = doublePreferencesKey("run_checkpoint_elevation_gain_meters")
// -1.0 sentinel for "no heart rate data yet" — DataStore has no nullable
// primitive type, and a dedicated boolean key for one optional double
// felt like more ceremony than this needs.
private val RUN_CHECKPOINT_AVG_HEART_RATE = doublePreferencesKey("run_checkpoint_avg_heart_rate")

data class RunCheckpoint(
    val startTime: Long,
    val distanceMeters: Double,
    val elevationGainMeters: Double,
    val avgHeartRateBpm: Double?
)

/** Small key-value settings that don't warrant a Room table. */
class SettingsStore(context: Context) {
    private val appContext = context.applicationContext

    suspend fun getLastInvResetEpochDay(): Long? =
        appContext.settingsDataStore.data.first()[LAST_INV_RESET_EPOCH_DAY]

    suspend fun setLastInvResetEpochDay(epochDay: Long) {
        appContext.settingsDataStore.edit { it[LAST_INV_RESET_EPOCH_DAY] = epochDay }
    }

    /**
     * A periodically-refreshed snapshot of the run RunTracker is currently
     * recording — not the full route/heart-rate sample lists, just enough
     * to salvage a run as "completed as of the last checkpoint" if the
     * process is killed before the user taps STOP RUN. MAP's normal
     * "stops on screen teardown" behavior (DisposableEffect) already
     * handles a clean navigate-away; this is specifically for process
     * death, which gets no such callback.
     */
    suspend fun saveRunCheckpoint(checkpoint: RunCheckpoint) {
        appContext.settingsDataStore.edit {
            it[RUN_CHECKPOINT_ACTIVE] = true
            it[RUN_CHECKPOINT_START_TIME] = checkpoint.startTime
            it[RUN_CHECKPOINT_DISTANCE_METERS] = checkpoint.distanceMeters
            it[RUN_CHECKPOINT_ELEVATION_GAIN_METERS] = checkpoint.elevationGainMeters
            it[RUN_CHECKPOINT_AVG_HEART_RATE] = checkpoint.avgHeartRateBpm ?: -1.0
        }
    }

    suspend fun getRunCheckpoint(): RunCheckpoint? {
        val prefs = appContext.settingsDataStore.data.first()
        if (prefs[RUN_CHECKPOINT_ACTIVE] != true) return null
        val avgHr = prefs[RUN_CHECKPOINT_AVG_HEART_RATE]
        return RunCheckpoint(
            startTime = prefs[RUN_CHECKPOINT_START_TIME] ?: return null,
            distanceMeters = prefs[RUN_CHECKPOINT_DISTANCE_METERS] ?: 0.0,
            elevationGainMeters = prefs[RUN_CHECKPOINT_ELEVATION_GAIN_METERS] ?: 0.0,
            avgHeartRateBpm = avgHr?.takeIf { it >= 0.0 }
        )
    }

    suspend fun clearRunCheckpoint() {
        appContext.settingsDataStore.edit { it[RUN_CHECKPOINT_ACTIVE] = false }
    }
}
