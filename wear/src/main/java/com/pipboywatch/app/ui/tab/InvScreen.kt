package com.pipboywatch.app.ui.tab

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.pipboywatch.app.data.InvItemEntity
import com.pipboywatch.app.inv.InvRepository
import com.pipboywatch.app.ui.components.CrtCard
import com.pipboywatch.app.ui.components.ScanlineOverlay
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun InvScreen() {
    val context = LocalContext.current
    val repository = remember { InvRepository(context) }
    val coroutineScope = rememberCoroutineScope()
    val items by repository.observeItems().collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        repository.ensureSeeded()
        repository.resetIfNewDay()
        // Foreground-only polling — cancels automatically when this screen
        // leaves composition. Good enough for v1; a WearableListenerService
        // would be needed for background/always-on accuracy.
        while (isActive) {
            repository.refreshPhoneConnection()
            delay(5000)
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
                .padding(top = 28.dp, bottom = 24.dp, start = 12.dp, end = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "INV", style = MaterialTheme.typography.title2, color = MaterialTheme.colors.primary)
            Spacer(Modifier.height(12.dp))

            if (items.isEmpty()) {
                CrtCard { Text("Loading checklist...", color = MaterialTheme.colors.primary, style = MaterialTheme.typography.body2) }
            } else {
                items.forEach { item ->
                    InvRow(item = item, onToggle = { coroutineScope.launch { repository.toggleChecked(item) } })
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
        ScanlineOverlay()
    }
}

@Composable
private fun InvRow(item: InvItemEntity, onToggle: () -> Unit) {
    val statusText = when {
        item.isSystemLinked && item.isChecked -> "[X] auto"
        item.isSystemLinked -> "[ ] auto"
        item.isChecked -> "[X]"
        else -> "[ ]"
    }
    var rowModifier = Modifier.fillMaxWidth()
    if (!item.isSystemLinked) {
        rowModifier = rowModifier.clickable(onClick = onToggle)
    }
    CrtCard(modifier = rowModifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(item.label, color = MaterialTheme.colors.primary, style = MaterialTheme.typography.body2)
            Text(statusText, color = MaterialTheme.colors.primary, style = MaterialTheme.typography.body2)
        }
    }
}
