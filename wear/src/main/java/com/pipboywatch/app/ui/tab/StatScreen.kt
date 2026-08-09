package com.pipboywatch.app.ui.tab

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.pipboywatch.app.health.HEALTH_PERMISSIONS
import com.pipboywatch.app.health.HealthConnectManager
import com.pipboywatch.app.health.StatSnapshot
import com.pipboywatch.app.ui.components.CrtCard
import com.pipboywatch.app.ui.components.ScanlineOverlay
import kotlinx.coroutines.launch

private sealed interface StatUiState {
    data object Loading : StatUiState
    data object Unavailable : StatUiState
    data object NeedsPermission : StatUiState
    data class Loaded(val snapshot: StatSnapshot) : StatUiState
}

@Composable
fun StatScreen() {
    val context = LocalContext.current
    val healthManager = remember { HealthConnectManager(context) }
    val coroutineScope = rememberCoroutineScope()
    var uiState by remember { mutableStateOf<StatUiState>(StatUiState.Loading) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        coroutineScope.launch {
            uiState = if (granted.containsAll(HEALTH_PERMISSIONS)) {
                StatUiState.Loaded(healthManager.readStatSnapshot())
            } else {
                StatUiState.NeedsPermission
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!healthManager.isAvailable) {
            uiState = StatUiState.Unavailable
            return@LaunchedEffect
        }
        uiState = if (healthManager.hasAllPermissions()) {
            StatUiState.Loaded(healthManager.readStatSnapshot())
        } else {
            StatUiState.NeedsPermission
        }
    }

    val scrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }
    val rotaryBehavior = RotaryScrollableDefaults.behavior(scrollableState = scrollState)
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .rotaryScrollable(rotaryBehavior, focusRequester)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(top = 28.dp, bottom = 24.dp, start = 12.dp, end = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "STAT", style = MaterialTheme.typography.title2, color = MaterialTheme.colors.primary)
            Spacer(Modifier.height(12.dp))

            when (val state = uiState) {
                is StatUiState.Loading -> LoadingCard()
                is StatUiState.Unavailable -> UnavailableCard()
                is StatUiState.NeedsPermission -> NeedsPermissionCard {
                    permissionLauncher.launch(HEALTH_PERMISSIONS)
                }
                is StatUiState.Loaded -> LoadedStats(state.snapshot)
            }
        }
        ScanlineOverlay()
    }
}

@Composable
private fun LoadingCard() {
    CrtCard { Text("Loading...", color = MaterialTheme.colors.primary, style = MaterialTheme.typography.body2) }
}

@Composable
private fun UnavailableCard() {
    CrtCard(title = "NO SIGNAL") {
        Text(
            "Health Connect isn't available on this device.",
            color = MaterialTheme.colors.primary,
            style = MaterialTheme.typography.body2
        )
    }
}

@Composable
private fun NeedsPermissionCard(onGrant: () -> Unit) {
    CrtCard(title = "ACCESS REQUIRED") {
        Text(
            "Grant health data access to populate this screen.",
            color = MaterialTheme.colors.primary,
            style = MaterialTheme.typography.body2
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onGrant) {
            Text("GRANT")
        }
    }
}

@Composable
private fun LoadedStats(snapshot: StatSnapshot) {
    CrtCard(title = "STEPS TODAY") {
        Text("${snapshot.steps}", color = MaterialTheme.colors.primary, style = MaterialTheme.typography.title3)
    }
    Spacer(Modifier.height(8.dp))
    CrtCard(title = "ACTIVE MINUTES") {
        Text("${snapshot.activeMinutes} min", color = MaterialTheme.colors.primary, style = MaterialTheme.typography.title3)
    }
    Spacer(Modifier.height(8.dp))
    CrtCard(title = "HEART RATE") {
        Text(
            snapshot.latestHeartRateBpm?.let { "$it bpm" } ?: "No recent reading",
            color = MaterialTheme.colors.primary,
            style = MaterialTheme.typography.title3
        )
    }
    Spacer(Modifier.height(8.dp))
    CrtCard(title = "SLEEP LAST NIGHT") {
        val minutes = snapshot.sleepMinutesLastNight
        val text = if (minutes > 0) "${minutes / 60}h ${minutes % 60}m" else "No data"
        Text(text, color = MaterialTheme.colors.primary, style = MaterialTheme.typography.title3)
    }
    Spacer(Modifier.height(8.dp))
    CrtCard(title = "RECENT WORKOUTS") {
        if (snapshot.recentWorkouts.isEmpty()) {
            Text("None in the last 7 days", color = MaterialTheme.colors.primary, style = MaterialTheme.typography.body2)
        } else {
            snapshot.recentWorkouts.forEach { workout ->
                Text(
                    "${workout.title} - ${workout.durationMinutes}min",
                    color = MaterialTheme.colors.primary,
                    style = MaterialTheme.typography.body2
                )
            }
        }
    }
}
