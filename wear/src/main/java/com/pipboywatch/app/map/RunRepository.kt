package com.pipboywatch.app.map

import android.content.Context
import com.pipboywatch.app.data.PipBoyDatabase
import com.pipboywatch.app.data.RunEntity
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

class RunRepository(context: Context) {
    private val dao = PipBoyDatabase.getInstance(context.applicationContext).runDao()

    fun observeRuns(): Flow<List<RunEntity>> = dao.observeAll()

    suspend fun saveRun(run: CompletedRun) {
        dao.insert(
            RunEntity(
                startTime = run.startTime,
                endTime = run.endTime,
                distanceMeters = run.distanceMeters,
                elevationGainMeters = run.elevationGainMeters,
                avgHeartRateBpm = run.avgHeartRateBpm,
                routePointsJson = encodeRoute(run.routePoints)
            )
        )
    }

    private fun encodeRoute(points: List<RunPoint>): String {
        val array = JSONArray()
        points.forEach { point ->
            array.put(
                JSONObject()
                    .put("lat", point.lat)
                    .put("lng", point.lng)
                    .put("alt", point.altitude)
            )
        }
        return array.toString()
    }
}

/** Pace in seconds-per-km, or null if there's no meaningful distance yet. */
fun paceSecondsPerKm(distanceMeters: Double, elapsedSeconds: Long): Double? {
    if (distanceMeters < 10.0) return null
    val km = distanceMeters / 1000.0
    return elapsedSeconds / km
}

fun formatPace(secondsPerKm: Double?): String {
    if (secondsPerKm == null || secondsPerKm.isNaN() || secondsPerKm.isInfinite()) return "--:--/km"
    val totalSeconds = secondsPerKm.toLong()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d/km".format(minutes, seconds)
}

fun formatElapsed(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
