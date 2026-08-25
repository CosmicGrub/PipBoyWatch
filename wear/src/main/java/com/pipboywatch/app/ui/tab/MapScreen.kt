package com.pipboywatch.app.ui.tab

import android.Manifest
import android.content.pm.PackageManager
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.pipboywatch.app.data.RunEntity
import com.pipboywatch.app.map.RunLiveStats
import com.pipboywatch.app.map.RunRepository
import com.pipboywatch.app.map.RunTracker
import com.pipboywatch.app.map.formatElapsed
import com.pipboywatch.app.map.formatPace
import com.pipboywatch.app.map.paceSecondsPerKm
import com.pipboywatch.app.ui.components.ActionRow
import com.pipboywatch.app.ui.components.CrtCard
import com.pipboywatch.app.ui.components.PipBoyTabScaffold
import kotlinx.coroutines.launch

@Composable
fun MapScreen() {
    val context = LocalContext.current
    val runTracker = remember { RunTracker(context) }
    val runRepository = remember { RunRepository(context) }
    val coroutineScope = rememberCoroutineScope()

    var isTracking by remember { mutableStateOf(false) }
    var justSalvagedRun by remember { mutableStateOf(false) }
    val liveStats by runTracker.liveStats.collectAsState()
    val pastRuns by runRepository.observeRuns().collectAsState(initial = emptyList())

    // If RunTracker left a checkpoint from a run that never got a proper
    // STOP RUN (process death, or the user navigated away mid-run — see
    // RunRepository.salvageInterruptedRun()), recover it as a completed
    // run now rather than silently losing it. Runs before the user can
    // start a new one so there's no ambiguity about which run a fresh
    // checkpoint would belong to.
    LaunchedEffect(Unit) {
        if (runRepository.salvageInterruptedRun()) {
            justSalvagedRun = true
        }
    }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    // Body Sensors is requested alongside location but can be independently
    // denied (or later revoked via system Settings) — location is the only
    // hard requirement for MAP, so a denial degrades to "no heart rate this
    // run" rather than blocking the whole tab. We don't need to track the
    // grant result ourselves here: RunTracker checks it live on every
    // start() (see heartRateEnabledThisRun below), which is more accurate
    // than caching it in this screen's state (handles a mid-session revoke
    // without an app restart).
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasLocationPermission = results[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }

    // Foreground-only tracking (see RunTracker doc comment) — stop cleanly
    // if this screen is ever torn down mid-run rather than leak sensors.
    DisposableEffect(Unit) {
        onDispose {
            if (isTracking) runTracker.stop()
        }
    }

    PipBoyTabScaffold(title = "MAP") {
        if (justSalvagedRun) {
            CrtCard(title = "RECOVERED") {
                Text(
                    "A run in progress was interrupted and has been saved to Past Runs below.",
                    color = MaterialTheme.colors.primary,
                    style = MaterialTheme.typography.body2
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        when {
            !hasLocationPermission -> {
                CrtCard(title = "ACCESS REQUIRED") {
                    Text(
                        "Grant location access to track runs.",
                        color = MaterialTheme.colors.primary,
                        style = MaterialTheme.typography.body2
                    )
                    Spacer(Modifier.height(8.dp))
                    ActionRow("GRANT") {
                        permissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BODY_SENSORS)
                        )
                    }
                }
            }
            isTracking -> {
                LiveRunCard(liveStats, heartRateEnabled = runTracker.heartRateEnabledThisRun)
                Spacer(Modifier.height(8.dp))
                ActionRow("STOP RUN") {
                    val completed = runTracker.stop()
                    isTracking = false
                    coroutineScope.launch { runRepository.saveRun(completed) }
                }
            }
            else -> {
                ActionRow("START RUN") {
                    runTracker.start()
                    isTracking = true
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader("PAST RUNS")
        if (pastRuns.isEmpty()) {
            CrtCard { Text("No runs recorded yet.", color = MaterialTheme.colors.primary, style = MaterialTheme.typography.body2) }
        } else {
            val bestPaceRunId = pastRuns
                .mapNotNull { run -> paceSecondsPerKm(run.distanceMeters, (run.endTime - run.startTime) / 1000)?.let { run.id to it } }
                .minByOrNull { it.second }?.first
            val bestClimbRunId = pastRuns.maxByOrNull { it.elevationGainMeters }?.id

            pastRuns.forEach { run ->
                PastRunRow(
                    run = run,
                    isBestPace = run.id == bestPaceRunId,
                    isBestClimb = run.id == bestClimbRunId && run.elevationGainMeters > 0
                )
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption2,
        color = MaterialTheme.colors.primary.copy(alpha = 0.6f),
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun LiveRunCard(stats: RunLiveStats?, heartRateEnabled: Boolean) {
    CrtCard(title = "TRACKING") {
        Text("Time: ${formatElapsed(stats?.elapsedSeconds ?: 0)}", color = MaterialTheme.colors.primary, style = MaterialTheme.typography.body2)
        Text(
            "Distance: %.2f km".format((stats?.distanceMeters ?: 0.0) / 1000.0),
            color = MaterialTheme.colors.primary,
            style = MaterialTheme.typography.body2
        )
        Text(
            "Pace: ${formatPace(paceSecondsPerKm(stats?.distanceMeters ?: 0.0, stats?.elapsedSeconds ?: 0))}",
            color = MaterialTheme.colors.primary,
            style = MaterialTheme.typography.body2
        )
        if (heartRateEnabled) {
            Text(
                "HR: ${stats?.currentHeartRateBpm?.let { "$it bpm" } ?: "--"}",
                color = MaterialTheme.colors.primary,
                style = MaterialTheme.typography.body2
            )
        } else {
            Text(
                "HR: unavailable (grant Body Sensors)",
                color = MaterialTheme.colors.primary.copy(alpha = 0.6f),
                style = MaterialTheme.typography.caption1
            )
        }
        Text(
            "Climb: %.0f m".format(stats?.elevationGainMeters ?: 0.0),
            color = MaterialTheme.colors.primary,
            style = MaterialTheme.typography.body2
        )
    }
}

@Composable
private fun PastRunRow(run: RunEntity, isBestPace: Boolean, isBestClimb: Boolean) {
    val durationSeconds = (run.endTime - run.startTime) / 1000
    val pace = paceSecondsPerKm(run.distanceMeters, durationSeconds)
    CrtCard(title = DateUtils.getRelativeTimeSpanString(run.startTime).toString()) {
        Text(
            "%.2f km in %s".format(run.distanceMeters / 1000.0, formatElapsed(durationSeconds)),
            color = MaterialTheme.colors.primary,
            style = MaterialTheme.typography.body2
        )
        Text(
            "Pace ${formatPace(pace)} - Climb %.0fm".format(run.elevationGainMeters),
            color = MaterialTheme.colors.primary,
            style = MaterialTheme.typography.caption1
        )
        if (isBestPace) {
            Text("* BEST PACE", color = MaterialTheme.colors.primary, style = MaterialTheme.typography.caption2)
        }
        if (isBestClimb) {
            Text("* BEST CLIMB", color = MaterialTheme.colors.primary, style = MaterialTheme.typography.caption2)
        }
    }
}
