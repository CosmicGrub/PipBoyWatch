package com.pipboywatch.app.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.pipboywatch.app.data.RunCheckpoint
import com.pipboywatch.app.data.SettingsStore
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class RunPoint(val lat: Double, val lng: Double, val altitude: Double)

data class RunLiveStats(
    val elapsedSeconds: Long,
    val distanceMeters: Double,
    val currentHeartRateBpm: Int?,
    val elevationGainMeters: Double
)

data class CompletedRun(
    val startTime: Long,
    val endTime: Long,
    val distanceMeters: Double,
    val elevationGainMeters: Double,
    val avgHeartRateBpm: Double?,
    val routePoints: List<RunPoint>
)

/**
 * Foreground-only run tracking (stops if you leave the MAP screen — no
 * foreground service in v1, an acceptable tradeoff for a "record now,
 * review later" first cut; see the design spec's MAP scope decision).
 * Heart rate comes straight from the raw TYPE_HEART_RATE sensor rather
 * than Health Connect, sidestepping this device's known HC gap entirely.
 */
class RunTracker(context: Context) {
    private val appContext = context.applicationContext
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(appContext)
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
    private val heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
    private val settings = SettingsStore(appContext)

    val hasBarometer: Boolean get() = pressureSensor != null
    val hasHeartRateSensor: Boolean get() = heartRateSensor != null

    private val _liveStats = MutableStateFlow<RunLiveStats?>(null)
    val liveStats: StateFlow<RunLiveStats?> = _liveStats.asStateFlow()

    private var startTimeMillis = 0L
    private var lastLocation: Location? = null
    private var distanceMeters = 0.0
    private var currentAltitude: Double? = null
    private var elevationGainMeters = 0.0
    private val heartRateSamples = mutableListOf<Int>()
    private var currentHeartRate: Int? = null
    private val routePoints = mutableListOf<RunPoint>()
    private var tickerJob: Job? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            lastLocation?.let { prev -> distanceMeters += prev.distanceTo(location) }
            lastLocation = location
            routePoints.add(RunPoint(location.latitude, location.longitude, currentAltitude ?: location.altitude))
            publish()
        }
    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_PRESSURE -> {
                    val altitude = SensorManager.getAltitude(
                        SensorManager.PRESSURE_STANDARD_ATMOSPHERE,
                        event.values[0]
                    ).toDouble()
                    val previous = currentAltitude
                    currentAltitude = altitude
                    if (previous != null && altitude > previous) {
                        elevationGainMeters += (altitude - previous)
                    }
                }
                Sensor.TYPE_HEART_RATE -> {
                    val bpm = event.values[0].toInt()
                    if (bpm > 0) {
                        currentHeartRate = bpm
                        heartRateSamples.add(bpm)
                    }
                }
            }
            publish()
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }

    /** Whether the last start() actually had Body Sensors permission to turn
     * on the heart-rate listener — MapScreen uses this to tell the user HR
     * won't be recorded rather than just silently showing "--". */
    var heartRateEnabledThisRun: Boolean = false
        private set

    /** Caller must have already confirmed ACCESS_FINE_LOCATION at runtime.
     * BODY_SENSORS is checked live here (not trusted from the caller) since
     * it's requested alongside location but can be independently denied —
     * or revoked later via system Settings without an app restart — and
     * registering the heart-rate listener without it throws SecurityException. */
    @SuppressLint("MissingPermission")
    fun start() {
        // Cancel any previous run's ticker before starting a new one — a
        // double-tap on START RUN before Compose recomposes past the button
        // would otherwise leak an extra unstructured 1Hz ticker forever,
        // surviving even a later stop().
        tickerJob?.cancel()

        startTimeMillis = System.currentTimeMillis()
        distanceMeters = 0.0
        elevationGainMeters = 0.0
        currentAltitude = null
        lastLocation = null
        heartRateSamples.clear()
        currentHeartRate = null
        routePoints.clear()

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3_000L).build()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        pressureSensor?.let { sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL) }

        val hasBodySensorsPermission = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.BODY_SENSORS
        ) == PackageManager.PERMISSION_GRANTED
        heartRateEnabledThisRun = hasBodySensorsPermission && heartRateSensor != null
        if (hasBodySensorsPermission) {
            heartRateSensor?.let { sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL) }
        }

        tickerJob = CoroutineScope(Dispatchers.Main).launch {
            var ticksSinceCheckpoint = 0
            while (isActive) {
                publish()
                // Checkpoint every ~10s, not every tick — DataStore writes
                // are cheap but there's no reason to do one a second. This
                // is a process-death safety net, not the source of truth
                // for the live UI (that's _liveStats above); see stop()
                // and RunRepository.salvageInterruptedRun() for the other
                // half of this — normal navigate-away already stops
                // tracking cleanly via MapScreen's DisposableEffect, this
                // is specifically for the OS killing the process outright,
                // which gets no such callback.
                ticksSinceCheckpoint++
                if (ticksSinceCheckpoint >= 10) {
                    ticksSinceCheckpoint = 0
                    settings.saveRunCheckpoint(
                        RunCheckpoint(
                            startTime = startTimeMillis,
                            distanceMeters = distanceMeters,
                            elevationGainMeters = elevationGainMeters,
                            avgHeartRateBpm = heartRateSamples.takeIf { it.isNotEmpty() }?.average()
                        )
                    )
                }
                delay(1_000)
            }
        }
        publish()
    }

    fun stop(): CompletedRun {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        sensorManager.unregisterListener(sensorListener)
        tickerJob?.cancel()

        val completed = CompletedRun(
            startTime = startTimeMillis,
            endTime = System.currentTimeMillis(),
            distanceMeters = distanceMeters,
            elevationGainMeters = elevationGainMeters,
            avgHeartRateBpm = heartRateSamples.takeIf { it.isNotEmpty() }?.average(),
            routePoints = routePoints.toList()
        )
        _liveStats.value = null
        // The caller is about to save this properly via RunRepository —
        // the checkpoint's only job was surviving a process death that
        // didn't happen, so it's stale as of now regardless of outcome.
        CoroutineScope(Dispatchers.IO).launch { settings.clearRunCheckpoint() }
        return completed
    }

    private fun publish() {
        _liveStats.value = RunLiveStats(
            elapsedSeconds = (System.currentTimeMillis() - startTimeMillis) / 1000,
            distanceMeters = distanceMeters,
            currentHeartRateBpm = currentHeartRate,
            elevationGainMeters = elevationGainMeters
        )
    }
}
