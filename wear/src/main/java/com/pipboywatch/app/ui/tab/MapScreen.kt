package com.pipboywatch.app.ui.tab

import android.Manifest
import android.content.pm.PackageManager
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.pipboywatch.app.data.RunEntity
import com.pipboywatch.app.map.RunLiveStats
import com.pipboywatch.app.map.RunRepository
import com.pipboywatch.app.map.RunTracker
import com.pipboywatch.app.map.formatElapsed
import com.pipboywatch.app.map.formatPace
import com.pipboywatch.app.map.paceSecondsPerKm
import com.pipboywatch.app.ui.components.CrtCard
import com.pipboywatch.app.ui.components.ScanlineOverlay
import com.pipboywatch.app.ui.components.screenContentPadding
import kotlinx.coroutines.launch

@Composable
fun MapScreen() {
    val context = LocalContext.current
    val runTracker = remember { RunTracker(context) }
    val runRepository = remember { RunRepository(context) }
    val coroutineScope = rememberCoroutineScope()

    var isTracking by remember { mutableStateOf(false) }
    val liveStats by runTracker.liveStats.collectAsState()
    val pastRuns by runRepository.observeRuns().collectAsState(initial = emptyList())

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

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

    val scrollState = rememberScrollState()
    val focusRequester = rememberActiveFocusRequester()
    val rotaryBehavior = RotaryScrollableDefaults.behavior(scrollableState = scrollState)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .rotaryScrollable(rotaryBehavior, focusRequester)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(screenContentPadding()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "MAP", style = MaterialTheme.typography.title2, color = MaterialTheme.colors.primary)
            Spacer(Modifier.height(12.dp))

            when {
                !hasLocationPermission -> {
                    CrtCard(title = "ACCESS REQUIRED") {
                        Text(
                            "Grant location access to track runs.",
                            color = MaterialTheme.colors.primary,
                            style = MaterialTheme.typography.body2
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            permissionLauncher.launch(
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BODY_SENSORS)
                            )
                        }) {
                            Text("GRANT")
                        }
                    }
                }
                isTracking -> {
                    LiveRunCard(liveStats)
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
        ScanlineOverlay()
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
private fun ActionRow(label: String, onClick: () -> Unit) {
    CrtCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Text(
            text = label,
            color = MaterialTheme.colors.primary,
            style = MaterialTheme.typography.body2,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun LiveRunCard(stats: RunLiveStats?) {
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
        Text(
            "HR: ${stats?.currentHeartRateBpm?.let { "$it bpm" } ?: "--"}",
            color = MaterialTheme.colors.primary,
            style = MaterialTheme.typography.body2
        )
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
