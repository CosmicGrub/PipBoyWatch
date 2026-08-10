package com.pipboywatch.app.ui.tab

import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.health.connect.client.PermissionController
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.pipboywatch.app.health.HEALTH_PERMISSIONS
import com.pipboywatch.app.health.HealthConnectManager
import com.pipboywatch.app.health.PhoneStatRelay
import com.pipboywatch.app.health.PhoneStatResult
import com.pipboywatch.app.health.StatSnapshot
import com.pipboywatch.app.ui.components.CrtCard
import com.pipboywatch.app.ui.components.ScanlineOverlay
import com.pipboywatch.app.ui.components.screenContentPadding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PHONE_RELAY_TIMEOUT_MS = 8000L

private sealed interface StatUiState {
    data object Loading : StatUiState
    data object Unavailable : StatUiState
    data object NeedsPermission : StatUiState
    data object AwaitingPhone : StatUiState
    data object PhoneNeedsPermission : StatUiState
    data class Loaded(val snapshot: StatSnapshot, val viaPhone: Boolean) : StatUiState
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
                StatUiState.Loaded(healthManager.readStatSnapshot(), viaPhone = false)
            } else {
                StatUiState.NeedsPermission
            }
        }
    }

    suspend fun tryPhoneRelay() {
        uiState = StatUiState.AwaitingPhone
        PhoneStatRelay.clear()
        PhoneStatRelay.requestFromPhone(context)
    }

    LaunchedEffect(Unit) {
        if (healthManager.isAvailable) {
            uiState = if (healthManager.hasAllPermissions()) {
                StatUiState.Loaded(healthManager.readStatSnapshot(), viaPhone = false)
            } else {
                StatUiState.NeedsPermission
            }
        } else {
            // On-watch Health Connect is unavailable on this hardware — ask
            // the paired phone for a snapshot instead (see PhoneStatRelay).
            tryPhoneRelay()
        }
    }

    val phoneResult by PhoneStatRelay.result.collectAsState()
    LaunchedEffect(phoneResult) {
        when (val result = phoneResult) {
            null -> Unit
            is PhoneStatResult.Success -> uiState = StatUiState.Loaded(result.snapshot, viaPhone = true)
            PhoneStatResult.NeedsPermission -> uiState = StatUiState.PhoneNeedsPermission
            PhoneStatResult.Unavailable -> uiState = StatUiState.Unavailable
        }
    }

    // If the phone never replies (not connected, app not installed there
    // yet, etc.), don't hang on "AWAITING PHONE" forever.
    LaunchedEffect(uiState) {
        if (uiState == StatUiState.AwaitingPhone) {
            delay(PHONE_RELAY_TIMEOUT_MS)
            if (uiState == StatUiState.AwaitingPhone) uiState = StatUiState.Unavailable
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
            Text(text = "STAT", style = MaterialTheme.typography.title2, color = MaterialTheme.colors.primary)
            Spacer(Modifier.height(12.dp))

            when (val state = uiState) {
                is StatUiState.Loading -> LoadingCard()
                is StatUiState.AwaitingPhone -> AwaitingPhoneCard()
                is StatUiState.PhoneNeedsPermission -> PhoneNeedsPermissionCard {
                    coroutineScope.launch { tryPhoneRelay() }
                }
                is StatUiState.Unavailable -> UnavailableCard {
                    coroutineScope.launch { tryPhoneRelay() }
                }
                is StatUiState.NeedsPermission -> NeedsPermissionCard {
                    permissionLauncher.launch(HEALTH_PERMISSIONS)
                }
                is StatUiState.Loaded -> LoadedStats(state.snapshot, state.viaPhone)
            }
        }
        ScanlineOverlay()
    }
}

/** Full-width tappable terminal row — same fix as RADIO's TransportRow:
 * Wear Compose's Button defaults to a small circular icon shape that wraps
 * short text mid-word ("GRAN"/"T"), so plain-text actions use a CrtCard
 * row instead. */
@Composable
private fun TerminalActionRow(label: String, onClick: () -> Unit) {
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
private fun LoadingCard() {
    CrtCard { Text("Loading...", color = MaterialTheme.colors.primary, style = MaterialTheme.typography.body2) }
}

@Composable
private fun AwaitingPhoneCard() {
    CrtCard(title = "SYNCING") {
        Text(
            "Watch Health Connect unavailable — asking paired phone for today's stats...",
            color = MaterialTheme.colors.primary,
            style = MaterialTheme.typography.body2
        )
    }
}

@Composable
private fun PhoneNeedsPermissionCard(onRetry: () -> Unit) {
    CrtCard(title = "ACCESS REQUIRED") {
        Text(
            "Open the Pip-Boy Companion app on your phone and grant Health Connect access there.",
            color = MaterialTheme.colors.primary,
            style = MaterialTheme.typography.body2
        )
    }
    Spacer(Modifier.height(8.dp))
    TerminalActionRow("RETRY") { onRetry() }
}

@Composable
private fun UnavailableCard(onRetry: () -> Unit) {
    CrtCard(title = "NO SIGNAL") {
        Text(
            "Health Connect isn't available on this device, and no paired phone answered either. " +
                "Make sure the Pip-Boy Companion app is installed and reachable on your phone.",
            color = MaterialTheme.colors.primary,
            style = MaterialTheme.typography.body2
        )
    }
    Spacer(Modifier.height(8.dp))
    TerminalActionRow("RETRY") { onRetry() }
}

@Composable
private fun NeedsPermissionCard(onGrant: () -> Unit) {
    CrtCard(title = "ACCESS REQUIRED") {
        Text(
            "Grant health data access to populate this screen.",
            color = MaterialTheme.colors.primary,
            style = MaterialTheme.typography.body2
        )
    }
    Spacer(Modifier.height(8.dp))
    TerminalActionRow("GRANT") { onGrant() }
}

@Composable
private fun LoadedStats(snapshot: StatSnapshot, viaPhone: Boolean) {
    if (viaPhone) {
        Text(
            "(via paired phone)",
            color = MaterialTheme.colors.primary.copy(alpha = 0.6f),
            style = MaterialTheme.typography.caption2
        )
        Spacer(Modifier.height(8.dp))
    }
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
