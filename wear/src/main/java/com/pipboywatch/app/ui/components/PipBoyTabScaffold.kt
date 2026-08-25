package com.pipboywatch.app.ui.components

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
    val rotaryBehavior = RotaryScrollableDefaults.behavior(scrollableState = scrollState)

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
