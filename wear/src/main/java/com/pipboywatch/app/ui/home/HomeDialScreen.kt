package com.pipboywatch.app.ui.home

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.pipboywatch.app.ui.PipBoyTab
import com.pipboywatch.app.ui.components.ScanlineOverlay

/** How much accumulated rotary scroll (in the same units
 * RotaryScrollableDefaults.behavior reports, roughly pixels) one physical
 * bezel detent needs to produce before it steps to the next tab. Started
 * at 50f, which on the Galaxy Watch6 Classic's physical rotating bezel
 * reliably overshot by 1-3 tabs per detent click — real-world feedback,
 * not a guess: rotating one notch trying to land on INV would often skip
 * straight past it to DATA or further, making some tabs hard to reliably
 * land on at all. Raised well above the original value rather than a
 * small bump, since the overshoot itself was multi-step, not off-by-one.
 * Tune further from here if it now reads as sluggish instead. */
private const val DIAL_DETENT_THRESHOLD = 160f

/**
 * The dial screen: rotate the physical bezel to spin through the five tabs
 * (one detent -> one tab), tap anywhere to enter the highlighted one.
 */
@Composable
fun HomeDialScreen(onTabSelected: (PipBoyTab) -> Unit) {
    val tabs = PipBoyTab.entries
    var selectedIndex by rememberSaveable { mutableStateOf(0) }
    val focusRequester = rememberActiveFocusRequester()
    val view = LocalView.current
    var accumulatedScroll by remember { mutableStateOf(0f) }
    val detentThreshold = DIAL_DETENT_THRESHOLD

    fun step(direction: Int) {
        selectedIndex = (selectedIndex + direction + tabs.size) % tabs.size
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    // A fake ScrollableState that never actually scrolls anything — bezel
    // rotation deltas are fed into it, and we convert accumulated distance
    // into discrete detent steps (one Pip-Boy tab per notch) instead.
    val dialScrollableState = rememberScrollableState { delta ->
        accumulatedScroll += delta
        if (accumulatedScroll >= detentThreshold) {
            step(1)
            accumulatedScroll = 0f
        } else if (accumulatedScroll <= -detentThreshold) {
            step(-1)
            accumulatedScroll = 0f
        }
        delta
    }
    val rotaryBehavior = RotaryScrollableDefaults.behavior(
        scrollableState = dialScrollableState,
        hapticFeedbackEnabled = false // we drive our own per-detent tick above
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .rotaryScrollable(rotaryBehavior, focusRequester)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onTabSelected(tabs[selectedIndex]) }
            ),
        contentAlignment = Alignment.Center
    ) {
        DialReadout(
            previous = tabs[(selectedIndex - 1 + tabs.size) % tabs.size],
            current = tabs[selectedIndex],
            next = tabs[(selectedIndex + 1) % tabs.size]
        )
        ScanlineOverlay()
    }
}

@Composable
private fun DialReadout(previous: PipBoyTab, current: PipBoyTab, next: PipBoyTab) {
    val dimColor = MaterialTheme.colors.primary.copy(alpha = 0.45f)
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "◄ ${previous.label}", style = MaterialTheme.typography.caption1, color = dimColor)
        Text(
            text = current.label,
            style = MaterialTheme.typography.title1,
            color = MaterialTheme.colors.primary
        )
        Text(text = "${next.label} ►", style = MaterialTheme.typography.caption1, color = dimColor)
    }
}
