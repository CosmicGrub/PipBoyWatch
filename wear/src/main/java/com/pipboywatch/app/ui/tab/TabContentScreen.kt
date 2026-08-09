package com.pipboywatch.app.ui.tab

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.pipboywatch.app.ui.PipBoyTab
import com.pipboywatch.app.ui.components.CrtCard
import com.pipboywatch.app.ui.components.ScanlineOverlay

/**
 * Placeholder content for a tab that doesn't have a real screen yet.
 * INV/DATA/MAP/RADIO still use this; STAT graduated to StatScreen in Phase 2.
 */
@Composable
fun PlaceholderTabScreen(tab: PipBoyTab) {
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
            Text(
                text = tab.label,
                style = MaterialTheme.typography.title2,
                color = MaterialTheme.colors.primary
            )
            Spacer(Modifier.height(12.dp))
            CrtCard(title = "STATUS") {
                Text(
                    text = "Real ${tab.label} content arrives in a later phase.",
                    color = MaterialTheme.colors.primary,
                    style = MaterialTheme.typography.body2
                )
            }
            Spacer(Modifier.height(8.dp))
            repeat(6) { i ->
                CrtCard(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = "Placeholder row ${i + 1}",
                        color = MaterialTheme.colors.primary,
                        style = MaterialTheme.typography.body2
                    )
                }
            }
        }
        ScanlineOverlay()
    }
}
