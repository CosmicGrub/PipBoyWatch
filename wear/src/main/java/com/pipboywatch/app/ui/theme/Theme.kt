package com.pipboywatch.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Typography

// CRT phosphor palette — everything else in the app should pull from these
// via MaterialTheme.colors rather than hardcoding hex values.
val PhosphorGreen = Color(0xFF33FF33)
val PhosphorGreenDim = Color(0xFF1AAA1A)
val CrtBlack = Color(0xFF0A1A0A)
val AlertRed = Color(0xFFFF3333)

private val PipBoyColors = Colors(
    primary = PhosphorGreen,
    primaryVariant = PhosphorGreenDim,
    secondary = PhosphorGreen,
    secondaryVariant = PhosphorGreenDim,
    background = CrtBlack,
    surface = CrtBlack,
    error = AlertRed,
    onPrimary = CrtBlack,
    onSecondary = CrtBlack,
    onBackground = PhosphorGreen,
    onSurface = PhosphorGreen,
    onError = CrtBlack
)

// Monospace everywhere — cheapest way to make every screen read as a
// terminal instead of default Wear OS Sans.
private val PipBoyTypography = Typography(defaultFontFamily = FontFamily.Monospace)

@Composable
fun PipBoyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = PipBoyColors,
        typography = PipBoyTypography,
        content = content
    )
}
