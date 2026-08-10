package com.pipboywatch.app.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/**
 * Content padding for a tab's scrollable Column. Round watches (this one
 * included) clip content that sits too close to the top/bottom edge under
 * the bezel curvature — visible in screenshots since Phase 1. Extra margin
 * on round screens keeps titles and the last row readable without
 * requiring extra scrolling on square ones.
 */
@Composable
fun screenContentPadding(): PaddingValues {
    val isRound = LocalConfiguration.current.isScreenRound
    return if (isRound) {
        PaddingValues(top = 36.dp, bottom = 32.dp, start = 16.dp, end = 16.dp)
    } else {
        PaddingValues(top = 24.dp, bottom = 20.dp, start = 12.dp, end = 12.dp)
    }
}
