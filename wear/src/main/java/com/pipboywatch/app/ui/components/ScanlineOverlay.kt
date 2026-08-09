package com.pipboywatch.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Subtle horizontal scanline pattern drawn over a screen's content. Purely
 * decorative and non-interactive — always draw it last, on top of real
 * content, never intercepting touch/rotary input.
 */
@Composable
fun ScanlineOverlay(modifier: Modifier = Modifier, lineAlpha: Float = 0.12f) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val spacing = 4.dp.toPx()
        var y = 0f
        while (y < size.height) {
            drawLine(
                color = Color.Black.copy(alpha = lineAlpha),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f
            )
            y += spacing
        }
    }
}
