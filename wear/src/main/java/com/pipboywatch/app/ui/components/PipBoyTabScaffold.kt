package com.pipboywatch.app.ui.components

import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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

/** Rotary-driven scroll delta is scaled by this before reaching the real
 * ScrollState — Wear Compose's own default 1:1 rotary-to-scroll mapping
 * moved fast enough on the Galaxy Watch6 Classic's physical bezel to
 * skip past a whole section (QUESTS/HOLOTAPES/PERKS/NOTES/BACKUP/
 * DIAGNOSTICS on DATA, for instance) in one detent's worth of rotation —
 * real-world feedback, the same complaint that motivated
 * HomeDialScreen's DIAL_DETENT_THRESHOLD bump. Touch-drag scrolling is
 * untouched by this — see the wrapper's own comment below for why. */
private const val ROTARY_SCROLL_SENSITIVITY = 0.4f

/**
 * The scroll/rotary/title/scanline shell every tab screen was hand-copying
 * (StatScreen, InvScreen, DataScreen, MapScreen, RadioScreen,
 * PlaceholderTabScreen) — identical Box > rotaryScrollable > Column >
 * verticalScroll > screenContentPadding() wrapper, byte-for-byte, six
 * times over. A screen that needs the raw scrollState/focusRequester for
 * something extra doesn't exist today; if one shows up, it can still
 * build its own Box/Column instead of using this.
 */
@Composable
fun PipBoyTabScaffold(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val scrollState = rememberScrollState()
    val focusRequester = rememberActiveFocusRequester()
    // A separate ScrollableState sitting between the rotary input path and
    // the real scrollState, scaling delta down before applying it — the
    // Column's own Modifier.verticalScroll(scrollState) below still uses
    // the real, unscaled scrollState directly, so touch-drag scrolling
    // keeps its normal 1:1 feel; only bezel rotation is slowed.
    val rotaryScrollableState = rememberScrollableState { delta ->
        scrollState.dispatchRawDelta(delta * ROTARY_SCROLL_SENSITIVITY)
        delta
    }
    val rotaryBehavior = RotaryScrollableDefaults.behavior(scrollableState = rotaryScrollableState)

    Box(
        modifier = modifier
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
            Text(text = title, style = MaterialTheme.typography.title2, color = MaterialTheme.colors.primary)
            Spacer(Modifier.height(12.dp))
            content()
        }
        ScanlineOverlay()
    }
}
